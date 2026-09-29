package com.carthing.data.maintenance

import com.carthing.data.maintenance.Usage.Companion.DAY_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MaintenanceStatusTest {
    private val utc = ZoneOffset.UTC
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(utc).toInstant().toEpochMilli()
    private val now = day(2026, 9, 29)
    private val noUsage = Usage(null, null)

    private fun item(km: Double? = 15_000.0, months: Int? = 12, doneAt: Long? = null, doneKm: Double? = null) =
        Schedule(km, months, doneAt, doneKm)

    private fun status(item: Schedule, odometer: Double, usage: Usage = noUsage) =
        MaintenanceStatus.of(item, usage, odometer, now, utc)

    @Test fun neverDoneIsUnknown() {
        val s = status(item(), 50_000.0)
        assertEquals(DueLevel.UNKNOWN, s.level)
        assertNull(s.kmRemaining)
        assertNull(s.daysRemaining)
    }

    @Test fun withinBothLimitsIsOk() {
        val s = status(item(doneAt = day(2026, 3, 1), doneKm = 40_000.0), 45_000.0)
        assertEquals(DueLevel.OK, s.level)
        assertEquals(10_000.0, s.kmRemaining!!, 1e-9)
        assertEquals(153L, s.daysRemaining) // 2027-03-01 minus 2026-09-29
    }

    @Test fun distanceLimitAloneMakesItDueSoon() {
        val s = status(item(doneAt = day(2026, 9, 1), doneKm = 40_000.0), 54_500.0)
        assertEquals(DueLevel.DUE_SOON, s.level)
        assertEquals(500.0, s.kmRemaining!!, 1e-9)
    }

    @Test fun timeLimitAloneMakesItOverdue() {
        val s = status(item(doneAt = day(2025, 9, 1), doneKm = 40_000.0), 41_000.0)
        assertEquals(DueLevel.OVERDUE, s.level)
        assertEquals(-28L, s.daysRemaining)
    }

    @Test fun exactlyAtDistanceLimitIsOverdue() {
        assertEquals(DueLevel.OVERDUE, status(item(doneAt = day(2026, 9, 1), doneKm = 40_000.0), 55_000.0).level)
    }

    @Test fun drivingRatePredictsDistanceLimitAndProjectsOdometer() {
        // Last reading 10 days ago at 50,000 km, driving 50 km/day -> ~50,500 km now, 4,500 km left = 90 days.
        val usage = Usage(OdometerReading(now - 10 * DAY_MILLIS, 50_000.0), kmPerDay = 50.0)
        val s = status(item(months = null, doneAt = day(2026, 1, 1), doneKm = 40_000.0), 50_000.0, usage)
        assertEquals(4_500.0, s.kmRemaining!!, 1e-6)
        assertEquals(90L, s.predictedDaysByKm)
        assertEquals(DueLevel.OK, s.level)
    }

    @Test fun predictedWithinThirtyDaysIsDueSoonEvenIfFarInKm() {
        // 3,000 km left is outside the 1,000 km margin, but at 150 km/day that's 20 days.
        val usage = Usage(OdometerReading(now, 52_000.0), kmPerDay = 150.0)
        val s = status(item(months = null, doneAt = day(2026, 1, 1), doneKm = 40_000.0), 52_000.0, usage)
        assertEquals(20L, s.predictedDaysByKm)
        assertEquals(DueLevel.DUE_SOON, s.level)
    }

    @Test fun monthlyCheckUsesShorterSoonWindow() {
        // A monthly check done 10 days ago has ~20 days left: OK, not permanently "due soon".
        val check = item(km = null, months = 1, doneAt = day(2026, 9, 19))
        assertEquals(DueLevel.OK, status(check, 0.0).level)
        assertEquals(DueLevel.DUE_SOON, status(check.copy(lastDoneEpochMillis = day(2026, 9, 5)), 0.0).level)
    }

    @Test fun daysUntilDueIsSoonestOfTimeAndPrediction() {
        val usage = Usage(OdometerReading(now, 50_000.0), kmPerDay = 100.0)
        val s = status(item(doneAt = day(2026, 3, 1), doneKm = 40_000.0), 50_000.0, usage)
        assertEquals(50L, s.predictedDaysByKm) // 5,000 km / 100
        assertEquals(50L, s.daysUntilDue)
    }
}

class ComponentStatusTest {
    private val now = 1_000L * Usage.DAY_MILLIS
    private val usage = Usage(null, null)

    @Test fun primaryIsMoreUrgentSchedule() {
        // Inspection overdue by time, replacement far off: inspection wins.
        val item = com.carthing.data.entity.MaintenanceItem(
            vehicleId = 1, name = "Timing belt",
            inspectMonths = 12, lastInspectedEpochMillis = now - 400 * Usage.DAY_MILLIS,
            replaceKm = 210_000.0, lastReplacedOdometerKm = 0.0
        )
        val s = ComponentStatus.of(item, usage, 90_000.0, now, ZoneOffset.UTC)
        assertEquals(ScheduleKind.INSPECTION, s.primaryKind)
        assertEquals(DueLevel.OVERDUE, s.level)
        assertEquals(DueLevel.OK, s.replacement!!.level)
    }

    @Test fun singleScheduleAndNoSchedule() {
        val oilOnly = com.carthing.data.entity.MaintenanceItem(vehicleId = 1, name = "Oil", replaceMonths = 12)
        val s = ComponentStatus.of(oilOnly, usage, 0.0, now, ZoneOffset.UTC)
        assertNull(s.inspection)
        assertEquals(ScheduleKind.REPLACEMENT, s.primaryKind)
        assertEquals(DueLevel.UNKNOWN, s.level)

        val none = ComponentStatus(null, null)
        assertNull(none.primaryKind)
        assertEquals(DueLevel.UNKNOWN, none.level)
    }
}
