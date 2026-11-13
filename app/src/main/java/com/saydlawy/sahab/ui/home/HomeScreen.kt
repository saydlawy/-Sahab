package com.saydlawy.sahab.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.R
import com.saydlawy.sahab.data.Kind
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
import kotlinx.coroutines.delay
import java.util.Locale

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
    val isTv = LocalIsTv.current
    val scrollState = rememberScrollState()
    val container = LocalContainer.current
    val recents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())

    val categories = remember {
        listOf(
            HomeSectionCard(
                label = Ar.live,
                sub = Ar.liveSub,
                route = Routes.LIVE,
                badge = "LIVE 4K",
                icon = Ic.Tv,
                art = Brush.linearGradient(listOf(Palette.Glow.copy(alpha = 0.42f), Palette.Blue.copy(alpha = 0.65f), Palette.DeepBlue)),
                accent = Palette.Glow,
            ),
            HomeSectionCard(
                label = Ar.movies,
                sub = Ar.moviesSub,
                route = Routes.MOVIES,
                badge = "VOD",
                icon = Ic.Movie,
                art = Brush.linearGradient(listOf(Palette.Royal.copy(alpha = 0.58f), Color(0xFF4C1D95), Palette.DeepBlue)),
                accent = Palette.Royal,
            ),
            HomeSectionCard(
                label = Ar.series,
                sub = Ar.seriesSub,
                route = Routes.SERIES,
                badge = "HD / 4K",
                icon = Ic.Series,
                art = Brush.linearGradient(listOf(Palette.Gold.copy(alpha = 0.48f), Palette.Royal.copy(alpha = 0.65f), Palette.DeepBlue)),
                accent = Palette.Gold,
            ),
        )
    }

    val displayMode = LocalDisplayMode.current
    val maxH = LocalWindowInfo.current.heightDp

    Box(
        Modifier
            .fillMaxSize()
            .background(Palette.DeepBlue),
    ) {
        Image(
            painter = painterResource(R.drawable.home_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Palette.DeepBlue.copy(alpha = 0.45f),
                            Palette.DeepBlue.copy(alpha = 0.75f),
                            Palette.DeepBlue.copy(alpha = 0.95f),
                        ),
                    ),
                ),
        )

        Column(Modifier.fillMaxSize()) {
            SahabTopBar(current = Routes.HOME)
            val s = (maxH.value / 720f).coerceIn(0.82f, 1.35f)
            when (displayMode) {
                DisplayMode.TABLET_OR_TV -> {
                    WideHome(
                        s = s,
                        categories = categories,
                        recents = recents,
                        autoFocus = isTv,
                        onOpen = onOpen,
                        onPlay = onPlay,
                        modifier = Modifier.weight(1f),
                    )
                }
                DisplayMode.PHONE_LANDSCAPE -> {
                    PhoneLandscapeHome(
                        categories = categories,
                        recents = recents,
                        onOpen = onOpen,
                        onPlay = onPlay,
                        scrollState = scrollState,
                        modifier = Modifier.weight(1f),
                    )
                }
                DisplayMode.PHONE_PORTRAIT -> {
                    PhoneHome(
                        categories = categories,
                        recents = recents,
                        onOpen = onOpen,
                        onPlay = onPlay,
                        scrollState = scrollState,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WideHome(
    s: Float,
    categories: List<HomeSectionCard>,
    recents: List<RecentItem>,
    autoFocus: Boolean,
    onOpen: (String) -> Unit,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                Modifier
                    .width((320 * s).dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                ClockWeatherCard(s)
                SportsQuickCard(Modifier.fillMaxWidth(), s) { onOpen(Routes.SPORTS) }
            }

            Spacer(Modifier.weight(1f))

            Row(horizontalArrangement = Arrangement.spacedBy((20 * s).dp)) {
                categories.forEachIndexed { i, cat ->
                    SectionHeroCard(
                        cat = cat,
                        modifier = Modifier
                            .width((210 * s).dp)
                            .height((320 * s).dp),
                        s = s,
                        autoFocus = autoFocus && i == 0,
                        onClick = { onOpen(cat.route) },
                    )
                }
            }
        }

        if (recents.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            RecentsStrip(recents = recents.take(10), onOpen = onOpen, onPlay = onPlay)
        }

        Spacer(Modifier.height(10.dp))
        LiveMatchesBar(onOpenLive = { onOpen(Routes.LIVE) }, onOpenSports = { onOpen(Routes.SPORTS) })
    }
}

@Composable
private fun PhoneLandscapeHome(
    categories: List<HomeSectionCard>,
    recents: List<RecentItem>,
    onOpen: (String) -> Unit,
    onPlay: (PlayRequest) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.width(235.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ClockWeatherCard(s = 0.78f)
                SportsQuickCard(Modifier.fillMaxWidth(), 0.85f) { onOpen(Routes.SPORTS) }
            }

            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                categories.forEach { cat ->
                    SectionHeroCard(
                        cat = cat,
                        modifier = Modifier
                            .weight(1f)
                            .height(206.dp),
                        s = 0.82f,
                        autoFocus = false,
                        onClick = { onOpen(cat.route) },
                    )
                }
            }
        }

        if (recents.isNotEmpty()) {
            RecentsStrip(recents = recents.take(10), onOpen = onOpen, onPlay = onPlay)
        }

        LiveMatchesBar(onOpenLive = { onOpen(Routes.LIVE) }, onOpenSports = { onOpen(Routes.SPORTS) })
    }
}

@Composable
private fun PhoneHome(
    categories: List<HomeSectionCard>,
    recents: List<RecentItem>,
    onOpen: (String) -> Unit,
    onPlay: (PlayRequest) -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ClockWeatherCard(s = 0.85f)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            categories.forEach { cat ->
                SectionHeroCard(
                    cat = cat,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.68f),
                    s = 0.85f,
                    autoFocus = false,
                    onClick = { onOpen(cat.route) },
                )
            }
        }
        if (recents.isNotEmpty()) {
            RecentsStrip(recents = recents.take(10), onOpen = onOpen, onPlay = onPlay)
        }
        SportsQuickCard(Modifier.fillMaxWidth(), 0.9f) { onOpen(Routes.SPORTS) }
        LiveMatchesBar(onOpenLive = { onOpen(Routes.LIVE) }, onOpenSports = { onOpen(Routes.SPORTS) })
    }
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
private fun ClockWeatherCard(s: Float) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val ar = Locale.forLanguageTag("ar")
    val time = formatNowMillis(now, "hh:mm", ar)
    val date = formatNowMillis(now, "EEEE، d MMMM", ar)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((22 * s).dp))
            .background(Palette.Glass)
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape((22 * s).dp))
            .padding((18 * s).dp),
        verticalArrangement = Arrangement.spacedBy((6 * s).dp),
    ) {
        Crossfade(targetState = time, animationSpec = tween(500), label = "clockFade") { target ->
            Text(
                target,
                fontSize = (54 * s).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
        }
        Text(date, fontSize = (15 * s).sp, color = Palette.TextDim, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Ic.Sun, null, tint = Palette.Gold, modifier = Modifier.size((24 * s).dp))
            Text("الكويت · 27°C", fontSize = (15 * s).sp, fontWeight = FontWeight.Bold, color = Palette.Glow)
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
