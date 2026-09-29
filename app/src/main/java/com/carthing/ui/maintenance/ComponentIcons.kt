package com.carthing.ui.maintenance

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
import com.carthing.data.entity.MaintenanceItem

/** An icon a component can use. [key] is what's stored, so keys must never change. */
data class ComponentIcon(val key: String, val label: String, val vector: ImageVector)

object ComponentIcons {
    val all = listOf(
        ComponentIcon("oil", "Oil", Icons.Default.OilBarrel),
        ComponentIcon("filter", "Filter", Icons.Default.FilterAlt),
        ComponentIcon("air", "Air / cabin", Icons.Default.Air),
        ComponentIcon("brakes", "Brakes", Icons.Default.DiscFull),
        ComponentIcon("fluid", "Fluid", Icons.Default.WaterDrop),
        ComponentIcon("coolant", "Coolant", Icons.Default.Thermostat),
        ComponentIcon("spark", "Ignition", Icons.Default.ElectricBolt),
        ComponentIcon("belt", "Belt / engine", Icons.Default.Settings),
        ComponentIcon("tires", "Tires", Icons.Default.TireRepair),
        ComponentIcon("pressure", "Pressure", Icons.Default.Speed),
        ComponentIcon("battery", "Battery", Icons.Default.BatteryChargingFull),
        ComponentIcon("wipers", "Wipers", Icons.Default.CleaningServices),
        ComponentIcon("lights", "Lights", Icons.Default.Lightbulb),
        ComponentIcon("ac", "Air con", Icons.Default.AcUnit),
        ComponentIcon("gearbox", "Gearbox", Icons.Default.Tune),
        ComponentIcon("inspection", "Inspection", Icons.AutoMirrored.Filled.FactCheck),
        ComponentIcon("safety", "Safety kit", Icons.Default.HealthAndSafety),
        ComponentIcon("fuel", "Fuel", Icons.Default.LocalGasStation),
        ComponentIcon("wash", "Wash", Icons.Default.LocalCarWash),
        ComponentIcon("key", "Key", Icons.Default.Key),
        ComponentIcon("warning", "Warning", Icons.Default.Warning),
        ComponentIcon("car", "Car", Icons.Default.DirectionsCar),
        ComponentIcon("wrench", "General", Icons.Default.Build),
    )
    private val byKey = all.associateBy { it.key }
    private val fallback = byKey.getValue("wrench")

    /**
     * Name words (English and Czech stems, lower case) to icons, most specific first:
     * "Brake fluid" is a fluid before it's brakes, "Air filter" a filter before it's air.
     */
    private val RULES = listOf(
        "pressure" to listOf("pressure", "tlak"),
        "coolant" to listOf("coolant", "antifreeze", "chladic", "chladicí", "nemrz"),
        "fluid" to listOf("fluid", "kapalin"),
        "oil" to listOf("oil", "olej"),
        "air" to listOf("cabin", "pollen", "kabin", "pyl"),
        "filter" to listOf("filter", "filtr"),
        "brakes" to listOf("brake", "brzd"),
        "spark" to listOf("spark", "ignition", "svíčk", "svick", "zapal"),
        "belt" to listOf("belt", "chain", "řemen", "remen", "rozvod"),
        "tires" to listOf("tire", "tyre", "wheel", "pneu", "kol"),
        "battery" to listOf("battery", "akumul", "bateri"),
        "wipers" to listOf("wiper", "stěrač", "sterac", "stirac"),
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
