package com.carthing.data.backup

import androidx.room.withTransaction
import com.carthing.data.CarThingDatabase
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Thrown when a file can't be imported; nothing has been changed when it is. */
class InvalidBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class BackupSummary(val vehicles: Int, val fuelEntries: Int, val serviceEntries: Int, val deadlines: Int, val exportedAtEpochMillis: Long)

class BackupRepository(
    private val db: CarThingDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        val SAFE_PHOTO_NAME = Regex("""[A-Za-z0-9-]{1,64}\.jpg""")
    }

    /** Serializes all data to JSON. */
    suspend fun export(): String = db.withTransaction {
        val vehicles = db.vehicleDao().getAll()
        val file = BackupFile(
            formatVersion = BackupFile.FORMAT_VERSION,
            exportedAtEpochMillis = clock(),
            vehicles = vehicles.map(VehicleDto::of),
            fuelEntries = vehicles.flatMap { db.fuelEntryDao().getForVehicle(it.id) }.map(FuelEntryDto::of),
            serviceEntries = vehicles.flatMap { db.serviceEntryDao().getForVehicle(it.id) }.map(ServiceEntryDto::of),
            maintenanceItems = vehicles.flatMap { db.maintenanceItemDao().getForVehicle(it.id) }.map(MaintenanceItemDto::of),
            deadlines = vehicles.flatMap { db.deadlineDao().getForVehicle(it.id) }.map(DeadlineDto::of),
            odometerEntries = vehicles.flatMap { db.odometerEntryDao().getForVehicle(it.id) }.map(OdometerEntryDto::of),
            attachments = db.attachmentDao().getAll().map(AttachmentDto::of),
        )
        json.encodeToString(BackupFile.serializer(), file)
    }

    /** Parses and validates [text] without touching the database. */
    fun read(text: String): BackupFile {
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            throw InvalidBackupException("This isn't a CarThing backup file.", e)
        } catch (e: IllegalArgumentException) {
            throw InvalidBackupException("This isn't a CarThing backup file.", e)
        }
        if (file.formatVersion > BackupFile.FORMAT_VERSION) {
            throw InvalidBackupException("This backup was made by a newer version of CarThing. Update the app first.")
        }
        validate(file)
        return file
    }

    fun summarize(file: BackupFile) = BackupSummary(
        file.vehicles.size, file.fuelEntries.size, file.serviceEntries.size, file.deadlines.size, file.exportedAtEpochMillis
    )

    /** Replaces all data with [file] in one transaction; on failure nothing changes. */
    suspend fun replaceAll(file: BackupFile) {
        validate(file)
        db.withTransaction {
            // Deleting vehicles cascades to all their entries, items and deadlines.
            db.vehicleDao().getAll().forEach { db.vehicleDao().delete(it) }
            file.vehicles.forEach { db.vehicleDao().upsert(it.toEntity()) }
            db.maintenanceItemDao().insertAll(file.maintenanceItems.map { it.toEntity() })
            file.fuelEntries.forEach { db.fuelEntryDao().upsert(it.toEntity()) }
            file.serviceEntries.forEach { db.serviceEntryDao().upsert(it.toEntity()) }
            file.deadlines.forEach { db.deadlineDao().upsert(it.toEntity()) }
            file.odometerEntries.forEach { db.odometerEntryDao().upsert(it.toEntity()) }
            db.attachmentDao().insertAll(file.attachments.map { it.toEntity() })
        }
    }

    private fun validate(file: BackupFile) {
        fun fail(what: String): Nothing = throw InvalidBackupException("The backup file is damaged ($what).")
        val vehicleIds = file.vehicles.map { it.id }.toSet()
        if (vehicleIds.size != file.vehicles.size) fail("duplicate vehicles")
        if (file.vehicles.any { it.name.isBlank() }) fail("vehicle without a name")
        val itemIds = file.maintenanceItems.map { it.id }.toSet()
        if (itemIds.size != file.maintenanceItems.size) fail("duplicate components")
        if (file.fuelEntries.any { it.vehicleId !in vehicleIds }) fail("fill-up for a missing vehicle")
        if (file.serviceEntries.any { it.vehicleId !in vehicleIds }) fail("service for a missing vehicle")
        if (file.serviceEntries.any { it.maintenanceItemId != null && it.maintenanceItemId !in itemIds }) fail("service linked to a missing component")
        if (file.maintenanceItems.any { it.vehicleId !in vehicleIds }) fail("component for a missing vehicle")
        if (file.deadlines.any { it.vehicleId !in vehicleIds }) fail("deadline for a missing vehicle")
        if (file.odometerEntries.any { it.vehicleId !in vehicleIds }) fail("odometer reading for a missing vehicle")
        val fuelIds = file.fuelEntries.map { it.id }.toSet()
        val serviceIds = file.serviceEntries.map { it.id }.toSet()
        for (a in file.attachments) {
            if (listOfNotNull(a.fuelEntryId, a.serviceEntryId, a.vehicleId).size != 1) fail("photo without exactly one owner")
            if (a.vehicleId != null && a.vehicleId !in vehicleIds) fail("service book photo for a missing vehicle")
            if (a.fuelEntryId != null && a.fuelEntryId !in fuelIds) fail("photo for a missing fill-up")
            if (a.serviceEntryId != null && a.serviceEntryId !in serviceIds) fail("photo for a missing service")
            // Names become file paths on restore; allow only the app's own generated names.
            if (!SAFE_PHOTO_NAME.matches(a.fileName)) fail("bad photo name")
        }
        if (file.attachments.map { it.fileName }.toSet().size != file.attachments.size) fail("duplicate photos")
    }
}
