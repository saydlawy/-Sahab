package com.saydlawy.sahab

import android.app.Application
import com.google.firebase.FirebaseApp
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache as CoilDiskCache
import coil.memory.MemoryCache
import com.saydlawy.sahab.data.DiskCache
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Prefs
import com.saydlawy.sahab.data.catalog.CatalogDatabase
import com.saydlawy.sahab.data.catalog.CatalogSync
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.channel.ChannelDatabase
import com.saydlawy.sahab.data.channel.ChannelSync
import com.saydlawy.sahab.data.channel.EpgSync
import com.saydlawy.sahab.data.TmdbApi
import com.saydlawy.sahab.data.XtreamApi
import com.saydlawy.sahab.data.sports.SyncScheduler
import com.saydlawy.sahab.ui.player.SahabPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.saydlawy.sahab.data.auth.CloudPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    val prefs = Prefs(context)
    val diskCache = DiskCache(context)
    val gemini by lazy { com.saydlawy.sahab.data.ai.GeminiStoryService() }
    val aiKeys by lazy { com.saydlawy.sahab.data.ai.ApiKeyManager(context.applicationContext) }
    val catalogDb by lazy { CatalogDatabase.getInstance(context.applicationContext) }
    val catalogSync by lazy { CatalogSync(catalogDb.movieDao(), firebaseCatalog) }
    val channelDb by lazy { ChannelDatabase.getInstance(context.applicationContext) }
    val channelSync by lazy { ChannelSync(channelDb.channelDao()) }
    val epgSync by lazy { EpgSync(channelDb.channelDao()) }
    val firebaseCatalog by lazy { com.saydlawy.sahab.data.catalog.FirebaseCatalogService() }
    val authManager by lazy { com.saydlawy.sahab.data.auth.AuthManager() }
    val userSyncService by lazy {
        com.saydlawy.sahab.data.auth.UserSyncService { prefs.getCloudSyncPin() }
    }
    val weather by lazy { com.saydlawy.sahab.data.weather.WeatherService(client, context.applicationContext) }

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
    val repo = Repository(XtreamApi(client), tmdb, diskCache, prefs, channelDao = channelDb.channelDao(), okHttpClient = client)

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

    val mergeCoordinator = com.saydlawy.sahab.data.auth.CloudMergeCoordinator()
    val syncCoordinator by lazy { com.saydlawy.sahab.data.SyncCoordinator(this) }

    val favoritesMergeDone: Boolean
        get() = mergeCoordinator.favoritesMergeDone

    val recentsMergeDone: Boolean
        get() = mergeCoordinator.recentsMergeDone

    val prefsMergeDone: Boolean
        get() = mergeCoordinator.prefsMergeDone

    private val recentsFlushTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var lastUploadedRecents: List<RecentItem>? = null

    private suspend fun uploadRecentsIfNeeded(recents: List<RecentItem>) {
        if (!recentsMergeDone || authManager.currentUser == null) return
        if (recents == lastUploadedRecents) return
        android.util.Log.d("SahabSync", "saveRecents: count=${recents.size}")
        userSyncService.saveRecents(recents)
        lastUploadedRecents = recents
    }

    /** يُستدعى عند خروج المشغل أو الإيقاف المؤقت أو الانتقال للخلفية لرفع أحدث موضع فوراً. */
    fun flushRecents() {
        recentsFlushTrigger.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class)
    fun startAutoSync() {
        appScope.launch {
            // مزامنة المفضلة بعد توقف المستخدم عن التفاعل لمدة ثانيتين (فقط بعد نجاح الدمج الأولي)
            prefs.favorites.drop(1).debounce(2000L).collect { favs ->
                if (favoritesMergeDone && authManager.currentUser != null) userSyncService.saveFavorites(favs)
            }
        }
        appScope.launch {
            // خنق رفع سجل المشاهدة أثناء التشغيل المستمر إلى مرة واحدة كل 3 دقائق (180 ثانية)
            prefs.recents.drop(1).sample(180_000L).collect { recents ->
                uploadRecentsIfNeeded(recents)
            }
        }
        appScope.launch {
            // رفع فوري عند خروج المشغل أو الإيقاف المؤقت أو ON_STOP
            recentsFlushTrigger.collect {
                uploadRecentsIfNeeded(prefs.recents.first())
            }
        }
        appScope.launch {
            // مزامنة تفضيلات الواجهة بعد ثانيتين من التغيير (فقط بعد نجاح الدمج الأولي)
            combine(
                prefs.lightEffects,
                prefs.homeChannelsLayout,
                prefs.homeChannelsSource,
                prefs.homeChannelsCategoryId,
            ) { light, layout, source, catId ->
                CloudPreferences(light, layout.name, source.name, catId)
            }.drop(1).debounce(2000L).collect { cloudPrefs ->
                if (prefsMergeDone && authManager.currentUser != null) userSyncService.savePreferences(cloudPrefs)
            }
        }
    }

    private val _switchingAccount = MutableStateFlow(false)
    val switchingAccount: StateFlow<Boolean> = _switchingAccount

    /**
     * توحيد تنظيف البيانات المحلية (الكتالوج، القنوات، البرامج، الكاش، ومواقيت المزامنة).
     * عند [clearPrefsAccount]=true يمسح أيضاً بيانات الحساب المخزنة في Prefs ويعيد ضبط بوابات الدمج.
     */
    suspend fun clearAllLocalData(clearPrefsAccount: Boolean = false) = withContext(Dispatchers.IO) {
        repo.clearCache(clearDisk = true)
        prefs.clearSyncTimestamps()
        runCatching { channelDb.channelDao().clearAll() }
            .onFailure { android.util.Log.e("SahabClear", "Failed to clear channels: ${it.message}") }
        runCatching { channelDb.channelDao().clearAllPrograms() }
            .onFailure { android.util.Log.e("SahabClear", "Failed to clear EPG programs: ${it.message}") }
        runCatching { catalogDb.movieDao().wipe() }
            .onFailure { android.util.Log.e("SahabClear", "Failed to wipe catalog movies: ${it.message}") }
        if (clearPrefsAccount) {
            mergeCoordinator.reset()
            repo.setAccount(null)
            prefs.clearAccount()
        }
    }

    fun switchAccount(acc: Account) {
        _switchingAccount.value = true
        repo.setAccount(acc)
        appScope.launch(Dispatchers.IO) {
            try {
                // 1. تثبيت الحساب النشط وتنظيف كل بيانات الحساب السابق قبل المزامنة
                prefs.setActiveAccount(acc.id)
                clearAllLocalData(clearPrefsAccount = false)

                // 2. تشغيل سلسلة المزامنة الكاملة للحساب الجديد (كتالوج + أفلام + قنوات + EPG)
                syncCoordinator.runFullSync(
                    context = appContext,
                    forceCatalog = true,
                    reason = "switchAccount",
                )
            } catch (e: Exception) {
                android.util.Log.e("SahabSwitch", "Failed to switch account: ${e.message}")
            } finally {
                _switchingAccount.value = false
            }
        }
    }

    fun saveProgress(item: PlayItem, positionMs: Long, durationMs: Long) {
        val live = item.kind == Kind.LIVE
        val accId = repo.account?.id ?: prefs.currentActiveAccountId()
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
            accountId = accId,
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
        val STRICT_HTTPS_DOMAINS = listOf("themoviedb.org", "tmdb.org", "github.com", "githubusercontent.com", "open-meteo.com", "ipwho.is")
    }
}

class SahabApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppCheckInstaller.install()
        container = AppContainer(this)
        container.startAutoSync()
        container.appScope.launch {
            // L-05: warm EncryptedSharedPreferences / MasterKey off the main thread.
            container.aiKeys
            SyncScheduler.scheduleInitialRunIfNeeded(this@SahabApp)
            SahabPlayer.warmUpMediaCache(this@SahabApp)
            container.weather.refreshIfNeeded()
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
        container.client.newBuilder()
            .cache(null)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
