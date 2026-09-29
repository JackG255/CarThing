package com.carthing.ui.vehicles

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.carthing.data.dao.VehicleWithOdometer
import com.carthing.ui.backup.BackupMenu
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
    Scaffold(
        topBar = { TopAppBar(title = { Text("CarThing") }, actions = { BackupMenu(snackbar) }) },
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
                    items(s.vehicles, key = { it.vehicle.id }) { VehicleCard(it) { onOpenVehicle(it.vehicle.id) } }
                }
        }
    }
}

@Composable
private fun EmptyVehicles(modifier: Modifier) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No vehicles yet", style = MaterialTheme.typography.titleMedium)
            Text("Tap + to add your first one.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun VehicleCard(item: VehicleWithOdometer, onClick: () -> Unit) {
    val v = item.vehicle
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(v.name, style = MaterialTheme.typography.titleMedium)
            val details = listOfNotNull(v.make, v.model, v.year?.toString()).joinToString(" ")
            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodyMedium)
            Text(formatKm(item.currentOdometerKm), style = MaterialTheme.typography.bodySmall)
        }
    }
}
