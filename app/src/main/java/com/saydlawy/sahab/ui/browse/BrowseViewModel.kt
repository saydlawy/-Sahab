package com.saydlawy.sahab.ui.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.Poster
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

internal const val RECENTS_CAT_ID = "__RECENTS__"
internal const val FAV_CAT_ID = "__FAVORITES__"
internal const val ALL_CAT_ID = "__ALL__"

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
    val featuredHeroItems: List<Poster> = emptyList(),
    val top10Items: List<Poster> = emptyList(),
    val categoryGroups: List<CategoryPosterGroup> = emptyList(),
    val focusedDetails: FocusedPosterDetails? = null,
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
    private var focusDetailJob: Job? = null
    private var searchPool: List<Poster>? = null
    private var indexedPool: List<Pair<Poster, String>>? = null

    private suspend fun buildLayoutCollections(
        categories: List<Category>,
        allItems: List<Poster>,
    ): Triple<List<Poster>, List<Poster>, List<CategoryPosterGroup>> = withContext(Dispatchers.Default) {
        val ranked = allItems.sortedWith(
            compareByDescending<Poster> { it.rating.toDoubleOrNull() ?: 0.0 }
                .thenByDescending { it.added },
        )
        val hero = ranked.filter { it.image.isNotBlank() }.take(6).ifEmpty { allItems.take(6) }
        val top10 = ranked.take(10)
        val byCat = allItems.groupBy { it.categoryId }
        val groups = categories.mapNotNull { cat ->
            val list = byCat[cat.id].orEmpty()
            if (list.isEmpty()) null else CategoryPosterGroup(cat, list.take(20))
        }.take(12)
        Triple(hero, top10, groups)
    }

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
                    val (hero, top10, groups) = buildLayoutCollections(categories, cachedAll)
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
                            featuredHeroItems = hero,
                            top10Items = top10,
                            categoryGroups = groups,
                            focusedDetails = it.focusedDetails ?: hero.firstOrNull()?.let { p ->
                                FocusedPosterDetails(poster = p, year = p.year, rating = p.rating, backdropUrl = p.image)
                            },
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
                        val (hero, top10, groups) = buildLayoutCollections(categories, allItems)
                        _state.update { current ->
                            current.copy(
                                totalCount = allItems.size,
                                categoryCounts = counts + current.categoryCounts,
                                items = if (current.selectedId == ALL_CAT_ID || current.items.isEmpty()) allItems else current.items,
                                loadingItems = if (current.selectedId == ALL_CAT_ID) false else current.loadingItems,
                                featuredHeroItems = hero,
                                top10Items = top10,
                                categoryGroups = groups,
                                focusedDetails = current.focusedDetails ?: hero.firstOrNull()?.let { p ->
                                    FocusedPosterDetails(poster = p, year = p.year, rating = p.rating, backdropUrl = p.image)
                                },
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

    fun onPosterFocused(poster: Poster) {
        if (_state.value.focusedDetails?.poster?.id == poster.id) return
        _state.update {
            it.copy(
                focusedDetails = FocusedPosterDetails(
                    poster = poster,
                    year = poster.year,
                    rating = poster.rating,
                    backdropUrl = poster.image,
                ),
            )
        }
        focusDetailJob?.cancel()
        focusDetailJob = viewModelScope.launch {
            delay(180L)
            val mediaType = if (series) "series" else "movie"
            val cached = withContext(Dispatchers.IO) {
                runCatching { container.catalogDb.movieDao().findByStreamIdAndType(poster.id, mediaType) }.getOrNull()
            }
            if (cached != null && cached.plot.isNotBlank()) {
                _state.update {
                    it.copy(
                        focusedDetails = FocusedPosterDetails(
                            poster = poster,
                            plot = cached.plot,
                            genre = cached.genre,
                            year = cached.year.ifBlank { poster.year },
                            rating = cached.rating.ifBlank { poster.rating },
                            runtime = cached.runtime,
                            backdropUrl = cached.backdropUrl.ifBlank { cached.posterUrl.ifBlank { poster.image } },
                        ),
                    )
                }
                return@launch
            }
            runCatching {
                if (series) {
                    val d = repo.seriesDetails(poster.id)
                    _state.update {
                        it.copy(
                            focusedDetails = FocusedPosterDetails(
                                poster = poster,
                                plot = d.plot,
                                genre = d.genre,
                                year = d.year.ifBlank { poster.year },
                                rating = d.rating.ifBlank { poster.rating },
                                backdropUrl = d.backdrop.ifBlank { d.cover.ifBlank { poster.image } },
                            ),
                        )
                    }
                } else {
                    val d = repo.movieDetails(poster.id, poster.name, poster.image, poster.ext)
                    _state.update {
                        it.copy(
                            focusedDetails = FocusedPosterDetails(
                                poster = poster,
                                plot = d.plot,
                                genre = d.genre,
                                year = d.year.ifBlank { poster.year },
                                rating = d.rating.ifBlank { poster.rating },
                                runtime = d.duration,
                                backdropUrl = d.backdrop.ifBlank { d.image.ifBlank { poster.image } },
                            ),
                        )
                    }
                }
            }
        }
    }

    fun toggleFavorite(poster: Poster) {
        val kind = if (series) Kind.SERIES else Kind.MOVIE
        viewModelScope.launch {
            container.prefs.toggleFavorite(
                FavItem(kind, poster.id, poster.name, poster.image, poster.ext, accountId = container.prefs.currentActiveAccountId())
            )
        }
    }
}
