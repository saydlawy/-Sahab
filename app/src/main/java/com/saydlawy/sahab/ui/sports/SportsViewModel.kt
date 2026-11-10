package com.saydlawy.sahab.ui.sports

import android.app.Application
import com.saydlawy.sahab.BuildConfig
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.saydlawy.sahab.data.sports.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class MainCategory(val label: String) {
    EGYPTIAN("الدوري المصري"),
    AFRICAN_CLUBS("البطولات الأفريقية للأندية"),
    AFRICAN_NATIONS("البطولات الأفريقية الدولية"),
    OTHER("بطولات أخرى")
}

enum class TimeFilter(val label: String) {
    LIVE("مباشر الآن"),
    TODAY("مباريات اليوم"),
    TOMORROW("مباريات الغد"),
    END_OF_MONTH("لنهاية الشهر")
}

sealed class SportsUiState {
    object Loading : SportsUiState()
    data class Success(val matches: List<Match>) : SportsUiState()
    data class Empty(val reason: String) : SportsUiState()
    data class Error(val errorMessage: String) : SportsUiState()
}

class SportsViewModel(app: Application) : AndroidViewModel(app) {

    private val db by lazy {
        try {
            val dbFile = app.getDatabasePath("sports_db")
            if (!dbFile.exists()) {
                dbFile.parentFile?.mkdirs()
                app.assets.open("databases/sports_db").use { input ->
                    java.io.FileOutputStream(dbFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Room.databaseBuilder(app, SportsDatabase::class.java, "sports_db")
                .fallbackToDestructiveMigration()
                .build()
        } catch (e: Exception) {
            android.util.Log.e("SportsVM", "Fatal DB Init Error", e)
            throw e // سيتم التقاطه في init
        }
    }
    private val dao by lazy { db.matchDao() }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val morphTvApi = MorphTvApi(okHttpClient)
    private val theSportsDbApi = TheSportsDbApi(okHttpClient)
    private val apiFootball = SportsApi(okHttpClient)
    private val syncManager by lazy { SportsSyncManager(dao, okHttpClient) }

    private var allFetchedMatches: List<Match> = emptyList()

    private val _uiState = MutableStateFlow<SportsUiState>(SportsUiState.Loading)
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()

    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedCategory = MutableStateFlow(MainCategory.EGYPTIAN)
    val selectedCategory: StateFlow<MainCategory> = _selectedCategory.asStateFlow()

    private val _selectedLeagueId = MutableStateFlow(233)
    val selectedLeagueId: StateFlow<Int> = _selectedLeagueId.asStateFlow()

    private val _availableLeaguesInCategory = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val availableLeaguesInCategory: StateFlow<List<Pair<Int, String>>> = _availableLeaguesInCategory.asStateFlow()

    private val _selectedTimeFilter = MutableStateFlow(TimeFilter.TODAY)
    val selectedTimeFilter: StateFlow<TimeFilter> = _selectedTimeFilter.asStateFlow()

    private val _selectedMatch = MutableStateFlow<Match?>(null)

    private val _standings = MutableStateFlow<List<Standing>>(emptyList())
    val standings: StateFlow<List<Standing>> = _standings.asStateFlow()

    private val _showStandings = MutableStateFlow(false)
    val showStandings: StateFlow<Boolean> = _showStandings.asStateFlow()

    private val _isStandingsLoading = MutableStateFlow(false)
    val isStandingsLoading: StateFlow<Boolean> = _isStandingsLoading.asStateFlow()
    val selectedMatch: StateFlow<Match?> = _selectedMatch.asStateFlow()

    private val _totalMatches = MutableStateFlow(0)
    val totalMatches: StateFlow<Int> = _totalMatches.asStateFlow()

        init {
        updateCategory(MainCategory.EGYPTIAN)
        
        viewModelScope.launch {
            try {
                // 1. تحميل البيانات المحلية فوراً
                val cached = dao.getAllMatches().map { it.toMatch() }
                if (cached.isNotEmpty()) {
                    allFetchedMatches = cached
                    applyLocalFilters()
                }
            } catch (e: Exception) {
                android.util.Log.e("SportsViewModel", "DB Read failed", e)
                _uiState.value = SportsUiState.Error("خطأ في قاعدة البيانات: ${e.message}")
                return@launch
            }

            // 2. تشغيل المزامنة بعد التأكد من سلامة قاعدة البيانات
            try {
                val syncManager = SportsSyncManager(dao, okHttpClient)
                syncManager.runMonthlySync(
                    openFootKey = BuildConfig.OPENFOOT_API_KEY,
                    highlightlyKey = BuildConfig.HIGHLIGHTLY_API_KEY
                )
                val updatedCache = dao.getAllMatches().map { it.toMatch() }
                allFetchedMatches = updatedCache
                applyLocalFilters()
            } catch (e: Exception) {
                android.util.Log.e("SportsViewModel", "Sync failed", e)
            }
        }
    }

    fun updateCategory(category: MainCategory) {
        _selectedCategory.value = category
        val leagues = when (category) {
            MainCategory.EGYPTIAN -> listOf(233 to "الدوري المصري الممتاز")
            MainCategory.AFRICAN_CLUBS -> listOf(12 to "دوري أبطال أفريقيا", 20 to "كأس الكونفدرالية")
            MainCategory.AFRICAN_NATIONS -> listOf(6 to "كأس الأمم الأفريقية")
            MainCategory.OTHER -> listOf(
                2 to "دوري أبطال أوروبا", 3 to "الدوري الأوروبي", 39 to "الدوري الإنجليزي",
                140 to "الدوري الإسباني", 135 to "الدوري الإيطالي", 78 to "الدوري الألماني",
                61 to "الدوري الفرنسي", 203 to "الدوري التركي", 1 to "كأس العالم", 452 to "كأس العرب"
            )
        }
        _availableLeaguesInCategory.value = leagues
        _selectedLeagueId.value = leagues.first().first
        fetchMatchesFromApi()
    }

    fun updateLeague(leagueId: Int) {
        _selectedLeagueId.value = leagueId
        fetchMatchesFromApi()
    }

    fun updateTimeFilter(filter: TimeFilter) {
        _selectedTimeFilter.value = filter
        fetchMatchesFromApi()
    }

    fun selectMatch(match: Match) {
        _selectedMatch.value = match
    }

    fun fetchMatches() {
        fetchMatchesFromApi()
    }


    fun fetchStandings() {
        viewModelScope.launch {
            val tsdbId = AllowedLeaguesConfig.THESPORTSDB_LEAGUE_ID[_selectedLeagueId.value]
            if (tsdbId == null) {
                _uiState.value = SportsUiState.Error("جدول الترتيب غير متاح لهذه البطولة حالياً")
                return@launch
            }
            _isStandingsLoading.value = true
            _showStandings.value = true
            val result = theSportsDbApi.getStandings(tsdbId, "2024-2025")
            when (result) {
                is ApiResult.Success -> _standings.value = result.data
                is ApiResult.Error -> {
                    _standings.value = emptyList()
                    _uiState.value = SportsUiState.Error(result.message)
                }
            }
            _isStandingsLoading.value = false
        }
    }

    fun hideStandings() {
        _showStandings.value = false
    }

    private fun loadFromDatabaseThenFetch() {
        viewModelScope.launch {
            val cached = dao.getAllMatches().map { it.toMatch() }
            if (cached.isNotEmpty()) {
                allFetchedMatches = cached
                applyLocalFilters()
            }
            fetchMatchesFromApi()
        }
    }

    private fun fetchMatchesFromApi() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _isLoading.value = true
                if (allFetchedMatches.isEmpty()) _uiState.value = SportsUiState.Loading

                val leagueId = _selectedLeagueId.value
                val currentFilter = _selectedTimeFilter.value
                var resultMatches: List<Match> = emptyList()

                // 1. Morph TV
                val morphResult = morphTvApi.getMatches()
                if (morphResult is ApiResult.Success) {
                    resultMatches = morphResult.data.filter { it.league.id == leagueId }
                }

                // 2. API-Football (احتياطي)
                if (resultMatches.isEmpty()) {
                    val apiResult = when (currentFilter) {
                        TimeFilter.LIVE -> apiFootball.getLiveMatches()
                        TimeFilter.TODAY -> apiFootball.getFixturesByDate(getFormattedDate(Calendar.getInstance()))
                        TimeFilter.TOMORROW -> {
                            val cal = Calendar.getInstance(); cal.add(Calendar.DAY_OF_YEAR, 1)
                            apiFootball.getFixturesByDate(getFormattedDate(cal))
                        }
                        TimeFilter.END_OF_MONTH -> {
                            val from = getFormattedDate(Calendar.getInstance())
                            val calEnd = Calendar.getInstance()
                            calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
                            apiFootball.getFixturesByDateRange(from, getFormattedDate(calEnd), leagueId)
                        }
                    }
                    if (apiResult is ApiResult.Success) {
                        resultMatches = apiResult.data.filter { it.league.id == leagueId }
                    }
                }

                // 3. الحفظ في قاعدة البيانات (محمي)
                try {
                    dao.insertMatches(resultMatches.map { it.toEntity() })
                    dao.deleteOldMatches(System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000))
                } catch (e: Exception) {
                    android.util.Log.e("SportsVM", "DB Insert failed: ${e.message}", e)
                }

                // 4. إعادة القراءة من الكاش (محمي)
                try {
                    allFetchedMatches = dao.getAllMatches().map { it.toMatch() }
                } catch (e: Exception) {
                    android.util.Log.e("SportsVM", "DB Read failed: ${e.message}", e)
                }

                applyLocalFilters()
                _isLoading.value = false
            } catch (e: Exception) {
                android.util.Log.e("SportsVM", "Fetch failed: ${e.message}", e)
                _isLoading.value = false
                _uiState.value = SportsUiState.Error("حدث خطأ أثناء جلب البيانات: ${e.message}")
            }
        }
    }

    private fun applyLocalFilters() {
        _totalMatches.value = allFetchedMatches.size
        val currentLeagueId = _selectedLeagueId.value
        val currentFilter = _selectedTimeFilter.value
        
        var filtered = allFetchedMatches.filter { it.league.id == currentLeagueId }

        // فلترة حسب التاريخ للطبقة القادمة من Morph TV
        if (currentFilter != TimeFilter.LIVE && currentFilter != TimeFilter.END_OF_MONTH) {
            val targetDate = if (currentFilter == TimeFilter.TODAY) {
                getFormattedDate(Calendar.getInstance())
            } else {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, 1)
                getFormattedDate(cal)
            }
            filtered = filtered.filter { it.date == targetDate }
        } else if (currentFilter == TimeFilter.LIVE) {
            filtered = filtered.filter { it.status == MatchStatus.LIVE }
        }

        _matches.value = filtered

        if (filtered.isEmpty()) {
            val emptyMsg = when (currentFilter) {
                TimeFilter.LIVE -> "لا توجد مباريات جارية"
                TimeFilter.TODAY -> "لا توجد مباريات اليوم"
                TimeFilter.TOMORROW -> "لا توجد مباريات غداً"
                TimeFilter.END_OF_MONTH -> "لا توجد مباريات مسجلة لهذه الفترة"
            }
            _uiState.value = SportsUiState.Empty(emptyMsg)
            _selectedMatch.value = null
        } else {
            _uiState.value = SportsUiState.Success(filtered)
            if (_selectedMatch.value == null || !filtered.any { it.id == _selectedMatch.value?.id }) {
                _selectedMatch.value = filtered.first()
            }
        }
    }

    private fun getFormattedDate(calendar: Calendar): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(calendar.time)
    }
}
