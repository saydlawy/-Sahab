package com.saydlawy.sahab.data

import com.saydlawy.sahab.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class GithubReleaseUpdate(
    val versionCode: Int,
    val title: String,
    val apkUrl: String,
    val htmlUrl: String,
    val sha256: String? = null,
)

/**
 * O-05: Lightweight GitHub Releases updater that compares the latest published build number
 * against [BuildConfig.VERSION_CODE] without requiring Firebase App Distribution in release builds.
 */
object GithubReleaseUpdater {

    private const val LATEST_TAG_URL = "https://api.github.com/repos/saydlawy/-Sahab/releases/tags/latest"
    private val BUILD_NUMBER_REGEX = Regex("""build\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val SHA256_REGEX = Regex("""\bsha-?256\s*[:=]\s*([0-9a-fA-F]{64})\b""", RegexOption.IGNORE_CASE)
    private val TRUSTED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
    )
    private val json = Json { ignoreUnknownKeys = true }

    fun isTrustedReleaseUrl(rawUrl: String): Boolean {
        val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return false
        if (!parsed.isHttps) return false
        val host = parsed.host.lowercase()
        return TRUSTED_HOSTS.any { trusted -> host == trusted || host.endsWith(".$trusted") }
    }

    suspend fun checkForUpdate(
        client: OkHttpClient,
        endpointUrl: String = LATEST_TAG_URL,
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
    ): GithubReleaseUpdate? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(endpointUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            val body = awaitResponseBody(client, req) ?: return@withContext null
            if (body.isBlank()) return@withContext null
            val root = json.parseToJsonElement(body).jsonObject
            val name = root["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val releaseNotes = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val rawHtmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val htmlUrl = rawHtmlUrl.takeIf { isTrustedReleaseUrl(it) }.orEmpty()
            val remoteCode = BUILD_NUMBER_REGEX.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return@withContext null
            if (remoteCode <= currentVersionCode) return@withContext null
            val assets = root["assets"]?.jsonArray.orEmpty()
            val apkAsset = assets.firstOrNull { el ->
                val assetName = el.jsonObject["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                assetName.endsWith(".apk", ignoreCase = true)
            }?.jsonObject
            val rawApkUrl = apkAsset?.get("browser_download_url")?.jsonPrimitive?.contentOrNull.orEmpty()
            val apkUrl = rawApkUrl.takeIf { isTrustedReleaseUrl(it) }.orEmpty().ifBlank { htmlUrl }
            if (apkUrl.isBlank() || !isTrustedReleaseUrl(apkUrl)) return@withContext null

            val assetDigest = apkAsset?.get("digest")?.jsonPrimitive?.contentOrNull
                ?.removePrefix("sha256:")
                ?.trim()
                ?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' } }
            val noteDigest = SHA256_REGEX.find(releaseNotes)?.groupValues?.getOrNull(1)
            GithubReleaseUpdate(
                versionCode = remoteCode,
                title = name.ifBlank { "إصدار جديد ($remoteCode)" },
                apkUrl = apkUrl,
                htmlUrl = htmlUrl,
                sha256 = assetDigest ?: noteDigest,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun awaitResponseBody(client: OkHttpClient, req: Request): String? =
        suspendCancellableCoroutine { cont ->
            val call = client.newCall(req)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use { resp ->
                            if (!resp.isSuccessful) null else resp.body?.string()
                        }
                    }
                    if (!cont.isActive) return
                    result.fold(
                        onSuccess = { cont.resume(it) },
                        onFailure = { cont.resumeWithException(it) },
                    )
                }
            })
        }
}
