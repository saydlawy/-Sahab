package com.saydlawy.sahab.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.MovieDetails
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.ai.AiGenerationState
import com.saydlawy.sahab.data.forAccount
import com.saydlawy.sahab.data.isFav
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.TrailerAutoPlayBox
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.RatingSanitizer
import com.saydlawy.sahab.util.formatDuration

private val NetflixRed = Color(0xFFE50914)
private val NetflixMatchGreen = Color(0xFF46D369)

@Composable
fun MovieDetailScreen(id: String, onBack: () -> Unit, onPlay: (PlayRequest) -> Unit) {
    val vm = appViewModel(key = "movie_$id") { MovieDetailViewModel(it, id) }
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
    val feedState = rememberRestorableLazyListState("movie_detail_feed_$id")
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
                SahabTopBar(current = Routes.MOVIES, onBack = onBack)
            }

            when {
                state.loading -> LoadingBox()
                details == null -> MessageBox(state.error ?: Ar.errUnknown, actionLabel = Ar.retry, onAction = vm::load)
                else -> {
                    val isFavorite = favorites.isFav(activeAccountId, Kind.MOVIE, details.id)
                    val resume = recents.firstOrNull { it.kind == Kind.MOVIE && it.id == details.id }
                    val resumeMs = resume?.positionMs ?: 0L
                    val durationMs = resume?.durationMs ?: 0L

                    val showTmdbPicker = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    MovieNetflixContent(
                        details = details,
                        resumeMs = resumeMs,
                        durationMs = durationMs,
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
                                focusNav.restoreAfterDialogDismiss("movie/${details.id}")
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieNetflixContent(
    details: MovieDetails,
    resumeMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    aiState: AiGenerationState,
    refreshingMetadata: Boolean,
    feedState: LazyListState,
    onGenerateStory: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val screenKey = remember(details.id) { "movie/${details.id}" }
    val displayMode = LocalDisplayMode.current
    val isPortrait = displayMode == DisplayMode.PHONE_PORTRAIT
    val hPad = when (displayMode) {
        DisplayMode.TABLET_OR_TV -> 28.dp
        DisplayMode.PHONE_LANDSCAPE -> 18.dp
        DisplayMode.PHONE_PORTRAIT -> 14.dp
    }

    val posterList = remember(details.id, details.posterUrls, details.image) {
        (if (details.posterUrls.isNotEmpty()) details.posterUrls else listOf(details.image))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    var selectedPosterIndex by remember(details.id, posterList) { mutableIntStateOf(0) }
    val activePoster = posterList.getOrElse(selectedPosterIndex) { details.image }
    val item = remember(details, activePoster) {
        PlayItem(Kind.MOVIE, details.id, details.name, icon = activePoster, ext = details.ext)
    }

    val backdropAlpha by remember(feedState) {
        derivedStateOf {
            if (feedState.firstVisibleItemIndex > 0) 0f
            else (1f - (feedState.firstVisibleItemScrollOffset / 280f)).coerceIn(0f, 1f)
        }
    }

    val showingSelectedGalleryImage = selectedPosterIndex > 0
    val heroMedia = if (showingSelectedGalleryImage) activePoster else details.backdrop.ifBlank { activePoster }

    Box(Modifier.fillMaxSize()) {
        MovieHeroBackdrop(
            backdropUrl = heroMedia,
            fallbackPoster = activePoster,
            trailerUrl = if (showingSelectedGalleryImage) "" else details.trailerUrl,
            title = details.name,
            scrollAlpha = backdropAlpha,
        )

        LazyColumn(
            state = feedState,
            modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(start = hPad, end = hPad, top = 8.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. بانر الفيلم والأزرار في أول الصفحة
            item(key = "movie_hero_block") {
                MovieHeroInfoBlock(
                    screenKey = screenKey,
                    details = details,
                    resumeMs = resumeMs,
                    durationMs = durationMs,
                    isFavorite = isFavorite,
                    refreshingMetadata = refreshingMetadata,
                    item = item,
                    onToggleFavorite = onToggleFavorite,
                    onRefresh = onRefresh,
                    onPlay = onPlay,
                    modifier = Modifier
                        .fillMaxWidth(if (isPortrait) 1f else 0.58f)
                        .padding(top = if (isPortrait) 110.dp else 8.dp),
                )
            }

            // 2. شريط البوسترات البديلة (إن وُجدت)
            if (posterList.size > 1) {
                item(key = "movie_alt_posters") {
                    val density = LocalDensity.current
                    val thumbWidth = 56.dp
                    val thumbWpx = remember(density) { with(density) { thumbWidth.roundToPx() } }
                    val thumbHpx = remember(thumbWpx) { (thumbWpx * 3) / 2 }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DotTitle("بوسترات الفيلم", NetflixRed, fontSize = 15.sp)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp),
                        ) {
                            itemsIndexed(posterList.take(10), key = { idx, url -> "${url}_$idx" }) { idx, url ->
                                val selected = idx == selectedPosterIndex
                                val thumbShape = RoundedCornerShape(10.dp)
                                val interaction = remember { MutableInteractionSource() }
                                val focused by interaction.collectIsFocusedAsState()
                                Box(
                                    modifier = Modifier
                                        .width(thumbWidth)
                                        .aspectRatio(2f / 3f)
                                        .clip(thumbShape)
                                        .border(
                                            width = if (focused) 2.5.dp else if (selected) 2.dp else 1.dp,
                                            color = when {
                                                focused -> Palette.FocusCyan
                                                selected -> Color.White
                                                else -> Palette.GlassBorder
                                            },
                                            shape = thumbShape,
                                        )
                                        .clickable(interactionSource = interaction, indication = null) { selectedPosterIndex = idx },
                                ) {
                                    RemoteImage(
                                        url = url,
                                        fallbackText = details.name,
                                        alignment = Alignment.TopCenter,
                                        targetWidthPx = thumbWpx,
                                        targetHeightPx = thumbHpx,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. بطاقة القصة الكاملة والمخرج وطاقم التمثيل وتوليد القصة بالذكاء الاصطناعي
            item(key = "movie_story_and_cast") {
                val themeCustomization = LocalThemeCustomization.current
                if (themeCustomization.isVisible("details_metadata")) {
                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        padding = 16.dp,
                    ) {
                        AiStoryPlotSection(
                            plot = details.plot,
                            emptyLabel = "لا توجد قصة متاحة لهذا الفيلم.",
                            aiState = aiState,
                            onGenerateStory = onGenerateStory,
                            plotFontSizeSp = 13,
                            plotLineHeightSp = 21,
                        )
                        if (details.director.isNotBlank() || details.castMembers.isNotEmpty() || details.cast.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (details.director.isNotBlank()) {
                                    MovieDetailRow(Ar.director, details.director)
                                }
                                if (details.castMembers.isNotEmpty()) {
                                    CastMembersRow(members = details.castMembers)
                                } else if (details.cast.isNotBlank()) {
                                    MovieDetailRow(Ar.cast, details.cast)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieHeroBackdrop(
    backdropUrl: String,
    fallbackPoster: String,
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
                val primaryImage = backdropUrl.ifBlank { fallbackPoster }
                val isPortraitOnly = backdropUrl.isBlank() || backdropUrl == fallbackPoster
                if (trailerUrl.isNotBlank() || !isPortraitOnly) {
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
                        alignment = Alignment.TopCenter,
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
private fun MovieHeroInfoBlock(
    screenKey: String,
    details: MovieDetails,
    resumeMs: Long,
    durationMs: Long,
    isFavorite: Boolean,
    refreshingMetadata: Boolean,
    item: PlayItem,
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
                text = "فيلم سينمائي",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            qualityOf(details.name)?.let { q ->
                Tag(q, if (q == "4K") Palette.Gold else Palette.Glow, fontSize = 10.sp)
            }
            if (details.ext.isNotBlank()) {
                Tag(details.ext.uppercase(), Palette.TextDim, fontSize = 10.sp)
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
            val primaryGenre = details.genre.substringBefore(",").substringBefore("،").trim()
            if (primaryGenre.isNotBlank()) add(primaryGenre)
            if (details.duration.isNotBlank()) add(details.duration)
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

        if (resumeMs > 10_000 && durationMs > 0) {
            ProgressLine(
                fraction = (resumeMs.toFloat() / durationMs).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth(0.7f),
                height = 4.dp,
            )
        }

        Spacer(Modifier.height(2.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val canResume = resumeMs > 10_000
            DetailPillButton(
                text = if (canResume) Ar.resumeFrom(formatDuration(resumeMs)) else Ar.play,
                icon = Ic.Play,
                primary = true,
                screenKey = screenKey,
                focusKey = "movie_play_btn",
                isDefaultEntry = true,
                onClick = { onPlay(PlayRequest(listOf(item), 0, initialPositionMs = resumeMs)) },
            )
            if (canResume) {
                DetailPillButton(
                    text = Ar.fromStart,
                    icon = Ic.Refresh,
                    primary = false,
                    screenKey = screenKey,
                    focusKey = "movie_from_start_btn",
                    onClick = { onPlay(PlayRequest(listOf(item), 0, fromStart = true)) },
                )
            }
            DetailPillButton(
                text = if (isFavorite) "في قائمتي" else "+ قائمتي",
                icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                primary = false,
                screenKey = screenKey,
                focusKey = "movie_fav_btn",
                onClick = onToggleFavorite,
            )
            DetailCircleIconButton(
                icon = Ic.Refresh,
                description = Ar.refreshData,
                active = refreshingMetadata,
                screenKey = screenKey,
                focusKey = "movie_refresh_btn",
                onClick = onRefresh,
            )
        }
    }
}

@Composable
private fun MovieDetailRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.Glow,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Palette.Text,
            modifier = Modifier.weight(1f),
        )
    }
}
