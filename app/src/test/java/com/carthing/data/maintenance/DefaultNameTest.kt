package com.carthing.data.maintenance

import com.carthing.data.Language
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultNameTest {
    @Test fun languageOfLocale() {
        assertEquals(Language.CS, Language.of(Locale("cs", "CZ")))
        assertEquals(Language.SK, Language.of(Locale("sk")))
        assertEquals(Language.EN, Language.of(Locale.GERMAN))
        assertEquals(Language.EN, Language.of(Locale.US))
    }

    @Test fun findMatchesAnyLanguageTrimmedAndExact() {
        assertEquals(DefaultName.BRAKE_FLUID, DefaultName.find("Brzdová kapalina"))
        assertEquals(DefaultName.BRAKE_FLUID, DefaultName.find("  Brake fluid "))
        assertNull(DefaultName.find("brake fluid"))
        assertNull(DefaultName.find("Brake fluid!"))
    }

    @Test fun labelPerLanguage() {
        assertEquals("Brzdová kvapalina", DefaultName.BRAKE_FLUID.label(Language.SK))
        assertEquals("Brake fluid", DefaultName.BRAKE_FLUID.label(Language.EN))
    }

    @Test fun noNameBelongsToTwoEntries() {
        val owners = DefaultName.entries.flatMap { e -> e.all.map { it to e } }.groupBy({ it.first }, { it.second })
        assertTrue(owners.filterValues { it.size > 1 }.toString(), owners.values.all { it.size == 1 })
    }

    @Test fun labelsAreDistinctWithinEachLanguage() {
        for (lang in Language.entries) {
            val labels = DefaultName.entries.map { it.label(lang) }
            assertEquals("duplicate labels in $lang", labels.size, labels.toSet().size)
        }
    }

    @Test fun setsMatchTheirUsers() {
        assertEquals(DefaultSchedule.templates.map { it.name }, DefaultName.COMPONENTS)
        assertEquals(com.carthing.data.deadlines.DeadlineTemplates.all.map { it.name }, DefaultName.DEADLINE_PRESETS)
        assertEquals(8, DefaultName.SERVICE_TYPES.size)
        assertEquals(DefaultName.SERVICE_TYPES + DefaultName.COMPONENTS, DefaultName.SERVICE_ENTRY_NAMES)
    }
}
