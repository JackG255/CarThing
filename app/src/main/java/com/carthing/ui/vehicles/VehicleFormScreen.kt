package com.carthing.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.entity.Vehicle
import com.carthing.ui.common.BackButton
import com.carthing.ui.common.DecimalInput
import com.carthing.ui.common.DeleteAction
import com.carthing.ui.common.LoadingBox
import com.carthing.ui.common.TextInput
import com.carthing.ui.common.editableNumber
import com.carthing.ui.common.parseDecimal
import kotlinx.coroutines.launch

/** Add ([vehicleId] = 0) or edit a vehicle. [onSaved] gets the saved id; [onDeleted] fires after deletion. */
@Composable
fun VehicleFormScreen(
    vehicleId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: VehicleFormViewModel = viewModel(factory = VehicleFormViewModel.factory(vehicleId))
) {
    val state by viewModel.uiState.collectAsState(VehicleFormUiState.Loading)
    when (val s = state) {
        VehicleFormUiState.Loading -> LoadingBox()
        is VehicleFormUiState.Ready -> VehicleForm(s.vehicle, viewModel, onBack, onSaved, onDeleted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VehicleForm(
    existing: Vehicle?,
    viewModel: VehicleFormViewModel,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onDeleted: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var make by rememberSaveable { mutableStateOf(existing?.make.orEmpty()) }
    var model by rememberSaveable { mutableStateOf(existing?.model.orEmpty()) }
    var year by rememberSaveable { mutableStateOf(existing?.year?.toString().orEmpty()) }
    var vin by rememberSaveable { mutableStateOf(existing?.vin.orEmpty()) }
    var plate by rememberSaveable { mutableStateOf(existing?.licensePlate.orEmpty()) }
    var odometer by rememberSaveable { mutableStateOf(editableNumber(existing?.initialOdometerKm)) }
    var submitted by rememberSaveable { mutableStateOf(false) }

    val nameError = if (submitted && name.isBlank()) "Name is required" else null
    val yearError = if (year.isNotBlank() && year.trim().toIntOrNull()?.takeIf { it in 1886..2100 } == null) "Enter a valid year" else null
    val odometerError = if (odometer.isNotBlank() && (parseDecimal(odometer) ?: -1.0) < 0) "Enter a valid odometer reading" else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add vehicle" else "Edit vehicle") },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (existing != null) DeleteAction(
                        title = "Delete ${existing.name}?",
                        message = "All fuel and service entries for this vehicle will be deleted too."
                    ) { scope.launch { viewModel.delete(existing); onDeleted() } }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextInput(name, { name = it }, "Name *", error = nameError)
            TextInput(make, { make = it }, "Make")
            TextInput(model, { model = it }, "Model")
            TextInput(year, { year = it }, "Year", error = yearError, keyboardType = KeyboardType.Number)
            TextInput(plate, { plate = it }, "License plate")
            TextInput(vin, { vin = it }, "VIN")
            DecimalInput(odometer, { odometer = it }, "Odometer when added (km)", error = odometerError)
            Button(
                onClick = {
                    submitted = true
                    if (name.isBlank() || yearError != null || odometerError != null) return@Button
                    val vehicle = Vehicle(
                        name = name,
                        make = make.trim().ifEmpty { null },
                        model = model.trim().ifEmpty { null },
                        year = year.trim().toIntOrNull(),
                        vin = vin.trim().ifEmpty { null },
                        licensePlate = plate.trim().ifEmpty { null },
                        initialOdometerKm = parseDecimal(odometer) ?: 0.0
                    )
                    scope.launch { onSaved(viewModel.save(vehicle)) }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}
