package com.carthing.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.carthing.CarThingApp
import com.carthing.data.AppContainer
import com.carthing.data.backup.BackupPolicy
import java.util.concurrent.TimeUnit

/** Daily check: reminders for components and deadlines that are due soon or overdue, and for stale backups. */
class MaintenanceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CarThingApp).container
        container.relabeler.relabelIfLanguageChangedSafely() // a failure here must not block the reminders
        // In the app's language: on Android 12 and older the in-app choice isn't applied to the application context.
        val text = container.languageSettings.wrap(applicationContext)
        container.maintenanceRepository.collectReminders().forEach { MaintenanceNotifier.post(text, it) }
        container.deadlineRepository.collectReminders().forEach { MaintenanceNotifier.post(text, it) }
        remindToBackUp(container, text)
        container.attachmentRepository.sweepOrphans()
        return Result.success()
    }

    private suspend fun remindToBackUp(c: AppContainer, text: Context) {
        val now = System.currentTimeMillis()
        val s = c.backupSettings.state.value
        val hasData = c.vehicleRepository.count() > 0
        if (BackupPolicy.shouldRemind(hasData, s.lastBackupEpochMillis, s.lastReminderEpochMillis, now)) {
            MaintenanceNotifier.postBackupReminder(text, s.lastBackupEpochMillis)
            c.backupSettings.recordReminder(now)
        }
    }

    companion object {
        private const val WORK_NAME = "maintenance-check"

        /** Idempotent: keeps an existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MaintenanceCheckWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Runs the check once now, independent of the daily schedule. */
        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<MaintenanceCheckWorker>().build())
        }
    }
}
