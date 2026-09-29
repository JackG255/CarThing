package com.carthing.data.maintenance

/** Colour step for a status chip: how close something is to being due, in a few distinct steps. */
enum class UrgencyBand {
    /** More than half the interval left. */
    FRESH,
    /** A quarter to half left. */
    MIDWAY,
    /** Under a quarter left, or already within the "due soon" window. */
    SOON,
    OVERDUE,
    UNKNOWN;

    companion object {
        fun of(level: DueLevel, fractionRemaining: Double?): UrgencyBand = when (level) {
            DueLevel.OVERDUE -> OVERDUE
            DueLevel.UNKNOWN -> UNKNOWN
            DueLevel.DUE_SOON -> SOON
            DueLevel.OK -> when {
                fractionRemaining == null || fractionRemaining > 0.5 -> FRESH
                fractionRemaining > 0.25 -> MIDWAY
                else -> SOON
            }
        }
    }
}
