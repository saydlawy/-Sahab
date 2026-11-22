package com.saydlawy.sahab.data.catalog

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.saydlawy.sahab.data.DEFAULT_VOD_EXT
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.saydlawy.sahab.util.RatingSanitizer
import kotlin.coroutines.resume

class FirebaseCatalogService {
    private val db = FirebaseFirestore.getInstance()
    private val collection = db.collection("global_catalog")
    private val metaDoc = db.collection("meta").document("catalog")

    /**
     * Single-read gate: fetches meta/catalog.lastUpdatedAt (1 Firestore read).
     * Returns -1L on network/quota error so callers do not trigger unnecessary syncs.
     */
    suspend fun getCatalogLastUpdatedAt(timeoutMs: Long = 10_000L): Long {
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Long> { cont ->
                metaDoc.get()
                    .addOnSuccessListener { snap ->
                        val ts = snap.getLong("lastUpdatedAt") ?: 0L
                        if (cont.isActive) cont.resume(ts)
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.w("SAHAB_CLOUD", "meta/catalog read failed: ${e.message}", e)
                        if (cont.isActive) cont.resume(-1L)
                    }
            }
        } ?: -1L
    }

    /**
     * Updates meta/catalog.lastUpdatedAt after a verified admin write.
     */
    suspend fun bumpCatalogMetaTimestamp(
        now: Long = System.currentTimeMillis(),
        timeoutMs: Long = 10_000L,
    ): Boolean {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != com.saydlawy.sahab.data.auth.AuthManager.ADMIN_UID) return false
        return withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val payload = mapOf(
                    "lastUpdatedAt" to now,
                    "updatedBy" to currentUid,
                )
                metaDoc.set(payload, SetOptions.merge())
                    .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                    .addOnFailureListener { e ->
                        android.util.Log.w("SAHAB_CLOUD", "meta/catalog update failed: ${e.message}", e)
                        if (cont.isActive) cont.resume(false)
                    }
            }
        } ?: false
    }

    @Volatile
    private var cachedRecommendedMovies: List<CatalogMovieEntity> = emptyList()

    fun peekCachedRecommendedMovies(): List<CatalogMovieEntity> = cachedRecommendedMovies

    /**
     * يسحب جميع مستندات كتالوج Firebase (`global_catalog`) على دفعات مرتبة بمعرّف المستند
     * ويمرر كل دفعة إلى [onBatch] لحفظها مباشرة في Room دون استهلاك مفرط للذاكرة.
     */
    data class BatchFetchOutcome(
        val fetched: Int,
        val completedCleanly: Boolean,
    )

    suspend fun fetchAllCatalogBatched(
        batchSize: Long = 400L,
        perBatchTimeoutMs: Long = 15_000L,
        sinceMs: Long = 0L,
        onBatch: suspend (items: List<CatalogMovieEntity>, totalFetched: Int) -> Unit,
    ): Int = fetchAllCatalogBatchedOutcome(batchSize, perBatchTimeoutMs, sinceMs, onBatch).fetched

    suspend fun fetchAllCatalogBatchedOutcome(
        batchSize: Long = 400L,
        perBatchTimeoutMs: Long = 15_000L,
        sinceMs: Long = 0L,
        onBatch: suspend (items: List<CatalogMovieEntity>, totalFetched: Int) -> Unit,
    ): BatchFetchOutcome {
        var lastDoc: DocumentSnapshot? = null
        var total = 0
        var completedCleanly = true
        while (true) {
            val snapshot = withTimeoutOrNull(perBatchTimeoutMs) {
                suspendCancellableCoroutine<com.google.firebase.firestore.QuerySnapshot?> { cont ->
                    var query = if (sinceMs > 0L) {
                        collection
                            .whereGreaterThan("updatedAt", sinceMs)
                            .orderBy("updatedAt")
                            .limit(batchSize)
                    } else {
                        collection
                            .orderBy(FieldPath.documentId())
                            .limit(batchSize)
                    }
                    if (lastDoc != null) {
                        query = query.startAfter(lastDoc!!)
                    }
                    query.get()
                        .addOnSuccessListener { snap -> if (cont.isActive) cont.resume(snap) }
                        .addOnFailureListener { if (cont.isActive) cont.resume(null) }
                }
            }
            if (snapshot == null) {
                completedCleanly = false
                break
            }

            val docs = snapshot.documents
            if (docs.isEmpty()) break

            val entities = docs.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val redirectTo = data["redirectTo"] as? String
                if (resolveRedirectId(doc.id, redirectTo) != doc.id) return@mapNotNull null
                runCatching { mapToEntity(doc.id, data) }.getOrNull()
            }
            total += entities.size
            if (entities.isNotEmpty()) {
                onBatch(entities, total)
            }
            lastDoc = docs.last()
            if (docs.size < batchSize) break
        }
        return BatchFetchOutcome(fetched = total, completedCleanly = completedCleanly)
    }

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
                        it.isNotBlank() && it != docId && it.startsWith("tmdb:")
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
     * PHASE 1 (INSTANT < 20ms): Returns the initial movies in default IPTV provider order
     * from Room or cached provider list without waiting for any network or Firestore round-trip.
     */
    suspend fun getInstantRecommendedMovies(
        repo: Repository,
        dao: CatalogMovieDao,
        limit: Int = 18,
    ): List<CatalogMovieEntity> = withContext(Dispatchers.IO) {
        if (cachedRecommendedMovies.isNotEmpty()) {
            return@withContext cachedRecommendedMovies.take(limit)
        }

        val roomOrdered = runCatching {
            dao.getPaged(mediaType = "movie", limit = limit * 2, offset = 0)
                .filter { it.isProviderItem && it.posterUrl.isNotBlank() }
                .take(limit)
        }.getOrNull().orEmpty()

        if (roomOrdered.isNotEmpty() && roomOrdered.any { it.providerOrder != Int.MAX_VALUE }) {
            cachedRecommendedMovies = roomOrdered
            return@withContext roomOrdered
        }

        val memMovies = repo.peekAllMovies()
            ?.asSequence()
            ?.filter { it.id.isNotBlank() && it.image.isNotBlank() }
            ?.take(limit)
            ?.toList()
            .orEmpty()

        if (memMovies.isNotEmpty()) {
            val streamIds = memMovies.map { it.id }
            val roomByStreamId = runCatching {
                dao.findByStreamIds(streamIds).associateBy { it.novaStreamId }
            }.getOrNull().orEmpty()

            val fastList = memMovies.map { poster ->
                mergeXtreamAndCatalog(
                    poster = poster,
                    room = roomByStreamId[poster.id],
                )
            }
            if (fastList.isNotEmpty()) {
                cachedRecommendedMovies = fastList
                return@withContext fastList
            }
        }

        if (roomOrdered.isNotEmpty()) {
            cachedRecommendedMovies = roomOrdered
        }
        roomOrdered
    }

    /**
     * PHASE 2: Selects movies strictly in the active IPTV provider's default order
     * and enriches them locally from Room (which holds synced TMDB / cloud metadata).
     */
    suspend fun getXtreamRecommendedMovies(
        repo: Repository,
        dao: CatalogMovieDao,
        limit: Int = 18,
    ): List<CatalogMovieEntity> = withContext(Dispatchers.IO) {
        val roomOrdered = runCatching {
            dao.getPaged(mediaType = "movie", limit = limit * 2, offset = 0)
                .filter { it.isProviderItem && it.posterUrl.isNotBlank() }
                .take(limit)
        }.getOrNull().orEmpty()

        if (roomOrdered.isNotEmpty() && roomOrdered.any { it.providerOrder != Int.MAX_VALUE }) {
            cachedRecommendedMovies = roomOrdered
            return@withContext roomOrdered
        }

        val xtreamMovies: List<Poster> = runCatching {
            (repo.peekAllMovies() ?: repo.movies(null))
                .asSequence()
                .filter { it.id.isNotBlank() && it.image.isNotBlank() }
                .take(limit)
                .toList()
        }.getOrNull().orEmpty()

        if (xtreamMovies.isEmpty()) {
            if (roomOrdered.isNotEmpty()) cachedRecommendedMovies = roomOrdered
            return@withContext roomOrdered
        }

        val candidateIds = xtreamMovies.map { it.id }
        val roomByStreamId = runCatching {
            dao.findByStreamIds(candidateIds).associateBy { it.novaStreamId }
        }.getOrNull().orEmpty()

        val finalResults = xtreamMovies.map { poster ->
            mergeXtreamAndCatalog(
                poster = poster,
                room = roomByStreamId[poster.id],
            )
        }

        if (finalResults.isNotEmpty()) {
            cachedRecommendedMovies = finalResults
        }
        finalResults
    }

    private fun mergeXtreamAndCatalog(
        poster: Poster,
        room: CatalogMovieEntity?,
    ): CatalogMovieEntity {
        val cleanXtreamName = cleanDisplayTitle(poster.name)
        val bestTitle = room?.titleEn?.takeIf { it.isNotBlank() && anyArabic(it) }
            ?: cleanXtreamName.ifBlank { poster.name }
        val bestRating = RatingSanitizer.format(room?.rating, room?.tmdbVoteCount)
            .ifBlank { RatingSanitizer.format(poster.rating) }
        val bestYear = room?.year?.ifBlank { null } ?: poster.year
        val bestGenre = room?.genre?.ifBlank { null } ?: ""
        val bestPlot = room?.plot.orEmpty()
        val bestPoster = poster.image.ifBlank {
            room?.backdropUrl?.ifBlank { null }
                ?: room?.posterUrl.orEmpty()
        }

        return CatalogMovieEntity(
            sahabId = room?.sahabId ?: CatalogMovieEntity.idPending(poster.id),
            novaStreamId = poster.id,
            categoryId = poster.categoryId,
            titleAr = bestTitle,
            titleEn = room?.titleEn.orEmpty(),
            year = bestYear,
            plot = bestPlot,
            genre = bestGenre,
            runtime = room?.runtime.orEmpty(),
            rating = bestRating,
            posterUrl = bestPoster,
            backdropUrl = room?.backdropUrl.orEmpty(),
            novaAdded = poster.added,
            containerExtension = poster.ext.ifBlank { DEFAULT_VOD_EXT },
            tmdbVoteCount = room?.tmdbVoteCount,
            needsEnrichment = room?.needsEnrichment ?: 1,
        )
    }

    private fun anyArabic(s: String): Boolean = s.any { it in '\u0600'..'\u06FF' }

    private fun cleanDisplayTitle(raw: String): String {
        return raw
            .replace(LEADING_BRACKETS_REGEX, "")
            .replace(TRAILING_BRACKETS_REGEX, "")
            .trim()
    }

    suspend fun saveCatalogItem(entity: CatalogMovieEntity) {
        saveCatalogItemVerified(entity)
    }

    /**
     * Writes an enriched/edited catalog item to Firestore and returns true ONLY on confirmed success.
     * Used by the local push queue so needsCloudPush is never cleared on quota/network failure.
     */
    @Volatile
    var lastWriteError: String = ""
        private set

    suspend fun saveCatalogItemVerified(
        entity: CatalogMovieEntity,
        bumpMeta: Boolean = true,
    ): Boolean {
        lastWriteError = ""
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentUid != com.saydlawy.sahab.data.auth.AuthManager.ADMIN_UID) {
            lastWriteError = "الحساب الحالي ليس حساب الأدمن (uid=${currentUid ?: "null"})"
            return false
        }
        val tmdbDocId = entity.tmdbId?.takeIf { it > 0 }?.let { CatalogMovieEntity.idFromTmdb(it) }
        val targetDocId = tmdbDocId ?: entity.sahabId
        if (!targetDocId.startsWith("tmdb:")) {
            lastWriteError = "العنصر غير مرتبط بـ TMDB ($targetDocId)"
            return false
        }
        val writeTs = System.currentTimeMillis()
        val ok = suspendCancellableCoroutine<Boolean> { cont ->
        val aliases = aliasesFor(entity).take(10)
        val normalizedMediaType = if (entity.mediaType == "series") "series" else "movie"
        val data = buildMap<String, Any?> {
            put("novaStreamId", entity.novaStreamId)
            put("titleAr", entity.titleAr)
            put("titleEn", entity.titleEn)
            put("year", entity.year)
            put("plot", entity.plot)
            put("plotSource", entity.plotSource)
            put("plotConfidence", entity.plotConfidence)
            put("identifiedTitle", entity.identifiedTitle)
            put("mediaType", normalizedMediaType)
            put("cast", entity.cast)
            put("castJson", entity.castJson)
            put("director", entity.director)
            put("genre", entity.genre)
            put("runtime", entity.runtime)
            put("rating", entity.rating)
            put("posterUrl", entity.posterUrl)
            put("backdropUrl", entity.backdropUrl)
            put("postersJson", entity.postersJson)
            put("trailerUrl", entity.trailerUrl)
            put("tmdbId", entity.tmdbId)
            put("imdbId", entity.imdbId)
            put("tmdbVoteCount", entity.tmdbVoteCount)
            if (aliases.isNotEmpty()) put("aliases", aliases)
        }
        val docRef = collection.document(targetDocId)
        docRef.get()
            .addOnSuccessListener { snap ->
                if (!snap.exists()) {
                    val createPayload = data.filterValues { v ->
                        v != null && (v !is String || v.isNotBlank())
                    } + ("updatedAt" to System.currentTimeMillis())
                    docRef.set(createPayload, SetOptions.merge())
                        .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                        .addOnFailureListener { e ->
                            lastWriteError = "create [$targetDocId]: ${e.message ?: e::class.java.simpleName}"
                            android.util.Log.w("SAHAB_CLOUD", "saveCatalogItem create failed [$targetDocId]: ${e.message}", e)
                            if (cont.isActive) cont.resume(false)
                        }
                } else {
                    val delta = mutableMapOf<String, Any?>()
                    for ((k, v) in data) {
                        if (k == "aliases") continue
                        if (v == null || (v is String && v.isBlank())) continue
                        val existing = snap.get(k)
                        if (existing == null || (existing is String && existing.isBlank())) {
                            delta[k] = v
                        }
                    }
                    if (aliases.isNotEmpty()) {
                        delta["aliases"] = FieldValue.arrayUnion(*aliases.toTypedArray())
                    }
                    if (delta.isEmpty()) {
                        if (cont.isActive) cont.resume(true)
                    } else {
                        delta["updatedAt"] = writeTs
                        docRef.set(delta, SetOptions.merge())
                            .addOnSuccessListener { if (cont.isActive) cont.resume(true) }
                            .addOnFailureListener { e ->
                                lastWriteError = "update [$targetDocId]: ${e.message ?: e::class.java.simpleName}"
                                android.util.Log.w("SAHAB_CLOUD", "saveCatalogItem update failed [$targetDocId]: ${e.message}", e)
                                if (cont.isActive) cont.resume(false)
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                lastWriteError = "get [$targetDocId]: ${e.message ?: e::class.java.simpleName}"
                android.util.Log.w("SAHAB_CLOUD", "saveCatalogItem get failed [$targetDocId]: ${e.message}", e)
                if (cont.isActive) cont.resume(false)
            }
        }
        if (ok && bumpMeta) {
            bumpCatalogMetaTimestamp(writeTs)
        }
        return ok
    }

    suspend fun linkPendingAliasToTmdb(
        streamId: String,
        tmdbDocId: String,
        titleAr: String = "",
        mediaType: String = "movie",
    ) {
        val pendingId = CatalogMovieEntity.idPending(streamId, mediaType)
        if (streamId.isBlank() || tmdbDocId.isBlank() || tmdbDocId == pendingId) return
        if (!tmdbDocId.startsWith("tmdb:")) return
        val uid = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (uid.isNullOrBlank()) return
        val isAdmin = uid == com.saydlawy.sahab.data.auth.AuthManager.ADMIN_UID
        suspendCancellableCoroutine<Unit> { cont ->
            val writePrivateRedirect = {
                db.collection("users").document(uid)
                    .collection("custom_catalog")
                    .document(pendingId)
                    .set(
                        mapOf("redirectTo" to tmdbDocId, "novaStreamId" to streamId, "titleAr" to titleAr),
                        SetOptions.merge(),
                    )
                    .addOnCompleteListener { if (cont.isActive) cont.resume(Unit) }
            }
            if (isAdmin) {
                val tmdbRef = collection.document(tmdbDocId)
                val mergePayload = mapOf(
                    "aliases" to com.google.firebase.firestore.FieldValue.arrayUnion(pendingId),
                )
                tmdbRef.set(mergePayload, SetOptions.merge())
                    .addOnCompleteListener { writePrivateRedirect() }
            } else {
                writePrivateRedirect()
            }
        }
    }

    private fun mapToEntity(id: String, data: Map<String, Any>): CatalogMovieEntity {
        val voteCount = (data["tmdbVoteCount"] as? Number)?.toInt()
        val titleAr = data["titleAr"] as? String ?: ""
        val titleEn = data["titleEn"] as? String ?: ""
        val identified = data["identifiedTitle"] as? String ?: ""
        return CatalogMovieEntity(
            sahabId = id,
            novaStreamId = "",
            titleAr = titleAr,
            titleEn = titleEn,
            year = data["year"] as? String ?: "",
            plot = data["plot"] as? String ?: "",
            plotSource = data["plotSource"] as? String ?: "",
            plotConfidence = data["plotConfidence"] as? String ?: "",
            identifiedTitle = identified,
            mediaType = data["mediaType"] as? String ?: "movie",
            cast = data["cast"] as? String ?: "",
            castJson = data["castJson"] as? String ?: "",
            director = data["director"] as? String ?: "",
            genre = data["genre"] as? String ?: "",
            runtime = data["runtime"] as? String ?: "",
            rating = RatingSanitizer.format(data["rating"] as? String, voteCount),
            posterUrl = data["posterUrl"] as? String ?: "",
            backdropUrl = data["backdropUrl"] as? String ?: "",
            postersJson = data["postersJson"] as? String ?: "",
            trailerUrl = data["trailerUrl"] as? String ?: "",
            imdbId = data["imdbId"] as? String ?: "",
            tmdbId = (data["tmdbId"] as? Number)?.toInt(),
            tmdbVoteCount = voteCount,
            needsEnrichment = 0,
            lastEnriched = System.currentTimeMillis(),
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L,
            searchTitle = buildCatalogSearchTitle(titleAr, titleEn, identified),
        )
    }

    companion object {
        private val LEADING_BRACKETS_REGEX = Regex("""^\[.*?]\s*""")
        private val TRAILING_BRACKETS_REGEX = Regex("""\s*\[.*?]$""")

        fun aliasesFor(entity: CatalogMovieEntity): List<String> {
            val streamId = entity.novaStreamId.trim()
            if (streamId.isEmpty()) return emptyList()
            val pendingId = CatalogMovieEntity.idPending(streamId, entity.mediaType)
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
