package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A plain odometer reading, logged without a fill-up or service, to keep km estimates current. */
@Entity(
    tableName = "odometer_entries",
    foreignKeys = [ForeignKey(
        entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("vehicleId")]
)
data class OdometerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateEpochMillis: Long,
    val odometerKm: Double,
)
