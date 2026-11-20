package com.saydlawy.sahab.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.MovieDetails
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailUiState<T>(
    val loading: Boolean = true,
    val data: T? = null,
    val error: String? = null,
)

class MovieDetailViewModel(private val container: AppContainer, private val id: String) : ViewModel() {

    private val _state = MutableStateFlow(DetailUiState<MovieDetails>())
    val state = _state.asStateFlow()

    private val _aiState = MutableStateFlow<com.saydlawy.sahab.data.ai.AiGenerationState>(com.saydlawy.sahab.data.ai.AiGenerationState.Idle)
    val aiState = _aiState.asStateFlow()

    private val _refreshingMetadata = MutableStateFlow(false)
    val refreshingMetadata = _refreshingMetadata.asStateFlow()

    fun generateAiStory() {
        viewModelScope.launch {
            _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.Loading
            val details = _state.value.data
            if (details == null) {
                _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.Error("لا توجد بيانات معروضة")
                return@launch
            }
            val entity = withContext(Dispatchers.IO) {
                runCatching { container.catalogDb.movieDao().findByStreamId(id) }.getOrNull()
            }
            val imdbId = entity?.imdbId ?: ""
            val result = generateStoryWithKeyRotation(
                container = container,
                rawName = details.name,
                year = details.year,
                imdbId = imdbId,
                posterUrl = details.image,
                contentType = com.saydlawy.sahab.data.ai.ContentType.MOVIE,
            )
            if (result == null) {
                _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.NoKey
                return@launch
            }
            when (result) {
                is com.saydlawy.sahab.data.ai.GeminiResult.Success -> {
                    val now = System.currentTimeMillis()
                    if (entity != null) {
                        withContext(Dispatchers.IO) {
                            container.catalogDb.movieDao().updateAiPlot(
                                sahabId = entity.sahabId,
                                plot = result.story,
                                source = "gemini_user_key",
                                confidence = result.confidence,
                                identifiedTitle = result.identifiedTitle,
                                now = now,
                            )
                        }
                    } else {
                        val newEntity = CatalogMovieEntity(
                            sahabId = "pending:$id",
                            novaStreamId = id,
                            titleAr = details.name,
                            year = details.year,
                            plot = result.story,
                            plotSource = "gemini_user_key",
                            plotConfidence = result.confidence,
                            identifiedTitle = result.identifiedTitle,
                            posterUrl = details.image,
                            backdropUrl = details.backdrop,
                            cast = details.cast,
                            director = details.director,
                            genre = details.genre,
                            runtime = details.duration,
                            rating = details.rating,
                            containerExtension = details.ext,
                            needsEnrichment = 0,
                            lastEnriched = now,
                            lastSeen = now,
                        )
                        withContext(Dispatchers.IO) {
                            runCatching { container.catalogDb.movieDao().upsert(newEntity) }
                        }
                    }
                    val updated = withContext(Dispatchers.IO) {
                        runCatching { container.catalogDb.movieDao().findByStreamId(id) }.getOrNull()
                    }
                    if (updated != null) {
                        // رفع القصة الجديدة إلى السحابة (Firebase) ليستفيد منها باقي المستخدمين
                        container.appScope.launch(Dispatchers.IO) {
                            container.firebaseCatalog.saveCatalogItem(updated)
                        }
                        _state.update { DetailUiState(loading = false, data = updated.toMovieDetails()) }
                    } else {
                        _state.update { st ->
                            val d = st.data
                            if (d != null) st.copy(data = d.copy(plot = result.story)) else st
                        }
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

    fun dismissAiDialog() {
        _aiState.value = com.saydlawy.sahab.data.ai.AiGenerationState.Idle
    }

    init {
        load()
    }

    private fun CatalogMovieEntity.toMovieDetails(): MovieDetails {
        val posters = postersJson
            .lineSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotEmpty() }
            .toList()
        return MovieDetails(
            id = novaStreamId,
            name = titleAr.ifBlank { titleEn },
            image = posterUrl,
            backdrop = backdropUrl.ifBlank { posterUrl },
            plot = plot,
            cast = cast,
            castMembers = com.saydlawy.sahab.data.catalog.parseCastMembersJson(castJson),
            director = director,
            genre = genre,
            year = year,
            duration = runtime,
            rating = rating,
            ext = containerExtension,
            posterUrls = posters,
            trailerUrl = trailerUrl,
        )
    }

    fun load() {
        viewModelScope.launch {
            // 1. Instant: read from Room
            val fromRoom = withContext(Dispatchers.IO) {
                runCatching { container.catalogDb.movieDao().findByStreamId(id) }.getOrNull()
            }

            if (fromRoom != null) {
                var currentDetails = fromRoom.toMovieDetails()
                _state.update { DetailUiState(loading = false, data = currentDetails) }

                // 2. Background enrichment if needed or if plot is missing in Room
                if (fromRoom.needsEnrichment == 1 || currentDetails.plot.isBlank()) {
                    try {
                        container.catalogSync.enrichOne(container.repo, container.tmdb, id)
                        val updated = withContext(Dispatchers.IO) {
                            runCatching { container.catalogDb.movieDao().findByStreamId(id) }.getOrNull()
                        }
                        if (updated != null) {
                            currentDetails = updated.toMovieDetails()
                            _state.update { DetailUiState(loading = false, data = currentDetails) }
                        }
                    } catch (_: Exception) {
                        // Keep showing basic data on enrichment failure
                    }
                }

                // 3. Fallback to repo.movieDetails when plot is still blank after Room lookup/enrichment
                if (currentDetails.plot.isBlank()) {
                    try {
                        val remote = container.repo.movieDetails(id)
                        val merged = currentDetails.copy(
                            image = currentDetails.image.ifBlank { remote.image },
                            backdrop = if (currentDetails.backdrop.isBlank() || currentDetails.backdrop == currentDetails.image) {
                                remote.backdrop.ifBlank { currentDetails.backdrop }
                            } else {
                                currentDetails.backdrop
                            },
                            plot = remote.plot.ifBlank { currentDetails.plot },
                            cast = currentDetails.cast.ifBlank { remote.cast },
                            director = currentDetails.director.ifBlank { remote.director },
                            genre = currentDetails.genre.ifBlank { remote.genre },
                            year = currentDetails.year.ifBlank { remote.year },
                            duration = currentDetails.duration.ifBlank { remote.duration },
                            rating = currentDetails.rating.ifBlank { remote.rating },
                        )
                        _state.update { DetailUiState(loading = false, data = merged) }
                    } catch (_: Exception) {
                        // Keep existing details if remote fallback fails
                    }
                }
                return@launch
            }

            // 3. Fallback: movie not yet in Room — use original repo path
            _state.update { DetailUiState(loading = true) }
            try {
                val details = container.repo.movieDetails(id)
                _state.update { DetailUiState(loading = false, data = details) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { DetailUiState(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun toggleFavorite(details: MovieDetails) {
        viewModelScope.launch {
            container.prefs.toggleFavorite(
                FavItem(Kind.MOVIE, details.id, details.name, details.image, details.ext, accountId = container.prefs.currentActiveAccountId())
            )
        }
    }

    fun refreshMetadata() {
        if (_refreshingMetadata.value) return
        viewModelScope.launch {
            _refreshingMetadata.value = true
            try {
                container.catalogSync.enrichOne(container.repo, container.tmdb, id, force = true)
                val updated = withContext(Dispatchers.IO) {
                    runCatching { container.catalogDb.movieDao().findByStreamId(id) }.getOrNull()
                }
                if (updated != null) {
                    _state.update { DetailUiState(loading = false, data = updated.toMovieDetails()) }
                } else {
                    val details = container.repo.movieDetails(id)
                    _state.update { DetailUiState(loading = false, data = details) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep existing details visible if manual refresh fails
            } finally {
                _refreshingMetadata.value = false
            }
        }
    }
}
