package com.saydlawy.sahab.ui.sports

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.saydlawy.sahab.data.sports.League
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStats
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.Standing
import com.saydlawy.sahab.data.sports.Team
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.SahabTopBar
import java.util.Locale

// Palette derived directly from sport/DESIGN.md and sport/code.html
private val SportSurface = Color(0xFF0C0919)
private val SportContainer = Color(0xFF151026)
private val SportContainerHigh = Color(0xFF1F1838)
private val SportContainerHighest = Color(0xFF2B224C)
private val SportPrimary = Color(0xFFDCB8FF)
private val SportViolet = Color(0xFF8A2BE2)
private val SportVioletDark = Color(0xFF7928CA)
private val SportCyan = Color(0xFF00FBFB)
private val SportLiveRed = Color(0xFFFF334B)
private val SportGold = Color(0xFFF5A623)
private val SportEmerald = Color(0xFF34D399)
private val SportTextMuted = Color(0xFFA9AEC7)

@Composable
fun SportsScreen(
    viewModel: SportsViewModel = viewModel(),
    onClose: () -> Unit = {},
    onOpenLive: () -> Unit = {},
) {
    val displayMode = LocalDisplayMode.current
    val windowInfo = LocalWindowInfo.current
    // Scale cleanly for Tablets and 40" - 90" TVs without breaking phone layouts
    val largeScale = when {
        displayMode != DisplayMode.TABLET_OR_TV -> 1f
        windowInfo.widthDp >= 1440.dp -> 1.22f
        windowInfo.widthDp >= 1100.dp -> 1.10f
        else -> 1f
    }

    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val subLeagues by viewModel.subLeagues.collectAsState()
    val selectedSubLeague by viewModel.selectedSubLeague.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val dateFilterCounts by viewModel.dateFilterCounts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedMatch by viewModel.selectedMatch.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val standings by viewModel.standings.collectAsState()
    val standingsLoading by viewModel.standingsLoading.collectAsState()
    val standingsError by viewModel.standingsError.collectAsState()
    val standingsLeagueName by viewModel.standingsLeagueName.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showStandingsDialog by remember { mutableStateOf(false) }
    var showPortraitDetailsDialog by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }

    // Available filters driven by local DB availability (always keep Yesterday/Today/Tomorrow, plus 7..90 when available)
    val availableDateFilters = remember(dateFilterCounts, selectedDate) {
        val core = setOf(DateFilter.YESTERDAY, DateFilter.TODAY, DateFilter.TOMORROW)
        DateFilter.values().filter { df ->
            df in core || df == selectedDate || (dateFilterCounts[df] ?: 0) > 0
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SportSurface),
        ) {
            // Ambient Neon Cosmic Backdrops from sport/code.html
            Box(
                Modifier
                    .size(460.dp)
                    .offset(x = (-90).dp, y = (-120).dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(SportViolet.copy(alpha = 0.16f), Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(420.dp)
                    .offset(x = 60.dp, y = 120.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(SportCyan.copy(alpha = 0.10f), Color.Transparent))),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                SahabTopBar(current = Routes.SPORTS, onBack = onClose)

                when (displayMode) {
                    DisplayMode.PHONE_PORTRAIT -> {
                        PhonePortraitSportsLayout(
                            selectedCategory = selectedCategory,
                            subLeagues = subLeagues,
                            selectedSubLeague = selectedSubLeague,
                            availableDateFilters = availableDateFilters,
                            selectedDate = selectedDate,
                            dateFilterCounts = dateFilterCounts,
                            searchQuery = searchQuery,
                            showSearchField = showSearchField,
                            onToggleSearch = { showSearchField = !showSearchField },
                            onSearchChange = viewModel::updateSearchQuery,
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.fetchMatchesFromApi(silent = false) },
                            onShowStandings = {
                                viewModel.fetchStandings()
                                showStandingsDialog = true
                            },
                            onSelectCategory = viewModel::updateCategory,
                            onSelectSubLeague = viewModel::updateSubLeague,
                            onSelectDate = viewModel::updateDate,
                            uiState = uiState,
                            selectedMatch = selectedMatch,
                            onMatchClick = { match ->
                                viewModel.selectMatch(match)
                                showPortraitDetailsDialog = true
                            },
                        )
                    }

                    DisplayMode.PHONE_LANDSCAPE -> {
                        PhoneLandscapeSportsLayout(
                            selectedCategory = selectedCategory,
                            subLeagues = subLeagues,
                            selectedSubLeague = selectedSubLeague,
                            availableDateFilters = availableDateFilters,
                            selectedDate = selectedDate,
                            dateFilterCounts = dateFilterCounts,
                            searchQuery = searchQuery,
                            showSearchField = showSearchField,
                            onToggleSearch = { showSearchField = !showSearchField },
                            onSearchChange = viewModel::updateSearchQuery,
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.fetchMatchesFromApi(silent = false) },
                            onShowStandings = {
                                viewModel.fetchStandings()
                                showStandingsDialog = true
                            },
                            onSelectCategory = viewModel::updateCategory,
                            onSelectSubLeague = viewModel::updateSubLeague,
                            onSelectDate = viewModel::updateDate,
                            uiState = uiState,
                            selectedMatch = selectedMatch,
                            onMatchClick = viewModel::selectMatch,
                            onWatchLive = onOpenLive,
                        )
                    }

                    DisplayMode.TABLET_OR_TV -> {
                        LargeScreenSportsLayout(
                            scale = largeScale,
                            selectedCategory = selectedCategory,
                            subLeagues = subLeagues,
                            selectedSubLeague = selectedSubLeague,
                            availableDateFilters = availableDateFilters,
                            selectedDate = selectedDate,
                            dateFilterCounts = dateFilterCounts,
                            searchQuery = searchQuery,
                            showSearchField = showSearchField,
                            onToggleSearch = { showSearchField = !showSearchField },
                            onSearchChange = viewModel::updateSearchQuery,
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.fetchMatchesFromApi(silent = false) },
                            onShowStandings = {
                                viewModel.fetchStandings()
                                showStandingsDialog = true
                            },
                            onSelectCategory = viewModel::updateCategory,
                            onSelectSubLeague = viewModel::updateSubLeague,
                            onSelectDate = viewModel::updateDate,
                            uiState = uiState,
                            selectedMatch = selectedMatch,
                            onMatchClick = viewModel::selectMatch,
                            onWatchLive = onOpenLive,
                        )
                    }
                }
            }
        }
    }

    if (showPortraitDetailsDialog && selectedMatch != null) {
        Dialog(onDismissRequest = { showPortraitDetailsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SportContainer,
                border = BorderStroke(1.dp, SportViolet.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "مركز تفاصيل المباراة",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        IconButton(onClick = { showPortraitDetailsDialog = false }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LiveMatchHubContent(
                        match = selectedMatch!!,
                        scale = 1f,
                        onOpenStandings = {
                            showPortraitDetailsDialog = false
                            viewModel.fetchStandings()
                            showStandingsDialog = true
                        },
                        onWatchLive = {
                            showPortraitDetailsDialog = false
                            onOpenLive()
                        },
                    )
                }
            }
        }
    }

    if (showStandingsDialog) {
        StandingsDialog(
            leagueName = standingsLeagueName,
            standings = standings,
            isLoading = standingsLoading,
            errorMessage = standingsError,
            onDismiss = { showStandingsDialog = false },
        )
    }
}

@Composable
private fun PhonePortraitSportsLayout(
    selectedCategory: MainCategory,
    subLeagues: List<Pair<Int?, String>>,
    selectedSubLeague: Int?,
    availableDateFilters: List<DateFilter>,
    selectedDate: DateFilter,
    dateFilterCounts: Map<DateFilter, Int>,
    searchQuery: String,
    showSearchField: Boolean,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onShowStandings: () -> Unit,
    onSelectCategory: (MainCategory) -> Unit,
    onSelectSubLeague: (Int?) -> Unit,
    onSelectDate: (DateFilter) -> Unit,
    uiState: SportsUiState,
    selectedMatch: Match?,
    onMatchClick: (Match) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SportsHeaderToolbar(
            scale = 1f,
            compact = true,
            showSearchField = showSearchField,
            searchQuery = searchQuery,
            isRefreshing = isRefreshing,
            onToggleSearch = onToggleSearch,
            onSearchChange = onSearchChange,
            onRefresh = onRefresh,
            onShowStandings = onShowStandings,
        )
        MainCategoryBar(selectedCategory, onSelectCategory, scale = 1f)
        DateFilterBar(availableDateFilters, selectedDate, dateFilterCounts, onSelectDate, scale = 1f)
        if (subLeagues.isNotEmpty()) {
            SubLeagueBar(subLeagues, selectedSubLeague, onSelectSubLeague, scale = 1f)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            SportsStateBody(
                uiState = uiState,
                selectedMatch = selectedMatch,
                scale = 1f,
                compactCard = true,
                onMatchClick = onMatchClick,
                onRetry = onRefresh,
            )
        }
    }
}

@Composable
private fun PhoneLandscapeSportsLayout(
    selectedCategory: MainCategory,
    subLeagues: List<Pair<Int?, String>>,
    selectedSubLeague: Int?,
    availableDateFilters: List<DateFilter>,
    selectedDate: DateFilter,
    dateFilterCounts: Map<DateFilter, Int>,
    searchQuery: String,
    showSearchField: Boolean,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onShowStandings: () -> Unit,
    onSelectCategory: (MainCategory) -> Unit,
    onSelectSubLeague: (Int?) -> Unit,
    onSelectDate: (DateFilter) -> Unit,
    uiState: SportsUiState,
    selectedMatch: Match?,
    onMatchClick: (Match) -> Unit,
    onWatchLive: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SportsHeaderToolbar(
            scale = 0.92f,
            compact = true,
            showSearchField = showSearchField,
            searchQuery = searchQuery,
            isRefreshing = isRefreshing,
            onToggleSearch = onToggleSearch,
            onSearchChange = onSearchChange,
            onRefresh = onRefresh,
            onShowStandings = onShowStandings,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                MainCategoryBar(selectedCategory, onSelectCategory, scale = 0.92f)
            }
            Box(Modifier.weight(1f)) {
                DateFilterBar(availableDateFilters, selectedDate, dateFilterCounts, onSelectDate, scale = 0.92f)
            }
        }
        if (subLeagues.isNotEmpty()) {
            SubLeagueBar(subLeagues, selectedSubLeague, onSelectSubLeague, scale = 0.92f)
        }
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(0.57f).fillMaxHeight()) {
                SportsStateBody(
                    uiState = uiState,
                    selectedMatch = selectedMatch,
                    scale = 0.94f,
                    compactCard = true,
                    onMatchClick = onMatchClick,
                    onRetry = onRefresh,
                )
            }
            Box(Modifier.weight(0.43f).fillMaxHeight()) {
                LiveMatchHubPane(
                    match = selectedMatch,
                    scale = 0.92f,
                    onOpenStandings = onShowStandings,
                    onWatchLive = onWatchLive,
                )
            }
        }
    }
}

@Composable
private fun LargeScreenSportsLayout(
    scale: Float,
    selectedCategory: MainCategory,
    subLeagues: List<Pair<Int?, String>>,
    selectedSubLeague: Int?,
    availableDateFilters: List<DateFilter>,
    selectedDate: DateFilter,
    dateFilterCounts: Map<DateFilter, Int>,
    searchQuery: String,
    showSearchField: Boolean,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onShowStandings: () -> Unit,
    onSelectCategory: (MainCategory) -> Unit,
    onSelectSubLeague: (Int?) -> Unit,
    onSelectDate: (DateFilter) -> Unit,
    uiState: SportsUiState,
    selectedMatch: Match?,
    onMatchClick: (Match) -> Unit,
    onWatchLive: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = (24 * scale).dp, vertical = (10 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((10 * scale).dp),
    ) {
        SportsHeaderToolbar(
            scale = scale,
            compact = false,
            showSearchField = showSearchField,
            searchQuery = searchQuery,
            isRefreshing = isRefreshing,
            onToggleSearch = onToggleSearch,
            onSearchChange = onSearchChange,
            onRefresh = onRefresh,
            onShowStandings = onShowStandings,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((12 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(0.52f)) {
                MainCategoryBar(selectedCategory, onSelectCategory, scale = scale)
            }
            Box(Modifier.weight(0.48f)) {
                DateFilterBar(availableDateFilters, selectedDate, dateFilterCounts, onSelectDate, scale = scale)
            }
        }

        if (subLeagues.isNotEmpty()) {
            SubLeagueBar(subLeagues, selectedSubLeague, onSelectSubLeague, scale = scale)
        }

        // 12-Column Grid equivalent (7 cols Fixtures + 5 cols Live Match Hub)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((18 * scale).dp),
        ) {
            Box(Modifier.weight(7f).fillMaxHeight()) {
                SportsStateBody(
                    uiState = uiState,
                    selectedMatch = selectedMatch,
                    scale = scale,
                    compactCard = false,
                    onMatchClick = onMatchClick,
                    onRetry = onRefresh,
                )
            }
            Box(Modifier.weight(5f).fillMaxHeight()) {
                LiveMatchHubPane(
                    match = selectedMatch,
                    scale = scale,
                    onOpenStandings = onShowStandings,
                    onWatchLive = onWatchLive,
                )
            }
        }
    }
}

@Composable
private fun SportsHeaderToolbar(
    scale: Float,
    compact: Boolean,
    showSearchField: Boolean,
    searchQuery: String,
    isRefreshing: Boolean,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowStandings: () -> Unit,
) {
    val spinTransition = rememberInfiniteTransition(label = "refreshSpin")
    val spinAngle by spinTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "refreshAngle",
    )

    Column(verticalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
            ) {
                Text(
                    "مركز المباريات والرياضة",
                    color = Color.White,
                    fontSize = if (compact) 15.sp else (18 * scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
                if (!compact) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SportContainer)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                            .padding(horizontal = (10 * scale).dp, vertical = (4 * scale).dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(SportEmerald))
                        Text(
                            "بيانات محدثة • 2026+",
                            color = SportTextMuted,
                            fontSize = (11 * scale).sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
            ) {
                ActionPillButton(
                    icon = Icons.Default.Search,
                    label = if (compact) null else "بحث",
                    highlighted = showSearchField || searchQuery.isNotBlank(),
                    scale = scale,
                    onClick = onToggleSearch,
                )
                ActionPillButton(
                    icon = Icons.Default.List,
                    label = if (compact) "الترتيب" else "جدول الترتيب",
                    highlighted = true,
                    scale = scale,
                    onClick = onShowStandings,
                )
                ActionPillButton(
                    icon = Icons.Default.Refresh,
                    label = if (isRefreshing) "جاري التحديث..." else if (compact) "تحديث" else "جلب التحديث",
                    highlighted = false,
                    accentCyan = true,
                    iconRotation = if (isRefreshing) spinAngle else 0f,
                    scale = scale,
                    onClick = onRefresh,
                )
            }
        }

        if (showSearchField) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("ابحث عن نادي أو بطولة...", color = SportTextMuted, fontSize = (13 * scale).sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SportCyan) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = "مسح", tint = SportTextMuted)
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height((48 * scale).dp),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SportCyan,
                    unfocusedBorderColor = SportViolet.copy(alpha = 0.4f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = SportContainer,
                    unfocusedContainerColor = SportContainer,
                ),
            )
        }
    }
}

@Composable
private fun ActionPillButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String?,
    highlighted: Boolean,
    scale: Float,
    accentCyan: Boolean = false,
    iconRotation: Float = 0f,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Brush.horizontalGradient(listOf(SportViolet, SportVioletDark))
        highlighted -> Brush.horizontalGradient(listOf(SportViolet.copy(alpha = 0.30f), SportContainerHigh))
        else -> Brush.horizontalGradient(listOf(SportContainerHigh, SportContainer))
    }
    val borderColor = when {
        focused -> SportCyan
        accentCyan -> SportCyan.copy(alpha = 0.45f)
        highlighted -> SportViolet.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = (if (label == null) 10f else 12f * scale).dp, vertical = (8f * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (accentCyan) SportCyan else SportPrimary,
            modifier = Modifier
                .size((17 * scale).dp)
                .rotate(iconRotation),
        )
        if (label != null) {
            Text(
                label,
                color = Color.White,
                fontSize = (12 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MainCategoryBar(selected: MainCategory, onSelect: (MainCategory) -> Unit, scale: Float) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
        items(MainCategory.values()) { category ->
            val isSelected = category == selected
            var isFocused by remember { mutableStateOf(false) }
            val fill = when {
                isSelected -> Brush.horizontalGradient(listOf(SportViolet, SportVioletDark))
                isFocused -> Brush.horizontalGradient(listOf(SportContainerHighest, SportContainerHigh))
                else -> Brush.horizontalGradient(listOf(SportContainer, SportContainer))
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(fill)
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = when {
                            isFocused -> SportCyan
                            isSelected -> SportPrimary.copy(alpha = 0.55f)
                            else -> Color.White.copy(alpha = 0.08f)
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelect(category) }
                    .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isSelected) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(SportCyan))
                }
                Text(
                    category.title,
                    color = if (isSelected || isFocused) Color.White else SportTextMuted,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = (12.5f * scale).sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun DateFilterBar(
    filters: List<DateFilter>,
    selected: DateFilter,
    counts: Map<DateFilter, Int>,
    onSelect: (DateFilter) -> Unit,
    scale: Float,
) {
    LazyRow(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SportContainer)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy((4 * scale).dp),
    ) {
        items(filters) { filter ->
            val isSelected = filter == selected
            var isFocused by remember { mutableStateOf(false) }
            val count = counts[filter] ?: 0
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isSelected) SportViolet else if (isFocused) SportContainerHigh else Color.Transparent)
                    .border(
                        width = if (isFocused) 1.5.dp else 0.dp,
                        color = if (isFocused) SportCyan else Color.Transparent,
                        shape = RoundedCornerShape(9.dp),
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelect(filter) }
                    .padding(horizontal = (10 * scale).dp, vertical = (6 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    filter.title,
                    color = if (isSelected || isFocused) Color.White else SportTextMuted,
                    fontSize = (11.5f * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                if (count > 0) {
                    Text(
                        "$count",
                        color = SportCyan,
                        fontSize = (10 * scale).sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SportSurface.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubLeagueBar(
    subLeagues: List<Pair<Int?, String>>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    scale: Float,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Text(
                "البطولات:",
                color = SportTextMuted,
                fontSize = (11 * scale).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        items(subLeagues) { (id, name) ->
            val isSelected = id == selectedId
            var isFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        when {
                            isSelected -> SportViolet.copy(alpha = 0.28f)
                            isFocused -> SportContainerHigh
                            else -> SportContainer
                        },
                    )
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = when {
                            isFocused -> SportCyan
                            isSelected -> SportViolet.copy(alpha = 0.65f)
                            else -> Color.White.copy(alpha = 0.06f)
                        },
                        shape = RoundedCornerShape(9.dp),
                    )
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelect(id) }
                    .padding(horizontal = (11 * scale).dp, vertical = (5 * scale).dp),
            ) {
                Text(
                    name,
                    color = if (isSelected) SportPrimary else Color.White.copy(alpha = 0.85f),
                    fontSize = (11.5f * scale).sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SportsStateBody(
    uiState: SportsUiState,
    selectedMatch: Match?,
    scale: Float,
    compactCard: Boolean,
    onMatchClick: (Match) -> Unit,
    onRetry: () -> Unit,
) {
    when (uiState) {
        is SportsUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = SportCyan, trackColor = SportViolet.copy(alpha = 0.25f))
            }
        }
        is SportsUiState.Error -> {
            ErrorDiagnosticBox(uiState.message, onRetry)
        }
        is SportsUiState.Success -> {
            if (uiState.groupedMatches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(18.dp))
                        .background(SportContainer.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "لا توجد مباريات في النطاق المحدد",
                            color = Color.White,
                            fontSize = (15 * scale).sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "جرّب نطاقاً زمنياً أوسع (مثل 7 أو 28 يوماً) أو اضغط جلب التحديث",
                            color = SportTextMuted,
                            fontSize = (12 * scale).sp,
                        )
                    }
                }
            } else {
                MatchesList(
                    groupedMatches = uiState.groupedMatches,
                    selectedMatch = selectedMatch,
                    scale = scale,
                    compactCard = compactCard,
                    onMatchClick = onMatchClick,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchesList(
    groupedMatches: Map<League, List<Match>>,
    selectedMatch: Match?,
    scale: Float,
    compactCard: Boolean,
    onMatchClick: (Match) -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy((10 * scale).dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        groupedMatches.forEach { (league, matches) ->
            stickyHeader(key = "hdr_${league.id}_${league.name}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SportContainerHigh.copy(alpha = 0.96f))
                        .border(1.dp, SportViolet.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                        .padding(horizontal = (12 * scale).dp, vertical = (7 * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(SportViolet))
                        Text(
                            league.name,
                            color = SportPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = (13 * scale).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${matches.size} مباراة",
                        color = SportCyan,
                        fontSize = (11 * scale).sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            items(matches, key = { it.id }) { match ->
                SportMatchCard(
                    match = match,
                    isSelected = match.id == selectedMatch?.id,
                    scale = scale,
                    compact = compactCard,
                    onClick = { onMatchClick(match) },
                )
            }
        }
    }
}

@Composable
private fun SportMatchCard(
    match: Match,
    isSelected: Boolean,
    scale: Float,
    compact: Boolean,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val active = isSelected || isFocused
    val shape = RoundedCornerShape((16 * scale).dp)

    val bg = if (active) {
        Brush.linearGradient(
            listOf(SportViolet.copy(alpha = 0.24f), SportContainer.copy(alpha = 0.96f)),
        )
    } else {
        Brush.verticalGradient(listOf(SportContainer.copy(alpha = 0.88f), SportContainer.copy(alpha = 0.88f)))
    }
    val borderColor = when {
        isFocused -> SportCyan
        isSelected -> SportViolet
        else -> Color.White.copy(alpha = 0.08f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (active) {
                    Modifier.shadow(
                        elevation = (16 * scale).dp,
                        shape = shape,
                        ambientColor = SportViolet,
                        spotColor = SportViolet,
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(bg)
            .border(if (active) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding((if (compact) 11f else 14f * scale).dp),
        verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
    ) {
        // Card Header: Championship & Time / Live Status & Broadcast Channel
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (match.date.isNotBlank()) {
                    Text(
                        match.date,
                        color = SportTextMuted,
                        fontSize = (10.5f * scale).sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (match.stadium.isNotBlank() && !compact) {
                    Text(
                        "• ${match.stadium}",
                        color = SportTextMuted,
                        fontSize = (10.5f * scale).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusTimePill(match = match, scale = scale)
                if (match.channel.isNotBlank()) {
                    Text(
                        match.channel,
                        color = Color.White,
                        fontSize = (10 * scale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SportContainerHighest)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

        // Card Center: Home Team | Score / Match Time | Away Team
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TeamRow(match.homeTeam, Modifier.weight(1f), Alignment.Start, scale = scale, crestSize = (if (compact) 30f else 36f * scale).dp)
            CenterScoreOrTime(match = match, scale = scale, compact = compact)
            TeamRow(match.awayTeam, Modifier.weight(1f), Alignment.End, scale = scale, crestSize = (if (compact) 30f else 36f * scale).dp)
        }

        if (!compact && (match.commentator.isNotBlank() || match.status == MatchStatus.LIVE)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SportSurface.copy(alpha = 0.55f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (match.commentator.isNotBlank()) "المعلق: ${match.commentator}" else "توقيت المباراة: ${formatMatchClock(match.time)}",
                    color = SportTextMuted,
                    fontSize = (11 * scale).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (match.status == MatchStatus.LIVE) {
                    Text("متاح على البث المباشر", color = SportCyan, fontSize = (11 * scale).sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatusTimePill(match: Match, scale: Float) {
    when (match.status) {
        MatchStatus.LIVE -> {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(SportLiveRed.copy(alpha = 0.22f))
                    .border(1.dp, SportLiveRed.copy(alpha = 0.55f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(SportLiveRed))
                Text(
                    "مباشر ${match.minute?.let { "'$it" } ?: ""}".trim(),
                    color = SportLiveRed,
                    fontSize = (10 * scale).sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        MatchStatus.FINISHED -> {
            Text(
                "منتهية • ${formatMatchClock(match.time)}",
                color = SportTextMuted,
                fontSize = (10 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(SportContainerHighest)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
        else -> {
            Text(
                "⏰ ${formatMatchClock(match.time)}",
                color = SportCyan,
                fontSize = (10.5f * scale).sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color(0xFF00FBFB).copy(alpha = 0.14f))
                    .border(1.dp, SportCyan.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun CenterScoreOrTime(match: Match, scale: Float, compact: Boolean) {
    Column(
        modifier = Modifier.padding(horizontal = (8 * scale).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (match.status) {
            MatchStatus.LIVE, MatchStatus.FINISHED -> {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SportSurface.copy(alpha = 0.90f))
                        .border(1.dp, SportViolet.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                        .padding(horizontal = (if (compact) 10f else 14f * scale).dp, vertical = (4f * scale).dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("${match.homeScore ?: 0}", color = Color.White, fontSize = (if (compact) 15f else 18f * scale).sp, fontWeight = FontWeight.Black)
                    Text(":", color = SportTextMuted, fontSize = (14f * scale).sp)
                    Text("${match.awayScore ?: 0}", color = Color.White, fontSize = (if (compact) 15f else 18f * scale).sp, fontWeight = FontWeight.Black)
                }
            }
            else -> {
                Text(
                    formatMatchClock(match.time),
                    color = Color.White,
                    fontSize = (if (compact) 13f else 15f * scale).sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SportContainerHighest)
                        .border(1.dp, SportCyan.copy(alpha = 0.30f), RoundedCornerShape(10.dp))
                        .padding(horizontal = (if (compact) 9f else 12f * scale).dp, vertical = (5f * scale).dp),
                )
            }
        }
    }
}

@Composable
private fun TeamRow(
    team: Team,
    modifier: Modifier,
    alignment: Alignment.Horizontal,
    scale: Float,
    crestSize: Dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignment == Alignment.Start) Arrangement.Start else Arrangement.End,
    ) {
        if (alignment == Alignment.Start) {
            TeamLogo(team, size = crestSize)
            Spacer(modifier = Modifier.width((8 * scale).dp))
            Text(
                team.name,
                color = Color.White,
                fontSize = (13 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                team.name,
                color = Color.White,
                fontSize = (13 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(modifier = Modifier.width((8 * scale).dp))
            TeamLogo(team, size = crestSize)
        }
    }
}

@Composable
private fun TeamLogo(team: Team, size: Dp = 34.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (team.logoUrl.isNotEmpty()) {
            AsyncImage(
                model = team.logoUrl,
                contentDescription = team.name,
                modifier = Modifier.size(size * 0.72f),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(team.name.take(2), color = SportPrimary, fontWeight = FontWeight.Black, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LiveMatchHubPane(
    match: Match?,
    scale: Float,
    onOpenStandings: () -> Unit,
    onWatchLive: () -> Unit,
) {
    val shape = RoundedCornerShape((20 * scale).dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(SportContainer.copy(alpha = 0.88f))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding((16 * scale).dp),
    ) {
        if (match == null) {
            Text(
                "اختر مباراة لعرض التفاصيل والترتيب",
                color = SportTextMuted,
                fontSize = (14 * scale).sp,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                LiveMatchHubContent(
                    match = match,
                    scale = scale,
                    onOpenStandings = onOpenStandings,
                    onWatchLive = onWatchLive,
                )
            }
        }
    }
}

@Composable
private fun LiveMatchHubContent(
    match: Match,
    scale: Float,
    onOpenStandings: () -> Unit,
    onWatchLive: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy((12 * scale).dp),
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                match.league.name,
                color = SportPrimary,
                fontWeight = FontWeight.ExtraBold,
                fontSize = (14 * scale).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            StatusTimePill(match = match, scale = scale)
        }

        // Stadium Showcase Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(SportViolet.copy(alpha = 0.25f), SportSurface, SportContainerHigh),
                    ),
                )
                .border(1.dp, SportViolet.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding((14 * scale).dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TeamLogo(match.homeTeam, size = (44 * scale).dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        match.homeTeam.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * scale).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 8.dp)) {
                    CenterScoreOrTime(match = match, scale = scale * 1.1f, compact = false)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        match.date.ifBlank { "—" },
                        color = SportTextMuted,
                        fontSize = (10.5f * scale).sp,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TeamLogo(match.awayTeam, size = (44 * scale).dp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        match.awayTeam.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = (13 * scale).sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }

        // Real Live Match Stats when returned by API
        match.stats?.let { stats ->
            MatchStatsBlock(match = match, stats = stats, scale = scale)
        }

        // Goal / Match Events when returned by API
        if (match.homeEvents.isNotEmpty() || match.awayEvents.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SportSurface.copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("أحداث المباراة", color = SportGold, fontSize = (11.5f * scale).sp, fontWeight = FontWeight.Bold)
                if (match.homeEvents.isNotEmpty()) {
                    Text("${match.homeTeam.name}: ${match.homeEvents.joinToString("، ")}", color = Color.White, fontSize = (11 * scale).sp)
                }
                if (match.awayEvents.isNotEmpty()) {
                    Text("${match.awayTeam.name}: ${match.awayEvents.joinToString("، ")}", color = Color.White, fontSize = (11 * scale).sp)
                }
            }
        }

        // Detailed Meta Info Rows
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SportSurface.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InfoRow("التاريخ", match.date.ifBlank { "غير محدد" }, scale)
            InfoRow("توقيت المباراة", formatMatchClock(match.time), scale, valueColor = SportCyan)
            InfoRow("الملعب", match.stadium.ifBlank { "غير محدد" }, scale)
            InfoRow("القناة الناقلة", match.channel.ifBlank { "يُحدد لاحقاً" }, scale)
            InfoRow("المعلق", match.commentator.ifBlank { "يُحدد لاحقاً" }, scale)
        }

        // Action Buttons (Open Standings + Watch Live Channels)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onOpenStandings,
                colors = ButtonDefaults.buttonColors(containerColor = SportContainerHighest),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, SportViolet.copy(alpha = 0.55f)),
                modifier = Modifier.weight(1f).height((44 * scale).dp),
            ) {
                Icon(Icons.Default.List, contentDescription = null, tint = SportPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("جدول الترتيب", color = Color.White, fontSize = (12 * scale).sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onWatchLive,
                colors = ButtonDefaults.buttonColors(containerColor = SportViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height((44 * scale).dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (match.status == MatchStatus.LIVE) "شاهد البث الحي" else "قنوات البث",
                    color = Color.White,
                    fontSize = (12 * scale).sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun MatchStatsBlock(match: Match, stats: MatchStats, scale: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SportSurface.copy(alpha = 0.65f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("إحصائيات المباراة", color = SportCyan, fontSize = (12 * scale).sp, fontWeight = FontWeight.Bold)
        // Possession bar
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${stats.homePossession}%", color = SportPrimary, fontSize = (11.5f * scale).sp, fontWeight = FontWeight.Bold)
            Text("الاستحواذ", color = SportTextMuted, fontSize = (11 * scale).sp)
            Text("${stats.awayPossession}%", color = SportCyan, fontSize = (11.5f * scale).sp, fontWeight = FontWeight.Bold)
        }
        val homeRatio = (stats.homePossession.coerceIn(1, 99) / 100f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(SportContainerHighest),
        ) {
            Box(Modifier.weight(homeRatio).fillMaxHeight().background(SportViolet))
            Box(Modifier.weight((1f - homeRatio).coerceAtLeast(0.01f)).fillMaxHeight().background(SportCyan))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${stats.homeShotsOnTarget} / ${stats.homeShots}", color = Color.White, fontSize = (11 * scale).sp)
            Text("التسديدات (على المرمى)", color = SportTextMuted, fontSize = (11 * scale).sp)
            Text("${stats.awayShotsOnTarget} / ${stats.awayShots}", color = Color.White, fontSize = (11 * scale).sp)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, scale: Float, valueColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = SportTextMuted, fontSize = (12 * scale).sp)
        Text(
            value,
            color = valueColor,
            fontSize = (12 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ErrorDiagnosticBox(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("تعذر جلب البيانات", color = SportLiveRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(message, color = SportTextMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = SportViolet),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("إعادة المحاولة", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun StandingsDialog(
    leagueName: String,
    standings: List<Standing>,
    isLoading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = SportContainer,
            border = BorderStroke(1.dp, SportViolet.copy(alpha = 0.45f)),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("جدول الترتيب", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        if (leagueName.isNotBlank()) {
                            Text(leagueName, color = SportCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

                when {
                    isLoading && standings.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = SportCyan)
                        }
                    }
                    standings.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text(
                                errorMessage ?: "جدول الترتيب غير متاح لهذه البطولة حالياً",
                                color = SportTextMuted,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    else -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("#", color = SportTextMuted, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                            Text("الفريق", color = SportTextMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text("لعب", color = SportTextMuted, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                            Text("فاز", color = SportTextMuted, fontSize = 12.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                            Text("نقاط", color = SportCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(standings, key = { "${it.rank}_${it.teamName}" }) { row ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SportContainerHigh.copy(alpha = 0.6f))
                                        .padding(vertical = 8.dp, horizontal = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("${row.rank}", color = SportGold, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                                    Text(row.teamName, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    Text("${row.played}", color = SportTextMuted, fontSize = 12.sp, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                                    Text("${row.win}", color = SportTextMuted, fontSize = 12.sp, modifier = Modifier.width(32.dp), textAlign = TextAlign.Center)
                                    Text("${row.points}", color = SportCyan, fontSize = 13.sp, fontWeight = FontWeight.Black, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatMatchClock(rawTime: String): String {
    val clean = rawTime.trim()
    if (clean.isBlank() || clean == "null") return "يُحدد لاحقاً"
    return clean.take(5).lowercase(Locale.US)
}
