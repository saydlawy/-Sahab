package com.saydlawy.sahab.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class RepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder().build()
        repo = Repository(
            api = XtreamApi(client),
            tmdb = TmdbApi(client, ""),
            disk = null,
        )
        val base = server.url("/").toString().trimEnd('/')
        repo.setAccount(Account(base, "alice user", "p@ss/1"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun streamUrl_buildsEncodedLiveMovieAndEpisodeUrls() {
        val base = server.url("/").toString().trimEnd('/')
        val live = repo.streamUrl(PlayItem(Kind.LIVE, "101", "Live"))
        val movie = repo.streamUrl(PlayItem(Kind.MOVIE, "202", "Movie", ext = "mkv"))
        val ep = repo.streamUrl(PlayItem(Kind.EPISODE, "303", "Ep"))

        assertEquals("$base/live/alice%20user/p%40ss%2F1/101.m3u8", live)
        assertEquals("$base/movie/alice%20user/p%40ss%2F1/202.mkv", movie)
        assertEquals("$base/series/alice%20user/p%40ss%2F1/303.mp4", ep)
    }

    @Test
    fun liveCategories_concurrentCallsDeduplicateIntoSingleNetworkRequest() = runTest {
        server.enqueue(
            MockResponse()
                .setBodyDelay(150, TimeUnit.MILLISECONDS)
                .setResponseCode(200)
                .setBody("""[{"category_id":"1","category_name":"رياضة"},{"category_id":"2","category_name":"أفلام"}]"""),
        )

        val (first, second) = awaitAll(
            async { repo.liveCategories() },
            async { repo.liveCategories() },
        )

        assertEquals(2, first.size)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }
}
