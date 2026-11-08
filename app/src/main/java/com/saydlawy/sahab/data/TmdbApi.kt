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
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TmdbApi(private val client: OkHttpClient, private val apiKey: String) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun enrichMovie(name: String, year: String): TmdbMovie? {
        if (apiKey.isBlank() || name.isBlank()) return null
        val id = searchMovie(name, year) ?: return null
        return getMovieDetails(id)
    }

    private suspend fun searchMovie(name: String, year: String): Int? {
        val encoded = try { URLEncoder.encode(name, "UTF-8") } catch (e: Exception) { return null }
        val urlBuilder = "https://api.themoviedb.org/3/search/movie".toHttpUrl().newBuilder()
            .addQueryParameter("query", name)
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("language", "ar")
        if (year.isNotBlank()) {
            val cleanYear = year.filter { it.isDigit() }
            if (cleanYear.length == 4) urlBuilder.addQueryParameter("year", cleanYear)
        }
        val text = execute(urlBuilder.build().toString()) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<SearchResponse>(text).results.firstOrNull()?.id
            } catch (e: Exception) { null }
        }
    }

    private suspend fun getMovieDetails(id: Int): TmdbMovie? {
        val url = "https://api.themoviedb.org/3/movie/$id?api_key=$apiKey&language=ar&append_to_response=credits".toHttpUrl()
        val text = execute(url.toString()) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                json.decodeFromString<TmdbMovie>(text)
            } catch (e: Exception) { null }
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
