package com.carthing.data.dao

import androidx.room.*
import com.carthing.data.entity.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY name")
    fun observeAll(): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :id")
    suspend fun getById(id: Long): Vehicle?

    @Upsert suspend fun upsert(vehicle: Vehicle): Long
    @Delete suspend fun delete(vehicle: Vehicle)
}
