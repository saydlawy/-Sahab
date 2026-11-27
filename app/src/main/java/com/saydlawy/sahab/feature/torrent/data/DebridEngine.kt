package com.saydlawy.sahab.feature.torrent.data

import com.saydlawy.sahab.feature.torrent.domain.TorrentEngineMode
import com.saydlawy.sahab.feature.torrent.domain.TorrentResolutionResult
import com.saydlawy.sahab.feature.torrent.domain.TorrentSettings
import com.saydlawy.sahab.feature.torrent.domain.TorrentStreamCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class DebridEngine(
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val videoExts = setOf("mkv", "mp4", "m4v", "avi", "mov", "m2ts", "ts", "webm")

    suspend fun resolve(
        candidate: TorrentStreamCandidate,
        settings: TorrentSettings,
    ): TorrentResolutionResult = withContext(Dispatchers.IO) {
        val apiKey = settings.debridApiKey.trim()
        if (apiKey.isBlank()) {
            return@withContext TorrentResolutionResult.Error("يرجى إدخال مفتاح Debrid في الإعدادات أولاً.")
        }
        val hash = candidate.infoHash
            ?: return@withContext TorrentResolutionResult.Error("المصدر لا يحتوي على InfoHash صالح.")

        when (settings.engineMode) {
            TorrentEngineMode.TORBOX -> resolveTorBox(hash, candidate.fileIdx, apiKey)
            else -> resolveRealDebrid(hash, candidate.fileIdx, apiKey)
        }
    }

    private suspend fun resolveRealDebrid(
        hash: String,
        preferredIdx: Int?,
        apiKey: String,
    ): TorrentResolutionResult {
        val magnet = "magnet:?xt=urn:btih:$hash"
        val addBody = postForm(
            url = "https://api.real-debrid.com/rest/1.0/torrents/addMagnet",
            params = mapOf("magnet" to magnet),
            apiKey = apiKey,
        ) ?: return TorrentResolutionResult.Error("فشل الاتصال بـ Real-Debrid. تحقق من المفتاح والإنترنت.")

        val torrentId = parseObj(addBody)?.get("id")?.jsonPrimitive?.contentOrNull
            ?: return TorrentResolutionResult.Error("تعذّر إضافة التورنت إلى Real-Debrid.")

        val infoBefore = getJson("https://api.real-debrid.com/rest/1.0/torrents/info/$torrentId", apiKey)
        val fileId = pickRdFileId(infoBefore, preferredIdx) ?: "all"
        postForm(
            url = "https://api.real-debrid.com/rest/1.0/torrents/selectFiles/$torrentId",
            params = mapOf("files" to fileId),
            apiKey = apiKey,
        )

        var hosterLink: String? = null
        repeat(3) { attempt ->
            val info = getJson("https://api.real-debrid.com/rest/1.0/torrents/info/$torrentId", apiKey)
            val links = info?.get("links")?.jsonArray
            val first = links?.firstOrNull()?.jsonPrimitive?.contentOrNull
            if (!first.isNullOrBlank()) {
                hosterLink = first
                return@repeat
            }
            if (attempt < 2) delay(1200)
        }

        val link = hosterLink
            ?: return TorrentResolutionResult.Error("الملف غير مخزّن فورياً على Real-Debrid. جرّب مصدراً آخر.")

        val unrestrictBody = postForm(
            url = "https://api.real-debrid.com/rest/1.0/unrestrict/link",
            params = mapOf("link" to link),
            apiKey = apiKey,
        ) ?: return TorrentResolutionResult.Error("تعذّر استخراج رابط البث المباشر من Real-Debrid.")

        val downloadUrl = parseObj(unrestrictBody)?.get("download")?.jsonPrimitive?.contentOrNull
            ?: return TorrentResolutionResult.Error("لم يتم العثور على رابط تشغيل صالح من Real-Debrid.")

        return TorrentResolutionResult.Ready(streamUrl = downloadUrl)
    }

    private suspend fun resolveTorBox(
        hash: String,
        preferredIdx: Int?,
        apiKey: String,
    ): TorrentResolutionResult {
        val magnet = "magnet:?xt=urn:btih:$hash"
        val createBody = postForm(
            url = "https://api.torbox.app/v1/api/torrents/createtorrent",
            params = mapOf("magnet" to magnet, "seed" to "1", "allow_zip" to "false"),
            apiKey = apiKey,
        ) ?: return TorrentResolutionResult.Error("فشل الاتصال بـ TorBox. تحقق من المفتاح والإنترنت.")

        val torrentId = parseObj(createBody)?.get("data")?.jsonObject?.get("torrent_id")?.jsonPrimitive?.intOrNull
            ?: return TorrentResolutionResult.Error("تعذّر تجهيز التورنت على TorBox.")

        var fileId: Int? = null
        repeat(3) { attempt ->
            val listObj = getJson("https://api.torbox.app/v1/api/torrents/mylist?id=$torrentId&bypass_cache=true", apiKey)
            val dataEl = listObj?.get("data")
            val itemObj = when (dataEl) {
                is JsonObject -> dataEl
                is JsonArray -> dataEl.firstOrNull()?.jsonObject
                else -> null
            }
            val files = itemObj?.get("files")?.jsonArray
            if (!files.isNullOrEmpty()) {
                fileId = pickTorBoxFileId(files, preferredIdx)
                return@repeat
            }
            if (attempt < 2) delay(1200)
        }

        val resolvedFileId = fileId
            ?: return TorrentResolutionResult.Error("الملف غير جاهز حالياً على TorBox. جرّب مصدراً آخر.")

        val dlObj = getJson(
            "https://api.torbox.app/v1/api/torrents/requestdl?token=$apiKey&torrent_id=$torrentId&file_id=$resolvedFileId&zip_link=false",
            apiKey,
        )
        val directUrl = dlObj?.get("data")?.jsonPrimitive?.contentOrNull
            ?: return TorrentResolutionResult.Error("تعذّر جلب رابط البث المباشر من TorBox.")

        return TorrentResolutionResult.Ready(streamUrl = directUrl)
    }

    private fun pickRdFileId(info: JsonObject?, preferredIdx: Int?): String? {
        val files = info?.get("files")?.jsonArray ?: return null
        if (preferredIdx != null && preferredIdx >= 0) {
            files.getOrNull(preferredIdx)?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                ?.let { return it.toString() }
        }
        return files.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val path = obj["path"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val bytes = obj["bytes"]?.jsonPrimitive?.longOrNull ?: 0L
            val ext = path.substringAfterLast('.', "").lowercase()
            Triple(id, ext in videoExts, bytes)
        }.filter { it.second }.maxByOrNull { it.third }?.first?.toString()
    }

    private fun pickTorBoxFileId(files: JsonArray, preferredIdx: Int?): Int? {
        if (preferredIdx != null && preferredIdx >= 0) {
            files.getOrNull(preferredIdx)?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                ?.let { return it }
        }
        return files.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val name = obj["short_name"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val size = obj["size"]?.jsonPrimitive?.longOrNull ?: 0L
            val ext = name.substringAfterLast('.', "").lowercase()
            Triple(id, ext in videoExts, size)
        }.filter { it.second }.maxByOrNull { it.third }?.first
            ?: files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
    }

    private fun parseObj(body: String): JsonObject? = runCatching {
        json.parseToJsonElement(body).jsonObject
    }.getOrNull()

    private fun getJson(url: String, apiKey: String): JsonObject? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()?.let(::parseObj)
        }
    }.getOrNull()

    private fun postForm(url: String, params: Map<String, String>, apiKey: String): String? = runCatching {
        val form = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
        }.build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(form)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) null else resp.body?.string()
        }
    }.getOrNull()
}
