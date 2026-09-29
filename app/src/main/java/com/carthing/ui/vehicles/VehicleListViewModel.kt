package com.carthing.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carthing.CarThingApp
import com.carthing.data.entity.Vehicle
import com.carthing.data.repository.VehicleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface VehicleListUiState {
    data object Loading : VehicleListUiState
    data class Loaded(val vehicles: List<Vehicle>) : VehicleListUiState
}

class VehicleListViewModel(private val vehicles: VehicleRepository) : ViewModel() {
    val uiState: StateFlow<VehicleListUiState> = vehicles.observeAll()
        .map { VehicleListUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleListUiState.Loading)

    fun save(vehicle: Vehicle) { viewModelScope.launch { vehicles.save(vehicle) } }
    fun delete(vehicle: Vehicle) { viewModelScope.launch { vehicles.delete(vehicle) } }

    companion object {
        val Factory = viewModelFactory {
            initializer { VehicleListViewModel((this[APPLICATION_KEY] as CarThingApp).container.vehicleRepository) }
        }
    }
}
