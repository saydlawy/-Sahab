package com.saydlawy.sahab.data.auth

import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.trimPerAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Coordinates safe cloud merge for favorites, recents, and UI preferences.
 *
 * Guarantees that each category's upload gate (`*MergeDone`) is only unlocked after
 * a confirmed read + merge (`CloudFetchResult.Success` or `CloudFetchResult.NotFound`),
 * preventing timeouts or network errors from overwriting remote cloud data with an empty local list.
 */
class CloudMergeCoordinator {

    @Volatile
    var favoritesMergeDone: Boolean = false
        private set

    @Volatile
    var recentsMergeDone: Boolean = false
        private set

    @Volatile
    var prefsMergeDone: Boolean = false
        private set

    fun reset() {
        favoritesMergeDone = false
        recentsMergeDone = false
        prefsMergeDone = false
    }

    suspend fun mergeFavorites(
        localFavs: suspend () -> List<FavItem>,
        fetchCloudFavs: suspend () -> CloudFetchResult<List<FavItem>>,
        saveMergedFavs: suspend (List<FavItem>) -> Unit,
        timeoutMs: Long = DEFAULT_FETCH_TIMEOUT_MS,
    ): Boolean {
        val result = withTimeoutOrNull(timeoutMs) { fetchCloudFavs() } ?: CloudFetchResult.Error()
        return when (result) {
            is CloudFetchResult.Success -> {
                val local = localFavs()
                val merged = (local + result.data).distinctBy { Triple(it.accountId, it.kind, it.id) }
                if (merged != local) {
                    saveMergedFavs(merged)
                }
                favoritesMergeDone = true
                true
            }
            is CloudFetchResult.NotFound -> {
                favoritesMergeDone = true
                true
            }
            is CloudFetchResult.Error -> false
        }
    }

    suspend fun mergeRecents(
        localRecents: suspend () -> List<RecentItem>,
        fetchCloudRecents: suspend () -> CloudFetchResult<List<RecentItem>>,
        saveMergedRecents: suspend (List<RecentItem>) -> Unit,
        timeoutMs: Long = DEFAULT_FETCH_TIMEOUT_MS,
    ): Boolean {
        val result = withTimeoutOrNull(timeoutMs) { fetchCloudRecents() } ?: CloudFetchResult.Error()
        return when (result) {
            is CloudFetchResult.Success -> {
                val local = localRecents()
                val merged = (local + result.data)
                    .sortedByDescending { it.updatedAt }
                    .distinctBy { Triple(it.accountId, it.kind, it.id) }
                    .trimPerAccount(MAX_RECENTS)
                if (merged != local) {
                    saveMergedRecents(merged)
                }
                recentsMergeDone = true
                true
            }
            is CloudFetchResult.NotFound -> {
                recentsMergeDone = true
                true
            }
            is CloudFetchResult.Error -> false
        }
    }

    suspend fun mergePreferences(
        fetchCloudPrefs: suspend () -> CloudFetchResult<CloudPreferences>,
        applyPrefs: suspend (CloudPreferences) -> Unit,
        timeoutMs: Long = DEFAULT_FETCH_TIMEOUT_MS,
    ): Boolean {
        val result = withTimeoutOrNull(timeoutMs) { fetchCloudPrefs() } ?: CloudFetchResult.Error()
        return when (result) {
            is CloudFetchResult.Success -> {
                applyPrefs(result.data)
                prefsMergeDone = true
                true
            }
            is CloudFetchResult.NotFound -> {
                prefsMergeDone = true
                true
            }
            is CloudFetchResult.Error -> false
        }
    }

    /**
     * Runs initial merge for all categories not yet marked done, then retries any failed/timed-out
     * categories up to [retryDelaysMs].size times with exponential backoff without blocking UI.
     */
    suspend fun mergeAllWithRetry(
        isSignedIn: () -> Boolean,
        localFavs: suspend () -> List<FavItem>,
        fetchCloudFavs: suspend () -> CloudFetchResult<List<FavItem>>,
        saveMergedFavs: suspend (List<FavItem>) -> Unit,
        localRecents: suspend () -> List<RecentItem>,
        fetchCloudRecents: suspend () -> CloudFetchResult<List<RecentItem>>,
        saveMergedRecents: suspend (List<RecentItem>) -> Unit,
        fetchCloudPrefs: suspend () -> CloudFetchResult<CloudPreferences>,
        applyPrefs: suspend (CloudPreferences) -> Unit,
        timeoutMs: Long = DEFAULT_FETCH_TIMEOUT_MS,
        retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    ) {
        suspend fun attemptIncomplete(): Boolean {
            if (!isSignedIn()) return true
            if (!favoritesMergeDone) {
                mergeFavorites(localFavs, fetchCloudFavs, saveMergedFavs, timeoutMs)
            }
            if (!recentsMergeDone) {
                mergeRecents(localRecents, fetchCloudRecents, saveMergedRecents, timeoutMs)
            }
            if (!prefsMergeDone) {
                mergePreferences(fetchCloudPrefs, applyPrefs, timeoutMs)
            }
            return favoritesMergeDone && recentsMergeDone && prefsMergeDone
        }

        if (attemptIncomplete()) return
        for (waitMs in retryDelaysMs) {
            delay(waitMs)
            if (!isSignedIn()) return
            if (attemptIncomplete()) return
        }
    }

    companion object {
        const val DEFAULT_FETCH_TIMEOUT_MS = 8_000L
        const val MAX_RECENTS = 30
        val DEFAULT_RETRY_DELAYS_MS = listOf(2_000L, 5_000L, 10_000L)
    }
}
