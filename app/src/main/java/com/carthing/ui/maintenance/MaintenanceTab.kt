package com.carthing.ui.maintenance

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
    onMarkDone: (MaintenanceItem, epochMillis: Long, odometerKm: Double, cost: Double?) -> Unit
) {
    RequestNotificationPermissionOnce()
    var marking by remember { mutableStateOf<MaintenanceItem?>(null) }

    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(items, key = { it.item.id }) { (item, status) ->
            ListItem(
                headlineContent = { Text(item.name) },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(item.describeSchedule(), style = MaterialTheme.typography.bodySmall)
                        if (item.enabled) StatusChip(status.level, status.describe()) else Text("Not tracked")
                    }
                },
                trailingContent = {
                    if (item.enabled) TextButton(onClick = { marking = item }) { Text(if (item.isCheck) "Checked" else "Done") }
                },
                modifier = Modifier.clickable { onEdit(item.id) }
            )
            HorizontalDivider()
        }
    }

    marking?.let { item ->
        MarkDoneDialog(item, currentOdometerKm, onDismiss = { marking = null }) { date, odo, cost ->
            marking = null
            onMarkDone(item, date, odo, cost)
        }
    }
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

@Composable
private fun MarkDoneDialog(
    item: MaintenanceItem,
    currentOdometerKm: Double,
    onDismiss: () -> Unit,
    onConfirm: (epochMillis: Long, odometerKm: Double, cost: Double?) -> Unit
) {
    var date by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var odometer by rememberSaveable { mutableStateOf(editableNumber(currentOdometerKm)) }
    var cost by rememberSaveable { mutableStateOf("") }
    val odo = parseDecimal(odometer)
    val costValue = parseDecimal(cost)
    val costInvalid = cost.isNotBlank() && costValue == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (item.isCheck) "Record that this was checked. The next check is scheduled from here."
                    else "Record the service. It's added to the service history and the interval restarts.",
                    style = MaterialTheme.typography.bodyMedium
                )
                DateInput(date, { date = it }, "Date")
                DecimalInput(odometer, { odometer = it }, "Odometer (km)",
                    error = if (odo == null || odo < 0) "Enter the odometer reading" else null)
                if (!item.isCheck) DecimalInput(cost, { cost = it }, "Cost", error = if (costInvalid) "Enter a valid cost" else null)
            }
        },
        confirmButton = {
            TextButton(
                enabled = odo != null && odo >= 0 && !costInvalid,
                onClick = { onConfirm(date, odo!!, costValue) }
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
