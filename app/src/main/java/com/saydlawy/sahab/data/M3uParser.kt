package com.saydlawy.sahab.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.UUID

class M3uParser(private val client: OkHttpClient) {

    suspend fun fetch(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw ApiException("فشل تحميل قائمة M3U: ${response.code}")
        }
        
        val body = response.body?.string() ?: ""
        val channels = mutableListOf<Channel>()
        val lines = body.lines()
        
        var currentName = ""
        var currentLogo = ""
        var currentGroup = ""
        var currentEpgId = ""
        
        for (line in lines) {
            if (line.startsWith("#EXTINF:")) {
                val nameMatch = Regex(",(.*)$").find(line)
                currentName = nameMatch?.groupValues?.get(1)?.trim() ?: "قناة غير معروفة"
                
                val logoMatch = Regex("tvg-logo=\"([^\"]*)\"").find(line)
                currentLogo = logoMatch?.groupValues?.get(1) ?: ""
                
                val groupMatch = Regex("group-title=\"([^\"]*)\"").find(line)
                currentGroup = groupMatch?.groupValues?.get(1) ?: ""
                
                val epgMatch = Regex("tvg-id=\"([^\"]*)\"").find(line)
                currentEpgId = epgMatch?.groupValues?.get(1) ?: ""
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
        channels
    }
}
