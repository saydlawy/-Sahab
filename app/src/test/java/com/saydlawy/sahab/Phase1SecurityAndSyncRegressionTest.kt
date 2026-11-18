package com.saydlawy.sahab

import com.saydlawy.sahab.data.GithubReleaseUpdater
import com.saydlawy.sahab.data.ai.ContentType
import com.saydlawy.sahab.data.ai.GeminiResult
import com.saydlawy.sahab.data.ai.GeminiStoryService
import com.saydlawy.sahab.data.auth.UserSyncService
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.catalog.FirebaseCatalogService
import com.saydlawy.sahab.ui.player.SahabPlaybackService
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase1SecurityAndSyncRegressionTest {

    @Test
    fun mediaSessionCallerGate_allowsOwnAppOrTrustedSystemAndBlocksUntrustedThirdParty() {
        assertTrue(SahabPlaybackService.isAllowedMediaController(uid = 10123, myUid = 10123, isTrusted = false))
        assertTrue(SahabPlaybackService.isAllowedMediaController(uid = 1000, myUid = 10123, isTrusted = true))
        assertFalse(SahabPlaybackService.isAllowedMediaController(uid = 10999, myUid = 10123, isTrusted = false))
    }

    @Test
    fun streamUrlRedaction_stripsXtreamCredentialsAndQueryTokens() {
        assertEquals(
            "",
            UserSyncService.redactCredentialsFromStreamUrl("http://tv.example.com:8080/live/alice/secret123/42.m3u8"),
        )
        assertEquals(
            "",
            UserSyncService.redactCredentialsFromStreamUrl("https://tv.example.com/movie/u1/p2/99.mp4"),
        )
        assertEquals(
            "https://cdn.example.com/live/stream.m3u8?token=REDACTED&quality=hd",
            UserSyncService.redactCredentialsFromStreamUrl("https://cdn.example.com/live/stream.m3u8?token=abc123&quality=hd"),
        )
        assertEquals(
            "https://public.example.com/hls/channel.m3u8",
            UserSyncService.redactCredentialsFromStreamUrl("https://public.example.com/hls/channel.m3u8"),
        )
    }

    @Test
    fun geminiStoryService_sendsApiKeyInHeaderNeverInUrlQuery() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """{"candidates":[{"content":{"parts":[{"text":"{\"story\":\"قصة قصيرة\",\"confidence\":\"عالية\",\"identified_title\":\"فيلم\"}"}]}}]}""",
                    ),
            )
            val service = GeminiStoryService(
                baseClient = OkHttpClient(),
                baseUrl = server.url("/").toString().trimEnd('/'),
            )
            val result = service.generateStory(
                rawName = "Movie",
                year = "2024",
                imdbId = "",
                posterBytes = null,
                contentType = ContentType.MOVIE,
                apiKey = "AIzaSyFakeTestKeyForUnitTestsOnly12345",
            )
            assertTrue(result is GeminiResult.Success)
            val req = server.takeRequest()
            assertEquals("AIzaSyFakeTestKeyForUnitTestsOnly12345", req.getHeader("x-goog-api-key"))
            assertFalse("URL must never carry ?key=", req.path.orEmpty().contains("key="))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun githubReleaseUpdater_enforcesTrustedHttpsHostsAndParsesSha256Digest() = runTest {
        assertTrue(GithubReleaseUpdater.isTrustedReleaseUrl("https://github.com/saydlawy/-Sahab/releases/download/latest/app.apk"))
        assertTrue(GithubReleaseUpdater.isTrustedReleaseUrl("https://objects.githubusercontent.com/github-production-release-asset/app.apk"))
        assertFalse(GithubReleaseUpdater.isTrustedReleaseUrl("http://github.com/saydlawy/-Sahab/releases/download/latest/app.apk"))
        assertFalse(GithubReleaseUpdater.isTrustedReleaseUrl("https://evil.example.com/app.apk"))

        val server = MockWebServer()
        server.start()
        try {
            val expectedHash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "name": "Build 999999",
                          "html_url": "https://github.com/saydlawy/-Sahab/releases/tag/latest",
                          "body": "Release notes",
                          "assets": [
                            {
                              "name": "sahab-release.apk",
                              "browser_download_url": "https://github.com/saydlawy/-Sahab/releases/download/latest/sahab-release.apk",
                              "digest": "sha256:$expectedHash"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            )
            val update = GithubReleaseUpdater.checkForUpdate(
                client = OkHttpClient(),
                endpointUrl = server.url("/release").toString(),
                currentVersionCode = 1,
            )
            assertNotNull(update)
            assertEquals(999999, update?.versionCode)
            assertEquals(expectedHash, update?.sha256)
            assertEquals("https://github.com/saydlawy/-Sahab/releases/download/latest/sahab-release.apk", update?.apkUrl)

            // Rejects release payload if both apk asset & html_url point to untrusted domain
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(
                        """
                        {
                          "name": "Build 999999",
                          "html_url": "https://evil.example.com/release",
                          "assets": [
                            {
                              "name": "sahab-release.apk",
                              "browser_download_url": "https://evil.example.com/malware.apk"
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            )
            val rejected = GithubReleaseUpdater.checkForUpdate(
                client = OkHttpClient(),
                endpointUrl = server.url("/release").toString(),
                currentVersionCode = 1,
            )
            assertNull(rejected)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun firebaseCatalogHelpers_resolveRedirectsAndPendingAliasesSafely() {
        assertEquals("tmdb:550", FirebaseCatalogService.resolveRedirectId("pending:10", "tmdb:550"))
        assertEquals("tmdb:550", FirebaseCatalogService.resolveRedirectId("tmdb:550", "tmdb:550"))
        assertEquals("pending:10", FirebaseCatalogService.resolveRedirectId("pending:10", null))

        val tmdbEntity = CatalogMovieEntity(
            sahabId = CatalogMovieEntity.idFromTmdb(550),
            novaStreamId = "10",
            titleEn = "Fight Club",
        )
        assertEquals(listOf("pending:10"), FirebaseCatalogService.aliasesFor(tmdbEntity))
        val pendingEntity = CatalogMovieEntity(
            sahabId = CatalogMovieEntity.idPending("10"),
            novaStreamId = "10",
            titleEn = "Fight Club",
        )
        assertEquals(emptyList<String>(), FirebaseCatalogService.aliasesFor(pendingEntity))
    }
}
