package com.carthing.data

import com.carthing.data.entity.FuelEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FuelEconomyTest {
    private var nextId = 1L
    private fun fill(odo: Double, liters: Double, full: Boolean = true, missed: Boolean = false) =
        FuelEntry(id = nextId++, vehicleId = 1, dateEpochMillis = 0, odometerKm = odo,
            liters = liters, isFullTank = full, missedPrevious = missed)

    @Test fun emptyAndSingleEntryYieldNoSegments() {
        assertTrue(FuelEconomy.segments(emptyList()).isEmpty())
        assertTrue(FuelEconomy.segments(listOf(fill(1000.0, 40.0))).isEmpty())
    }

    @Test fun fullToFull() {
        val s = FuelEconomy.segments(listOf(fill(1000.0, 40.0), fill(1500.0, 30.0))).single()
        assertEquals(500.0, s.distanceKm, 1e-9)
        assertEquals(30.0, s.liters, 1e-9)
        assertEquals(6.0, s.litersPer100Km, 1e-9)
    }

    @Test fun partialsAccumulateIntoNextFull() {
        val entries = listOf(fill(1000.0, 40.0), fill(1200.0, 10.0, full = false), fill(1500.0, 20.0))
        val s = FuelEconomy.segments(entries).single()
        assertEquals(500.0, s.distanceKm, 1e-9)
        assertEquals(30.0, s.liters, 1e-9)
        assertEquals(entries[2].id, s.endEntryId)
    }

    @Test fun partialsBeforeFirstFullAreIgnored() {
        val s = FuelEconomy.segments(listOf(fill(900.0, 15.0, full = false), fill(1000.0, 40.0), fill(1500.0, 30.0)))
        assertEquals(30.0, s.single().liters, 1e-9)
    }

    @Test fun unsortedInputIsOrderedByOdometer() {
        val s = FuelEconomy.segments(listOf(fill(2000.0, 35.0), fill(1000.0, 40.0), fill(1500.0, 30.0)))
        assertEquals(listOf(500.0, 500.0), s.map { it.distanceKm })
        assertEquals(listOf(30.0, 35.0), s.map { it.liters })
    }

    @Test fun missedPreviousFullTankRestartsChain() {
        val s = FuelEconomy.segments(listOf(
            fill(1000.0, 40.0), fill(2000.0, 50.0, missed = true), fill(2400.0, 28.0))).single()
        assertEquals(400.0, s.distanceKm, 1e-9)
        assertEquals(7.0, s.litersPer100Km, 1e-9)
    }

    @Test fun missedPreviousPartialBreaksChainUntilNextFull() {
        val s = FuelEconomy.segments(listOf(
            fill(1000.0, 40.0), fill(2000.0, 10.0, full = false, missed = true),
            fill(2200.0, 30.0), fill(2700.0, 35.0))).single()
        assertEquals(500.0, s.distanceKm, 1e-9)
        assertEquals(35.0, s.liters, 1e-9)
    }

    @Test fun zeroDistanceSegmentIsSkipped() {
        // Documents current behavior: a duplicate odometer reading yields no segment and
        // its liters are dropped rather than carried into the next segment.
        val s = FuelEconomy.segments(listOf(fill(1000.0, 40.0), fill(1000.0, 5.0), fill(1500.0, 30.0)))
        assertEquals(30.0, s.single().liters, 1e-9)
    }
}
