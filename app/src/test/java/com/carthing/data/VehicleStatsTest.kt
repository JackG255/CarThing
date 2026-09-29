package com.carthing.data

import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VehicleStatsTest {
    private fun fill(id: Long, odo: Double, liters: Double, price: Double?) =
        FuelEntry(id = id, vehicleId = 1, dateEpochMillis = id, odometerKm = odo, liters = liters, totalPrice = price)

    @Test fun emptyHistory() {
        val s = VehicleStats.from(emptyList(), emptyList())
        assertNull(s.averageLitersPer100Km)
        assertNull(s.lastLitersPer100Km)
        assertNull(s.costPerKm)
        assertEquals(0.0, s.trackedDistanceKm, 1e-9)
    }

    @Test fun averageIsDistanceWeighted() {
        // Segments: 30 L / 500 km (6.0) and 16 L / 200 km (8.0) -> 46 L / 700 km.
        val fuel = listOf(fill(1, 1000.0, 40.0, 60.0), fill(2, 1500.0, 30.0, 45.0), fill(3, 1700.0, 16.0, null))
        val service = listOf(ServiceEntry(vehicleId = 1, dateEpochMillis = 0, odometerKm = 1200.0, type = "Oil", cost = 35.0))
        val s = VehicleStats.from(fuel, service)
        assertEquals(46.0 / 700.0 * 100.0, s.averageLitersPer100Km!!, 1e-9)
        assertEquals(8.0, s.lastLitersPer100Km!!, 1e-9)
        assertEquals(105.0, s.totalFuelCost, 1e-9)
        assertEquals(35.0, s.totalServiceCost, 1e-9)
        assertEquals(700.0, s.trackedDistanceKm, 1e-9)
        // The first fill (60) is excluded: that fuel was burned before tracking started.
        assertEquals((45.0 + 35.0) / 700.0, s.costPerKm!!, 1e-9)
    }

    @Test fun costPerKmExcludesFirstFillEvenWhenUnsorted() {
        val fuel = listOf(fill(2, 1650.0, 40.5, 1500.0), fill(1, 1000.0, 45.0, 1650.0))
        assertEquals(1500.0 / 650.0, VehicleStats.from(fuel, emptyList()).costPerKm!!, 1e-9)
    }
}
