package com.saydlawy.sahab.data

import android.content.Context
import android.util.Log
import com.saydlawy.sahab.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * منسق استعادة الحسابات والمزامنة السحابية والمحلية.
 */
class SyncCoordinator(private val container: AppContainer) {

    /**
     * يستعيد اشتراكات المستخدم من السحابة (إن لم يوجد اشتراك محلي) ويدمج المفضلة والسجل
     * والإعدادات بأمان مع ضبط أعلام الدمج الثلاثة فقط عند نجاح الجلب الفعلي.
     */
    suspend fun performCloudRestoreAndMerge(
        accountTimeoutMs: Long = 4_000L,
        mergeTimeoutMs: Long = 6_000L,
    ): Account? = coroutineScope {
        val currentUser = container.authManager.currentUser
        val localDeferred = async { container.prefs.account.first() }
        if (currentUser == null) {
            return@coroutineScope localDeferred.await()
        }

        val cloudAccountDeferred = async {
            withTimeoutOrNull(accountTimeoutMs) {
                val multi = container.userSyncService.getAccounts().orEmpty()
                if (multi.isNotEmpty()) multi else listOfNotNull(container.userSyncService.getXtreamAccount())
            }
        }

        // دمج المفضلة والسجل والإعدادات بالتوازي مع إعادة المحاولة في الخلفية عند الفشل/المهلة
        val favsJob = async {
            container.mergeCoordinator.mergeFavorites(
                localFavs = { container.prefs.favorites.first() },
                fetchCloudFavs = { container.userSyncService.fetchFavorites() },
                saveMergedFavs = { container.prefs.restoreFavorites(it) },
                timeoutMs = mergeTimeoutMs,
            )
        }
        val recentsJob = async {
            container.mergeCoordinator.mergeRecents(
                localRecents = { container.prefs.recents.first() },
                fetchCloudRecents = { container.userSyncService.fetchRecents() },
                saveMergedRecents = { container.prefs.restoreRecents(it) },
                timeoutMs = mergeTimeoutMs,
            )
        }
        val prefsJob = async {
            container.mergeCoordinator.mergePreferences(
                fetchCloudPrefs = { container.userSyncService.fetchPreferences() },
                applyPrefs = { cp ->
                    container.prefs.setLightEffects(cp.lightEffects)
                    container.prefs.setHomeChannelsLayout(
                        runCatching { HomeChannelsLayout.valueOf(cp.homeChannelsLayout) }
                            .getOrDefault(HomeChannelsLayout.CAROUSEL)
                    )
                    container.prefs.setHomeChannelsSource(
                        runCatching { HomeChannelsSource.valueOf(cp.homeChannelsSource) }
                            .getOrDefault(HomeChannelsSource.AUTO)
                    )
                    container.prefs.setHomeChannelsCategoryId(cp.homeChannelsCategoryId)
                },
                timeoutMs = mergeTimeoutMs,
            )
        }

        var activeAccount = localDeferred.await()
        if (activeAccount == null) {
            val cloudAccounts = cloudAccountDeferred.await().orEmpty()
            if (cloudAccounts.isNotEmpty()) {
                cloudAccounts.forEach { container.prefs.saveAccount(it) }
                activeAccount = cloudAccounts.first()
                container.prefs.setActiveAccount(activeAccount.id)
            }
        }

        val allMerged = favsJob.await() && recentsJob.await() && prefsJob.await()
        if (!allMerged) {
            container.appScope.launch {
                container.mergeCoordinator.mergeAllWithRetry(
                    isSignedIn = { container.authManager.currentUser != null },
                    localFavs = { container.prefs.favorites.first() },
                    fetchCloudFavs = { container.userSyncService.fetchFavorites() },
                    saveMergedFavs = { container.prefs.restoreFavorites(it) },
                    localRecents = { container.prefs.recents.first() },
                    fetchCloudRecents = { container.userSyncService.fetchRecents() },
                    saveMergedRecents = { container.prefs.restoreRecents(it) },
                    fetchCloudPrefs = { container.userSyncService.fetchPreferences() },
                    applyPrefs = { cp ->
                        container.prefs.setLightEffects(cp.lightEffects)
                        container.prefs.setHomeChannelsLayout(
                            runCatching { HomeChannelsLayout.valueOf(cp.homeChannelsLayout) }
                                .getOrDefault(HomeChannelsLayout.CAROUSEL)
                        )
                        container.prefs.setHomeChannelsSource(
                            runCatching { HomeChannelsSource.valueOf(cp.homeChannelsSource) }
                                .getOrDefault(HomeChannelsSource.AUTO)
                        )
                        container.prefs.setHomeChannelsCategoryId(cp.homeChannelsCategoryId)
                    },
                )
            }
        }

        activeAccount
    }

    /**
     * يشغّل سلسلة المزامنة الخلفية بالكامل للحساب النشط.
     */
    fun triggerFullSync(context: Context, forceCatalog: Boolean = false, reason: String = "startup") {
        container.appScope.launch {
            runCatching {
                val stats = container.catalogSync.populateFromSeed(context)
                Log.d("SahabSeed", "[$reason] seed: total=${stats.total} loaded=${stats.loaded} skipped=${stats.wasSkipped} dur=${stats.durationMs}ms")
            }

            runCatching { container.repo.prewarmCatalog() }
                .onFailure { Log.w("SahabSync", "[$reason] prewarm failed: ${it.message}") }

            val sync = runCatching { container.repo.syncCatalog(force = forceCatalog) }
                .getOrElse { Repository.SyncResult.Failure(it.message ?: "unknown") }
            if (sync is Repository.SyncResult.Success || sync is Repository.SyncResult.Failure) {
                runCatching { container.catalogSync.syncMovies(container.repo) }
                    .onFailure { Log.w("SahabSync", "[$reason] syncMovies failed: ${it.message}") }
            }

            val lastChSync = container.prefs.lastSyncTime.first()
            val chStats = if (forceCatalog || System.currentTimeMillis() - lastChSync > 24L * 3600_000L) {
                runCatching { container.channelSync.sync(container.repo, forceFullRefresh = forceCatalog) }
                    .onSuccess { st ->
                        Log.d("SahabChannelSync", "[$reason] total=${st.total} enriched=${st.enriched} errors=${st.errors.size}")
                        if (st.isSuccess) container.prefs.setLastSyncTime(System.currentTimeMillis())
                    }
                    .getOrNull()
            } else null

            val lastEpgSync = container.prefs.lastEpgSyncTime.first()
            val canRunEpg = (chStats?.isSuccess == true) || lastChSync > 0L
            if (canRunEpg && (forceCatalog || System.currentTimeMillis() - lastEpgSync > 24L * 3600_000L)) {
                runCatching { container.epgSync.sync() }
                    .onSuccess { epgStats ->
                        Log.d("SahabEpgSync", "[$reason] epgIds=${epgStats.epgIdsProcessed} upserted=${epgStats.programsUpserted} errors=${epgStats.errors.size}")
                        if (epgStats.isSuccess) container.prefs.setLastEpgSyncTime(System.currentTimeMillis())
                    }
                    .onFailure { Log.w("SahabEpgSync", "[$reason] failed: ${it.message}") }
            }
        }
    }
}
