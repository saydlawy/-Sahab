package com.saydlawy.sahab.ui.focus

import android.os.SystemClock
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import com.saydlawy.sahab.ui.LocalIsTv
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.flow.distinctUntilChanged

data class SavedScrollPosition(
    val index: Int = 0,
    val offset: Int = 0,
)

/**
 * Central state coordinator for Android TV D-Pad Navigation & Exact Focus Restoration.
 *
 * Lifecycle tracked per screen:
 * 1. Focus Acquired / Changed -> [recordFocus]
 * 2. Item Activated (OK / Enter) -> [markNavigateFrom]
 * 3. Destination Screen / Dialog Opened
 * 4. Back Pressed -> [onBackToScreen]
 * 5. Previous Screen & Scroll Offset Restored -> [rememberRestorableLazyListState] / [rememberRestorableLazyGridState]
 * 6. Exact Element Focus Restored -> [restorableFocus]
 */
@Stable
class FocusNavigationController {

    /** Last known focused item key for each screenKey (e.g. "home" -> "trending_128491"). */
    private val lastFocusedByScreen = mutableStateMapOf<String, String>()

    /** Active pending restoration target per screenKey; non-null when returning via Back or closing a Dialog. */
    private val pendingRestoreByScreen = mutableStateMapOf<String, String>()

    /** Monotonic counter per screenKey so repeated restorations to the same itemKey re-trigger LaunchedEffect. */
    private val restoreGenerationByScreen = mutableStateMapOf<String, Int>()

    /** Saved scroll positions for LazyColumn / LazyRow keyed by logical container ID. */
    private val listScrollPositions = HashMap<String, SavedScrollPosition>()

    /** Saved scroll positions for LazyVerticalGrid keyed by logical container ID. */
    private val gridScrollPositions = HashMap<String, SavedScrollPosition>()

    /** Saved selected category / tab / filter per screenKey so state survives Back navigation. */
    private val selectedFilterByScreen = mutableStateMapOf<String, String>()

    /** Saved previewed item ID (channel or poster) per screenKey. */
    private val previewItemByScreen = mutableStateMapOf<String, String>()

    /** Saved selected season index per series ID. */
    private val selectedSeasonBySeries = mutableStateMapOf<String, Int>()

    /** Saved search query per screenKey. */
    private val searchQueryByScreen = mutableStateMapOf<String, String>()

    /** Guard against duplicate navigation on rapid OK / Center presses. */
    private var lastNavigationUptimeMs: Long = 0L

    fun canNavigateNow(debounceMs: Long = 320L): Boolean {
        val now = SystemClock.uptimeMillis()
        if (now - lastNavigationUptimeMs < debounceMs) return false
        lastNavigationUptimeMs = now
        return true
    }

    /** Called when an item receives D-Pad focus. */
    fun recordFocus(screenKey: String, itemKey: String) {
        if (screenKey.isNotBlank() && itemKey.isNotBlank()) {
            val prev = lastFocusedByScreen[screenKey]
            lastFocusedByScreen[screenKey] = itemKey
            android.util.Log.d("SAHAB_FOCUS", "FOCUS screen=$screenKey item=$itemKey prev=$prev")
        }
    }

    /** Called immediately before opening a detail screen, player, or dialog from [itemKey]. */
    fun markNavigateFrom(screenKey: String, itemKey: String) {
        if (screenKey.isNotBlank() && itemKey.isNotBlank()) {
            lastFocusedByScreen[screenKey] = itemKey
            pendingRestoreByScreen[screenKey] = itemKey
            restoreGenerationByScreen[screenKey] = (restoreGenerationByScreen[screenKey] ?: 0) + 1
            android.util.Log.d("SAHAB_FOCUS", "NAV_FROM screen=$screenKey item=$itemKey")
        }
    }

    /** Called when a screen becomes active again after Back press or Dialog dismissal. */
    fun onScreenResumed(screenKey: String) {
        val target = pendingRestoreByScreen[screenKey] ?: lastFocusedByScreen[screenKey]
        android.util.Log.d("SAHAB_FOCUS", "RESUME screen=$screenKey target=$target")
        if (!target.isNullOrBlank()) {
            pendingRestoreByScreen[screenKey] = target
            restoreGenerationByScreen[screenKey] = (restoreGenerationByScreen[screenKey] ?: 0) + 1
        }
    }

    /** Triggers focus restoration to the item that was focused before opening a Dialog/Sheet. */
    fun restoreAfterDialogDismiss(screenKey: String, fallbackItemKey: String? = null) {
        val target = lastFocusedByScreen[screenKey] ?: fallbackItemKey
        if (!target.isNullOrBlank()) {
            pendingRestoreByScreen[screenKey] = target
            restoreGenerationByScreen[screenKey] = (restoreGenerationByScreen[screenKey] ?: 0) + 1
        }
    }

    fun pendingTarget(screenKey: String): String? = pendingRestoreByScreen[screenKey]

    fun lastFocused(screenKey: String): String? = lastFocusedByScreen[screenKey]

    fun restoreGeneration(screenKey: String): Int = restoreGenerationByScreen[screenKey] ?: 0

    fun hasSavedFocus(screenKey: String): Boolean =
        !pendingRestoreByScreen[screenKey].isNullOrBlank() || !lastFocusedByScreen[screenKey].isNullOrBlank()

    fun consumeRestore(screenKey: String, itemKey: String) {
        if (pendingRestoreByScreen[screenKey] == itemKey) {
            pendingRestoreByScreen.remove(screenKey)
        }
    }

    // --- Scroll State Persistence ---

    fun getListScroll(key: String): SavedScrollPosition =
        listScrollPositions[key] ?: SavedScrollPosition(0, 0)

    fun saveListScroll(key: String, index: Int, offset: Int) {
        listScrollPositions[key] = SavedScrollPosition(index, offset)
    }

    fun getGridScroll(key: String): SavedScrollPosition =
        gridScrollPositions[key] ?: SavedScrollPosition(0, 0)

    fun saveGridScroll(key: String, index: Int, offset: Int) {
        gridScrollPositions[key] = SavedScrollPosition(index, offset)
    }

    // --- Screen Filter / Category / Season / Preview Persistence ---

    fun getSelectedCategory(screenKey: String): String? = selectedFilterByScreen[screenKey]

    fun setSelectedCategory(screenKey: String, categoryId: String?) {
        if (categoryId == null) selectedFilterByScreen.remove(screenKey)
        else selectedFilterByScreen[screenKey] = categoryId
    }

    fun saveSelectedCategory(screenKey: String, categoryId: String?) =
        setSelectedCategory(screenKey, categoryId)

    fun getPreviewItem(screenKey: String): String? = previewItemByScreen[screenKey]

    fun setPreviewItem(screenKey: String, itemId: String?) {
        if (itemId == null) previewItemByScreen.remove(screenKey)
        else previewItemByScreen[screenKey] = itemId
    }

    fun getSelectedSeason(seriesId: String): Int = selectedSeasonBySeries[seriesId] ?: 0

    fun setSelectedSeason(seriesId: String, seasonIndex: Int) {
        selectedSeasonBySeries[seriesId] = seasonIndex
    }

    fun saveSelectedSeason(seriesId: String, seasonIndex: Int) =
        setSelectedSeason(seriesId, seasonIndex)

    fun getSearchQuery(screenKey: String): String = searchQueryByScreen[screenKey].orEmpty()

    fun setSearchQuery(screenKey: String, query: String) {
        if (query.isBlank()) searchQueryByScreen.remove(screenKey)
        else searchQueryByScreen[screenKey] = query
    }

    fun saveSearchQuery(screenKey: String, query: String) =
        setSearchQuery(screenKey, query)
}

val LocalFocusNavigation = compositionLocalOf { FocusNavigationController() }

/**
 * Remembers a [LazyListState] whose exact `(firstVisibleItemIndex, firstVisibleItemScrollOffset)`
 * is persisted inside [FocusNavigationController] under [storageKey].
 * When returning via Back navigation, the list starts at the exact saved scroll offset without jumping to 0.
 */
@Composable
fun rememberRestorableLazyListState(storageKey: String): LazyListState {
    val controller = LocalFocusNavigation.current
    val initial = remember(storageKey) { controller.getListScroll(storageKey) }
    val state = remember(storageKey) {
        LazyListState(
            firstVisibleItemIndex = initial.index,
            firstVisibleItemScrollOffset = initial.offset,
        )
    }
    LaunchedEffect(state, storageKey) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                controller.saveListScroll(storageKey, idx, off)
            }
    }
    DisposableEffect(storageKey) {
        onDispose {
            controller.saveListScroll(
                storageKey,
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
            )
        }
    }
    return state
}

/**
 * Remembers a [LazyGridState] whose exact `(firstVisibleItemIndex, firstVisibleItemScrollOffset)`
 * is persisted inside [FocusNavigationController] under [storageKey].
 */
@Composable
fun rememberRestorableLazyGridState(storageKey: String): LazyGridState {
    val controller = LocalFocusNavigation.current
    val initial = remember(storageKey) { controller.getGridScroll(storageKey) }
    val state = remember(storageKey) {
        LazyGridState(
            firstVisibleItemIndex = initial.index,
            firstVisibleItemScrollOffset = initial.offset,
        )
    }
    LaunchedEffect(state, storageKey) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (idx, off) ->
                controller.saveGridScroll(storageKey, idx, off)
            }
    }
    DisposableEffect(storageKey) {
        onDispose {
            controller.saveGridScroll(
                storageKey,
                state.firstVisibleItemIndex,
                state.firstVisibleItemScrollOffset,
            )
        }
    }
    return state
}

/**
 * Attaches deterministic D-Pad focus tracking and exact focus restoration to any focusable card/item.
 *
 * - Whenever this item receives D-Pad focus, its [itemKey] is saved under [screenKey].
 * - When returning to [screenKey] after Back navigation (or dialog close), if [itemKey] matches the
 *   saved focus target, it brings the item into view and requests focus on frame readiness.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.restorableFocus(
    screenKey: String,
    itemKey: String,
    isDefaultEntry: Boolean = false,
): Modifier {
    val controller = LocalFocusNavigation.current
    val isTv = LocalIsTv.current
    val requester = remember(screenKey, itemKey) { FocusRequester() }
    val bringIntoViewRequester = remember(screenKey, itemKey) { BringIntoViewRequester() }

    val pendingTarget = controller.pendingTarget(screenKey)
    val restoreGen = controller.restoreGeneration(screenKey)
    val hasAnySaved = controller.hasSavedFocus(screenKey)

    val shouldRestoreThis = pendingTarget == itemKey ||
        (isTv && pendingTarget == null && !hasAnySaved && isDefaultEntry)

    if (shouldRestoreThis) {
        LaunchedEffect(screenKey, itemKey, restoreGen, pendingTarget) {
            withFrameNanos { }
            runCatching { bringIntoViewRequester.bringIntoView() }
            withFrameNanos { }
            val focusedOk = runCatching { requester.requestFocus() }.isSuccess
            android.util.Log.d("SAHAB_FOCUS", "RESTORE_TRY screen=$screenKey item=$itemKey ok=$focusedOk isTv=$isTv pending=$pendingTarget")
            if (focusedOk && pendingTarget == itemKey) {
                controller.consumeRestore(screenKey, itemKey)
            }
        }
    }

    return this
        .bringIntoViewRequester(bringIntoViewRequester)
        .focusRequester(requester)
        .onFocusChanged { focusState ->
            if (focusState.isFocused) {
                controller.recordFocus(screenKey, itemKey)
                if (controller.pendingTarget(screenKey) == itemKey) {
                    controller.consumeRestore(screenKey, itemKey)
                }
            }
        }
}
