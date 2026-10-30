package com.carthing.data.repository

import android.content.SharedPreferences
import androidx.room.withTransaction
import android.util.Log
import com.carthing.BuildConfig
import com.carthing.data.CarThingDatabase
import com.carthing.data.Language
import com.carthing.data.maintenance.DefaultName
import kotlin.coroutines.cancellation.CancellationException

/**
 * Rewrites stored component names, service types and deadline titles that are still a default name
 * so they follow the app's [language]. A name matches only if its trimmed text equals a default in
 * any language exactly (case-sensitive); anything else, and all free-text notes, are left alone.
 * Each table only matches its own names: components in `maintenance_items`, quick picks plus
 * components in `service_entries`, presets in `deadlines`.
 */
class DefaultNameRelabeler(
    private val db: CarThingDatabase,
    private val prefs: SharedPreferences,
    private val language: () -> Language,
) {
    /** Relabels every default name to the current language, and remembers it. Returns the number of rows changed. */
    suspend fun relabel(): Int = run { it.all }

    /**
     * Relabels only if the language differs from the one last relabelled to; 0 and no database access otherwise.
     * Before anything is stored, data can only come from 0.3.0, whose defaults are English, so only
     * English names count as defaults then: a Czech or Slovak one was typed by hand.
     */
    suspend fun relabelIfLanguageChanged(): Int {
        val stored = prefs.getString(KEY_LANGUAGE, null)
        if (stored == language().name) return 0
        return run { if (stored == null) listOf(it.label(Language.EN)) else it.all }
    }

    /** [relabel] for callers whose real work must not fail with it: logs (debug builds only) and returns 0 on any error but cancellation. */
    suspend fun relabelSafely(): Int = safely { relabel() }

    /** [relabelIfLanguageChanged] with the same never-throws contract as [relabelSafely]. */
    suspend fun relabelIfLanguageChangedSafely(): Int = safely { relabelIfLanguageChanged() }

    private suspend fun safely(block: suspend () -> Int): Int = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (BuildConfig.DEBUG) Log.w("CarThing", "Relabeling default names failed", e)
        0
    }

    private suspend fun run(from: (DefaultName) -> List<String>): Int {
        val target = language()
        val changed = db.withTransaction {
            DefaultName.entries.sumOf { e ->
                val names = from(e)
                val to = e.label(target)
                (if (e in DefaultName.COMPONENTS) db.maintenanceItemDao().relabel(names, to) else 0) +
                    (if (e in DefaultName.SERVICE_ENTRY_NAMES) db.serviceEntryDao().relabelType(names, to) else 0) +
                    (if (e in DefaultName.DEADLINE_PRESETS) db.deadlineDao().relabelTitle(names, to) else 0)
            }
        }
        prefs.edit().putString(KEY_LANGUAGE, target.name).apply()
        return changed
    }

    companion object {
        const val KEY_LANGUAGE = "language"
    }
}
