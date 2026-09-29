package com.carthing.ui.backup

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.carthing.CarThingApp
import com.carthing.data.backup.BackupArchive
import com.carthing.data.backup.BackupRepository
import com.carthing.data.backup.BackupSettings
import com.carthing.data.backup.FolderBackup
import com.carthing.notifications.AutoBackupWorker
import com.carthing.data.backup.BackupSummary
import com.carthing.data.backup.InvalidBackupException
import com.carthing.data.backup.ParsedBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.LocalDate

/** Export and import of the whole database through files the user picks. */
class BackupViewModel(
    app: Application,
    private val backups: BackupRepository,
    private val archive: BackupArchive,
    private val settings: BackupSettings,
    private val folders: FolderBackup,
) : AndroidViewModel(app) {
    val settingsState: StateFlow<BackupSettings.State> = settings.state

    /** A validated file waiting for the user to confirm replacing all data. */
    data class PendingImport(val parsed: ParsedBackup, val summary: BackupSummary)

    private val _pending = MutableStateFlow<PendingImport?>(null)
    val pending: StateFlow<PendingImport?> = _pending

    /** One-off user-facing result messages; cleared by [messageShown]. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun suggestedFileName() = "carthing-backup-${LocalDate.now()}.zip"

    fun exportTo(uri: Uri) = viewModelScope.launch {
        _message.value = try {
            withContext(Dispatchers.IO) {
                resolver().openOutputStream(uri, "wt")?.use { archive.writeTo(it) } ?: throw IOException("Can't open file")
            }
            settings.recordBackup(System.currentTimeMillis())
            "Backup saved"
        } catch (e: IOException) {
            "Couldn't save the backup: ${e.message}"
        }
    }

    /** Reads and validates [uri] (zip, or a JSON backup from before photos); on success asks for confirmation via [pending]. */
    fun prepareImport(uri: Uri) = viewModelScope.launch {
        try {
            val parsed = withContext(Dispatchers.IO) {
                val bytes = resolver().openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Can't open file")
                archive.read(bytes)
            }
            _pending.value = PendingImport(parsed, backups.summarize(parsed.file))
        } catch (e: InvalidBackupException) {
            _message.value = e.message
        } catch (e: IOException) {
            _message.value = "Couldn't read the file: ${e.message}"
        }
    }

    fun confirmImport() = viewModelScope.launch {
        val p = _pending.value ?: return@launch
        _pending.value = null
        withContext(Dispatchers.IO) { archive.restore(p.parsed) }
        _message.value = "Backup restored"
    }

    /** Turns on weekly backups into [tree], keeping access to it across restarts, and backs up right away. */
    fun enableAutoBackup(tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver().takePersistableUriPermission(tree, flags)
        } catch (_: SecurityException) {
            // Some storage apps (often cloud ones) don't allow keeping access to a folder.
            _message.value = "That folder can't be used for automatic backups. Try a folder on the phone that a sync app uploads."
            return
        }
        settings.state.value.autoFolder?.takeIf { it != tree }?.let { old -> runCatching { resolver().releasePersistableUriPermission(old, flags) } }
        settings.setAutoFolder(tree)
        AutoBackupWorker.schedule(getApplication())
        AutoBackupWorker.runOnce(getApplication())
        _message.value = "Automatic backups on"
    }

    fun disableAutoBackup() {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        settings.state.value.autoFolder?.let { runCatching { resolver().releasePersistableUriPermission(it, flags) } }
        settings.setAutoFolder(null)
        AutoBackupWorker.cancel(getApplication())
        _message.value = "Automatic backups off"
    }

    fun runAutoBackupNow() {
        AutoBackupWorker.runOnce(getApplication())
        _message.value = "Backing up…"
    }

    fun folderName(tree: Uri): String? = folders.folderName(tree)

    fun cancelImport() { _pending.value = null }
    fun messageShown() { _message.value = null }

    private fun resolver() = getApplication<Application>().contentResolver

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as CarThingApp
                val c = app.container
                BackupViewModel(app, c.backupRepository, c.backupArchive, c.backupSettings, c.folderBackup)
            }
        }
    }
}
