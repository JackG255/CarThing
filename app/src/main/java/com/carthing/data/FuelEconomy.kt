package com.carthing.data

import com.carthing.data.entity.FuelEntry

/**
 * Full-tank-to-full-tank method: economy for a segment = liters added since the
 * previous full fill (inclusive of partials) / distance driven. Returns L/100km per segment.
 */
object FuelEconomy {
    data class Segment(val endEntryId: Long, val distanceKm: Double, val liters: Double) {
        val litersPer100Km: Double get() = liters / distanceKm * 100.0
    }

    fun segments(entries: List<FuelEntry>): List<Segment> {
        val sorted = entries.sortedBy { it.odometerKm }
        val result = mutableListOf<Segment>()
        var lastFull: FuelEntry? = null
        var accumulated = 0.0
        for (e in sorted) {
            if (e.missedPrevious) { lastFull = if (e.isFullTank) e else null; accumulated = 0.0; continue }
            if (lastFull == null) { if (e.isFullTank) lastFull = e; continue }
            accumulated += e.liters
            if (e.isFullTank) {
                val dist = e.odometerKm - lastFull.odometerKm
                if (dist > 0) result += Segment(e.id, dist, accumulated)
                lastFull = e; accumulated = 0.0
            }
        }
        return result
    }
}
