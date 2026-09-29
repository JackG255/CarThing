package com.carthing.data.maintenance

import com.carthing.data.maintenance.Usage.Companion.DAY_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsageTest {
    private fun r(day: Long, km: Double) = OdometerReading(day * DAY_MILLIS, km)

    @Test fun noReadings() {
        val u = Usage.estimate(emptyList())
        assertNull(u.lastReading)
        assertNull(u.kmPerDay)
        assertNull(u.estimatedOdometerKm(0))
    }

    @Test fun rateFromReadingsInWindow() {
        val u = Usage.estimate(listOf(r(0, 10_000.0), r(20, 11_000.0), r(40, 12_000.0)))
        assertEquals(50.0, u.kmPerDay!!, 1e-9)
        assertEquals(12_500.0, u.estimatedOdometerKm(50 * DAY_MILLIS)!!, 1e-9)
    }

    @Test fun olderReadingsOutsideWindowAreIgnored() {
        // Driving slowed down: the last 90 days show 10 km/day, older history 100 km/day.
        val u = Usage.estimate(listOf(r(0, 0.0), r(100, 10_000.0), r(190, 10_900.0)))
        assertEquals(10.0, u.kmPerDay!!, 1e-9)
    }

    @Test fun fallsBackToAllHistoryWhenWindowTooShort() {
        // Only one reading inside the last 90 days: use the whole span.
        val u = Usage.estimate(listOf(r(0, 0.0), r(200, 4_000.0)))
        assertEquals(20.0, u.kmPerDay!!, 1e-9)
    }

    @Test fun spanUnderAWeekGivesNoRate() {
        val u = Usage.estimate(listOf(r(0, 1_000.0), r(5, 1_500.0)))
        assertNull(u.kmPerDay)
        assertEquals(1_500.0, u.estimatedOdometerKm(100 * DAY_MILLIS)!!, 1e-9)
    }
}
