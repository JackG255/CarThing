package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A photo attached to exactly one owner: a fill-up or service entry (receipts, invoices), or a
 * vehicle itself (its service book: stamp pages not tied to one record).
 * The image lives in app-private storage under [fileName]; rows go away with their entry.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(entity = FuelEntry::class, parentColumns = ["id"], childColumns = ["fuelEntryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ServiceEntry::class, parentColumns = ["id"], childColumns = ["serviceEntryId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("fuelEntryId"), Index("serviceEntryId"), Index("vehicleId"), Index(value = ["fileName"], unique = true)]
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fuelEntryId: Long? = null,
    val serviceEntryId: Long? = null,
    val fileName: String,
    val addedEpochMillis: Long,
    /** Set for service book photos, which belong to the vehicle rather than an entry. */
    val vehicleId: Long? = null,
)
