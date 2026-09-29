package com.carthing.ui.vehicles

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.graphics.vector.ImageVector
import com.carthing.ui.common.EmptyState
import com.carthing.ui.common.IconBadge
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.ui.attachments.ServiceBookPhotos
import com.carthing.data.FuelEconomy
import com.carthing.data.VehicleStats
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.repository.DeadlineRepository
import com.carthing.ui.common.BackButton
import com.carthing.ui.common.DecimalInput
import com.carthing.ui.common.LoadingBox
import com.carthing.ui.common.editableNumber
import com.carthing.ui.common.formatCostPerKm
import com.carthing.ui.common.formatDate
import com.carthing.ui.common.formatEconomy
import com.carthing.ui.common.formatKm
import com.carthing.ui.common.formatLiters
import com.carthing.ui.common.formatMoney
import com.carthing.ui.common.parseDecimal
import com.carthing.ui.maintenance.MaintenanceTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailScreen(
    vehicleId: Long,
    onBack: () -> Unit,
    onEditVehicle: () -> Unit,
    onAddFuel: () -> Unit,
    onEditFuel: (Long) -> Unit,
    onAddService: () -> Unit,
    onEditService: (Long) -> Unit,
    onAddMaintenanceItem: () -> Unit,
    onEditMaintenanceItem: (Long) -> Unit,
    onAddDeadline: () -> Unit,
    onEditDeadline: (Long) -> Unit,
    initialTab: Int = TAB_FUEL,
    viewModel: VehicleDetailViewModel = viewModel(factory = VehicleDetailViewModel.factory(vehicleId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val scope = rememberCoroutineScope()
    var updatingOdometer by rememberSaveable { mutableStateOf(false) }

    // The vehicle was deleted elsewhere (e.g. from the edit form): leave this screen.
    if (state == VehicleDetailUiState.NotFound) LaunchedEffect(Unit) { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text((state as? VehicleDetailUiState.Loaded)?.vehicle?.name.orEmpty()) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onEditVehicle) { Icon(Icons.Default.Edit, contentDescription = "Edit vehicle") }
                }
            )
        },
        floatingActionButton = {
            val (onAdd, label) = when (tab) {
                TAB_FUEL -> onAddFuel to "Add fill-up"
                TAB_SERVICE -> onAddService to "Add service"
                else -> onAddMaintenanceItem to "Add component"
            }
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = label) }
        }
    ) { padding ->
        val s = state as? VehicleDetailUiState.Loaded ?: return@Scaffold LoadingBox(Modifier.padding(padding))
        if (updatingOdometer) UpdateOdometerDialog(s.currentOdometerKm, onDismiss = { updatingOdometer = false }) { date, km ->
            updatingOdometer = false
            scope.launch { viewModel.updateOdometer(date, km) }
        }
        val attention = s.maintenance.count { it.item.enabled && it.status.level in setOf(DueLevel.DUE_SOON, DueLevel.OVERDUE) } +
            s.deadlines.count { DeadlineRepository.needsAttention(it.status) }
        Column(Modifier.padding(padding)) {
            StatsCard(s.currentOdometerKm, s.stats, onUpdateOdometer = { updatingOdometer = true })
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == TAB_FUEL, onClick = { tab = TAB_FUEL }, text = { Text("Fuel") })
                Tab(selected = tab == TAB_SERVICE, onClick = { tab = TAB_SERVICE }, text = { Text("Service") })
                Tab(
                    selected = tab == TAB_MAINTENANCE, onClick = { tab = TAB_MAINTENANCE },
                    text = { Text(if (attention > 0) "Maintenance ($attention)" else "Maintenance", maxLines = 1) }
                )
            }
            when (tab) {
                TAB_FUEL -> FuelList(s.fuelEntries, s.fuelWithPhotos, onEditFuel)
                TAB_SERVICE -> ServiceList(s.serviceEntries, s.serviceWithPhotos, onEditService) { ServiceBookPhotos(viewModel) }
                else -> MaintenanceTab(
                    s.maintenance, s.currentOdometerKm, onEditMaintenanceItem,
                    onRecord = { item, kind, date, odo, cost -> scope.launch { viewModel.markDone(item, kind, date, odo, cost) } },
                    deadlines = s.deadlines, onAddDeadline = onAddDeadline, onEditDeadline = onEditDeadline,
                    onRenewDeadline = { d, due -> scope.launch { viewModel.renewDeadline(d, due) } }
                )
            }
        }
    }
}

const val TAB_FUEL = 0
const val TAB_SERVICE = 1
const val TAB_MAINTENANCE = 2

/** Shown on entries that have receipt photos. */
private const val PHOTO_MARK = "📎"

@Composable
private fun StatsCard(odometerKm: Double, stats: VehicleStats, onUpdateOdometer: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(28.dp))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("Odometer", style = MaterialTheme.typography.labelMedium)
                    Text(formatKm(odometerKm), style = MaterialTheme.typography.headlineSmall)
                }
                FilledTonalButton(onClick = onUpdateOdometer) { Text("Update") }
            }
            Row(Modifier.fillMaxWidth()) {
                Stat(Icons.Default.LocalGasStation, "Average", formatEconomy(stats.averageLitersPer100Km), Modifier.weight(1f))
                Stat(Icons.Default.History, "Last fill-up", formatEconomy(stats.lastLitersPer100Km), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Stat(Icons.Default.Payments, "Total spent", formatMoney(stats.totalFuelCost + stats.totalServiceCost), Modifier.weight(1f))
                Stat(Icons.Default.Route, "Cost per km", formatCostPerKm(stats.costPerKm), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FuelList(entries: List<FuelEntry>, withPhotos: Set<Long>, onEdit: (Long) -> Unit) {
    if (entries.isEmpty()) return EmptyTab(Icons.Default.LocalGasStation, "No fill-ups yet", "Log fill-ups to see your fuel economy and costs. Tip: snap the receipt and let the app read it.")
    val economyByEntry = remember(entries) { FuelEconomy.segments(entries).associate { it.endEntryId to it.litersPer100Km } }
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(entries, key = { it.id }) { e ->
            val flags = listOfNotNull("partial".takeIf { !e.isFullTank }, "missed previous".takeIf { e.missedPrevious }, PHOTO_MARK.takeIf { e.id in withPhotos })
            ListItem(
                leadingContent = { IconBadge(Icons.Default.LocalGasStation) },
                headlineContent = { Text("${formatLiters(e.liters)} · ${formatKm(e.odometerKm)}") },
                supportingContent = {
                    Text((listOf(formatDate(e.dateEpochMillis)) + listOfNotNull(e.station) + flags).joinToString(" · "))
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        economyByEntry[e.id]?.let { Text(formatEconomy(it), style = MaterialTheme.typography.titleSmall) }
                        e.totalPrice?.let { Text(formatMoney(it), style = MaterialTheme.typography.bodySmall) }
                    }
                },
                modifier = Modifier.clickable { onEdit(e.id) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun ServiceList(entries: List<ServiceEntry>, withPhotos: Set<Long>, onEdit: (Long) -> Unit, serviceBook: @Composable () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        item(key = "book") {
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { serviceBook() }
            HorizontalDivider()
        }
        if (entries.isEmpty()) item {
            EmptyState(Icons.Default.Build, "No service records yet", "Add oil changes, repairs and inspections. Photos of invoices can fill in the form for you.")
        }
        items(entries, key = { it.id }) { e ->
            ListItem(
                leadingContent = { IconBadge(serviceIcon(e.type)) },
                headlineContent = { Text(e.type) },
                supportingContent = {
                    Text((listOf(formatDate(e.dateEpochMillis), formatKm(e.odometerKm)) + listOfNotNull(e.shop, PHOTO_MARK.takeIf { e.id in withPhotos })).joinToString(" · "))
                },
                trailingContent = { e.cost?.let { Text(formatMoney(it), style = MaterialTheme.typography.titleSmall) } },
                modifier = Modifier.clickable { onEdit(e.id) }
            )
            HorizontalDivider()
        }
    }
}

/** An icon hinting at the kind of work, from the service type's wording. */
private fun serviceIcon(type: String): ImageVector {
    val t = type.lowercase()
    return when {
        listOf("oil", "olej").any { it in t } -> Icons.Default.OilBarrel
        listOf("tire", "tyre", "pneu", "wheel").any { it in t } -> Icons.Default.TireRepair
        listOf("inspection", "stk", "emission").any { it in t } -> Icons.AutoMirrored.Filled.FactCheck
        listOf("battery", "akumul").any { it in t } -> Icons.Default.BatteryChargingFull
        listOf("wash", "clean").any { it in t } -> Icons.Default.LocalCarWash
        else -> Icons.Default.Build
    }
}

@Composable
private fun EmptyTab(icon: ImageVector, title: String, message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { EmptyState(icon, title, message) }
}

/** Logs today's odometer reading; it can't be lower than the current one. */
@Composable
private fun UpdateOdometerDialog(currentKm: Double, onDismiss: () -> Unit, onConfirm: (epochMillis: Long, km: Double) -> Unit) {
    var text by rememberSaveable { mutableStateOf(editableNumber(currentKm)) }
    val km = parseDecimal(text)
    val error = when {
        km == null -> "Enter the odometer reading"
        km < currentKm -> "Can't be lower than ${formatKm(currentKm)}"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update odometer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("A current reading keeps distance-based reminders accurate between fill-ups.",
                    style = MaterialTheme.typography.bodyMedium)
                DecimalInput(text, { text = it }, "Odometer today (km)", error)
            }
        },
        confirmButton = {
            TextButton(enabled = error == null, onClick = { onConfirm(System.currentTimeMillis(), km!!) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
