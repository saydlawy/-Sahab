package com.saydlawy.sahab.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume

class TmdbApi(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val diskCache: DiskCache? = null,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val memoryCacheLock = Any()
    private val memoryCache = object : LinkedHashMap<String, TmdbCacheEnvelope>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TmdbCacheEnvelope>?): Boolean =
            size > MAX_MEMORY_ENTRIES
    }
    private val searchMemoryCache = object : LinkedHashMap<String, TmdbSearchCacheEnvelope>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TmdbSearchCacheEnvelope>?): Boolean =
            size > MAX_MEMORY_ENTRIES
    }
    private val detailsMemoryCache = object : LinkedHashMap<String, TmdbCacheEnvelope>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TmdbCacheEnvelope>?): Boolean =
            size > MAX_MEMORY_ENTRIES
    }

    private fun getCachedMemory(key: String): TmdbCacheEnvelope? = synchronized(memoryCacheLock) {
        memoryCache[key]
    }

    private fun putCachedMemory(key: String, value: TmdbCacheEnvelope) {
        synchronized(memoryCacheLock) {
            memoryCache[key] = value
        }
    }

    private fun getSearchMemory(key: String): TmdbSearchCacheEnvelope? = synchronized(memoryCacheLock) {
        searchMemoryCache[key]
    }

    private fun putSearchMemory(key: String, value: TmdbSearchCacheEnvelope) {
        synchronized(memoryCacheLock) {
            searchMemoryCache[key] = value
        }
    }

    private fun getDetailMemory(key: String): TmdbCacheEnvelope? = synchronized(memoryCacheLock) {
        detailsMemoryCache[key]
    }

    private fun putDetailMemory(key: String, value: TmdbCacheEnvelope) {
        synchronized(memoryCacheLock) {
            detailsMemoryCache[key] = value
        }
    }

    suspend fun enrichMovie(name: String, year: String): TmdbMovie? {
        if (apiKey.isBlank() || name.isBlank()) return null
        val cleanedName = cleanQueryTitle(name)
        if (cleanedName.isBlank()) return null
        val cleanYear = year.filter { it.isDigit() }.takeIf { it.length == 4 }.orEmpty()
        val cacheKey = "tmdb_v1:${cleanedName.lowercase(Locale.ROOT)}:$cleanYear"

        // 1. Memory cache check (0ms)
        getCachedMemory(cacheKey)?.let { return it.movie }

        // 2. Persistent disk cache check (saves free TMDB API quota across app restarts)
        if (diskCache != null) {
            val cached = diskCache.get(
                key = cacheKey,
                serializer = TmdbCacheEnvelope.serializer(),
                ttlMs = DiskCache.TTL_TMDB_MS,
                permanentNamespace = true,
            )
            if (cached != null) {
                // If it was a negative cache entry (not found), check shorter TTL (7 days).
                val ageOk = cached.found || (System.currentTimeMillis() - cached.savedAt) < DiskCache.TTL_TMDB_MISS_MS
                if (ageOk) {
                    putCachedMemory(cacheKey, cached)
                    return cached.movie
                }
            }
        }

        // 3. Network call (only when never queried before)
        val searchOut = searchMovieOutcome(cleanedName, cleanYear.toIntOrNull(), "ar")
        if (searchOut is SearchOutcome.NetworkError || searchOut is SearchOutcome.MissingKey) {
            return null
        }
        val id = (searchOut as SearchOutcome.Ok).results.firstOrNull()?.id
        val details = if (id != null) getMovieDetails(id) else null
        if (id != null && details == null) {
            return null
        }
        val envelope = TmdbCacheEnvelope(
            found = details != null,
            savedAt = System.currentTimeMillis(),
            movie = details,
        )
        putCachedMemory(cacheKey, envelope)
        diskCache?.put(
            key = cacheKey,
            serializer = TmdbCacheEnvelope.serializer(),
            value = envelope,
            permanentNamespace = true,
        )
        return details
    }

    /** Strips common IPTV tags ("AR:", "[4K]", "(2024)", "1080p", etc.) for cleaner TMDB matching. */
    private fun cleanQueryTitle(raw: String): String =
        raw.replace(PREFIX_TAG_REGEX, "")
            .replace(BRACKET_TAG_REGEX, " ")
            .replace(YEAR_PAREN_REGEX, " ")
            .replace(QUALITY_TAG_REGEX, " ")
            .replace(WHITESPACE_REGEX, " ")
            .trim()

    private val trimmedKey = apiKey.trim()
    private val isBearerV4Token = trimmedKey.startsWith("eyJ")

    private suspend fun cachedDetail(
        kindTag: String,
        id: Int,
        includeExtras: Boolean,
        fetch: suspend () -> TmdbMovie?,
    ): TmdbMovie? {
        val fullKey = "tmdb_detail_v1:$kindTag:$id:full"
        val targetKey = "tmdb_detail_v1:$kindTag:$id:${if (includeExtras) "full" else "lite"}"

        getDetailMemory(fullKey)?.movie?.let { return it }
        if (!includeExtras) {
            getDetailMemory(targetKey)?.movie?.let { return it }
        }

        if (diskCache != null) {
            diskCache.get(
                key = fullKey,
                serializer = TmdbCacheEnvelope.serializer(),
                ttlMs = DiskCache.TTL_TMDB_MS,
                permanentNamespace = true,
            )?.let { cached ->
                if (cached.movie != null) {
                    putDetailMemory(fullKey, cached)
                    return cached.movie
                }
            }
            if (!includeExtras) {
                diskCache.get(
                    key = targetKey,
                    serializer = TmdbCacheEnvelope.serializer(),
                    ttlMs = DiskCache.TTL_TMDB_MS,
                    permanentNamespace = true,
                )?.let { cached ->
                    if (cached.movie != null) {
                        putDetailMemory(targetKey, cached)
                        return cached.movie
                    }
                }
            }
        }

        val fetched = fetch() ?: return null
        val envelope = TmdbCacheEnvelope(found = true, savedAt = System.currentTimeMillis(), movie = fetched)
        putDetailMemory(targetKey, envelope)
        diskCache?.put(
            key = targetKey,
            serializer = TmdbCacheEnvelope.serializer(),
            value = envelope,
            permanentNamespace = true,
        )
        return fetched
    }

    private suspend fun cachedSearch(
        kindTag: String,
        query: String,
        year: Int?,
        lang: String,
        fetch: suspend () -> SearchOutcome,
    ): SearchOutcome {
        if (apiKey.isBlank()) return SearchOutcome.MissingKey
        if (query.isBlank()) return SearchOutcome.Ok(emptyList())
        val normQuery = query.trim().lowercase(Locale.ROOT)
        val cacheKey = "tmdb_search_v1:$kindTag:$normQuery:${year ?: ""}:$lang"

        getSearchMemory(cacheKey)?.let { cached ->
            val ageOk = cached.results.isNotEmpty() || (System.currentTimeMillis() - cached.savedAt) < DiskCache.TTL_TMDB_MISS_MS
            if (ageOk) return SearchOutcome.Ok(cached.results)
        }

        if (diskCache != null) {
            val cached = diskCache.get(
                key = cacheKey,
                serializer = TmdbSearchCacheEnvelope.serializer(),
                ttlMs = DiskCache.TTL_TMDB_MS,
                permanentNamespace = true,
            )
            if (cached != null) {
                val ageOk = cached.results.isNotEmpty() || (System.currentTimeMillis() - cached.savedAt) < DiskCache.TTL_TMDB_MISS_MS
                if (ageOk) {
                    putSearchMemory(cacheKey, cached)
                    return SearchOutcome.Ok(cached.results)
                }
            }
        }

        val outcome = fetch()
        if (outcome is SearchOutcome.Ok) {
            val envelope = TmdbSearchCacheEnvelope(
                savedAt = System.currentTimeMillis(),
                results = outcome.results,
            )
            putSearchMemory(cacheKey, envelope)
            diskCache?.put(
                key = cacheKey,
                serializer = TmdbSearchCacheEnvelope.serializer(),
                value = envelope,
                permanentNamespace = true,
            )
        }
        return outcome
    }

    private suspend fun getMovieDetails(id: Int, includeExtras: Boolean = true): TmdbMovie? =
        cachedDetail("movie", id, includeExtras) {
            val append = if (includeExtras) "credits,images,videos,translations" else "translations"
            val urlBuilder = "https://api.themoviedb.org/3/movie/$id".toHttpUrl().newBuilder()
                .addQueryParameter("language", "ar")
                .addQueryParameter("append_to_response", append)
                .apply {
                    if (includeExtras) {
                        addQueryParameter("include_image_language", "ar,en,null")
                        addQueryParameter("include_video_language", "ar,en,null")
                    }
                }
            if (!isBearerV4Token) {
                urlBuilder.addQueryParameter("api_key", trimmedKey)
            }
            val text = execute(urlBuilder.build().toString()) ?: return@cachedDetail null
            withContext(Dispatchers.Default) {
                try {
                    val raw = json.decodeFromString<TmdbMovie>(text)
                    val posters = if (includeExtras) buildPosterUrls(raw.posterPath, raw.images) else buildPosterUrls(raw.posterPath, null)
                    val trailer = if (includeExtras) extractTrailerUrl(raw.videos) else ""
                    val resolvedOverview = resolveOverview(raw.overview, raw.translations)
                    raw.copy(overview = resolvedOverview, posterUrls = posters, trailerUrl = trailer)
                } catch (_: Exception) {
                    null
                }
            }
        }

    suspend fun getTvDetailsById(id: Int, includeExtras: Boolean = true): TmdbMovie? =
        cachedDetail("tv", id, includeExtras) {
            val append = if (includeExtras) "credits,images,videos,translations" else "translations"
            val urlBuilder = "https://api.themoviedb.org/3/tv/$id".toHttpUrl().newBuilder()
                .addQueryParameter("language", "ar")
                .addQueryParameter("append_to_response", append)
                .apply {
                    if (includeExtras) {
                        addQueryParameter("include_image_language", "ar,en,null")
                        addQueryParameter("include_video_language", "ar,en,null")
                    }
                }
            if (!isBearerV4Token) {
                urlBuilder.addQueryParameter("api_key", trimmedKey)
            }
            val text = execute(urlBuilder.build().toString()) ?: return@cachedDetail null
            withContext(Dispatchers.Default) {
                try {
                    val tv = json.decodeFromString<TmdbTvRaw>(text)
                    val posters = if (includeExtras) buildPosterUrls(tv.posterPath, tv.images) else buildPosterUrls(tv.posterPath, null)
                    val trailer = if (includeExtras) extractTrailerUrl(tv.videos) else ""
                    val resolvedOverview = resolveOverview(tv.overview, tv.translations)
                    TmdbMovie(
                        id = tv.id,
                        title = tv.name,
                        overview = resolvedOverview,
                        releaseDate = tv.firstAirDate,
                        voteAverage = tv.voteAverage,
                        voteCount = tv.voteCount,
                        popularity = tv.popularity,
                        adult = false,
                        runtime = tv.episodeRunTime.firstOrNull(),
                        genres = tv.genres,
                        credits = tv.credits,
                        posterPath = tv.posterPath,
                        backdropPath = tv.backdropPath,
                        posterUrls = posters,
                        trailerUrl = trailer,
                    )
                } catch (_: Exception) {
                    null
                }
            }
        }

    private fun resolveOverview(primary: String, translations: TmdbTranslationsResponse?): String {
        if (primary.isNotBlank()) return primary
        val items = translations?.translations.orEmpty()
        return items.firstOrNull {
            it.iso6391.equals("ar", ignoreCase = true) && !it.data?.overview.isNullOrBlank()
        }?.data?.overview
            ?: items.firstOrNull {
                it.iso6391.equals("en", ignoreCase = true) && !it.data?.overview.isNullOrBlank()
            }?.data?.overview
            ?: ""
    }

    private fun buildPosterUrls(primaryPath: String?, images: TmdbImagesResponse?): List<String> {
        val paths = buildList {
            primaryPath?.takeIf { it.isNotBlank() }?.let { add(it) }
            images?.posters.orEmpty()
                .mapNotNull { it.filePath?.takeIf { p -> p.isNotBlank() } }
                .forEach { add(it) }
        }.distinct().take(10)
        return paths.map { "https://image.tmdb.org/t/p/w500$it" }
    }

    private fun extractTrailerUrl(videos: TmdbVideosResponse?): String {
        val ytVideos = videos?.results.orEmpty().filter {
            it.site.equals("YouTube", ignoreCase = true) && it.key.isNotBlank()
        }
        val bestVideo = ytVideos.firstOrNull {
            it.type.equals("Trailer", ignoreCase = true) && it.official
        } ?: ytVideos.firstOrNull {
            it.type.equals("Trailer", ignoreCase = true)
        } ?: ytVideos.firstOrNull {
            it.type.equals("Teaser", ignoreCase = true) && it.official
        } ?: ytVideos.firstOrNull()
        return bestVideo?.let { "https://www.youtube.com/watch?v=${it.key}" } ?: ""
    }

    private suspend fun execute(url: String): String? = suspendCancellableCoroutine { cont ->
        val reqBuilder = Request.Builder().url(url)
        if (isBearerV4Token) {
            reqBuilder.addHeader("Authorization", "Bearer $trimmedKey")
        }
        val call = client.newCall(reqBuilder.build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (it.isSuccessful) {
                        if (cont.isActive) cont.resume(it.body?.string())
                    } else {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
        })
    }

    /**
     * Raw search — returns full result objects (not just IDs).
     * Used by TmdbMatcher to score candidates.
     */
    sealed class SearchOutcome {
        data class Ok(val results: List<TmdbSearchResult>) : SearchOutcome()
        object NetworkError : SearchOutcome()
        object MissingKey : SearchOutcome()
    }

    fun hasApiKey(): Boolean = apiKey.isNotBlank()

    suspend fun searchMovieOutcome(
        query: String,
        year: Int?,
        lang: String = "en-US",
    ): SearchOutcome = cachedSearch("movie", query, year, lang) {
        val urlBuilder = "https://api.themoviedb.org/3/search/movie".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("language", lang)
            .addQueryParameter("include_adult", "false")
        if (!isBearerV4Token) urlBuilder.addQueryParameter("api_key", trimmedKey)
        if (year != null) urlBuilder.addQueryParameter("primary_release_year", year.toString())
        val text = execute(urlBuilder.build().toString()) ?: return@cachedSearch SearchOutcome.NetworkError
        val list = withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<RawSearchResponse>(text).results
            } catch (_: Exception) {
                null
            }
        } ?: return@cachedSearch SearchOutcome.NetworkError
        SearchOutcome.Ok(list)
    }

    suspend fun searchMovieRaw(
        query: String,
        year: Int?,
        lang: String = "en-US",
    ): List<TmdbSearchResult> = when (val out = searchMovieOutcome(query, year, lang)) {
        is SearchOutcome.Ok -> out.results
        else -> emptyList()
    }

    suspend fun searchTvOutcome(
        query: String,
        year: Int?,
        lang: String = "en-US",
    ): SearchOutcome = cachedSearch("tv", query, year, lang) {
        val urlBuilder = "https://api.themoviedb.org/3/search/tv".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("language", lang)
            .addQueryParameter("include_adult", "false")
        if (!isBearerV4Token) urlBuilder.addQueryParameter("api_key", trimmedKey)
        if (year != null) urlBuilder.addQueryParameter("first_air_date_year", year.toString())
        val text = execute(urlBuilder.build().toString()) ?: return@cachedSearch SearchOutcome.NetworkError
        val list = withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<TvSearchResponse>(text).results.map { item ->
                    TmdbSearchResult(
                        id = item.id,
                        title = item.name,
                        originalTitle = item.originalName,
                        releaseDate = item.firstAirDate,
                        voteCount = item.voteCount,
                        popularity = item.popularity,
                        originalLanguage = item.originalLanguage,
                        adult = false,
                        posterPath = item.posterPath,
                        voteAverage = item.voteAverage,
                    )
                }
            } catch (_: Exception) {
                null
            }
        } ?: return@cachedSearch SearchOutcome.NetworkError
        SearchOutcome.Ok(list)
    }

    /** Public alias for getMovieDetails — used by TmdbMatcher. */
    suspend fun getMovieDetailsById(id: Int, includeExtras: Boolean = true): TmdbMovie? =
        getMovieDetails(id, includeExtras)

    private companion object {
        const val MAX_MEMORY_ENTRIES = 200
        val PREFIX_TAG_REGEX = Regex("^[A-Za-z]{2,4}\\s*[:|\\-]\\s*")
        val BRACKET_TAG_REGEX = Regex("\\[[^]]*]")
        val YEAR_PAREN_REGEX = Regex("\\((19|20)\\d{2}\\)")
        val QUALITY_TAG_REGEX = Regex("\\b(4K|UHD|FHD|HD|1080p|720p|BluRay|WEB-DL|WEBRip|x264|x265|HEVC)\\b", RegexOption.IGNORE_CASE)
        val WHITESPACE_REGEX = Regex("\\s+")
    }
}

@Serializable
private data class TmdbCacheEnvelope(
    val found: Boolean = false,
    val savedAt: Long = 0L,
    val movie: TmdbMovie? = null,
)

@Serializable
private data class TmdbSearchCacheEnvelope(
    val savedAt: Long = 0L,
    val results: List<TmdbSearchResult> = emptyList(),
)

@Serializable
private data class SearchResponse(val results: List<SearchResult> = emptyList())

@Serializable
private data class SearchResult(val id: Int)

@Serializable
private data class RawSearchResponse(val results: List<TmdbSearchResult> = emptyList())

@Serializable
data class TmdbSearchResult(
    val id: Int = 0,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("original_language") val originalLanguage: String = "",
    val adult: Boolean = false,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
data class TmdbMovie(
    val id: Int = 0,
    val title: String = "",
    val overview: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    val adult: Boolean = false,
    val runtime: Int? = null,
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
    val images: TmdbImagesResponse? = null,
    val videos: TmdbVideosResponse? = null,
    val translations: TmdbTranslationsResponse? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val posterUrls: List<String> = emptyList(),
    val trailerUrl: String = "",
)

@Serializable
data class TmdbImagesResponse(
    val posters: List<TmdbImageItem> = emptyList(),
    val backdrops: List<TmdbImageItem> = emptyList(),
)

@Serializable
data class TmdbImageItem(
    @SerialName("file_path") val filePath: String? = null,
)

@Serializable
data class TmdbVideosResponse(
    val results: List<TmdbVideoItem> = emptyList(),
)

@Serializable
data class TmdbVideoItem(
    val key: String = "",
    val site: String = "",
    val type: String = "",
    val official: Boolean = false,
)

@Serializable
data class Genre(val name: String = "")

@Serializable
data class Credits(
    val cast: List<Cast> = emptyList(),
    val crew: List<Crew> = emptyList(),
)

@Serializable
data class Cast(
    val name: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
)

@Serializable
data class Crew(val name: String = "", val job: String = "")

@Serializable
private data class TvSearchResponse(val results: List<TvSearchItem> = emptyList())

@Serializable
private data class TvSearchItem(
    val id: Int = 0,
    val name: String = "",
    @SerialName("original_name") val originalName: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("original_language") val originalLanguage: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
)

@Serializable
private data class TmdbTvRaw(
    val id: Int = 0,
    val name: String = "",
    val overview: String = "",
    @SerialName("first_air_date") val firstAirDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val popularity: Double = 0.0,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
    val images: TmdbImagesResponse? = null,
    val videos: TmdbVideosResponse? = null,
    val translations: TmdbTranslationsResponse? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class TmdbTranslationsResponse(
    val translations: List<TmdbTranslationItem> = emptyList(),
)

@Serializable
data class TmdbTranslationItem(
    @SerialName("iso_639_1") val iso6391: String = "",
    val data: TmdbTranslationData? = null,
)

@Serializable
data class TmdbTranslationData(
    val overview: String = "",
)
