package com.carthing.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.carthing.MainActivity
import com.carthing.R
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.data.repository.DeadlineReminder
import com.carthing.data.repository.Reminder
import com.carthing.ui.common.formatDate
import com.carthing.ui.deadlines.describeExpiry
import com.carthing.ui.maintenance.describe

object MaintenanceNotifier {
    private const val CHANNEL_ID = "maintenance"
    const val EXTRA_VEHICLE_ID = "com.carthing.extra.VEHICLE_ID"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // Runs on every app start and before each post, so the name and description follow a language change.
        val res = context.resources
        val channel = NotificationChannel(CHANNEL_ID, res.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = res.getString(R.string.notification_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** E.g. "Brakes replacement is overdue"; any [level] other than OVERDUE reads as due soon. */
    fun reminderTitle(res: Resources, name: String, kind: ScheduleKind, level: DueLevel): String = res.getString(
        when {
            level == DueLevel.OVERDUE && kind == ScheduleKind.INSPECTION -> R.string.reminder_inspection_overdue
            level == DueLevel.OVERDUE -> R.string.reminder_replacement_overdue
            kind == ScheduleKind.INSPECTION -> R.string.reminder_inspection_due_soon
            else -> R.string.reminder_replacement_due_soon
        },
        name
    )

    fun post(context: Context, reminder: Reminder) {
        val res = context.resources
        // One slot per component and schedule, so an inspection reminder doesn't replace a replacement one.
        show(context, itemNotificationId(reminder.item.id, reminder.kind), reminder.vehicle.id,
            reminderTitle(res, reminder.item.name, reminder.kind, reminder.level),
            "${reminder.vehicle.name} · ${reminder.status.describe(res)}")
    }

    fun post(context: Context, reminder: DeadlineReminder) {
        // Negative ids keep deadlines apart from maintenance notifications.
        show(context, deadlineNotificationId(reminder.deadline.id), reminder.vehicle.id,
            context.resources.getString(R.string.reminder_deadline, reminder.deadline.title, reminder.status.describeExpiry(context.resources)),
            reminder.vehicle.name)
    }

    fun postBackupReminder(context: Context, lastBackupEpochMillis: Long?) {
        val res = context.resources
        val text = lastBackupEpochMillis?.let { res.getString(R.string.backup_reminder_last, formatDate(it)) }
            ?: res.getString(R.string.backup_reminder_never)
        show(context, BACKUP_NOTIFICATION_ID, vehicleId = null, res.getString(R.string.backup_reminder_title), text)
    }

    private const val BACKUP_NOTIFICATION_ID = Int.MAX_VALUE

    fun itemNotificationId(itemId: Long, kind: ScheduleKind) = (itemId * 2 + kind.ordinal).toInt()
    fun deadlineNotificationId(deadlineId: Long) = -deadlineId.toInt()

    /** [vehicleId] null opens the vehicle list. */
    private fun show(context: Context, notificationId: Int, vehicleId: Long?, title: String, text: String) {
        if (!canPost(context)) return
        ensureChannel(context)
        val tap = PendingIntent.getActivity(
            context, vehicleId?.toInt() ?: 0,
            Intent(context, MainActivity::class.java)
                .apply { vehicleId?.let { putExtra(EXTRA_VEHICLE_ID, it) } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; the Maintenance tab still shows the status.
        }
    }
}
