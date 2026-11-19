package com.saydlawy.sahab.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.SeriesDetails
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesDetailViewModel(private val container: AppContainer, private val id: String) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState<SeriesDetails>())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        refreshMetadata()
    }

    fun refreshMetadata() {
        viewModelScope.launch {
            _state.update { DetailUiState(loading = true) }
            try {
                var details = container.repo.seriesDetails(id)
                val needsMeta = details.plot.isBlank() ||
                    details.genre.isBlank() ||
                    details.year.isBlank() ||
                    details.rating.isBlank() ||
                    details.cast.isBlank() ||
                    details.backdrop.isBlank()

                if (needsMeta) {
                    val mergedMeta = withContext(Dispatchers.IO) {
                        var candidate = runCatching {
                            container.catalogDb.movieDao().findByStreamIdAndType(id, "series")
                        }.getOrNull()

                        if (candidate == null || candidate.plot.isBlank()) {
                            val remote = runCatching {
                                container.firebaseCatalog.getCatalogItem("series_pending:$id")
                            }.getOrNull()
                            if (remote != null && (remote.plot.isNotBlank() || remote.genre.isNotBlank() || remote.year.isNotBlank())) {
                                val normalized = remote.copy(novaStreamId = id, mediaType = "series")
                                runCatching { container.catalogDb.movieDao().upsert(normalized) }
                                candidate = normalized
                            }
                        }

                        if (candidate == null || candidate.plot.isBlank()) {
                            runCatching {
                                container.catalogSync.enrichOne(
                                    repo = container.repo,
                                    tmdb = container.tmdb,
                                    streamId = id,
                                    mediaType = "series",
                                )
                                container.catalogDb.movieDao().findByStreamIdAndType(id, "series")
                            }.getOrNull()?.let { enriched ->
                                candidate = enriched
                            }
                        }
                        candidate
                    }

                    if (mergedMeta != null) {
                        details = details.copy(
                            backdrop = details.backdrop.ifBlank { mergedMeta.backdropUrl },
                            plot = details.plot.ifBlank { mergedMeta.plot },
                            cast = details.cast.ifBlank { mergedMeta.cast },
                            genre = details.genre.ifBlank { mergedMeta.genre },
                            year = details.year.ifBlank { mergedMeta.year },
                            rating = details.rating.ifBlank { mergedMeta.rating },
                        )
                    }
                }
                val catalogRecord = withContext(Dispatchers.IO) {
                    runCatching { container.catalogDb.movieDao().findByStreamIdAndType(id, "series") }.getOrNull()
                }
                if (catalogRecord != null) {
                    val posters = catalogRecord.postersJson
                        .lineSequence()
                        .map { line -> line.trim() }
                        .filter { line -> line.isNotEmpty() }
                        .toList()
                    val resolvedTrailer = if (catalogRecord.trailerUrl.isNotBlank()) {
                        catalogRecord.trailerUrl
                    } else {
                        val tid = catalogRecord.tmdbId
                        if (tid != null && container.tmdb.hasApiKey()) {
                            withContext(Dispatchers.IO) {
                                runCatching { container.tmdb.getTvDetailsById(tid)?.trailerUrl }.getOrNull().orEmpty()
                            }
                        } else {
                            ""
                        }
                    }
                    details = details.copy(
                        backdrop = details.backdrop.ifBlank { catalogRecord.backdropUrl },
                        posterUrls = posters,
                        trailerUrl = resolvedTrailer,
                    )
                }
                _state.update { DetailUiState(loading = false, data = details) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { DetailUiState(loading = false, error = e.userMessage()) }
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

    /** O-02: Generate series story using Gemini key rotation and persist to global_catalog. */
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
                    val entity = com.saydlawy.sahab.data.catalog.CatalogMovieEntity(
                        sahabId = "series_pending:$id",
                        novaStreamId = id,
                        mediaType = "series",
                        titleAr = details.name,
                        year = details.year,
                        plot = result.story,
                        plotSource = "gemini_user_key",
                        plotConfidence = result.confidence,
                        identifiedTitle = result.identifiedTitle,
                        posterUrl = details.cover,
                        backdropUrl = details.backdrop,
                        cast = details.cast,
                        genre = details.genre,
                        rating = details.rating,
                        needsEnrichment = 0,
                        lastEnriched = now,
                        lastSeen = now,
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        runCatching { container.catalogDb.movieDao().upsert(entity) }
                    }
                    container.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        container.firebaseCatalog.saveCatalogItem(entity)
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
