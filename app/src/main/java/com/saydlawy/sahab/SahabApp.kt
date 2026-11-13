package com.saydlawy.sahab

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache as CoilDiskCache
import coil.memory.MemoryCache
import com.saydlawy.sahab.data.DiskCache
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Prefs
import com.saydlawy.sahab.data.catalog.CatalogDatabase
import com.saydlawy.sahab.data.catalog.CatalogSync
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.TmdbApi
import com.saydlawy.sahab.data.XtreamApi
import com.saydlawy.sahab.data.sports.SyncScheduler
import com.saydlawy.sahab.ui.player.SahabPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    val prefs = Prefs(context)
    val diskCache = DiskCache(context)
    val gemini by lazy { com.saydlawy.sahab.data.ai.GeminiStoryService() }
    val aiKeys by lazy { com.saydlawy.sahab.data.ai.ApiKeyManager(context.applicationContext) }
    val catalogDb by lazy { CatalogDatabase.getInstance(context.applicationContext) }
    val catalogSync by lazy { CatalogSync(catalogDb.movieDao()) }

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.applicationContext.cacheDir, "http_cache"), 50L * 1024L * 1024L))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .addInterceptor(CleartextGuardInterceptor())
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .addInterceptor(RetryInterceptor())
        .build()

    val tmdb = TmdbApi(client, BuildConfig.TMDB_API_KEY, diskCache)
    val repo = Repository(XtreamApi(client), tmdb, diskCache, prefs)

    /** Outlives screens, so progress can still be saved while the player is closing. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What the player screen should play; set right before navigating to it. */
    @Volatile
    var playback: PlayRequest? = null

    /** Short stable hash of (server + user). Used to isolate the video cache per account
     *  without exposing credentials. Returns "anon" before login. */
    val accountHash: String
        get() {
            val a = repo.account ?: return "anon"
            val bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest("${a.server}|${a.user}".toByteArray(Charsets.UTF_8))
            return bytes.take(4).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        }

    fun saveProgress(item: PlayItem, positionMs: Long, durationMs: Long) {
        val live = item.kind == Kind.LIVE
        val recent = RecentItem(
            kind = item.kind,
            id = item.id,
            title = item.title,
            subtitle = item.subtitle,
            icon = item.icon,
            ext = item.ext,
            groupId = item.groupId,
            positionMs = if (live) 0 else positionMs.coerceAtLeast(0),
            durationMs = if (live) 0 else durationMs.coerceAtLeast(0),
            updatedAt = System.currentTimeMillis(),
        )
        appScope.launch { prefs.recordPlayback(recent) }
    }

    private class CleartextGuardInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            if (req.url.scheme.equals("http", ignoreCase = true)) {
                val host = req.url.host.lowercase(Locale.US)
                if (STRICT_HTTPS_DOMAINS.any { host == it || host.endsWith(".$it") }) {
                    throw IOException("HTTPS required for $host")
                }
            }
            return chain.proceed(req)
        }
    }

    private class RetryInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val req = chain.request()
            val path = req.url.encodedPath
            // Never retry long-lived media streams (live/movie/series) in OkHttp; ExoPlayer handles its own recovery.
            if (path.contains("/live/") || path.contains("/movie/") || path.contains("/series/")) {
                return chain.proceed(req)
            }
            var attempt = 0
            while (true) {
                var response: Response? = null
                try {
                    response = chain.proceed(req)
                    if (response.code !in 500..599 || attempt >= RETRY_BACKOFFS_MS.size) {
                        return response
                    }
                    response.close()
                } catch (ioe: IOException) {
                    response?.close()
                    if (attempt >= RETRY_BACKOFFS_MS.size) throw ioe
                }
                val delayMs = RETRY_BACKOFFS_MS[attempt]
                attempt++
                try {
                    Thread.sleep(delayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Retry interrupted")
                }
            }
        }
    }

    private companion object {
        const val USER_AGENT = "Sahab/1.0 (Android)"
        val RETRY_BACKOFFS_MS = longArrayOf(500L, 1500L)
        val STRICT_HTTPS_DOMAINS = listOf("themoviedb.org", "tmdb.org", "goal-api.com", "github.com", "githubusercontent.com")
    }
}

class SahabApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            SyncScheduler.scheduleInitialRunIfNeeded(this@SahabApp)
            SahabPlayer.warmUpMediaCache(this@SahabApp)
        }
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(coilClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                CoilDiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(500L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()

    private val coilClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
