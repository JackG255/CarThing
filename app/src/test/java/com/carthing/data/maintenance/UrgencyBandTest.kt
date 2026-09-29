package com.carthing.data.maintenance

import com.carthing.data.deadlines.DeadlineStatus
import com.carthing.data.entity.Deadline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class UrgencyBandTest {
    @Test fun okSplitsByShareLeft() {
        assertEquals(UrgencyBand.FRESH, UrgencyBand.of(DueLevel.OK, 0.8))
        assertEquals(UrgencyBand.MIDWAY, UrgencyBand.of(DueLevel.OK, 0.5))
        assertEquals(UrgencyBand.MIDWAY, UrgencyBand.of(DueLevel.OK, 0.3))
        assertEquals(UrgencyBand.SOON, UrgencyBand.of(DueLevel.OK, 0.25))
        assertEquals(UrgencyBand.FRESH, UrgencyBand.of(DueLevel.OK, null))
    }

    @Test fun levelsOverrideShare() {
        assertEquals(UrgencyBand.SOON, UrgencyBand.of(DueLevel.DUE_SOON, 0.9))
        assertEquals(UrgencyBand.OVERDUE, UrgencyBand.of(DueLevel.OVERDUE, -0.1))
        assertEquals(UrgencyBand.UNKNOWN, UrgencyBand.of(DueLevel.UNKNOWN, null))
    }

    @Test fun fractionUsesTheCloserLimit() {
        val utc = ZoneOffset.UTC
        val now = LocalDate.of(2026, 9, 29).atStartOfDay(utc).toInstant().toEpochMilli()
        val done = LocalDate.of(2026, 3, 29).atStartOfDay(utc).toInstant().toEpochMilli()
        // Oil every 15,000 km or 12 months, done 6 months and 12,000 km ago: time ~50% left, km 20% left.
        val s = MaintenanceStatus.of(Schedule(15_000.0, 12, done, 40_000.0), Usage(null, null), 52_000.0, now, utc)
        assertEquals(0.2, s.fractionRemaining!!, 1e-9)
        assertEquals(UrgencyBand.SOON, UrgencyBand.of(s.level, s.fractionRemaining))
    }

    @Test fun unknownScheduleHasNoFraction() {
        val s = MaintenanceStatus.of(Schedule(15_000.0, 12, null, null), Usage(null, null), 0.0, 0L)
        assertNull(s.fractionRemaining)
    }

    @Test fun deadlineFractionIsShareOfRepeatPeriod() {
        val utc = ZoneOffset.UTC
        val now = LocalDate.of(2026, 9, 29).atStartOfDay(utc).toInstant().toEpochMilli()
        val due = LocalDate.of(2027, 9, 29).atStartOfDay(utc).toInstant().toEpochMilli()
        val stk = Deadline(vehicleId = 1, title = "STK", dueEpochMillis = due, repeatMonths = 24)
        assertEquals(365 / (24 * MaintenanceStatus.DAYS_PER_MONTH), DeadlineStatus.of(stk, now, utc).fractionRemaining!!, 1e-9)
        assertNull(DeadlineStatus.of(stk.copy(repeatMonths = null), now, utc).fractionRemaining)
    }
}
