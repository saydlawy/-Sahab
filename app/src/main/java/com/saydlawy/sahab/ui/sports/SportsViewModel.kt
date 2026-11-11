package com.saydlawy.sahab.ui.sports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.saydlawy.sahab.data.sports.AllowedLeaguesConfig
import com.saydlawy.sahab.data.sports.League
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.SportsDatabase
import com.saydlawy.sahab.data.sports.Standing
import com.saydlawy.sahab.data.sports.SyncScheduler
import com.saydlawy.sahab.data.sports.toMatch
import com.saydlawy.sahab.data.sports.toStanding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumMap
import java.util.Locale

enum class MainCategory(val title: String) {
    EGYPTIAN("الدوري المصري"),
    SAUDI("دوري روشن"),
    CLUBS_INTL("الدولية للأندية"),
    NATIONAL_TEAMS("الدولية للمنتخبات"),
    EUROPEAN("بطولات أخرى")
}

enum class DateFilter(
    val title: String,
    val offsetDays: Int = 0,
    val isRange: Boolean = false,
    val rangeDays: Int = 0,
) {
    YESTERDAY("الأمس", offsetDays = -1),
    TODAY("اليوم", offsetDays = 0),
    TOMORROW("غداً", offsetDays = 1),
    DAYS_7("7 أيام", isRange = true, rangeDays = 7),
    DAYS_14("14 يوماً", isRange = true, rangeDays = 14),
    DAYS_28("28 يوماً", isRange = true, rangeDays = 28),
    DAYS_60("60 يوماً", isRange = true, rangeDays = 60),
    DAYS_90("90 يوماً", isRange = true, rangeDays = 90),
}

sealed class SportsUiState {
    object Loading : SportsUiState()
    data class Success(val groupedMatches: Map<League, List<Match>>) : SportsUiState()
    data class Error(val message: String) : SportsUiState()
}

class SportsViewModel(application: Application) : AndroidViewModel(application) {

    private val db by lazy { SportsDatabase.getInstance(getApplication()) }

    private val dao by lazy { db.matchDao() }

    private val _selectedCategory = MutableStateFlow(MainCategory.EGYPTIAN)
    val selectedCategory: StateFlow<MainCategory> = _selectedCategory.asStateFlow()

    private val _subLeagues = MutableStateFlow<List<Pair<Int?, String>>>(emptyList())
    val subLeagues: StateFlow<List<Pair<Int?, String>>> = _subLeagues.asStateFlow()

    private val _selectedSubLeague = MutableStateFlow<Int?>(233)
    val selectedSubLeague: StateFlow<Int?> = _selectedSubLeague.asStateFlow()

    private val _selectedDate = MutableStateFlow(DateFilter.TODAY)
    val selectedDate: StateFlow<DateFilter> = _selectedDate.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedMatch = MutableStateFlow<Match?>(null)
    val selectedMatch: StateFlow<Match?> = _selectedMatch.asStateFlow()

    private val _standings = MutableStateFlow<List<Standing>>(emptyList())
    val standings: StateFlow<List<Standing>> = _standings.asStateFlow()

    private val _standingsLoading = MutableStateFlow(false)
    val standingsLoading: StateFlow<Boolean> = _standingsLoading.asStateFlow()

    private val _standingsError = MutableStateFlow<String?>(null)
    val standingsError: StateFlow<String?> = _standingsError.asStateFlow()

    private val _standingsLeagueName = MutableStateFlow("")
    val standingsLeagueName: StateFlow<String> = _standingsLeagueName.asStateFlow()

    private val _totalDbCount = MutableStateFlow(0)
    val totalDbCount: StateFlow<Int> = _totalDbCount.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _dateFilterCounts = MutableStateFlow<Map<DateFilter, Int>>(emptyMap())
    val dateFilterCounts: StateFlow<Map<DateFilter, Int>> = _dateFilterCounts.asStateFlow()

    private val _uiState = MutableStateFlow<SportsUiState>(SportsUiState.Loading)
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()

    @Volatile
    private var allMatches: List<Match> = emptyList()

    init {
        configureSubLeaguesFor(MainCategory.EGYPTIAN)
        fetchStandings()

        viewModelScope.launch {
            try {
                val cached = withContext(Dispatchers.IO) {
                    dao.getAllMatches().map { it.toMatch() }
                }
                _totalDbCount.value = cached.size
                if (cached.isNotEmpty()) {
                    allMatches = cached
                    applyFilters(allMatches)
                } else {
                    _uiState.value = SportsUiState.Success(emptyMap())
                }
            } catch (e: Exception) {
                android.util.Log.e("SportsDB", "Failed to read DB", e)
                _uiState.value = SportsUiState.Error("DB read error: ${e.message ?: "unknown"}")
            }
        }
    }
    fun fetchMatchesFromApi(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _isRefreshing.value = true
            try {
                val merged = withContext(Dispatchers.IO) {
                    dao.getAllMatches()
                        .map { it.toMatch() }
                        .filter { (it.date.isBlank() || it.date >= "2026-01-01") && isSeniorFixture(it) }
                        .distinctBy {
                            "${it.date}_${it.homeTeam.name.trim().lowercase(Locale.ROOT)}_${it.awayTeam.name.trim().lowercase(Locale.ROOT)}"
                        }
                }
                _isRefreshing.value = false
                _totalDbCount.value = merged.size
                if (merged.isNotEmpty()) {
                    allMatches = merged
                    applyFilters(allMatches)
                } else if (allMatches.isEmpty()) {
                    _uiState.value = SportsUiState.Success(emptyMap())
                }
            } catch (e: Exception) {
                _isRefreshing.value = false
                _uiState.value = SportsUiState.Error("DB read error: ${e.message ?: "unknown"}")
            }
        }
    }
    fun fetchStandings(explicitLeagueId: Int? = null) {
        val leagueId = explicitLeagueId
            ?: _selectedSubLeague.value
            ?: defaultLeagueForCategory(_selectedCategory.value)

        _standingsLeagueName.value = AllowedLeaguesConfig.translateLeagueName(leagueId)
        _standingsError.value = null
        _standings.value = emptyList()
        _standingsLoading.value = true

        viewModelScope.launch {
            // 1. قراءة فورية من قاعدة البيانات المحلية لموسم 2026-2027
            val localRows = withContext(Dispatchers.IO) {
                runCatching { dao.getStandingsByLeague(leagueId).map { it.toStanding() } }.getOrDefault(emptyList())
            }
            if (localRows.isNotEmpty()) {
                _standings.value = localRows
                _standingsLoading.value = false
                return@launch
            }

            // 2. البطولة بنظام مجموعات أو بدون جدول دوري — لا ترتيب متاح
            _standingsLoading.value = false
            _standings.value = emptyList()
            _standingsError.value = "هذه البطولة بنظام المجموعات — لا يوجد جدول ترتيب موحّد"
        }
    }

    private fun defaultLeagueForCategory(category: MainCategory): Int {
        return when (category) {
            MainCategory.EGYPTIAN -> 233
            MainCategory.SAUDI -> 307
            MainCategory.CLUBS_INTL -> 2
            MainCategory.NATIONAL_TEAMS -> 1
            MainCategory.EUROPEAN -> 39
        }
    }

    private fun configureSubLeaguesFor(category: MainCategory) {
        _selectedCategory.value = category
        when (category) {
            MainCategory.EGYPTIAN -> {
                _subLeagues.value = emptyList()
                _selectedSubLeague.value = 233
            }
            MainCategory.SAUDI -> {
                _subLeagues.value = emptyList()
                _selectedSubLeague.value = 307
            }
            MainCategory.CLUBS_INTL -> {
                _subLeagues.value = listOf(
                    null to "الكل",
                    12 to "أبطال أفريقيا",
                    20 to "الكونفدرالية",
                    2 to "أبطال أوروبا",
                    3 to "الدوري الأوروبي",
                    17 to "أبطال آسيا"
                )
                _selectedSubLeague.value = null
            }
            MainCategory.NATIONAL_TEAMS -> {
                _subLeagues.value = listOf(
                    null to "الكل",
                    6 to "أمم أفريقيا",
                    32 to "تصفيات أمم أفريقيا",
                    403 to "أمم أوروبا",
                    8 to "كأس آسيا",
                    1 to "كأس العالم",
                    452 to "كأس العرب"
                )
                _selectedSubLeague.value = null
            }
            MainCategory.EUROPEAN -> {
                _subLeagues.value = listOf(
                    null to "الكل",
                    39 to "الإنجليزي",
                    140 to "الإسباني",
                    135 to "الإيطالي",
                    78 to "الألماني",
                    61 to "الفرنسي",
                    203 to "التركي"
                )
                _selectedSubLeague.value = null
            }
        }
    }

    fun updateCategory(category: MainCategory) {
        configureSubLeaguesFor(category)
        applyFilters(allMatches)
        fetchMatchesFromApi(silent = true)
        fetchStandings()
    }

    fun updateSubLeague(leagueId: Int?) {
        _selectedSubLeague.value = leagueId
        applyFilters(allMatches)
        fetchStandings()
    }

    fun refreshActiveFilter() {
        refreshManually()
    }

    fun refreshManually() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        val app = getApplication<Application>()
        val request = SyncScheduler.enqueueManualSyncNow(app)
        viewModelScope.launch {
            try {
                try {
                    WorkManager.getInstance(app)
                        .getWorkInfoByIdFlow(request.id)
                        .firstOrNull { info -> info != null && info.state.isFinished }
                } catch (_: Exception) {
                    // Reload local Room data even if WorkManager observation fails
                }
                fetchMatchesFromApi(silent = true)
                fetchStandings()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun updateDate(date: DateFilter) {
        _selectedDate.value = date
        applyFilters(allMatches)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters(allMatches)
    }

    fun selectMatch(match: Match?) {
        _selectedMatch.value = match
    }

    private fun applyFilters(matchesToFilter: List<Match>) {
        if (matchesToFilter.isEmpty()) {
            _uiState.value = SportsUiState.Success(emptyMap())
            return
        }

        val result = filterMatchesPure(
            matchesToFilter = matchesToFilter,
            category = _selectedCategory.value,
            subLeague = _selectedSubLeague.value,
            query = _searchQuery.value.trim(),
            filter = _selectedDate.value,
            todayDate = getFormattedDate(0),
            targetDate = getFormattedDate(_selectedDate.value.offsetDays),
            dateOfOffset = { offset -> getFormattedDate(offset) },
        )
        _dateFilterCounts.value = result.counts
        if (_selectedMatch.value == null || !result.sortedMatches.contains(_selectedMatch.value)) {
            _selectedMatch.value = result.sortedMatches.firstOrNull()
        }
        _uiState.value = SportsUiState.Success(result.groupedMatches)
    }

    private fun getFormattedDate(offsetDays: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, offsetDays)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(calendar.time)
    }

    internal data class FilteredMatchesResult(
        val counts: Map<DateFilter, Int>,
        val sortedMatches: List<Match>,
        val groupedMatches: Map<League, List<Match>>,
    )

    companion object {
        private val YOUTH_OR_CUP_REGEX = Regex(
            """\b(U18|U19|U20|U21|U23|EFL Trophy|Premier League 2|Reserves)\b""",
            RegexOption.IGNORE_CASE,
        )

        private fun isSeniorFixture(match: Match): Boolean {
            val haystack = "${match.league.name} ${match.homeTeam.name} ${match.awayTeam.name}"
            return !YOUTH_OR_CUP_REGEX.containsMatchIn(haystack)
        }

        internal fun filterMatchesPure(
            matchesToFilter: List<Match>,
            category: MainCategory,
            subLeague: Int?,
            query: String,
            filter: DateFilter,
            todayDate: String,
            targetDate: String,
            dateOfOffset: (Int) -> String,
        ): FilteredMatchesResult {
            val categoryLeagueIds = when (category) {
                MainCategory.EGYPTIAN -> setOf(233)
                MainCategory.SAUDI -> setOf(307)
                MainCategory.CLUBS_INTL -> setOf(12, 17, 20, 2, 3)
                MainCategory.NATIONAL_TEAMS -> setOf(6, 32, 403, 8, 1, 452)
                MainCategory.EUROPEAN -> setOf(39, 140, 135, 78, 61, 203)
            }

            val deduplicatedSource = matchesToFilter
                .filter { (it.date.isBlank() || it.date >= "2026-01-01") && isSeniorFixture(it) }
                .distinctBy {
                    "${it.date}_${it.homeTeam.name.trim().lowercase(Locale.ROOT)}_${it.awayTeam.name.trim().lowercase(Locale.ROOT)}"
                }

            val byCategoryAndQuery = deduplicatedSource.filter { match ->
                val inCategory = if (subLeague != null) {
                    match.league.id == subLeague
                } else {
                    match.league.id in categoryLeagueIds
                }
                val matchesSearch = if (query.isEmpty()) {
                    true
                } else {
                    match.homeTeam.name.contains(query, ignoreCase = true) ||
                        match.awayTeam.name.contains(query, ignoreCase = true) ||
                        match.league.name.contains(query, ignoreCase = true)
                }
                inCategory && matchesSearch
            }

            val counts = EnumMap<DateFilter, Int>(DateFilter::class.java)
            DateFilter.values().forEach { df ->
                counts[df] = if (df.isRange) {
                    val endDate = dateOfOffset(df.rangeDays)
                    byCategoryAndQuery.count { it.date in todayDate..endDate }
                } else {
                    val d = dateOfOffset(df.offsetDays)
                    byCategoryAndQuery.count { it.date == d }
                }
            }

            val exactMatches = if (filter.isRange) {
                val endDate = dateOfOffset(filter.rangeDays)
                byCategoryAndQuery.filter { it.date in todayDate..endDate }
            } else {
                byCategoryAndQuery.filter { it.date == targetDate }
            }

            // Strictly show only the matches for the selected date/range (or all matching dates when searching).
            val effectiveMatches = if (query.isNotEmpty()) {
                byCategoryAndQuery
            } else {
                exactMatches
            }

            val sortedMatches = effectiveMatches.sortedWith(
                compareBy<Match> { AllowedLeaguesConfig.getLeagueSortPriority(it.league.id) }
                    .thenBy {
                        when (it.status) {
                            MatchStatus.LIVE -> 0
                            MatchStatus.UPCOMING -> 1
                            MatchStatus.FINISHED -> 2
                            MatchStatus.POSTPONED -> 3
                            MatchStatus.CANCELLED -> 4
                        }
                    }
                    .thenBy { it.date }
                    .thenBy { it.time },
            )

            return FilteredMatchesResult(
                counts = counts,
                sortedMatches = sortedMatches,
                groupedMatches = sortedMatches.groupBy { it.league },
            )
        }
    }
}
