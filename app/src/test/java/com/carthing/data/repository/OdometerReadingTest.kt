package com.carthing.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.data.maintenance.Usage.Companion.DAY_MILLIS
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
class OdometerReadingTest {
    private lateinit var db: CarThingDatabase
    private lateinit var vehicles: VehicleRepository
    private val now = 1_000 * DAY_MILLIS

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
            .allowMainThreadQueries().build()
        vehicles = VehicleRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun readingRaisesCurrentOdometerInDetailAndList() = runTest {
        val id = vehicles.save(Vehicle(name = "Car", initialOdometerKm = 90_000.0))
        vehicles.addOdometerReading(id, now, 94_500.0)
        assertEquals(94_500.0, vehicles.observeCurrentOdometer(id).first()!!, 1e-9)
        assertEquals(94_500.0, vehicles.observeAllWithOdometer().first().single().currentOdometerKm, 1e-9)
    }

    @Test fun readingBelowCurrentIsRejected() = runTest {
        val id = vehicles.save(Vehicle(name = "Car", initialOdometerKm = 90_000.0))
        assertTrue(runCatching { vehicles.addOdometerReading(id, now, 89_000.0) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun readingsFeedTheDrivingRateForKmReminders() = runTest {
        val id = vehicles.save(Vehicle(name = "Car", initialOdometerKm = 0.0))
        val maintenance = MaintenanceRepository(db, clock = { now })
        val oilId = maintenance.save(MaintenanceItem(vehicleId = id, name = "Oil", replaceKm = 15_000.0))
        maintenance.markDone(maintenance.getById(oilId)!!, ScheduleKind.REPLACEMENT, now - 60 * DAY_MILLIS, 10_000.0)
        // One fill-up 60 days ago, then only odometer readings: 100 km/day.
        FuelRepository(db.fuelEntryDao(), db.vehicleDao())
            .save(FuelEntry(vehicleId = id, dateEpochMillis = now - 60 * DAY_MILLIS, odometerKm = 10_000.0, liters = 40.0))
        vehicles.addOdometerReading(id, now - 30 * DAY_MILLIS, 13_000.0)
        vehicles.addOdometerReading(id, now, 16_000.0)

        val oil = maintenance.observeWithStatus(id).first().first { it.item.id == oilId }.status.replacement!!
        assertEquals(9_000.0, oil.kmRemaining!!, 1e-6) // 25,000 due - 16,000 now
        assertEquals(90L, oil.predictedDaysByKm)
    }
}
