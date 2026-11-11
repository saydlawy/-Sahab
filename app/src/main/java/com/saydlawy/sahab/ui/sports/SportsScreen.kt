package com.saydlawy.sahab.ui.sports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.saydlawy.sahab.data.sports.League
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.Standing
import com.saydlawy.sahab.data.sports.Team
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.util.formatUtcClockToLocal

// Palette derived from sport/DESIGN.md and sport/code.html
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

private val SportVioletGradient: Brush = Brush.horizontalGradient(listOf(SportViolet, SportVioletDark))
private val SportFocusedCategoryGradient: Brush = Brush.horizontalGradient(listOf(SportContainerHighest, SportContainerHigh))
private val SportIdleCategoryBrush: Brush = SolidColor(SportContainer)
private val SportActiveMatchBrush: Brush = Brush.linearGradient(
    listOf(SportViolet.copy(alpha = 0.24f), SportContainer.copy(alpha = 0.96f)),
)
private val SportIdleMatchBrush: Brush = SolidColor(SportContainer.copy(alpha = 0.90f))
private val SportHighlightedPillBrush: Brush = Brush.horizontalGradient(listOf(SportViolet.copy(alpha = 0.28f), SportContainerHigh))
private val SportIdlePillBrush: Brush = Brush.horizontalGradient(listOf(SportContainerHigh, SportContainer))

@Composable
fun SportsScreen(
    viewModel: SportsViewModel = viewModel(),
    onClose: () -> Unit = {},
    onOpenLive: () -> Unit = {},
) {
    val displayMode = LocalDisplayMode.current
    val isTv = LocalIsTv.current
    val windowInfo = LocalWindowInfo.current
    val largeScale = when {
        displayMode != DisplayMode.TABLET_OR_TV -> 1f
        windowInfo.widthDp >= 1440.dp -> 1.22f
        windowInfo.widthDp >= 1100.dp -> 1.10f
        else -> 1f
    }

    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val subLeagues by viewModel.subLeagues.collectAsStateWithLifecycle()
    val selectedSubLeague by viewModel.selectedSubLeague.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val dateFilterCounts by viewModel.dateFilterCounts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedMatch by viewModel.selectedMatch.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val standings by viewModel.standings.collectAsStateWithLifecycle()
    val standingsLoading by viewModel.standingsLoading.collectAsStateWithLifecycle()
    val standingsError by viewModel.standingsError.collectAsStateWithLifecycle()
    val standingsLeagueName by viewModel.standingsLeagueName.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showStandingsDialog by remember { mutableStateOf(false) }
    var showPhoneStandingsTab by remember { mutableStateOf(false) }
    var showPhoneDetailsDialog by remember { mutableStateOf(false) }
    var showSearchField by remember { mutableStateOf(false) }

    val availableDateFilters = remember { DateFilter.values().toList() }

    val portraitListState = rememberLazyListState()
    val landscapeGridState = rememberLazyGridState()
    val phoneScrolled by remember(displayMode) {
        derivedStateOf {
            when (displayMode) {
                DisplayMode.PHONE_PORTRAIT ->
                    portraitListState.firstVisibleItemIndex > 0 || portraitListState.firstVisibleItemScrollOffset > 28
                DisplayMode.PHONE_LANDSCAPE ->
                    landscapeGridState.firstVisibleItemIndex > 0 || landscapeGridState.firstVisibleItemScrollOffset > 28
                DisplayMode.TABLET_OR_TV -> false
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Soft radial cosmic accents rendered smoothly over AppRoot's full-bleed background
            Box(
                Modifier
                    .size(440.dp)
                    .offset(x = (-80).dp, y = (-100).dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(SportViolet.copy(alpha = 0.14f), Color.Transparent))),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(380.dp)
                    .offset(x = 60.dp, y = 100.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(SportCyan.copy(alpha = 0.08f), Color.Transparent))),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                    visible = displayMode == DisplayMode.TABLET_OR_TV || !phoneScrolled,
                    enter = expandVertically(tween(180)) + fadeIn(tween(160)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
                ) {
                    SahabTopBar(current = Routes.SPORTS, onBack = onClose)
                }

                when (displayMode) {
                    DisplayMode.PHONE_PORTRAIT -> {
                        PhonePortraitSportsLayout(
                            listState = portraitListState,
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
                            onRefresh = viewModel::refreshActiveFilter,
                            showStandingsTab = showPhoneStandingsTab,
                            onToggleStandingsTab = {
                                viewModel.fetchStandings()
                                showPhoneStandingsTab = !showPhoneStandingsTab
                            },
                            standings = standings,
                            standingsLoading = standingsLoading,
                            standingsError = standingsError,
                            standingsLeagueName = standingsLeagueName,
                            onWatchLive = onOpenLive,
                            onSelectCategory = viewModel::updateCategory,
                            onSelectSubLeague = viewModel::updateSubLeague,
                            onSelectDate = viewModel::updateDate,
                            uiState = uiState,
                            selectedMatch = selectedMatch,
                            onMatchClick = { match ->
                                viewModel.selectMatch(match)
                                showPhoneDetailsDialog = true
                            },
                        )
                    }

                    DisplayMode.PHONE_LANDSCAPE -> {
                        PhoneLandscapeSportsLayout(
                            gridState = landscapeGridState,
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
                            onRefresh = viewModel::refreshActiveFilter,
                            showStandingsTab = showPhoneStandingsTab,
                            onToggleStandingsTab = {
                                viewModel.fetchStandings()
                                showPhoneStandingsTab = !showPhoneStandingsTab
                            },
                            standings = standings,
                            standingsLoading = standingsLoading,
                            standingsError = standingsError,
                            standingsLeagueName = standingsLeagueName,
                            onWatchLive = onOpenLive,
                            onSelectCategory = viewModel::updateCategory,
                            onSelectSubLeague = viewModel::updateSubLeague,
                            onSelectDate = viewModel::updateDate,
                            uiState = uiState,
                            selectedMatch = selectedMatch,
                            onMatchClick = { match ->
                                viewModel.selectMatch(match)
                                showPhoneDetailsDialog = true
                            },
                        )
                    }

                    DisplayMode.TABLET_OR_TV -> {
                        LargeScreenSportsLayout(
                            scale = largeScale,
                            isTv = isTv,
                            selectedCategory = selectedCategory,
                            subLeagues = subLeagues,
                            selectedSubLeague = selectedSubLeague,
                            availableDateFilters = availableDateFilters,
                            selectedDate = selectedDate,
                            dateFilterCounts = dateFilterCounts,
                            isRefreshing = isRefreshing,
                            onRefresh = viewModel::refreshActiveFilter,
                            onSelectCategory = viewModel::updateCategory,
                            onSelectSubLeague = viewModel::updateSubLeague,
                            onSelectDate = viewModel::updateDate,
                            uiState = uiState,
                            selectedMatch = selectedMatch,
                            standings = standings,
                            standingsLoading = standingsLoading,
                            standingsError = standingsError,
                            standingsLeagueName = standingsLeagueName,
                            onMatchClick = { match ->
                                viewModel.selectMatch(match)
                                showPhoneDetailsDialog = true
                            },
                            onWatchLive = onOpenLive,
                        )
                    }
                }
            }
        }
    }

    val dialogMatch = selectedMatch
    if (showPhoneDetailsDialog && dialogMatch != null) {
        val dialogCloseFocus = remember { FocusRequester() }
        LaunchedEffect(isTv) {
            if (isTv) runCatching { dialogCloseFocus.requestFocus() }
        }
        Dialog(onDismissRequest = { showPhoneDetailsDialog = false }) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SportContainer,
                border = BorderStroke(1.dp, SportViolet.copy(alpha = 0.50f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(max = 560.dp),
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "تفاصيل المباراة",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                        IconButton(
                            onClick = { showPhoneDetailsDialog = false },
                            modifier = Modifier.size(36.dp).focusRequester(dialogCloseFocus),
                        ) {
                            Icon(Ic.Close, contentDescription = "إغلاق", tint = Color.White)
                        }
                    }
                    LiveMatchHubContent(
                        match = dialogMatch,
                        scale = 1f,
                        onOpenStandings = {
                            showPhoneDetailsDialog = false
                            viewModel.fetchStandings()
                            showStandingsDialog = true
                        },
                        onWatchLive = {
                            showPhoneDetailsDialog = false
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

/**
 * Mode A: Mobile Portrait — single column, DateFilterBar, inline Standings toggle, clear touch targets.
 */
@Composable
private fun PhonePortraitSportsLayout(
    listState: LazyListState,
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
    showStandingsTab: Boolean,
    onToggleStandingsTab: () -> Unit,
    standings: List<Standing>,
    standingsLoading: Boolean,
    standingsError: String?,
    standingsLeagueName: String,
    onWatchLive: () -> Unit,
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
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SportsCompactPhoneToolbar(
            availableDateFilters = availableDateFilters,
            selectedDate = selectedDate,
            dateFilterCounts = dateFilterCounts,
            onSelectDate = onSelectDate,
            showSearchField = showSearchField,
            searchQuery = searchQuery,
            isRefreshing = isRefreshing,
            showStandingsTab = showStandingsTab,
            onToggleSearch = onToggleSearch,
            onSearchChange = onSearchChange,
            onRefresh = onRefresh,
            onShowStandings = onToggleStandingsTab,
        )
        MainCategoryBar(selectedCategory, onSelectCategory, scale = 1f, isTv = false)
        if (subLeagues.isNotEmpty()) {
            SubLeagueBar(subLeagues, selectedSubLeague, onSelectSubLeague, scale = 1f, isTv = false)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showStandingsTab) {
                StandingsPane(
                    leagueName = standingsLeagueName,
                    standings = standings,
                    isLoading = standingsLoading,
                    errorMessage = standingsError,
                    scale = 1f,
                    onWatchLive = onWatchLive,
                )
            } else {
                SportsStateBody(
                    uiState = uiState,
                    selectedMatch = selectedMatch,
                    scale = 1f,
                    compactCard = true,
                    highlightSelection = false,
                    useTwoColumnGrid = false,
                    listState = listState,
                    onMatchClick = onMatchClick,
                    onRetry = onRefresh,
                )
            }
        }
    }
}

/**
 * Mode B: Mobile Landscape — zero left Detail Pane duplication!
 * Collapsed single control bar (Categories + DatePickerPill + Standings toggle + Actions) -> Full-width 2-column match grid or inline StandingsPane.
 */
@Composable
private fun PhoneLandscapeSportsLayout(
    gridState: LazyGridState,
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
    showStandingsTab: Boolean,
    onToggleStandingsTab: () -> Unit,
    standings: List<Standing>,
    standingsLoading: Boolean,
    standingsError: String?,
    standingsLeagueName: String,
    onWatchLive: () -> Unit,
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
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Single unified horizontal strip: scrolling categories on start + DatePickerPill & actions on end (no collision/clipping)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                MainCategoryBar(selectedCategory, onSelectCategory, scale = 0.94f, isTv = false)
            }
            DatePickerPill(
                filters = availableDateFilters,
                selected = selectedDate,
                counts = dateFilterCounts,
                onSelect = onSelectDate,
            )
            ActionPillButton(
                icon = Ic.List,
                label = if (showStandingsTab) "المباريات" else "جدول الترتيب",
                highlighted = showStandingsTab,
                accentCyan = showStandingsTab,
                scale = 0.94f,
                isTv = false,
                onClick = onToggleStandingsTab,
            )
            ActionPillButton(
                icon = Ic.Refresh,
                label = null,
                highlighted = false,
                accentCyan = true,
                spinning = isRefreshing,
                scale = 0.94f,
                isTv = false,
                onClick = onRefresh,
            )
            ActionPillButton(
                icon = Ic.Search,
                label = null,
                highlighted = showSearchField || searchQuery.isNotBlank(),
                scale = 0.94f,
                isTv = false,
                onClick = onToggleSearch,
            )
        }

        if (showSearchField) {
            SportsSearchInput(searchQuery = searchQuery, onSearchChange = onSearchChange, scale = 0.95f)
        }

        if (subLeagues.isNotEmpty()) {
            SubLeagueBar(subLeagues, selectedSubLeague, onSelectSubLeague, scale = 0.94f, isTv = false)
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (showStandingsTab) {
                StandingsPane(
                    leagueName = standingsLeagueName,
                    standings = standings,
                    isLoading = standingsLoading,
                    errorMessage = standingsError,
                    scale = 0.96f,
                    onWatchLive = onWatchLive,
                )
            } else {
                SportsStateBody(
                    uiState = uiState,
                    selectedMatch = selectedMatch,
                    scale = 0.96f,
                    compactCard = true,
                    highlightSelection = false,
                    useTwoColumnGrid = true,
                    gridState = gridState,
                    onMatchClick = onMatchClick,
                    onRetry = onRefresh,
                )
            }
        }
    }
}

/**
 * Mode C: Tablet & Large TV (40" to 90") — Full 12-Column Master-Detail Hub matching sport/code.html.
 */
@Composable
private fun LargeScreenSportsLayout(
    scale: Float,
    isTv: Boolean,
    selectedCategory: MainCategory,
    subLeagues: List<Pair<Int?, String>>,
    selectedSubLeague: Int?,
    availableDateFilters: List<DateFilter>,
    selectedDate: DateFilter,
    dateFilterCounts: Map<DateFilter, Int>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSelectCategory: (MainCategory) -> Unit,
    onSelectSubLeague: (Int?) -> Unit,
    onSelectDate: (DateFilter) -> Unit,
    uiState: SportsUiState,
    selectedMatch: Match?,
    standings: List<Standing>,
    standingsLoading: Boolean,
    standingsError: String?,
    standingsLeagueName: String,
    onMatchClick: (Match) -> Unit,
    onWatchLive: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = (24 * scale).dp, vertical = (8 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
    ) {
        // Row 1: Main Category Pills + Date Dropdown Pill + Content Refresh button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                MainCategoryBar(
                    selected = selectedCategory,
                    onSelect = onSelectCategory,
                    scale = scale,
                    isTv = isTv,
                )
            }
            DatePickerPill(
                filters = availableDateFilters,
                selected = selectedDate,
                counts = dateFilterCounts,
                onSelect = onSelectDate,
                scale = scale,
                isTv = isTv,
            )
            ActionPillButton(
                icon = Ic.Refresh,
                label = if (isRefreshing) "جاري التحديث..." else "تحديث المحتوى",
                highlighted = false,
                accentCyan = true,
                spinning = isRefreshing,
                scale = scale,
                isTv = isTv,
                onClick = onRefresh,
            )
        }

        // Row 2: Sub-Leagues Bar (if any)
        if (subLeagues.isNotEmpty()) {
            SubLeagueBar(
                subLeagues = subLeagues,
                selectedId = selectedSubLeague,
                onSelect = onSelectSubLeague,
                scale = scale,
                isTv = isTv,
            )
        }

        // Row 4: Two-Pane Split: Matches (Right) and Live Standings (Left)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy((16 * scale).dp),
        ) {
            Box(Modifier.weight(6.5f).fillMaxHeight()) {
                SportsStateBody(
                    uiState = uiState,
                    selectedMatch = selectedMatch,
                    scale = scale,
                    compactCard = false,
                    highlightSelection = true,
                    useTwoColumnGrid = false,
                    suppressLeagueHeaderIfSingle = true,
                    onMatchClick = onMatchClick,
                    onRetry = onRefresh,
                )
            }
            Box(Modifier.weight(5.5f).fillMaxHeight()) {
                StandingsPane(
                    leagueName = standingsLeagueName,
                    standings = standings,
                    isLoading = standingsLoading,
                    errorMessage = standingsError,
                    scale = scale,
                    onWatchLive = onWatchLive,
                )
            }
        }
    }
}

@Composable
private fun StandingsPane(
    leagueName: String,
    standings: List<Standing>,
    isLoading: Boolean,
    errorMessage: String?,
    scale: Float,
    onWatchLive: () -> Unit,
) {
    val isTv = LocalIsTv.current
    val shape = RoundedCornerShape((20 * scale).dp)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(SportContainer.copy(alpha = 0.90f))
            .border(1.dp, SportViolet.copy(alpha = 0.35f), shape)
            .padding((16 * scale).dp),
        verticalArrangement = Arrangement.spacedBy((10 * scale).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(SportCyan))
                Text(
                    "جدول الترتيب",
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (15 * scale).sp,
                )
            }
            if (leagueName.isNotBlank()) {
                Text(
                    leagueName,
                    color = SportPrimary,
                    fontSize = (12.5f * scale).sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

        when {
            isLoading && standings.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = SportCyan)
                }
            }
            standings.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        errorMessage ?: "جدول الترتيب غير متاح لهذه البطولة حالياً",
                        color = SportTextMuted,
                        fontSize = (13 * scale).sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("#", color = SportTextMuted, fontSize = (11 * scale).sp, modifier = Modifier.width(24.dp))
                    Text("الفريق", color = SportTextMuted, fontSize = (11 * scale).sp, modifier = Modifier.weight(1f))
                    Text("لعب", color = SportTextMuted, fontSize = (11 * scale).sp, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
                    Text("فاز", color = SportTextMuted, fontSize = (11 * scale).sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.Center)
                    Text("ت", color = SportTextMuted, fontSize = (11 * scale).sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                    Text("خ", color = SportTextMuted, fontSize = (11 * scale).sp, modifier = Modifier.width(24.dp), textAlign = TextAlign.Center)
                    Text("نقاط", color = SportCyan, fontSize = (11 * scale).sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp), textAlign = TextAlign.Center)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy((5 * scale).dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(standings, key = { "${it.rank}_${it.teamName}" }) { row ->
                        var rowFocusedRaw by remember { mutableStateOf(false) }
                        val rowFocused = rowFocusedRaw && isTv
                        val rankColor = when (row.rank) {
                            1 -> SportGold
                            2, 3 -> SportCyan
                            else -> SportTextMuted
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (rowFocused) SportViolet.copy(alpha = 0.35f)
                                    else SportContainerHigh.copy(alpha = 0.65f),
                                )
                                .border(
                                    width = if (rowFocused) 2.dp else 1.dp,
                                    color = if (rowFocused) SportCyan else Color.White.copy(alpha = 0.05f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .onFocusChanged { rowFocusedRaw = it.isFocused }
                                .clickable { }
                                .padding(vertical = (7 * scale).dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${row.rank}",
                                color = rankColor,
                                fontSize = (12 * scale).sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(24.dp),
                            )
                            Text(
                                row.teamName,
                                color = Color.White,
                                fontSize = (12.5f * scale).sp,
                                fontWeight = if (rowFocused) FontWeight.ExtraBold else FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${row.played}",
                                color = SportTextMuted,
                                fontSize = (11.5f * scale).sp,
                                modifier = Modifier.width(28.dp),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "${row.win}",
                                color = SportTextMuted,
                                fontSize = (11.5f * scale).sp,
                                modifier = Modifier.width(26.dp),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "${row.draw}",
                                color = SportTextMuted,
                                fontSize = (11.5f * scale).sp,
                                modifier = Modifier.width(24.dp),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "${row.loss}",
                                color = SportTextMuted,
                                fontSize = (11.5f * scale).sp,
                                modifier = Modifier.width(24.dp),
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                "${row.points}",
                                color = SportCyan,
                                fontSize = (12.5f * scale).sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.width(34.dp),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SportsCompactPhoneToolbar(
    availableDateFilters: List<DateFilter>,
    selectedDate: DateFilter,
    dateFilterCounts: Map<DateFilter, Int>,
    onSelectDate: (DateFilter) -> Unit,
    showSearchField: Boolean,
    searchQuery: String,
    isRefreshing: Boolean,
    showStandingsTab: Boolean = false,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowStandings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DatePickerPill(
                filters = availableDateFilters,
                selected = selectedDate,
                counts = dateFilterCounts,
                onSelect = onSelectDate,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionPillButton(
                    icon = Ic.List,
                    label = if (showStandingsTab) "المباريات" else "جدول الترتيب",
                    highlighted = showStandingsTab,
                    accentCyan = showStandingsTab,
                    scale = 1f,
                    isTv = false,
                    onClick = onShowStandings,
                )
                ActionPillButton(
                    icon = Ic.Refresh,
                    label = if (isRefreshing) "..." else "تحديث",
                    highlighted = false,
                    accentCyan = true,
                    spinning = isRefreshing,
                    scale = 1f,
                    isTv = false,
                    onClick = onRefresh,
                )
                ActionPillButton(
                    icon = Ic.Search,
                    label = null,
                    highlighted = showSearchField || searchQuery.isNotBlank(),
                    scale = 1f,
                    isTv = false,
                    onClick = onToggleSearch,
                )
            }
        }
        if (showSearchField) {
            SportsSearchInput(searchQuery = searchQuery, onSearchChange = onSearchChange, scale = 1f)
        }
    }
}

@Composable
private fun DatePickerPill(
    filters: List<DateFilter>,
    selected: DateFilter,
    counts: Map<DateFilter, Int>,
    onSelect: (DateFilter) -> Unit,
    scale: Float = 1f,
    isTv: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var focusedRaw by remember { mutableStateOf(false) }
    val focused = focusedRaw && isTv
    val count = counts[selected] ?: 0
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) SportContainerHighest else SportContainerHigh)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) SportCyan else SportViolet.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                )
                .onFocusChanged { focusedRaw = it.isFocused }
                .clickable { expanded = true }
                .padding(horizontal = (12 * scale).dp, vertical = (8 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                selected.title,
                color = Color.White,
                fontSize = (12.5f * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (count > 0) {
                Text(
                    "$count",
                    color = SportCyan,
                    fontSize = (10.5f * scale).sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SportSurface)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Icon(
                Ic.ArrowDown,
                contentDescription = null,
                tint = if (focused) SportCyan else SportPrimary,
                modifier = Modifier.size((18 * scale).dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SportContainer),
        ) {
            filters.forEach { df ->
                val dfCount = counts[df] ?: 0
                val active = df == selected
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                df.title,
                                color = if (active) SportCyan else Color.White,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (dfCount > 0) "$dfCount" else "—",
                                color = if (dfCount > 0) SportPrimary else SportTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(df)
                    },
                )
            }
        }
    }
}

@Composable
private fun SportsSearchInput(searchQuery: String, onSearchChange: (String) -> Unit, scale: Float) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("ابحث عن نادي أو بطولة...", color = SportTextMuted, fontSize = (13 * scale).sp) },
        leadingIcon = { Icon(Ic.Search, contentDescription = null, tint = SportCyan) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Ic.Close, contentDescription = "مسح", tint = SportTextMuted)
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
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

@Composable
private fun ActionPillButton(
    icon: ImageVector,
    label: String?,
    highlighted: Boolean,
    scale: Float,
    isTv: Boolean,
    accentCyan: Boolean = false,
    spinning: Boolean = false,
    onClick: () -> Unit,
) {
    var focusedRaw by remember { mutableStateOf(false) }
    val focused = focusedRaw && isTv
    val spinAngle = if (spinning) {
        val spinTransition = rememberInfiniteTransition(label = "refreshSpin")
        val angle by spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "refreshAngle",
        )
        angle
    } else {
        0f
    }

    val bg = when {
        focused -> SportVioletGradient
        highlighted -> SportHighlightedPillBrush
        else -> SportIdlePillBrush
    }
    val borderColor = when {
        focused -> SportCyan
        accentCyan -> SportCyan.copy(alpha = 0.45f)
        highlighted -> SportViolet.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Row(
        modifier = Modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { focusedRaw = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = (if (label == null) 11f else 12f * scale).dp, vertical = (8f * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (accentCyan) SportCyan else SportPrimary,
            modifier = Modifier
                .size((18 * scale).dp)
                .rotate(if (spinning) spinAngle else 0f),
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
private fun MainCategoryBar(selected: MainCategory, onSelect: (MainCategory) -> Unit, scale: Float, isTv: Boolean) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
        items(MainCategory.values(), key = { it.name }) { category ->
            val isSelected = category == selected
            var focusedRaw by remember { mutableStateOf(false) }
            val isFocused = focusedRaw && isTv
            val fill = when {
                isSelected -> SportVioletGradient
                isFocused -> SportFocusedCategoryGradient
                else -> SportIdleCategoryBrush
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 42.dp)
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
                    .onFocusChanged { focusedRaw = it.isFocused }
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
private fun SubLeagueBar(
    subLeagues: List<Pair<Int?, String>>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    scale: Float,
    isTv: Boolean,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Text(
                "البطولات:",
                color = SportTextMuted,
                fontSize = (11.5f * scale).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        items(subLeagues, key = { it.first ?: -1 }) { (id, name) ->
            val isSelected = id == selectedId
            var focusedRaw by remember { mutableStateOf(false) }
            val isFocused = focusedRaw && isTv
            Box(
                modifier = Modifier
                    .heightIn(min = if (isTv) 34.dp else 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            isSelected -> SportViolet.copy(alpha = 0.30f)
                            isFocused -> SportContainerHigh
                            else -> SportContainer
                        },
                    )
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = when {
                            isFocused -> SportCyan
                            isSelected -> SportViolet.copy(alpha = 0.70f)
                            else -> Color.White.copy(alpha = 0.06f)
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                    .onFocusChanged { focusedRaw = it.isFocused }
                    .clickable { onSelect(id) }
                    .padding(horizontal = (12 * scale).dp, vertical = (7 * scale).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    color = if (isSelected) SportPrimary else Color.White.copy(alpha = 0.88f),
                    fontSize = (12 * scale).sp,
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
    highlightSelection: Boolean,
    useTwoColumnGrid: Boolean,
    suppressLeagueHeaderIfSingle: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    gridState: LazyGridState = rememberLazyGridState(),
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
                            "جرّب نطاقاً زمنياً أوسع (مثل 7 أو 28 يوماً) أو اضغط تحديث",
                            color = SportTextMuted,
                            fontSize = (12 * scale).sp,
                        )
                    }
                }
            } else if (useTwoColumnGrid) {
                MatchesGrid(
                    groupedMatches = uiState.groupedMatches,
                    gridState = gridState,
                    scale = scale,
                    onMatchClick = onMatchClick,
                )
            } else {
                MatchesList(
                    groupedMatches = uiState.groupedMatches,
                    listState = listState,
                    selectedMatch = selectedMatch,
                    highlightSelection = highlightSelection,
                    scale = scale,
                    compactCard = compactCard,
                    suppressLeagueHeaderIfSingle = suppressLeagueHeaderIfSingle,
                    onMatchClick = onMatchClick,
                )
            }
        }
    }
}

@Composable
private fun MatchesGrid(
    groupedMatches: Map<League, List<Match>>,
    gridState: LazyGridState,
    scale: Float,
    onMatchClick: (Match) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 18.dp),
    ) {
        groupedMatches.forEach { (league, matches) ->
            item(key = "ghdr_${league.id}_${league.name}", span = { GridItemSpan(2) }) {
                LeagueGroupHeader(league = league, count = matches.size, scale = scale)
            }
            items(matches, key = { it.id }) { match ->
                SportMatchCard(
                    match = match,
                    isSelected = false,
                    scale = scale,
                    compact = true,
                    onClick = { onMatchClick(match) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MatchesList(
    groupedMatches: Map<League, List<Match>>,
    listState: LazyListState,
    selectedMatch: Match?,
    highlightSelection: Boolean,
    scale: Float,
    compactCard: Boolean,
    suppressLeagueHeaderIfSingle: Boolean = false,
    onMatchClick: (Match) -> Unit,
) {
    val showHeaders = !suppressLeagueHeaderIfSingle || groupedMatches.size > 1
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy((10 * scale).dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        groupedMatches.forEach { (league, matches) ->
            if (showHeaders) {
                stickyHeader(key = "hdr_${league.id}_${league.name}") {
                    LeagueGroupHeader(league = league, count = matches.size, scale = scale)
                }
            }
            items(matches, key = { it.id }) { match ->
                SportMatchCard(
                    match = match,
                    isSelected = highlightSelection && match.id == selectedMatch?.id,
                    scale = scale,
                    compact = compactCard,
                    onClick = { onMatchClick(match) },
                )
            }
        }
    }
}

@Composable
private fun LeagueGroupHeader(league: League, count: Int, scale: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(SportContainerHigh.copy(alpha = 0.96f))
            .border(1.dp, SportViolet.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
            .padding(horizontal = (12 * scale).dp, vertical = (7 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
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
        Spacer(Modifier.width(8.dp))
        Text(
            "$count مباراة",
            color = SportCyan,
            fontSize = (11 * scale).sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
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
    val isTv = LocalIsTv.current
    var focusedRaw by remember { mutableStateOf(false) }
    val isFocused = focusedRaw && isTv
    val active = isSelected || isFocused
    val shape = RoundedCornerShape((16 * scale).dp)

    val bg = if (active) SportActiveMatchBrush else SportIdleMatchBrush
    val borderColor = when {
        isFocused -> SportCyan
        isSelected -> SportViolet
        else -> Color.White.copy(alpha = 0.08f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .then(
                if (active) {
                    Modifier.shadow(
                        elevation = (14 * scale).dp,
                        shape = shape,
                        ambientColor = SportViolet,
                        spotColor = SportViolet,
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(bg)
            .border(if (active) 2.dp else 1.dp, borderColor, shape)
            .onFocusChanged { focusedRaw = it.isFocused }
            .clickable(onClick = onClick)
            .padding((if (compact) 11f else 14f * scale).dp),
        verticalArrangement = Arrangement.spacedBy((8 * scale).dp),
    ) {
        // Card Header: Date + Optional Stadium + Status Badge + Channel (NO duplicate time here!)
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
                        maxLines = 1,
                    )
                }
                if (match.stadium.isNotBlank() && match.stadium != "غير محدد" && !compact) {
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
                MatchStatusChip(match = match, scale = scale)
                if (match.channel.isNotBlank() && match.channel != "غير محدد") {
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

        // Card Center: Home Team | Score OR Single Clear Time Pill | Away Team
        Row(
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TeamRow(match.homeTeam, Modifier.weight(1f), Alignment.Start, scale = scale, crestSize = (if (compact) 30f else 36f * scale).dp)
            CenterScoreOrTime(match = match, scale = scale, compact = compact)
            TeamRow(match.awayTeam, Modifier.weight(1f), Alignment.End, scale = scale, crestSize = (if (compact) 30f else 36f * scale).dp)
        }

        // Card Footer: Only rendered for LIVE matches to indicate live broadcast availability
        if (match.status == MatchStatus.LIVE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SportLiveRed.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("متاح على البث المباشر", color = SportCyan, fontSize = (11 * scale).sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MatchStatusChip(match: Match, scale: Float) {
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
                "انتهت",
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
                "لم تبدأ",
                color = SportPrimary,
                fontSize = (10 * scale).sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(SportViolet.copy(alpha = 0.20f))
                    .border(1.dp, SportViolet.copy(alpha = 0.40f), RoundedCornerShape(7.dp))
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
                val clock = formatMatchClock(match.time, match.date)
                if (clock.isNotBlank()) {
                    Text(
                        clock,
                        color = SportCyan,
                        fontSize = (if (compact) 13f else 15f * scale).sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SportContainerHighest)
                            .border(1.dp, SportCyan.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                            .padding(horizontal = (if (compact) 9f else 12f * scale).dp, vertical = (5f * scale).dp),
                    )
                } else {
                    Text(
                        match.date.takeLast(5).ifBlank { "قريباً" },
                        color = SportTextMuted,
                        fontSize = (if (compact) 11f else 12f * scale).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SportContainerHighest)
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(10.dp))
                            .padding(horizontal = (if (compact) 8f else 10f * scale).dp, vertical = (4f * scale).dp),
                    )
                }
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
                fontSize = (12.5f * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                team.name,
                color = Color.White,
                fontSize = (12.5f * scale).sp,
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
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy((12 * scale).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().wrapContentHeight().padding(top = 2.dp),
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
            Spacer(Modifier.width(8.dp))
            MatchStatusChip(match = match, scale = scale)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
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

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(SportSurface.copy(alpha = 0.65f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            InfoRow("التاريخ", match.date.ifBlank { "—" }, scale)
            val localClock = formatMatchClock(match.time, match.date)
            if (localClock.isNotBlank()) {
                InfoRow("توقيت المباراة", localClock, scale, valueColor = SportCyan)
            }
            if (match.stadium.isNotBlank() && match.stadium != "غير محدد") InfoRow("الملعب", match.stadium, scale)
            if (match.channel.isNotBlank() && match.channel != "غير محدد") InfoRow("القناة الناقلة", match.channel, scale)
        }

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
                Icon(Ic.List, contentDescription = null, tint = SportPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("جدول الترتيب", color = Color.White, fontSize = (12 * scale).sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = onWatchLive,
                colors = ButtonDefaults.buttonColors(containerColor = SportViolet),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f).height((44 * scale).dp),
            ) {
                Icon(Ic.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
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
    val isTv = LocalIsTv.current
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(isTv) {
        if (isTv) runCatching { closeFocus.requestFocus() }
    }
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
                    IconButton(onClick = onDismiss, modifier = Modifier.focusRequester(closeFocus)) {
                        Icon(Ic.Close, contentDescription = "إغلاق", tint = Color.White)
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
                                var focusedRaw by remember { mutableStateOf(false) }
                                val focused = focusedRaw && isTv
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (focused) SportViolet.copy(alpha = 0.35f)
                                            else SportContainerHigh.copy(alpha = 0.6f),
                                        )
                                        .border(
                                            width = if (focused) 2.dp else 0.dp,
                                            color = if (focused) SportCyan else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                        .onFocusChanged { focusedRaw = it.isFocused }
                                        .clickable { }
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

private fun formatMatchClock(rawTime: String, dateStr: String = ""): String =
    formatUtcClockToLocal(rawTime, dateStr)
