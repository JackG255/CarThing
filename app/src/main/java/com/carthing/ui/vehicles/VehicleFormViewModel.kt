package com.carthing.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carthing.CarThingApp
import com.carthing.data.entity.Vehicle
import com.carthing.data.repository.DeadlineRepository
import com.carthing.data.repository.MaintenanceRepository
import com.carthing.data.repository.VehicleRepository
import com.carthing.notifications.ReminderNotifications
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

sealed interface VehicleFormUiState {
    data object Loading : VehicleFormUiState
    /** [vehicle] is null when adding a new vehicle. */
    data class Ready(val vehicle: Vehicle?) : VehicleFormUiState
}

/** Backs the add/edit vehicle form; [vehicleId] of 0 means a new vehicle. */
class VehicleFormViewModel(
    private val vehicleId: Long,
    private val vehicles: VehicleRepository,
    private val maintenance: MaintenanceRepository,
    private val deadlines: DeadlineRepository,
    private val notifications: ReminderNotifications,
) : ViewModel() {
    // Loaded once: the form owns the edits, and observing would flip it to "new" after a delete.
    val uiState: Flow<VehicleFormUiState> = flow {
        emit(VehicleFormUiState.Ready(if (vehicleId == 0L) null else vehicles.observe(vehicleId).first()))
    }

    /** Suspends so the caller can navigate only after the write has finished. */
    suspend fun save(vehicle: Vehicle): Long = vehicles.save(vehicle.copy(id = vehicleId))
    /** Deletes [vehicle] with all its records, and removes its pending reminders. */
    suspend fun delete(vehicle: Vehicle) {
        val itemIds = maintenance.idsForVehicle(vehicle.id)
        val deadlineIds = deadlines.idsForVehicle(vehicle.id)
        vehicles.delete(vehicle)
        notifications.cancelVehicle(itemIds, deadlineIds)
    }

    companion object {
        fun factory(vehicleId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as CarThingApp).container
                VehicleFormViewModel(vehicleId, c.vehicleRepository, c.maintenanceRepository, c.deadlineRepository, c.reminderNotifications)
            }
        }
    }
}
