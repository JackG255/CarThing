package com.carthing.data.dao

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: CarThingDatabase

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
            .allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun vehiclesUpsertGetAndOrderByName() = runTest {
        val dao = db.vehicleDao()
        val id = dao.upsert(Vehicle(name = "Skoda"))
        dao.upsert(Vehicle(name = "Audi"))
        assertEquals(listOf("Audi", "Skoda"), dao.observeAll().first().map { it.name })

        dao.upsert(Vehicle(id = id, name = "Skoda Octavia"))
        assertEquals("Skoda Octavia", dao.getById(id)?.name)
        assertEquals(2, dao.observeAll().first().size)
    }

    @Test fun fuelEntriesWithSameOdometerNewestFirst() = runTest {
        val v = db.vehicleDao().upsert(Vehicle(name = "A"))
        val dao = db.fuelEntryDao()
        dao.upsert(FuelEntry(vehicleId = v, dateEpochMillis = 100, odometerKm = 1000.0, liters = 40.0))
        dao.upsert(FuelEntry(vehicleId = v, dateEpochMillis = 200, odometerKm = 1000.0, liters = 5.0, isFullTank = false))
        assertEquals(listOf(200L, 100L), dao.observeForVehicle(v).first().map { it.dateEpochMillis })
    }

    @Test fun fuelEntriesFilteredByVehicleAndOrderedByOdometerDesc() = runTest {
        val a = db.vehicleDao().upsert(Vehicle(name = "A"))
        val b = db.vehicleDao().upsert(Vehicle(name = "B"))
        val dao = db.fuelEntryDao()
        dao.upsert(FuelEntry(vehicleId = a, dateEpochMillis = 0, odometerKm = 1000.0, liters = 40.0))
        dao.upsert(FuelEntry(vehicleId = a, dateEpochMillis = 0, odometerKm = 1500.0, liters = 30.0))
        dao.upsert(FuelEntry(vehicleId = b, dateEpochMillis = 0, odometerKm = 9000.0, liters = 50.0))
        assertEquals(listOf(1500.0, 1000.0), dao.observeForVehicle(a).first().map { it.odometerKm })
    }

    @Test fun serviceEntriesOrderedByDateDesc() = runTest {
        val v = db.vehicleDao().upsert(Vehicle(name = "A"))
        val dao = db.serviceEntryDao()
        dao.upsert(ServiceEntry(vehicleId = v, dateEpochMillis = 100, odometerKm = 1000.0, type = "Oil change"))
        dao.upsert(ServiceEntry(vehicleId = v, dateEpochMillis = 200, odometerKm = 2000.0, type = "Tires"))
        assertEquals(listOf("Tires", "Oil change"), dao.observeForVehicle(v).first().map { it.type })
    }

    @Test fun deletingVehicleCascadesToEntries() = runTest {
        val id = db.vehicleDao().upsert(Vehicle(name = "A"))
        db.fuelEntryDao().upsert(FuelEntry(vehicleId = id, dateEpochMillis = 0, odometerKm = 1.0, liters = 1.0))
        db.serviceEntryDao().upsert(ServiceEntry(vehicleId = id, dateEpochMillis = 0, odometerKm = 1.0, type = "X"))
        db.vehicleDao().delete(Vehicle(id = id, name = "A"))
        assertNull(db.vehicleDao().getById(id))
        assertTrue(db.fuelEntryDao().observeForVehicle(id).first().isEmpty())
        assertTrue(db.serviceEntryDao().observeForVehicle(id).first().isEmpty())
    }
}
