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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyGridState
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NetflixBrowseLayout(
    route: String,
    series: Boolean,
    state: BrowseUiState,
    focusedDetailsFlow: StateFlow<FocusedPosterDetails?>,
    items: List<Poster>,
    favoriteIds: Set<String>,
    screenRecents: List<RecentItem> = emptyList(),
    listState: LazyListState = rememberRestorableLazyListState("${route}_netflix_feed"),
    gridState: LazyGridState = rememberRestorableLazyGridState("${route}_nf_grid"),
    callbacks: BrowseLayoutCallbacks,
) {
    val displayMode = LocalDisplayMode.current
    val isPhone = displayMode == DisplayMode.PHONE_PORTRAIT
    val horizPad = if (isPhone) 12.dp else 24.dp
    val density = LocalDensity.current
    val fallbackHeroPoster = state.featuredHeroItems.firstOrNull()
    val showGrid = state.query.trim().length >= 2 ||
        (state.selectedId != null && state.selectedId != ALL_CAT_ID)

    Box(Modifier.fillMaxSize().background(Palette.CinematicBg)) {
        if (!showGrid) {
            NetflixBackdropHost(
                focusedDetailsFlow = focusedDetailsFlow,
                fallbackPoster = fallbackHeroPoster,
                listState = listState,
                showGrid = showGrid,
            )
        }

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
                            state = gridState,
                            modifier = Modifier.fillMaxSize().focusRestorer(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(items, key = { index, p -> "nf_grid_${index}_${p.id}" }) { index, poster ->
                                val focusKey = "nf_grid_${index}_${poster.id}"
                                PosterCard(
                                    title = poster.name,
                                    imageUrl = poster.image,
                                    fallbackImageUrl = poster.fallbackImage,
                                    badge = poster.rating,
                                    quality = qualityOf(poster.name),
                                    subtitle = poster.year,
                            genre = poster.genre,
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

            val tileWidthDp = if (isPhone) 110.dp else 132.dp
            val tileWidthPx = remember(density, tileWidthDp) { with(density) { tileWidthDp.roundToPx() } }
            val tileHeightPx = remember(tileWidthPx) { (tileWidthPx * 3) / 2 }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 26.dp),
            ) {
                if (fallbackHeroPoster != null) {
                    item(key = "nf_hero_header") {
                        NetflixHeroHeaderHost(
                            screenKey = route,
                            focusedDetailsFlow = focusedDetailsFlow,
                            fallbackPoster = fallbackHeroPoster,
                            series = series,
                            favoriteIds = favoriteIds,
                            hasRecents = screenRecents.isNotEmpty(),
                            onOpen = callbacks.onOpenPoster,
                            onToggleFavorite = callbacks.onToggleFavorite,
                            modifier = Modifier
                                .fillMaxWidth(if (isPhone) 1f else 0.56f)
                                .padding(top = 4.dp, bottom = 2.dp),
                        )
                    }
                }
                if (screenRecents.isNotEmpty()) {
                    item(key = "nf_continue_watching") {
                        val cwState = rememberRestorableLazyListState("${route}_nf_continue")
                        val cwWidth = if (isPhone) 168.dp else 216.dp
                        val cwHeight = if (isPhone) 96.dp else 118.dp
                        val cwWidthPx = remember(density, cwWidth) { with(density) { cwWidth.roundToPx() } }
                        val cwHeightPx = remember(density, cwHeight) { with(density) { cwHeight.roundToPx() } }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            DotTitle("تابع المشاهدة", Color(0xFFE50914), fontSize = 16.sp)
                            LazyRow(
                                state = cwState,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                            ) {
                                itemsIndexed(screenRecents.take(12), key = { idx, r -> "nf_cw_${idx}_${r.kind}_${r.id}" }) { idx, recent ->
                                    val focusKey = "nf_cw_${recent.kind}_${recent.id}"
                                    var cwFocused by remember { mutableStateOf(false) }
                                    val cwScale by animateFloatAsState(
                                        targetValue = if (cwFocused && LocalIsTv.current) 1.04f else 1f,
                                        animationSpec = tween(130),
                                        label = "nfCwScale",
                                    )
                                    val cwShape = RoundedCornerShape(8.dp)
                                    val cwBorderModifier = if (cwFocused) {
                                        Modifier.border(2.5.dp, Color.White, cwShape)
                                    } else {
                                        Modifier
                                    }
                                    Box(
                                        Modifier
                                            .width(cwWidth)
                                            .height(cwHeight)
                                            .graphicsLayer { scaleX = cwScale; scaleY = cwScale }
                                            .restorableFocus(route, focusKey, isDefaultEntry = idx == 0)
                                            .onFocusChanged { cwFocused = it.isFocused }
                                            .clip(cwShape)
                                            .background(Palette.CardSurface)
                                            .then(cwBorderModifier)
                                            .combinedClickable(onClick = { callbacks.onPlayRecent(recent) }),
                                    ) {
                                        RemoteImage(
                                            url = recent.icon,
                                            fallbackText = recent.title,
                                            contentScale = ContentScale.Crop,
                                            alignment = Alignment.TopCenter,
                                            targetWidthPx = cwWidthPx,
                                            targetHeightPx = cwHeightPx,
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
                                                text = recent.title,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val pct = if (recent.durationMs > 0L) {
                                            ((recent.positionMs * 100L) / recent.durationMs).toInt().coerceIn(0, 100)
                                        } else {
                                            0
                                        }
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

                items(state.categoryGroups, key = { "nf_group_${it.category.id}" }) { group ->
                    val rowState = rememberRestorableLazyListState("${route}_nf_row_${group.category.id}")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DotTitle(group.category.name, Color(0xFFE50914), fontSize = 16.sp)
                        LazyRow(
                            state = rowState,
                            modifier = Modifier.fillMaxWidth().focusRestorer(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        ) {
                            itemsIndexed(group.posters, key = { idx, it -> "nf_${group.category.id}_${idx}_${it.id}" }) { idx, poster ->
                                val focusKey = "nf_${group.category.id}_${idx}_${poster.id}"
                                NetflixPosterTile(
                                    poster = poster,
                                    route = route,
                                    focusKey = focusKey,
                                    isDefault = false,
                                    targetWidthPx = tileWidthPx,
                                    targetHeightPx = tileHeightPx,
                                    onOpen = { callbacks.onOpenPosterWithKey(poster, focusKey) },
                                    onLongClick = { callbacks.onToggleFavorite(poster) },
                                    onFocused = { callbacks.onPosterFocused(poster) },
                                    modifier = Modifier.width(tileWidthDp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NetflixPosterTile(
    poster: Poster,
    route: String,
    focusKey: String,
    isDefault: Boolean = false,
    targetWidthPx: Int? = null,
    targetHeightPx: Int? = null,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PosterCard(
        title = poster.name,
        imageUrl = poster.image,
        fallbackImageUrl = poster.fallbackImage,
        badge = poster.rating,
        quality = qualityOf(poster.name),
        subtitle = poster.year,
        genre = poster.genre,
        onClick = onOpen,
        onLongClick = onLongClick,
        onFocused = onFocused,
        modifier = modifier.restorableFocus(route, focusKey, isDefault),
    )
}

@Composable
private fun NetflixBackdropHost(
    focusedDetailsFlow: StateFlow<FocusedPosterDetails?>,
    fallbackPoster: Poster?,
    listState: LazyListState,
    showGrid: Boolean,
) {
    val focusedDetails by focusedDetailsFlow.collectAsStateWithLifecycle()
    val rawDetails = focusedDetails ?: fallbackPoster?.let { p ->
        FocusedPosterDetails(poster = p, year = p.year, rating = p.rating, backdropUrl = p.image)
    }
    val heroDetails = com.saydlawy.sahab.ui.browse.layouts.rememberDebouncedHero(rawDetails)
    val backdropAlpha by remember(listState, showGrid) {
        derivedStateOf {
            if (showGrid) {
                0.22f
            } else if (listState.firstVisibleItemIndex > 0) {
                0f
            } else {
                (1f - (listState.firstVisibleItemScrollOffset / 260f)).coerceIn(0f, 1f)
            }
        }
    }
    NetflixBackdrop(details = heroDetails, scrollAlpha = backdropAlpha)
}

@Composable
private fun NetflixHeroHeaderHost(
    screenKey: String,
    focusedDetailsFlow: StateFlow<FocusedPosterDetails?>,
    fallbackPoster: Poster?,
    series: Boolean,
    favoriteIds: Set<String>,
    hasRecents: Boolean,
    onOpen: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusedDetails by focusedDetailsFlow.collectAsStateWithLifecycle()
    val rawDetails = focusedDetails ?: fallbackPoster?.let { p ->
        FocusedPosterDetails(poster = p, year = p.year, rating = p.rating, backdropUrl = p.image)
    }
    val heroDetails = com.saydlawy.sahab.ui.browse.layouts.rememberDebouncedHero(rawDetails) ?: return
    NetflixHeroInfo(
        screenKey = screenKey,
        details = heroDetails,
        series = series,
        isFavorite = heroDetails.poster.id in favoriteIds,
        hasRecents = hasRecents,
        onOpen = onOpen,
        onToggleFavorite = onToggleFavorite,
        modifier = modifier,
    )
}
