package com.saydlawy.sahab.data.central

import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.Prefs
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.catalog.CatalogMovieDao
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy

class SahabCentralArchitectureTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun centralContentId_parsesTmdbAndSeparatesMovieVsSeriesPendingSchemes() {
        val tmdb = CentralContentId.parse("tmdb:550", "movie")
        assertEquals("tmdb", tmdb.scheme)
        assertEquals("550", tmdb.rawId)
        assertEquals("tmdb:550", tmdb.canonical)

        val moviePending = CentralContentId.fromStream("42", "movie")
        val seriesPending = CentralContentId.fromStream("42", "series")
        assertEquals("pending:42", moviePending.canonical)
        assertEquals("series_pending:42", seriesPending.canonical)
        assertEquals("movie", moviePending.mediaType)
        assertEquals("series", seriesPending.mediaType)
    }

    @Test
    fun localCentralAdapters_enforceZeroCostFlagsAndBridgePrefsAndRoom() = runTest {
        val dataStoreFile = tempFolder.newFile("central_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { dataStoreFile },
        )
        val prefs = Prefs(
            context = null,
            secureStoreOverride = InMemoryPrefs(),
            dataStoreOverride = dataStore,
        )
        prefs.saveAccount(Account(id = "acc-1", server = "https://tv.example", user = "u", pass = "p"))
        prefs.toggleFavorite(FavItem(Kind.MOVIE, "m10", "فيلم", accountId = "acc-1"))
        prefs.recordPlayback(RecentItem(Kind.MOVIE, "m10", "فيلم", positionMs = 15_000L, durationMs = 120_000L, accountId = "acc-1"))
        prefs.setLastCloudDeltaSyncTime(99_000L)

        val identity = LocalCentralIdentityAdapter(
            prefs = prefs,
            authManager = null,
            flags = CentralFeatureFlags(enableCentralSync = true, zeroCostMode = false, paidTierBillingAllowed = true),
        )
        assertEquals("acc-1", identity.currentAccountId())
        assertFalse(identity.isSignedInCloud)
        assertFalse(identity.isCatalogAdmin)
        val enforcedFlags = identity.featureFlags()
        assertTrue(enforcedFlags.zeroCostMode)
        assertFalse(enforcedFlags.paidTierBillingAllowed)

        val bridge = LocalCentralSyncBridgeAdapter(prefs) { item -> "https://stream.local/${item.id}.${item.ext}" }
        val envelope = bridge.buildEnvelope()
        assertEquals("acc-1", envelope.accountId)
        assertEquals(99_000L, envelope.deltaCursorMs)
        assertEquals(1, envelope.favoriteCount)
        assertEquals(1, envelope.recentCount)

        val grant = bridge.resolvePlaybackGrant(
            item = PlayItem(kind = Kind.MOVIE, id = "m10", title = "فيلم", ext = "mp4"),
            resumeMs = 15_000L,
        )
        assertEquals("https://stream.local/m10.mp4", grant.streamUrl)
        assertEquals(15_000L, grant.resumePositionMs)
        assertEquals(0.0, grant.costUsd, 0.0)

        NoOpZeroCostTelemetrySink.recordEvent("playback_started", mapOf("id" to "m10"))
        assertEquals(0.0, NoOpZeroCostTelemetrySink.totalEstimatedCostUsd(), 0.0)

        val sampleEntity = CatalogMovieEntity(
            sahabId = "tmdb:603",
            novaStreamId = "m10",
            mediaType = "movie",
            titleAr = "ماتريكس",
            titleEn = "The Matrix",
            year = "1999",
            rating = "8.7",
            tmdbId = 603,
            lastEnriched = 123_456L,
        )
        val fakeDao = Proxy.newProxyInstance(
            CatalogMovieDao::class.java.classLoader,
            arrayOf(CatalogMovieDao::class.java),
        ) { _, method, args ->
            when (method.name) {
                "findById" -> if (args?.firstOrNull() == "tmdb:603") sampleEntity else null
                "countByType" -> 1
                "searchCatalogPaged" -> listOf(sampleEntity)
                "getPaged" -> listOf(sampleEntity)
                else -> null
            }
        } as CatalogMovieDao

        val catalogAdapter = LocalRoomCentralCatalogAdapter(fakeDao)
        val loaded = catalogAdapter.getByContentId(CentralContentId.fromTmdb(603))
        assertNotNull(loaded)
        assertEquals("The Matrix", loaded?.titleEn)
        assertEquals(1, catalogAdapter.countItems("movie"))
        assertEquals(1, catalogAdapter.searchPaged("Matrix", "movie", 20, 0).size)

        val autoMapped = LocalThresholdContentMatcher.evaluateMatch("acc-1", "101", "movie", 603, 99)
        assertEquals(CentralMatchTier.AUTO_MATCH, autoMapped.tier)
        assertEquals("tmdb:603", autoMapped.globalContentId?.canonical)

        val reviewMapped = LocalThresholdContentMatcher.evaluateMatch("acc-1", "102", "series", 1396, 90)
        assertEquals(CentralMatchTier.ADMIN_REVIEW, reviewMapped.tier)
        assertEquals("tmdb:1396", reviewMapped.globalContentId?.canonical)

        val unmatched = LocalThresholdContentMatcher.evaluateMatch("acc-1", "103", "movie", 550, 72)
        assertEquals(CentralMatchTier.UNMATCHED, unmatched.tier)
        assertEquals("pending:103", unmatched.globalContentId?.canonical)

        val connector = LocalOfflineProviderConnector(dao = fakeDao)
        val conn = CentralConnectionDescriptor(
            connectionId = "acc-1",
            providerType = "xtream",
            providerIdentifier = "tv.example",
            credentialsRef = "keystore:acc-1",
            version = CentralCatalogVersion(schemaVersion = 1, generationId = 5L),
        )
        val version = connector.fetchCatalogVersion(conn)
        assertEquals(2, version.itemCount)
        val delta = connector.fetchDelta(conn, sinceGenerationId = 3L)
        assertEquals(5L, delta.nextGenerationId)
        assertTrue(delta.completedCleanly)
    }

    private class InMemoryPrefs : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false
            override fun putString(k: String?, v: String?) = apply { if (k != null) pending[k] = v }
            override fun putStringSet(k: String?, v: MutableSet<String>?) = apply { if (k != null) pending[k] = v }
            override fun putInt(k: String?, v: Int) = apply { if (k != null) pending[k] = v }
            override fun putLong(k: String?, v: Long) = apply { if (k != null) pending[k] = v }
            override fun putFloat(k: String?, v: Float) = apply { if (k != null) pending[k] = v }
            override fun putBoolean(k: String?, v: Boolean) = apply { if (k != null) pending[k] = v }
            override fun remove(k: String?) = apply { if (k != null) pending[k] = null }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean { apply(); return true }
            override fun apply() {
                if (clearAll) map.clear()
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
                pending.clear()
                clearAll = false
            }
        }
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }
}
