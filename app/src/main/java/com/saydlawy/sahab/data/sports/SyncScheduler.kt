package com.saydlawy.sahab.data.sports

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val PREFS_NAME = "sahab_sync_prefs"
    const val KEY_LAST_SYNC_MS = "last_sync_ms"
    const val KEY_SYNC_CURSOR = "sync_cursor"
    const val BATCH_SIZE = 3
    private const val KEY_INITIAL_SCHEDULED = "initial_scheduled"
    private const val UNIQUE_MONTHLY_WORK = "monthly_sync"
    private const val UNIQUE_MANUAL_WORK = "manual_sync"

    /** Minimum interval between automatic batch syncs (25 hours). */
    const val MIN_INTERVAL_MS = 25L * 3_600_000L

    fun scheduleInitialRunIfNeeded(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIAL_SCHEDULED, false)) {
            scheduleMonthlySync(app)
            prefs.edit().putBoolean(KEY_INITIAL_SCHEDULED, true).apply()
        }
    }

    /**
     * Schedules the next automatic batch sync, 25 hours from now.
     * The batch syncs BATCH_SIZE leagues per run, rotating through the full
     * list via the persisted cursor in prefs.
     */
    fun scheduleMonthlySync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<MonthlySyncWorker>(
            MIN_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )
            .setInitialDelay(MIN_INTERVAL_MS, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_MONTHLY_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Enqueues an immediate sync. When [leagueId] is provided, only that league
     * is synced (manual single-league mode). Otherwise the worker uses batch mode.
     */
    fun enqueueManualSyncNow(context: Context, leagueId: Int? = null): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val inputBuilder = Data.Builder()
            .putBoolean(MonthlySyncWorker.KEY_FORCE, true)
        if (leagueId != null) {
            inputBuilder.putInt(MonthlySyncWorker.KEY_LEAGUE_ID, leagueId)
        }
        val request = OneTimeWorkRequestBuilder<MonthlySyncWorker>()
            .setConstraints(constraints)
            .setInputData(inputBuilder.build())
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_MANUAL_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request
    }
}
