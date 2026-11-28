package com.saydlawy.sahab.feature.instantwatch.presentation

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.feature.instantwatch.data.InstantDiscoverFeed
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType
import com.saydlawy.sahab.feature.instantwatch.data.InstantGenreTile
import com.saydlawy.sahab.feature.instantwatch.data.TmdbCatalog
import com.saydlawy.sahab.feature.instantwatch.domain.GateResult
import com.saydlawy.sahab.feature.instantwatch.domain.InputResolutionResult
import com.saydlawy.sahab.feature.instantwatch.domain.OrchestrationResult
import com.saydlawy.sahab.feature.instantwatch.domain.PlaybackSessionRequest
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedPlaybackSource
import com.saydlawy.sahab.feature.instantwatch.player.ActivePlaybackSession
import com.saydlawy.sahab.feature.torrent.domain.TorrentResolutionResult
import com.saydlawy.sahab.feature.torrent.domain.TorrentStreamCandidate
import com.saydlawy.sahab.feature.torrent.presentation.TorrentSourcesSheet
import com.saydlawy.sahab.feature.instantwatch.local.InstantCustomWatchlist
import com.saydlawy.sahab.feature.instantwatch.player.PlayerSessionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private sealed class InstantWatchRoute {
    data object Home : InstantWatchRoute()
    data class SearchResults(val query: String, val items: List<InstantMediaItem>, val people: List<Pair<Int, String>> = emptyList()) : InstantWatchRoute()
    data class Details(val item: InstantMediaItem) : InstantWatchRoute()
    data class Browse(val target: InstantBrowseTarget) : InstantWatchRoute()
}

@Composable
fun InstantWatchScreen(
    sessionManager: PlayerSessionManager,
    onRequireLogin: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isTv = (config.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION ||
        config.screenWidthDp >= 840

    val settings by sessionManager.preferences.settings.collectAsState()
    val customWatchlists by sessionManager.preferences.customWatchlists.collectAsState()
    var showCreateWatchlistDialog by remember { mutableStateOf(false) }
    val torrentSettings by sessionManager.torrentBridge.settings.collectAsState()
    var torrentTarget by remember { mutableStateOf<Triple<InstantMediaItem, Int?, Int?>?>(null) }
    var torrentStreams by remember { mutableStateOf<List<TorrentStreamCandidate>>(emptyList()) }
    val torrentCache = remember { mutableStateMapOf<String, List<TorrentStreamCandidate>>() }
    var isTorrentLoading by remember { mutableStateOf(false) }
    var resolvingTorrentId by remember { mutableStateOf<String?>(null) }
    var torrentError by remember { mutableStateOf<String?>(null) }
    val isSignedIn by sessionManager.authGate.observeAuthSignedIn()
        .collectAsState(initial = sessionManager.authGate.hasCachedUser())
    val activeSession by sessionManager.activeSession.collectAsState()
    val recentHistory by sessionManager.orchestrator.observeRecentHistory().collectAsState(initial = emptyList())

    val backStack = remember { mutableStateListOf<InstantWatchRoute>(InstantWatchRoute.Home) }
    val homeListState = rememberLazyListState()
    val route = backStack.lastOrNull() ?: InstantWatchRoute.Home
    fun navigateTo(next: InstantWatchRoute) {
        if (backStack.lastOrNull() != next) backStack.add(next)
    }
    fun navigateBack() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }
    fun resetToHome() {
        backStack.clear()
        backStack.add(InstantWatchRoute.Home)
    }
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    var discoverFeed by remember { mutableStateOf(InstantDiscoverFeed()) }
    var isFeedLoading by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(InstantQuickFilter.ALL) }
    var genreMovies by remember { mutableStateOf<List<InstantMediaItem>>(emptyList()) }
    var genreTv by remember { mutableStateOf<List<InstantMediaItem>>(emptyList()) }
    var isGenreLoading by remember { mutableStateOf(false) }
    var focusedItem by remember { mutableStateOf<InstantMediaItem?>(null) }

    BackHandler(enabled = activeSession == null && backStack.size > 1) {
        navigateBack()
    }

    LaunchedEffect(isSignedIn, settings.enabled) {
        if (!isSignedIn || !settings.enabled) {
            sessionManager.onServiceDisabledOrSignedOut()
            resetToHome()
        } else {
            isFeedLoading = true
            discoverFeed = sessionManager.tmdbRepository.loadDiscoverFeed()
            isFeedLoading = false
        }
    }

    LaunchedEffect(selectedFilter) {
        val mGenre = selectedFilter.movieGenreId
        val tGenre = selectedFilter.tvGenreId
        if (mGenre != null || tGenre != null) {
            isGenreLoading = true
            val mDef = mGenre?.let { async { sessionManager.tmdbRepository.discoverByGenre(it, InstantMediaType.MOVIE) } }
            val tDef = tGenre?.let { async { sessionManager.tmdbRepository.discoverByGenre(it, InstantMediaType.TV) } }
            genreMovies = mDef?.await().orEmpty()
            genreTv = tDef?.await().orEmpty()
            isGenreLoading = false
        } else {
            genreMovies = emptyList()
            genreTv = emptyList()
        }
    }

    if (!settings.enabled) {
        GateMessageView(
            title = "المشاهدة الفورية معطلة",
            body = "تم تعطيل خدمة المشاهدة الفورية من الإعدادات. يمكنك إعادة تفعيلها في أي وقت.",
            primaryLabel = "رجوع",
            onPrimary = onBack,
            modifier = modifier,
        )
        return
    }

    if (!isSignedIn) {
        GateMessageView(
            title = "تسجيل الدخول مطلوب",
            body = "خدمة المشاهدة الفورية متاحة فقط بعد تسجيل الدخول بحسابك في سحاب.",
            primaryLabel = "تسجيل الدخول",
            onPrimary = onRequireLogin,
            secondaryLabel = "رجوع",
            onSecondary = onBack,
            modifier = modifier,
        )
        return
    }

    fun startPlayback(item: InstantMediaItem, season: Int? = null, episode: Int? = null) {
        scope.launch {
            isLoading = true
            statusMessage = null
            when (
                val res = sessionManager.orchestrator.preparePlayback(
                    PlaybackSessionRequest(item = item, seasonNumber = season, episodeNumber = episode),
                )
            ) {
                is OrchestrationResult.Ready -> {
                    sessionManager.startSession(
                        ActivePlaybackSession(
                            item = res.item,
                            seasonNumber = res.seasonNumber,
                            episodeNumber = res.episodeNumber,
                            source = res.source,
                            resumePositionMs = res.resumePositionMs,
                        ),
                    )
                }
                is OrchestrationResult.Denied -> {
                    statusMessage = when (val g = res.gate) {
                        GateResult.ServiceDisabled -> "الخدمة معطلة من الإعدادات."
                        GateResult.Unauthenticated -> {
                            onRequireLogin()
                            "يرجى تسجيل الدخول أولاً."
                        }
                        is GateResult.SessionInvalid -> g.reason
                        is GateResult.Authorized -> null
                    }
                }
                is OrchestrationResult.Error -> statusMessage = res.message
            }
            isLoading = false
        }
    }

    fun handleMediaSelected(item: InstantMediaItem) {
        navigateTo(InstantWatchRoute.Details(item))
    }

    fun performSearch(overrideQuery: String? = null) {
        val q = (overrideQuery ?: inputText).trim()
        scope.launch {
            isLoading = true
            statusMessage = null
            val peopleDef = async { sessionManager.tmdbRepository.searchPeople(q) }
            val res = sessionManager.inputResolver.resolve(q)
            val people = peopleDef.await()
            when (res) {
                is InputResolutionResult.SingleMatch -> {
                    if (people.isNotEmpty()) {
                        navigateTo(InstantWatchRoute.SearchResults(q, listOf(res.item), people))
                    } else {
                        handleMediaSelected(res.item)
                    }
                }
                is InputResolutionResult.MultipleMatches -> navigateTo(InstantWatchRoute.SearchResults(q, res.items, people))
                InputResolutionResult.NoResults -> {
                    if (people.isNotEmpty()) {
                        navigateTo(InstantWatchRoute.SearchResults(q, emptyList(), people))
                    } else {
                        statusMessage = "لم يتم العثور على نتائج مطابقة."
                    }
                }
                is InputResolutionResult.InvalidInput -> statusMessage = res.message
                is InputResolutionResult.Denied -> {
                    statusMessage = when (val g = res.gate) {
                        GateResult.Unauthenticated -> {
                            onRequireLogin()
                            "يرجى تسجيل الدخول أولاً."
                        }
                        GateResult.ServiceDisabled -> "الخدمة معطلة من الإعدادات."
                        is GateResult.SessionInvalid -> g.reason
                        is GateResult.Authorized -> null
                    }
                }
            }
            isLoading = false
        }
    }

    val currentSession = activeSession
    if (currentSession != null) {
        InstantPlayerScreen(
            session = currentSession,
            sessionManager = sessionManager,
            onProgressUpdate = { pos, dur ->
                scope.launch {
                    sessionManager.orchestrator.saveProgress(
                        item = currentSession.item,
                        seasonNumber = currentSession.seasonNumber,
                        episodeNumber = currentSession.episodeNumber,
                        positionMs = pos,
                        durationMs = dur,
                    )
                }
            },
            onClose = { sessionManager.endSession() },
            modifier = modifier,
        )
        return
    }

    when (val r = route) {
        is InstantWatchRoute.SearchResults -> {
            SearchResultsScreen(
                query = r.query,
                results = r.items,
                people = r.people,
                onSelect = { handleMediaSelected(it) },
                onSelectPerson = { pid, pname ->
                    navigateTo(InstantWatchRoute.Browse(InstantBrowseTarget.Person(pid, pname)))
                },
                onBack = { navigateBack() },
                modifier = modifier,
            )
            return
        }
        is InstantWatchRoute.Details -> {
            fun loadTorrentSources(
                item: InstantMediaItem,
                season: Int?,
                episode: Int?,
                forceRefresh: Boolean = false,
            ) {
                val cacheKey = "${item.contentKey}:${season ?: 0}:${episode ?: 0}"
                torrentTarget = Triple(item, season, episode)
                torrentError = null
                resolvingTorrentId = null
                if (!forceRefresh) {
                    val cached = torrentCache[cacheKey]
                    if (!cached.isNullOrEmpty()) {
                        torrentStreams = cached
                        isTorrentLoading = false
                        return
                    }
                }
                torrentStreams = emptyList()
                if (!sessionManager.torrentBridge.isEnabledAndReady()) {
                    isTorrentLoading = false
                    torrentError = "يرجى إضافة رابط Stremio Manifest واحد على الأقل من إعدادات المشاهدة الفورية."
                    return
                }
                isTorrentLoading = true
                scope.launch {
                    val imdbId = item.imdbId?.takeIf { it.isNotBlank() }
                        ?: sessionManager.imdbResolver.fetchImdbId(item.tmdbId, item.mediaType)
                    val searchTitle = item.originalTitle.ifBlank { item.title }
                    val list = sessionManager.torrentBridge.fetchStreams(
                        imdbId = imdbId.orEmpty(),
                        isSeries = item.mediaType == InstantMediaType.TV,
                        season = season,
                        episode = episode,
                        tmdbId = item.tmdbId,
                        searchTitle = searchTitle,
                    )
                    if (list.isNotEmpty()) {
                        torrentCache[cacheKey] = list
                    }
                    torrentStreams = list
                    isTorrentLoading = false
                    if (list.isEmpty()) {
                        torrentError = "لم ترجع الإضافات أي مصادر لهذا العمل."
                    }
                }
            }

            Box(modifier = modifier.fillMaxSize()) {
                InstantMediaDetailScreen(
                    initialItem = r.item,
                    tmdbRepository = sessionManager.tmdbRepository,
                    onPlayMovie = { startPlayback(it) },
                    onPlayEpisode = { series, season, ep -> startPlayback(series, season, ep) },
                    onSelectSimilar = { navigateTo(InstantWatchRoute.Details(it))},
                    onSelectPerson = { pid, pname ->
                        navigateTo(InstantWatchRoute.Browse(InstantBrowseTarget.Person(pid, pname)))
                    },
                    onSelectGenre = { gid, gname ->
                        val matched = TmdbCatalog.featuredGenres.firstOrNull { it.movieGenreId == gid || it.tvGenreId == gid }
                            ?: InstantGenreTile("g_$gid", gname, gid, gid, 0xFF2563EB)
                        navigateTo(InstantWatchRoute.Browse(InstantBrowseTarget.Genre(matched)))
                    },
                    onBrowseTorrentSources = if (torrentSettings.enabled) {
                        { item, season, ep -> loadTorrentSources(item, season, ep) }
                    } else null,
                    onBack = { navigateBack() },
                    modifier = Modifier.fillMaxSize(),
                )

                val activeTarget = torrentTarget
                if (activeTarget != null) {
                    val (tItem, tSeason, tEp) = activeTarget
                    val sheetTitle = if (tSeason != null && tEp != null) {
                        "${tItem.title} • م$tSeason ح$tEp"
                    } else {
                        tItem.title
                    }
                    TorrentSourcesSheet(
                        title = sheetTitle,
                        streams = torrentStreams,
                        isLoading = isTorrentLoading,
                        resolvingId = resolvingTorrentId,
                        errorMessage = torrentError,
                        onSelect = { candidate ->
                            resolvingTorrentId = candidate.id
                            torrentError = null
                            scope.launch {
                                when (val res = sessionManager.torrentBridge.resolveForPlayback(candidate)) {
                                    is TorrentResolutionResult.Ready -> {
                                        resolvingTorrentId = null
                                        sessionManager.startSession(
                                            ActivePlaybackSession(
                                                item = tItem,
                                                seasonNumber = tSeason,
                                                episodeNumber = tEp,
                                                source = ResolvedPlaybackSource.NativeMedia3(streamUrl = res.streamUrl),
                                                resumePositionMs = 0L,
                                                headers = res.headers,
                                                torrServerHashToCleanup = res.torrServerHashToCleanup,
                                            ),
                                        )
                                    }
                                    is TorrentResolutionResult.Error -> {
                                        resolvingTorrentId = null
                                        torrentError = res.message
                                    }
                                }
                            }
                        },
                        onRetry = { loadTorrentSources(tItem, tSeason, tEp, forceRefresh = true) },
                        onDismiss = {
                            if (resolvingTorrentId == null) {
                                torrentTarget = null
                                torrentError = null
                            }
                        },
                    )
                }
            }
            return
        }
        is InstantWatchRoute.Browse -> {
            InstantBrowseScreen(
                target = r.target,
                tmdbRepository = sessionManager.tmdbRepository,
                onSelectItem = { navigateTo(InstantWatchRoute.Details(it))},
                onBack = { navigateBack() },
                modifier = modifier,
            )
            return
        }
        InstantWatchRoute.Home -> Unit
    }

    val horizontalPad = if (isTv) 28.dp else if (isLandscape) 18.dp else 14.dp
    val cardWidth = if (isTv) 156.dp else if (isLandscape) 128.dp else 134.dp
    val cardImageHeight = if (isTv) 216.dp else if (isLandscape) 168.dp else 186.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120)),
    ) {
        InstantWatchTopBar(
            inputText = inputText,
            onInputChange = { inputText = it; statusMessage = null },
            onSearch = { performSearch() },
            onPasteAndSearch = {
                val pasted = clipboard.getText()?.text?.trim().orEmpty()
                if (pasted.isNotEmpty()) {
                    inputText = pasted
                    performSearch(pasted)
                }
            },
            onRefresh = {
                scope.launch {
                    isFeedLoading = true
                    discoverFeed = sessionManager.tmdbRepository.loadDiscoverFeed(forceRefresh = true)
                    isFeedLoading = false
                }
            },
            onBack = onBack,
            isLoading = isLoading,
            statusMessage = statusMessage,
            selectedFilter = selectedFilter,
            onFilterSelected = { selectedFilter = it },
            focusedItem = focusedItem,
            isLandscape = isLandscape,
            isTv = isTv,
            horizontalPad = horizontalPad,
        )

        LazyColumn(
            state = homeListState,
            modifier = Modifier.fillMaxSize().weight(1f),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (isFeedLoading || isGenreLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF38BDF8))
                    }
                }
            }

            if (selectedFilter == InstantQuickFilter.ALL && recentHistory.isNotEmpty()) {
                item {
                    SectionHeader(title = "آخر المشاهدات", horizontalPad = horizontalPad)
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = horizontalPad),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(recentHistory, key = { it.contentKey }) { entry ->
                            RecentWatchCard(
                                entry = entry,
                                onClick = {
                                    val type = if (entry.mediaType == "tv") InstantMediaType.TV else InstantMediaType.MOVIE
                            val item = InstantMediaItem(
                                tmdbId = entry.tmdbId,
                                mediaType = type,
                                title = entry.title,
                                originalTitle = entry.originalTitle ?: entry.title,
                                releaseYear = entry.releaseYear,
                                posterUrl = entry.posterUrl,
                                imdbId = entry.imdbId,
                            )
                            if (type == InstantMediaType.TV && (entry.seasonNumber == null || entry.episodeNumber == null)) {
                                handleMediaSelected(item)
                            } else {
                                startPlayback(item, entry.seasonNumber, entry.episodeNumber)
                            }
                                },
                            )
                        }
                    }
                }
            }

            if (selectedFilter.movieGenreId != null || selectedFilter.tvGenreId != null) {
                if (genreMovies.isNotEmpty()) {
                    item {
                        MediaCarouselSection(
                            title = "أفلام ${selectedFilter.label}",
                            items = genreMovies,
                            horizontalPad = horizontalPad,
                            cardWidth = cardWidth,
                            cardImageHeight = cardImageHeight,
                            onSelect = ::handleMediaSelected,
                            onFocusItem = { focusedItem = it },
                        )
                    }
                }
                if (genreTv.isNotEmpty()) {
                    item {
                        MediaCarouselSection(
                            title = "مسلسلات ${selectedFilter.label}",
                            items = genreTv,
                            horizontalPad = horizontalPad,
                            cardWidth = cardWidth,
                            cardImageHeight = cardImageHeight,
                            onSelect = ::handleMediaSelected,
                            onFocusItem = { focusedItem = it },
                        )
                    }
                }
            } else {
                val showMovies = selectedFilter == InstantQuickFilter.ALL || selectedFilter == InstantQuickFilter.MOVIES
                val showTv = selectedFilter == InstantQuickFilter.ALL || selectedFilter == InstantQuickFilter.TV
                val showTrending = selectedFilter == InstantQuickFilter.ALL
                val showTopRated = selectedFilter == InstantQuickFilter.ALL ||
                    selectedFilter == InstantQuickFilter.MOVIES ||
                    selectedFilter == InstantQuickFilter.TOP_RATED

                if (showMovies && discoverFeed.latestMovies.isNotEmpty()) {
                    item {
                        BackdropMediaCarouselSection(
                            title = "أحدث الأفلام",
                            subtitle = "أحدث الإصدارات المعروضة حالياً",
                            items = discoverFeed.latestMovies,
                            horizontalPad = horizontalPad,
                            isTv = isTv,
                            onSelect = ::handleMediaSelected,
                            onFocusItem = { focusedItem = it },
                        )
                    }
                }
                if (showTrending && discoverFeed.trendingAll.isNotEmpty()) {
                    item {
                        TopTenDetailedCarouselSection(
                            title = "أفضل 10 أعمال هذا الأسبوع",
                            subtitle = "الأكثر بحثاً ومشاهدة عالمياً",
                            items = discoverFeed.trendingAll,
                            horizontalPad = horizontalPad,
                            isTv = isTv,
                            onSelect = ::handleMediaSelected,
                            onFocusItem = { focusedItem = it },
                        )
                    }
                }
                if (selectedFilter == InstantQuickFilter.ALL) {
                    item {
                        CustomWatchlistsCarouselSection(
                            watchlists = customWatchlists,
                            horizontalPad = horizontalPad,
                            onCreateClick = { showCreateWatchlistDialog = true },
                            onOpenWatchlist = { wl ->
                                if (wl.kind == InstantCustomWatchlist.KIND_ACTOR && wl.personId != null) {
                                    navigateTo(InstantWatchRoute.Browse(InstantBrowseTarget.Person(wl.personId, wl.name)))
                                } else {
                                    navigateTo(InstantWatchRoute.Browse(InstantBrowseTarget.CustomList(wl)))
                                }
                            },
                            onDeleteWatchlist = { id -> sessionManager.preferences.deleteCustomWatchlist(id) },
                        )
                    }
                }
                if (selectedFilter == InstantQuickFilter.ALL || selectedFilter == InstantQuickFilter.MOVIES) {
                    item {
                        CollectionsCarouselSection(
                            collections = TmdbCatalog.featuredCollections,
                            horizontalPad = horizontalPad,
                            onSelect = { col -> navigateTo(InstantWatchRoute.Browse(InstantBrowseTarget.Collection(col)))},
                        )
                    }
                }
                if (selectedFilter == InstantQuickFilter.ALL) {
                    item {
                        RegionsCarouselSection(
                            regions = TmdbCatalog.featuredRegions,
                            horizontalPad = horizontalPad,
                            onSelect = { reg -> navigateTo(InstantWatchRoute.Browse(InstantBrowseTarget.Region(reg)))},
                        )
                    }
                    item {
                        GenresCarouselSection(
                            genres = TmdbCatalog.featuredGenres,
                            horizontalPad = horizontalPad,
                            onSelect = { gen -> navigateTo(InstantWatchRoute.Browse(InstantBrowseTarget.Genre(gen)))},
                        )
                    }
                }
                if (showTv && discoverFeed.latestTv.isNotEmpty()) {
                    item {
                        MediaCarouselSection("أحدث المسلسلات", discoverFeed.latestTv, horizontalPad, cardWidth, cardImageHeight, ::handleMediaSelected, { focusedItem = it }, subtitle = "حلقات ومواسم تُعرض هذا الأسبوع")
                    }
                }
                if (showTopRated && discoverFeed.topRatedMovies.isNotEmpty()) {
                    item {
                        MediaCarouselSection("روائع السينما", discoverFeed.topRatedMovies, horizontalPad, cardWidth, cardImageHeight, ::handleMediaSelected, { focusedItem = it }, subtitle = "الأعلى تقييماً في تاريخ TMDB")
                    }
                }
            }
        }
    }

    if (showCreateWatchlistDialog) {
        CreateWatchlistDialog(
            tmdbRepository = sessionManager.tmdbRepository,
            onDismiss = { showCreateWatchlistDialog = false },
            onCreated = { wl ->
                sessionManager.preferences.saveCustomWatchlist(wl)
                showCreateWatchlistDialog = false
            },
        )
    }
}

@Composable
private fun GateMessageView(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = body,
                color = Color(0xFFCBD5E1),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onPrimary,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                ) {
                    Text(primaryLabel, color = Color.White)
                }
                if (secondaryLabel != null && onSecondary != null) {
                    TextButton(onClick = onSecondary) {
                        Text(secondaryLabel, color = Color(0xFF94A3B8))
                    }
                }
            }
        }
    }
}
