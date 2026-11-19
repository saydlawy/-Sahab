package com.saydlawy.sahab.ui.browse.layouts.netflix

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import com.saydlawy.sahab.ui.browse.layouts.prime.PrimeCategoryTabs
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.RemoteImage
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
    screenRecents: List<RecentItem> = emptyList(),
    callbacks: BrowseLayoutCallbacks,
) {
    val displayMode = LocalDisplayMode.current
    val isPhone = displayMode == DisplayMode.PHONE_PORTRAIT
    val horizPad = if (isPhone) 12.dp else 24.dp
    val listState = rememberRestorableLazyListState("${route}_netflix_feed")
    val heroDetails = state.focusedDetails ?: state.featuredHeroItems.firstOrNull()?.let { p ->
        FocusedPosterDetails(poster = p, year = p.year, rating = p.rating, backdropUrl = p.image)
    }
    val showGrid = state.query.trim().length >= 2 ||
        (state.selectedId != null && state.selectedId != ALL_CAT_ID)

    Box(Modifier.fillMaxSize().background(Palette.CinematicBg)) {
        NetflixBackdrop(heroDetails)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizPad, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimeCategoryTabs(
                screenKey = route,
                categories = state.categories,
                selectedId = state.selectedId,
                query = state.query,
                hasRecents = true,
                onQuery = callbacks.onQuery,
                onSelect = callbacks.onSelectCategory,
            )

            if (showGrid) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        state.busy && items.isEmpty() -> SkeletonGrid(columnWidth = 118.dp)
                        state.error != null && items.isEmpty() -> MessageBox(state.error, actionLabel = Ar.retry, onAction = callbacks.onRetry)
                        items.isEmpty() -> MessageBox(if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noTitles)
                        else -> LazyVerticalGrid(
                            columns = when (displayMode) {
                                DisplayMode.TABLET_OR_TV -> GridCells.Fixed(6)
                                DisplayMode.PHONE_LANDSCAPE -> GridCells.Fixed(5)
                                DisplayMode.PHONE_PORTRAIT -> GridCells.Adaptive(112.dp)
                            },
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(items, key = { _, p -> "nf_grid_${p.id}" }) { index, poster ->
                                val focusKey = "nf_grid_${poster.id}"
                                PosterCard(
                                    title = poster.name,
                                    imageUrl = poster.image,
                                    badge = poster.rating,
                                    quality = qualityOf(poster.name),
                                    subtitle = poster.year,
                                    onClick = { callbacks.onOpenPosterWithKey(poster, focusKey) },
                                    onLongClick = { callbacks.onToggleFavorite(poster) },
                                    onFocused = { callbacks.onPosterFocused(poster) },
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 26.dp),
            ) {
                if (heroDetails != null) {
                    item(key = "nf_hero_header") {
                        NetflixHeroInfo(
                            details = heroDetails,
                            series = series,
                            isFavorite = heroDetails.poster.id in favoriteIds,
                            onOpen = callbacks.onOpenPoster,
                            onToggleFavorite = callbacks.onToggleFavorite,
                            modifier = Modifier
                                .fillMaxWidth(if (isPhone) 1f else 0.56f)
                                .padding(top = 4.dp, bottom = 4.dp),
                        )
                    }
                }
                if (screenRecents.isNotEmpty()) {
                    item(key = "nf_continue_watching") {
                        val cwState = rememberRestorableLazyListState("${route}_nf_continue")
                        val cwWidth = if (isPhone) 168.dp else 216.dp
                        val cwHeight = if (isPhone) 96.dp else 118.dp
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DotTitle("تابع المشاهدة", Palette.Accent, fontSize = 16.sp)
                            LazyRow(
                                state = cwState,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            ) {
                                itemsIndexed(screenRecents.take(12), key = { _, r -> "nf_cw_${r.id}" }) { idx, recent ->
                                    val focusKey = "nf_cw_${recent.id}"
                                    var cwFocused by remember { mutableStateOf(false) }
                                    val cwScale by animateFloatAsState(
                                        targetValue = if (cwFocused && LocalIsTv.current) 1.04f else 1f,
                                        animationSpec = tween(130),
                                        label = "nfCwScale",
                                    )
                                    val cwShape = RoundedCornerShape(8.dp)
                                    Box(
                                        Modifier
                                            .width(cwWidth)
                                            .height(cwHeight)
                                            .graphicsLayer { scaleX = cwScale; scaleY = cwScale }
                                            .restorableFocus(route, focusKey, isDefault = idx == 0)
                                            .onFocusChanged { cwFocused = it.isFocused }
                                            .clip(cwShape)
                                            .background(Palette.CardSurface)
                                            .combinedClickable(onClick = { callbacks.onPlayRecent(recent) })
                                            .border(if (cwFocused) 2.5.dp else 0.dp, Color.White, cwShape),
                                    ) {
                                        RemoteImage(
                                            url = recent.image,
                                            fallbackText = recent.name,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = recent.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val pct = recent.progressPercent.coerceIn(0, 100)
                                        if (pct > 0) {
                                            Box(
                                                Modifier
                                                    .align(Alignment.BottomStart)
                                                    .fillMaxWidth(pct / 100f)
                                                    .height(3.dp)
                                                    .background(Color(0xFFE50914)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (state.top10Items.isNotEmpty()) {
                    item(key = "nf_top10") {
                        NetflixTop10Row(
                            screenKey = route,
                            top10 = state.top10Items,
                            onOpenPoster = { poster -> callbacks.onOpenPosterWithKey(poster, "top10_${poster.id}") },
                            onToggleFavorite = callbacks.onToggleFavorite,
                            onFocusedPoster = callbacks.onPosterFocused,
                        )
                    }
                }

                items(state.categoryGroups, key = { "nf_group_${it.category.id}" }) { group ->
                    val rowState = rememberRestorableLazyListState("${route}_nf_row_${group.category.id}")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DotTitle(group.category.name, Color(0xFFE50914), fontSize = 16.sp)
                        LazyRow(
                            state = rowState,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        ) {
                            itemsIndexed(group.posters, key = { _, it -> "nf_${group.category.id}_${it.id}" }) { idx, poster ->
                                val focusKey = "nf_${group.category.id}_${poster.id}"
                                NetflixPosterTile(
                                    poster = poster,
                                    route = route,
                                    focusKey = focusKey,
                                    isDefault = idx == 0 && state.top10Items.isEmpty(),
                                    onOpen = { callbacks.onOpenPosterWithKey(poster, focusKey) },
                                    onLongClick = { callbacks.onToggleFavorite(poster) },
                                    onFocused = { callbacks.onPosterFocused(poster) },
                                    modifier = Modifier.width(if (isPhone) 110.dp else 132.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NetflixPosterTile(
    poster: Poster,
    route: String,
    focusKey: String,
    isDefault: Boolean = false,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.04f else 1f,
        animationSpec = tween(130),
        label = "nfTileScale",
    )
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .aspectRatio(2f / 3f)
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
            .border(if (focused) 2.5.dp else 0.dp, Color.White, shape),
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
