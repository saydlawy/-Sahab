package com.saydlawy.sahab.ui.browse

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.VodCustomGrouping
import com.saydlawy.sahab.data.VodOrganizationMode
import com.saydlawy.sahab.data.catalog.CatalogFacetRow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.catalog.TmdbMatcher
import com.saydlawy.sahab.util.RatingSanitizer
import com.saydlawy.sahab.util.normalizeForSearch
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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
    val organizationMode: VodOrganizationMode = VodOrganizationMode.DEFAULT,
    val customGrouping: VodCustomGrouping = VodCustomGrouping.GENRE,
) {
    val visibleItems: List<Poster> get() = searchResults ?: items
    val busy: Boolean get() = loadingItems || searching
}

@OptIn(FlowPreview::class)
class BrowseViewModel(
    private val container: AppContainer,
    private val series: Boolean,
    private val initialCategoryId: String? = null,
    private val initialQuery: String = "",
) : ViewModel() {

    private val repo = container.repo
    private val _state = MutableStateFlow(BrowseUiState(query = initialQuery))
    val state = _state.asStateFlow()

    private val _focusedDetails = MutableStateFlow<FocusedPosterDetails?>(null)
    val focusedDetails: StateFlow<FocusedPosterDetails?> = _focusedDetails.asStateFlow()

    private var itemsJob: Job? = null
    private var searchJob: Job? = null
    private var focusDetailJob: Job? = null
    private var bgEnrichJob: Job? = null
    private val bgEnrichAttempted: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private var searchPool: List<Poster>? = null
    private var indexedPool: List<Pair<Poster, String>>? = null
    private var defaultCategories: List<Category> = emptyList()
    private var defaultCounts: Map<String, Int> = emptyMap()
    private var customItemsByCategory: Map<String, List<Poster>> = emptyMap()
    private var cachedFacets: Map<String, CatalogFacetRow>? = null
    private var cachedFacetsByTitle: Map<String, CatalogFacetRow> = emptyMap()

    init {
        viewModelScope.launch {
            combine(
                container.prefs.vodOrganizationMode,
                container.prefs.vodCustomGrouping,
            ) { mode, grouping -> mode to grouping }
                .collect { (mode, grouping) ->
                    val prev = _state.value
                    val changed = prev.organizationMode != mode || prev.customGrouping != grouping
                    _state.update { it.copy(organizationMode = mode, customGrouping = grouping) }
                    if (changed && !searchPool.isNullOrEmpty()) {
                        applyOrganization(searchPool.orEmpty(), mode, grouping)
                    }
                }
        }
        viewModelScope.launch {
            val mediaType = if (series) "series" else "movie"
            container.catalogDb.movieDao().observeFacetMetadata(mediaType)
                .debounce(2_500L)
                .map { rows -> buildFacetMaps(rows) }
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default)
                .collectLatest { (newStreamMap, newTitleMap) ->
                    if (newStreamMap == cachedFacets && newTitleMap == cachedFacetsByTitle) return@collectLatest
                    cachedFacets = newStreamMap
                    cachedFacetsByTitle = newTitleMap
                    val currentPool = searchPool
                    if (!currentPool.isNullOrEmpty()) {
                        val refreshed = enrichWithTmdb(currentPool)
                        updateSearchPool(refreshed)
                        applyOrganization(refreshed)
                    }
                }
        }
    }

    fun setOrganizationMode(mode: VodOrganizationMode) {
        viewModelScope.launch { container.prefs.setVodOrganizationMode(mode) }
    }

    fun setCustomGrouping(grouping: VodCustomGrouping) {
        viewModelScope.launch {
            container.prefs.setVodOrganization(VodOrganizationMode.CUSTOM, grouping)
        }
    }

    private fun normalizeTitleKey(raw: String): String {
        if (raw.isBlank()) return ""
        return raw.lowercase(java.util.Locale.ROOT)
            .replace(TITLE_PREFIX_PIPE_REGEX, "")
            .replace(TITLE_BRACKETS_REGEX, " ")
            .replace(ARABIC_DIACRITICS_REGEX, "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ة', 'ه').replace('ى', 'ي')
            .replace(TITLE_NOISE_TOKENS_REGEX, " ")
            .replace(TITLE_NON_ALNUM_REGEX, " ")
            .trim()
    }

    private fun buildFacetMaps(rows: List<CatalogFacetRow>): Pair<Map<String, CatalogFacetRow>, Map<String, CatalogFacetRow>> {
        val byStream = HashMap<String, CatalogFacetRow>(rows.size)
        val byTitle = HashMap<String, CatalogFacetRow>(rows.size * 2)
        for (row in rows) {
            if (row.novaStreamId.isNotBlank()) {
                byStream[row.novaStreamId] = row
            }
            val hasUsefulMeta = row.genre.isNotBlank() || row.year.isNotBlank() || row.rating.isNotBlank()
            if (!hasUsefulMeta) continue
            for (rawTitle in listOf(row.titleAr, row.titleEn, row.identifiedTitle)) {
                val key = normalizeTitleKey(rawTitle)
                if (key.length >= 2 && !byTitle.containsKey(key)) {
                    byTitle[key] = row
                }
            }
        }
        return byStream to byTitle
    }

    private fun resolveFacet(
        poster: Poster,
        byStream: Map<String, CatalogFacetRow>,
        byTitle: Map<String, CatalogFacetRow>,
    ): CatalogFacetRow? {
        byStream[poster.id]?.let { return it }
        val key = normalizeTitleKey(poster.name)
        return if (key.length >= 2) byTitle[key] else null
    }

    private suspend fun getFacetsMap(): Map<String, CatalogFacetRow> {
        cachedFacets?.let { return it }
        val mediaType = if (series) "series" else "movie"
        val (byStream, byTitle) = withContext(Dispatchers.IO) {
            runCatching {
                buildFacetMaps(container.catalogDb.movieDao().getFacetMetadata(mediaType))
            }.getOrDefault(emptyMap<String, CatalogFacetRow>() to emptyMap())
        }
        cachedFacets = byStream
        cachedFacetsByTitle = byTitle
        return byStream
    }

    fun refreshFacets() {
        val currentPool = searchPool ?: return
        if (currentPool.isEmpty()) return
        viewModelScope.launch {
            cachedFacets = null
            cachedFacetsByTitle = emptyMap()
            val refreshed = enrichWithTmdb(currentPool)
            updateSearchPool(refreshed)
            applyOrganization(refreshed)
        }
    }

    fun refreshFacetsIfNeeded() {
        if (cachedFacets != null) return
        refreshFacets()
    }

    private suspend fun enrichWithTmdb(items: List<Poster>): List<Poster> {
        if (items.isEmpty()) return items
        val facets = getFacetsMap()
        val byTitle = cachedFacetsByTitle
        return withContext(Dispatchers.Default) {
            items.map { p ->
                val f = resolveFacet(p, facets, byTitle)
                val tmdbRating = RatingSanitizer.format(f?.rating, f?.tmdbVoteCount)
                val finalRating = tmdbRating.ifBlank { RatingSanitizer.format(p.rating) }
                val tmdbYear = VodCustomOrganizer.extractTmdbYear(f?.year)
                val tmdbGenre = VodCustomOrganizer.primaryGenre(f?.genre)
                p.copy(
                    rating = finalRating,
                    year = tmdbYear.ifBlank { p.year },
                    genre = tmdbGenre.ifBlank { p.genre },
                )
            }
        }
    }

    private suspend fun applyOrganization(
        allItems: List<Poster>,
        mode: VodOrganizationMode = _state.value.organizationMode,
        grouping: VodCustomGrouping = _state.value.customGrouping,
        preferredCat: String? = null,
    ) {
        if (mode == VodOrganizationMode.DEFAULT) {
            customItemsByCategory = emptyMap()
            val (hero, top10, groups) = buildLayoutCollections(defaultCategories, allItems)
            val currentSel = preferredCat ?: _state.value.selectedId
            val targetCat = if (currentSel == null || currentSel.startsWith(CUSTOM_CAT_PREFIX)) ALL_CAT_ID else currentSel
            val itemsForSel = when (targetCat) {
                ALL_CAT_ID, FAV_CAT_ID, RECENTS_CAT_ID -> allItems
                else -> withContext(Dispatchers.Default) {
                    allItems.filter { it.categoryId == targetCat }.ifEmpty { allItems }
                }
            }
            _state.update {
                it.copy(
                    categories = defaultCategories,
                    selectedId = targetCat,
                    items = itemsForSel,
                    categoryCounts = defaultCounts,
                    featuredHeroItems = hero,
                    top10Items = top10,
                    categoryGroups = groups,
                )
            }
            return
        }

        val facets = getFacetsMap()
        val byTitle = cachedFacetsByTitle
        val organized = withContext(Dispatchers.Default) {
            val resolvedFacets = HashMap<String, CatalogFacetRow>(allItems.size)
            for (p in allItems) {
                resolveFacet(p, facets, byTitle)?.let { resolvedFacets[p.id] = it }
            }
            VodCustomOrganizer.organize(allItems, resolvedFacets, grouping)
        }
        customItemsByCategory = organized.itemsByCategory
        val groups = organized.categories.mapNotNull { cat ->
            val list = organized.itemsByCategory[cat.id].orEmpty()
            if (list.isEmpty()) null else CategoryPosterGroup(cat, list.take(20))
        }.take(18)
        val currentSel = preferredCat ?: _state.value.selectedId
        val targetCat = when {
            currentSel == ALL_CAT_ID || currentSel == FAV_CAT_ID || currentSel == RECENTS_CAT_ID -> currentSel
            currentSel != null && organized.itemsByCategory.containsKey(currentSel) -> currentSel
            else -> ALL_CAT_ID
        }
        val itemsForSel = when (targetCat) {
            ALL_CAT_ID, FAV_CAT_ID, RECENTS_CAT_ID -> allItems
            else -> organized.itemsByCategory[targetCat].orEmpty()
        }
        _state.update {
            it.copy(
                categories = organized.categories,
                selectedId = targetCat,
                items = itemsForSel,
                categoryCounts = organized.counts,
                categoryGroups = groups,
            )
        }
    }

    private suspend fun buildLayoutCollections(
        categories: List<Category>,
        allItems: List<Poster>,
        facets: Map<String, CatalogFacetRow> = cachedFacets.orEmpty(),
    ): Triple<List<Poster>, List<Poster>, List<CategoryPosterGroup>> = withContext(Dispatchers.Default) {
        val ranked = allItems.sortedWith(
            compareByDescending<Poster> {
                RatingSanitizer.rankingScore(it.rating, facets[it.id]?.tmdbVoteCount)
            }.thenByDescending { it.added },
        )
        val hero = ranked.filter { it.image.isNotBlank() }.take(6).ifEmpty { allItems.take(6) }
        val top10 = ranked
            .filter { RatingSanitizer.parseValid(it.rating, facets[it.id]?.tmdbVoteCount) != null }
            .take(10)
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
        scheduleBackgroundEnrichment(list)
    }

    private fun scheduleBackgroundEnrichment(items: List<Poster>) {
        val missing = items.asSequence()
            .filter { it.id.isNotBlank() && (it.genre.isBlank() || it.year.isBlank() || it.rating.isBlank()) }
            .filter { it.id !in bgEnrichAttempted }
            .take(60)
            .toList()
        if (bgEnrichJob?.isActive == true) return
        if (missing.isEmpty()) return
        bgEnrichJob = viewModelScope.launch(Dispatchers.IO) {
            delay(2_500L)
            val mediaType = if (series) "series" else "movie"
            val dao = container.catalogDb.movieDao()
            val total = missing.size
            var done = 0
            var failed = 0
            var lastError = ""
            container.catalogSync.reportBackgroundEnrichProgress(0, total, active = true)
            try {
                for (poster in missing) {
                    if (!bgEnrichAttempted.add(poster.id)) continue
                    container.catalogSync.reportBackgroundEnrichProgress(
                        done = done,
                        total = total,
                        active = true,
                        currentTitle = poster.name,
                        failed = failed,
                        lastError = lastError,
                    )
                    runCatching {
                        if (dao.findByStreamId(poster.id, mediaType) == null) {
                            dao.upsert(
                                CatalogMovieEntity(
                                    sahabId = CatalogMovieEntity.idPending(poster.id),
                                    novaStreamId = poster.id,
                                    titleAr = poster.name,
                                    titleEn = poster.name,
                                    mediaType = mediaType,
                                    categoryId = poster.categoryId,
                                    posterUrl = poster.image,
                                    needsEnrichment = 1,
                                )
                            )
                        }
                        container.catalogSync.enrichOne(
                            repo = repo,
                            tmdb = container.tmdb,
                            streamId = poster.id,
                            force = false,
                            mediaType = mediaType,
                        )
                    }.onFailure { err ->
                        if (err is kotlinx.coroutines.CancellationException) throw err
                        failed += 1
                        lastError = err.message?.take(120) ?: err::class.java.simpleName
                        android.util.Log.w("SAHAB_ENRICH", "Failed [${poster.id}] ${poster.name}: $lastError", err)
                    }
                    done += 1
                    container.catalogSync.reportBackgroundEnrichProgress(
                        done = done,
                        total = total,
                        active = true,
                        currentTitle = poster.name,
                        failed = failed,
                        lastError = lastError,
                    )
                    delay(350L)
                }
            } finally {
                container.catalogSync.reportBackgroundEnrichProgress(
                    done = done,
                    total = total,
                    active = false,
                    failed = failed,
                    lastError = lastError,
                )
            }
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
                defaultCategories = categories
                val cachedAll = peekAllItems()
                val preferredCat = initialCategoryId ?: _state.value.selectedId

                if (!cachedAll.isNullOrEmpty()) {
                    val enrichedAll = enrichWithTmdb(cachedAll)
                    updateSearchPool(enrichedAll)
                    val counts = computeCounts(enrichedAll)
                    defaultCounts = counts
                    val (hero, top10, groups) = buildLayoutCollections(categories, enrichedAll)
                    val targetCat = preferredCat ?: ALL_CAT_ID
                    val initialItems = if (targetCat == ALL_CAT_ID || targetCat == FAV_CAT_ID || targetCat == RECENTS_CAT_ID) {
                        enrichedAll
                    } else {
                        withContext(Dispatchers.Default) {
                            enrichedAll.filter { it.categoryId == targetCat }.ifEmpty { enrichedAll }
                        }
                    }
                    _state.update {
                        it.copy(
                            categories = categories,
                            selectedId = targetCat,
                            items = initialItems,
                            loadingCategories = false,
                            loadingItems = false,
                            totalCount = enrichedAll.size,
                            categoryCounts = counts,
                            featuredHeroItems = hero,
                            top10Items = top10,
                            categoryGroups = groups,
                        )
                    }
                    if (_focusedDetails.value == null) {
                        hero.firstOrNull()?.let { p ->
                            _focusedDetails.value = FocusedPosterDetails(
                                poster = p,
                                year = p.year,
                                rating = cleanRating(p.rating),
                                backdropUrl = p.image,
                            )
                        }
                    }
                    if (_state.value.organizationMode == VodOrganizationMode.CUSTOM) {
                        applyOrganization(enrichedAll, preferredCat = preferredCat)
                    }
                    hero.firstOrNull()?.let { fetchDetailsForHero(it) }
                    if (initialQuery.isNotBlank()) {
                        onQuery(initialQuery)
                    }
                    return@launch
                }

                val firstCatId = preferredCat ?: categories.firstOrNull()?.id ?: ALL_CAT_ID
                val mediaType = if (series) "series" else "movie"
                val initialRoomPage = if (preferredCat == null || preferredCat == ALL_CAT_ID) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            container.catalogDb.movieDao().getPaged(mediaType = mediaType, limit = 120, offset = 0)
                                .map { it.toPoster() }
                        }.getOrDefault(emptyList())
                    }
                } else {
                    emptyList()
                }
                if (initialRoomPage.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            categories = categories,
                            selectedId = ALL_CAT_ID,
                            items = initialRoomPage,
                            loadingCategories = false,
                            loadingItems = false,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            categories = categories,
                            selectedId = firstCatId,
                            loadingCategories = false,
                        )
                    }
                    select(firstCatId)
                }

                viewModelScope.launch {
                    val rawAll = runCatching { fetchItems(null) }.getOrDefault(emptyList())
                    val allItems = enrichWithTmdb(rawAll)
                    if (allItems.isNotEmpty()) {
                        updateSearchPool(allItems)
                        val counts = computeCounts(allItems)
                        defaultCounts = counts
                        val (hero, top10, groups) = buildLayoutCollections(categories, allItems)
                        val hadDetails = _focusedDetails.value != null
                        _state.update { current ->
                            current.copy(
                                totalCount = allItems.size,
                                categoryCounts = counts + current.categoryCounts,
                                items = if (current.selectedId == ALL_CAT_ID || current.items.isEmpty()) allItems else current.items,
                                loadingItems = if (current.selectedId == ALL_CAT_ID) false else current.loadingItems,
                                featuredHeroItems = hero,
                                top10Items = top10,
                                categoryGroups = groups,
                            )
                        }
                        if (!hadDetails) {
                            hero.firstOrNull()?.let { p ->
                                _focusedDetails.value = FocusedPosterDetails(
                                    poster = p,
                                    year = p.year,
                                    rating = cleanRating(p.rating),
                                    backdropUrl = p.image,
                                )
                            }
                        }
                        if (_state.value.organizationMode == VodOrganizationMode.CUSTOM) {
                            applyOrganization(allItems)
                        }
                        if (!hadDetails) {
                            hero.firstOrNull()?.let { fetchDetailsForHero(it) }
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
                    loadingItems = pool.isNullOrEmpty() && it.items.isEmpty(),
                    error = null,
                )
            }
            if (pool.isNullOrEmpty()) {
                itemsJob = viewModelScope.launch {
                    val rawAll = runCatching { fetchItems(null) }.getOrDefault(emptyList())
                    val all = enrichWithTmdb(rawAll)
                    if (all.isNotEmpty()) {
                        updateSearchPool(all)
                        _state.update {
                            it.copy(
                                selectedId = FAV_CAT_ID,
                                items = all,
                                loadingItems = false,
                                totalCount = maxOf(it.totalCount, all.size),
                                error = null,
                            )
                        }
                    } else {
                        _state.update { it.copy(loadingItems = false) }
                    }
                }
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
        if (categoryId.startsWith(CUSTOM_CAT_PREFIX)) {
            val customList = customItemsByCategory[categoryId].orEmpty()
            _state.update {
                it.copy(
                    selectedId = categoryId,
                    items = customList,
                    loadingItems = false,
                    error = null,
                )
            }
            return
        }
        val cachedPool = searchPool
        if (!cachedPool.isNullOrEmpty()) {
            _state.update { it.copy(selectedId = categoryId, loadingItems = cachedPool.size > 400, error = null) }
            itemsJob = viewModelScope.launch {
                val filtered = if (cachedPool.size > 400) {
                    withContext(Dispatchers.Default) { cachedPool.filter { it.categoryId == categoryId } }
                } else {
                    cachedPool.filter { it.categoryId == categoryId }
                }
                if (filtered.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            selectedId = categoryId,
                            items = filtered,
                            loadingItems = false,
                            error = null,
                        )
                    }
                    return@launch
                }
                fetchCategoryItems(categoryId)
            }
            return
        }

        _state.update { it.copy(selectedId = categoryId, loadingItems = true, error = null) }
        itemsJob = viewModelScope.launch {
            fetchCategoryItems(categoryId)
        }
    }

    private suspend fun fetchCategoryItems(categoryId: String) {
        try {
                val targetId = if (categoryId == ALL_CAT_ID) null else categoryId
                val items = enrichWithTmdb(fetchItems(targetId))
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
                if (indexedPool == null && searchPool == null) {
                    val mediaType = if (series) "series" else "movie"
                    val roomMatches = withContext(Dispatchers.IO) {
                        runCatching {
                            container.catalogDb.movieDao()
                                .searchCatalogPaged(query.trim(), mediaType = mediaType, limit = 200, offset = 0)
                                .map { it.toPoster() }
                        }.getOrDefault(emptyList())
                    }
                    if (roomMatches.isNotEmpty()) {
                        _state.update { it.copy(searchResults = roomMatches, searching = false) }
                        return@launch
                    }
                }
                val indexed = indexedPool ?: run {
                    val pool = searchPool ?: enrichWithTmdb(fetchItems(null)).also { updateSearchPool(it) }
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
            val mediaType = if (series) "series" else "movie"
            val cached = withContext(Dispatchers.IO) {
                runCatching { container.catalogDb.movieDao().findByStreamIdAndType(poster.id, mediaType) }.getOrNull()
            }
            if (cached != null && cached.plot.isNotBlank()) {
                val cachedPosters = cached.postersJson
                    .lineSequence()
                    .map { line -> line.trim() }
                    .filter { line -> line.isNotEmpty() }
                    .toList()
                val resolvedTrailer = cached.trailerUrl
                _focusedDetails.value = FocusedPosterDetails(
                    poster = poster,
                    plot = cached.plot,
                    genre = cached.genre,
                    year = cached.year.ifBlank { poster.year },
                    rating = RatingSanitizer.format(cached.rating, cached.tmdbVoteCount)
                        .ifBlank { cleanRating(poster.rating) },
                    runtime = cached.runtime,
                    backdropUrl = cached.backdropUrl.ifBlank { cached.posterUrl.ifBlank { poster.image } },
                    trailerUrl = resolvedTrailer,
                    posterUrls = cachedPosters,
                )
                return@launch
            }

            // 2. البحث في كتالوج Firebase السحابي قبل استدعاء الشبكة/TMDB
            val cloudItem = withContext(Dispatchers.IO) {
                val lookupId = cached?.tmdbId?.takeIf { it > 0 }?.let { CatalogMovieEntity.idFromTmdb(it) }
                    ?: cached?.sahabId
                    ?: CatalogMovieEntity.idPending(poster.id)
                runCatching { container.firebaseCatalog.getCatalogItem(lookupId) }.getOrNull()
            }
            if (cloudItem != null && cloudItem.plot.isNotBlank()) {
                val cleanCloudRating = RatingSanitizer.format(cloudItem.rating, cloudItem.tmdbVoteCount)
                val cloudPosters = cloudItem.postersJson
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
                _focusedDetails.value = FocusedPosterDetails(
                    poster = poster,
                    plot = cloudItem.plot,
                    genre = cloudItem.genre,
                    year = cloudItem.year.ifBlank { poster.year },
                    rating = cleanCloudRating.ifBlank { cleanRating(poster.rating) },
                    runtime = cloudItem.runtime,
                    backdropUrl = cloudItem.backdropUrl.ifBlank { cloudItem.posterUrl.ifBlank { poster.image } },
                    trailerUrl = cloudItem.trailerUrl,
                    posterUrls = cloudPosters,
                )
                withContext(Dispatchers.IO) {
                    runCatching {
                        val dao = container.catalogDb.movieDao()
                        val now = System.currentTimeMillis()
                        if (cached != null) {
                            dao.applyEnrichment(
                                streamId = poster.id,
                                tmdbId = cloudItem.tmdbId,
                                titleEn = cloudItem.titleEn,
                                year = cloudItem.year,
                                plot = cloudItem.plot,
                                cast = cloudItem.cast,
                                director = cloudItem.director,
                                genre = cloudItem.genre,
                                runtime = cloudItem.runtime,
                                rating = cleanCloudRating,
                                backdropUrl = cloudItem.backdropUrl,
                                voteCount = cloudItem.tmdbVoteCount,
                                status = 0,
                                now = now,
                                mediaType = mediaType,
                                postersJson = cloudItem.postersJson,
                                trailerUrl = cloudItem.trailerUrl,
                                castJson = cloudItem.castJson,
                            )
                        } else {
                            dao.upsert(
                                cloudItem.copy(
                                    novaStreamId = poster.id,
                                    categoryId = cloudItem.categoryId.ifBlank { poster.categoryId },
                                    mediaType = mediaType,
                                    rating = cleanCloudRating,
                                    posterUrl = cloudItem.posterUrl.ifBlank { poster.image },
                                    needsEnrichment = 0,
                                    lastEnriched = now,
                                    lastSeen = now,
                                ),
                            )
                        }
                    }
                }
                return@launch
            }

            // 3. جلب التفاصيل من Xtream أولاً
            var xtreamPlot = ""
            var xtreamGenre = ""
            var xtreamYear = poster.year
            var xtreamRating = cleanRating(poster.rating)
            var xtreamRuntime = ""
            var xtreamBackdrop = ""
            var xtreamTrailer = ""

            runCatching {
                if (series) {
                    val d = repo.seriesDetails(poster.id)
                    xtreamPlot = d.plot
                    xtreamGenre = d.genre
                    xtreamYear = d.year.ifBlank { xtreamYear }
                    xtreamRating = cleanRating(d.rating).ifBlank { xtreamRating }
                    xtreamBackdrop = d.backdrop.ifBlank { d.cover }
                    xtreamTrailer = d.trailerUrl
                } else {
                    val d = repo.movieDetails(poster.id)
                    xtreamPlot = d.plot
                    xtreamGenre = d.genre
                    xtreamYear = d.year.ifBlank { xtreamYear }
                    xtreamRating = cleanRating(d.rating).ifBlank { xtreamRating }
                    xtreamRuntime = d.duration
                    xtreamBackdrop = d.backdrop.ifBlank { d.image }
                    xtreamTrailer = d.trailerUrl
                }
            }.onFailure { if (it is CancellationException) throw it }

            if (xtreamPlot.isNotBlank()) {
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
                return@launch
            }

            // 4. إثراء تلقائي عبر TMDB عند نقص القصة أو الخلفية (مهم جداً للمسلسلات)
            if (container.tmdb.hasApiKey()) {
                val catName = _state.value.categories.firstOrNull { it.id == poster.categoryId }?.name.orEmpty()
                val enriched = withContext(Dispatchers.IO) {
                    try {
                        TmdbMatcher.enrich(
                            tmdb = container.tmdb,
                            rawName = poster.name,
                            categoryName = catName,
                            novaYear = xtreamYear.toIntOrNull(),
                            mediaType = mediaType,
                            includeExtras = false,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        null
                    }
                }
                val tmdbItem = enriched?.movie
                if (tmdbItem != null && (enriched.level == TmdbMatcher.Level.STRONG || enriched.level == TmdbMatcher.Level.WEAK)) {
                    val resolvedRating = RatingSanitizer.format(tmdbItem.voteAverage, tmdbItem.voteCount)
                        .ifBlank { xtreamRating }
                    val resolvedBackdrop = tmdbItem.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                        ?.ifBlank { xtreamBackdrop.ifBlank { poster.image } }
                        ?: xtreamBackdrop.ifBlank { poster.image }
                    val resolvedGenre = tmdbItem.genres.joinToString(", ") { it.name }.ifBlank { xtreamGenre }
                    val resolvedYear = tmdbItem.releaseDate.take(4).ifBlank { xtreamYear }
                    val resolvedRuntime = tmdbItem.runtime?.let { "${it}m" }?.ifBlank { xtreamRuntime } ?: xtreamRuntime

                    _focusedDetails.value = FocusedPosterDetails(
                        poster = poster,
                        plot = tmdbItem.overview.ifBlank { xtreamPlot },
                        genre = resolvedGenre,
                        year = resolvedYear,
                        rating = resolvedRating,
                        runtime = resolvedRuntime,
                        backdropUrl = resolvedBackdrop,
                        trailerUrl = tmdbItem.trailerUrl.ifBlank { xtreamTrailer },
                        posterUrls = tmdbItem.posterUrls,
                    )
                    val resolvedCast = tmdbItem.credits?.cast?.take(5)?.joinToString(", ") { it.name }.orEmpty()
                    val resolvedDirector = tmdbItem.credits?.crew?.firstOrNull { it.job == "Director" }?.name.orEmpty()
                    val resolvedCastJson = com.saydlawy.sahab.data.catalog.encodeTmdbCastJson(tmdbItem.credits?.cast)
                    val isStrong = enriched.level == TmdbMatcher.Level.STRONG
                    val now = System.currentTimeMillis()
                    val heroEntity = CatalogMovieEntity(
                        sahabId = CatalogMovieEntity.idFromTmdb(tmdbItem.id),
                        novaStreamId = poster.id,
                        categoryId = poster.categoryId,
                        titleAr = poster.name,
                        titleEn = tmdbItem.title,
                        year = resolvedYear,
                        plot = tmdbItem.overview,
                        cast = resolvedCast,
                        castJson = resolvedCastJson,
                        director = resolvedDirector,
                        genre = resolvedGenre,
                        runtime = resolvedRuntime,
                        rating = resolvedRating,
                        posterUrl = poster.image,
                        backdropUrl = resolvedBackdrop,
                        tmdbId = tmdbItem.id,
                        tmdbVoteCount = tmdbItem.voteCount,
                        mediaType = mediaType,
                        postersJson = tmdbItem.posterUrls.joinToString("\n"),
                        trailerUrl = tmdbItem.trailerUrl,
                        needsEnrichment = if (isStrong && resolvedCast.isNotBlank()) 0 else 1,
                        lastEnriched = now,
                        lastSeen = now,
                    )
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val dao = container.catalogDb.movieDao()
                            if (cached != null) {
                                dao.applyEnrichment(
                                    streamId = poster.id,
                                    tmdbId = heroEntity.tmdbId,
                                    titleEn = heroEntity.titleEn,
                                    year = heroEntity.year,
                                    plot = heroEntity.plot,
                                    cast = heroEntity.cast,
                                    director = heroEntity.director,
                                    genre = heroEntity.genre,
                                    runtime = heroEntity.runtime,
                                    rating = heroEntity.rating,
                                    backdropUrl = heroEntity.backdropUrl,
                                    voteCount = heroEntity.tmdbVoteCount,
                                    status = heroEntity.needsEnrichment,
                                    now = now,
                                    mediaType = mediaType,
                                    postersJson = heroEntity.postersJson,
                                    trailerUrl = heroEntity.trailerUrl,
                                    castJson = heroEntity.castJson,
                                )
                            } else {
                                dao.upsert(heroEntity)
                            }
                        }
                    }
                    if (isStrong) {
                        withContext(Dispatchers.IO) {
                            runCatching { container.firebaseCatalog.saveCatalogItem(heroEntity) }
                        }
                    }
                    return@launch
                }
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

    suspend fun loadCatalogPage(categoryId: String?, limit: Int = 100, offset: Int = 0): List<Poster> =
        withContext(Dispatchers.IO) {
            val mediaType = if (series) "series" else "movie"
            val dao = container.catalogDb.movieDao()
            val entities = if (categoryId.isNullOrBlank() || categoryId == ALL_CAT_ID) {
                dao.getPaged(mediaType = mediaType, limit = limit, offset = offset)
            } else {
                dao.getPagedByCategory(catId = categoryId, mediaType = mediaType, limit = limit, offset = offset)
            }
            entities.map { it.toPoster() }
        }

    private fun CatalogMovieEntity.toPoster(): Poster = Poster(
        id = novaStreamId,
        name = titleAr.ifBlank { titleEn },
        image = posterUrl,
        rating = RatingSanitizer.format(rating, tmdbVoteCount),
        ext = containerExtension,
        year = VodCustomOrganizer.extractTmdbYear(year),
        genre = VodCustomOrganizer.primaryGenre(genre),
        categoryId = categoryId,
        added = novaAdded,
    )

    fun toggleFavorite(poster: Poster) {
        val kind = if (series) Kind.SERIES else Kind.MOVIE
        viewModelScope.launch {
            container.prefs.toggleFavorite(
                FavItem(kind, poster.id, poster.name, poster.image, poster.ext, accountId = container.prefs.currentActiveAccountId())
            )
        }
    }

    private companion object {
        val TITLE_PREFIX_PIPE_REGEX = Regex("""^[^|]{1,20}\|\s*""")
        val TITLE_BRACKETS_REGEX = Regex("""\[.*?]|\(.*?\)""")
        val ARABIC_DIACRITICS_REGEX = Regex("""[\u064B-\u065F\u0670\u0640]""")
        val TITLE_NOISE_TOKENS_REGEX = Regex(
            """\b(19\d\d|20\d\d|4k|uhd|fhd|hd|1080p|720p|bluray|web-dl|webrip|hdtv|vod|ar|en|sub|dub|s\d{1,2}|season\s*\d{1,2}|فيلم|مسلسل|انمي|كرتون|كارتون|الموسم|موسم|مدبلج|مترجم)\b""",
        )
        val TITLE_NON_ALNUM_REGEX = Regex("""[^\p{L}\p{N}]+""")
    }
}
