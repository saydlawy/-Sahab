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
private data class WyzieSubtitleItem(
    val id: String = "",
    val url: String = "",
    val display: String = "",
    val language: String = "",
    val format: String = "",
    val isHearingImpaired: Boolean = false,
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
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): List<OnlineSubtitleOption> = withContext(Dispatchers.IO) {
        val urlBuilder = "https://sub.wyzie.ru/search".toHttpUrl().newBuilder()
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
        }.getOrNull() ?: return@withContext emptyList()

        val items = runCatching {
            json.decodeFromString<List<WyzieSubtitleItem>>(body)
        }.getOrDefault(emptyList())

        items.filter { it.url.startsWith("https://") }
            .take(10)
            .mapIndexed { idx, item ->
                val fmt = item.format.ifBlank { "SRT" }.uppercase()
                val name = item.display.ifBlank { "ترجمة عربية #${idx + 1}" }
                OnlineSubtitleOption(
                    id = item.id.ifBlank { "sub_$idx" },
                    label = "$name ($fmt)",
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
