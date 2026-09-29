package com.carthing.data.ocr

/** A number found in text, with where it was, so rules can look at what comes right after it. */
data class FoundNumber(val value: Double, val start: Int, val end: Int)

object Numbers {
    // Thousands grouped by spaces ("1 650", "1 650,17"), or by dots only when a comma decimal
    // follows ("1.650,17"): a lone "42.150" is far more likely 42.15 litres than 42 150.
    private const val SPACE_GROUPED = """\d{1,3}(?:[  ]\d{3})+(?:,\d{1,3})?"""
    private const val DOT_GROUPED = """\d{1,3}(?:\.\d{3})+,\d{1,3}"""
    private const val PLAIN = """\d+(?:[.,]\d{1,3})?"""
    private val NUMBER = Regex("""(?<![\d.,])(?:$DOT_GROUPED|$SPACE_GROUPED|$PLAIN)(?![\d])""")

    fun findAll(text: String): List<FoundNumber> = NUMBER.findAll(text).mapNotNull { m ->
        parse(m.value)?.let { FoundNumber(it, m.range.first, m.range.last + 1) }
    }.toList()

    fun parse(raw: String): Double? {
        val s = raw.replace(' ', ' ').trim()
        return when {
            Regex(DOT_GROUPED).matches(s) -> s.replace(".", "").replace(',', '.').toDoubleOrNull()
            Regex(SPACE_GROUPED).matches(s) -> s.replace(" ", "").replace(',', '.').toDoubleOrNull()
            else -> s.replace(',', '.').toDoubleOrNull()
        }
    }
}
