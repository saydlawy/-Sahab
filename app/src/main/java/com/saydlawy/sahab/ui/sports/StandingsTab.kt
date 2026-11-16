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
internal fun StandingsPane(
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
                    items(standings, key = { it.teamName }) { row ->
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
internal fun LiveMatchHubPane(
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
internal fun LiveMatchHubContent(
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
internal fun InfoRow(label: String, value: String, scale: Float, valueColor: Color = Color.White) {
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
internal fun ErrorDiagnosticBox(message: String, onRetry: () -> Unit) {
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
internal fun StandingsDialog(
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
                            items(standings, key = { it.teamName }) { row ->
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

internal fun formatMatchClock(rawTime: String, dateStr: String = ""): String =
    formatUtcClockToLocal(rawTime, dateStr)
