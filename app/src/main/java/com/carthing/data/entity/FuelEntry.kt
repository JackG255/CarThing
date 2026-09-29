package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "fuel_entries",
    foreignKeys = [ForeignKey(
        entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("vehicleId")]
)
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateEpochMillis: Long,
    val odometerKm: Double,
    val liters: Double,
    val totalPrice: Double? = null,
    /** True if the tank was filled completely; required for accurate economy calculation. */
    val isFullTank: Boolean = true,
    /** True if a previous fill-up was not recorded; breaks the economy chain. */
    val missedPrevious: Boolean = false,
    val station: String? = null,
    val note: String? = null
)
