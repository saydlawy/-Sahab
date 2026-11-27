package com.saydlawy.sahab.feature.torrent.domain

import kotlinx.serialization.Serializable

@Serializable
enum class TorrentEngineMode(val labelAr: String) {
    AUTO("تلقائي (رابط مباشر أو TorrServer / Debrid)"),
    TORRSERVER("TorrServer (مجاني - محلي أو شبكة)"),
    REAL_DEBRID("Real-Debrid (سحابي فوري)"),
    TORBOX("TorBox (سحابي فوري)"),
}

@Serializable
data class TorrentAddonConfig(
    val id: String,
    val name: String,
    val manifestUrl: String,
    val enabled: Boolean = true,
) {
    val baseUrl: String
        get() = manifestUrl.trim().removeSuffix("/manifest.json").trimEnd('/')
}

@Serializable
data class TorrentSettings(
    val enabled: Boolean = false,
    val engineMode: TorrentEngineMode = TorrentEngineMode.AUTO,
    val torrServerUrl: String = "http://127.0.0.1:8090",
    val torrServerUser: String = "",
    val torrServerPass: String = "",
    val debridApiKey: String = "",
    val addons: List<TorrentAddonConfig> = emptyList(),
    val preferArabicSubtitle: Boolean = true,
) {
    val hasConfiguredAddons: Boolean
        get() = addons.any { it.enabled && it.baseUrl.startsWith("http") }
}

data class TorrentStreamCandidate(
    val id: String,
    val addonName: String,
    val rawTitle: String,
    val directUrl: String? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val trackers: List<String> = emptyList(),
    val resolution: String = "",
    val resolutionRank: Int = 0,
    val sourceTag: String = "",
    val hdrTag: String = "",
    val audioTag: String = "",
    val sizeGb: Double? = null,
    val seeders: Int? = null,
    val isInstantCached: Boolean = false,
) {
    val isDirectHttp: Boolean
        get() = !directUrl.isNullOrBlank() && directUrl.startsWith("http")
}

sealed class TorrentResolutionResult {
    data class Ready(
        val streamUrl: String,
        val headers: Map<String, String> = emptyMap(),
        val torrServerHashToCleanup: String? = null,
    ) : TorrentResolutionResult()

    data class Error(val message: String) : TorrentResolutionResult()
}
