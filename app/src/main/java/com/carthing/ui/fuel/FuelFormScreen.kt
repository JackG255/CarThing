package com.carthing.ui.fuel

import android.content.res.Resources
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.R
import com.carthing.data.attachments.AttachmentOwner
import com.carthing.data.entity.FuelEntry
import com.carthing.data.repository.FuelIssue
import com.carthing.data.repository.SaveResult
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
import com.carthing.ui.common.formatDate
import com.carthing.ui.common.formatKm
import com.carthing.ui.common.parseDecimal
import com.carthing.ui.vehicles.VehicleDetailUiState
import com.carthing.ui.vehicles.VehicleDetailViewModel
import java.time.ZoneId
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
    val photos = rememberPendingPhotos()
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
    // Receipt reading: fields the user typed in are never overwritten; filled ones are marked.
    var touched by rememberSaveable { mutableStateOf(listOf<String>()) }
    var fromReceipt by rememberSaveable { mutableStateOf(listOf<String>()) }
    var readingReceipt by remember { mutableStateOf(false) }
    // Field keys filled by the last read (empty: nothing found); null: no message. Kept as keys so the text follows the language.
    var receiptFilled by rememberSaveable { mutableStateOf<List<String>?>(null) }
    // Reading is opt-in: a new photo only offers it, since not every photo is a fuel receipt.
    var offerRead by rememberSaveable { mutableStateOf<String?>(null) }
    fun touch(field: String) { touched = touched + field; fromReceipt = fromReceipt - field }

    fun readReceipt(fileName: String) = scope.launch {
        readingReceipt = true
        val r = viewModel.readFuelReceipt(fileName)
        readingReceipt = false
        val filled = mutableListOf<String>()
        fun fill(field: String, value: Any?, apply: () -> Unit) {
            if (value != null && field !in touched) { apply(); filled += field }
        }
        r?.let {
            fill("liters", it.liters) { liters = editableNumber(it.liters) }
            fill("price", it.total) { price = editableNumber(it.total) }
            fill("date", it.date) { date = it.date!!.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
            fill("station", it.station) { station = it.station!! }
            if (note.isBlank()) fill("note", it.fuelType) { note = it.fuelType!! }
        }
        fromReceipt = (fromReceipt + filled).distinct()
        receiptFilled = filled.toList()
    }
    val fromReceiptHint = stringResource(R.string.form_hint_from_receipt)
    fun hint(field: String) = fromReceiptHint.takeIf { field in fromReceipt }

    fun save(entry: FuelEntry, acceptWarnings: Boolean) = scope.launch {
        when (val result = viewModel.saveFuel(entry, acceptWarnings)) {
            is SaveResult.Saved -> {
                viewModel.attachPhotos(AttachmentOwner.Fuel(result.id), photos.toList())
                onDone()
            }
            is SaveResult.Rejected ->
                if (result.issues.none { it.isError }) pendingWarnings = entry else issues = result.issues.filter { it.isError }
        }
    }

    val res = LocalContext.current.resources
    val odometerError = "odometer".takeIf { it in parseErrors }?.let { res.getString(R.string.detail_odometer_required) }
        ?: issues.firstNotNullOfOrNull { it.odometerMessage(res) }
    val litersError = "liters".takeIf { it in parseErrors }?.let { res.getString(R.string.fuel_error_liters_required) }
        ?: issues.firstNotNullOfOrNull { (it as? FuelIssue.NonPositiveLiters)?.let { res.getString(R.string.fuel_error_liters_positive) } }
    val priceError = "price".takeIf { it in parseErrors }?.let { res.getString(R.string.fuel_error_price_invalid) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (existing == null) R.string.fuel_form_add else R.string.fuel_form_edit)) },
                navigationIcon = { BackButton(onDone) },
                actions = {
                    if (existing != null) DeleteAction(stringResource(R.string.fuel_delete_title), stringResource(R.string.delete_cannot_be_undone)) {
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
            EntryPhotos(
                existing?.let { AttachmentOwner.Fuel(it.id) }, photos, viewModel,
                onPhotoAdded = { offerRead = it; receiptFilled = null },
                onRead = { offerRead = null; readReceipt(it) },
            )
            offerRead?.let { name ->
                ReadOffer(stringResource(R.string.fuel_offer_read), onRead = { offerRead = null; readReceipt(name) }, onDismiss = { offerRead = null })
            }
            if (readingReceipt) Text(stringResource(R.string.fuel_reading_receipt), style = MaterialTheme.typography.bodySmall)
            receiptFilled?.let { ReceiptBanner(filledMessage(res, it)) { receiptFilled = null } }
            DateInput(date, { date = it; issues = emptyList(); touch("date") }, stringResource(if ("date" in fromReceipt) R.string.form_date_from_receipt else R.string.form_date))
            OdometerInput(odometer, { odometer = it; issues = emptyList(); parseErrors -= "odometer" }, odometerError, existing?.odometerKm ?: suggestedOdometerKm, viewModel, onPhotoAdded = { photos += it })
            DecimalInput(liters, { liters = it; issues = emptyList(); parseErrors -= "liters"; touch("liters") }, stringResource(R.string.fuel_field_liters), litersError, hint("liters"))
            DecimalInput(price, { price = it; parseErrors -= "price"; touch("price") }, stringResource(R.string.fuel_field_total_price), priceError, hint("price"))
            SwitchRow(stringResource(R.string.fuel_switch_full_tank), fullTank) { fullTank = it; issues = emptyList() }
            SwitchRow(stringResource(R.string.fuel_switch_missed_previous), missedPrevious) { missedPrevious = it }
            TextInput(station, { station = it; touch("station") }, stringResource(R.string.fuel_field_station), hint = hint("station"))
            TextInput(note, { note = it; touch("note") }, stringResource(R.string.form_note), singleLine = false, hint = hint("note"))
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
            ) { Text(stringResource(R.string.action_save)) }
        }
    }

    pendingWarnings?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingWarnings = null },
            title = { Text(stringResource(R.string.fuel_save_anyway_title)) },
            text = { Text(stringResource(R.string.fuel_save_anyway_body, formatKm(entry.odometerKm))) },
            confirmButton = { TextButton(onClick = { pendingWarnings = null; save(entry, acceptWarnings = true) }) { Text(stringResource(R.string.action_save)) } },
            dismissButton = { TextButton(onClick = { pendingWarnings = null }) { Text(stringResource(R.string.action_cancel)) } }
        )
    }
}

private fun FuelIssue.odometerMessage(res: Resources): String? = when (this) {
    is FuelIssue.BelowInitialOdometer -> res.getString(R.string.fuel_error_odometer_below_initial, formatKm(res, initialOdometerKm))
    is FuelIssue.OdometerOutOfOrder ->
        res.getString(R.string.fuel_error_odometer_out_of_order, formatDate(other.dateEpochMillis), formatKm(res, other.odometerKm))
    is FuelIssue.DuplicateFullTank -> res.getString(R.string.fuel_error_duplicate_full_tank, formatDate(other.dateEpochMillis))
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

/** Names of the fields a receipt can fill, for the "Filled from the receipt: …" banner. */
private val LABELS = mapOf(
    "liters" to R.string.read_field_liters, "price" to R.string.read_field_total_price, "date" to R.string.read_field_date,
    "station" to R.string.read_field_station, "note" to R.string.read_field_fuel_type,
)

/** The banner text after a read that filled [filled] (field keys); empty means nothing could be read. */
private fun filledMessage(res: Resources, filled: List<String>): String =
    if (filled.isEmpty()) res.getString(R.string.receipt_read_nothing)
    else res.getString(R.string.fuel_filled_from_receipt, filled.joinToString(", ") { res.getString(LABELS.getValue(it)) })
