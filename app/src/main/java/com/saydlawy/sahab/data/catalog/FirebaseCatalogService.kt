package com.saydlawy.sahab.data.catalog

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class FirebaseCatalogService {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("global_catalog")

    suspend fun getCatalogItem(sahabId: String): CatalogMovieEntity? = suspendCancellableCoroutine { cont ->
        collection.document(sahabId).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.exists()) {
                    cont.resume(mapToEntity(sahabId, snapshot.data ?: emptyMap()))
                } else {
                    cont.resume(null)
                }
            }
            .addOnFailureListener {
                cont.resume(null)
            }
    }

    suspend fun getTrendingMovies(limit: Long = 15): List<CatalogMovieEntity> = suspendCancellableCoroutine { cont ->
        collection
            .orderBy("rating", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents.mapNotNull { doc ->
                    try {
                        mapToEntity(doc.id, doc.data ?: emptyMap())
                    } catch (e: Exception) {
                        null
                    }
                }
                cont.resume(list)
            }
            .addOnFailureListener {
                cont.resume(emptyList())
            }
    }

    /**
     * Loads recommended movies for the Home Screen by combining:
     * 1. Real playable VOD streams from the active Xtream server (`repo.movies(null)`).
     * 2. Enriched metadata (plot, genre, rating, year, TMDB/IMDB info) from Firebase `global_catalog`
     *    and the local synced `CatalogMovieDao`.
     *
     * Every returned item is guaranteed to have a valid Xtream `novaStreamId` and live poster URL.
     */
    suspend fun getXtreamRecommendedMovies(
        repo: Repository,
        dao: CatalogMovieDao,
        limit: Int = 15,
    ): List<CatalogMovieEntity> = withContext(Dispatchers.IO) {
        // 1. Fetch real movies from the active Xtream server
        val xtreamMovies: List<Poster> = runCatching {
            repo.peekAllMovies() ?: repo.movies(null)
        }.getOrNull().orEmpty().filter { it.id.isNotBlank() && it.image.isNotBlank() }

        if (xtreamMovies.isEmpty()) {
            return@withContext runCatching { getTrendingMovies(limit.toLong()) }.getOrNull().orEmpty()
        }

        val xtreamById = HashMap<String, Poster>(xtreamMovies.size)
        val xtreamByTitle = HashMap<String, Poster>(xtreamMovies.size)
        for (p in xtreamMovies) {
            xtreamById[p.id] = p
            val norm = normalizeTitle(p.name)
            if (norm.length >= 3 && !xtreamByTitle.containsKey(norm)) {
                xtreamByTitle[norm] = p
            }
        }

        val results = ArrayList<CatalogMovieEntity>(limit)
        val usedStreamIds = HashSet<String>()
        val usedSahabIds = HashSet<String>()

        // 2. Query Firebase global_catalog & match with active Xtream server streams
        val fbCandidates = withTimeoutOrNull(4500L) {
            runCatching { getTrendingMovies(60) }.getOrNull()
        }.orEmpty()

        if (fbCandidates.isNotEmpty()) {
            val fbIds = fbCandidates.map { it.sahabId }.distinct()
            val roomMatchesBySahabId = runCatching {
                dao.findByIds(fbIds.take(100)).associateBy { it.sahabId }
            }.getOrNull().orEmpty()

            for (fb in fbCandidates) {
                if (results.size >= limit) break
                val roomEntity = roomMatchesBySahabId[fb.sahabId]
                val candidateStreamId = fb.novaStreamId.ifBlank { roomEntity?.novaStreamId.orEmpty() }

                val matchedPoster: Poster? = when {
                    candidateStreamId.isNotBlank() && xtreamById.containsKey(candidateStreamId) ->
                        xtreamById[candidateStreamId]
                    else -> {
                        val normAr = normalizeTitle(fb.titleAr)
                        val normEn = normalizeTitle(fb.titleEn)
                        val normRoom = normalizeTitle(roomEntity?.titleAr.orEmpty())
                        xtreamByTitle[normAr]
                            ?: xtreamByTitle[normEn]
                            ?: xtreamByTitle[normRoom]
                    }
                }

                if (matchedPoster != null && usedStreamIds.add(matchedPoster.id) && usedSahabIds.add(fb.sahabId)) {
                    results.add(
                        mergeXtreamAndCatalog(
                            poster = matchedPoster,
                            fb = fb,
                            room = roomEntity,
                        ),
                    )
                }
            }
        }

        // 3. Fill remaining slots with top/recent Xtream server movies enriched from Room + Firebase
        if (results.size < limit) {
            val needed = limit - results.size
            // Prioritize recent high-rated Xtream movies with valid posters
            val topXtreamCandidates = xtreamMovies
                .asSequence()
                .filter { it.id !in usedStreamIds }
                .sortedWith(
                    compareByDescending<Poster> { it.year >= "2024" }
                        .thenByDescending { it.rating.toDoubleOrNull() ?: 0.0 }
                        .thenByDescending { it.added },
                )
                .take(needed * 2)
                .toList()

            val candidateIds = topXtreamCandidates.map { it.id }
            val roomByStreamId = runCatching {
                dao.findByStreamIds(candidateIds).associateBy { it.novaStreamId }
            }.getOrNull().orEmpty()

            // Fetch Firebase enrichment in parallel for these Xtream movies
            val fbByStreamId: Map<String, CatalogMovieEntity?> = withTimeoutOrNull(4000L) {
                supervisorScope {
                    topXtreamCandidates.take(needed).map { poster ->
                        async {
                            val roomItem = roomByStreamId[poster.id]
                            val lookupSahabId = roomItem?.sahabId ?: CatalogMovieEntity.idPending(poster.id)
                            val fbItem = runCatching { getCatalogItem(lookupSahabId) }.getOrNull()
                            poster.id to fbItem
                        }
                    }.awaitAll().toMap()
                }
            }.orEmpty()

            for (poster in topXtreamCandidates) {
                if (results.size >= limit) break
                val roomItem = roomByStreamId[poster.id]
                val fbItem = fbByStreamId[poster.id]
                val merged = mergeXtreamAndCatalog(
                    poster = poster,
                    fb = fbItem,
                    room = roomItem,
                )
                if (!usedStreamIds.add(poster.id) || !usedSahabIds.add(merged.sahabId)) continue
                results.add(merged)
            }
        }

        results
    }

    private fun mergeXtreamAndCatalog(
        poster: Poster,
        fb: CatalogMovieEntity?,
        room: CatalogMovieEntity?,
    ): CatalogMovieEntity {
        val cleanXtreamName = cleanDisplayTitle(poster.name)
        val bestTitle = cleanXtreamName.ifBlank {
            fb?.titleAr?.ifBlank { null }
                ?: room?.titleAr?.ifBlank { null }
                ?: fb?.titleEn?.ifBlank { null }
                ?: poster.name
        }
        val bestRating = poster.rating.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            ?: fb?.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            ?: room?.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            ?: "7.8"
        val bestYear = poster.year.ifBlank {
            fb?.year?.ifBlank { null } ?: room?.year.orEmpty()
        }
        val bestGenre = fb?.genre?.ifBlank { null }
            ?: room?.genre?.ifBlank { null }
            ?: "دراما وتشويق"
        val bestPlot = fb?.plot?.ifBlank { null }
            ?: room?.plot?.ifBlank { null }
            ?: ""
        // Always prefer the active Xtream server's poster URL so dead seed domains never break images
        val bestPoster = poster.image.ifBlank {
            fb?.posterUrl?.ifBlank { null } ?: room?.posterUrl.orEmpty()
        }

        return CatalogMovieEntity(
            sahabId = fb?.sahabId ?: room?.sahabId ?: CatalogMovieEntity.idPending(poster.id),
            novaStreamId = poster.id,
            categoryId = poster.categoryId,
            titleAr = bestTitle,
            titleEn = fb?.titleEn ?: room?.titleEn.orEmpty(),
            year = bestYear,
            plot = bestPlot,
            genre = bestGenre,
            runtime = fb?.runtime ?: room?.runtime.orEmpty(),
            rating = bestRating,
            posterUrl = bestPoster,
            backdropUrl = fb?.backdropUrl ?: room?.backdropUrl.orEmpty(),
            novaAdded = poster.added,
            containerExtension = poster.ext.ifBlank { "mp4" },
            needsEnrichment = 0,
        )
    }

    private fun normalizeTitle(raw: String): String {
        if (raw.isBlank()) return ""
        return raw
            .lowercase(Locale.ROOT)
            .replace(Regex("""\[.*?]|\(.*?\)"""), " ")
            .replace(Regex("""\b(19\d\d|20\d\d|4k|uhd|fhd|hd|1080p|720p|bluray|web-dl|webrip|ar|en|sub|مدبلج|مترجم)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
    }

    private fun cleanDisplayTitle(raw: String): String {
        return raw
            .replace(Regex("""^\[.*?]\s*"""), "")
            .replace(Regex("""\s*\[.*?]$"""), "")
            .trim()
    }

    suspend fun saveCatalogItem(entity: CatalogMovieEntity) = suspendCancellableCoroutine { cont ->
        val data = mapOf(
            "novaStreamId" to entity.novaStreamId,
            "titleAr" to entity.titleAr,
            "titleEn" to entity.titleEn,
            "year" to entity.year,
            "plot" to entity.plot,
            "plotSource" to entity.plotSource,
            "plotConfidence" to entity.plotConfidence,
            "identifiedTitle" to entity.identifiedTitle,
            "mediaType" to entity.mediaType,
            "cast" to entity.cast,
            "director" to entity.director,
            "genre" to entity.genre,
            "runtime" to entity.runtime,
            "rating" to entity.rating,
            "posterUrl" to entity.posterUrl,
            "backdropUrl" to entity.backdropUrl,
            "tmdbId" to entity.tmdbId,
            "imdbId" to entity.imdbId,
            "tmdbVoteCount" to entity.tmdbVoteCount,
        )
        collection.document(entity.sahabId).set(data)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resume(Unit) }
    }

    private fun mapToEntity(id: String, data: Map<String, Any>): CatalogMovieEntity {
        return CatalogMovieEntity(
            sahabId = id,
            novaStreamId = data["novaStreamId"] as? String ?: "",
            titleAr = data["titleAr"] as? String ?: "",
            titleEn = data["titleEn"] as? String ?: "",
            year = data["year"] as? String ?: "",
            plot = data["plot"] as? String ?: "",
            plotSource = data["plotSource"] as? String ?: "",
            plotConfidence = data["plotConfidence"] as? String ?: "",
            identifiedTitle = data["identifiedTitle"] as? String ?: "",
            mediaType = data["mediaType"] as? String ?: "movie",
            cast = data["cast"] as? String ?: "",
            director = data["director"] as? String ?: "",
            genre = data["genre"] as? String ?: "",
            runtime = data["runtime"] as? String ?: "",
            rating = data["rating"] as? String ?: "",
            posterUrl = data["posterUrl"] as? String ?: "",
            backdropUrl = data["backdropUrl"] as? String ?: "",
            imdbId = data["imdbId"] as? String ?: "",
            tmdbId = (data["tmdbId"] as? Number)?.toInt(),
            tmdbVoteCount = (data["tmdbVoteCount"] as? Number)?.toInt(),
            needsEnrichment = 0,
            lastEnriched = System.currentTimeMillis(),
        )
    }
}
