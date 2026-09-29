package com.carthing.ui.fuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.entity.FuelEntry
import com.carthing.data.repository.FuelIssue
import com.carthing.data.repository.SaveResult
import com.carthing.ui.common.BackButton
import com.carthing.ui.common.DateInput
import com.carthing.ui.common.DecimalInput
import com.carthing.ui.common.DeleteAction
import com.carthing.ui.common.LoadingBox
import com.carthing.ui.common.TextInput
import com.carthing.ui.common.editableNumber
import com.carthing.ui.common.formatDate
import com.carthing.ui.common.formatKm
import com.carthing.ui.common.parseDecimal
import com.carthing.ui.vehicles.VehicleDetailUiState
import com.carthing.ui.vehicles.VehicleDetailViewModel
import kotlinx.coroutines.launch

/** Add ([entryId] = 0) or edit a fill-up. Validation issues from the repository are shown inline; warnings ask to confirm. */
@Composable
fun FuelFormScreen(
    vehicleId: Long,
    entryId: Long,
    onDone: () -> Unit,
    viewModel: VehicleDetailViewModel = viewModel(factory = VehicleDetailViewModel.factory(vehicleId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state as? VehicleDetailUiState.Loaded ?: return LoadingBox()
    // Captured once so later database updates (including our own save) don't reset the form.
    val existing = remember { s.fuelEntries.firstOrNull { it.id == entryId } }
    val suggestedOdometer = remember { s.currentOdometerKm }
    FuelForm(existing, suggestedOdometer, viewModel, onDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FuelForm(existing: FuelEntry?, suggestedOdometerKm: Double, viewModel: VehicleDetailViewModel, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var date by rememberSaveable { mutableLongStateOf(existing?.dateEpochMillis ?: System.currentTimeMillis()) }
    var odometer by rememberSaveable { mutableStateOf(editableNumber(existing?.odometerKm ?: suggestedOdometerKm)) }
    var liters by rememberSaveable { mutableStateOf(editableNumber(existing?.liters)) }
    var price by rememberSaveable { mutableStateOf(editableNumber(existing?.totalPrice)) }
    var fullTank by rememberSaveable { mutableStateOf(existing?.isFullTank ?: true) }
    var missedPrevious by rememberSaveable { mutableStateOf(existing?.missedPrevious ?: false) }
    var station by rememberSaveable { mutableStateOf(existing?.station.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    // Issues from the last save attempt; cleared when the user edits a related field.
    var issues by remember { mutableStateOf<List<FuelIssue>>(emptyList()) }
    var parseErrors by remember { mutableStateOf(emptySet<String>()) }
    var pendingWarnings by remember { mutableStateOf<FuelEntry?>(null) }

    fun save(entry: FuelEntry, acceptWarnings: Boolean) = scope.launch {
        when (val result = viewModel.saveFuel(entry, acceptWarnings)) {
            is SaveResult.Saved -> onDone()
            is SaveResult.Rejected ->
                if (result.issues.none { it.isError }) pendingWarnings = entry else issues = result.issues.filter { it.isError }
        }
    }

    val odometerError = "odometer".takeIf { it in parseErrors }?.let { "Enter the odometer reading" }
        ?: issues.firstNotNullOfOrNull { it.odometerMessage() }
    val litersError = "liters".takeIf { it in parseErrors }?.let { "Enter the amount of fuel" }
        ?: issues.firstNotNullOfOrNull { (it as? FuelIssue.NonPositiveLiters)?.let { "Must be more than 0" } }
    val priceError = "price".takeIf { it in parseErrors }?.let { "Enter a valid price" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add fill-up" else "Edit fill-up") },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (existing != null) DeleteAction("Delete fill-up?", "This can't be undone.") {
                        scope.launch { viewModel.deleteFuel(existing); onDone() }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateInput(date, { date = it; issues = emptyList() }, "Date")
            DecimalInput(odometer, { odometer = it; issues = emptyList(); parseErrors -= "odometer" }, "Odometer (km)", odometerError)
            DecimalInput(liters, { liters = it; issues = emptyList(); parseErrors -= "liters" }, "Liters", litersError)
            DecimalInput(price, { price = it; parseErrors -= "price" }, "Total price", priceError)
            SwitchRow("Filled the tank completely", fullTank) { fullTank = it; issues = emptyList() }
            SwitchRow("Missed recording the previous fill-up", missedPrevious) { missedPrevious = it }
            TextInput(station, { station = it }, "Station")
            TextInput(note, { note = it }, "Note", singleLine = false)
            Button(
                onClick = {
                    val odo = parseDecimal(odometer)
                    val l = parseDecimal(liters)
                    val p = parseDecimal(price)
                    parseErrors = setOfNotNull(
                        "odometer".takeIf { odo == null },
                        "liters".takeIf { l == null },
                        "price".takeIf { price.isNotBlank() && p == null }
                    )
                    if (parseErrors.isNotEmpty()) return@Button
                    val entry = FuelEntry(
                        id = existing?.id ?: 0, vehicleId = existing?.vehicleId ?: 0,
                        dateEpochMillis = date, odometerKm = odo!!, liters = l!!, totalPrice = p,
                        isFullTank = fullTank, missedPrevious = missedPrevious,
                        station = station.trim().ifEmpty { null }, note = note.trim().ifEmpty { null }
                    )
                    save(entry, acceptWarnings = false)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }

    pendingWarnings?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingWarnings = null },
            title = { Text("Save anyway?") },
            text = { Text("There's already a fill-up at ${formatKm(entry.odometerKm)}. Is this a top-off at the same stop?") },
            confirmButton = { TextButton(onClick = { pendingWarnings = null; save(entry, acceptWarnings = true) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { pendingWarnings = null }) { Text("Cancel") } }
        )
    }
}

private fun FuelIssue.odometerMessage(): String? = when (this) {
    is FuelIssue.BelowInitialOdometer -> "Lower than the vehicle's starting odometer (${formatKm(initialOdometerKm)})"
    is FuelIssue.OdometerOutOfOrder ->
        "Conflicts with the fill-up on ${formatDate(other.dateEpochMillis)} at ${formatKm(other.odometerKm)}"
    is FuelIssue.DuplicateFullTank ->
        "There's already a full-tank fill-up at this reading (${formatDate(other.dateEpochMillis)})"
    else -> null
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    // The whole row toggles, so tapping the label works too.
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}
