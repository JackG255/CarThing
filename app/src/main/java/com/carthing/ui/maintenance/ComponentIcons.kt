package com.carthing.ui.maintenance

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DiscFull
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.TireRepair
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector
import com.carthing.R
import com.carthing.data.entity.MaintenanceItem

/** An icon a component can use. [key] is what's stored, so keys must never change; [label] is its name in the picker. */
data class ComponentIcon(val key: String, @StringRes val label: Int, val vector: ImageVector)

object ComponentIcons {
    val all = listOf(
        ComponentIcon("oil", R.string.icon_oil, Icons.Default.OilBarrel),
        ComponentIcon("filter", R.string.icon_filter, Icons.Default.FilterAlt),
        ComponentIcon("air", R.string.icon_air_cabin, Icons.Default.Air),
        ComponentIcon("brakes", R.string.icon_brakes, Icons.Default.DiscFull),
        ComponentIcon("fluid", R.string.icon_fluid, Icons.Default.WaterDrop),
        ComponentIcon("coolant", R.string.icon_coolant, Icons.Default.Thermostat),
        ComponentIcon("spark", R.string.icon_ignition, Icons.Default.ElectricBolt),
        ComponentIcon("belt", R.string.icon_belt_engine, Icons.Default.Settings),
        ComponentIcon("tires", R.string.icon_tires, Icons.Default.TireRepair),
        ComponentIcon("pressure", R.string.icon_pressure, Icons.Default.Speed),
        ComponentIcon("battery", R.string.icon_battery, Icons.Default.BatteryChargingFull),
        ComponentIcon("wipers", R.string.icon_wipers, Icons.Default.CleaningServices),
        ComponentIcon("lights", R.string.icon_lights, Icons.Default.Lightbulb),
        ComponentIcon("ac", R.string.icon_air_con, Icons.Default.AcUnit),
        ComponentIcon("gearbox", R.string.icon_gearbox, Icons.Default.Tune),
        ComponentIcon("inspection", R.string.icon_inspection, Icons.AutoMirrored.Filled.FactCheck),
        ComponentIcon("safety", R.string.icon_safety_kit, Icons.Default.HealthAndSafety),
        ComponentIcon("fuel", R.string.icon_fuel, Icons.Default.LocalGasStation),
        ComponentIcon("wash", R.string.icon_wash, Icons.Default.LocalCarWash),
        ComponentIcon("key", R.string.icon_key, Icons.Default.Key),
        ComponentIcon("warning", R.string.icon_warning, Icons.Default.Warning),
        ComponentIcon("car", R.string.icon_car, Icons.Default.DirectionsCar),
        ComponentIcon("wrench", R.string.icon_general, Icons.Default.Build),
    )
    private val byKey = all.associateBy { it.key }
    private val fallback = byKey.getValue("wrench")

    /**
     * Name words (English, Czech and Slovak stems, lower case) to icons, most specific first:
     * "Brake fluid" is a fluid before it's brakes, "Air filter" a filter before it's air.
     */
    private val RULES = listOf(
        "pressure" to listOf("pressure", "tlak"),
        "coolant" to listOf("coolant", "antifreeze", "chladic", "chladicí", "chladiac", "nemrz"),
        "fluid" to listOf("fluid", "kapalin", "kvapalin"),
        "oil" to listOf("oil", "olej"),
        "air" to listOf("cabin", "pollen", "kabin", "kabín", "pyl"),
        "filter" to listOf("filter", "filtr"),
        "brakes" to listOf("brake", "brzd"),
        "spark" to listOf("spark", "ignition", "svíčk", "svick", "sviečk", "zapal", "zapaľ"),
        "belt" to listOf("belt", "chain", "řemen", "remen", "remeň", "rozvod"),
        "tires" to listOf("tire", "tyre", "wheel", "pneu", "kol"),
        "battery" to listOf("battery", "akumul", "bateri"),
        "wipers" to listOf("wiper", "stěrač", "sterac", "stierač", "stirac"),
        "lights" to listOf("light", "bulb", "žárov", "zarov", "světl", "svetl"),
        "ac" to listOf("air con", "a/c", "klimatiz"),
        "gearbox" to listOf("gear", "transmission", "clutch", "převod", "prevod", "spojk"),
        "inspection" to listOf("inspection", "stk", "emission", "emise"),
        "safety" to listOf("first aid", "lékárn", "lekarn", "safety"),
        "fuel" to listOf("fuel", "palivo"),
    )

    fun byKey(key: String?): ComponentIcon? = key?.let(byKey::get)

    /** The icon for a component name, when none was chosen. */
    fun guess(name: String): ComponentIcon {
        val n = name.lowercase()
        return RULES.firstOrNull { (_, words) -> words.any { it in n } }?.let { byKey.getValue(it.first) } ?: fallback
    }

    fun forItem(item: MaintenanceItem): ComponentIcon = byKey(item.icon) ?: guess(item.name)
}
