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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.FuelEconomy
import com.carthing.data.VehicleStats
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.maintenance.DueLevel
import com.carthing.ui.common.BackButton
import com.carthing.ui.common.LoadingBox
import com.carthing.ui.common.formatCostPerKm
import com.carthing.ui.common.formatDate
import com.carthing.ui.common.formatEconomy
import com.carthing.ui.common.formatKm
import com.carthing.ui.common.formatLiters
import com.carthing.ui.common.formatMoney
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
    initialTab: Int = TAB_FUEL,
    viewModel: VehicleDetailViewModel = viewModel(factory = VehicleDetailViewModel.factory(vehicleId))
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(initialTab) }
    val scope = rememberCoroutineScope()

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
        val attention = s.maintenance.count { it.item.enabled && it.status.level in setOf(DueLevel.DUE_SOON, DueLevel.OVERDUE) }
        Column(Modifier.padding(padding)) {
            StatsCard(s.currentOdometerKm, s.stats)
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == TAB_FUEL, onClick = { tab = TAB_FUEL }, text = { Text("Fuel") })
                Tab(selected = tab == TAB_SERVICE, onClick = { tab = TAB_SERVICE }, text = { Text("Service") })
                Tab(
                    selected = tab == TAB_MAINTENANCE, onClick = { tab = TAB_MAINTENANCE },
                    text = { Text(if (attention > 0) "Maintenance ($attention)" else "Maintenance", maxLines = 1) }
                )
            }
            when (tab) {
                TAB_FUEL -> FuelList(s.fuelEntries, onEditFuel)
                TAB_SERVICE -> ServiceList(s.serviceEntries, onEditService)
                else -> MaintenanceTab(s.maintenance, s.currentOdometerKm, onEditMaintenanceItem) { item, date, odo, cost ->
                    scope.launch { viewModel.markDone(item, date, odo, cost) }
                }
            }
        }
    }
}

const val TAB_FUEL = 0
const val TAB_SERVICE = 1
const val TAB_MAINTENANCE = 2

@Composable
private fun StatsCard(odometerKm: Double, stats: VehicleStats) {
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatKm(odometerKm), style = MaterialTheme.typography.headlineSmall)
            Row(Modifier.fillMaxWidth()) {
                Stat("Average", formatEconomy(stats.averageLitersPer100Km), Modifier.weight(1f))
                Stat("Last", formatEconomy(stats.lastLitersPer100Km), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                Stat("Total spent", formatMoney(stats.totalFuelCost + stats.totalServiceCost), Modifier.weight(1f))
                Stat("Cost", formatCostPerKm(stats.costPerKm), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun FuelList(entries: List<FuelEntry>, onEdit: (Long) -> Unit) {
    if (entries.isEmpty()) return EmptyTab("No fill-ups yet")
    val economyByEntry = remember(entries) { FuelEconomy.segments(entries).associate { it.endEntryId to it.litersPer100Km } }
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(entries, key = { it.id }) { e ->
            val flags = listOfNotNull("partial".takeIf { !e.isFullTank }, "missed previous".takeIf { e.missedPrevious })
            ListItem(
                headlineContent = { Text("${formatLiters(e.liters)} · ${formatKm(e.odometerKm)}") },
                supportingContent = {
                    Text((listOf(formatDate(e.dateEpochMillis)) + listOfNotNull(e.station) + flags).joinToString(" · "))
                },
                trailingContent = {
                    Column(horizontalAlignment = Alignment.End) {
                        economyByEntry[e.id]?.let { Text(formatEconomy(it)) }
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
private fun ServiceList(entries: List<ServiceEntry>, onEdit: (Long) -> Unit) {
    if (entries.isEmpty()) return EmptyTab("No service records yet")
    LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
        items(entries, key = { it.id }) { e ->
            ListItem(
                headlineContent = { Text(e.type) },
                supportingContent = {
                    Text((listOf(formatDate(e.dateEpochMillis), formatKm(e.odometerKm)) + listOfNotNull(e.shop)).joinToString(" · "))
                },
                trailingContent = { e.cost?.let { Text(formatMoney(it)) } },
                modifier = Modifier.clickable { onEdit(e.id) }
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EmptyTab(message: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
