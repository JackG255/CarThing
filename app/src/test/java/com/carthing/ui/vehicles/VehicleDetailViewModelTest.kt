package com.carthing.ui.vehicles

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.repository.FuelRepository
import com.carthing.data.repository.ServiceRepository
import com.carthing.data.repository.VehicleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class VehicleDetailViewModelTest {
    private lateinit var db: CarThingDatabase
    private lateinit var vehicles: VehicleRepository
    private lateinit var fuel: FuelRepository

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
            .allowMainThreadQueries().build()
        vehicles = VehicleRepository(db)
        fuel = FuelRepository(db.fuelEntryDao(), db.vehicleDao())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(id: Long) = VehicleDetailViewModel(id, vehicles, fuel, ServiceRepository(db))

    @Test fun loadedStateCombinesVehicleEntriesAndStats() = runTest {
        val id = vehicles.save(Vehicle(name = "Car", initialOdometerKm = 900.0))
        val vm = viewModel(id)
        vm.saveFuel(FuelEntry(vehicleId = 0, dateEpochMillis = 1, odometerKm = 1000.0, liters = 40.0))
        vm.saveFuel(FuelEntry(vehicleId = 0, dateEpochMillis = 2, odometerKm = 1500.0, liters = 30.0))

        val state = vm.uiState.first { it is VehicleDetailUiState.Loaded && it.fuelEntries.size == 2 }
            as VehicleDetailUiState.Loaded
        assertEquals("Car", state.vehicle.name)
        assertEquals(1500.0, state.currentOdometerKm, 1e-9)
        assertEquals(6.0, state.stats.averageLitersPer100Km!!, 1e-9)
    }

    @Test fun missingVehicleIsNotFound() = runTest {
        assertEquals(VehicleDetailUiState.NotFound, viewModel(42).uiState.first { it != VehicleDetailUiState.Loading })
    }
}
