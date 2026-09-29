package com.carthing.data.repository

import androidx.room.withTransaction
import com.carthing.data.CarThingDatabase
import com.carthing.data.entity.FuelEntry
import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.entity.ServiceEntry
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.MaintenanceStatus
import com.carthing.data.maintenance.OdometerReading
import com.carthing.data.maintenance.ReminderPolicy
import com.carthing.data.maintenance.Usage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class ItemWithStatus(val item: MaintenanceItem, val status: MaintenanceStatus)

/** A reminder the background check should post. */
data class Reminder(val vehicle: Vehicle, val item: MaintenanceItem, val level: DueLevel, val status: MaintenanceStatus)

class MaintenanceRepository(
    private val db: CarThingDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = db.maintenanceItemDao()

    /** Items for [vehicleId] with live status, most urgent first; disabled items last. */
    fun observeWithStatus(vehicleId: Long): Flow<List<ItemWithStatus>> = combine(
        dao.observeForVehicle(vehicleId),
        db.fuelEntryDao().observeForVehicle(vehicleId),
        db.serviceEntryDao().observeForVehicle(vehicleId),
        db.vehicleDao().observeCurrentOdometer(vehicleId)
    ) { items, fuel, service, odometer ->
        val usage = usageOf(fuel, service)
        val now = clock()
        items.map { ItemWithStatus(it, MaintenanceStatus.of(it, usage, odometer ?: 0.0, now)) }
            .sortedWith(urgency)
    }

    suspend fun getById(id: Long): MaintenanceItem? = dao.getById(id)

    suspend fun save(item: MaintenanceItem): Long {
        require(item.name.isNotBlank()) { "Name must not be blank" }
        require(item.intervalKm != null || item.intervalMonths != null) { "At least one interval is required" }
        val id = dao.upsert(item.copy(name = item.name.trim()))
        return if (id == -1L) item.id else id
    }

    suspend fun delete(item: MaintenanceItem) = dao.delete(item)

    /**
     * Records [item] as done: resets its interval and, unless it is only a check, adds a service
     * entry linked to it. Returns the service entry id, or null for checks.
     */
    suspend fun markDone(item: MaintenanceItem, epochMillis: Long, odometerKm: Double, cost: Double? = null): Long? =
        db.withTransaction {
            dao.upsert(item.copy(lastDoneEpochMillis = epochMillis, lastDoneOdometerKm = odometerKm, notifiedLevel = 0))
            if (item.isCheck) null
            else db.serviceEntryDao().upsert(
                ServiceEntry(vehicleId = item.vehicleId, dateEpochMillis = epochMillis, odometerKm = odometerKm,
                    type = item.name, cost = cost, maintenanceItemId = item.id)
            )
        }

    /**
     * Evaluates every vehicle's enabled items, stores the new notified levels and returns the
     * reminders to post. Called by the daily background check.
     */
    suspend fun collectReminders(): List<Reminder> = db.withTransaction {
        val now = clock()
        val reminders = mutableListOf<Reminder>()
        for (vehicle in db.vehicleDao().getAll()) {
            val fuel = db.fuelEntryDao().getForVehicle(vehicle.id)
            val service = db.serviceEntryDao().getForVehicle(vehicle.id)
            val usage = usageOf(fuel, service)
            val recorded = (fuel.map { it.odometerKm } + service.map { it.odometerKm } + vehicle.initialOdometerKm).max()
            for (item in dao.getEnabledForVehicle(vehicle.id)) {
                val status = MaintenanceStatus.of(item, usage, recorded, now)
                val decision = ReminderPolicy.decide(item, status)
                if (decision.newNotifiedLevel != item.notifiedLevel) dao.setNotifiedLevel(item.id, decision.newNotifiedLevel)
                decision.notify?.let { reminders += Reminder(vehicle, item, it, status) }
            }
        }
        reminders
    }

    companion object {
        internal fun usageOf(fuel: List<FuelEntry>, service: List<ServiceEntry>) = Usage.estimate(
            fuel.map { OdometerReading(it.dateEpochMillis, it.odometerKm) } +
                service.map { OdometerReading(it.dateEpochMillis, it.odometerKm) }
        )

        private val levelOrder = listOf(DueLevel.OVERDUE, DueLevel.DUE_SOON, DueLevel.UNKNOWN, DueLevel.OK)
        private val urgency = compareBy<ItemWithStatus>(
            { !it.item.enabled },
            { levelOrder.indexOf(it.status.level) },
            { it.status.daysUntilDue ?: Long.MAX_VALUE },
            { it.item.name }
        )
    }
}
