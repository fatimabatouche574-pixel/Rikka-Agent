package me.rerere.rikkahub.utils

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.IOException as DataStoreIOException
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.settingsStore
import java.util.Locale

/**
 * Forces the app's UI locale to the persisted [SettingsStore.APP_LANGUAGE] preference
 * (default `"en"`) instead of the device system locale, across every Activity.
 *
 * Applied in each Activity's `attachBaseContext` via [applyLocale].
 *
 * Design notes:
 * - [tagToLocale] is a pure function (no Android deps) so it is unit-testable in pure JVM.
 * - [current] reads the DataStore **synchronously** (one-shot `first()` with a cached
 *   mirror) so `attachBaseContext` is never blocked after the first frame.
 * - [applyLocale] wraps the base Context via `createConfigurationContext` and sets
 *   `Locale.setDefault` so all `stringResource(...)` lookups use the target locale.
 */
object LocaleHelper {

    private val SUPPORTED_TAGS = setOf(
        "en", "fr", "de", "it", "ja", "ko", "zh-CN", "zh-TW", "es", "ar", "fa", "ur", "in"
    )

    @Volatile
    private var cachedLocale: Locale? = null

    /**
     * Pure function: BCP-47 tag string → [Locale].
     *
     * - `null` / blank → [Locale.ENGLISH]
     * - Unknown/garbage tag not in the 13 supported locales → [Locale.ENGLISH] (never crash, never blank)
     * - `"in"` (deprecated Indonesian code used by Android resource dirs) → `Locale("in")`
     *   so `values-in/` resources resolve correctly.
     */
    fun tagToLocale(tag: String?): Locale {
        if (tag.isNullOrBlank()) return Locale.ENGLISH
        if (tag !in SUPPORTED_TAGS) return Locale.ENGLISH
        return try {
            Locale.forLanguageTag(tag)
        } catch (e: Exception) {
            Locale.ENGLISH
        }
    }

    /**
     * Synchronous one-shot read of the persisted app-language tag.
     * Caches the result so the first frame is never blocked on subsequent calls.
     */
    fun current(context: Context): Locale {
        cachedLocale?.let { return it }
        val tag = readAppLanguageSync(context)
        val locale = tagToLocale(tag)
        cachedLocale = locale
        return locale
    }

    /**
     * Wraps [base] with a configuration context carrying the target locale,
     * and sets [Locale.setDefault] before returning.
     */
    fun applyLocale(base: Context): Context {
        val locale = current(base)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Updates the cached locale when the user picks a new language via the picker.
     * Called by [SettingsStore.setAppLanguage] so the next `attachBaseContext` (after
     * `recreate()`) picks up the new value without re-reading the DataStore.
     */
    fun updateCache(tag: String) {
        cachedLocale = tagToLocale(tag)
    }

    private fun readAppLanguageSync(context: Context): String {
        return try {
            runBlocking {
                context.settingsStore.data
                    .catch { if (it is DataStoreIOException) emit(emptyPreferences()) else throw it }
                    .first()[SettingsStore.APP_LANGUAGE] ?: "en"
            }
        } catch (e: Exception) {
            "en"
        }
    }
}
