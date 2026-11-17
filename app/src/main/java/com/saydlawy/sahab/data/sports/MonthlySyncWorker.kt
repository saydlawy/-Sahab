package com.saydlawy.sahab.data.sports

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * WorkManager worker that syncs sports data from Firestore (`global_sports`) into Room.
 */
class MonthlySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(SyncScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val force = inputData.getBoolean(KEY_FORCE, false)
        val singleLeagueId = inputData.getInt(KEY_LEAGUE_ID, 0)

        val now = System.currentTimeMillis()
        val lastSync = prefs.getLong(SyncScheduler.KEY_LAST_SYNC_MS, 0L)
        val dao = SportsDatabase.getInstance(applicationContext).matchDao()
        val isEmptyDb = dao.getAllMatches().isEmpty()

        if (!force && !isEmptyDb && lastSync > 0L && (now - lastSync) < SyncScheduler.MIN_INTERVAL_MS) {
            SyncScheduler.scheduleMonthlySync(applicationContext)
            return@withContext Result.success()
        }

        val firestore = FirebaseFirestore.getInstance()
        val docs: List<DocumentSnapshot> = try {
            if (singleLeagueId > 0 && !isEmptyDb) {
                listOf(fetchDoc(firestore, singleLeagueId.toString()))
            } else {
                fetchAllDocs(firestore)
            }
        } catch (e: Exception) {
            android.util.Log.e("MonthlySyncWorker", "Firestore global_sports fetch failed: ${e.message}")
            return@withContext if (!force) Result.retry() else Result.failure()
        }

        var successCount = 0
        for (doc in docs) {
            if (!doc.exists()) continue
            val leagueId = (doc.getLong("leagueId") ?: doc.id.toLongOrNull())?.toInt() ?: continue
            val matches = parseMatches(doc, leagueId, now)
            val standings = parseStandings(doc, leagueId)

            if (matches.isNotEmpty()) {
                dao.deleteMatchesByLeague(leagueId)
                dao.insertMatches(matches)
            }
            if (standings.isNotEmpty()) {
                dao.deleteStandingsByLeague(leagueId)
                dao.insertStandings(standings)
            }
            if (matches.isNotEmpty() || standings.isNotEmpty()) {
                successCount++
            }
        }

        if (successCount > 0) {
            prefs.edit().putLong(SyncScheduler.KEY_LAST_SYNC_MS, now).apply()
            SyncScheduler.scheduleMonthlySync(applicationContext)
            Result.success()
        } else if (!force) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private suspend fun fetchDoc(db: FirebaseFirestore, id: String): DocumentSnapshot =
        suspendCancellableCoroutine { cont ->
            db.collection("global_sports").document(id).get()
                .addOnSuccessListener { snap -> if (cont.isActive) cont.resume(snap) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

    private suspend fun fetchAllDocs(db: FirebaseFirestore): List<DocumentSnapshot> =
        suspendCancellableCoroutine { cont ->
            db.collection("global_sports").get()
                .addOnSuccessListener { snap -> if (cont.isActive) cont.resume(snap.documents) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        }

    private fun parseMatches(doc: DocumentSnapshot, fallbackLeagueId: Int, now: Long): List<MatchEntity> {
        val rawList = doc.get("matches") as? List<*> ?: return emptyList()
        return rawList.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = (m["id"] as? Number)?.toInt() ?: return@mapNotNull null
            val homeName = (m["homeTeamName"] as? String)?.trim().orEmpty()
            val awayName = (m["awayTeamName"] as? String)?.trim().orEmpty()
            if (homeName.isBlank() || awayName.isBlank()) return@mapNotNull null
            MatchEntity(
                id = id,
                leagueId = (m["leagueId"] as? Number)?.toInt() ?: fallbackLeagueId,
                leagueName = m["leagueName"] as? String ?: "",
                leagueCountry = m["leagueCountry"] as? String ?: "",
                homeTeamId = (m["homeTeamId"] as? Number)?.toInt() ?: 0,
                homeTeamName = homeName,
                homeTeamLogo = m["homeTeamLogo"] as? String ?: "",
                awayTeamId = (m["awayTeamId"] as? Number)?.toInt() ?: 0,
                awayTeamName = awayName,
                awayTeamLogo = m["awayTeamLogo"] as? String ?: "",
                homeScore = (m["homeScore"] as? Number)?.toInt(),
                awayScore = (m["awayScore"] as? Number)?.toInt(),
                status = m["status"] as? String ?: "UPCOMING",
                minute = m["minute"] as? String,
                date = m["date"] as? String ?: "",
                time = m["time"] as? String ?: "17:00",
                stadium = m["stadium"] as? String ?: "غير محدد",
                channel = m["channel"] as? String ?: "غير محدد",
                commentator = m["commentator"] as? String ?: "غير محدد",
                savedAt = (m["savedAt"] as? Number)?.toLong() ?: now,
            )
        }
    }

    private fun parseStandings(doc: DocumentSnapshot, fallbackLeagueId: Int): List<StandingEntity> {
        val rawList = doc.get("standings") as? List<*> ?: return emptyList()
        return rawList.mapNotNull { item ->
            val s = item as? Map<*, *> ?: return@mapNotNull null
            val rank = (s["rank"] as? Number)?.toInt() ?: return@mapNotNull null
            val teamName = (s["teamName"] as? String)?.trim().orEmpty()
            if (teamName.isBlank()) return@mapNotNull null
            StandingEntity(
                leagueId = (s["leagueId"] as? Number)?.toInt() ?: fallbackLeagueId,
                rank = rank,
                teamName = teamName,
                played = (s["played"] as? Number)?.toInt() ?: 0,
                win = (s["win"] as? Number)?.toInt() ?: 0,
                draw = (s["draw"] as? Number)?.toInt() ?: 0,
                loss = (s["loss"] as? Number)?.toInt() ?: 0,
                goalsFor = (s["goalsFor"] as? Number)?.toInt() ?: 0,
                goalsAgainst = (s["goalsAgainst"] as? Number)?.toInt() ?: 0,
                goalDifference = (s["goalDifference"] as? Number)?.toInt() ?: 0,
                points = (s["points"] as? Number)?.toInt() ?: 0,
            )
        }
    }

    companion object {
        const val KEY_FORCE = "force_sync"
        const val KEY_LEAGUE_ID = "league_id"
    }
}
