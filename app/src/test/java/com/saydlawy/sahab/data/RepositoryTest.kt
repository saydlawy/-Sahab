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
        repo.setAccount(Account(server = base, user = "alice user", pass = "p@ss/1"))
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

    @Test
    fun shortEpg_cachesInMemoryForPeekShortEpg() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"epg_listings":[{"title":"أخبار","description":"","start_timestamp":"1700000000","stop_timestamp":"1700003600"}]}""",
            ),
        )
        assertEquals(null, repo.peekShortEpg("55", 2))
        val loaded = repo.shortEpg("55", 2)
        assertEquals(1, loaded.size)
        assertEquals(loaded, repo.peekShortEpg("55", 2))
    }

    @Test
    fun movieDetails_fetchesAndParsesDetailsBySingleId() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"info":{"name":"الرسالة","plot":"قصة تاريخية","genre":"تاريخي","releasedate":"1976-01-01","duration":"177m","rating":"8.5","backdrop_path":["https://img/bg.jpg"]},"movie_data":{"name":"الرسالة","container_extension":"mp4"}}""",
            ),
        )
        val details = repo.movieDetails("777")
        assertEquals("777", details.id)
        assertEquals("الرسالة", details.name)
        assertEquals("قصة تاريخية", details.plot)
        assertEquals("1976", details.year)
        assertEquals("177m", details.duration)
        assertEquals("8.5", details.rating)
    }
}
