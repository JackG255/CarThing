package com.carthing.data.dao

import androidx.room.*
import com.carthing.data.entity.Deadline
import kotlinx.coroutines.flow.Flow

@Dao
interface DeadlineDao {
    @Query("SELECT * FROM deadlines WHERE vehicleId = :vehicleId ORDER BY dueEpochMillis")
    fun observeForVehicle(vehicleId: Long): Flow<List<Deadline>>

    @Query("SELECT * FROM deadlines WHERE vehicleId = :vehicleId")
    suspend fun getForVehicle(vehicleId: Long): List<Deadline>

    @Query("SELECT * FROM deadlines WHERE id = :id")
    suspend fun getById(id: Long): Deadline?

    @Query("UPDATE deadlines SET notifiedLevel = :level WHERE id = :id")
    suspend fun setNotifiedLevel(id: Long, level: Int)

    /** Retitles deadlines whose trimmed title is in [from] to [to]; returns the rows changed. */
    @Query("UPDATE deadlines SET title = :to WHERE trim(title) IN (:from) AND title != :to")
    suspend fun relabelTitle(from: List<String>, to: String): Int

    @Upsert suspend fun upsert(deadline: Deadline): Long
    @Delete suspend fun delete(deadline: Deadline)
}
