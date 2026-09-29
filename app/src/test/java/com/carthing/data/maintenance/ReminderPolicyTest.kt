package com.carthing.data.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPolicyTest {
    private fun status(level: DueLevel) = MaintenanceStatus(level, null, null, null)
    private fun decide(notified: Int, level: DueLevel, enabled: Boolean = true) =
        ReminderPolicy.decide(enabled, notified, status(level))

    @Test fun notifiesOnReachingDueSoonThenOverdueOnce() {
        assertEquals(ReminderPolicy.Decision(DueLevel.DUE_SOON, 1), decide(0, DueLevel.DUE_SOON))
        assertEquals(ReminderPolicy.Decision(null, 1), decide(1, DueLevel.DUE_SOON))
        assertEquals(ReminderPolicy.Decision(DueLevel.OVERDUE, 2), decide(1, DueLevel.OVERDUE))
        assertEquals(ReminderPolicy.Decision(null, 2), decide(2, DueLevel.OVERDUE))
    }

    @Test fun jumpingStraightToOverdueNotifiesOnce() {
        assertEquals(ReminderPolicy.Decision(DueLevel.OVERDUE, 2), decide(0, DueLevel.OVERDUE))
    }

    @Test fun droppingBackLowersStoredLevelSoItCanNotifyAgain() {
        assertEquals(ReminderPolicy.Decision(null, 0), decide(2, DueLevel.OK))
    }

    @Test fun unknownAndDisabledNeverNotify() {
        assertNull(decide(0, DueLevel.UNKNOWN).notify)
        assertNull(decide(0, DueLevel.OVERDUE, enabled = false).notify)
    }
}
