package com.saydlawy.sahab.ui.sports

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.Team

private val BgColor = Color(0xFF090817)
private val CardColor = Color(0xFF1B1629)
private val PrimaryPurple = Color(0xFFA42AF2)
private val AccentCyan = Color(0xFF00D9DF)
private val LiveRed = Color(0xFFFF648A)
private val TextWhite = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFA09BB8)
private val LiveGreen = Color(0xFF2ECC71)

@Composable
fun SportsScreen(
    viewModel: SportsViewModel = viewModel(),
    onClose: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedDateFilter by viewModel.selectedDateFilter.collectAsState()
    val availableLeagues by viewModel.availableLeagues.collectAsState()
    val selectedLeague by viewModel.selectedLeague.collectAsState()
    val selectedMatch by viewModel.selectedMatch.collectAsState()

    var showMobileMatchDetails by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(BgColor)
                .systemBarsPadding()
        ) {
            val isTvOrWideScreen = maxWidth >= 720.dp

            Column(modifier = Modifier.fillMaxSize()) {
                SportsTopBar(
                    onRefresh = { viewModel.fetchMatches() },
                    onClose = onClose
                )

                FiltersSection(
                    selectedDateFilter = selectedDateFilter,
                    onDateFilterSelected = { viewModel.selectDateFilter(it) },
                    availableLeagues = availableLeagues,
                    selectedLeague = selectedLeague,
                    onLeagueSelected = { viewModel.selectLeagueFilter(it) }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    when (val state = uiState) {
                        is SportsUiState.Loading -> {
                            LoadingStateView()
                        }
                        is SportsUiState.Error -> {
                            ErrorStateView(
                                errorMessage = state.errorMessage,
                                onRetry = { viewModel.fetchMatches() }
                            )
                        }
                        is SportsUiState.Empty -> {
                            EmptyStateView(
                                message = state.reason,
                                onRetry = { viewModel.fetchMatches() }
                            )
                        }
                        is SportsUiState.Success -> {
                            if (isTvOrWideScreen) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .weight(0.55f)
                                            .fillMaxHeight(),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(bottom = 24.dp)
                                    ) {
                                        items(state.matches, key = { it.id }) { match ->
                                            MatchCard(
                                                match = match,
                                                isSelected = selectedMatch?.id == match.id,
                                                onClick = { viewModel.selectMatch(match) }
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(0.45f)
                                            .fillMaxHeight()
                                    ) {
                                        selectedMatch?.let { match ->
                                            MatchDetailsPanel(match = match)
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(bottom = 24.dp)
                                ) {
                                    items(state.matches, key = { it.id }) { match ->
                                        MatchCard(
                                            match = match,
                                            isSelected = false,
                                            onClick = {
                                                viewModel.selectMatch(match)
                                                showMobileMatchDetails = true
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isTvOrWideScreen && showMobileMatchDetails && selectedMatch != null) {
                Dialog(onDismissRequest = { showMobileMatchDetails = false }) {
                    MatchDetailsPanel(
                        match = selectedMatch!!,
                        onCloseDialog = { showMobileMatchDetails = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun SportsTopBar(onRefresh: () -> Unit, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FocusableIconButton(onClick = onClose) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = TextWhite)
            }
            Text(text = "سحاب IPTV", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clip(RoundedCornerShape(50)).background(CardColor).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(LiveGreen))
                Text(text = "محدث تلقائياً", color = LiveGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            FocusableIconButton(onClick = onRefresh) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = "تحديث البيانات", tint = AccentCyan)
            }
        }
    }
}

@Composable
private fun FiltersSection(
    selectedDateFilter: DateFilter,
    onDateFilterSelected: (DateFilter) -> Unit,
    availableLeagues: List<String>,
    selectedLeague: String,
    onLeagueSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DateFilter.values()) { filter ->
                val isSelected = filter == selectedDateFilter
                val activeColor = if (filter == DateFilter.LIVE) LiveRed else PrimaryPurple
                FilterChipButton(
                    text = filter.label,
                    isSelected = isSelected,
                    activeColor = activeColor,
                    showDot = filter == DateFilter.LIVE,
                    onClick = { onDateFilterSelected(filter) }
                )
            }
        }

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(availableLeagues) { league ->
                FilterChipButton(
                    text = league,
                    isSelected = league == selectedLeague,
                    activeColor = PrimaryPurple,
                    showDot = league == selectedLeague,
                    onClick = { onLeagueSelected(league) }
                )
            }
        }
    }
}

@Composable
private fun FilterChipButton(text: String, isSelected: Boolean, activeColor: Color, showDot: Boolean = false, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> activeColor
            isFocused -> CardColor.copy(alpha = 0.9f)
            else -> CardColor
        }, label = "chipBg"
    )
    val borderColor = when {
        isFocused -> AccentCyan
        isSelected -> activeColor
        else -> Color.Transparent
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bgColor).border(BorderStroke(2.dp, borderColor), RoundedCornerShape(50)).onFocusChanged { isFocused = it.isFocused }.focusable().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (showDot) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(TextWhite))
        }
        Text(text = text, color = if (isSelected || isFocused) TextWhite else TextSecondary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun MatchCard(match: Match, isSelected: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = when {
        isFocused -> AccentCyan
        isSelected -> PrimaryPurple
        else -> Color.White.copy(alpha = 0.06f)
    }
    val borderWidth = if (isFocused || isSelected) 2.dp else 1.dp

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(borderWidth, borderColor),
        modifier = Modifier.fillMaxWidth().onFocusChanged { isFocused = it.isFocused }.focusable().clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = match.league.name, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                MatchStatusBadge(match = match)
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TeamDisplay(team = match.homeTeam, alignment = Alignment.Start, modifier = Modifier.weight(1f))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp).clip(RoundedCornerShape(10.dp)).background(BgColor).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    if (match.status == MatchStatus.UPCOMING) {
                        Text(text = match.time.ifBlank { "--:--" }, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = "${match.homeScore ?: 0}", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            Text(text = "-", color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(text = "${match.awayScore ?: 0}", color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                TeamDisplay(team = match.awayTeam, alignment = Alignment.End, modifier = Modifier.weight(1f))
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🏟️ ${match.stadium}", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(text = "📺 ${match.channel}", color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TeamDisplay(team: Team, alignment: Alignment.Horizontal, modifier: Modifier = Modifier) {
    val isStart = alignment == Alignment.Start
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (isStart) Arrangement.Start else Arrangement.End) {
        if (isStart) {
            TeamAvatar(teamName = team.name)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = team.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        } else {
            Text(text = team.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.width(8.dp))
            TeamAvatar(teamName = team.name)
        }
    }
}

@Composable
private fun TeamAvatar(teamName: String) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp).clip(CircleShape).background(PrimaryPurple.copy(alpha = 0.25f)).border(1.dp, PrimaryPurple.copy(alpha = 0.6f), CircleShape)) {
        Text(text = teamName.take(2).uppercase(), color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MatchStatusBadge(match: Match) {
    val (text, badgeColor) = when (match.status) {
        MatchStatus.LIVE -> {
            val min = if (!match.minute.isNullOrBlank()) " ${match.minute}'" else ""
            "مباشر$min" to LiveRed
        }
        MatchStatus.FINISHED -> "انتهت" to TextSecondary
        MatchStatus.POSTPONED -> "مؤجلة" to Color(0xFFF39C12)
        MatchStatus.CANCELLED -> "ملغاة" to LiveRed
        MatchStatus.UPCOMING -> "لم تبدأ" to PrimaryPurple
    }
    Box(modifier = Modifier.clip(RoundedCornerShape(50)).background(badgeColor.copy(alpha = 0.18f)).border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(text = text, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MatchDetailsPanel(match: Match, onCloseDialog: (() -> Unit)? = null) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        border = BorderStroke(1.dp, PrimaryPurple.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = "بطاقة المباراة", color = AccentCyan, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                if (onCloseDialog != null) {
                    FocusableIconButton(onClick = onCloseDialog) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "إغلاق", tint = TextWhite)
                    }
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            Text(text = "${match.league.name} (${match.league.country})", color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TeamAvatar(teamName = match.homeTeam.name)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = match.homeTeam.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 12.dp)) {
                    MatchStatusBadge(match = match)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = if (match.status == MatchStatus.UPCOMING) match.time else "${match.homeScore ?: 0} - ${match.awayScore ?: 0}", color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    TeamAvatar(teamName = match.awayTeam.name)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = match.awayTeam.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            }
            Divider(color = Color.White.copy(alpha = 0.08f))
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow(label = "التاريخ", value = match.date)
                DetailRow(label = "وقت الانطلاق (UTC)", value = match.time)
                DetailRow(label = "الملعب", value = match.stadium)
                DetailRow(label = "القناة الناقلة", value = match.channel)
                DetailRow(label = "المعلق", value = match.commentator)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(BgColor).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LoadingStateView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(color = PrimaryPurple)
            Text(text = "جاري جلب جدول المباريات...", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyStateView(message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(24.dp)) {
            Text(text = message, color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            FocusableActionButton(text = "تحديث القائمة", onClick = onRetry)
        }
    }
}

@Composable
private fun ErrorStateView(errorMessage: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardColor),
            border = BorderStroke(1.dp, LiveRed.copy(alpha = 0.6f)),
            modifier = Modifier.fillMaxWidth(0.9f).padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(20.dp)) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = "خطأ", tint = LiveRed, modifier = Modifier.size(40.dp))
                Text(text = "تعذر تحميل المباريات", color = LiveRed, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text(text = errorMessage, color = TextWhite, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 20.sp)
                Spacer(modifier = Modifier.height(4.dp))
                FocusableActionButton(text = "إعادة المحاولة", onClick = onRetry)
            }
        }
    }
}

@Composable
private fun FocusableActionButton(text: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = if (isFocused) AccentCyan else PrimaryPurple),
        shape = RoundedCornerShape(50),
        modifier = Modifier.onFocusChanged { isFocused = it.isFocused }.focusable()
    ) {
        Text(text = text, color = if (isFocused) BgColor else TextWhite, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
    }
}

@Composable
private fun FocusableIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(38.dp).clip(CircleShape).background(if (isFocused) PrimaryPurple else CardColor).border(width = if (isFocused) 2.dp else 0.dp, color = if (isFocused) AccentCyan else Color.Transparent, shape = CircleShape).onFocusChanged { isFocused = it.isFocused }.focusable().clickable { onClick() }
    ) {
        content()
    }
}


