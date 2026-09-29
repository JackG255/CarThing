package com.carthing.data.maintenance

/** An odometer reading at a point in time, from any fuel or service entry. */
data class OdometerReading(val epochMillis: Long, val odometerKm: Double)

/** How much a vehicle is driven, estimated from its recorded readings. */
data class Usage(
    val lastReading: OdometerReading?,
    /** Average km per day; null when there isn't enough history to estimate it. */
    val kmPerDay: Double?
) {
    /** Odometer projected to [nowMillis]; never below the last recorded reading. */
    fun estimatedOdometerKm(nowMillis: Long): Double? {
        val last = lastReading ?: return null
        val rate = kmPerDay ?: return last.odometerKm
        val days = (nowMillis - last.epochMillis).coerceAtLeast(0) / DAY_MILLIS.toDouble()
        return last.odometerKm + rate * days
    }

    companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
        private const val WINDOW_DAYS = 90L
        private const val MIN_SPAN_DAYS = 7L

        /**
         * Estimates km/day from readings in the [WINDOW_DAYS] before the latest one, falling back to
         * all history when that window is too short. Needs readings at least [MIN_SPAN_DAYS] apart.
         */
        fun estimate(readings: List<OdometerReading>): Usage {
            val last = readings.maxWithOrNull(compareBy({ it.odometerKm }, { it.epochMillis }))
                ?: return Usage(null, null)
            val windowStart = last.epochMillis - WINDOW_DAYS * DAY_MILLIS
            val rate = rate(readings.filter { it.epochMillis >= windowStart }) ?: rate(readings)
            return Usage(last, rate)
        }

        private fun rate(readings: List<OdometerReading>): Double? {
            val first = readings.minByOrNull { it.epochMillis } ?: return null
            val last = readings.maxByOrNull { it.epochMillis } ?: return null
            val days = (last.epochMillis - first.epochMillis) / DAY_MILLIS.toDouble()
            if (days < MIN_SPAN_DAYS) return null
            return ((last.odometerKm - first.odometerKm) / days).takeIf { it >= 0 }
        }
    }
}
