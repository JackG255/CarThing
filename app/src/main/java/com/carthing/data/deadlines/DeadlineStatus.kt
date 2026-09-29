package com.carthing.data.deadlines

import com.carthing.data.entity.Deadline
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.MaintenanceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DeadlineStatus(
    val level: DueLevel,
    /** Days until the due date: 0 on the day itself, negative once expired. */
    val daysLeft: Long,
    /** Share of the repeat period still left; null for one-off deadlines. */
    val fractionRemaining: Double? = null,
) {
    companion object {
        const val SOON_DAYS = 30L

        fun of(deadline: Deadline, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): DeadlineStatus {
            val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            val due = Instant.ofEpochMilli(deadline.dueEpochMillis).atZone(zone).toLocalDate()
            val days = due.toEpochDay() - today.toEpochDay()
            val level = when {
                days < 0 -> DueLevel.OVERDUE
                days <= SOON_DAYS -> DueLevel.DUE_SOON
                else -> DueLevel.OK
            }
            val fraction = deadline.repeatMonths?.let { days / (it * MaintenanceStatus.DAYS_PER_MONTH) }
            return DeadlineStatus(level, days, fraction)
        }

        /** The suggested next due date when renewing: the current one plus the repeat interval. */
        fun nextDue(deadline: Deadline, zone: ZoneId = ZoneId.systemDefault()): Long? = deadline.repeatMonths?.let {
            val due: LocalDate = Instant.ofEpochMilli(deadline.dueEpochMillis).atZone(zone).toLocalDate()
            due.plusMonths(it.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        }
    }
}

/** Quick-add presets; the user supplies the due date. */
object DeadlineTemplates {
    data class Template(val title: String, val repeatMonths: Int?)

    val all = listOf(
        Template("Technical inspection (STK)", 24),
        Template("Emissions check", 24),
        Template("Motorway vignette", 12),
        Template("Insurance", 12),
    )
}
