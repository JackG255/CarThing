package com.carthing.data.maintenance

import com.carthing.data.entity.MaintenanceItem

/** Generic intervals added to every new vehicle; the owner's service book is the real source. */
object DefaultSchedule {
    data class Template(val name: String, val intervalKm: Double?, val intervalMonths: Int?, val isCheck: Boolean)

    val templates = listOf(
        Template("Engine oil & filter", 15_000.0, 12, isCheck = false),
        Template("Air filter", 30_000.0, 24, isCheck = false),
        Template("Cabin filter", 15_000.0, 12, isCheck = false),
        Template("Brake fluid", null, 24, isCheck = false),
        Template("Brake pads", 30_000.0, 24, isCheck = true),
        Template("Coolant", 60_000.0, 48, isCheck = false),
        Template("Spark plugs", 60_000.0, 48, isCheck = false),
        Template("Timing belt", 120_000.0, 60, isCheck = false),
        Template("Tires", 40_000.0, 60, isCheck = true),
        Template("Tire pressure", null, 1, isCheck = true),
        Template("Battery", null, 48, isCheck = true),
        Template("Wiper blades", null, 12, isCheck = false),
    )

    fun itemsFor(vehicleId: Long): List<MaintenanceItem> = templates.map {
        MaintenanceItem(vehicleId = vehicleId, name = it.name, intervalKm = it.intervalKm,
            intervalMonths = it.intervalMonths, isCheck = it.isCheck)
    }
}
