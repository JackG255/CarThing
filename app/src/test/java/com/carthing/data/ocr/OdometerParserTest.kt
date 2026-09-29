package com.carthing.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic dashboard texts, as rows the way [TextRows] produces them. */
class OdometerParserTest {
    private val cluster = listOf(
        "14:32   22°C",
        "TRIP A   523.4 km",
        "Range 480 km",
        "5.8 l/100km",
        "94 215 km",
    )

    @Test fun picksTheOdometerOverTripRangeClockAndTemperature() {
        assertEquals(listOf(94215.0), OdometerParser.candidates(cluster))
    }

    @Test fun expectedReadingRanksTheClosestFirstAndDropsLowerOnes() {
        val rows = listOf("094215", "1234", "ODO 94 215")
        assertEquals(94215.0, OdometerParser.candidates(rows, expectedKm = 94_020.0).first(), 1e-9)
        assertTrue(1234.0 !in OdometerParser.candidates(rows, expectedKm = 94_020.0))
    }

    @Test fun withoutAnythingElseTheLongestNumberWins() {
        assertEquals(listOf(123456.0, 88.0), OdometerParser.candidates(listOf("88", "123456")))
    }

    @Test fun datesAndDecimalsAreNotReadings() {
        assertTrue(OdometerParser.candidates(listOf("29/09/2026", "12,5", "3.14")).isEmpty())
    }
}
