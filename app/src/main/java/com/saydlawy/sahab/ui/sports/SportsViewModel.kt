package com.saydlawy.sahab.ui.sports

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.saydlawy.sahab.BuildConfig
import com.saydlawy.sahab.data.sports.AllowedLeaguesConfig
import com.saydlawy.sahab.data.sports.ApiResult
import com.saydlawy.sahab.data.sports.League
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.MorphTvApi
import com.saydlawy.sahab.data.sports.SportsApi
import com.saydlawy.sahab.data.sports.SportsDatabase
import com.saydlawy.sahab.data.sports.SportsSyncManager
import com.saydlawy.sahab.data.sports.Standing
import com.saydlawy.sahab.data.sports.TheSportsDbApi
import com.saydlawy.sahab.data.sports.toEntity
import com.saydlawy.sahab.data.sports.toMatch
import com.saydlawy.sahab.data.sports.toStanding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.EnumMap
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

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

    private val db by lazy {
        val app = getApplication<Application>()
        val dbFile = app.getDatabasePath("sports_db")

        if (!dbFile.exists() || !isDatabaseSchemaCompatible(dbFile)) {
            try {
                dbFile.parentFile?.mkdirs()
                File(dbFile.path + "-wal").delete()
                File(dbFile.path + "-shm").delete()
                app.assets.open("databases/sports_db").use { input ->
                    FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SportsDB", "Failed to copy pre-packaged sports_db from assets", e)
            }
        }

        Room.databaseBuilder(app, SportsDatabase::class.java, "sports_db")
            .fallbackToDestructiveMigration()
            .build()
    }

    private fun isDatabaseSchemaCompatible(dbFile: File): Boolean {
        if (!dbFile.exists()) return false
        return try {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY).use { sqLite ->
                var leagueIdNotNull = false
                sqLite.rawQuery("PRAGMA table_info(matches)", null).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    val notNullIdx = cursor.getColumnIndex("notnull")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0 && notNullIdx >= 0 && cursor.getString(nameIdx) == "leagueId") {
                            leagueIdNotNull = cursor.getInt(notNullIdx) == 1
                        }
                    }
                }
                var standingsPkValid = false
                sqLite.rawQuery("PRAGMA table_info(standings)", null).use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    val pkIdx = cursor.getColumnIndex("pk")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0 && pkIdx >= 0 && cursor.getString(nameIdx) == "leagueId") {
                            standingsPkValid = cursor.getInt(pkIdx) > 0
                        }
                    }
                }
                var hasPre2026OrYouth = false
                sqLite.rawQuery(
                    "SELECT count(*) FROM matches WHERE date < '2026-01-01' OR time = '00:00' OR trim(time) = '' OR leagueId = 848 OR leagueName LIKE '%EFL Trophy%' OR homeTeamName LIKE '%U21%' OR awayTeamName LIKE '%U21%'",
                    null,
                ).use { c ->
                    if (c.moveToNext()) hasPre2026OrYouth = c.getInt(0) > 0
                }
                var has2026Standings = false
                sqLite.rawQuery(
                    "SELECT count(*) FROM standings WHERE leagueId = 307 AND played < 34",
                    null,
                ).use { c ->
                    if (c.moveToNext()) has2026Standings = c.getInt(0) >= 18
                }
                var hasIntlStandings = false
                sqLite.rawQuery(
                    "SELECT count(DISTINCT leagueId) FROM standings WHERE leagueId IN (1, 6, 8, 403, 452)",
                    null,
                ).use { c ->
                    if (c.moveToNext()) hasIntlStandings = c.getInt(0) >= 5
                }
                leagueIdNotNull && standingsPkValid && !hasPre2026OrYouth && has2026Standings && hasIntlStandings
            }
        } catch (e: Exception) {
            false
        }
    }

    private val dao by lazy { db.matchDao() }

    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
    private val syncManager by lazy { SportsSyncManager(dao, okHttpClient) }
    private val sportsApi by lazy { SportsApi(okHttpClient) }
    private val morphTvApi by lazy { MorphTvApi(okHttpClient) }
    private val theSportsDbApi by lazy { TheSportsDbApi(okHttpClient) }

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
    private var pollingJob: Job? = null

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
                }
            } catch (e: Exception) {
                android.util.Log.e("SportsDB", "Error reading initial DB", e)
            }

            // جلب التحديثات الحية والمزامنة الذكية في الخلفية
            fetchMatchesFromApi(silent = allMatches.isNotEmpty())

            withContext(Dispatchers.IO) {
                try {
                    val openFootKey = try { BuildConfig.OPENFOOT_API_KEY } catch (e: Exception) { "" }
                    val highlightlyKey = try { BuildConfig.HIGHLIGHTLY_API_KEY } catch (e: Exception) { "" }
                    syncManager.runMonthlySync(openFootKey, highlightlyKey)

                    val updated = dao.getAllMatches().map { it.toMatch() }
                    _totalDbCount.value = updated.size
                    if (updated.isNotEmpty()) {
                        allMatches = updated
                        withContext(Dispatchers.Main) {
                            applyFilters(allMatches)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SportsSync", "Error in monthly sync", e)
                }
            }
        }
        startSmartPolling()
    }

    fun fetchMatchesFromApi(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _isRefreshing.value = true
            if (!silent && allMatches.isEmpty()) {
                _uiState.value = SportsUiState.Loading
            }

            val merged = withContext(Dispatchers.IO) {
                val targetDate = getFormattedDate(_selectedDate.value.offsetDays)
                val apiFootballDeferred = async {
                    if (_selectedDate.value == DateFilter.TODAY) {
                        // Today = full day schedule (fixtures?date=today) + live enrich (fixtures?live=all)
                        val today = getFormattedDate(0)
                        val liveDeferred = async { sportsApi.getLiveMatches() }
                        val fixturesDeferred = async { sportsApi.getFixturesByDate(today) }
                        val liveRes = liveDeferred.await()
                        val fixRes = fixturesDeferred.await()
                        when {
                            fixRes is ApiResult.Success && liveRes is ApiResult.Success ->
                                ApiResult.Success(
                                    (fixRes.data + liveRes.data).distinctBy { it.id }
                                )
                            fixRes is ApiResult.Success -> fixRes
                            liveRes is ApiResult.Success -> liveRes
                            else -> fixRes
                        }
                    } else {
                        sportsApi.getFixturesByDate(targetDate)
                    }
                }
                val morphTvDeferred = async { morphTvApi.getMatches() }

                val apiResult = apiFootballDeferred.await()
                val morphResult = morphTvDeferred.await()

                val combinedMatches = mutableListOf<Match>()
                if (apiResult is ApiResult.Success) {
                    combinedMatches.addAll(apiResult.data.filter { it.league.id in AllowedLeaguesConfig.ALLOWED_LEAGUES && it.date >= "2026-01-01" && isSeniorFixture(it) })
                }
                if (morphResult is ApiResult.Success) {
                    combinedMatches.addAll(morphResult.data.filter { it.league.id in AllowedLeaguesConfig.ALLOWED_LEAGUES && it.date >= "2026-01-01" && isSeniorFixture(it) })
                }

                if (combinedMatches.isNotEmpty()) {
                    val distinctNew = combinedMatches.distinctBy {
                        "${it.date}_${it.homeTeam.name.trim().lowercase(Locale.ROOT)}_${it.awayTeam.name.trim().lowercase(Locale.ROOT)}"
                    }
                    runCatching { dao.insertMatches(distinctNew.map { it.toEntity() }) }
                }

                runCatching {
                    dao.getAllMatches()
                        .map { it.toMatch() }
                        .filter { (it.date.isBlank() || it.date >= "2026-01-01") && isSeniorFixture(it) }
                        .distinctBy {
                            "${it.date}_${it.homeTeam.name.trim().lowercase(Locale.ROOT)}_${it.awayTeam.name.trim().lowercase(Locale.ROOT)}"
                        }
                }.getOrDefault(allMatches)
            }

            _isRefreshing.value = false
            _totalDbCount.value = merged.size
            if (merged.isNotEmpty()) {
                allMatches = merged
                applyFilters(allMatches)
            } else if (allMatches.isEmpty()) {
                _uiState.value = SportsUiState.Success(emptyMap())
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

            // 2. تحديث من TheSportsDB في الخلفية فقط إذا لم تتوفر بيانات محلية
            val tsdbId = AllowedLeaguesConfig.THESPORTSDB_LEAGUE_ID[leagueId]
            if (tsdbId == null) {
                _standingsLoading.value = false
                _standings.value = emptyList()
                _standingsError.value = "جدول الترتيب غير متاح لهذه البطولة حالياً"
                return@launch
            }

            val result = withContext(Dispatchers.IO) {
                theSportsDbApi.getStandings(tsdbId)
            }
            _standingsLoading.value = false
            when (result) {
                is ApiResult.Success -> {
                    if (result.data.isNotEmpty()) {
                        _standings.value = result.data
                        _standingsError.value = null
                        withContext(Dispatchers.IO) {
                            runCatching {
                                dao.deleteStandingsByLeague(leagueId)
                                dao.insertStandings(result.data.map { it.toEntity(leagueId) })
                            }
                        }
                    } else {
                        _standings.value = emptyList()
                        _standingsError.value = "لا تتوفر بيانات ترتيب لهذه البطولة حالياً"
                    }
                }
                is ApiResult.Error -> {
                    _standings.value = emptyList()
                    _standingsError.value = "تعذر تحديث جدول الترتيب: ${result.message}"
                }
            }
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

    private fun startSmartPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                val hasLiveMatches = allMatches.any { it.status == MatchStatus.LIVE }
                if (hasLiveMatches && _selectedDate.value == DateFilter.TODAY) {
                    fetchMatchesFromApi(silent = true)
                }
            }
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
        fetchMatchesFromApi(silent = false)
        fetchStandings()
    }

    fun updateDate(date: DateFilter) {
        _selectedDate.value = date
        applyFilters(allMatches)
        fetchMatchesFromApi(silent = true)
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

        val category = _selectedCategory.value
        val subLeague = _selectedSubLeague.value
        val query = _searchQuery.value.trim()
        val filter = _selectedDate.value
        val todayDate = getFormattedDate(0)
        val targetDate = getFormattedDate(filter.offsetDays)

        val categoryLeagueIds = when (category) {
            MainCategory.EGYPTIAN -> setOf(233)
            MainCategory.SAUDI -> setOf(307)
            MainCategory.CLUBS_INTL -> setOf(12, 17, 20, 2, 3)
            MainCategory.NATIONAL_TEAMS -> setOf(6, 403, 8, 1, 452)
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

        // Compute match counts per DateFilter based on actual local DB availability
        val counts = EnumMap<DateFilter, Int>(DateFilter::class.java)
        DateFilter.values().forEach { df ->
            counts[df] = if (df.isRange) {
                val endDate = getFormattedDate(df.rangeDays)
                byCategoryAndQuery.count { it.date in todayDate..endDate }
            } else {
                val d = getFormattedDate(df.offsetDays)
                byCategoryAndQuery.count { it.date == d }
            }
        }
        _dateFilterCounts.value = counts

        // Match exact date or date range (Today / Yesterday / Tomorrow / 7..90 days)
        val exactMatches = if (filter.isRange) {
            val endDate = getFormattedDate(filter.rangeDays)
            byCategoryAndQuery.filter { it.date in todayDate..endDate }
        } else {
            byCategoryAndQuery.filter { match ->
                match.date.isBlank() || match.date == targetDate
            }
        }

        // Fallback to nearest upcoming matches if no fixtures exist for that exact date/range
        val effectiveMatches = if (exactMatches.isNotEmpty() || query.isNotEmpty()) {
            exactMatches
        } else {
            val targetEpoch = parseDateEpochDays(targetDate)
            val upcoming = byCategoryAndQuery
                .filter { it.date >= targetDate }
                .sortedWith(compareBy<Match> { it.date }.thenBy { it.time })
                .take(40)
            if (upcoming.isNotEmpty()) {
                upcoming
            } else {
                byCategoryAndQuery
                    .sortedWith(
                        compareBy<Match> { abs(parseDateEpochDays(it.date) - targetEpoch) }
                            .thenByDescending { it.date }
                            .thenBy { it.time }
                    )
                    .take(40)
            }
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
                .thenBy { it.time }
        )

        if (_selectedMatch.value == null || !sortedMatches.contains(_selectedMatch.value)) {
            _selectedMatch.value = sortedMatches.firstOrNull()
        }

        val grouped = sortedMatches.groupBy { it.league }
        _uiState.value = SportsUiState.Success(grouped)
    }

    private fun isSeniorFixture(match: Match): Boolean {
        val haystack = "${match.league.name} ${match.homeTeam.name} ${match.awayTeam.name}"
        return !YOUTH_OR_CUP_REGEX.containsMatchIn(haystack)
    }

    private companion object {
        val YOUTH_OR_CUP_REGEX = Regex(
            """\b(U18|U19|U20|U21|U23|EFL Trophy|Premier League 2|Reserves)\b""",
            RegexOption.IGNORE_CASE,
        )
    }

    private fun getFormattedDate(offsetDays: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, offsetDays)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return format.format(calendar.time)
    }

    private fun parseDateEpochDays(dateStr: String): Long {
        if (dateStr.length < 10) return Long.MAX_VALUE / 2
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val timeMs = sdf.parse(dateStr.take(10))?.time ?: return Long.MAX_VALUE / 2
            timeMs / 86_400_000L
        } catch (e: Exception) {
            Long.MAX_VALUE / 2
        }
    }
}
