package com.carthing.data.ocr

import com.carthing.data.ocr.FuelReceiptParser.fold
import kotlin.math.abs

/**
 * Finds the odometer reading in a dashboard photo's text. A cluster shows many numbers (trip,
 * range, clock, temperature, consumption), so this returns a few candidates, best first, for the
 * user to pick from rather than a single answer.
 */
object OdometerParser {
    /** Whole kilometres, optionally grouped ("94 020", "94.020"); not part of a decimal, time or date. */
    private val NUMBER = Regex("""(?<![\d.,:/-])(\d{1,3}(?:[ .]\d{3})+|\d{2,7})(?![\d]|[.,]\d|\s*:\s*\d|\s*[/°])""")
    /** Rows that show something else: trip meters, range, consumption, speed, clock. */
    private val OTHER_ROWS = listOf("TRIP", "RANGE", "DOJEZD", "L/100", "KM/H", "RPM", "AVG", "PRUM", "SPOTR", "°C", "MPH")
    private val ODO_WORDS = listOf("ODO", "CELKEM", "TOTAL", "TACHO")
    private val KM_UNIT = Regex("""^\s*KM\b(?!\s*/)""")
    private const val MAX_KM = 2_000_000.0

    /**
     * Candidate readings, best first, at most [limit]. With [expectedKm] (the last known reading),
     * values at or slightly below it and closest to it win; much lower ones are dropped.
     */
    fun candidates(rows: List<String>, expectedKm: Double? = null, limit: Int = 3): List<Double> {
        data class Candidate(val km: Double, val score: Int)
        val found = rows.map(::fold).filter { row -> OTHER_ROWS.none { it in row } }.flatMap { row ->
            NUMBER.findAll(row).mapNotNull { m ->
                val km = m.value.replace(" ", "").replace(".", "").toDoubleOrNull()?.takeIf { it in 10.0..MAX_KM }
                    ?: return@mapNotNull null
                var score = m.value.count(Char::isDigit) // longer numbers look more like an odometer
                if (KM_UNIT.containsMatchIn(row.substring(m.range.last + 1))) score += 5
                if (ODO_WORDS.any { it in row }) score += 5
                Candidate(km, score)
            }.toList()
        }
        val ranked = if (expectedKm != null && expectedKm > 0) {
            // A car doesn't lose kilometres; allow a little below for a stale or rounded last reading.
            found.filter { it.km >= expectedKm - 100 }.sortedWith(compareBy({ abs(it.km - expectedKm) }, { -it.score }))
        } else {
            found.sortedByDescending { it.score }
        }
        return ranked.map { it.km }.distinct().take(limit)
    }
}
