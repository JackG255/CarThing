package com.carthing.data.maintenance

import com.carthing.data.entity.MaintenanceItem
import java.time.Instant
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.min

enum class DueLevel(val notifyLevel: Int) { UNKNOWN(0), OK(0), DUE_SOON(1), OVERDUE(2) }

data class MaintenanceStatus(
    val level: DueLevel,
    /** Km left on the distance limit (negative when overdue); null when not tracked. */
    val kmRemaining: Double?,
    /** Days left on the time limit (negative when overdue); null when not tracked. */
    val daysRemaining: Long?,
    /** Days until the distance limit at the current driving rate; null without a rate. */
    val predictedDaysByKm: Long?,
    /** Share of the interval still left, by whichever limit is closer (0 or less when overdue); null when unknown. */
    val fractionRemaining: Double? = null,
) {
    /** Soonest of the time limit and the predicted distance limit. */
    val daysUntilDue: Long? get() = listOfNotNull(daysRemaining, predictedDaysByKm).minOrNull()

    companion object {
        private const val SOON_KM = 1_000.0
        private const val SOON_DAYS = 30L
        const val DAYS_PER_MONTH = 30.44

        /** Status of one [schedule]; [recordedOdometerKm] is the highest known reading, used when usage can't project further. */
        fun of(
            schedule: Schedule, usage: Usage, recordedOdometerKm: Double, nowMillis: Long,
            zone: ZoneId = ZoneId.systemDefault()
        ): MaintenanceStatus {
            val odometer = maxOf(recordedOdometerKm, usage.estimatedOdometerKm(nowMillis) ?: 0.0)
            val kmRemaining = schedule.intervalKm?.let { interval ->
                val base = schedule.lastDoneOdometerKm ?: return@let null
                base + interval - odometer
            }
            val daysRemaining = schedule.intervalMonths?.let { months ->
                val base = schedule.lastDoneEpochMillis ?: return@let null
                val due = Instant.ofEpochMilli(base).atZone(zone).plusMonths(months.toLong()).toInstant().toEpochMilli()
                Math.floorDiv(due - nowMillis, Usage.DAY_MILLIS)
            }
            val predicted = kmRemaining?.let { km ->
                val rate = usage.kmPerDay?.takeIf { it > 0 } ?: return@let null
                if (km <= 0) 0L else ceil(km / rate).toLong()
            }

            // "Soon" scales down for short intervals, so a monthly check isn't permanently due soon.
            val soonKm = schedule.intervalKm?.let { min(SOON_KM, it / 10) } ?: SOON_KM
            val soonDays = schedule.intervalMonths?.let { min(SOON_DAYS, it * 30L / 4) } ?: SOON_DAYS

            val level = when {
                kmRemaining == null && daysRemaining == null -> DueLevel.UNKNOWN
                (kmRemaining ?: 1.0) <= 0 || (daysRemaining ?: 1L) <= 0 -> DueLevel.OVERDUE
                (kmRemaining ?: Double.MAX_VALUE) <= soonKm ||
                    (daysRemaining ?: Long.MAX_VALUE) <= soonDays ||
                    (predicted ?: Long.MAX_VALUE) <= soonDays -> DueLevel.DUE_SOON
                else -> DueLevel.OK
            }
            val kmFraction = kmRemaining?.let { km -> schedule.intervalKm?.let { km / it } }
            val timeFraction = daysRemaining?.let { days -> schedule.intervalMonths?.let { days / (it * DAYS_PER_MONTH) } }
            val fraction = listOfNotNull(kmFraction, timeFraction).minOrNull()
            return MaintenanceStatus(level, kmRemaining, daysRemaining, predicted, fraction)
        }
    }
}

/** Status of a component's schedules; either is null when the component doesn't have it. */
data class ComponentStatus(val inspection: MaintenanceStatus?, val replacement: MaintenanceStatus?) {
    /** The more urgent schedule, preferring replacement on a tie; null only with no schedules at all. */
    val primaryKind: ScheduleKind?
        get() = when {
            inspection == null -> replacement?.let { ScheduleKind.REPLACEMENT }
            replacement == null -> ScheduleKind.INSPECTION
            urgency(inspection) < urgency(replacement) -> ScheduleKind.INSPECTION
            else -> ScheduleKind.REPLACEMENT
        }

    val primary: MaintenanceStatus?
        get() = when (primaryKind) {
            ScheduleKind.INSPECTION -> inspection
            ScheduleKind.REPLACEMENT -> replacement
            null -> null
        }

    val level: DueLevel get() = primary?.level ?: DueLevel.UNKNOWN

    fun of(kind: ScheduleKind): MaintenanceStatus? = if (kind == ScheduleKind.INSPECTION) inspection else replacement

    companion object {
        private val levelOrder = listOf(DueLevel.OVERDUE, DueLevel.DUE_SOON, DueLevel.UNKNOWN, DueLevel.OK)

        /** Lower is more urgent: by level, then by days until due. */
        fun urgency(s: MaintenanceStatus): Pair<Int, Long> = levelOrder.indexOf(s.level) to (s.daysUntilDue ?: Long.MAX_VALUE)

        private operator fun Pair<Int, Long>.compareTo(other: Pair<Int, Long>): Int =
            compareValuesBy(this, other, { it.first }, { it.second })

        fun of(item: MaintenanceItem, usage: Usage, recordedOdometerKm: Double, nowMillis: Long,
               zone: ZoneId = ZoneId.systemDefault()) = ComponentStatus(
            item.inspection?.let { MaintenanceStatus.of(it, usage, recordedOdometerKm, nowMillis, zone) },
            item.replacement?.let { MaintenanceStatus.of(it, usage, recordedOdometerKm, nowMillis, zone) }
        )
    }
}
