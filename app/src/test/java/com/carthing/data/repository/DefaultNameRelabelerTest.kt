package com.carthing.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.CarThingDatabase
import com.carthing.data.Language
import com.carthing.data.entity.Deadline
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultNameRelabelerTest {
    private lateinit var db: CarThingDatabase
    private var lang = Language.EN
    private var vehicleId = 0L

    private fun prefs(name: String): SharedPreferences =
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences(name, Context.MODE_PRIVATE).also { it.edit().clear().commit() }

    private fun relabeler(p: SharedPreferences = prefs("language")) = DefaultNameRelabeler(db, p) { lang }

    @Before fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java)
            .allowMainThreadQueries().build()
        vehicleId = db.vehicleDao().upsert(Vehicle(name = "Car"))
        db.maintenanceItemDao().insertAll(
            listOf("Brake fluid", "  Coolant ", "brake fluid", "Brake fluid (rear)", "Moje brzdy").map { MaintenanceItem(vehicleId = vehicleId, name = it) }
        )
        listOf("Oil change", "Engine oil & filter").forEach {
            db.serviceEntryDao().upsert(ServiceEntry(vehicleId = vehicleId, dateEpochMillis = 1, odometerKm = 1.0, type = it))
        }
        listOf("Motorway vignette", "Vignette AT").forEach {
            db.deadlineDao().upsert(Deadline(vehicleId = vehicleId, title = it, dueEpochMillis = 1))
        }
    }

    @After fun tearDown() = db.close()

    private suspend fun items() = db.maintenanceItemDao().getForVehicle(vehicleId).map { it.name }
    private suspend fun types() = db.serviceEntryDao().getForVehicle(vehicleId).map { it.type }
    private suspend fun titles() = db.deadlineDao().getForVehicle(vehicleId).map { it.title }

    @Test fun defaultsFollowTheLanguage() = runTest {
        lang = Language.CS
        relabeler().relabel()
        assertEquals(listOf("Brzdová kapalina", "Chladicí kapalina", "brake fluid", "Brake fluid (rear)", "Moje brzdy"), items())
        assertEquals(listOf("Výměna oleje", "Motorový olej a filtr"), types())
        assertEquals(listOf("Dálniční známka", "Vignette AT"), titles())
    }

    @Test fun roundTripRestoresEnglish() = runTest {
        val r = relabeler()
        lang = Language.CS
        r.relabel()
        lang = Language.EN
        r.relabel()
        assertEquals(listOf("Brake fluid", "Coolant", "brake fluid", "Brake fluid (rear)", "Moje brzdy"), items())
        assertEquals(listOf("Oil change", "Engine oil & filter"), types())
        assertEquals(listOf("Motorway vignette", "Vignette AT"), titles())
    }

    @Test fun secondRunWithSameLanguageWritesNothing() = runTest {
        lang = Language.CS
        val r = relabeler()
        assertTrue(r.relabelIfLanguageChanged() > 0)
        assertEquals(0, r.relabelIfLanguageChanged())
    }

    @Test fun slovakFromCzech() = runTest {
        val r = relabeler()
        lang = Language.CS
        r.relabel()
        lang = Language.SK
        r.relabel()
        assertEquals("Brzdová kvapalina", items().first())
    }

    @Test fun firstRunOnlyTouchesEnglishDefaults() = runTest {
        db.serviceEntryDao().upsert(ServiceEntry(vehicleId = vehicleId, dateEpochMillis = 1, odometerKm = 1.0, type = "Oprava"))
        db.deadlineDao().upsert(Deadline(vehicleId = vehicleId, title = "Pojištění", dueEpochMillis = 1))

        relabeler(prefs("fresh-en")).relabelIfLanguageChanged()
        assertTrue("Oprava" in types())
        assertTrue("Pojištění" in titles())

        lang = Language.CS
        relabeler(prefs("fresh-cs")).relabelIfLanguageChanged()
        assertEquals("Brzdová kapalina", items().first())
        assertTrue("Oprava" in types())
        assertTrue("Pojištění" in titles())
    }

    @Test fun forcedRelabelAfterRestore() = runTest {
        val r = relabeler()
        r.relabel()
        db.maintenanceItemDao().insertAll(listOf(MaintenanceItem(vehicleId = vehicleId, name = "Brzdová kapalina")))
        r.relabel()
        assertEquals(0, items().count { it == "Brzdová kapalina" })
        assertEquals(2, items().count { it == "Brake fluid" })
    }
}
