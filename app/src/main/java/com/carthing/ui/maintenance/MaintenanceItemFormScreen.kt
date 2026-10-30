package com.carthing.ui.maintenance

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.R
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.ui.common.BackButton
import com.carthing.ui.common.DateInput
import com.carthing.ui.common.DecimalInput
import com.carthing.ui.common.DeleteAction
import com.carthing.ui.common.IconBadge
import com.carthing.ui.common.LoadingBox
import com.carthing.ui.common.TextInput
import com.carthing.ui.common.editableNumber
import com.carthing.ui.common.parseDecimal
import com.carthing.ui.vehicles.VehicleDetailUiState
import com.carthing.ui.vehicles.VehicleDetailViewModel
import kotlinx.coroutines.launch

/** Add ([itemId] = 0) or edit a tracked component. */
@Composable
fun MaintenanceItemFormScreen(
    vehicleId: Long,
    itemId: Long,
    onDone: () -> Unit,
    viewModel: VehicleDetailViewModel = viewModel(factory = VehicleDetailViewModel.factory(vehicleId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state as? VehicleDetailUiState.Loaded ?: return LoadingBox()
    val existing = remember { s.maintenance.firstOrNull { it.item.id == itemId }?.item }
    MaintenanceItemForm(existing, viewModel, onDone)
}

/** Editable text state for one schedule section; each field survives rotation. */
private class ScheduleFields(
    private val kmState: MutableState<String>,
    private val monthsState: MutableState<String>,
    val lastDate: MutableState<Long?>,
    private val lastKmState: MutableState<String>,
) {
    var km by kmState
    var months by monthsState
    var lastKm by lastKmState

    val kmValue get() = parseDecimal(km)
    val monthsValue get() = months.trim().toIntOrNull()
    val lastKmValue get() = parseDecimal(lastKm)
    val isSet get() = km.isNotBlank() || months.isNotBlank()

    /** Each field's error as a string resource, or null when it's fine. */
    @get:StringRes val kmError get() = if (km.isNotBlank() && (kmValue ?: 0.0) <= 0) R.string.maintenance_error_distance else null
    @get:StringRes val monthsError get() = if (months.isNotBlank() && (monthsValue ?: 0) <= 0) R.string.form_error_whole_months else null
    @get:StringRes val lastKmError get() = if (lastKm.isNotBlank() && (lastKmValue ?: -1.0) < 0) R.string.maintenance_error_reading else null
    val hasError get() = kmError != null || monthsError != null || lastKmError != null
}

@Composable
private fun rememberScheduleFields(km: Double?, months: Int?, lastDate: Long?, lastKm: Double?) = ScheduleFields(
    rememberSaveable { mutableStateOf(editableNumber(km)) },
    rememberSaveable { mutableStateOf(months?.toString().orEmpty()) },
    rememberSaveable { mutableStateOf(lastDate) },
    rememberSaveable { mutableStateOf(editableNumber(lastKm)) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceItemForm(existing: MaintenanceItem?, viewModel: VehicleDetailViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    var icon by rememberSaveable { mutableStateOf(existing?.icon) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val inspect = rememberScheduleFields(existing?.inspectKm, existing?.inspectMonths, existing?.lastInspectedEpochMillis, existing?.lastInspectedOdometerKm)
    val replace = rememberScheduleFields(existing?.replaceKm, existing?.replaceMonths, existing?.lastReplacedEpochMillis, existing?.lastReplacedOdometerKm)

    val nameError = if (submitted && name.isBlank()) stringResource(R.string.form_name_required) else null
    val noSchedule = submitted && !inspect.isSet && !replace.isSet

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.detail_add_component else R.string.maintenance_form_edit)) },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (existing != null) DeleteAction(
                        stringResource(R.string.maintenance_delete_title, existing.name),
                        stringResource(R.string.maintenance_delete_message)
                    ) { scope.launch { viewModel.deleteMaintenanceItem(existing); onDone() } }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextInput(name, { name = it }, stringResource(R.string.form_name), error = nameError)
            IconField(icon, name) { icon = it }
            SwitchRow(stringResource(R.string.maintenance_track), enabled) { enabled = it }
            Text(stringResource(R.string.maintenance_form_hint), style = MaterialTheme.typography.bodySmall)
            if (noSchedule) Text(stringResource(R.string.maintenance_error_no_schedule),
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            ScheduleSection(ScheduleKind.INSPECTION, inspect)
            ScheduleSection(ScheduleKind.REPLACEMENT, replace)

            Button(
                onClick = {
                    submitted = true
                    if (name.isBlank() || inspect.hasError || replace.hasError || (!inspect.isSet && !replace.isSet)) return@Button
                    val base = existing ?: MaintenanceItem(vehicleId = 0, name = "")
                    val item = base.copy(
                        name = name, enabled = enabled, icon = icon,
                        inspectKm = inspect.kmValue, inspectMonths = inspect.monthsValue,
                        lastInspectedEpochMillis = inspect.lastDate.value, lastInspectedOdometerKm = inspect.lastKmValue,
                        replaceKm = replace.kmValue, replaceMonths = replace.monthsValue,
                        lastReplacedEpochMillis = replace.lastDate.value, lastReplacedOdometerKm = replace.lastKmValue,
                    )
                    // A changed schedule starts a new reminder cycle for it.
                    val saved = item.copy(
                        inspectNotifiedLevel = if (item.inspection == base.inspection) base.inspectNotifiedLevel else 0,
                        replaceNotifiedLevel = if (item.replacement == base.replacement) base.replaceNotifiedLevel else 0,
                    )
                    scope.launch { viewModel.saveMaintenanceItem(saved); onDone() }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}

@Composable
private fun ScheduleSection(kind: ScheduleKind, fields: ScheduleFields) {
    val inspection = kind == ScheduleKind.INSPECTION
    Text(stringResource(if (inspection) R.string.maintenance_section_inspection else R.string.maintenance_section_replacement),
        style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    DecimalInput(fields.km, { fields.km = it }, stringResource(R.string.maintenance_every_km), fields.kmError?.let { stringResource(it) })
    TextInput(fields.months, { fields.months = it }, stringResource(R.string.maintenance_every_months),
        error = fields.monthsError?.let { stringResource(it) }, keyboardType = KeyboardType.Number)
    val date = fields.lastDate.value
    if (date == null) {
        OutlinedButton(onClick = { fields.lastDate.value = System.currentTimeMillis() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (inspection) R.string.maintenance_set_last_inspection else R.string.maintenance_set_last_replacement))
        }
    } else {
        DateInput(date, { fields.lastDate.value = it },
            stringResource(if (inspection) R.string.maintenance_last_inspection else R.string.maintenance_last_replacement),
            onClear = { fields.lastDate.value = null })
    }
    DecimalInput(fields.lastKm, { fields.lastKm = it },
        stringResource(if (inspection) R.string.maintenance_odometer_last_inspection else R.string.maintenance_odometer_last_replacement),
        fields.lastKmError?.let { stringResource(it) })
    if (fields.km.isNotBlank() && fields.lastDate.value != null && fields.lastKm.isBlank()) {
        Text(stringResource(R.string.maintenance_km_missing_note),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Shows the component's icon; tapping opens a grid to pick one, or "Automatic" (from the name). */
@Composable
private fun IconField(key: String?, name: String, onPick: (String?) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val chosen = ComponentIcons.byKey(key)
    val shown = chosen ?: ComponentIcons.guess(name)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable { picking = true }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(shown.vector, size = 48.dp, container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer)
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(stringResource(R.string.maintenance_icon), style = MaterialTheme.typography.labelMedium)
            val label = stringResource(shown.label)
            Text(if (chosen == null) stringResource(R.string.maintenance_icon_automatic_named, label) else label, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = { picking = true }) { Text(stringResource(R.string.maintenance_icon_change)) }
    }
    if (picking) AlertDialog(
        onDismissRequest = { picking = false },
        title = { Text(stringResource(R.string.maintenance_icon_choose)) },
        text = {
            LazyVerticalGrid(columns = GridCells.Adaptive(76.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item { IconCell(ComponentIcons.guess(name).vector, stringResource(R.string.maintenance_icon_automatic), selected = key == null) { onPick(null); picking = false } }
                items(ComponentIcons.all, key = { it.key }) { option ->
                    IconCell(option.vector, stringResource(option.label), selected = option.key == key) { onPick(option.key); picking = false }
                }
            }
        },
        confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun IconCell(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier.clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp)
            .semantics { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBadge(icon, container = if (selected) colors.primary else colors.surfaceVariant, content = if (selected) colors.onPrimary else colors.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
    }
}
