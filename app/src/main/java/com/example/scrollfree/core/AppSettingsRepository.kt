package com.example.scrollfree.core

import android.content.Context
import android.content.SharedPreferences
import com.example.scrollfree.model.UserSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettingsRepository private constructor(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    fun refresh() {
        _settings.value = readSettings()
    }

    fun updateDetectionEnabled(enabled: Boolean) {
        persist(_settings.value.copy(detectionEnabled = enabled))
    }

    fun updateOverlayEnabled(enabled: Boolean) {
        persist(_settings.value.copy(overlayEnabled = enabled))
    }

    fun updateSensitivity(sensitivity: Int) {
        persist(_settings.value.copy(sensitivity = sensitivity.coerceIn(1, 100)))
    }

    fun updateCooldownMs(cooldownMs: Long) {
        persist(_settings.value.copy(cooldownMs = cooldownMs.coerceIn(500L, 3000L)))
    }

    fun updateDoubleBlinkWindowMs(windowMs: Long) {
        persist(_settings.value.copy(doubleBlinkWindowMs = windowMs.coerceIn(300L, 1200L)))
    }

    private fun persist(updated: UserSettings) {
        prefs.edit()
            .putBoolean(KEY_DETECTION_ENABLED, updated.detectionEnabled)
            .putBoolean(KEY_OVERLAY_ENABLED, updated.overlayEnabled)
            .putInt(KEY_SENSITIVITY, updated.sensitivity)
            .putLong(KEY_COOLDOWN_MS, updated.cooldownMs)
            .putLong(KEY_DOUBLE_BLINK_WINDOW_MS, updated.doubleBlinkWindowMs)
            .apply()
        _settings.value = updated
    }

    private fun readSettings(): UserSettings {
        return UserSettings(
            detectionEnabled = prefs.getBoolean(KEY_DETECTION_ENABLED, false),
            overlayEnabled = prefs.getBoolean(KEY_OVERLAY_ENABLED, true),
            sensitivity = prefs.getInt(KEY_SENSITIVITY, 55),
            cooldownMs = prefs.getLong(KEY_COOLDOWN_MS, 1200L),
            doubleBlinkWindowMs = prefs.getLong(KEY_DOUBLE_BLINK_WINDOW_MS, 650L)
        )
    }

    companion object {
        private const val PREFS_NAME = "scrollfree_prefs"
        private const val KEY_DETECTION_ENABLED = "detection_enabled"
        private const val KEY_OVERLAY_ENABLED = "overlay_enabled"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_COOLDOWN_MS = "cooldown_ms"
        private const val KEY_DOUBLE_BLINK_WINDOW_MS = "double_blink_window_ms"

        @Volatile
        private var instance: AppSettingsRepository? = null

        fun get(context: Context): AppSettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: AppSettingsRepository(context).also { instance = it }
            }
        }
    }
}
