package com.carthing.data.ocr

import java.text.Normalizer
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong

/** What could be read from a fuel receipt; anything not found reliably is null. */
data class FuelReceipt(
    val date: LocalDate? = null,
    val liters: Double? = null,
    val pricePerLiter: Double? = null,
    val total: Double? = null,
    val station: String? = null,
    val fuelType: String? = null,
) {
    val isEmpty get() = date == null && liters == null && total == null && station == null
}

/**
 * Rules for typical Czech fuel receipts. Works on rows of text (see [TextRows]); tolerant of
 * missing diacritics ("Kc", "Mnozstvi") since recognition and thermal printers often drop them.
 */
object FuelReceiptParser {
    private val BRANDS = listOf(
        "ORLEN", "BENZINA", "SHELL", "OMV", "MOL", "EUROOIL", "GLOBUS", "TANK ONO", "ROBIN OIL", "PRIM",
        "KM PRONA", "MAKRO", "CIRCLE K", "LUKOIL", "SLOVNAFT", "ARAL", "ESSO", "TOTAL", "AGIP", "ENI",
        "KAUFLAND", "TESCO", "AVIA", "PAP OIL", "TANKPOINT", "CERPACI STANICE",
    )
    private val FUELS = listOf(
        "NATURAL 95" to "Natural 95", "NATURAL 98" to "Natural 98", "BA 95" to "Natural 95", "BA 98" to "Natural 98",
        "VERVA" to "Verva", "V-POWER" to "V-Power", "EFECTA" to "Efecta", "MAXXMOTION" to "MaxxMotion",
        "DIESEL" to "Diesel", "NAFTA" to "Diesel", "LPG" to "LPG", "CNG" to "CNG", "AD BLUE" to "AdBlue",
    )
    private val TOTAL_WORDS = listOf("CELKEM", "K UHRADE", "UHRADA", "TOTAL", "SUMA", "CASTKA", "ZAPLACENO", "PLATBA")
    private val LITER_WORDS = listOf("MNOZSTVI", "OBJEM", "LITRU", "LITRY", "QTY")
    private val UNIT_PRICE_WORDS = listOf("CENA/L", "CENA ZA L", "JEDN. CENA", "JEDN.CENA", "J.CENA", "CENA/J")
    /** Rows with these are about the business or tax, never the fill-up amounts. */
    private val IGNORE_WORDS = listOf("ICO", "DIC", "DPH", "ZAKLAD", "TEL", "IBAN", "EAN")

    private val LITER_UNIT = Regex("""^\s*(l|lt|ltr|litr\w*)\b(?!\s*/)""", RegexOption.IGNORE_CASE)
    private val PER_LITER = Regex("""^\s*(kc|czk)?\s*/\s*l\b""", RegexOption.IGNORE_CASE)
    /** Money, but not a price per litre ("Kč/l"). */
    private val CURRENCY = Regex("""^\s*(kc|czk)\b(?!\s*/)""", RegexOption.IGNORE_CASE)

    fun parse(rows: List<String>, today: LocalDate = LocalDate.now()): FuelReceipt {
        val plain = rows.map { repair(fold(it)) }
        // Amounts and units are read from the folded text ("Kč" -> "KC"); original rows are kept for names.
        val liters = findLiters(plain)
        val unitPrice = findUnitPrice(plain)
        var total = findTotal(plain, liters, unitPrice)

        // Cross-check the three amounts; fill a missing one only when the other two are present.
        var litersOut = liters
        when {
            liters != null && unitPrice != null && total != null ->
                if (abs(liters * unitPrice - total) > maxOf(1.0, total * 0.02)) total = null
            liters != null && unitPrice != null && total == null -> total = round2(liters * unitPrice)
            liters == null && unitPrice != null && total != null -> litersOut = round2(total / unitPrice)
        }
        return FuelReceipt(
            date = ReceiptDates.find(plain, today),
            liters = litersOut?.takeIf { it in 0.5..300.0 },
            pricePerLiter = unitPrice,
            total = total,
            station = findStation(rows, plain),
            fuelType = FUELS.firstOrNull { (key, _) -> plain.any { key in it } }?.second,
        )
    }

    /** Plausible fuel prices in Kč per litre; filters out stray numbers taken for a unit price. */
    private val PRICE_PER_LITER = 20.0..80.0

    /**
     * Fixes common recognition slips on receipts: a space after the decimal comma ("39, 15"), and
     * the litre unit read as a digit or bar ("42,15 1 x 39,15"). Works on [fold]ed text.
     */
    fun repair(s: String): String = s
        .replace(Regex("""(\d),\s+(\d{2,3})\b"""), "$1,$2")
        .replace(Regex("""(\d[.,]\d{1,3})\s+[1I|](?=\s*(X|\*|$))"""), "$1 L")

    /** Upper case without diacritics, so "Množství"/"MNOZSTVI" and "Kč"/"KC" compare equal. */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "").uppercase()

    private fun findLiters(plain: List<String>): Double? {
        // 1) A number followed by a litre unit ("42,15 l", "42.150 L"), but not a price per litre ("39,15 Kč/l").
        for ((i, row) in plain.withIndex()) {
            if (ignored(plain[i])) continue
            Numbers.findAll(row).firstOrNull { LITER_UNIT.containsMatchIn(row.substring(it.end)) }?.let { return it.value }
        }
        // 2) A number on a row labelled as quantity ("Množství: 42,15").
        for ((i, row) in plain.withIndex()) {
            if (LITER_WORDS.any { it in plain[i] } && !ignored(plain[i])) {
                Numbers.findAll(row).firstOrNull { it.value in 0.5..300.0 && !PER_LITER.containsMatchIn(row.substring(it.end)) }
                    ?.let { return it.value }
            }
        }
        return null
    }

    private fun findUnitPrice(plain: List<String>): Double? {
        for ((i, row) in plain.withIndex()) {
            Numbers.findAll(row).firstOrNull { PER_LITER.containsMatchIn(row.substring(it.end)) && it.value in PRICE_PER_LITER }
                ?.let { return it.value }
            if (UNIT_PRICE_WORDS.any { it in plain[i] }) {
                Numbers.findAll(row).firstOrNull { it.value in PRICE_PER_LITER }?.let { return it.value }
            }
        }
        return null
    }

    private fun findTotal(plain: List<String>, liters: Double?, unitPrice: Double?): Double? {
        fun amounts(i: Int) = Numbers.findAll(plain[i]).filter { it.value >= 1.0 }
        // 1) The largest amount on a row with a total keyword; the first such row wins.
        for (i in plain.indices) {
            if (TOTAL_WORDS.any { it in plain[i] } && !ignored(plain[i])) {
                amounts(i).maxByOrNull { it.value }?.let { return it.value }
            }
        }
        // 2) An amount that matches litres x unit price.
        if (liters != null && unitPrice != null) {
            val expected = liters * unitPrice
            plain.indices.flatMap(::amounts).firstOrNull { abs(it.value - expected) <= maxOf(1.0, expected * 0.02) }?.let { return it.value }
        }
        // 3) The largest amount marked as money ("... Kč").
        return plain.indices.filterNot { ignored(plain[it]) }
            .flatMap { i -> amounts(i).filter { CURRENCY.containsMatchIn(plain[i].substring(it.end)) } }
            .maxByOrNull { it.value }?.value
    }

    private fun findStation(rows: List<String>, plain: List<String>): String? {
        BRANDS.firstOrNull { brand -> plain.any { brand in it } }?.let { brand ->
            return rows[plain.indexOfFirst { brand in it }].trim().takeIf { it.length <= 40 } ?: pretty(brand)
        }
        // Otherwise the first mostly-letters line near the top (typically the operator's name).
        return rows.take(4).map { it.trim() }.firstOrNull { row ->
            val letters = row.count { it.isLetter() }
            letters >= 4 && letters >= row.length * 0.6 && !ignored(fold(row))
        }
    }

    private fun ignored(plainRow: String) = IGNORE_WORDS.any { Regex("""\b$it\b""").containsMatchIn(plainRow) }
    private fun pretty(brand: String) = brand.lowercase().split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    private fun round2(v: Double) = (v * 100).roundToLong() / 100.0

}
