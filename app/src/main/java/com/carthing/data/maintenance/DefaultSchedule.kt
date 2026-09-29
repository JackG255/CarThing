package com.carthing.data.maintenance

import com.carthing.data.entity.MaintenanceItem

/** Generic intervals added to every new vehicle; the owner's service book is the real source. */
object DefaultSchedule {
    data class Template(
        val name: String,
        val inspectKm: Double? = null,
        val inspectMonths: Int? = null,
        val replaceKm: Double? = null,
        val replaceMonths: Int? = null,
    )

    val templates = listOf(
        Template("Engine oil & filter", replaceKm = 15_000.0, replaceMonths = 12),
        Template("Air filter", replaceKm = 30_000.0, replaceMonths = 24),
        Template("Cabin filter", replaceKm = 15_000.0, replaceMonths = 12),
        Template("Brake fluid", replaceMonths = 24),
        Template("Brake pads", inspectKm = 30_000.0, inspectMonths = 24),
        Template("Coolant", replaceKm = 60_000.0, replaceMonths = 48),
        Template("Spark plugs", replaceKm = 60_000.0, replaceMonths = 48),
        Template("Timing belt", replaceKm = 120_000.0, replaceMonths = 60),
        Template("Tires", inspectKm = 40_000.0, inspectMonths = 60),
        Template("Tire pressure", inspectMonths = 1),
        Template("Battery", inspectMonths = 48),
        Template("Wiper blades", replaceMonths = 12),
    )

    fun itemsFor(vehicleId: Long): List<MaintenanceItem> = templates.map {
        MaintenanceItem(vehicleId = vehicleId, name = it.name, inspectKm = it.inspectKm, inspectMonths = it.inspectMonths,
            replaceKm = it.replaceKm, replaceMonths = it.replaceMonths)
    }
}
