package com.carthing.data.dao

import androidx.room.*
import com.carthing.data.entity.OdometerEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface OdometerEntryDao {
    @Query("SELECT * FROM odometer_entries WHERE vehicleId = :vehicleId ORDER BY dateEpochMillis DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<OdometerEntry>>

    @Query("SELECT * FROM odometer_entries WHERE vehicleId = :vehicleId")
    suspend fun getForVehicle(vehicleId: Long): List<OdometerEntry>

    @Upsert suspend fun upsert(entry: OdometerEntry): Long
}
