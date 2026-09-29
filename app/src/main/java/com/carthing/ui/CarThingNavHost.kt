package com.carthing.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.carthing.ui.fuel.FuelFormScreen
import com.carthing.ui.maintenance.MaintenanceItemFormScreen
import com.carthing.ui.service.ServiceFormScreen
import com.carthing.ui.vehicles.VehicleDetailScreen
import com.carthing.ui.vehicles.TAB_FUEL
import com.carthing.ui.vehicles.TAB_MAINTENANCE
import com.carthing.ui.vehicles.VehicleFormScreen
import com.carthing.ui.vehicles.VehicleListScreen
import kotlinx.serialization.Serializable

@Serializable object VehicleListRoute
/** [vehicleId] of 0 adds a new vehicle. */
@Serializable data class VehicleFormRoute(val vehicleId: Long = 0)
@Serializable data class VehicleDetailRoute(val vehicleId: Long, val tab: Int = TAB_FUEL)
/** [entryId] of 0 adds a new entry. */
@Serializable data class FuelFormRoute(val vehicleId: Long, val entryId: Long = 0)
@Serializable data class ServiceFormRoute(val vehicleId: Long, val entryId: Long = 0)
@Serializable data class MaintenanceItemFormRoute(val vehicleId: Long, val itemId: Long = 0)

@Composable
fun CarThingNavHost(openVehicleId: Long? = null, onOpenedVehicle: () -> Unit = {}) {
    val nav = rememberNavController()
    // Opened from a maintenance notification: show that vehicle's Maintenance tab.
    LaunchedEffect(openVehicleId) {
        openVehicleId?.let {
            nav.navigate(VehicleDetailRoute(it, TAB_MAINTENANCE)) { popUpTo<VehicleListRoute>() }
            onOpenedVehicle()
        }
    }
    NavHost(nav, startDestination = VehicleListRoute) {
        composable<VehicleListRoute> {
            VehicleListScreen(
                onOpenVehicle = { nav.navigate(VehicleDetailRoute(it)) },
                onAddVehicle = { nav.navigate(VehicleFormRoute()) }
            )
        }
        composable<VehicleFormRoute> { entry ->
            val route = entry.toRoute<VehicleFormRoute>()
            VehicleFormScreen(
                vehicleId = route.vehicleId,
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    // A new vehicle opens its detail screen; an edit returns to it.
                    if (route.vehicleId == 0L) nav.navigate(VehicleDetailRoute(id)) { popUpTo<VehicleListRoute>() }
                    else nav.popBackStack()
                },
                onDeleted = { nav.popBackStack<VehicleListRoute>(inclusive = false) }
            )
        }
        composable<VehicleDetailRoute> { entry ->
            val route = entry.toRoute<VehicleDetailRoute>()
            val id = route.vehicleId
            VehicleDetailScreen(
                vehicleId = id,
                onBack = { nav.popBackStack() },
                onEditVehicle = { nav.navigate(VehicleFormRoute(id)) },
                onAddFuel = { nav.navigate(FuelFormRoute(id)) },
                onEditFuel = { nav.navigate(FuelFormRoute(id, it)) },
                onAddService = { nav.navigate(ServiceFormRoute(id)) },
                onEditService = { nav.navigate(ServiceFormRoute(id, it)) },
                onAddMaintenanceItem = { nav.navigate(MaintenanceItemFormRoute(id)) },
                onEditMaintenanceItem = { nav.navigate(MaintenanceItemFormRoute(id, it)) },
                initialTab = route.tab
            )
        }
        composable<FuelFormRoute> { entry ->
            val route = entry.toRoute<FuelFormRoute>()
            FuelFormScreen(route.vehicleId, route.entryId, onDone = { nav.popBackStack() })
        }
        composable<ServiceFormRoute> { entry ->
            val route = entry.toRoute<ServiceFormRoute>()
            ServiceFormScreen(route.vehicleId, route.entryId, onDone = { nav.popBackStack() })
        }
        composable<MaintenanceItemFormRoute> { entry ->
            val route = entry.toRoute<MaintenanceItemFormRoute>()
            MaintenanceItemFormScreen(route.vehicleId, route.itemId, onDone = { nav.popBackStack() })
        }
    }
}
