package com.carthing.ui.maintenance

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.data.repository.ItemWithStatus
import com.carthing.notifications.MaintenanceNotifier
import com.carthing.ui.common.DateInput
import com.carthing.ui.common.DecimalInput
import com.carthing.ui.common.editableNumber
import com.carthing.ui.common.parseDecimal

@Composable
fun MaintenanceTab(
    items: List<ItemWithStatus>,
    currentOdometerKm: Double,
    onEdit: (Long) -> Unit,
    onRecord: (MaintenanceItem, ScheduleKind, epochMillis: Long, odometerKm: Double, cost: Double?) -> Unit
) {
    RequestNotificationPermissionOnce()
    var recording by remember { mutableStateOf<MaintenanceItem?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(items, key = { it.item.id }) { (item, status) ->
            ListItem(
                headlineContent = { Text(item.name) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.inspection?.let { Text(it.describe(ScheduleKind.INSPECTION), style = MaterialTheme.typography.bodySmall) }
                        item.replacement?.let { Text(it.describe(ScheduleKind.REPLACEMENT), style = MaterialTheme.typography.bodySmall) }
                        if (item.enabled) StatusChip(status.level, status.describe()) else Text("Not tracked")
                    }
                },
                trailingContent = {
                    if (item.enabled) TextButton(onClick = { recording = item }) { Text(recordLabel(item)) }
                },
                modifier = Modifier.clickable { onEdit(item.id) }
            )
            HorizontalDivider()
        }
    }

    recording?.let { item ->
        RecordDialog(item, currentOdometerKm, onDismiss = { recording = null }) { kind, date, odo, cost ->
            recording = null
            onRecord(item, kind, date, odo, cost)
        }
    }
}

private fun recordLabel(item: MaintenanceItem) = when {
    item.inspection != null && item.replacement != null -> "Record"
    item.inspection != null -> "Inspected"
    else -> "Replaced"
}

@Composable
private fun StatusChip(level: DueLevel, text: String) {
    val color = when (level) {
        DueLevel.OVERDUE -> MaterialTheme.colorScheme.error
        DueLevel.DUE_SOON -> MaterialTheme.colorScheme.tertiary
        DueLevel.OK -> MaterialTheme.colorScheme.primary
        DueLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(labelColor = color),
        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = color.copy(alpha = 0.5f))
    )
}

/**
 * Records an inspection or a replacement. Components with only one schedule skip the choice;
 * one with only an inspection schedule can still be recorded as replaced (e.g. worn brake pads).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDialog(
    item: MaintenanceItem,
    currentOdometerKm: Double,
    onDismiss: () -> Unit,
    onConfirm: (ScheduleKind, epochMillis: Long, odometerKm: Double, cost: Double?) -> Unit
) {
    val canInspect = item.inspection != null
    var kind by rememberSaveable { mutableStateOf(if (canInspect) ScheduleKind.INSPECTION else ScheduleKind.REPLACEMENT) }
    var date by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var odometer by rememberSaveable { mutableStateOf(editableNumber(currentOdometerKm)) }
    var cost by rememberSaveable { mutableStateOf("") }
    val odo = parseDecimal(odometer)
    val costValue = parseDecimal(cost)
    val costInvalid = kind == ScheduleKind.REPLACEMENT && cost.isNotBlank() && costValue == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canInspect) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        ScheduleKind.entries.forEachIndexed { i, k ->
                            SegmentedButton(
                                selected = kind == k, onClick = { kind = k },
                                shape = SegmentedButtonDefaults.itemShape(i, ScheduleKind.entries.size)
                            ) { Text(if (k == ScheduleKind.INSPECTION) "Inspected" else "Replaced") }
                        }
                    }
                }
                Text(
                    if (kind == ScheduleKind.INSPECTION) "Only the inspection schedule restarts. Nothing is added to the service history."
                    else "Added to the service history. Both schedules restart, since a new part needs no inspection yet.",
                    style = MaterialTheme.typography.bodyMedium
                )
                DateInput(date, { date = it }, "Date")
                DecimalInput(odometer, { odometer = it }, "Odometer (km)",
                    error = if (odo == null || odo < 0) "Enter the odometer reading" else null)
                if (kind == ScheduleKind.REPLACEMENT) {
                    DecimalInput(cost, { cost = it }, "Cost", error = if (costInvalid) "Enter a valid cost" else null)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = odo != null && odo >= 0 && !costInvalid,
                onClick = { onConfirm(kind, date, odo!!, costValue.takeIf { kind == ScheduleKind.REPLACEMENT }) }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Android 13+ needs runtime permission for notifications; ask once per screen visit if not granted. */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (!asked && !MaintenanceNotifier.canPost(context)) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
