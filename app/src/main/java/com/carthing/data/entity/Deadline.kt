package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A date-based obligation such as a technical inspection (STK), vignette or insurance. Unlike
 * maintenance, it's defined by when it expires rather than when it was last done.
 */
@Entity(
    tableName = "deadlines",
    foreignKeys = [ForeignKey(
        entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("vehicleId")]
)
data class Deadline(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val title: String,
    /** Last valid day, as epoch millis at local midnight. */
    val dueEpochMillis: Long,
    /** How far renewing moves the due date; null for one-off deadlines. */
    val repeatMonths: Int? = null,
    val note: String? = null,
    /** Highest reminder already sent for the current due date: 0 none, 1 due soon, 2 overdue. */
    val notifiedLevel: Int = 0
)
