package com.saydlawy.sahab.ui.browse.layouts.prime

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.browse.ALL_CAT_ID
import com.saydlawy.sahab.ui.browse.BrowseLayoutCallbacks
import com.saydlawy.sahab.ui.browse.BrowseUiState
import com.saydlawy.sahab.ui.browse.FAV_CAT_ID
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SkeletonGrid
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

/**
 * Amazon Prime Video style browse: cinematic backdrop with the title block on the START side
 * (right in RTL), a "Top 10" shelf with giant rank numerals, then themed category shelves.
 * Everything uses start/end semantics so it mirrors correctly in RTL.
 */
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
    val isPhone = displayMode == DisplayMode.PHONE_PORTRAIT
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
    val rank = heroDetails?.poster?.id?.let { id ->
        state.top10Items.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.plus(1)
    }
    val hPad = if (isPhone) 12.dp else 24.dp
    val homeMode = (state.selectedId == null || state.selectedId == ALL_CAT_ID) &&
        state.searchResults == null && state.query.isBlank() &&
        (state.top10Items.isNotEmpty() || state.categoryGroups.isNotEmpty())
    val showMessage = !homeMode && items.isEmpty()
    val tabs: @Composable () -> Unit = {
        PrimeCategoryTabs(
            screenKey = route,
            categories = state.categories,
            selectedId = state.selectedId,
            query = state.query,
            hasRecents = screenRecents.isNotEmpty(),
            onQuery = callbacks.onQuery,
            onSelect = callbacks.onSelectCategory,
        )
    }

    Box(Modifier.fillMaxSize().background(Palette.CinematicBg)) {
        PrimeBackdrop(heroDetails)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hPad, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            tabs()

            if (showMessage) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.busy -> SkeletonGrid(columnWidth = 118.dp)
                        state.error != null -> MessageBox(state.error, actionLabel = Ar.retry, onAction = callbacks.onRetry)
                        else -> MessageBox(if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noTitles)
                    }
                }
            } else {
                if (homeMode) {
                    PrimeHeroInfo(
                        details = heroDetails,
                        rank = rank,
                        modifier = Modifier
                            .fillMaxWidth(if (isPhone) 1f else 0.56f)
                            .padding(bottom = 2.dp),
                    )
                }
                LazyVerticalGrid(
                    columns = columns,
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth().focusRestorer(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {

                if (homeMode) {
                    if (state.top10Items.isNotEmpty()) {
                        item(key = "prime_top10", span = { GridItemSpan(maxLineSpan) }) {
                            PrimeTop10Shelf(route, state.top10Items, callbacks, isPhone)
                        }
                    }
                    items(
                        items = state.categoryGroups,
                        key = { "prime_group_${it.category.id}" },
                        span = { GridItemSpan(maxLineSpan) },
                    ) { group ->
                        PrimeShelf(
                            title = group.category.name,
                            rowId = "prime_group_${group.category.id}",
                            route = route,
                            posters = group.posters,
                            callbacks = callbacks,
                            isPhone = isPhone,
                        )
                    }
                } else {
                    itemsIndexed(items, key = { _, p -> "prime_${p.id}" }) { index, poster ->
                        PrimeTile(
                            poster = poster,
                            route = route,
                            focusKey = "poster_${poster.id}",
                            isDefault = index == 0,
                            onOpen = { callbacks.onOpenPoster(poster) },
                            onLongClick = { callbacks.onToggleFavorite(poster) },
                            onFocused = { callbacks.onPosterFocused(poster) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun PrimeTop10Shelf(
    route: String,
    posters: List<Poster>,
    callbacks: BrowseLayoutCallbacks,
    isPhone: Boolean,
) {
    val rowState = rememberRestorableLazyListState("${route}_prime_top10")
    val cardW: Dp = if (isPhone) 168.dp else 218.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = PrimeCyan, fontWeight = FontWeight.Bold))
                append("Prime ")
                pop()
                append("الأعلى 10")
            },
            color = Color.White,
            fontSize = if (isPhone) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(posters, key = { _, p -> "prime_top10_${p.id}" }) { index, poster ->
                val rank = index + 1
                val focusKey = "prime_top10_${poster.id}"
                Box(Modifier.width(cardW + if (rank >= 10) 86.dp else 54.dp)) {
                    Text(
                        text = "$rank",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = if (isPhone) 68.sp else 92.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomStart),
                    )
                    PrimeTile(
                        poster = poster,
                        route = route,
                        focusKey = focusKey,
                        isDefault = false,
                        landscape = true,
                        onOpen = { callbacks.onOpenPosterWithKey(poster, focusKey) },
                        onLongClick = { callbacks.onToggleFavorite(poster) },
                        onFocused = { callbacks.onPosterFocused(poster) },
                        modifier = Modifier.width(cardW).align(Alignment.CenterEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimeShelf(
    title: String,
    rowId: String,
    route: String,
    posters: List<Poster>,
    callbacks: BrowseLayoutCallbacks,
    isPhone: Boolean,
) {
    val rowState = rememberRestorableLazyListState("${route}_$rowId")
    val cardW = if (isPhone) 168.dp else 210.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = Palette.Gold,
            fontSize = if (isPhone) 14.sp else 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            itemsIndexed(posters, key = { _, p -> "${rowId}_${p.id}" }) { _, poster ->
                val focusKey = "${rowId}_${poster.id}"
                PrimeTile(
                    poster = poster,
                    route = route,
                    focusKey = focusKey,
                    isDefault = false,
                    landscape = true,
                    onOpen = { callbacks.onOpenPosterWithKey(poster, focusKey) },
                    onLongClick = { callbacks.onToggleFavorite(poster) },
                    onFocused = { callbacks.onPosterFocused(poster) },
                    modifier = Modifier.width(cardW),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

/** Poster tile with the white focus outline used by Prime Video; scales slightly on TV. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PrimeTile(
    poster: Poster,
    route: String,
    focusKey: String,
    isDefault: Boolean,
    landscape: Boolean = false,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.06f else 1f,
        animationSpec = tween(120),
        label = "primeTileScale",
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .restorableFocus(route, focusKey, isDefault)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(shape)
            .background(Palette.CardSurface)
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .border(if (focused) 3.dp else 0.dp, Color.White, shape),
    ) {
        RemoteImage(
            url = poster.image,
            fallbackText = poster.name,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Text(
                text = poster.name,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
