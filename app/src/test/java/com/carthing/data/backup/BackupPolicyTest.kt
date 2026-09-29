package com.carthing.data.backup

import com.carthing.data.maintenance.Usage.Companion.DAY_MILLIS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPolicyTest {
    private val now = 1_000 * DAY_MILLIS

    @Test fun staleWhenNeverOrOlderThanThirtyDays() {
        assertTrue(BackupPolicy.isStale(null, now))
        assertFalse(BackupPolicy.isStale(now - 30 * DAY_MILLIS, now))
        assertTrue(BackupPolicy.isStale(now - 31 * DAY_MILLIS, now))
    }

    @Test fun remindsOnlyWithDataAndStaleBackup() {
        assertFalse(BackupPolicy.shouldRemind(hasData = false, lastBackup = null, lastReminder = null, now = now))
        assertFalse(BackupPolicy.shouldRemind(hasData = true, lastBackup = now - DAY_MILLIS, lastReminder = null, now = now))
        assertTrue(BackupPolicy.shouldRemind(hasData = true, lastBackup = null, lastReminder = null, now = now))
    }

    @Test fun remindsAtMostEveryTwoWeeks() {
        assertFalse(BackupPolicy.shouldRemind(true, null, lastReminder = now - 13 * DAY_MILLIS, now = now))
        assertTrue(BackupPolicy.shouldRemind(true, null, lastReminder = now - 14 * DAY_MILLIS, now = now))
    }

    @Test fun keepsNewestAutoBackupsAndIgnoresOtherFiles() {
        val names = listOf(
            "carthing-auto-2026-09-01-080000.json", "carthing-auto-2026-09-08-080000.json",
            "carthing-auto-2026-09-15-080000.json", "carthing-auto-2026-09-22-080000.json",
            "carthing-auto-2026-09-29-080000.json", "carthing-auto-2026-10-06-080000.json",
            "carthing-auto-2026-10-13-080000.json",
            "carthing-backup-2026-09-29.json", "holiday.jpg", "carthing-auto-notes.txt",
        )
        assertEquals(
            listOf("carthing-auto-2026-09-08-080000.json", "carthing-auto-2026-09-01-080000.json"),
            BackupPolicy.autoBackupsToDelete(names)
        )
        assertTrue(BackupPolicy.autoBackupsToDelete(names.take(3)).isEmpty())
        // Older .json and newer .zip auto backups are ranked together by their timestamp.
        val mixed = listOf("carthing-auto-2026-09-01-080000.json", "carthing-auto-2026-10-01-080000.zip", "carthing-auto-2026-11-01-080000.zip")
        assertEquals(listOf("carthing-auto-2026-09-01-080000.json"), BackupPolicy.autoBackupsToDelete(mixed, keep = 2))
    }
}
