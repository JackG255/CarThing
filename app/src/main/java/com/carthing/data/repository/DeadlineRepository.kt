package com.carthing.data.repository

import androidx.room.withTransaction
import com.carthing.data.CarThingDatabase
import com.carthing.data.deadlines.DeadlineStatus
import com.carthing.data.entity.Deadline
import com.carthing.data.entity.Vehicle
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.ReminderPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DeadlineWithStatus(val deadline: Deadline, val status: DeadlineStatus)

data class DeadlineReminder(val vehicle: Vehicle, val deadline: Deadline, val status: DeadlineStatus)

class DeadlineRepository(
    private val db: CarThingDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val dao = db.deadlineDao()

    /** Deadlines for [vehicleId], soonest first. */
    fun observeWithStatus(vehicleId: Long): Flow<List<DeadlineWithStatus>> =
        dao.observeForVehicle(vehicleId).map { list ->
            val now = clock()
            list.map { DeadlineWithStatus(it, DeadlineStatus.of(it, now)) }
        }

    suspend fun getById(id: Long): Deadline? = dao.getById(id)

    suspend fun save(deadline: Deadline): Long {
        require(deadline.title.isNotBlank()) { "Title must not be blank" }
        require(deadline.repeatMonths == null || deadline.repeatMonths > 0) { "Repeat interval must be positive" }
        val id = dao.upsert(deadline.copy(title = deadline.title.trim()))
        return if (id == -1L) deadline.id else id
    }

    suspend fun delete(deadline: Deadline) = dao.delete(deadline)

    /** Moves [deadline] to [newDueEpochMillis] and starts a new reminder cycle. */
    suspend fun renew(deadline: Deadline, newDueEpochMillis: Long) {
        dao.upsert(deadline.copy(dueEpochMillis = newDueEpochMillis, notifiedLevel = 0))
    }

    /** Evaluates every deadline, stores the new notified levels and returns the reminders to post. */
    suspend fun collectReminders(): List<DeadlineReminder> = db.withTransaction {
        val now = clock()
        val reminders = mutableListOf<DeadlineReminder>()
        for (vehicle in db.vehicleDao().getAll()) {
            for (deadline in dao.getForVehicle(vehicle.id)) {
                val status = DeadlineStatus.of(deadline, now)
                val decision = ReminderPolicy.decide(enabled = true, notifiedLevel = deadline.notifiedLevel, dueLevel = status.level)
                if (decision.newNotifiedLevel != deadline.notifiedLevel) dao.setNotifiedLevel(deadline.id, decision.newNotifiedLevel)
                if (decision.notify != null) reminders += DeadlineReminder(vehicle, deadline, status)
            }
        }
        reminders
    }

    companion object {
        fun needsAttention(status: DeadlineStatus) = status.level == DueLevel.DUE_SOON || status.level == DueLevel.OVERDUE
    }
}
