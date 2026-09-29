package com.carthing.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.DefaultSchedule
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.Usage.Companion.DAY_MILLIS
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
class MaintenanceRepositoryTest {
    private lateinit var db: CarThingDatabase
    private lateinit var vehicles: VehicleRepository
    private lateinit var service: ServiceRepository
    private lateinit var maintenance: MaintenanceRepository
    private var now = 1_000 * DAY_MILLIS

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
            .allowMainThreadQueries().build()
        vehicles = VehicleRepository(db)
        service = ServiceRepository(db)
        maintenance = MaintenanceRepository(db, clock = { now })
    }

    @After fun tearDown() = db.close()

    private suspend fun items(vehicleId: Long) = maintenance.observeWithStatus(vehicleId).first()
    private suspend fun item(vehicleId: Long, name: String) = items(vehicleId).first { it.item.name == name }.item

    @Test fun newVehicleGetsDefaultScheduleButEditDoesNot() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        vehicles.save(Vehicle(id = id, name = "Car renamed"))
        val all = items(id)
        assertEquals(DefaultSchedule.templates.size, all.size)
        assertTrue(all.all { it.status.level == DueLevel.UNKNOWN })
        assertTrue(all.any { it.item.name == "Tire pressure" && it.item.isCheck && it.item.intervalMonths == 1 })
    }

    @Test fun markDoneReplacementAddsLinkedServiceEntry() = runTest {
        val id = vehicles.save(Vehicle(name = "Car", initialOdometerKm = 50_000.0))
        val oil = item(id, "Engine oil & filter")
        val entryId = maintenance.markDone(oil, now, 50_000.0, cost = 80.0)!!

        val entry = service.observeForVehicle(id).first().single()
        assertEquals(entryId, entry.id)
        assertEquals(oil.id, entry.maintenanceItemId)
        assertEquals("Engine oil & filter", entry.type)
        val updated = items(id).first { it.item.id == oil.id }
        assertEquals(50_000.0, updated.item.lastDoneOdometerKm!!, 1e-9)
        assertEquals(DueLevel.OK, updated.status.level)
    }

    @Test fun markDoneCheckAddsNoServiceEntry() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        assertNull(maintenance.markDone(item(id, "Tire pressure"), now, 0.0))
        assertTrue(service.observeForVehicle(id).first().isEmpty())
        assertEquals(now, item(id, "Tire pressure").lastDoneEpochMillis)
    }

    @Test fun linkedServiceEntryResetsItemOnlyWhenNewer() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val oil = item(id, "Engine oil & filter")
        service.save(ServiceEntry(vehicleId = id, dateEpochMillis = now, odometerKm = 60_000.0, type = "Oil", maintenanceItemId = oil.id))
        assertEquals(60_000.0, item(id, oil.name).lastDoneOdometerKm!!, 1e-9)

        // Backfilling an older record doesn't move "last done" backwards.
        service.save(ServiceEntry(vehicleId = id, dateEpochMillis = now - 400 * DAY_MILLIS, odometerKm = 45_000.0,
            type = "Oil", maintenanceItemId = oil.id))
        assertEquals(60_000.0, item(id, oil.name).lastDoneOdometerKm!!, 1e-9)
    }

    @Test fun deletingItemKeepsServiceHistory() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val oil = item(id, "Engine oil & filter")
        maintenance.markDone(oil, now, 0.0)
        maintenance.delete(oil)
        assertNull(service.observeForVehicle(id).first().single().maintenanceItemId)
    }

    @Test fun collectRemindersNotifiesOncePerLevel() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val wipers = item(id, "Wiper blades") // 12 months, no km limit
        maintenance.markDone(wipers, now - 350 * DAY_MILLIS, 0.0)

        val first = maintenance.collectReminders()
        assertEquals(listOf("Wiper blades" to DueLevel.DUE_SOON), first.map { it.item.name to it.level })
        assertTrue(maintenance.collectReminders().isEmpty())

        now += 30 * DAY_MILLIS
        assertEquals(listOf(DueLevel.OVERDUE), maintenance.collectReminders().map { it.level })
        assertTrue(maintenance.collectReminders().isEmpty())

        // Doing it again resets the reminder state.
        maintenance.markDone(item(id, "Wiper blades"), now, 0.0)
        assertEquals(0, item(id, "Wiper blades").notifiedLevel)
    }

    @Test fun disabledItemsSortLastAndNeverRemind() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val wipers = item(id, "Wiper blades")
        maintenance.save(wipers.copy(enabled = false, lastDoneEpochMillis = now - 800 * DAY_MILLIS))
        assertEquals("Wiper blades", items(id).last().item.name)
        assertTrue(maintenance.collectReminders().isEmpty())
    }

    @Test fun saveRequiresAnInterval() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val result = runCatching { maintenance.save(MaintenanceItem(vehicleId = id, name = "Thing")) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
