package com.carthing.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.ui.common.formatDate

/** Overflow menu with backup export/import, plus the import confirmation and result snackbar. */
@Composable
fun BackupMenu(snackbar: SnackbarHostState, viewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)) {
    var open by remember { mutableStateOf(false) }
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportTo)
    }
    // Some file providers report JSON as text/plain or octet-stream, so accept those too; the content is validated.
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
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            })
        }
    }

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
