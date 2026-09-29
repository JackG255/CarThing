package com.carthing.data.backup

import com.carthing.data.maintenance.Usage.Companion.DAY_MILLIS

/** When to nag about backups, and which automatic backup files to keep. */
object BackupPolicy {
    /** A backup older than this (or none at all) counts as stale. */
    const val STALE_DAYS = 30L
    /** While stale, remind again at most this often. */
    const val REMIND_EVERY_DAYS = 14L
    /** Automatic backups kept in the folder; older ones are deleted. */
    const val KEEP_AUTO_BACKUPS = 5
    const val AUTO_PREFIX = "carthing-auto-"

    fun isStale(lastBackup: Long?, now: Long) = lastBackup == null || now - lastBackup > STALE_DAYS * DAY_MILLIS

    /** Only nag when there's something to lose, the backup is stale, and we haven't nagged recently. */
    fun shouldRemind(hasData: Boolean, lastBackup: Long?, lastReminder: Long?, now: Long) =
        hasData && isStale(lastBackup, now) &&
            (lastReminder == null || now - lastReminder >= REMIND_EVERY_DAYS * DAY_MILLIS)

    /**
     * Of the file names in the backup folder, the automatic backups to delete: all but the newest
     * [keep]. Names embed a sortable timestamp; older .json and newer .zip backups count alike, other files are never touched.
     */
    fun autoBackupsToDelete(names: List<String>, keep: Int = KEEP_AUTO_BACKUPS): List<String> =
        names.filter { it.startsWith(AUTO_PREFIX) && (it.endsWith(".zip") || it.endsWith(".json")) }
            .sortedByDescending { it.substringBeforeLast(".") }.drop(keep)
}
