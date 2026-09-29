package com.carthing.ui.maintenance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.entity.MaintenanceItem
import com.carthing.ui.common.BackButton
import com.carthing.ui.common.DateInput
import com.carthing.ui.common.DecimalInput
import com.carthing.ui.common.DeleteAction
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

    val kmError get() = if (km.isNotBlank() && (kmValue ?: 0.0) <= 0) "Enter a distance above 0" else null
    val monthsError get() = if (months.isNotBlank() && (monthsValue ?: 0) <= 0) "Enter a whole number of months" else null
    val lastKmError get() = if (lastKm.isNotBlank() && (lastKmValue ?: -1.0) < 0) "Enter a valid reading" else null
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
    var submitted by rememberSaveable { mutableStateOf(false) }
    val inspect = rememberScheduleFields(existing?.inspectKm, existing?.inspectMonths, existing?.lastInspectedEpochMillis, existing?.lastInspectedOdometerKm)
    val replace = rememberScheduleFields(existing?.replaceKm, existing?.replaceMonths, existing?.lastReplacedEpochMillis, existing?.lastReplacedOdometerKm)

    val nameError = if (submitted && name.isBlank()) "Name is required" else null
    val noSchedule = submitted && !inspect.isSet && !replace.isSet

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add component" else "Edit component") },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (existing != null) DeleteAction(
                        "Stop tracking ${existing.name}?",
                        "Its schedule is removed. Service history is kept."
                    ) { scope.launch { viewModel.deleteMaintenanceItem(existing); onDone() } }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextInput(name, { name = it }, "Name *", error = nameError)
            SwitchRow("Track and remind", enabled) { enabled = it }
            Text("Each schedule is due after whichever of its limits comes first. Leave a section empty if it doesn't apply.",
                style = MaterialTheme.typography.bodySmall)
            if (noSchedule) Text("Set at least one inspection or replacement interval",
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

            ScheduleSection("Inspection", inspect)
            ScheduleSection("Replacement", replace)

            Button(
                onClick = {
                    submitted = true
                    if (name.isBlank() || inspect.hasError || replace.hasError || (!inspect.isSet && !replace.isSet)) return@Button
                    val base = existing ?: MaintenanceItem(vehicleId = 0, name = "")
                    val item = base.copy(
                        name = name, enabled = enabled,
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
            ) { Text("Save") }
        }
    }
}

@Composable
private fun ScheduleSection(title: String, fields: ScheduleFields) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
    DecimalInput(fields.km, { fields.km = it }, "Every (km)", fields.kmError)
    TextInput(fields.months, { fields.months = it }, "Every (months)", error = fields.monthsError, keyboardType = KeyboardType.Number)
    val date = fields.lastDate.value
    if (date == null) {
        OutlinedButton(onClick = { fields.lastDate.value = System.currentTimeMillis() }, modifier = Modifier.fillMaxWidth()) {
            Text("Set last ${title.lowercase()} date")
        }
    } else {
        DateInput(date, { fields.lastDate.value = it }, "Last ${title.lowercase()}")
    }
    DecimalInput(fields.lastKm, { fields.lastKm = it }, "Odometer at last ${title.lowercase()} (km)", fields.lastKmError)
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
