package com.carthing.ui.service

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.attachments.AttachmentOwner
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.ServiceEntry
import com.carthing.ui.attachments.EntryPhotos
import com.carthing.ui.attachments.OdometerInput
import com.carthing.ui.attachments.ReadOffer
import com.carthing.ui.attachments.ReceiptBanner
import com.carthing.ui.attachments.rememberPendingPhotos
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
import java.time.ZoneId
import kotlinx.coroutines.launch

private val COMMON_TYPES = listOf("Oil change", "Tires", "Brakes", "Inspection", "Battery", "Filters", "Wipers", "Repair")

/** Add ([entryId] = 0) or edit a service record. */
@Composable
fun ServiceFormScreen(
    vehicleId: Long,
    entryId: Long,
    onDone: () -> Unit,
    viewModel: VehicleDetailViewModel = viewModel(factory = VehicleDetailViewModel.factory(vehicleId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val s = state as? VehicleDetailUiState.Loaded ?: return LoadingBox()
    val existing = remember { s.serviceEntries.firstOrNull { it.id == entryId } }
    val suggestedOdometer = remember { s.currentOdometerKm }
    // Offer the user's own past types first, then the common ones.
    val suggestions = remember { (s.serviceEntries.map { it.type } + COMMON_TYPES).distinct() }
    val components = remember { s.maintenance.map { it.item }.sortedBy { it.name } }
    ServiceForm(existing, suggestedOdometer, suggestions, components, viewModel, onDone)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceForm(
    existing: ServiceEntry?,
    suggestedOdometerKm: Double,
    suggestions: List<String>,
    components: List<MaintenanceItem>,
    viewModel: VehicleDetailViewModel,
    onDone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val photos = rememberPendingPhotos()
    var type by rememberSaveable { mutableStateOf(existing?.type.orEmpty()) }
    var date by rememberSaveable { mutableLongStateOf(existing?.dateEpochMillis ?: System.currentTimeMillis()) }
    var odometer by rememberSaveable { mutableStateOf(editableNumber(existing?.odometerKm ?: suggestedOdometerKm)) }
    var cost by rememberSaveable { mutableStateOf(editableNumber(existing?.cost)) }
    var shop by rememberSaveable { mutableStateOf(existing?.shop.orEmpty()) }
    var note by rememberSaveable { mutableStateOf(existing?.note.orEmpty()) }
    var componentId by rememberSaveable { mutableStateOf(existing?.maintenanceItemId) }
    var submitted by rememberSaveable { mutableStateOf(false) }
    // Invoice reading, as on the fill-up form: opt-in per photo, never overwrites what the user typed.
    var touched by rememberSaveable { mutableStateOf(listOf<String>()) }
    var fromInvoice by rememberSaveable { mutableStateOf(listOf<String>()) }
    var reading by remember { mutableStateOf(false) }
    var readMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var offerRead by rememberSaveable { mutableStateOf<String?>(null) }
    fun touch(field: String) { touched = touched + field; fromInvoice = fromInvoice - field }
    fun hint(field: String) = "From invoice".takeIf { field in fromInvoice }

    fun readInvoice(fileName: String) = scope.launch {
        reading = true
        val r = viewModel.readServiceInvoice(fileName)
        reading = false
        val filled = mutableListOf<String>()
        fun fill(field: String, value: Any?, apply: () -> Unit) {
            if (value != null && field !in touched) { apply(); filled += field }
        }
        r?.let {
            if (type.isBlank()) fill("type", it.services.firstOrNull()) { type = it.services.first() }
            fill("date", it.date) { date = it.date!!.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            fill("odometer", it.odometerKm) { odometer = editableNumber(it.odometerKm) }
            fill("cost", it.total) { cost = editableNumber(it.total) }
            fill("shop", it.shop) { shop = it.shop!! }
            if (note.isBlank() && it.services.size > 1) fill("note", it.services) { note = "Work: " + it.services.joinToString(", ") }
        }
        fromInvoice = (fromInvoice + filled).distinct()
        readMessage = if (filled.isEmpty()) "Couldn't read anything from this photo. It's still attached."
            else "Filled from the invoice: ${filled.joinToString(", ") { LABELS.getValue(it) }}. Check them before saving."
    }

    val typeError = if (submitted && type.isBlank()) "Service type is required" else null
    val odometerError = if (submitted && (parseDecimal(odometer) ?: -1.0) < 0) "Enter the odometer reading" else null
    val costError = if (cost.isNotBlank() && parseDecimal(cost) == null) "Enter a valid cost" else null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add service" else "Edit service") },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (existing != null) DeleteAction("Delete service record?", "This can't be undone.") {
                        scope.launch { viewModel.deleteService(existing); onDone() }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EntryPhotos(
                existing?.let { AttachmentOwner.Service(it.id) }, photos, viewModel,
                onPhotoAdded = { offerRead = it; readMessage = null },
                onRead = { offerRead = null; readInvoice(it) },
            )
            offerRead?.let { name ->
                ReadOffer("Fill in the form from this invoice?", onRead = { offerRead = null; readInvoice(name) }, onDismiss = { offerRead = null })
            }
            if (reading) Text("Reading the invoice…", style = MaterialTheme.typography.bodySmall)
            readMessage?.let { ReceiptBanner(it) { readMessage = null } }
            TextInput(type, { type = it; touch("type") }, "Type *", error = typeError, hint = hint("type"))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.forEach { SuggestionChip(onClick = { type = it; touch("type") }, label = { Text(it) }) }
            }
            ComponentPicker(components, componentId) { picked ->
                // Fill the type from the component unless the user typed something of their own.
                val previous = components.firstOrNull { it.id == componentId }?.name
                if (picked != null && (type.isBlank() || type == previous)) type = picked.name
                componentId = picked?.id
            }
            DateInput(date, { date = it; touch("date") }, if ("date" in fromInvoice) "Date (from invoice)" else "Date")
            OdometerInput(odometer, { odometer = it; touch("odometer") }, odometerError, existing?.odometerKm ?: suggestedOdometerKm, viewModel, onPhotoAdded = { photos += it }, hint = hint("odometer"))
            DecimalInput(cost, { cost = it; touch("cost") }, "Cost", costError, hint("cost"))
            TextInput(shop, { shop = it; touch("shop") }, "Shop", hint = hint("shop"))
            TextInput(note, { note = it; touch("note") }, "Note", singleLine = false, hint = hint("note"))
            Button(
                onClick = {
                    submitted = true
                    val odo = parseDecimal(odometer)
                    if (type.isBlank() || odo == null || odo < 0 || costError != null) return@Button
                    val entry = ServiceEntry(
                        id = existing?.id ?: 0, vehicleId = existing?.vehicleId ?: 0,
                        dateEpochMillis = date, odometerKm = odo, type = type.trim(), cost = parseDecimal(cost),
                        shop = shop.trim().ifEmpty { null }, note = note.trim().ifEmpty { null },
                        maintenanceItemId = componentId
                    )
                    scope.launch {
                        val id = viewModel.saveService(entry)
                        viewModel.attachPhotos(AttachmentOwner.Service(id), photos.toList())
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}

/** Optional link to a tracked component; linking restarts that component's interval. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComponentPicker(components: List<MaintenanceItem>, selectedId: Long?, onPick: (MaintenanceItem?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = components.firstOrNull { it.id == selectedId }?.name ?: "None",
            onValueChange = {},
            readOnly = true,
            label = { Text("Component (resets its schedule)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onPick(null); expanded = false })
            components.forEach { c ->
                DropdownMenuItem(text = { Text(c.name) }, onClick = { onPick(c); expanded = false })
            }
        }
    }
}

private val LABELS = mapOf(
    "type" to "type", "date" to "date", "odometer" to "odometer", "cost" to "cost", "shop" to "shop", "note" to "work done",
)
