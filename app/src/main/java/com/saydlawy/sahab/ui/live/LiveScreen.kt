package com.saydlawy.sahab.ui.live

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalAppScale
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.SideNavItem
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.components.rememberNow
import com.saydlawy.sahab.ui.player.SahabPlayer
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatClock
import com.saydlawy.sahab.util.formatNowMillis
import com.saydlawy.sahab.util.normalizeForSearch
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.runtime.Immutable

private val IdleChannelRowFill: Brush = SolidColor(Palette.Glass)
private val PreviewShadeBrush: Brush = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.65f)),
)
private val PreviewPlaceholderBrush: Brush = Brush.radialGradient(
    listOf(Palette.Royal.copy(alpha = 0.32f), Palette.DeepBlue, Color.Black),
)

private const val FAV_CAT_ID = "__FAVORITES__"
private const val ALL_CAT_ID = "__ALL__"

@Immutable
data class LiveUiState(
    val categories: List<Category> = emptyList(),
    val selectedId: String? = ALL_CAT_ID,
    val channels: List<Channel> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val totalCount: Int = 0,
    val query: String = "",
    val searchResults: List<Channel>? = null,
    val loadingCategories: Boolean = true,
    val loadingChannels: Boolean = false,
    val searching: Boolean = false,
    val error: String? = null,
) {
    val visibleChannels: List<Channel> get() = searchResults ?: channels
    val busy: Boolean get() = loadingChannels || searching
}

class LiveViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repo
    private val _state = MutableStateFlow(LiveUiState())
    val state = _state.asStateFlow()

    private var channelsJob: Job? = null
    private var searchJob: Job? = null
    private var searchPool: List<Channel>? = null
    private var indexedPool: List<Pair<Channel, String>>? = null

    private suspend fun updateSearchPool(channels: List<Channel>) {
        searchPool = channels
        indexedPool = withContext(Dispatchers.Default) {
            channels.map { it to it.name.normalizeForSearch() }
        }
    }

    init {
        loadCategories()
    }

    private suspend fun computeCounts(allChannels: List<Channel>): Map<String, Int> =
        withContext(Dispatchers.Default) {
            val counts = HashMap<String, Int>()
            allChannels.forEach { ch ->
                val cid = ch.categoryId
                if (cid.isNotBlank()) {
                    counts[cid] = (counts[cid] ?: 0) + 1
                }
            }
            counts
        }

    fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCategories = true, error = null) }
            try {
                val categories = repo.liveCategories()
                val cachedAll = repo.peekAllLiveChannels()
                if (!cachedAll.isNullOrEmpty()) {
                    updateSearchPool(cachedAll)
                    val counts = computeCounts(cachedAll)
                    _state.update {
                        it.copy(
                            categories = categories,
                            selectedId = ALL_CAT_ID,
                            channels = cachedAll,
                            loadingCategories = false,
                            loadingChannels = false,
                            totalCount = cachedAll.size,
                            categoryCounts = counts,
                        )
                    }
                    return@launch
                }

                // Cold start: display categories immediately and load the first category right away
                val firstCatId = categories.firstOrNull()?.id ?: ALL_CAT_ID
                _state.update {
                    it.copy(
                        categories = categories,
                        selectedId = firstCatId,
                        loadingCategories = false,
                    )
                }
                select(firstCatId)

                // Fetch full channel pool & category counts in background without blocking UI
                viewModelScope.launch {
                    val allChannels = runCatching { repo.liveChannels(null) }.getOrDefault(emptyList())
                    if (allChannels.isNotEmpty()) {
                        updateSearchPool(allChannels)
                        val counts = computeCounts(allChannels)
                        _state.update { current ->
                            current.copy(
                                totalCount = allChannels.size,
                                categoryCounts = counts + current.categoryCounts,
                                channels = if (current.selectedId == ALL_CAT_ID || current.channels.isEmpty()) allChannels else current.channels,
                                loadingChannels = if (current.selectedId == ALL_CAT_ID) false else current.loadingChannels,
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingCategories = false, error = e.userMessage()) }
            }
        }
    }

    fun select(categoryId: String) {
        channelsJob?.cancel()
        if (categoryId == FAV_CAT_ID) {
            val pool = searchPool
            _state.update {
                it.copy(
                    selectedId = FAV_CAT_ID,
                    channels = pool ?: it.channels,
                    loadingChannels = false,
                    error = null,
                )
            }
            return
        }
        if (categoryId == ALL_CAT_ID) {
            val cached = searchPool
            if (!cached.isNullOrEmpty()) {
                _state.update {
                    it.copy(
                        selectedId = ALL_CAT_ID,
                        channels = cached,
                        loadingChannels = false,
                        error = null,
                    )
                }
                return
            }
        }
        _state.update { it.copy(selectedId = categoryId, loadingChannels = true, error = null) }
        channelsJob = viewModelScope.launch {
            try {
                val targetId = if (categoryId == ALL_CAT_ID) null else categoryId
                val channels = repo.liveChannels(targetId)
                if (categoryId == ALL_CAT_ID) updateSearchPool(channels)
                _state.update {
                    it.copy(
                        channels = channels,
                        loadingChannels = false,
                        categoryCounts = if (categoryId != ALL_CAT_ID && categoryId != FAV_CAT_ID) {
                            it.categoryCounts + (categoryId to channels.size)
                        } else it.categoryCounts,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(loadingChannels = false, channels = emptyList(), error = e.userMessage())
                }
            }
        }
    }

    fun onQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        val needle = query.normalizeForSearch().trim()
        if (needle.length < 2) {
            _state.update { it.copy(searchResults = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.update { it.copy(searching = true) }
            try {
                val indexed = indexedPool ?: run {
                    val pool = searchPool ?: repo.liveChannels(null).also { updateSearchPool(it) }
                    indexedPool ?: withContext(Dispatchers.Default) {
                        pool.map { it to it.name.normalizeForSearch() }
                    }
                }
                val results = withContext(Dispatchers.Default) {
                    indexed.asSequence()
                        .filter { it.second.contains(needle) }
                        .map { it.first }
                        .take(300)
                        .toList()
                }
                _state.update { it.copy(searchResults = results, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, searchResults = emptyList(), error = e.userMessage()) }
            }
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            container.prefs.toggleFavorite(FavItem(Kind.LIVE, channel.id, channel.name, channel.icon))
        }
    }
}

@Composable
fun LiveScreen(onBack: () -> Unit, onPlay: (PlayRequest) -> Unit) {
    val vm = appViewModel { LiveViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by LocalContainer.current.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteIds = remember(favorites) { favorites.filter { it.kind == Kind.LIVE }.map { it.id }.toSet() }
    var guideFor by remember { mutableStateOf<Channel?>(null) }
    var previewChannel by remember { mutableStateOf<Channel?>(null) }

    val rawChannels = state.visibleChannels
    val channels = remember(rawChannels, state.selectedId, favoriteIds, state.searchResults) {
        if (state.selectedId == FAV_CAT_ID && state.searchResults == null) {
            rawChannels.filter { it.id in favoriteIds }
        } else {
            rawChannels
        }
    }

    val playScope = rememberCoroutineScope()
    var openingPlayer by remember { mutableStateOf(false) }

    val channelIdSet = remember(channels) {
        HashSet<String>(channels.size).apply { channels.forEach { add(it.id) } }
    }

    val playAt: (Int) -> Unit = { index ->
        if (!openingPlayer && channels.isNotEmpty() && index in channels.indices) {
            openingPlayer = true
            val snapshot = channels
            playScope.launch {
                try {
                    val items = if (snapshot.size > 300) {
                        withContext(Dispatchers.Default) {
                            snapshot.map { PlayItem(Kind.LIVE, it.id, it.name, icon = it.icon) }
                        }
                    } else {
                        snapshot.map { PlayItem(Kind.LIVE, it.id, it.name, icon = it.icon) }
                    }
                    kotlinx.coroutines.delay(300)
                    onPlay(PlayRequest(items, index))
                } finally {
                    openingPlayer = false
                }
            }
        }
    }
    val playChannel: (Channel) -> Unit = { ch ->
        val idx = channels.indexOfFirst { it.id == ch.id }
        if (idx >= 0) playAt(idx)
        else onPlay(PlayRequest(listOf(PlayItem(Kind.LIVE, ch.id, ch.name, icon = ch.icon)), 0))
    }

    val activePreview = previewChannel?.takeIf { it.id in channelIdSet }

    val handleChannelActivate: (Int) -> Unit = { idx ->
        val ch = channels.getOrNull(idx)
        if (ch != null && activePreview?.id != ch.id) {
            previewChannel = ch
        } else {
            playAt(idx)
        }
    }

    Box(Modifier.fillMaxSize()) {
        val displayMode = LocalDisplayMode.current
        when (displayMode) {
            DisplayMode.TABLET_OR_TV -> {
                WideLiveLayout(
                    state = state,
                    channels = channels,
                    favoriteIds = favoriteIds,
                    previewChannel = activePreview,
                    onPreview = { previewChannel = it },
                    onPlayAt = handleChannelActivate,
                    onPlayChannel = playChannel,
                    onToggleFavorite = vm::toggleFavorite,
                    onGuide = { guideFor = it },
                    onSelect = vm::select,
                    onQuery = vm::onQuery,
                    onBack = onBack,
                    onRetry = { state.selectedId?.let(vm::select) },
                )
            }
            DisplayMode.PHONE_LANDSCAPE -> {
                PhoneLandscapeLiveLayout(
                    state = state,
                    channels = channels,
                    favoriteIds = favoriteIds,
                    previewChannel = activePreview,
                    onPreview = { previewChannel = it },
                    onPlayAt = handleChannelActivate,
                    onPlayChannel = playChannel,
                    onToggleFavorite = vm::toggleFavorite,
                    onGuide = { guideFor = it },
                    onSelect = vm::select,
                    onQuery = vm::onQuery,
                    onBack = onBack,
                    onRetry = { state.selectedId?.let(vm::select) },
                )
            }
            DisplayMode.PHONE_PORTRAIT -> {
                PhonePortraitLiveLayout(
                    state = state,
                    channels = channels,
                    favoriteIds = favoriteIds,
                    previewChannel = activePreview,
                    onPreview = { previewChannel = it },
                    onPlayAt = handleChannelActivate,
                    onPlayChannel = playChannel,
                    onToggleFavorite = vm::toggleFavorite,
                    onGuide = { guideFor = it },
                    onSelect = vm::select,
                    onQuery = vm::onQuery,
                    onBack = onBack,
                    onRetry = { state.selectedId?.let(vm::select) },
                )
            }
        }
    }

    guideFor?.let { channel ->
        EpgDialog(channelName = channel.name, channelId = channel.id, onDismiss = { guideFor = null })
    }
}

@Composable
private fun WideLiveLayout(
    state: LiveUiState,
    channels: List<Channel>,
    favoriteIds: Set<String>,
    previewChannel: Channel?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onSelect: (String) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize()) {
            SahabTopBar(current = Routes.LIVE, onBack = onBack)

            Row(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = appDp(24), vertical = appDp(10)),
                horizontalArrangement = Arrangement.spacedBy(appDp(22)),
            ) {
                // Column 1 (Right in RTL): Search + Categories sidebar
                Column(
                    Modifier
                        .weight(0.24f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(appDp(10)),
                ) {
                    SearchBox(
                        state.query,
                        onQuery,
                        Ar.searchChannels,
                        Modifier.fillMaxWidth(),
                    )
                    CategorySidebar(
                        categories = state.categories,
                        selectedId = state.selectedId,
                        onSelect = onSelect,
                        totalCount = state.totalCount,
                        favCount = favoriteIds.size,
                        categoryCounts = state.categoryCounts,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                // Column 2 (Center): Live Channel List — wider so full channel names fit
                ChannelColumn(
                    state = state,
                    channels = channels,
                    favoriteIds = favoriteIds,
                    previewId = previewChannel?.id,
                    onPreview = onPreview,
                    onPlayAt = onPlayAt,
                    onToggleFavorite = onToggleFavorite,
                    onGuide = onGuide,
                    onRetry = onRetry,
                    modifier = Modifier
                        .weight(0.39f)
                        .fillMaxHeight(),
                )

                // Column 3 (Left in RTL): Video Preview & EPG Schedule
                PreviewPanel(
                    channel = previewChannel,
                    isFavorite = previewChannel?.id in favoriteIds,
                    onPlay = { previewChannel?.let(onPlayChannel) },
                    onToggleFavorite = { previewChannel?.let(onToggleFavorite) },
                    onGuide = onGuide,
                    showEpg = true,
                    modifier = Modifier
                        .weight(0.37f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun PhoneLandscapeLiveLayout(
    state: LiveUiState,
    channels: List<Channel>,
    favoriteIds: Set<String>,
    previewChannel: Channel?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onSelect: (String) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(Modifier.fillMaxSize()) {
            SahabTopBar(current = Routes.LIVE, onBack = onBack)

            LazyRow(
                modifier = Modifier.padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = FAV_CAT_ID) {
                    Chip(Ar.favorites, state.selectedId == FAV_CAT_ID, { onSelect(FAV_CAT_ID) })
                }
                item(key = ALL_CAT_ID) {
                    Chip(Ar.all, state.selectedId == ALL_CAT_ID, { onSelect(ALL_CAT_ID) })
                }
                items(state.categories, key = { it.id }) { cat ->
                    Chip(cat.name, cat.id == state.selectedId, { onSelect(cat.id) })
                }
            }

            Row(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Right pane in RTL: Search + Channel list
                Column(
                    Modifier
                        .weight(0.54f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SearchBox(
                        state.query,
                        onQuery,
                        Ar.searchChannels,
                        Modifier.fillMaxWidth(),
                    )
                    ChannelColumn(
                        state = state,
                        channels = channels,
                        favoriteIds = favoriteIds,
                        previewId = previewChannel?.id,
                        onPreview = onPreview,
                        onPlayAt = onPlayAt,
                        onToggleFavorite = onToggleFavorite,
                        onGuide = onGuide,
                        onRetry = onRetry,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                // Left pane in RTL: Compact Live Video Preview without EPG card overflow
                PreviewPanel(
                    channel = previewChannel,
                    isFavorite = previewChannel?.id in favoriteIds,
                    onPlay = { previewChannel?.let(onPlayChannel) },
                    onToggleFavorite = { previewChannel?.let(onToggleFavorite) },
                    onGuide = onGuide,
                    showEpg = false,
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight(),
                )
            }
        }
    }
}

@Composable
private fun PhonePortraitLiveLayout(
    state: LiveUiState,
    channels: List<Channel>,
    favoriteIds: Set<String>,
    previewChannel: Channel?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onSelect: (String) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SahabTopBar(current = Routes.LIVE, onBack = onBack)

        if (previewChannel != null) {
            PreviewPanel(
                channel = previewChannel,
                isFavorite = previewChannel.id in favoriteIds,
                onPlay = { onPlayChannel(previewChannel) },
                onToggleFavorite = { onToggleFavorite(previewChannel) },
                onGuide = onGuide,
                showEpg = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        SearchBox(
            state.query,
            onQuery,
            Ar.searchChannels,
            Modifier.padding(horizontal = appDp(12), vertical = appDp(4)),
        )
        LazyRow(
            modifier = Modifier.padding(vertical = appDp(4)),
            contentPadding = PaddingValues(horizontal = appDp(12)),
            horizontalArrangement = Arrangement.spacedBy(appDp(8)),
        ) {
            item(key = FAV_CAT_ID) {
                Chip(Ar.favorites, state.selectedId == FAV_CAT_ID, { onSelect(FAV_CAT_ID) })
            }
            item(key = ALL_CAT_ID) {
                Chip(Ar.all, state.selectedId == ALL_CAT_ID, { onSelect(ALL_CAT_ID) })
            }
            items(state.categories, key = { it.id }) { cat ->
                Chip(cat.name, cat.id == state.selectedId, { onSelect(cat.id) })
            }
        }
        ChannelColumn(
            state = state,
            channels = channels,
            favoriteIds = favoriteIds,
            previewId = previewChannel?.id,
            onPreview = onPreview,
            onPlayAt = onPlayAt,
            onToggleFavorite = onToggleFavorite,
            onGuide = onGuide,
            onRetry = onRetry,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = appDp(12)),
        )
    }
}

@Composable
private fun CategorySidebar(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    totalCount: Int,
    favCount: Int,
    categoryCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(appDp(8)),
        contentPadding = PaddingValues(vertical = appDp(4)),
    ) {
        item(key = FAV_CAT_ID) {
            SideNavItem(
                label = Ar.favorites,
                selected = selectedId == FAV_CAT_ID,
                onClick = { onSelect(FAV_CAT_ID) },
                count = favCount.takeIf { it > 0 },
                icon = Ic.HeartOutline,
                iconTint = Palette.Glow,
            )
        }
        item(key = ALL_CAT_ID) {
            SideNavItem(
                label = Ar.all,
                selected = selectedId == ALL_CAT_ID || selectedId == null,
                onClick = { onSelect(ALL_CAT_ID) },
                count = totalCount.takeIf { it > 0 },
            )
        }
        items(categories, key = { it.id }) { cat ->
            val count = categoryCounts[cat.id]
            val isSport = cat.name.contains("رياض") || cat.name.uppercase().contains("SPORT") || cat.name.uppercase().contains("BEIN")
            SideNavItem(
                label = cat.name,
                selected = cat.id == selectedId,
                onClick = { onSelect(cat.id) },
                count = if (count != null && count > 0) count else null,
                trailingText = if (isSport && (count == null || count == 0)) "مباشر ⚽" else null,
            )
        }
    }
}

@Composable
private fun ChannelColumn(
    state: LiveUiState,
    channels: List<Channel>,
    favoriteIds: Set<String>,
    previewId: String?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = appDp(6), vertical = appDp(4)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                Ar.channelList,
                fontSize = appSp(15),
                fontWeight = FontWeight.Bold,
                color = Palette.TextDim,
            )
            if (channels.isNotEmpty()) {
                Tag(Ar.itemsCount(channels.size), Palette.Glow, fontSize = appSp(10))
            }
        }

        Spacer(Modifier.height(appDp(4)))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy && channels.isEmpty() -> LoadingBox()
                state.error != null && channels.isEmpty() -> MessageBox(
                    state.error,
                    actionLabel = Ar.retry,
                    onAction = onRetry,
                )
                channels.isEmpty() -> MessageBox(
                    if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noChannels,
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(vertical = appDp(4)),
                    verticalArrangement = Arrangement.spacedBy(appDp(8)),
                ) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                        ChannelRow(
                            channel = channel,
                            isPreviewed = channel.id == previewId,
                            onActivate = { onPlayAt(index) },
                            onToggleFavorite = { onToggleFavorite(channel) },
                        )
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelRow(
    channel: Channel,
    isPreviewed: Boolean,
    onActivate: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val repo = LocalContainer.current.repo
    val cachedPrograms = remember(channel.id) {
        if (channel.epgId.isBlank()) null else repo.peekShortEpg(channel.id, 2)
    }
    val initialProgram = remember(cachedPrograms) {
        val t = System.currentTimeMillis() / 1000
        cachedPrograms?.firstOrNull { t in it.start until it.end }
    }
    val now by produceState<Program?>(initialValue = initialProgram, channel.id) {
        if (channel.epgId.isBlank()) {
            value = null
            return@produceState
        }
        val memHit = repo.peekShortEpg(channel.id, 2)
        if (memHit != null) {
            val t = System.currentTimeMillis() / 1000
            value = memHit.firstOrNull { t in it.start until it.end }
            return@produceState
        }
        delay(300) // Wait for scroll to settle before hitting disk/network EPG
        value = try {
            val t = System.currentTimeMillis() / 1000
            repo.shortEpg(channel.id, 2).firstOrNull { t in it.start until it.end }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }
    var focused by remember { mutableStateOf(false) }
    val active = focused || isPreviewed
    val shape = RoundedCornerShape(appDp(14))
    val borderColor = when {
        focused -> Palette.Glow
        isPreviewed -> Palette.Glow.copy(alpha = 0.7f)
        else -> Palette.GlassBorder
    }
    val bg: Brush = if (active) Palette.ActiveFill else IdleChannelRowFill
    val borderWidth = if (active) appDp(2) else appDp(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (active) {
                    Modifier.shadow(
                        elevation = appDp(12),
                        shape = shape,
                        ambientColor = Palette.Glow,
                        spotColor = Palette.Glow,
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(bg)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                onClick = onActivate,
                onLongClick = onToggleFavorite,
            )
            .padding(horizontal = appDp(10), vertical = appDp(9)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appDp(8)),
    ) {
        RemoteImage(
            url = channel.icon,
            fallbackText = channel.name,
            modifier = Modifier
                .size(appDp(26))
                .clip(RoundedCornerShape(appDp(7)))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(appDp(2)),
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appDp(2))) {
            Text(
                channel.name,
                fontSize = appSp(14),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val program = now
            if (program != null) {
                Text(
                    program.title.ifBlank { Ar.noName },
                    fontSize = appSp(11),
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isPreviewed) {
            LiveBadge()
        }

        qualityOf(channel.name)?.let { q ->
            Tag(q, if (q == "4K") Palette.Gold else Palette.Glow, fontSize = appSp(10))
        }
    }
}

@Composable
private fun LiveBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Live.copy(alpha = 0.22f))
            .border(appDp(1), Palette.Live.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = appDp(8), vertical = appDp(3)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appDp(4)),
    ) {
        Box(
            Modifier
                .size(appDp(6))
                .clip(CircleShape)
                .background(Palette.Live.copy(alpha = dotAlpha)),
        )
        Text(Ar.liveBadge, fontSize = appSp(10), fontWeight = FontWeight.Bold, color = Palette.Live)
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPanel(
    channel: Channel?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGuide: (Channel) -> Unit,
    showEpg: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val container = LocalContainer.current
    val repo = container.repo
    val context = LocalContext.current

    var previewExo by remember { mutableStateOf<ExoPlayer?>(null) }
    var isScreenActive by remember { mutableStateOf(true) }

    var isPlaying by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(false) }
    var videoReady by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(true) }
    var formatAttempt by remember { mutableIntStateOf(0) }
    var tracks by remember { mutableStateOf(Tracks.EMPTY) }

    DisposableEffect(Unit) {
        onDispose {
            previewExo?.release()
            previewExo = null
        }
    }

    DisposableEffect(previewExo) {
        val exo = previewExo
        if (exo == null) {
            isPlaying = false
            buffering = false
            videoReady = false
            tracks = Tracks.EMPTY
            return@DisposableEffect onDispose { }
        }
        exo.volume = if (muted) 0f else 1f
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
            }

            override fun onRenderedFirstFrame() {
                videoReady = true
            }

            override fun onTracksChanged(newTracks: Tracks) {
                tracks = newTracks
            }

            override fun onPlayerError(error: PlaybackException) {
                if (formatAttempt + 1 < repo.liveExtensions().size) {
                    formatAttempt += 1
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        isScreenActive = true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        isScreenActive = false
        previewExo?.release()
        previewExo = null
    }

    LaunchedEffect(channel?.id) {
        formatAttempt = 0
        videoReady = false
        tracks = Tracks.EMPTY
    }

    LaunchedEffect(channel?.id, formatAttempt, isScreenActive) {
        val ch = channel
        if (ch == null || !isScreenActive) {
            previewExo?.stop()
            previewExo?.clearMediaItems()
            if (ch == null) {
                previewExo?.release()
                previewExo = null
            }
            videoReady = false
            return@LaunchedEffect
        }
        val url = runCatching {
            repo.streamUrl(PlayItem(Kind.LIVE, ch.id, ch.name, icon = ch.icon), formatAttempt)
        }.getOrNull() ?: return@LaunchedEffect

        val exo = previewExo ?: SahabPlayer.create(
            context = context,
            client = container.client,
            isLive = true,
            isPreview = true,
            accountHash = container.accountHash,
        ).also { previewExo = it }

        exo.volume = if (muted) 0f else 1f
        exo.setMediaItem(MediaItem.fromUri(url))
        exo.prepare()
        exo.playWhenReady = true
    }

    val openFullScreen: () -> Unit = {
        // Fully release preview player before opening full-screen player (prevents dual MediaCodec/RAM usage)
        previewExo?.release()
        previewExo = null
        onPlay()
    }

    val programs by produceState<List<Program>>(emptyList(), channel?.id) {
        value = if (channel != null && channel.epgId.isNotBlank()) {
            try { repo.shortEpg(channel.id, 6) } catch (_: Exception) { emptyList() }
        } else emptyList()
    }
    val today = formatNowMillis(rememberNow(), "EEEE، d MMMM", Locale.forLanguageTag("ar"))

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(appDp(14)),
    ) {
        // Card 1: Live Video Preview & Control Toolbar
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(appDp(24)),
            padding = appDp(12),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(appDp(16)))
                    .background(Color.Black)
                    .border(appDp(1), Color.White.copy(alpha = 0.10f), RoundedCornerShape(appDp(16)))
                    .clickable(enabled = channel != null, onClick = openFullScreen),
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            player = previewExo
                        }
                    },
                    update = { view ->
                        if (view.player !== previewExo) {
                            view.player = previewExo
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                if (!videoReady) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(PreviewPlaceholderBrush),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (channel != null && channel.icon.isNotBlank()) {
                            RemoteImage(
                                url = channel.icon,
                                fallbackText = channel.name,
                                modifier = Modifier.size(appDp(56)).clip(RoundedCornerShape(appDp(12))),
                                contentScale = ContentScale.Fit,
                            )
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(PreviewShadeBrush),
                )

                if (channel == null) {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(appDp(14)))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(appDp(1), Palette.Glow.copy(alpha = 0.35f), RoundedCornerShape(appDp(14)))
                            .padding(horizontal = appDp(16), vertical = appDp(10)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(appDp(6)),
                    ) {
                        Icon(Ic.Play, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size(appDp(26)))
                        Text(
                            "اختر قناة واضغط موافق أو المسها للمعاينة",
                            fontSize = appSp(12),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                } else if (buffering && !videoReady) {
                    Box(Modifier.align(Alignment.Center)) {
                        CircularProgressIndicator(
                            color = Palette.Glow,
                            trackColor = Color.White.copy(alpha = 0.15f),
                            modifier = Modifier.size(appDp(32)),
                            strokeWidth = appDp(3),
                        )
                    }
                }

                // Top-right badge: "مباشر - 1080p 60fps"
                Row(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(appDp(10))
                        .clip(RoundedCornerShape(appDp(8)))
                        .background(Color.Black.copy(alpha = 0.70f))
                        .border(appDp(1), Color.White.copy(alpha = 0.12f), RoundedCornerShape(appDp(8)))
                        .padding(horizontal = appDp(10), vertical = appDp(5)),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(appDp(6)),
                ) {
                    Box(Modifier.size(appDp(7)).clip(CircleShape).background(Palette.Live))
                    val vBadge = SahabPlayer.videoBadge(tracks, channel?.let { qualityOf(it.name) })
                    Text("${Ar.liveBadge} - $vBadge", fontSize = appSp(11), fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Bottom-left badge: Audio stream info or channel name
                if (channel != null) {
                    val aBadge = SahabPlayer.audioBadge(tracks) ?: channel.name
                    Row(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(appDp(10))
                            .clip(RoundedCornerShape(appDp(8)))
                            .background(Color.Black.copy(alpha = 0.70f))
                            .border(appDp(1), Color.White.copy(alpha = 0.12f), RoundedCornerShape(appDp(8)))
                            .padding(horizontal = appDp(10), vertical = appDp(5)),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(appDp(6)),
                    ) {
                        Text(
                            aBadge,
                            fontSize = appSp(11),
                            fontWeight = FontWeight.Bold,
                            color = Palette.Glow,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            if (channel != null) {
                Spacer(Modifier.height(appDp(10)))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(appDp(12), Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerControlButton(
                        icon = if (isPlaying) Ic.Pause else Ic.Play,
                        description = if (isPlaying) Ar.pause else Ar.play,
                        onClick = {
                            previewExo?.let { exo ->
                                if (exo.isPlaying) exo.pause() else exo.play()
                            }
                        },
                    )
                    PlayerControlButton(
                        icon = if (muted) Ic.VolumeOff else Ic.VolumeUp,
                        description = if (muted) Ar.unmute else Ar.mute,
                        onClick = {
                            muted = !muted
                            previewExo?.volume = if (muted) 0f else 1f
                        },
                    )
                    PlayerControlButton(
                        icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                        description = Ar.favorites,
                        onClick = onToggleFavorite,
                        isRed = true,
                    )
                    PlayerControlButton(icon = Ic.Search, description = Ar.guide, onClick = { onGuide(channel) })
                    PlayerControlButton(icon = Ic.Aspect, description = Ar.fullscreen, onClick = openFullScreen)
                }
            }
        }

        // Card 2: EPG Schedule (shown on Tablet/TV where vertical space allows both video and schedule)
        if (showEpg) {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(appDp(24)),
                padding = appDp(16),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DotTitle(Ar.epgTitle, Palette.Gold, fontSize = appSp(14))
                    Text(today, fontSize = appSp(11), color = Palette.TextDim)
                }

                Spacer(Modifier.height(appDp(10)))

                if (channel == null) {
                    Text(Ar.pickChannel, fontSize = appSp(13), color = Palette.TextDim)
                } else {
                    val now = System.currentTimeMillis() / 1000
                    val past = programs.lastOrNull { it.end <= now }
                    val current = programs.firstOrNull { now in it.start until it.end }
                    val upcoming = programs.filter { it.start > (current?.start ?: now) }.take(2)

                    if (past == null && current == null && upcoming.isEmpty()) {
                        Text(Ar.noEpgShort, fontSize = appSp(13), color = Palette.TextDim)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(appDp(8))) {
                            past?.let { prog ->
                                EpgScheduleRow(
                                    time = "${formatClock(prog.start)} - ${formatClock(prog.end)}",
                                    title = prog.title.ifBlank { Ar.noName },
                                    state = EpgRowState.ENDED,
                                    onClick = { onGuide(channel) },
                                )
                            }
                            current?.let { prog ->
                                EpgScheduleRow(
                                    time = "${formatClock(prog.start)} - ${formatClock(prog.end)}",
                                    title = prog.title.ifBlank { Ar.noName },
                                    state = EpgRowState.NOW,
                                    onClick = { onGuide(channel) },
                                )
                            }
                            upcoming.forEach { prog ->
                                EpgScheduleRow(
                                    time = "${formatClock(prog.start)} - ${formatClock(prog.end)}",
                                    title = prog.title.ifBlank { Ar.noName },
                                    state = EpgRowState.SOON,
                                    onClick = { onGuide(channel) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class EpgRowState { ENDED, NOW, SOON }

@Composable
private fun EpgScheduleRow(
    time: String,
    title: String,
    state: EpgRowState,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(appDp(12))
    val isNow = state == EpgRowState.NOW
    val bg: Brush = if (isNow) Palette.ActiveFill else Brush.verticalGradient(listOf(Palette.Glass, Palette.Glass))
    val borderColor = if (isNow) Palette.Glow else Palette.GlassBorder

    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(appDp(1), borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = appDp(12), vertical = appDp(9)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(appDp(10)),
        ) {
            Text(
                time,
                fontSize = appSp(11),
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                color = if (isNow) Palette.Gold else Palette.TextDim,
                maxLines = 1,
            )
            Text(
                title,
                fontSize = appSp(13),
                fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium,
                color = if (state == EpgRowState.ENDED) Palette.TextDim else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.width(appDp(8)))
        when (state) {
            EpgRowState.ENDED -> Tag(Ar.ended, Palette.TextDim, fontSize = appSp(10))
            EpgRowState.NOW -> Tag(Ar.onAirNow, Palette.Live, filled = true, fontSize = appSp(10))
            EpgRowState.SOON -> Tag(Ar.soon, Palette.Glow, fontSize = appSp(10))
        }
    }
}

@Composable
private fun PlayerControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    isRed: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isRed -> Palette.Live.copy(alpha = if (focused) 0.55f else 0.30f)
        focused -> Palette.Royal.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.10f)
    }
    val borderColor = when {
        focused -> Palette.Glow
        isRed -> Palette.Live.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    Box(
        modifier = Modifier
            .size(appDp(40))
            .clip(CircleShape)
            .background(bg)
            .border(if (focused) appDp(2) else appDp(1), borderColor, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (isRed) Palette.Live else Color.White,
            modifier = Modifier.size(appDp(18)),
        )
    }
}

@Composable
private fun appSp(n: Int): TextUnit = (n * LocalAppScale.current).sp

@Composable
private fun appDp(n: Int): Dp = (n * LocalAppScale.current).dp
