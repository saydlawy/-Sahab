package com.saydlawy.sahab.feature.instantwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.Charset

@Serializable
private data class OpenSubV3Response(
    val subtitles: List<OpenSubV3Item> = emptyList(),
)

@Serializable
private data class OpenSubV3Item(
    val id: String = "",
    val url: String = "",
    val lang: String = "",
    val subtitleFileName: String = "",
    val movieReleaseName: String = "",
)

@Serializable
private data class WyzieSubtitleItem(
    val id: String = "",
    val url: String = "",
    val display: String = "",
    val language: String = "",
    val format: String = "",
)

data class OnlineSubtitleOption(
    val id: String,
    val label: String,
    val url: String,
    val format: String,
)

class OnlineSubtitleFetcher(
    private val client: OkHttpClient,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun searchArabic(
        tmdbId: Int,
        imdbId: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): List<OnlineSubtitleOption> = withContext(Dispatchers.IO) {
        val results = ArrayList<OnlineSubtitleOption>()

        // 1) OpenSubtitles v3 (primary when IMDb ID is available)
        if (!imdbId.isNullOrBlank()) {
            results.addAll(searchOpenSubtitlesV3(imdbId.trim(), seasonNumber, episodeNumber))
        }

        // 2) Wyzie (sub.wyzie.io) as secondary / fallback
        if (results.size < 5) {
            results.addAll(searchWyzie(tmdbId, seasonNumber, episodeNumber))
        }

        results.distinctBy { it.url }.take(10)
    }

    private fun searchOpenSubtitlesV3(
        imdbId: String,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): List<OnlineSubtitleOption> {
        val cleanImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        val url = if (seasonNumber != null && episodeNumber != null) {
            "https://opensubtitles-v3.strem.io/subtitles/series/$cleanImdb:$seasonNumber:$episodeNumber.json"
        } else {
            "https://opensubtitles-v3.strem.io/subtitles/movie/$cleanImdb.json"
        }

        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "SahabInstantWatch/1.0")
            .header("Accept", "application/json")
            .get()
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return emptyList()

        val parsed = runCatching {
            json.decodeFromString<OpenSubV3Response>(body)
        }.getOrNull() ?: return emptyList()

        return parsed.subtitles
            .filter {
                val l = it.lang.lowercase()
                (l == "ara" || l == "ar" || l == "arabic") && it.url.startsWith("https://")
            }
            .take(8)
            .mapIndexed { idx, item ->
                val rawName = item.movieReleaseName
                    .ifBlank { item.subtitleFileName }
                    .ifBlank { "OpenSubtitles عربي #${idx + 1}" }
                OnlineSubtitleOption(
                    id = "os_${item.id.ifBlank { idx.toString() }}",
                    label = "OS • $rawName",
                    url = item.url,
                    format = "SRT",
                )
            }
    }

    private fun searchWyzie(
        tmdbId: Int,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): List<OnlineSubtitleOption> {
        val urlBuilder = "https://sub.wyzie.io/search".toHttpUrl().newBuilder()
            .addQueryParameter("id", tmdbId.toString())
            .addQueryParameter("language", "ar")

        if (seasonNumber != null && episodeNumber != null) {
            urlBuilder.addQueryParameter("season", seasonNumber.toString())
            urlBuilder.addQueryParameter("episode", episodeNumber.toString())
        }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", "SahabInstantWatch/1.0")
            .header("Accept", "application/json")
            .get()
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return emptyList()

        val items = runCatching {
            json.decodeFromString<List<WyzieSubtitleItem>>(body)
        }.getOrDefault(emptyList())

        return items
            .filter { it.url.startsWith("https://") }
            .take(6)
            .mapIndexed { idx, item ->
                val fmt = item.format.ifBlank { "SRT" }.uppercase()
                val name = item.display.ifBlank { "ترجمة عربية #${idx + 1}" }
                OnlineSubtitleOption(
                    id = "wz_${item.id.ifBlank { idx.toString() }}",
                    label = "Wyzie • $name ($fmt)",
                    url = item.url,
                    format = fmt,
                )
            }
    }

    suspend fun downloadSubtitleText(url: String): String? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "SahabInstantWatch/1.0")
            .get()
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                val utf8 = bytes.toString(Charsets.UTF_8)
                if (utf8.contains('\uFFFD')) {
                    runCatching { bytes.toString(Charset.forName("windows-1256")) }.getOrDefault(utf8)
                } else {
                    utf8
                }
            }
        }.getOrNull()
    }
}
