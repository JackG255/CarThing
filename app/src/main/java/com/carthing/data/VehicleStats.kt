package com.carthing.data

import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry

data class VehicleStats(
    /** Distance-weighted average over all economy segments; null until two full fills exist. */
    val averageLitersPer100Km: Double?,
    val lastLitersPer100Km: Double?,
    val totalFuelCost: Double,
    val totalServiceCost: Double,
    /** Distance covered by recorded fill-ups (first to last reading); 0 with fewer than two. */
    val trackedDistanceKm: Double,
    /** (fuel cost excluding the first fill + service cost) / tracked distance; null when no distance is tracked. */
    val costPerKm: Double?
) {
    companion object {
        fun from(fuel: List<FuelEntry>, service: List<ServiceEntry>): VehicleStats {
            val segments = FuelEconomy.segments(fuel)
            val segDistance = segments.sumOf { it.distanceKm }
            val fuelCost = fuel.sumOf { it.totalPrice ?: 0.0 }
            val serviceCost = service.sumOf { it.cost ?: 0.0 }
            val distance = if (fuel.size < 2) 0.0 else fuel.maxOf { it.odometerKm } - fuel.minOf { it.odometerKm }
            // The first fill's fuel was burned before tracking began, so it doesn't pay for the tracked distance.
            val first = fuel.minWithOrNull(compareBy({ it.odometerKm }, { it.dateEpochMillis }, { it.id }))
            val trackedFuelCost = fuelCost - (first?.totalPrice ?: 0.0)
            return VehicleStats(
                averageLitersPer100Km = if (segDistance > 0) segments.sumOf { it.liters } / segDistance * 100.0 else null,
                lastLitersPer100Km = segments.lastOrNull()?.litersPer100Km,
                totalFuelCost = fuelCost,
                totalServiceCost = serviceCost,
                trackedDistanceKm = distance,
                costPerKm = if (distance > 0) (trackedFuelCost + serviceCost) / distance else null
            )
        }
    }
}
