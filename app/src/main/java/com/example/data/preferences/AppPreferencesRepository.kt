package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.SecurityManager
import com.example.data.local.entities.AppThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "hisabpulse_prefs")

data class SecurityPreferences(
    val pinEnabled: Boolean = false,
    val pinHash: String = "",
    val pinSalt: String = "",
    val biometricEnabled: Boolean = false,
    val autoLockMinutes: Int = 5,
    val hideAmounts: Boolean = false,
    val lastBackgroundMillis: Long = 0L
)

data class TutorialPreferences(
    val completed: Boolean = false,
    val currentStep: Int = 0
)

class AppPreferencesRepository(private val context: Context) {
    private val fastPrefs = context.applicationContext
        .getSharedPreferences("hisabpulse_fast_session", Context.MODE_PRIVATE)

    private object FastKeys {
        const val READY_UID = "ready_uid"
        const val ONBOARDING_DONE = "onboarding_done_sync"
        const val PIN_ENABLED = "pin_enabled_sync"
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
        val TUTORIAL_STEP = intPreferencesKey("tutorial_step")
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
        val LAST_BG_MILLIS = longPreferencesKey("last_bg_millis")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
    }

    val themeMode: Flow<AppThemeMode> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.THEME_MODE] ?: AppThemeMode.LIGHT.name
        runCatching { AppThemeMode.valueOf(raw) }.getOrDefault(AppThemeMode.LIGHT)
    }

    val onboardingCompleted: Flow<Boolean> = context.dataStore.data.map { prefs ->
        val value = prefs[Keys.ONBOARDING_COMPLETED] ?: false
        if (value && !isOnboardingCompletedSync()) {
            fastPrefs.edit().putBoolean(FastKeys.ONBOARDING_DONE, true).apply()
        }
        value
    }

    val tutorialPreferences: Flow<TutorialPreferences> = context.dataStore.data.map { prefs ->
        TutorialPreferences(
            completed = prefs[Keys.TUTORIAL_COMPLETED] ?: false,
            currentStep = prefs[Keys.TUTORIAL_STEP] ?: 0
        )
    }

    val securityPreferences: Flow<SecurityPreferences> = context.dataStore.data.map { prefs ->
        val pinEnabled = prefs[Keys.PIN_ENABLED] ?: false
        if (fastPrefs.getBoolean(FastKeys.PIN_ENABLED, false) != pinEnabled) {
            fastPrefs.edit().putBoolean(FastKeys.PIN_ENABLED, pinEnabled).apply()
        }
        SecurityPreferences(
            pinEnabled = pinEnabled,
            pinHash = prefs[Keys.PIN_HASH] ?: "",
            pinSalt = prefs[Keys.PIN_SALT] ?: "",
            biometricEnabled = prefs[Keys.BIOMETRIC_ENABLED] ?: false,
            autoLockMinutes = prefs[Keys.AUTO_LOCK_MINUTES] ?: 5,
            hideAmounts = prefs[Keys.HIDE_AMOUNTS] ?: false,
            lastBackgroundMillis = prefs[Keys.LAST_BG_MILLIS] ?: 0L
        )
    }

    val languageCode: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.LANGUAGE_CODE] ?: "en"
    }

    fun hasCompletedSetupForUidSync(uid: String?): Boolean {
        if (uid.isNullOrBlank()) return false
        return fastPrefs.getString(FastKeys.READY_UID, null) == uid
    }

    fun markSetupCompleteForUidSync(uid: String) {
        if (uid.isBlank()) return
        fastPrefs.edit()
            .putString(FastKeys.READY_UID, uid)
            .putBoolean(FastKeys.ONBOARDING_DONE, true)
            .apply()
    }

    fun clearSessionCacheSync() {
        fastPrefs.edit()
            .remove(FastKeys.READY_UID)
            .apply()
    }

    fun isOnboardingCompletedSync(): Boolean =
        fastPrefs.getBoolean(FastKeys.ONBOARDING_DONE, false)

    fun isPinEnabledSync(): Boolean =
        fastPrefs.getBoolean(FastKeys.PIN_ENABLED, false)

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        fastPrefs.edit().putBoolean(FastKeys.ONBOARDING_DONE, completed).apply()
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setTutorialCompleted(completed: Boolean, step: Int = 0) {
        context.dataStore.edit {
            it[Keys.TUTORIAL_COMPLETED] = completed
            it[Keys.TUTORIAL_STEP] = step
        }
    }

    suspend fun setPin(pin: String) {
        val salt = SecurityManager.generateSalt()
        val hash = SecurityManager.hashPin(pin, salt)
        fastPrefs.edit().putBoolean(FastKeys.PIN_ENABLED, true).apply()
        context.dataStore.edit {
            it[Keys.PIN_ENABLED] = true
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash
        }
    }

    suspend fun disablePin() {
        fastPrefs.edit().putBoolean(FastKeys.PIN_ENABLED, false).apply()
        context.dataStore.edit {
            it[Keys.PIN_ENABLED] = false
            it[Keys.PIN_SALT] = ""
            it[Keys.PIN_HASH] = ""
            it[Keys.BIOMETRIC_ENABLED] = false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val sec = securityPreferences.first()
        return SecurityManager.verifyPin(pin, sec.pinSalt, sec.pinHash)
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.AUTO_LOCK_MINUTES] = minutes }
    }

    suspend fun setHideAmounts(hide: Boolean) {
        context.dataStore.edit { it[Keys.HIDE_AMOUNTS] = hide }
    }

    suspend fun updateLastBackgroundMillis(millis: Long) {
        context.dataStore.edit { it[Keys.LAST_BG_MILLIS] = millis }
    }

    suspend fun setLanguageCode(code: String) {
        context.dataStore.edit { it[Keys.LANGUAGE_CODE] = code }
    }
}
