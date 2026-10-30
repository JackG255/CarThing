package com.carthing.ui

import android.app.LocaleManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.carthing.R
import com.carthing.data.Language
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LanguageSettingsTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val originalLocale = Locale.getDefault()

    // Older phones also move the default locale (for number and date formats); keep other tests unaffected.
    @After fun restoreLocale() = Locale.setDefault(originalLocale)

    @Test @Config(sdk = [32]) fun olderAndroidRemembersTheChoiceAndAppliesItToAnyContext() {
        LanguageSettings(context).choose(Language.CS)
        // A fresh instance stands in for a later start, e.g. the daily check with no screen open.
        val later = LanguageSettings(context)
        assertEquals(Language.CS, later.chosen)
        assertEquals("Uložit", later.wrap(context).getString(R.string.action_save))
    }

    @Test @Config(sdk = [32]) fun olderAndroidSystemDefaultLeavesTheContextAlone() {
        val settings = LanguageSettings(context)
        settings.choose(Language.SK)
        settings.choose(null)
        assertNull(LanguageSettings(context).chosen)
        assertSame(context, settings.wrap(context))
        assertEquals("Save", context.getString(R.string.action_save))
    }

    @Test fun android13UsesThePerAppLocaleTheSystemSettingsShow() {
        val settings = LanguageSettings(context)
        settings.choose(Language.SK)
        assertEquals("sk", context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags())
        assertEquals(Language.SK, settings.chosen)
        // Android applies it to the whole app itself, so contexts aren't wrapped.
        assertSame(context, settings.wrap(context))
        settings.choose(null)
        assertNull(settings.chosen)
    }
}
