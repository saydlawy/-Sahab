package com.saydlawy.sahab.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import com.google.firebase.messaging.FirebaseMessaging
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.MyFirebaseMessagingService
import com.saydlawy.sahab.data.auth.CloudAccountRestoreResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * منسق استعادة الحسابات والمزامنة السحابية والمحلية.
 */
class SyncCoordinator(private val container: AppContainer) {

    private val _needsSyncPinPrompt = MutableStateFlow(false)
    val needsSyncPinPrompt: StateFlow<Boolean> = _needsSyncPinPrompt.asStateFlow()

    private val syncMutex = Mutex()

    @Volatile
    private var activeSyncJob: Job? = null

    fun dismissSyncPinPrompt() {
        _needsSyncPinPrompt.value = false
    }

    suspend fun submitRestorePin(context: Context, pin: String): CloudAccountRestoreResult {
        val result = container.userSyncService.restoreAccountsWithPin(pinOverride = pin)
        if (result is CloudAccountRestoreResult.Restored && result.accounts.isNotEmpty()) {
            container.prefs.setCloudSyncPin(pin)
            result.accounts.asReversed().forEach { container.prefs.saveAccount(it) }
            val active = result.accounts.first()
            container.prefs.setActiveAccount(active.id)
            container.repo.setAccount(active)
            _needsSyncPinPrompt.value = false
            triggerFullSync(context, forceCatalog = false, reason = "pin_restore")
        }
        return result
    }

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
        val localAccount = container.prefs.account.first()
        if (currentUser == null) {
            return@coroutineScope localAccount
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
                        container.prefs.setMoviesLayoutStyle(
                            runCatching { VodLayoutStyle.valueOf(cp.moviesLayoutStyle) }
                                .getOrDefault(VodLayoutStyle.CLASSIC)
                        )
                        container.prefs.setSeriesLayoutStyle(
                            runCatching { VodLayoutStyle.valueOf(cp.seriesLayoutStyle) }
                                .getOrDefault(VodLayoutStyle.CLASSIC)
                        )
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
                            container.prefs.setMoviesLayoutStyle(
                                runCatching { VodLayoutStyle.valueOf(cp.moviesLayoutStyle) }
                                    .getOrDefault(VodLayoutStyle.CLASSIC)
                            )
                            container.prefs.setSeriesLayoutStyle(
                                runCatching { VodLayoutStyle.valueOf(cp.seriesLayoutStyle) }
                                    .getOrDefault(VodLayoutStyle.CLASSIC)
                            )
                        },
                    )
                }
            }
        }

        if (!waitForMerge) {
            container.appScope.launch { runMergeAndRetry() }
        }

        var activeAccount = localAccount
        if (activeAccount == null) {
            val restoreRes = withTimeoutOrNull(accountTimeoutMs) {
                container.userSyncService.restoreAccountsWithPin()
            } ?: CloudAccountRestoreResult.Empty
            when (restoreRes) {
                is CloudAccountRestoreResult.Restored -> {
                    val cloudAccounts = restoreRes.accounts
                    if (cloudAccounts.isNotEmpty()) {
                        cloudAccounts.asReversed().forEach { container.prefs.saveAccount(it) }
                        activeAccount = cloudAccounts.first()
                        container.prefs.setActiveAccount(activeAccount.id)
                    }
                    _needsSyncPinPrompt.value = false
                }
                is CloudAccountRestoreResult.NeedsPin,
                is CloudAccountRestoreResult.WrongPin -> {
                    _needsSyncPinPrompt.value = true
                }
                is CloudAccountRestoreResult.Empty -> {
                    _needsSyncPinPrompt.value = false
                }
            }
        }

        val activeId = activeAccount?.id
        if (!activeId.isNullOrBlank()) {
            container.prefs.migrateHistoryAccountIdIfNeeded(activeId)
        }

        if (waitForMerge) {
            runMergeAndRetry()
        }

        activeAccount
    }

    fun syncFcmToken(context: Context) {
        if (container.authManager.currentUser == null) return
        val appContext = context.applicationContext
        container.appScope.launch {
            val cached = MyFirebaseMessagingService.getSavedToken(appContext)
            val token = cached ?: suspendCancellableCoroutine<String?> { cont ->
                FirebaseMessaging.getInstance().token
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
            if (!token.isNullOrBlank()) {
                MyFirebaseMessagingService.savePendingToken(appContext, token)
                container.userSyncService.saveFcmToken(token)
            }
        }
    }

    /**
     * يوقف أي مزامنة خلفية نشطة وينتظر تحرير القفل قبل مسح البيانات أو تبديل الحساب.
     */
    suspend fun stopActiveSync() {
        val job = activeSyncJob
        activeSyncJob = null
        job?.cancelAndJoin()
    }

    /**
     * يشغّل سلسلة المزامنة الخلفية بالكامل للحساب النشط في [container.appScope] دون حجب الواجهة.
     */
    fun triggerFullSync(context: Context, forceCatalog: Boolean = false, reason: String = "startup") {
        val appContext = context.applicationContext
        CatalogSyncWorker.schedulePeriodic(appContext)
        val previous = activeSyncJob
        if (!forceCatalog && previous?.isActive == true) {
            return
        }
        activeSyncJob = container.appScope.launch {
            previous?.cancelAndJoin()
            runFullSync(appContext, forceCatalog = forceCatalog, reason = reason)
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean = runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@runCatching true
        val network = cm.activeNetwork ?: return@runCatching false
        val caps = cm.getNetworkCapabilities(network) ?: return@runCatching false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(true)

    private suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }

    /**
     * يشغّل المزامنة اليدوية من الإعدادات داخل [container.appScope] بحيث تستمر حتى لو أغلق المستخدم شاشة الإعدادات.
     */
    fun triggerManualSync(context: Context) {
        val appContext = context.applicationContext
        val previous = activeSyncJob
        activeSyncJob = container.appScope.launch(Dispatchers.IO) {
            previous?.cancelAndJoin()
            syncMutex.withLock {
                val msg = suspendRunCatching {
                    container.repo.syncCatalog(force = true)
                    container.catalogSync.syncAllFromFirebaseToRoom(force = false)
                    container.catalogSync.syncMovies(container.repo)
                    container.catalogSync.syncSeries(container.repo)
                    val chStats = container.channelSync.sync(container.repo, forceFullRefresh = true)
                    if (chStats.isSuccess) container.prefs.setLastChannelSyncTime(System.currentTimeMillis())

                    val epgStats = if (chStats.isSuccess || chStats.total > 0) {
                        container.epgSync.sync().also { st ->
                            if (st.isSuccess) container.prefs.setLastEpgSyncTime(System.currentTimeMillis())
                        }
                    } else {
                        com.saydlawy.sahab.data.channel.EpgSync.Stats(errors = listOf("skipped"))
                    }

                    buildString {
                        append("القنوات: ")
                        when {
                            chStats.isSuccess && chStats.total > 0 ->
                                append("${chStats.total} قناة (${chStats.enriched} بـ EPG)")
                            chStats.hasPartialFailure -> append("جزئي (${chStats.errors.size} خطأ)")
                            else -> append("فشل")
                        }
                        append("\nالبرامج: ")
                        when {
                            epgStats.isSuccess && epgStats.programsUpserted > 0 ->
                                append("${epgStats.programsUpserted} برنامج")
                            epgStats.hasPartialFailure -> append("جزئي (${epgStats.errors.size} خطأ)")
                            else -> append("فشل")
                        }
                    }
                }.getOrElse { err ->
                    "تعذر إتمام المزامنة: ${err.message ?: "خطأ غير متوقع"}"
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    /**
     * ينفذ سلسلة المزامنة الكاملة بالترتيب:
     * 1) prewarmCatalog (+ البذرة) -> 2) syncCatalog -> 3) catalogSync.syncMovies ->
     * 4) channelSync.sync -> 5) epgSync.sync (يعمل فقط بعد channelSync ناجح).
     * كل خطوة تُسجّل وتفشل بمعزل دون إيقاف السلسلة.
     */
    suspend fun runFullSync(context: Context, forceCatalog: Boolean = false, reason: String = "startup"): Unit = syncMutex.withLock {
        container.prefs.migrateSyncKeysIfNeeded()

        suspendRunCatching {
            val stats = container.catalogSync.populateFromSeed(context)
            Log.d("SahabSeed", "[$reason] seed: total=${stats.total} loaded=${stats.loaded} skipped=${stats.wasSkipped} dur=${stats.durationMs}ms")
        }.onFailure { Log.w("SahabSeed", "[$reason] seed failed: ${it.message}") }

        if (!forceCatalog) {
            Log.d("SahabSync", "[$reason] step 1/5: prewarmCatalog")
            suspendRunCatching { container.repo.prewarmCatalog() }
                .onFailure { Log.w("SahabSync", "[$reason] prewarm failed: ${it.message}") }
        }

        if (!forceCatalog && !isNetworkAvailable(context)) {
            Log.d("SahabSync", "[$reason] offline — skipping network sync steps")
            return@withLock
        }

        Log.d("SahabSync", "[$reason] step 2/5: syncCatalog (force=$forceCatalog)")
        val sync = suspendRunCatching { container.repo.syncCatalog(force = forceCatalog) }
            .getOrElse { Repository.SyncResult.Failure(it.message ?: "unknown") }
        Log.d("SahabSync", "[$reason] step 2/5 result: $sync")

        Log.d("SahabSync", "[$reason] step 2.5/5: catalogSync.syncAllFromFirebaseToRoom (force=$forceCatalog)")
        suspendRunCatching { container.catalogSync.syncAllFromFirebaseToRoom(force = forceCatalog) }
            .onSuccess { count -> Log.d("SahabSync", "[$reason] firebase->room synced=$count") }
            .onFailure { Log.w("SahabSync", "[$reason] firebase->room failed: ${it.message}") }

        val movieCount = suspendRunCatching { container.catalogDb.movieDao().countByType("movie") }.getOrDefault(0)
        val seriesCount = suspendRunCatching { container.catalogDb.movieDao().countByType("series") }.getOrDefault(0)
        val missingMovieMeta = suspendRunCatching { container.catalogDb.movieDao().countMissingProviderOrderOrSearch("movie") }.getOrDefault(0)
        val missingSeriesMeta = suspendRunCatching { container.catalogDb.movieDao().countMissingProviderOrderOrSearch("series") }.getOrDefault(0)
        if (sync is Repository.SyncResult.Success || movieCount == 0 || missingMovieMeta > 0 || (sync is Repository.SyncResult.Skipped && movieCount > 0)) {
            Log.d("SahabSync", "[$reason] step 3/5: catalogSync.syncMovies")
            suspendRunCatching { container.catalogSync.syncMovies(container.repo) }
                .onSuccess { st -> Log.d("SahabSync", "[$reason] step 3/5 movies done: total=${st.total} ins=${st.inserted} upd=${st.updated}") }
                .onFailure { Log.w("SahabSync", "[$reason] step 3/5 movies failed: ${it.message}") }
        }
        if (sync is Repository.SyncResult.Success || seriesCount == 0 || missingSeriesMeta > 0 || (sync is Repository.SyncResult.Skipped && seriesCount > 0)) {
            Log.d("SahabSync", "[$reason] step 3.5/5: catalogSync.syncSeries")
            suspendRunCatching { container.catalogSync.syncSeries(container.repo) }
                .onSuccess { st -> Log.d("SahabSync", "[$reason] step 3.5/5 series done: total=${st.total} ins=${st.inserted} upd=${st.updated}") }
                .onFailure { Log.w("SahabSync", "[$reason] step 3.5/5 series failed: ${it.message}") }
        }

        val lastChSync = container.prefs.lastChannelSyncTime.first()
        val needChSync = forceCatalog || lastChSync == 0L || (System.currentTimeMillis() - lastChSync > 24L * 3600_000L)
        val chStats = if (needChSync) {
            Log.d("SahabChannelSync", "[$reason] step 4/5: channelSync.sync")
            suspendRunCatching { container.channelSync.sync(container.repo, forceFullRefresh = forceCatalog) }
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
            suspendRunCatching { container.epgSync.sync() }
                .onSuccess { epgStats ->
                    Log.d("SahabEpgSync", "[$reason] epgIds=${epgStats.epgIdsProcessed} upserted=${epgStats.programsUpserted} errors=${epgStats.errors.size}")
                    if (epgStats.isSuccess) container.prefs.setLastEpgSyncTime(System.currentTimeMillis())
                }
                .onFailure { Log.w("SahabEpgSync", "[$reason] step 5/5 failed: ${it.message}") }
        }
    }
}
