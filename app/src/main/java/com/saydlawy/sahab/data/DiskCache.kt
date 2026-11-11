package com.saydlawy.sahab.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Persistent disk cache for API responses (Xtream catalogs, movie/series details, EPG, and TMDB).
 * Each entry is stored in its own file whose `lastModified` timestamp tracks freshness (TTL).
 * TMDB entries live in a dedicated sub-folder so refreshing the IPTV playlist never wastes TMDB quota.
 */
class DiskCache(
    private val rootDir: File,
    private val maxCatalogBytes: Long = MAX_CATALOG_BYTES,
) {

    constructor(context: Context) : this(
        rootDir = File(context.applicationContext.cacheDir, "sahab_api_cache"),
    )

    private val catalogDir = File(rootDir.apply { mkdirs() }, "catalog").apply { mkdirs() }
    private val tmdbDir = File(rootDir, "tmdb").apply { mkdirs() }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Fixed striped lock pool prevents unbounded ConcurrentHashMap growth and lock/eviction races.
    private val lockStripes = Array(LOCK_STRIPES) { Mutex() }

    private fun lockFor(key: String): Mutex =
        lockStripes[(key.hashCode() and Int.MAX_VALUE) % LOCK_STRIPES]

    suspend fun <T> get(
        key: String,
        serializer: KSerializer<T>,
        ttlMs: Long,
        permanentNamespace: Boolean = false,
    ): T? = withContext(Dispatchers.IO) {
        lockFor(key).withLock {
            val file = fileFor(key, permanentNamespace)
            if (!file.exists()) return@withLock null
            val age = System.currentTimeMillis() - file.lastModified()
            if (age > ttlMs) return@withLock null
            runCatching {
                val text = file.readText(Charsets.UTF_8)
                if (text.isBlank()) null else json.decodeFromString(serializer, text)
            }.getOrNull()
        }
    }

    suspend fun <T> put(
        key: String,
        serializer: KSerializer<T>,
        value: T,
        permanentNamespace: Boolean = false,
    ): Boolean = withContext(Dispatchers.IO) {
        lockFor(key).withLock {
            runCatching {
                val dir = if (permanentNamespace) tmdbDir else catalogDir
                if (!dir.exists()) dir.mkdirs()
                val file = fileFor(key, permanentNamespace)
                val tmp = File(dir, "${file.name}.tmp")
                tmp.writeText(json.encodeToString(serializer, value), Charsets.UTF_8)
                var renamed = tmp.renameTo(file)
                if (!renamed) {
                    if (file.exists()) file.delete()
                    renamed = tmp.renameTo(file)
                }
                if (!renamed) {
                    tmp.delete()
                    return@runCatching false
                }
                file.setLastModified(System.currentTimeMillis())
                if (!permanentNamespace) {
                    enforceCatalogLruLimitLocked()
                }
                true
            }.getOrDefault(false)
        }
    }

    private fun enforceCatalogLruLimitLocked() {
        val files = catalogDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= maxCatalogBytes) return
        val oldestFirst = files.sortedBy { it.lastModified() }
        for (candidate in oldestFirst) {
            if (totalBytes <= maxCatalogBytes) break
            val size = candidate.length()
            if (candidate.delete()) {
                totalBytes -= size
            }
        }
    }

    /** Clears IPTV catalog/EPG cache while keeping the TMDB quota cache intact. */
    fun clearCatalog() {
        runCatching {
            catalogDir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun fileFor(key: String, permanentNamespace: Boolean): File {
        val dir = if (permanentNamespace) tmdbDir else catalogDir
        return File(dir, "${sha256(key)}.json")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v < 16) sb.append('0')
            sb.append(v.toString(16))
        }
        return sb.toString()
    }

    companion object {
        private const val LOCK_STRIPES = 64
        const val MAX_CATALOG_BYTES = 200L * 1024L * 1024L // 200 MB LRU cap
        const val TTL_CATALOG_MS = 12 * 3600_000L       // 12 hours for categories & channel/VOD lists
        const val TTL_DETAILS_MS = 7 * 24 * 3600_000L   // 7 days for movie & series details
        const val TTL_TMDB_MS = 30L * 24 * 3600_000L    // 30 days for TMDB enriched data
        const val TTL_TMDB_MISS_MS = 7L * 24 * 3600_000L // 7 days negative cache when TMDB has no match
        const val TTL_EPG_MS = 30 * 60_000L             // 30 minutes for EPG program guide
    }
}
