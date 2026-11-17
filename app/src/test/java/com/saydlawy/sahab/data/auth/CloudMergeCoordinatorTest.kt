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

    @Test
    fun mergeRecents_ordersByUpdatedAtDescBeforeDistinctAndCapsAt30() = runTest {
        val coordinator = CloudMergeCoordinator()
        val local = listOf(
            RecentItem(Kind.MOVIE, "dup", "محلي أقدم", positionMs = 1_000L, updatedAt = 100L),
            RecentItem(Kind.MOVIE, "onlyLocal", "محلي فقط", positionMs = 2_000L, updatedAt = 500L),
        ) + (1..20).map { i ->
            RecentItem(Kind.LIVE, "l_$i", "قناة $i", positionMs = 0L, updatedAt = 1_000L + i)
        }
        val cloud = listOf(
            RecentItem(Kind.MOVIE, "dup", "سحابي أحدث", positionMs = 9_000L, updatedAt = 9_999L),
        ) + (21..40).map { i ->
            RecentItem(Kind.SERIES, "s_$i", "مسلسل $i", positionMs = 3_000L, updatedAt = 2_000L + i)
        }

        var saved: List<RecentItem> = emptyList()
        val ok = coordinator.mergeRecents(
            localRecents = { local },
            fetchCloudRecents = { CloudFetchResult.Success(cloud) },
            saveMergedRecents = { saved = it },
        )

        assertTrue(ok)
        assertEquals(30, saved.size)
        assertEquals("dup", saved.first().id)
        assertEquals(9_000L, saved.first().positionMs)
        assertTrue(saved.zipWithNext().all { (a, b) -> a.updatedAt >= b.updatedAt })
        assertEquals(1, saved.count { it.kind == Kind.MOVIE && it.id == "dup" })
    }

    @Test
    fun accountIsolation_keepsSameItemSeparateAcrossAccounts() = runTest {
        val coordinator = CloudMergeCoordinator()
        val acc1Fav = FavItem(Kind.MOVIE, "100", "فيلم في حساب 1", accountId = "acc_1")
        val acc2Fav = FavItem(Kind.MOVIE, "100", "فيلم في حساب 2", accountId = "acc_2")

        var saved: List<FavItem> = emptyList()
        val ok = coordinator.mergeFavorites(
            localFavs = { listOf(acc1Fav) },
            fetchCloudFavs = { CloudFetchResult.Success(listOf(acc2Fav)) },
            saveMergedFavs = { saved = it },
        )

        assertTrue(ok)
        assertEquals(2, saved.size)
        assertTrue(com.saydlawy.sahab.data.run { saved.isFav("acc_1", Kind.MOVIE, "100") })
        assertTrue(com.saydlawy.sahab.data.run { saved.isFav("acc_2", Kind.MOVIE, "100") })
        assertEquals(1, com.saydlawy.sahab.data.run { saved.forAccount("acc_1") }.size)
        assertEquals(1, com.saydlawy.sahab.data.run { saved.forAccount("acc_2") }.size)
    }
}
