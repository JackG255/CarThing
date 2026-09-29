package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A photo (receipt, invoice, service book stamp) attached to exactly one fill-up or service entry.
 * The image lives in app-private storage under [fileName]; rows go away with their entry.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(entity = FuelEntry::class, parentColumns = ["id"], childColumns = ["fuelEntryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ServiceEntry::class, parentColumns = ["id"], childColumns = ["serviceEntryId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fuelEntryId"), Index("serviceEntryId"), Index(value = ["fileName"], unique = true)]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fuelEntryId: Long? = null,
    val serviceEntryId: Long? = null,
    val fileName: String,
    val addedEpochMillis: Long,
)
