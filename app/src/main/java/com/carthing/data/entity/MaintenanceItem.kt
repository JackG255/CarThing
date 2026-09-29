package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A component tracked for wear on one vehicle. It is due when either interval since it was
 * last done runs out, whichever comes first; a null interval means that limit is not tracked.
 */
@Entity(
    tableName = "maintenance_items",
    foreignKeys = [ForeignKey(
        entity = Vehicle::class, parentColumns = ["id"], childColumns = ["vehicleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("vehicleId")]
)
data class MaintenanceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val name: String,
    val intervalKm: Double? = null,
    val intervalMonths: Int? = null,
    /** An inspection rather than a replacement: marking it done doesn't add a service entry. */
    val isCheck: Boolean = false,
    val enabled: Boolean = true,
    val lastDoneEpochMillis: Long? = null,
    val lastDoneOdometerKm: Double? = null,
    /** Highest reminder already sent for the current interval: 0 none, 1 due soon, 2 overdue. */
    val notifiedLevel: Int = 0
)
