package com.saydlawy.sahab.data.catalog

import com.saydlawy.sahab.data.DEFAULT_VOD_EXT

import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import com.saydlawy.sahab.util.RatingSanitizer
import java.util.zip.GZIPInputStream

/**
 * Syncs Nova (Xtream) movies into the local Room catalog.
 *
 * Design:
 *  - Reads posters via Repository.movies(null) (respects existing cache/network logic)
 *  - Reads categories via Repository.movieCategories() (for year extraction)
 *  - Batches upserts (500 rows) to avoid memory spikes
 *  - Preserves existing enrichment (does not overwrite titleEn/plot/cast/etc.)
 *  - Soft-deletes movies that disappeared from Nova
 *  - Tracks its own `syncing` StateFlow (separate from Repository)
 */
class CatalogSync(
    private val dao: CatalogMovieDao,
    private val firebaseCatalog: FirebaseCatalogService,
    private val appContext: Context? = null,
) {
    data class Stats(
        val total: Int = 0,
        val inserted: Int = 0,
        val updated: Int = 0,
        val deleted: Int = 0,
        val durationMs: Long = 0L,
    )

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing

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
            nm.cancel(notifId)
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
                else "تم تحديث $done من $total عمل"
            )
            .setProgress(total, done.coerceAtMost(total), false)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        runCatching { nm.notify(notifId, notif) }
    }

    /**
     * يرفع جميع العناصر المكتملة محلياً في Room (التي لها tmdbId) إلى global_catalog في Firebase.
     */
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
        val items = dao.getAllEnriched()
        if (items.isEmpty()) return@withContext 0
        val total = items.size
        var done = 0
        var failed = 0
        var lastError = ""
        reportBackgroundEnrichProgress(0, total, active = true, currentTitle = "بدء رفع البيانات المحلية...")
        try {
            for (item in items) {
                val label = item.titleAr.ifBlank { item.titleEn }
                reportBackgroundEnrichProgress(done, total, active = true, currentTitle = label, failed = failed, lastError = lastError)
                runCatching {
                    firebaseCatalog.saveCatalogItem(item)
                }.onFailure { err ->
                    if (err is kotlinx.coroutines.CancellationException) throw err
                    failed += 1
                    lastError = err.message?.take(120) ?: err::class.java.simpleName
                    android.util.Log.w("SAHAB_CLOUD", "pushLocalEnrichedToCloud failed [$label]: $lastError", err)
                }
                done += 1
                reportBackgroundEnrichProgress(done, total, active = true, currentTitle = label, failed = failed, lastError = lastError)
            }
        } finally {
            reportBackgroundEnrichProgress(done, total, active = false, failed = failed, lastError = lastError)
        }
        (done - failed).coerceAtLeast(0)
    }

    /**
     * يسحب جميع بيانات كتالوج Firebase (`global_catalog`) إلى Room على دفعات
     * مع تحديث [firebaseSyncing] و[firebaseSyncMessage] ليظهر شعار دائم للمستخدم لحين الانتهاء.
     */
    suspend fun syncAllFromFirebaseToRoom(force: Boolean = false): Int = withContext(Dispatchers.IO) {
        if (!force && dao.countEnriched() >= 200) return@withContext 0
        if (!_firebaseSyncing.compareAndSet(expect = false, update = true)) return@withContext 0
        _firebaseSyncMessage.value = "جارٍ تحميل مكتبة سحاب..."
        try {
            firebaseCatalog.fetchAllCatalogBatched(batchSize = 400L) { batch, totalFetched ->
                dao.upsertAll(batch)
                _firebaseSyncMessage.value = "جارٍ مزامنة مكتبة سحاب... ($totalFetched عنصر)"
            }
        } catch (e: Exception) {
            android.util.Log.w("SahabFirebaseSync", "syncAllFromFirebaseToRoom failed: ${e.message}")
            0
        } finally {
            _firebaseSyncing.value = false
            _firebaseSyncMessage.value = null
        }
    }

    suspend fun syncMovies(repo: Repository): Stats = withContext(Dispatchers.IO) {
        if (!_syncing.compareAndSet(expect = false, update = true)) return@withContext Stats()
        val start = System.currentTimeMillis()
        try {
            val now = System.currentTimeMillis()

            // 1. Fetch categories (for year extraction from category name)
            val categories: Map<String, String> = runCatching {
                repo.movieCategories().associate { it.id to it.name }
            }.getOrElse { emptyMap() }

            // 2. Fetch posters (uses Repository cache; goes to Nova only if needed)
            val posters: List<Poster> = runCatching {
                repo.movies(null)
            }.getOrElse { emptyList() }

            if (posters.isEmpty()) {
                return@withContext Stats(durationMs = System.currentTimeMillis() - start)
            }

            // 3. Existing stream IDs — to decide upsert vs touch
            val existing: Set<String> = dao.allStreamIds().toHashSet()

            var inserted = 0
            var updated = 0

            // 4. Process in batches of 500 (SQLite parameter limit ~999)
            posters.chunked(BATCH_SIZE).forEach { batch ->
                val newEntities = mutableListOf<CatalogMovieEntity>()
                val existingInBatch = mutableListOf<String>()

                batch.forEach { p ->
                    if (p.id in existing) {
                        existingInBatch.add(p.id)
                        updated++
                    } else {
                        newEntities.add(buildEntity(p, categories[p.categoryId] ?: "", now))
                        inserted++
                    }
                }

                dao.applySyncBatch(existingInBatch, newEntities, now, "movie")
            }

            // 5. Soft-delete stale (movies not seen for >24h)
            val deleted = dao.softDeleteBefore(cutoff = now - STALE_CUTOFF_MS)

            Stats(
                total = posters.size,
                inserted = inserted,
                updated = updated,
                deleted = deleted,
                durationMs = System.currentTimeMillis() - start,
            )
        } finally {
            _syncing.value = false
        }
    }

    /**
     * Enriches a single movie. Fetches from TMDB via TmdbMatcher, applies to Room.
     * Safe to call from ViewModel. Does nothing if already enriched or marked failed.
     */
    data class SeedStats(
        val total: Int = 0,
        val loaded: Int = 0,
        val durationMs: Long = 0L,
        val wasSkipped: Boolean = false,
    )

    /**
     * Reads catalog_seed.json.gz from assets and populates Room.
     * Skips if DB already has enrichment (dbCount > 1000 AND enrichedCount > 100).
     * Uses batched upserts (500) to stay under SQLite limits.
     * Sets _syncing flag so SyncOverlay shows the operation.
     */
    suspend fun populateFromSeed(context: Context): SeedStats = withContext(Dispatchers.IO) {
        if (!_syncing.compareAndSet(expect = false, update = true)) return@withContext SeedStats(wasSkipped = true)
        val start = System.currentTimeMillis()
        try {
            val dbCount = dao.observeCount().first()
            val enrichedCount = dao.countEnriched()
            if (dbCount > 1000 && enrichedCount > 100) {
                return@withContext SeedStats(
                    total = dbCount,
                    wasSkipped = true,
                    durationMs = System.currentTimeMillis() - start,
                )
            }

            val json = Json { ignoreUnknownKeys = true }
            var total = 0
            var loaded = 0

            try {
                // AAPT2/workflow may unpack .gz during build, producing
                // "catalog_seed.json" (uncompressed) inside the APK.
                // Try .json first, fall back to original .gz name.
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
                                    rating = RatingSanitizer.format(decoded.rating, decoded.tmdbVoteCount),
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
                return@withContext SeedStats(wasSkipped = true, durationMs = System.currentTimeMillis() - start)
            } catch (e: Exception) {
                android.util.Log.e("SahabSeed", "seed load failed — falling back to lazy enrichment", e)
                return@withContext SeedStats(wasSkipped = true, durationMs = System.currentTimeMillis() - start)
            }

            SeedStats(
                total = total,
                loaded = loaded,
                durationMs = System.currentTimeMillis() - start,
            )
        } finally {
            _syncing.value = false
        }
    }

    suspend fun enrichOne(
        repo: Repository,
        tmdb: com.saydlawy.sahab.data.TmdbApi,
        streamId: String,
        force: Boolean = false,
        mediaType: String = "movie",
    ): com.saydlawy.sahab.data.catalog.TmdbMatcher.EnrichResult? {
        val normalizedType = mediaType.ifBlank { "movie" }
        val existing = dao.findByStreamId(streamId, normalizedType) ?: return null
        if (!force && (existing.needsEnrichment == 0 || existing.needsEnrichment == -1)) return null

        val now = System.currentTimeMillis()
        // WEAK match retry gate: if we tried in the last 24h, skip (avoid re-hitting TMDB on every reopen)
        if (!force && existing.needsEnrichment == 1 && existing.lastEnriched > 0L &&
            (now - existing.lastEnriched) < 24L * 3600_000L
        ) {
            return null
        }

        // 1. البحث في السحابة (Firebase) فقط إذا كان للعنصر tmdbId فعلي (تجنباً لاستنزاف القراءات بطلبات pending:)
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
                year = cloudData.year,
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
            )
            return null // تم الإثراء من Firebase بنجاح دون استدعاء TMDB
        }
        if (!tmdb.hasApiKey()) return null

        val catName = runCatching {
            val cats = if (normalizedType.equals("series", ignoreCase = true)) {
                repo.seriesCategories()
            } else {
                repo.movieCategories()
            }
            cats.firstOrNull { it.id == existing.categoryId }?.name ?: ""
        }.getOrElse { "" }

        val serverYear = if (force || existing.year.isBlank()) {
            runCatching {
                val rawYear = if (normalizedType.equals("series", ignoreCase = true)) {
                    repo.seriesDetails(streamId).year
                } else {
                    repo.movieDetails(streamId).year
                }
                Regex("(19|20)\\d{2}").find(rawYear)?.value?.toIntOrNull()
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
            val newId = CatalogMovieEntity.idFromTmdb(movie.id)
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
                status = 0,
                now = now,
                mediaType = normalizedType,
                postersJson = movie.posterUrls.joinToString("\n"),
                trailerUrl = movie.trailerUrl,
                castJson = encodeTmdbCastJson(movie.credits?.cast),
            )
            if (existing.sahabId.startsWith("pending:")) {
                runCatching { firebaseCatalog.linkPendingAliasToTmdb(streamId, newId, existing.titleAr) }
            }
            runCatching {
                dao.findByStreamId(streamId, normalizedType)?.let { enrichedEntity ->
                    firebaseCatalog.saveCatalogItem(enrichedEntity)
                }
            }
        } else if (result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.WEAK && movie != null) {
            // Weak match — store in local Room only (keep needsEnrichment=1), never pollute shared Firebase catalog
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
            )
        } else if (
            result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.NETWORK_ERROR ||
            (movie == null && (result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.STRONG ||
                result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.WEAK))
        ) {
            // Transient network/key error or details fetch failed — keep needsEnrichment=1 to retry later.
        } else {
            dao.markFailed(streamId, now, normalizedType)
        }
        return result
    }

    suspend fun applyManualTmdbMatch(
        tmdb: com.saydlawy.sahab.data.TmdbApi,
        streamId: String,
        tmdbId: Int,
        mediaType: String = "movie",
    ): Boolean {
        val normalizedType = mediaType.ifBlank { "movie" }
        val existing = dao.findByStreamId(streamId, normalizedType) ?: return false
        val movie = if (normalizedType.equals("series", ignoreCase = true)) {
            tmdb.getTvDetailsById(tmdbId)
        } else {
            tmdb.getMovieDetailsById(tmdbId)
        } ?: return false

        val now = System.currentTimeMillis()
        val newId = CatalogMovieEntity.idFromTmdb(movie.id)
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
            status = 0,
            now = now,
            mediaType = normalizedType,
            postersJson = movie.posterUrls.joinToString("\n"),
            trailerUrl = movie.trailerUrl,
            castJson = encodeTmdbCastJson(movie.credits?.cast),
        )
        if (existing.sahabId.startsWith("pending:")) {
            runCatching { firebaseCatalog.linkPendingAliasToTmdb(streamId, newId, existing.titleAr) }
        }
        runCatching {
            dao.findByStreamId(streamId, normalizedType)?.let { enrichedEntity ->
                firebaseCatalog.saveCatalogItem(enrichedEntity)
            }
        }
        return true
    }

    private fun buildEntity(
        p: Poster,
        categoryName: String,
        now: Long,
    ): CatalogMovieEntity {
        val year = extractYear(p.name, categoryName)
        return CatalogMovieEntity(
            sahabId = CatalogMovieEntity.idPending(p.id),
            novaStreamId = p.id,
            categoryId = p.categoryId,
            titleAr = p.name,
            year = year,
            posterUrl = p.image,
            novaAdded = p.added,
            containerExtension = p.ext.ifBlank { DEFAULT_VOD_EXT },
            needsEnrichment = 1,
            lastSeen = now,
            deleted = 0,
        )
    }

    /**
     * Prefer trailing year (avoids extracting 1917 from title "1917").
     * Falls back to category name, then empty.
     */
    private fun extractYear(name: String, categoryName: String): String {
        val matches = YEAR_REGEX.findAll(name).toList()
        if (matches.isNotEmpty()) {
            val endThreshold = maxOf(0, name.length - 25)
            val trailing = matches.filter { it.range.first >= endThreshold }
            if (trailing.isNotEmpty()) return trailing.last().value
        }
        return YEAR_REGEX.find(categoryName)?.value ?: ""
    }

    companion object {
        private const val BATCH_SIZE = 500
        private const val STALE_CUTOFF_MS = 24L * 3600_000L
        private val YEAR_REGEX = Regex("""\b(19[2-9]\d|20[0-3]\d)\b""")
    }
}
