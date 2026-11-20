package com.saydlawy.sahab.ui.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.HomeChannelsSource
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.util.RatingSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Immutable
data class HomeUiState(
    val featuredChannels: List<Channel> = emptyList(),
    val trendingMovies: List<CatalogMovieEntity> = emptyList(),
    val topRatedMovies: List<CatalogMovieEntity> = emptyList(),
)

/**
 * M-18: Owns Home screen data loading (featured channels, instant/enriched movies)
 * outside Composable state.
 */
class HomeViewModel(
    private val container: AppContainer,
    private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(
        HomeUiState(trendingMovies = container.firebaseCatalog.peekCachedRecommendedMovies()),
    )
    val state = _state.asStateFlow()

    private var featuredJob: Job? = null
    private var trendingJob: Job? = null

    fun refreshFeaturedChannels(
        source: HomeChannelsSource,
        categoryId: String,
        customIds: Set<String>,
        favorites: List<FavItem>,
    ) {
        featuredJob?.cancel()
        featuredJob = viewModelScope.launch {
            val loaded = loadFeaturedChannels(
                repo = container.repo,
                source = source,
                categoryId = categoryId,
                customIds = customIds,
                favorites = favorites,
            )
            _state.update { it.copy(featuredChannels = loaded) }
        }
    }

    fun refreshTrendingMovies() {
        trendingJob?.cancel()
        trendingJob = viewModelScope.launch {
            val instant = runCatching {
                container.firebaseCatalog.getInstantRecommendedMovies(
                    repo = container.repo,
                    dao = container.catalogDb.movieDao(),
                    limit = 18,
                )
            }.getOrNull().orEmpty()
            if (instant.isNotEmpty()) {
                val topInstant = instant
                    .filter { RatingSanitizer.parseValid(it.rating, it.tmdbVoteCount) != null }
                    .sortedByDescending { RatingSanitizer.rankingScore(it.rating, it.tmdbVoteCount) }
                    .take(18)
                _state.update { it.copy(trendingMovies = instant, topRatedMovies = topInstant.ifEmpty { it.topRatedMovies }) }
            }

            val enriched = runCatching {
                container.firebaseCatalog.getXtreamRecommendedMovies(
                    repo = container.repo,
                    dao = container.catalogDb.movieDao(),
                    limit = 18,
                )
            }.getOrNull().orEmpty()
            if (enriched.isNotEmpty()) {
                val topEnriched = enriched
                    .filter { RatingSanitizer.parseValid(it.rating, it.tmdbVoteCount) != null }
                    .sortedByDescending { RatingSanitizer.rankingScore(it.rating, it.tmdbVoteCount) }
                    .take(18)
                _state.update { it.copy(trendingMovies = enriched, topRatedMovies = topEnriched.ifEmpty { it.topRatedMovies }) }
            }
        }
    }
}

internal suspend fun loadFeaturedChannels(
    repo: Repository,
    source: HomeChannelsSource,
    categoryId: String,
    customIds: Set<String>,
    favorites: List<FavItem>,
): List<Channel> = withContext(Dispatchers.IO) {
    runCatching {
        val all = repo.peekAllLiveChannels() ?: repo.liveChannels(null)
        if (all.isEmpty()) return@withContext emptyList()

        when (source) {
            HomeChannelsSource.CUSTOM -> {
                if (customIds.isNotEmpty()) {
                    val byId = all.associateBy { it.id }
                    val picked = customIds.mapNotNull { byId[it] }
                    if (picked.isNotEmpty()) return@withContext picked
                }
            }
            HomeChannelsSource.FAVORITES -> {
                val favLiveIds = favorites.filter { it.kind == Kind.LIVE }.map { it.id }.toSet()
                if (favLiveIds.isNotEmpty()) {
                    val picked = all.filter { it.id in favLiveIds }
                    if (picked.isNotEmpty()) return@withContext picked
                }
            }
            HomeChannelsSource.CATEGORY -> {
                if (categoryId.isNotBlank()) {
                    val catChannels = all.filter { it.categoryId == categoryId }.ifEmpty {
                        runCatching { repo.liveChannels(categoryId) }.getOrDefault(emptyList())
                    }
                    if (catChannels.isNotEmpty()) return@withContext catChannels.take(18)
                }
            }
            HomeChannelsSource.AUTO -> Unit
        }

        val targetKeywords = listOf(
            listOf("bein sports 1", "bein sport 1", "بي ان سبورت 1"),
            listOf("mbc 1", "ام بي سي 1"),
            listOf("mbc action", "ام بي سي اكشن"),
            listOf("mbc masr", "ام بي سي مصر"),
            listOf("mbc drama", "ام بي سي دراما"),
            listOf("روتانا سينما", "rotana cinema"),
            listOf("روتانا خليجية", "rotana khalijiah", "rotana khalijia"),
            listOf("الجزيرة", "al jazeera", "aljazeera"),
            listOf("العربية", "alarabiya", "الحدث"),
            listOf("ssc 1", "ssc", "alkass", "الكأس", "on time"),
            listOf("ناشونال", "national geographic", "nat geo"),
            listOf("دبي", "dubai", "abu dhabi", "أبوظبي"),
        )

        val matched = mutableListOf<Channel>()
        val seen = mutableSetOf<String>()

        for (keywords in targetKeywords) {
            val candidate = all.firstOrNull { ch ->
                val lower = ch.name.lowercase(Locale.ROOT)
                keywords.any { kw -> lower.contains(kw.lowercase(Locale.ROOT)) } && seen.add(ch.id)
            }
            if (candidate != null) {
                matched.add(candidate)
            }
        }

        for (ch in all) {
            if (matched.size >= 14) break
            if (seen.add(ch.id)) {
                matched.add(ch)
            }
        }
        matched
    }.getOrDefault(emptyList())
}
