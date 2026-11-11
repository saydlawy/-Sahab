package com.saydlawy.sahab.data.sports

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.saydlawy.sahab.BuildConfig
import com.saydlawy.sahab.SahabApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that syncs sports data from Goal API into Room.
 *
 * Two modes:
 *  - Manual single-league: [KEY_LEAGUE_ID] is set in inputData; only that league
 *    is fetched. Triggered by the user pressing "refresh" on a specific league.
 *  - Automatic batch: no [KEY_LEAGUE_ID]; syncs BATCH_SIZE leagues per run,
 *    rotating through the full list via a persisted cursor. Triggered every
 *    SyncScheduler.MIN_INTERVAL_MS (25 hours).
 */
class MonthlySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(SyncScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val force = inputData.getBoolean(KEY_FORCE, false)
        val singleLeagueId = inputData.getInt(KEY_LEAGUE_ID, 0)
        val isManualSingle = singleLeagueId > 0

        val now = System.currentTimeMillis()
        val lastSync = prefs.getLong(SyncScheduler.KEY_LAST_SYNC_MS, 0L)
        if (!force && lastSync > 0L && (now - lastSync) < SyncScheduler.MIN_INTERVAL_MS) {
            SyncScheduler.scheduleMonthlySync(applicationContext)
            return@withContext Result.success()
        }

        val apiKey = try {
            BuildConfig.GOAL_API_KEY
        } catch (_: Exception) {
            ""
        }
        if (apiKey.isBlank()) {
            SyncScheduler.scheduleMonthlySync(applicationContext)
            return@withContext Result.success()
        }

        val client = (applicationContext as? SahabApp)?.container?.client
            ?: OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()

        val dao = SportsDatabase.getInstance(applicationContext).matchDao()
        val api = GoalApi(client = client, apiKey = apiKey)
        val gate = Semaphore(MAX_CONCURRENT_LEAGUES)

        val leaguesToSync: List<GoalLeagueSpec> = if (isManualSingle) {
            listOfNotNull(GoalLeagueMapping.findByLeagueId(singleLeagueId))
        } else {
            val cursor = prefs.getInt(SyncScheduler.KEY_SYNC_CURSOR, 0)
            val all = GoalLeagueMapping.ALL
            val n = all.size
            val size = minOf(SyncScheduler.BATCH_SIZE, n)
            (0 until size).map { offset -> all[(cursor + offset) % n] }
        }

        var successCount = 0
        if (leaguesToSync.isNotEmpty()) {
            coroutineScope {
                leaguesToSync.map { league ->
                    async {
                        gate.withPermit {
                            var leagueOk = false
                            val fixturesRes = api.fixturesByLeague(league)
                            if (fixturesRes is ApiResult.Success && fixturesRes.data.isNotEmpty()) {
                                dao.insertMatches(fixturesRes.data)
                                leagueOk = true
                            }
                            val standingsRes = api.standingsByLeague(league)
                            if (standingsRes is ApiResult.Success && standingsRes.data.isNotEmpty()) {
                                dao.deleteStandingsByLeague(league.leagueId)
                                dao.insertStandings(standingsRes.data)
                                leagueOk = true
                            }
                            leagueOk
                        }
                    }
                }.awaitAll().forEach { ok ->
                    if (ok) successCount++
                }
            }
        }

        if (!isManualSingle && successCount > 0) {
            val cursor = prefs.getInt(SyncScheduler.KEY_SYNC_CURSOR, 0)
            val n = GoalLeagueMapping.ALL.size
            val newCursor = if (n > 0) (cursor + SyncScheduler.BATCH_SIZE) % n else 0
            prefs.edit().putInt(SyncScheduler.KEY_SYNC_CURSOR, newCursor).apply()
        }

        if (successCount > 0) {
            if (!isManualSingle) {
                prefs.edit().putLong(SyncScheduler.KEY_LAST_SYNC_MS, now).apply()
                SyncScheduler.scheduleMonthlySync(applicationContext)
            }
            Result.success()
        } else if (!force) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE = "force_sync"
        const val KEY_LEAGUE_ID = "league_id"
        private const val MAX_CONCURRENT_LEAGUES = 4
    }
}
