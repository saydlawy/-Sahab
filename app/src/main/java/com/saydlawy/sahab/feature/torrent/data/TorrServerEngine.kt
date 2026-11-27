package com.saydlawy.sahab.feature.torrent.data

import com.saydlawy.sahab.feature.torrent.domain.TorrentResolutionResult
import com.saydlawy.sahab.feature.torrent.domain.TorrentSettings
import com.saydlawy.sahab.feature.torrent.domain.TorrentStreamCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

class TorrServerEngine(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val videoExts = setOf("mkv", "mp4", "m4v", "avi", "mov", "m2ts", "ts", "webm")

    suspend fun resolve(
        candidate: TorrentStreamCandidate,
        settings: TorrentSettings,
    ): TorrentResolutionResult = withContext(Dispatchers.IO) {
        val baseUrl = settings.torrServerUrl.trim().trimEnd('/')
        if (baseUrl.toHttpUrlOrNull() == null) {
            return@withContext TorrentResolutionResult.Error("عنوان TorrServer غير صالح. تحقق منه في الإعدادات.")
        }
        val hash = candidate.infoHash
            ?: return@withContext TorrentResolutionResult.Error("المصدر لا يحتوي على InfoHash صالح.")

        val magnet = buildMagnet(hash, candidate.rawTitle, candidate.trackers)
        val headers = authHeaders(settings)

        val siloPayload = buildJsonObject {
            put("link", magnet)
            put("title", candidate.rawTitle.take(120))
        }.toString()

        var isSilo = true
        var addResponse = executePost("$baseUrl/api/torrents", siloPayload, headers)
        if (addResponse == null) {
            isSilo = false
            val classicPayload = buildJsonObject {
                put("action", "add")
                put("link", magnet)
                put("title", candidate.rawTitle.take(120))
                put("save_to_db", false)
            }.toString()
            addResponse = executePost("$baseUrl/torrents", classicPayload, headers)
        }
        val body = addResponse
            ?: return@withContext TorrentResolutionResult.Error("تعذّر الاتصال بـ TorrServer على $baseUrl")

        val resolvedHash = extractHash(body) ?: hash
        val fileIndex = resolveFileIndex(body, candidate.fileIdx)

        val streamUrl = if (isSilo) {
            "$baseUrl/api/stream/$resolvedHash/$fileIndex"
        } else {
            "$baseUrl/stream?link=$resolvedHash&index=$fileIndex&play"
        }
        TorrentResolutionResult.Ready(
            streamUrl = streamUrl,
            headers = headers,
            torrServerHashToCleanup = resolvedHash,
        )
    }

    suspend fun cleanup(hash: String, settings: TorrentSettings) = withContext(Dispatchers.IO) {
        val baseUrl = settings.torrServerUrl.trim().trimEnd('/')
        if (hash.isBlank() || baseUrl.toHttpUrlOrNull() == null) return@withContext
        val headers = authHeaders(settings)
        if (!executeDelete("$baseUrl/api/torrents/$hash", headers)) {
            val dropPayload = buildJsonObject {
                put("action", "drop")
                put("hash", hash)
            }.toString()
            executePost("$baseUrl/torrents", dropPayload, headers)
        }
    }

    suspend fun testConnection(settings: TorrentSettings): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = settings.torrServerUrl.trim().trimEnd('/')
            require(baseUrl.toHttpUrlOrNull() != null) { "عنوان TorrServer غير صالح" }
            val headers = authHeaders(settings)
            val probePaths = listOf("/api/system/ping", "/echo", "/", "/plugir")
            var lastCode: Int? = null
            for (path in probePaths) {
                val reqBuilder = Request.Builder().url("$baseUrl$path").get()
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    lastCode = resp.code
                    val bodyText = resp.body?.string()?.trim().orEmpty()
                    if (resp.isSuccessful || resp.code in 300..399) {
                        val label = when {
                            path == "/echo" && bodyText.isNotBlank() && !bodyText.contains("404", ignoreCase = true) ->
                                "متصل ($bodyText)"
                            path == "/api/system/ping" || path == "/plugir" || bodyText.contains("Silo", ignoreCase = true) ->
                                "متصل (TorrServer Silo)"
                            else -> "متصل بنجاح"
                        }
                        return@runCatching label
                    }
                }
            }
            error("رمز الخطأ HTTP ${lastCode ?: 0}")
        }
    }

    private fun extractHash(body: String): String? = runCatching {
        json.parseToJsonElement(body).jsonObject["hash"]?.jsonPrimitive?.contentOrNull
            ?.trim()?.takeIf { it.length >= 32 }
    }.getOrNull()

    private fun resolveFileIndex(body: String, preferredZeroBasedIdx: Int?): Int {
        if (preferredZeroBasedIdx != null && preferredZeroBasedIdx >= 0) {
            return preferredZeroBasedIdx + 1
        }
        val bestId = runCatching {
            val stats = json.parseToJsonElement(body).jsonObject["file_stats"]?.jsonArray
                ?: return@runCatching null
            stats.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                val path = obj["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val length = obj["length"]?.jsonPrimitive?.longOrNull ?: 0L
                val ext = path.substringAfterLast('.', "").lowercase()
                Triple(id, ext in videoExts, length)
            }.filter { it.second }.maxByOrNull { it.third }?.first
        }.getOrNull()
        return bestId ?: 1
    }

    private fun buildMagnet(hash: String, title: String, trackers: List<String>): String {
        val sb = StringBuilder("magnet:?xt=urn:btih:").append(hash)
        if (title.isNotBlank()) {
            sb.append("&dn=").append(URLEncoder.encode(title.take(100), "UTF-8"))
        }
        trackers.take(8).forEach { tr ->
            sb.append("&tr=").append(URLEncoder.encode(tr, "UTF-8"))
        }
        return sb.toString()
    }

    private fun authHeaders(settings: TorrentSettings): Map<String, String> {
        if (settings.torrServerUser.isBlank()) return emptyMap()
        return mapOf("Authorization" to Credentials.basic(settings.torrServerUser, settings.torrServerPass))
    }

    private fun executePost(url: String, payload: String, headers: Map<String, String>): String? = runCatching {
        val reqBuilder = Request.Builder()
            .url(url)
            .post(payload.toRequestBody(jsonMedia))
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        client.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }.getOrNull()

    private fun executeDelete(url: String, headers: Map<String, String>): Boolean = runCatching {
        val reqBuilder = Request.Builder()
            .url(url)
            .delete()
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        client.newCall(reqBuilder.build()).execute().use { resp ->
            resp.isSuccessful
        }
    }.getOrDefault(false)
}
