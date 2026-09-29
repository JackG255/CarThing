package com.carthing.data.maintenance

/** One interval-based schedule (inspection or replacement) and when it was last done. */
data class Schedule(
    val intervalKm: Double?,
    val intervalMonths: Int?,
    val lastDoneEpochMillis: Long?,
    val lastDoneOdometerKm: Double?,
) {
    companion object {
        /** Null when neither interval is set, i.e. the component has no such schedule. */
        fun of(km: Double?, months: Int?, lastMillis: Long?, lastKm: Double?): Schedule? =
            if (km == null && months == null) null else Schedule(km, months, lastMillis, lastKm)
    }
}

enum class ScheduleKind { INSPECTION, REPLACEMENT }
