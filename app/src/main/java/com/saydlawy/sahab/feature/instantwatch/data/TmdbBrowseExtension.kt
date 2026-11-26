package com.saydlawy.sahab.feature.instantwatch.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl

private val browseJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}

data class InstantGenreTile(
    val id: String,
    val title: String,
    val movieGenreId: Int?,
    val tvGenreId: Int?,
    val accentHex: Long,
)

data class InstantRegionTile(
    val id: String,
    val title: String,
    val subtitle: String,
    val originalLanguage: String,
    val originCountry: String? = null,
    val accentHex: Long,
)

data class InstantCollectionTile(
    val collectionId: Int,
    val title: String,
    val subtitle: String,
    val accentHex: Long,
)

data class InstantPersonProfile(
    val personId: Int,
    val name: String,
    val biography: String,
    val profileUrl: String?,
    val credits: List<InstantMediaItem>,
)

data class InstantCollectionDetails(
    val collectionId: Int,
    val name: String,
    val overview: String,
    val backdropUrl: String?,
    val parts: List<InstantMediaItem>,
)

object TmdbCatalog {
    val genreNamesAr: Map<Int, String> = mapOf(
        28 to "أكشن", 12 to "مغامرة", 16 to "أنمي", 35 to "كوميدي",
        80 to "جريمة", 99 to "وثائقي", 18 to "دراما", 10751 to "عائلي",
        14 to "فانتازيا", 36 to "تاريخي", 27 to "رعب", 10402 to "موسيقي",
        9648 to "غموض", 10749 to "رومانسي", 878 to "خيال علمي", 53 to "إثارة",
        10752 to "حرب", 37 to "غربي", 10759 to "أكشن ومغامرة",
        10765 to "خيال علمي", 10768 to "حرب وسياسة",
    )

    fun primaryGenreLabel(genreIds: List<Int>): String? =
        genreIds.firstNotNullOfOrNull { genreNamesAr[it] }

    val featuredGenres = listOf(
        InstantGenreTile("horror", "رعب", 27, 9648, 0xFFDC2626),
        InstantGenreTile("action", "أكشن", 28, 10759, 0xFFEA580C),
        InstantGenreTile("scifi", "خيال علمي", 878, 10765, 0xFF0284C7),
        InstantGenreTile("drama", "دراما", 18, 18, 0xFF7C3AED),
        InstantGenreTile("comedy", "كوميدي", 35, 35, 0xFFD97706),
        InstantGenreTile("crime", "جريمة وإثارة", 80, 80, 0xFF475569),
        InstantGenreTile("anime", "أنمي", 16, 16, 0xFFDB2777),
        InstantGenreTile("family", "عائلي", 10751, 10751, 0xFF059669),
    )

    val featuredRegions = listOf(
        InstantRegionTile("ar", "عربي ومصري", "أفلام ومسلسلات عربية", "ar", null, 0xFF059669),
        InstantRegionTile("ko", "كوري", "K-Drama وأفلام كورية", "ko", "KR", 0xFF2563EB),
        InstantRegionTile("hi", "هندي", "بوليوود وسينما الهند", "hi", "IN", 0xFFD97706),
        InstantRegionTile("tr", "تركي", "دراما وأكشن تركي", "tr", "TR", 0xFFDC2626),
        InstantRegionTile("ja", "ياباني", "أنمي وسينما يابانية", "ja", "JP", 0xFFDB2777),
        InstantRegionTile("en", "أمريكي وعالمي", "هوليوود وبريطانيا", "en", "US", 0xFF0284C7),
        InstantRegionTile("es", "إسباني وأوروبي", "إثارة ودراما أوروبية", "es", "ES", 0xFF7C3AED),
    )

    val featuredCollections = listOf(
        InstantCollectionTile(404609, "سلسلة John Wick", "4 أفلام أكشن", 0xFFDC2626),
        InstantCollectionTile(1241, "سلسلة Harry Potter", "8 أفلام فانتازيا", 0xFFD97706),
        InstantCollectionTile(119, "سلسلة Lord of the Rings", "الثلاثية الأسطورية", 0xFF059669),
        InstantCollectionTile(86311, "سلسلة The Avengers", "عالم مارفل", 0xFF2563EB),
        InstantCollectionTile(9485, "سلسلة Fast & Furious", "أكشن وسيارات", 0xFFEA580C),
        InstantCollectionTile(263, "ثلاثية The Dark Knight", "باتمان • نولان", 0xFF475569),
        InstantCollectionTile(87359, "سلسلة Mission: Impossible", "توم كروز", 0xFF0284C7),
        InstantCollectionTile(295, "قراصنة الكاريبي", "مغامرة وفانتازيا", 0xFF7C3AED),
    )
}

suspend fun TmdbRepository.discoverByGenreBoth(
    movieGenreId: Int?,
    tvGenreId: Int?,
    sortBy: String = "popularity.desc",
): List<InstantMediaItem> = coroutineScope {
    val mDef = movieGenreId?.let { async { discoverCustom(InstantMediaType.MOVIE, sortBy, withGenres = it.toString()) } }
    val tDef = tvGenreId?.let { async { discoverCustom(InstantMediaType.TV, sortBy, withGenres = it.toString()) } }
    interleaveLists(mDef?.await().orEmpty(), tDef?.await().orEmpty())
}

suspend fun TmdbRepository.discoverByRegion(
    language: String,
    country: String? = null,
    sortBy: String = "popularity.desc",
): List<InstantMediaItem> = coroutineScope {
    val mDef = async { discoverCustom(InstantMediaType.MOVIE, sortBy, origLang = language, origCountry = country) }
    val tDef = async { discoverCustom(InstantMediaType.TV, sortBy, origLang = language, origCountry = country) }
    interleaveLists(mDef.await(), tDef.await())
}

suspend fun TmdbRepository.getPersonProfileAndCredits(personId: Int): InstantPersonProfile? = withContext(Dispatchers.IO) {
    if (apiKey.isBlank() || personId <= 0) return@withContext null
    val url = "https://api.themoviedb.org/3/person/$personId".toHttpUrl().newBuilder()
        .addQueryParameter("api_key", apiKey)
        .addQueryParameter("language", "ar-SA")
        .addQueryParameter("append_to_response", "combined_credits")
        .build()

    val body = executeGet(url.toString()) ?: return@withContext null
    val dto = runCatching { browseJson.decodeFromString<PersonDetailDto>(body) }.getOrNull() ?: return@withContext null

    val credits = dto.combinedCredits?.cast.orEmpty()
        .sortedByDescending { it.popularity ?: 0.0 }
        .distinctBy { "${it.mediaType}:${it.id}" }
        .mapNotNull { c ->
            val type = when (c.mediaType) {
                "movie" -> InstantMediaType.MOVIE
                "tv" -> InstantMediaType.TV
                else -> return@mapNotNull null
            }
            val title = (c.title ?: c.name).orEmpty().ifBlank { return@mapNotNull null }
            InstantMediaItem(
                tmdbId = c.id,
                mediaType = type,
                title = title,
                originalTitle = (c.originalTitle ?: c.originalName).orEmpty().ifBlank { title },
                releaseYear = (c.releaseDate ?: c.firstAirDate)?.take(4)?.toIntOrNull(),
                posterUrl = c.posterPath?.let { "${TmdbRepository.POSTER_BASE}$it" },
                backdropUrl = c.backdropPath?.let { "${TmdbRepository.BACKDROP_BASE}$it" },
                overview = c.overview.orEmpty(),
                rating = c.voteAverage?.takeIf { it > 0.0 && it < 9.95 },
                genreIds = c.genreIds.orEmpty(),
            )
        }

    InstantPersonProfile(
        personId = dto.id,
        name = dto.name.orEmpty(),
        biography = dto.biography.orEmpty(),
        profileUrl = dto.profilePath?.let { "https://image.tmdb.org/t/p/w185$it" },
        credits = credits,
    )
}

suspend fun TmdbRepository.getCollectionDetails(collectionId: Int): InstantCollectionDetails? = withContext(Dispatchers.IO) {
    if (apiKey.isBlank() || collectionId <= 0) return@withContext null
    val url = "https://api.themoviedb.org/3/collection/$collectionId".toHttpUrl().newBuilder()
        .addQueryParameter("api_key", apiKey)
        .addQueryParameter("language", "ar-SA")
        .build()

    val body = executeGet(url.toString()) ?: return@withContext null
    val dto = runCatching { browseJson.decodeFromString<CollectionDto>(body) }.getOrNull() ?: return@withContext null

    val parts = dto.parts.orEmpty()
        .mapNotNull { m ->
            val title = m.title.orEmpty().ifBlank { return@mapNotNull null }
            InstantMediaItem(
                tmdbId = m.id,
                mediaType = InstantMediaType.MOVIE,
                title = title,
                originalTitle = m.originalTitle.orEmpty().ifBlank { title },
                releaseYear = m.releaseDate?.take(4)?.toIntOrNull(),
                posterUrl = m.posterPath?.let { "${TmdbRepository.POSTER_BASE}$it" },
                backdropUrl = m.backdropPath?.let { "${TmdbRepository.BACKDROP_BASE}$it" },
                overview = m.overview.orEmpty(),
                rating = m.voteAverage?.takeIf { it > 0.0 && it < 9.95 },
                genreIds = m.genreIds.orEmpty(),
            )
        }
        .sortedBy { it.releaseYear ?: 9999 }

    InstantCollectionDetails(
        collectionId = dto.id,
        name = dto.name.orEmpty(),
        overview = dto.overview.orEmpty(),
        backdropUrl = dto.backdropPath?.let { "${TmdbRepository.BACKDROP_BASE}$it" },
        parts = parts,
    )
}

private suspend fun TmdbRepository.discoverCustom(
    type: InstantMediaType,
    sortBy: String,
    withGenres: String? = null,
    origLang: String? = null,
    origCountry: String? = null,
): List<InstantMediaItem> = withContext(Dispatchers.IO) {
    if (apiKey.isBlank()) return@withContext emptyList()
    val builder = "https://api.themoviedb.org/3/discover/${type.apiValue}".toHttpUrl().newBuilder()
        .addQueryParameter("api_key", apiKey)
        .addQueryParameter("language", "ar-SA")
        .addQueryParameter("include_adult", "false")
        .addQueryParameter("sort_by", sortBy)
    if (sortBy.startsWith("vote_average")) builder.addQueryParameter("vote_count.gte", "150")
    if (!withGenres.isNullOrBlank()) builder.addQueryParameter("with_genres", withGenres)
    if (!origLang.isNullOrBlank()) builder.addQueryParameter("with_original_language", origLang)
    if (!origCountry.isNullOrBlank()) builder.addQueryParameter("with_origin_country", origCountry)

    val body = executeGet(builder.build().toString()) ?: return@withContext emptyList()
    val parsed = runCatching { browseJson.decodeFromString<DiscoverRespDto>(body) }.getOrNull() ?: return@withContext emptyList()
    parsed.results.mapNotNull { d ->
        val title = (d.title ?: d.name).orEmpty().ifBlank { return@mapNotNull null }
        InstantMediaItem(
            tmdbId = d.id,
            mediaType = type,
            title = title,
            originalTitle = (d.originalTitle ?: d.originalName).orEmpty().ifBlank { title },
            releaseYear = (d.releaseDate ?: d.firstAirDate)?.take(4)?.toIntOrNull(),
            posterUrl = d.posterPath?.let { "${TmdbRepository.POSTER_BASE}$it" },
            backdropUrl = d.backdropPath?.let { "${TmdbRepository.BACKDROP_BASE}$it" },
            overview = d.overview.orEmpty(),
            rating = d.voteAverage?.takeIf { it > 0.0 && it < 9.95 },
            genreIds = d.genreIds.orEmpty(),
        )
    }
}

private fun interleaveLists(a: List<InstantMediaItem>, b: List<InstantMediaItem>): List<InstantMediaItem> {
    val out = ArrayList<InstantMediaItem>(a.size + b.size)
    val max = maxOf(a.size, b.size)
    for (i in 0 until max) {
        if (i < a.size) out.add(a[i])
        if (i < b.size) out.add(b[i])
    }
    return out
}

@Serializable private data class DiscoverRespDto(val results: List<BrowseItemDto> = emptyList())
@Serializable private data class PersonDetailDto(
    val id: Int,
    val name: String? = null,
    val biography: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("combined_credits") val combinedCredits: CombinedCreditsDto? = null,
)
@Serializable private data class CombinedCreditsDto(val cast: List<BrowseItemDto>? = null)
@Serializable private data class CollectionDto(
    val id: Int,
    val name: String? = null,
    val overview: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    val parts: List<BrowseItemDto>? = null,
)
@Serializable private data class BrowseItemDto(
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
    val popularity: Double? = null,
    @SerialName("genre_ids") val genreIds: List<Int>? = null,
)
