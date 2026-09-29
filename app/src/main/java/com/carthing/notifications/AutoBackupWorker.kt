package com.carthing.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.carthing.CarThingApp
import java.util.concurrent.TimeUnit

/** Weekly export into the folder chosen under "Automatic backups". */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as CarThingApp).container
        val folder = c.backupSettings.state.value.autoFolder ?: return Result.success()
        return try {
            c.folderBackup.write(folder, c.backupRepository.export())
            c.backupSettings.recordBackup(System.currentTimeMillis())
            Result.success()
        } catch (e: Exception) {
            // Typically the folder was deleted or its permission revoked. The backup reminder
            // takes over once the last good backup goes stale.
            c.backupSettings.recordAutoError(e.message ?: e.javaClass.simpleName)
            Result.success()
        }
    }

    companion object {
        private const val WORK_NAME = "auto-backup"

        /** Replaces any existing weekly schedule; call [runOnce] as well for an immediate backup. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)

        fun runOnce(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<AutoBackupWorker>().build())
        }
    }
}
