package com.saydlawy.sahab.ui.browse.layouts.netflix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.browse.BrowseLayoutCallbacks
import com.saydlawy.sahab.ui.browse.BrowseUiState
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.SkeletonGrid
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

@Composable
internal fun NetflixBrowseLayout(
    route: String,
    series: Boolean,
    state: BrowseUiState,
    items: List<Poster>,
    favoriteIds: Set<String>,
    callbacks: BrowseLayoutCallbacks,
) {
    val displayMode = LocalDisplayMode.current
    val horizPad = if (displayMode == DisplayMode.PHONE_PORTRAIT) 12.dp else 22.dp
    val listState = rememberRestorableLazyListState("${route}_netflix_feed")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizPad, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SearchBox(
            value = state.query,
            onValueChange = callbacks.onQuery,
            placeholder = Ar.searchTitles,
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.query.trim().length >= 2) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.searching -> SkeletonGrid(columnWidth = 116.dp)
                    items.isEmpty() -> MessageBox(Ar.noTitles)
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(116.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(items, key = { _, p -> "nf_search_${p.id}" }) { index, poster ->
                            val focusKey = "nf_search_${poster.id}"
                            PosterCard(
                                title = poster.name,
                                imageUrl = poster.image,
                                badge = poster.rating,
                                quality = qualityOf(poster.name),
                                subtitle = poster.year,
                                onClick = { callbacks.onOpenPosterWithKey(poster, focusKey) },
                                onLongClick = { callbacks.onToggleFavorite(poster) },
                                modifier = Modifier.restorableFocus(route, focusKey, isDefaultEntry = index == 0),
                            )
                        }
                    }
                }
            }
            return@Column
        }

        if (state.busy && state.categoryGroups.isEmpty() && items.isEmpty()) {
            SkeletonGrid(columnWidth = 118.dp)
            return@Column
        }

        if (state.error != null && state.categoryGroups.isEmpty() && items.isEmpty()) {
            MessageBox(state.error, actionLabel = Ar.retry, onAction = callbacks.onRetry)
            return@Column
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().focusRestorer(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (state.featuredHeroItems.isNotEmpty() && displayMode != DisplayMode.PHONE_LANDSCAPE) {
                item(key = "nf_billboard") {
                    NetflixBillboard(
                        featured = state.featuredHeroItems,
                        favoriteIds = favoriteIds,
                        onOpen = callbacks.onOpenPoster,
                        onToggleFavorite = callbacks.onToggleFavorite,
                    )
                }
            }

            if (state.top10Items.isNotEmpty()) {
                item(key = "nf_top10") {
                    NetflixTop10Row(
                        screenKey = route,
                        top10 = state.top10Items,
                        onOpenPoster = { poster -> callbacks.onOpenPosterWithKey(poster, "top10_${poster.id}") },
                        onToggleFavorite = callbacks.onToggleFavorite,
                    )
                }
            }

            items(state.categoryGroups, key = { "nf_group_${it.category.id}" }) { group ->
                val rowState = rememberRestorableLazyListState("${route}_nf_row_${group.category.id}")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DotTitle(group.category.name, Palette.Glow, fontSize = 16.sp)
                    LazyRow(
                        state = rowState,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) {
                        items(group.posters, key = { "nf_${group.category.id}_${it.id}" }) { poster ->
                            val focusKey = "nf_${group.category.id}_${poster.id}"
                            PosterCard(
                                title = poster.name,
                                imageUrl = poster.image,
                                badge = poster.rating,
                                quality = qualityOf(poster.name),
                                subtitle = poster.year,
                                onClick = { callbacks.onOpenPosterWithKey(poster, focusKey) },
                                onLongClick = { callbacks.onToggleFavorite(poster) },
                                modifier = Modifier
                                    .width(128.dp)
                                    .restorableFocus(route, focusKey),
                            )
                        }
                    }
                }
            }

            if (state.categoryGroups.isEmpty() && items.isNotEmpty()) {
                item(key = "nf_fallback_row") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (series) Ar.seriesLibrary else Ar.moviesLibrary,
                            color = Palette.Text,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(items.take(40), key = { "nf_fb_${it.id}" }) { poster ->
                                val focusKey = "nf_fb_${poster.id}"
                                PosterCard(
                                    title = poster.name,
                                    imageUrl = poster.image,
                                    badge = poster.rating,
                                    quality = qualityOf(poster.name),
                                    subtitle = poster.year,
                                    onClick = { callbacks.onOpenPosterWithKey(poster, focusKey) },
                                    onLongClick = { callbacks.onToggleFavorite(poster) },
                                    modifier = Modifier
                                        .width(128.dp)
                                        .restorableFocus(route, focusKey),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
