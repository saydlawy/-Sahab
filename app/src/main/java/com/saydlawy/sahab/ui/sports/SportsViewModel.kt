package com.saydlawy.sahab.ui.sports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.saydlawy.sahab.data.sports.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class DateFilter(val label: String, val dayOffset: Int) {
    LIVE("مباشر الآن", 0),
    TODAY("مباريات اليوم", 0),
    YESTERDAY("أمس", -1),
    TOMORROW("غداً", 1)
}

sealed class SportsUiState {
    object Loading : SportsUiState()
    data class Success(val matches: List<Match>) : SportsUiState()
    data class Empty(val reason: String) : SportsUiState()
    data class Error(val errorMessage: String) : SportsUiState()
}

class SportsViewModel(app: Application) : AndroidViewModel(app) {

    private val db by lazy {
        Room.databaseBuilder(app, SportsDatabase::class.java, "sports_db")
            .fallbackToDestructiveMigration()
            .build()
    }
    private val dao = db.matchDao()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val espnApi = EspnApi(okHttpClient)
    private val apiFootball = SportsApi(okHttpClient)

    private var allFetchedMatches: List<Match> = emptyList()

    private val _uiState = MutableStateFlow<SportsUiState>(SportsUiState.Loading)
    val uiState: StateFlow<SportsUiState> = _uiState.asStateFlow()

    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedDateFilter = MutableStateFlow(DateFilter.TODAY)
    val selectedDateFilter: StateFlow<DateFilter> = _selectedDateFilter.asStateFlow()

    private val _availableLeagues = MutableStateFlow<List<String>>(listOf("كل البطولات"))
    val availableLeagues: StateFlow<List<String>> = _availableLeagues.asStateFlow()

    private val _selectedLeague = MutableStateFlow("كل البطولات")
    val selectedLeague: StateFlow<String> = _selectedLeague.asStateFlow()

    private val _selectedMatch = MutableStateFlow<Match?>(null)
    val selectedMatch: StateFlow<Match?> = _selectedMatch.asStateFlow()

    init {
        loadFromDatabaseThenFetch()
    }

    private fun loadFromDatabaseThenFetch() {
        viewModelScope.launch {
            val cached = dao.getAllMatches().map { it.toMatch() }
            if (cached.isNotEmpty()) {
                allFetchedMatches = cached
                applyLeagueFilter()
            }
            fetchMatchesFromApi()
        }
    }

    fun selectDateFilter(filter: DateFilter) {
        if (_selectedDateFilter.value == filter && _uiState.value is SportsUiState.Success) return
        _selectedDateFilter.value = filter
        _selectedLeague.value = "كل البطولات"
        fetchMatchesFromApi()
    }

    fun selectLeagueFilter(leagueName: String) {
        _selectedLeague.value = leagueName
        applyLeagueFilter()
    }

    fun selectMatch(match: Match) {
        _selectedMatch.value = match
    }

    fun fetchMatches() {
        fetchMatchesFromApi()
    }

    private fun fetchMatchesFromApi() {
        viewModelScope.launch {
            _isLoading.value = true
            if (allFetchedMatches.isEmpty()) _uiState.value = SportsUiState.Loading

            val currentFilter = _selectedDateFilter.value
            val dateStr = getFormattedDate(currentFilter.dayOffset)
            var resultMatches: List<Match> = emptyList()

            // 1. خط الدفاع الأول: ESPN (مجاني وبدون مفتاح) - فقط إذا لم نكن نبحث عن المباشر حالياً
            if (currentFilter != DateFilter.LIVE) {
                val espnDeferred = AllowedLeaguesConfig.ESPN_SLUG_TO_ID.values.map { leagueId ->
                    async { espnApi.getFixturesByDate(dateStr, leagueId) }
                }
                val espnResponses = espnDeferred.awaitAll()
                resultMatches = espnResponses
                    .filterIsInstance<ApiResult.Success<List<Match>>>()
                    .flatMap { it.data }
            }

            // 2. خط الدفاع الثاني: API-Football (إذا فشل ESPN أو كنا نطلب المباشر)
            if (resultMatches.isEmpty()) {
                val apiResult = if (currentFilter == DateFilter.LIVE) {
                    apiFootball.getLiveMatches()
                } else {
                    apiFootball.getFixturesByDate(dateStr)
                }

                when (apiResult) {
                    is ApiResult.Success -> resultMatches = apiResult.data
                    is ApiResult.Error -> {
                        if (allFetchedMatches.isEmpty()) {
                            _uiState.value = SportsUiState.Error(apiResult.message)
                        }
                        _isLoading.value = false
                        return@launch
                    }
                }
            }

            // 3. فلترة البطولات المسموح بها وحفظها في قاعدة البيانات
            val filteredByLeague = resultMatches.filter { 
                AllowedLeaguesConfig.ALLOWED_LEAGUES.contains(it.league.id) 
            }
            
            dao.insertMatches(filteredByLeague.map { it.toEntity() })
            
            // مسح البيانات الأقدم من شهر
            val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            dao.deleteOldMatches(oneMonthAgo)

            // إعادة القراءة من الكاش
            val updatedCache = dao.getAllMatches().map { it.toMatch() }
            allFetchedMatches = updatedCache

            val leagues = mutableListOf("كل البطولات")
            leagues.addAll(updatedCache.map { it.league.name }.distinct())
            _availableLeagues.value = leagues

            applyLeagueFilter()
            _isLoading.value = false
        }
    }

    private fun applyLeagueFilter() {
        val league = _selectedLeague.value
        val filtered = if (league == "كل البطولات") {
            allFetchedMatches
        } else {
            allFetchedMatches.filter { it.league.name == league }
        }

        _matches.value = filtered

        if (filtered.isEmpty()) {
            val emptyMsg = when (_selectedDateFilter.value) {
                DateFilter.LIVE -> "لا توجد مباريات جارية"
                DateFilter.TODAY -> "لا توجد مباريات اليوم"
                DateFilter.YESTERDAY -> "لا توجد مباريات أمس"
                DateFilter.TOMORROW -> "لا توجد مباريات غداً"
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

    private fun getFormattedDate(dayOffset: Int): String {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, dayOffset)
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return formatter.format(calendar.time)
    }
}
