package com.saydlawy.sahab.data

import android.util.Log
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.data.catalog.TmdbMatcher
import com.saydlawy.sahab.data.catalog.encodeTmdbCastJson
import com.saydlawy.sahab.data.catalog.parseCastMembersJson
import com.saydlawy.sahab.data.channel.ChannelDao
import java.net.URLEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import okio.ByteString.Companion.decodeBase64
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first

class Repository(
    private val api: XtreamApi,
    private val tmdb: TmdbApi,
    private val disk: DiskCache? = null,
    private val prefs: Prefs? = null,
    private val channelDao: ChannelDao? = null,
    okHttpClient: okhttp3.OkHttpClient? = null,
) {

    private val m3uParser = M3uParser(okHttpClient ?: okhttp3.OkHttpClient())

    @Volatile
    var account: Account? = null
        private set

    @Volatile
    var userInfo: UserInfo? = null
        private set

    private val _syncing = kotlinx.coroutines.flow.MutableStateFlow(false)
    val syncing: kotlinx.coroutines.flow.StateFlow<Boolean> = _syncing

    private data class CacheEntry(
        val typeTag: String,
        val value: Any,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<CacheEntry>>()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> getTypedCache(key: String, serializer: KSerializer<T>): T? {
        val entry = cache[key] ?: return null
        return if (entry.typeTag == serializer.descriptor.serialName) entry.value as? T else null
    }

    private fun <T : Any> putTypedCache(key: String, serializer: KSerializer<T>, value: T) {
        cache[key] = CacheEntry(serializer.descriptor.serialName, value)
    }
    private val epgCacheLock = Any()
    private val epgCache = object : LinkedHashMap<String, Pair<Long, List<Program>>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<Program>>>?): Boolean =
            size > MAX_EPG_MEMORY_ENTRIES
    }
    private val epgGate = Semaphore(4)

    private fun getEpgMem(key: String): Pair<Long, List<Program>>? = synchronized(epgCacheLock) {
        epgCache[key]
    }

    private fun putEpgMem(key: String, value: Pair<Long, List<Program>>) {
        synchronized(epgCacheLock) {
            epgCache[key] = value
        }
    }

    fun setAccount(value: Account?) {
        val changed = account != value
        account = value
        if (changed) {
            userInfo = null
            clearCache(clearDisk = false)
        }
    }

    fun clearCache(clearDisk: Boolean = false) {
        cache.clear()
        inFlight.clear()
        synchronized(epgCacheLock) {
            epgCache.clear()
        }
        if (clearDisk) {
            disk?.clearCatalog()
        }
    }

    fun invalidateLiveChannelCache() {
        cache.keys.removeIf { it.startsWith("live:") }
        inFlight.keys.removeIf { it.contains("::live:") }
    }

    private fun requireAccount(): Account = account ?: throw ApiException(Ar.errNoAccount)

    private fun accountTag(): String {
        val a = account ?: return "anon"
        if (a.type == "M3U") {
            val urlHash = java.security.MessageDigest.getInstance("SHA-256")
                .digest(a.m3uUrl.toByteArray(Charsets.UTF_8))
                .take(6)
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            return "m3u|${a.id}|$urlHash"
        }
        return "${a.id}|${a.server}|${a.user}"
    }

    private fun diskKey(key: String): String = "${accountTag()}::$key"

    suspend fun login(candidate: Account): UserInfo? {
        if (candidate.type == "M3U") {
            if (candidate.m3uUrl.isBlank()) throw ApiException(Ar.errBadUrl)
            val channels = m3uParser.fetch(candidate.m3uUrl)
            if (channels.isEmpty()) {
                throw ApiException("قائمة M3U فارغة أو لا تحتوي على قنوات صالحة")
            }
            account = candidate
            userInfo = null
            clearCache(clearDisk = false)
            putTypedCache("live_m3u", ListSerializer(Channel.serializer()), channels)
            return null
        }
        val info = parseUserInfo(api.request(candidate, null))
        account = candidate
        userInfo = info
        clearCache(clearDisk = false)
        disk?.put(diskKey("user_info"), UserInfo.serializer(), info)
        return info
    }

    suspend fun refreshUserInfo(): UserInfo {
        val a = requireAccount()
        disk?.get(diskKey("user_info"), UserInfo.serializer(), USER_INFO_TTL_MS)?.let { cachedInfo ->
            if (userInfo == null) userInfo = cachedInfo
        }
        val info = parseUserInfo(api.request(a, null))
        userInfo = info
        disk?.put(diskKey("user_info"), UserInfo.serializer(), info)
        return info
    }

    /** Pre-warms categories and full catalogs concurrently in background so any screen opens fast. */
    suspend fun prewarmCatalog() = supervisorScope {
        if (account == null) return@supervisorScope
        if (userInfo == null) {
            disk?.get(diskKey("user_info"), UserInfo.serializer(), USER_INFO_TTL_MS)?.let {
                userInfo = it
            }
        }
        val gate = Semaphore(MAX_CONCURRENT_CATALOG_TASKS)
        awaitAll(
            async(Dispatchers.IO) { gate.withPermit { runCatching { liveCategories() } } },
            async(Dispatchers.IO) { gate.withPermit { runCatching { movieCategories() } } },
            async(Dispatchers.IO) { gate.withPermit { runCatching { seriesCategories() } } },
            async(Dispatchers.IO) { gate.withPermit { runCatching { liveChannels(null) } } },
            async(Dispatchers.IO) { gate.withPermit { runCatching { movies(null) } } },
            async(Dispatchers.IO) { gate.withPermit { runCatching { seriesList(null) } } },
        )
    }

    sealed class SyncResult {
        object Skipped : SyncResult()
        object AlreadyRunning : SyncResult()
        object Success : SyncResult()
        data class Failure(val reason: String) : SyncResult()
    }

    private suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }

    /**
     * Syncs catalog if [force]=true OR 24h passed since last sync.
     * Bypasses cached data during fetch via forceRefresh (no cache wipe = no data loss on failure).
     */
    suspend fun syncCatalog(force: Boolean = false): SyncResult {
        if (!_syncing.compareAndSet(expect = false, update = true)) return SyncResult.AlreadyRunning
        prefs?.migrateSyncKeysIfNeeded()
        val lastSync = if (prefs == null) 0L else prefs.lastCatalogSyncTime.first()
        val now = System.currentTimeMillis()
        if (!force && lastSync > 0L && (now - lastSync) < SYNC_TTL_MS) {
            _syncing.value = false
            return SyncResult.Skipped
        }
        return try {
            requireAccount()
            suspendRunCatching { refreshUserInfo() }
            val gate = Semaphore(MAX_CONCURRENT_CATALOG_TASKS)
            val results: List<Result<*>> = supervisorScope {
                awaitAll(
                    async(Dispatchers.IO) { gate.withPermit { suspendRunCatching { liveCategories(forceNetwork = true) } } },
                    async(Dispatchers.IO) { gate.withPermit { suspendRunCatching { movieCategories(forceNetwork = true) } } },
                    async(Dispatchers.IO) { gate.withPermit { suspendRunCatching { seriesCategories(forceNetwork = true) } } },
                    async(Dispatchers.IO) { gate.withPermit { suspendRunCatching { liveChannels(null, forceNetwork = true) } } },
                    async(Dispatchers.IO) { gate.withPermit { suspendRunCatching { movies(null, forceNetwork = true) } } },
                    async(Dispatchers.IO) { gate.withPermit { suspendRunCatching { seriesList(null, forceNetwork = true) } } },
                )
            }
            val firstFailure = results.firstNotNullOfOrNull { it.exceptionOrNull() }
            if (firstFailure != null) {
                runCatching { Log.w("SahabSync", "syncCatalog aborted without stamping time: ${firstFailure.message}") }
                SyncResult.Failure(firstFailure.message ?: "sync failed")
            } else {
                prefs?.setLastCatalogSyncTime(System.currentTimeMillis())
                SyncResult.Success
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncResult.Failure(e.message ?: "unknown")
        } finally {
            _syncing.value = false
        }
    }

    /** Legacy alias — always forces. Returns SyncResult for caller feedback. */
    suspend fun refreshSubscription(): SyncResult = syncCatalog(force = true)

    fun liveExtensions(): List<String> {
        val allowed = userInfo?.formats.orEmpty()
        val preferred = listOf("m3u8", "ts").filter { it in allowed }
        return preferred.ifEmpty { listOf("m3u8", "ts") }
    }

    fun streamUrl(item: PlayItem, formatAttempt: Int = 0): String {
        if (item.url.isNotBlank() && item.url.startsWith("http")) {
            return item.url
        }
        if (item.url.isBlank() && item.kind == Kind.LIVE) {
            val channelListSerializer = ListSerializer(Channel.serializer())
            val cachedUrl = getTypedCache("live:", channelListSerializer)?.firstOrNull { it.id == item.id }?.url
                ?: getTypedCache("live_m3u", channelListSerializer)?.firstOrNull { it.id == item.id }?.url
            if (!cachedUrl.isNullOrBlank() && cachedUrl.startsWith("http")) {
                return cachedUrl
            }
        }
        val a = requireAccount()
        val user = encodeSegment(a.user)
        val pass = encodeSegment(a.pass)
        return when (item.kind) {
            Kind.LIVE -> {
                val exts = liveExtensions()
                val ext = exts[formatAttempt.coerceIn(0, exts.lastIndex)]
                "${a.server}/live/$user/$pass/${item.id}.$ext"
            }
            Kind.MOVIE -> "${a.server}/movie/$user/$pass/${item.id}.${item.ext.ifBlank { DEFAULT_VOD_EXT }}"
            else -> "${a.server}/series/$user/$pass/${item.id}.${item.ext.ifBlank { DEFAULT_VOD_EXT }}"
        }
    }

    private fun encodeSegment(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private suspend fun <T : Any> cachedPersistent(
        key: String,
        serializer: KSerializer<T>,
        ttlMs: Long,
        forceNetwork: Boolean = false,
        load: suspend () -> T,
    ): T {
        val typeTag = serializer.descriptor.serialName
        val dKey = diskKey(key)
        if (!forceNetwork) {
            getTypedCache(key, serializer)?.let { return it }
            val existing = inFlight[dKey]
            if (existing != null) {
                return try {
                    val awaited = existing.await()
                    if (awaited.typeTag == typeTag) {
                        @Suppress("UNCHECKED_CAST")
                        awaited.value as T
                    } else {
                        load().also { loaded ->
                            putTypedCache(key, serializer, loaded)
                            disk?.put(dKey, serializer, loaded)
                        }
                    }
                } catch (e: CancellationException) {
                    currentCoroutineContext().ensureActive()
                    cachedPersistent(key, serializer, ttlMs, forceNetwork = false, load = load)
                }
            }
        }
        val mine = CompletableDeferred<CacheEntry>()
        val existing = if (forceNetwork) null else inFlight.putIfAbsent(dKey, mine)
        if (existing != null) {
            return try {
                val awaited = existing.await()
                if (awaited.typeTag == typeTag) {
                    @Suppress("UNCHECKED_CAST")
                    awaited.value as T
                } else {
                    load().also { loaded ->
                        putTypedCache(key, serializer, loaded)
                        disk?.put(dKey, serializer, loaded)
                    }
                }
            } catch (e: CancellationException) {
                currentCoroutineContext().ensureActive()
                cachedPersistent(key, serializer, ttlMs, forceNetwork = false, load = load)
            }
        }
        return try {
            if (!forceNetwork) {
                getTypedCache(key, serializer)?.let { cached ->
                    mine.complete(CacheEntry(typeTag, cached))
                    return cached
                }
                val fromDisk = disk?.get(dKey, serializer, ttlMs)
                if (fromDisk != null) {
                    putTypedCache(key, serializer, fromDisk)
                    mine.complete(CacheEntry(typeTag, fromDisk))
                    return fromDisk
                }
            }
            val loaded = load()
            putTypedCache(key, serializer, loaded)
            disk?.put(dKey, serializer, loaded)
            mine.complete(CacheEntry(typeTag, loaded))
            loaded
        } catch (e: CancellationException) {
            inFlight.remove(dKey, mine)
            mine.cancel(e)
            throw e
        } catch (t: Throwable) {
            mine.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(dKey, mine)
        }
    }

    suspend fun <T : Any> peekCachedList(
        key: String,
        serializer: KSerializer<List<T>>,
        ttlMs: Long = CATALOG_LIST_TTL_MS,
    ): List<T>? {
        getTypedCache(key, serializer)?.let { return it }
        val fromDisk = disk?.get(diskKey(key), serializer, ttlMs)
        if (fromDisk != null) {
            putTypedCache(key, serializer, fromDisk)
            return fromDisk
        }
        return null
    }

    private suspend fun request(action: String, params: Map<String, String> = emptyMap()): JsonElement =
        api.request(requireAccount(), action, params)

    private suspend fun <T> streamArray(
        action: String,
        params: Map<String, String> = emptyMap(),
        parseStream: (InputStream) -> List<T>,
    ): List<T> = api.streamArray(
        account = requireAccount(),
        action = action,
        params = params,
        onEmptyOrNonArray = { emptyList() },
        parseStream = parseStream,
    )

    private fun categoryParams(categoryId: String?): Map<String, String> =
        if (categoryId.isNullOrBlank()) emptyMap() else mapOf("category_id" to categoryId)

    // Live

    suspend fun liveCategories(forceNetwork: Boolean = false): List<Category> {
        val acc = requireAccount()
        if (acc.type == "M3U") {
            val channels = liveChannels(null, forceNetwork = forceNetwork)
            return channels.map { it.categoryId }.filter { it.isNotBlank() }.distinct().map { Category(it, it) }
        }
        return cachedPersistent("live_cats", ListSerializer(Category.serializer()), CATEGORIES_TTL_MS, forceNetwork = forceNetwork) {
            parseCategories(request("get_live_categories"))
        }
    }

    private suspend fun enrichWithLogos(channels: List<Channel>): List<Channel> {
        val dao = channelDao ?: return channels
        val rows = runCatching { dao.getLogosList() }.getOrNull() ?: return channels
        if (rows.isEmpty()) return channels
        val logos = rows.associate { it.streamId to it.logoUrl }
        return channels.map { ch ->
            val logo = logos[ch.id]
            if (!logo.isNullOrBlank() && logo != ch.icon) ch.copy(icon = logo) else ch
        }
    }

    private suspend fun roomChannels(): List<Channel>? {
        val rows = runCatching { channelDao?.visibleOrdered() }.getOrNull().orEmpty()
        if (rows.isEmpty()) return null
        return rows.map {
            Channel(
                id = it.streamId,
                name = it.displayName.ifBlank { it.name },
                icon = it.logoUrl,
                categoryId = it.categoryId,
                epgId = it.epgId,
                url = it.streamUrlTemplate.takeIf { u -> u.startsWith("http") }.orEmpty(),
            )
        }
    }

    suspend fun peekAllLiveChannels(): List<Channel>? =
        roomChannels()
            ?: peekCachedList("live:", ListSerializer(Channel.serializer()), CATALOG_LIST_TTL_MS)?.let { enrichWithLogos(it) }

    suspend fun liveChannels(categoryId: String?, forceNetwork: Boolean = false): List<Channel> {
        val acc = requireAccount()
        if (acc.type == "M3U") {
            val key = "live_m3u"
            val all = cachedPersistent(key, ListSerializer(Channel.serializer()), CATALOG_LIST_TTL_MS, forceNetwork = forceNetwork) {
                m3uParser.fetch(acc.m3uUrl)
            }
            return if (categoryId.isNullOrBlank()) all else all.filter { it.categoryId == categoryId }
        }

        val key = "live:${categoryId.orEmpty()}"
        val channelListSerializer = ListSerializer(Channel.serializer())
        if (!forceNetwork) {
            getTypedCache(key, channelListSerializer)?.let { return it }

            roomChannels()?.let { all ->
                val result = if (categoryId.isNullOrBlank()) all
                else withContext(Dispatchers.Default) { all.filter { it.categoryId == categoryId } }
                if (result.isNotEmpty() || categoryId.isNullOrBlank()) {
                    putTypedCache(key, channelListSerializer, result)
                    return result
                }
            }

            if (!categoryId.isNullOrBlank()) {
                val all = peekAllLiveChannels()
                if (!all.isNullOrEmpty() && all.any { it.categoryId.isNotBlank() }) {
                    val filtered = withContext(Dispatchers.Default) {
                        all.filter { it.categoryId == categoryId }
                    }
                    putTypedCache(key, channelListSerializer, filtered)
                    return filtered
                }
            }
        }

        return enrichWithLogos(cachedPersistent(key, ListSerializer(Channel.serializer()), CATALOG_LIST_TTL_MS, forceNetwork = forceNetwork) {
            streamArray("get_live_streams", categoryParams(categoryId)) { stream ->
                parseChannelsStream(stream)
            }
        })
    }

    // Movies

    suspend fun movieCategories(forceNetwork: Boolean = false): List<Category> {
        if (requireAccount().type == "M3U") return emptyList()
        return cachedPersistent("vod_cats", ListSerializer(Category.serializer()), CATEGORIES_TTL_MS, forceNetwork = forceNetwork) {
            parseCategories(request("get_vod_categories"))
        }
    }

    suspend fun peekAllMovies(): List<Poster>? =
        peekCachedList("vod:", ListSerializer(Poster.serializer()), CATALOG_LIST_TTL_MS)

    suspend fun movies(categoryId: String?, forceNetwork: Boolean = false): List<Poster> {
        if (requireAccount().type == "M3U") return emptyList()
        val key = "vod:${categoryId.orEmpty()}"
        val posterListSerializer = ListSerializer(Poster.serializer())
        if (!forceNetwork) {
            getTypedCache(key, posterListSerializer)?.let { return it }

            if (!categoryId.isNullOrBlank()) {
                val all = peekAllMovies()
                if (!all.isNullOrEmpty() && all.any { it.categoryId.isNotBlank() }) {
                    val filtered = withContext(Dispatchers.Default) {
                        all.filter { it.categoryId == categoryId }
                    }
                    putTypedCache(key, posterListSerializer, filtered)
                    return filtered
                }
            }
        }

        return cachedPersistent(key, ListSerializer(Poster.serializer()), CATALOG_LIST_TTL_MS, forceNetwork = forceNetwork) {
            streamArray("get_vod_streams", categoryParams(categoryId)) { stream ->
                parseMoviesStream(stream)
            }
        }
    }

    suspend fun movieDetails(id: String): MovieDetails =
        cachedPersistent("vod_info:$id", MovieDetails.serializer(), DETAILS_TTL_MS) {
            val json = request("get_vod_info", mapOf("vod_id" to id))
            val base = parseMovieDetails(id, json)
            val tmdbData = try {
                if (tmdb.hasApiKey()) {
                    val res = TmdbMatcher.enrich(
                        tmdb = tmdb,
                        rawName = base.name,
                        categoryName = base.genre,
                        novaYear = base.year.toIntOrNull(),
                        maxQueries = 3,
                        mediaType = "movie",
                    )
                    if (res.level == TmdbMatcher.Level.STRONG) res.movie else null
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
            if (tmdbData == null) base else base.copy(
                image = base.image.ifBlank { tmdbData.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty() },
                backdrop = base.backdrop.ifBlank { tmdbData.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }.orEmpty() },
                plot = base.plot.ifBlank { tmdbData.overview },
                year = base.year.ifBlank { tmdbData.releaseDate.take(4) },
                rating = base.rating.ifBlank { if (tmdbData.voteAverage > 0) String.format(Locale.US, "%.1f", tmdbData.voteAverage) else "" },
                duration = base.duration.ifBlank { tmdbData.runtime?.let { "${it}m" }.orEmpty() },
                genre = base.genre.ifBlank { tmdbData.genres.joinToString(", ") { it.name } },
                director = base.director.ifBlank { tmdbData.credits?.crew?.firstOrNull { it.job == "Director" }?.name.orEmpty() },
                cast = base.cast.ifBlank { tmdbData.credits?.cast?.take(5)?.joinToString(", ") { it.name }.orEmpty() },
                castMembers = base.castMembers.ifEmpty { parseCastMembersJson(encodeTmdbCastJson(tmdbData.credits?.cast)) },
                posterUrls = base.posterUrls.ifEmpty { tmdbData.posterUrls },
                trailerUrl = base.trailerUrl.ifBlank { tmdbData.trailerUrl },
            )
        }

    // Series

    suspend fun seriesCategories(forceNetwork: Boolean = false): List<Category> {
        if (requireAccount().type == "M3U") return emptyList()
        return cachedPersistent("series_cats", ListSerializer(Category.serializer()), CATEGORIES_TTL_MS, forceNetwork = forceNetwork) {
            parseCategories(request("get_series_categories"))
        }
    }

    suspend fun peekAllSeries(): List<Poster>? =
        peekCachedList("series:", ListSerializer(Poster.serializer()), CATALOG_LIST_TTL_MS)

    suspend fun seriesList(categoryId: String?, forceNetwork: Boolean = false): List<Poster> {
        if (requireAccount().type == "M3U") return emptyList()
        val key = "series:${categoryId.orEmpty()}"
        val posterListSerializer = ListSerializer(Poster.serializer())
        if (!forceNetwork) {
            getTypedCache(key, posterListSerializer)?.let { return it }

            if (!categoryId.isNullOrBlank()) {
                val all = peekAllSeries()
                if (!all.isNullOrEmpty() && all.any { it.categoryId.isNotBlank() }) {
                    val filtered = withContext(Dispatchers.Default) {
                        all.filter { it.categoryId == categoryId }
                    }
                    putTypedCache(key, posterListSerializer, filtered)
                    return filtered
                }
            }
        }

        return cachedPersistent(key, ListSerializer(Poster.serializer()), CATALOG_LIST_TTL_MS, forceNetwork = forceNetwork) {
            streamArray("get_series", categoryParams(categoryId)) { stream ->
                parseSeriesStream(stream)
            }
        }
    }

    suspend fun seriesDetails(id: String): SeriesDetails =
        cachedPersistent("series_info:$id", SeriesDetails.serializer(), SERIES_DETAILS_TTL_MS) {
            val json = request("get_series_info", mapOf("series_id" to id))
            withContext(Dispatchers.Default) { parseSeriesDetails(id, json) }
        }

    // Program guide

    fun peekShortEpg(streamId: String, limit: Int = 3): List<Program>? {
        val key = "short:$streamId:$limit"
        val entry = getEpgMem(key) ?: return null
        return if (System.currentTimeMillis() - entry.first < EPG_TTL_MS) entry.second else null
    }

    suspend fun shortEpg(streamId: String, limit: Int = 3): List<Program> {
        val key = "short:$streamId:$limit"
        getEpgMem(key)?.let { (time, list) ->
            if (System.currentTimeMillis() - time < EPG_TTL_MS) return list
        }
        val dao = channelDao
        if (dao != null) {
            val ch = runCatching { dao.findById(streamId) }.getOrNull()
            if (ch != null && ch.epgId.isNotBlank()) {
                val nowSec = System.currentTimeMillis() / 1000
                val local = runCatching { dao.programsForChannel(ch.epgId, nowSec) }.getOrNull().orEmpty()
                if (local.isNotEmpty()) {
                    val mapped = local.take(limit).map { Program(it.title, it.description, it.start, it.end) }
                    putEpgMem(key, System.currentTimeMillis() to mapped)
                    return mapped
                }
            }
        }
        val dKey = diskKey("epg_$key")
        disk?.get(dKey, ListSerializer(Program.serializer()), EPG_DISK_TTL_MS)?.let { fromDisk ->
            putEpgMem(key, System.currentTimeMillis() to fromDisk)
            return fromDisk
        }
        return epgGate.withPermit {
            getEpgMem(key)?.let { (time, list) ->
                if (System.currentTimeMillis() - time < EPG_TTL_MS) return@withPermit list
            }
            val json = request("get_short_epg", mapOf("stream_id" to streamId, "limit" to limit.toString()))
            val list = parsePrograms(json)
            putEpgMem(key, System.currentTimeMillis() to list)
            disk?.put(dKey, ListSerializer(Program.serializer()), list)
            list
        }
    }

    suspend fun dayEpg(streamId: String): List<Program> {
        val key = "day:$streamId"
        getEpgMem(key)?.let { (time, list) ->
            if (System.currentTimeMillis() - time < EPG_TTL_MS) return list
        }
        val dao = channelDao
        if (dao != null) {
            val ch = runCatching { dao.findById(streamId) }.getOrNull()
            if (ch != null && ch.epgId.isNotBlank()) {
                val nowSec = System.currentTimeMillis() / 1000 - 2 * 3600
                val local = runCatching { dao.programsForChannel(ch.epgId, nowSec) }.getOrNull().orEmpty()
                if (local.isNotEmpty()) {
                    val mapped = local.take(80).map { Program(it.title, it.description, it.start, it.end) }
                    putEpgMem(key, System.currentTimeMillis() to mapped)
                    return mapped
                }
            }
        }
        val dKey = diskKey("epg_$key")
        disk?.get(dKey, ListSerializer(Program.serializer()), DAY_EPG_DISK_TTL_MS)?.let { fromDisk ->
            val nowSec = System.currentTimeMillis() / 1000
            val valid = fromDisk.filter { it.end > nowSec - 2 * 3600 }
            if (valid.isNotEmpty()) {
                putEpgMem(key, System.currentTimeMillis() to valid)
                return valid
            }
        }
        return epgGate.withPermit {
            getEpgMem(key)?.let { (time, list) ->
                if (System.currentTimeMillis() - time < EPG_TTL_MS) return@withPermit list
            }
            val json = request("get_simple_data_table", mapOf("stream_id" to streamId))
            val nowSec = System.currentTimeMillis() / 1000
            val list = parsePrograms(json).filter { it.end > nowSec - 2 * 3600 }.take(80)
            putEpgMem(key, System.currentTimeMillis() to list)
            disk?.put(dKey, ListSerializer(Program.serializer()), list)
            list
        }
    }

    // Parsing

    private fun parseUserInfo(json: JsonElement): UserInfo {
        val root = json.obj() ?: throw ApiException(Ar.errBadResponse)
        val u = root["user_info"].obj() ?: throw ApiException(Ar.errBadLogin)
        val auth = u.s("auth")
        if (auth != "1" && !auth.equals("true", ignoreCase = true)) throw ApiException(Ar.errBadLogin)
        val status = u.s("status")
        if (status.equals("Disabled", true) || status.equals("Banned", true)) throw ApiException(Ar.errDisabled)
        if (status.equals("Expired", true)) throw ApiException(Ar.errExpired)
        return UserInfo(
            status = status,
            expiry = u.s("exp_date").toLongOrNull(),
            maxConnections = u.s("max_connections"),
            activeConnections = u.s("active_cons"),
            isTrial = u.s("is_trial") == "1",
            formats = root["user_info"].obj()?.get("allowed_output_formats").arr()
                ?.map { it.str().lowercase(Locale.US) }.orEmpty(),
        )
    }

    private fun parseCategories(json: JsonElement): List<Category> =
        json.arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.s("category_id")
            if (id.isBlank()) null else Category(id, o.s("category_name").ifBlank { Ar.noName })
        }.distinctBy { it.id }

    @Serializable
    private data class XtreamChannelDto(
        val stream_id: JsonElement? = null,
        val name: JsonElement? = null,
        val stream_icon: JsonElement? = null,
        val category_id: JsonElement? = null,
        val epg_channel_id: JsonElement? = null,
        val added: JsonElement? = null,
    )

    @Serializable
    private data class XtreamMovieDto(
        val stream_id: JsonElement? = null,
        val name: JsonElement? = null,
        val stream_icon: JsonElement? = null,
        val rating: JsonElement? = null,
        val container_extension: JsonElement? = null,
        val category_id: JsonElement? = null,
        val added: JsonElement? = null,
    )

    @Serializable
    private data class XtreamSeriesDto(
        val series_id: JsonElement? = null,
        val name: JsonElement? = null,
        val cover: JsonElement? = null,
        val rating: JsonElement? = null,
        val releaseDate: JsonElement? = null,
        val release_date: JsonElement? = null,
        val category_id: JsonElement? = null,
        val last_modified: JsonElement? = null,
    )

    private fun parseChannelsStream(stream: InputStream): List<Channel> {
        val result = ArrayList<Channel>(256)
        val seen = HashSet<String>(256)
        for (o in api.decodeArraySequence<XtreamChannelDto>(stream)) {
            val id = o.stream_id.str().trim()
            if (id.isNotBlank() && seen.add(id)) {
                result.add(
                    Channel(
                        id = id,
                        name = o.name.str().trim().ifBlank { Ar.noName },
                        icon = o.stream_icon.str().trim(),
                        categoryId = o.category_id.str().trim(),
                        epgId = o.epg_channel_id.str().trim(),
                        added = o.added.str().trim().toLongOrNull() ?: 0L,
                    ),
                )
            }
        }
        return if (result.any { it.added > 0L }) result.sortedByDescending { it.added } else result
    }

    private fun parseMoviesStream(stream: InputStream): List<Poster> {
        val result = ArrayList<Poster>(256)
        val seen = HashSet<String>(256)
        for (o in api.decodeArraySequence<XtreamMovieDto>(stream)) {
            val id = o.stream_id.str().trim()
            if (id.isNotBlank() && seen.add(id)) {
                val name = o.name.str().trim()
                result.add(
                    Poster(
                        id = id,
                        name = name.ifBlank { Ar.noName },
                        image = o.stream_icon.str().trim(),
                        rating = formatRating(o.rating.str().trim()),
                        ext = o.container_extension.str().trim(),
                        year = yearOf(name),
                        categoryId = o.category_id.str().trim(),
                        added = o.added.str().trim().toLongOrNull() ?: 0L,
                    ),
                )
            }
        }
        return if (result.any { it.added > 0L }) result.sortedByDescending { it.added } else result
    }

    private fun parseSeriesStream(stream: InputStream): List<Poster> {
        val result = ArrayList<Poster>(256)
        val seen = HashSet<String>(256)
        for (o in api.decodeArraySequence<XtreamSeriesDto>(stream)) {
            val id = o.series_id.str().trim()
            if (id.isNotBlank() && seen.add(id)) {
                val name = o.name.str().trim()
                val release = o.releaseDate.str().trim().ifBlank { o.release_date.str().trim() }.ifBlank { name }
                result.add(
                    Poster(
                        id = id,
                        name = name.ifBlank { Ar.noName },
                        image = o.cover.str().trim(),
                        rating = formatRating(o.rating.str().trim()),
                        ext = "",
                        year = yearOf(release),
                        categoryId = o.category_id.str().trim(),
                        added = o.last_modified.str().trim().toLongOrNull() ?: 0L,
                    ),
                )
            }
        }
        return if (result.any { it.added > 0L }) result.sortedByDescending { it.added } else result
    }

    private fun parseMovieDetails(id: String, json: JsonElement): MovieDetails {
        val root = json.obj()
        val info = root?.get("info").obj() ?: JsonObject(emptyMap())
        val data = root?.get("movie_data").obj() ?: JsonObject(emptyMap())
        return MovieDetails(
            id = id,
            name = data.s("name").ifBlank { info.s("name") }.ifBlank { Ar.noName },
            image = info.s("movie_image").ifBlank { info.s("cover_big") },
            backdrop = info["backdrop_path"].firstString(),
            plot = info.s("plot").ifBlank { info.s("description") },
            cast = info.s("cast").ifBlank { info.s("actors") },
            director = info.s("director"),
            genre = info.s("genre"),
            year = yearOf(info.s("releasedate").ifBlank { info.s("release_date") }),
            duration = info.s("duration"),
            rating = formatRating(info.s("rating")),
            ext = data.s("container_extension"),
        )
    }

    private fun parseSeriesDetails(id: String, json: JsonElement): SeriesDetails {
        val root = json.obj()
        val info = root?.get("info").obj() ?: JsonObject(emptyMap())
        val episodes = mutableListOf<Episode>()
        when (val raw = root?.get("episodes")) {
            is JsonObject -> raw.forEach { (seasonKey, list) ->
                list.arr()?.forEach { addEpisode(episodes, it, seasonKey.toIntOrNull() ?: 1) }
            }
            is JsonArray -> raw.forEach { entry ->
                // Some panels return a flat list, or a list of seasons.
                val nested = entry.arr()
                if (nested != null) nested.forEach { addEpisode(episodes, it, 1) }
                else addEpisode(episodes, entry, 1)
            }
            else -> Unit
        }
        return SeriesDetails(
            id = id,
            name = info.s("name").ifBlank { Ar.noName },
            cover = info.s("cover"),
            backdrop = info["backdrop_path"].firstString(),
            plot = info.s("plot"),
            cast = info.s("cast"),
            genre = info.s("genre"),
            year = yearOf(info.s("releaseDate").ifBlank { info.s("release_date") }),
            rating = formatRating(info.s("rating")),
            episodes = episodes.distinctBy { it.id }
                .sortedWith(compareBy<Episode> { it.season }.thenBy { it.number }),
        )
    }

    private fun addEpisode(target: MutableList<Episode>, element: JsonElement, fallbackSeason: Int) {
        val o = element.obj() ?: return
        val id = o.s("id")
        if (id.isBlank()) return
        val info = o["info"].obj() ?: JsonObject(emptyMap())
        val number = o.i("episode_num")
        val season = o.i("season").takeIf { it > 0 } ?: fallbackSeason
        target += Episode(
            id = id,
            season = season,
            number = number,
            title = o.s("title").ifBlank { Ar.episodeNumber(number) },
            ext = o.s("container_extension"),
            image = info.s("movie_image"),
            plot = info.s("plot"),
            duration = info.s("duration"),
        )
    }

    private fun parsePrograms(json: JsonElement): List<Program> =
        json.obj()?.get("epg_listings").arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val start = o.s("start_timestamp").toLongOrNull() ?: return@mapNotNull null
            val end = o.s("stop_timestamp").toLongOrNull()
                ?: o.s("end_timestamp").toLongOrNull()
                ?: return@mapNotNull null
            Program(decodeText(o.s("title")), decodeText(o.s("description")), start, end)
        }.sortedBy { it.start }

    private fun decodeText(raw: String): String {
        if (raw.isBlank()) return ""
        return try {
            val decoded = raw.decodeBase64()?.utf8()?.trim().orEmpty()
            if (decoded.isBlank() || decoded.contains('�')) raw else decoded
        } catch (e: Exception) {
            raw
        }
    }

    private fun formatRating(raw: String): String {
        val value = raw.toDoubleOrNull() ?: return ""
        return if (value <= 0.0 || value >= 9.9) "" else String.format(Locale.US, "%.1f", value)
    }

    private fun yearOf(raw: String): String =
        YEAR_REGEX.find(raw)?.value.orEmpty()

    private companion object {
        const val MAX_CONCURRENT_CATALOG_TASKS = 2
        const val MAX_EPG_MEMORY_ENTRIES = 256
        val YEAR_REGEX = Regex("(19|20)\\d{2}")
        const val SYNC_TTL_MS = 24L * 3600_000L
        const val EPG_TTL_MS = 5 * 60_000L
        const val EPG_DISK_TTL_MS = 15 * 60_000L
        const val DAY_EPG_DISK_TTL_MS = 2 * 3600_000L
        const val USER_INFO_TTL_MS = 24 * 3600_000L
        const val CATEGORIES_TTL_MS = 12 * 3600_000L
        const val CATALOG_LIST_TTL_MS = 6 * 3600_000L
        const val DETAILS_TTL_MS = 7L * 24 * 3600_000L
        const val SERIES_DETAILS_TTL_MS = 24 * 3600_000L
    }
}
