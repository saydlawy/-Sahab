package com.saydlawy.sahab.ui.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.VodLayoutStyle
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.forAccount
import com.saydlawy.sahab.util.RatingSanitizer
import com.saydlawy.sahab.util.normalizeForSearch
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
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
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val searching: Boolean = false,
    val error: String? = null,
    val featuredHeroItems: List<Poster> = emptyList(),
    val categoryGroups: List<CategoryPosterGroup> = emptyList(),
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
    private val dao = container.catalogDb.movieDao()
    private val mediaType = if (series) "series" else "movie"

    private val _state = MutableStateFlow(BrowseUiState(query = initialQuery))
    val state = _state.asStateFlow()

    private val _focusedDetails = MutableStateFlow<FocusedPosterDetails?>(null)
    val focusedDetails: StateFlow<FocusedPosterDetails?> = _focusedDetails.asStateFlow()

    private var itemsJob: Job? = null
    private var loadMoreJob: Job? = null
    private var searchJob: Job? = null
    private var focusDetailJob: Job? = null
    private var groupsJob: Job? = null

    private var defaultCategories: List<Category> = emptyList()
    private var providerAllCache: List<Poster> = emptyList()
    private var nextCursorOrder: Int? = null
    private var nextCursorSahabId: String? = null
    private var fallbackOffset: Int = 0
    private var searchOffset: Int = 0
    private var currentLayoutStyle: VodLayoutStyle = VodLayoutStyle.CLASSIC

    init {
        viewModelScope.launch {
            val styleFlow = if (series) container.prefs.seriesLayoutStyle else container.prefs.moviesLayoutStyle
            styleFlow.distinctUntilChanged().collectLatest { style ->
                currentLayoutStyle = style
                if (style != VodLayoutStyle.CLASSIC && defaultCategories.isNotEmpty() && _state.value.categoryGroups.isEmpty()) {
                    loadCategoryGroupsAsync(defaultCategories)
                }
            }
        }
        viewModelScope.launch {
            container.catalogSync.syncVersion
                .distinctUntilChanged()
                .collectLatest { version ->
                    if (version <= 0L || _state.value.loadingCategories) return@collectLatest
                    refreshCountsAndCurrentViewAfterSync()
                }
        }
        loadCategories()
    }

    private suspend fun fetchCategories(): List<Category> =
        if (series) repo.seriesCategories() else repo.movieCategories()

    private suspend fun fetchProviderItems(categoryId: String?): List<Poster> =
        if (series) repo.seriesList(categoryId) else repo.movies(categoryId)

    private suspend fun ensureProviderAllLoaded(): List<Poster> {
        if (providerAllCache.isNotEmpty()) return providerAllCache
        val peeked = runCatching {
            if (series) repo.peekAllSeries() else repo.peekAllMovies()
        }.getOrNull().orEmpty()
        if (peeked.isNotEmpty()) {
            providerAllCache = peeked
            return peeked
        }
        val fetched = runCatching { fetchProviderItems(null) }.getOrDefault(emptyList())
        if (fetched.isNotEmpty()) {
            providerAllCache = fetched
        }
        return providerAllCache
    }

    private suspend fun computeMergedCategoryCounts(roomCounts: Map<String, Int>, providerAll: List<Poster>): Map<String, Int> {
        if (providerAll.isEmpty()) return roomCounts
        return withContext(Dispatchers.Default) {
            val providerCounts = HashMap<String, Int>()
            for (p in providerAll) {
                if (p.categoryId.isNotBlank()) {
                    providerCounts[p.categoryId] = (providerCounts[p.categoryId] ?: 0) + 1
                }
            }
            val merged = HashMap<String, Int>(maxOf(roomCounts.size, providerCounts.size))
            merged.putAll(roomCounts)
            for ((catId, pCnt) in providerCounts) {
                val rCnt = merged[catId] ?: 0
                if (pCnt > rCnt) merged[catId] = pCnt
            }
            merged
        }
    }

    private suspend fun refreshCountsAndCurrentViewAfterSync() {
        val roomTotal = withContext(Dispatchers.IO) {
            runCatching { dao.countByType(mediaType) }.getOrDefault(0)
        }
        if (roomTotal <= 0) return
        val roomCounts = withContext(Dispatchers.IO) {
            runCatching { dao.getCategoryCounts(mediaType).associate { it.categoryId to it.cnt } }.getOrDefault(emptyMap())
        }
        val mergedCounts = computeMergedCategoryCounts(roomCounts, providerAllCache)
        val effectiveTotal = maxOf(roomTotal, providerAllCache.size)

        val current = _state.value
        val selectedCat = current.selectedId ?: ALL_CAT_ID
        val needsReloadFirstPage = current.items.isEmpty() ||
            (selectedCat == ALL_CAT_ID && current.items.size < PAGE_SIZE && effectiveTotal > current.items.size) ||
            (selectedCat != ALL_CAT_ID && selectedCat != FAV_CAT_ID && selectedCat != RECENTS_CAT_ID &&
                current.items.size < (mergedCounts[selectedCat] ?: 0).coerceAtMost(PAGE_SIZE))

        if (needsReloadFirstPage && !current.loadingItems && !current.loadingMore) {
            nextCursorOrder = null
            nextCursorSahabId = null
            fallbackOffset = 0
            val queryCat = selectedCat.takeIf { it != ALL_CAT_ID && it != FAV_CAT_ID && it != RECENTS_CAT_ID }
            val (page, hasMore) = loadPageWithProviderFallback(
                categoryId = queryCat,
                limit = PAGE_SIZE,
                expectedTotal = if (queryCat == null) effectiveTotal else (mergedCounts[queryCat] ?: 0),
            )
            _state.update {
                it.copy(
                    totalCount = effectiveTotal,
                    categoryCounts = mergedCounts,
                    items = if (page.isNotEmpty()) page else it.items,
                    hasMore = hasMore,
                )
            }
        } else {
            _state.update {
                it.copy(
                    totalCount = effectiveTotal,
                    categoryCounts = mergedCounts,
                )
            }
        }

        if (currentLayoutStyle != VodLayoutStyle.CLASSIC && defaultCategories.isNotEmpty()) {
            loadCategoryGroupsAsync(defaultCategories)
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            _state.update { it.copy(loadingCategories = true, error = null) }
            try {
                val styleFlow = if (series) container.prefs.seriesLayoutStyle else container.prefs.moviesLayoutStyle
                currentLayoutStyle = runCatching { styleFlow.first() }.getOrDefault(VodLayoutStyle.CLASSIC)

                var categoriesError: Exception? = null
                val categories = try {
                    fetchCategories()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    categoriesError = e
                    emptyList()
                }
                defaultCategories = categories

                val providerAll = ensureProviderAllLoaded()

                var roomTotal = withContext(Dispatchers.IO) {
                    runCatching { dao.countByType(mediaType) }.getOrDefault(0)
                }
                val missingMeta = withContext(Dispatchers.IO) {
                    runCatching { dao.countMissingProviderOrderOrSearch(mediaType) }.getOrDefault(0)
                }

                if (roomTotal == 0) {
                    runCatching {
                        if (series) container.catalogSync.syncSeries(repo) else container.catalogSync.syncMovies(repo)
                    }
                    roomTotal = withContext(Dispatchers.IO) {
                        runCatching { dao.countByType(mediaType) }.getOrDefault(0)
                    }
                } else if ((providerAll.isNotEmpty() && roomTotal < providerAll.size) || missingMeta > 0) {
                    container.appScope.launch(Dispatchers.IO) {
                        runCatching {
                            if (series) container.catalogSync.syncSeries(repo) else container.catalogSync.syncMovies(repo)
                        }
                    }
                }

                val effectiveTotal = maxOf(roomTotal, providerAll.size)
                if (categories.isEmpty() && effectiveTotal == 0 && categoriesError != null) {
                    throw categoriesError
                }

                val roomCounts = withContext(Dispatchers.IO) {
                    runCatching { dao.getCategoryCounts(mediaType).associate { it.categoryId to it.cnt } }.getOrDefault(emptyMap())
                }
                val mergedCounts = computeMergedCategoryCounts(roomCounts, providerAll)

                val rawPref = initialCategoryId ?: _state.value.selectedId
                val selectedCat = when {
                    rawPref == ALL_CAT_ID || rawPref == FAV_CAT_ID || rawPref == RECENTS_CAT_ID -> rawPref
                    rawPref != null && categories.any { it.id == rawPref } -> rawPref
                    else -> ALL_CAT_ID
                }

                nextCursorOrder = null
                nextCursorSahabId = null
                fallbackOffset = 0

                val (initialItems, initialHasMore) = when (selectedCat) {
                    RECENTS_CAT_ID -> emptyList<Poster>() to false
                    FAV_CAT_ID -> loadFavoritesFromRoom() to false
                    ALL_CAT_ID -> loadPageWithProviderFallback(
                        categoryId = null,
                        limit = PAGE_SIZE,
                        expectedTotal = effectiveTotal,
                    )
                    else -> loadPageWithProviderFallback(
                        categoryId = selectedCat,
                        limit = PAGE_SIZE,
                        expectedTotal = mergedCounts[selectedCat] ?: 0,
                    )
                }

                val heroCandidates = if (selectedCat == ALL_CAT_ID && initialItems.isNotEmpty()) {
                    initialItems.filter { it.image.isNotBlank() }.take(6).ifEmpty { initialItems.take(6) }
                } else {
                    withContext(Dispatchers.IO) {
                        runCatching { dao.getPaged(mediaType = mediaType, limit = 6, offset = 0).map { it.toPoster() } }
                            .getOrDefault(emptyList())
                            .ifEmpty { providerAll.take(6) }
                    }
                }

                _state.update {
                    it.copy(
                        categories = categories,
                        selectedId = selectedCat,
                        items = initialItems,
                        totalCount = effectiveTotal,
                        categoryCounts = mergedCounts,
                        featuredHeroItems = heroCandidates,
                        loadingCategories = false,
                        loadingItems = false,
                        hasMore = initialHasMore,
                    )
                }

                val heroFirst = heroCandidates.firstOrNull() ?: initialItems.firstOrNull()
                if (_focusedDetails.value == null && heroFirst != null) {
                    _focusedDetails.value = FocusedPosterDetails(
                        poster = heroFirst,
                        year = heroFirst.year,
                        rating = cleanRating(heroFirst.rating),
                        backdropUrl = heroFirst.image,
                    )
                    fetchDetailsForHero(heroFirst)
                }

                if (currentLayoutStyle != VodLayoutStyle.CLASSIC && categories.isNotEmpty()) {
                    loadCategoryGroupsAsync(categories)
                }

                if (initialQuery.isNotBlank()) {
                    onQuery(initialQuery)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingCategories = false, error = e.userMessage()) }
            }
        }
    }

    private fun loadCategoryGroupsAsync(categories: List<Category>) {
        groupsJob?.cancel()
        groupsJob = viewModelScope.launch {
            val groups = buildRoomCategoryGroups(categories)
            _state.update { it.copy(categoryGroups = groups) }
        }
    }

    private suspend fun buildRoomCategoryGroups(
        cats: List<Category>,
        maxGroups: Int = Int.MAX_VALUE,
        itemsPerGroup: Int = 60,
    ): List<CategoryPosterGroup> = withContext(Dispatchers.IO) {
        val providerByCat: Map<String, List<Poster>> by lazy {
            providerAllCache.groupBy { it.categoryId }
        }
        val result = ArrayList<CategoryPosterGroup>(cats.size)
        for (cat in cats) {
            if (result.size >= maxGroups) break
            val roomList = runCatching {
                dao.getPagedByCategory(catId = cat.id, mediaType = mediaType, limit = itemsPerGroup, offset = 0)
                    .map { it.toPoster() }
            }.getOrDefault(emptyList())
            val fallbackList = if (roomList.size < itemsPerGroup && providerAllCache.isNotEmpty()) {
                providerByCat[cat.id].orEmpty().take(itemsPerGroup)
            } else {
                emptyList()
            }
            val list = if (fallbackList.size > roomList.size) fallbackList else roomList
            if (list.isNotEmpty()) {
                result.add(CategoryPosterGroup(cat, list))
            }
        }
        result
    }

    fun select(categoryId: String) {
        itemsJob?.cancel()
        loadMoreJob?.cancel()
        nextCursorOrder = null
        nextCursorSahabId = null
        fallbackOffset = 0

        if (categoryId == RECENTS_CAT_ID) {
            _state.update {
                it.copy(
                    selectedId = RECENTS_CAT_ID,
                    loadingItems = false,
                    loadingMore = false,
                    hasMore = false,
                    error = null,
                )
            }
            return
        }

        if (categoryId == FAV_CAT_ID) {
            _state.update {
                it.copy(
                    selectedId = FAV_CAT_ID,
                    loadingItems = true,
                    loadingMore = false,
                    hasMore = false,
                    error = null,
                )
            }
            itemsJob = viewModelScope.launch {
                val favItems = loadFavoritesFromRoom()
                _state.update {
                    it.copy(
                        selectedId = FAV_CAT_ID,
                        items = favItems,
                        loadingItems = false,
                        hasMore = false,
                        error = null,
                    )
                }
            }
            return
        }

        _state.update {
            it.copy(
                selectedId = categoryId,
                loadingItems = true,
                loadingMore = false,
                hasMore = true,
                error = null,
            )
        }
        itemsJob = viewModelScope.launch {
            val queryCat = categoryId.takeIf { it != ALL_CAT_ID }
            val current = _state.value
            val expectedTotal = if (queryCat == null) current.totalCount else (current.categoryCounts[queryCat] ?: 0)
            val (page, hasMore) = loadPageWithProviderFallback(
                categoryId = queryCat,
                limit = PAGE_SIZE,
                expectedTotal = expectedTotal,
            )
            _state.update {
                it.copy(
                    selectedId = categoryId,
                    items = page,
                    loadingItems = false,
                    hasMore = hasMore,
                    error = null,
                )
            }
        }
    }

    private suspend fun loadFavoritesFromRoom(): List<Poster> = withContext(Dispatchers.IO) {
        val kind = if (series) Kind.SERIES else Kind.MOVIE
        val activeAcc = container.prefs.currentActiveAccountId()
        val favs = container.prefs.favorites.first()
            .forAccount(activeAcc)
            .filter { it.kind == kind && it.id.isNotBlank() }
        if (favs.isEmpty()) return@withContext emptyList()

        val roomByStream = HashMap<String, CatalogMovieEntity>(favs.size)
        favs.map { it.id }.distinct().chunked(500).forEach { chunk ->
            runCatching { dao.findByStreamIds(chunk, mediaType) }.getOrNull().orEmpty().forEach { row ->
                roomByStream[row.novaStreamId] = row
            }
        }
        favs.map { fav ->
            roomByStream[fav.id]?.toPoster() ?: Poster(
                id = fav.id,
                name = fav.name,
                image = fav.icon,
                ext = fav.ext,
            )
        }.distinctBy { it.id }
    }

    private suspend fun loadPageWithProviderFallback(
        categoryId: String?,
        limit: Int = PAGE_SIZE,
        expectedTotal: Int = 0,
    ): Pair<List<Poster>, Boolean> {
        val roomPage = if (fallbackOffset == 0) {
            loadRoomPageAndAdvanceCursor(categoryId = categoryId, limit = limit)
        } else {
            emptyList()
        }
        val expectedInFirstPage = if (expectedTotal > 0) minOf(limit, expectedTotal) else limit
        if (roomPage.size >= expectedInFirstPage && fallbackOffset == 0) {
            return roomPage to (roomPage.size >= limit)
        }

        // Room is either empty or still mid-sync for this category: serve directly from Repository provider list
        val providerAll = ensureProviderAllLoaded()
        val fullSource = if (categoryId.isNullOrBlank()) {
            providerAll
        } else {
            val fromAll = providerAll.filter { it.categoryId == categoryId }
            if (fromAll.isNotEmpty()) {
                fromAll
            } else {
                runCatching { fetchProviderItems(categoryId) }.getOrDefault(emptyList())
            }
        }
        if (fullSource.isEmpty() || fullSource.size <= roomPage.size) {
            return roomPage to (roomPage.size >= limit)
        }
        val start = fallbackOffset.coerceAtMost(fullSource.size)
        val end = (start + limit).coerceAtMost(fullSource.size)
        val slice = fullSource.subList(start, end)
        fallbackOffset = end
        return slice to (end < fullSource.size)
    }

    private suspend fun loadRoomPageAndAdvanceCursor(
        categoryId: String?,
        limit: Int = PAGE_SIZE,
    ): List<Poster> = withContext(Dispatchers.IO) {
        val cursorOrder = nextCursorOrder
        val cursorSahabId = nextCursorSahabId
        val entities = runCatching {
            if (cursorOrder != null && cursorSahabId != null) {
                if (categoryId.isNullOrBlank()) {
                    dao.getPagedAfter(
                        mediaType = mediaType,
                        lastOrder = cursorOrder,
                        lastSahabId = cursorSahabId,
                        limit = limit,
                    )
                } else {
                    dao.getPagedByCategoryAfter(
                        catId = categoryId,
                        mediaType = mediaType,
                        lastOrder = cursorOrder,
                        lastSahabId = cursorSahabId,
                        limit = limit,
                    )
                }
            } else {
                if (categoryId.isNullOrBlank()) {
                    dao.getPaged(mediaType = mediaType, limit = limit, offset = 0)
                } else {
                    dao.getPagedByCategory(catId = categoryId, mediaType = mediaType, limit = limit, offset = 0)
                }
            }
        }.getOrDefault(emptyList())

        entities.lastOrNull()?.let { last ->
            nextCursorOrder = last.providerOrder
            nextCursorSahabId = last.sahabId
        }
        entities.map { it.toPoster() }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loadingItems || current.loadingMore || !current.hasMore) return
        if (loadMoreJob?.isActive == true) return

        if (current.query.isNotBlank() && current.searchResults != null) {
            val rawTrim = current.query.trim()
            val needle = rawTrim.normalizeForSearch().trim()
            if (needle.length < 2) return
            _state.update { it.copy(loadingMore = true) }
            loadMoreJob = viewModelScope.launch {
                val nextMatches = withContext(Dispatchers.IO) {
                    runCatching {
                        dao.searchCatalogPaged(
                            query = rawTrim,
                            normalizedQuery = needle,
                            mediaType = mediaType,
                            limit = SEARCH_LIMIT,
                            offset = searchOffset,
                        ).map { it.toPoster() }
                    }.getOrDefault(emptyList())
                }
                searchOffset += nextMatches.size
                _state.update { s ->
                    val existing = s.searchResults.orEmpty()
                    val existingIds = existing.asSequence().map { it.id }.toHashSet()
                    val uniqueNew = nextMatches.filter { it.id !in existingIds }
                    s.copy(
                        searchResults = if (uniqueNew.isEmpty()) existing else existing + uniqueNew,
                        loadingMore = false,
                        hasMore = nextMatches.size >= SEARCH_LIMIT && uniqueNew.isNotEmpty(),
                    )
                }
            }
            return
        }

        val catId = current.selectedId ?: ALL_CAT_ID
        if (catId == RECENTS_CAT_ID || catId == FAV_CAT_ID) return

        _state.update { it.copy(loadingMore = true) }
        loadMoreJob = viewModelScope.launch {
            val queryCat = catId.takeIf { it != ALL_CAT_ID }
            val expectedTotal = if (queryCat == null) current.totalCount else (current.categoryCounts[queryCat] ?: 0)
            if (fallbackOffset == 0 && current.items.isNotEmpty() && nextCursorOrder == null) {
                fallbackOffset = current.items.size
            }
            val (nextPage, pageHasMore) = loadPageWithProviderFallback(
                categoryId = queryCat,
                limit = PAGE_SIZE,
                expectedTotal = expectedTotal,
            )

            _state.update { s ->
                if (s.selectedId != catId) return@update s.copy(loadingMore = false)
                val existingIds = s.items.asSequence().map { it.id }.toHashSet()
                val uniqueNew = nextPage.filter { it.id !in existingIds }
                val updatedItems = if (uniqueNew.isEmpty()) s.items else s.items + uniqueNew
                val moreRemains = (pageHasMore && uniqueNew.isNotEmpty()) ||
                    (expectedTotal > updatedItems.size && nextPage.isNotEmpty())
                s.copy(
                    items = updatedItems,
                    loadingMore = false,
                    hasMore = moreRemains,
                )
            }
        }
    }

    fun onQuery(query: String) {
        _state.update { it.copy(query = query) }
        searchJob?.cancel()
        val rawTrim = query.trim()
        val needle = rawTrim.normalizeForSearch().trim()
        if (needle.length < 2) {
            searchOffset = 0
            _state.update { it.copy(searchResults = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250)
            _state.update { it.copy(searching = true) }
            try {
                val roomMatches = withContext(Dispatchers.IO) {
                    runCatching {
                        dao.searchCatalogPaged(
                            query = rawTrim,
                            normalizedQuery = needle,
                            mediaType = mediaType,
                            limit = SEARCH_LIMIT,
                            offset = 0,
                        ).map { it.toPoster() }
                    }.getOrDefault(emptyList())
                }
                val finalMatches = if (roomMatches.isNotEmpty()) {
                    roomMatches
                } else {
                    val providerAll = ensureProviderAllLoaded()
                    withContext(Dispatchers.Default) {
                        providerAll.filter { p ->
                            p.name.contains(rawTrim, ignoreCase = true) ||
                                p.name.normalizeForSearch().contains(needle)
                        }.take(SEARCH_LIMIT)
                    }
                }
                searchOffset = finalMatches.size
                _state.update {
                    it.copy(
                        searchResults = finalMatches,
                        searching = false,
                        hasMore = finalMatches.size >= SEARCH_LIMIT,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(searching = false, searchResults = emptyList(), error = e.userMessage()) }
            }
        }
    }

    private fun cleanRating(raw: String): String = RatingSanitizer.format(raw)

    fun onPosterFocused(poster: Poster) {
        if (_focusedDetails.value?.poster?.id == poster.id) return
        _focusedDetails.value = FocusedPosterDetails(
            poster = poster,
            year = poster.year,
            rating = cleanRating(poster.rating),
            backdropUrl = poster.image,
        )
        fetchDetailsForHero(poster)
    }

    private fun fetchDetailsForHero(poster: Poster) {
        focusDetailJob?.cancel()
        focusDetailJob = viewModelScope.launch {
            delay(180L)
            val cached = withContext(Dispatchers.IO) {
                runCatching { dao.findByStreamIdAndType(poster.id, mediaType) }.getOrNull()
            }
            if (cached != null && cached.plot.isNotBlank()) {
                _focusedDetails.value = cached.toFocusedDetails(poster)
                return@launch
            }

            if (cached != null && cached.needsEnrichment == 1) {
                runCatching {
                    container.catalogSync.enrichOne(
                        repo = repo,
                        tmdb = container.tmdb,
                        streamId = poster.id,
                        force = false,
                        mediaType = mediaType,
                    )
                }
                val enriched = withContext(Dispatchers.IO) {
                    runCatching { dao.findByStreamIdAndType(poster.id, mediaType) }.getOrNull()
                }
                if (enriched != null && enriched.plot.isNotBlank()) {
                    _focusedDetails.value = enriched.toFocusedDetails(poster)
                    return@launch
                }
            }

            var xtreamPlot = cached?.plot.orEmpty()
            var xtreamGenre = cached?.genre.orEmpty()
            var xtreamYear = cached?.year?.ifBlank { poster.year } ?: poster.year
            var xtreamRating = cleanRating(cached?.rating.orEmpty()).ifBlank { cleanRating(poster.rating) }
            var xtreamRuntime = cached?.runtime.orEmpty()
            var xtreamBackdrop = cached?.backdropUrl.orEmpty()
            var xtreamTrailer = cached?.trailerUrl.orEmpty()

            if (xtreamPlot.isBlank()) {
                runCatching {
                    if (series) {
                        val d = repo.seriesDetails(poster.id)
                        xtreamPlot = d.plot
                        xtreamGenre = d.genre.ifBlank { xtreamGenre }
                        xtreamYear = d.year.ifBlank { xtreamYear }
                        xtreamRating = cleanRating(d.rating).ifBlank { xtreamRating }
                        xtreamBackdrop = d.backdrop.ifBlank { d.cover }.ifBlank { xtreamBackdrop }
                        xtreamTrailer = d.trailerUrl.ifBlank { xtreamTrailer }
                    } else {
                        val d = repo.movieDetails(poster.id)
                        xtreamPlot = d.plot
                        xtreamGenre = d.genre.ifBlank { xtreamGenre }
                        xtreamYear = d.year.ifBlank { xtreamYear }
                        xtreamRating = cleanRating(d.rating).ifBlank { xtreamRating }
                        xtreamRuntime = d.duration.ifBlank { xtreamRuntime }
                        xtreamBackdrop = d.backdrop.ifBlank { d.image }.ifBlank { xtreamBackdrop }
                        xtreamTrailer = d.trailerUrl.ifBlank { xtreamTrailer }
                    }
                }.onFailure { if (it is CancellationException) throw it }
            }

            _focusedDetails.value = FocusedPosterDetails(
                poster = poster,
                plot = xtreamPlot,
                genre = xtreamGenre,
                year = xtreamYear,
                rating = xtreamRating,
                runtime = xtreamRuntime,
                backdropUrl = xtreamBackdrop.ifBlank { poster.image },
                trailerUrl = xtreamTrailer,
            )
        }
    }

    private fun CatalogMovieEntity.toFocusedDetails(poster: Poster): FocusedPosterDetails {
        val cachedPosters = postersJson
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .toList()
        return FocusedPosterDetails(
            poster = poster,
            plot = plot,
            genre = genre,
            year = year.ifBlank { poster.year },
            rating = RatingSanitizer.format(rating, tmdbVoteCount).ifBlank { cleanRating(poster.rating) },
            runtime = runtime,
            backdropUrl = backdropUrl.ifBlank { posterUrl.ifBlank { poster.image } },
            trailerUrl = trailerUrl,
            posterUrls = cachedPosters,
        )
    }

    private fun CatalogMovieEntity.toPoster(): Poster = Poster(
        id = novaStreamId,
        name = titleAr.ifBlank { titleEn },
        image = posterUrl,
        rating = RatingSanitizer.format(rating, tmdbVoteCount),
        ext = containerExtension,
        year = RatingSanitizer.extractFourDigitYear(year),
        genre = RatingSanitizer.extractPrimaryGenre(genre),
        categoryId = categoryId,
        added = novaAdded,
    )

    fun toggleFavorite(poster: Poster) {
        val kind = if (series) Kind.SERIES else Kind.MOVIE
        viewModelScope.launch {
            container.prefs.toggleFavorite(
                FavItem(kind, poster.id, poster.name, poster.image, poster.ext, accountId = container.prefs.currentActiveAccountId()),
            )
        }
    }

    private companion object {
        const val PAGE_SIZE = 120
        const val SEARCH_LIMIT = 500
    }
}
