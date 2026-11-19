package com.saydlawy.sahab.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
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

    private val catalogEvictionLock = Any()

    @Volatile
    private var estimatedCatalogBytes: Long = -1L

    private fun lockFor(key: String): Mutex =
        lockStripes[(key.hashCode() and Int.MAX_VALUE) % LOCK_STRIPES]

    @OptIn(ExperimentalSerializationApi::class)
    suspend fun <T> get(
        key: String,
        serializer: KSerializer<T>,
        ttlMs: Long,
        permanentNamespace: Boolean = false,
    ): T? = withContext(Dispatchers.IO) {
        lockFor(key).withLock {
            val file = fileFor(key, permanentNamespace)
            if (!file.exists() || file.length() == 0L) return@withLock null
            val age = System.currentTimeMillis() - file.lastModified()
            if (age > ttlMs) return@withLock null
            runCatching {
                BufferedInputStream(file.inputStream(), 64 * 1024).use { input ->
                    json.decodeFromStream(serializer, input)
                }
            }.getOrNull()
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
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
                val previousBytes = if (!permanentNamespace && file.exists()) file.length() else 0L
                val tmp = File(dir, "${file.name}.tmp")
                BufferedOutputStream(tmp.outputStream(), 64 * 1024).use { output ->
                    json.encodeToStream(serializer, value, output)
                }
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
                    onCatalogPut(file.length() - previousBytes)
                }
                true
            }.getOrDefault(false)
        }
    }

    private fun onCatalogPut(deltaBytes: Long) {
        synchronized(catalogEvictionLock) {
            if (estimatedCatalogBytes < 0L) {
                val files = catalogDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }.orEmpty()
                estimatedCatalogBytes = files.sumOf { it.length() }
            } else {
                estimatedCatalogBytes = (estimatedCatalogBytes + deltaBytes).coerceAtLeast(0L)
            }
            if (estimatedCatalogBytes > maxCatalogBytes) {
                enforceCatalogLruLimitLocked()
            }
        }
    }

    private fun enforceCatalogLruLimitLocked() {
        val files = catalogDir.listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") } ?: return
        var totalBytes = files.sumOf { it.length() }
        if (totalBytes <= maxCatalogBytes) {
            estimatedCatalogBytes = totalBytes
            return
        }
        val oldestFirst = files.sortedBy { it.lastModified() }
        for (candidate in oldestFirst) {
            if (totalBytes <= maxCatalogBytes) break
            val size = candidate.length()
            if (candidate.delete()) {
                totalBytes -= size
            }
        }
        estimatedCatalogBytes = totalBytes.coerceAtLeast(0L)
    }

    /** Clears IPTV catalog/EPG cache while keeping the TMDB quota cache intact. */
    fun clearCatalog() {
        runCatching {
            synchronized(catalogEvictionLock) {
                val remaining = catalogDir.listFiles()?.filter { file ->
                    file.isFile && !file.delete()
                }.orEmpty()
                estimatedCatalogBytes = remaining.sumOf { it.length() }.coerceAtLeast(0L)
            }
        }
    }

    private fun fileFor(key: String, permanentNamespace: Boolean): File {
        val dir = if (permanentNamespace) tmdbDir else catalogDir
        return File(dir, "${sha256(key)}.json")
    }

    private fun sha256(input: String): String {
        val digest = SHA256_LOCAL.get()!!
        digest.reset()
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
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
        private val SHA256_LOCAL: ThreadLocal<MessageDigest> =
            ThreadLocal.withInitial { MessageDigest.getInstance("SHA-256") }
        const val MAX_CATALOG_BYTES = 200L * 1024L * 1024L // 200 MB LRU cap
        const val TTL_CATALOG_MS = 12 * 3600_000L       // 12 hours for categories & channel/VOD lists
        const val TTL_DETAILS_MS = 7 * 24 * 3600_000L   // 7 days for movie & series details
        const val TTL_TMDB_MS = 30L * 24 * 3600_000L    // 30 days for TMDB enriched data
        const val TTL_TMDB_MISS_MS = 7L * 24 * 3600_000L // 7 days negative cache when TMDB has no match
        const val TTL_EPG_MS = 30 * 60_000L             // 30 minutes for EPG program guide
    }
}
