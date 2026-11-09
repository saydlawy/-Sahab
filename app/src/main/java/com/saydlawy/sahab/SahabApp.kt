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
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.TmdbApi
import com.saydlawy.sahab.data.XtreamApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    val prefs = Prefs(context)
    val diskCache = DiskCache(context)

    val client: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.applicationContext.cacheDir, "http_cache"), 50L * 1024L * 1024L))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    val tmdb = TmdbApi(client, BuildConfig.TMDB_API_KEY, diskCache)
    val repo = Repository(XtreamApi(client), tmdb, diskCache)

    /** Outlives screens, so progress can still be saved while the player is closing. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What the player screen should play; set right before navigating to it. */
    @Volatile
    var playback: PlayRequest? = null

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

    private companion object {
        const val USER_AGENT = "Sahab/1.0 (Android)"
    }
}

class SahabApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(container.client)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                CoilDiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()
}
