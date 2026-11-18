package com.saydlawy.sahab.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class M3uParser(private val client: OkHttpClient) {

    suspend fun fetch(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
        val response = suspendCancellableCoroutine<Response> { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (cont.isActive) cont.resume(response) else response.close()
                }
            })
        }
        response.use { resp ->
            if (!resp.isSuccessful) {
                throw ApiException("فشل تحميل قائمة M3U: ${resp.code}")
            }

            resp.body?.charStream()?.buffered()?.useLines { lines ->
                parseLines(lines)
            } ?: emptyList()
        }
    }

    internal fun parseLines(lines: Sequence<String>): List<Channel> {
        val channels = mutableListOf<Channel>()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentEpgId = ""
        var hasExtInf = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                currentName = if (',' in line) {
                    line.substringAfterLast(',').trim().ifBlank { "قناة غير معروفة" }
                } else {
                    "قناة غير معروفة"
                }
                currentLogo = LOGO_REGEX.find(line)?.groupValues?.get(1) ?: ""
                currentGroup = GROUP_REGEX.find(line)?.groupValues?.get(1) ?: ""
                currentEpgId = EPG_REGEX.find(line)?.groupValues?.get(1) ?: ""
                hasExtInf = true
            } else if (!line.startsWith("#")) {
                if (line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true)) {
                    val id = UUID.nameUUIDFromBytes(line.toByteArray()).toString()
                    channels.add(
                        Channel(
                            id = id,
                            name = if (hasExtInf && currentName.isNotBlank()) currentName else "قناة غير معروفة",
                            icon = currentLogo,
                            categoryId = currentGroup,
                            epgId = currentEpgId,
                            url = line,
                        )
                    )
                    if (channels.size >= MAX_CHANNELS) break
                }
                currentName = ""; currentLogo = ""; currentGroup = ""; currentEpgId = ""; hasExtInf = false
            }
        }
        return channels.distinctBy { it.id }
    }

    private companion object {
        const val MAX_CHANNELS = 25_000
        val LOGO_REGEX = Regex("tvg-logo=\"([^\"]*)\"")
        val GROUP_REGEX = Regex("group-title=\"([^\"]*)\"")
        val EPG_REGEX = Regex("tvg-id=\"([^\"]*)\"")
    }
}