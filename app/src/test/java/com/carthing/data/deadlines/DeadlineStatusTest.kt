package com.carthing.data.deadlines

import com.carthing.data.entity.Deadline
import com.carthing.data.maintenance.DueLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

class DeadlineStatusTest {
    private val utc = ZoneOffset.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()
    private val now = LocalDateTime.of(2026, 9, 29, 18, 30).toInstant(utc).toEpochMilli()
    private fun deadline(due: Long, repeat: Int? = 24) = Deadline(id = 1, vehicleId = 1, title = "STK", dueEpochMillis = due, repeatMonths = repeat)
    private fun status(due: Long) = DeadlineStatus.of(deadline(due), now, utc)

    @Test fun farAwayIsOk() {
        val s = status(day(2027, 3, 1))
        assertEquals(DueLevel.OK, s.level)
        assertEquals(153L, s.daysLeft)
    }

    @Test fun withinThirtyDaysIsDueSoon() {
        assertEquals(DueLevel.DUE_SOON, status(day(2026, 10, 29)).level)
        assertEquals(DueLevel.OK, status(day(2026, 10, 30)).level)
    }

    @Test fun dueDayItselfIsStillValidRegardlessOfTimeOfDay() {
        // Evening on the last valid day: 0 days left, not yet expired.
        val s = status(day(2026, 9, 29))
        assertEquals(0L, s.daysLeft)
        assertEquals(DueLevel.DUE_SOON, s.level)
    }

    @Test fun dayAfterIsOverdue() {
        val s = status(day(2026, 9, 28))
        assertEquals(-1L, s.daysLeft)
        assertEquals(DueLevel.OVERDUE, s.level)
    }

    @Test fun nextDueAddsRepeatMonths() {
        assertEquals(day(2027, 9, 15), DeadlineStatus.nextDue(deadline(day(2026, 9, 15), repeat = 12), utc))
        assertEquals(day(2028, 2, 28), DeadlineStatus.nextDue(deadline(day(2026, 2, 28), repeat = 24), utc))
        // Month-end clamps to the last day of the shorter month.
        assertEquals(day(2026, 2, 28), DeadlineStatus.nextDue(deadline(day(2026, 1, 31), repeat = 1), utc))
        assertNull(DeadlineStatus.nextDue(deadline(day(2026, 9, 15), repeat = null), utc))
    }
}
