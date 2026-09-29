package com.carthing.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.Deadline
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.DueLevel
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
class DeadlineRepositoryTest {
    private lateinit var db: CarThingDatabase
    private lateinit var deadlines: DeadlineRepository
    private var now = 20_000 * DAY_MILLIS
    private var vehicleId = 0L

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
            .allowMainThreadQueries().build()
        deadlines = DeadlineRepository(db, clock = { now })
        vehicleId = VehicleRepository(db).save(Vehicle(name = "Car"))
    }

    @After fun tearDown() = db.close()

    private suspend fun add(daysFromNow: Long, title: String = "STK") =
        deadlines.getById(deadlines.save(Deadline(vehicleId = vehicleId, title = title, dueEpochMillis = now + daysFromNow * DAY_MILLIS, repeatMonths = 24)))!!

    @Test fun observedSoonestFirstWithStatus() = runTest {
        add(200, "Insurance")
        add(10, "Vignette")
        val list = deadlines.observeWithStatus(vehicleId).first()
        assertEquals(listOf("Vignette", "Insurance"), list.map { it.deadline.title })
        assertEquals(DueLevel.DUE_SOON, list.first().status.level)
    }

    @Test fun remindsOncePerLevelAndRenewResets() = runTest {
        val stk = add(40)
        assertTrue(deadlines.collectReminders().isEmpty())

        now += 15 * DAY_MILLIS
        assertEquals(listOf(DueLevel.DUE_SOON), deadlines.collectReminders().map { it.status.level })
        assertTrue(deadlines.collectReminders().isEmpty())

        now += 30 * DAY_MILLIS
        assertEquals(listOf(DueLevel.OVERDUE), deadlines.collectReminders().map { it.status.level })
        assertTrue(deadlines.collectReminders().isEmpty())

        deadlines.renew(deadlines.getById(stk.id)!!, now + 700 * DAY_MILLIS)
        val renewed = deadlines.getById(stk.id)!!
        assertEquals(0, renewed.notifiedLevel)
        assertEquals(DueLevel.OK, deadlines.observeWithStatus(vehicleId).first().single().status.level)
    }

    @Test fun deletingVehicleRemovesDeadlines() = runTest {
        add(100)
        VehicleRepository(db).delete(Vehicle(id = vehicleId, name = "Car"))
        assertTrue(deadlines.observeWithStatus(vehicleId).first().isEmpty())
    }

    @Test fun saveValidates() = runTest {
        assertTrue(runCatching { deadlines.save(Deadline(vehicleId = vehicleId, title = " ", dueEpochMillis = now)) }.isFailure)
        assertTrue(runCatching { deadlines.save(Deadline(vehicleId = vehicleId, title = "X", dueEpochMillis = now, repeatMonths = 0)) }.isFailure)
    }
}
