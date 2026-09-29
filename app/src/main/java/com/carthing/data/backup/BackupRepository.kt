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
    }
}
