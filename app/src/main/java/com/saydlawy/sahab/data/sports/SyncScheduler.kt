package com.saydlawy.sahab.data.sports

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val PREFS_NAME = "sahab_sync_prefs"
    const val KEY_LAST_SYNC_MS = "last_sync_ms"
    private const val KEY_INITIAL_SCHEDULED = "initial_scheduled"
    private const val UNIQUE_MONTHLY_WORK = "monthly_sync"
    private const val UNIQUE_MANUAL_WORK = "manual_sync"
    const val MIN_INTERVAL_MS = 25L * 86_400_000L

    fun scheduleInitialRunIfNeeded(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_INITIAL_SCHEDULED, false)) {
            scheduleMonthlySync(app)
            prefs.edit().putBoolean(KEY_INITIAL_SCHEDULED, true).apply()
        }
    }

    fun scheduleMonthlySync(context: Context) {
        val delayMs = millisUntilNextMonthMidnight()
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<MonthlySyncWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_MONTHLY_WORK,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun enqueueManualSyncNow(context: Context): OneTimeWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val input = Data.Builder()
            .putBoolean(MonthlySyncWorker.KEY_FORCE, true)
            .build()
        val request = OneTimeWorkRequestBuilder<MonthlySyncWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_MANUAL_WORK,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        return request
    }

    private fun millisUntilNextMonthMidnight(now: Calendar = Calendar.getInstance()): Long {
        val target = (now.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(TimeUnit.HOURS.toMillis(1))
    }
}
