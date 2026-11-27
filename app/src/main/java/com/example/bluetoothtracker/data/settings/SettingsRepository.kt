
package com.example.bluetoothtracker.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.bluetoothtracker.core.AppLanguage
import com.example.bluetoothtracker.core.Constants
import com.example.bluetoothtracker.domain.model.AppTheme
import com.example.bluetoothtracker.domain.model.DistanceUnit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings"
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private object Keys {
        val BACKGROUND = booleanPreferencesKey("background_tracking")
        val UNIT = stringPreferencesKey("unit")
        val SCAN_MS = longPreferencesKey("scan_duration_ms")
        val APP_THEME = stringPreferencesKey("app_theme")
        val LANGUAGE_TAG = stringPreferencesKey("language_tag")
        val LANGUAGE_SELECTED = booleanPreferencesKey("language_selected")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    /**
     * Existing installed users who already have saved Room data or user-configured
     * settings should not be forced back through first-launch language/onboarding setup.
     */
    private fun isExistingInstallation(prefs: Preferences): Boolean {
        val hasLegacySettings = prefs.contains(Keys.BACKGROUND) ||
            prefs.contains(Keys.UNIT) ||
            prefs.contains(Keys.SCAN_MS) ||
            prefs.contains(Keys.APP_THEME)
        val hasExistingDb = context.getDatabasePath("bluetooth_tracker.db").exists()
        return hasLegacySettings || hasExistingDb
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val existingUser = isExistingInstallation(prefs)
        AppSettings(
            backgroundTracking = prefs[Keys.BACKGROUND] ?: false,

            unit = prefs[Keys.UNIT]
                ?.let { runCatching { DistanceUnit.valueOf(it) }.getOrNull() }
                ?: DistanceUnit.METER,

            scanDurationMs = prefs[Keys.SCAN_MS]
                ?: Constants.DEFAULT_SCAN_DURATION_MS,


            appTheme = prefs[Keys.APP_THEME]
                ?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                ?: AppTheme.BLUE,

            languageTag = prefs[Keys.LANGUAGE_TAG] ?: AppLanguage.DEFAULT.tag,

            languageSelected = prefs[Keys.LANGUAGE_SELECTED] ?: false,

            onboardingCompleted = prefs[Keys.ONBOARDING_COMPLETED] ?: existingUser
        )
    }

    suspend fun setBackgroundTracking(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.BACKGROUND] = enabled
        }
    }

    suspend fun setUnit(unit: DistanceUnit) {
        context.dataStore.edit {
            it[Keys.UNIT] = unit.name
        }
    }

    suspend fun setScanDuration(ms: Long) {
        context.dataStore.edit {
            it[Keys.SCAN_MS] = ms
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit {
            it[Keys.APP_THEME] = theme.name
        }
    }

    suspend fun setLanguage(tag: String, markSelected: Boolean = true) {
        context.dataStore.edit {
            it[Keys.LANGUAGE_TAG] = tag
            if (markSelected) it[Keys.LANGUAGE_SELECTED] = true
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit {
            it[Keys.ONBOARDING_COMPLETED] = completed
        }
    }
}
