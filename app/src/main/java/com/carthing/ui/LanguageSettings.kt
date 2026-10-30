package com.carthing.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import android.os.LocaleList
import com.carthing.data.Language
import java.util.Locale

/**
 * The language picked in the app. Android 13+ keeps it as the per-app locale (the same setting as
 * system Settings → Apps → CarThing → Language) and applies it to the whole app; older phones have no
 * such setting, so it's saved here and applied by [wrap].
 */
class LanguageSettings(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ui_settings", Context.MODE_PRIVATE)

    /** null follows the phone's language. */
    val chosen: Language?
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            app.getSystemService(LocaleManager::class.java).applicationLocales.takeUnless { it.isEmpty }?.let { Language.of(it[0]) }
        else prefs.getString(KEY_LANGUAGE, null)?.let { tag -> Language.entries.firstOrNull { it.tag == tag } }

    /** Android 13+ recreates the open screens itself; on older phones the caller recreates them. */
    fun choose(language: Language?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            app.getSystemService(LocaleManager::class.java).applicationLocales =
                language?.let { LocaleList.forLanguageTags(it.tag) } ?: LocaleList.getEmptyLocaleList()
        } else {
            prefs.edit().putString(KEY_LANGUAGE, language?.tag).commit()
            // Numbers and dates format with the default locale, so it has to follow the choice too.
            Locale.setDefault(language?.let { Locale.forLanguageTag(it.tag) } ?: Resources.getSystem().configuration.locales[0])
        }
    }

    /** [base] in the chosen language on Android 12 and older; otherwise [base] itself. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val locale = chosen?.let { Locale.forLanguageTag(it.tag) } ?: return base
        Locale.setDefault(locale)
        return base.createConfigurationContext(Configuration(base.resources.configuration).apply { setLocale(locale) })
    }

    private val Language.tag get() = name.lowercase()

    private companion object { const val KEY_LANGUAGE = "app_language" }
}
