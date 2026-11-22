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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.VodLayoutStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.VodCustomGrouping
import com.saydlawy.sahab.data.VodOrganizationMode
import com.saydlawy.sahab.ui.theme.Palette
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
    LaunchedEffect(gridState, state.selectedId, state.visibleItems.size, state.hasMore) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            total to lastVisible
        }.collect { (total, lastVisible) ->
            if (total > 0 && lastVisible >= total - 24 && state.hasMore && !state.loadingMore && !state.loadingItems) {
                vm.loadMore()
            }
        }
    }
    LaunchedEffect(state.query) {
        focusNav.setSearchQuery(route, state.query)
    }
    LaunchedEffect(Unit) {
        vm.refreshFacetsIfNeeded()
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
    val isSpecialCategory = state.searchResults == null &&
        (state.selectedId == RECENTS_CAT_ID || state.selectedId == FAV_CAT_ID)
    val specialItems by produceState(
        initialValue = emptyList(),
        isSpecialCategory,
        rawItems,
        state.selectedId,
        favoriteIds,
        favorites,
        screenRecents,
    ) {
        if (!isSpecialCategory) return@produceState
        value = when (state.selectedId) {
            RECENTS_CAT_ID -> {
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
            FAV_CAT_ID -> {
                val fromPool = if (rawItems.size > 400) {
                    withContext(Dispatchers.Default) { rawItems.filter { it.id in favoriteIds } }
                } else {
                    rawItems.filter { it.id in favoriteIds }
                }
                if (fromPool.isNotEmpty()) fromPool
                else favorites.filter { it.kind == kind }.map {
                    Poster(id = it.id, name = it.name, image = it.icon, rating = "", ext = it.ext, year = "")
                }
            }
            else -> rawItems
        }
    }
    val items = if (isSpecialCategory) specialItems else rawItems

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
                Column {
                    SahabTopBar(current = route, onBack = onBack)
                    VodOrganizationQuickBar(
                        mode = state.organizationMode,
                        grouping = state.customGrouping,
                        onSelectDefault = { vm.setOrganizationMode(VodOrganizationMode.DEFAULT) },
                        onSelectGrouping = { grouping -> vm.setCustomGrouping(grouping) },
                    )
                }
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
                        focusedDetailsFlow = vm.focusedDetails,
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
                        focusedDetailsFlow = vm.focusedDetails,
                        items = items,
                        favoriteIds = favoriteIds,
                        screenRecents = screenRecents,
                        listState = netflixFeedState,
                        gridState = gridState,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }
}

@Composable
private fun VodOrganizationQuickBar(
    mode: VodOrganizationMode,
    grouping: VodCustomGrouping,
    onSelectDefault: () -> Unit,
    onSelectGrouping: (VodCustomGrouping) -> Unit,
) {
    val chips = listOf(
        Triple("الافتراضي", mode == VodOrganizationMode.DEFAULT, onSelectDefault),
        Triple(
            "النوع",
            mode == VodOrganizationMode.CUSTOM && grouping == VodCustomGrouping.GENRE,
            { onSelectGrouping(VodCustomGrouping.GENRE) },
        ),
        Triple(
            "السنة",
            mode == VodOrganizationMode.CUSTOM && grouping == VodCustomGrouping.YEAR,
            { onSelectGrouping(VodCustomGrouping.YEAR) },
        ),
        Triple(
            "الممثل",
            mode == VodOrganizationMode.CUSTOM && grouping == VodCustomGrouping.CAST,
            { onSelectGrouping(VodCustomGrouping.CAST) },
        ),
        Triple(
            "التقييم",
            mode == VodOrganizationMode.CUSTOM && grouping == VodCustomGrouping.RATING,
            { onSelectGrouping(VodCustomGrouping.RATING) },
        ),
    )

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "الترتيب:",
            color = Palette.TextDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        chips.forEach { (label, active, onClick) ->
            var focused by remember { mutableStateOf(false) }
            Text(
                text = label,
                color = if (active || focused) Color.White else Palette.TextDim,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            active -> Palette.Royal
                            focused -> Palette.Royal.copy(alpha = 0.35f)
                            else -> Color.White.copy(alpha = 0.06f)
                        },
                    )
                    .border(
                        width = if (focused) 1.5.dp else 1.dp,
                        color = if (focused) Color.White else if (active) Palette.Royal else Color.White.copy(alpha = 0.10f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .clickable(onClick = onClick)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
