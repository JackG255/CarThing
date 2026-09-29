package com.carthing.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.DefaultSchedule
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.ScheduleKind
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

    /** A component with both schedules, like a timing belt. */
    private suspend fun belt(vehicleId: Long): MaintenanceItem {
        val id = maintenance.save(MaintenanceItem(vehicleId = vehicleId, name = "Belt",
            inspectMonths = 12, replaceKm = 210_000.0, replaceMonths = 120))
        return maintenance.getById(id)!!
    }

    @Test fun newVehicleGetsDefaultScheduleButEditDoesNot() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        vehicles.save(Vehicle(id = id, name = "Car renamed"))
        val all = items(id)
        assertEquals(DefaultSchedule.templates.size, all.size)
        assertTrue(all.all { it.status.level == DueLevel.UNKNOWN })
        val pressure = all.first { it.item.name == "Tire pressure" }.item
        assertEquals(1, pressure.inspectMonths)
        assertNull(pressure.replacement)
    }

    @Test fun replacementAddsLinkedServiceEntryAndResetsBothSchedules() = runTest {
        val id = vehicles.save(Vehicle(name = "Car", initialOdometerKm = 50_000.0))
        val belt = belt(id)
        val entryId = maintenance.markDone(belt, ScheduleKind.REPLACEMENT, now, 50_000.0, cost = 400.0)!!

        val entry = service.observeForVehicle(id).first().single()
        assertEquals(entryId, entry.id)
        assertEquals(belt.id, entry.maintenanceItemId)
        assertEquals(400.0, entry.cost!!, 1e-9)
        val updated = maintenance.getById(belt.id)!!
        assertEquals(now, updated.lastReplacedEpochMillis)
        assertEquals(now, updated.lastInspectedEpochMillis)
        assertEquals(DueLevel.OK, items(id).first { it.item.id == belt.id }.status.level)
    }

    @Test fun inspectionResetsOnlyInspectionAndAddsNoServiceEntry() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val belt = belt(id)
        assertNull(maintenance.markDone(belt, ScheduleKind.INSPECTION, now, 90_000.0))
        assertTrue(service.observeForVehicle(id).first().isEmpty())
        val updated = maintenance.getById(belt.id)!!
        assertEquals(now, updated.lastInspectedEpochMillis)
        assertNull(updated.lastReplacedEpochMillis)
    }

    @Test fun linkedServiceEntryCountsAsReplacementOnlyWhenNewer() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val oil = item(id, "Engine oil & filter")
        service.save(ServiceEntry(vehicleId = id, dateEpochMillis = now, odometerKm = 60_000.0, type = "Oil", maintenanceItemId = oil.id))
        assertEquals(60_000.0, maintenance.getById(oil.id)!!.lastReplacedOdometerKm!!, 1e-9)

        // Backfilling an older record doesn't move "last replaced" backwards.
        service.save(ServiceEntry(vehicleId = id, dateEpochMillis = now - 400 * DAY_MILLIS, odometerKm = 45_000.0,
            type = "Oil", maintenanceItemId = oil.id))
        assertEquals(60_000.0, maintenance.getById(oil.id)!!.lastReplacedOdometerKm!!, 1e-9)
    }

    @Test fun deletingItemKeepsServiceHistory() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val oil = item(id, "Engine oil & filter")
        maintenance.markDone(oil, ScheduleKind.REPLACEMENT, now, 0.0)
        maintenance.delete(oil)
        assertNull(service.observeForVehicle(id).first().single().maintenanceItemId)
    }

    @Test fun remindersPerScheduleNotifyOncePerLevel() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        // Inspection 12 months, replacement 120 months; both last done 350 days ago.
        maintenance.markDone(belt(id), ScheduleKind.REPLACEMENT, now - 350 * DAY_MILLIS, 0.0)

        val first = maintenance.collectReminders()
        assertEquals(listOf(ScheduleKind.INSPECTION to DueLevel.DUE_SOON), first.map { it.kind to it.level })
        assertTrue(maintenance.collectReminders().isEmpty())

        now += 30 * DAY_MILLIS
        assertEquals(listOf(ScheduleKind.INSPECTION to DueLevel.OVERDUE), maintenance.collectReminders().map { it.kind to it.level })
        assertTrue(maintenance.collectReminders().isEmpty())

        // Inspecting resets only the inspection reminder state.
        val belt = items(id).first { it.item.name == "Belt" }.item
        maintenance.markDone(belt, ScheduleKind.INSPECTION, now, 0.0)
        assertEquals(0, maintenance.getById(belt.id)!!.inspectNotifiedLevel)
    }

    @Test fun inspectionAndReplacementRemindIndependently() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val belt = belt(id)
        // Replacement overdue (done 121 months ago), inspection fine (done yesterday).
        maintenance.save(belt.copy(lastReplacedEpochMillis = now - 3_700 * DAY_MILLIS, lastInspectedEpochMillis = now - DAY_MILLIS))
        assertEquals(listOf(ScheduleKind.REPLACEMENT to DueLevel.OVERDUE), maintenance.collectReminders().map { it.kind to it.level })
    }

    @Test fun disabledItemsSortLastAndNeverRemind() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val wipers = item(id, "Wiper blades")
        maintenance.save(wipers.copy(enabled = false, lastReplacedEpochMillis = now - 800 * DAY_MILLIS))
        assertEquals("Wiper blades", items(id).last().item.name)
        assertTrue(maintenance.collectReminders().isEmpty())
    }

    @Test fun saveRequiresAnInterval() = runTest {
        val id = vehicles.save(Vehicle(name = "Car"))
        val result = runCatching { maintenance.save(MaintenanceItem(vehicleId = id, name = "Thing")) }
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
