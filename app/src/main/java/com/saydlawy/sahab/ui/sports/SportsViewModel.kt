package com.saydlawy.sahab.ui.sports

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import com.saydlawy.sahab.data.sports.*
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

    private val api = SportsApi(okHttpClient)

    // قائمة الـ IDs المسموح بها فقط (18 بطولة ودوري)
    private val ALLOWED_LEAGUES = setOf(
        233, 307, 12, 17, 20, 2, 3, 6, 403, 8, 1, 452, 39, 140, 135, 78, 61, 203
    )

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
            // 1. قراءة البيانات من الكاش أولاً
            val cached = dao.getAllMatches().map { it.toMatch() }
            if (cached.isNotEmpty()) {
                allFetchedMatches = cached
                applyLeagueFilter()
            }
            // 2. جلب البيانات الجديدة في الخلفية
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
            val result: ApiResult<List<Match>> = if (currentFilter == DateFilter.LIVE) {
                api.getLiveMatches()
            } else {
                api.getFixturesByDate(getFormattedDate(currentFilter.dayOffset))
            }

            when (result) {
                is ApiResult.Success -> {
                    // فلترة النتائج لتشمل البطولات المسموح بها فقط
                    val filteredByLeague = result.data.filter { ALLOWED_LEAGUES.contains(it.league.id) }
                    
                    // حفظ في قاعدة البيانات
                    dao.insertMatches(filteredByLeague.map { it.toEntity() })
                    
                    // مسح البيانات الأقدم من شهر
                    val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
                    dao.deleteOldMatches(oneMonthAgo)

                    // إعادة القراءة من الكاش بعد التحديث
                    val updatedCache = dao.getAllMatches().map { it.toMatch() }
                    allFetchedMatches = updatedCache

                    val leagues = mutableListOf("كل البطولات")
                    leagues.addAll(updatedCache.map { it.league.name }.distinct())
                    _availableLeagues.value = leagues

                    applyLeagueFilter()
                }
                is ApiResult.Error -> {
                    if (allFetchedMatches.isEmpty()) {
                        _uiState.value = SportsUiState.Error(result.message)
                    }
                }
            }
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
