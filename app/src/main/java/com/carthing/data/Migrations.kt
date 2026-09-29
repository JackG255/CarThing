package com.carthing.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.carthing.data.maintenance.DefaultSchedule

/** v2: maintenance_items table (seeded with the default schedule) and service_entries.maintenanceItemId. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `maintenance_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleId` INTEGER NOT NULL, `name` TEXT NOT NULL, `intervalKm` REAL, `intervalMonths` INTEGER, " +
                "`isCheck` INTEGER NOT NULL, `enabled` INTEGER NOT NULL, `lastDoneEpochMillis` INTEGER, " +
                "`lastDoneOdometerKm` REAL, `notifiedLevel` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_items_vehicleId` ON `maintenance_items` (`vehicleId`)")

        // SQLite can't add a foreign key to an existing table, so service_entries is rebuilt.
        db.execSQL(
            "CREATE TABLE `service_entries_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleId` INTEGER NOT NULL, `dateEpochMillis` INTEGER NOT NULL, `odometerKm` REAL NOT NULL, " +
                "`type` TEXT NOT NULL, `cost` REAL, `shop` TEXT, `note` TEXT, `maintenanceItemId` INTEGER, " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`maintenanceItemId`) REFERENCES `maintenance_items`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
        )
        db.execSQL(
            "INSERT INTO `service_entries_new` (id, vehicleId, dateEpochMillis, odometerKm, type, cost, shop, note) " +
                "SELECT id, vehicleId, dateEpochMillis, odometerKm, type, cost, shop, note FROM `service_entries`"
        )
        db.execSQL("DROP TABLE `service_entries`")
        db.execSQL("ALTER TABLE `service_entries_new` RENAME TO `service_entries`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_entries_vehicleId` ON `service_entries` (`vehicleId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_service_entries_maintenanceItemId` ON `service_entries` (`maintenanceItemId`)")

        // Existing vehicles get the default schedule, with "last done" unknown.
        for (t in DefaultSchedule.templates) {
            db.execSQL(
                "INSERT INTO maintenance_items (vehicleId, name, intervalKm, intervalMonths, isCheck, enabled, notifiedLevel) " +
                    "SELECT id, ?, ?, ?, ?, 1, 0 FROM vehicles",
                arrayOf(t.name, t.intervalKm, t.intervalMonths, if (t.isCheck) 1 else 0)
            )
        }
    }
}
