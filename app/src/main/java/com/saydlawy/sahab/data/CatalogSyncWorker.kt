package com.saydlawy.sahab.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.saydlawy.sahab.SahabApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Periodic background sync scheduled via WorkManager with network & battery constraints
 * so catalog/channels/EPG stay fresh without draining battery on unmetered polling timers.
 */
class CatalogSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? SahabApp)?.container ?: return Result.success()
        return try {
            val activeAccount = container.repo.account ?: container.prefs.account.first()?.also {
                container.repo.setAccount(it)
            }
            if (activeAccount == null) {
                return Result.success()
            }
            container.syncCoordinator.runFullSync(
                context = applicationContext,
                forceCatalog = false,
                reason = "workmanager_periodic",
            )
            Result.success()
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "sahab_periodic_catalog_sync"

        fun schedulePeriodic(context: Context) {
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
                val request = PeriodicWorkRequestBuilder<CatalogSyncWorker>(12L, TimeUnit.HOURS)
                    .setConstraints(constraints)
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }
        }

        fun cancelPeriodic(context: Context) {
            runCatching {
                WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            }
        }
    }
}
