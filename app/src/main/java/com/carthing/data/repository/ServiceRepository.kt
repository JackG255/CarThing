package com.carthing.data.repository

import androidx.room.withTransaction
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.maintenance.ScheduleKind
import kotlinx.coroutines.flow.Flow

class ServiceRepository(private val db: CarThingDatabase) {
    private val dao = db.serviceEntryDao()

    fun observeForVehicle(vehicleId: Long): Flow<List<ServiceEntry>> = dao.observeForVehicle(vehicleId)

    /**
     * Saves [entry]. A service linked to a maintenance item counts as replacing it: when it's at
     * least as recent as the item's last replacement, both of the item's schedules restart from it.
     */
    suspend fun save(entry: ServiceEntry): Long {
        require(entry.type.isNotBlank()) { "Service type must not be blank" }
        return db.withTransaction {
            val id = dao.upsert(entry).let { if (it == -1L) entry.id else it }
            val itemDao = db.maintenanceItemDao()
            entry.maintenanceItemId?.let { itemDao.getById(it) }
                ?.takeIf { it.vehicleId == entry.vehicleId && entry.dateEpochMillis >= (it.lastReplacedEpochMillis ?: Long.MIN_VALUE) }
                ?.let { itemDao.upsert(it.recorded(ScheduleKind.REPLACEMENT, entry.dateEpochMillis, entry.odometerKm)) }
            id
        }
    }

    suspend fun delete(entry: ServiceEntry) = dao.delete(entry)
}
