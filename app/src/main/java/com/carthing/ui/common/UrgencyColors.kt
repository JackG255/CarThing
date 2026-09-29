package com.carthing.ui.common

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.carthing.data.maintenance.UrgencyBand

/** Status colour for [band]; darker shades in light theme and lighter ones in dark theme keep text readable. */
@Composable
fun urgencyColor(band: UrgencyBand): Color {
    val dark = isSystemInDarkTheme()
    return when (band) {
        UrgencyBand.FRESH -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        UrgencyBand.MIDWAY -> if (dark) Color(0xFFFFD54F) else Color(0xFF8A6D00)
        UrgencyBand.SOON -> if (dark) Color(0xFFFFB74D) else Color(0xFFD84315)
        UrgencyBand.OVERDUE -> MaterialTheme.colorScheme.error
        UrgencyBand.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
