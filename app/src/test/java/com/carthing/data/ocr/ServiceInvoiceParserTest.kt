package com.carthing.data.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Synthetic invoices modelled on common Czech layouts, as rows the way [TextRows] produces them. */
class ServiceInvoiceParserTest {
    private val today = LocalDate.of(2026, 9, 29)
    private fun parse(vararg rows: String) = ServiceInvoiceParser.parse(rows.toList(), today)

    @Test fun typicalInvoiceWithVatTable() {
        val r = parse(
            "FAKTURA - DAŇOVÝ DOKLAD č. 20260412",
            "Dodavatel:   AutoServis Novák s.r.o.",
            "Průmyslová 5, 602 00 Brno",
            "IČO: 12345678   DIČ: CZ12345678",
            "Odběratel:   Jan Kolář",
            "Datum vystavení: 26.09.2026   Datum splatnosti: 10.10.2026",
            "DUZP: 25.09.2026",
            "Stav km: 94 020",
            "Výměna oleje a filtru   1   450,00",
            "Motorový olej 5W-30 4 l   1   1 280,00",
            "Olejový filtr   1   320,00",
            "Vzduchový filtr   1   390,00",
            "Celkem bez DPH   2 440,00",
            "DPH 21 %   512,40",
            "Celkem k úhradě   2 952,40 Kč",
        )
        assertEquals(LocalDate.of(2026, 9, 25), r.date)
        assertEquals(2952.40, r.total!!, 1e-9)
        assertEquals("AutoServis Novák s.r.o.", r.shop)
        assertEquals(94020.0, r.odometerKm!!, 1e-9)
        assertEquals(listOf("Oil change", "Filters"), r.services)
    }

    @Test fun shortShopReceipt() {
        val r = parse(
            "PNEUSERVIS U Mostu",
            "Přezutí 4 kol   800,- Kč",
            "Vyvážení   400,- Kč",
            "Celkem   1 200,- Kč",
            "12.4.26",
        )
        assertEquals("PNEUSERVIS U Mostu", r.shop)
        assertEquals(1200.0, r.total!!, 1e-9)
        assertEquals(LocalDate.of(2026, 4, 12), r.date)
        assertEquals(listOf("Tires"), r.services)
        assertNull(r.odometerKm)
    }

    @Test fun netTotalIsNotTakenAsPaidAmount() {
        val r = parse("Autodílna Pokorný", "Celkem bez DPH   1 000,00", "DPH 21%   210,00", "Celkem s DPH   1 210,00")
        assertEquals(1210.0, r.total!!, 1e-9)
    }

    @Test fun kilometresAfterNumberButNotPrice() {
        val r = parse("Servis Brno", "Brzdové destičky přední   1 950 Kč", "Najeto 101 450 km", "Celkem 1 950 Kč")
        assertEquals(101450.0, r.odometerKm!!, 1e-9)
        assertEquals(listOf("Brakes"), r.services)
    }

    @Test fun inspectionStation() {
        val r = parse("STK Brno - Líšeň", "Technická kontrola M1   1 000,00", "Měření emisí   550,00", "K úhradě   1 550,00 Kč")
        assertEquals(1550.0, r.total!!, 1e-9)
        assertEquals("Inspection", r.services.first())
    }

    @Test fun dueDateAloneIsUsedWhenNothingElse() {
        assertEquals(LocalDate.of(2026, 9, 1), parse("Servis", "Splatnost 01.09.2026").date)
    }

    @Test fun unrelatedTextYieldsNothingUseful() {
        val r = parse("12 345", "---")
        assertTrue(r.total == null && r.date == null && r.services.isEmpty() && r.odometerKm == null)
    }
}
