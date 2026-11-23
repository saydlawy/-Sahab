package com.saydlawy.sahab.ui.browse.layouts.prime

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.platform.LocalDensity
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
import com.saydlawy.sahab.ui.browse.layouts.ContinueWatchingProgressBar
import com.saydlawy.sahab.util.RatingSanitizer
import com.saydlawy.sahab.ui.browse.layouts.TvPivotScrollContainer
import com.saydlawy.sahab.ui.browse.layouts.rememberDebouncedHero
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.SkeletonGrid
import com.saydlawy.sahab.ui.components.qualityOf
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
    focusedDetailsFlow: StateFlow<FocusedPosterDetails?>,
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
    val fallbackHeroPoster = state.featuredHeroItems.firstOrNull()
    val hPad = if (isPhone) 12.dp else 24.dp
    val homeMode = (state.selectedId == null || state.selectedId == ALL_CAT_ID) &&
        state.searchResults == null && state.query.isBlank() &&
        (state.categoryGroups.isNotEmpty() || fallbackHeroPoster != null)
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
        if (homeMode) {
            PrimeBackdropHost(
                focusedDetailsFlow = focusedDetailsFlow,
                fallbackPoster = fallbackHeroPoster,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = hPad, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                TvPivotScrollContainer(parentFraction = 0.08f) {
                LazyVerticalGrid(
                    columns = columns,
                    state = gridState,
                    modifier = Modifier.weight(1f).fillMaxWidth().focusRestorer(),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {

                if (homeMode) {
                    item(key = "prime_hero_info", span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth()) {
                            PrimeHeroHeaderHost(
                                focusedDetailsFlow = focusedDetailsFlow,
                                fallbackPoster = fallbackHeroPoster,
                                modifier = Modifier
                                    .fillMaxWidth(if (isPhone) 1f else 0.54f)
                                    .padding(top = 12.dp, bottom = 4.dp),
                            )
                        }
                    }
                    if (screenRecents.isNotEmpty()) {
                        item(key = "prime_recents", span = { GridItemSpan(maxLineSpan) }) {
                            PrimeContinueWatchingShelf(route, screenRecents, callbacks, isPhone)
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
}

@Composable
private fun PrimeContinueWatchingShelf(
    route: String,
    recents: List<RecentItem>,
    callbacks: BrowseLayoutCallbacks,
    isPhone: Boolean,
) {
    val rowState = rememberRestorableLazyListState("${route}_prime_recents")
    val cardW = if (isPhone) 172.dp else 232.dp
    val density = LocalDensity.current
    val cardWPx = remember(density, cardW) { with(density) { cardW.roundToPx() } }
    val cardHPx = remember(cardWPx) { (cardWPx * 9) / 16 }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = Ar.continueWatching,
            color = PrimeCyan,
            fontSize = if (isPhone) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) {
            itemsIndexed(recents, key = { _, r -> "prime_recent_${r.kind}_${r.id}" }) { index, recent ->
                var focused by remember { mutableStateOf(false) }
                val isTv = LocalIsTv.current
                val scale by animateFloatAsState(
                    targetValue = if (focused && isTv) 1.06f else 1f,
                    animationSpec = tween(120),
                    label = "primeRecentScale",
                )
                val shape = RoundedCornerShape(8.dp)
                val focusKey = "prime_recent_${recent.id}"
                Box(
                    Modifier
                        .width(cardW)
                        .aspectRatio(16f / 9f)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .restorableFocus(route, focusKey, index == 0)
                        .onFocusChanged { focused = it.isFocused }
                        .clip(shape)
                        .background(Palette.CardSurface)
                        .combinedClickable(onClick = { callbacks.onPlayRecent(recent) })
                        .border(if (focused) 3.dp else 0.dp, Color.White, shape),
                ) {
                    RemoteImage(
                        url = recent.icon,
                        fallbackText = recent.title,
                        targetWidthPx = cardWPx,
                        targetHeightPx = cardHPx,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.88f))))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = recent.title,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (recent.subtitle.isNotBlank()) {
                            Text(
                                text = recent.subtitle,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        ContinueWatchingProgressBar(
                            positionMs = recent.positionMs,
                            durationMs = recent.durationMs,
                            accentColor = PrimeCyan,
                        )
                    }
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
    val cardW = if (isPhone) 108.dp else 138.dp
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            color = Palette.Gold,
            fontSize = if (isPhone) 15.sp else 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxWidth().focusRestorer(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        ) {
            itemsIndexed(posters, key = { _, p -> "${rowId}_${p.id}" }) { _, poster ->
                val focusKey = "${rowId}_${poster.id}"
                PrimeTile(
                    poster = poster,
                    route = route,
                    focusKey = focusKey,
                    isDefault = false,
                    landscape = false,
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
    if (!landscape) {
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
        return
    }
    var focused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.06f else 1f,
        animationSpec = tween(120),
        label = "primeTileScale",
    )
    val shape = RoundedCornerShape(8.dp)
    val isPhone = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val density = LocalDensity.current
    val tileW = if (landscape) {
        if (isPhone) 172.dp else 232.dp
    } else {
        if (isPhone) 118.dp else 138.dp
    }
    val tileWPx = remember(density, tileW) { with(density) { tileW.roundToPx() }.takeIf { it > 0 } }
    val tileHPx = remember(tileWPx, landscape) {
        tileWPx?.let { w -> if (landscape) (w * 9) / 16 else (w * 3) / 2 }
    }
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
            targetWidthPx = tileWPx,
            targetHeightPx = tileHPx,
            modifier = Modifier.fillMaxSize(),
        )
        val quality = remember(poster.name) { qualityOf(poster.name) }
        if (!quality.isNullOrBlank()) {
            Text(
                text = quality,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(PrimeCyan.copy(alpha = 0.90f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        val cleanRating = remember(poster.rating) {
            RatingSanitizer.format(poster.rating).takeIf { it.isNotBlank() }
        }
        if (cleanRating != null) {
            Text(
                text = "★ $cleanRating",
                color = Color(0xFFFFD54F),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
        val metaLine = remember(poster.year, poster.genre) {
            val primaryGenre = poster.genre.split(",", "،").firstOrNull()?.trim().orEmpty()
            listOfNotNull(
                poster.year.trim().takeIf { it.isNotBlank() },
                primaryGenre.takeIf { it.isNotBlank() },
            ).joinToString(" • ")
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.90f))))
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = poster.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (metaLine.isNotBlank()) {
                    Text(
                        text = metaLine,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrimeBackdropHost(
    focusedDetailsFlow: StateFlow<FocusedPosterDetails?>,
    fallbackPoster: Poster?,
) {
    val focusedDetails by focusedDetailsFlow.collectAsStateWithLifecycle()
    val rawDetails = focusedDetails ?: fallbackPoster?.let { p ->
        FocusedPosterDetails(poster = p, year = p.year, rating = p.rating, backdropUrl = p.image)
    }
    val heroDetails = rememberDebouncedHero(rawDetails)
    PrimeBackdrop(heroDetails)
}

@Composable
private fun PrimeHeroHeaderHost(
    focusedDetailsFlow: StateFlow<FocusedPosterDetails?>,
    fallbackPoster: Poster?,
    modifier: Modifier = Modifier,
) {
    val focusedDetails by focusedDetailsFlow.collectAsStateWithLifecycle()
    val rawDetails = focusedDetails ?: fallbackPoster?.let { p ->
        FocusedPosterDetails(poster = p, year = p.year, rating = p.rating, backdropUrl = p.image)
    }
    val heroDetails = rememberDebouncedHero(rawDetails)
    PrimeHeroInfo(
        details = heroDetails,
        rank = null,
        modifier = modifier,
    )
}
