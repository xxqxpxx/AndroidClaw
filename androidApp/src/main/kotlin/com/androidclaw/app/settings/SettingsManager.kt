package com.androidclaw.app.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Centralized settings manager providing reactive state for all app preferences.
 * Uses SharedPreferences under the hood for zero-dependency persistence.
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("androidclaw_settings", Context.MODE_PRIVATE)

    // Reactive state flows
    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _apiKey = MutableStateFlow(prefs.getString(KEY_API_KEY, "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _model = MutableStateFlow(prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL)
    val model: StateFlow<String> = _model.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.fromString(prefs.getString(KEY_THEME, "system") ?: "system"))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _dynamicColors = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLORS, true))
    val dynamicColors: StateFlow<Boolean> = _dynamicColors.asStateFlow()

    private val _voiceEnabled = MutableStateFlow(prefs.getBoolean(KEY_VOICE_ENABLED, true))
    val voiceEnabled: StateFlow<Boolean> = _voiceEnabled.asStateFlow()

    private val _alwaysListening = MutableStateFlow(prefs.getBoolean(KEY_ALWAYS_LISTENING, false))
    val alwaysListening: StateFlow<Boolean> = _alwaysListening.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    private val _onboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    val onboardingCompleted: StateFlow<Boolean> = _onboardingCompleted.asStateFlow()

    private val _maxContextTokens = MutableStateFlow(prefs.getInt(KEY_MAX_CONTEXT_TOKENS, DEFAULT_MAX_CONTEXT_TOKENS))
    val maxContextTokens: StateFlow<Int> = _maxContextTokens.asStateFlow()

    fun setServerUrl(url: String) { _serverUrl.value = url; prefs.edit().putString(KEY_SERVER_URL, url).apply() }
    fun setApiKey(key: String) { _apiKey.value = key; prefs.edit().putString(KEY_API_KEY, key).apply() }
    fun setModel(model: String) { _model.value = model; prefs.edit().putString(KEY_MODEL, model).apply() }
    fun setThemeMode(mode: ThemeMode) { _themeMode.value = mode; prefs.edit().putString(KEY_THEME, mode.name.lowercase()).apply() }
    fun setDynamicColors(enabled: Boolean) { _dynamicColors.value = enabled; prefs.edit().putBoolean(KEY_DYNAMIC_COLORS, enabled).apply() }
    fun setVoiceEnabled(enabled: Boolean) { _voiceEnabled.value = enabled; prefs.edit().putBoolean(KEY_VOICE_ENABLED, enabled).apply() }
    fun setAlwaysListening(enabled: Boolean) { _alwaysListening.value = enabled; prefs.edit().putBoolean(KEY_ALWAYS_LISTENING, enabled).apply() }
    fun setHapticFeedback(enabled: Boolean) { _hapticFeedback.value = enabled; prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply() }
    fun setOnboardingCompleted(completed: Boolean) { _onboardingCompleted.value = completed; prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply() }
    fun setMaxContextTokens(tokens: Int) { _maxContextTokens.value = tokens; prefs.edit().putInt(KEY_MAX_CONTEXT_TOKENS, tokens).apply() }

    val isConfigured: Boolean
        get() = _serverUrl.value.isNotBlank()

    companion object {
        const val KEY_SERVER_URL = "server_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
        const val KEY_THEME = "theme_mode"
        const val KEY_DYNAMIC_COLORS = "dynamic_colors"
        const val KEY_VOICE_ENABLED = "voice_enabled"
        const val KEY_ALWAYS_LISTENING = "always_listening"
        const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        const val KEY_MAX_CONTEXT_TOKENS = "max_context_tokens"

        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
        const val DEFAULT_MODEL = "claude-sonnet-4-20250514"
        const val DEFAULT_MAX_CONTEXT_TOKENS = 100000
    }
}

enum class ThemeMode {
    LIGHT, DARK, SYSTEM;

    companion object {
        fun fromString(value: String): ThemeMode = when (value.lowercase()) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }
}
