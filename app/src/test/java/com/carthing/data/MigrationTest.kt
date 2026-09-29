package com.carthing.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.carthing.data.maintenance.DefaultSchedule
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

    @Test fun migrate1To2KeepsDataAndSeedsSchedule() = runTest {
        helper.createDatabase(dbName, 1).use { db ->
            db.execSQL("INSERT INTO vehicles (id, name, initialOdometerKm) VALUES (1, 'Car', 1000.0)")
            db.execSQL("INSERT INTO vehicles (id, name, initialOdometerKm) VALUES (2, 'Van', 0.0)")
            db.execSQL("INSERT INTO fuel_entries (vehicleId, dateEpochMillis, odometerKm, liters, isFullTank, missedPrevious) VALUES (1, 10, 1500.0, 40.0, 1, 0)")
            db.execSQL("INSERT INTO service_entries (id, vehicleId, dateEpochMillis, odometerKm, type, cost) VALUES (7, 1, 20, 1600.0, 'Oil change', 80.0)")
        }

        // Validates the migrated schema against the exported v2 schema.
        helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2).close()

        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), CarThingDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2).allowMainThreadQueries().build()
        try {
            val service = room.serviceEntryDao().observeForVehicle(1).first().single()
            assertEquals(7L, service.id)
            assertEquals("Oil change", service.type)
            assertNull(service.maintenanceItemId)
            assertEquals(1, room.fuelEntryDao().getForVehicle(1).size)

            for (vehicleId in listOf(1L, 2L)) {
                val items = room.maintenanceItemDao().observeForVehicle(vehicleId).first()
                assertEquals(DefaultSchedule.templates.map { it.name }.sorted(), items.map { it.name })
                assertEquals(1, items.first { it.name == "Tire pressure" }.intervalMonths)
                assertNull(items.first { it.name == "Tire pressure" }.intervalKm)
            }
        } finally {
            room.close()
        }
    }
}
