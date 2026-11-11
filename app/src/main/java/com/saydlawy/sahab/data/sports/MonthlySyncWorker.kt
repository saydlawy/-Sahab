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
 * Background WorkManager worker responsible for syncing all 19 sports leagues from Goal API
 * into local Room tables at most once every 25 days (unless explicitly forced by manual refresh).
 */
class MonthlySyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(SyncScheduler.PREFS_NAME, Context.MODE_PRIVATE)
        val force = inputData.getBoolean(KEY_FORCE, false)
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

        var successCount = 0
        coroutineScope {
            GoalLeagueMapping.ALL.map { league ->
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

    companion object {
        const val KEY_FORCE = "force_sync"
        private const val MAX_CONCURRENT_LEAGUES = 4
    }
}
