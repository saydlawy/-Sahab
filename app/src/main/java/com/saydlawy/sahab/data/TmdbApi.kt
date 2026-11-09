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
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

class TmdbApi(
    private val client: OkHttpClient,
    private val apiKey: String,
    private val diskCache: DiskCache? = null,
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val memoryCache = ConcurrentHashMap<String, TmdbCacheEnvelope>()

    suspend fun enrichMovie(name: String, year: String): TmdbMovie? {
        if (apiKey.isBlank() || name.isBlank()) return null
        val cleanedName = cleanQueryTitle(name)
        if (cleanedName.isBlank()) return null
        val cleanYear = year.filter { it.isDigit() }.takeIf { it.length == 4 }.orEmpty()
        val cacheKey = "tmdb_v1:${cleanedName.lowercase(Locale.ROOT)}:$cleanYear"

        // 1. Memory cache check (0ms)
        memoryCache[cacheKey]?.let { return it.movie }

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
                    memoryCache[cacheKey] = cached
                    return cached.movie
                }
            }
        }

        // 3. Network call (only when never queried before)
        val id = searchMovie(cleanedName, cleanYear)
        val details = if (id != null) getMovieDetails(id) else null
        val envelope = TmdbCacheEnvelope(
            found = details != null,
            savedAt = System.currentTimeMillis(),
            movie = details,
        )
        memoryCache[cacheKey] = envelope
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
        raw.replace(Regex("^[A-Za-z]{2,4}\\s*[:|\\-]\\s*"), "")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\((19|20)\\d{2}\\)"), " ")
            .replace(Regex("\\b(4K|UHD|FHD|HD|1080p|720p|BluRay|WEB-DL|WEBRip|x264|x265|HEVC)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private suspend fun searchMovie(name: String, year: String): Int? {
        val urlBuilder = "https://api.themoviedb.org/3/search/movie".toHttpUrl().newBuilder()
            .addQueryParameter("query", name)
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "ar")
        if (year.length == 4) {
            urlBuilder.addQueryParameter("year", year)
        }
        val text = execute(urlBuilder.build().toString()) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<SearchResponse>(text).results.firstOrNull()?.id
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun getMovieDetails(id: Int): TmdbMovie? {
        val url = "https://api.themoviedb.org/3/movie/$id?api_key=$apiKey&language=ar&append_to_response=credits".toHttpUrl()
        val text = execute(url.toString()) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<TmdbMovie>(text)
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun execute(url: String): String? = suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(url).build()
        val call = client.newCall(request)
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
}

@Serializable
private data class TmdbCacheEnvelope(
    val found: Boolean = false,
    val savedAt: Long = 0L,
    val movie: TmdbMovie? = null,
)

@Serializable
private data class SearchResponse(val results: List<SearchResult> = emptyList())

@Serializable
private data class SearchResult(val id: Int)

@Serializable
data class TmdbMovie(
    val id: Int = 0,
    val title: String = "",
    val overview: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    val runtime: Int? = null,
    val genres: List<Genre> = emptyList(),
    val credits: Credits? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
)

@Serializable
data class Genre(val name: String = "")

@Serializable
data class Credits(
    val cast: List<Cast> = emptyList(),
    val crew: List<Crew> = emptyList(),
)

@Serializable
data class Cast(val name: String = "")

@Serializable
data class Crew(val name: String = "", val job: String = "")
