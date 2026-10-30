package com.carthing.data.dao

import androidx.room.*
import com.carthing.data.entity.ServiceEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ServiceEntryDao {
    @Query("SELECT * FROM service_entries WHERE vehicleId = :vehicleId ORDER BY dateEpochMillis DESC")
    fun observeForVehicle(vehicleId: Long): Flow<List<ServiceEntry>>

    @Query("SELECT * FROM service_entries WHERE vehicleId = :vehicleId")
    suspend fun getForVehicle(vehicleId: Long): List<ServiceEntry>

    /** Retypes entries whose trimmed type is in [from] to [to]; returns the rows changed. */
    @Query("UPDATE service_entries SET type = :to WHERE trim(type) IN (:from) AND type != :to")
    suspend fun relabelType(from: List<String>, to: String): Int

    @Upsert suspend fun upsert(entry: ServiceEntry): Long
    @Delete suspend fun delete(entry: ServiceEntry)
}
