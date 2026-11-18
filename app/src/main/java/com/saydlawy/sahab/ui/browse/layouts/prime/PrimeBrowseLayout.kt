package com.saydlawy.sahab.ui.browse.layouts.prime

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.browse.BrowseLayoutCallbacks
import com.saydlawy.sahab.ui.browse.BrowseUiState
import com.saydlawy.sahab.ui.browse.FAV_CAT_ID
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.SkeletonGrid
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.util.Ar

@Composable
internal fun PrimeBrowseLayout(
    route: String,
    state: BrowseUiState,
    items: List<Poster>,
    favoriteIds: Set<String>,
    screenRecents: List<RecentItem>,
    gridState: LazyGridState,
    callbacks: BrowseLayoutCallbacks,
) {
    val displayMode = LocalDisplayMode.current
    val columns = remember(displayMode) {
        when (displayMode) {
            DisplayMode.TABLET_OR_TV -> GridCells.Fixed(6)
            DisplayMode.PHONE_LANDSCAPE -> GridCells.Fixed(5)
            DisplayMode.PHONE_PORTRAIT -> GridCells.Adaptive(112.dp)
        }
    }
    val heroDetails = state.focusedDetails ?: state.featuredHeroItems.firstOrNull()?.let { p ->
        FocusedPosterDetails(poster = p, year = p.year, rating = p.rating, backdropUrl = p.image)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = if (displayMode == DisplayMode.PHONE_PORTRAIT) 12.dp else 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (displayMode != DisplayMode.PHONE_LANDSCAPE) {
            PrimeHeroBanner(
                details = heroDetails,
                isFavorite = heroDetails?.poster?.id in favoriteIds,
                onOpen = callbacks.onOpenPoster,
                onToggleFavorite = callbacks.onToggleFavorite,
            )
        }

        PrimeCategoryTabs(
            screenKey = route,
            categories = state.categories,
            selectedId = state.selectedId,
            query = state.query,
            hasRecents = screenRecents.isNotEmpty(),
            onQuery = callbacks.onQuery,
            onSelect = callbacks.onSelectCategory,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy && items.isEmpty() -> SkeletonGrid(columnWidth = 118.dp)
                state.error != null && items.isEmpty() -> MessageBox(state.error, actionLabel = Ar.retry, onAction = callbacks.onRetry)
                items.isEmpty() -> MessageBox(if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noTitles)
                else -> LazyVerticalGrid(
                    columns = columns,
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(items, key = { _, p -> "prime_${p.id}" }) { index, poster ->
                        PosterCard(
                            title = poster.name,
                            imageUrl = poster.image,
                            badge = poster.rating,
                            quality = qualityOf(poster.name),
                            subtitle = poster.year,
                            onClick = { callbacks.onOpenPoster(poster) },
                            onLongClick = { callbacks.onToggleFavorite(poster) },
                            onFocused = { callbacks.onPosterFocused(poster) },
                            modifier = Modifier.restorableFocus(
                                screenKey = route,
                                itemKey = "poster_${poster.id}",
                                isDefaultEntry = index == 0,
                            ),
                        )
                    }
                }
            }
        }
    }
}
