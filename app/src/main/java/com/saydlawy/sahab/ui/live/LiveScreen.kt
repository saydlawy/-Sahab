package com.saydlawy.sahab.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.CategoryPane
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.ScreenHeader
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
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
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.util.formatClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val SPACE_EARTH_BG_URL =
    "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1920&q=85"

private const val STADIUM_MATCH_PREVIEW_URL =
    "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=1000&q=80"

private const val UCL_BANNER_URL =
    "https://images.unsplash.com/photo-1522778119026-d647f0596c20?w=600&q=80"

data class LiveUiState(
    val categories: List<Category> = emptyList(),
    val selectedId: String? = null,
    val channels: List<Channel> = emptyList(),
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

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCategories = true, error = null) }
            try {
                val categories = repo.liveCategories()
                _state.update { it.copy(categories = categories, loadingCategories = false) }
                categories.firstOrNull()?.let { select(it.id) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingCategories = false, error = e.userMessage()) }
            }
        }
    }

    fun select(categoryId: String) {
        channelsJob?.cancel()
        _state.update { it.copy(selectedId = categoryId, loadingChannels = true, error = null) }
        channelsJob = viewModelScope.launch {
            try {
                val channels = repo.liveChannels(categoryId)
                _state.update { it.copy(channels = channels, loadingChannels = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingChannels = false, channels = emptyList(), error = e.userMessage()) }
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
                val pool = searchPool ?: repo.liveChannels(null).also { searchPool = it }
                val results = withContext(Dispatchers.Default) {
                    pool.filter { it.name.normalizeForSearch().contains(needle) }.take(300)
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

    val channels = state.visibleChannels
    val playAt: (Int) -> Unit = { index ->
        val items = channels.map { PlayItem(Kind.LIVE, it.id, it.name, icon = it.icon) }
        onPlay(PlayRequest(items, index))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = (LocalIsTv.current || maxWidth >= 800.dp) && maxWidth > maxHeight
        if (wide) {
            WideLiveLayout(
                state = state,
                favoriteIds = favoriteIds,
                previewChannel = previewChannel ?: channels.firstOrNull(),
                onPreview = { previewChannel = it },
                onPlayAt = playAt,
                onToggleFavorite = vm::toggleFavorite,
                onGuide = { guideFor = it },
                onSelect = vm::select,
                onQuery = vm::onQuery,
                onBack = onBack,
                onRetry = { state.selectedId?.let(vm::select) },
            )
        } else {
            NarrowLiveLayout(
                state = state,
                favoriteIds = favoriteIds,
                onPlayAt = playAt,
                onToggleFavorite = vm::toggleFavorite,
                onGuide = { guideFor = it },
                onSelect = vm::select,
                onQuery = vm::onQuery,
                onBack = onBack,
                onRetry = { state.selectedId?.let(vm::select) },
            )
        }
    }

    guideFor?.let { channel ->
        EpgDialog(channelName = channel.name, channelId = channel.id, onDismiss = { guideFor = null })
    }
}

@Composable
private fun LiveHeader(onBack: () -> Unit) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            now = LocalDateTime.now()
        }
    }
    val ar = Locale.forLanguageTag("ar")
    val timeStr = now.format(DateTimeFormatter.ofPattern("hh:mm a", ar))
    val dateStr = now.format(DateTimeFormatter.ofPattern("EEEE، d MMMM", ar))

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Ic.Back, Ar.back, onBack, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Palette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Play, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Live TV", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text(Ar.live, fontSize = 12.sp, color = Color(0xFF9AA7D6))
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(timeStr, fontSize = 22.sp, fontWeight = FontWeight.Light, color = Color.White)
            Text(dateStr, fontSize = 12.sp, color = Color(0xFF9AA7D6))
        }
    }
}

@Composable
private fun WideLiveLayout(
    state: LiveUiState,
    favoriteIds: Set<String>,
    previewChannel: Channel?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onSelect: (String) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0xFF060814))) {
        RemoteImage(
            url = SPACE_EARTH_BG_URL,
            fallbackText = "",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(
                Color(0xFF050716).copy(alpha = 0.72f),
                Color(0xFF080D24).copy(alpha = 0.84f),
                Color(0xFF0D0B28).copy(alpha = 0.92f),
                Color(0xFF1A0E3E).copy(alpha = 0.96f),
            ))
        ))
        Column(Modifier.fillMaxSize()) {
        LiveHeader(onBack = onBack)
        SearchBox(
            state.query,
            onQuery,
            Ar.searchChannels,
            Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showPreview = maxWidth >= 750.dp
            val sidebarWidth = (maxWidth * 0.15f).coerceIn(140.dp, 200.dp)
            val previewWidth = (maxWidth * 0.24f).coerceIn(220.dp, 340.dp)
            val hPad = (maxWidth * 0.02f).coerceIn(12.dp, 24.dp)
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = hPad, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CategorySidebar(
                    categories = state.categories,
                    selectedId = state.selectedId,
                    onSelect = onSelect,
                    modifier = Modifier.width(sidebarWidth).fillMaxHeight(),
                )
                ChannelColumn(
                    state = state,
                    favoriteIds = favoriteIds,
                    previewId = previewChannel?.id,
                    onPreview = onPreview,
                    onPlayAt = onPlayAt,
                    onToggleFavorite = onToggleFavorite,
                    onGuide = onGuide,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                if (showPreview) {
                    PreviewPanel(
                        channel = previewChannel,
                        onGuide = onGuide,
                        modifier = Modifier.width(previewWidth).fillMaxHeight(),
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun NarrowLiveLayout(
    state: LiveUiState,
    favoriteIds: Set<String>,
    onPlayAt: (Int) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onSelect: (String) -> Unit,
    onQuery: (String) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(Ar.live, onBack) {}
        SearchBox(
            state.query,
            onQuery,
            Ar.searchChannels,
            Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        if (state.categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(vertical = 6.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.categories, key = { it.id }) { cat ->
                    HorizontalCategoryChip(
                        label = cat.name,
                        selected = cat.id == state.selectedId,
                        onClick = { onSelect(cat.id) },
                    )
                }
            }
        }
        ChannelColumn(
            state = state,
            favoriteIds = favoriteIds,
            previewId = null,
            onPreview = {},
            onPlayAt = onPlayAt,
            onToggleFavorite = onToggleFavorite,
            onGuide = onGuide,
            onRetry = onRetry,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
private fun CategorySidebar(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        item {
            Text(
                "الفئات",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        items(categories, key = { it.id }) { cat ->
            VerticalCategoryChip(
                label = cat.name,
                selected = cat.id == selectedId,
                onClick = { onSelect(cat.id) },
            )
        }
    }
}

@Composable
private fun VerticalCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val bg = when {
        focused -> Color(0xFF8055E8).copy(alpha = 0.60f)
        selected -> Color(0xFF8055E8).copy(alpha = 0.45f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val borderColor = when {
        focused -> Color(0xFF7DC4FF)
        selected -> Color(0xFF8055E8).copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            label,
            fontSize = 16.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            color = if (focused || selected) Color.White else Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HorizontalCategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val bg = when {
        focused -> Color(0xFF8055E8).copy(alpha = 0.35f)
        selected -> Color(0xFF8055E8).copy(alpha = 0.25f)
        else -> Color.White.copy(alpha = 0.06f)
    }
    Box(
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Color(0xFF8055E8) else Color.White.copy(alpha = 0.10f), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = Palette.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChannelColumn(
    state: LiveUiState,
    favoriteIds: Set<String>,
    previewId: String?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = state.visibleChannels
    val listState = rememberLazyListState()

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "قائمة القنوات",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
            )
            if (channels.isNotEmpty()) {
                Text(
                    "${channels.size}",
                    fontSize = 14.sp,
                    color = Palette.Cyan,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy && channels.isEmpty() -> LoadingBox()
                state.error != null && channels.isEmpty() -> MessageBox(
                    state.error,
                    actionLabel = Ar.retry,
                    onAction = onRetry,
                )
                channels.isEmpty() -> MessageBox(Ar.noChannels)
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                        ChannelRow(
                            channel = channel,
                            index = index + 1,
                            isFavorite = channel.id in favoriteIds,
                            isPreviewed = channel.id == previewId,
                            onPlay = { onPlayAt(index) },
                            onFocus = { onPreview(channel) },
                            onToggleFavorite = { onToggleFavorite(channel) },
                            onGuide = { onGuide(channel) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        LatestMatchesBottomStrip(
            channels = channels,
            onPlayChannel = { ch ->
                val idx = channels.indexOfFirst { it.id == ch.id }
                if (idx >= 0) onPlayAt(idx)
            },
        )
    }
}

@Composable
private fun LatestMatchesBottomStrip(
    channels: List<Channel>,
    onPlayChannel: (Channel) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 6.dp, end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("أحدث المباريات", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(3.dp).height(16.dp).clip(RoundedCornerShape(50)).background(Color(0xFFA855F7)))
        }
        Row(
            Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF171C32).copy(alpha = 0.92f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.width(100.dp).fillMaxHeight().clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(10.dp))
                    .clickable { channels.firstOrNull()?.let(onPlayChannel) },
            ) {
                RemoteImage(
                    url = UCL_BANNER_URL,
                    fallbackText = "Match",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Color.White)
                    .clickable { channels.firstOrNull { it.name.contains("bein", ignoreCase = true) }?.let(onPlayChannel) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("beIN", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color(0xFF3B0764))
                    Text("SPORTS", fontSize = 6.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3B0764))
                }
            }
            MatchBrandTile(
                title = "mbc",
                bgColors = listOf(Color(0xFFEF4444), Color(0xFF991B1B)),
                onClick = { channels.firstOrNull { it.name.contains("mbc", ignoreCase = true) }?.let(onPlayChannel) },
                modifier = Modifier.weight(1f),
            )
            MatchBrandTile(
                title = "العربية",
                bgColors = listOf(Color(0xFF6D28D9), Color(0xFF4C1D95)),
                onClick = { channels.firstOrNull { it.name.contains("arab", ignoreCase = true) }?.let(onPlayChannel) },
                modifier = Modifier.weight(1f),
            )
            MatchBrandTile(
                title = "العربية",
                bgColors = listOf(Color(0xFF5B21B6), Color(0xFF3B0764)),
                onClick = { channels.firstOrNull { it.name.contains("arab", ignoreCase = true) }?.let(onPlayChannel) },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(28.dp).clip(CircleShape)
                    .clickable { channels.firstOrNull()?.let(onPlayChannel) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Next, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun MatchBrandTile(
    title: String,
    bgColors: List<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(bgColors))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    index: Int,
    isFavorite: Boolean,
    isPreviewed: Boolean,
    onPlay: () -> Unit,
    onFocus: () -> Unit,
    onToggleFavorite: () -> Unit,
    onGuide: () -> Unit,
) {
    val repo = LocalContainer.current.repo
    val now by produceState<Program?>(initialValue = null, channel.id) {
        value = if (channel.epgId.isBlank()) null else try {
            val t = System.currentTimeMillis() / 1000
            repo.shortEpg(channel.id, 2).firstOrNull { t in it.start until it.end }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val borderColor = when {
        focused -> Color(0xFF7DC4FF)
        isPreviewed -> Color(0xFF8055E8).copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val bg = when {
        focused -> Color(0xFF8055E8).copy(alpha = 0.20f)
        isPreviewed -> Color.White.copy(alpha = 0.08f)
        else -> Color.White.copy(alpha = 0.04f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            index.toString(),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Palette.TextDim,
            modifier = Modifier.width(28.dp),
        )
        RemoteImage(
            url = channel.icon,
            fallbackText = channel.name,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(4.dp),
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                qualityOf(channel.name)?.let { q ->
                    Spacer(Modifier.width(8.dp))
                    QualityBadge(q)
                }
                val program = now
                if (program != null) {
                    Spacer(Modifier.width(8.dp))
                    LiveBadge()
                }
            }
            val program = now
            if (program != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatClock(program.start)} - ${formatClock(program.end)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Palette.Cyan,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        program.title.ifBlank { Ar.noName },
                        fontSize = 13.sp,
                        color = Palette.TextDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                val total = (program.end - program.start).coerceAtLeast(1)
                val progress = ((System.currentTimeMillis() / 1000 - program.start).toFloat() / total).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.15f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress)
                            .background(Palette.Cyan),
                    )
                }
            }
        }
        GlassIconButton(
            icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
            description = Ar.favorites,
            onClick = onToggleFavorite,
            size = 38.dp,
            tint = if (isFavorite) Palette.Pink else Palette.TextDim,
        )
        GlassIconButton(
            icon = Ic.List,
            description = Ar.guide,
            onClick = onGuide,
            size = 38.dp,
        )
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
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowAlpha",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFFE53935).copy(alpha = 0.9f))
            .border(1.dp, Color(0xFFE53935).copy(alpha = glowAlpha), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = dotAlpha)),
        )
        Text("مباشر", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun QualityBadge(q: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, Palette.Peach.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(q, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Palette.Peach)
    }
}

private fun qualityOf(name: String): String? {
    val u = name.uppercase()
    return when {
        "4K" in u || "UHD" in u -> "4K"
        "FHD" in u || "1080" in u -> "FHD"
        "HD" in u -> "HD"
        else -> null
    }
}

@Composable
private fun PreviewPanel(channel: Channel?, onGuide: (Channel) -> Unit, modifier: Modifier = Modifier) {
    val repo = LocalContainer.current.repo
    val programs by produceState<List<Program>>(emptyList(), channel?.id) {
        value = if (channel != null && channel.epgId.isNotBlank()) {
            try { repo.shortEpg(channel.id, 6) } catch (e: Exception) { emptyList() }
        } else emptyList()
    }

    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF181D32).copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black),
        ) {
            RemoteImage(
                url = STADIUM_MATCH_PREVIEW_URL,
                fallbackText = channel?.name ?: "Live TV",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (channel != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .align(Alignment.TopStart),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteImage(
                            url = channel.icon,
                            fallbackText = channel.name,
                            modifier = Modifier.size(22.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            channel.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (channel != null) {

            Spacer(Modifier.height(4.dp))
            Text("دليل البرامج", fontSize = 13.sp, color = Palette.Cyan, fontWeight = FontWeight.Bold)

            val now = System.currentTimeMillis() / 1000
            val current = programs.firstOrNull { now in it.start until it.end }
            val upcoming = programs.filter { it.start >= now }.take(3)

            if (current == null && upcoming.isEmpty()) {
                Text("لا تتوفر بيانات الدليل لهذه القناة", fontSize = 13.sp, color = Palette.TextDim)
            } else {
                current?.let { prog ->
                    EpgMiniCard(
                        title = prog.title.ifBlank { Ar.noName },
                        time = "${formatClock(prog.start)} - ${formatClock(prog.end)}",
                        progress = ((now - prog.start).toFloat() / (prog.end - prog.start).coerceAtLeast(1)).coerceIn(0f, 1f),
                        highlight = true,
                    )
                }
                upcoming.forEach { prog ->
                    EpgMiniCard(
                        title = prog.title.ifBlank { Ar.noName },
                        time = "${formatClock(prog.start)} - ${formatClock(prog.end)}",
                        progress = 0f,
                        highlight = false,
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Palette.Accent)
                    .clickable { onGuide(channel) },
                contentAlignment = Alignment.Center,
            ) {
                Text("فتح الدليل الكامل", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
private fun EpgMiniCard(title: String, time: String, progress: Float, highlight: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (highlight) Color(0xFF3978FF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(time, fontSize = 11.sp, color = Palette.TextDim)
        }
        if (highlight) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.15f)),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .background(Palette.Cyan),
                )
            }
        }
    }
}
