package com.carthing.data

import android.content.Context
import com.carthing.data.attachments.AttachmentRepository
import com.carthing.data.attachments.PhotoStore
import com.carthing.data.backup.BackupArchive
import com.carthing.data.backup.BackupRepository
import com.carthing.data.backup.BackupSettings
import com.carthing.data.backup.FolderBackup
import com.carthing.data.repository.DeadlineRepository
import com.carthing.data.repository.FuelRepository
import com.carthing.data.repository.MaintenanceRepository
import com.carthing.data.repository.ServiceRepository
import com.carthing.data.repository.VehicleRepository
import com.carthing.notifications.ReminderNotifications

/** Manual dependency container; one instance per process, owned by [com.carthing.CarThingApp]. */
class AppContainer(context: Context, db: CarThingDatabase = CarThingDatabase.get(context)) {
    val vehicleRepository = VehicleRepository(db)
    val fuelRepository = FuelRepository(db.fuelEntryDao(), db.vehicleDao())
    val serviceRepository = ServiceRepository(db)
    val maintenanceRepository = MaintenanceRepository(db)
    val deadlineRepository = DeadlineRepository(db)

    val photoStore = PhotoStore(context)
    val attachmentRepository = AttachmentRepository(db, photoStore)

    val backupRepository = BackupRepository(db)
    val backupArchive = BackupArchive(db, backupRepository, photoStore)
    val backupSettings = BackupSettings(context)
    val folderBackup = FolderBackup(context.contentResolver)

    val reminderNotifications = ReminderNotifications(context)
}
