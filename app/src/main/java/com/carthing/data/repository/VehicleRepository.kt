package com.carthing.data.repository

import com.carthing.data.dao.VehicleDao
import com.carthing.data.dao.VehicleWithOdometer
import com.carthing.data.entity.Vehicle
import kotlinx.coroutines.flow.Flow

class VehicleRepository(private val dao: VehicleDao) {
    fun observeAll(): Flow<List<Vehicle>> = dao.observeAll()
    fun observeAllWithOdometer(): Flow<List<VehicleWithOdometer>> = dao.observeAllWithOdometer()
    fun observe(id: Long): Flow<Vehicle?> = dao.observeById(id)
    fun observeCurrentOdometer(id: Long): Flow<Double?> = dao.observeCurrentOdometer(id)

    suspend fun save(vehicle: Vehicle): Long {
        require(vehicle.name.isNotBlank()) { "Vehicle name must not be blank" }
        val id = dao.upsert(vehicle.copy(name = vehicle.name.trim()))
        return if (id == -1L) vehicle.id else id
    }

    suspend fun delete(vehicle: Vehicle) = dao.delete(vehicle)
}
