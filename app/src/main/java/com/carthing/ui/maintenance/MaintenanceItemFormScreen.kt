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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MaintenanceItemForm(existing: MaintenanceItem?, viewModel: VehicleDetailViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var km by rememberSaveable { mutableStateOf(editableNumber(existing?.intervalKm)) }
    var months by rememberSaveable { mutableStateOf(existing?.intervalMonths?.toString().orEmpty()) }
    var isCheck by rememberSaveable { mutableStateOf(existing?.isCheck ?: false) }
    var enabled by rememberSaveable { mutableStateOf(existing?.enabled ?: true) }
    var lastDate by rememberSaveable { mutableStateOf(existing?.lastDoneEpochMillis) }
    var lastKm by rememberSaveable { mutableStateOf(editableNumber(existing?.lastDoneOdometerKm)) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val kmValue = parseDecimal(km)
    val monthsValue = months.trim().toIntOrNull()
    val lastKmValue = parseDecimal(lastKm)
    val nameError = if (submitted && name.isBlank()) "Name is required" else null
    val kmError = if (km.isNotBlank() && (kmValue == null || kmValue <= 0)) "Enter a distance above 0" else null
    val monthsError = when {
        months.isNotBlank() && (monthsValue == null || monthsValue <= 0) -> "Enter a whole number of months"
        submitted && km.isBlank() && months.isBlank() -> "Set a distance, a time interval, or both"
        else -> null
    }
    val lastKmError = if (lastKm.isNotBlank() && (lastKmValue == null || lastKmValue < 0)) "Enter a valid reading" else null

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
            Text("Due after whichever comes first:", style = MaterialTheme.typography.labelLarge)
            DecimalInput(km, { km = it }, "Every (km)", kmError)
            TextInput(months, { months = it }, "Every (months)", error = monthsError, keyboardType = KeyboardType.Number)
            SwitchRow("Inspection only (don't add to service history)", isCheck) { isCheck = it }
            SwitchRow("Track and remind", enabled) { enabled = it }

            Text("Last done", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            val date = lastDate
            if (date == null) {
                OutlinedButton(onClick = { lastDate = System.currentTimeMillis() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Set last done date")
                }
            } else {
                DateInput(date, { lastDate = it }, "Date")
            }
            DecimalInput(lastKm, { lastKm = it }, "Odometer (km)", lastKmError)

            Button(
                onClick = {
                    submitted = true
                    if (name.isBlank() || kmError != null || lastKmError != null ||
                        (months.isNotBlank() && (monthsValue == null || monthsValue <= 0)) ||
                        (km.isBlank() && months.isBlank())
                    ) return@Button
                    val scheduleChanged = existing == null || existing.lastDoneEpochMillis != lastDate ||
                        existing.lastDoneOdometerKm != lastKmValue || existing.intervalKm != kmValue ||
                        existing.intervalMonths != monthsValue
                    val item = (existing ?: MaintenanceItem(vehicleId = 0, name = "")).copy(
                        name = name, intervalKm = kmValue, intervalMonths = monthsValue, isCheck = isCheck,
                        enabled = enabled, lastDoneEpochMillis = lastDate, lastDoneOdometerKm = lastKmValue,
                        // A changed schedule starts a new reminder cycle.
                        notifiedLevel = if (scheduleChanged) 0 else existing!!.notifiedLevel
                    )
                    scope.launch { viewModel.saveMaintenanceItem(item); onDone() }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
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
