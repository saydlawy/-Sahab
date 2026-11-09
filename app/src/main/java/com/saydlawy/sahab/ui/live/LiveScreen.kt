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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
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
import com.saydlawy.sahab.ui.LocalAppScale
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private const val SPACE_EARTH_BG_URL =
    "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=1920&q=85"

private const val STADIUM_MATCH_PREVIEW_URL =
    "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=1000&q=80"


private const val FAV_CAT_ID = "__FAVORITES__"
private const val ALL_CAT_ID = "__ALL__"

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

    init {
        loadCategories()
    }

    fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCategories = true, error = null) }
            try {
                val categories = repo.liveCategories()
                // Load all channels to compute per-category counts and total
                val allChannels = try {
                    repo.liveChannels(null).also { searchPool = it }
                } catch (_: Exception) {
                    emptyList()
                }
                // Compute counts only if server sent categoryId
                val counts = HashMap<String, Int>()
                allChannels.forEach { ch ->
                    val cid = ch.categoryId
                    if (cid.isNotBlank()) {
                        counts[cid] = (counts[cid] ?: 0) + 1
                    }
                }
                _state.update {
                    it.copy(
                        categories = categories,
                        loadingCategories = false,
                        totalCount = allChannels.size,
                        categoryCounts = counts,
                    )
                }
                // Set initial selection
                if (allChannels.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            selectedId = ALL_CAT_ID,
                            channels = allChannels,
                            loadingChannels = false,
                        )
                    }
                } else {
                    categories.firstOrNull()?.let { select(it.id) }
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
            _state.update {
                it.copy(selectedId = FAV_CAT_ID, loadingChannels = false, error = null)
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
                if (categoryId == ALL_CAT_ID) searchPool = channels
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

    val rawChannels = state.visibleChannels
    val channels = remember(rawChannels, state.selectedId, favoriteIds) {
        if (state.selectedId == FAV_CAT_ID && state.searchResults == null) {
            rawChannels.filter { it.id in favoriteIds }
        } else {
            rawChannels
        }
    }

    val playAt: (Int) -> Unit = { index ->
        if (channels.isNotEmpty() && index in channels.indices) {
            val items = channels.map { PlayItem(Kind.LIVE, it.id, it.name, icon = it.icon) }
            onPlay(PlayRequest(items, index))
        }
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
            delay(1000)
            now = LocalDateTime.now()
        }
    }
    val ar = Locale.forLanguageTag("ar")
    val timeStr = now.format(DateTimeFormatter.ofPattern("hh:mm a", ar))
    val dateStr = now.format(DateTimeFormatter.ofPattern("EEEE، d MMMM", ar))

    Row(
        Modifier.fillMaxWidth().padding(horizontal = appDp(24), vertical = appDp(12)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassIconButton(Ic.Back, Ar.back, onBack, size = appDp(44))
        Spacer(Modifier.width(appDp(14)))
        Box(
            Modifier.size(appDp(42)).clip(CircleShape).background(Palette.Accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Play, null, tint = Color.White, modifier = Modifier.size(appDp(22)))
        }
        Spacer(Modifier.width(appDp(12)))
        Column {
            Text("Live TV", fontSize = appSp(22), fontWeight = FontWeight.Bold, color = Color.White)
            Text(Ar.live, fontSize = appSp(12), color = Color(0xFF9AA7D6))
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(timeStr, fontSize = appSp(22), fontWeight = FontWeight.Light, color = Color.White)
            Text(dateStr, fontSize = appSp(12), color = Color(0xFF9AA7D6))
        }
        Spacer(Modifier.width(appDp(14)))
        HeaderCircleButton(icon = Ic.Settings, description = "الإعدادات", onClick = {})
        Spacer(Modifier.width(appDp(8)))
        HeaderCircleButton(icon = Ic.Person, description = "الحساب", onClick = {})
    }
}

@Composable
private fun HeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(appDp(42))
            .clip(CircleShape)
            .background(
                if (focused) Color(0xFF8055E8).copy(alpha = 0.45f)
                else Color(0xFF232946).copy(alpha = 0.78f)
            )
            .border(
                width = appDp(1),
                color = if (focused) Color(0xFFB37FEB) else Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(appDp(20)))
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
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF050716).copy(alpha = 0.72f),
                        Color(0xFF080D24).copy(alpha = 0.84f),
                        Color(0xFF0D0B28).copy(alpha = 0.92f),
                        Color(0xFF1A0E3E).copy(alpha = 0.96f),
                    )
                )
            )
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxSize()) {
                LiveHeader(onBack = onBack)

                Row(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = appDp(24), vertical = appDp(12)),
                    horizontalArrangement = Arrangement.spacedBy(appDp(28)),
                ) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(appDp(12)),
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
                            categoryCounts = state.categoryCounts,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
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
                    PreviewPanel(
                        channel = previewChannel,
                        onGuide = onGuide,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
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
            Modifier.padding(horizontal = appDp(20), vertical = appDp(4)),
        )
        if (state.categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.padding(vertical = appDp(6)),
                contentPadding = PaddingValues(horizontal = appDp(16)),
                horizontalArrangement = Arrangement.spacedBy(appDp(8)),
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
    totalCount: Int,
    categoryCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(appDp(8)),
        contentPadding = PaddingValues(vertical = appDp(4)),
    ) {
        item(key = FAV_CAT_ID) {
            SidebarFavoritesChip(
                selected = selectedId == FAV_CAT_ID,
                onClick = { onSelect(FAV_CAT_ID) },
            )
        }
        item(key = ALL_CAT_ID) {
            VerticalCategoryChip(
                label = "الكل",
                selected = selectedId == ALL_CAT_ID || selectedId == null,
                count = if (totalCount > 0) totalCount else null,
                onClick = { onSelect(ALL_CAT_ID) },
            )
        }
        items(categories, key = { it.id }) { cat ->
            val count = categoryCounts[cat.id]
            VerticalCategoryChip(
                label = cat.name,
                selected = cat.id == selectedId,
                count = if (count != null && count > 0) count else null,
                onClick = { onSelect(cat.id) },
            )
        }
    }
}

@Composable
private fun SidebarFavoritesChip(selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val active = selected || focused
    val shape = RoundedCornerShape(appDp(14))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = appDp(52))
            .clip(shape)
            .background(
                if (active) Color(0xFF8055E8).copy(alpha = 0.55f)
                else Color.White.copy(alpha = 0.06f)
            )
            .border(
                width = if (focused) appDp(2) else appDp(1),
                color = if (focused) Color(0xFF7DC4FF)
                else if (selected) Color(0xFF8055E8).copy(alpha = 0.8f)
                else Color.White.copy(alpha = 0.10f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = appDp(14), vertical = appDp(14)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "المفضلة",
                fontSize = appSp(16),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                color = if (active) Color.White else Palette.TextDim,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Ic.Heart,
                contentDescription = null,
                tint = if (active) Color(0xFFFF5C8D) else Palette.TextDim,
                modifier = Modifier.size(appDp(18)),
            )
        }
    }
}

@Composable
private fun VerticalCategoryChip(
    label: String,
    selected: Boolean,
    count: Int? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(appDp(14))
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
            .heightIn(min = appDp(52))
            .clip(shape)
            .background(bg)
            .border(if (focused) appDp(2) else appDp(1), borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = appDp(14), vertical = appDp(14)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                fontSize = appSp(16),
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                color = if (focused || selected) Color.White else Palette.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Spacer(Modifier.width(appDp(8)))
                Text(
                    count.toString(),
                    fontSize = appSp(13),
                    fontWeight = FontWeight.Medium,
                    color = if (focused || selected) Color.White.copy(alpha = 0.85f)
                    else Palette.TextDim.copy(alpha = 0.7f),
                )
            }
        }
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
            .border(appDp(1), if (selected) Color(0xFF8055E8) else Color.White.copy(alpha = 0.10f), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = appDp(18), vertical = appDp(10)),
    ) {
        Text(
            label,
            fontSize = appSp(14),
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
            Modifier.fillMaxWidth().padding(horizontal = appDp(8), vertical = appDp(6)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "قائمة القنوات",
                fontSize = appSp(20),
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                modifier = Modifier.weight(1f),
            )
            if (channels.isNotEmpty()) {
                Text(
                    "${channels.size}",
                    fontSize = appSp(14),
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
                    contentPadding = PaddingValues(horizontal = appDp(4), vertical = appDp(4)),
                    verticalArrangement = Arrangement.spacedBy(appDp(8)),
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
    val shape = RoundedCornerShape(appDp(16))
    val borderColor = when {
        focused -> Color(0xFF7DC4FF)
        isPreviewed -> Color(0xFF7DC4FF).copy(alpha = 0.75f)
        else -> Color.White.copy(alpha = 0.08f)
    }
    val bg = when {
        focused -> Color(0xFF8055E8).copy(alpha = 0.20f)
        isPreviewed -> Color(0xFF7DC4FF).copy(alpha = 0.10f)
        else -> Color.White.copy(alpha = 0.04f)
    }
    val borderWidth = if (focused || isPreviewed) appDp(2) else appDp(1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(appDp(72))
            .then(
                if (isPreviewed && !focused) {
                    Modifier.shadow(
                        elevation = appDp(10),
                        shape = shape,
                        ambientColor = Color(0xFF7DC4FF),
                        spotColor = Color(0xFF7DC4FF),
                    )
                } else Modifier
            )
            .clip(shape)
            .background(bg)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clickable(onClick = onPlay)
            .padding(horizontal = appDp(12)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appDp(10)),
    ) {
        Text(
            index.toString(),
            fontSize = appSp(15),
            fontWeight = FontWeight.Bold,
            color = Palette.TextDim,
            modifier = Modifier.width(appDp(28)),
        )
        RemoteImage(
            url = channel.icon,
            fallbackText = channel.name,
            modifier = Modifier
                .size(appDp(52))
                .clip(RoundedCornerShape(appDp(12)))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(appDp(4)),
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appDp(4))) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    channel.name,
                    fontSize = appSp(16),
                    fontWeight = FontWeight.Bold,
                    color = Palette.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                qualityOf(channel.name)?.let { q ->
                    Spacer(Modifier.width(appDp(8)))
                    QualityBadge(q)
                }
                val program = now
                if (program != null) {
                    Spacer(Modifier.width(appDp(8)))
                    LiveBadge()
                }
            }
            val program = now
            if (program != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${formatClock(program.start)} - ${formatClock(program.end)}",
                        fontSize = appSp(12),
                        fontWeight = FontWeight.Medium,
                        color = Palette.Cyan,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(appDp(8)))
                    Text(
                        program.title.ifBlank { Ar.noName },
                        fontSize = appSp(13),
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
                        .height(appDp(3))
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
            size = appDp(38),
            tint = if (isFavorite) Palette.Pink else Palette.TextDim,
        )
        GlassIconButton(
            icon = Ic.List,
            description = Ar.guide,
            onClick = onGuide,
            size = appDp(38),
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
            .border(appDp(1), Color(0xFFE53935).copy(alpha = glowAlpha), RoundedCornerShape(50))
            .padding(horizontal = appDp(8), vertical = appDp(3)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appDp(4)),
    ) {
        Box(
            Modifier
                .size(appDp(6))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = dotAlpha)),
        )
        Text("مباشر", fontSize = appSp(10), fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun QualityBadge(q: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(appDp(6)))
            .border(appDp(1), Palette.Peach.copy(alpha = 0.7f), RoundedCornerShape(appDp(6)))
            .padding(horizontal = appDp(6), vertical = appDp(2)),
    ) {
        Text(q, fontSize = appSp(10), fontWeight = FontWeight.Bold, color = Palette.Peach)
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
            .clip(RoundedCornerShape(appDp(20)))
            .background(Color(0xFF181D32).copy(alpha = 0.92f))
            .border(appDp(1), Color.White.copy(alpha = 0.15f), RoundedCornerShape(appDp(20)))
            .padding(appDp(18)),
        verticalArrangement = Arrangement.spacedBy(appDp(14)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(appDp(14)))
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
                        .padding(horizontal = appDp(10), vertical = appDp(6))
                        .align(Alignment.TopStart),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RemoteImage(
                            url = channel.icon,
                            fallbackText = channel.name,
                            modifier = Modifier.size(appDp(22)).clip(RoundedCornerShape(appDp(4))),
                            contentScale = ContentScale.Fit,
                        )
                        Spacer(Modifier.width(appDp(8)))
                        Text(
                            channel.name,
                            fontSize = appSp(13),
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

            Spacer(Modifier.height(appDp(10)))
            // 5 round control buttons (stop / record / search / fullscreen / mute)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = appDp(4)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerControlButton(icon = Ic.Pause, description = "إيقاف", onClick = {})
                PlayerControlButton(icon = Ic.Close, description = "تسجيل", onClick = {}, isRed = true)
                PlayerControlButton(icon = Ic.Search, description = "دليل البرامج", onClick = { onGuide(channel) })
                PlayerControlButton(icon = Ic.Aspect, description = "ملء الشاشة", onClick = {})
                PlayerControlButton(icon = Ic.Tracks, description = "كتم الصوت", onClick = {})
            }

            Spacer(Modifier.height(appDp(4)))
            Text("دليل البرامج الإلكتروني", fontSize = appSp(13), color = Palette.Cyan, fontWeight = FontWeight.Bold)

            val now = System.currentTimeMillis() / 1000
            val current = programs.firstOrNull { now in it.start until it.end }
            val upcoming = programs.filter { it.start >= now }.take(3)

            if (current == null && upcoming.isEmpty()) {
                Text("لا تتوفر بيانات الدليل لهذه القناة", fontSize = appSp(13), color = Palette.TextDim)
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
        isRed -> Color(0xFFE53935).copy(alpha = 0.85f)
        focused -> Color(0xFF8055E8).copy(alpha = 0.55f)
        else -> Color(0xFF232946).copy(alpha = 0.85f)
    }
    val borderColor = when {
        isRed -> Color(0xFFE53935)
        focused -> Color(0xFF7DC4FF)
        else -> Color.White.copy(alpha = 0.15f)
    }
    Box(
        modifier = Modifier
            .size(appDp(38))
            .clip(CircleShape)
            .background(bg)
            .border(if (focused) appDp(2) else appDp(1), borderColor, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(appDp(16)))
    }
}

@Composable
private fun EpgMiniCard(title: String, time: String, progress: Float, highlight: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(appDp(12)))
            .background(if (highlight) Color(0xFF3978FF).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
            .padding(appDp(10)),
        verticalArrangement = Arrangement.spacedBy(appDp(4)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                fontSize = appSp(13),
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(appDp(8)))
            Text(time, fontSize = appSp(11), color = Palette.TextDim)
        }
        if (highlight) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(appDp(3))
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

@Composable
private fun appSp(n: Int): TextUnit = (n * LocalAppScale.current).sp

@Composable
private fun appDp(n: Int): Dp = (n * LocalAppScale.current).dp

