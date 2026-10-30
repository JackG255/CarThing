package com.carthing.ui.maintenance

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.carthing.R
import com.carthing.data.entity.Deadline
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.maintenance.UrgencyBand
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.data.repository.DeadlineWithStatus
import com.carthing.data.repository.ItemWithStatus
import com.carthing.notifications.MaintenanceNotifier
import com.carthing.ui.deadlines.RenewDialog
import com.carthing.ui.deadlines.deadlinesSection
import com.carthing.ui.common.DateInput
import com.carthing.ui.common.DecimalInput
import com.carthing.ui.common.IconBadge
import com.carthing.ui.common.editableNumber
import com.carthing.ui.common.parseDecimal
import com.carthing.ui.common.urgencyColor

@Composable
fun MaintenanceTab(
    items: List<ItemWithStatus>,
    currentOdometerKm: Double,
    onEdit: (Long) -> Unit,
    onRecord: (MaintenanceItem, ScheduleKind, epochMillis: Long, odometerKm: Double, cost: Double?) -> Unit,
    deadlines: List<DeadlineWithStatus>,
    onAddDeadline: () -> Unit,
    onEditDeadline: (Long) -> Unit,
    onRenewDeadline: (Deadline, newDueEpochMillis: Long) -> Unit,
) {
    RequestNotificationPermissionOnce()
    var recording by remember { mutableStateOf<MaintenanceItem?>(null) }
    var renewing by remember { mutableStateOf<Deadline?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        deadlinesSection(deadlines, onAddDeadline, onEditDeadline, onRenew = { renewing = it })
        items(items, key = { it.item.id }) { (item, status) ->
            ListItem(
                leadingContent = {
                    // Tinted by urgency, so an overdue part stands out before reading anything.
                    val tint = if (item.enabled) urgencyColor(UrgencyBand.of(status.level, status.primary?.fractionRemaining))
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    IconBadge(ComponentIcons.forItem(item).vector, container = tint.copy(alpha = 0.16f), content = tint)
                },
                headlineContent = { Text(item.name) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        item.inspection?.let { Text(it.describe(ScheduleKind.INSPECTION), style = MaterialTheme.typography.bodySmall) }
                        item.replacement?.let { Text(it.describe(ScheduleKind.REPLACEMENT), style = MaterialTheme.typography.bodySmall) }
                        if (item.enabled) StatusChip(UrgencyBand.of(status.level, status.primary?.fractionRemaining), status.describe())
                        else Text(stringResource(R.string.maintenance_not_tracked))
                    }
                },
                trailingContent = {
                    if (item.enabled) TextButton(onClick = { recording = item }) { Text(stringResource(recordLabel(item))) }
                },
                modifier = Modifier.clickable { onEdit(item.id) }
            )
            HorizontalDivider()
        }
    }

    renewing?.let { deadline ->
        RenewDialog(deadline, onDismiss = { renewing = null }) { due -> renewing = null; onRenewDeadline(deadline, due) }
    }
    recording?.let { item ->
        RecordDialog(item, currentOdometerKm, onDismiss = { recording = null }) { kind, date, odo, cost ->
            recording = null
            onRecord(item, kind, date, odo, cost)
        }
    }
}

@StringRes
private fun recordLabel(item: MaintenanceItem) = when {
    item.inspection != null && item.replacement != null -> R.string.maintenance_record
    item.inspection != null -> R.string.maintenance_inspected
    else -> R.string.maintenance_replaced
}

@Composable
private fun StatusChip(band: UrgencyBand, text: String) {
    val color = urgencyColor(band)
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
                            ) { Text(stringResource(if (k == ScheduleKind.INSPECTION) R.string.maintenance_inspected else R.string.maintenance_replaced)) }
                        }
                    }
                }
                Text(
                    stringResource(if (kind == ScheduleKind.INSPECTION) R.string.maintenance_record_inspected_note else R.string.maintenance_record_replaced_note),
                    style = MaterialTheme.typography.bodyMedium
                )
                DateInput(date, { date = it }, stringResource(R.string.form_date))
                DecimalInput(odometer, { odometer = it }, stringResource(R.string.odometer_input_label),
                    error = if (odo == null || odo < 0) stringResource(R.string.detail_odometer_required) else null)
                if (kind == ScheduleKind.REPLACEMENT) {
                    DecimalInput(cost, { cost = it }, stringResource(R.string.service_field_cost),
                        error = if (costInvalid) stringResource(R.string.service_error_cost_invalid) else null)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = odo != null && odo >= 0 && !costInvalid,
                onClick = { onConfirm(kind, date, odo!!, costValue.takeIf { kind == ScheduleKind.REPLACEMENT }) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
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
