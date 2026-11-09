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
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.time.LocalDateTime

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

private data class Tile(val icon: ImageVector, val label: String, val sub: String, val route: String, val gradient: List<Color>)

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onPlay: (PlayRequest) -> Unit) {
    val isTv = LocalIsTv.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = if (isTv) 48.dp else 20.dp,
                vertical = if (isTv) 28.dp else 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(if (isTv) 22.dp else 16.dp),
    ) {
        HomeHeader(isTv, onOpen)
        WidgetsRow(isTv)
        MainTiles(isTv, onOpen)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeHeader(isTv: Boolean, onOpen: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (isTv) 54.dp else 42.dp)
                .clip(CircleShape)
                .background(Palette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Ic.Play, contentDescription = null, tint = Color.White,
                modifier = Modifier.size(if (isTv) 26.dp else 22.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                Ar.appName,
                fontSize = if (isTv) 26.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
            )
            Text(
                Ar.tagline,
                fontSize = if (isTv) 13.sp else 11.sp,
                color = Palette.TextDim,
                maxLines = 1,
            )
        }
        GlassIconButton(
            icon = Ic.Heart,
            description = Ar.favorites,
            onClick = { onOpen(Routes.FAVORITES) },
            size = if (isTv) 52.dp else 44.dp,
        )
        GlassIconButton(
            icon = Ic.Settings,
            description = Ar.settings,
            onClick = { onOpen(Routes.SETTINGS) },
            size = if (isTv) 52.dp else 44.dp,
        )
    }
}

@Composable
private fun WidgetsRow(isTv: Boolean) {
    if (isTv) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ClockWidget(Modifier.weight(1f))
            WeatherWidget(Modifier.weight(1f))
            SportsWidget(Modifier.weight(1f))
            PrayerWidget(Modifier.weight(1f))
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ClockWidget(Modifier.width(200.dp)) }
            item { WeatherWidget(Modifier.width(200.dp)) }
            item { SportsWidget(Modifier.width(220.dp)) }
            item { PrayerWidget(Modifier.width(200.dp)) }
        }
    }
}

@Composable
private fun ClockWidget(modifier: Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }
    val timeStr = now.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.forLanguageTag("ar")))
    val dateStr = now.format(DateTimeFormatter.ofPattern("EEEE، d MMMM", Locale.forLanguageTag("ar")))

    GlassCard(
        onClick = {},
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("الساعة", fontSize = 11.sp, color = Palette.TextDim)
                Spacer(Modifier.height(4.dp))
                Text(timeStr, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1)
                Spacer(Modifier.height(2.dp))
                Text(dateStr, fontSize = 11.sp, color = Palette.TextDim, maxLines = 1)
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Palette.Cyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Clock, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun WeatherWidget(modifier: Modifier) {
    GlassCard(
        onClick = {},
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("الطقس", fontSize = 11.sp, color = Palette.TextDim)
                Spacer(Modifier.height(4.dp))
                Text("27°", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Spacer(Modifier.height(2.dp))
                Text("الكويت · سماء صافية", fontSize = 11.sp, color = Palette.TextDim, maxLines = 1)
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Palette.Peach.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Sun, contentDescription = null, tint = Palette.Peach, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun SportsWidget(modifier: Modifier) {
    GlassCard(
        onClick = {},
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("مباريات اليوم", fontSize = 11.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
                Icon(Ic.Football, contentDescription = null, tint = Palette.Cyan, modifier = Modifier.size(14.dp))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "الفريق الأول  vs  الفريق الثاني",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text("9:00 م", fontSize = 11.sp, color = Palette.TextDim)
        }
    }
}

@Composable
private fun PrayerWidget(modifier: Modifier) {
    GlassCard(
        onClick = {},
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("الصلاة القادمة", fontSize = 11.sp, color = Palette.TextDim)
                Spacer(Modifier.height(4.dp))
                Text("العصر", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Spacer(Modifier.height(2.dp))
                Text("متبقي ساعتان", fontSize = 11.sp, color = Palette.Cyan, maxLines = 1)
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Palette.Violet.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Moon, contentDescription = null, tint = Palette.Violet, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
private fun MainTiles(isTv: Boolean, onOpen: (String) -> Unit) {
    val tiles = listOf(
        Tile(Ic.Tv, Ar.live, Ar.liveSub, Routes.LIVE, listOf(Color(0xFF1E3C72), Color(0xFF2A5298))),
        Tile(Ic.Movie, Ar.movies, Ar.moviesSub, Routes.MOVIES, listOf(Color(0xFF8E2DE2), Color(0xFF4A00E0))),
        Tile(Ic.Series, Ar.series, Ar.seriesSub, Routes.SERIES, listOf(Color(0xFFF953C6), Color(0xFFB91D73))),
    )

    if (isTv) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            tiles.forEachIndexed { index, tile ->
                BigTile(tile, Modifier.weight(1f).height(210.dp), index == 0, onOpen)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            tiles.forEachIndexed { index, tile ->
                BigTile(tile, Modifier.fillMaxWidth().height(150.dp), index == 0, onOpen)
            }
        }
    }
}

@Composable
private fun BigTile(tile: Tile, modifier: Modifier, autoFocus: Boolean, onOpen: (String) -> Unit) {
    val isTv = LocalIsTv.current
    GlassCard(
        onClick = { onOpen(tile.route) },
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        autoFocus = autoFocus,
        fill = Brush.linearGradient(tile.gradient),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTv) 64.dp else 56.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    tile.icon, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(if (isTv) 34.dp else 30.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                tile.label,
                fontSize = if (isTv) 24.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tile.sub,
                fontSize = if (isTv) 13.sp else 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
