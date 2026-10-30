package com.carthing.ui.maintenance

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.carthing.data.deadlines.DeadlineStatus
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.Schedule
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.notifications.MaintenanceNotifier
import com.carthing.ui.deadlines.describeExpiry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class StatusTextTest {
    private val res: Resources get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test fun englishByDefault() {
        assertEquals("1 day", formatDays(res, 1))
        assertEquals("5 days", formatDays(res, 5))
        assertEquals("3 weeks", formatDays(res, 21))
    }

    @Test @Config(qualifiers = "cs") fun czechPlurals() {
        assertEquals("1 den", formatDays(res, 1))
        assertEquals("2 dny", formatDays(res, 2))
        assertEquals("5 dní", formatDays(res, 5))
    }

    @Test @Config(qualifiers = "sk") fun slovakPlurals() {
        assertEquals("1 deň", formatDays(res, 1))
        assertEquals("2 dni", formatDays(res, 2))
        assertEquals("5 dní", formatDays(res, 5))
    }

    @Test @Config(qualifiers = "de") fun otherLanguagesFallBackToEnglish() {
        assertEquals("5 days", formatDays(res, 5))
    }

    @Test @Config(qualifiers = "cs") fun czechDeadline() {
        assertTrue(DeadlineStatus(DueLevel.DUE_SOON, 12).describeExpiry(res).contains("12 dní"))
    }

    @Test @Config(qualifiers = "cs") fun czechAgoUsesItsOwnCase() {
        assertTrue(DeadlineStatus(DueLevel.OVERDUE, -3).describeExpiry(res).endsWith("před 3 dny"))
        assertTrue(DeadlineStatus(DueLevel.OVERDUE, -400).describeExpiry(res).endsWith("před 13 měsíci"))
    }

    @Test @Config(qualifiers = "sk") fun slovakAgoUsesItsOwnCase() {
        assertTrue(DeadlineStatus(DueLevel.OVERDUE, -1).describeExpiry(res).endsWith("pred 1 dňom"))
        assertTrue(DeadlineStatus(DueLevel.OVERDUE, -800).describeExpiry(res).endsWith("pred 2 rokmi"))
    }

    @Test fun englishExpiry() {
        assertEquals("expires today", DeadlineStatus(DueLevel.DUE_SOON, 0).describeExpiry(res))
        assertEquals("expired 3 weeks ago", DeadlineStatus(DueLevel.OVERDUE, -21).describeExpiry(res))
    }

    @Test @Config(qualifiers = "cs") fun czechScheduleAgreesWithTheCount() {
        assertEquals("Výměna každé 2 měsíce", Schedule(null, 2, null, null).describe(res, ScheduleKind.REPLACEMENT))
        assertEquals("Kontrola každý měsíc", Schedule(null, 1, null, null).describe(res, ScheduleKind.INSPECTION))
        assertTrue(Schedule(15_000.0, 24, null, null).describe(res, ScheduleKind.REPLACEMENT).endsWith("km nebo 24 měsíců"))
    }

    @Test @Config(qualifiers = "cs") fun czechNotificationTitle() {
        val title = MaintenanceNotifier.reminderTitle(res, "Brzdy", ScheduleKind.REPLACEMENT, DueLevel.OVERDUE)
        assertTrue(title, title.contains("Brzdy"))
        assertFalse(title, title.contains("is overdue"))
        assertFalse(title, title.contains("replacement"))
    }
}
