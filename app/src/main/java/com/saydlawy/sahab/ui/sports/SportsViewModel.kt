package com.saydlawy.sahab.ui.sports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.SportsApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SportsViewModel : ViewModel() {
    private val api = SportsApi(OkHttpClient())
    
    private val _matches = MutableStateFlow<List<Match>>(emptyList())
    val matches: StateFlow<List<Match>> = _matches.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        fetchMatches()
    }

    fun fetchMatches() {
        viewModelScope.launch {
            _isLoading.value = true
            // نجلب المباريات المباشرة أولاً
            var result = api.getLiveMatches()
            
            // إذا لم توجد مباريات مباشرة، نجلب مباريات اليوم
            if (result.isEmpty()) {
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                result = api.getFixturesByDate(today)
            }
            
            _matches.value = result
            _isLoading.value = false
        }
    }
}
