package com.carthing.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: CarThingDatabase
    private lateinit var vehicles: VehicleRepository
    private lateinit var fuel: FuelRepository
    private lateinit var service: ServiceRepository

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
            .allowMainThreadQueries().build()
        vehicles = VehicleRepository(db)
        fuel = FuelRepository(db.fuelEntryDao(), db.vehicleDao())
        service = ServiceRepository(db)
    }

    @After fun tearDown() = db.close()

    private fun fill(vehicleId: Long, date: Long, odo: Double, full: Boolean = true) =
        FuelEntry(vehicleId = vehicleId, dateEpochMillis = date, odometerKm = odo, liters = 30.0, isFullTank = full)

    @Test fun saveReturnsIdForInsertAndUpdate() = runTest {
        val id = vehicles.save(Vehicle(name = "  Car  "))
        assertEquals("Car", vehicles.observe(id).first()?.name)
        assertEquals(id, vehicles.save(Vehicle(id = id, name = "Car 2")))

        val saved = fuel.save(fill(id, 100, 1000.0)) as SaveResult.Saved
        val entry = fuel.observeForVehicle(id).first().single()
        assertEquals(saved.id, entry.id)
        assertEquals(SaveResult.Saved(entry.id), fuel.save(entry.copy(liters = 31.0)))
    }

    @Test fun errorsRejectAndWriteNothing() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        fuel.save(fill(id, 100, 1000.0))
        val result = fuel.save(fill(id, 200, 900.0)) as SaveResult.Rejected
        assertTrue(result.issues.single() is FuelIssue.OdometerOutOfOrder)
        assertEquals(1, fuel.observeForVehicle(id).first().size)
    }

    @Test fun warningsRequireConfirmation() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        fuel.save(fill(id, 100, 1000.0))
        val topOff = fill(id, 200, 1000.0, full = false)
        assertTrue(fuel.save(topOff) is SaveResult.Rejected)
        assertTrue(fuel.save(topOff, acceptWarnings = true) is SaveResult.Saved)
        assertEquals(2, fuel.observeForVehicle(id).first().size)
    }

    @Test fun currentOdometerIsMaxOfInitialFuelAndService() = runTest {
        val id = vehicles.save(Vehicle(name = "Car", initialOdometerKm = 500.0))
        assertEquals(500.0, vehicles.observeCurrentOdometer(id).first()!!, 1e-9)
        fuel.save(fill(id, 100, 1000.0))
        assertEquals(1000.0, vehicles.observeCurrentOdometer(id).first()!!, 1e-9)
        service.save(ServiceEntry(vehicleId = id, dateEpochMillis = 200, odometerKm = 1300.0, type = "Oil"))
        assertEquals(1300.0, vehicles.observeCurrentOdometer(id).first()!!, 1e-9)
    }
}
