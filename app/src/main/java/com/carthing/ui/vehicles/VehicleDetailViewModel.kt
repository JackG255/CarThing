package com.carthing.ui.vehicles

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carthing.CarThingApp
import com.carthing.data.VehicleStats
import com.carthing.data.attachments.AttachmentOwner
import com.carthing.data.attachments.AttachmentRepository
import com.carthing.data.attachments.PhotoStore
import com.carthing.data.entity.Attachment
import com.carthing.data.ocr.FuelReceipt
import com.carthing.data.ocr.ReceiptTextReader
import com.carthing.data.entity.Deadline
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.data.repository.DeadlineRepository
import com.carthing.data.repository.DeadlineWithStatus
import com.carthing.data.repository.FuelRepository
import com.carthing.data.repository.ItemWithStatus
import com.carthing.data.repository.MaintenanceRepository
import com.carthing.data.repository.SaveResult
import com.carthing.data.repository.ServiceRepository
import com.carthing.data.repository.VehicleRepository
import com.carthing.notifications.ReminderNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.File

sealed interface VehicleDetailUiState {
    data object Loading : VehicleDetailUiState
    /** The vehicle was deleted or never existed. */
    data object NotFound : VehicleDetailUiState
    data class Loaded(
        val vehicle: Vehicle,
        val currentOdometerKm: Double,
        val fuelEntries: List<FuelEntry>,
        val serviceEntries: List<ServiceEntry>,
        val stats: VehicleStats,
        /** Most urgent first. */
        val maintenance: List<ItemWithStatus>,
        /** Soonest first. */
        val deadlines: List<DeadlineWithStatus>,
        /** Entries with at least one photo, for the paperclip marker. */
        val fuelWithPhotos: Set<Long> = emptySet(),
        val serviceWithPhotos: Set<Long> = emptySet(),
    ) : VehicleDetailUiState
}

private data class Extras(
    val items: List<ItemWithStatus>,
    val deadlines: List<DeadlineWithStatus>,
    val fuelWithPhotos: Set<Long>,
    val serviceWithPhotos: Set<Long>,
)

class VehicleDetailViewModel(
    private val vehicleId: Long,
    private val vehicles: VehicleRepository,
    private val fuel: FuelRepository,
    private val service: ServiceRepository,
    private val maintenance: MaintenanceRepository,
    private val deadlines: DeadlineRepository,
    private val notifications: ReminderNotifications,
    private val attachments: AttachmentRepository,
    private val photos: PhotoStore,
    private val receipts: ReceiptTextReader,
) : ViewModel() {
    val uiState: StateFlow<VehicleDetailUiState> = combine(
        vehicles.observe(vehicleId),
        vehicles.observeCurrentOdometer(vehicleId),
        fuel.observeForVehicle(vehicleId),
        service.observeForVehicle(vehicleId),
        combine(
            maintenance.observeWithStatus(vehicleId), deadlines.observeWithStatus(vehicleId),
            attachments.observeFuelEntriesWithAttachments(), attachments.observeServiceEntriesWithAttachments()
        ) { items, deadlineList, fuelIds, serviceIds -> Extras(items, deadlineList, fuelIds.toSet(), serviceIds.toSet()) }
    ) { vehicle, odometer, fuelEntries, serviceEntries, extras ->
        if (vehicle == null) VehicleDetailUiState.NotFound
        else VehicleDetailUiState.Loaded(
            vehicle, odometer ?: vehicle.initialOdometerKm, fuelEntries, serviceEntries,
            VehicleStats.from(fuelEntries, serviceEntries), extras.items, extras.deadlines, extras.fuelWithPhotos, extras.serviceWithPhotos
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDetailUiState.Loading)

    /** Returns the validation outcome so the form can show errors or ask to confirm warnings. */
    suspend fun saveFuel(entry: FuelEntry, acceptWarnings: Boolean = false): SaveResult =
        fuel.save(entry.copy(vehicleId = vehicleId), acceptWarnings)

    // Suspending so forms can navigate away only after the write has finished.
    suspend fun deleteFuel(entry: FuelEntry) = fuel.delete(entry)
    suspend fun saveService(entry: ServiceEntry): Long = service.save(entry.copy(vehicleId = vehicleId))
    suspend fun deleteService(entry: ServiceEntry) = service.delete(entry)

    suspend fun saveMaintenanceItem(item: MaintenanceItem): Long = maintenance.save(item.copy(vehicleId = vehicleId))
    suspend fun deleteMaintenanceItem(item: MaintenanceItem) {
        maintenance.delete(item)
        notifications.cancelItem(item.id)
    }
    suspend fun markDone(item: MaintenanceItem, kind: ScheduleKind, epochMillis: Long, odometerKm: Double, cost: Double?) {
        maintenance.markDone(item, kind, epochMillis, odometerKm, cost)
        notifications.cancelItem(item.id)
    }

    suspend fun updateOdometer(epochMillis: Long, odometerKm: Double) = vehicles.addOdometerReading(vehicleId, epochMillis, odometerKm)

    fun observeAttachments(owner: AttachmentOwner): Flow<List<Attachment>> = attachments.observe(owner)
    fun photoFile(fileName: String): File = photos.file(fileName)
    fun newCameraUri(): Uri = photos.newCameraUri()
    suspend fun importPhoto(uri: Uri): String = withContext(Dispatchers.IO) { photos.import(uri) }
    suspend fun attachPhotos(owner: AttachmentOwner, fileNames: List<String>) = attachments.attach(owner, fileNames)
    suspend fun deleteAttachment(attachment: Attachment) = withContext(Dispatchers.IO) { attachments.delete(attachment) }
    /** A photo added in a form that is being discarded before saving. */
    fun discardPhoto(fileName: String) = photos.delete(fileName)

    /** Reads a just-added photo as a fuel receipt; null if recognition fails (the photo is still attached). */
    suspend fun readFuelReceipt(fileName: String): FuelReceipt? =
        runCatching { receipts.readFuelReceipt(photos.file(fileName)) }
            .onFailure { Log.w("CarThing", "Receipt reading failed", it) }.getOrNull()

    suspend fun saveDeadline(deadline: Deadline): Long = deadlines.save(deadline.copy(vehicleId = vehicleId))
    suspend fun deleteDeadline(deadline: Deadline) {
        deadlines.delete(deadline)
        notifications.cancelDeadline(deadline.id)
    }
    suspend fun renewDeadline(deadline: Deadline, newDueEpochMillis: Long) {
        deadlines.renew(deadline, newDueEpochMillis)
        notifications.cancelDeadline(deadline.id)
    }

    companion object {
        fun factory(vehicleId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as CarThingApp).container
                VehicleDetailViewModel(vehicleId, c.vehicleRepository, c.fuelRepository, c.serviceRepository,
                    c.maintenanceRepository, c.deadlineRepository, c.reminderNotifications, c.attachmentRepository, c.photoStore,
                    c.receiptReader)
            }
        }
    }
}
