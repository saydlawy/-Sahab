package com.saydlawy.sahab.ui.sports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.saydlawy.sahab.data.sports.League
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.Standing
import com.saydlawy.sahab.data.sports.Team
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.SahabTopBar

@Composable
fun SportsScreen(
    viewModel: SportsViewModel = viewModel(),
    onClose: () -> Unit = {}
) {
    val displayMode = LocalDisplayMode.current
    val isPhonePortrait = displayMode == DisplayMode.PHONE_PORTRAIT
    val isPhoneLandscape = displayMode == DisplayMode.PHONE_LANDSCAPE

    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val subLeagues by viewModel.subLeagues.collectAsState()
    val selectedSubLeague by viewModel.selectedSubLeague.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedMatch by viewModel.selectedMatch.collectAsState()
    val standings by viewModel.standings.collectAsState()
    val standingsLoading by viewModel.standingsLoading.collectAsState()
    val standingsError by viewModel.standingsError.collectAsState()
    val standingsLeagueName by viewModel.standingsLeagueName.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showStandingsDialog by remember { mutableStateOf(false) }
    var showPortraitDetailsDialog by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF0A0F1D), Color(0xFF050811))))
        ) {
            SahabTopBar(current = Routes.SPORTS, onBack = onClose)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isPhonePortrait) 12.dp else 16.dp, vertical = if (isPhoneLandscape) 6.dp else 10.dp)
            ) {
                SportsTopBar(
                    searchQuery = searchQuery,
                    onSearchChange = viewModel::updateSearchQuery,
                    onRefresh = { viewModel.fetchMatchesFromApi(silent = false) },
                    onShowStandings = {
                        viewModel.fetchStandings()
                        showStandingsDialog = true
                    },
                    compact = isPhoneLandscape
                )

                Spacer(modifier = Modifier.height(if (isPhoneLandscape) 6.dp else 10.dp))

                if (isPhoneLandscape) {
                    // دمج الفلاتر الرئيسية والزمنية في صف واحد مضغوط للهاتف الأفقي لتوفير الارتفاع
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            MainCategoryBar(selectedCategory, viewModel::updateCategory)
                        }
                        DateFilterRow(selectedDate, viewModel::updateDate)
                    }
                } else {
                    MainCategoryBar(selectedCategory, viewModel::updateCategory)
                    if (subLeagues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        SubLeagueBar(subLeagues, selectedSubLeague, viewModel::updateSubLeague)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DateFilterBar(selectedDate, viewModel::updateDate)
                }

                if (isPhoneLandscape && subLeagues.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    SubLeagueBar(subLeagues, selectedSubLeague, viewModel::updateSubLeague)
                }

                Spacer(modifier = Modifier.height(if (isPhoneLandscape) 8.dp else 12.dp))

                when (val state = uiState) {
                    is SportsUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF3B82F6))
                        }
                    }
                    is SportsUiState.Error -> {
                        ErrorDiagnosticBox(state.message) { viewModel.fetchMatchesFromApi(silent = false) }
                    }
                    is SportsUiState.Success -> {
                        if (state.groupedMatches.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد مباريات مطابقة للبحث في هذا القسم", color = Color.Gray, fontSize = 16.sp)
                            }
                        } else if (!isPhonePortrait) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Box(modifier = Modifier.weight(0.56f).fillMaxHeight()) {
                                    MatchesList(
                                        groupedMatches = state.groupedMatches,
                                        selectedMatch = selectedMatch,
                                        onMatchClick = { viewModel.selectMatch(it) }
                                    )
                                }
                                Box(modifier = Modifier.weight(0.44f).fillMaxHeight()) {
                                    MatchDetailsPane(match = selectedMatch)
                                }
                            }
                        } else {
                            MatchesList(
                                groupedMatches = state.groupedMatches,
                                selectedMatch = selectedMatch,
                                onMatchClick = { match ->
                                    viewModel.selectMatch(match)
                                    showPortraitDetailsDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showPortraitDetailsDialog && selectedMatch != null) {
            Dialog(onDismissRequest = { showPortraitDetailsDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "تفاصيل المباراة",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(onClick = { showPortraitDetailsDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        MatchDetailsContent(match = selectedMatch!!)
                    }
                }
            }
        }

        if (showStandingsDialog) {
            StandingsDialog(
                leagueName = standingsLeagueName,
                standings = standings,
                loading = standingsLoading,
                errorMessage = standingsError,
                onDismiss = { showStandingsDialog = false }
            )
        }
    }
}

@Composable
fun ErrorDiagnosticBox(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFEF4444).copy(alpha = 0.1f))
                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(24.dp)
        ) {
            Text("⚠️ تشخيص العطل", color = Color(0xFFEF4444), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B82F6))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("إعادة المحاولة")
            }
        }
    }
}

@Composable
fun StandingsDialog(
    leagueName: String,
    standings: List<Standing>,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 240.dp, max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("جدول الترتيب", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        if (leagueName.isNotBlank()) {
                            Text(leagueName, color = Color(0xFF3B82F6), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))

                when {
                    loading && standings.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color(0xFF3B82F6))
                        }
                    }
                    standings.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                errorMessage ?: "جدول الترتيب غير متاح لهذه البطولة حالياً",
                                color = Color.Gray,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                            Text("الفريق", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("لعب", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                            Text("فاز", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                            Text("نقاط", color = Color(0xFF3B82F6), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(standings, key = { "${it.rank}_${it.teamName}" }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${row.rank}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                    Text(row.teamName, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${row.played}", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                                    Text("${row.win}", color = Color.LightGray, fontSize = 12.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                                    Text("${row.points}", color = Color(0xFF3B82F6), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SportsTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowStandings: () -> Unit,
    compact: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "⚽ مركز المباريات",
            color = Color.White,
            fontSize = if (compact) 16.sp else 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("ابحث عن فريق أو بطولة...", color = Color.Gray, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .height(if (compact) 44.dp else 48.dp)
        )
        IconButton(
            onClick = onShowStandings,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF3B82F6).copy(alpha = 0.2f))
        ) {
            Icon(Icons.Default.List, contentDescription = "جدول الترتيب", tint = Color(0xFF3B82F6))
        }
        IconButton(
            onClick = onRefresh,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "تحديث", tint = Color.White)
        }
    }
}

@Composable
fun MainCategoryBar(selected: MainCategory, onSelect: (MainCategory) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(MainCategory.values()) { category ->
            val isSelected = category == selected
            var isFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.05f))
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = if (isFocused) Color.White else Color.Transparent,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelect(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(category.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun SubLeagueBar(subLeagues: List<Pair<Int?, String>>, selectedId: Int?, onSelect: (Int?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(subLeagues) { (id, name) ->
            val isSelected = id == selectedId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.2f) else Color.White.copy(alpha = 0.03f))
                    .border(1.dp, if (isSelected) Color(0xFF10B981) else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    name,
                    color = if (isSelected) Color(0xFF10B981) else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun DateFilterBar(selected: DateFilter, onSelect: (DateFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DateFilter.values().forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.03f))
                    .border(1.dp, if (isSelected) Color.White.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(filter) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    filter.title,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun DateFilterRow(selected: DateFilter, onSelect: (DateFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DateFilter.values().forEach { filter ->
            val isSelected = filter == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f))
                    .border(1.dp, if (isSelected) Color.White.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(10.dp))
                    .clickable { onSelect(filter) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    filter.title,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MatchesList(
    groupedMatches: Map<League, List<Match>>,
    selectedMatch: Match?,
    onMatchClick: (Match) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        groupedMatches.forEach { (league, matches) ->
            stickyHeader {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B).copy(alpha = 0.95f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(league.name, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("${matches.size} مباريات", color = Color.Gray, fontSize = 11.sp)
                }
            }
            items(matches, key = { it.id }) { match ->
                MatchCard(
                    match = match,
                    isSelected = match.id == selectedMatch?.id,
                    onClick = { onMatchClick(match) }
                )
            }
        }
    }
}

@Composable
fun MatchCard(match: Match, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF3B82F6).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f))
            .border(
                width = if (isFocused || isSelected) 1.5.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) Color(0xFF3B82F6) else Color.White.copy(alpha = 0.07f),
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TeamRow(match.homeTeam, Modifier.weight(1f), Alignment.Start)
        ScoreBadge(match, Modifier.padding(horizontal = 8.dp))
        TeamRow(match.awayTeam, Modifier.weight(1f), Alignment.End)
    }
}

@Composable
fun TeamRow(team: Team, modifier: Modifier, alignment: Alignment.Horizontal) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignment == Alignment.Start) Arrangement.Start else Arrangement.End
    ) {
        if (alignment == Alignment.Start) {
            TeamLogo(team)
            Spacer(modifier = Modifier.width(8.dp))
            Text(team.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text(team.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.width(8.dp))
            TeamLogo(team)
        }
    }
}

@Composable
fun TeamLogo(team: Team) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (team.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = team.logoUrl,
                contentDescription = team.name,
                modifier = Modifier.size(24.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(team.name.take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
fun ScoreBadge(match: Match, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when (match.status) {
            MatchStatus.LIVE -> {
                Text("${match.homeScore ?: 0} - ${match.awayScore ?: 0}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text("مباشر ${match.minute ?: ""}'", color = Color(0xFFEF4444), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            MatchStatus.FINISHED -> {
                Text("${match.homeScore ?: 0} - ${match.awayScore ?: 0}", color = Color.LightGray, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(match.date.ifBlank { "انتهت" }, color = Color.Gray, fontSize = 10.sp)
            }
            else -> {
                Text(match.time.ifBlank { "--:--" }, color = Color(0xFF38BDF8), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(match.date.ifBlank { "قادمة" }, color = Color.Gray, fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun MatchDetailsPane(match: Match?) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        if (match == null) {
            Text("اختر مباراة لعرض التفاصيل", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
        } else {
            MatchDetailsContent(match = match)
        }
    }
}

@Composable
private fun MatchDetailsContent(match: Match) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(match.league.name, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                TeamLogo(match.homeTeam)
                Spacer(modifier = Modifier.height(6.dp))
                Text(match.homeTeam.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
            ScoreBadge(match, Modifier.padding(horizontal = 12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                TeamLogo(match.awayTeam)
                Spacer(modifier = Modifier.height(6.dp))
                Text(match.awayTeam.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 16.dp))
        InfoRow("📅 التاريخ", match.date.ifBlank { "غير محدد" })
        InfoRow("⏰ التوقيت", match.time.ifBlank { "غير محدد" })
        InfoRow("🏟️ الملعب", match.stadium.ifBlank { "غير محدد" })
        InfoRow("📺 القناة الناقلة", match.channel.ifBlank { "غير محدد" })
        InfoRow("🎙️ المعلق", match.commentator.ifBlank { "غير محدد" })
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
