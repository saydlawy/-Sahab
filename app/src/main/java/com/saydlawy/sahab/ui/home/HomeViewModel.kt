package com.saydlawy.sahab.ui.home

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.HomeChannelsSource
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.SportsDatabase
import com.saydlawy.sahab.data.sports.SyncScheduler
import com.saydlawy.sahab.data.sports.toMatch
import com.saydlawy.sahab.util.formatDayOffsetYmd
import com.saydlawy.sahab.util.utcDateToLocalYmd
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

@Immutable
data class HomeUiState(
    val featuredChannels: List<Channel> = emptyList(),
    val trendingMovies: List<CatalogMovieEntity> = emptyList(),
    val todayMatches: List<Match> = emptyList(),
)

/**
 * M-18: Owns Home screen data loading (featured channels, instant/enriched movies, today's matches)
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

    init {
        loadTodayMatches()
    }

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
                _state.update { it.copy(trendingMovies = instant) }
            }

            val enriched = runCatching {
                container.firebaseCatalog.getXtreamRecommendedMovies(
                    repo = container.repo,
                    dao = container.catalogDb.movieDao(),
                    limit = 18,
                )
            }.getOrNull().orEmpty()
            if (enriched.isNotEmpty()) {
                _state.update { it.copy(trendingMovies = enriched) }
            }
        }
    }

    fun loadTodayMatches() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = SportsDatabase.getInstance(appContext).matchDao()
                var all = dao.getAllMatches().map { it.toMatch() }
                if (all.isEmpty()) {
                    val req = SyncScheduler.enqueueManualSyncNow(appContext)
                    withTimeoutOrNull(45_000L) {
                        WorkManager.getInstance(appContext)
                            .getWorkInfoByIdFlow(req.id)
                            .firstOrNull { it != null && it.state.isFinished }
                    }
                    all = dao.getAllMatches().map { it.toMatch() }
                }
                val todayStr = formatDayOffsetYmd(0)
                val tomorrowStr = formatDayOffsetYmd(1)
                val yesterdayStr = formatDayOffsetYmd(-1)

                val todayList = all.filter {
                    utcDateToLocalYmd(it.date, it.time) == todayStr || it.status == MatchStatus.LIVE
                }
                val combined = if (todayList.size >= 5) {
                    todayList
                } else {
                    val nearby = all.filter {
                        val localDate = utcDateToLocalYmd(it.date, it.time)
                        localDate == tomorrowStr || localDate == yesterdayStr
                    }
                    (todayList + nearby + all).distinctBy { it.id }.take(12)
                }
                _state.update { it.copy(todayMatches = combined.take(12)) }
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
