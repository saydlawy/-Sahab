package com.saydlawy.sahab.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.UserInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SectionTitle
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(container: AppContainer) : ViewModel() {

    val recents: StateFlow<List<RecentItem>> = container.prefs.recents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _info = MutableStateFlow<UserInfo?>(container.repo.userInfo)
    val info: StateFlow<UserInfo?> = _info.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _info.value = container.repo.refreshUserInfo()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The subscription line simply stays as it was.
            }
        }
    }
}

private data class Tile(val icon: ImageVector, val label: String, val sub: String, val route: String)

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onPlay: (PlayRequest) -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val recents by vm.recents.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Ar.appName, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(Ar.tagline, fontSize = 16.sp, color = Palette.TextDim)
                info?.let { SubscriptionLine(it) }
            }
            GlassIconButton(Ic.Settings, Ar.settings, { onOpen(Routes.SETTINGS) })
        }

        Spacer(Modifier.height(24.dp))

        val tiles = listOf(
            Tile(Ic.Tv, Ar.live, Ar.liveSub, Routes.LIVE),
            Tile(Ic.Movie, Ar.movies, Ar.moviesSub, Routes.MOVIES),
            Tile(Ic.Series, Ar.series, Ar.seriesSub, Routes.SERIES),
            Tile(Ic.Heart, Ar.favorites, Ar.favoritesSub, Routes.FAVORITES),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 720.dp) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    tiles.forEachIndexed { index, tile ->
                        HomeTile(tile, Modifier.weight(1f).height(190.dp), index == 0, onOpen)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    tiles.chunked(2).forEachIndexed { rowIndex, pair ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            pair.forEachIndexed { col, tile ->
                                HomeTile(tile, Modifier.weight(1f).height(150.dp), rowIndex == 0 && col == 0, onOpen)
                            }
                        }
                    }
                }
            }
        }

        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(30.dp))
            SectionTitle(Ar.continueWatching)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(recents, key = { "${it.kind}_${it.id}" }) { item ->
                    RecentCard(item) {
                        if (item.kind == Kind.EPISODE && item.groupId.isNotBlank()) {
                            onOpen(Routes.series(item.groupId))
                        } else {
                            onPlay(
                                PlayRequest(
                                    listOf(
                                        PlayItem(item.kind, item.id, item.title, item.subtitle, item.icon, item.ext, item.groupId),
                                    ),
                                    0,
                                ),
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SubscriptionLine(info: UserInfo) {
    val expiry = info.expiry
    val text = when {
        expiry == null || expiry <= 0L -> Ar.subscriptionOpen
        else -> {
            val daysLeft = ((expiry - System.currentTimeMillis() / 1000) / 86_400).toInt()
            if (daysLeft < 0) Ar.subscriptionEnded(formatDate(expiry))
            else Ar.subscriptionLine(formatDate(expiry), daysLeft)
        }
    }
    Text(
        text = text,
        fontSize = 14.sp,
        color = Palette.Cyan,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun HomeTile(tile: Tile, modifier: Modifier, autoFocus: Boolean, onOpen: (String) -> Unit) {
    GlassCard(
        onClick = { onOpen(tile.route) },
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        autoFocus = autoFocus,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Palette.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(tile.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(tile.label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            Text(
                tile.sub,
                fontSize = 14.sp,
                color = Palette.TextDim,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RecentCard(item: RecentItem, onClick: () -> Unit) {
    val live = item.kind == Kind.LIVE
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .width(240.dp)
            .height(140.dp),
        shape = RoundedCornerShape(22.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            RemoteImage(
                url = item.icon,
                modifier = if (live) Modifier.fillMaxSize().padding(28.dp) else Modifier.fillMaxSize(),
                contentScale = if (live) ContentScale.Fit else ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))),
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
            ) {
                Text(
                    item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, fontSize = 13.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!live && item.durationMs > 0) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(item.positionMs.toFloat() / item.durationMs)
                }
            }
        }
    }
}
