package com.carthing.data.repository

import androidx.room.withTransaction
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.ServiceEntry
import kotlinx.coroutines.flow.Flow

class ServiceRepository(private val db: CarThingDatabase) {
    private val dao = db.serviceEntryDao()

    fun observeForVehicle(vehicleId: Long): Flow<List<ServiceEntry>> = dao.observeForVehicle(vehicleId)

    /**
     * Saves [entry]. When it's linked to a maintenance item and is at least as recent as the
     * item's last-done date, the item's interval restarts from this entry.
     */
    suspend fun save(entry: ServiceEntry): Long {
        require(entry.type.isNotBlank()) { "Service type must not be blank" }
        return db.withTransaction {
            val id = dao.upsert(entry).let { if (it == -1L) entry.id else it }
            val itemDao = db.maintenanceItemDao()
            entry.maintenanceItemId?.let { itemDao.getById(it) }
                ?.takeIf { it.vehicleId == entry.vehicleId && entry.dateEpochMillis >= (it.lastDoneEpochMillis ?: Long.MIN_VALUE) }
                ?.let { itemDao.upsert(it.copy(lastDoneEpochMillis = entry.dateEpochMillis,
                    lastDoneOdometerKm = entry.odometerKm, notifiedLevel = 0)) }
            id
        }
    }

    suspend fun delete(entry: ServiceEntry) = dao.delete(entry)
}
