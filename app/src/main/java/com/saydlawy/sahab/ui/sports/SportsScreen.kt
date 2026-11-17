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
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
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
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyGridState
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.formatUtcClockToLocal

// Palette derived from sport/DESIGN.md and sport/code.html
internal val SportSurface get() = Palette.color("SportSurface")
internal val SportContainer get() = Palette.color("SportContainer")
internal val SportContainerHigh get() = Palette.color("SportContainerHigh")
internal val SportContainerHighest get() = Palette.color("SportContainerHighest")
internal val SportPrimary get() = Palette.color("SportPrimary")
internal val SportViolet get() = Palette.color("SportViolet")
internal val SportVioletDark get() = Palette.color("SportVioletDark")
internal val SportCyan get() = Palette.color("SportCyan")
internal val SportLiveRed get() = Palette.color("SportLiveRed")
internal val SportGold get() = Palette.color("SportGold")
internal val SportEmerald get() = Palette.color("SportEmerald")
internal val SportTextMuted get() = Palette.color("SportTextMuted")

internal val SportVioletGradient: Brush get() = Brush.horizontalGradient(listOf(SportViolet, SportVioletDark))
internal val SportFocusedCategoryGradient: Brush get() = Brush.horizontalGradient(listOf(SportContainerHighest, SportContainerHigh))
internal val SportIdleCategoryBrush: Brush get() = SolidColor(SportContainer)
internal val SportActiveMatchBrush: Brush get() = Brush.linearGradient(
    listOf(SportViolet.copy(alpha = 0.24f), SportContainer.copy(alpha = 0.96f)),
)
internal val SportIdleMatchBrush: Brush get() = SolidColor(SportContainer.copy(alpha = 0.90f))
internal val SportHighlightedPillBrush: Brush get() = Brush.horizontalGradient(listOf(SportViolet.copy(alpha = 0.28f), SportContainerHigh))
internal val SportIdlePillBrush: Brush get() = Brush.horizontalGradient(listOf(SportContainerHigh, SportContainer))

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

    val focusNav = LocalFocusNavigation.current
    val portraitListState = rememberRestorableLazyListState("sports_portrait_list")
    val landscapeGridState = rememberRestorableLazyGridState("sports_landscape_grid")
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
            if (isTv) {
                androidx.compose.runtime.withFrameNanos { }
                runCatching { dialogCloseFocus.requestFocus() }
            }
        }
        Dialog(onDismissRequest = {
            showPhoneDetailsDialog = false
            focusNav.onScreenResumed(Routes.SPORTS)
        }) {
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
internal fun PhonePortraitSportsLayout(
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
internal fun PhoneLandscapeSportsLayout(
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
internal fun LargeScreenSportsLayout(
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

