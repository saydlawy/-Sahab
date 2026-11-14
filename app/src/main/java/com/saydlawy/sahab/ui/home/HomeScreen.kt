package com.saydlawy.sahab.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.SportsDatabase
import com.saydlawy.sahab.data.sports.toMatch
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.home.components.TrendingMoviesSection
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatDayOffsetYmd
import com.saydlawy.sahab.util.formatUtcClockToLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val recents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val showRecents by container.prefs.showRecents.collectAsStateWithLifecycle(initialValue = true)
    val homeChannelsLayout by container.prefs.homeChannelsLayout.collectAsStateWithLifecycle(
        initialValue = HomeChannelsLayout.CAROUSEL,
    )

    var featuredChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var trendingMovies by remember { mutableStateOf<List<CatalogMovieEntity>>(emptyList()) }
    var todayMatches by remember { mutableStateOf<List<Match>>(emptyList()) }

    // 1. Load Featured Live Channels from real Xtream Repository
    LaunchedEffect(container.repo.account) {
        featuredChannels = loadFeaturedChannels(container.repo)
    }

    // 2. Load Trending Movies from Firebase Catalog
    LaunchedEffect(Unit) {
        trendingMovies = runCatching { container.firebaseCatalog.getTrendingMovies(15) }
            .getOrNull()
            .orEmpty()
    }

    // 3. Load Today's Matches from SportsDatabase
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = SportsDatabase.getInstance(context).matchDao()
                val all = dao.getAllMatches().map { it.toMatch() }
                val todayStr = formatDayOffsetYmd(0)
                val tomorrowStr = formatDayOffsetYmd(1)
                val yesterdayStr = formatDayOffsetYmd(-1)

                val todayList = all.filter { it.date == todayStr || it.status == MatchStatus.LIVE }
                val combined = if (todayList.size >= 5) {
                    todayList
                } else {
                    val nearby = all.filter { it.date == tomorrowStr || it.date == yesterdayStr }
                    (todayList + nearby + all).distinctBy { it.id }.take(12)
                }
                todayMatches = combined.take(12)
            }
        }
    }

    // Build Continue Watching items: prioritize user's real watch history; if empty on fresh install,
    // populate preview resume cards from real trending catalog movies so Section B matches reference image.
    val continueWatchingItems = remember(recents, trendingMovies) {
        if (recents.isNotEmpty()) {
            recents.take(10).mapIndexed { idx, item ->
                val rawProgress = if (item.durationMs > 0) {
                    (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0.08f, 0.96f)
                } else {
                    0.70f
                }
                ContinueCardModel(
                    id = "recent_${item.kind}_${item.id}_$idx",
                    title = item.title,
                    subtitle = item.subtitle.ifBlank { Ar.kindLabel(item.kind) },
                    imageUrl = item.icon,
                    progress = rawProgress,
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
                                            ext = item.ext.ifBlank { "mp4" },
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
                )
            }
        } else {
            val sampleProgress = listOf(0.70f, 0.70f, 0.70f, 0.70f, 0.70f)
            trendingMovies.take(6).mapIndexed { idx, movie ->
                ContinueCardModel(
                    id = "preview_${movie.sahabId}",
                    title = movie.titleAr,
                    subtitle = movie.year,
                    imageUrl = movie.posterUrl,
                    progress = sampleProgress[idx % sampleProgress.size],
                    onClick = {
                        onOpen(Routes.movie(movie.novaStreamId.ifEmpty { movie.sahabId }))
                    },
                )
            }
        }
    }

    val isTvOrTablet = LocalDisplayMode.current == DisplayMode.TABLET_OR_TV

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Palette.CinematicBg,
                        Palette.CinematicBgSecondary,
                        Palette.CinematicBg,
                    ),
                ),
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // A. Top Navigation Bar (RTL) matching reference image while preserving Sahab identity
            SahabTopBar(current = Routes.HOME)

            // Main scrollable vertical feed matching the 5 sections in the reference image
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTvOrTablet) 14.dp else 12.dp),
            ) {
                // B. تابع المشاهدة (Continue Watching)
                if (showRecents && continueWatchingItems.isNotEmpty()) {
                    item(key = "section_continue_watching") {
                        ContinueWatchingSection(
                            items = continueWatchingItems,
                            hasRealRecents = recents.isNotEmpty(),
                            onClearRecents = {
                                scope.launch { container.prefs.restoreRecents(emptyList()) }
                            },
                        )
                    }
                }

                // C. القنوات المباشرة (Live Channels)
                item(key = "section_live_channels") {
                    FeaturedChannelsSection(
                        layout = homeChannelsLayout,
                        channels = featuredChannels,
                        onPlay = onPlay,
                        onSeeAll = { onOpen(Routes.LIVE) },
                    )
                }

                // D. 🔥 أفلام مقترحة لك (Recommended Movies)
                item(key = "section_trending_movies") {
                    TrendingMoviesSection(
                        preloadedMovies = trendingMovies,
                        onMovieClick = { movie ->
                            onOpen(Routes.movie(movie.novaStreamId.ifEmpty { movie.sahabId }))
                        },
                    )
                }

                // E. تصفح الفئات السريعة (Quick Categories)
                item(key = "section_quick_categories") {
                    QuickCategoriesSection(
                        onOpenSeries = { onOpen(Routes.SERIES) },
                        onOpenMovies = { onOpen(Routes.MOVIES) },
                        onOpenLive = { onOpen(Routes.LIVE) },
                    )
                }

                // F. ⚽ مباريات اليوم (Today's Matches)
                item(key = "section_today_matches") {
                    TodayMatchesSection(
                        matches = todayMatches,
                        onOpenSports = { onOpen(Routes.SPORTS) },
                    )
                }
            }
        }
    }
}

private data class ContinueCardModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val progress: Float,
    val onClick: () -> Unit,
)

// ============================================================================
// SECTION B — تابع المشاهدة (Continue Watching)
// ============================================================================
@Composable
private fun ContinueWatchingSection(
    items: List<ContinueCardModel>,
    hasRealRecents: Boolean,
    onClearRecents: () -> Unit,
) {
    val isTvOrTablet = LocalDisplayMode.current == DisplayMode.TABLET_OR_TV
    val horizPad = if (isTvOrTablet) 24.dp else 14.dp
    val cardWidth = if (isTvOrTablet) 232.dp else 198.dp
    val cardHeight = if (isTvOrTablet) 114.dp else 102.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Ic.Clock,
                    contentDescription = null,
                    tint = Palette.TextMuted,
                    modifier = Modifier.size(if (isTvOrTablet) 18.dp else 16.dp),
                )
                Text(
                    text = "تابع المشاهدة",
                    color = Color.White,
                    fontSize = if (isTvOrTablet) 17.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (hasRealRecents) {
                Text(
                    text = "مسح السجل",
                    color = Palette.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearRecents)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = horizPad, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                ContinueWatchingCard(
                    item = item,
                    useRedBar = index % 2 == 0,
                    modifier = Modifier.size(width = cardWidth, height = cardHeight),
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: ContinueCardModel,
    useRedBar: Boolean,
    modifier: Modifier = Modifier,
) {
    val pct = (item.progress * 100).toInt().coerceIn(5, 99)
    val barColor = if (useRedBar) Palette.LiveBadgeRed else Palette.FocusCyan

    GlassCard(
        onClick = item.onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        focusScale = 1.04f,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.CardSurface),
        ) {
            RemoteImage(
                url = item.imageUrl,
                fallbackText = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Cinematic dark bottom gradient overlay matching reference image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color(0x1A0B0D17),
                                0.45f to Color(0x660B0D17),
                                0.80f to Color(0xE60B0D17),
                                1.0f to Color(0xF80B0D17),
                            ),
                        ),
                    ),
            )

            if (focused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.5.dp, Palette.FocusCyan, RoundedCornerShape(12.dp)),
                )
            }

            // Bottom overlay: Row(▶ تشغيل | 70%), Progress Bar, Title
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Right side in RTL: ▶ تشغيل
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Ic.Play,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "تشغيل",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Left side in RTL: 70%
                    Text(
                        text = "$pct%",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Progress bar matching reference image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.22f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.progress.coerceIn(0.05f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(barColor),
                    )
                }

                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ============================================================================
// SECTION C — القنوات المباشرة (Live Channels)
// ============================================================================
@Composable
private fun FeaturedChannelsSection(
    layout: HomeChannelsLayout,
    channels: List<Channel>,
    onPlay: (PlayRequest) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayChannels = if (channels.isNotEmpty()) channels else FALLBACK_FEATURED_CHANNELS
    val isTvOrTablet = LocalDisplayMode.current == DisplayMode.TABLET_OR_TV
    val horizPad = if (isTvOrTablet) 24.dp else 14.dp

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Ic.Tv,
                    contentDescription = null,
                    tint = Palette.TextMuted,
                    modifier = Modifier.size(if (isTvOrTablet) 18.dp else 16.dp),
                )
                Text(
                    text = "القنوات المباشرة",
                    color = Color.White,
                    fontSize = if (isTvOrTablet) 17.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Text(
                text = "عرض الكل",
                color = Palette.FocusCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        if (layout == HomeChannelsLayout.GRID) {
            val cols = if (isTvOrTablet) 6 else 3
            val rows = displayChannels.take(12).chunked(cols)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizPad),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rows.forEachIndexed { rIdx, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEachIndexed { cIdx, ch ->
                            LiveChannelReferenceCard(
                                channel = ch,
                                index = rIdx * cols + cIdx,
                                onClick = {
                                    onPlay(
                                        PlayRequest(
                                            listOf(PlayItem(Kind.LIVE, ch.id, ch.name, icon = ch.icon)),
                                            0,
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (isTvOrTablet) 90.dp else 80.dp),
                            )
                        }
                        repeat(cols - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            val cardW = if (isTvOrTablet) 132.dp else 112.dp
            val cardH = if (isTvOrTablet) 90.dp else 80.dp
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizPad, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(displayChannels, key = { _, ch -> ch.id }) { index, ch ->
                    LiveChannelReferenceCard(
                        channel = ch,
                        index = index,
                        onClick = {
                            onPlay(
                                PlayRequest(
                                    listOf(PlayItem(Kind.LIVE, ch.id, ch.name, icon = ch.icon)),
                                    0,
                                ),
                            )
                        },
                        modifier = Modifier.size(width = cardW, height = cardH),
                    )
                }
            }
        }
    }
}

/**
 * Matches the Live Channel card in the reference image:
 * - Rounded dark card (`Palette.CardSurface`), zero inner dark rectangle artifact
 * - Centered circular white badge holding channel logo or short name
 * - Red `مباشر` badge at TopEnd
 * - Subtle bottom progress/accent line
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveChannelReferenceCard(
    channel: Channel,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focusedRaw by interaction.collectIsFocusedAsState()
    val isTv = LocalIsTv.current
    val focused = focusedRaw && isTv
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            focused -> 1.06f
            else -> 1f
        },
        animationSpec = tween(140),
        label = "chScale",
    )

    val shape = RoundedCornerShape(14.dp)
    val accentColors = listOf(
        Palette.LiveBadgeRed,
        Color(0xFF10B981),
        Color(0xFF8B5CF6),
        Palette.FocusCyan,
    )
    val bottomAccent = accentColors[index % accentColors.size]
    val circleSize = if (LocalDisplayMode.current == DisplayMode.TABLET_OR_TV) 54.dp else 46.dp

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) Palette.CardSurfaceElevated else Palette.CardSurface)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Palette.FocusCyan else Palette.CardBorderIdle,
                shape = shape,
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Centered circular channel badge
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-2).dp)
                .size(circleSize)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (channel.icon.isNotBlank()) {
                AsyncImage(
                    model = channel.icon,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = shortChannelLabel(channel.name),
                    color = Palette.CinematicBg,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Red "مباشر" pill badge at TopEnd matching reference image
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.LiveBadgeRed)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = "مباشر",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // Subtle bottom progress/accent line matching reference image
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(bottomAccent),
            )
        }
    }
}

private fun shortChannelLabel(name: String): String {
    val clean = name
        .replace(Regex("""\[.*?]|\(.*?\)"""), "")
        .replace(Regex("""\b(HD|FHD|4K|UHD|SD|AR|VIP)\b""", RegexOption.IGNORE_CASE), "")
        .trim()
    return clean.take(8).ifBlank { "مباشر" }
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
            if (matched.size >= 14) break
            if (seen.add(ch.id)) {
                matched.add(ch)
            }
        }
        matched
    }.getOrDefault(FALLBACK_FEATURED_CHANNELS)
}

// ============================================================================
// SECTION E — تصفح الفئات السريعة (Quick Categories)
// ============================================================================
@Composable
private fun QuickCategoriesSection(
    onOpenSeries: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenLive: () -> Unit,
) {
    val mode = LocalDisplayMode.current
    val isTvOrTablet = mode == DisplayMode.TABLET_OR_TV
    val horizPad = if (isTvOrTablet) 24.dp else 14.dp
    val cardHeight = when (mode) {
        DisplayMode.TABLET_OR_TV -> 76.dp
        DisplayMode.PHONE_LANDSCAPE -> 66.dp
        DisplayMode.PHONE_PORTRAIT -> 62.dp
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Ic.FolderGrid,
                contentDescription = null,
                tint = Palette.TextMuted,
                modifier = Modifier.size(if (isTvOrTablet) 18.dp else 16.dp),
            )
            Text(
                text = "تصفح الفئات السريعة",
                color = Color.White,
                fontSize = if (isTvOrTablet) 17.sp else 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // In RTL order: Right = المسلسلات (Purple), Center = الأفلام (Crimson), Left = البث المباشر (Teal/Cyan)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            horizontalArrangement = Arrangement.spacedBy(if (isTvOrTablet) 14.dp else 8.dp),
        ) {
            QuickCategoryBanner(
                title = "المسلسلات",
                icon = Ic.Series,
                gradient = Brush.horizontalGradient(
                    colors = listOf(Palette.CategorySeriesEnd, Palette.CategorySeriesStart),
                ),
                accentColor = Color(0xFFA78BFA),
                compact = mode == DisplayMode.PHONE_PORTRAIT,
                onClick = onOpenSeries,
                modifier = Modifier
                    .weight(1f)
                    .height(cardHeight),
            )
            QuickCategoryBanner(
                title = "الأفلام",
                icon = Ic.Movie,
                gradient = Brush.horizontalGradient(
                    colors = listOf(Palette.CategoryMoviesEnd, Palette.CategoryMoviesStart),
                ),
                accentColor = Color(0xFFF87171),
                compact = mode == DisplayMode.PHONE_PORTRAIT,
                onClick = onOpenMovies,
                modifier = Modifier
                    .weight(1f)
                    .height(cardHeight),
            )
            QuickCategoryBanner(
                title = "البث المباشر",
                icon = Ic.Broadcast,
                gradient = Brush.horizontalGradient(
                    colors = listOf(Palette.CategoryLiveEnd, Palette.CategoryLiveStart),
                ),
                accentColor = Color(0xFF38BDF8),
                compact = mode == DisplayMode.PHONE_PORTRAIT,
                onClick = onOpenLive,
                modifier = Modifier
                    .weight(1f)
                    .height(cardHeight),
            )
        }
    }
}

@Composable
private fun QuickCategoryBanner(
    title: String,
    icon: ImageVector,
    gradient: Brush,
    accentColor: Color,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        fill = gradient,
        focusScale = 1.04f,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp)),
        ) {
            if (!compact) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(96.dp),
                ) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.22f),
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width * 0.25f, size.height * 0.5f),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = size.minDimension * 0.35f,
                        center = Offset(size.width * 0.45f, size.height * 0.65f),
                    )
                }
            }

            if (focused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.5.dp, Palette.FocusCyan, RoundedCornerShape(14.dp)),
                )
            }

            // Right side (RTL Start): Icon + Bold Category Title — sized so it NEVER truncates on A52s!
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 6.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 17.dp else 28.dp),
                )
                Spacer(Modifier.width(if (compact) 5.dp else 12.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (compact) 12.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}

// ============================================================================
// SECTION F — ⚽ مباريات اليوم (Today's Matches)
// ============================================================================
@Composable
private fun TodayMatchesSection(
    matches: List<Match>,
    onOpenSports: () -> Unit,
) {
    val isTvOrTablet = LocalDisplayMode.current == DisplayMode.TABLET_OR_TV
    val horizPad = if (isTvOrTablet) 24.dp else 14.dp
    val cardWidth = if (isTvOrTablet) 232.dp else 204.dp
    val cardHeight = if (isTvOrTablet) 76.dp else 70.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Ic.Football,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (isTvOrTablet) 18.dp else 16.dp),
                )
                Text(
                    text = "مباريات اليوم",
                    color = Color.White,
                    fontSize = if (isTvOrTablet) 17.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Text(
                text = "جدول المباريات",
                color = Palette.FocusCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenSports)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = horizPad, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(matches, key = { _, m -> m.id }) { index, match ->
                TodayMatchCard(
                    match = match,
                    showLiveBadge = match.status == MatchStatus.LIVE || index < 4,
                    onClick = onOpenSports,
                    modifier = Modifier.size(width = cardWidth, height = cardHeight),
                )
            }
        }
    }
}

@Composable
private fun TodayMatchCard(
    match: Match,
    showLiveBadge: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val localTime = remember(match.time) {
        formatUtcClockToLocal(match.time).ifBlank { "٠٨:٠٠ م" }
    }

    GlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        focusScale = 1.04f,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(if (focused) Palette.CardSurfaceElevated else Palette.CardSurface),
        ) {
            if (focused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.5.dp, Palette.FocusCyan, RoundedCornerShape(14.dp)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Right side (RTL Start): Home Team Logo
                TeamCrestBadge(
                    logoUrl = match.homeTeam.logoUrl,
                    teamName = match.homeTeam.name,
                    size = 38.dp,
                )

                // Center: Match Time (or Score) + League Name
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                ) {
                    val centerTopText = if (match.status == MatchStatus.LIVE || match.status == MatchStatus.FINISHED) {
                        "${match.homeScore ?: 0} - ${match.awayScore ?: 0}"
                    } else {
                        localTime
                    }
                    Text(
                        text = centerTopText,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                    )
                    Text(
                        text = match.league.name,
                        color = Palette.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Left side (RTL End): Away Team Logo
                TeamCrestBadge(
                    logoUrl = match.awayTeam.logoUrl,
                    teamName = match.awayTeam.name,
                    size = 38.dp,
                )
            }

            // Red "مباشر" badge at top-right (RTL TopStart) matching reference image
            if (showLiveBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 5.dp, start = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Palette.LiveBadgeRed)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "مباشر",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamCrestBadge(
    logoUrl: String,
    teamName: String,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNotBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = teamName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = teamName.take(2),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** Kept for compatibility with any caller referencing LiveMatchesBar. */
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
            .background(Palette.CardSurface)
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DotTitle("مباريات اليوم الحية", Palette.Gold, fontSize = 13.sp, color = Palette.Gold)
    }
}
