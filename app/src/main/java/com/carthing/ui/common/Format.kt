package com.carthing.ui.common

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.carthing.R
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun decimals(max: Int, min: Int = 0) =
    NumberFormat.getNumberInstance().apply { maximumFractionDigits = max; minimumFractionDigits = min }

// Each text helper takes the Resources to read units from; the @Composable overload uses the current ones.
fun formatKm(res: Resources, km: Double): String = res.getString(R.string.format_km, decimals(0).format(km))
fun formatLiters(res: Resources, liters: Double): String = res.getString(R.string.format_liters, decimals(2).format(liters))
/** "—" when [litersPer100Km] is null (not enough fills yet). */
fun formatEconomy(res: Resources, litersPer100Km: Double?): String =
    litersPer100Km?.let { res.getString(R.string.format_economy, decimals(1, min = 1).format(it)) } ?: "—"
fun formatMoney(amount: Double?): String = amount?.let { decimals(2).format(it) } ?: "—"
/** "—" when [amount] is null. */
fun formatCostPerKm(res: Resources, amount: Double?): String =
    amount?.let { res.getString(R.string.format_cost_per_km, decimals(3).format(it)) } ?: "—"

@Composable fun formatKm(km: Double): String = formatKm(LocalContext.current.resources, km)
@Composable fun formatLiters(liters: Double): String = formatLiters(LocalContext.current.resources, liters)
@Composable fun formatEconomy(litersPer100Km: Double?): String = formatEconomy(LocalContext.current.resources, litersPer100Km)
@Composable fun formatCostPerKm(amount: Double?): String = formatCostPerKm(LocalContext.current.resources, amount)

// Built per call: a per-app language change keeps the process, so a cached formatter would keep the old locale.
fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

/** Parses user input, accepting both '.' and ',' as the decimal separator. Blank → null. */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()

/** Formats a stored number for an editable text field without grouping separators. */
fun editableNumber(value: Double?): String = when {
    value == null -> ""
    value % 1.0 == 0.0 -> value.toLong().toString()
    else -> value.toString()
}
