package com.saydlawy.sahab.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.Episode
import com.saydlawy.sahab.data.SeriesDetails
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.catalog.SeriesEpisodeEntity
import com.saydlawy.sahab.data.catalog.buildCatalogSearchTitle
import com.saydlawy.sahab.data.catalog.encodeEpisodesJson
import com.saydlawy.sahab.data.catalog.parseCastMembersJson
import com.saydlawy.sahab.data.catalog.parseEpisodesJson
import com.saydlawy.sahab.util.RatingSanitizer
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

    private fun cleanRating(raw: String): String = RatingSanitizer.format(raw)

    private fun List<SeriesEpisodeEntity>.toEpisodes(): List<Episode> = map { e ->
        Episode(
            id = e.episodeId,
            season = e.seasonNumber,
            number = e.episodeNumber,
            title = e.title,
            ext = e.ext,
            image = e.iptvImage.ifBlank { e.tmdbStillUrl },
            plot = e.plot,
            duration = e.duration,
        )
    }

    private fun CatalogMovieEntity.toSeriesDetails(relationalEpisodes: List<Episode> = emptyList()): SeriesDetails {
        val posters = postersJson
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        return SeriesDetails(
            id = novaStreamId,
            name = titleAr.ifBlank { titleEn },
            cover = posterUrl,
            backdrop = backdropUrl.ifBlank { posterUrl },
            plot = plot,
            cast = cast,
            castMembers = parseCastMembersJson(castJson),
            genre = genre,
            year = year,
            rating = RatingSanitizer.format(rating, tmdbVoteCount),
            episodes = relationalEpisodes.ifEmpty { parseEpisodesJson(episodesJson) },
            posterUrls = posters,
            trailerUrl = trailerUrl,
        )
    }

    private fun mergeSeriesDetails(
        remote: SeriesDetails?,
        room: CatalogMovieEntity?,
    ): SeriesDetails {
        val roomDetails = room?.toSeriesDetails()
        if (remote == null && roomDetails != null) return roomDetails
        val base = remote ?: SeriesDetails(id = id, name = "")
        if (roomDetails == null) {
            return base.copy(rating = cleanRating(base.rating))
        }
        return SeriesDetails(
            id = id,
            name = base.name.ifBlank { roomDetails.name },
            cover = base.cover.ifBlank { roomDetails.cover },
            backdrop = roomDetails.backdrop.ifBlank { base.backdrop }.ifBlank { base.cover.ifBlank { roomDetails.cover } },
            plot = roomDetails.plot.ifBlank { base.plot },
            cast = roomDetails.cast.ifBlank { base.cast },
            castMembers = roomDetails.castMembers.ifEmpty { base.castMembers },
            genre = roomDetails.genre.ifBlank { base.genre },
            year = roomDetails.year.ifBlank { base.year },
            rating = roomDetails.rating.ifBlank { cleanRating(base.rating) },
            episodes = base.episodes.ifEmpty { roomDetails.episodes },
            posterUrls = roomDetails.posterUrls.ifEmpty { base.posterUrls },
            trailerUrl = roomDetails.trailerUrl.ifBlank { base.trailerUrl },
        )
    }

    private fun fetchDetails(forceRefresh: Boolean) {
        viewModelScope.launch {
            val dao = container.catalogDb.movieDao()
            val relDao = container.catalogDb.relationalDao()
            if (forceRefresh) {
                _refreshingMetadata.value = true
            }
            try {
                // 1. Instant local read from Room (series metadata + relational episodes)
                var roomEntity: CatalogMovieEntity? = withContext(Dispatchers.IO) {
                    runCatching { dao.findByStreamIdAndType(id, "series") }.getOrNull()
                }
                val localEpisodes = withContext(Dispatchers.IO) {
                    runCatching { relDao.getAllEpisodesForSeries(id).toEpisodes() }.getOrDefault(emptyList())
                }
                if (roomEntity != null && !forceRefresh) {
                    _state.update { DetailUiState(loading = false, data = roomEntity.toSeriesDetails(localEpisodes)) }
                } else if (!forceRefresh) {
                    _state.update { DetailUiState(loading = true) }
                }

                // 2. Refresh live episodes & provider info from IPTV when available
                var remoteError: Exception? = null
                val remote = try {
                    container.repo.seriesDetails(id)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    remoteError = e
                    null
                }

                if (remote != null) {
                    val now = System.currentTimeMillis()
                    val encodedEpisodes = encodeEpisodesJson(remote.episodes)
                    withContext(Dispatchers.IO) {
                        runCatching {
                            if (remote.episodes.isNotEmpty()) {
                                relDao.syncSeriesEpisodesPreservingTmdb(
                                    seriesStreamId = id,
                                    incoming = remote.episodes.map { ep ->
                                        SeriesEpisodeEntity(
                                            episodeId = ep.id,
                                            seriesStreamId = id,
                                            seasonNumber = ep.season,
                                            episodeNumber = ep.number,
                                            title = ep.title,
                                            ext = ep.ext,
                                            iptvImage = ep.image,
                                            plot = ep.plot,
                                            duration = ep.duration,
                                            updatedAt = now,
                                        )
                                    },
                                    now = now,
                                )
                            }
                            if (roomEntity != null) {
                                if (encodedEpisodes.isNotBlank()) {
                                    dao.updateSeriesEpisodes(id, encodedEpisodes, now)
                                }
                            } else {
                                val newEntity = CatalogMovieEntity(
                                    sahabId = CatalogMovieEntity.idPending(id, "series"),
                                    novaStreamId = id,
                                    mediaType = "series",
                                    titleAr = remote.name,
                                    year = remote.year,
                                    plot = remote.plot,
                                    cast = remote.cast,
                                    genre = remote.genre,
                                    rating = cleanRating(remote.rating),
                                    posterUrl = remote.cover,
                                    backdropUrl = remote.backdrop,
                                    trailerUrl = remote.trailerUrl,
                                    episodesJson = encodedEpisodes,
                                    needsEnrichment = if (remote.plot.isNotBlank() && remote.backdrop.isNotBlank()) 0 else 1,
                                    lastSeen = now,
                                    deleted = 0,
                                    searchTitle = buildCatalogSearchTitle(remote.name),
                                )
                                dao.upsert(newEntity)
                            }
                            roomEntity = dao.findByStreamIdAndType(id, "series")
                        }
                    }
                    _state.update { DetailUiState(loading = false, data = mergeSeriesDetails(remote, roomEntity)) }
                } else if (roomEntity == null) {
                    if (!forceRefresh) {
                        _state.update {
                            DetailUiState(loading = false, error = remoteError?.userMessage() ?: "تعذر تحميل المسلسل")
                        }
                    }
                    return@launch
                }

                // 3. Unified enrichment (Room cloud ref -> Firebase -> TMDB) when plot/backdrop missing or forced
                val currentEntity = roomEntity
                val needsMeta = forceRefresh ||
                    currentEntity == null ||
                    currentEntity.needsEnrichment == 1 ||
                    currentEntity.plot.isBlank() ||
                    currentEntity.backdropUrl.isBlank()
                if (needsMeta) {
                    runCatching {
                        container.catalogSync.enrichOne(
                            repo = container.repo,
                            tmdb = container.tmdb,
                            streamId = id,
                            force = forceRefresh,
                            mediaType = "series",
                        )
                    }
                    val updatedRoom = withContext(Dispatchers.IO) {
                        runCatching { dao.findByStreamIdAndType(id, "series") }.getOrNull()
                    }
                    if (updatedRoom != null) {
                        _state.update {
                            DetailUiState(loading = false, data = mergeSeriesDetails(remote, updatedRoom))
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!forceRefresh && _state.value.data == null) {
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
                FavItem(Kind.SERIES, details.id, details.name, details.cover, accountId = container.prefs.currentActiveAccountId()),
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
                    val dao = container.catalogDb.movieDao()
                    val existing = withContext(Dispatchers.IO) {
                        runCatching { dao.findByStreamIdAndType(id, "series") }.getOrNull()
                    }
                    val entity = (existing ?: CatalogMovieEntity(
                        sahabId = CatalogMovieEntity.idPending(id, "series"),
                        novaStreamId = id,
                        mediaType = "series",
                        titleAr = details.name,
                        year = details.year,
                        posterUrl = details.cover,
                        backdropUrl = details.backdrop,
                        cast = details.cast,
                        genre = details.genre,
                        rating = cleanRating(details.rating),
                        episodesJson = encodeEpisodesJson(details.episodes),
                    )).copy(
                        plot = result.story,
                        plotSource = "gemini_user_key",
                        plotConfidence = result.confidence,
                        identifiedTitle = result.identifiedTitle,
                        needsEnrichment = 0,
                        lastEnriched = now,
                        lastSeen = now,
                        needsCloudPush = 1,
                        updatedAt = now,
                        searchTitle = buildCatalogSearchTitle(
                            existing?.titleAr ?: details.name,
                            existing?.titleEn.orEmpty(),
                            result.identifiedTitle,
                        ),
                    )
                    withContext(Dispatchers.IO) {
                        runCatching { dao.upsert(entity) }
                    }
                    container.appScope.launch(Dispatchers.IO) {
                        container.catalogSync.maybeAutoUploadToCloud(entity)
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

    suspend fun searchTmdbCandidates(query: String): List<com.saydlawy.sahab.data.TmdbSearchResult> {
        val q = query.trim()
        if (q.isBlank() || !container.tmdb.hasApiKey()) return emptyList()
        return withContext(Dispatchers.IO) {
            val directId = q.toIntOrNull()?.takeIf { it > 0 }
                ?: TMDB_URL_ID_REGEX.find(q)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val direct = directId?.let { tid ->
                runCatching { container.tmdb.getTvDetailsById(tid) }.getOrNull()?.let { m ->
                    listOf(
                        com.saydlawy.sahab.data.TmdbSearchResult(
                            id = m.id,
                            title = m.title,
                            releaseDate = m.releaseDate,
                            voteCount = m.voteCount,
                            popularity = m.popularity,
                            posterPath = m.posterPath,
                            voteAverage = m.voteAverage,
                        ),
                    )
                }
            }.orEmpty()
            val ar = (container.tmdb.searchTvOutcome(q, null, "ar-SA") as? com.saydlawy.sahab.data.TmdbApi.SearchOutcome.Ok)?.results.orEmpty()
            val en = (container.tmdb.searchTvOutcome(q, null, "en-US") as? com.saydlawy.sahab.data.TmdbApi.SearchOutcome.Ok)?.results.orEmpty()
            (direct + ar + en).distinctBy { it.id }.take(15)
        }
    }

    fun applyManualTmdbMatch(tmdbId: Int) {
        if (_refreshingMetadata.value) return
        viewModelScope.launch {
            _refreshingMetadata.value = true
            var ok = false
            try {
                ok = withContext(Dispatchers.IO) {
                    container.catalogSync.applyManualTmdbMatch(container.tmdb, id, tmdbId, "series")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                _refreshingMetadata.value = false
            }
            if (ok) {
                fetchDetails(forceRefresh = false)
            }
        }
    }

    private companion object {
        val TMDB_URL_ID_REGEX = Regex("(?:movie|tv)/(\\d+)")
    }
}
