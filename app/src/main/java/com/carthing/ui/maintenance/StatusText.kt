package com.carthing.ui.maintenance

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.carthing.R
import com.carthing.data.maintenance.ComponentStatus
import com.carthing.data.maintenance.DueLevel
import com.carthing.data.maintenance.MaintenanceStatus
import com.carthing.data.maintenance.Schedule
import com.carthing.data.maintenance.ScheduleKind
import com.carthing.ui.common.formatDate
import com.carthing.ui.common.formatKm
import kotlin.math.abs

/** Short human description, e.g. "Due in ~800 km · 3 weeks" or "Overdue by 500 km". */
fun MaintenanceStatus.describe(res: Resources): String = when (level) {
    DueLevel.UNKNOWN -> res.getString(R.string.status_not_recorded)
    DueLevel.OVERDUE -> {
        val km = kmRemaining?.takeIf { it <= 0 }
        val days = daysRemaining?.takeIf { it <= 0 }
        when {
            km != null -> res.getString(R.string.status_overdue_by, formatKm(res, abs(km)))
            days != null && days == 0L -> res.getString(R.string.status_due_today)
            days != null -> res.getString(R.string.status_overdue_by, formatDays(res, abs(days)))
            else -> res.getString(R.string.status_overdue)
        }
    }
    else -> {
        val km = kmRemaining?.let { formatKm(res, it) }
        val time = daysUntilDue?.let { formatDays(res, it) }
        when {
            km != null && time != null -> res.getString(R.string.status_due_in_km_and_time, km, time)
            km != null -> res.getString(R.string.status_due_in_km, km)
            time != null -> res.getString(R.string.status_due_in_time, time)
            else -> res.getString(R.string.status_not_recorded)
        }
    }
}

/** [days] (≥ 0) in the coarsest fitting unit: days under 2 weeks, weeks under 60 days, months under 2 years, then years. */
fun formatDays(res: Resources, days: Long): String =
    formatSpan(res, days, R.plurals.days, R.plurals.weeks, R.plurals.months, R.plurals.years)

/**
 * [days] picked into a unit as in [formatDays], using one plurals resource per unit, so a phrase that
 * needs another grammatical case (e.g. "před 5 dny") can have its own set.
 */
internal fun formatSpan(
    res: Resources, days: Long,
    @PluralsRes daysId: Int, @PluralsRes weeksId: Int, @PluralsRes monthsId: Int, @PluralsRes yearsId: Int,
): String {
    val (id, count) = when {
        days < 14 -> daysId to days
        days < 60 -> weeksId to days / 7
        days < 730 -> monthsId to days / 30
        else -> yearsId to days / 365
    }
    return res.getQuantityString(id, count.toInt(), count.toInt())
}

/** E.g. "Replace every 15,000 km or 12 months · last 12 Mar 2026 at 50,000 km". */
fun Schedule.describe(res: Resources, kind: ScheduleKind): String {
    val km = intervalKm?.let { formatKm(res, it) }
    val months = intervalMonths
    val every = when {
        km != null && months != null -> res.getQuantityString(R.plurals.schedule_every_km_or_months, months, km, months)
        km != null -> res.getString(R.string.schedule_every_km, km)
        months != null -> res.getQuantityString(R.plurals.schedule_every_months, months, months)
        else -> null
    }
    val first = every?.let {
        res.getString(if (kind == ScheduleKind.INSPECTION) R.string.schedule_inspect else R.string.schedule_replace, it)
    }
    val last = lastDoneEpochMillis?.let { date ->
        lastDoneOdometerKm?.let { res.getString(R.string.schedule_last_at, formatDate(date), formatKm(res, it)) }
            ?: res.getString(R.string.schedule_last, formatDate(date))
    }
    // With a date but no reading, only the time limit is checked; say so rather than fail silently.
    val kmMissing = res.getString(R.string.schedule_km_not_recorded)
        .takeIf { intervalKm != null && lastDoneEpochMillis != null && lastDoneOdometerKm == null }
    return listOfNotNull(first, last, kmMissing).joinToString(" · ")
}

/** Status line for a component, naming the schedule when it has both, e.g. "Inspection: Due in 3 weeks". */
fun ComponentStatus.describe(res: Resources): String {
    val kind = primaryKind ?: return res.getString(R.string.status_no_schedule)
    val text = primary!!.describe(res)
    if (inspection == null || replacement == null) return text
    return res.getString(if (kind == ScheduleKind.INSPECTION) R.string.status_inspection else R.string.status_replacement, text)
}

@Composable fun MaintenanceStatus.describe(): String = describe(LocalContext.current.resources)
@Composable fun formatDays(days: Long): String = formatDays(LocalContext.current.resources, days)
@Composable fun Schedule.describe(kind: ScheduleKind): String = describe(LocalContext.current.resources, kind)
@Composable fun ComponentStatus.describe(): String = describe(LocalContext.current.resources)
