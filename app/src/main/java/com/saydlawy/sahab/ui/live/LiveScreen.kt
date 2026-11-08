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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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

    val channels = state.visibleChannels
    val playAt: (Int) -> Unit = { index ->
        val items = channels.map { PlayItem(Kind.LIVE, it.id, it.name, icon = it.icon) }
        onPlay(PlayRequest(items, index))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(Ar.live, onBack) {
                if (wide) {
                    SearchBox(state.query, vm::onQuery, Ar.searchChannels, Modifier.width(380.dp))
                }
            }
            if (!wide) {
                SearchBox(
                    state.query,
                    vm::onQuery,
                    Ar.searchChannels,
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            when {
                state.loadingCategories -> LoadingBox()
                state.categories.isEmpty() -> MessageBox(
                    message = state.error ?: Ar.noCategories,
                    actionLabel = Ar.retry,
                    onAction = vm::loadCategories,
                )
                wide -> Row(Modifier.fillMaxSize()) {
                    CategoryPane(
                        categories = state.categories,
                        selectedId = state.selectedId,
                        onSelect = vm::select,
                        wide = true,
                        modifier = Modifier
                            .width(270.dp)
                            .fillMaxHeight(),
                    )
                    ChannelList(
                        state = state,
                        favoriteIds = favoriteIds,
                        onPlayAt = playAt,
                        onToggleFavorite = vm::toggleFavorite,
                        onGuide = { guideFor = it },
                        onRetry = { state.selectedId?.let(vm::select) },
                        modifier = Modifier.weight(1f),
                    )
                }
                else -> Column(Modifier.fillMaxSize()) {
                    CategoryPane(
                        categories = state.categories,
                        selectedId = state.selectedId,
                        onSelect = vm::select,
                        wide = false,
                    )
                    ChannelList(
                        state = state,
                        favoriteIds = favoriteIds,
                        onPlayAt = playAt,
                        onToggleFavorite = vm::toggleFavorite,
                        onGuide = { guideFor = it },
                        onRetry = { state.selectedId?.let(vm::select) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }

    guideFor?.let { channel ->
        EpgDialog(channelName = channel.name, channelId = channel.id, onDismiss = { guideFor = null })
    }
}

@Composable
private fun ChannelList(
    state: LiveUiState,
    favoriteIds: Set<String>,
    onPlayAt: (Int) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = state.visibleChannels
    Box(modifier.fillMaxSize()) {
        when {
            state.busy && channels.isEmpty() -> LoadingBox()
            state.error != null && channels.isEmpty() -> MessageBox(state.error, actionLabel = Ar.retry, onAction = onRetry)
            channels.isEmpty() -> MessageBox(Ar.noChannels)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    ChannelRow(
                        channel = channel,
                        isFavorite = channel.id in favoriteIds,
                        onPlay = { onPlayAt(index) },
                        onToggleFavorite = { onToggleFavorite(channel) },
                        onGuide = { onGuide(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    isFavorite: Boolean,
    onPlay: () -> Unit,
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassCard(
            onClick = onPlay,
            onLongClick = onToggleFavorite,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(
                    url = channel.icon,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(5.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = channel.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val program = now
                    if (program != null) {
                        Text(
                            text = program.title.ifBlank { Ar.noName },
                            fontSize = 14.sp,
                            color = Palette.TextDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(5.dp))
                        val total = (program.end - program.start).coerceAtLeast(1)
                        ProgressLine((System.currentTimeMillis() / 1000 - program.start).toFloat() / total)
                    }
                }
            }
        }
        GlassIconButton(Ic.List, Ar.guide, onGuide, size = 50.dp)
        GlassIconButton(
            icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
            description = Ar.favorites,
            onClick = onToggleFavorite,
            size = 50.dp,
            tint = if (isFavorite) Palette.Pink else Palette.Text,
        )
    }
}
