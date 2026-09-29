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
        assertEquals(listOf(123456.0), OdometerParser.candidates(listOf("88", "123456")))
        assertEquals(listOf(523.0, 88.0), OdometerParser.candidates(listOf("88", "523")))
    }

    /** Rows exactly as ML Kit returned them for a real Škoda Fabia III cluster photo. */
    @Test fun realFabiaClusterPhoto() {
        val rows = listOf(
            "40   7:59   100   120 -", "50", "Ø spotřeba   80", "70 vookm", "0d načerpání", "11.0c", "km   trip",
            "94020   386.5   20", "g0", "-   O km/lh", "130", "0.0 /SET",
        )
        assertEquals(listOf(94020.0), OdometerParser.candidates(rows))
        assertEquals(listOf(94020.0), OdometerParser.candidates(rows, expectedKm = 93_500.0))
    }

    @Test fun datesAndDecimalsAreNotReadings() {
        assertTrue(OdometerParser.candidates(listOf("29/09/2026", "12,5", "3.14")).isEmpty())
    }
}
