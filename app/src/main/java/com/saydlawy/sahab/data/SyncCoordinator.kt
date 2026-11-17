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
        waitForMerge: Boolean = true,
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

        suspend fun runMergeAndRetry() = coroutineScope {
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
        }

        if (!waitForMerge) {
            container.appScope.launch { runMergeAndRetry() }
        }

        var activeAccount = localDeferred.await()
        if (activeAccount == null) {
            val cloudAccounts = cloudAccountDeferred.await().orEmpty()
            if (cloudAccounts.isNotEmpty()) {
                cloudAccounts.asReversed().forEach { container.prefs.saveAccount(it) }
                activeAccount = cloudAccounts.first()
                container.prefs.setActiveAccount(activeAccount.id)
            }
        }

        if (waitForMerge) {
            runMergeAndRetry()
        }

        activeAccount
    }

    /**
     * يشغّل سلسلة المزامنة الخلفية بالكامل للحساب النشط في [container.appScope] دون حجب الواجهة.
     */
    fun triggerFullSync(context: Context, forceCatalog: Boolean = false, reason: String = "startup") {
        val appContext = context.applicationContext
        container.appScope.launch {
            runFullSync(appContext, forceCatalog = forceCatalog, reason = reason)
        }
    }

    /**
     * ينفذ سلسلة المزامنة الكاملة بالترتيب:
     * 1) prewarmCatalog (+ البذرة) -> 2) syncCatalog -> 3) catalogSync.syncMovies ->
     * 4) channelSync.sync -> 5) epgSync.sync (يعمل فقط بعد channelSync ناجح).
     * كل خطوة تُسجّل وتفشل بمعزل دون إيقاف السلسلة.
     */
    suspend fun runFullSync(context: Context, forceCatalog: Boolean = false, reason: String = "startup") {
        container.prefs.migrateSyncKeysIfNeeded()

        runCatching {
            val stats = container.catalogSync.populateFromSeed(context)
            Log.d("SahabSeed", "[$reason] seed: total=${stats.total} loaded=${stats.loaded} skipped=${stats.wasSkipped} dur=${stats.durationMs}ms")
        }.onFailure { Log.w("SahabSeed", "[$reason] seed failed: ${it.message}") }

        Log.d("SahabSync", "[$reason] step 1/5: prewarmCatalog")
        runCatching { container.repo.prewarmCatalog() }
            .onFailure { Log.w("SahabSync", "[$reason] prewarm failed: ${it.message}") }

        Log.d("SahabSync", "[$reason] step 2/5: syncCatalog (force=$forceCatalog)")
        val sync = runCatching { container.repo.syncCatalog(force = forceCatalog) }
            .getOrElse { Repository.SyncResult.Failure(it.message ?: "unknown") }
        Log.d("SahabSync", "[$reason] step 2/5 result: $sync")

        val hasLocalCatalog = runCatching { container.catalogDb.movieDao().count() > 0 }.getOrDefault(false)
        if (sync is Repository.SyncResult.Success || (sync is Repository.SyncResult.Skipped && hasLocalCatalog)) {
            Log.d("SahabSync", "[$reason] step 3/5: catalogSync.syncMovies")
            runCatching { container.catalogSync.syncMovies(container.repo) }
                .onSuccess { st -> Log.d("SahabSync", "[$reason] step 3/5 done: total=${st.total} ins=${st.inserted} upd=${st.updated}") }
                .onFailure { Log.w("SahabSync", "[$reason] step 3/5 failed: ${it.message}") }
        }

        val lastChSync = container.prefs.lastChannelSyncTime.first()
        val needChSync = forceCatalog || lastChSync == 0L || (System.currentTimeMillis() - lastChSync > 24L * 3600_000L)
        val chStats = if (needChSync) {
            Log.d("SahabChannelSync", "[$reason] step 4/5: channelSync.sync")
            runCatching { container.channelSync.sync(container.repo, forceFullRefresh = forceCatalog) }
                .onSuccess { st ->
                    Log.d("SahabChannelSync", "[$reason] total=${st.total} enriched=${st.enriched} errors=${st.errors.size}")
                    if (st.isSuccess) container.prefs.setLastChannelSyncTime(System.currentTimeMillis())
                }
                .onFailure { Log.w("SahabChannelSync", "[$reason] step 4/5 failed: ${it.message}") }
                .getOrNull()
        } else null

        val lastEpgSync = container.prefs.lastEpgSyncTime.first()
        val updatedChSync = container.prefs.lastChannelSyncTime.first()
        val canRunEpg = (chStats?.isSuccess == true) || updatedChSync > 0L
        val needEpgSync = forceCatalog || lastEpgSync == 0L || (System.currentTimeMillis() - lastEpgSync > 24L * 3600_000L)
        if (canRunEpg && needEpgSync) {
            Log.d("SahabEpgSync", "[$reason] step 5/5: epgSync.sync")
            runCatching { container.epgSync.sync() }
                .onSuccess { epgStats ->
                    Log.d("SahabEpgSync", "[$reason] epgIds=${epgStats.epgIdsProcessed} upserted=${epgStats.programsUpserted} errors=${epgStats.errors.size}")
                    if (epgStats.isSuccess) container.prefs.setLastEpgSyncTime(System.currentTimeMillis())
                }
                .onFailure { Log.w("SahabEpgSync", "[$reason] step 5/5 failed: ${it.message}") }
        }
    }
}
