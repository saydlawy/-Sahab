package com.saydlawy.sahab.data.channel

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Fetches EPG programmes from Firebase `global_epg/{epgId}` for all channels
 * that have an epgId in Room, upserts them into Room, and prunes old entries.
 *
 * Firebase cost & performance:
 *  - Only queries epgIds that actually exist in the local channels table.
 *  - Uses whereIn(FieldPath.documentId(), ...) — 30 IDs per query.
 *  - ~249 epgIds → ~9 queries instead of 249.
 *  - Prunes programmes ending more than 1h ago (keeps the "just ended" row visible).
 *
 * Failure policy: NEVER fail silently. All errors captured in Stats.errors + Log.e.
 */
class EpgSync(
    private val dao: ChannelDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    data class Stats(
        val epgIdsProcessed: Int = 0,
        val programsUpserted: Int = 0,
        val programsDeleted: Int = 0,
        val failedBatches: Int = 0,
        val errors: List<String> = emptyList(),
        val durationMs: Long = 0L,
    ) {
        val isSuccess: Boolean get() = errors.isEmpty()
        val hasPartialFailure: Boolean get() = errors.isNotEmpty() && programsUpserted > 0
    }

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    suspend fun sync(): Stats = withContext(Dispatchers.IO) {
        if (!_syncing.compareAndSet(expect = false, update = true)) {
            return@withContext Stats(errors = listOf("EPG sync already in progress — ignored"))
        }
        val start = System.currentTimeMillis()
        val errors = mutableListOf<String>()
        try {
            val nowSec = start / 1000
            val cutoffSec = nowSec - 3600

            // 1) Distinct epgIds from channels (populated by ChannelSync)
            val epgIds = try {
                dao.distinctEpgIds()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read epgIds from Room", e)
                return@withContext Stats(
                    errors = listOf("Room read failed: ${e.message ?: e.javaClass.simpleName}"),
                    durationMs = System.currentTimeMillis() - start,
                )
            }
            if (epgIds.isEmpty()) {
                Log.w(TAG, "No epgIds — run ChannelSync first")
                return@withContext Stats(
                    errors = listOf("No channels with epgId — run ChannelSync first"),
                    durationMs = System.currentTimeMillis() - start,
                )
            }

            // 2) Fetch from Firebase
            val fetchResult = fetchFirebase(epgIds, cutoffSec)
            errors.addAll(fetchResult.errors)

            // 3) Upsert programmes into Room (batched)
            var upserted = 0
            try {
                fetchResult.entities.chunked(ROOM_BATCH_SIZE).forEach { batch ->
                    dao.upsertPrograms(batch)
                    upserted += batch.size
                }
            } catch (e: Exception) {
                Log.e(TAG, "Room program upsert failed", e)
                errors.add("Room program upsert failed: ${e.message ?: e.javaClass.simpleName}")
            }

            // 4) Prune old programmes
            var deleted = 0
            try {
                deleted = dao.deleteOldPrograms(cutoffSec)
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup failed", e)
                errors.add("Cleanup failed: ${e.message ?: e.javaClass.simpleName}")
            }

            Stats(
                epgIdsProcessed = epgIds.size,
                programsUpserted = upserted,
                programsDeleted = deleted,
                failedBatches = fetchResult.failedBatches,
                errors = errors.toList(),
                durationMs = System.currentTimeMillis() - start,
            )
        } finally {
            _syncing.value = false
        }
    }

    // ─────────────────────────────────────────────────────────
    // Firebase
    // ─────────────────────────────────────────────────────────

    private data class FirebaseFetchResult(
        val entities: List<EpgProgramEntity>,
        val failedBatches: Int,
        val errors: List<String>,
    )

    private suspend fun fetchFirebase(
        epgIds: List<String>,
        cutoffSec: Long,
    ): FirebaseFetchResult {
        val out = mutableListOf<EpgProgramEntity>()
        val errors = mutableListOf<String>()
        var failedBatches = 0

        epgIds.chunked(WHERE_IN_LIMIT).forEachIndexed { index, chunk ->
            try {
                val docs = queryByIds(chunk)
                docs.forEach { doc ->
                    out.addAll(docToPrograms(doc, cutoffSec))
                }
            } catch (e: Exception) {
                failedBatches++
                val msg = "Firebase epg query #${index + 1} (${chunk.size} ids): " +
                    (e.message ?: e.javaClass.simpleName)
                Log.e(TAG, msg, e)
                errors.add(msg)
            }
        }

        return FirebaseFetchResult(out, failedBatches, errors)
    }

    private suspend fun queryByIds(epgIds: List<String>): List<DocumentSnapshot> =
        suspendCancellableCoroutine { cont ->
            firestore.collection("global_epg")
                .whereIn(FieldPath.documentId(), epgIds)
                .get()
                .addOnSuccessListener { snap ->
                    if (cont.isActive) cont.resume(snap.documents)
                }
                .addOnFailureListener { e ->
                    if (cont.isActive) cont.resumeWithException(e)
                }
        }

    private fun docToPrograms(doc: DocumentSnapshot, cutoffSec: Long): List<EpgProgramEntity> {
        if (!doc.exists()) return emptyList()
        val d = doc.data ?: return emptyList()
        val epgId = doc.id
        @Suppress("UNCHECKED_CAST")
        val raw = d["programs"] as? List<Map<String, Any?>> ?: return emptyList()
        val now = System.currentTimeMillis()
        val result = ArrayList<EpgProgramEntity>(raw.size)
        raw.forEach { m ->
            val startSec = (m["start"] as? Number)?.toLong() ?: return@forEach
            val endSec = (m["end"] as? Number)?.toLong() ?: return@forEach
            if (endSec < cutoffSec) return@forEach
            val title = m["title"] as? String ?: ""
            if (title.isBlank()) return@forEach
            val desc = m["description"] as? String ?: ""
            result.add(
                EpgProgramEntity(
                    id = "${epgId}_$startSec",
                    epgId = epgId,
                    start = startSec,
                    end = endSec,
                    title = title,
                    description = desc,
                    category = null,
                    updatedAt = now,
                )
            )
        }
        return result
    }

    companion object {
        private const val TAG = "EpgSync"
        private const val ROOM_BATCH_SIZE = 500
        private const val WHERE_IN_LIMIT = 30
    }
}
