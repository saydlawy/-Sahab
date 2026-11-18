package com.saydlawy.sahab

import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.DiskCache
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.TmdbApi
import com.saydlawy.sahab.data.XtreamApi
import com.saydlawy.sahab.data.channel.ChannelSync
import com.saydlawy.sahab.util.ServerUrl
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
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
}
