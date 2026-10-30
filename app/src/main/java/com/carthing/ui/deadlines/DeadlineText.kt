package com.carthing.ui.deadlines

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.carthing.R
import com.carthing.data.deadlines.DeadlineStatus
import com.carthing.ui.maintenance.formatDays
import com.carthing.ui.maintenance.formatSpan
import kotlin.math.abs

/** E.g. "expires in 12 days", "expires today", "expired 3 days ago"; lower case, to fit after a title. */
fun DeadlineStatus.describeExpiry(res: Resources): String = when {
    daysLeft == 0L -> res.getString(R.string.deadline_expires_today)
    daysLeft > 0 -> res.getString(R.string.deadline_expires_in, formatDays(res, daysLeft))
    // "Ago" takes another case in Czech and Slovak, so it has its own plurals.
    else -> formatSpan(
        res, abs(daysLeft), R.plurals.deadline_expired_days_ago, R.plurals.deadline_expired_weeks_ago,
        R.plurals.deadline_expired_months_ago, R.plurals.deadline_expired_years_ago
    )
}

@Composable fun DeadlineStatus.describeExpiry(): String = describeExpiry(LocalContext.current.resources)
