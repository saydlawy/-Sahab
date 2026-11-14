package com.saydlawy.sahab.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.zIndex
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.weather.WeatherCondition
import java.util.Calendar
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.LocalLightEffects
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatNowMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.foundation.lazy.LazyColumn
import com.saydlawy.sahab.ui.home.components.CompactTopBarInfo
import com.saydlawy.sahab.ui.home.components.TrendingMoviesSection

private data class HomeSectionCard(
    val label: String,
    val sub: String,
    val route: String,
    val badge: String,
    val icon: ImageVector,
    val art: Brush,
    val accent: Color,
)

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onPlay: (PlayRequest) -> Unit) {
    val container = LocalContainer.current
    val recents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val showRecents by container.prefs.showRecents.collectAsStateWithLifecycle(initialValue = true)
    val homeChannelsLayout by container.prefs.homeChannelsLayout.collectAsStateWithLifecycle(initialValue = HomeChannelsLayout.CAROUSEL)

    var featuredChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    LaunchedEffect(container.repo.account) {
        featuredChannels = loadFeaturedChannels(container.repo)
    }

    val categories = remember {
        listOf(
            HomeSectionCard(
                label = Ar.live, sub = Ar.liveSub, route = Routes.LIVE, badge = "LIVE 4K", icon = Ic.Tv,
                art = Brush.linearGradient(listOf(Palette.Glow.copy(alpha = 0.42f), Palette.Blue.copy(alpha = 0.65f), Palette.DeepBlue)),
                accent = Palette.Glow,
            ),
            HomeSectionCard(
                label = Ar.movies, sub = Ar.moviesSub, route = Routes.MOVIES, badge = "VOD", icon = Ic.Movie,
                art = Brush.linearGradient(listOf(Palette.Royal.copy(alpha = 0.58f), Color(0xFF4C1D95), Palette.DeepBlue)),
                accent = Palette.Royal,
            ),
            HomeSectionCard(
                label = Ar.series, sub = Ar.seriesSub, route = Routes.SERIES, badge = "HD / 4K", icon = Ic.Series,
                art = Brush.linearGradient(listOf(Palette.Gold.copy(alpha = 0.48f), Palette.Royal.copy(alpha = 0.65f), Palette.DeepBlue)),
                accent = Palette.Gold,
            ),
        )
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SahabTopBar(current = Routes.HOME)
            
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                        CompactTopBarInfo()
                    }
                }

                if (showRecents && recents.isNotEmpty()) {
                    item { RecentsStrip(recents = recents.take(10), onOpen = onOpen, onPlay = onPlay) }
                }

                item {
                    FeaturedChannelsSection(
                        channels = featuredChannels,
                        layout = homeChannelsLayout,
                        onPlay = onPlay
                    )
                }

                item {
                    TrendingMoviesSection { movie ->
                        // الانتقال لشاشة التفاصيل باستخدام novaStreamId أو sahabId
                        onOpen(Routes.movie(movie.novaStreamId.ifEmpty { movie.sahabId }))
                    }
                }

                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        categories.forEach { cat ->
                            SectionHeroCard(
                                cat = cat,
                                modifier = Modifier.weight(1f).height(140.dp),
                                s = 0.85f,
                                autoFocus = false,
                                onClick = { onOpen(cat.route) }
                            )
                        }
                    }
                }

                item {
                    LiveMatchesBar(onOpenLive = { onOpen(Routes.LIVE) }, onOpenSports = { onOpen(Routes.SPORTS) })
                }
            }
        }
    }
}

@Composable
private fun FeaturedChannelsSection(
    layout: HomeChannelsLayout,
    channels: List<Channel>,
    onPlay: (PlayRequest) -> Unit,
    s: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val displayChannels = if (channels.isNotEmpty()) channels else FALLBACK_FEATURED_CHANNELS
    when (layout) {
        HomeChannelsLayout.CAROUSEL -> {
            FeaturedChannelsCarousel(
                channels = displayChannels,
                onPlay = onPlay,
                s = s,
                modifier = modifier,
            )
        }
        HomeChannelsLayout.GRID -> {
            FeaturedChannelsGrid(
                channels = displayChannels,
                onPlay = onPlay,
                s = s,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun FeaturedChannelsCarousel(
    channels: List<Channel>,
    onPlay: (PlayRequest) -> Unit,
    s: Float = 1f,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return

    val listState = rememberLazyListState()
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    LazyRow(
        state = listState,
        flingBehavior = flingBehavior,
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth().height((220 * s).dp)
    ) {
        items(channels, key = { it.id }) { channel ->
            // حساب الموضع التقريبي للعنصر بالنسبة للمركز
            val itemInfo = listState.layoutInfo.visibleItemsInfo.find { it.key == channel.id }
            val viewportHalf = (listState.layoutInfo.viewportEndOffset / 2).coerceAtLeast(1)
            val centerOffset = if (itemInfo != null) {
                val itemCenter = itemInfo.offset + itemInfo.size / 2
                (itemCenter - viewportHalf).toFloat() / viewportHalf
            } else 1f

            val isCentered = centerOffset > -0.3f && centerOffset < 0.3f
            val scale by animateFloatAsState(
                targetValue = if (isCentered) 1.1f else 0.85f,
                animationSpec = tween(300), label = "scale"
            )
            val alpha by animateFloatAsState(
                targetValue = if (isCentered) 1f else 0.6f,
                animationSpec = tween(300), label = "alpha"
            )

            GlassCard(
                onClick = {
                    onPlay(
                        PlayRequest(
                            listOf(PlayItem(Kind.LIVE, channel.id, channel.name, icon = channel.icon)),
                            0,
                        ),
                    )
                },
                modifier = Modifier
                    .width((180 * s).dp)
                    .fillMaxHeight()
                    .zIndex(if (isCentered) 5f else 1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    },
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    RemoteImage(
                        url = channel.icon,
                        fallbackText = channel.name,
                        modifier = Modifier.size((80 * s).dp).clip(CircleShape),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        channel.name,
                        color = Palette.Text,
                        fontSize = (14 * s).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun FeaturedChannelsGrid(
    channels: List<Channel>,
    onPlay: (PlayRequest) -> Unit,
    s: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val isTv = LocalIsTv.current
    val cols = if (isTv) 4 else 3
    val rows = (channels.size + cols - 1) / cols
    val tileHeight = if (isTv) (110 * s).dp else 95.dp
    val gridHeight = (rows * (tileHeight.value.toInt()) + (rows - 1) * 10).dp

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotTitle("القنوات المميزة", Palette.Glow, fontSize = (15 * s).sp)
            Tag("شبكة الأيقونات", Palette.Royal)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxWidth().height(gridHeight),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(channels, key = { it.id }) { channel ->
                FeaturedChannelGridTile(
                    channel = channel,
                    s = s,
                    onClick = {
                        onPlay(
                            PlayRequest(
                                listOf(PlayItem(Kind.LIVE, channel.id, channel.name, icon = channel.icon)),
                                0,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FeaturedChannelCard(
    channel: Channel,
    width: Dp,
    s: Float,
    isFocused: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape((18 * s).dp)
    val cardHeight = (width.value * 0.58f).dp

    Box(
        modifier = modifier
            .width(width)
            .height(cardHeight)
            .onFocusChanged { onFocusChanged(it.isFocused) }
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isFocused) Palette.Royal.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.08f),
                        if (isFocused) Palette.DeepBlue.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.03f),
                    ),
                ),
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Palette.Glow else Color.White.copy(alpha = 0.14f),
                shape = shape,
            )
            .padding((12 * s).dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag("مباشر", if (isFocused) Palette.Glow else Palette.Royal)
                Box(
                    Modifier
                        .size((28 * s).dp)
                        .clip(CircleShape)
                        .background(if (isFocused) Palette.Glow else Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Ic.Play,
                        contentDescription = null,
                        tint = if (isFocused) Color.Black else Color.White,
                        modifier = Modifier.size((14 * s).dp),
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((10 * s).dp),
            ) {
                if (channel.icon.isNotBlank()) {
                    RemoteImage(
                        url = channel.icon,
                        fallbackText = channel.name,
                        modifier = Modifier
                            .size((40 * s).dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    Box(
                        Modifier
                            .size((40 * s).dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Ic.Tv, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size((22 * s).dp))
                    }
                }

                Text(
                    text = channel.name,
                    fontSize = (13 * s).sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun FeaturedChannelGridTile(
    channel: Channel,
    s: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape((16 * s).dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((95 * s).dp)
            .onFocusChanged { isFocused = it.isFocused }
            .clip(shape)
            .clickable(onClick = onClick)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (isFocused) Palette.Royal.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.08f),
                        if (isFocused) Palette.DeepBlue.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.03f),
                    ),
                ),
            )
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Palette.Glow else Color.White.copy(alpha = 0.12f),
                shape = shape,
            )
            .padding((8 * s).dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (channel.icon.isNotBlank()) {
                RemoteImage(
                    url = channel.icon,
                    fallbackText = channel.name,
                    modifier = Modifier
                        .size((38 * s).dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    Modifier
                        .size((38 * s).dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Tv, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size((20 * s).dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = channel.name,
                fontSize = (11 * s).sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

private val FALLBACK_FEATURED_CHANNELS = listOf(
    Channel("rotana_cinema", "روتانا سينما"),
    Channel("rotana_khalijia", "روتانا خليجية"),
    Channel("aljazeera_news", "الجزيرة الإخبارية"),
    Channel("bein_sports_1", "beIN SPORTS 1 HD"),
    Channel("ssc_1", "SSC 1 HD"),
    Channel("mbc_1", "MBC 1 HD"),
)

private suspend fun loadFeaturedChannels(repo: Repository): List<Channel> = withContext(Dispatchers.IO) {
    runCatching {
        val all = repo.peekAllLiveChannels() ?: repo.liveChannels(null)
        if (all.isEmpty()) return@withContext FALLBACK_FEATURED_CHANNELS

        val targetKeywords = listOf(
            listOf("روتانا سينما", "rotana cinema"),
            listOf("روتانا خليجية", "rotana khalijiah", "rotana khalijia"),
            listOf("الجزيرة", "al jazeera", "aljazeera", "العربية", "alarabiya", "الحدث"),
            listOf("bein sports", "bein sport", "ssc", "alkass", "on time"),
            listOf("mbc 1", "mbc drama", "mbc masr", "mbc"),
            listOf("ناشونال", "national geographic", "nat geo", "discovery"),
            listOf("دبي", "dubai", "abu dhabi", "أبوظبي"),
        )

        val matched = mutableListOf<Channel>()
        val seen = mutableSetOf<String>()

        for (keywords in targetKeywords) {
            val candidate = all.firstOrNull { ch ->
                val lower = ch.name.lowercase(Locale.ROOT)
                keywords.any { kw -> lower.contains(kw.lowercase(Locale.ROOT)) } && seen.add(ch.id)
            }
            if (candidate != null) {
                matched.add(candidate)
            }
        }

        for (ch in all) {
            if (matched.size >= 8) break
            if (seen.add(ch.id)) {
                matched.add(ch)
            }
        }

        if (matched.isNotEmpty()) matched else FALLBACK_FEATURED_CHANNELS
    }.getOrDefault(FALLBACK_FEATURED_CHANNELS)
}

@Composable
private fun RecentsStrip(
    recents: List<RecentItem>,
    onOpen: (String) -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DotTitle(Ar.continueWatching, Palette.Glow, fontSize = 15.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(recents, key = { "${it.kind}_${it.id}" }) { item ->
                val progress = if (item.durationMs > 0) {
                    (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                } else null
                GlassCard(
                    onClick = {
                        when (item.kind) {
                            Kind.LIVE -> onPlay(
                                PlayRequest(
                                    listOf(PlayItem(Kind.LIVE, item.id, item.title, icon = item.icon)),
                                    0,
                                ),
                            )
                            Kind.MOVIE -> onPlay(
                                PlayRequest(
                                    listOf(
                                        PlayItem(
                                            Kind.MOVIE,
                                            item.id,
                                            item.title,
                                            subtitle = item.subtitle,
                                            icon = item.icon,
                                            ext = item.ext,
                                        ),
                                    ),
                                    startIndex = 0,
                                    fromStart = false,
                                    initialPositionMs = item.positionMs,
                                ),
                            )
                            Kind.EPISODE -> onPlay(
                                PlayRequest(
                                    listOf(
                                        PlayItem(
                                            Kind.EPISODE,
                                            item.id,
                                            item.title,
                                            subtitle = item.subtitle,
                                            icon = item.icon,
                                            ext = item.ext,
                                            groupId = item.groupId,
                                        ),
                                    ),
                                    startIndex = 0,
                                    fromStart = false,
                                    initialPositionMs = item.positionMs,
                                ),
                            )
                            else -> onOpen(Routes.series(item.id))
                        }
                    },
                    modifier = Modifier.width(210.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RemoteImage(
                                url = item.icon,
                                fallbackText = item.title,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = if (item.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.subtitle.ifBlank { Ar.kindLabel(item.kind) },
                                    fontSize = 11.sp,
                                    color = Palette.Glow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (progress != null) {
                            ProgressLine(progress, height = 3.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsQuickCard(modifier: Modifier, s: Float, onClick: () -> Unit) {
    GlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape((20 * s).dp),
    ) {
        Column(
            Modifier.padding((18 * s).dp),
            verticalArrangement = Arrangement.spacedBy((6 * s).dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Ic.Football, null, tint = Palette.Gold, modifier = Modifier.size((18 * s).dp))
                    Text("مباريات اليوم الحية", fontSize = (14 * s).sp, fontWeight = FontWeight.Bold, color = Palette.Gold)
                }
                Tag("مباشر ⚽", Palette.Live, filled = false)
            }
            Text(
                "قمة الجولة الأوروبية · استوديو تحليلي",
                fontSize = (16 * s).sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text("09:00 م · بتوقيت مكة والكويت", fontSize = (12 * s).sp, color = Palette.Glow)
        }
    }
}

@Composable
private fun AnalogClockFace(
    calendar: Calendar,
    sizeDp: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // 1. Dial Background with subtle cosmic glow
        drawCircle(
            color = Palette.GlassStrong,
            radius = radius,
            center = center,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Palette.Royal.copy(alpha = 0.22f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = Palette.Glow.copy(alpha = 0.28f),
            radius = radius,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()),
        )

        // 2. 12 Hour Ticks
        for (i in 0 until 12) {
            val angleDeg = i * 30f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val isMajor = i % 3 == 0
            val tickLen = if (isMajor) radius * 0.16f else radius * 0.08f
            val strokeW = if (isMajor) 2.4.dp.toPx() else 1.2.dp.toPx()
            val tickColor = if (isMajor) Palette.Glow else Palette.TextDim.copy(alpha = 0.45f)

            val startX = center.x + (radius - tickLen) * kotlin.math.sin(angleRad).toFloat()
            val startY = center.y - (radius - tickLen) * kotlin.math.cos(angleRad).toFloat()
            val endX = center.x + (radius - 2.dp.toPx()) * kotlin.math.sin(angleRad).toFloat()
            val endY = center.y - (radius - 2.dp.toPx()) * kotlin.math.cos(angleRad).toFloat()

            drawLine(
                color = tickColor,
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = strokeW,
                cap = StrokeCap.Round,
            )
        }

        // 3. Hour Hand
        val hours = calendar.get(Calendar.HOUR)
        val minutes = calendar.get(Calendar.MINUTE)
        val seconds = calendar.get(Calendar.SECOND)

        val hourAngleDeg = (hours % 12 + minutes / 60f) * 30f
        val hourAngleRad = Math.toRadians(hourAngleDeg.toDouble())
        val hourHandLen = radius * 0.52f
        val hourEndX = center.x + hourHandLen * kotlin.math.sin(hourAngleRad).toFloat()
        val hourEndY = center.y - hourHandLen * kotlin.math.cos(hourAngleRad).toFloat()

        drawLine(
            brush = Brush.linearGradient(listOf(Palette.Royal, Palette.Glow)),
            start = center,
            end = Offset(hourEndX, hourEndY),
            strokeWidth = 3.6.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 4. Minute Hand
        val minuteAngleDeg = (minutes + seconds / 60f) * 6f
        val minuteAngleRad = Math.toRadians(minuteAngleDeg.toDouble())
        val minuteHandLen = radius * 0.74f
        val minEndX = center.x + minuteHandLen * kotlin.math.sin(minuteAngleRad).toFloat()
        val minEndY = center.y - minuteHandLen * kotlin.math.cos(minuteAngleRad).toFloat()

        drawLine(
            color = Color.White,
            start = center,
            end = Offset(minEndX, minEndY),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // 5. Center Pivot
        drawCircle(
            color = Palette.Gold,
            radius = 3.5.dp.toPx(),
            center = center,
        )
        drawCircle(
            color = Palette.DeepBlue,
            radius = 1.5.dp.toPx(),
            center = center,
        )
    }
}

@Composable
private fun ClockWeatherCard(s: Float) {
    val container = LocalContainer.current
    val weatherData by container.weather.weather.collectAsStateWithLifecycle()

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        container.weather.refreshIfNeeded()
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    val calendar = remember(now) {
        Calendar.getInstance().apply { timeInMillis = now }
    }

    val ar = Locale.forLanguageTag("ar")
    val timeStr = formatNowMillis(now, "hh:mm", ar)
    val amPmStr = formatNowMillis(now, "a", ar)
    val dateStr = formatNowMillis(now, "EEEE، d MMMM", ar)

    val (weatherIcon, weatherColor) = when (weatherData.condition) {
        WeatherCondition.CLEAR_DAY -> Ic.Sun to Palette.Gold
        WeatherCondition.CLEAR_NIGHT -> Ic.Moon to Palette.Glow
        WeatherCondition.PARTLY_CLOUDY -> Ic.Sun to Palette.Gold
        WeatherCondition.CLOUDY -> Ic.Cloud to Palette.Glow
        WeatherCondition.FOG -> Ic.Cloud to Palette.TextDim
        WeatherCondition.RAIN -> Ic.Rain to Color(0xFF60A5FA)
        WeatherCondition.SNOW -> Ic.Cloud to Color.White
        WeatherCondition.THUNDERSTORM -> Ic.Thunderstorm to Color(0xFFFBBF24)
    }

    val shape = RoundedCornerShape((22 * s).dp)

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Palette.GlassStrong)
            .border(1.dp, Palette.GlassBorder, shape)
            .padding((14 * s).dp),
    ) {
        val isNarrow = maxWidth < 260.dp

        if (isNarrow) {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy((10 * s).dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((10 * s).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnalogClockFace(calendar = calendar, sizeDp = (54 * s).dp)
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                timeStr,
                                fontSize = (26 * s).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                " $amPmStr",
                                fontSize = (12 * s).sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Glow,
                                modifier = Modifier.padding(bottom = (3 * s).dp),
                            )
                        }
                        Text(dateStr, fontSize = (12 * s).sp, color = Palette.TextDim, maxLines = 1)
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape((14 * s).dp))
                        .background(Palette.Glass)
                        .border(1.dp, Palette.GlassBorder, RoundedCornerShape((14 * s).dp))
                        .padding(horizontal = (10 * s).dp, vertical = (6 * s).dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((6 * s).dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(weatherIcon, null, tint = weatherColor, modifier = Modifier.size((18 * s).dp))
                        Text("${weatherData.tempC}°C", fontSize = (14 * s).sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Text(
                        "${weatherData.city} · ${weatherData.conditionText}",
                        fontSize = (11 * s).sp,
                        fontWeight = FontWeight.Medium,
                        color = Palette.Glow,
                        maxLines = 1,
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy((12 * s).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnalogClockFace(calendar = calendar, sizeDp = (64 * s).dp)
                    Column(verticalArrangement = Arrangement.spacedBy((2 * s).dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                timeStr,
                                fontSize = (32 * s).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Text(
                                " $amPmStr",
                                fontSize = (14 * s).sp,
                                fontWeight = FontWeight.Bold,
                                color = Palette.Glow,
                                modifier = Modifier.padding(bottom = (4 * s).dp),
                            )
                        }
                        Text(dateStr, fontSize = (13 * s).sp, color = Palette.TextDim, maxLines = 1)
                    }
                }

                Box(
                    Modifier
                        .clip(RoundedCornerShape((16 * s).dp))
                        .background(Palette.Glass)
                        .border(1.dp, Palette.GlassBorder, RoundedCornerShape((16 * s).dp))
                        .padding(horizontal = (14 * s).dp, vertical = (10 * s).dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy((2 * s).dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((6 * s).dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(weatherIcon, null, tint = weatherColor, modifier = Modifier.size((22 * s).dp))
                            Text(
                                "${weatherData.tempC}°C",
                                fontSize = (20 * s).sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Text(
                            "${weatherData.city} · ${weatherData.conditionText}",
                            fontSize = (12 * s).sp,
                            fontWeight = FontWeight.Medium,
                            color = Palette.Glow,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeroCard(
    cat: HomeSectionCard,
    modifier: Modifier,
    s: Float,
    autoFocus: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val light = LocalLightEffects.current
    val requester = remember { FocusRequester() }
    val scale by animateFloatAsState(if (focused && !light) 1.05f else 1f, label = "cardScale")
    val shape = RoundedCornerShape((22 * s).dp)

    val glowAlpha = if (focused) {
        if (light) {
            0.85f
        } else {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.45f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "pulseAlpha",
            )
            pulseAlpha
        }
    } else {
        0f
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(120)
            runCatching { requester.requestFocus() }
        }
    }

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (focused && !light) {
                    Modifier.shadow(
                        elevation = 24.dp,
                        shape = shape,
                        ambientColor = Palette.Glow.copy(alpha = glowAlpha),
                        spotColor = Palette.Glow.copy(alpha = glowAlpha),
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(cat.art)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Palette.Glow else Color.White.copy(alpha = 0.14f),
                shape = shape,
            )
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        listOf(
                            cat.accent.copy(alpha = 0.24f),
                            Palette.DeepBlue.copy(alpha = 0.45f),
                            Palette.DeepBlue.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding((16 * s).dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size((44 * s).dp)
                    .clip(RoundedCornerShape((14 * s).dp))
                    .background(Palette.Royal.copy(alpha = 0.45f))
                    .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape((14 * s).dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    cat.icon,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size((24 * s).dp),
                )
            }
            Tag(cat.badge, Palette.Glow)
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding((18 * s).dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                cat.label,
                fontSize = (22 * s).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                cat.sub,
                fontSize = (13 * s).sp,
                color = Palette.TextDim,
                maxLines = 1,
            )
        }
    }
}

/** Bottom quick bar matching the reference footer ("مباريات اليوم الحية" + quick channel badges). */
@Composable
fun LiveMatchesBar(
    onOpenLive: () -> Unit,
    onOpenSports: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Glass)
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DotTitle("مباريات اليوم الحية", Palette.Gold, fontSize = 13.sp, color = Palette.Gold)
        LazyRow(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            item {
                GlassCard(
                    onClick = onOpenSports,
                    shape = RoundedCornerShape(12.dp),
                    focusScale = 1.03f,
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Palette.Royal.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("UCL", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Palette.Glow)
                        }
                        Text(
                            "قمة أبطال أوروبا · مباشر",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Palette.Text,
                            maxLines = 1,
                        )
                    }
                }
            }
            item { QuickChannelBadge("beIN", Palette.Blue, Palette.Glow, onOpenLive) }
            item { QuickChannelBadge("mbc", Palette.Live, Palette.Live, onOpenLive) }
            item { QuickChannelBadge("سحاب", Palette.Royal, Palette.Glow, onOpenLive) }
        }
    }
}

@Composable
private fun QuickChannelBadge(
    label: String,
    bgTint: Color,
    textTint: Color,
    onClick: () -> Unit,
) {
    GlassCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        focusScale = 1.06f,
    ) {
        Box(
            Modifier
                .background(bgTint.copy(alpha = 0.20f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textTint, maxLines = 1)
        }
    }
}
