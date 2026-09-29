package com.carthing.ui.vehicles

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import com.carthing.ui.common.EmptyState
import com.carthing.ui.common.IconBadge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.CarThingApp
import com.carthing.data.dao.VehicleWithOdometer
import com.carthing.data.backup.BackupPolicy
import com.carthing.ui.backup.BackupBanner
import com.carthing.ui.backup.BackupMenu
import com.carthing.ui.backup.BackupViewModel
import com.carthing.ui.common.LoadingBox
import com.carthing.ui.common.formatKm

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListScreen(
    onOpenVehicle: (Long) -> Unit,
    onAddVehicle: () -> Unit,
    viewModel: VehicleListViewModel = viewModel(factory = VehicleListViewModel.Factory)
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val backupViewModel: BackupViewModel = viewModel(factory = BackupViewModel.Factory)
    val backupSettings by backupViewModel.settingsState.collectAsStateWithLifecycle()
    val backupStale = BackupPolicy.isStale(backupSettings.lastBackupEpochMillis, System.currentTimeMillis())
    Scaffold(
        topBar = { TopAppBar(title = { Text("CarThing") }, actions = { BackupMenu(snackbar, backupViewModel) { close -> AppearanceMenuItems(close) } }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddVehicle) { Icon(Icons.Default.Add, contentDescription = "Add vehicle") }
        }
    ) { padding ->
        when (val s = state) {
            VehicleListUiState.Loading -> LoadingBox(Modifier.padding(padding))
            is VehicleListUiState.Loaded ->
                if (s.vehicles.isEmpty()) EmptyVehicles(Modifier.padding(padding))
                else LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(padding)
                ) {
                    if (backupStale) item(key = "backup-banner") { BackupBanner(backupViewModel) }
                    items(s.vehicles, key = { it.vehicle.id }) { VehicleCard(it) { onOpenVehicle(it.vehicle.id) } }
                }
        }
    }
}

@Composable
private fun EmptyVehicles(modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        EmptyState(Icons.Default.DirectionsCar, "No vehicles yet", "Tap + to add your car, then log fill-ups and services.")
    }
}

@Composable
private fun VehicleCard(item: VehicleWithOdometer, onClick: () -> Unit) {
    val v = item.vehicle
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                Icons.Default.DirectionsCar, size = 48.dp,
                container = MaterialTheme.colorScheme.primaryContainer, content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(v.name, style = MaterialTheme.typography.titleMedium)
                val details = listOfNotNull(v.make, v.model, v.year?.toString()).joinToString(" ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(formatKm(item.currentOdometerKm), style = MaterialTheme.typography.labelLarge)
                    v.licensePlate?.takeIf { it.isNotBlank() }?.let { PlateBadge(it) }
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The licence plate, drawn like one: outlined, upper case. */
@Composable
private fun PlateBadge(plate: String) {
    Text(
        plate.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** Colour choice: the phone's wallpaper colours (Android 12+) or the app's own teal. */
@Composable
private fun AppearanceMenuItems(close: () -> Unit) {
    val appearance = (LocalContext.current.applicationContext as CarThingApp).container.appearance
    if (!appearance.wallpaperColorsAvailable) return
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text("Wallpaper colours") },
        trailingIcon = { Checkbox(checked = appearance.useWallpaperColors, onCheckedChange = null) },
        onClick = { appearance.setWallpaperColors(!appearance.useWallpaperColors); close() },
    )
}
