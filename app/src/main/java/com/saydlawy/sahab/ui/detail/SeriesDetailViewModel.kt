package com.saydlawy.sahab.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.SeriesDetails
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.catalog.TmdbMatcher
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class SeriesDetailViewModel(private val container: AppContainer, private val id: String) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState<SeriesDetails>())
    val state = _state.asStateFlow()

    private val _refreshingMetadata = MutableStateFlow(false)
    val refreshingMetadata = _refreshingMetadata.asStateFlow()

    init {
        load()
    }

    fun load() {
        fetchDetails(forceRefresh = false)
    }

    fun refreshMetadata() {
        if (_refreshingMetadata.value) return
        fetchDetails(forceRefresh = true)
    }

    private fun cleanRating(raw: String): String {
        val v = raw.trim()
        if (v.isBlank() || v == "0" || v == "0.0" || v == "0.00") return ""
        return v
    }

    private fun fetchDetails(forceRefresh: Boolean) {
        viewModelScope.launch {
            if (forceRefresh) {
                _refreshingMetadata.value = true
            } else {
                _state.update { DetailUiState(loading = true) }
            }
            try {
                var details = container.repo.seriesDetails(id)
                details = details.copy(rating = cleanRating(details.rating))

                val needsMeta = forceRefresh ||
                    details.plot.isBlank() ||
                    details.genre.isBlank() ||
                    details.year.isBlank() ||
                    details.rating.isBlank() ||
                    details.cast.isBlank() ||
                    details.backdrop.isBlank()

                var mergedMeta: CatalogMovieEntity? = withContext(Dispatchers.IO) {
                    runCatching {
                        container.catalogDb.movieDao().findByStreamIdAndType(id, "series")
                    }.getOrNull()
                }

                if (needsMeta) {
                    // 1. البحث في Firebase السحابي بجميع صيغ المعرّفات (مطابقة مع بانر الهيرو)
                    if (forceRefresh || mergedMeta == null || mergedMeta.plot.isBlank() || mergedMeta.backdropUrl.isBlank()) {
                        val cloudCandidate = withContext(Dispatchers.IO) {
                            val lookupIds = buildList {
                                mergedMeta?.tmdbId?.takeIf { it > 0 }?.let { add(CatalogMovieEntity.idFromTmdb(it)) }
                                mergedMeta?.sahabId?.takeIf { it.isNotBlank() }?.let { add(it) }
                                add(CatalogMovieEntity.idPending(id))
                                add("series_pending:$id")
                            }.distinct()

                            var found: CatalogMovieEntity? = null
                            for (lid in lookupIds) {
                                val remote = runCatching { container.firebaseCatalog.getCatalogItem(lid) }.getOrNull()
                                if (remote != null && (remote.plot.isNotBlank() || remote.backdropUrl.isNotBlank() || remote.genre.isNotBlank())) {
                                    found = remote.copy(novaStreamId = id, mediaType = "series")
                                    break
                                }
                            }
                            if (found != null) {
                                runCatching { container.catalogDb.movieDao().upsert(found) }
                            }
                            found
                        }
                        if (cloudCandidate != null) {
                            mergedMeta = cloudCandidate
                        }
                    }

                    // 2. إثراء مباشر من TMDB عند نقص القصة أو الخلفية أو التحديث الإجباري
                    if (container.tmdb.hasApiKey() &&
                        (forceRefresh || mergedMeta == null || mergedMeta.plot.isBlank() || mergedMeta.backdropUrl.isBlank())
                    ) {
                        val cachedPoster = withContext(Dispatchers.IO) {
                            runCatching { container.repo.peekAllSeries()?.firstOrNull { it.id == id } }.getOrNull()
                        }
                        val catName = withContext(Dispatchers.IO) {
                            val cid = cachedPoster?.categoryId.orEmpty()
                            if (cid.isBlank()) "" else {
                                runCatching { container.repo.seriesCategories().firstOrNull { it.id == cid }?.name.orEmpty() }.getOrDefault("")
                            }
                        }
                        val rawTitle = details.name.ifBlank { cachedPoster?.name.orEmpty() }
                        val novaYear = details.year.ifBlank { cachedPoster?.year.orEmpty() }.toIntOrNull()

                        val enriched = withContext(Dispatchers.IO) {
                            runCatching {
                                TmdbMatcher.enrich(
                                    tmdb = container.tmdb,
                                    rawName = rawTitle,
                                    categoryName = catName,
                                    novaYear = novaYear,
                                    mediaType = "series",
                                )
                            }.getOrNull()
                        }
                        val tv = enriched?.movie
                        if (tv != null && (enriched.level == TmdbMatcher.Level.STRONG || enriched.level == TmdbMatcher.Level.WEAK)) {
                            val now = System.currentTimeMillis()
                            val resolvedRating = if (tv.voteAverage > 0) {
                                String.format(Locale.US, "%.1f", tv.voteAverage)
                            } else {
                                details.rating
                            }
                            val resolvedBackdrop = tv.backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" }
                                ?.ifBlank { details.backdrop.ifBlank { details.cover } }
                                ?: details.backdrop.ifBlank { details.cover }
                            val resolvedGenre = tv.genres.joinToString(", ") { it.name }.ifBlank { details.genre }
                            val resolvedYear = tv.releaseDate.take(4).ifBlank { details.year }
                            val resolvedCast = tv.credits?.cast?.take(6)?.joinToString(", ") { it.name }
                                ?.ifBlank { details.cast } ?: details.cast

                            val entity = CatalogMovieEntity(
                                sahabId = CatalogMovieEntity.idFromTmdb(tv.id),
                                novaStreamId = id,
                                categoryId = cachedPoster?.categoryId.orEmpty(),
                                mediaType = "series",
                                titleAr = rawTitle,
                                titleEn = tv.title,
                                year = resolvedYear,
                                plot = tv.overview.ifBlank { details.plot },
                                cast = resolvedCast,
                                castJson = com.saydlawy.sahab.data.catalog.encodeTmdbCastJson(tv.credits?.cast),
                                genre = resolvedGenre,
                                rating = resolvedRating,
                                posterUrl = details.cover.ifBlank { cachedPoster?.image.orEmpty() },
                                backdropUrl = resolvedBackdrop,
                                tmdbId = tv.id,
                                tmdbVoteCount = tv.voteCount,
                                postersJson = tv.posterUrls.joinToString("\n"),
                                trailerUrl = tv.trailerUrl.ifBlank { details.trailerUrl },
                                needsEnrichment = 0,
                                lastEnriched = now,
                                lastSeen = now,
                            )
                            withContext(Dispatchers.IO) {
                                runCatching { container.catalogDb.movieDao().upsert(entity) }
                            }
                            container.appScope.launch(Dispatchers.IO) {
                                runCatching { container.firebaseCatalog.saveCatalogItem(entity) }
                            }
                            mergedMeta = entity
                        }
                    }
                }

                if (mergedMeta != null) {
                    val posters = mergedMeta.postersJson
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                    val resolvedTrailer = mergedMeta.trailerUrl.ifBlank {
                        val tid = mergedMeta.tmdbId
                        if (tid != null && container.tmdb.hasApiKey()) {
                            withContext(Dispatchers.IO) {
                                runCatching { container.tmdb.getTvDetailsById(tid)?.trailerUrl }.getOrNull().orEmpty()
                            }
                        } else {
                            details.trailerUrl
                        }
                    }
                    details = details.copy(
                        backdrop = details.backdrop.ifBlank { mergedMeta.backdropUrl },
                        plot = details.plot.ifBlank { mergedMeta.plot },
                        cast = details.cast.ifBlank { mergedMeta.cast },
                        castMembers = com.saydlawy.sahab.data.catalog.parseCastMembersJson(mergedMeta.castJson)
                            .ifEmpty { details.castMembers },
                        genre = details.genre.ifBlank { mergedMeta.genre },
                        year = details.year.ifBlank { mergedMeta.year },
                        rating = cleanRating(details.rating).ifBlank { cleanRating(mergedMeta.rating) },
                        posterUrls = if (posters.isNotEmpty()) posters else details.posterUrls,
                        trailerUrl = resolvedTrailer.ifBlank { details.trailerUrl },
                    )
                }

                _state.update { DetailUiState(loading = false, data = details) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!forceRefresh) {
                    _state.update { DetailUiState(loading = false, error = e.userMessage()) }
                }
            } finally {
                _refreshingMetadata.value = false
            }
        }
    }

    fun toggleFavorite(details: SeriesDetails) {
        viewModelScope.launch {
            container.prefs.toggleFavorite(
                FavItem(Kind.SERIES, details.id, details.name, details.cover, accountId = container.prefs.currentActiveAccountId())
            )
        }
    }

    private val _aiState = MutableStateFlow<com.saydlawy.sahab.data.ai.AiGenerationState>(
        com.saydlawy.sahab.data.ai.AiGenerationState.Idle,
    )
    val aiState = _aiState.asStateFlow()

    fun generateAiStory() {
        viewModelScope.launch {
            _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.Loading
            val details = _state.value.data
            if (details == null) {
                _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.Error("لا توجد بيانات معروضة")
                return@launch
            }
            val result = generateStoryWithKeyRotation(
                container = container,
                rawName = details.name,
                year = details.year,
                imdbId = "",
                posterUrl = details.cover,
                contentType = com.saydlawy.sahab.data.ai.ContentType.SERIES,
            )
            if (result == null) {
                _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.NoKey
                return@launch
            }
            when (result) {
                is com.saydlawy.sahab.data.ai.GeminiResult.Success -> {
                    val now = System.currentTimeMillis()
                    val existing = withContext(Dispatchers.IO) {
                        runCatching { container.catalogDb.movieDao().findByStreamIdAndType(id, "series") }.getOrNull()
                    }
                    val entity = (existing ?: CatalogMovieEntity(
                        sahabId = CatalogMovieEntity.idPending(id),
                        novaStreamId = id,
                        mediaType = "series",
                        titleAr = details.name,
                        year = details.year,
                        posterUrl = details.cover,
                        backdropUrl = details.backdrop,
                        cast = details.cast,
                        genre = details.genre,
                        rating = details.rating,
                    )).copy(
                        plot = result.story,
                        plotSource = "gemini_user_key",
                        plotConfidence = result.confidence,
                        identifiedTitle = result.identifiedTitle,
                        needsEnrichment = 0,
                        lastEnriched = now,
                        lastSeen = now,
                    )
                    withContext(Dispatchers.IO) {
                        runCatching { container.catalogDb.movieDao().upsert(entity) }
                    }
                    container.appScope.launch(Dispatchers.IO) {
                        runCatching { container.firebaseCatalog.saveCatalogItem(entity) }
                    }
                    _state.update { st ->
                        val d = st.data
                        if (d != null) st.copy(data = d.copy(plot = result.story)) else st
                    }
                    _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.Success(result.story, result.sources)
                }
                is com.saydlawy.sahab.data.ai.GeminiResult.RateLimited -> {
                    _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.RateLimited
                }
                is com.saydlawy.sahab.data.ai.GeminiResult.InvalidKey -> {
                    _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.InvalidKey
                }
                is com.saydlawy.sahab.data.ai.GeminiResult.Error -> {
                    _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.Error(result.message)
                }
            }
        }
    }
}
