package com.carthing.data.maintenance

/**
 * Decides when to notify about one schedule: once on reaching "due soon", once more on "overdue",
 * never repeating a level. Each schedule and deadline is tracked separately.
 */
object ReminderPolicy {
    data class Decision(
        /** Level to notify about now, or null for no notification. */
        val notify: DueLevel?,
        /** Value to store as the schedule's notified level. */
        val newNotifiedLevel: Int
    )

    fun decide(enabled: Boolean, notifiedLevel: Int, status: MaintenanceStatus): Decision =
        decide(enabled, notifiedLevel, status.level)

    fun decide(enabled: Boolean, notifiedLevel: Int, dueLevel: DueLevel): Decision {
        val level = if (enabled) dueLevel.notifyLevel else 0
        // Storing a lower level (e.g. after the interval was extended) lets a later rise notify again.
        return Decision(notify = dueLevel.takeIf { level > notifiedLevel }, newNotifiedLevel = level)
    }
}
