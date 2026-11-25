package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.feature.instantwatch.data.OnlineSubtitleOption
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedPlaybackSource
import com.saydlawy.sahab.feature.instantwatch.player.ActivePlaybackSession
import com.saydlawy.sahab.feature.instantwatch.player.IsolatedPlayerWebView
import com.saydlawy.sahab.feature.instantwatch.player.NativeMedia3Player
import com.saydlawy.sahab.feature.instantwatch.player.PlayerSessionManager
import com.saydlawy.sahab.feature.instantwatch.player.SubtitleCue
import com.saydlawy.sahab.feature.instantwatch.player.SubtitleOverlay
import com.saydlawy.sahab.feature.instantwatch.player.SubtitleParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun InstantPlayerScreen(
    session: ActivePlaybackSession,
    sessionManager: PlayerSessionManager,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securityState by sessionManager.securityMonitor.state.collectAsState()
    var reloadKey by remember { mutableIntStateOf(0) }
    var errorMessage by remember(session) { mutableStateOf<String?>(null) }

    // Top bar visibility (compact & collapsible)
    var showTopBar by remember { mutableStateOf(true) }

    // Subtitle & Sync state
    var showSubtitlePanel by remember { mutableStateOf(false) }
    var subtitlesVisible by remember { mutableStateOf(true) }
    var subtitleCues by remember(session) { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleOffsetMs by remember(session) { mutableLongStateOf(0L) }
    var subtitleFontSizeSp by remember { mutableIntStateOf(18) }
    var playbackPosMs by remember(session) { mutableLongStateOf(session.resumePositionMs) }
    var embedTimerRunning by remember(session) { mutableStateOf(false) }

    // Online subtitle search state
    var onlineSearching by remember { mutableStateOf(false) }
    var onlineOptions by remember(session) { mutableStateOf<List<OnlineSubtitleOption>>(emptyList()) }
    var onlineStatus by remember(session) { mutableStateOf<String?>(null) }

    val isEmbedSource = session.source is ResolvedPlaybackSource.IsolatedEmbed

    LaunchedEffect(isEmbedSource, embedTimerRunning, subtitleCues.isNotEmpty()) {
        if (isEmbedSource && embedTimerRunning && subtitleCues.isNotEmpty()) {
            while (true) {
                delay(250L)
                playbackPosMs += 250L
            }
        }
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val loaded = SubtitleParser.loadFromUri(context, uri)
            subtitleCues = loaded
            subtitlesVisible = true
            onlineStatus = if (loaded.isNotEmpty()) "تم تحميل ${loaded.size} سطر من الجهاز." else "الملف فارغ أو غير مدعوم."
            if (isEmbedSource && loaded.isNotEmpty()) {
                embedTimerRunning = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "تعذر تشغيل الفيديو",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage.orEmpty(),
                    color = Color(0xFFCBD5E1),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            errorMessage = null
                            sessionManager.securityMonitor.clearFailure()
                            reloadKey++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    ) {
                        Text("إعادة المحاولة", color = Color.White)
                    }
                    TextButton(onClick = onClose) {
                        Text("رجوع", color = Color(0xFF94A3B8))
                    }
                }
            }
        } else {
            when (val src = session.source) {
                is ResolvedPlaybackSource.NativeMedia3 -> {
                    NativeMedia3Player(
                        streamUrl = src.streamUrl,
                        subtitle = src.subtitle,
                        resumePositionMs = session.resumePositionMs,
                        onProgressUpdate = { pos, dur ->
                            playbackPosMs = pos
                            onProgressUpdate(pos, dur)
                        },
                        onPlaybackError = { err -> errorMessage = err },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is ResolvedPlaybackSource.IsolatedEmbed -> {
                    IsolatedPlayerWebView(
                        embedUrl = src.embedUrl,
                        reloadKey = reloadKey,
                        navigationGuard = sessionManager.navigationGuard,
                        securityPolicy = sessionManager.webViewSecurityPolicy,
                        securityMonitor = sessionManager.securityMonitor,
                        onPlaybackError = { err -> errorMessage = err },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is ResolvedPlaybackSource.Rejected -> {
                    errorMessage = src.reason
                }
            }

            SubtitleOverlay(
                cues = subtitleCues,
                currentPositionMs = playbackPosMs,
                offsetMs = subtitleOffsetMs,
                fontSizeSp = subtitleFontSizeSp,
                visible = subtitlesVisible,
            )
        }

        // Compact floating top bar (doesn't push video down)
        if (showTopBar) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xB80B1120))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildString {
                        append(session.item.title)
                        if (session.seasonNumber != null && session.episodeNumber != null) {
                            append(" • م${session.seasonNumber} ح${session.episodeNumber}")
                        }
                    },
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 6.dp),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CompactBarChip(
                        label = if (subtitleCues.isNotEmpty()) "الترجمة ✓" else "الترجمة",
                        bgColor = Color(0xFF1E3A8A),
                        textColor = Color(0xFF93C5FD),
                        onClick = { showSubtitlePanel = !showSubtitlePanel },
                    )
                    CompactBarChip(
                        label = "حماية ${securityState.blockedCount}",
                        bgColor = Color(0xFF064E3B),
                        textColor = Color(0xFF6EE7B7),
                        onClick = { showTopBar = false },
                    )
                    CompactBarChip(
                        label = "إخفاء",
                        bgColor = Color(0xFF334155),
                        textColor = Color(0xFFCBD5E1),
                        onClick = { showTopBar = false },
                    )
                    CompactBarChip(
                        label = "إغلاق",
                        bgColor = Color(0xFF7F1D1D),
                        textColor = Color(0xFFFCA5A5),
                        onClick = onClose,
                    )
                }
            }
        } else {
            CompactBarChip(
                label = "الأدوات",
                bgColor = Color(0x990F172A),
                textColor = Color(0xFFCBD5E1),
                onClick = { showTopBar = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }

        if (showSubtitlePanel) {
            SubtitleSyncControlPanel(
                hasSubtitles = subtitleCues.isNotEmpty(),
                cuesCount = subtitleCues.size,
                visible = subtitlesVisible,
                offsetMs = subtitleOffsetMs,
                fontSizeSp = subtitleFontSizeSp,
                isEmbedSource = isEmbedSource,
                embedTimerRunning = embedTimerRunning,
                onlineSearching = onlineSearching,
                onlineOptions = onlineOptions,
                onlineStatus = onlineStatus,
                onSearchOnline = {
                    scope.launch {
                        onlineSearching = true
                        onlineStatus = "جارٍ البحث عن ترجمة عربية..."
                        val resolvedImdbId = session.item.imdbId?.takeIf { it.isNotBlank() }
                            ?: sessionManager.tmdbRepository
                                .getDetails(session.item.tmdbId, session.item.mediaType)
                                ?.imdbId
                        val found = sessionManager.onlineSubtitleFetcher.searchArabic(
                            tmdbId = session.item.tmdbId,
                            imdbId = resolvedImdbId,
                            seasonNumber = session.seasonNumber,
                            episodeNumber = session.episodeNumber,
                        )
                        onlineOptions = found
                        onlineStatus = if (found.isEmpty()) {
                            "لم يتم العثور على ترجمة عربية أونلاين لهذا العمل."
                        } else {
                            "تم العثور على ${found.size} ترجمة عربية، اختر واحدة:"
                        }
                        onlineSearching = false
                    }
                },
                onSelectOnline = { option ->
                    scope.launch {
                        onlineSearching = true
                        onlineStatus = "جارٍ تحميل الترجمة..."
                        val text = sessionManager.onlineSubtitleFetcher.downloadSubtitleText(option.url)
                        val parsed = text?.let { SubtitleParser.parse(it) }.orEmpty()
                        if (parsed.isNotEmpty()) {
                            subtitleCues = parsed
                            subtitlesVisible = true
                            onlineStatus = "تم تفعيل: ${option.label} (${parsed.size} سطر)"
                            if (isEmbedSource) embedTimerRunning = true
                        } else {
                            onlineStatus = "تعذر تحميل هذه الترجمة، جرّب خياراً آخر."
                        }
                        onlineSearching = false
                    }
                },
                onPickFile = { subtitlePicker.launch(arrayOf("*/*")) },
                onToggleVisible = { subtitlesVisible = it },
                onAdjustOffset = { delta -> subtitleOffsetMs += delta },
                onResetOffset = { subtitleOffsetMs = 0L },
                onAdjustFontSize = { delta ->
                    subtitleFontSizeSp = (subtitleFontSizeSp + delta).coerceIn(12, 32)
                },
                onToggleEmbedTimer = { embedTimerRunning = !embedTimerRunning },
                onResetEmbedTimer = { playbackPosMs = 0L },
                onClose = { showSubtitlePanel = false },
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp, vertical = 48.dp)
                    .widthIn(max = 360.dp),
            )
        }
    }
}

@Composable
private fun CompactBarChip(
    label: String,
    bgColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun SubtitleSyncControlPanel(
    hasSubtitles: Boolean,
    cuesCount: Int,
    visible: Boolean,
    offsetMs: Long,
    fontSizeSp: Int,
    isEmbedSource: Boolean,
    embedTimerRunning: Boolean,
    onlineSearching: Boolean,
    onlineOptions: List<OnlineSubtitleOption>,
    onlineStatus: String?,
    onSearchOnline: () -> Unit,
    onSelectOnline: (OnlineSubtitleOption) -> Unit,
    onPickFile: () -> Unit,
    onToggleVisible: (Boolean) -> Unit,
    onAdjustOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onAdjustFontSize: (Int) -> Unit,
    onToggleEmbedTimer: () -> Unit,
    onResetEmbedTimer: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetSec = offsetMs / 1000.0
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF20F172A))
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "الترجمة والمزامنة",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            CompactBarChip(
                label = "إغلاق",
                bgColor = Color(0xFF1E293B),
                textColor = Color(0xFF60A5FA),
                onClick = onClose,
            )
        }

        // Source actions: Online search + Local file
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSearchOnline,
                enabled = !onlineSearching,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (onlineSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("بحث أونلاين (عربي)", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = onPickFile,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("ملف SRT/VTT", color = Color(0xFFCBD5E1), fontSize = 12.sp, maxLines = 1)
            }
        }

        if (!onlineStatus.isNullOrBlank()) {
            Text(
                text = onlineStatus,
                color = Color(0xFF93C5FD),
                fontSize = 12.sp,
            )
        }

        if (onlineOptions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                onlineOptions.take(5).forEach { opt ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .clickable { onSelectOnline(opt) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = opt.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (hasSubtitles) "إظهار الترجمة ($cuesCount سطر)" else "إظهار الترجمة الخارجية",
                color = Color(0xFFCBD5E1),
                fontSize = 13.sp,
            )
            Switch(checked = visible, onCheckedChange = onToggleVisible)
        }

        // Sync controls: evenly spaced, zero squishing
        Text(
            text = "المزامنة: ${String.format("%+.1f", offsetSec)} ثانية",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SyncStepButton("-2ث", Modifier.weight(1f)) { onAdjustOffset(-2000L) }
            SyncStepButton("-0.5ث", Modifier.weight(1f)) { onAdjustOffset(-500L) }
            SyncStepButton("0", Modifier.weight(1f), highlight = true) { onResetOffset() }
            SyncStepButton("+0.5ث", Modifier.weight(1f)) { onAdjustOffset(500L) }
            SyncStepButton("+2ث", Modifier.weight(1f)) { onAdjustOffset(2000L) }
        }

        // Font size
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("حجم الخط: $fontSizeSp", color = Color(0xFFCBD5E1), fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncStepButton("-", Modifier.widthIn(min = 44.dp)) { onAdjustFontSize(-2) }
                SyncStepButton("+", Modifier.widthIn(min = 44.dp)) { onAdjustFontSize(2) }
            }
        }

        if (isEmbedSource && hasSubtitles) {
            HorizontalDivider(color = Color(0xFF1E293B))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onToggleEmbedTimer,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = if (embedTimerRunning) "إيقاف المؤقت" else "تشغيل المؤقت",
                        color = Color(0xFF34D399),
                        fontSize = 12.sp,
                    )
                }
                OutlinedButton(
                    onClick = onResetEmbedTimer,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("تصفير الوقت", color = Color(0xFFFCA5A5), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SyncStepButton(
    label: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlight) Color(0xFF1E3A8A) else Color(0xFF1E293B))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (highlight) Color(0xFF93C5FD) else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
