package com.saydlawy.sahab.feature.instantwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

enum class InstantMediaType(val apiValue: String) {
    MOVIE("movie"),
    TV("tv"),
}

data class InstantMediaItem(
    val tmdbId: Int,
    val mediaType: InstantMediaType,
    val title: String,
    val originalTitle: String,
    val releaseYear: Int?,
    val posterUrl: String?,
    val overview: String = "",
    val imdbId: String? = null,
    val seasons: List<InstantSeasonSummary> = emptyList(),
    val rating: Double? = null,
    val genreIds: List<Int> = emptyList(),
    val backdropUrl: String? = null,
) {
    val contentKey: String get() = "${mediaType.apiValue}:$tmdbId"
}

data class InstantSeasonSummary(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
)

data class InstantDiscoverFeed(
    val latestMovies: List<InstantMediaItem> = emptyList(),
    val latestTv: List<InstantMediaItem> = emptyList(),
    val trendingAll: List<InstantMediaItem> = emptyList(),
    val topRatedMovies: List<InstantMediaItem> = emptyList(),
)

class TmdbRepository(
    private val client: OkHttpClient,
    internal val apiKey: String,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private data class CacheEntry(
        val items: List<InstantMediaItem>,
        val savedAtMs: Long,
    )

    private val listCache = ConcurrentHashMap<String, CacheEntry>()

    suspend fun loadDiscoverFeed(forceRefresh: Boolean = false): InstantDiscoverFeed = coroutineScope {
        if (apiKey.isBlank()) return@coroutineScope InstantDiscoverFeed()
        val latestMoviesDef = async {
            fetchListCached("movie/now_playing", InstantMediaType.MOVIE, forceRefresh)
        }
        val latestTvDef = async {
            fetchListCached("tv/on_the_air", InstantMediaType.TV, forceRefresh)
        }
        val trendingDef = async {
            fetchListCached("trending/all/week", null, forceRefresh)
        }
        val topRatedDef = async {
            fetchListCached("movie/top_rated", InstantMediaType.MOVIE, forceRefresh)
        }
        InstantDiscoverFeed(
            latestMovies = latestMoviesDef.await(),
            latestTv = latestTvDef.await(),
            trendingAll = trendingDef.await(),
            topRatedMovies = topRatedDef.await(),
        )
    }

    suspend fun discoverByGenre(
        genreId: Int,
        type: InstantMediaType,
        forceRefresh: Boolean = false,
    ): List<InstantMediaItem> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext emptyList()
        val cacheKey = "discover:${type.apiValue}:$genreId"
        if (!forceRefresh) {
            listCache[cacheKey]?.takeIf { System.currentTimeMillis() - it.savedAtMs < CACHE_TTL_MS }
                ?.let { return@withContext it.items }
        }
        val items = fetchMultiplePages(
            path = "discover/${type.apiValue}",
            fixedType = type,
            maxPages = 3,
            extraParams = mapOf(
                "sort_by" to "popularity.desc",
                "with_genres" to genreId.toString(),
            ),
        )
        if (items.isNotEmpty()) {
            listCache[cacheKey] = CacheEntry(items, System.currentTimeMillis())
        }
        items
    }

    private suspend fun fetchListCached(
        endpoint: String,
        fixedType: InstantMediaType?,
        forceRefresh: Boolean,
    ): List<InstantMediaItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            listCache[endpoint]?.takeIf { System.currentTimeMillis() - it.savedAtMs < CACHE_TTL_MS }
                ?.let { return@withContext it.items }
        }
        val items = fetchMultiplePages(
            path = endpoint,
            fixedType = fixedType,
            maxPages = 3,
        )
        if (items.isNotEmpty()) {
            listCache[endpoint] = CacheEntry(items, System.currentTimeMillis())
        }
        items
    }

    private suspend fun fetchPagesRaw(
        path: String,
        fixedType: InstantMediaType?,
        maxPages: Int,
        extraParams: Map<String, String>,
        language: String,
    ): List<InstantMediaItem> = coroutineScope {
        if (apiKey.isBlank() || maxPages <= 0) return@coroutineScope emptyList()
        fun buildUrl(page: Int): String {
            val b = "https://api.themoviedb.org/3/$path".toHttpUrl().newBuilder()
                .addQueryParameter("api_key", apiKey)
                .addQueryParameter("language", language)
                .addQueryParameter("include_adult", "false")
                .addQueryParameter("page", page.toString())
            extraParams.forEach { (k, v) -> b.addQueryParameter(k, v) }
            return b.build().toString()
        }

        val firstBody = executeGet(buildUrl(1)) ?: return@coroutineScope emptyList()
        val firstParsed = runCatching { json.decodeFromString<MultiSearchResponse>(firstBody) }.getOrNull()
            ?: return@coroutineScope emptyList()

        val targetPages = minOf(firstParsed.totalPages.coerceAtLeast(1), maxPages)
        val allDtos = ArrayList<SearchItemDto>(targetPages * 20)
        allDtos.addAll(firstParsed.results)

        if (targetPages > 1) {
            val restPages = (2..targetPages).map { page ->
                async(Dispatchers.IO) {
                    val body = executeGet(buildUrl(page)) ?: return@async emptyList()
                    runCatching { json.decodeFromString<MultiSearchResponse>(body) }.getOrNull()?.results.orEmpty()
                }
            }.awaitAll()
            restPages.forEach { allDtos.addAll(it) }
        }

        mapDtosToItems(allDtos, fixedType).distinctBy { it.contentKey }
    }

    internal suspend fun fetchMultiplePages(
        path: String,
        fixedType: InstantMediaType?,
        maxPages: Int,
        extraParams: Map<String, String> = emptyMap(),
        language: String = "ar-SA",
        fallbackToEnglishTitles: Boolean = true,
    ): List<InstantMediaItem> = coroutineScope {
        if (!fallbackToEnglishTitles || language != "ar-SA") {
            return@coroutineScope fetchPagesRaw(path, fixedType, maxPages, extraParams, language)
        }
        val arDef = async(Dispatchers.IO) { fetchPagesRaw(path, fixedType, maxPages, extraParams, "ar-SA") }
        val enDef = async(Dispatchers.IO) { fetchPagesRaw(path, fixedType, maxPages, extraParams, "en-US") }
        val arItems = arDef.await()
        val enByKey = enDef.await().associateBy { it.contentKey }
        arItems.map { item ->
            val enItem = enByKey[item.contentKey]
            val bestTitle = preferArabicOrEnglish(item.title, enItem?.title)
            val bestOrig = enItem?.title?.takeIf { it.isNotBlank() } ?: item.originalTitle
            if (bestTitle != item.title || bestOrig != item.originalTitle) {
                item.copy(title = bestTitle, originalTitle = bestOrig)
            } else {
                item
            }
        }
    }

    private fun mapDtosToItems(
        dtos: List<SearchItemDto>,
        fixedType: InstantMediaType?,
    ): List<InstantMediaItem> {
        return dtos.mapNotNull { dto ->
            val type = fixedType ?: when (dto.mediaType) {
                "movie" -> InstantMediaType.MOVIE
                "tv" -> InstantMediaType.TV
                else -> return@mapNotNull null
            }
            val displayTitle = (dto.title ?: dto.name).orEmpty().ifBlank { return@mapNotNull null }
            val origTitle = (dto.originalTitle ?: dto.originalName).orEmpty().ifBlank { displayTitle }
            val year = (dto.releaseDate ?: dto.firstAirDate)?.take(4)?.toIntOrNull()
            val cleanRating = dto.voteAverage?.takeIf { it > 0.0 && it < 9.95 }
            InstantMediaItem(
                tmdbId = dto.id,
                mediaType = type,
                title = displayTitle,
                originalTitle = origTitle,
                releaseYear = year,
                posterUrl = dto.posterPath?.let { "$POSTER_BASE$it" },
                backdropUrl = dto.backdropPath?.let { "$BACKDROP_BASE$it" },
                overview = dto.overview.orEmpty(),
                rating = cleanRating,
                genreIds = dto.genreIds.orEmpty(),
            )
        }
    }

    suspend fun searchMulti(query: String): List<InstantMediaItem> = coroutineScope {
        val cleanQuery = query.trim()
        if (apiKey.isBlank() || cleanQuery.isBlank()) return@coroutineScope emptyList()

        val arMultiDef = async(Dispatchers.IO) {
            fetchMultiplePages(
                path = "search/multi",
                fixedType = null,
                maxPages = 12,
                extraParams = mapOf("query" to cleanQuery),
                language = "ar-SA",
                fallbackToEnglishTitles = false,
            )
        }
        val enMoviesDef = async(Dispatchers.IO) {
            fetchMultiplePages(
                path = "search/movie",
                fixedType = InstantMediaType.MOVIE,
                maxPages = 6,
                extraParams = mapOf("query" to cleanQuery),
                language = "en-US",
                fallbackToEnglishTitles = false,
            )
        }
        val enTvDef = async(Dispatchers.IO) {
            fetchMultiplePages(
                path = "search/tv",
                fixedType = InstantMediaType.TV,
                maxPages = 6,
                extraParams = mapOf("query" to cleanQuery),
                language = "en-US",
                fallbackToEnglishTitles = false,
            )
        }

        val arList = arMultiDef.await()
        val enList = enMoviesDef.await() + enTvDef.await()
        val enByKey = enList.associateBy { it.contentKey }

        val combined = linkedMapOf<String, InstantMediaItem>()
        for (item in arList) {
            val enItem = enByKey[item.contentKey]
            val bestTitle = preferArabicOrEnglish(item.title, enItem?.title)
            val bestOrig = enItem?.title?.takeIf { it.isNotBlank() } ?: item.originalTitle
            combined[item.contentKey] = if (bestTitle != item.title || bestOrig != item.originalTitle) {
                item.copy(title = bestTitle, originalTitle = bestOrig)
            } else {
                item
            }
        }
        for (item in enList) {
            combined.putIfAbsent(item.contentKey, item)
        }
        combined.values.toList()
    }

    suspend fun searchPeople(query: String): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (apiKey.isBlank() || cleanQuery.isBlank()) return@withContext emptyList()
        val url = "https://api.themoviedb.org/3/search/person".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("query", cleanQuery)
            .addQueryParameter("include_adult", "false")
            .addQueryParameter("language", "ar-SA")
            .build()
        val body = executeGet(url.toString()) ?: return@withContext emptyList()
        val parsed = runCatching { json.decodeFromString<MultiSearchResponse>(body) }.getOrNull()
            ?: return@withContext emptyList()
        parsed.results.mapNotNull { dto ->
            val personName = (dto.name ?: dto.title).orEmpty().trim()
            if (dto.id > 0 && personName.isNotBlank()) dto.id to personName else null
        }.distinctBy { it.first }
    }

    suspend fun getDetails(tmdbId: Int, type: InstantMediaType): InstantMediaItem? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || tmdbId <= 0) return@withContext null
        val url = "https://api.themoviedb.org/3/${type.apiValue}/$tmdbId".toHttpUrl().newBuilder()
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "ar-SA")
            .addQueryParameter("append_to_response", "external_ids")
            .build()

        val body = executeGet(url.toString()) ?: return@withContext null
        val dto = runCatching { json.decodeFromString<DetailDto>(body) }.getOrNull()
            ?: return@withContext null

        val displayTitle = (dto.title ?: dto.name).orEmpty().ifBlank { return@withContext null }
        val origTitle = (dto.originalTitle ?: dto.originalName).orEmpty().ifBlank { displayTitle }
        val year = (dto.releaseDate ?: dto.firstAirDate)?.take(4)?.toIntOrNull()
        val seasons = dto.seasons.orEmpty()
            .filter { it.seasonNumber > 0 && it.episodeCount > 0 }
            .map { InstantSeasonSummary(it.seasonNumber, it.name.orEmpty().ifBlank { "الموسم ${it.seasonNumber}" }, it.episodeCount) }
        val cleanRating = dto.voteAverage?.takeIf { it > 0.0 && it < 9.95 }

        InstantMediaItem(
            tmdbId = dto.id,
            mediaType = type,
            title = displayTitle,
            originalTitle = origTitle,
            releaseYear = year,
            posterUrl = dto.posterPath?.let { "$POSTER_BASE$it" },
                backdropUrl = dto.backdropPath?.let { "$BACKDROP_BASE$it" },
            overview = dto.overview.orEmpty(),
            imdbId = dto.imdbId ?: dto.externalIds?.imdbId,
            seasons = seasons,
            rating = cleanRating,
            genreIds = dto.genres.orEmpty().map { it.id },
        )
    }

    suspend fun resolveByNumericId(tmdbId: Int): List<InstantMediaItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<InstantMediaItem>()
        getDetails(tmdbId, InstantMediaType.MOVIE)?.let { results.add(it) }
        getDetails(tmdbId, InstantMediaType.TV)?.let { results.add(it) }
        results
    }

    internal fun executeGet(url: String): String? {
        val req = Request.Builder().url(url).get().build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                resp.body?.string()
            }
        }.getOrNull()
    }

    @Serializable
    private data class MultiSearchResponse(
        val page: Int = 1,
        @SerialName("total_pages") val totalPages: Int = 1,
        @SerialName("total_results") val totalResults: Int = 0,
        val results: List<SearchItemDto> = emptyList(),
    )

    @Serializable
    private data class SearchItemDto(
        val id: Int,
        @SerialName("media_type") val mediaType: String? = null,
        val title: String? = null,
        val name: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        val overview: String? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        @SerialName("genre_ids") val genreIds: List<Int>? = null,
    )

    @Serializable
    private data class DetailDto(
        val id: Int,
        val title: String? = null,
        val name: String? = null,
        @SerialName("original_title") val originalTitle: String? = null,
        @SerialName("original_name") val originalName: String? = null,
        @SerialName("release_date") val releaseDate: String? = null,
        @SerialName("first_air_date") val firstAirDate: String? = null,
        @SerialName("poster_path") val posterPath: String? = null,
        @SerialName("backdrop_path") val backdropPath: String? = null,
        val overview: String? = null,
        @SerialName("imdb_id") val imdbId: String? = null,
        @SerialName("external_ids") val externalIds: ExternalIdsDto? = null,
        val seasons: List<SeasonDto>? = null,
        @SerialName("vote_average") val voteAverage: Double? = null,
        val genres: List<GenreDto>? = null,
    )

    @Serializable
    private data class GenreDto(val id: Int, val name: String? = null)

    @Serializable
    private data class ExternalIdsDto(@SerialName("imdb_id") val imdbId: String? = null)

    @Serializable
    private data class SeasonDto(
        @SerialName("season_number") val seasonNumber: Int = 0,
        val name: String? = null,
        @SerialName("episode_count") val episodeCount: Int = 0,
    )

    companion object {
        internal fun hasArabicLetters(text: String): Boolean =
            text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF' }

        internal fun preferArabicOrEnglish(arTitle: String?, enTitle: String?): String {
            val ar = arTitle?.trim().orEmpty()
            val en = enTitle?.trim().orEmpty()
            if (ar.isNotBlank() && hasArabicLetters(ar)) return ar
            if (en.isNotBlank()) return en
            return ar
        }

        const val POSTER_BASE = "https://image.tmdb.org/t/p/w500"
        internal const val BACKDROP_BASE = "https://image.tmdb.org/t/p/w780"
        private const val CACHE_TTL_MS = 30 * 60 * 1000L
    }
}
