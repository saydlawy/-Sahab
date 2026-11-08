package com.saydlawy.sahab.data

import android.net.Uri
import android.util.Base64
import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class Repository(private val api: XtreamApi, private val tmdb: TmdbApi) {

    @Volatile
    var account: Account? = null
        private set

    @Volatile
    var userInfo: UserInfo? = null
        private set

    private val cache = ConcurrentHashMap<String, Any>()
    private val epgCache = ConcurrentHashMap<String, Pair<Long, List<Program>>>()
    private val epgGate = Semaphore(3)

    fun setAccount(value: Account?) {
        account = value
        userInfo = null
        clearCache()
    }

    fun clearCache() {
        cache.clear()
        epgCache.clear()
    }

    private fun requireAccount(): Account = account ?: throw ApiException(Ar.errNoAccount)

    suspend fun login(candidate: Account): UserInfo {
        val info = parseUserInfo(api.request(candidate, null))
        account = candidate
        userInfo = info
        clearCache()
        return info
    }

    suspend fun refreshUserInfo(): UserInfo {
        val info = parseUserInfo(api.request(requireAccount(), null))
        userInfo = info
        return info
    }

    fun liveExtensions(): List<String> {
        val allowed = userInfo?.formats.orEmpty()
        val preferred = listOf("m3u8", "ts").filter { it in allowed }
        return preferred.ifEmpty { listOf("m3u8", "ts") }
    }

    fun streamUrl(item: PlayItem, formatAttempt: Int = 0): String {
        val a = requireAccount()
        val user = Uri.encode(a.user)
        val pass = Uri.encode(a.pass)
        return when (item.kind) {
            Kind.LIVE -> {
                val exts = liveExtensions()
                val ext = exts[formatAttempt.coerceIn(0, exts.lastIndex)]
                "${a.server}/live/$user/$pass/${item.id}.$ext"
            }
            Kind.MOVIE -> "${a.server}/movie/$user/$pass/${item.id}.${item.ext.ifBlank { "mp4" }}"
            else -> "${a.server}/series/$user/$pass/${item.id}.${item.ext.ifBlank { "mp4" }}"
        }
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> cached(key: String, load: suspend () -> T): T {
        (cache[key] as? T)?.let { return it }
        return load().also { cache[key] = it }
    }

    private suspend fun request(action: String, params: Map<String, String> = emptyMap()): JsonElement =
        api.request(requireAccount(), action, params)

    private fun categoryParams(categoryId: String?): Map<String, String> =
        if (categoryId.isNullOrBlank()) emptyMap() else mapOf("category_id" to categoryId)

    // Live

    suspend fun liveCategories(): List<Category> = cached("live_cats") {
        parseCategories(request("get_live_categories"))
    }

    suspend fun liveChannels(categoryId: String?): List<Channel> = cached("live:${categoryId.orEmpty()}") {
        val json = request("get_live_streams", categoryParams(categoryId))
        withContext(Dispatchers.Default) { parseChannels(json) }
    }

    // Movies

    suspend fun movieCategories(): List<Category> = cached("vod_cats") {
        parseCategories(request("get_vod_categories"))
    }

    suspend fun movies(categoryId: String?): List<Poster> = cached("vod:${categoryId.orEmpty()}") {
        val json = request("get_vod_streams", categoryParams(categoryId))
        withContext(Dispatchers.Default) { parseMovies(json) }
    }

    suspend fun movieDetails(id: String): MovieDetails = cached("vod_info:$id") {
        val json = request("get_vod_info", mapOf("vod_id" to id))
        val base = parseMovieDetails(id, json)
        val tmdbData = try { tmdb.enrichMovie(base.name, base.year) } catch (e: Exception) { null }
        if (tmdbData == null) base else base.copy(
            image = base.image.ifBlank { tmdbData.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }.orEmpty() },
            backdrop = base.backdrop.ifBlank { tmdbData.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }.orEmpty() },
            plot = base.plot.ifBlank { tmdbData.overview },
            year = base.year.ifBlank { tmdbData.releaseDate.take(4) },
            rating = base.rating.ifBlank { if (tmdbData.voteAverage > 0) String.format(Locale.US, "%.1f", tmdbData.voteAverage) else "" },
            duration = base.duration.ifBlank { tmdbData.runtime?.let { "${it}m" }.orEmpty() },
            genre = base.genre.ifBlank { tmdbData.genres.joinToString(", ") { it.name } },
            director = base.director.ifBlank { tmdbData.credits?.crew?.firstOrNull { it.job == "Director" }?.name.orEmpty() },
            cast = base.cast.ifBlank { tmdbData.credits?.cast?.take(5)?.joinToString(", ") { it.name }.orEmpty() }
        )
    }

    // Series

    suspend fun seriesCategories(): List<Category> = cached("series_cats") {
        parseCategories(request("get_series_categories"))
    }

    suspend fun seriesList(categoryId: String?): List<Poster> = cached("series:${categoryId.orEmpty()}") {
        val json = request("get_series", categoryParams(categoryId))
        withContext(Dispatchers.Default) { parseSeries(json) }
    }

    suspend fun seriesDetails(id: String): SeriesDetails = cached("series_info:$id") {
        val json = request("get_series_info", mapOf("series_id" to id))
        withContext(Dispatchers.Default) { parseSeriesDetails(id, json) }
    }

    // Program guide

    suspend fun shortEpg(streamId: String, limit: Int = 3): List<Program> {
        val key = "short:$streamId:$limit"
        epgCache[key]?.let { (time, list) ->
            if (System.currentTimeMillis() - time < EPG_TTL_MS) return list
        }
        return epgGate.withPermit {
            val json = request("get_short_epg", mapOf("stream_id" to streamId, "limit" to limit.toString()))
            val list = parsePrograms(json)
            epgCache[key] = System.currentTimeMillis() to list
            list
        }
    }

    suspend fun dayEpg(streamId: String): List<Program> {
        val key = "day:$streamId"
        epgCache[key]?.let { (time, list) ->
            if (System.currentTimeMillis() - time < EPG_TTL_MS) return list
        }
        val json = request("get_simple_data_table", mapOf("stream_id" to streamId))
        val nowSec = System.currentTimeMillis() / 1000
        val list = parsePrograms(json).filter { it.end > nowSec - 2 * 3600 }.take(80)
        epgCache[key] = System.currentTimeMillis() to list
        return list
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

    private fun parseChannels(json: JsonElement): List<Channel> =
        json.arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.s("stream_id")
            if (id.isBlank()) null
            else Channel(id, o.s("name").ifBlank { Ar.noName }, o.s("stream_icon"), o.s("category_id"), o.s("epg_channel_id"))
        }.distinctBy { it.id }

    private fun parseMovies(json: JsonElement): List<Poster> =
        json.arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.s("stream_id")
            if (id.isBlank()) null
            else Poster(
                id = id,
                name = o.s("name").ifBlank { Ar.noName },
                image = o.s("stream_icon"),
                rating = formatRating(o.s("rating")),
                ext = o.s("container_extension"),
                year = "",
            )
        }.distinctBy { it.id }

    private fun parseSeries(json: JsonElement): List<Poster> =
        json.arr().orEmpty().mapNotNull { e ->
            val o = e.obj() ?: return@mapNotNull null
            val id = o.s("series_id")
            if (id.isBlank()) null
            else Poster(
                id = id,
                name = o.s("name").ifBlank { Ar.noName },
                image = o.s("cover"),
                rating = formatRating(o.s("rating")),
                ext = "",
                year = yearOf(o.s("releaseDate").ifBlank { o.s("release_date") }),
            )
        }.distinctBy { it.id }

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
            val decoded = String(Base64.decode(raw, Base64.DEFAULT), Charsets.UTF_8).trim()
            if (decoded.isBlank() || decoded.contains('�')) raw else decoded
        } catch (e: IllegalArgumentException) {
            raw
        }
    }

    private fun formatRating(raw: String): String {
        val value = raw.toDoubleOrNull() ?: return ""
        return if (value <= 0.0) "" else String.format(Locale.US, "%.1f", value)
    }

    private fun yearOf(raw: String): String {
        val match = Regex("(19|20)\\d{2}").find(raw)
        return match?.value.orEmpty()
    }

    private companion object {
        const val EPG_TTL_MS = 5 * 60_000L
    }
}
