package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_entries",
    foreignKeys = [
        ForeignKey(
            entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MaintenanceItem::class, parentColumns = ["id"], childColumns = ["maintenanceItemId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("vehicleId"), Index("maintenanceItemId")]
)
data class ServiceEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateEpochMillis: Long,
    val odometerKm: Double,
    val type: String,          // e.g. "Oil change", "Tires", "Brakes"
    val cost: Double? = null,
    val shop: String? = null,
    val note: String? = null,
    /** The tracked component this service replaced or checked; resets its interval. */
    val maintenanceItemId: Long? = null
)
