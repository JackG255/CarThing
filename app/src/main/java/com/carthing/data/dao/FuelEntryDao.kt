package com.carthing.data.dao

import androidx.room.*
import com.carthing.data.entity.FuelEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FuelEntryDao {
    @Query("SELECT * FROM fuel_entries WHERE vehicleId = :vehicleId ORDER BY odometerKm DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<FuelEntry>>

    @Upsert suspend fun upsert(entry: FuelEntry): Long
    @Delete suspend fun delete(entry: FuelEntry)
}
