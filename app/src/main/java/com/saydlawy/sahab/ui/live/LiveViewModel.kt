package com.saydlawy.sahab.ui.live

import com.saydlawy.sahab.data.channel.ChannelListEntity
import com.saydlawy.sahab.data.channel.EpgProgramEntity
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
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.ChannelLogoResolver
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.runtime.Immutable

internal const val CUSTOM_LIST_PREFIX = "custom_list:"

@Immutable
data class LiveUiState(
    val categories: List<Category> = emptyList(),
    val customLists: List<ChannelListEntity> = emptyList(),
    val hiddenChannelIds: Set<String> = emptySet(),
    val selectedId: String? = ALL_CAT_ID,
    val channels: List<Channel> = emptyList(),
    val visibleChannels: List<Channel> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val totalCount: Int = 0,
    val query: String = "",
    val searchResults: List<Channel>? = null,
    val loadingCategories: Boolean = true,
    val loadingChannels: Boolean = false,
    val searching: Boolean = false,
    val error: String? = null,
) {
    val busy: Boolean get() = loadingChannels || searching
}

class LiveViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repo
    private val _state = MutableStateFlow(LiveUiState())
    val state = _state.asStateFlow()
    private val visibleMutex = Mutex()

    private var channelsJob: Job? = null
    private var searchJob: Job? = null
    private var searchPool: List<Channel>? = null
    private var indexedPool: List<Pair<Channel, String>>? = null

    private suspend fun updateWithVisible(transform: (LiveUiState) -> LiveUiState) {
        visibleMutex.withLock {
            val candidate = transform(_state.value)
            val source = candidate.searchResults ?: candidate.channels
            val hidden = candidate.hiddenChannelIds
            val visible = when {
                hidden.isEmpty() -> source
                source.size <= 200 -> source.filterNot { it.id in hidden }
                else -> withContext(Dispatchers.Default) { source.filterNot { it.id in hidden } }
            }
            _state.update { latest ->
                transform(latest).copy(visibleChannels = visible)
            }
        }
    }

    private suspend fun updateSearchPool(channels: List<Channel>) {
        searchPool = channels
        indexedPool = withContext(Dispatchers.Default) {
            channels.map { it to it.name.normalizeForSearch() }
        }
    }

    private val dao = container.channelDb.channelDao()

    init {
        loadCategories()
        observeCustomTvState()
    }

    private fun observeCustomTvState() {
        viewModelScope.launch {
            container.prefs.activeAccountId.collectLatest { accId ->
                dao.observeLists(accId).collect { lists ->
                    _state.update { it.copy(customLists = lists) }
                }
            }
        }
        viewModelScope.launch {
            dao.observeHiddenIds().collect { hidden ->
                val hiddenSet = hidden.toSet()
                updateWithVisible { it.copy(hiddenChannelIds = hiddenSet) }
            }
        }
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
                    updateWithVisible {
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
                        updateWithVisible { current ->
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
        if (categoryId.startsWith(CUSTOM_LIST_PREFIX)) {
            val listId = categoryId.removePrefix(CUSTOM_LIST_PREFIX)
            _state.update { it.copy(selectedId = categoryId, loadingChannels = true, error = null) }
            channelsJob = viewModelScope.launch {
                val orderedIds = withContext(Dispatchers.IO) { dao.itemsOfList(listId) }
                val pool = allLiveChannels()
                val byId = pool.associateBy { it.id }
                val listChannels = orderedIds.mapNotNull { byId[it] }
                updateWithVisible {
                    it.copy(
                        selectedId = categoryId,
                        channels = listChannels,
                        loadingChannels = false,
                        error = null,
                    )
                }
            }
            return
        }
        if (categoryId == FAV_CAT_ID) {
            val pool = searchPool
            channelsJob = viewModelScope.launch {
                updateWithVisible {
                    it.copy(
                        selectedId = FAV_CAT_ID,
                        channels = pool ?: it.channels,
                        loadingChannels = pool.isNullOrEmpty() && it.channels.isEmpty(),
                        error = null,
                    )
                }
                if (pool.isNullOrEmpty()) {
                    val all = runCatching { repo.liveChannels(null) }.getOrDefault(emptyList())
                    if (all.isNotEmpty()) {
                        updateSearchPool(all)
                        updateWithVisible {
                            it.copy(
                                selectedId = FAV_CAT_ID,
                                channels = all,
                                loadingChannels = false,
                                error = null,
                            )
                        }
                    } else {
                        updateWithVisible { it.copy(loadingChannels = false) }
                    }
                }
            }
            return
        }
        if (categoryId == ALL_CAT_ID) {
            val cached = searchPool
            if (!cached.isNullOrEmpty()) {
                viewModelScope.launch {
                    updateWithVisible {
                        it.copy(
                            selectedId = ALL_CAT_ID,
                            channels = cached,
                            loadingChannels = false,
                            error = null,
                        )
                    }
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
                updateWithVisible {
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
                updateWithVisible {
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
            viewModelScope.launch {
                updateWithVisible { it.copy(searchResults = null, searching = false) }
            }
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
                updateWithVisible { it.copy(searchResults = results, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                updateWithVisible { it.copy(searching = false, searchResults = emptyList(), error = e.userMessage()) }
            }
        }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch {
            container.prefs.toggleFavorite(
                FavItem(Kind.LIVE, channel.id, channel.name, channel.icon, url = channel.url, accountId = container.prefs.currentActiveAccountId())
            )
        }
    }

    suspend fun allLiveChannels(): List<Channel> {
        searchPool?.let { return it }
        val loaded = runCatching { repo.liveChannels(null) }.getOrDefault(emptyList())
        if (loaded.isNotEmpty()) updateSearchPool(loaded)
        return loaded
    }

    suspend fun listChannelIds(listId: String): List<String> = withContext(Dispatchers.IO) {
        dao.itemsOfList(listId)
    }

    fun createCustomList(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val accId = container.prefs.currentActiveAccountId()
            dao.upsertList(
                ChannelListEntity(
                    listId = java.util.UUID.randomUUID().toString(),
                    name = clean,
                    createdAt = now,
                    updatedAt = now,
                    accountId = accId,
                )
            )
        }
    }

    fun deleteCustomList(listId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteListAndItems(listId)
            if (_state.value.selectedId == "$CUSTOM_LIST_PREFIX$listId") {
                withContext(Dispatchers.Main) { select(ALL_CAT_ID) }
            }
        }
    }

    fun saveListChannels(listId: String, orderedChannelIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.replaceListItems(listId, orderedChannelIds)
            if (_state.value.selectedId == "$CUSTOM_LIST_PREFIX$listId") {
                withContext(Dispatchers.Main) { select("$CUSTOM_LIST_PREFIX$listId") }
            }
        }
    }

    fun toggleChannelInList(listId: String, channelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.toggleChannelInList(listId, channelId)
            if (_state.value.selectedId == "$CUSTOM_LIST_PREFIX$listId") {
                withContext(Dispatchers.Main) { select("$CUSTOM_LIST_PREFIX$listId") }
            }
        }
    }

    fun hideChannel(channelId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.setHidden(channelId, true, System.currentTimeMillis())
        }
    }

    fun moveChannelOrder(channelId: String, delta: Int) {
        val current = _state.value.channels.toMutableList()
        val idx = current.indexOfFirst { it.id == channelId }
        val target = (idx + delta).coerceIn(0, (current.size - 1).coerceAtLeast(0))
        if (idx < 0 || idx == target) return
        val moved = current.removeAt(idx)
        current.add(target, moved)
        viewModelScope.launch {
            updateWithVisible { it.copy(channels = current) }
        }

        val selected = _state.value.selectedId
        viewModelScope.launch(Dispatchers.IO) {
            if (selected != null && selected.startsWith(CUSTOM_LIST_PREFIX)) {
                val listId = selected.removePrefix(CUSTOM_LIST_PREFIX)
                dao.replaceListItems(listId, current.map { it.id })
            } else {
                val now = System.currentTimeMillis()
                dao.setSortOrder(current[idx].id, idx, now)
                dao.setSortOrder(current[target].id, target, now)
            }
        }
    }
}

