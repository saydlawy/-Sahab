package com.saydlawy.sahab.data.auth

import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.RecentItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudMergeCoordinatorTest {

    @Test
    fun localOnly_cloudNotFound_keepsLocalAndMarksMergeDone() = runTest {
        val coordinator = CloudMergeCoordinator()
        val localFavs = listOf(FavItem(Kind.MOVIE, "10", "فيلم محلي"))
        var saved: List<FavItem>? = null

        val ok = coordinator.mergeFavorites(
            localFavs = { localFavs },
            fetchCloudFavs = { CloudFetchResult.NotFound },
            saveMergedFavs = { saved = it },
        )

        assertTrue(ok)
        assertTrue(coordinator.favoritesMergeDone)
        assertEquals(null, saved)
    }

    @Test
    fun cloudOnly_localEmpty_restoresCloudAndMarksMergeDone() = runTest {
        val coordinator = CloudMergeCoordinator()
        val cloudFavs = listOf(FavItem(Kind.LIVE, "5", "قناة سحابية"))
        var saved: List<FavItem>? = null

        val ok = coordinator.mergeFavorites(
            localFavs = { emptyList() },
            fetchCloudFavs = { CloudFetchResult.Success(cloudFavs) },
            saveMergedFavs = { saved = it },
        )

        assertTrue(ok)
        assertTrue(coordinator.favoritesMergeDone)
        assertEquals(cloudFavs, saved)
    }

    @Test
    fun bothLocalAndCloud_mergesWithoutDuplicatesAndKeepsNewestRecents() = runTest {
        val coordinator = CloudMergeCoordinator()
        val localFavs = listOf(FavItem(Kind.MOVIE, "1", "فيلم 1"), FavItem(Kind.MOVIE, "2", "فيلم 2"))
        val cloudFavs = listOf(FavItem(Kind.MOVIE, "2", "فيلم 2"), FavItem(Kind.SERIES, "9", "مسلسل 9"))
        var savedFavs: List<FavItem> = emptyList()

        val favOk = coordinator.mergeFavorites(
            localFavs = { localFavs },
            fetchCloudFavs = { CloudFetchResult.Success(cloudFavs) },
            saveMergedFavs = { savedFavs = it },
        )

        assertTrue(favOk)
        assertTrue(coordinator.favoritesMergeDone)
        assertEquals(3, savedFavs.size)

        val localRecents = listOf(RecentItem(Kind.MOVIE, "1", "فيلم 1", positionMs = 1_000L, updatedAt = 100L))
        val cloudRecents = listOf(RecentItem(Kind.MOVIE, "1", "فيلم 1", positionMs = 5_000L, updatedAt = 200L))
        var savedRecents: List<RecentItem> = emptyList()

        val recOk = coordinator.mergeRecents(
            localRecents = { localRecents },
            fetchCloudRecents = { CloudFetchResult.Success(cloudRecents) },
            saveMergedRecents = { savedRecents = it },
        )

        assertTrue(recOk)
        assertTrue(coordinator.recentsMergeDone)
        assertEquals(1, savedRecents.size)
        assertEquals(5_000L, savedRecents.first().positionMs)
    }

    @Test
    fun fetchFailureOrTimeout_neverMarksMergeDone_retriesAndBlocksUpload() = runTest {
        val coordinator = CloudMergeCoordinator()
        var uploadCalled = false

        val okTimeout = coordinator.mergeFavorites(
            localFavs = { emptyList() },
            fetchCloudFavs = {
                delay(500L)
                CloudFetchResult.Success(listOf(FavItem(Kind.MOVIE, "1", "لا يجب أن يصل")))
            },
            saveMergedFavs = { },
            timeoutMs = 50L,
        )

        assertFalse(okTimeout)
        assertFalse(coordinator.favoritesMergeDone)
        if (coordinator.favoritesMergeDone) uploadCalled = true
        assertFalse(uploadCalled)

        var attempts = 0
        coordinator.mergeAllWithRetry(
            isSignedIn = { true },
            localFavs = { emptyList() },
            fetchCloudFavs = {
                attempts++
                if (attempts < 3) CloudFetchResult.Error() else CloudFetchResult.Success(listOf(FavItem(Kind.MOVIE, "7", "مستعاد")))
            },
            saveMergedFavs = { },
            localRecents = { emptyList() },
            fetchCloudRecents = { CloudFetchResult.Error() },
            saveMergedRecents = { },
            fetchCloudPrefs = { CloudFetchResult.NotFound },
            applyPrefs = { },
            timeoutMs = 1_000L,
            retryDelaysMs = listOf(10L, 10L, 10L),
        )

        assertEquals(3, attempts)
        assertTrue(coordinator.favoritesMergeDone)
        assertFalse(coordinator.recentsMergeDone) // Failed all retries -> stays false -> upload blocked!
        assertTrue(coordinator.prefsMergeDone)
    }
}
