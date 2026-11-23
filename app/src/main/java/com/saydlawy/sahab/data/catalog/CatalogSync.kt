package com.saydlawy.sahab.data.catalog

import android.content.Context
import com.saydlawy.sahab.data.DEFAULT_VOD_EXT
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.util.RatingSanitizer
import com.saydlawy.sahab.util.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.Locale
import java.util.zip.GZIPInputStream

/**
 * Syncs IPTV movies and series into local Room (`catalog_movies`) preserving provider order,
 * manages Firebase `global_catalog` delta sync, unifies enrichment (Room -> Firebase -> TMDB),
 * and gates cloud uploads through [maybeAutoUploadToCloud].
 */
class CatalogSync(
    private val dao: CatalogMovieDao,
    private val firebaseCatalog: FirebaseCatalogService,
    private val appContext: Context? = null,
    private val prefs: com.saydlawy.sahab.data.Prefs? = null,
    private val relationalDao: CatalogRelationalDao? = null,
) {
    data class Stats(
        val total: Int = 0,
        val inserted: Int = 0,
        val updated: Int = 0,
        val deleted: Int = 0,
        val durationMs: Long = 0L,
    )

    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    private val inFlightEnrich = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val activeSyncCount = java.util.concurrent.atomic.AtomicInteger(0)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

    private val _syncVersion = MutableStateFlow(0L)
    val syncVersion: StateFlow<Long> = _syncVersion

    private suspend fun <T> withSyncLock(block: suspend () -> T): T {
        activeSyncCount.incrementAndGet()
        _syncing.value = true
        return try {
            syncMutex.lock()
            try {
                block()
            } finally {
                syncMutex.unlock()
            }
        } finally {
            if (activeSyncCount.decrementAndGet() <= 0) {
                activeSyncCount.set(0)
                _syncing.value = false
            }
        }
    }

    private val _firebaseSyncing = MutableStateFlow(false)
    val firebaseSyncing: StateFlow<Boolean> = _firebaseSyncing

    private val _firebaseSyncMessage = MutableStateFlow<String?>(null)
    val firebaseSyncMessage: StateFlow<String?> = _firebaseSyncMessage

    data class BackgroundEnrichStatus(
        val active: Boolean = false,
        val done: Int = 0,
        val total: Int = 0,
        val currentTitle: String = "",
        val failed: Int = 0,
        val lastError: String = "",
    )

    private val _bgEnrichStatus = MutableStateFlow(BackgroundEnrichStatus())
    val bgEnrichStatus: StateFlow<BackgroundEnrichStatus> = _bgEnrichStatus
    private val lastEnrichNotifyAtMs = java.util.concurrent.atomic.AtomicLong(0L)

    fun reportBackgroundEnrichProgress(
        done: Int,
        total: Int,
        active: Boolean,
        currentTitle: String = "",
        failed: Int = 0,
        lastError: String = "",
    ) {
        _bgEnrichStatus.value = BackgroundEnrichStatus(
            active = active,
            done = done,
            total = total,
            currentTitle = currentTitle,
            failed = failed,
            lastError = lastError,
        )
        val ctx = appContext ?: return
        val isTv = ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)
        if (isTv) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager ?: return
        val notifId = 4092
        if (!active || total <= 0) {
            lastEnrichNotifyAtMs.set(0L)
            nm.cancel(notifId)
            return
        }
        val nowMs = System.currentTimeMillis()
        val prevMs = lastEnrichNotifyAtMs.get()
        if (done < total && prevMs > 0L && (nowMs - prevMs) < 1_000L) {
            return
        }
        if (done < total && !lastEnrichNotifyAtMs.compareAndSet(prevMs, nowMs)) {
            return
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val channelId = "sahab_bg_enrich"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val ch = android.app.NotificationChannel(
                channelId,
                "تحديث بيانات المكتبة",
                android.app.NotificationManager.IMPORTANCE_LOW,
            ).apply { setShowBadge(false) }
            nm.createNotificationChannel(ch)
        }
        val notif = androidx.core.app.NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("سحاب — إكمال بيانات المكتبة")
            .setContentText(
                if (currentTitle.isNotBlank()) "($done/$total) $currentTitle"
                else "تم تحديث $done من $total عمل",
            )
            .setProgress(total, done.coerceAtMost(total), false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { nm.notify(notifId, notif) }
    }

    /**
     * Pushes only locally modified/enriched items flagged with needsCloudPush = 1.
     * Clears needsCloudPush ONLY after verified Firestore write success, then bumps meta/catalog once.
     */
    suspend fun pushPendingCloudQueue(batchLimit: Int = 50): Int = withContext(Dispatchers.IO) {
        val uid = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (uid.isNullOrBlank()) {
            reportBackgroundEnrichProgress(
                done = 0,
                total = 1,
                active = false,
                failed = 1,
                lastError = "يلزم تسجيل الدخول للحساب السحابي أولاً لرفع التعديلات المعلّقة",
            )
            return@withContext 0
        }
        val total = dao.countPendingCloudPush()
        if (total <= 0) return@withContext 0
        var done = 0
        var pushed = 0
        var failed = 0
        var lastError = ""
        reportBackgroundEnrichProgress(0, total, active = true, currentTitle = "رفع التعديلات المعلّقة ($total)...")
        try {
            while (true) {
                val batch = dao.getPendingCloudPush(limit = batchLimit)
                if (batch.isEmpty()) break
                val succeededIds = mutableListOf<String>()
                var batchFailed = 0
                for (item in batch) {
                    val label = item.titleAr.ifBlank { item.titleEn }
                    reportBackgroundEnrichProgress(done, total, active = true, currentTitle = label, failed = failed, lastError = lastError)
                    val ok = runCatching {
                        firebaseCatalog.saveCatalogItemVerified(item, bumpMeta = false)
                    }.getOrElse { err ->
                        if (err is kotlinx.coroutines.CancellationException) throw err
                        lastError = err.message?.take(120) ?: err::class.java.simpleName
                        android.util.Log.w("SAHAB_CLOUD", "pushPendingCloudQueue failed [$label]: $lastError", err)
                        false
                    }
                    if (ok) {
                        succeededIds += item.sahabId
                        pushed += 1
                    } else {
                        failed += 1
                        batchFailed += 1
                        if (lastError.isBlank()) lastError = firebaseCatalog.lastWriteError.ifBlank { "فشل الكتابة في Firestore (قد يكون الحد اليومي نفد)" }
                    }
                    done += 1
                    reportBackgroundEnrichProgress(done, total, active = true, currentTitle = label, failed = failed, lastError = lastError)
                }
                if (succeededIds.isNotEmpty()) {
                    dao.markCloudPushed(succeededIds)
                }
                if (batchFailed == batch.size || batch.size < batchLimit) break
            }
            if (pushed > 0) {
                firebaseCatalog.bumpCatalogMetaTimestamp()
            }
        } finally {
            reportBackgroundEnrichProgress(done, total, active = false, failed = failed, lastError = lastError)
        }
        pushed
    }

    suspend fun pushLocalEnrichedToCloud(): Int = withContext(Dispatchers.IO) {
        val uid = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (uid.isNullOrBlank()) {
            reportBackgroundEnrichProgress(
                done = 0,
                total = 1,
                active = false,
                failed = 1,
                lastError = "يلزم تسجيل الدخول للحساب السحابي أولاً لرفع البيانات",
            )
            return@withContext 0
        }
        val total = dao.countEnriched()
        if (total <= 0) return@withContext 0
        var done = 0
        var pushed = 0
        var failed = 0
        var lastError = ""
        reportBackgroundEnrichProgress(0, total, active = true, currentTitle = "بدء رفع البيانات المحلية...")
        try {
            var cursor = ""
            while (true) {
                val page = dao.getEnrichedPage(afterSahabId = cursor, limit = CATALOG_ENRICHED_PAGE_SIZE)
                if (page.isEmpty()) break
                val succeededIds = mutableListOf<String>()
                for (item in page) {
                    val label = item.titleAr.ifBlank { item.titleEn }
                    reportBackgroundEnrichProgress(done, total, active = true, currentTitle = label, failed = failed, lastError = lastError)
                    val ok = runCatching {
                        firebaseCatalog.saveCatalogItemVerified(item, bumpMeta = false)
                    }.getOrElse { err ->
                        if (err is kotlinx.coroutines.CancellationException) throw err
                        lastError = err.message?.take(120) ?: err::class.java.simpleName
                        android.util.Log.w("SAHAB_CLOUD", "pushLocalEnrichedToCloud failed [$label]: $lastError", err)
                        false
                    }
                    if (ok) {
                        succeededIds += item.sahabId
                        pushed += 1
                    } else {
                        failed += 1
                        if (lastError.isBlank()) lastError = firebaseCatalog.lastWriteError.ifBlank { "فشل الكتابة في Firestore" }
                    }
                    done += 1
                    reportBackgroundEnrichProgress(done, total, active = true, currentTitle = label, failed = failed, lastError = lastError)
                }
                if (succeededIds.isNotEmpty()) {
                    dao.markCloudPushed(succeededIds)
                }
                val nextCursor = page.last().sahabId
                if (page.size < CATALOG_ENRICHED_PAGE_SIZE || nextCursor.isEmpty() || nextCursor <= cursor) break
                cursor = nextCursor
            }
            if (pushed > 0) {
                firebaseCatalog.bumpCatalogMetaTimestamp()
            }
        } finally {
            reportBackgroundEnrichProgress(done, total, active = false, failed = failed, lastError = lastError)
        }
        pushed
    }

    /**
     * Unified upload gate respecting admin permission, `cloudAutoUploadEnabled`, and valid `tmdb:` ID.
     * Returns true only if Firestore write succeeded and `needsCloudPush` was cleared.
     */
    suspend fun maybeAutoUploadToCloud(entity: CatalogMovieEntity): Boolean = withContext(Dispatchers.IO) {
        if ((entity.tmdbId ?: 0) <= 0) return@withContext false
        val autoUpload = runCatching { prefs?.cloudAutoUploadEnabled?.first() ?: true }.getOrDefault(true)
        if (!autoUpload) return@withContext false
        val ok = runCatching {
            firebaseCatalog.saveCatalogItemVerified(entity, bumpMeta = true)
        }.getOrDefault(false)
        if (ok) {
            runCatching { dao.markCloudPushed(listOf(entity.sahabId)) }
        }
        ok
    }

    /**
     * Free-tier safe catalog sync:
     * 1. Reads meta/catalog (1 Firestore read) when force == false; exits immediately if unchanged.
     * 2. Never auto-pulls the full catalog on launch (prevents Spark Plan quota exhaustion).
     * 3. Pulls only delta documents with updatedAt > localLastSync and merges into Room preserving IPTV fields.
     */
    suspend fun syncAllFromFirebaseToRoom(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (!_firebaseSyncing.compareAndSet(expect = false, update = true)) return@withContext 0
        val lastDeltaMs = if (force) {
            0L
        } else {
            runCatching { prefs?.lastCloudDeltaSyncTime?.first() ?: 0L }.getOrDefault(0L)
        }
        try {
            if (!force) {
                val remoteLastUpdatedAt = firebaseCatalog.getCatalogLastUpdatedAt()
                if (remoteLastUpdatedAt < 0L) {
                    return@withContext 0
                }
                if (lastDeltaMs == 0L) {
                    val anchorTs = if (remoteLastUpdatedAt > 0L) remoteLastUpdatedAt else System.currentTimeMillis()
                    runCatching { prefs?.setLastCloudDeltaSyncTime(anchorTs) }
                    return@withContext 0
                }
                if (remoteLastUpdatedAt <= lastDeltaMs) {
                    return@withContext 0
                }
            }
            val syncStartMs = System.currentTimeMillis()
            var maxRemoteUpdatedAt = 0L
            _firebaseSyncMessage.value = if (lastDeltaMs > 0L) "جارٍ سحب التحديثات السحابية..." else "جارٍ تحميل مكتبة سحاب..."
            val outcome = firebaseCatalog.fetchAllCatalogBatchedOutcome(batchSize = 400L, sinceMs = lastDeltaMs) { batch, totalFetched ->
                for (item in batch) {
                    if (item.updatedAt > maxRemoteUpdatedAt) {
                        maxRemoteUpdatedAt = item.updatedAt
                    }
                }
                dao.mergeCloudBatchPreservingLocal(batch)
                _firebaseSyncMessage.value = "جارٍ مزامنة مكتبة سحاب... ($totalFetched عنصر)"
            }
            if (outcome.completedCleanly) {
                val cursorTs = maxRemoteUpdatedAt.takeIf { it > 0L } ?: syncStartMs
                runCatching { prefs?.setLastCloudDeltaSyncTime(cursorTs) }
            }
            outcome.fetched
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("SahabFirebaseSync", "syncAllFromFirebaseToRoom failed: ${e.message}")
            0
        } finally {
            _firebaseSyncing.value = false
            _firebaseSyncMessage.value = null
        }
    }

    suspend fun syncMovies(repo: Repository, forceFullRefresh: Boolean = false): Stats =
        syncMedia(repo, mediaType = "movie", forceFullRefresh = forceFullRefresh)

    suspend fun syncSeries(repo: Repository, forceFullRefresh: Boolean = false): Stats =
        syncMedia(repo, mediaType = "series", forceFullRefresh = forceFullRefresh)

    private suspend fun syncMedia(
        repo: Repository,
        mediaType: String,
        forceFullRefresh: Boolean = false,
    ): Stats = withContext(Dispatchers.IO) {
        withSyncLock {
            val start = System.currentTimeMillis()
            val isSeries = mediaType.equals("series", ignoreCase = true)
            val normalizedType = if (isSeries) "series" else "movie"
            val now = System.currentTimeMillis()

            val categories: Map<String, String> = runCatching {
                val cats = if (isSeries) repo.seriesCategories() else repo.movieCategories()
                if (cats.isNotEmpty()) {
                    relationalDao?.replaceCategories(
                        mediaType = normalizedType,
                        items = cats.mapIndexed { idx, c ->
                            CatalogCategoryEntity(
                                mediaType = normalizedType,
                                categoryId = c.id,
                                name = c.name,
                                sortOrder = idx,
                                updatedAt = now,
                            )
                        },
                    )
                }
                cats.associate { it.id to it.name }
            }.getOrElse { emptyMap() }

            val posters: List<Poster> = runCatching {
                if (isSeries) repo.seriesList(null) else repo.movies(null)
            }.getOrElse { emptyList() }

            if (posters.isEmpty()) {
                return@withSyncLock Stats(durationMs = System.currentTimeMillis() - start)
            }

            val existing = dao.loadAllProviderSyncSnapshot(normalizedType)

            var inserted = 0
            var updated = 0

            val indexedPosters = posters.mapIndexed { idx, poster -> idx to poster }
            indexedPosters.chunked(BATCH_SIZE).forEach { batch ->
                val newEntities = mutableListOf<CatalogMovieEntity>()
                val unchangedIds = mutableListOf<String>()
                val updatedFields = mutableListOf<ProviderSyncUpdate>()

                batch.forEach { (providerOrder, p) ->
                    val prev = existing[p.id]
                    val resolvedExt = if (isSeries) {
                        ""
                    } else {
                        p.ext.ifBlank { prev?.containerExtension.orEmpty() }.ifBlank { DEFAULT_VOD_EXT }
                    }
                    if (prev != null) {
                        val expectedSearch = buildCatalogSearchTitle(p.name, prev.titleEn, prev.identifiedTitle)
                        val changed = prev.categoryId != p.categoryId ||
                            prev.titleAr != p.name ||
                            prev.posterUrl != p.image ||
                            prev.containerExtension != resolvedExt ||
                            prev.providerOrder != providerOrder ||
                            prev.searchTitle != expectedSearch ||
                            prev.deleted != 0
                        if (changed) {
                            updatedFields.add(
                                ProviderSyncUpdate(
                                    streamId = p.id,
                                    categoryId = p.categoryId,
                                    titleAr = p.name,
                                    posterUrl = p.image,
                                    containerExtension = resolvedExt,
                                    added = p.added,
                                    providerOrder = providerOrder,
                                    searchTitle = expectedSearch,
                                ),
                            )
                        } else {
                            unchangedIds.add(p.id)
                        }
                        updated++
                    } else {
                        newEntities.add(
                            buildEntity(
                                p = p,
                                categoryName = categories[p.categoryId] ?: "",
                                now = now,
                                mediaType = normalizedType,
                                providerOrder = providerOrder,
                                resolvedExt = resolvedExt,
                            ),
                        )
                        inserted++
                    }
                }

                dao.applySyncBatch(unchangedIds, updatedFields, newEntities, now, normalizedType)
            }

            val activeExistingCount = existing.values.count { it.deleted == 0 }
            val isSuspiciousPartial = !forceFullRefresh &&
                activeExistingCount >= 50 &&
                posters.size < (activeExistingCount * 0.70).toInt()
            val deleted = if (isSuspiciousPartial) {
                android.util.Log.w(
                    "SahabCatalogSync",
                    "Skipped soft-delete for $normalizedType: partial response (${posters.size}/$activeExistingCount)",
                )
                0
            } else {
                dao.softDeleteBefore(cutoff = now, mediaType = normalizedType)
            }
            _syncVersion.value = System.currentTimeMillis()
            runCatching {
                relationalDao?.upsertSyncMeta(
                    SyncMetadataEntity(
                        syncKey = "iptv_$normalizedType",
                        lastSuccessAt = now,
                        itemCount = posters.size,
                        status = if (isSuspiciousPartial) "partial_guarded" else "ok",
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }

            Stats(
                total = posters.size,
                inserted = inserted,
                updated = updated,
                deleted = deleted,
                durationMs = System.currentTimeMillis() - start,
            )
        }
    }

    data class SeedStats(
        val total: Int = 0,
        val loaded: Int = 0,
        val durationMs: Long = 0L,
        val wasSkipped: Boolean = false,
    )

    suspend fun populateFromSeed(context: Context): SeedStats = withContext(Dispatchers.IO) {
        withSyncLock {
            val start = System.currentTimeMillis()
            val dbCount = dao.observeCount().first()
            val enrichedCount = dao.countEnriched()
            if (dbCount > 1000 && enrichedCount > 100) {
                return@withSyncLock SeedStats(
                    total = dbCount,
                    wasSkipped = true,
                    durationMs = System.currentTimeMillis() - start,
                )
            }

            val json = Json { ignoreUnknownKeys = true }
            var total = 0
            var loaded = 0

            try {
                val stream: java.io.InputStream = runCatching {
                    context.assets.open("databases/catalog_seed.json")
                }.getOrElse {
                    GZIPInputStream(context.assets.open("databases/catalog_seed.json.gz"))
                }
                stream.bufferedReader().use { reader ->
                    val batch = mutableListOf<CatalogMovieEntity>()
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty()) {
                            total++
                            try {
                                val decoded = json.decodeFromString<CatalogMovieEntity>(trimmed)
                                val entity = decoded.copy(
                                    novaStreamId = CatalogMovieEntity.cloudRefStreamId(decoded.sahabId),
                                    categoryId = "",
                                    providerOrder = Int.MAX_VALUE,
                                    episodesJson = "",
                                    needsCloudPush = 0,
                                    rating = RatingSanitizer.format(decoded.rating, decoded.tmdbVoteCount),
                                    searchTitle = buildCatalogSearchTitle(decoded.titleAr, decoded.titleEn, decoded.identifiedTitle),
                                )
                                batch.add(entity)
                                if (batch.size >= 500) {
                                    dao.upsertAll(batch)
                                    loaded += batch.size
                                    batch.clear()
                                }
                            } catch (_: Exception) {
                                // Skip malformed line
                            }
                        }
                        line = reader.readLine()
                    }
                    if (batch.isNotEmpty()) {
                        dao.upsertAll(batch)
                        loaded += batch.size
                    }
                }
            } catch (e: java.io.FileNotFoundException) {
                android.util.Log.e("SahabSeed", "catalog_seed.json.gz NOT FOUND in assets — skipping seed load, falling back to lazy enrichment", e)
                return@withSyncLock SeedStats(wasSkipped = true, durationMs = System.currentTimeMillis() - start)
            } catch (e: Exception) {
                android.util.Log.e("SahabSeed", "seed load failed — falling back to lazy enrichment", e)
                return@withSyncLock SeedStats(wasSkipped = true, durationMs = System.currentTimeMillis() - start)
            }

            if (loaded > 0) {
                _syncVersion.value = System.currentTimeMillis()
            }
            SeedStats(
                total = total,
                loaded = loaded,
                durationMs = System.currentTimeMillis() - start,
            )
        }
    }

    /**
     * Unified enrichment pipeline:
     * 1) Local Room cloud reference -> 2) Firebase `global_catalog` -> 3) TMDB match -> merge into Room
     * without overwriting user IPTV provider fields (`novaStreamId`, `categoryId`, `providerOrder`, `episodesJson`).
     */
    suspend fun enrichOne(
        repo: Repository,
        tmdb: com.saydlawy.sahab.data.TmdbApi,
        streamId: String,
        force: Boolean = false,
        mediaType: String = "movie",
    ): com.saydlawy.sahab.data.catalog.TmdbMatcher.EnrichResult? {
        val normalizedType = if (mediaType.equals("series", ignoreCase = true)) "series" else "movie"
        val lockKey = "$normalizedType:$streamId"
        if (!inFlightEnrich.add(lockKey)) return null
        return try {
            enrichOneInternal(repo, tmdb, streamId, force, normalizedType)
        } finally {
            inFlightEnrich.remove(lockKey)
        }
    }

    private suspend fun enrichOneInternal(
        repo: Repository,
        tmdb: com.saydlawy.sahab.data.TmdbApi,
        streamId: String,
        force: Boolean,
        normalizedType: String,
    ): com.saydlawy.sahab.data.catalog.TmdbMatcher.EnrichResult? {
        val existing = dao.findByStreamId(streamId, normalizedType) ?: return null
        if (!force && (existing.needsEnrichment == 0 || existing.needsEnrichment == -1) && existing.plot.isNotBlank()) {
            return null
        }

        val now = System.currentTimeMillis()
        if (!force && existing.needsEnrichment == 1 && existing.lastEnriched > 0L &&
            (now - existing.lastEnriched) < 24L * 3600_000L
        ) {
            return null
        }

        // 1. Check already-synced local Room cloud references first (0 network calls)
        if (!force) {
            val localCloudRef = findLocalCloudMatch(existing, normalizedType)
            if (localCloudRef != null && localCloudRef.plot.isNotBlank()) {
                dao.applyEnrichment(
                    streamId = streamId,
                    tmdbId = localCloudRef.tmdbId,
                    titleEn = localCloudRef.titleEn,
                    year = localCloudRef.year.ifBlank { existing.year },
                    plot = localCloudRef.plot,
                    cast = localCloudRef.cast,
                    director = localCloudRef.director,
                    genre = localCloudRef.genre,
                    runtime = localCloudRef.runtime,
                    rating = RatingSanitizer.format(localCloudRef.rating, localCloudRef.tmdbVoteCount),
                    backdropUrl = localCloudRef.backdropUrl,
                    voteCount = localCloudRef.tmdbVoteCount,
                    status = 0,
                    now = now,
                    mediaType = normalizedType,
                    postersJson = localCloudRef.postersJson,
                    trailerUrl = localCloudRef.trailerUrl,
                    castJson = localCloudRef.castJson,
                    searchTitle = buildCatalogSearchTitle(existing.titleAr, localCloudRef.titleEn, localCloudRef.identifiedTitle),
                    needsCloudPush = 0,
                )
                return null
            }
        }

        // 2. Query Firebase `global_catalog` only when a concrete tmdbId exists (avoids pending: read storms)
        val cloudLookupId = existing.tmdbId?.takeIf { it > 0 }?.let { CatalogMovieEntity.idFromTmdb(it) }
        val cloudData = if (!force && cloudLookupId != null) {
            runCatching { firebaseCatalog.getCatalogItem(cloudLookupId) }.getOrNull()
        } else {
            null
        }
        if (cloudData != null && cloudData.plot.isNotBlank()) {
            dao.applyEnrichment(
                streamId = streamId,
                tmdbId = cloudData.tmdbId,
                titleEn = cloudData.titleEn,
                year = cloudData.year.ifBlank { existing.year },
                plot = cloudData.plot,
                cast = cloudData.cast,
                director = cloudData.director,
                genre = cloudData.genre,
                runtime = cloudData.runtime,
                rating = RatingSanitizer.format(cloudData.rating, cloudData.tmdbVoteCount),
                backdropUrl = cloudData.backdropUrl,
                voteCount = cloudData.tmdbVoteCount,
                status = 0,
                now = now,
                mediaType = normalizedType,
                postersJson = cloudData.postersJson,
                trailerUrl = cloudData.trailerUrl,
                castJson = cloudData.castJson,
                searchTitle = buildCatalogSearchTitle(existing.titleAr, cloudData.titleEn, cloudData.identifiedTitle),
                needsCloudPush = 0,
            )
            return null
        }

        if (!tmdb.hasApiKey()) return null

        val catName = runCatching {
            val cats = if (normalizedType == "series") repo.seriesCategories() else repo.movieCategories()
            cats.firstOrNull { it.id == existing.categoryId }?.name ?: ""
        }.getOrElse { "" }

        val serverYear = if (force || existing.year.isBlank()) {
            runCatching {
                val rawYear = if (normalizedType == "series") {
                    repo.seriesDetails(streamId).year
                } else {
                    repo.movieDetails(streamId).year
                }
                YEAR_REGEX.find(rawYear)?.value?.toIntOrNull()
            }.getOrNull()
        } else {
            null
        }
        val novaYear = serverYear
            ?: com.saydlawy.sahab.data.catalog.TmdbMatcher.extractYear(existing.titleAr, catName)
            ?: if (!force) existing.year.toIntOrNull() else null
        val result = com.saydlawy.sahab.data.catalog.TmdbMatcher.enrich(
            tmdb = tmdb,
            rawName = existing.titleAr,
            categoryName = catName,
            novaYear = novaYear,
            mediaType = normalizedType,
        )

        val movie = result.movie
        if (result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.STRONG && movie != null) {
            val localByMatchedTmdb = runCatching { dao.findBestEnrichedByTmdbId(movie.id, normalizedType) }.getOrNull()
                ?.takeIf { it.sahabId != existing.sahabId }
            val resolvedPlot = movie.overview.ifBlank { localByMatchedTmdb?.plot.orEmpty() }
            val resolvedPosters = movie.posterUrls.joinToString("\n").ifBlank { localByMatchedTmdb?.postersJson.orEmpty() }
            val resolvedTrailer = movie.trailerUrl.ifBlank { localByMatchedTmdb?.trailerUrl.orEmpty() }
            val resolvedRating = RatingSanitizer.format(movie.voteAverage, movie.voteCount)
                .ifBlank { RatingSanitizer.format(localByMatchedTmdb?.rating, localByMatchedTmdb?.tmdbVoteCount) }
            val newId = CatalogMovieEntity.idFromTmdb(movie.id)
            dao.applyEnrichment(
                streamId = streamId,
                tmdbId = movie.id,
                titleEn = movie.title,
                year = movie.releaseDate.take(4),
                plot = resolvedPlot,
                cast = movie.credits?.cast?.take(5)?.joinToString(", ") { it.name } ?: "",
                director = movie.credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: "",
                genre = movie.genres.joinToString(", ") { it.name },
                runtime = movie.runtime?.let { "${it}m" } ?: "",
                rating = resolvedRating,
                backdropUrl = movie.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: "",
                voteCount = movie.voteCount,
                status = 0,
                now = now,
                mediaType = normalizedType,
                postersJson = resolvedPosters,
                trailerUrl = resolvedTrailer,
                castJson = encodeTmdbCastJson(movie.credits?.cast),
                searchTitle = buildCatalogSearchTitle(existing.titleAr, movie.title, existing.identifiedTitle),
                needsCloudPush = 1,
            )
            if (existing.sahabId.startsWith("pending:") || existing.sahabId.startsWith("series_pending:")) {
                runCatching { firebaseCatalog.linkPendingAliasToTmdb(streamId, newId, existing.titleAr, normalizedType) }
            }
            runCatching {
                dao.findByStreamId(streamId, normalizedType)?.let { enrichedEntity ->
                    maybeAutoUploadToCloud(enrichedEntity)
                }
            }
        } else if (result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.WEAK && movie != null) {
            dao.applyEnrichment(
                streamId = streamId,
                tmdbId = movie.id,
                titleEn = movie.title,
                year = movie.releaseDate.take(4),
                plot = movie.overview,
                cast = movie.credits?.cast?.take(5)?.joinToString(", ") { it.name } ?: "",
                director = movie.credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: "",
                genre = movie.genres.joinToString(", ") { it.name },
                runtime = movie.runtime?.let { "${it}m" } ?: "",
                rating = RatingSanitizer.format(movie.voteAverage, movie.voteCount),
                backdropUrl = movie.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: "",
                voteCount = movie.voteCount,
                status = 1,
                now = now,
                mediaType = normalizedType,
                postersJson = movie.posterUrls.joinToString("\n"),
                trailerUrl = movie.trailerUrl,
                castJson = encodeTmdbCastJson(movie.credits?.cast),
                searchTitle = buildCatalogSearchTitle(existing.titleAr, movie.title, existing.identifiedTitle),
                needsCloudPush = 0,
            )
        } else if (
            result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.NETWORK_ERROR ||
            (movie == null && (result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.STRONG ||
                result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.WEAK))
        ) {
            // Transient network/key error — keep needsEnrichment=1 to retry later.
        } else {
            dao.markFailed(streamId, now, normalizedType)
        }
        return result
    }

    private suspend fun findLocalCloudMatch(
        existing: CatalogMovieEntity,
        normalizedType: String,
    ): CatalogMovieEntity? {
        existing.tmdbId?.takeIf { it > 0 }?.let { tid ->
            val byTmdb = runCatching { dao.findBestEnrichedByTmdbId(tid, normalizedType) }.getOrNull()
            if (byTmdb != null && byTmdb.sahabId != existing.sahabId && byTmdb.plot.isNotBlank()) {
                return byTmdb
            }
        }
        val cleaned = cleanNormalizedLookupTitle(existing.titleAr.ifBlank { existing.titleEn })
        if (cleaned.length < 4) return null
        val candidates = runCatching { dao.findEnrichedCandidatesBySearchTitle(cleaned, normalizedType) }.getOrNull().orEmpty()
        if (candidates.isEmpty()) return null
        val itemYear = RatingSanitizer.extractFourDigitYear(existing.year.ifBlank { existing.titleAr })
        return candidates.firstOrNull { candidate ->
            if (candidate.sahabId == existing.sahabId || candidate.plot.isBlank()) return@firstOrNull false
            val candYear = RatingSanitizer.extractFourDigitYear(candidate.year)
            if (itemYear.isNotBlank() && candYear.isNotBlank() && itemYear != candYear) return@firstOrNull false
            val candAr = cleanNormalizedLookupTitle(candidate.titleAr)
            val candEn = cleanNormalizedLookupTitle(candidate.titleEn)
            val candIdentified = cleanNormalizedLookupTitle(candidate.identifiedTitle)
            cleaned == candAr || cleaned == candEn || cleaned == candIdentified
        }
    }

    private fun cleanNormalizedLookupTitle(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lowercase(Locale.ROOT)
            .replace(LOOKUP_PREFIX_PIPE_REGEX, "")
            .replace(LOOKUP_BRACKETS_REGEX, " ")
            .normalizeForSearch()
            .replace(LOOKUP_NOISE_REGEX, " ")
            .replace(LOOKUP_NON_ALNUM_REGEX, " ")
            .trim()
    }

    suspend fun applyManualTmdbMatch(
        tmdb: com.saydlawy.sahab.data.TmdbApi,
        streamId: String,
        tmdbId: Int,
        mediaType: String = "movie",
    ): Boolean {
        val normalizedType = if (mediaType.equals("series", ignoreCase = true)) "series" else "movie"
        val existing = dao.findByStreamId(streamId, normalizedType)
        val movie = if (normalizedType == "series") {
            tmdb.getTvDetailsById(tmdbId)
        } else {
            tmdb.getMovieDetailsById(tmdbId)
        } ?: return false

        val now = System.currentTimeMillis()
        val newId = CatalogMovieEntity.idFromTmdb(movie.id)
        val castStr = movie.credits?.cast?.take(5)?.joinToString(", ") { it.name } ?: ""
        val directorStr = movie.credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: ""
        val genreStr = movie.genres.joinToString(", ") { it.name }
        val runtimeStr = movie.runtime?.let { "${it}m" } ?: ""
        val ratingStr = RatingSanitizer.format(movie.voteAverage, movie.voteCount)
        val backdropStr = movie.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: ""
        val postersStr = movie.posterUrls.joinToString("\n")
        val castJsonStr = encodeTmdbCastJson(movie.credits?.cast)
        val updatedSearch = buildCatalogSearchTitle(
            existing?.titleAr.orEmpty().ifBlank { movie.title },
            movie.title,
            existing?.identifiedTitle.orEmpty(),
        )

        if (existing != null) {
            dao.applyEnrichment(
                streamId = streamId,
                tmdbId = movie.id,
                titleEn = movie.title,
                year = movie.releaseDate.take(4),
                plot = movie.overview,
                cast = castStr,
                director = directorStr,
                genre = genreStr,
                runtime = runtimeStr,
                rating = ratingStr,
                backdropUrl = backdropStr,
                voteCount = movie.voteCount,
                status = 0,
                now = now,
                mediaType = normalizedType,
                postersJson = postersStr,
                trailerUrl = movie.trailerUrl,
                castJson = castJsonStr,
                searchTitle = updatedSearch,
                needsCloudPush = 1,
            )
            if (existing.sahabId.startsWith("pending:") || existing.sahabId.startsWith("series_pending:")) {
                runCatching { firebaseCatalog.linkPendingAliasToTmdb(streamId, newId, existing.titleAr, normalizedType) }
            }
        } else {
            dao.upsert(
                CatalogMovieEntity(
                    sahabId = CatalogMovieEntity.idPending(streamId, normalizedType),
                    novaStreamId = streamId,
                    mediaType = normalizedType,
                    titleAr = movie.title,
                    titleEn = movie.title,
                    year = movie.releaseDate.take(4),
                    plot = movie.overview,
                    cast = castStr,
                    castJson = castJsonStr,
                    director = directorStr,
                    genre = genreStr,
                    runtime = runtimeStr,
                    rating = ratingStr,
                    posterUrl = movie.posterUrls.firstOrNull().orEmpty(),
                    backdropUrl = backdropStr,
                    tmdbId = movie.id,
                    tmdbVoteCount = movie.voteCount,
                    postersJson = postersStr,
                    trailerUrl = movie.trailerUrl,
                    needsEnrichment = 0,
                    lastEnriched = now,
                    lastSeen = now,
                    needsCloudPush = 1,
                    updatedAt = now,
                    searchTitle = updatedSearch,
                ),
            )
        }
        runCatching {
            dao.findByStreamId(streamId, normalizedType)?.let { enrichedEntity ->
                maybeAutoUploadToCloud(enrichedEntity)
            }
        }
        return true
    }

    private fun buildEntity(
        p: Poster,
        categoryName: String,
        now: Long,
        mediaType: String,
        providerOrder: Int,
        resolvedExt: String,
    ): CatalogMovieEntity {
        val year = extractYear(p.name, categoryName)
        return CatalogMovieEntity(
            sahabId = CatalogMovieEntity.idPending(p.id, mediaType),
            novaStreamId = p.id,
            categoryId = p.categoryId,
            titleAr = p.name,
            year = year,
            mediaType = mediaType,
            posterUrl = p.image,
            novaAdded = p.added,
            containerExtension = resolvedExt,
            needsEnrichment = 1,
            lastSeen = now,
            deleted = 0,
            providerOrder = providerOrder,
            searchTitle = buildCatalogSearchTitle(p.name),
        )
    }

    private fun extractYear(name: String, categoryName: String): String {
        val matches = YEAR_REGEX.findAll(name).toList()
        if (matches.isNotEmpty()) {
            val endThreshold = maxOf(0, name.length - 25)
            val trailing = matches.filter { it.range.first >= endThreshold }
            if (trailing.isNotEmpty()) return trailing.last().value
        }
        return YEAR_REGEX.find(categoryName)?.value ?: ""
    }

    /**
     * Admin-only controlled background enrichment batch:
     * discovers pending items in Room, enriches via TMDB, stamps updatedAt, and uploads to Firestore
     * so regular users only pull lightweight deltas. Regular users return 0 immediately.
     */
    suspend fun enrichPendingBatchForAdmin(
        repo: Repository,
        tmdb: com.saydlawy.sahab.data.TmdbApi,
        mediaType: String = "movie",
        batchSize: Int = 20,
    ): Int = withContext(Dispatchers.IO) {
        val uid = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        if (uid != com.saydlawy.sahab.data.auth.AuthManager.ADMIN_UID) return@withContext 0
        if (!tmdb.hasApiKey()) return@withContext 0

        val normalizedType = if (mediaType.equals("series", ignoreCase = true)) "series" else "movie"
        val pending = dao.pendingEnrichment(limit = batchSize.coerceIn(1, 50), mediaType = normalizedType)
        if (pending.isEmpty()) return@withContext 0

        var done = 0
        var enrichedCount = 0
        var failed = 0
        reportBackgroundEnrichProgress(0, pending.size, active = true, currentTitle = "بدء إثراء الأدمن ($normalizedType)...")
        try {
            for (item in pending) {
                val label = item.titleAr.ifBlank { item.titleEn }
                reportBackgroundEnrichProgress(done, pending.size, active = true, currentTitle = label, failed = failed)
                val res = runCatching {
                    enrichOne(repo, tmdb, item.novaStreamId, force = false, mediaType = normalizedType)
                }.getOrElse { err ->
                    if (err is kotlinx.coroutines.CancellationException) throw err
                    failed++
                    null
                }
                if (res?.level == TmdbMatcher.Level.STRONG) enrichedCount++
                done++
                reportBackgroundEnrichProgress(done, pending.size, active = true, currentTitle = label, failed = failed)
                kotlinx.coroutines.delay(300L)
            }
            val now = System.currentTimeMillis()
            runCatching {
                relationalDao?.upsertSyncMeta(
                    SyncMetadataEntity(
                        syncKey = "admin_enrich_$normalizedType",
                        lastSuccessAt = now,
                        itemCount = enrichedCount,
                        status = if (failed == 0) "ok" else "partial",
                        lastCursor = pending.lastOrNull()?.sahabId.orEmpty(),
                        updatedAt = now,
                    ),
                )
            }
        } finally {
            reportBackgroundEnrichProgress(done, pending.size, active = false, failed = failed)
        }
        enrichedCount
    }

    companion object {
        private const val BATCH_SIZE = 500
        private const val STALE_CUTOFF_MS = 24L * 3600_000L
        private val YEAR_REGEX = Regex("""\b(19[2-9]\d|20[0-3]\d)\b""")
        private val LOOKUP_PREFIX_PIPE_REGEX = Regex("""^[^|]{1,20}\|\s*""")
        private val LOOKUP_BRACKETS_REGEX = Regex("""\[.*?]|\(.*?\)""")
        private val LOOKUP_NOISE_REGEX = Regex("""\b(19\d\d|20\d\d|4k|uhd|fhd|hd|1080p|720p|bluray|web-dl|webrip|hdtv|vod|ar|en|sub|dub|s\d{1,2}|season\s*\d{1,2}|فيلم|مسلسل|انمي|كرتون|كارتون|الموسم|موسم|مدبلج|مترجم)\b""")
        private val LOOKUP_NON_ALNUM_REGEX = Regex("""[^\p{L}\p{N}]+""")
    }
}
