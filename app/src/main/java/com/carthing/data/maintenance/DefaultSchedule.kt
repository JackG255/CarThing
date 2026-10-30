package com.carthing.data.maintenance

import com.carthing.data.Language
import com.carthing.data.entity.MaintenanceItem

/** Generic intervals added to every new vehicle; the owner's service book is the real source. */
object DefaultSchedule {
    data class Template(
        val name: DefaultName,
        val inspectKm: Double? = null,
        val inspectMonths: Int? = null,
        val replaceKm: Double? = null,
        val replaceMonths: Int? = null,
    )

    val templates = listOf(
        Template(DefaultName.ENGINE_OIL, replaceKm = 15_000.0, replaceMonths = 12),
        Template(DefaultName.AIR_FILTER, replaceKm = 30_000.0, replaceMonths = 24),
        Template(DefaultName.CABIN_FILTER, replaceKm = 15_000.0, replaceMonths = 12),
        Template(DefaultName.BRAKE_FLUID, replaceMonths = 24),
        Template(DefaultName.BRAKE_PADS, inspectKm = 30_000.0, inspectMonths = 24),
        Template(DefaultName.COOLANT, replaceKm = 60_000.0, replaceMonths = 48),
        Template(DefaultName.SPARK_PLUGS, replaceKm = 60_000.0, replaceMonths = 48),
        Template(DefaultName.TIMING_BELT, replaceKm = 120_000.0, replaceMonths = 60),
        Template(DefaultName.TIRES, inspectKm = 40_000.0, inspectMonths = 60),
        Template(DefaultName.TIRE_PRESSURE, inspectMonths = 1),
        Template(DefaultName.BATTERY, inspectMonths = 48),
        Template(DefaultName.WIPER_BLADES, replaceMonths = 12),
    )

    /** The schedule as new items for [vehicleId], named in [language]. */
    fun itemsFor(vehicleId: Long, language: Language): List<MaintenanceItem> = templates.map {
        MaintenanceItem(vehicleId = vehicleId, name = it.name.label(language), inspectKm = it.inspectKm, inspectMonths = it.inspectMonths,
            replaceKm = it.replaceKm, replaceMonths = it.replaceMonths)
    }
}
