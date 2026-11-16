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
import com.saydlawy.sahab.util.formatUtcClockToLocal

// Palette derived from sport/DESIGN.md and sport/code.html
@Composable
internal fun SportMatchCard(
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
            .restorableFocus(
                screenKey = Routes.SPORTS,
                itemKey = "sport_match_${match.id}",
            )
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
internal fun MatchStatusChip(match: Match, scale: Float) {
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
internal fun CenterScoreOrTime(match: Match, scale: Float, compact: Boolean) {
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
internal fun TeamRow(
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
internal fun TeamLogo(team: Team, size: Dp = 34.dp) {
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

