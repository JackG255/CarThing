package com.carthing.data.attachments

import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.Attachment
import kotlinx.coroutines.flow.Flow

/** Which entry an attachment belongs to. */
sealed interface AttachmentOwner {
    data class Fuel(val entryId: Long) : AttachmentOwner
    data class Service(val entryId: Long) : AttachmentOwner
}

class AttachmentRepository(
    private val db: CarThingDatabase,
    private val photos: PhotoStore,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = db.attachmentDao()

    fun observe(owner: AttachmentOwner): Flow<List<Attachment>> = when (owner) {
        is AttachmentOwner.Fuel -> dao.observeForFuel(owner.entryId)
        is AttachmentOwner.Service -> dao.observeForService(owner.entryId)
    }

    fun observeFuelEntriesWithAttachments(): Flow<List<Long>> = dao.observeFuelEntryIdsWithAttachments()
    fun observeServiceEntriesWithAttachments(): Flow<List<Long>> = dao.observeServiceEntryIdsWithAttachments()

    /** Links already-imported photo files to [owner]; used when a form with new photos is saved. */
    suspend fun attach(owner: AttachmentOwner, fileNames: List<String>) {
        val now = clock()
        dao.insertAll(fileNames.mapIndexed { i, name ->
            Attachment(
                fuelEntryId = (owner as? AttachmentOwner.Fuel)?.entryId,
                serviceEntryId = (owner as? AttachmentOwner.Service)?.entryId,
                fileName = name, addedEpochMillis = now + i // keeps the order they were added in
            )
        })
    }

    suspend fun delete(attachment: Attachment) {
        dao.delete(attachment)
        photos.delete(attachment.fileName)
    }

    /** Removes photo files no entry refers to (deleted entries, abandoned forms). */
    suspend fun sweepOrphans(minAgeMillis: Long = PhotoStore.ORPHAN_AGE_MILLIS): Int =
        photos.sweep(dao.allFileNames().toSet(), clock(), minAgeMillis)
}
