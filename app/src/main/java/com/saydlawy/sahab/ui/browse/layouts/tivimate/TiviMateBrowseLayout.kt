package com.saydlawy.sahab.ui.browse.layouts.tivimate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.browse.ALL_CAT_ID
import com.saydlawy.sahab.ui.browse.BrowseLayoutCallbacks
import com.saydlawy.sahab.ui.browse.BrowseUiState
import com.saydlawy.sahab.ui.browse.FAV_CAT_ID
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.SideNavItem
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

@Composable
internal fun TiviMateBrowseLayout(
    route: String,
    state: BrowseUiState,
    items: List<Poster>,
    favoriteIds: Set<String>,
    callbacks: BrowseLayoutCallbacks,
) {
    val displayMode = LocalDisplayMode.current
    if (displayMode == DisplayMode.PHONE_PORTRAIT) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBox(state.query, callbacks.onQuery, Ar.searchTitles, Modifier.fillMaxWidth())
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = FAV_CAT_ID) { Chip(Ar.favorites, state.selectedId == FAV_CAT_ID, { callbacks.onSelectCategory(FAV_CAT_ID) }) }
                item(key = ALL_CAT_ID) { Chip(Ar.all, state.selectedId == ALL_CAT_ID, { callbacks.onSelectCategory(ALL_CAT_ID) }) }
                items(state.categories, key = { it.id }) { cat ->
                    Chip(cat.name, cat.id == state.selectedId, { callbacks.onSelectCategory(cat.id) })
                }
            }
            TiviMateTitleColumn(
                route = route,
                state = state,
                items = items,
                favoriteIds = favoriteIds,
                callbacks = callbacks,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            Modifier.weight(0.24f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SearchBox(state.query, callbacks.onQuery, Ar.searchTitles, Modifier.fillMaxWidth())
            TiviMateCategoryColumn(
                route = route,
                categories = state.categories,
                selectedId = state.selectedId,
                totalCount = state.totalCount,
                favCount = favoriteIds.size,
                categoryCounts = state.categoryCounts,
                onSelect = callbacks.onSelectCategory,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }

        TiviMateTitleColumn(
            route = route,
            state = state,
            items = items,
            favoriteIds = favoriteIds,
            callbacks = callbacks,
            modifier = Modifier.weight(0.40f).fillMaxHeight(),
        )

        TiviMateDetailsPane(
            details = state.focusedDetails,
            isFavorite = state.focusedDetails?.poster?.id in favoriteIds,
            onOpen = callbacks.onOpenPoster,
            onToggleFavorite = callbacks.onToggleFavorite,
            modifier = Modifier.weight(0.36f).fillMaxHeight(),
        )
    }
}

@Composable
private fun TiviMateCategoryColumn(
    route: String,
    categories: List<Category>,
    selectedId: String?,
    totalCount: Int,
    favCount: Int,
    categoryCounts: Map<String, Int>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberRestorableLazyListState("${route}_tm_cats")
    LazyColumn(
        state = listState,
        modifier = modifier.focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = FAV_CAT_ID) {
            SideNavItem(
                label = Ar.favorites,
                selected = selectedId == FAV_CAT_ID,
                onClick = { onSelect(FAV_CAT_ID) },
                count = favCount.takeIf { it > 0 },
                icon = Ic.HeartOutline,
                iconTint = Palette.Glow,
                modifier = Modifier.restorableFocus(route, "tm_cat_$FAV_CAT_ID"),
            )
        }
        item(key = ALL_CAT_ID) {
            SideNavItem(
                label = Ar.all,
                selected = selectedId == ALL_CAT_ID || selectedId == null,
                onClick = { onSelect(ALL_CAT_ID) },
                count = totalCount.takeIf { it > 0 },
                modifier = Modifier.restorableFocus(route, "tm_cat_$ALL_CAT_ID"),
            )
        }
        items(categories, key = { it.id }) { cat ->
            SideNavItem(
                label = cat.name,
                selected = cat.id == selectedId,
                onClick = { onSelect(cat.id) },
                count = categoryCounts[cat.id]?.takeIf { it > 0 },
                modifier = Modifier.restorableFocus(route, "tm_cat_${cat.id}"),
            )
        }
    }
}

@Composable
private fun TiviMateTitleColumn(
    route: String,
    state: BrowseUiState,
    items: List<Poster>,
    favoriteIds: Set<String>,
    callbacks: BrowseLayoutCallbacks,
    modifier: Modifier = Modifier,
) {
    val listState = rememberRestorableLazyListState("${route}_tm_titles_${state.selectedId ?: ALL_CAT_ID}")
    Box(modifier) {
        when {
            state.busy && items.isEmpty() -> LoadingBox()
            state.error != null && items.isEmpty() -> MessageBox(state.error, actionLabel = Ar.retry, onAction = callbacks.onRetry)
            items.isEmpty() -> MessageBox(if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noTitles)
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().focusRestorer(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                itemsIndexed(items, key = { idx, p -> "tm_${p.id}_$idx" }) { index, poster ->
                    val isFav = poster.id in favoriteIds
                    val isFocusedItem = state.focusedDetails?.poster?.id == poster.id
                    GlassCard(
                        onClick = { callbacks.onOpenPoster(poster) },
                        onLongClick = { callbacks.onToggleFavorite(poster) },
                        selected = isFocusedItem,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .restorableFocus(route, "tm_item_${poster.id}", isDefaultEntry = index == 0),
                    ) { focused ->
                        if (focused) {
                            LaunchedEffect(poster.id) { callbacks.onPosterFocused(poster) }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = Palette.TextMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.width(28.dp),
                            )
                            RemoteImage(
                                url = poster.image,
                                fallbackText = poster.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(width = 36.dp, height = 50.dp).clip(RoundedCornerShape(6.dp)),
                            )
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = poster.name,
                                    color = Palette.Text,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (poster.year.isNotBlank()) Text(poster.year, color = Palette.TextDim, fontSize = 11.sp)
                                    if (poster.rating.isNotBlank()) Text("★ ${poster.rating}", color = Palette.Gold, fontSize = 11.sp)
                                    if (isFav) Text("♥", color = Palette.LiveBadgeRed, fontSize = 11.sp)
                                }
                            }
                            qualityOf(poster.name)?.let { q ->
                                Tag(q, Palette.FocusCyan, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
