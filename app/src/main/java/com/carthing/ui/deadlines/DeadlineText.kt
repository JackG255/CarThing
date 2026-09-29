package com.carthing.ui.deadlines

import com.carthing.data.deadlines.DeadlineStatus
import com.carthing.ui.maintenance.formatDays
import kotlin.math.abs

/** E.g. "expires in 12 days", "expires today", "expired 3 days ago". */
fun DeadlineStatus.describeExpiry(): String = when {
    daysLeft == 0L -> "expires today"
    daysLeft > 0 -> "expires in ${formatDays(daysLeft)}"
    else -> "expired ${formatDays(abs(daysLeft))} ago"
}
