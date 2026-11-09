package com.saydlawy.sahab.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onPlay: (PlayRequest) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val isTv = LocalIsTv.current
    val favorites by container.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    var filterKind by remember { mutableStateOf<String?>(null) }
    val filtered = remember(favorites, filterKind) {
        if (filterKind == null) favorites else favorites.filter { it.kind == filterKind }
    }
    val gridState = rememberLazyGridState()

    Column(Modifier.fillMaxSize()) {
        SahabTopBar(current = Routes.FAVORITES, onBack = onBack)

        if (favorites.isEmpty()) {
            MessageBox(Ar.noFavorites)
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Chip("${Ar.all} (${favorites.size})", filterKind == null, { filterKind = null })
                    }
                    item {
                        Chip(Ar.live, filterKind == Kind.LIVE, { filterKind = Kind.LIVE })
                    }
                    item {
                        Chip(Ar.movies, filterKind == Kind.MOVIE, { filterKind = Kind.MOVIE })
                    }
                    item {
                        Chip(Ar.series, filterKind == Kind.SERIES, { filterKind = Kind.SERIES })
                    }
                }
                Tag(Ar.dpadReady, Palette.Gold, fontSize = 10.sp)
            }

            Spacer(Modifier.height(4.dp))

            if (filtered.isEmpty()) {
                MessageBox(Ar.noFavorites)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(if (isTv) 164.dp else 128.dp),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DotTitle(Ar.favoritesSub, Palette.Glow, fontSize = 14.sp)
                            Text(Ar.favoritesHint, fontSize = 12.sp, color = Palette.TextDim)
                        }
                    }
                    items(filtered, key = { "${it.kind}_${it.id}" }) { favorite ->
                        PosterCard(
                            title = favorite.name,
                            imageUrl = favorite.icon,
                            quality = qualityOf(favorite.name),
                            subtitle = Ar.kindLabel(favorite.kind),
                            contentScale = if (favorite.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                            onClick = {
                                when (favorite.kind) {
                                    Kind.LIVE -> onPlay(
                                        PlayRequest(listOf(PlayItem(Kind.LIVE, favorite.id, favorite.name, icon = favorite.icon)), 0),
                                    )
                                    Kind.MOVIE -> onOpen(Routes.movie(favorite.id))
                                    else -> onOpen(Routes.series(favorite.id))
                                }
                            },
                            onLongClick = { scope.launch { container.prefs.toggleFavorite(favorite) } },
                        )
                    }
                }
            }
        }
    }
}
