package com.saydlawy.sahab.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.VodLayoutStyle
import com.saydlawy.sahab.data.forAccount
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.browse.layouts.classic.ClassicBrowseLayout
import com.saydlawy.sahab.ui.browse.layouts.netflix.NetflixBrowseLayout
import com.saydlawy.sahab.ui.browse.layouts.prime.PrimeBrowseLayout
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyGridState
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.util.Ar

@Composable
fun BrowseScreen(
    series: Boolean,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onPlay: (PlayRequest) -> Unit = {},
) {
    val route = if (series) Routes.SERIES else Routes.MOVIES
    val container = LocalContainer.current
    val focusNav = LocalFocusNavigation.current
    val savedCategory = remember(route) { focusNav.getSelectedCategory(route) }
    val savedQuery = remember(route) { focusNav.getSearchQuery(route) }

    val vm = appViewModel(key = route) { BrowseViewModel(it, series, savedCategory, savedQuery) }
    val state by vm.state.collectAsStateWithLifecycle()
    val kind = if (series) Kind.SERIES else Kind.MOVIE

    val layoutStyle by remember(container, series) {
        if (series) container.prefs.seriesLayoutStyle else container.prefs.moviesLayoutStyle
    }.collectAsStateWithLifecycle(initialValue = VodLayoutStyle.CLASSIC)

    val activeAccountId by container.prefs.activeAccountId.collectAsStateWithLifecycle(
        initialValue = container.prefs.currentActiveAccountId(),
    )
    val allFavorites by container.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val allRecents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites = remember(allFavorites, activeAccountId) { allFavorites.forAccount(activeAccountId) }
    val recents = remember(allRecents, activeAccountId) { allRecents.forAccount(activeAccountId) }
    val favoriteIds = remember(favorites, kind) { favorites.filter { it.kind == kind }.map { it.id }.toSet() }
    val screenRecents = remember(recents, series) {
        if (series) recents.filter { it.kind == Kind.SERIES || it.kind == Kind.EPISODE }
        else recents.filter { it.kind == Kind.MOVIE }
    }

    LaunchedEffect(state.selectedId) {
        state.selectedId?.let { focusNav.setSelectedCategory(route, it) }
    }
    LaunchedEffect(state.query) {
        focusNav.setSearchQuery(route, state.query)
    }

    val playRecent: (RecentItem) -> Unit = { recent ->
        focusNav.markNavigateFrom(route, "nf_cw_${recent.kind}_${recent.id}")
        onPlay(
            PlayRequest(
                items = listOf(
                    PlayItem(
                        kind = recent.kind,
                        id = recent.id,
                        title = recent.title,
                        subtitle = recent.subtitle,
                        icon = recent.icon,
                        ext = recent.ext,
                        groupId = recent.groupId,
                    ),
                ),
                startIndex = 0,
                fromStart = false,
                initialPositionMs = recent.positionMs,
            ),
        )
    }

    val rawItems = state.visibleItems
    val items = remember(rawItems, state.selectedId, favoriteIds, state.searchResults, favorites, screenRecents) {
        when {
            state.selectedId == RECENTS_CAT_ID && state.searchResults == null -> {
                screenRecents.map { r ->
                    Poster(
                        id = if (series && r.groupId.isNotBlank()) r.groupId else r.id,
                        name = r.title,
                        image = r.icon,
                        rating = "",
                        ext = r.ext,
                        year = r.subtitle,
                    )
                }.distinctBy { it.id }
            }
            state.selectedId == FAV_CAT_ID && state.searchResults == null -> {
                val fromPool = rawItems.filter { it.id in favoriteIds }
                if (fromPool.isNotEmpty()) fromPool
                else favorites.filter { it.kind == kind }.map {
                    Poster(id = it.id, name = it.name, image = it.icon, rating = "", ext = it.ext, year = "")
                }
            }
            else -> rawItems
        }
    }

    val gridStorageKey = "${route}_grid_${state.selectedId ?: ALL_CAT_ID}"
    val gridState = rememberRestorableLazyGridState(gridStorageKey)
    val netflixFeedState = rememberRestorableLazyListState("${route}_netflix_feed")
    val displayMode = LocalDisplayMode.current

    val callbacks = remember(route, vm, onOpenItem, onBack) {
        BrowseLayoutCallbacks(
            onSelectCategory = { catId ->
                focusNav.setSelectedCategory(route, catId)
                vm.select(catId)
            },
            onQuery = vm::onQuery,
            onOpenPoster = { poster ->
                focusNav.markNavigateFrom(route, "poster_${poster.id}")
                onOpenItem(poster.id)
            },
            onOpenPosterWithKey = { poster, focusKey ->
                focusNav.markNavigateFrom(route, focusKey)
                onOpenItem(poster.id)
            },
            onToggleFavorite = vm::toggleFavorite,
            onPosterFocused = vm::onPosterFocused,
            onPlayRecent = playRecent,
            onRetry = { state.selectedId?.let(vm::select) },
            onBack = onBack,
        )
    }

    Box(Modifier.fillMaxSize()) {
        val showTopBar by remember(displayMode, gridState, netflixFeedState, layoutStyle) {
            derivedStateOf {
                when {
                    displayMode == DisplayMode.TABLET_OR_TV -> true
                    layoutStyle == VodLayoutStyle.NETFLIX ->
                        !netflixFeedState.canScrollBackward ||
                            (netflixFeedState.firstVisibleItemIndex == 0 && netflixFeedState.firstVisibleItemScrollOffset < 28)
                    layoutStyle == VodLayoutStyle.CLASSIC ->
                        !gridState.canScrollBackward ||
                            (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 28)
                    else -> true
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showTopBar,
                enter = expandVertically(tween(180)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
            ) {
                SahabTopBar(current = route, onBack = onBack)
            }

            when {
                state.loadingCategories -> LoadingBox()
                state.categories.isEmpty() && items.isEmpty() -> MessageBox(
                    message = state.error ?: Ar.noCategories,
                    actionLabel = Ar.retry,
                    onAction = vm::loadCategories,
                )
                else -> when (layoutStyle) {
                    VodLayoutStyle.CLASSIC -> ClassicBrowseLayout(
                        route = route,
                        series = series,
                        state = state,
                        items = items,
                        favoriteIds = favoriteIds,
                        screenRecents = screenRecents,
                        gridState = gridState,
                        callbacks = callbacks,
                    )
                    VodLayoutStyle.PRIME -> PrimeBrowseLayout(
                        route = route,
                        state = state,
                        items = items,
                        favoriteIds = favoriteIds,
                        screenRecents = screenRecents,
                        gridState = gridState,
                        callbacks = callbacks,
                    )
                    VodLayoutStyle.NETFLIX -> NetflixBrowseLayout(
                        route = route,
                        series = series,
                        state = state,
                        items = items,
                        favoriteIds = favoriteIds,
                        screenRecents = screenRecents,
                        listState = netflixFeedState,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }
}
