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
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyGridState
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.util.formatUtcClockToLocal

// Palette derived from sport/DESIGN.md and sport/code.html
@Composable
internal fun SportsStateBody(
    uiState: SportsUiState,
    selectedMatch: Match?,
    scale: Float,
    compactCard: Boolean,
    highlightSelection: Boolean,
    useTwoColumnGrid: Boolean,
    suppressLeagueHeaderIfSingle: Boolean = false,
    listState: LazyListState = rememberRestorableLazyListState("sports_tv_list"),
    gridState: LazyGridState = rememberRestorableLazyGridState("sports_tv_grid"),
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
internal fun MatchesGrid(
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
internal fun MatchesList(
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
internal fun LeagueGroupHeader(league: League, count: Int, scale: Float) {
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

