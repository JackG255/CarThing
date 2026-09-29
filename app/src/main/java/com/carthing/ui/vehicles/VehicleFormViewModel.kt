package com.carthing.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carthing.CarThingApp
import com.carthing.data.entity.Vehicle
import com.carthing.data.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

sealed interface VehicleFormUiState {
    data object Loading : VehicleFormUiState
    /** [vehicle] is null when adding a new vehicle. */
    data class Ready(val vehicle: Vehicle?) : VehicleFormUiState
}

/** Backs the add/edit vehicle form; [vehicleId] of 0 means a new vehicle. */
class VehicleFormViewModel(private val vehicleId: Long, private val vehicles: VehicleRepository) : ViewModel() {
    // Loaded once: the form owns the edits, and observing would flip it to "new" after a delete.
    val uiState: Flow<VehicleFormUiState> = flow {
        emit(VehicleFormUiState.Ready(if (vehicleId == 0L) null else vehicles.observe(vehicleId).first()))
    }

    /** Suspends so the caller can navigate only after the write has finished. */
    suspend fun save(vehicle: Vehicle): Long = vehicles.save(vehicle.copy(id = vehicleId))
    suspend fun delete(vehicle: Vehicle) = vehicles.delete(vehicle)

    companion object {
        fun factory(vehicleId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                VehicleFormViewModel(vehicleId, (this[APPLICATION_KEY] as CarThingApp).container.vehicleRepository)
            }
        }
    }
}
