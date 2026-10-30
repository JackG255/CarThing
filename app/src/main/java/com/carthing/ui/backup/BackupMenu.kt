package com.carthing.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.backup.BackupPolicy
import com.carthing.R
import com.carthing.data.backup.AutoBackupError
import com.carthing.data.backup.BackupSettings
import com.carthing.ui.common.formatDate
import com.carthing.ui.common.resolve

/** Overflow menu with backup export/import, plus the import confirmation and result snackbar. */
@Composable
fun BackupMenu(
    snackbar: SnackbarHostState,
    viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory),
    /** More items after the backup ones; called with a function that closes the menu. */
    extraItems: @Composable (close: () -> Unit) -> Unit = {},
) {
    var open by remember { mutableStateOf(false) }
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    var showAuto by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let(viewModel::enableAutoBackup)
    }
    // Zip for current backups, JSON for older ones; some providers report either as text/plain or octet-stream. The content is validated.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::prepareImport)
    }

    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.backup_more_options)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.backup_menu_export)) }, onClick = {
                open = false
                exportLauncher.launch(viewModel.suggestedFileName())
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.backup_menu_restore)) }, onClick = {
                open = false
                importLauncher.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream"))
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.backup_menu_auto)) }, onClick = { open = false; showAuto = true })
            extraItems { open = false }
        }
    }

    if (showAuto) AutoBackupDialog(
        settings, folderName = settings.autoFolder?.let(viewModel::folderName),
        onDismiss = { showAuto = false },
        onChooseFolder = { showAuto = false; folderLauncher.launch(null) },
        onTurnOff = { showAuto = false; viewModel.disableAutoBackup() },
        onBackUpNow = { showAuto = false; viewModel.runAutoBackupNow() },
    )

    pending?.let { p ->
        val s = p.summary
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = {
                Text(stringResource(
                    R.string.backup_restore_body,
                    formatDate(s.exportedAtEpochMillis),
                    pluralStringResource(R.plurals.backup_count_vehicles, s.vehicles, s.vehicles),
                    pluralStringResource(R.plurals.backup_count_fuel_entries, s.fuelEntries, s.fuelEntries),
                    pluralStringResource(R.plurals.backup_count_service_entries, s.serviceEntries, s.serviceEntries),
                    pluralStringResource(R.plurals.backup_count_deadlines, s.deadlines, s.deadlines),
                ))
            },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text(stringResource(R.string.backup_restore_confirm)) } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text(stringResource(R.string.action_cancel)) } }
        )
    }

    val resources = LocalContext.current.resources
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it.resolve(resources))
            viewModel.messageShown()
        }
    }
}

@Composable
private fun AutoBackupDialog(
    settings: BackupSettings.State,
    folderName: String?,
    onDismiss: () -> Unit,
    onChooseFolder: () -> Unit,
    onTurnOff: () -> Unit,
    onBackUpNow: () -> Unit,
) {
    val on = settings.autoFolder != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_auto_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (on) {
                    Text(
                        if (folderName != null) stringResource(R.string.backup_auto_on_folder, folderName)
                        else stringResource(R.string.backup_auto_on_folder_unknown)
                    )
                    Text(pluralStringResource(R.plurals.backup_auto_keep, BackupPolicy.KEEP_AUTO_BACKUPS, BackupPolicy.KEEP_AUTO_BACKUPS))
                } else Text(stringResource(R.string.backup_auto_off_hint))
                Text(lastBackupText(settings.lastBackupEpochMillis), style = MaterialTheme.typography.bodySmall)
                settings.autoError?.let {
                    Text(stringResource(it.messageRes), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(stringResource(R.string.backup_auto_android_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (on) Row {
                TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.backup_auto_change_folder)) }
                TextButton(onClick = onBackUpNow) { Text(stringResource(R.string.backup_action_back_up_now)) }
            } else TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.backup_auto_choose_folder)) }
        },
        dismissButton = {
            if (on) TextButton(onClick = onTurnOff) { Text(stringResource(R.string.backup_auto_turn_off)) }
            else TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private val AutoBackupError.messageRes
    get() = when (this) {
        AutoBackupError.FOLDER_UNAVAILABLE -> R.string.backup_auto_failed_folder
        AutoBackupError.OTHER -> R.string.backup_auto_failed_other
    }

/** "Last backup: 12 Mar 2026", or "never" when [epochMillis] is null. */
@Composable
private fun lastBackupText(epochMillis: Long?) =
    if (epochMillis != null) stringResource(R.string.backup_last, formatDate(epochMillis)) else stringResource(R.string.backup_last_never)

/** Nudge shown at the top of a non-empty vehicle list while the last backup is stale. */
@Composable
fun BackupBanner(viewModel: BackupViewModel) {
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.backup_reminder_title), style = MaterialTheme.typography.titleSmall)
                Text(lastBackupText(settings.lastBackupEpochMillis), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { exportLauncher.launch(viewModel.suggestedFileName()) }) { Text(stringResource(R.string.backup_action_back_up_now)) }
        }
    }
}
