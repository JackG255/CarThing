package com.carthing.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.carthing.data.maintenance.Schedule

/**
 * A component tracked for wear on one vehicle, with up to two independent schedules: inspection
 * and replacement. Each is due when either of its intervals runs out, whichever comes first;
 * a null interval means that limit isn't tracked. Replacing also counts as an inspection.
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
    val enabled: Boolean = true,

    val inspectKm: Double? = null,
    val inspectMonths: Int? = null,
    val lastInspectedEpochMillis: Long? = null,
    val lastInspectedOdometerKm: Double? = null,
    /** Highest inspection reminder already sent: 0 none, 1 due soon, 2 overdue. */
    val inspectNotifiedLevel: Int = 0,

    val replaceKm: Double? = null,
    val replaceMonths: Int? = null,
    val lastReplacedEpochMillis: Long? = null,
    val lastReplacedOdometerKm: Double? = null,
    /** Highest replacement reminder already sent: 0 none, 1 due soon, 2 overdue. */
    val replaceNotifiedLevel: Int = 0,
) {
    /** Null when the component has no inspection schedule. */
    val inspection: Schedule?
        get() = Schedule.of(inspectKm, inspectMonths, lastInspectedEpochMillis, lastInspectedOdometerKm)

    /** Null when the component has no replacement schedule. */
    val replacement: Schedule?
        get() = Schedule.of(replaceKm, replaceMonths, lastReplacedEpochMillis, lastReplacedOdometerKm)
}
