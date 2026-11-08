package com.saydlawy.sahab.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.CategoryPane
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.ScreenHeader
import com.saydlawy.sahab.ui.components.SearchBox
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
import com.saydlawy.sahab.ui.components.SkeletonGrid

data class BrowseUiState(
    val categories: List<Category> = emptyList(),
    val selectedId: String? = null,
    val items: List<Poster> = emptyList(),
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
class BrowseViewModel(container: AppContainer, private val series: Boolean) : ViewModel() {

    private val repo = container.repo
    private val _state = MutableStateFlow(BrowseUiState())
    val state = _state.asStateFlow()

    private var itemsJob: Job? = null
    private var searchJob: Job? = null
    private var searchPool: List<Poster>? = null

    init {
        loadCategories()
    }

    private suspend fun fetchCategories() = if (series) repo.seriesCategories() else repo.movieCategories()

    private suspend fun fetchItems(categoryId: String?) =
        if (series) repo.seriesList(categoryId) else repo.movies(categoryId)

    fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCategories = true, error = null) }
            try {
                val categories = fetchCategories()
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
        itemsJob?.cancel()
        _state.update { it.copy(selectedId = categoryId, loadingItems = true, error = null) }
        itemsJob = viewModelScope.launch {
            try {
                val items = fetchItems(categoryId)
                _state.update { it.copy(items = items, loadingItems = false) }
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
                val pool = searchPool ?: fetchItems(null).also { searchPool = it }
                val results = withContext(Dispatchers.Default) {
                    pool.filter { it.name.normalizeForSearch().contains(needle) }.take(400)
                }
                _state.update { it.copy(searchResults = results, searching = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, searchResults = emptyList(), error = e.userMessage()) }
            }
        }
    }
}

@Composable
fun BrowseScreen(series: Boolean, onBack: () -> Unit, onOpenItem: (String) -> Unit) {
    val vm = appViewModel { BrowseViewModel(it, series) }
    val state by vm.state.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current
    val title = if (series) Ar.series else Ar.movies

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(title, onBack) {
                if (wide) {
                    SearchBox(state.query, vm::onQuery, Ar.searchTitles, Modifier.width(380.dp))
                }
            }
            if (!wide) {
                SearchBox(
                    state.query,
                    vm::onQuery,
                    Ar.searchTitles,
                    Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            val grid: @Composable (Modifier) -> Unit = { modifier ->
                PosterGrid(
                    state = state,
                    columnWidth = if (isTv) 170.dp else 128.dp,
                    onOpenItem = onOpenItem,
                    onRetry = { state.selectedId?.let(vm::select) },
                    modifier = modifier,
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
                    grid(Modifier.weight(1f))
                }
                else -> Column(Modifier.fillMaxSize()) {
                    CategoryPane(
                        categories = state.categories,
                        selectedId = state.selectedId,
                        onSelect = vm::select,
                        wide = false,
                    )
                    grid(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(
    state: BrowseUiState,
    columnWidth: androidx.compose.ui.unit.Dp,
    onOpenItem: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = state.visibleItems
    val gridState = rememberLazyGridState()
    Box(modifier.fillMaxSize()) {
        when {
            state.busy && items.isEmpty() -> SkeletonGrid(columnWidth = columnWidth)
            state.error != null && items.isEmpty() -> MessageBox(state.error, actionLabel = Ar.retry, onAction = onRetry)
            items.isEmpty() -> MessageBox(Ar.noTitles)
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(columnWidth),
                state = gridState,
                modifier = Modifier.fillMaxSize().focusRestorer(),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(items, key = { it.id }) { poster ->
                    PosterCard(
                        title = poster.name,
                        imageUrl = poster.image,
                        badge = poster.rating,
                        onClick = { onOpenItem(poster.id) },
                    )
                }
            }
        }
    }
}
