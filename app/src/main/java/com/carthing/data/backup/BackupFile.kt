package com.carthing.data.backup

import com.carthing.data.entity.Deadline
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import kotlinx.serialization.Serializable

/**
 * The on-disk backup format. Kept separate from the Room entities so a schema change is a
 * deliberate format change: bump [FORMAT_VERSION] and map older versions on import.
 */
@Serializable
data class BackupFile(
    val formatVersion: Int,
    val exportedAtEpochMillis: Long,
    val vehicles: List<VehicleDto>,
    val fuelEntries: List<FuelEntryDto>,
    val serviceEntries: List<ServiceEntryDto>,
    val maintenanceItems: List<MaintenanceItemDto>,
    val deadlines: List<DeadlineDto>,
) {
    companion object { const val FORMAT_VERSION = 1 }
}

@Serializable
data class VehicleDto(
    val id: Long, val name: String, val make: String? = null, val model: String? = null, val year: Int? = null,
    val vin: String? = null, val licensePlate: String? = null, val initialOdometerKm: Double = 0.0,
) {
    fun toEntity() = Vehicle(id, name, make, model, year, vin, licensePlate, initialOdometerKm)
    companion object {
        fun of(v: Vehicle) = VehicleDto(v.id, v.name, v.make, v.model, v.year, v.vin, v.licensePlate, v.initialOdometerKm)
    }
}

@Serializable
data class FuelEntryDto(
    val id: Long, val vehicleId: Long, val dateEpochMillis: Long, val odometerKm: Double, val liters: Double,
    val totalPrice: Double? = null, val isFullTank: Boolean = true, val missedPrevious: Boolean = false,
    val station: String? = null, val note: String? = null,
) {
    fun toEntity() = FuelEntry(id, vehicleId, dateEpochMillis, odometerKm, liters, totalPrice, isFullTank, missedPrevious, station, note)
    companion object {
        fun of(e: FuelEntry) = FuelEntryDto(e.id, e.vehicleId, e.dateEpochMillis, e.odometerKm, e.liters, e.totalPrice,
            e.isFullTank, e.missedPrevious, e.station, e.note)
    }
}

@Serializable
data class ServiceEntryDto(
    val id: Long, val vehicleId: Long, val dateEpochMillis: Long, val odometerKm: Double, val type: String,
    val cost: Double? = null, val shop: String? = null, val note: String? = null, val maintenanceItemId: Long? = null,
) {
    fun toEntity() = ServiceEntry(id, vehicleId, dateEpochMillis, odometerKm, type, cost, shop, note, maintenanceItemId)
    companion object {
        fun of(e: ServiceEntry) = ServiceEntryDto(e.id, e.vehicleId, e.dateEpochMillis, e.odometerKm, e.type, e.cost,
            e.shop, e.note, e.maintenanceItemId)
    }
}

@Serializable
data class MaintenanceItemDto(
    val id: Long, val vehicleId: Long, val name: String, val enabled: Boolean = true,
    val inspectKm: Double? = null, val inspectMonths: Int? = null,
    val lastInspectedEpochMillis: Long? = null, val lastInspectedOdometerKm: Double? = null, val inspectNotifiedLevel: Int = 0,
    val replaceKm: Double? = null, val replaceMonths: Int? = null,
    val lastReplacedEpochMillis: Long? = null, val lastReplacedOdometerKm: Double? = null, val replaceNotifiedLevel: Int = 0,
) {
    fun toEntity() = MaintenanceItem(id, vehicleId, name, enabled,
        inspectKm, inspectMonths, lastInspectedEpochMillis, lastInspectedOdometerKm, inspectNotifiedLevel,
        replaceKm, replaceMonths, lastReplacedEpochMillis, lastReplacedOdometerKm, replaceNotifiedLevel)
    companion object {
        fun of(m: MaintenanceItem) = MaintenanceItemDto(m.id, m.vehicleId, m.name, m.enabled,
            m.inspectKm, m.inspectMonths, m.lastInspectedEpochMillis, m.lastInspectedOdometerKm, m.inspectNotifiedLevel,
            m.replaceKm, m.replaceMonths, m.lastReplacedEpochMillis, m.lastReplacedOdometerKm, m.replaceNotifiedLevel)
    }
}

@Serializable
data class DeadlineDto(
    val id: Long, val vehicleId: Long, val title: String, val dueEpochMillis: Long,
    val repeatMonths: Int? = null, val note: String? = null, val notifiedLevel: Int = 0,
) {
    fun toEntity() = Deadline(id, vehicleId, title, dueEpochMillis, repeatMonths, note, notifiedLevel)
    companion object {
        fun of(d: Deadline) = DeadlineDto(d.id, d.vehicleId, d.title, d.dueEpochMillis, d.repeatMonths, d.note, d.notifiedLevel)
    }
}
