package com.saydlawy.sahab.data.catalog

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.saydlawy.sahab.data.DEFAULT_VOD_EXT
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

    @Volatile
    private var cachedRecommendedMovies: List<CatalogMovieEntity> = emptyList()

    fun peekCachedRecommendedMovies(): List<CatalogMovieEntity> = cachedRecommendedMovies

    suspend fun getCatalogItem(sahabId: String): CatalogMovieEntity? {
        val direct = fetchDocById(sahabId)
        if (direct != null) return direct
        if (sahabId.startsWith("pending:")) {
            return queryByAlias(sahabId)
        }
        return null
    }

    private suspend fun fetchDocById(docId: String, allowRedirect: Boolean = true): CatalogMovieEntity? =
        suspendCancellableCoroutine { cont ->
            collection.document(docId).get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.exists()) {
                        cont.resume(null)
                        return@addOnSuccessListener
                    }
                    val redirectTarget = snapshot.getString("redirectTo")?.takeIf {
                        it.isNotBlank() && it != docId
                    }
                    if (allowRedirect && redirectTarget != null) {
                        collection.document(redirectTarget).get()
                            .addOnSuccessListener { targetSnap ->
                                if (targetSnap.exists()) {
                                    cont.resume(mapToEntity(redirectTarget, targetSnap.data ?: emptyMap()))
                                } else {
                                    cont.resume(mapToEntity(docId, snapshot.data ?: emptyMap()))
                                }
                            }
                            .addOnFailureListener {
                                cont.resume(mapToEntity(docId, snapshot.data ?: emptyMap()))
                            }
                    } else {
                        cont.resume(mapToEntity(docId, snapshot.data ?: emptyMap()))
                    }
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }

    private suspend fun queryByAlias(aliasId: String): CatalogMovieEntity? =
        suspendCancellableCoroutine { cont ->
            collection.whereArrayContains("aliases", aliasId)
                .limit(1)
                .get()
                .addOnSuccessListener { query ->
                    val doc = query.documents.firstOrNull()
                    if (doc != null && doc.exists()) {
                        cont.resume(mapToEntity(doc.id, doc.data ?: emptyMap()))
                    } else {
                        cont.resume(null)
                    }
                }
                .addOnFailureListener { cont.resume(null) }
        }

    /**
     * Queries Firebase `global_catalog` for recently enriched / newest items.
     */
    suspend fun getTrendingMovies(limit: Long = 18): List<CatalogMovieEntity> = suspendCancellableCoroutine { cont ->
        collection
            .orderBy("tmdbVoteCount", Query.Direction.DESCENDING)
            .limit(limit * 2)
            .get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.documents
                    .mapNotNull { doc ->
                        try {
                            mapToEntity(doc.id, doc.data ?: emptyMap())
                        } catch (e: Exception) {
                            null
                        }
                    }
                    .filter { m -> m.year >= "2024" }
                    .take(limit.toInt())
                cont.resume(list)
            }
            .addOnFailureListener {
                cont.resume(emptyList())
            }
    }

    /**
     * PHASE 1 (INSTANT < 20ms): Returns the newest added movies immediately from memory/disk/Room
     * without waiting for any network or Firestore round-trip, so HomeScreen renders immediately.
     */
    suspend fun getInstantRecommendedMovies(
        repo: Repository,
        dao: CatalogMovieDao,
        limit: Int = 18,
    ): List<CatalogMovieEntity> = withContext(Dispatchers.IO) {
        if (cachedRecommendedMovies.isNotEmpty()) {
            return@withContext cachedRecommendedMovies.take(limit)
        }

        val memMovies = repo.peekAllMovies()
            ?.filter { it.id.isNotBlank() && it.image.isNotBlank() }
            .orEmpty()

        if (memMovies.isNotEmpty()) {
            val newestPosters = sortNewestXtreamAdditions(memMovies, limit)
            val streamIds = newestPosters.map { it.id }
            val roomByStreamId = runCatching {
                dao.findByStreamIds(streamIds).associateBy { it.novaStreamId }
            }.getOrNull().orEmpty()

            val fastList = newestPosters.map { poster ->
                mergeXtreamAndCatalog(
                    poster = poster,
                    fb = null,
                    room = roomByStreamId[poster.id],
                )
            }
            if (fastList.isNotEmpty()) {
                cachedRecommendedMovies = fastList
                return@withContext fastList
            }
        }

        // Fallback to Room's recently added table (novaAdded DESC) while Xtream cache warms up
        val roomRecent = runCatching {
            dao.getRecentlyAdded(limit * 2)
                .filter { it.novaStreamId.isNotBlank() && it.posterUrl.isNotBlank() }
                .take(limit)
        }.getOrNull().orEmpty()

        if (roomRecent.isNotEmpty()) {
            cachedRecommendedMovies = roomRecent
        }
        roomRecent
    }

    /**
     * PHASE 2: Selects movies strictly based on the NEWEST ADDITIONS (`أحدث الإضافات فقط`)
     * from the active Xtream server (`poster.added DESC` & `stream_id DESC`) and enriches them
     * with real metadata from the local synced Room DB + Firebase `global_catalog`.
     */
    suspend fun getXtreamRecommendedMovies(
        repo: Repository,
        dao: CatalogMovieDao,
        limit: Int = 18,
    ): List<CatalogMovieEntity> = withContext(Dispatchers.IO) {
        val xtreamMovies: List<Poster> = runCatching {
            repo.peekAllMovies() ?: repo.movies(null)
        }.getOrNull().orEmpty().filter { it.id.isNotBlank() && it.image.isNotBlank() }

        if (xtreamMovies.isEmpty()) {
            val fallback = runCatching { dao.getRecentlyAdded(limit) }.getOrNull().orEmpty()
            if (fallback.isNotEmpty()) cachedRecommendedMovies = fallback
            return@withContext fallback
        }

        // Strictly sort by newest additions on the Xtream server (added timestamp DESC, then numeric stream_id DESC)
        val newestPosters = sortNewestXtreamAdditions(xtreamMovies, limit)
        val candidateIds = newestPosters.map { it.id }

        val roomByStreamId = runCatching {
            dao.findByStreamIds(candidateIds).associateBy { it.novaStreamId }
        }.getOrNull().orEmpty()

        // Emit immediate local merge to cache so UI is already populated
        val initialMerged = newestPosters.map { poster ->
            mergeXtreamAndCatalog(
                poster = poster,
                fb = null,
                room = roomByStreamId[poster.id],
            )
        }
        if (initialMerged.isNotEmpty()) {
            cachedRecommendedMovies = initialMerged
        }

        // Enrich with live Firebase `global_catalog` documents in parallel (bounded to 2000ms)
        val fbByStreamId: Map<String, CatalogMovieEntity?> = withTimeoutOrNull(2000L) {
            supervisorScope {
                newestPosters.map { poster ->
                    async {
                        val roomItem = roomByStreamId[poster.id]
                        // If Room already has rich plot & genre from Firebase seed/sync, avoid redundant network call
                        if (roomItem != null && roomItem.plot.isNotBlank() && roomItem.genre.isNotBlank()) {
                            poster.id to roomItem
                        } else {
                            val lookupSahabId = roomItem?.sahabId ?: CatalogMovieEntity.idPending(poster.id)
                            val fbItem = runCatching { getCatalogItem(lookupSahabId) }.getOrNull()
                            poster.id to fbItem
                        }
                    }
                }.awaitAll().toMap()
            }
        }.orEmpty()

        val finalResults = ArrayList<CatalogMovieEntity>(newestPosters.size)
        val usedStreamIds = HashSet<String>()

        for (poster in newestPosters) {
            if (!usedStreamIds.add(poster.id)) continue
            val roomItem = roomByStreamId[poster.id]
            val fbItem = fbByStreamId[poster.id]
            finalResults.add(
                mergeXtreamAndCatalog(
                    poster = poster,
                    fb = fbItem,
                    room = roomItem,
                ),
            )
        }

        if (finalResults.isNotEmpty()) {
            cachedRecommendedMovies = finalResults
        }
        finalResults
    }

    /**
     * Sorts Xtream VOD streams strictly by newest additions (`added` timestamp DESC,
     * then numeric `id` DESC, deduplicated by normalized title).
     */
    private fun sortNewestXtreamAdditions(
        movies: List<Poster>,
        limit: Int,
    ): List<Poster> {
        val sorted = movies.sortedWith(
            compareByDescending<Poster> { it.added }
                .thenByDescending { it.id.toLongOrNull() ?: 0L },
        )
        val selected = ArrayList<Poster>(limit)
        val seenTitles = HashSet<String>()
        for (poster in sorted) {
            if (selected.size >= limit) break
            val norm = normalizeTitle(poster.name).ifBlank { poster.id }
            if (seenTitles.add(norm)) {
                selected.add(poster)
            }
        }
        return selected
    }

    private fun mergeXtreamAndCatalog(
        poster: Poster,
        fb: CatalogMovieEntity?,
        room: CatalogMovieEntity?,
    ): CatalogMovieEntity {
        val cleanXtreamName = cleanDisplayTitle(poster.name)
        val bestTitle = fb?.titleEn?.takeIf { it.isNotBlank() && anyArabic(it) }
            ?: fb?.titleAr?.takeIf { it.isNotBlank() }
            ?: room?.titleEn?.takeIf { it.isNotBlank() && anyArabic(it) }
            ?: cleanXtreamName.ifBlank { poster.name }
        val bestRating = fb?.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            ?: room?.rating?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            ?: poster.rating.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
            ?: ""
        val bestYear = fb?.year?.ifBlank { null }
            ?: room?.year?.ifBlank { null }
            ?: poster.year
        val bestGenre = fb?.genre?.ifBlank { null }
            ?: room?.genre?.ifBlank { null }
            ?: "أحدث الإضافات"
        val bestPlot = fb?.plot?.ifBlank { null }
            ?: room?.plot?.ifBlank { null }
            ?: ""
        // Always prefer the active Xtream server's live poster URL
        val bestPoster = poster.image.ifBlank {
            fb?.backdropUrl?.ifBlank { null }
                ?: room?.backdropUrl?.ifBlank { null }
                ?: fb?.posterUrl.orEmpty()
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
            containerExtension = poster.ext.ifBlank { DEFAULT_VOD_EXT },
            tmdbVoteCount = fb?.tmdbVoteCount ?: room?.tmdbVoteCount,
            needsEnrichment = 0,
        )
    }

    private fun anyArabic(s: String): Boolean = s.any { it in '\u0600'..'\u06FF' }

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
        val aliases = aliasesFor(entity)
        val tmdbDocId = entity.tmdbId?.takeIf { it > 0 }?.let { CatalogMovieEntity.idFromTmdb(it) }
        val data = buildMap<String, Any?> {
            put("novaStreamId", entity.novaStreamId)
            put("titleAr", entity.titleAr)
            put("titleEn", entity.titleEn)
            put("year", entity.year)
            put("plot", entity.plot)
            put("plotSource", entity.plotSource)
            put("plotConfidence", entity.plotConfidence)
            put("identifiedTitle", entity.identifiedTitle)
            put("mediaType", entity.mediaType)
            put("cast", entity.cast)
            put("director", entity.director)
            put("genre", entity.genre)
            put("runtime", entity.runtime)
            put("rating", entity.rating)
            put("posterUrl", entity.posterUrl)
            put("backdropUrl", entity.backdropUrl)
            put("tmdbId", entity.tmdbId)
            put("imdbId", entity.imdbId)
            put("tmdbVoteCount", entity.tmdbVoteCount)
            if (aliases.isNotEmpty()) put("aliases", aliases)
        }
        if (tmdbDocId != null) {
            collection.document(tmdbDocId).set(data, SetOptions.merge())
                .addOnCompleteListener { cont.resume(Unit) }
        } else {
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) {
                cont.resume(Unit)
                return@suspendCancellableCoroutine
            }
            db.collection("users").document(uid)
                .collection("custom_catalog")
                .document(entity.sahabId)
                .set(data, SetOptions.merge())
                .addOnCompleteListener { cont.resume(Unit) }
        }
    }

    suspend fun linkPendingAliasToTmdb(streamId: String, tmdbDocId: String, titleAr: String = "") {
        if (streamId.isBlank() || tmdbDocId.isBlank() || tmdbDocId == CatalogMovieEntity.idPending(streamId)) return
        val pendingId = CatalogMovieEntity.idPending(streamId)
        suspendCancellableCoroutine<Unit> { cont ->
            val pendingDoc = collection.document(pendingId)
            pendingDoc.get()
                .addOnSuccessListener { snap ->
                    val existingPlot = snap.getString("plot").orEmpty()
                    val tmdbRef = collection.document(tmdbDocId)
                    val mergePayload = buildMap<String, Any> {
                        put("aliases", com.google.firebase.firestore.FieldValue.arrayUnion(pendingId))
                        if (existingPlot.isNotBlank()) put("plot", existingPlot)
                        snap.getString("plotSource")?.takeIf { it.isNotBlank() }?.let { put("plotSource", it) }
                        snap.getString("identifiedTitle")?.takeIf { it.isNotBlank() }?.let { put("identifiedTitle", it) }
                    }
                    tmdbRef.set(mergePayload, SetOptions.merge())
                        .addOnCompleteListener {
                            pendingDoc.set(
                                mapOf("redirectTo" to tmdbDocId, "novaStreamId" to streamId, "titleAr" to titleAr),
                                SetOptions.merge(),
                            ).addOnCompleteListener { cont.resume(Unit) }
                        }
                }
                .addOnFailureListener { cont.resume(Unit) }
        }
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

    companion object {
        fun aliasesFor(entity: CatalogMovieEntity): List<String> {
            val streamId = entity.novaStreamId.trim()
            if (streamId.isEmpty()) return emptyList()
            val pendingId = CatalogMovieEntity.idPending(streamId)
            return if (entity.sahabId != pendingId) listOf(pendingId) else emptyList()
        }

        fun resolveRedirectId(
            docId: String,
            redirectTo: String?,
        ): String {
            val clean = redirectTo?.trim().orEmpty()
            return if (clean.isNotEmpty() && clean != docId) clean else docId
        }
    }
}
