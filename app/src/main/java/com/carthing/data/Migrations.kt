package com.carthing.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        for (t in V2_DEFAULTS) {
            db.execSQL(
                "INSERT INTO maintenance_items (vehicleId, name, intervalKm, intervalMonths, isCheck, enabled, notifiedLevel) " +
                    "SELECT id, ?, ?, ?, ?, 1, 0 FROM vehicles",
                arrayOf(t.name, t.km, t.months, if (t.isCheck) 1 else 0)
            )
        }
    }
}

/** The default schedule as it was in v2, frozen so this migration stays reproducible. */
private data class V2Default(val name: String, val km: Double?, val months: Int?, val isCheck: Boolean)

private val V2_DEFAULTS = listOf(
    V2Default("Engine oil & filter", 15_000.0, 12, false),
    V2Default("Air filter", 30_000.0, 24, false),
    V2Default("Cabin filter", 15_000.0, 12, false),
    V2Default("Brake fluid", null, 24, false),
    V2Default("Brake pads", 30_000.0, 24, true),
    V2Default("Coolant", 60_000.0, 48, false),
    V2Default("Spark plugs", 60_000.0, 48, false),
    V2Default("Timing belt", 120_000.0, 60, false),
    V2Default("Tires", 40_000.0, 60, true),
    V2Default("Tire pressure", null, 1, true),
    V2Default("Battery", null, 48, true),
    V2Default("Wiper blades", null, 12, false),
)

/**
 * v3: split each maintenance item's single interval into separate inspection and replacement
 * schedules. v2 "check" items become inspection schedules; everything else becomes replacement.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Dropping the old table can null out service_entries links (ON DELETE SET NULL), so keep a copy.
        db.execSQL("CREATE TEMP TABLE service_links AS SELECT id, maintenanceItemId FROM service_entries WHERE maintenanceItemId IS NOT NULL")

        db.execSQL(
            "CREATE TABLE `maintenance_items_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleId` INTEGER NOT NULL, `name` TEXT NOT NULL, `enabled` INTEGER NOT NULL, " +
                "`inspectKm` REAL, `inspectMonths` INTEGER, `lastInspectedEpochMillis` INTEGER, " +
                "`lastInspectedOdometerKm` REAL, `inspectNotifiedLevel` INTEGER NOT NULL, " +
                "`replaceKm` REAL, `replaceMonths` INTEGER, `lastReplacedEpochMillis` INTEGER, " +
                "`lastReplacedOdometerKm` REAL, `replaceNotifiedLevel` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT INTO maintenance_items_new (id, vehicleId, name, enabled, " +
                "inspectKm, inspectMonths, lastInspectedEpochMillis, lastInspectedOdometerKm, inspectNotifiedLevel, " +
                "replaceKm, replaceMonths, lastReplacedEpochMillis, lastReplacedOdometerKm, replaceNotifiedLevel) " +
                "SELECT id, vehicleId, name, enabled, " +
                "CASE WHEN isCheck THEN intervalKm END, CASE WHEN isCheck THEN intervalMonths END, " +
                "CASE WHEN isCheck THEN lastDoneEpochMillis END, CASE WHEN isCheck THEN lastDoneOdometerKm END, " +
                "CASE WHEN isCheck THEN notifiedLevel ELSE 0 END, " +
                "CASE WHEN isCheck THEN NULL ELSE intervalKm END, CASE WHEN isCheck THEN NULL ELSE intervalMonths END, " +
                "CASE WHEN isCheck THEN NULL ELSE lastDoneEpochMillis END, CASE WHEN isCheck THEN NULL ELSE lastDoneOdometerKm END, " +
                "CASE WHEN isCheck THEN 0 ELSE notifiedLevel END " +
                "FROM maintenance_items"
        )
        db.execSQL("DROP TABLE maintenance_items")
        db.execSQL("ALTER TABLE maintenance_items_new RENAME TO maintenance_items")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_maintenance_items_vehicleId` ON `maintenance_items` (`vehicleId`)")

        db.execSQL(
            "UPDATE service_entries SET maintenanceItemId = " +
                "(SELECT maintenanceItemId FROM service_links WHERE service_links.id = service_entries.id) " +
                "WHERE id IN (SELECT id FROM service_links)"
        )
        db.execSQL("DROP TABLE service_links")
    }
}

/** v4: deadlines table (technical inspection, vignette, insurance...). */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `deadlines` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleId` INTEGER NOT NULL, `title` TEXT NOT NULL, `dueEpochMillis` INTEGER NOT NULL, " +
                "`repeatMonths` INTEGER, `note` TEXT, `notifiedLevel` INTEGER NOT NULL, " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_deadlines_vehicleId` ON `deadlines` (`vehicleId`)")
    }
}

/** v5: odometer_entries, readings logged without a fill-up or service. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `odometer_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`vehicleId` INTEGER NOT NULL, `dateEpochMillis` INTEGER NOT NULL, `odometerKm` REAL NOT NULL, " +
                "FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_odometer_entries_vehicleId` ON `odometer_entries` (`vehicleId`)")
    }
}

/** v6: attachments (receipt and invoice photos) on fuel and service entries. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `attachments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`fuelEntryId` INTEGER, `serviceEntryId` INTEGER, `fileName` TEXT NOT NULL, `addedEpochMillis` INTEGER NOT NULL, " +
                "FOREIGN KEY(`fuelEntryId`) REFERENCES `fuel_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
                "FOREIGN KEY(`serviceEntryId`) REFERENCES `service_entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_fuelEntryId` ON `attachments` (`fuelEntryId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_serviceEntryId` ON `attachments` (`serviceEntryId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_attachments_fileName` ON `attachments` (`fileName`)")
    }
}
