package com.saydlawy.sahab.feature.instantwatch.local

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

@Serializable
data class InstantWatchlistEntry(
    val tmdbId: Int,
    val mediaType: String,
    val title: String,
    val originalTitle: String = title,
    val releaseYear: Int? = null,
    val posterUrl: String? = null,
    val rating: Double? = null,
)

@Serializable
data class InstantCustomWatchlist(
    val id: String,
    val name: String,
    val kind: String = KIND_CUSTOM,
    val personId: Int? = null,
    val items: List<InstantWatchlistEntry> = emptyList(),
) {
    companion object {
        const val KIND_CUSTOM = "CUSTOM"
        const val KIND_ACTOR = "ACTOR"
    }
}

class InstantWatchPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<InstantWatchSettings> = _settings.asStateFlow()

    private val _customWatchlists = MutableStateFlow(readCustomWatchlists())
    val customWatchlists: StateFlow<List<InstantCustomWatchlist>> = _customWatchlists.asStateFlow()

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

    fun saveCustomWatchlist(list: InstantCustomWatchlist) {
        val current = _customWatchlists.value.toMutableList()
        val idx = current.indexOfFirst { it.id == list.id }
        if (idx >= 0) current[idx] = list else current.add(0, list)
        persistWatchlists(current)
    }

    fun deleteCustomWatchlist(id: String) {
        val updated = _customWatchlists.value.filterNot { it.id == id }
        persistWatchlists(updated)
    }

    private fun persistWatchlists(lists: List<InstantCustomWatchlist>) {
        val raw = runCatching { json.encodeToString(lists) }.getOrDefault("[]")
        prefs.edit().putString(KEY_CUSTOM_WATCHLISTS, raw).apply()
        _customWatchlists.value = lists
    }

    private fun readCustomWatchlists(): List<InstantCustomWatchlist> {
        val raw = prefs.getString(KEY_CUSTOM_WATCHLISTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<InstantCustomWatchlist>>(raw) }
            .getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "instant_watch_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_AR_SUBS = "auto_arabic_subtitles"
        private const val KEY_SAVE_PROGRESS = "save_progress_locally"
        private const val KEY_EXTERNAL_SOURCE = "external_source"
        private const val KEY_SUBDL_API = "subdl_api_key"
        private const val KEY_OPENSUB_API = "opensubtitles_api_key"
        private const val KEY_CUSTOM_WATCHLISTS = "custom_watchlists_json"
    }
}
