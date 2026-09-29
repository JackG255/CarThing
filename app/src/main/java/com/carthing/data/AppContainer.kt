package com.carthing.data

import android.content.Context
import com.carthing.data.repository.FuelRepository
import com.carthing.data.repository.MaintenanceRepository
import com.carthing.data.repository.ServiceRepository
import com.carthing.data.repository.VehicleRepository

/** Manual dependency container; one instance per process, owned by [com.carthing.CarThingApp]. */
class AppContainer(context: Context, db: CarThingDatabase = CarThingDatabase.get(context)) {
    val vehicleRepository = VehicleRepository(db)
    val fuelRepository = FuelRepository(db.fuelEntryDao(), db.vehicleDao())
    val serviceRepository = ServiceRepository(db)
    val maintenanceRepository = MaintenanceRepository(db)
}
