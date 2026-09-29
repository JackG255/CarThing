package com.carthing.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.carthing.ui.fuel.FuelFormScreen
import com.carthing.ui.service.ServiceFormScreen
import com.carthing.ui.vehicles.VehicleDetailScreen
import com.carthing.ui.vehicles.VehicleFormScreen
import com.carthing.ui.vehicles.VehicleListScreen
import kotlinx.serialization.Serializable

@Serializable object VehicleListRoute
/** [vehicleId] of 0 adds a new vehicle. */
@Serializable data class VehicleFormRoute(val vehicleId: Long = 0)
@Serializable data class VehicleDetailRoute(val vehicleId: Long)
/** [entryId] of 0 adds a new entry. */
@Serializable data class FuelFormRoute(val vehicleId: Long, val entryId: Long = 0)
@Serializable data class ServiceFormRoute(val vehicleId: Long, val entryId: Long = 0)

@Composable
fun CarThingNavHost() {
    val nav = rememberNavController()
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
            val id = entry.toRoute<VehicleDetailRoute>().vehicleId
            VehicleDetailScreen(
                vehicleId = id,
                onBack = { nav.popBackStack() },
                onEditVehicle = { nav.navigate(VehicleFormRoute(id)) },
                onAddFuel = { nav.navigate(FuelFormRoute(id)) },
                onEditFuel = { nav.navigate(FuelFormRoute(id, it)) },
                onAddService = { nav.navigate(ServiceFormRoute(id)) },
                onEditService = { nav.navigate(ServiceFormRoute(id, it)) }
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
    }
}
