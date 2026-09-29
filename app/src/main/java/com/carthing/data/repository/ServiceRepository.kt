package com.carthing.data.repository

import com.carthing.data.dao.ServiceEntryDao
import com.carthing.data.entity.ServiceEntry
import kotlinx.coroutines.flow.Flow

class ServiceRepository(private val dao: ServiceEntryDao) {
    fun observeForVehicle(vehicleId: Long): Flow<List<ServiceEntry>> = dao.observeForVehicle(vehicleId)

    suspend fun save(entry: ServiceEntry): Long {
        require(entry.type.isNotBlank()) { "Service type must not be blank" }
        val id = dao.upsert(entry)
        return if (id == -1L) entry.id else id
    }

    suspend fun delete(entry: ServiceEntry) = dao.delete(entry)
}
