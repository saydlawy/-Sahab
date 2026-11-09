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
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent disk cache for API responses (Xtream catalogs, movie/series details, EPG, and TMDB).
 * Each entry is stored in its own file whose `lastModified` timestamp tracks freshness (TTL).
 * TMDB entries live in a dedicated sub-folder so refreshing the IPTV playlist never wastes TMDB quota.
 */
class DiskCache(context: Context) {

    private val rootDir = File(context.applicationContext.cacheDir, "sahab_api_cache").apply { mkdirs() }
    private val catalogDir = File(rootDir, "catalog").apply { mkdirs() }
    private val tmdbDir = File(rootDir, "tmdb").apply { mkdirs() }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val locks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(key: String): Mutex = locks.getOrPut(key) { Mutex() }

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
    ) = withContext(Dispatchers.IO) {
        lockFor(key).withLock {
            runCatching {
                val dir = if (permanentNamespace) tmdbDir else catalogDir
                if (!dir.exists()) dir.mkdirs()
                val file = fileFor(key, permanentNamespace)
                val tmp = File(dir, "${file.name}.tmp")
                tmp.writeText(json.encodeToString(serializer, value), Charsets.UTF_8)
                if (file.exists()) file.delete()
                tmp.renameTo(file)
                file.setLastModified(System.currentTimeMillis())
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
        const val TTL_CATALOG_MS = 12 * 3600_000L       // 12 hours for categories & channel/VOD lists
        const val TTL_DETAILS_MS = 7 * 24 * 3600_000L   // 7 days for movie & series details
        const val TTL_TMDB_MS = 30L * 24 * 3600_000L    // 30 days for TMDB enriched data
        const val TTL_TMDB_MISS_MS = 7L * 24 * 3600_000L // 7 days negative cache when TMDB has no match
        const val TTL_EPG_MS = 30 * 60_000L             // 30 minutes for EPG program guide
    }
}

