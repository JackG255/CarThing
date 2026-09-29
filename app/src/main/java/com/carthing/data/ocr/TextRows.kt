package com.carthing.data.ocr

import kotlin.math.abs
import kotlin.math.tan

/**
 * One recognised line of text and where it sits in the image (pixels, y grows downward).
 * [angleDegrees] is the line's tilt as reported by recognition, positive = clockwise.
 */
data class TextPiece(
    val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int,
    val angleDegrees: Float = 0f,
) {
    val centerX get() = (left + right) / 2
    val centerY get() = (top + bottom) / 2
    val height get() = bottom - top
}

object TextRows {
    /**
     * Joins pieces that sit on the same line into rows, left to right, top to bottom. Recognition
     * often returns a receipt's label ("CELKEM") and its amount as separate pieces; rows put them
     * back together so rules can read "CELKEM   1 650,17 Kč".
     *
     * Photos are rarely straight: on a receipt tilted by 4°, a value 400 px to the right of its
     * label sits ~28 px lower. Positions are corrected by the typical tilt of the lines first.
     */
    fun group(pieces: List<TextPiece>): List<String> {
        val visible = pieces.filter { it.text.isNotBlank() }
        val slope = tan(Math.toRadians(median(visible.map { it.angleDegrees.toDouble() })))
        fun level(p: TextPiece) = p.centerY - p.centerX * slope

        val rows = mutableListOf<MutableList<TextPiece>>()
        for (p in visible.sortedBy(::level)) {
            val row = rows.lastOrNull()
            // Same row when the corrected centres are within half a line height of each other.
            if (row != null && abs(level(p) - row.map(::level).average()) <= maxOf(p.height, row.first().height) / 2.0) {
                row += p
            } else {
                rows += mutableListOf(p)
            }
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString("   ") { it.text.trim() } }
    }

    private fun median(values: List<Double>): Double =
        if (values.isEmpty()) 0.0 else values.sorted().let { it[it.size / 2] }
}
