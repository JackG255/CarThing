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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.backup.BackupPolicy
import com.carthing.data.backup.BackupSettings
import com.carthing.ui.common.formatDate

/** Overflow menu with backup export/import, plus the import confirmation and result snackbar. */
@Composable
fun BackupMenu(snackbar: SnackbarHostState, viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
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
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Export backup") }, onClick = {
                open = false
                exportLauncher.launch(viewModel.suggestedFileName())
            })
            DropdownMenuItem(text = { Text("Restore from backup") }, onClick = {
                open = false
                importLauncher.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream"))
            })
            DropdownMenuItem(text = { Text("Automatic backups…") }, onClick = { open = false; showAuto = true })
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
            title = { Text("Replace all data?") },
            text = {
                Text("This backup from ${formatDate(s.exportedAtEpochMillis)} has ${s.vehicles} vehicle(s), " +
                    "${s.fuelEntries} fill-up(s), ${s.serviceEntries} service record(s) and ${s.deadlines} deadline(s).\n\n" +
                    "Everything currently in the app will be replaced. Export a backup first if you might need it.")
            },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("Replace") } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } }
        )
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
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
        title = { Text("Automatic backups") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (on) "Once a week, a backup is saved to “${folderName ?: "the chosen folder"}”. The newest ${BackupPolicy.KEEP_AUTO_BACKUPS} are kept."
                    else "Choose a folder and a backup is saved there every week. Pick a folder your cloud or sync app uploads, so a copy lives off this phone."
                )
                Text("Last backup: ${settings.lastBackupEpochMillis?.let(::formatDate) ?: "never"}", style = MaterialTheme.typography.bodySmall)
                settings.autoError?.let {
                    Text("Last automatic backup failed: $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text("Android also backs the app up to your Google account on its own, if backup is enabled in the phone's settings.",
                    style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            if (on) Row { TextButton(onClick = onChooseFolder) { Text("Change folder") }; TextButton(onClick = onBackUpNow) { Text("Back up now") } }
            else TextButton(onClick = onChooseFolder) { Text("Choose folder") }
        },
        dismissButton = { if (on) TextButton(onClick = onTurnOff) { Text("Turn off") } else TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

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
                Text("Back up your service book", style = MaterialTheme.typography.titleSmall)
                Text("Last backup: ${settings.lastBackupEpochMillis?.let(::formatDate) ?: "never"}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { exportLauncher.launch(viewModel.suggestedFileName()) }) { Text("Back up now") }
        }
    }
}
