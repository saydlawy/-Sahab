package com.saydlawy.sahab.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.Episode
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.SeriesDetails
import com.saydlawy.sahab.data.ai.AiGenerationState
import com.saydlawy.sahab.data.forAccount
import com.saydlawy.sahab.data.isFav
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabIcon
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.TrailerAutoPlayBox
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.RatingSanitizer
import com.saydlawy.sahab.util.userMessage

private val NetflixRed = Color(0xFFE50914)
private val NetflixMatchGreen = Color(0xFF46D369)

@Composable
fun SeriesDetailScreen(id: String, onBack: () -> Unit, onPlay: (PlayRequest) -> Unit) {
    val vm = appViewModel(key = "series_$id") { SeriesDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val aiState by vm.aiState.collectAsStateWithLifecycle()
    val refreshingMetadata by vm.refreshingMetadata.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val focusNav = LocalFocusNavigation.current
    val activeAccountId by container.prefs.activeAccountId.collectAsStateWithLifecycle(
        initialValue = container.prefs.currentActiveAccountId(),
    )
    val favorites by container.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val allRecents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents = remember(allRecents, activeAccountId) { allRecents.forAccount(activeAccountId) }

    val details = state.data
    val displayMode = LocalDisplayMode.current
    val feedState = rememberRestorableLazyListState("series_detail_feed_$id")
    val showTopBar by remember(displayMode, feedState) {
        derivedStateOf {
            displayMode == DisplayMode.TABLET_OR_TV ||
                !feedState.canScrollBackward ||
                (feedState.firstVisibleItemIndex == 0 && feedState.firstVisibleItemScrollOffset < 28)
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.CinematicBg)) {
        Column(Modifier.fillMaxSize()) {
            if (showTopBar) {
                SahabTopBar(current = Routes.SERIES, onBack = onBack)
            }

            when {
                state.loading -> LoadingBox()
                details == null -> MessageBox(state.error ?: Ar.errUnknown, actionLabel = Ar.retry, onAction = vm::load)
                else -> {
                    val isFavorite = favorites.isFav(activeAccountId, Kind.SERIES, details.id)
                    val showTmdbPicker = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    SeriesNetflixContent(
                        details = details,
                        recents = recents,
                        isFavorite = isFavorite,
                        aiState = aiState,
                        refreshingMetadata = refreshingMetadata,
                        feedState = feedState,
                        onGenerateStory = { vm.generateAiStory() },
                        onToggleFavorite = { vm.toggleFavorite(details) },
                        onRefresh = { showTmdbPicker.value = true },
                        onPlay = onPlay,
                    )
                    if (showTmdbPicker.value) {
                        TmdbMatchPickerDialog(
                            initialQuery = details.name,
                            onSearch = { q -> vm.searchTmdbCandidates(q) },
                            onSelect = { tmdbId -> vm.applyManualTmdbMatch(tmdbId) },
                            onDismiss = {
                                showTmdbPicker.value = false
                                focusNav.restoreAfterDialogDismiss("series/${details.id}")
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun playItem(details: SeriesDetails, episode: Episode) = PlayItem(
    kind = Kind.EPISODE,
    id = episode.id,
    title = details.name,
    subtitle = Ar.seasonEpisode(episode.season, episode.number),
    icon = episode.image.ifBlank { details.cover },
    ext = episode.ext,
    groupId = details.id,
)

@Composable
private fun SeriesNetflixContent(
    details: SeriesDetails,
    recents: List<RecentItem>,
    isFavorite: Boolean,
    aiState: AiGenerationState,
    refreshingMetadata: Boolean,
    feedState: androidx.compose.foundation.lazy.LazyListState,
    onGenerateStory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val focusNav = LocalFocusNavigation.current
    val screenKey = remember(details.id) { "series/${details.id}" }
    val displayMode = LocalDisplayMode.current
    val isPortrait = displayMode == DisplayMode.PHONE_PORTRAIT
    val hPad = when (displayMode) {
        DisplayMode.TABLET_OR_TV -> 28.dp
        DisplayMode.PHONE_LANDSCAPE -> 18.dp
        DisplayMode.PHONE_PORTRAIT -> 14.dp
    }

    val seasons = details.seasons
    var selectedSeasonIndex by remember(details.id) {
        mutableIntStateOf(
            focusNav.getSelectedSeason(details.id).coerceIn(0, (seasons.size - 1).coerceAtLeast(0))
        )
    }
    LaunchedEffect(selectedSeasonIndex) {
        focusNav.saveSelectedSeason(details.id, selectedSeasonIndex)
    }
    val season = seasons.getOrNull(selectedSeasonIndex) ?: seasons.firstOrNull()
    val visibleEpisodes = remember(details.episodes, season) {
        details.episodes.filter { it.season == season }
    }

    val queue = remember(details) { details.episodes.map { playItem(details, it) } }
    val episodeIds = remember(details) { details.episodes.map { it.id }.toSet() }
    val progress = remember(recents, details.id, episodeIds) {
        recents.filter {
            it.kind == Kind.EPISODE && (it.groupId == details.id || it.id in episodeIds)
        }.associateBy { it.id }
    }
    val lastWatched = remember(recents, details.id, episodeIds) {
        recents.firstOrNull {
            it.kind == Kind.EPISODE && (it.groupId == details.id || it.id in episodeIds)
        }
    }
    val resumeIndex = lastWatched?.let { last -> details.episodes.indexOfFirst { it.id == last.id } } ?: -1
    val resumePosMs = lastWatched?.positionMs ?: -1L
    val seasonsRowState = rememberRestorableLazyListState("series_seasons_${details.id}")
    val episodesRowState = rememberRestorableLazyListState("series_episodes_row_${details.id}_$selectedSeasonIndex")

    val backdropAlpha by remember(feedState) {
        derivedStateOf {
            if (feedState.firstVisibleItemIndex > 0) 0f
            else (1f - (feedState.firstVisibleItemScrollOffset / 280f)).coerceIn(0f, 1f)
        }
    }

    val heroMedia = details.backdrop.ifBlank {
        visibleEpisodes.firstOrNull { it.image.isNotBlank() }?.image ?: details.cover
    }

    Box(Modifier.fillMaxSize()) {
        // خلفية بانر Netflix في أعلى الشاشة
        SeriesHeroBackdrop(
            backdropUrl = heroMedia,
            fallbackCover = details.cover,
            trailerUrl = details.trailerUrl,
            title = details.name,
            scrollAlpha = backdropAlpha,
        )

        LazyColumn(
            state = feedState,
            modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. بانر المعلومات والأزرار في أول الصفحة
            item(key = "series_hero_block") {
                SeriesHeroInfoBlock(
                    screenKey = screenKey,
                    details = details,
                    resumeIndex = resumeIndex,
                    resumePositionMs = resumePosMs,
                    queue = queue,
                    isFavorite = isFavorite,
                    refreshingMetadata = refreshingMetadata,
                    onToggleFavorite = onToggleFavorite,
                    onRefresh = onRefresh,
                    onPlay = onPlay,
                    modifier = Modifier
                        .fillMaxWidth(if (isPortrait) 1f else 0.58f)
                        .padding(top = if (isPortrait) 110.dp else 8.dp),
                )
            }

            // 2. شريط تبويبات المواسم
            if (seasons.isNotEmpty()) {
                item(key = "series_seasons_tabs") {
                    LazyRow(
                        state = seasonsRowState,
                        modifier = Modifier.fillMaxWidth().focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        itemsIndexed(seasons, key = { idx, s -> "season_${idx}_$s" }) { index, s ->
                            SeasonPillTab(
                                label = Ar.season(s),
                                selected = index == selectedSeasonIndex,
                                screenKey = screenKey,
                                focusKey = "season_tab_$s",
                                onClick = { selectedSeasonIndex = index },
                            )
                        }
                    }
                }
            }

            // 3. سلايدر الحلقات الأفقي وبجانب كل حلقة رقم ضخم 1، 2، 3...
            item(key = "series_episodes_slider") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DotTitle(
                        text = if (season != null) "${Ar.season(season)}  •  ${visibleEpisodes.size} حلقة" else Ar.seriesSub,
                        dot = NetflixRed,
                        fontSize = 16.sp,
                    )

                    if (visibleEpisodes.isEmpty()) {
                        Text(Ar.noEpisodes, fontSize = 14.sp, color = Palette.TextDim)
                    } else {
                        val density = LocalDensity.current
                        val cardWidth = when (displayMode) {
                            DisplayMode.TABLET_OR_TV -> 210.dp
                            DisplayMode.PHONE_LANDSCAPE -> 185.dp
                            DisplayMode.PHONE_PORTRAIT -> 175.dp
                        }
                        val cardHeight = when (displayMode) {
                            DisplayMode.TABLET_OR_TV -> 118.dp
                            DisplayMode.PHONE_LANDSCAPE -> 104.dp
                            DisplayMode.PHONE_PORTRAIT -> 98.dp
                        }
                        val cardWidthPx = remember(density, cardWidth) { with(density) { cardWidth.roundToPx() } }
                        val cardHeightPx = remember(density, cardHeight) { with(density) { cardHeight.roundToPx() } }

                        LazyRow(
                            state = episodesRowState,
                            modifier = Modifier.fillMaxWidth().focusRestorer(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        ) {
                            itemsIndexed(
                                items = visibleEpisodes,
                                key = { index, ep -> "ep_${season ?: 0}_${index}_${ep.id}" },
                            ) { index, episode ->
                                val epNumber = if (episode.number > 0) episode.number else (index + 1)
                                val focusKey = "ep_${season ?: 0}_${index}_${episode.id}"
                                EpisodeNumberedSlideCard(
                                    number = epNumber,
                                    episode = episode,
                                    coverFallback = details.cover,
                                    saved = progress[episode.id],
                                    cardWidth = cardWidth,
                                    cardHeight = cardHeight,
                                    targetWidthPx = cardWidthPx,
                                    targetHeightPx = cardHeightPx,
                                    screenKey = screenKey,
                                    focusKey = focusKey,
                                    onClick = {
                                        focusNav.markNavigateFrom(screenKey, focusKey)
                                        val globalIndex = details.episodes.indexOfFirst { it.id == episode.id }
                                        if (globalIndex >= 0) {
                                            onPlay(PlayRequest(queue, globalIndex))
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // 4. قسم القصة وطاقم التمثيل وتوليد القصة بالذكاء الاصطناعي
            item(key = "series_story_and_cast") {
                val themeCustomization = LocalThemeCustomization.current
                if (themeCustomization.isVisible("details_metadata")) {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        padding = 16.dp,
                    ) {
                        AiStoryPlotSection(
                            plot = details.plot,
                            emptyLabel = "لا توجد قصة متاحة لهذا المسلسل.",
                            aiState = aiState,
                            onGenerateStory = onGenerateStory,
                            plotFontSizeSp = 13,
                            plotLineHeightSp = 21,
                        )
                        if (details.castMembers.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            CastMembersRow(members = details.castMembers)
                        } else if (details.cast.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = Ar.cast,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Glow,
                                    modifier = Modifier.width(84.dp),
                                )
                                Text(
                                    text = details.cast,
                                    fontSize = 13.sp,
                                    color = Palette.Text,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeriesHeroBackdrop(
    backdropUrl: String,
    fallbackCover: String,
    trailerUrl: String,
    title: String,
    scrollAlpha: Float,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bg = Palette.CinematicBg
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val clampedAlpha = scrollAlpha.coerceIn(0f, 1f)

    Box(Modifier.fillMaxSize()) {
        if (clampedAlpha > 0.02f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(if (compact) 1f else 0.68f)
                    .fillMaxHeight(if (compact) 0.42f else 0.72f)
                    .graphicsLayer { alpha = clampedAlpha },
            ) {
                val isTv = com.saydlawy.sahab.ui.LocalIsTv.current
                val lightMode = com.saydlawy.sahab.ui.LocalLightEffects.current
                val primaryImage = backdropUrl.ifBlank { fallbackCover }
                val isPortraitOnly = backdropUrl.isBlank() || backdropUrl == fallbackCover
                if (!isTv && !lightMode && trailerUrl.isNotBlank()) {
                    TrailerAutoPlayBox(
                        trailerUrl = trailerUrl,
                        posterUrl = primaryImage,
                        title = title,
                        autoPlayDelayMs = 250L,
                        startMuted = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    RemoteImage(
                        url = primaryImage,
                        fallbackText = title,
                        contentScale = ContentScale.Crop,
                        alignment = if (isPortraitOnly) Alignment.TopCenter else Alignment.Center,
                        targetWidthPx = 1280,
                        targetHeightPx = 720,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                val sideStops = if (rtl) {
                    arrayOf(0.0f to bg.copy(alpha = 0.28f), 0.14f to Color.Transparent, 0.70f to bg.copy(alpha = 0.68f), 1.0f to bg)
                } else {
                    arrayOf(0.0f to bg, 0.30f to bg.copy(alpha = 0.68f), 0.86f to Color.Transparent, 1.0f to bg.copy(alpha = 0.28f))
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (compact) Brush.horizontalGradient(listOf(bg.copy(alpha = 0.25f), bg.copy(alpha = 0.25f)))
                            else Brush.horizontalGradient(colorStops = sideStops),
                        ),
                )
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bg.copy(alpha = 0.45f),
                            0.24f to Color.Transparent,
                            if (compact) 0.42f to bg else 0.65f to bg.copy(alpha = 0.94f),
                            1.0f to bg,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun SeriesHeroInfoBlock(
    screenKey: String,
    details: SeriesDetails,
    resumeIndex: Int,
    resumePositionMs: Long,
    queue: List<PlayItem>,
    isFavorite: Boolean,
    refreshingMetadata: Boolean,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val cleanRating = RatingSanitizer.format(details.rating)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NetflixRed)
                    .padding(horizontal = 6.dp, vertical = 1.5.dp),
            ) {
                Text("N", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Text(
                text = "مسلسل",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            qualityOf(details.name)?.let { q ->
                Tag(q, if (q == "4K") Palette.Gold else Palette.Glow, fontSize = 10.sp)
            }
        }

        Text(
            text = details.name,
            color = Color.White,
            fontSize = if (compact) 21.sp else 25.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val metaParts = buildList {
            if (details.year.isNotBlank()) add(details.year)
            if (details.seasons.isNotEmpty()) add("${details.seasons.size} مواسم")
            val primaryGenre = details.genre.substringBefore(",").substringBefore("،").trim()
            if (primaryGenre.isNotBlank()) add(primaryGenre)
        }
        if (cleanRating.isNotBlank() || metaParts.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cleanRating.isNotBlank()) {
                    Text(
                        text = "تقييم $cleanRating",
                        color = NetflixMatchGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("  •  "),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        if (details.plot.isNotBlank()) {
            Text(
                text = details.plot,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (queue.isNotEmpty()) {
                val resumeItem = queue.getOrNull(resumeIndex)
                DetailPillButton(
                    text = if (resumeItem != null) Ar.resumeEpisode else Ar.playFirst,
                    icon = Ic.Play,
                    primary = true,
                    screenKey = screenKey,
                    focusKey = "series_play_btn",
                    isDefaultEntry = true,
                    onClick = {
                        onPlay(
                            PlayRequest(
                                items = queue,
                                startIndex = resumeIndex.coerceAtLeast(0),
                                fromStart = false,
                                initialPositionMs = if (resumeItem != null) resumePositionMs else -1L,
                            ),
                        )
                    },
                )
            }
            DetailPillButton(
                text = if (isFavorite) "في قائمتي" else "+ قائمتي",
                icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                primary = false,
                screenKey = screenKey,
                focusKey = "series_fav_btn",
                onClick = onToggleFavorite,
            )
            DetailCircleIconButton(
                icon = Ic.Refresh,
                description = Ar.refreshData,
                active = refreshingMetadata,
                screenKey = screenKey,
                focusKey = "series_refresh_btn",
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun SeasonPillTab(
    label: String,
    selected: Boolean,
    screenKey: String,
    focusKey: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    val bgColor = when {
        selected -> NetflixRed
        focused -> Color.White.copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val borderColor = when {
        focused -> Color.White
        selected -> NetflixRed
        else -> Color.White.copy(alpha = 0.22f)
    }

    Box(
        modifier = Modifier
            .height(34.dp)
            .restorableFocus(screenKey, focusKey)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(bgColor)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.5.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeNumberedSlideCard(
    number: Int,
    episode: Episode,
    coverFallback: String,
    saved: RecentItem?,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    targetWidthPx: Int,
    targetHeightPx: Int,
    screenKey: String,
    focusKey: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.04f else 1f,
        animationSpec = tween(130),
        label = "epSlideScale",
    )
    val shape = RoundedCornerShape(10.dp)
    val borderModifier = if (focused) Modifier.border(2.5.dp, Color.White, shape) else Modifier

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // رقم كبير بجانب الحلقة 1، 2، 3... بنفس أسلوب Top 10
        Box(Modifier.padding(bottom = 4.dp)) {
            Text(
                text = "$number",
                color = if (focused) NetflixRed else Color.White.copy(alpha = 0.80f),
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
            )
        }

        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .restorableFocus(screenKey, focusKey)
                .onFocusChanged { focused = it.isFocused }
                .clip(shape)
                .background(Palette.CardSurface)
                .then(borderModifier)
                .combinedClickable(onClick = onClick),
        ) {
            RemoteImage(
                url = episode.image.ifBlank { coverFallback },
                fallbackText = episode.title,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                targetWidthPx = targetWidthPx,
                targetHeightPx = targetHeightPx,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f), Color.Black.copy(alpha = 0.90f)),
                        ),
                    ),
            )

            // شارة المدة في الأعلى
            if (episode.duration.isNotBlank()) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 5.dp, vertical = 1.5.dp),
                ) {
                    Text(
                        text = episode.duration,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // عنوان الحلقة وشريط التقدم في الأسفل
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = episode.title.ifBlank { Ar.episodeNumber(number) },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (saved != null && saved.durationMs > 0L) {
                    ProgressLine(
                        fraction = (saved.positionMs.toFloat() / saved.durationMs).coerceIn(0f, 1f),
                        height = 3.dp,
                    )
                }
            }
        }
    }
}

@Composable
internal fun DetailPillButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    screenKey: String,
    focusKey: String,
    isDefaultEntry: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.05f else 1f,
        animationSpec = tween(130),
        label = "detailPillScale",
    )
    val shape = RoundedCornerShape(8.dp)
    val bgColor = when {
        primary && focused -> Color(0xFFFF1F2C)
        primary -> NetflixRed
        focused -> Color.White.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val borderColor = when {
        focused -> Color.White
        primary -> Color.Transparent
        else -> Color.White.copy(alpha = 0.28f)
    }

    Row(
        modifier = Modifier
            .height(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .restorableFocus(screenKey, focusKey, isDefaultEntry = isDefaultEntry)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(bgColor)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SahabIcon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun DetailCircleIconButton(
    icon: ImageVector,
    description: String,
    active: Boolean,
    screenKey: String,
    focusKey: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.06f else 1f,
        animationSpec = tween(130),
        label = "detailCircleScale",
    )

    Box(
        modifier = Modifier
            .size(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .restorableFocus(screenKey, focusKey)
            .onFocusChanged { focused = it.isFocused }
            .clip(CircleShape)
            .background(if (focused) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.14f))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color.White.copy(alpha = 0.28f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SahabIcon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) Palette.Gold else Color.White,
            modifier = Modifier.size(17.dp),
        )
    }
}
