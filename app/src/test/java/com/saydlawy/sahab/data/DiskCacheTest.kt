package com.saydlawy.sahab.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiskCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun putAndGet_roundTripsValueWithinTtl() = runTest {
        val cache = DiskCache(tempFolder.root, maxCatalogBytes = 1024L * 1024L)
        val ok = cache.put("k1", String.serializer(), "hello_sahab")
        assertEquals(true, ok)
        val loaded = cache.get("k1", String.serializer(), ttlMs = 60_000L)
        assertEquals("hello_sahab", loaded)
    }

    @Test
    fun put_evictsOldestFilesWhenCatalogExceedsCap() = runTest {
        val payload = "A".repeat(180)
        // Small cap (~300 bytes) holds at most 1 payload (~182 bytes encoded) so older entries are evicted.
        val cache = DiskCache(tempFolder.root, maxCatalogBytes = 300L)

        cache.put("oldest", String.serializer(), payload)
        Thread.sleep(30)
        cache.put("middle", String.serializer(), payload)
        Thread.sleep(30)
        cache.put("newest", String.serializer(), payload)

        assertNull(cache.get("oldest", String.serializer(), ttlMs = 60_000L))
        assertNotNull(cache.get("newest", String.serializer(), ttlMs = 60_000L))
    }
}
