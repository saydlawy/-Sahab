package com.saydlawy.sahab

import android.app.Application
import com.google.firebase.FirebaseApp
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache as CoilDiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
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
import com.saydlawy.sahab.ui.player.SahabPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.saydlawy.sahab.data.auth.CloudPreferences
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val gemini by lazy { com.saydlawy.sahab.data.ai.GeminiStoryService(client) }
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

    fun newImageHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectionPool(client.connectionPool)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .addInterceptor(CleartextGuardInterceptor())
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    val tmdb = TmdbApi(client, BuildConfig.TMDB_API_KEY, diskCache)
    val repo = Repository(XtreamApi(client), tmdb, diskCache, prefs, channelDao = channelDb.channelDao(), okHttpClient = client)

    /** Outlives screens, so progress can still be saved while the player is closing. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What the player screen should play; set right before navigating to it. */
    @Volatile
    var playback: PlayRequest? = null

    private val pendingPlaybackLock = Any()
    private val pendingPlaybackFile by lazy { File(appContext.filesDir, "pending_playback.json") }
    private val playbackJson by lazy {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }

    /** M-18: Persist a bounded window around [request.startIndex] so Process Death on PlayerScreen recovers cleanly. */
    fun savePendingPlayback(request: PlayRequest) {
        val safeIdx = request.startIndex.coerceIn(0, (request.items.size - 1).coerceAtLeast(0))
        val windowStart = (safeIdx - 40).coerceAtLeast(0)
        val windowEnd = (safeIdx + 41).coerceAtMost(request.items.size)
        val bounded = if (request.items.size > 81) {
            request.copy(
                items = request.items.subList(windowStart, windowEnd),
                startIndex = safeIdx - windowStart,
            )
        } else {
            request
        }
        val payload = playbackJson.encodeToString(PlayRequest.serializer(), bounded)
        appScope.launch {
            runCatching {
                synchronized(pendingPlaybackLock) {
                    val tmpFile = File(pendingPlaybackFile.parentFile, "pending_playback.json.tmp")
                    tmpFile.writeText(payload)
                    if (!tmpFile.renameTo(pendingPlaybackFile)) {
                        pendingPlaybackFile.writeText(payload)
                        tmpFile.delete()
                    }
                }
            }
        }
    }

    fun restorePendingPlayback(): PlayRequest? = runCatching {
        synchronized(pendingPlaybackLock) {
            if (!pendingPlaybackFile.exists()) return null
            playbackJson.decodeFromString(PlayRequest.serializer(), pendingPlaybackFile.readText())
                .also { playback = it }
        }
    }.getOrNull()

    fun clearPendingPlayback() {
        playback = null
        appScope.launch {
            runCatching {
                synchronized(pendingPlaybackLock) {
                    pendingPlaybackFile.delete()
                    File(pendingPlaybackFile.parentFile, "pending_playback.json.tmp").delete()
                }
            }
        }
    }

    /** Short stable hash of (id + server + user + type). Used to isolate the video cache per account
     *  without exposing credentials. Returns "anon" before login. */
    val accountHash: String
        get() {
            val a = repo.account ?: return "anon"
            val identity = if (a.type == "M3U") "${a.id}|m3u|${a.m3uUrl}" else "${a.id}|${a.server}|${a.user}"
            val bytes = java.security.MessageDigest.getInstance("SHA-256")
                .digest(identity.toByteArray(Charsets.UTF_8))
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

    @Volatile
    private var lastUploadedFavorites: List<com.saydlawy.sahab.data.FavItem>? = null

    @Volatile
    private var lastUploadedPreferences: CloudPreferences? = null

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

    private val autoSyncLock = Any()
    private var autoSyncJob: Job? = null

    fun startAutoSync() = ensureAutoSyncStarted()

    @OptIn(FlowPreview::class)
    fun ensureAutoSyncStarted() {
        synchronized(autoSyncLock) {
            if (authManager.currentUser == null || autoSyncJob?.isActive == true) return
            val job = SupervisorJob(appScope.coroutineContext[Job])
            autoSyncJob = job
            val syncScope = CoroutineScope(appScope.coroutineContext + job)
            syncScope.launch {
                // مزامنة المفضلة بعد توقف المستخدم عن التفاعل لمدة ثانيتين (فقط بعد نجاح الدمج الأولي)
                prefs.favorites.distinctUntilChanged().drop(1).debounce(2000L).collect { favs ->
                    if (favoritesMergeDone && authManager.currentUser != null && favs != lastUploadedFavorites) {
                        userSyncService.saveFavorites(favs)
                        lastUploadedFavorites = favs
                    }
                }
            }
            syncScope.launch {
                // خنق رفع سجل المشاهدة أثناء التشغيل المستمر إلى مرة واحدة كل 3 دقائق (180 ثانية)
                prefs.recents.distinctUntilChanged().drop(1).sample(180_000L).collect { recents ->
                    uploadRecentsIfNeeded(recents)
                }
            }
            syncScope.launch {
                // خنق طلبات الرفع عند الخروج المتكرر إلى مرة كل 15 ثانية (SAH-M04)
                recentsFlushTrigger.debounce(15_000L).collect {
                    uploadRecentsIfNeeded(prefs.recents.first())
                }
            }
            syncScope.launch {
                // مزامنة تفضيلات الواجهة بعد ثانيتين من التغيير (فقط بعد نجاح الدمج الأولي)
                combine(
                    combine(
                        prefs.lightEffects,
                        prefs.homeChannelsLayout,
                        prefs.homeChannelsSource,
                    ) { light, layout, source -> Triple(light, layout, source) },
                    combine(
                        prefs.homeChannelsCategoryId,
                        prefs.moviesLayoutStyle,
                        prefs.seriesLayoutStyle,
                    ) { categoryId, moviesStyle, seriesStyle -> Triple(categoryId, moviesStyle, seriesStyle) },
                ) { (light, layout, source), (categoryId, moviesStyle, seriesStyle) ->
                    CloudPreferences(
                        lightEffects = light,
                        homeChannelsLayout = layout.name,
                        homeChannelsSource = source.name,
                        homeChannelsCategoryId = categoryId,
                        moviesLayoutStyle = moviesStyle.name,
                        seriesLayoutStyle = seriesStyle.name,
                    )
                }.distinctUntilChanged().drop(1).debounce(2000L).collect { cloudPrefs ->
                    if (prefsMergeDone && authManager.currentUser != null && cloudPrefs != lastUploadedPreferences) {
                        userSyncService.savePreferences(cloudPrefs)
                        lastUploadedPreferences = cloudPrefs
                    }
                }
            }
        }
    }

    fun stopAutoSync() {
        synchronized(autoSyncLock) {
            autoSyncJob?.cancel()
            autoSyncJob = null
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
                // 0. إيقاف أي مزامنة جارية للحساب السابق قبل تنظيف قواعد البيانات
                syncCoordinator.stopActiveSync()

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
            url = item.url,
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
        val STRICT_HTTPS_DOMAINS = listOf(
            "themoviedb.org",
            "tmdb.org",
            "github.com",
            "githubusercontent.com",
            "open-meteo.com",
            "ipwho.is",
            "generativelanguage.googleapis.com",
        )
    }
}

class SahabApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppCheckInstaller.install()
        container = AppContainer(this)
        container.ensureAutoSyncStarted()
        container.appScope.launch {
            // L-05: warm EncryptedSharedPreferences / MasterKey off the main thread.
            container.aiKeys
            SahabPlayer.warmUpMediaCache(this@SahabApp)
            container.weather.refreshIfNeeded()
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { coilClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.15)
                    .strongReferencesEnabled(true)
                    .build()
            }
            .diskCache {
                CoilDiskCache.Builder()
                    .directory(File(cacheDir, "image_cache").toOkioPath())
                    .maxSizeBytes(150L * 1024L * 1024L)
                    .build()
            }
            .crossfade(false)
            .build()

    private val coilClient: OkHttpClient by lazy {
        container.newImageHttpClient()
    }
}
