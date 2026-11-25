package com.saydlawy.sahab.feature.instantwatch.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstantWatchSettings(
    val enabled: Boolean = true,
    val securityProtectionAlwaysOn: Boolean = true,
    val autoArabicSubtitles: Boolean = true,
    val saveProgressLocally: Boolean = true,
    val externalSource: String = SOURCE_VIDLINK,
    val subdlApiKey: String = "",
    val openSubtitlesApiKey: String = "",
) {
    companion object {
        const val SOURCE_VIDLINK = "VidLink"
    }
}

class InstantWatchPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<InstantWatchSettings> = _settings.asStateFlow()

    val isEnabled: Boolean
        get() = _settings.value.enabled

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _settings.value = readSettings()
    }

    fun setAutoArabicSubtitles(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_AR_SUBS, enabled).apply()
        _settings.value = readSettings()
    }

    fun setSaveProgressLocally(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SAVE_PROGRESS, enabled).apply()
        _settings.value = readSettings()
    }

    fun setExternalSource(source: String) {
        val sanitized = source.ifBlank { InstantWatchSettings.SOURCE_VIDLINK }
        prefs.edit().putString(KEY_EXTERNAL_SOURCE, sanitized).apply()
        _settings.value = readSettings()
    }

    fun setSubtitleApiKeys(subdlKey: String, openSubtitlesKey: String) {
        prefs.edit()
            .putString(KEY_SUBDL_API, subdlKey.trim())
            .putString(KEY_OPENSUB_API, openSubtitlesKey.trim())
            .apply()
        _settings.value = readSettings()
    }

    private fun readSettings(): InstantWatchSettings = InstantWatchSettings(
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        securityProtectionAlwaysOn = true,
        autoArabicSubtitles = prefs.getBoolean(KEY_AUTO_AR_SUBS, true),
        saveProgressLocally = prefs.getBoolean(KEY_SAVE_PROGRESS, true),
        externalSource = prefs.getString(KEY_EXTERNAL_SOURCE, InstantWatchSettings.SOURCE_VIDLINK)
            ?: InstantWatchSettings.SOURCE_VIDLINK,
        subdlApiKey = prefs.getString(KEY_SUBDL_API, "") ?: "",
        openSubtitlesApiKey = prefs.getString(KEY_OPENSUB_API, "") ?: "",
    )

    companion object {
        private const val PREFS_NAME = "instant_watch_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_AR_SUBS = "auto_arabic_subtitles"
        private const val KEY_SAVE_PROGRESS = "save_progress_locally"
        private const val KEY_EXTERNAL_SOURCE = "external_source"
        private const val KEY_SUBDL_API = "subdl_api_key"
        private const val KEY_OPENSUB_API = "opensubtitles_api_key"
    }
}
