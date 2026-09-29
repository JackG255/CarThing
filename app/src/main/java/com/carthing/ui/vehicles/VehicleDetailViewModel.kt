package com.carthing.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carthing.CarThingApp
import com.carthing.data.VehicleStats
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
        val deadlines: List<DeadlineWithStatus>
    ) : VehicleDetailUiState
}

class VehicleDetailViewModel(
    private val vehicleId: Long,
    vehicles: VehicleRepository,
    private val fuel: FuelRepository,
    private val service: ServiceRepository,
    private val maintenance: MaintenanceRepository,
    private val deadlines: DeadlineRepository
) : ViewModel() {
    val uiState: StateFlow<VehicleDetailUiState> = combine(
        vehicles.observe(vehicleId),
        vehicles.observeCurrentOdometer(vehicleId),
        fuel.observeForVehicle(vehicleId),
        service.observeForVehicle(vehicleId),
        combine(maintenance.observeWithStatus(vehicleId), deadlines.observeWithStatus(vehicleId), ::Pair)
    ) { vehicle, odometer, fuelEntries, serviceEntries, (items, deadlineList) ->
        if (vehicle == null) VehicleDetailUiState.NotFound
        else VehicleDetailUiState.Loaded(
            vehicle, odometer ?: vehicle.initialOdometerKm, fuelEntries, serviceEntries,
            VehicleStats.from(fuelEntries, serviceEntries), items, deadlineList
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
    suspend fun deleteMaintenanceItem(item: MaintenanceItem) = maintenance.delete(item)
    suspend fun markDone(item: MaintenanceItem, kind: ScheduleKind, epochMillis: Long, odometerKm: Double, cost: Double?) =
        maintenance.markDone(item, kind, epochMillis, odometerKm, cost)

    suspend fun saveDeadline(deadline: Deadline): Long = deadlines.save(deadline.copy(vehicleId = vehicleId))
    suspend fun deleteDeadline(deadline: Deadline) = deadlines.delete(deadline)
    suspend fun renewDeadline(deadline: Deadline, newDueEpochMillis: Long) = deadlines.renew(deadline, newDueEpochMillis)

    companion object {
        fun factory(vehicleId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as CarThingApp).container
                VehicleDetailViewModel(vehicleId, c.vehicleRepository, c.fuelRepository, c.serviceRepository,
                    c.maintenanceRepository, c.deadlineRepository)
            }
        }
    }
}
