package com.carthing.data.repository

import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.Vehicle

sealed interface FuelIssue {
    /** Errors block saving; warnings block it unless the user confirms. */
    val isError: Boolean

    data object NonPositiveLiters : FuelIssue { override val isError = true }
    data class BelowInitialOdometer(val initialOdometerKm: Double) : FuelIssue { override val isError = true }
    /** Odometer is out of order with [other]: lower than an earlier entry, or higher than a later one. */
    data class OdometerOutOfOrder(val other: FuelEntry) : FuelIssue { override val isError = true }
    /** Two full-tank fills at the same odometer cannot both be true. */
    data class DuplicateFullTank(val other: FuelEntry) : FuelIssue { override val isError = true }
    /** Same odometer as [other] but one is a partial top-off; plausible, so only a warning. */
    data class SameOdometer(val other: FuelEntry) : FuelIssue { override val isError = false }
}

object FuelValidation {
    /** Validates [entry] against the vehicle's other fill-ups; an entry with the same id is treated as itself being edited. */
    fun validate(entry: FuelEntry, vehicle: Vehicle, existing: List<FuelEntry>): List<FuelIssue> {
        val issues = mutableListOf<FuelIssue>()
        if (entry.liters <= 0) issues += FuelIssue.NonPositiveLiters
        if (entry.odometerKm < vehicle.initialOdometerKm) issues += FuelIssue.BelowInitialOdometer(vehicle.initialOdometerKm)
        for (other in existing) {
            if (entry.id != 0L && other.id == entry.id) continue
            when {
                other.odometerKm == entry.odometerKm ->
                    issues += if (entry.isFullTank && other.isFullTank) FuelIssue.DuplicateFullTank(other)
                              else FuelIssue.SameOdometer(other)
                // Equal timestamps give no ordering information, so only strictly earlier/later entries count.
                other.dateEpochMillis < entry.dateEpochMillis && other.odometerKm > entry.odometerKm ||
                other.dateEpochMillis > entry.dateEpochMillis && other.odometerKm < entry.odometerKm ->
                    issues += FuelIssue.OdometerOutOfOrder(other)
            }
        }
        return issues
    }
}
