package com.carthing.data.maintenance

import com.carthing.data.entity.MaintenanceItem

/** Decides when to notify: once on reaching "due soon", once more on "overdue", never repeating a level. */
object ReminderPolicy {
    data class Decision(
        /** Level to notify about now, or null for no notification. */
        val notify: DueLevel?,
        /** Value to store in [MaintenanceItem.notifiedLevel]. */
        val newNotifiedLevel: Int
    )

    fun decide(item: MaintenanceItem, status: MaintenanceStatus): Decision {
        val level = if (item.enabled) status.level.notifyLevel else 0
        // Storing a lower level (e.g. after the interval was extended) lets a later rise notify again.
        return Decision(notify = status.level.takeIf { level > item.notifiedLevel }, newNotifiedLevel = level)
    }
}
