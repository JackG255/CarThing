package com.carthing.data.dao

import androidx.room.*
import com.carthing.data.entity.Attachment
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE fuelEntryId = :fuelEntryId ORDER BY addedEpochMillis")
    fun observeForFuel(fuelEntryId: Long): Flow<List<Attachment>>

    @Query("SELECT * FROM attachments WHERE serviceEntryId = :serviceEntryId ORDER BY addedEpochMillis")
    fun observeForService(serviceEntryId: Long): Flow<List<Attachment>>

    /** Entry ids that have at least one attachment, for the paperclip marker in lists. */
    @Query("SELECT fuelEntryId FROM attachments WHERE fuelEntryId IS NOT NULL")
    fun observeFuelEntryIdsWithAttachments(): Flow<List<Long>>

    @Query("SELECT serviceEntryId FROM attachments WHERE serviceEntryId IS NOT NULL")
    fun observeServiceEntryIdsWithAttachments(): Flow<List<Long>>

    @Query("SELECT * FROM attachments")
    suspend fun getAll(): List<Attachment>

    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>

    @Insert suspend fun insert(attachment: Attachment): Long
    @Insert suspend fun insertAll(attachments: List<Attachment>)
    @Delete suspend fun delete(attachment: Attachment)
}
