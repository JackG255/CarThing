package com.carthing.data.backup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.Deadline
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.data.repository.DeadlineRepository
import com.carthing.data.repository.FuelRepository
import com.carthing.data.repository.MaintenanceRepository
import com.carthing.data.repository.ServiceRepository
import com.carthing.data.repository.VehicleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BackupRepositoryTest {
    private val dbs = mutableListOf<CarThingDatabase>()
    private lateinit var source: CarThingDatabase

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
        .allowMainThreadQueries().build().also { dbs += it }

    @Before fun setUp() { source = newDb() }
    @After fun tearDown() = dbs.forEach { it.close() }

    /** A vehicle with every kind of record, including a service entry linked to a component. */
    private suspend fun populate(db: CarThingDatabase, name: String = "Škodovka"): Long {
        val id = VehicleRepository(db).save(Vehicle(name = name, make = "Škoda", year = 2016, initialOdometerKm = 90_000.0))
        FuelRepository(db.fuelEntryDao(), db.vehicleDao()).save(FuelEntry(vehicleId = id, dateEpochMillis = 1_000, odometerKm = 94_000.0, liters = 40.0, totalPrice = 1500.0))
        ServiceRepository(db).save(ServiceEntry(vehicleId = id, dateEpochMillis = 2_000, odometerKm = 94_020.0, type = "Tires", cost = 4000.0))
        val maintenance = MaintenanceRepository(db)
        val oil = maintenance.observeWithStatus(id).first().first { it.item.name == "Engine oil & filter" }.item
        maintenance.markDone(oil, ScheduleKind.REPLACEMENT, 3_000, 94_020.0, cost = 2200.0)
        DeadlineRepository(db).save(Deadline(vehicleId = id, title = "STK", dueEpochMillis = 5_000, repeatMonths = 24, note = "sticker"))
        return id
    }

    private suspend fun snapshot(db: CarThingDatabase) = BackupRepository(db, clock = { 0 }).export()

    @Test fun roundTripRestoresEverythingExactly() = runTest {
        populate(source)
        val exported = BackupRepository(source).export()

        val target = newDb()
        populate(target, name = "Something else") // restoring replaces existing data
        val repo = BackupRepository(target)
        repo.replaceAll(repo.read(exported))

        assertEquals(snapshot(source), snapshot(target))
        val service = target.serviceEntryDao().getForVehicle(target.vehicleDao().getAll().single().id)
        assertTrue("service link survives", service.any { it.maintenanceItemId != null })
    }

    @Test fun summaryCountsRecords() = runTest {
        populate(source)
        val repo = BackupRepository(source)
        val s = repo.summarize(repo.read(repo.export()))
        assertEquals(1, s.vehicles)
        assertEquals(1, s.fuelEntries)
        assertEquals(2, s.serviceEntries) // tires + oil replacement
        assertEquals(1, s.deadlines)
    }

    @Test fun rejectsGarbageNewerVersionsAndBrokenReferences() = runTest {
        populate(source)
        val repo = BackupRepository(source)
        val good = repo.export()
        fun assertRejected(text: String) {
            try { repo.read(text); fail("accepted: ${text.take(60)}") } catch (_: InvalidBackupException) {}
        }
        assertRejected("not json at all")
        assertRejected("""{"hello": "world"}""")
        assertRejected(good.replace("\"formatVersion\": 1", "\"formatVersion\": 99"))
        // A fill-up pointing at a vehicle that isn't in the file.
        val vehicleId = source.vehicleDao().getAll().single().id
        assertRejected(good.replaceFirst("\"vehicleId\": $vehicleId", "\"vehicleId\": 12345"))
    }

    @Test fun failedImportLeavesDataUntouched() = runTest {
        populate(source)
        val before = snapshot(source)
        val repo = BackupRepository(source)
        val broken = repo.read(repo.export()).let { it.copy(fuelEntries = it.fuelEntries.map { e -> e.copy(vehicleId = 999) }) }
        assertTrue(runCatching { repo.replaceAll(broken) }.exceptionOrNull() is InvalidBackupException)
        assertEquals(before, snapshot(source))
    }
}
