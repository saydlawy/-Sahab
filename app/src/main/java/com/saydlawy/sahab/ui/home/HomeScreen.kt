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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
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
import com.saydlawy.sahab.data.DEFAULT_VOD_EXT
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.HomeChannelsSource
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import androidx.work.WorkManager
import com.saydlawy.sahab.data.sports.SportsDatabase
import com.saydlawy.sahab.data.sports.SyncScheduler
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
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.home.components.TrendingMoviesSection
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.LocalThemePreferences
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.ChannelLogoResolver
import com.saydlawy.sahab.util.formatDayOffsetYmd
import com.saydlawy.sahab.util.utcDateToLocalYmd
import com.saydlawy.sahab.util.formatUtcClockToLocal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

@Composable
fun HomeScreen(
    onOpen: (String) -> Unit,
    onPlay: (PlayRequest) -> Unit,
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusNav = LocalFocusNavigation.current
    val themeCustomization = LocalThemeCustomization.current
    val activeThemeId = LocalThemePreferences.current.selectedThemeId

    val recents by container.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val favorites by container.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val showRecents by container.prefs.showRecents.collectAsStateWithLifecycle(initialValue = true)
    val showLiveChannels by container.prefs.showLiveChannelsOnHome.collectAsStateWithLifecycle(initialValue = true)
    val showRecommended by container.prefs.showRecommendedOnHome.collectAsStateWithLifecycle(initialValue = true)
    val showQuickCategories by container.prefs.showQuickCategoriesOnHome.collectAsStateWithLifecycle(initialValue = true)
    val showMatches by container.prefs.showMatchesOnHome.collectAsStateWithLifecycle(initialValue = true)

    val homeChannelsLayout by container.prefs.homeChannelsLayout.collectAsStateWithLifecycle(
        initialValue = HomeChannelsLayout.CAROUSEL,
    )
    val homeChannelsSource by container.prefs.homeChannelsSource.collectAsStateWithLifecycle(
        initialValue = HomeChannelsSource.AUTO,
    )
    val homeChannelsCategoryId by container.prefs.homeChannelsCategoryId.collectAsStateWithLifecycle(
        initialValue = "",
    )
    val homeCustomChannelIds by container.prefs.homeCustomChannelIds.collectAsStateWithLifecycle(
        initialValue = emptySet(),
    )

    var featuredChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var trendingMovies by remember {
        mutableStateOf(container.firebaseCatalog.peekCachedRecommendedMovies())
    }
    var todayMatches by remember { mutableStateOf<List<Match>>(emptyList()) }

    // 1. Load Featured Live Channels according to user's Settings customization
    LaunchedEffect(
        container.repo.account,
        homeChannelsSource,
        homeChannelsCategoryId,
        homeCustomChannelIds,
        favorites,
    ) {
        featuredChannels = loadFeaturedChannels(
            repo = container.repo,
            source = homeChannelsSource,
            categoryId = homeChannelsCategoryId,
            customIds = homeCustomChannelIds,
            favorites = favorites,
        )
    }

    // 2. Two-stage ultra-fast loading of Newest Additions from Xtream + Firebase
    LaunchedEffect(container.repo.account) {
        val instant = runCatching {
            container.firebaseCatalog.getInstantRecommendedMovies(
                repo = container.repo,
                dao = container.catalogDb.movieDao(),
                limit = 18,
            )
        }.getOrNull().orEmpty()
        if (instant.isNotEmpty()) {
            trendingMovies = instant
        }

        val enriched = runCatching {
            container.firebaseCatalog.getXtreamRecommendedMovies(
                repo = container.repo,
                dao = container.catalogDb.movieDao(),
                limit = 18,
            )
        }.getOrNull().orEmpty()
        if (enriched.isNotEmpty()) {
            trendingMovies = enriched
        }
    }

    // 3. Load Today's Matches from SportsDatabase (auto-sync from Firestore if empty)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = SportsDatabase.getInstance(context).matchDao()
                var all = dao.getAllMatches().map { it.toMatch() }
                if (all.isEmpty()) {
                    val req = SyncScheduler.enqueueManualSyncNow(context)
                    withTimeoutOrNull(45_000L) {
                        WorkManager.getInstance(context)
                            .getWorkInfoByIdFlow(req.id)
                            .firstOrNull { it != null && it.state.isFinished }
                    }
                    all = dao.getAllMatches().map { it.toMatch() }
                }
                val todayStr = formatDayOffsetYmd(0)
                val tomorrowStr = formatDayOffsetYmd(1)
                val yesterdayStr = formatDayOffsetYmd(-1)

                val todayList = all.filter { utcDateToLocalYmd(it.date, it.time) == todayStr || it.status == MatchStatus.LIVE }
                val combined = if (todayList.size >= 5) {
                    todayList
                } else {
                    val nearby = all.filter {
                        val localDate = utcDateToLocalYmd(it.date, it.time)
                        localDate == tomorrowStr || localDate == yesterdayStr
                    }
                    (todayList + nearby + all).distinctBy { it.id }.take(12)
                }
                todayMatches = combined.take(12)
            }
        }
    }

    val continueWatchingItems = remember(recents, trendingMovies) {
        if (recents.isNotEmpty()) {
            recents.take(10).mapIndexed { idx, item ->
                val rawProgress = if (item.durationMs > 0) {
                    (item.positionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0.08f, 0.96f)
                } else {
                    0f
                }
                val itemFocusKey = "home_recent_${item.kind}_${item.id}_$idx"
                ContinueCardModel(
                    id = itemFocusKey,
                    title = item.title,
                    subtitle = item.subtitle.ifBlank { Ar.kindLabel(item.kind) },
                    imageUrl = item.icon,
                    progress = rawProgress,
                    onClick = {
                        focusNav.markNavigateFrom(Routes.HOME, itemFocusKey)
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
                                            ext = item.ext.ifBlank { DEFAULT_VOD_EXT },
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
            emptyList()
        }
    }

    val currentDisplayMode = LocalDisplayMode.current
    val isTvOrTablet = currentDisplayMode == DisplayMode.TABLET_OR_TV
    val mainFeedState = rememberRestorableLazyListState("home_main_vertical_feed")

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
            // A. Top Navigation Bar (RTL)
            SahabTopBar(current = Routes.HOME)

            // Main scrollable vertical feed with preserved scroll & focus state
            LazyColumn(
                state = mainFeedState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTvOrTablet) 14.dp else 12.dp),
            ) {
                val sectionDefaultOrder = listOf(
                    "section_continue_watching", "section_live_channels", "section_trending_movies",
                    "section_quick_categories", "section_today_matches",
                )
                val orderedHomeSections = when (themeCustomization.componentPlacement["home_sections"]) {
                    "live_first" -> listOf("section_live_channels", "section_continue_watching", "section_trending_movies", "section_quick_categories", "section_today_matches")
                    "movies_first" -> listOf("section_trending_movies", "section_continue_watching", "section_live_channels", "section_quick_categories", "section_today_matches")
                    "matches_first" -> listOf("section_today_matches", "section_continue_watching", "section_live_channels", "section_trending_movies", "section_quick_categories")
                    else -> sectionDefaultOrder
                }
                val visibleHomeSections = orderedHomeSections.filter { section ->
                    when (section) {
                        "section_continue_watching" -> showRecents && themeCustomization.isVisible("home_continue") && continueWatchingItems.isNotEmpty()
                        "section_live_channels" -> showLiveChannels && themeCustomization.isVisible("home_live_channels")
                        "section_trending_movies" -> showRecommended && themeCustomization.isVisible("home_recommendations")
                        "section_quick_categories" -> showQuickCategories && themeCustomization.isVisible("home_categories")
                        "section_today_matches" -> showMatches && themeCustomization.isVisible("home_matches")
                        else -> false
                    }
                }
                if (activeThemeId == "dynamic_bento" && themeCustomization.isVisible("home_highlights")) {
                    item(key = "bento_home_highlights") {
                        BentoHomeHighlights(
                            vertical = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT,
                            channelCount = featuredChannels.size,
                            movieCount = trendingMovies.size,
                            matchCount = todayMatches.size,
                            placement = themeCustomization.componentPlacement["home_highlights"] ?: "start",
                            onLive = { onOpen(Routes.LIVE) },
                            onMovies = { onOpen(Routes.MOVIES) },
                            onSports = { onOpen(Routes.SPORTS) },
                        )
                    }
                }
                if (activeThemeId == "oled_cinema" && themeCustomization.isVisible("home_spotlight") && currentDisplayMode != DisplayMode.PHONE_LANDSCAPE) {
                    trendingMovies.firstOrNull()?.let { spotlight ->
                        item(key = "oled_home_spotlight") {
                            OledHomeSpotlight(
                                title = spotlight.titleAr,
                                subtitle = spotlight.year,
                                imageUrl = spotlight.posterUrl,
                                compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT,
                                onClick = { onOpen(Routes.movie(spotlight.novaStreamId.ifEmpty { spotlight.sahabId })) },
                            )
                        }
                    }
                }

                items(visibleHomeSections, key = { it }) { section ->
                    when (section) {
                        "section_continue_watching" -> ContinueWatchingSection(
                            items = continueWatchingItems,
                            hasRealRecents = recents.isNotEmpty(),
                            onClearRecents = { scope.launch { container.prefs.restoreRecents(emptyList()) } },
                        )
                        "section_live_channels" -> FeaturedChannelsSection(
                            layout = homeChannelsLayout,
                            channels = featuredChannels,
                            onPlay = onPlay,
                            onSeeAll = {
                                focusNav.markNavigateFrom(Routes.HOME, "home_live_see_all")
                                onOpen(Routes.LIVE)
                            },
                        )
                        "section_trending_movies" -> TrendingMoviesSection(
                            movies = trendingMovies,
                            onMovieClick = { movie -> onOpen(Routes.movie(movie.novaStreamId.ifEmpty { movie.sahabId })) },
                        )
                        "section_quick_categories" -> QuickCategoriesSection(
                            onOpenLive = { focusNav.markNavigateFrom(Routes.HOME, "home_quick_live"); onOpen(Routes.LIVE) },
                            onOpenMovies = { focusNav.markNavigateFrom(Routes.HOME, "home_quick_movies"); onOpen(Routes.MOVIES) },
                            onOpenSeries = { focusNav.markNavigateFrom(Routes.HOME, "home_quick_series"); onOpen(Routes.SERIES) },
                        )
                        "section_today_matches" -> TodayMatchesSection(
                            matches = todayMatches,
                            onOpenSports = { matchKey -> focusNav.markNavigateFrom(Routes.HOME, matchKey); onOpen(Routes.SPORTS) },
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

internal data class ContinueCardModel(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val progress: Float,
    val onClick: () -> Unit,
)

@Composable
private fun BentoHomeHighlights(
    vertical: Boolean,
    channelCount: Int,
    movieCount: Int,
    matchCount: Int,
    placement: String,
    onLive: () -> Unit,
    onMovies: () -> Unit,
    onSports: () -> Unit,
) {
    val live = Triple("البث المباشر", "$channelCount قناة", onLive)
    val movies = Triple("أفلام مقترحة", "$movieCount عملاً", onMovies)
    val sports = Triple("مباريات اليوم", "$matchCount مباراة", onSports)
    val highlights = when (placement) {
        "center" -> listOf(movies, live, sports)
        "end" -> listOf(sports, movies, live)
        else -> listOf(live, movies, sports)
    }
    if (vertical) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            highlights.forEach { (title, subtitle, action) -> BentoHighlightCard(title, subtitle, action, Modifier.fillMaxWidth()) }
        }
    } else {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            highlights.forEach { (title, subtitle, action) ->
                BentoHighlightCard(title, subtitle, action, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BentoHighlightCard(title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(onClick = onClick, modifier = modifier.height(104.dp)) { focused ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, color = if (focused) Palette.FocusCyan else Palette.Text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(subtitle, color = Palette.TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun OledHomeSpotlight(
    title: String,
    subtitle: String,
    imageUrl: String,
    compact: Boolean,
    onClick: () -> Unit,
) {
    GlassCard(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(if (compact) 168.dp else 250.dp)) { focused ->
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))),
                ),
            )
            Column(
                Modifier.align(Alignment.BottomStart).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text("مقترح لك", color = Palette.FocusCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(title, color = Color.White, fontSize = if (compact) 19.sp else 25.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
                if (focused) Text("تشغيل", color = Palette.FocusCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

