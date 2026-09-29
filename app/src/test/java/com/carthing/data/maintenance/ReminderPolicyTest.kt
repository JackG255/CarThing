package com.carthing.data.maintenance

import com.carthing.data.entity.MaintenanceItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPolicyTest {
    private fun item(notified: Int, enabled: Boolean = true) =
        MaintenanceItem(id = 1, vehicleId = 1, name = "Oil", intervalMonths = 12, notifiedLevel = notified, enabled = enabled)
    private fun status(level: DueLevel) = MaintenanceStatus(level, null, null, null)

    @Test fun notifiesOnReachingDueSoonThenOverdueOnce() {
        assertEquals(ReminderPolicy.Decision(DueLevel.DUE_SOON, 1), ReminderPolicy.decide(item(0), status(DueLevel.DUE_SOON)))
        assertEquals(ReminderPolicy.Decision(null, 1), ReminderPolicy.decide(item(1), status(DueLevel.DUE_SOON)))
        assertEquals(ReminderPolicy.Decision(DueLevel.OVERDUE, 2), ReminderPolicy.decide(item(1), status(DueLevel.OVERDUE)))
        assertEquals(ReminderPolicy.Decision(null, 2), ReminderPolicy.decide(item(2), status(DueLevel.OVERDUE)))
    }

    @Test fun jumpingStraightToOverdueNotifiesOnce() {
        assertEquals(ReminderPolicy.Decision(DueLevel.OVERDUE, 2), ReminderPolicy.decide(item(0), status(DueLevel.OVERDUE)))
    }

    @Test fun droppingBackLowersStoredLevelSoItCanNotifyAgain() {
        assertEquals(ReminderPolicy.Decision(null, 0), ReminderPolicy.decide(item(2), status(DueLevel.OK)))
    }

    @Test fun unknownAndDisabledNeverNotify() {
        assertNull(ReminderPolicy.decide(item(0), status(DueLevel.UNKNOWN)).notify)
        assertNull(ReminderPolicy.decide(item(0, enabled = false), status(DueLevel.OVERDUE)).notify)
    }
}
