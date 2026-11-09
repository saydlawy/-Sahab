package com.saydlawy.sahab.ui.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.data.sports.ApiResult
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.SportsApi
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

class SportsViewModel : ViewModel() {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val api = SportsApi(okHttpClient)

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
        fetchMatches()
    }

    fun selectDateFilter(filter: DateFilter) {
        if (_selectedDateFilter.value == filter && _uiState.value is SportsUiState.Success) return
        _selectedDateFilter.value = filter
        _selectedLeague.value = "كل البطولات"
        fetchMatches()
    }

    fun selectLeagueFilter(leagueName: String) {
        _selectedLeague.value = leagueName
        applyLeagueFilter()
    }

    fun selectMatch(match: Match) {
        _selectedMatch.value = match
    }

    fun fetchMatches() {
        viewModelScope.launch {
            _isLoading.value = true
            _uiState.value = SportsUiState.Loading

            val currentFilter = _selectedDateFilter.value
            val result: ApiResult<List<Match>> = if (currentFilter == DateFilter.LIVE) {
                api.getLiveMatches()
            } else {
                val targetDate = getFormattedDate(currentFilter.dayOffset)
                api.getFixturesByDate(targetDate)
            }

            when (result) {
                is ApiResult.Success -> {
                    val sortedMatches = result.data.sortedWith(
                        compareBy<Match> {
                            when (it.status) {
                                MatchStatus.LIVE -> 0
                                MatchStatus.UPCOMING -> 1
                                MatchStatus.FINISHED -> 2
                                else -> 3
                            }
                        }.thenBy { it.time }
                    )

                    allFetchedMatches = sortedMatches

                    val leagues = mutableListOf("كل البطولات")
                    leagues.addAll(sortedMatches.map { it.league.name }.distinct())
                    _availableLeagues.value = leagues

                    applyLeagueFilter()
                }
                is ApiResult.Error -> {
                    allFetchedMatches = emptyList()
                    _matches.value = emptyList()
                    _uiState.value = SportsUiState.Error(result.message)
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
                DateFilter.LIVE -> "لا توجد مباريات جارية في هذه اللحظة"
                DateFilter.TODAY -> "لا توجد مباريات مسجلة لهذا اليوم"
                DateFilter.YESTERDAY -> "لا توجد مباريات مسجلة ليوم أمس"
                DateFilter.TOMORROW -> "لا توجد مباريات مسجلة ليوم الغد"
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
