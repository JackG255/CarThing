package com.carthing.data.maintenance

import com.carthing.data.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultScheduleTest {
    @Test fun itemsAreNamedInTheGivenLanguage() {
        val en = DefaultSchedule.itemsFor(1, Language.EN)
        val cs = DefaultSchedule.itemsFor(1, Language.CS)
        assertEquals(12, cs.size)
        assertTrue(cs.first().name.startsWith("Motorový olej a filtr"))
        assertEquals("Engine oil & filter", en.first().name)
        en.zip(cs).forEach { (e, c) ->
            assertEquals(e.replaceKm, c.replaceKm)
            assertEquals(e.replaceMonths, c.replaceMonths)
            assertEquals(e.inspectKm, c.inspectKm)
            assertEquals(e.inspectMonths, c.inspectMonths)
        }
    }
}
