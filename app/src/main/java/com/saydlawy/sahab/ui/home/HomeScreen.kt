package com.saydlawy.sahab.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FocusBlue = Color(0xFF7DC4FF)
private val DimText = Color(0xFF9AA7D6)

// === Change images here ===
private const val BG_URL = "https://images.unsplash.com/photo-1560448204-e02f11c3d0e2?q=80&w=1920&auto=format&fit=crop"
private const val LIVE_BG = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?q=80&w=800&auto=format&fit=crop"
private const val MOVIES_BG = "https://images.unsplash.com/photo-1485846234645-a62644f84728?q=80&w=800&auto=format&fit=crop"
private const val SERIES_BG = "https://images.unsplash.com/photo-1593359677879-a4bb92f829d1?q=80&w=800&auto=format&fit=crop"

private data class Category(
    val label: String,
    val sub: String,
    val route: String,
    val icon: ImageVector,
    val art: Brush,
    val bgUrl: String,
)

@Composable
fun HomeScreen(onOpen: (String) -> Unit, onPlay: (PlayRequest) -> Unit) {
    val isTv = LocalIsTv.current
    val scrollState = rememberScrollState()

    val categories = remember {
        listOf(
            Category(Ar.live, Ar.liveSub, Routes.LIVE, Ic.Tv,
                Brush.linearGradient(listOf(Color(0xFF4FC3FF), Color(0xFF1B5FD6), Color(0xFF0B1F5C))),
                LIVE_BG),
            Category(Ar.movies, Ar.moviesSub, Routes.MOVIES, Ic.Movie,
                Brush.linearGradient(listOf(Color(0xFFFF8AD8), Color(0xFF9B2FD6), Color(0xFF2A0B5C))),
                MOVIES_BG),
            Category(Ar.series, Ar.seriesSub, Routes.SERIES, Ic.Series,
                Brush.linearGradient(listOf(Color(0xFFFFB36B), Color(0xFFD83D7A), Color(0xFF3A0B2E))),
                SERIES_BG),
        )
    }

    var showWidgets by remember { mutableStateOf(false) }
    var showLive by remember { mutableStateOf(false) }
    var showMovies by remember { mutableStateOf(false) }
    var showSeries by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60); showWidgets = true
        delay(80); showLive = true
        delay(80); showMovies = true
        delay(80); showSeries = true
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF05060F))
    ) {
        val wide = isTv || maxWidth > maxHeight

        // Layer 1: Background image (parallax on phone)
        val bgParallax = if (wide) 0f else scrollState.value * 0.3f
        RemoteImage(
            url = BG_URL,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.22f)
                .graphicsLayer { translationY = -bgParallax },
            contentScale = ContentScale.Crop,
        )

        // Layer 2: Dark gradient overlay
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.35f),
                            Color.Black.copy(alpha = 0.75f),
                            Color(0xFF05060F).copy(alpha = 0.96f),
                        )
                    )
                )
        )

        // Layer 3: Content
        val s = (maxHeight.value / 720f).coerceIn(0.7f, 2f)
        if (wide) {
            WideHome(s, categories, isTv, onOpen, showWidgets, showLive, showMovies, showSeries)
        } else {
            PhoneHome(categories, onOpen, scrollState, showWidgets, showLive, showMovies, showSeries)
        }
    }
}

@Composable
private fun WideHome(
    s: Float,
    categories: List<Category>,
    autoFocus: Boolean,
    onOpen: (String) -> Unit,
    showWidgets: Boolean, showLive: Boolean, showMovies: Boolean, showSeries: Boolean,
) {
    val shows = listOf(showLive, showMovies, showSeries)
    Row(
        Modifier
            .fillMaxSize()
            .padding(horizontal = (56 * s).dp, vertical = (40 * s).dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            Modifier.width((300 * s).dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy((22 * s).dp),
        ) {
            ActionIcons(size = (44 * s).dp, onOpen = onOpen)
            AnimatedVisibility(
                visible = showWidgets,
                enter = fadeIn(tween(400)) + slideInVertically(tween(400), initialOffsetY = { it / 6 }),
            ) {
                SportsCard(Modifier.fillMaxWidth(), s) { onOpen(Routes.SPORTS) }
            }
            AnimatedVisibility(
                visible = showWidgets,
                enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(tween(500, delayMillis = 100), initialOffsetY = { it / 6 }),
            ) {
                ClockWeather(s)
            }
        }

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy((22 * s).dp)) {
            categories.forEachIndexed { i, cat ->
                AnimatedVisibility(
                    visible = shows[i],
                    enter = fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.92f),
                ) {
                    CategoryCard(
                        cat = cat,
                        modifier = Modifier.width((200 * s).dp).height((320 * s).dp),
                        s = s,
                        autoFocus = autoFocus && i == 0,
                        onClick = { onOpen(cat.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhoneHome(
    categories: List<Category>,
    onOpen: (String) -> Unit,
    scrollState: ScrollState,
    showWidgets: Boolean, showLive: Boolean, showMovies: Boolean, showSeries: Boolean,
) {
    val shows = listOf(showLive, showMovies, showSeries)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ActionIcons(size = 44.dp, onOpen = onOpen)
        AnimatedVisibility(
            visible = showWidgets,
            enter = fadeIn(tween(400)) + slideInVertically(tween(400), initialOffsetY = { it / 6 }),
        ) {
            ClockWeather(s = 0.8f)
        }
        AnimatedVisibility(
            visible = showWidgets,
            enter = fadeIn(tween(500, delayMillis = 100)) + slideInVertically(tween(500, delayMillis = 100), initialOffsetY = { it / 6 }),
        ) {
            SportsCard(Modifier.fillMaxWidth(), 0.8f) { onOpen(Routes.SPORTS) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            categories.forEachIndexed { i, cat ->
                Box(Modifier.weight(1f)) {
                    AnimatedVisibility(
                        visible = shows[i],
                        enter = fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.92f),
                    ) {
                        CategoryCard(
                            cat = cat,
                            modifier = Modifier.fillMaxWidth().aspectRatio(0.625f),
                            s = 0.6f,
                            autoFocus = false,
                            onClick = { onOpen(cat.route) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionIcons(size: androidx.compose.ui.unit.Dp, onOpen: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassIconButton(Ic.Heart, Ar.favorites, { onOpen(Routes.FAVORITES) }, size = size)
        GlassIconButton(Ic.Settings, Ar.settings, { onOpen(Routes.SETTINGS) }, size = size)
    }
}

@Composable
private fun SportsCard(modifier: Modifier, s: Float, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "sportsScale")
    val shape = RoundedCornerShape((20 * s).dp)

    Column(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(Color.White.copy(alpha = 0.06f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) FocusBlue else Color.White.copy(alpha = 0.12f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding((20 * s).dp),
        verticalArrangement = Arrangement.spacedBy((6 * s).dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Ic.Football, null, tint = FocusBlue, modifier = Modifier.size((18 * s).dp))
            Text("مباريات اليوم", fontSize = (15 * s).sp, color = DimText)
        }
        Text(
            "الفريق الأول  vs  الفريق الثاني",
            fontSize = (19 * s).sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            maxLines = 1,
        )
        Text("9:00 م", fontSize = (14 * s).sp, color = FocusBlue)
    }
}

@Composable
private fun ClockWeather(s: Float) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }
    val ar = Locale.forLanguageTag("ar")
    val time = now.format(DateTimeFormatter.ofPattern("hh:mm", ar))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE، d MMMM", ar))

    Column(
        Modifier.padding(horizontal = (8 * s).dp),
        verticalArrangement = Arrangement.spacedBy((10 * s).dp),
    ) {
        Crossfade(targetState = time, animationSpec = tween(500), label = "clockFade") { target ->
            Text(
                target,
                fontSize = (72 * s).sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                maxLines = 1,
            )
        }
        Text(date, fontSize = (17 * s).sp, color = DimText, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Ic.Sun, null, tint = Palette.Peach, modifier = Modifier.size((32 * s).dp))
            Text("27° · الكويت", fontSize = (20 * s).sp, color = Color.White)
        }
    }
}

@Composable
private fun CategoryCard(
    cat: Category,
    modifier: Modifier,
    s: Float,
    autoFocus: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "cardScale")
    val shape = RoundedCornerShape((18 * s).dp)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )
    val glowAlpha = if (focused) pulseAlpha else 0f

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            delay(400)
            requester.requestFocus()
        }
    }

    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(
                elevation = if (focused) 24.dp else 0.dp,
                shape = shape,
                ambientColor = FocusBlue.copy(alpha = glowAlpha),
                spotColor = FocusBlue.copy(alpha = glowAlpha),
            )
            .clip(shape)
            .background(cat.art)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) FocusBlue.copy(alpha = 0.4f + 0.6f * glowAlpha) else Color.White.copy(alpha = 0.12f),
                shape = shape,
            )
            .focusRequester(requester)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
    ) {
        RemoteImage(
            url = cat.bgUrl,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.85f),
                        )
                    )
                )
        )
        Icon(
            cat.icon, null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopStart).padding((20 * s).dp).size((44 * s).dp),
        )
        Column(
            Modifier.align(Alignment.BottomStart).padding((20 * s).dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(cat.label, fontSize = (22 * s).sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1)
            Text(cat.sub, fontSize = (13 * s).sp, color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
    }
}
