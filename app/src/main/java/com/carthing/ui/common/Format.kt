package com.carthing.ui.common

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun decimals(max: Int) = NumberFormat.getNumberInstance().apply { maximumFractionDigits = max }

fun formatKm(km: Double): String = "${decimals(0).format(km)} km"
fun formatLiters(liters: Double): String = "${decimals(2).format(liters)} L"
fun formatEconomy(litersPer100Km: Double?): String =
    litersPer100Km?.let { "${decimals(1).format(it)} L/100km" } ?: "—"
fun formatMoney(amount: Double?): String = amount?.let { decimals(2).format(it) } ?: "—"
fun formatCostPerKm(amount: Double?): String = amount?.let { "${decimals(3).format(it)} /km" } ?: "—"

private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

/** Parses user input, accepting both '.' and ',' as the decimal separator. Blank → null. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/** Formats a stored number for an editable text field without grouping separators. */
fun editableNumber(value: Double?): String = when {
    value == null -> ""
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}
