package com.saydlawy.sahab.data

import com.saydlawy.sahab.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class GithubReleaseUpdate(
    val versionCode: Int,
    val title: String,
    val apkUrl: String,
    val htmlUrl: String,
)

/**
 * O-05: Lightweight GitHub Releases updater that compares the latest published build number
 * against [BuildConfig.VERSION_CODE] without requiring Firebase App Distribution in release builds.
 */
object GithubReleaseUpdater {

    private const val LATEST_TAG_URL = "https://api.github.com/repos/saydlawy/-Sahab/releases/tags/latest"
    private val BUILD_NUMBER_REGEX = Regex("""build\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(client: OkHttpClient): GithubReleaseUpdate? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url(LATEST_TAG_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string().orEmpty()
                if (body.isBlank()) return@runCatching null
                val root = json.parseToJsonElement(body).jsonObject
                val name = root["name"]?.jsonPrimitive?.content.orEmpty()
                val htmlUrl = root["html_url"]?.jsonPrimitive?.content.orEmpty()
                val remoteCode = BUILD_NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: return@runCatching null
                if (remoteCode <= BuildConfig.VERSION_CODE) return@runCatching null
                val assets = root["assets"]?.jsonArray.orEmpty()
                val apkUrl = assets.firstNotNullOfOrNull { el ->
                    val obj = el.jsonObject
                    val assetName = obj["name"]?.jsonPrimitive?.content.orEmpty()
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        obj["browser_download_url"]?.jsonPrimitive?.content
                    } else null
                }.orEmpty().ifBlank { htmlUrl }
                if (apkUrl.isBlank()) return@runCatching null
                GithubReleaseUpdate(
                    versionCode = remoteCode,
                    title = name.ifBlank { "إصدار جديد ($remoteCode)" },
                    apkUrl = apkUrl,
                    htmlUrl = htmlUrl,
                )
            }
        }.getOrNull()
    }
}
