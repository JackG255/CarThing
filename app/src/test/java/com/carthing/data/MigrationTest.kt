package com.carthing.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), CarThingDatabase::class.java)

    private fun openRoom() = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java, dbName)
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).allowMainThreadQueries().build()

    @Test fun migrate1To4KeepsDataAndSeedsSplitSchedule() = runTest {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO vehicles (id, name, initialOdometerKm) VALUES (1, 'Car', 1000.0)")
            db.execSQL("INSERT INTO vehicles (id, name, initialOdometerKm) VALUES (2, 'Van', 0.0)")
            db.execSQL("INSERT INTO fuel_entries (vehicleId, dateEpochMillis, odometerKm, liters, isFullTank, missedPrevious) VALUES (1, 10, 1500.0, 40.0, 1, 0)")
            db.execSQL("INSERT INTO service_entries (id, vehicleId, dateEpochMillis, odometerKm, type, cost) VALUES (7, 1, 20, 1600.0, 'Oil change', 80.0)")
        }
        // Each step is validated against its exported schema.
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).close()
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).close()
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).close()
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).close()
        helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6).close()

        val room = openRoom()
        try {
            val service = room.serviceEntryDao().observeForVehicle(1).first().single()
            assertEquals(7L, service.id)
            assertNull(service.maintenanceItemId)
            assertEquals(1, room.fuelEntryDao().getForVehicle(1).size)
            assertEquals(0, room.deadlineDao().getForVehicle(1).size)
            assertEquals(0, room.odometerEntryDao().getForVehicle(1).size)

            for (vehicleId in listOf(1L, 2L)) {
                val items = room.maintenanceItemDao().observeForVehicle(vehicleId).first().associateBy { it.name }
                assertEquals(12, items.size)
                // v2 checks became inspection schedules...
                assertEquals(1, items.getValue("Tire pressure").inspectMonths)
                assertNull(items.getValue("Tire pressure").replacement)
                // ...everything else replacement schedules.
                assertEquals(15_000.0, items.getValue("Engine oil & filter").replaceKm!!, 1e-9)
                assertNull(items.getValue("Engine oil & filter").inspection)
            }
        } finally {
            room.close()
        }
    }

    @Test fun migrate2To4MapsLastDoneAndKeepsServiceLinks() = runTest {
        helper.createDatabase(dbName, 2).use { db ->
            db.execSQL("INSERT INTO vehicles (id, name, initialOdometerKm) VALUES (1, 'Car', 0.0)")
            db.execSQL(
                "INSERT INTO maintenance_items (id, vehicleId, name, intervalKm, intervalMonths, isCheck, enabled, " +
                    "lastDoneEpochMillis, lastDoneOdometerKm, notifiedLevel) VALUES " +
                    "(10, 1, 'Engine oil', 15000.0, 12, 0, 1, 500, 50000.0, 1), " +
                    "(11, 1, 'Tire pressure', NULL, 1, 1, 0, 600, 51000.0, 2)"
            )
            db.execSQL("INSERT INTO service_entries (id, vehicleId, dateEpochMillis, odometerKm, type, maintenanceItemId) VALUES (5, 1, 500, 50000.0, 'Engine oil', 10)")
        }
        helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3).close()
        helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4).close()
        helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5).close()
        helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6).close()

        val room = openRoom()
        try {
            val oil = room.maintenanceItemDao().getById(10)!!
            assertEquals(15_000.0, oil.replaceKm!!, 1e-9)
            assertEquals(12, oil.replaceMonths)
            assertEquals(500L, oil.lastReplacedEpochMillis)
            assertEquals(50_000.0, oil.lastReplacedOdometerKm!!, 1e-9)
            assertEquals(1, oil.replaceNotifiedLevel)
            assertNull(oil.inspection)

            val pressure = room.maintenanceItemDao().getById(11)!!
            assertEquals(1, pressure.inspectMonths)
            assertEquals(600L, pressure.lastInspectedEpochMillis)
            assertEquals(2, pressure.inspectNotifiedLevel)
            assertEquals(false, pressure.enabled)
            assertNull(pressure.replacement)

            assertEquals(10L, room.serviceEntryDao().observeForVehicle(1).first().single().maintenanceItemId)
        } finally {
            room.close()
        }
    }
}
