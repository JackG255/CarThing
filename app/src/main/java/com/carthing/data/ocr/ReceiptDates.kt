package com.carthing.data.ocr

import java.time.LocalDate

/** Dates on receipts and invoices ("27.09.2026", "27. 9. 26", "2026-09-27"), from [FuelReceiptParser.fold]ed rows. */
object ReceiptDates {
    private val DATE = Regex("""\b(\d{1,2})\s*[./]\s*(\d{1,2})\s*[./]\s*(\d{2}|\d{4})\b|\b(\d{4})-(\d{2})-(\d{2})\b""")

    /**
     * The document's date: dates in the future or older than 5 years are skipped (warranties,
     * due dates, promos). Rows with a [preferred] word win, the first one listed first; rows with
     * an [avoided] word are used only when nothing else is left.
     */
    fun find(
        plain: List<String>, today: LocalDate,
        preferred: List<String> = listOf("DATUM", "DATE"), avoided: List<String> = emptyList(),
    ): LocalDate? {
        val candidates = plain.flatMap { row -> DATE.findAll(row).mapNotNull(::toDate).map { it to row }.toList() }
            .filter { (d, _) -> !d.isAfter(today) && d.isAfter(today.minusYears(5)) }
        preferred.forEach { word -> candidates.firstOrNull { (_, row) -> word in row }?.let { return it.first } }
        return (candidates.firstOrNull { (_, row) -> avoided.none { it in row } } ?: candidates.firstOrNull())?.first
    }

    private fun toDate(m: MatchResult): LocalDate? = runCatching {
        val g = m.groupValues
        if (g[4].isNotEmpty()) LocalDate.of(g[4].toInt(), g[5].toInt(), g[6].toInt())
        else {
            val year = g[3].toInt().let { if (it < 100) 2000 + it else it }
            LocalDate.of(year, g[2].toInt(), g[1].toInt())
        }
    }.getOrNull()
}
