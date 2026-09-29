package com.carthing.ui.maintenance

import com.carthing.data.entity.MaintenanceItem
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.MaintenanceStatus
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

/** E.g. "Every 15,000 km or 12 months · last 12 Mar 2026 at 50,000 km". */
fun MaintenanceItem.describeSchedule(): String {
    val every = listOfNotNull(
        intervalKm?.let { formatKm(it) },
        intervalMonths?.let { if (it == 1) "month" else "$it months" }
    ).joinToString(" or ")
    val last = lastDoneEpochMillis?.let { date ->
        "last ${formatDate(date)}" + (lastDoneOdometerKm?.let { " at ${formatKm(it)}" } ?: "")
    }
    return listOfNotNull("Every $every", last).joinToString(" · ")
}
