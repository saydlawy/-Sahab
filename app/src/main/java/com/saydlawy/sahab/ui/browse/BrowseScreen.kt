package com.saydlawy.sahab.ui.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
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
import com.saydlawy.sahab.ui.components.GlassCard
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
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyGridState
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
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

@Immutable
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

class BrowseViewModel(
    private val container: AppContainer,
    private val series: Boolean,
    private val initialCategoryId: String? = null,
    private val initialQuery: String = "",
) : ViewModel() {

    private val repo = container.repo
    private val _state = MutableStateFlow(BrowseUiState(query = initialQuery))
    val state = _state.asStateFlow()

    private var itemsJob: Job? = null
    private var searchJob: Job? = null
    private var searchPool: List<Poster>? = null
    private var indexedPool: List<Pair<Poster, String>>? = null

    private suspend fun updateSearchPool(list: List<Poster>) {
        searchPool = list
        indexedPool = withContext(Dispatchers.Default) {
            list.map { it to it.name.normalizeForSearch() }
        }
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
                val preferredCat = initialCategoryId ?: _state.value.selectedId

                if (!cachedAll.isNullOrEmpty()) {
                    updateSearchPool(cachedAll)
                    val counts = computeCounts(cachedAll)
                    val targetCat = preferredCat ?: ALL_CAT_ID
                    val initialItems = if (targetCat == ALL_CAT_ID || targetCat == FAV_CAT_ID || targetCat == RECENTS_CAT_ID) {
                        cachedAll
                    } else {
                        cachedAll.filter { it.categoryId == targetCat }.ifEmpty { cachedAll }
                    }
                    _state.update {
                        it.copy(
                            categories = categories,
                            selectedId = targetCat,
                            items = initialItems,
                            loadingCategories = false,
                            loadingItems = false,
                            totalCount = cachedAll.size,
                            categoryCounts = counts,
                        )
                    }
                    if (initialQuery.isNotBlank()) {
                        onQuery(initialQuery)
                    }
                    return@launch
                }

                val firstCatId = preferredCat ?: categories.firstOrNull()?.id ?: ALL_CAT_ID
                _state.update {
                    it.copy(
                        categories = categories,
                        selectedId = firstCatId,
                        loadingCategories = false,
                    )
                }
                select(firstCatId)

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
        val cachedPool = searchPool
        if (!cachedPool.isNullOrEmpty()) {
            val filtered = cachedPool.filter { it.categoryId == categoryId }
            if (filtered.isNotEmpty()) {
                _state.update {
                    it.copy(
                        selectedId = categoryId,
                        items = filtered,
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
            delay(250)
            _state.update { it.copy(searching = true) }
            try {
                val indexed = indexedPool ?: run {
                    val pool = searchPool ?: fetchItems(null).also { updateSearchPool(it) }
                    indexedPool ?: withContext(Dispatchers.Default) {
                        pool.map { it to it.name.normalizeForSearch() }
                    }
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
    val route = if (series) Routes.SERIES else Routes.MOVIES
    val focusNav = LocalFocusNavigation.current
    val savedCategory = remember(route) { focusNav.getSelectedCategory(route) }
    val savedQuery = remember(route) { focusNav.getSearchQuery(route) }

    val vm = appViewModel { BrowseViewModel(it, series, savedCategory, savedQuery) }
    val state by vm.state.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val kind = if (series) Kind.SERIES else Kind.MOVIE

    val favorites by LocalContainer.current.prefs.favorites.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents by LocalContainer.current.prefs.recents.collectAsStateWithLifecycle(initialValue = emptyList())
    val favoriteIds = remember(favorites, kind) { favorites.filter { it.kind == kind }.map { it.id }.toSet() }
    val screenRecents = remember(recents, series) {
        if (series) recents.filter { it.kind == Kind.SERIES || it.kind == Kind.EPISODE }
        else recents.filter { it.kind == Kind.MOVIE }
    }

    LaunchedEffect(state.selectedId) {
        state.selectedId?.let { focusNav.setSelectedCategory(route, it) }
    }
    LaunchedEffect(state.query) {
        focusNav.setSearchQuery(route, state.query)
    }

    val playRecent: (RecentItem) -> Unit = { recent ->
        focusNav.markNavigateFrom(route, "browse_recent_${recent.kind}_${recent.id}")
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

    val gridStorageKey = "${route}_grid_${state.selectedId ?: ALL_CAT_ID}"
    val gridState = rememberRestorableLazyGridState(gridStorageKey)
    val displayMode = LocalDisplayMode.current

    val handleOpenPoster: (Poster) -> Unit = { poster ->
        focusNav.markNavigateFrom(route, "poster_${poster.id}")
        onOpenItem(poster.id)
    }

    val handleSelectCategory: (String) -> Unit = { catId ->
        focusNav.setSelectedCategory(route, catId)
        vm.select(catId)
    }

    Box(Modifier.fillMaxSize()) {
        val showTopBar by remember(displayMode, gridState) {
            derivedStateOf {
                displayMode == DisplayMode.TABLET_OR_TV ||
                    !gridState.canScrollBackward ||
                    (gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset < 28)
            }
        }

        Column(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showTopBar,
                enter = expandVertically(tween(180)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
            ) {
                SahabTopBar(current = route, onBack = onBack)
            }

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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // Column 1 (Right in RTL): Compact Search + Categories sidebar
                    Column(
                        Modifier
                            .width(192.dp)
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
                            screenKey = route,
                            categories = state.categories,
                            selectedId = state.selectedId,
                            onSelect = handleSelectCategory,
                            totalCount = state.totalCount,
                            favCount = favoriteIds.size,
                            recentCount = screenRecents.size,
                            categoryCounts = state.categoryCounts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }

                    // Column 2 (Full Remaining Width): 6 Posters per Row on Large Screens / TV!
                    PosterGrid(
                        screenKey = route,
                        series = series,
                        state = state,
                        items = items,
                        gridState = gridState,
                        recents = if (state.query.isBlank() && state.selectedId != RECENTS_CAT_ID) screenRecents else emptyList(),
                        onPlayRecent = playRecent,
                        fixedColumns = 6,
                        columnWidth = if (isTv) 110.dp else 116.dp,
                        onOpenPoster = handleOpenPoster,
                        onToggleFavorite = vm::toggleFavorite,
                        onRetry = { state.selectedId?.let(vm::select) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                displayMode == DisplayMode.PHONE_LANDSCAPE -> Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Column(
                        Modifier
                            .width(158.dp)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SearchBox(
                            state.query,
                            vm::onQuery,
                            Ar.searchTitles,
                            Modifier.fillMaxWidth(),
                        )
                        BrowseSidebar(
                            screenKey = route,
                            categories = state.categories,
                            selectedId = state.selectedId,
                            onSelect = handleSelectCategory,
                            totalCount = state.totalCount,
                            favCount = favoriteIds.size,
                            recentCount = screenRecents.size,
                            categoryCounts = state.categoryCounts,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }

                    PosterGrid(
                        screenKey = route,
                        series = series,
                        state = state,
                        items = items,
                        gridState = gridState,
                        recents = if (state.query.isBlank() && state.selectedId == RECENTS_CAT_ID) screenRecents else emptyList(),
                        onPlayRecent = playRecent,
                        fixedColumns = 5,
                        columnWidth = 104.dp,
                        onOpenPoster = handleOpenPoster,
                        onToggleFavorite = vm::toggleFavorite,
                        onRetry = { state.selectedId?.let(vm::select) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
                else -> Column(Modifier.fillMaxSize()) {
                    val chipsState = rememberRestorableLazyListState("${route}_chips_row")
                    SearchBox(
                        state.query,
                        vm::onQuery,
                        Ar.searchTitles,
                        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    )
                    LazyRow(
                        state = chipsState,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (screenRecents.isNotEmpty()) {
                            item(key = RECENTS_CAT_ID) {
                                Chip(
                                    Ar.continueWatching,
                                    state.selectedId == RECENTS_CAT_ID,
                                    { handleSelectCategory(RECENTS_CAT_ID) },
                                    Modifier.restorableFocus(route, "cat_$RECENTS_CAT_ID"),
                                )
                            }
                        }
                        item(key = FAV_CAT_ID) {
                            Chip(
                                Ar.favorites,
                                state.selectedId == FAV_CAT_ID,
                                { handleSelectCategory(FAV_CAT_ID) },
                                Modifier.restorableFocus(route, "cat_$FAV_CAT_ID"),
                            )
                        }
                        item(key = ALL_CAT_ID) {
                            Chip(
                                Ar.all,
                                state.selectedId == ALL_CAT_ID,
                                { handleSelectCategory(ALL_CAT_ID) },
                                Modifier.restorableFocus(route, "cat_$ALL_CAT_ID"),
                            )
                        }
                        items(state.categories, key = { it.id }) { cat ->
                            Chip(
                                cat.name,
                                cat.id == state.selectedId,
                                { handleSelectCategory(cat.id) },
                                Modifier.restorableFocus(route, "cat_${cat.id}"),
                            )
                        }
                    }
                    PosterGrid(
                        screenKey = route,
                        series = series,
                        state = state,
                        items = items,
                        gridState = gridState,
                        recents = if (state.query.isBlank() && state.selectedId == RECENTS_CAT_ID) screenRecents else emptyList(),
                        onPlayRecent = playRecent,
                        fixedColumns = null,
                        columnWidth = 104.dp,
                        onOpenPoster = handleOpenPoster,
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
    screenKey: String,
    categories: List<Category>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    totalCount: Int,
    favCount: Int,
    recentCount: Int,
    categoryCounts: Map<String, Int>,
    modifier: Modifier = Modifier,
) {
    val listState = rememberRestorableLazyListState("${screenKey}_sidebar")
    LazyColumn(
        state = listState,
        modifier = modifier.focusRestorer(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
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
                    modifier = Modifier.restorableFocus(screenKey, "cat_$RECENTS_CAT_ID"),
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
                modifier = Modifier.restorableFocus(screenKey, "cat_$FAV_CAT_ID"),
            )
        }
        item(key = ALL_CAT_ID) {
            SideNavItem(
                label = Ar.all,
                selected = selectedId == ALL_CAT_ID || selectedId == null,
                onClick = { onSelect(ALL_CAT_ID) },
                count = totalCount.takeIf { it > 0 },
                modifier = Modifier.restorableFocus(screenKey, "cat_$ALL_CAT_ID"),
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
                modifier = Modifier.restorableFocus(screenKey, "cat_${cat.id}"),
            )
        }
    }
}

@Composable
private fun PosterGrid(
    screenKey: String,
    series: Boolean,
    state: BrowseUiState,
    items: List<Poster>,
    gridState: LazyGridState,
    recents: List<RecentItem>,
    onPlayRecent: (RecentItem) -> Unit,
    fixedColumns: Int?,
    columnWidth: Dp,
    onOpenPoster: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val headerTitle = if (series) Ar.seriesLibrary else Ar.moviesLibrary
    val gridCells = remember(fixedColumns, columnWidth) {
        if (fixedColumns != null) GridCells.Fixed(fixedColumns) else GridCells.Adaptive(columnWidth)
    }

    Column(modifier) {
        if (LocalDisplayMode.current == DisplayMode.TABLET_OR_TV) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(headerTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.TextDim)
                    if (items.isNotEmpty()) {
                        Tag(Ar.itemsCount(items.size), Palette.Glow, fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy && items.isEmpty() -> SkeletonGrid(columnWidth = columnWidth)
                state.error != null && items.isEmpty() -> MessageBox(state.error, actionLabel = Ar.retry, onAction = onRetry)
                items.isEmpty() && recents.isEmpty() -> MessageBox(if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noTitles)
                else -> LazyVerticalGrid(
                    columns = gridCells,
                    state = gridState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (recents.isNotEmpty()) {
                        item(key = "browse_recents_header", span = { GridItemSpan(maxLineSpan) }) {
                            BrowseRecentsStrip(
                                screenKey = screenKey,
                                title = if (series) "آخر ما تم مشاهدته في المسلسلات" else "آخر ما تم مشاهدته في الأفلام",
                                recents = recents.take(10),
                                onPlayRecent = onPlayRecent,
                            )
                        }
                    }
                    itemsIndexed(items, key = { _, p -> p.id }) { index, poster ->
                        val itemKey = "poster_${poster.id}"
                        PosterCard(
                            title = poster.name,
                            imageUrl = poster.image,
                            badge = poster.rating,
                            quality = qualityOf(poster.name),
                            subtitle = poster.year,
                            onClick = { onOpenPoster(poster) },
                            onLongClick = { onToggleFavorite(poster) },
                            modifier = Modifier.restorableFocus(
                                screenKey = screenKey,
                                itemKey = itemKey,
                                isDefaultEntry = index == 0,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseRecentsStrip(
    screenKey: String,
    title: String,
    recents: List<RecentItem>,
    onPlayRecent: (RecentItem) -> Unit,
) {
    val rowState = rememberRestorableLazyListState("${screenKey}_recents_strip")
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DotTitle(title, Palette.Gold, fontSize = 14.sp)
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(recents, key = { "browse_recent_${it.kind}_${it.id}" }) { item ->
                val itemKey = "browse_recent_${item.kind}_${item.id}"
                val progress = if (item.durationMs > 0) {
                    (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f)
                } else null
                GlassCard(
                    onClick = { onPlayRecent(item) },
                    modifier = Modifier
                        .width(200.dp)
                        .restorableFocus(screenKey, itemKey),
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
