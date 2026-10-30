package com.carthing.ui.common

import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {
    private val original = Locale.getDefault()
    private val oct30 = LocalDate.of(2026, 10, 30).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @After fun restoreLocale() = Locale.setDefault(original)

    @Test fun dateFollowsALanguageChangeWithoutRestart() {
        // A per-app language change keeps the process, so the formatter must not capture the start-up locale.
        Locale.setDefault(Locale.UK)
        assertEquals("30 Oct 2026", formatDate(oct30))
        Locale.setDefault(Locale.forLanguageTag("cs-CZ"))
        assertEquals("30. 10. 2026", formatDate(oct30))
    }
}
