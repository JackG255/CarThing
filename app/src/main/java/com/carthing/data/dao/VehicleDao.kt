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

    @Query("SELECT * FROM vehicles WHERE id = :id")
    fun observeById(id: Long): Flow<Vehicle?>

    /** Highest known odometer: max of the initial value and all fuel/service readings. */
    @Query(
        """SELECT MAX(v.initialOdometerKm,
               COALESCE((SELECT MAX(odometerKm) FROM fuel_entries WHERE vehicleId = v.id), 0),
               COALESCE((SELECT MAX(odometerKm) FROM service_entries WHERE vehicleId = v.id), 0))
           FROM vehicles v WHERE v.id = :id"""
    )
    fun observeCurrentOdometer(id: Long): Flow<Double?>

    @Upsert suspend fun upsert(vehicle: Vehicle): Long
    @Delete suspend fun delete(vehicle: Vehicle)
}
