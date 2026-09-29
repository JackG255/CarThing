package com.carthing.notifications

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.carthing.data.maintenance.ScheduleKind

/** Removes posted reminders that no longer apply (deleted, done or renewed). Ids match [MaintenanceNotifier]. */
class ReminderNotifications(context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    fun cancelItem(itemId: Long) = ScheduleKind.entries.forEach { manager.cancel(MaintenanceNotifier.itemNotificationId(itemId, it)) }
    fun cancelDeadline(deadlineId: Long) = manager.cancel(MaintenanceNotifier.deadlineNotificationId(deadlineId))

    fun cancelVehicle(itemIds: List<Long>, deadlineIds: List<Long>) {
        itemIds.forEach(::cancelItem)
        deadlineIds.forEach(::cancelDeadline)
    }
}
