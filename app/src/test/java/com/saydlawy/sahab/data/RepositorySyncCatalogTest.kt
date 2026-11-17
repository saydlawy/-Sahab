package com.saydlawy.sahab.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositorySyncCatalogTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun syncCatalog_returnsFailureAndDoesNotStampTimestamp_whenEndpointsFail() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse().setResponseCode(500).setBody("server error")
            }
        }
        server.start()
        try {
            val prefs = Prefs(
                context = null,
                dataStoreOverride = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempFolder.newFile("sync_fail.preferences_pb") },
                ),
            )
            val client = OkHttpClient()
            val repo = Repository(
                api = XtreamApi(client),
                tmdb = TmdbApi(client, "", null),
                prefs = prefs,
                okHttpClient = client,
            )
            repo.setAccount(Account(name = "Test", server = server.url("/").toString().trimEnd('/'), user = "u", pass = "p"))

            val result = repo.syncCatalog(force = true)
            assertTrue("Expected SyncResult.Failure but got $result", result is Repository.SyncResult.Failure)
            assertEquals(0L, prefs.lastCatalogSyncTime.first())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun syncCatalog_returnsFailure_whenPartialCoreEndpointFails() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action")
                return if (action == "get_vod_streams") {
                    MockResponse().setResponseCode(503).setBody("unavailable")
                } else if (action == null) {
                    MockResponse().setBody("""{"user_info":{"status":"Active"},"server_info":{"url":"localhost"}}""")
                } else {
                    MockResponse().setBody("[]")
                }
            }
        }
        server.start()
        try {
            val prefs = Prefs(
                context = null,
                dataStoreOverride = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempFolder.newFile("sync_partial.preferences_pb") },
                ),
            )
            val client = OkHttpClient()
            val repo = Repository(
                api = XtreamApi(client),
                tmdb = TmdbApi(client, "", null),
                prefs = prefs,
                okHttpClient = client,
            )
            repo.setAccount(Account(name = "Test", server = server.url("/").toString().trimEnd('/'), user = "u", pass = "p"))

            val result = repo.syncCatalog(force = true)
            assertTrue("Expected SyncResult.Failure on partial endpoint failure but got $result", result is Repository.SyncResult.Failure)
            assertEquals(0L, prefs.lastCatalogSyncTime.first())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun syncCatalog_returnsSuccessAndUpdatesTimestamp_whenAllCoreEndpointsSucceed() = runTest {
        val server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val action = request.requestUrl?.queryParameter("action")
                return if (action == null) {
                    MockResponse().setBody("""{"user_info":{"status":"Active"},"server_info":{"url":"localhost"}}""")
                } else {
                    MockResponse().setBody("[]")
                }
            }
        }
        server.start()
        try {
            val prefs = Prefs(
                context = null,
                dataStoreOverride = PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { tempFolder.newFile("sync_ok.preferences_pb") },
                ),
            )
            val client = OkHttpClient()
            val repo = Repository(
                api = XtreamApi(client),
                tmdb = TmdbApi(client, "", null),
                prefs = prefs,
                okHttpClient = client,
            )
            repo.setAccount(Account(name = "Test", server = server.url("/").toString().trimEnd('/'), user = "u", pass = "p"))

            val result = repo.syncCatalog(force = true)
            assertTrue("Expected SyncResult.Success but got $result", result is Repository.SyncResult.Success)
            assertTrue(prefs.lastCatalogSyncTime.first() > 0L)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun migrateSyncKeysIfNeeded_copiesLegacyLastSyncTimeToCatalogOnly() = runTest {
        val ds = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { tempFolder.newFile("sync_migrate.preferences_pb") },
        )
        ds.edit { p ->
            p[androidx.datastore.preferences.core.longPreferencesKey("last_sync_time")] = 123_456_789L
            p[androidx.datastore.preferences.core.longPreferencesKey("epg_last_sync_time")] = 999L
        }
        val prefs = Prefs(context = null, dataStoreOverride = ds)
        prefs.migrateSyncKeysIfNeeded()

        assertEquals(123_456_789L, prefs.lastCatalogSyncTime.first())
        assertEquals(0L, prefs.lastChannelSyncTime.first())
        assertEquals(0L, prefs.lastEpgSyncTime.first())
    }
}
