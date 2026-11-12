package com.saydlawy.sahab.data.catalog

import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

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
