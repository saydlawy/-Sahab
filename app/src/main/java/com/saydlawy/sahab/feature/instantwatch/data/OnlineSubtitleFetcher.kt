package com.saydlawy.sahab.feature.instantwatch.data

import com.saydlawy.sahab.feature.instantwatch.player.SubtitleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

enum class SubtitleSourceFilter(val label: String) {
    ALL("الكل"),
    SUBDL("SubDL"),
    OPENSUB_OFFICIAL("OS رسمي"),
    FREE_V3("مجاني"),
}

data class OnlineSubtitleOption(
    val id: String,
    val label: String,
    val sourceTag: String,
    val url: String,
    val format: String = "SRT",
    val isZip: Boolean = false,
    val openSubFileId: Int? = null,
)

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
private data class SubdlResponse(
    val status: Boolean = false,
    val subtitles: List<SubdlItem> = emptyList(),
)

@Serializable
private data class SubdlItem(
    @SerialName("release_name") val releaseName: String = "",
    val name: String = "",
    val lang: String = "",
    val url: String = "",
    val author: String = "",
)

@Serializable
private data class OsOfficialResponse(
    val data: List<OsOfficialItem> = emptyList(),
)

@Serializable
private data class OsOfficialItem(
    val id: String = "",
    val attributes: OsOfficialAttr? = null,
)

@Serializable
private data class OsOfficialAttr(
    val release: String = "",
    @SerialName("download_count") val downloadCount: Int = 0,
    val files: List<OsOfficialFile> = emptyList(),
)

@Serializable
private data class OsOfficialFile(
    @SerialName("file_id") val fileId: Int = 0,
    @SerialName("file_name") val fileName: String = "",
)

@Serializable
private data class OsDownloadResponse(
    val link: String = "",
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
        isTv: Boolean = false,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        manualQuery: String? = null,
        sourceFilter: SubtitleSourceFilter = SubtitleSourceFilter.ALL,
        subdlApiKey: String = "",
        openSubtitlesApiKey: String = "",
    ): List<OnlineSubtitleOption> = withContext(Dispatchers.IO) {
        val results = ArrayList<OnlineSubtitleOption>()
        val cleanQuery = manualQuery?.trim().orEmpty()

        val wantSubdl = sourceFilter == SubtitleSourceFilter.ALL || sourceFilter == SubtitleSourceFilter.SUBDL
        val wantOsOfficial = sourceFilter == SubtitleSourceFilter.ALL || sourceFilter == SubtitleSourceFilter.OPENSUB_OFFICIAL
        val wantFreeV3 = sourceFilter == SubtitleSourceFilter.ALL || sourceFilter == SubtitleSourceFilter.FREE_V3

        if (wantSubdl && subdlApiKey.isNotBlank()) {
            results.addAll(
                searchSubdl(
                    apiKey = subdlApiKey.trim(),
                    tmdbId = tmdbId,
                    imdbId = imdbId,
                    isTv = isTv,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    manualQuery = cleanQuery,
                )
            )
        }

        if (wantOsOfficial && openSubtitlesApiKey.isNotBlank()) {
            results.addAll(
                searchOpenSubtitlesOfficial(
                    apiKey = openSubtitlesApiKey.trim(),
                    tmdbId = tmdbId,
                    seasonNumber = seasonNumber,
                    episodeNumber = episodeNumber,
                    manualQuery = cleanQuery,
                )
            )
        }

        if (wantFreeV3 && !imdbId.isNullOrBlank()) {
            results.addAll(searchOpenSubtitlesV3(imdbId.trim(), seasonNumber, episodeNumber))
        }

        results.distinctBy { it.id }.take(20)
    }

    private fun searchSubdl(
        apiKey: String,
        tmdbId: Int,
        imdbId: String?,
        isTv: Boolean,
        seasonNumber: Int?,
        episodeNumber: Int?,
        manualQuery: String,
    ): List<OnlineSubtitleOption> {
        val urlBuilder = "https://api.subdl.com/api/v1/subtitles".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("languages", "AR")
            .addQueryParameter("subs_per_page", "12")
            .addQueryParameter("type", if (isTv) "tv" else "movie")

        if (manualQuery.isNotBlank()) {
            urlBuilder.addQueryParameter("film_name", manualQuery)
        } else if (!imdbId.isNullOrBlank()) {
            val cleanImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
            urlBuilder.addQueryParameter("imdb_id", cleanImdb)
        } else {
            urlBuilder.addQueryParameter("tmdb_id", tmdbId.toString())
        }

        if (isTv && seasonNumber != null) {
            urlBuilder.addQueryParameter("season_number", seasonNumber.toString())
            if (episodeNumber != null) {
                urlBuilder.addQueryParameter("episode_number", episodeNumber.toString())
            }
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

        val parsed = runCatching {
            json.decodeFromString<SubdlResponse>(body)
        }.getOrNull() ?: return emptyList()

        return parsed.subtitles
            .filter { it.url.isNotBlank() }
            .take(10)
            .mapIndexed { idx, item ->
                val fullUrl = if (item.url.startsWith("http")) item.url else "https://dl.subdl.com${item.url}"
                val title = item.releaseName.ifBlank { item.name }.ifBlank { "ترجمة عربية #${idx + 1}" }
                val authorPart = if (item.author.isNotBlank()) " • ${item.author}" else ""
                OnlineSubtitleOption(
                    id = "subdl_${fullUrl.hashCode()}",
                    label = "$title$authorPart",
                    sourceTag = "SubDL",
                    url = fullUrl,
                    format = "ZIP",
                    isZip = true,
                )
            }
    }

    private fun searchOpenSubtitlesOfficial(
        apiKey: String,
        tmdbId: Int,
        seasonNumber: Int?,
        episodeNumber: Int?,
        manualQuery: String,
    ): List<OnlineSubtitleOption> {
        val urlBuilder = "https://api.opensubtitles.com/api/v1/subtitles".toHttpUrl().newBuilder()
            .addQueryParameter("languages", "ar")
            .addQueryParameter("machine_translated", "exclude")
            .addQueryParameter("ai_translated", "exclude")
            .addQueryParameter("order_by", "download_count")
            .addQueryParameter("order_direction", "desc")

        if (manualQuery.isNotBlank()) {
            urlBuilder.addQueryParameter("query", manualQuery)
        } else {
            urlBuilder.addQueryParameter("tmdb_id", tmdbId.toString())
        }
        seasonNumber?.let { urlBuilder.addQueryParameter("season_number", it.toString()) }
        episodeNumber?.let { urlBuilder.addQueryParameter("episode_number", it.toString()) }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("Api-Key", apiKey)
            .header("User-Agent", "SahabInstantWatch v1.0")
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
            json.decodeFromString<OsOfficialResponse>(body)
        }.getOrNull() ?: return emptyList()

        return parsed.data.mapNotNull { item ->
            val attr = item.attributes ?: return@mapNotNull null
            val firstFile = attr.files.firstOrNull() ?: return@mapNotNull null
            val label = attr.release.ifBlank { firstFile.fileName }.ifBlank { "OpenSubtitles #${firstFile.fileId}" }
            OnlineSubtitleOption(
                id = "os_off_${firstFile.fileId}",
                label = label,
                sourceTag = "OS رسمي",
                url = "",
                format = "SRT",
                openSubFileId = firstFile.fileId,
            )
        }.take(10)
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
            .sortedByDescending { item ->
                val n = (item.movieReleaseName + " " + item.subtitleFileName).lowercase()
                when {
                    "web-dl" in n || "webdl" in n || "bluray" in n -> 3
                    "webrip" in n || "hdrip" in n -> 2
                    "cam" in n || "ts" in n || "telesync" in n -> -2
                    else -> 1
                }
            }
            .take(8)
            .mapIndexed { idx, item ->
                val rawName = item.movieReleaseName
                    .ifBlank { item.subtitleFileName }
                    .ifBlank { "ترجمة #${idx + 1}" }
                OnlineSubtitleOption(
                    id = "osv3_${item.id.ifBlank { idx.toString() }}",
                    label = rawName,
                    sourceTag = "مجاني",
                    url = item.url,
                    format = "SRT",
                )
            }
    }

    suspend fun downloadSubtitleText(
        option: OnlineSubtitleOption,
        openSubtitlesApiKey: String = "",
    ): String? = withContext(Dispatchers.IO) {
        val downloadUrl = if (option.openSubFileId != null) {
            resolveOpenSubOfficialDownloadUrl(option.openSubFileId, openSubtitlesApiKey)
        } else {
            option.url
        } ?: return@withContext null

        if (!downloadUrl.startsWith("https://")) return@withContext null

        val req = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "SahabInstantWatch/1.0")
            .get()
            .build()

        runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val bytes = resp.body?.bytes() ?: return@use null
                val rawBytes = if (option.isZip || (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte())) {
                    extractFirstSubtitleFromZip(bytes) ?: return@use null
                } else {
                    bytes
                }
                decodeArabicBytes(rawBytes)
            }
        }.getOrNull()
    }

    private fun resolveOpenSubOfficialDownloadUrl(fileId: Int, apiKey: String): String? {
        if (apiKey.isBlank()) return null
        val payload = """{"file_id":$fileId}""".toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("https://api.opensubtitles.com/api/v1/download")
            .header("Api-Key", apiKey.trim())
            .header("User-Agent", "SahabInstantWatch v1.0")
            .header("Accept", "application/json")
            .post(payload)
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return null

        return runCatching {
            json.decodeFromString<OsDownloadResponse>(body).link.takeIf { it.startsWith("https://") }
        }.getOrNull()
    }

    private fun extractFirstSubtitleFromZip(zipBytes: ByteArray): ByteArray? {
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory && (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass"))) {
                    return zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }
        return null
    }

    private fun decodeArabicBytes(bytes: ByteArray): String {
        val utf8 = bytes.toString(Charsets.UTF_8)
        return if (utf8.contains('\uFFFD')) {
            runCatching { bytes.toString(Charset.forName("windows-1256")) }.getOrDefault(utf8)
        } else {
            utf8
        }
    }

    fun isLikelyPersian(text: String): Boolean {
        val sample = text.take(15_000)
        var persianChars = 0
        for (ch in sample) {
            if (ch == 'پ' || ch == 'چ' || ch == 'ژ' || ch == 'گ') {
                persianChars++
                if (persianChars >= 5) return true
            }
        }
        return false
    }

    fun extractPreviewLines(text: String): String {
        val cues = SubtitleParser.parse(text)
        return cues.take(2).joinToString("\n") { it.text }.ifBlank { "لا توجد أسطر قابلة للعرض." }
    }
}
