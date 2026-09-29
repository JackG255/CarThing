package com.carthing.ui.maintenance

import com.carthing.data.maintenance.ComponentStatus
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.MaintenanceStatus
import com.carthing.data.maintenance.Schedule
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.ui.common.formatDate
import com.carthing.ui.common.formatKm
import kotlin.math.abs

/** Short human description, e.g. "Due in ~800 km · 3 weeks" or "Overdue by 500 km". */
fun MaintenanceStatus.describe(): String = when (level) {
    DueLevel.UNKNOWN -> "Not recorded yet"
    DueLevel.OVERDUE -> {
        val km = kmRemaining?.takeIf { it <= 0 }
        val days = daysRemaining?.takeIf { it <= 0 }
        when {
            km != null -> "Overdue by ${formatKm(abs(km))}"
            days != null && days == 0L -> "Due today"
            days != null -> "Overdue by ${formatDays(abs(days))}"
            else -> "Overdue"
        }
    }
    else -> "Due in " + listOfNotNull(
        kmRemaining?.let { "~${formatKm(it)}" },
        daysUntilDue?.let { formatDays(it) }
    ).joinToString(" · ")
}

fun formatDays(days: Long): String = when {
    days < 14 -> if (days == 1L) "1 day" else "$days days"
    days < 60 -> "${days / 7} weeks"
    days < 730 -> "${days / 30} months"
    else -> "${days / 365} years"
}

/** E.g. "Replace every 15,000 km or 12 months · last 12 Mar 2026 at 50,000 km". */
fun Schedule.describe(kind: ScheduleKind): String {
    val every = listOfNotNull(
        intervalKm?.let { formatKm(it) },
        intervalMonths?.let { if (it == 1) "month" else "$it months" }
    ).joinToString(" or ")
    val last = lastDoneEpochMillis?.let { date ->
        "last ${formatDate(date)}" + (lastDoneOdometerKm?.let { " at ${formatKm(it)}" } ?: "")
    }
    // With a date but no reading, only the time limit is checked; say so rather than fail silently.
    val kmMissing = "km not recorded".takeIf { intervalKm != null && lastDoneEpochMillis != null && lastDoneOdometerKm == null }
    return listOfNotNull("${kind.verb()} every $every", last, kmMissing).joinToString(" · ")
}

fun ScheduleKind.verb(): String = if (this == ScheduleKind.INSPECTION) "Inspect" else "Replace"
fun ScheduleKind.noun(): String = if (this == ScheduleKind.INSPECTION) "inspection" else "replacement"

/** Status line for a component, naming the schedule when it has both, e.g. "Inspection: Due in 3 weeks". */
fun ComponentStatus.describe(): String {
    val kind = primaryKind ?: return "No schedule"
    val text = primary!!.describe()
    return if (inspection != null && replacement != null) "${kind.noun().replaceFirstChar { it.uppercase() }}: $text" else text
}
