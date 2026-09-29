package com.carthing.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.carthing.MainActivity
import com.carthing.R
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.repository.Reminder
import com.carthing.ui.maintenance.describe
import com.carthing.ui.maintenance.noun

object MaintenanceNotifier {
    private const val CHANNEL_ID = "maintenance"
    const val EXTRA_VEHICLE_ID = "com.carthing.extra.VEHICLE_ID"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(CHANNEL_ID, "Maintenance reminders", NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = "When a component is due soon or overdue" }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, reminder: Reminder) {
        if (!canPost(context)) return
        ensureChannel(context)
        val title = when (reminder.level) {
            DueLevel.OVERDUE -> "${reminder.item.name} ${reminder.kind.noun()} is overdue"
            else -> "${reminder.item.name} ${reminder.kind.noun()} is due soon"
        }
        val tap = PendingIntent.getActivity(
            context, reminder.vehicle.id.toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_VEHICLE_ID, reminder.vehicle.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("${reminder.vehicle.name} · ${reminder.status.describe()}")
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        try {
            // One slot per component and schedule, so an inspection reminder doesn't replace a replacement one.
            NotificationManagerCompat.from(context).notify((reminder.item.id * 2 + reminder.kind.ordinal).toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; the Maintenance tab still shows the status.
        }
    }
}
