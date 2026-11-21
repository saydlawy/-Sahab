package com.saydlawy.sahab.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class RepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

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

    @Test
    fun cachedPersistent_leaderCancellationDoesNotAbortIndependentAwaiter() = runTest {
        server.enqueue(
            MockResponse()
                .setBodyDelay(150, TimeUnit.MILLISECONDS)
                .setResponseCode(200)
                .setBody("""[{"category_id":"1","category_name":"أولى"}]"""),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"category_id":"2","category_name":"ثانية"}]"""),
        )

        val leader = async { repo.liveCategories(forceNetwork = false) }
        delay(20)
        val follower = async { repo.liveCategories() }
        leader.cancel(CancellationException("caller navigated away"))

        val result = follower.await()
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun m3uAccountsWithDifferentUrls_doNotShareDiskCache() = runTest {
        val disk = DiskCache(tempFolder.root)
        val diskRepo = Repository(
            api = XtreamApi(OkHttpClient()),
            tmdb = TmdbApi(OkHttpClient(), ""),
            disk = disk,
        )
        val base = server.url("/").toString().trimEnd('/')
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                #EXTM3U
                #EXTINF:-1 group-title="A",Channel One
                http://stream.one/1.m3u8
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                #EXTM3U
                #EXTINF:-1 group-title="B",Channel Two
                http://stream.two/2.m3u8
                """.trimIndent(),
            ),
        )

        diskRepo.setAccount(Account(id = "m3u-1", type = "M3U", m3uUrl = "$base/list1.m3u"))
        val first = diskRepo.liveChannels(null)
        assertEquals(listOf("Channel One"), first.map { it.name })

        diskRepo.setAccount(Account(id = "m3u-2", type = "M3U", m3uUrl = "$base/list2.m3u"))
        val second = diskRepo.liveChannels(null)
        assertEquals(listOf("Channel Two"), second.map { it.name })
    }

    @Test
    fun forceNetwork_concurrentCallsCoalesceIntoSingleNetworkRequest() = runTest {
        server.enqueue(
            MockResponse()
                .setBodyDelay(150, TimeUnit.MILLISECONDS)
                .setResponseCode(200)
                .setBody("""[{"category_id":"1","category_name":"رياضة"}]"""),
        )

        val (first, second) = awaitAll(
            async { repo.liveCategories(forceNetwork = true) },
            async { repo.liveCategories(forceNetwork = true) },
        )

        assertEquals(1, first.size)
        assertEquals(first, second)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun syncCatalog_evictsStaleCategorySubCaches() = runTest {
        // 1. Populate category sub-cache "vod:10" with initial movie
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"stream_id":"1","name":"Old Movie","category_id":"10","added":"100"}]""",
            ),
        )
        val before = repo.movies("10")
        assertEquals(listOf("Old Movie"), before.map { it.name })

        // 2. Sync catalog succeeds and returns updated catalog for category "10"
        val updatedMoviesJson = """[{"stream_id":"2","name":"Fresh Movie","category_id":"10","added":"200"}]"""
        repeat(7) { idx ->
            val path = server.url("/").toString() // dummy reference
            assertTrue(path.isNotBlank())
        }
        // Enqueue responses in any order via Dispatcher since syncCatalog launches parallel endpoints
        val syncServer = MockWebServer()
        syncServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                return when (request.requestUrl?.queryParameter("action")) {
                    null -> MockResponse().setBody("""{"user_info":{"status":"Active"},"server_info":{"url":"localhost"}}""")
                    "get_vod_streams" -> MockResponse().setBody(updatedMoviesJson)
                    else -> MockResponse().setBody("[]")
                }
            }
        }
        syncServer.start()
        try {
            val client = OkHttpClient.Builder().build()
            val syncRepo = Repository(
                api = XtreamApi(client),
                tmdb = TmdbApi(client, ""),
                disk = DiskCache(tempFolder.root),
            )
            syncRepo.setAccount(Account(id = "sync-acc", server = syncServer.url("/").toString().trimEnd('/'), user = "u", pass = "p"))

            // Prime stale category "10" cache directly via get_vod_streams?category_id=10
            val afterFirstLoad = syncRepo.movies("10")
            assertEquals(listOf("Fresh Movie"), afterFirstLoad.map { it.name })

            // Now change server response for next sync and verify sub-cache invalidates cleanly
            syncServer.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                    return when (request.requestUrl?.queryParameter("action")) {
                        null -> MockResponse().setBody("""{"user_info":{"status":"Active"},"server_info":{"url":"localhost"}}""")
                        "get_vod_streams" -> MockResponse().setBody("""[{"stream_id":"3","name":"Synced Movie V2","category_id":"10","added":"300"}]""")
                        else -> MockResponse().setBody("[]")
                    }
                }
            }
            val syncResult = syncRepo.syncCatalog(force = true)
            assertTrue(syncResult is Repository.SyncResult.Success)
            val afterSync = syncRepo.movies("10")
            assertEquals(listOf("Synced Movie V2"), afterSync.map { it.name })
        } finally {
            syncServer.shutdown()
        }
    }
}
