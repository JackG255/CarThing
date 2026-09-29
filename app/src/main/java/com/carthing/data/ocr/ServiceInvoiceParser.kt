package com.carthing.data.ocr

import com.carthing.data.ocr.FuelReceiptParser.fold
import com.carthing.data.ocr.FuelReceiptParser.repair
import java.time.LocalDate

/** What could be read from a service invoice; anything not found reliably is null. */
data class ServiceInvoice(
    val date: LocalDate? = null,
    val total: Double? = null,
    val shop: String? = null,
    val odometerKm: Double? = null,
    /** Kinds of work found, most significant first (e.g. "Oil change", "Filters"). */
    val services: List<String> = emptyList(),
) {
    val isEmpty get() = date == null && total == null && shop == null && odometerKm == null && services.isEmpty()
}

/**
 * Rules for typical Czech service invoices and repair-shop receipts. Works on rows of text (see
 * [TextRows]); tolerant of missing diacritics like [FuelReceiptParser].
 */
object ServiceInvoiceParser {
    /** Keyword stems (folded) to the service types the form suggests; order = significance. */
    private val SERVICES = listOf(
        "Inspection" to listOf("STK", "TECHNICKA KONTROLA", "EMISE", "MERENI EMIS"),
        "Oil change" to listOf("VYMENA OLEJE", "MOTOROVY OLEJ", "OLEJ", "OIL"),
        "Brakes" to listOf("BRZD", "BRAKE"),
        "Tires" to listOf("PNEU", "PREZUT", "PREZOUV", "VYVAZ", "GEOMETRI"),
        "Battery" to listOf("AKUMULATOR", "BATERI", "BATTERY"),
        "Filters" to listOf("FILTR", "FILTER"),
        "Wipers" to listOf("STERAC", "STIRAC", "STETIN", "WIPER"),
    )
    private val PAYABLE_WORDS = listOf("K UHRADE", "CELKEM K ZAPLACENI", "ZAPLACENO", "CELKEM S DPH", "CELKEM VC", "CELKEM VCETNE")
    private val TOTAL_WORDS = listOf("CELKEM", "TOTAL", "SUMA", "CASTKA")
    /** Net amounts and VAT lines, never the amount paid. */
    private val NET_WORDS = listOf("BEZ DPH", "ZAKLAD", "SAZBA", "DPH 21", "DPH 12", "DPH 15", "DPH 10")
    private val ID_WORDS = listOf("ICO", "DIC", "TEL", "IBAN", "EAN", "UCET", "VS", "VARIABILNI", "CISLO", "PSC")
    private val SHOP_HINTS = listOf("SERVIS", "AUTO", "PNEU", "GARAGE", "GARAZ", "MOTOR", "DILNA")
    private val NOT_SHOP = listOf("FAKTURA", "DANOVY DOKLAD", "DOKLAD", "ODBERATEL", "PRIJEMCE", "ZAKAZNIK", "UCTENKA", "PARAGON")
    private val ODOMETER_WORDS = listOf("STAV KM", "STAV TACH", "TACHOMETR", "NAJETO", "KM STAV", "NAJEZD", "ODOMETER", "MILEAGE")
    private val KM_UNIT = Regex("""^\s*KM\b""")
    private val CURRENCY = Regex("""^\s*(KC|CZK|,-)""")

    fun parse(rows: List<String>, today: LocalDate = LocalDate.now()): ServiceInvoice {
        val plain = rows.map { repair(fold(it)) }
        return ServiceInvoice(
            // The issue date or the date of the work; a due date ("splatnost") is the last resort.
            date = ReceiptDates.find(
                plain, today,
                preferred = listOf("DUZP", "PLNENI", "VYSTAVEN", "DATUM PRIJ", "DATUM"), avoided = listOf("SPLATNOST"),
            ),
            total = findTotal(plain),
            shop = findShop(rows, plain),
            odometerKm = findOdometer(plain),
            services = SERVICES.filter { (_, words) -> plain.any { row -> words.any { word(it).containsMatchIn(row) } } }.map { it.first },
        )
    }

    private fun findTotal(plain: List<String>): Double? {
        fun amounts(row: String) = Numbers.findAll(row).filter { it.value >= 1.0 }
        fun largestOn(words: List<String>, allowNet: Boolean) = plain
            .filter { row -> words.any { it in row } && (allowNet || NET_WORDS.none { it in row }) && !isId(row) }
            .firstNotNullOfOrNull { row -> amounts(row).maxByOrNull { it.value }?.value }
        // 1) What was to be paid ("K úhradě", "Celkem s DPH"). 2) A gross total. 3) The largest amount in Kč.
        return largestOn(PAYABLE_WORDS, allowNet = true)
            ?: largestOn(TOTAL_WORDS, allowNet = false)
            ?: plain.filterNot(::isId).flatMap { row -> amounts(row).filter { CURRENCY.containsMatchIn(row.substring(it.end)) } }
                .maxByOrNull { it.value }?.value
    }

    private fun findOdometer(plain: List<String>): Double? {
        fun plausible(v: Double) = v in 100.0..2_000_000.0
        // 1) A labelled reading ("Stav km: 94 020"). 2) A number followed by "km" (not "Kč").
        plain.filter { row -> ODOMETER_WORDS.any { it in row } }.forEach { row ->
            Numbers.findAll(row).firstOrNull { plausible(it.value) }?.let { return it.value }
        }
        return plain.filterNot(::isId).firstNotNullOfOrNull { row ->
            Numbers.findAll(row).firstOrNull { plausible(it.value) && KM_UNIT.containsMatchIn(row.substring(it.end)) }?.value
        }
    }

    private fun findShop(rows: List<String>, plain: List<String>): String? {
        // The supplier comes before the customer ("Odběratel"); look only above it when present.
        val end = plain.indexOfFirst { "ODBERATEL" in it || "PRIJEMCE" in it }.takeIf { it > 0 } ?: minOf(plain.size, 8)
        val candidates = (0 until end).mapNotNull { i ->
            val name = SUPPLIER_LABEL.replace(rows[i], "").trim()
            val letters = name.count { it.isLetter() }
            val ok = letters >= 4 && letters >= name.length * 0.6 && name.length <= 40 &&
                !isId(plain[i]) && NOT_SHOP.none { it in plain[i] }
            if (ok) name to plain[i] else null
        }
        return (candidates.firstOrNull { (_, p) -> SHOP_HINTS.any { it in p } } ?: candidates.firstOrNull())?.first
    }

    private val SUPPLIER_LABEL = Regex("""^\s*(dodavatel|prodávající|prodavajici)\s*:?""", RegexOption.IGNORE_CASE)

    private fun isId(plainRow: String) = ID_WORDS.any { Regex("""\b$it\b""").containsMatchIn(plainRow) }
    /** A keyword at the start of a word, so "OLEJ" matches "OLEJE" but "STK" doesn't match inside a word. */
    private fun word(stem: String) = Regex("""\b$stem""")
}
