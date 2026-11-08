package com.saydlawy.sahab.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.ScreenHeader
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.launch

@Composable
fun FavoritesScreen(onBack: () -> Unit, onOpen: (String) -> Unit, onPlay: (PlayRequest) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val isTv = LocalIsTv.current
    val favorites by container.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Ar.favorites, onBack)
        if (favorites.isEmpty()) {
            MessageBox(Ar.noFavorites)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(if (isTv) 170.dp else 128.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(Ar.favoritesHint, fontSize = 14.sp, color = Palette.TextDim, modifier = Modifier.padding(bottom = 4.dp))
                }
                items(favorites, key = { "${it.kind}_${it.id}" }) { favorite ->
                    PosterCard(
                        title = favorite.name,
                        imageUrl = favorite.icon,
                        badge = Ar.kindLabel(favorite.kind),
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
