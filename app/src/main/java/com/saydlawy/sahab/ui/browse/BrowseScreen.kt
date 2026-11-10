package com.saydlawy.sahab.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.RecentItem
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.SideNavItem
import com.saydlawy.sahab.ui.components.SkeletonGrid
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
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

private const val RECENTS_CAT_ID = "__RECENTS__"
private const val FAV_CAT_ID = "__FAVORITES__"
private const val ALL_CAT_ID = "__ALL__"

data class BrowseUiState(
    val categories: List<Category> = emptyList(),
    val selectedId: String? = null,
    val items: List<Poster> = emptyList(),
    val categoryCounts: Map<String, Int> = emptyMap(),
    val totalCount: Int = 0,
    val query: String = "",
    val searchResults: List<Poster>? = null,
    val loadingCategories: Boolean = true,
    val loadingItems: Boolean = false,
    val searching: Boolean = false,
    val error: String? = null,
) {
    val visibleItems: List<Poster> get() = searchResults ?: items
    val busy: Boolean get() = loadingItems || searching
}

/** One view model for both movies and series: they only differ in which calls they make. */
class BrowseViewModel(private val container: AppContainer, private val series: Boolean) : ViewModel() {

    private val repo = container.repo
    private val _state = MutableStateFlow(BrowseUiState())
    val state = _state.asStateFlow()

    private var itemsJob: Job? = null
    private var searchJob: Job? = null
    private var searchPool: List<Poster>? = null
    private var indexedPool: List<Pair<Poster, String>>? = null

    private fun updateSearchPool(list: List<Poster>) {
        searchPool = list
        indexedPool = list.map { it to it.name.normalizeForSearch() }
    }

    init {
        loadCategories()
    }

    private suspend fun fetchCategories() = if (series) repo.seriesCategories() else repo.movieCategories()

    private suspend fun peekAllItems() = if (series) repo.peekAllSeries() else repo.peekAllMovies()

    private suspend fun fetchItems(categoryId: String?) =
        if (series) repo.seriesList(categoryId) else repo.movies(categoryId)

    private suspend fun computeCounts(allItems: List<Poster>): Map<String, Int> =
        withContext(Dispatchers.Default) {
            val counts = HashMap<String, Int>()
            allItems.forEach { p ->
                val cid = p.categoryId
                if (cid.isNotBlank()) counts[cid] = (counts[cid] ?: 0) + 1
            }
            counts
        }

    fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCategories = true, error = null) }
            try {
                val categories = fetchCategories()
                val cachedAll = peekAllItems()
                if (!cachedAll.isNullOrEmpty()) {
                    updateSearchPool(cachedAll)
                    val counts = computeCounts(cachedAll)
                    _state.update {
                        it.copy(
                            categories = categories,
                            selectedId = ALL_CAT_ID,
                            items = cachedAll,
                            loadingCategories = false,
                            loadingItems = false,
                            totalCount = cachedAll.size,
                            categoryCounts = counts,
                        )
                    }
                    return@launch
                }

                // First-time cold start: show categories immediately and load the first category right away
                val firstCatId = categories.firstOrNull()?.id ?: ALL_CAT_ID
                _state.update {
                    it.copy(
                        categories = categories,
                        selectedId = firstCatId,
                        loadingCategories = false,
                    )
                }
                select(firstCatId)

                // Warm up the full catalog and category counts in the background without blocking UI
                viewModelScope.launch {
                    val allItems = runCatching { fetchItems(null) }.getOrDefault(emptyList())
                    if (allItems.isNotEmpty()) {
                        updateSearchPool(allItems)
                        val counts = computeCounts(allItems)
                        _state.update { current ->
                            current.copy(
                                totalCount = allItems.size,
                                categoryCounts = counts + current.categoryCounts,
                                items = if (current.selectedId == ALL_CAT_ID || current.items.isEmpty()) allItems else current.items,
                                loadingItems = if (current.selectedId == ALL_CAT_ID) false else current.loadingItems,
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
        itemsJob?.cancel()
        if (categoryId == RECENTS_CAT_ID) {
            _state.update {
                it.copy(
                    selectedId = RECENTS_CAT_ID,
                    loadingItems = false,
                    error = null,
                )
            }
            return
        }
        if (categoryId == FAV_CAT_ID) {
            val pool = searchPool
            _state.update {
                it.copy(
                    selectedId = FAV_CAT_ID,
                    items = pool ?: it.items,
                    loadingItems = false,
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
                        items = cached,
                        loadingItems = false,
                        error = null,
                    )
                }
                return
            }
        }
        _state.update { it.copy(selectedId = categoryId, loadingItems = true, error = null) }
        itemsJob = viewModelScope.launch {
            try {
                val targetId = if (categoryId == ALL_CAT_ID) null else categoryId
                val items = fetchItems(targetId)
                if (categoryId == ALL_CAT_ID) updateSearchPool(items)
                _state.update {
                    it.copy(
                        items = items,
                        loadingItems = false,
                        categoryCounts = if (categoryId != ALL_CAT_ID && categoryId != FAV_CAT_ID && categoryId != RECENTS_CAT_ID) {
                            it.categoryCounts + (categoryId to items.size)
                        } else it.categoryCounts,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingItems = false, items = emptyList(), error = e.userMessage()) }
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
                    val pool = searchPool ?: fetchItems(null).also { updateSearchPool(it) }
                    indexedPool ?: pool.map { it to it.name.normalizeForSearch() }
                }
                val results = withContext(Dispatchers.Default) {
                    indexed.asSequence()
                        .filter { it.second.contains(needle) }
                        .map { it.first }
                        .take(400)
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

    fun toggleFavorite(poster: Poster) {
        val kind = if (series) Kind.SERIES else Kind.MOVIE
        viewModelScope.launch {
            container.prefs.toggleFavorite(FavItem(kind, poster.id, poster.name, poster.image, poster.ext))
        }
    }
}

@Composable
fun BrowseScreen(
    series: Boolean,
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onPlay: (PlayRequest) -> Unit = {},
) {
    val vm = appViewModel { BrowseViewModel(it, series) }
    val state by vm.state.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val kind = if (series) Kind.SERIES else Kind.MOVIE
    val route = if (series) Routes.SERIES else Routes.MOVIES
    val favorites by LocalContainer.current.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents by LocalContainer.current.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteIds = remember(favorites, kind) { favorites.filter { it.kind == kind }.map { it.id }.toSet() }
    val screenRecents = remember(recents, series) {
        if (series) recents.filter { it.kind == Kind.SERIES || it.kind == Kind.EPISODE }
        else recents.filter { it.kind == Kind.MOVIE }
    }
    var previewPoster by remember { mutableStateOf<Poster?>(null) }

    val playRecent: (RecentItem) -> Unit = { recent ->
        onPlay(
            PlayRequest(
                items = listOf(
                    PlayItem(
                        kind = recent.kind,
                        id = recent.id,
                        title = recent.title,
                        subtitle = recent.subtitle,
                        icon = recent.icon,
                        ext = recent.ext,
                        groupId = recent.groupId,
                    ),
                ),
                startIndex = 0,
                fromStart = false,
                initialPositionMs = recent.positionMs,
            ),
        )
    }

    val rawItems = state.visibleItems
    val items = remember(rawItems, state.selectedId, favoriteIds, state.searchResults, favorites, screenRecents) {
        when {
            state.selectedId == RECENTS_CAT_ID && state.searchResults == null -> {
                screenRecents.map { r ->
                    Poster(
                        id = if (series && r.groupId.isNotBlank()) r.groupId else r.id,
                        name = r.title,
                        image = r.icon,
                        rating = "",
                        ext = r.ext,
                        year = r.subtitle,
                    )
                }.distinctBy { it.id }
            }
            state.selectedId == FAV_CAT_ID && state.searchResults == null -> {
                val fromPool = rawItems.filter { it.id in favoriteIds }
                if (fromPool.isNotEmpty()) fromPool
                else favorites.filter { it.kind == kind }.map {
                    Poster(id = it.id, name = it.name, image = it.icon, rating = "", ext = it.ext, year = "")
                }
            }
            else -> rawItems
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val displayMode = LocalDisplayMode.current
        val showPreview = displayMode == DisplayMode.TABLET_OR_TV && maxWidth >= 1040.dp

        Column(Modifier.fillMaxSize()) {
            SahabTopBar(current = route, onBack = onBack)

            when {
                state.loadingCategories -> LoadingBox()
                state.categories.isEmpty() && items.isEmpty() -> MessageBox(
                    message = state.error ?: Ar.noCategories,
                    actionLabel = Ar.retry,
                    onAction = vm::loadCategories,
                )
                displayMode == DisplayMode.TABLET_OR_TV -> Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // Column 1 (Right in RTL): Search + Categories sidebar
                    Column(
                        Modifier
                            .width(250.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SearchBox(
                            state.query,
                            vm::onQuery,
                            Ar.searchTitles,
                            Modifier.fillMaxWidth(),
                        )
                        BrowseSidebar(
                            categories = state.categories,
                            selectedId = state.selectedId,
                            onSelect = vm::select,
                            totalCount = state.totalCount,
                            favCount = favoriteIds.size,
                            recentCount = screenRecents.size,
                            categoryCounts = state.categoryCounts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }

                    // Column 2 (Center): Poster grid
                    PosterGrid(
                        series = series,
                        state = state,
                        items = items,
                        recents = if (state.query.isBlank() && state.selectedId != RECENTS_CAT_ID) screenRecents else emptyList(),
                        onPlayRecent = playRecent,
                        columnWidth = if (isTv) 152.dp else 138.dp,
                        onOpenItem = onOpenItem,
                        onPreview = { previewPoster = it },
                        onToggleFavorite = vm::toggleFavorite,
                        onRetry = { state.selectedId?.let(vm::select) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )

                    // Column 3 (Left in RTL): Live Preview Panel
                    if (showPreview) {
                        val active = previewPoster?.takeIf { p -> items.any { it.id == p.id } } ?: items.firstOrNull()
                        TitlePreviewPanel(
                            series = series,
                            poster = active,
                            isFavorite = active?.id in favoriteIds,
                            onOpenItem = { active?.let { onOpenItem(it.id) } },
                            onPlay = onPlay,
                            onToggleFavorite = { active?.let(vm::toggleFavorite) },
                            modifier = Modifier
                                .width(340.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
                displayMode == DisplayMode.PHONE_LANDSCAPE -> Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Compact sidebar on right in RTL for landscape phones
                    Column(
                        Modifier
                            .width(210.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SearchBox(
                            state.query,
                            vm::onQuery,
                            Ar.searchTitles,
                            Modifier.fillMaxWidth(),
                        )
                        BrowseSidebar(
                            categories = state.categories,
                            selectedId = state.selectedId,
                            onSelect = vm::select,
                            totalCount = state.totalCount,
                            favCount = favoriteIds.size,
                            recentCount = screenRecents.size,
                            categoryCounts = state.categoryCounts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }

                    // Poster grid taking all remaining width without 3rd column squeeze
                    PosterGrid(
                        series = series,
                        state = state,
                        items = items,
                        recents = if (state.query.isBlank() && state.selectedId != RECENTS_CAT_ID) screenRecents else emptyList(),
                        onPlayRecent = playRecent,
                        columnWidth = 132.dp,
                        onOpenItem = onOpenItem,
                        onPreview = {},
                        onToggleFavorite = vm::toggleFavorite,
                        onRetry = { state.selectedId?.let(vm::select) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                else -> Column(Modifier.fillMaxSize()) {
                    SearchBox(
                        state.query,
                        vm::onQuery,
                        Ar.searchTitles,
                        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (screenRecents.isNotEmpty()) {
                            item(key = RECENTS_CAT_ID) {
                                Chip(Ar.continueWatching, state.selectedId == RECENTS_CAT_ID, { vm.select(RECENTS_CAT_ID) })
                            }
                        }
                        item(key = FAV_CAT_ID) {
                            Chip(Ar.favorites, state.selectedId == FAV_CAT_ID, { vm.select(FAV_CAT_ID) })
                        }
                        item(key = ALL_CAT_ID) {
                            Chip(Ar.all, state.selectedId == ALL_CAT_ID, { vm.select(ALL_CAT_ID) })
                        }
                        items(state.categories, key = { it.id }) { cat ->
                            Chip(cat.name, cat.id == state.selectedId, { vm.select(cat.id) })
                        }
                    }
                    PosterGrid(
                        series = series,
                        state = state,
                        items = items,
                        recents = if (state.query.isBlank() && state.selectedId != RECENTS_CAT_ID) screenRecents else emptyList(),
                        onPlayRecent = playRecent,
                        columnWidth = 132.dp,
                        onOpenItem = onOpenItem,
                        onPreview = {},
                        onToggleFavorite = vm::toggleFavorite,
                        onRetry = { state.selectedId?.let(vm::select) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowseSidebar(
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    totalCount: Int,
    favCount: Int,
    recentCount: Int,
    categoryCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        if (recentCount > 0) {
            item(key = RECENTS_CAT_ID) {
                SideNavItem(
                    label = Ar.continueWatching,
                    selected = selectedId == RECENTS_CAT_ID,
                    onClick = { onSelect(RECENTS_CAT_ID) },
                    count = recentCount,
                    icon = Ic.Clock,
                    iconTint = Palette.Gold,
                )
            }
        }
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
            val q = qualityOf(cat.name)
            SideNavItem(
                label = cat.name,
                selected = cat.id == selectedId,
                onClick = { onSelect(cat.id) },
                count = if (count != null && count > 0) count else null,
                trailingText = if (q != null && (count == null || count == 0)) q else null,
            )
        }
    }
}

@Composable
private fun PosterGrid(
    series: Boolean,
    state: BrowseUiState,
    items: List<Poster>,
    recents: List<RecentItem>,
    onPlayRecent: (RecentItem) -> Unit,
    columnWidth: Dp,
    onOpenItem: (String) -> Unit,
    onPreview: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val headerTitle = if (series) Ar.seriesLibrary else Ar.moviesLibrary

    Column(modifier) {
        if (recents.isNotEmpty()) {
            BrowseRecentsStrip(
                title = if (series) "آخر ما تم مشاهدته في المسلسلات" else "آخر ما تم مشاهدته في الأفلام",
                recents = recents.take(10),
                onPlayRecent = onPlayRecent,
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(headerTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextDim)
                if (items.isNotEmpty()) {
                    Tag(Ar.itemsCount(items.size), Palette.Glow, fontSize = 10.sp)
                }
            }
            if (LocalIsTv.current) {
                Tag(Ar.dpadReady, Palette.Gold, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(4.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy && items.isEmpty() -> SkeletonGrid(columnWidth = columnWidth)
                state.error != null && items.isEmpty() -> MessageBox(state.error, actionLabel = Ar.retry, onAction = onRetry)
                items.isEmpty() -> MessageBox(if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noTitles)
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(columnWidth),
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(items, key = { it.id }) { poster ->
                        PosterCard(
                            title = poster.name,
                            imageUrl = poster.image,
                            badge = poster.rating,
                            quality = qualityOf(poster.name),
                            subtitle = poster.year,
                            onClick = { onOpenItem(poster.id) },
                            onLongClick = { onToggleFavorite(poster) },
                            onFocused = { onPreview(poster) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseRecentsStrip(
    title: String,
    recents: List<RecentItem>,
    onPlayRecent: (RecentItem) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DotTitle(title, Palette.Gold, fontSize = 14.sp)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(recents, key = { "browse_recent_${it.kind}_${it.id}" }) { item ->
                val progress = if (item.durationMs > 0) {
                    (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                } else null
                GlassCard(
                    onClick = { onPlayRecent(item) },
                    modifier = Modifier.width(200.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RemoteImage(
                                url = item.icon,
                                fallbackText = item.title,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(9.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Palette.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    item.subtitle.ifBlank { Ar.kindLabel(item.kind) },
                                    fontSize = 11.sp,
                                    color = Palette.Glow,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (progress != null) {
                            ProgressLine(progress, height = 3.dp)
                        }
                    }
                }
            }
        }
    }
}

private data class PreviewMeta(
    val backdrop: String = "",
    val plot: String = "",
    val genre: String = "",
    val year: String = "",
    val duration: String = "",
    val rating: String = "",
    val cast: String = "",
    val director: String = "",
    val firstPlayItem: PlayItem? = null,
)

@Composable
private fun TitlePreviewPanel(
    series: Boolean,
    poster: Poster?,
    isFavorite: Boolean,
    onOpenItem: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val repo = LocalContainer.current.repo
    val meta by produceState<PreviewMeta?>(initialValue = null, poster?.id, series) {
        value = null
        val p = poster ?: return@produceState
        delay(220) // Debounce rapid D-Pad navigation
        value = try {
            if (series) {
                val d = repo.seriesDetails(p.id)
                val firstEp = d.episodes.firstOrNull()
                PreviewMeta(
                    backdrop = d.backdrop.ifBlank { d.cover }.ifBlank { p.image },
                    plot = d.plot,
                    genre = d.genre,
                    year = d.year.ifBlank { p.year },
                    duration = if (d.seasons.isNotEmpty()) "${d.seasons.size} مواسم · ${d.episodes.size} حلقة" else "",
                    rating = d.rating.ifBlank { p.rating },
                    cast = d.cast,
                    firstPlayItem = firstEp?.let {
                        PlayItem(
                            kind = Kind.EPISODE,
                            id = it.id,
                            title = d.name,
                            subtitle = Ar.seasonEpisode(it.season, it.number),
                            icon = it.image.ifBlank { d.cover },
                            ext = it.ext,
                            groupId = d.id,
                        )
                    },
                )
            } else {
                val d = repo.movieDetails(p.id)
                PreviewMeta(
                    backdrop = d.backdrop.ifBlank { d.image }.ifBlank { p.image },
                    plot = d.plot,
                    genre = d.genre,
                    year = d.year.ifBlank { p.year },
                    duration = d.duration,
                    rating = d.rating.ifBlank { p.rating },
                    cast = d.cast,
                    director = d.director,
                    firstPlayItem = PlayItem(
                        kind = Kind.MOVIE,
                        id = d.id,
                        title = d.name,
                        icon = d.image,
                        ext = d.ext.ifBlank { p.ext },
                    ),
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        padding = 16.dp,
    ) {
        if (poster == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(Ar.pickTitle, fontSize = 14.sp, color = Palette.TextDim)
            }
        } else {
            val heroUrl = meta?.backdrop?.ifBlank { poster.image } ?: poster.image
            val rating = meta?.rating?.ifBlank { poster.rating } ?: poster.rating
            val year = meta?.year?.ifBlank { poster.year } ?: poster.year
            val duration = meta?.duration.orEmpty()
            val quality = qualityOf(poster.name) ?: "HD"

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenItem),
            ) {
                RemoteImage(
                    url = heroUrl,
                    fallbackText = poster.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent, Palette.DeepBlue.copy(alpha = 0.85f)),
                            ),
                        ),
                )
                Tag(
                    quality,
                    if (quality == "4K") Palette.Gold else Palette.Glow,
                    Modifier.align(Alignment.TopStart).padding(10.dp),
                )
                if (rating.isNotBlank()) {
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.70f))
                            .border(1.dp, Palette.Gold.copy(alpha = 0.4f), RoundedCornerShape(50))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Star, null, tint = Palette.Gold, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(rating, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Gold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                poster.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Tag(if (series) Ar.series else Ar.movies, Palette.Royal)
                if (year.isNotBlank()) {
                    Text(year, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Glow)
                }
                if (duration.isNotBlank()) {
                    Text("• $duration", fontSize = 12.sp, color = Palette.TextDim, maxLines = 1)
                }
            }

            val genre = meta?.genre.orEmpty()
            if (genre.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    genre.split(",", "،", "/").map { it.trim() }.filter { it.isNotBlank() }.take(3).forEach { g ->
                        Tag(g, Palette.Glow, fontSize = 10.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Quick Action Row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton(
                    text = Ar.play,
                    onClick = {
                        val playItem = meta?.firstPlayItem ?: if (!series) {
                            PlayItem(Kind.MOVIE, poster.id, poster.name, icon = poster.image, ext = poster.ext)
                        } else null
                        if (playItem != null) {
                            onPlay(PlayRequest(listOf(playItem), 0))
                        } else {
                            onOpenItem()
                        }
                    },
                    icon = Ic.Play,
                    primary = true,
                    modifier = Modifier.weight(1f),
                )
                GlassIconButton(
                    icon = Ic.Info,
                    description = Ar.details,
                    onClick = onOpenItem,
                    size = 46.dp,
                    tint = Palette.Glow,
                )
                GlassIconButton(
                    icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                    description = Ar.favorites,
                    onClick = onToggleFavorite,
                    size = 46.dp,
                    tint = if (isFavorite) Palette.Live else Palette.TextDim,
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val plot = meta?.plot.orEmpty()
                DotTitle(Ar.plot, Palette.Gold, fontSize = 13.sp)
                Text(
                    plot.ifBlank { "اضغط على العنوان لعرض كافة التفاصيل والقصة الكاملة والممثلين." },
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = Palette.TextDim,
                )
                val cast = meta?.cast.orEmpty()
                if (cast.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    DotTitle(Ar.cast, Palette.Glow, fontSize = 12.sp)
                    Text(cast, fontSize = 12.sp, color = Palette.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
