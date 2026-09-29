package com.carthing.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Synthetic receipts modelled on common Czech layouts, as rows the way [TextRows] produces them.
 * Real receipts may differ; add a fixture here for any that the rules get wrong.
 */
class FuelReceiptParserTest {
    private val today = LocalDate.of(2026, 9, 29)
    private fun parse(vararg rows: String) = FuelReceiptParser.parse(rows.toList(), today)

    @Test fun chainReceiptWithDiacritics() {
        val r = parse(
            "ORLEN Česká republika s.r.o.",
            "ČS 1234 Praha - Chodov",
            "IČO: 12345678   DIČ: CZ12345678",
            "Datum: 27.09.2026   Čas: 14:32",
            "Natural 95   stojan 3",
            "42,15 l   x   39,15 Kč/l",
            "1 650,17 Kč",
            "CELKEM K ÚHRADĚ   1 650,17 Kč",
            "DPH 21 %   286,40",
            "Platba kartou",
        )
        assertEquals(LocalDate.of(2026, 9, 27), r.date)
        assertEquals(42.15, r.liters!!, 1e-9)
        assertEquals(39.15, r.pricePerLiter!!, 1e-9)
        assertEquals(1650.17, r.total!!, 1e-9)
        assertEquals("ORLEN Česká republika s.r.o.", r.station)
        assertEquals("Natural 95", r.fuelType)
    }

    @Test fun supermarketStationShortDateAndDots() {
        val r = parse(
            "GLOBUS ČR, k.s.",
            "Čerpací stanice Brno",
            "27.9.26 08:11",
            "NAFTA",
            "38.40 L",
            "37.90 CZK/L",
            "SUMA   1455.36 CZK",
        )
        assertEquals(LocalDate.of(2026, 9, 27), r.date)
        assertEquals(38.40, r.liters!!, 1e-9)
        assertEquals(37.90, r.pricePerLiter!!, 1e-9)
        assertEquals(1455.36, r.total!!, 1e-9)
        assertEquals("Diesel", r.fuelType)
        assertTrue(r.station!!.startsWith("GLOBUS"))
    }

    @Test fun quantityLabelledWithoutUnit() {
        val r = parse(
            "Shell Czech Republic a.s.",
            "Mnozstvi:   40,002",
            "Jedn. cena:   38,90",
            "Celkem:   1 556,08 Kc",
            "2026-09-20 17:45",
        )
        assertEquals(40.002, r.liters!!, 1e-9)
        assertEquals(38.90, r.pricePerLiter!!, 1e-9)
        assertEquals(1556.08, r.total!!, 1e-9)
        assertEquals(LocalDate.of(2026, 9, 20), r.date)
    }

    @Test fun missingLitersAreDerivedFromTotalAndUnitPrice() {
        val r = parse("MOL", "Natural 95   39,90 Kč/l", "Celkem   1 197,00 Kč")
        assertEquals(30.0, r.liters!!, 1e-9)
        assertEquals(1197.0, r.total!!, 1e-9)
    }

    @Test fun missingTotalIsDerivedFromLitersAndUnitPrice() {
        val r = parse("OMV", "35,50 l   38,00 Kč/l")
        assertEquals(1349.0, r.total!!, 1e-9)
    }

    @Test fun inconsistentTotalIsDropped() {
        // Recognition misread the total; liters x price doesn't match, so don't trust it.
        val r = parse("MOL", "40,00 l   39,00 Kč/l", "CELKEM   9 999,00 Kč")
        assertEquals(40.0, r.liters!!, 1e-9)
        assertNull(r.total)
    }

    @Test fun vatTableIsNotTakenAsTotal() {
        val r = parse(
            "EuroOil",
            "Diesel   45,00 l",
            "Základ DPH   1 380,17",
            "DPH 21%   289,83",
            "Celkem   1 670,00 Kč",
        )
        assertEquals(1670.0, r.total!!, 1e-9)
    }

    @Test fun futureAndDatumLabelledDates() {
        // A future date (e.g. a warranty or promo) is ignored; a "Datum" row wins over others.
        val r = parse("Benzina", "Platnost kupónu do 31.12.2026", "Datum 25.09.2026", "10,00 l")
        assertEquals(LocalDate.of(2026, 9, 25), r.date)
    }

    @Test fun businessIdsAreNotReadAsAmounts() {
        val r = parse("TANK ONO", "IČO 27123456", "DIČ CZ27123456", "Tel 777 123 456")
        assertNull(r.liters)
        assertNull(r.total)
        assertTrue(r.isEmpty.not()) // the station is still found
    }

    @Test fun unrelatedTextYieldsNothing() {
        assertTrue(parse("Hello world", "Lorem ipsum").let { it.liters == null && it.total == null && it.date == null })
    }
}

class NumbersTest {
    @Test fun czechFormats() {
        assertEquals(1650.17, Numbers.parse("1 650,17")!!, 1e-9)
        assertEquals(1650.17, Numbers.parse("1.650,17")!!, 1e-9)
        assertEquals(42.15, Numbers.parse("42,15")!!, 1e-9)
        assertEquals(42.15, Numbers.parse("42.150")!!, 1e-9) // dot without comma decimal is a decimal point
        assertEquals(1650.0, Numbers.parse("1 650")!!, 1e-9)
    }

    @Test fun findAllKeepsGroupedNumbersTogether() {
        assertEquals(listOf(42.15, 39.15, 1650.17), Numbers.findAll("42,15 l x 39,15 Kč/l = 1 650,17 Kč").map { it.value })
    }
}

class TextRowsTest {
    @Test fun piecesAtTheSameHeightJoinLeftToRight() {
        val rows = TextRows.group(listOf(
            TextPiece("1 650,17 Kč", 400, 1000, 700, 1040),
            TextPiece("CELKEM", 60, 1002, 250, 1042),
            TextPiece("Natural 95", 60, 640, 300, 680),
        ))
        assertEquals(listOf("Natural 95", "CELKEM   1 650,17 Kč"), rows)
    }

    @Test fun slightlySlantedRowStaysTogetherButNextLineSplits() {
        val rows = TextRows.group(listOf(
            TextPiece("42,15 l", 60, 700, 200, 740),
            TextPiece("39,15 Kč/l", 400, 712, 600, 752), // 12 px lower: still the same row
            TextPiece("1 650,17 Kč", 400, 780, 700, 820), // next line
        ))
        assertEquals(listOf("42,15 l   39,15 Kč/l", "1 650,17 Kč"), rows)
    }
}

class TiltedRowsTest {
    @Test fun tiltedReceiptKeepsLabelAndValueOnOneRow() {
        // Rotated 4° clockwise: pieces further right sit lower (~0.07 px per px).
        fun piece(text: String, x: Int, y: Int, w: Int) = TextPiece(text, x, y, x + w, y + 36, angleDegrees = 4f)
        val rows = TextRows.group(listOf(
            piece("Mnozstvi:", 165, 660, 140), piece("40,002", 430, 680, 100),
            piece("Jedn. cena:", 160, 732, 170), piece("38,90", 445, 752, 90),
            piece("Celkem:", 140, 1015, 150), piece("1 556,08 Kc", 385, 1040, 210),
        ))
        assertEquals(listOf("Mnozstvi:   40,002", "Jedn. cena:   38,90", "Celkem:   1 556,08 Kc"), rows)
        assertEquals(40.002, FuelReceiptParser.parse(rows, LocalDate.of(2026, 9, 29)).liters!!, 1e-9)
    }
}

/** Rows exactly as ML Kit returned them for the generated test receipts on a real phone. */
class RecognitionSlipsTest {
    private val today = LocalDate.of(2026, 9, 29)

    @Test fun litreUnitReadAsDigitAndSpaceAfterDecimalComma() {
        val r = FuelReceiptParser.parse(listOf(
            "TEST STATION S.r.o.", "Hlavni 12, Praha", "ICO: 12345678", "Datum : 27.09.2026 14:32", "Stojan: 3",
            "Natural 95", "42,15 1 x 39, 15 Kc/l", "1 650, 17 Kc", "CELKEM   1 650,17 Kc", "Platba kartou",
            "Dekujeme za navsteVu",
        ), today)
        assertEquals(42.15, r.liters!!, 1e-9)
        assertEquals(39.15, r.pricePerLiter!!, 1e-9)
        assertEquals(1650.17, r.total!!, 1e-9)
        assertEquals(LocalDate.of(2026, 9, 27), r.date)
    }

    @Test fun repairLeavesNormalTextAlone() {
        assertEquals("42,15 L X 39,15 KC/L", FuelReceiptParser.repair("42,15 L X 39,15 KC/L"))
        assertEquals("STOJAN: 3", FuelReceiptParser.repair("STOJAN: 3"))
        assertEquals("IČO 1 X", FuelReceiptParser.repair("IČO 1 X")) // no decimal before the 1
    }

    @Test fun strayNumberIsNotTakenAsUnitPrice() {
        // "15 Kč/l" is not a plausible fuel price, so liters aren't derived from it.
        val r = FuelReceiptParser.parse(listOf("MOL", "15 Kc/l", "Celkem 1 650,17 Kc"), today)
        assertEquals(null, r.pricePerLiter)
        assertEquals(null, r.liters)
        assertEquals(1650.17, r.total!!, 1e-9)
    }
}
