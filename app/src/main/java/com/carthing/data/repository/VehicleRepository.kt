package com.carthing.data.repository

import androidx.room.withTransaction
import com.carthing.data.CarThingDatabase
import com.carthing.data.dao.VehicleWithOdometer
import com.carthing.data.entity.OdometerEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.DefaultSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class VehicleRepository(private val db: CarThingDatabase) {
    private val dao = db.vehicleDao()

    fun observeAll(): Flow<List<Vehicle>> = dao.observeAll()
    fun observeAllWithOdometer(): Flow<List<VehicleWithOdometer>> = dao.observeAllWithOdometer()
    fun observe(id: Long): Flow<Vehicle?> = dao.observeById(id)
    fun observeCurrentOdometer(id: Long): Flow<Double?> = dao.observeCurrentOdometer(id)

    /** Saves [vehicle]; a new vehicle also gets the default maintenance schedule. */
    suspend fun save(vehicle: Vehicle): Long {
        require(vehicle.name.isNotBlank()) { "Vehicle name must not be blank" }
        return db.withTransaction {
            val id = dao.upsert(vehicle.copy(name = vehicle.name.trim()))
            if (id == -1L) vehicle.id
            else id.also { db.maintenanceItemDao().insertAll(DefaultSchedule.itemsFor(it)) }
        }
    }

    suspend fun delete(vehicle: Vehicle) = dao.delete(vehicle)

    suspend fun count(): Int = dao.getAll().size

    /** Logs a plain odometer reading; it can't be below the highest reading already known. */
    suspend fun addOdometerReading(vehicleId: Long, epochMillis: Long, odometerKm: Double) {
        val current = dao.observeCurrentOdometer(vehicleId).first() ?: 0.0
        require(odometerKm >= current) { "Reading is below the current odometer" }
        db.odometerEntryDao().upsert(OdometerEntry(vehicleId = vehicleId, dateEpochMillis = epochMillis, odometerKm = odometerKm))
    }
}
