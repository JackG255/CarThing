package com.carthing.data

import android.content.Context
import com.carthing.data.attachments.AttachmentRepository
import com.carthing.data.attachments.PhotoStore
import com.carthing.data.ocr.ReceiptTextReader
import com.carthing.data.backup.BackupArchive
import com.carthing.data.backup.BackupRepository
import com.carthing.data.backup.BackupSettings
import com.carthing.data.backup.FolderBackup
import com.carthing.data.repository.DeadlineRepository
import com.carthing.data.repository.DefaultNameRelabeler
import com.carthing.data.repository.FuelRepository
import com.carthing.data.repository.MaintenanceRepository
import com.carthing.data.repository.ServiceRepository
import com.carthing.data.repository.VehicleRepository
import com.carthing.notifications.ReminderNotifications
import com.carthing.ui.AppearanceSettings

/** Manual dependency container; one instance per process, owned by [com.carthing.CarThingApp]. */
class AppContainer(context: Context, db: CarThingDatabase = CarThingDatabase.get(context)) {
    /** The app's current language; follows the per-app locale (Android 13+) because it reads the application context. */
    val language: () -> Language = { Language.of(context.resources.configuration.locales[0]) }

    val vehicleRepository = VehicleRepository(db, language)
    val fuelRepository = FuelRepository(db.fuelEntryDao(), db.vehicleDao())
    val serviceRepository = ServiceRepository(db)
    val maintenanceRepository = MaintenanceRepository(db)
    val deadlineRepository = DeadlineRepository(db)

    val appearance = AppearanceSettings(context)
    val photoStore = PhotoStore(context)
    val attachmentRepository = AttachmentRepository(db, photoStore)
    val receiptReader = ReceiptTextReader(context)

    val backupRepository = BackupRepository(db)
    val backupArchive = BackupArchive(db, backupRepository, photoStore)
    val backupSettings = BackupSettings(context)
    val folderBackup = FolderBackup(context.contentResolver)

    val relabeler = DefaultNameRelabeler(db, context.getSharedPreferences("language", Context.MODE_PRIVATE), language)

    val reminderNotifications = ReminderNotifications(context)
}
