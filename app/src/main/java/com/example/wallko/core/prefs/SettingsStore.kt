package com.example.wallko.core.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Small key/value settings. Anything relational lives in Room instead. */
@Singleton
class SettingsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val remindersEnabled: Flow<Boolean> = read(REMINDERS_ENABLED, true)

    /** Reminder time as minutes after local midnight. */
    val reminderMinuteOfDay: Flow<Int> = read(REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)

    val spriteStyleId: Flow<String?> = read(SPRITE_STYLE_ID)

    /** Epoch millis up to which the game engine has applied time. */
    val lastProcessedTickAt: Flow<Long?> = read(LAST_TICK_AT)

    /** dbVersion of the species JSON last seeded into Room; 0 = never seeded. */
    val speciesDbVersion: Flow<Int> = read(SPECIES_DB_VERSION, 0)

    /** True once the user has finished (or skipped) onboarding. */
    val onboardingDone: Flow<Boolean> = read(ONBOARDING_DONE, false)

    /** "METRIC" or "IMPERIAL". */
    val units: Flow<String> = read(UNITS, "METRIC")

    suspend fun setRemindersEnabled(enabled: Boolean) = write(REMINDERS_ENABLED, enabled)
    suspend fun setOnboardingDone(done: Boolean) = write(ONBOARDING_DONE, done)
    suspend fun setUnits(units: String) = write(UNITS, units)
    suspend fun setReminderMinuteOfDay(minute: Int) = write(REMINDER_MINUTE, minute)
    suspend fun setSpriteStyleId(id: String) = write(SPRITE_STYLE_ID, id)
    suspend fun setLastProcessedTickAt(millis: Long) = write(LAST_TICK_AT, millis)
    suspend fun setSpeciesDbVersion(version: Int) = write(SPECIES_DB_VERSION, version)

    private fun <T> read(key: Preferences.Key<T>): Flow<T?> =
        dataStore.data.map { it[key] }.distinctUntilChanged()

    private fun <T : Any> read(key: Preferences.Key<T>, default: T): Flow<T> =
        dataStore.data.map { it[key] ?: default }.distinctUntilChanged()

    private suspend fun <T> write(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }

    private companion object {
        const val DEFAULT_REMINDER_MINUTE = 9 * 60
        val REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute_of_day")
        val SPRITE_STYLE_ID = stringPreferencesKey("sprite_style_id")
        val LAST_TICK_AT = longPreferencesKey("last_processed_tick_at")
        val SPECIES_DB_VERSION = intPreferencesKey("species_db_version")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val UNITS = stringPreferencesKey("units")
    }
}
