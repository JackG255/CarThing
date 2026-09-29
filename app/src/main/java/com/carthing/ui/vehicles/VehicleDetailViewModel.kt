package com.carthing.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carthing.CarThingApp
import com.carthing.data.VehicleStats
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.repository.FuelRepository
import com.carthing.data.repository.SaveResult
import com.carthing.data.repository.ServiceRepository
import com.carthing.data.repository.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VehicleDetailUiState {
    data object Loading : VehicleDetailUiState
    /** The vehicle was deleted or never existed. */
    data object NotFound : VehicleDetailUiState
    data class Loaded(
        val vehicle: Vehicle,
        val currentOdometerKm: Double,
        val fuelEntries: List<FuelEntry>,
        val serviceEntries: List<ServiceEntry>,
        val stats: VehicleStats
    ) : VehicleDetailUiState
}

class VehicleDetailViewModel(
    private val vehicleId: Long,
    vehicles: VehicleRepository,
    private val fuel: FuelRepository,
    private val service: ServiceRepository
) : ViewModel() {
    val uiState: StateFlow<VehicleDetailUiState> = combine(
        vehicles.observe(vehicleId),
        vehicles.observeCurrentOdometer(vehicleId),
        fuel.observeForVehicle(vehicleId),
        service.observeForVehicle(vehicleId)
    ) { vehicle, odometer, fuelEntries, serviceEntries ->
        if (vehicle == null) VehicleDetailUiState.NotFound
        else VehicleDetailUiState.Loaded(
            vehicle, odometer ?: vehicle.initialOdometerKm, fuelEntries, serviceEntries,
            VehicleStats.from(fuelEntries, serviceEntries)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleDetailUiState.Loading)

    /** Returns the validation outcome so the form can show errors or ask to confirm warnings. */
    suspend fun saveFuel(entry: FuelEntry, acceptWarnings: Boolean = false): SaveResult =
        fuel.save(entry.copy(vehicleId = vehicleId), acceptWarnings)

    fun deleteFuel(entry: FuelEntry) { viewModelScope.launch { fuel.delete(entry) } }
    fun saveService(entry: ServiceEntry) { viewModelScope.launch { service.save(entry.copy(vehicleId = vehicleId)) } }
    fun deleteService(entry: ServiceEntry) { viewModelScope.launch { service.delete(entry) } }

    companion object {
        fun factory(vehicleId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as CarThingApp).container
                VehicleDetailViewModel(vehicleId, c.vehicleRepository, c.fuelRepository, c.serviceRepository)
            }
        }
    }
}
