package com.carthing.data.dao

import androidx.room.*
import com.carthing.data.entity.MaintenanceItem
import kotlinx.coroutines.flow.Flow

@Dao
interface MaintenanceItemDao {
    @Query("SELECT * FROM maintenance_items WHERE vehicleId = :vehicleId ORDER BY name")
    fun observeForVehicle(vehicleId: Long): Flow<List<MaintenanceItem>>

    @Query("SELECT * FROM maintenance_items WHERE vehicleId = :vehicleId AND enabled = 1")
    suspend fun getEnabledForVehicle(vehicleId: Long): List<MaintenanceItem>

    @Query("SELECT * FROM maintenance_items WHERE id = :id")
    suspend fun getById(id: Long): MaintenanceItem?

    @Query("UPDATE maintenance_items SET inspectNotifiedLevel = :inspect, replaceNotifiedLevel = :replace WHERE id = :id")
    suspend fun setNotifiedLevels(id: Long, inspect: Int, replace: Int)

    @Insert suspend fun insertAll(items: List<MaintenanceItem>)
    @Upsert suspend fun upsert(item: MaintenanceItem): Long
    @Delete suspend fun delete(item: MaintenanceItem)
}
