package com.saydlawy.sahab.data.catalog

import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.Locale

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

    suspend fun syncMovies(repo: Repository): Stats = withContext(Dispatchers.IO) {
        if (_syncing.value) return@withContext Stats()
        _syncing.value = true
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

                if (existingInBatch.isNotEmpty()) {
                    dao.touchBulk(existingInBatch, now)
                }
                if (newEntities.isNotEmpty()) {
                    dao.upsertAll(newEntities)
                }
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
    suspend fun enrichOne(
        repo: Repository,
        tmdb: com.saydlawy.sahab.data.TmdbApi,
        streamId: String,
    ): com.saydlawy.sahab.data.catalog.TmdbMatcher.EnrichResult? {
        val existing = dao.findByStreamId(streamId) ?: return null
        if (existing.needsEnrichment == 0 || existing.needsEnrichment == -1) return null

        val now = System.currentTimeMillis()
        // WEAK match retry gate: if we tried in the last 24h, skip (avoid re-hitting TMDB on every reopen)
        if (existing.needsEnrichment == 1 && existing.lastEnriched > 0L &&
            (now - existing.lastEnriched) < 24L * 3600_000L
        ) {
            return null
        }
        val catName = runCatching {
            repo.movieCategories().firstOrNull { it.id == existing.categoryId }?.name ?: ""
        }.getOrElse { "" }

        val novaYear = existing.year.toIntOrNull()
        val result = com.saydlawy.sahab.data.catalog.TmdbMatcher.enrich(
            tmdb = tmdb,
            rawName = existing.titleAr,
            categoryName = catName,
            novaYear = novaYear,
        )

        val movie = result.movie
        if (result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.STRONG && movie != null) {
            val newId = CatalogMovieEntity.idFromTmdb(movie.id)
            dao.applyEnrichment(
                streamId = streamId,
                newId = newId,
                tmdbId = movie.id,
                titleEn = movie.title,
                year = movie.releaseDate.take(4),
                plot = movie.overview,
                cast = movie.credits?.cast?.take(5)?.joinToString(", ") { it.name } ?: "",
                director = movie.credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: "",
                genre = movie.genres.joinToString(", ") { it.name },
                runtime = movie.runtime?.let { "${it}m" } ?: "",
                rating = if (movie.voteAverage > 0) String.format(Locale.US, "%.1f", movie.voteAverage) else "",
                backdropUrl = movie.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: "",
                voteCount = movie.voteCount,
                status = 0,
                now = now,
            )
        } else if (result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.WEAK && movie != null) {
            // Weak match — store data but keep needsEnrichment=1 for potential retry
            val newId = CatalogMovieEntity.idFromTmdb(movie.id)
            dao.applyEnrichment(
                streamId = streamId,
                newId = newId,
                tmdbId = movie.id,
                titleEn = movie.title,
                year = movie.releaseDate.take(4),
                plot = movie.overview,
                cast = movie.credits?.cast?.take(5)?.joinToString(", ") { it.name } ?: "",
                director = movie.credits?.crew?.firstOrNull { it.job == "Director" }?.name ?: "",
                genre = movie.genres.joinToString(", ") { it.name },
                runtime = movie.runtime?.let { "${it}m" } ?: "",
                rating = if (movie.voteAverage > 0) String.format(Locale.US, "%.1f", movie.voteAverage) else "",
                backdropUrl = movie.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" } ?: "",
                voteCount = movie.voteCount,
                status = 1,
                now = now,
            )
        } else if (result.level == com.saydlawy.sahab.data.catalog.TmdbMatcher.Level.STRONG && movie == null) {
            // Search matched but details fetch failed — keep needsEnrichment=1 to retry later.
        } else {
            dao.markFailed(streamId, now)
        }
        return result
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
            containerExtension = p.ext.ifBlank { "m3u8" },
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
