package com.saydlawy.sahab.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class M3uParser(private val client: OkHttpClient) {

    suspend fun fetch(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val channels = mutableListOf<Channel>()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException("فشل تحميل قائمة M3U: ${response.code}")
            }

            var currentName = ""
            var currentLogo = ""
            var currentGroup = ""
            var currentEpgId = ""

            response.body?.charStream()?.buffered()?.useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("#EXTINF:")) {
                        currentName = if (',' in line) {
                            line.substringAfterLast(',').trim().ifBlank { "قناة غير معروفة" }
                        } else {
                            "قناة غير معروفة"
                        }
                        currentLogo = LOGO_REGEX.find(line)?.groupValues?.get(1) ?: ""
                        currentGroup = GROUP_REGEX.find(line)?.groupValues?.get(1) ?: ""
                        currentEpgId = EPG_REGEX.find(line)?.groupValues?.get(1) ?: ""
                    } else if (line.isNotBlank() && !line.startsWith("#")) {
                        val streamUrl = line.trim()
                        val id = UUID.nameUUIDFromBytes(streamUrl.toByteArray()).toString()
                        channels.add(
                            Channel(
                                id = id,
                                name = currentName,
                                icon = currentLogo,
                                categoryId = currentGroup,
                                epgId = currentEpgId,
                                url = streamUrl
                            )
                        )
                        currentName = ""; currentLogo = ""; currentGroup = ""; currentEpgId = ""
                    }
                }
            }
        }
        channels.distinctBy { it.id }
    }

    private companion object {
        val LOGO_REGEX = Regex("tvg-logo=\"([^\"]*)\"")
        val GROUP_REGEX = Regex("group-title=\"([^\"]*)\"")
        val EPG_REGEX = Regex("tvg-id=\"([^\"]*)\"")
    }
}