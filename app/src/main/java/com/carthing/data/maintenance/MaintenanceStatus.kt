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
) {
    /** Soonest of the time limit and the predicted distance limit. */
    val daysUntilDue: Long? get() = listOfNotNull(daysRemaining, predictedDaysByKm).minOrNull()

    companion object {
        private const val SOON_KM = 1_000.0
        private const val SOON_DAYS = 30L

        /** [recordedOdometerKm] is the highest known reading, used when usage can't project further. */
        fun of(
            item: MaintenanceItem, usage: Usage, recordedOdometerKm: Double, nowMillis: Long,
            zone: ZoneId = ZoneId.systemDefault()
        ): MaintenanceStatus {
            val odometer = maxOf(recordedOdometerKm, usage.estimatedOdometerKm(nowMillis) ?: 0.0)
            val kmRemaining = item.intervalKm?.let { interval ->
                val base = item.lastDoneOdometerKm ?: return@let null
                base + interval - odometer
            }
            val daysRemaining = item.intervalMonths?.let { months ->
                val base = item.lastDoneEpochMillis ?: return@let null
                val due = Instant.ofEpochMilli(base).atZone(zone).plusMonths(months.toLong()).toInstant().toEpochMilli()
                Math.floorDiv(due - nowMillis, Usage.DAY_MILLIS)
            }
            val predicted = kmRemaining?.let { km ->
                val rate = usage.kmPerDay?.takeIf { it > 0 } ?: return@let null
                if (km <= 0) 0L else ceil(km / rate).toLong()
            }

            // "Soon" scales down for short intervals, so a monthly check isn't permanently due soon.
            val soonKm = item.intervalKm?.let { min(SOON_KM, it / 10) } ?: SOON_KM
            val soonDays = item.intervalMonths?.let { min(SOON_DAYS, it * 30L / 4) } ?: SOON_DAYS

            val level = when {
                kmRemaining == null && daysRemaining == null -> DueLevel.UNKNOWN
                (kmRemaining ?: 1.0) <= 0 || (daysRemaining ?: 1L) <= 0 -> DueLevel.OVERDUE
                (kmRemaining ?: Double.MAX_VALUE) <= soonKm ||
                    (daysRemaining ?: Long.MAX_VALUE) <= soonDays ||
                    (predicted ?: Long.MAX_VALUE) <= soonDays -> DueLevel.DUE_SOON
                else -> DueLevel.OK
            }
            return MaintenanceStatus(level, kmRemaining, daysRemaining, predicted)
        }
    }
}
