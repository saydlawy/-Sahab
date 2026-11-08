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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.saydlawy.sahab.ui.LocalIsTv
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

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
    val isTv = LocalIsTv.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (isTv) 48.dp else 20.dp,
                vertical = if (isTv) 32.dp else 20.dp
            ),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        HomeHeader(isTv, onOpen)
        WidgetsRow(isTv)
        MainTiles(isTv, onOpen)
    }
}

@Composable
private fun HomeHeader(isTv: Boolean, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 56.dp else 42.dp)
                    .clip(CircleShape)
                    .background(Palette.Accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(Ic.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(if (isTv) 28.dp else 22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(Ar.appName, fontSize = if (isTv) 28.sp else 22.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(Ar.tagline, fontSize = if (isTv) 14.sp else 12.sp, color = Palette.TextDim)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassIconButton(Ic.Heart, Ar.favorites, { onOpen(Routes.FAVORITES) }, size = if (isTv) 52.dp else 44.dp)
            GlassIconButton(Ic.Settings, Ar.settings, { onOpen(Routes.SETTINGS) }, size = if (isTv) 52.dp else 44.dp)
        }
    }
}

@Composable
private fun WidgetsRow(isTv: Boolean) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now()
        }
    }
    val timeString = currentTime.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.US))

    if (isTv) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WidgetCard("الساعة", timeString, Modifier.weight(1f))
            WidgetCard("الطقس", "27° الكويت", Modifier.weight(1f))
            WidgetCard("المباريات", "الفريق الأول VS الثاني", Modifier.weight(1f))
            WidgetCard("الصلاة القادمة", "العصر", Modifier.weight(1f))
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { WidgetCard("الساعة", timeString, Modifier.width(140.dp)) }
            item { WidgetCard("الطقس", "27° الكويت", Modifier.width(140.dp)) }
            item { WidgetCard("المباريات", "اليوم 9:00م", Modifier.width(140.dp)) }
            item { WidgetCard("الصلاة", "العصر", Modifier.width(140.dp)) }
        }
    }
}

@Composable
private fun WidgetCard(title: String, value: String, modifier: Modifier) {
    GlassCard(
        onClick = {},
        modifier = modifier.height(90.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 13.sp, color = Palette.TextDim)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1)
        }
    }
}

@Composable
private fun MainTiles(isTv: Boolean, onOpen: (String) -> Unit) {
    val tiles = listOf(
        Tile(Ic.Tv, Ar.live, Ar.liveSub, Routes.LIVE),
        Tile(Ic.Movie, Ar.movies, Ar.moviesSub, Routes.MOVIES),
        Tile(Ic.Series, Ar.series, Ar.seriesSub, Routes.SERIES),
    )

    if (isTv) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            tiles.forEachIndexed { index, tile ->
                BigTile(tile, Modifier.weight(1f).height(220.dp), index == 0, onOpen)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            tiles.forEachIndexed { index, tile ->
                BigTile(tile, Modifier.fillMaxWidth().height(160.dp), index == 0, onOpen)
            }
        }
    }
}

@Composable
private fun BigTile(tile: Tile, modifier: Modifier, autoFocus: Boolean, onOpen: (String) -> Unit) {
    GlassCard(
        onClick = { onOpen(tile.route) },
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        autoFocus = autoFocus
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tile.icon, contentDescription = null, tint = Palette.Pink, modifier = Modifier.size(38.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(tile.label, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            Text(tile.sub, fontSize = 15.sp, color = Palette.TextDim, textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}
