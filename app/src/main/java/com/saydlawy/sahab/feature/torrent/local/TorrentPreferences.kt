package com.saydlawy.sahab.feature.torrent.local

import android.content.Context
import com.saydlawy.sahab.feature.torrent.domain.TorrentAddonConfig
import com.saydlawy.sahab.feature.torrent.domain.TorrentEngineMode
import com.saydlawy.sahab.feature.torrent.domain.TorrentSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class TorrentPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<TorrentSettings> = _settings.asStateFlow()
    val current: TorrentSettings get() = _settings.value

    private fun load(): TorrentSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return TorrentSettings()
        return runCatching { json.decodeFromString<TorrentSettings>(raw) }
            .getOrDefault(TorrentSettings())
    }

    fun update(transform: (TorrentSettings) -> TorrentSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(updated)).apply()
    }

    fun setEnabled(enabled: Boolean) = update { it.copy(enabled = enabled) }

    fun setEngineMode(mode: TorrentEngineMode) = update { it.copy(engineMode = mode) }

    fun setTorrServer(url: String, user: String = "", pass: String = "") = update {
        it.copy(
            torrServerUrl = url.trim().trimEnd('/'),
            torrServerUser = user.trim(),
            torrServerPass = pass.trim(),
        )
    }

    fun setDebridApiKey(apiKey: String) = update { it.copy(debridApiKey = apiKey.trim()) }

    fun setPreferArabicSubtitle(prefer: Boolean) = update { it.copy(preferArabicSubtitle = prefer) }

    fun addAddon(name: String, manifestUrl: String) = update { cur ->
        val cleanUrl = manifestUrl.trim()
        if (cleanUrl.isBlank()) return@update cur
        val cleanName = name.trim().ifBlank {
            cleanUrl.removePrefix("https://").removePrefix("http://").substringBefore("/")
        }
        val exists = cur.addons.any { it.manifestUrl.equals(cleanUrl, ignoreCase = true) }
        if (exists) return@update cur
        val addon = TorrentAddonConfig(
            id = UUID.randomUUID().toString(),
            name = cleanName,
            manifestUrl = cleanUrl,
            enabled = true,
        )
        cur.copy(addons = cur.addons + addon)
    }

    fun toggleAddon(id: String, enabled: Boolean) = update { cur ->
        cur.copy(addons = cur.addons.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun removeAddon(id: String) = update { cur ->
        cur.copy(addons = cur.addons.filterNot { it.id == id })
    }

    companion object {
        private const val PREFS_NAME = "sahab_torrent_prefs"
        private const val KEY_SETTINGS = "torrent_settings_json"
    }
}
