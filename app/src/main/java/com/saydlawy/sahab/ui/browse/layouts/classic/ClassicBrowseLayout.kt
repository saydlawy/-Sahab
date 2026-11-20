package com.saydlawy.sahab.ui.browse.layouts.classic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.browse.ALL_CAT_ID
import com.saydlawy.sahab.ui.browse.BrowseLayoutCallbacks
import com.saydlawy.sahab.ui.browse.BrowseUiState
import com.saydlawy.sahab.ui.browse.FAV_CAT_ID
import com.saydlawy.sahab.ui.browse.RECENTS_CAT_ID
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.SideNavItem
import com.saydlawy.sahab.ui.components.SkeletonGrid
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

@Composable
internal fun ClassicBrowseLayout(
    route: String,
    series: Boolean,
    state: BrowseUiState,
    items: List<Poster>,
    favoriteIds: Set<String>,
    screenRecents: List<RecentItem>,
    gridState: LazyGridState,
    callbacks: BrowseLayoutCallbacks,
) {
    val displayMode = LocalDisplayMode.current
    val isTv = LocalIsTv.current
    when (displayMode) {
        DisplayMode.TABLET_OR_TV -> Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                Modifier
                    .width(192.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SearchBox(state.query, callbacks.onQuery, Ar.searchTitles, Modifier.fillMaxWidth())
                ClassicBrowseSidebar(
                    screenKey = route,
                    categories = state.categories,
                    selectedId = state.selectedId,
                    onSelect = callbacks.onSelectCategory,
                    totalCount = state.totalCount,
                    favCount = favoriteIds.size,
                    recentCount = screenRecents.size,
                    categoryCounts = state.categoryCounts,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
            ClassicPosterGrid(
                screenKey = route,
                series = series,
                state = state,
                items = items,
                gridState = gridState,
                recents = if (state.query.isBlank() && state.selectedId != RECENTS_CAT_ID) screenRecents else emptyList(),
                onPlayRecent = callbacks.onPlayRecent,
                fixedColumns = 6,
                columnWidth = if (isTv) 110.dp else 116.dp,
                onOpenPoster = callbacks.onOpenPoster,
                onToggleFavorite = callbacks.onToggleFavorite,
                onRetry = callbacks.onRetry,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        DisplayMode.PHONE_LANDSCAPE -> Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                Modifier
                    .width(158.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SearchBox(state.query, callbacks.onQuery, Ar.searchTitles, Modifier.fillMaxWidth())
                ClassicBrowseSidebar(
                    screenKey = route,
                    categories = state.categories,
                    selectedId = state.selectedId,
                    onSelect = callbacks.onSelectCategory,
                    totalCount = state.totalCount,
                    favCount = favoriteIds.size,
                    recentCount = screenRecents.size,
                    categoryCounts = state.categoryCounts,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
            ClassicPosterGrid(
                screenKey = route,
                series = series,
                state = state,
                items = items,
                gridState = gridState,
                recents = if (state.query.isBlank() && state.selectedId == RECENTS_CAT_ID) screenRecents else emptyList(),
                onPlayRecent = callbacks.onPlayRecent,
                fixedColumns = 5,
                columnWidth = 104.dp,
                onOpenPoster = callbacks.onOpenPoster,
                onToggleFavorite = callbacks.onToggleFavorite,
                onRetry = callbacks.onRetry,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        DisplayMode.PHONE_PORTRAIT -> Column(Modifier.fillMaxSize()) {
            val chipsState = rememberRestorableLazyListState("${route}_chips_row")
            SearchBox(
                state.query,
                callbacks.onQuery,
                Ar.searchTitles,
                Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            LazyRow(
                state = chipsState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (screenRecents.isNotEmpty()) {
                    item(key = RECENTS_CAT_ID) {
                        Chip(
                            Ar.continueWatching,
                            state.selectedId == RECENTS_CAT_ID,
                            { callbacks.onSelectCategory(RECENTS_CAT_ID) },
                            Modifier.restorableFocus(route, "cat_$RECENTS_CAT_ID"),
                        )
                    }
                }
                item(key = FAV_CAT_ID) {
                    Chip(
                        Ar.favorites,
                        state.selectedId == FAV_CAT_ID,
                        { callbacks.onSelectCategory(FAV_CAT_ID) },
                        Modifier.restorableFocus(route, "cat_$FAV_CAT_ID"),
                    )
                }
                item(key = ALL_CAT_ID) {
                    Chip(
                        Ar.all,
                        state.selectedId == ALL_CAT_ID,
                        { callbacks.onSelectCategory(ALL_CAT_ID) },
                        Modifier.restorableFocus(route, "cat_$ALL_CAT_ID"),
                    )
                }
                items(state.categories, key = { it.id }) { cat ->
                    Chip(
                        cat.name,
                        cat.id == state.selectedId,
                        { callbacks.onSelectCategory(cat.id) },
                        Modifier.restorableFocus(route, "cat_${cat.id}"),
                    )
                }
            }
            ClassicPosterGrid(
                screenKey = route,
                series = series,
                state = state,
                items = items,
                gridState = gridState,
                recents = if (state.query.isBlank() && state.selectedId == RECENTS_CAT_ID) screenRecents else emptyList(),
                onPlayRecent = callbacks.onPlayRecent,
                fixedColumns = null,
                columnWidth = 104.dp,
                onOpenPoster = callbacks.onOpenPoster,
                onToggleFavorite = callbacks.onToggleFavorite,
                onRetry = callbacks.onRetry,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun ClassicBrowseSidebar(
    screenKey: String,
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    totalCount: Int,
    favCount: Int,
    recentCount: Int,
    categoryCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberRestorableLazyListState("${screenKey}_sidebar")
    LazyColumn(
        state = listState,
        modifier = modifier.focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        if (recentCount > 0) {
            item(key = RECENTS_CAT_ID) {
                SideNavItem(
                    label = Ar.continueWatching,
                    selected = selectedId == RECENTS_CAT_ID,
                    onClick = { onSelect(RECENTS_CAT_ID) },
                    count = recentCount,
                    icon = Ic.Clock,
                    iconTint = Palette.Gold,
                    modifier = Modifier.restorableFocus(screenKey, "cat_$RECENTS_CAT_ID"),
                )
            }
        }
        item(key = FAV_CAT_ID) {
            SideNavItem(
                label = Ar.favorites,
                selected = selectedId == FAV_CAT_ID,
                onClick = { onSelect(FAV_CAT_ID) },
                count = favCount.takeIf { it > 0 },
                icon = Ic.HeartOutline,
                iconTint = Palette.Glow,
                modifier = Modifier.restorableFocus(screenKey, "cat_$FAV_CAT_ID"),
            )
        }
        item(key = ALL_CAT_ID) {
            SideNavItem(
                label = Ar.all,
                selected = selectedId == ALL_CAT_ID || selectedId == null,
                onClick = { onSelect(ALL_CAT_ID) },
                count = totalCount.takeIf { it > 0 },
                modifier = Modifier.restorableFocus(screenKey, "cat_$ALL_CAT_ID"),
            )
        }
        items(categories, key = { it.id }) { cat ->
            val count = categoryCounts[cat.id]
            val q = qualityOf(cat.name)
            SideNavItem(
                label = cat.name,
                selected = cat.id == selectedId,
                onClick = { onSelect(cat.id) },
                count = if (count != null && count > 0) count else null,
                trailingText = if (q != null && (count == null || count == 0)) q else null,
                modifier = Modifier.restorableFocus(screenKey, "cat_${cat.id}"),
            )
        }
    }
}

@Composable
private fun ClassicPosterGrid(
    screenKey: String,
    series: Boolean,
    state: BrowseUiState,
    items: List<Poster>,
    gridState: LazyGridState,
    recents: List<RecentItem>,
    onPlayRecent: (RecentItem) -> Unit,
    fixedColumns: Int?,
    columnWidth: Dp,
    onOpenPoster: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerTitle = if (series) Ar.seriesLibrary else Ar.moviesLibrary
    val gridCells = remember(fixedColumns, columnWidth) {
        if (fixedColumns != null) GridCells.Fixed(fixedColumns) else GridCells.Adaptive(columnWidth)
    }

    Column(modifier) {
        if (LocalDisplayMode.current == DisplayMode.TABLET_OR_TV) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(headerTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextDim)
                    if (items.isNotEmpty()) Tag(Ar.itemsCount(items.size), Palette.Glow, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy && items.isEmpty() -> SkeletonGrid(columnWidth = columnWidth)
                state.error != null && items.isEmpty() -> MessageBox(state.error, actionLabel = Ar.retry, onAction = onRetry)
                items.isEmpty() && recents.isEmpty() -> MessageBox(if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noTitles)
                else -> LazyVerticalGrid(
                    columns = gridCells,
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (recents.isNotEmpty()) {
                        item(key = "browse_recents_header", span = { GridItemSpan(maxLineSpan) }) {
                            ClassicRecentsStrip(
                                screenKey = screenKey,
                                title = if (series) "آخر ما تم مشاهدته في المسلسلات" else "آخر ما تم مشاهدته في الأفلام",
                                recents = recents.take(10),
                                onPlayRecent = onPlayRecent,
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, p -> p.id }) { index, poster ->
                        val itemKey = "poster_${poster.id}"
                        PosterCard(
                            title = poster.name,
                            imageUrl = poster.image,
                            badge = poster.rating,
                            quality = qualityOf(poster.name),
                            subtitle = poster.year,
                            genre = poster.genre,
                            onClick = { onOpenPoster(poster) },
                            onLongClick = { onToggleFavorite(poster) },
                            modifier = Modifier.restorableFocus(
                                screenKey = screenKey,
                                itemKey = itemKey,
                                isDefaultEntry = index == 0,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClassicRecentsStrip(
    screenKey: String,
    title: String,
    recents: List<RecentItem>,
    onPlayRecent: (RecentItem) -> Unit,
) {
    val rowState = rememberRestorableLazyListState("${screenKey}_recents_strip")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DotTitle(title, Palette.Gold, fontSize = 14.sp)
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(recents, key = { "browse_recent_${it.kind}_${it.id}" }) { item ->
                val itemKey = "browse_recent_${item.kind}_${item.id}"
                val progress = if (item.durationMs > 0) (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) else null
                GlassCard(
                    onClick = { onPlayRecent(item) },
                    modifier = Modifier.width(200.dp).restorableFocus(screenKey, itemKey),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val density = LocalDensity.current
                            val iconPx = remember(density) { with(density) { 40.dp.roundToPx() } }
                            RemoteImage(
                                url = item.icon,
                                fallbackText = item.title,
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)),
                                contentScale = ContentScale.Crop,
                                targetWidthPx = iconPx,
                                targetHeightPx = iconPx,
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle.ifBlank { Ar.kindLabel(item.kind) }, fontSize = 11.sp, color = Palette.Glow, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (progress != null) ProgressLine(progress, height = 3.dp)
                    }
                }
            }
        }
    }
}
