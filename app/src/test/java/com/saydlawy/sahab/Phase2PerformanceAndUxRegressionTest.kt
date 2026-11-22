package com.saydlawy.sahab

import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.DiskCache
import com.saydlawy.sahab.data.Episode
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.TmdbApi
import com.saydlawy.sahab.data.XtreamApi
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.catalog.buildCatalogSearchTitle
import com.saydlawy.sahab.data.catalog.encodeEpisodesJson
import com.saydlawy.sahab.data.catalog.parseEpisodesJson
import com.saydlawy.sahab.data.channel.ChannelSync
import com.saydlawy.sahab.ui.browse.BrowseUiState
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.ServerUrl
import com.saydlawy.sahab.util.normalizeForSearch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Phase2PerformanceAndUxRegressionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun xtreamStreamingParser_handlesUnquotedIntsNullsAndNonArrayPayloads() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val client = OkHttpClient()
            val disk = DiskCache(tempFolder.root)
            val repo = Repository(XtreamApi(client), TmdbApi(client, ""), disk = disk)
            val base = server.url("/").toString().trimEnd('/')
            repo.setAccount(Account(id = "acc-stream", server = base, user = "u", pass = "p"))

            server.enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """
                    [
                      {"stream_id": 101, "name": "قناة الرياضة", "stream_icon": null, "category_id": 5, "epg_channel_id": "epg.1", "added": 200},
                      {"stream_id": "102", "name": "قناة الأخبار", "stream_icon": "https://img/news.png", "category_id": "6", "epg_channel_id": "", "added": "300"}
                    ]
                    """.trimIndent(),
                ),
            )
            val channels = repo.liveChannels(null, forceNetwork = true)
            assertEquals(listOf("102", "101"), channels.map { it.id })
            assertEquals(listOf("5", "6"), channels.sortedBy { it.id }.map { it.categoryId })

            // Non-array JSON error object must degrade gracefully to emptyList
            server.enqueue(MockResponse().setResponseCode(200).setBody("""{"error":"empty"}"""))
            val emptyMovies = repo.movies("999", forceNetwork = true)
            assertTrue(emptyMovies.isEmpty())

            // Verify DiskCache streaming roundtrip
            val cached = listOf(Channel(id = "55", name = "مباشر", categoryId = "1", epgId = "e1"))
            assertTrue(disk.put("stream_test", ListSerializer(Channel.serializer()), cached))
            assertEquals(cached, disk.get("stream_test", ListSerializer(Channel.serializer()), ttlMs = 60_000L))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun channelSyncSlug_andServerUrlNormalization_handleArabicAndHostPortCleanly() {
        assertEquals("bein_sports_1_hd", ChannelSync.slug("  beIN SPORTS 1 [HD]!  "))
        assertEquals("قناة_العربية", ChannelSync.slug("  قناة   العربية!! "))

        assertEquals("http://portal.tv:8080", ServerUrl.normalizeServer("  portal.tv:8080/c/ "))
        assertEquals("https://secure.tv", ServerUrl.normalizeServer("https://secure.tv/player_api.php?a=1"))
        assertNull(ServerUrl.normalizeServer("   "))
    }

    @Test
    fun browseUiState_decouplesFocusedDetailsSoFocusTransitionsPreserveStructuralEquality() {
        val fieldNames = BrowseUiState::class.java.declaredFields.map { it.name }
        assertFalse(
            "BrowseUiState must not hold focusedDetails directly (AUD-002)",
            "focusedDetails" in fieldNames,
        )

        val categories = listOf(Category(id = "10", name = "أفلام أكشن"))
        val posterA = Poster(id = "m1", name = "فيلم ١", year = "2024", rating = "8.1")
        val posterB = Poster(id = "m2", name = "فيلم ٢", year = "2025", rating = "7.5")

        val stateBeforeFocusMove = BrowseUiState(
            categories = categories,
            selectedId = "10",
            items = listOf(posterA, posterB),
            loadingCategories = false,
        )
        val stateAfterFocusMove = BrowseUiState(
            categories = categories,
            selectedId = "10",
            items = listOf(posterA, posterB),
            loadingCategories = false,
        )

        val focusA = FocusedPosterDetails(poster = posterA, year = "2024", rating = "8.1")
        val focusB = FocusedPosterDetails(poster = posterB, year = "2025", rating = "7.5")

        assertNotEquals(focusA, focusB)
        assertEquals(stateBeforeFocusMove, stateAfterFocusMove)
        assertEquals(stateBeforeFocusMove.visibleItems, stateAfterFocusMove.visibleItems)
    }

    @Test
    fun arabicLocalization_rendersChannelsCountDistinctlyFromVodItemsCount() {
        assertEquals("1529 قناة", Ar.channelsCount(1529))
        assertEquals("1529 عنوان", Ar.itemsCount(1529))
    }

    @Test
    fun roomArabicSearchNormalizationAndEpisodesSerialization_roundtripSafely() {
        val searchTitle = buildCatalogSearchTitle("[4K] أحمد [مترجم] في الإسكندرية", "Ahmad in Alexandria")
        assertTrue(searchTitle.contains("احمد في الاسكندريه"))
        assertTrue(searchTitle.contains("احمد".normalizeForSearch()))
        assertTrue(searchTitle.contains("alexandria"))

        val episodes = listOf(
            Episode(id = "e1", season = 1, number = 1, title = "الحلقة الأولى", ext = "mp4", duration = "45m"),
            Episode(id = "e2", season = 1, number = 2, title = "الحلقة الثانية", ext = "mkv", duration = "48m"),
        )
        val encoded = encodeEpisodesJson(episodes)
        assertTrue(encoded.isNotBlank())
        assertEquals(episodes, parseEpisodesJson(encoded))
        assertEquals(emptyList<Episode>(), parseEpisodesJson(""))

        val entity = CatalogMovieEntity(
            sahabId = CatalogMovieEntity.idPending("55", "series"),
            novaStreamId = "55",
            mediaType = "series",
            providerOrder = 0,
            episodesJson = encoded,
            searchTitle = searchTitle,
        )
        assertTrue(entity.isProviderItem)
        assertEquals(120L * 1024L * 1024L, DiskCache.MAX_CATALOG_BYTES)
        assertEquals(60L * 1024L * 1024L, DiskCache.MAX_TMDB_BYTES)
    }
}
