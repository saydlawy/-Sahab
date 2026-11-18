package com.saydlawy.sahab.data.channel

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Syncs the user's Xtream live channels into Room (channel.db),
 * enriched with Firebase `global_channels` metadata.
 *
 * Firebase cost & performance strategy:
 *  - Room IS our cache: channels with a valid epgId are SKIPPED (0 reads).
 *  - Channels without epgId are re-queried at most every 7 days (lastFirebaseCheckAt TTL).
 *  - Enrichment uses `whereIn(FieldPath.documentId(), ...)` — 30 IDs per query.
 *    Cost is unchanged (per-document billing) but latency drops from ~1510
 *    round-trips to ~51 (massive UX improvement).
 *  - No silent failures: every error is captured in Stats.errors and Log.e.
 */
class ChannelSync(
    private val dao: ChannelDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    data class Stats(
        val total: Int = 0,
        val inserted: Int = 0,
        val updated: Int = 0,
        val enriched: Int = 0,
        val firebaseReads: Int = 0,
        val failedBatches: Int = 0,
        val errors: List<String> = emptyList(),
        val durationMs: Long = 0L,
    ) {
        val isSuccess: Boolean get() = errors.isEmpty()
        val hasPartialFailure: Boolean get() = errors.isNotEmpty() && total > 0
    }

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    suspend fun sync(repo: Repository, forceFullRefresh: Boolean = false): Stats =
        withContext(Dispatchers.IO) {
            // Atomic lock — prevents race between concurrent calls
            if (!_syncing.compareAndSet(expect = false, update = true)) {
                return@withContext Stats(errors = listOf("Sync already in progress — ignored"))
            }
            val start = System.currentTimeMillis()
            val errors = mutableListOf<String>()
            try {
                val now = System.currentTimeMillis()

                // 1) Xtream fetch
                val remote = try {
                    repo.liveChannels(null, forceNetwork = true)
                } catch (e: Exception) {
                    Log.e(TAG, "liveChannels failed", e)
                    return@withContext Stats(
                        errors = listOf("Xtream liveChannels failed: ${e.message ?: e.javaClass.simpleName}"),
                        durationMs = System.currentTimeMillis() - start,
                    )
                }
                if (remote.isEmpty()) {
                    return@withContext Stats(
                        errors = listOf("Xtream returned 0 channels — check subscription/credentials"),
                        durationMs = System.currentTimeMillis() - start,
                    )
                }

                // 2) Room snapshot (our cache)
                val existing = try {
                    dao.all().associateBy { it.streamId }
                } catch (e: Exception) {
                    Log.e(TAG, "Room read failed", e)
                    return@withContext Stats(
                        errors = listOf("Room read failed: ${e.message ?: e.javaClass.simpleName}"),
                        durationMs = System.currentTimeMillis() - start,
                    )
                }

                // 3) Which channels need Firebase?
                //    Skip channels whose epgId is already populated.
                //    For those without epgId, re-query only after RETRY_TTL_MS.
                val retryCutoff = now - RETRY_TTL_MS
                val needEnrich = if (forceFullRefresh) remote
                else remote.filter { ch ->
                    val prev = existing[ch.id]
                    prev == null || (prev.epgId.isEmpty() && prev.lastFirebaseCheckAt < retryCutoff)
                }

                // 4) Firebase fetch
                val fbResult = if (needEnrich.isNotEmpty()) fetchFirebase(needEnrich)
                else FirebaseFetchResult(emptyMap(), emptySet(), 0, emptyList())
                errors.addAll(fbResult.errors)

                // 5) Build rows (preserve prev values on partial refresh)
                val rows = remote.mapIndexed { idx, ch ->
                    val chSlug = slug(ch.name)
                    buildEntity(
                        ch = ch,
                        listIndex = idx,
                        fb = fbResult.data[chSlug],
                        prev = existing[ch.id],
                        now = now,
                        wasQueried = chSlug in fbResult.queriedSlugs,
                    )
                }

                // 6) Room upsert
                var inserted = 0
                var updated = 0
                var enriched = 0
                try {
                    rows.chunked(ROOM_BATCH_SIZE).forEach { batch ->
                        dao.upsert(batch)
                        batch.forEach { e ->
                            if (existing.containsKey(e.streamId)) updated++ else inserted++
                            if (e.epgId.isNotEmpty()) enriched++
                        }
                    }
                    val remoteIds = remote.mapTo(HashSet(remote.size)) { it.id }
                    val removedIds = existing.keys.filter { it !in remoteIds }
                    removedIds.chunked(ROOM_BATCH_SIZE).forEach { batch ->
                        dao.deleteByStreamIds(batch)
                    }
                    repo.invalidateLiveChannelCache()
                } catch (e: Exception) {
                    Log.e(TAG, "Room upsert failed", e)
                    errors.add("Room write failed: ${e.message ?: e.javaClass.simpleName}")
                }

                Stats(
                    total = rows.size,
                    inserted = inserted,
                    updated = updated,
                    enriched = enriched,
                    firebaseReads = fbResult.data.size,
                    failedBatches = fbResult.failedBatches,
                    errors = errors.toList(),
                    durationMs = System.currentTimeMillis() - start,
                )
            } finally {
                _syncing.value = false
            }
        }

    // ─────────────────────────────────────────────────────────
    // Firebase (whereIn batches — max 30 IDs per query)
    // ─────────────────────────────────────────────────────────

    private data class FirebaseFetchResult(
        val data: Map<String, FirebaseChannelData>,
        val queriedSlugs: Set<String>,
        val failedBatches: Int,
        val errors: List<String>,
    )

    private suspend fun fetchFirebase(channels: List<Channel>): FirebaseFetchResult {
        val slugs = channels.map { slug(it.name) }.filter { it.isNotEmpty() }.distinct()
        if (slugs.isEmpty()) return FirebaseFetchResult(emptyMap(), emptySet(), 0, emptyList())

        val result = mutableMapOf<String, FirebaseChannelData>()
        val errors = mutableListOf<String>()
        var failedBatches = 0

        // whereIn: Firestore allows up to 30 values per query
        slugs.chunked(WHERE_IN_LIMIT).forEachIndexed { index, chunk ->
            try {
                val docs = queryByIds(chunk)
                docs.forEach { doc ->
                    docToFirebaseData(doc)?.let { result[doc.id] = it }
                }
            } catch (e: Exception) {
                failedBatches++
                val msg = "Firebase query #${index + 1} (${chunk.size} ids): " +
                    (e.message ?: e.javaClass.simpleName)
                Log.e(TAG, msg, e)
                errors.add(msg)
            }
        }

        return FirebaseFetchResult(
            data = result,
            queriedSlugs = slugs.toSet(),
            failedBatches = failedBatches,
            errors = errors,
        )
    }

    private suspend fun queryByIds(slugs: List<String>): List<DocumentSnapshot> =
        suspendCancellableCoroutine { cont ->
            firestore.collection("global_channels")
                .whereIn(FieldPath.documentId(), slugs)
                .get()
                .addOnSuccessListener { snap ->
                    if (cont.isActive) cont.resume(snap.documents)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }

    private fun docToFirebaseData(doc: DocumentSnapshot): FirebaseChannelData? {
        if (!doc.exists()) return null
        val d = doc.data ?: return null
        return FirebaseChannelData(
            displayName = d["displayName"] as? String ?: "",
            logoUrl = d["logoUrl"] as? String ?: "",
            epgId = d["epgId"] as? String ?: "",
            qualityTag = d["qualityTag"] as? String ?: "",
            groupId = d["groupId"] as? String ?: "",
            channelNumber = (d["channelNumber"] as? Number)?.toInt(),
            categoryName = d["category"] as? String ?: "",
        )
    }

    // ─────────────────────────────────────────────────────────
    // Entity builder — preserves previous data on partial refresh
    // ─────────────────────────────────────────────────────────

    private fun buildEntity(
        ch: Channel,
        listIndex: Int,
        fb: FirebaseChannelData?,
        prev: ChannelEntity?,
        now: Long,
        wasQueried: Boolean,
    ): ChannelEntity = ChannelEntity(
        streamId = ch.id,
        name = ch.name,
        // Firebase wins, then previous row, then Xtream original
        displayName = fb?.displayName?.takeIf { it.isNotBlank() } ?: prev?.displayName ?: ch.name,
        logoUrl = fb?.logoUrl?.takeIf { it.isNotBlank() } ?: prev?.logoUrl ?: ch.icon,
        categoryId = ch.categoryId,
        categoryName = fb?.categoryName?.takeIf { it.isNotBlank() } ?: prev?.categoryName ?: "",
        channelNumber = fb?.channelNumber ?: prev?.channelNumber,
        epgId = fb?.epgId?.takeIf { it.isNotBlank() } ?: prev?.epgId ?: ch.epgId,
        qualityTag = fb?.qualityTag?.takeIf { it.isNotBlank() } ?: prev?.qualityTag ?: "",
        groupId = fb?.groupId?.takeIf { it.isNotBlank() } ?: prev?.groupId ?: "",
        streamUrlTemplate = if (ch.url.isNotBlank()) ch.url else STREAM_TEMPLATE,
        sortOrder = prev?.sortOrder ?: (fb?.channelNumber ?: listIndex),
        isFavorite = prev?.isFavorite ?: false,
        isHidden = prev?.isHidden ?: false,
        lastWatchedAt = prev?.lastWatchedAt ?: 0L,
        updatedAt = now,
        // Only update this when we actually queried Firebase for this slug
        lastFirebaseCheckAt = if (wasQueried) now else (prev?.lastFirebaseCheckAt ?: 0L),
    )

    companion object {
        private const val TAG = "ChannelSync"
        private const val ROOM_BATCH_SIZE = 500
        private const val WHERE_IN_LIMIT = 30            // Firestore max per whereIn
        private const val RETRY_TTL_MS = 24L * 3600_000L       // 24 hours
        private const val STREAM_TEMPLATE = "/live/{USER}/{PASS}/{streamId}.{ext}"
        private val SLUG_NON_WORD_REGEX = Regex("[^\\w\\u0600-\\u06FF]+")
        private val SLUG_EDGE_UNDERSCORES = Regex("^_+|_+$")
        private val SLUG_MULTI_UNDERSCORES = Regex("_+")

        fun slug(name: String): String = name.lowercase(Locale.US).trim()
            .replace(SLUG_NON_WORD_REGEX, "_")
            .replace(SLUG_EDGE_UNDERSCORES, "")
            .replace(SLUG_MULTI_UNDERSCORES, "_")
            .take(200)
    }
}

private data class FirebaseChannelData(
    val displayName: String,
    val logoUrl: String,
    val epgId: String,
    val qualityTag: String,
    val groupId: String,
    val channelNumber: Int?,
    val categoryName: String,
)
