package com.carthing.ui.maintenance

import com.carthing.data.entity.MaintenanceItem
import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentIconsTest {
    @Test fun defaultComponentsGetFittingIcons() {
        val expected = mapOf(
            "Engine oil & filter" to "oil", "Air filter" to "filter", "Cabin filter" to "air",
            "Brake fluid" to "fluid", "Brake pads" to "brakes", "Coolant" to "coolant",
            "Spark plugs" to "spark", "Timing belt" to "belt", "Tires" to "tires",
            "Tire pressure" to "pressure", "Battery" to "battery", "Wiper blades" to "wipers",
        )
        assertEquals(expected, expected.keys.associateWith { ComponentIcons.guess(it).key })
    }

    @Test fun czechNamesAndUnknownOnes() {
        assertEquals("brakes", ComponentIcons.guess("Brzdové destičky").key)
        assertEquals("belt", ComponentIcons.guess("Rozvodový řemen").key)
        assertEquals("wrench", ComponentIcons.guess("Something else").key)
    }

    @Test fun chosenIconWinsAndUnknownKeysFallBack() {
        val item = MaintenanceItem(vehicleId = 1, name = "Brake pads")
        assertEquals("lights", ComponentIcons.forItem(item.copy(icon = "lights")).key)
        assertEquals("brakes", ComponentIcons.forItem(item.copy(icon = "no-such-icon")).key)
    }

    @Test fun keysAreUnique() {
        assertEquals(ComponentIcons.all.size, ComponentIcons.all.map { it.key }.toSet().size)
    }
}
