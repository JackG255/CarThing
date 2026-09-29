package com.carthing.data.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Backup bookkeeping and the automatic-backup folder. Excluded from Android's cloud backup. */
class BackupSettings(context: Context) {
    data class State(
        /** Last successful export, manual or automatic; null if never. */
        val lastBackupEpochMillis: Long? = null,
        /** Folder for automatic backups (a persisted document-tree URI); null when off. */
        val autoFolder: Uri? = null,
        /** Why the last automatic backup failed; cleared on success. */
        val autoError: String? = null,
        val lastReminderEpochMillis: Long? = null,
    )

    private val prefs = context.getSharedPreferences("backup_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<State> = _state

    fun recordBackup(epochMillis: Long) = update { it.copy(lastBackupEpochMillis = epochMillis, autoError = null) }
    fun recordAutoError(message: String) = update { it.copy(autoError = message) }
    fun recordReminder(epochMillis: Long) = update { it.copy(lastReminderEpochMillis = epochMillis) }
    fun setAutoFolder(uri: Uri?) = update { it.copy(autoFolder = uri, autoError = null) }

    private fun load() = State(
        lastBackupEpochMillis = prefs.getLong(KEY_LAST_BACKUP, -1).takeIf { it >= 0 },
        autoFolder = prefs.getString(KEY_FOLDER, null)?.let(Uri::parse),
        autoError = prefs.getString(KEY_ERROR, null),
        lastReminderEpochMillis = prefs.getLong(KEY_LAST_REMINDER, -1).takeIf { it >= 0 },
    )

    @Synchronized
    private fun update(change: (State) -> State) {
        val s = change(_state.value)
        prefs.edit()
            .putLong(KEY_LAST_BACKUP, s.lastBackupEpochMillis ?: -1)
            .putString(KEY_FOLDER, s.autoFolder?.toString())
            .putString(KEY_ERROR, s.autoError)
            .putLong(KEY_LAST_REMINDER, s.lastReminderEpochMillis ?: -1)
            .apply()
        _state.value = s
    }

    private companion object {
        const val KEY_LAST_BACKUP = "last_backup"
        const val KEY_FOLDER = "auto_folder"
        const val KEY_ERROR = "auto_error"
        const val KEY_LAST_REMINDER = "last_reminder"
    }
}
