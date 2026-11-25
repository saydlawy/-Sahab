package com.saydlawy.sahab.feature.instantwatch.data

import com.saydlawy.sahab.feature.instantwatch.security.StreamSecurityPolicy
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

data class SubtitleQuery(
    val tmdbId: Int,
    val originalTitle: String,
    val releaseYear: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val language: String = "ar",
    val releaseHint: String? = null,
)

data class SubtitleCandidate(
    val fileId: Int,
    val fileName: String,
    val language: String,
    val downloadCount: Int,
    val releaseName: String,
    val downloadUrl: String? = null,
)

class SubtitleRepository(
    private val client: OkHttpClient,
    private val securityPolicy: StreamSecurityPolicy,
    private val apiKey: String = "",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    suspend fun searchArabicSubtitles(query: SubtitleQuery): List<SubtitleCandidate> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()

        val urlBuilder = "$API_BASE/subtitles".toHttpUrl().newBuilder()
            .addQueryParameter("tmdb_id", query.tmdbId.toString())
            .addQueryParameter("languages", query.language)
            .addQueryParameter("order_by", "download_count")
            .addQueryParameter("order_direction", "desc")

        if (query.originalTitle.isNotBlank()) {
            urlBuilder.addQueryParameter("query", query.originalTitle)
        }
        query.releaseYear?.let { urlBuilder.addQueryParameter("year", it.toString()) }
        query.seasonNumber?.let { urlBuilder.addQueryParameter("season_number", it.toString()) }
        query.episodeNumber?.let { urlBuilder.addQueryParameter("episode_number", it.toString()) }

        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext emptyList()

        val parsed = runCatching { json.decodeFromString<SearchResponse>(body) }.getOrNull()
            ?: return@withContext emptyList()

        val hint = query.releaseHint?.lowercase().orEmpty()
        parsed.data.mapNotNull { item ->
            val attrs = item.attributes ?: return@mapNotNull null
            val firstFile = attrs.files.firstOrNull() ?: return@mapNotNull null
            SubtitleCandidate(
                fileId = firstFile.fileId,
                fileName = firstFile.fileName.orEmpty().ifBlank { "subtitle_${firstFile.fileId}.srt" },
                language = attrs.language.orEmpty().ifBlank { query.language },
                downloadCount = attrs.downloadCount,
                releaseName = attrs.release.orEmpty(),
            )
        }.sortedByDescending { candidate ->
            val hintMatch = if (hint.isNotBlank() && candidate.releaseName.lowercase().contains(hint)) 1_000_000 else 0
            hintMatch + candidate.downloadCount
        }
    }

    suspend fun requestDownloadLink(fileId: Int): String? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || fileId <= 0) return@withContext null
        val payload = """{"file_id":$fileId}""".toRequestBody("application/json".toMediaType())
        val req = Request.Builder()
            .url("$API_BASE/download")
            .header("Api-Key", apiKey)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .post(payload)
            .build()

        val body = runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        val link = runCatching { json.decodeFromString<DownloadResponse>(body).link }.getOrNull()
            ?.trim()
            .orEmpty()
        if (link.isEmpty()) return@withContext null

        val check = securityPolicy.validateUrl(link, requireTrustedHost = false)
        if (!check.isAllowed) return@withContext null
        link
    }

    @Serializable
    private data class SearchResponse(val data: List<SubtitleItemDto> = emptyList())

    @Serializable
    private data class SubtitleItemDto(val attributes: SubtitleAttrsDto? = null)

    @Serializable
    private data class SubtitleAttrsDto(
        val language: String? = null,
        @SerialName("download_count") val downloadCount: Int = 0,
        val release: String? = null,
        val files: List<SubtitleFileDto> = emptyList(),
    )

    @Serializable
    private data class SubtitleFileDto(
        @SerialName("file_id") val fileId: Int,
        @SerialName("file_name") val fileName: String? = null,
    )

    @Serializable
    private data class DownloadResponse(val link: String? = null)

    companion object {
        private const val API_BASE = "https://api.opensubtitles.com/api/v1"
        private const val USER_AGENT = "SahabInstantWatch v1.0"
    }
}
