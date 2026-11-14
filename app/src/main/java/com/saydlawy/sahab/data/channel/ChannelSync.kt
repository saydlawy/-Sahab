package com.saydlawy.sahab.data.channel

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
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

/**
 * Syncs the user's Xtream live channels into Room (channel.db),
 * enriched with Firebase `global_channels` metadata.
 *
 * Failure policy: NEVER fail silently.
 *  - All errors are captured in `Stats.errors` AND logged with Log.e.
 *  - Partial failures (some Firebase batches fail) do not abort the whole sync;
 *    they are reported in `Stats.failedBatches` and `Stats.hasPartialFailure`.
 *  - Full failures (Xtream empty, Room write fails) return early with a clear error.
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
            if (_syncing.value) {
                return@withContext Stats(
                    errors = listOf("Sync already in progress — ignored"),
                )
            }
            _syncing.value = true
            val start = System.currentTimeMillis()
            val errors = mutableListOf<String>()
            try {
                val now = System.currentTimeMillis()

                // ─── Step 1: Xtream fetch (surface failures, don't swallow) ───
                val remote = try {
                    repo.liveChannels(null)
                } catch (e: Exception) {
                    Log.e(TAG, "liveChannels failed", e)
                    return@withContext Stats(
                        errors = listOf("Xtream liveChannels failed: ${e.message ?: e.javaClass.simpleName}"),
                        durationMs = System.currentTimeMillis() - start,
                    )
                }
                if (remote.isEmpty()) {
                    Log.w(TAG, "liveChannels returned 0 channels")
                    return@withContext Stats(
                        errors = listOf("Xtream returned 0 channels — check subscription/credentials"),
                        durationMs = System.currentTimeMillis() - start,
                    )
                }

                // ─── Step 2: Snapshot Room (our cache) ───
                val existing = try {
                    dao.all().associateBy { it.streamId }
                } catch (e: Exception) {
                    Log.e(TAG, "Room read failed", e)
                    return@withContext Stats(
                        errors = listOf("Room read failed: ${e.message ?: e.javaClass.simpleName}"),
                        durationMs = System.currentTimeMillis() - start,
                    )
                }

                // ─── Step 3: Decide which channels need Firebase ───
                val needEnrich = if (forceFullRefresh) remote
                else remote.filter { ch -> existing[ch.id]?.epgId.isNullOrEmpty() }

                // ─── Step 4: Firebase fetch (collect partial failures) ───
                val fbMap: Map<String, FirebaseChannelData>
                var failedBatches = 0
                if (needEnrich.isNotEmpty()) {
                    val result = fetchFirebase(needEnrich)
                    fbMap = result.data
                    failedBatches = result.failedBatches
                    errors.addAll(result.errors)
                } else {
                    fbMap = emptyMap()
                }

                // ─── Step 5: Build entity rows ───
                val rows = remote.mapIndexed { idx, ch ->
                    buildEntity(ch, idx, fbMap[slug(ch.name)], existing[ch.id], now)
                }

                // ─── Step 6: Room upsert (surface write errors) ───
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
                } catch (e: Exception) {
                    Log.e(TAG, "Room upsert failed", e)
                    errors.add("Room write failed: ${e.message ?: e.javaClass.simpleName}")
                }

                Stats(
                    total = rows.size,
                    inserted = inserted,
                    updated = updated,
                    enriched = enriched,
                    firebaseReads = fbMap.size,
                    failedBatches = failedBatches,
                    errors = errors.toList(),
                    durationMs = System.currentTimeMillis() - start,
                )
            } finally {
                _syncing.value = false
            }
        }

    // ─────────────────────────────────────────────────────────
    // Firebase (batch reads via getAll — 300 per call for safety)
    // ─────────────────────────────────────────────────────────

    private data class FirebaseFetchResult(
        val data: Map<String, FirebaseChannelData>,
        val failedBatches: Int,
        val errors: List<String>,
    )

    private suspend fun fetchFirebase(channels: List<Channel>): FirebaseFetchResult {
        val slugs = channels.map { slug(it.name) }.filter { it.isNotEmpty() }.distinct()
        if (slugs.isEmpty()) return FirebaseFetchResult(emptyMap(), 0, emptyList())

        val result = mutableMapOf<String, FirebaseChannelData>()
        val errors = mutableListOf<String>()
        var failedBatches = 0

        slugs.chunked(FIREBASE_BATCH_SIZE).forEachIndexed { index, chunk ->
            when (val r = readDocsBatch(chunk)) {
                is Result.Success -> result.putAll(r.getOrNull().orEmpty())
                is Result.Failure -> {
                    failedBatches++
                    val msg = "Firebase batch #${index + 1} (${chunk.size} docs) failed: " +
                        (r.exceptionOrNull()?.message ?: "unknown error")
                    Log.e(TAG, msg, r.exceptionOrNull())
                    errors.add(msg)
                }
            }
        }

        return FirebaseFetchResult(
            data = result,
            failedBatches = failedBatches,
            errors = errors,
        )
    }

    private suspend fun readDocsBatch(slugs: List<String>): Result<Map<String, FirebaseChannelData>> =
        suspendCancellableCoroutine { cont ->
            try {
                val refs = slugs.map { firestore.collection("global_channels").document(it) }
                firestore.getAll(*refs.toTypedArray())
                    .addOnSuccessListener { snapshot ->
                        val out = mutableMapOf<String, FirebaseChannelData>()
                        snapshot.documents.forEach { doc ->
                            val data = docToFirebaseData(doc) ?: return@forEach
                            out[doc.id] = data
                        }
                        cont.resume(Result.success(out))
                    }
                    .addOnFailureListener { e ->
                        cont.resume(Result.failure(e))
                    }
            } catch (e: Exception) {
                cont.resume(Result.failure(e))
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
    // Entity builder
    // ─────────────────────────────────────────────────────────

    private fun buildEntity(
        ch: Channel,
        listIndex: Int,
        fb: FirebaseChannelData?,
        prev: ChannelEntity?,
        now: Long,
    ): ChannelEntity = ChannelEntity(
        streamId = ch.id,
        name = ch.name,
        displayName = fb?.displayName?.takeIf { it.isNotBlank() } ?: ch.name,
        logoUrl = fb?.logoUrl?.takeIf { it.isNotBlank() } ?: ch.icon,
        categoryId = ch.categoryId,
        categoryName = fb?.categoryName?.takeIf { it.isNotBlank() } ?: "",
        channelNumber = fb?.channelNumber,
        epgId = fb?.epgId?.takeIf { it.isNotBlank() } ?: ch.epgId,
        qualityTag = fb?.qualityTag ?: "",
        groupId = fb?.groupId ?: "",
        streamUrlTemplate = STREAM_TEMPLATE,
        sortOrder = prev?.sortOrder ?: (fb?.channelNumber ?: listIndex),
        isFavorite = prev?.isFavorite ?: false,
        isHidden = prev?.isHidden ?: false,
        lastWatchedAt = prev?.lastWatchedAt ?: 0L,
        updatedAt = now,
    )

    companion object {
        private const val TAG = "ChannelSync"
        private const val ROOM_BATCH_SIZE = 500
        private const val FIREBASE_BATCH_SIZE = 300   // safer than 500
        private const val STREAM_TEMPLATE = "/live/{USER}/{PASS}/{streamId}.{ext}"

        /**
         * MUST match `slug()` in `uploader/scripts/update-epg.js` and `import_channels.js`.
         */
        fun slug(name: String): String = name.lowercase(Locale.US).trim()
            .replace(Regex("[^\\w\\u0600-\\u06FF]+"), "_")
            .replace(Regex("^_+|_+$"), "")
            .replace(Regex("_+"), "_")
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
