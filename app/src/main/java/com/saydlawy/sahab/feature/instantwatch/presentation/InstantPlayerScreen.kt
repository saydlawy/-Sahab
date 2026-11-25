package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedPlaybackSource
import com.saydlawy.sahab.feature.instantwatch.player.ActivePlaybackSession
import com.saydlawy.sahab.feature.instantwatch.player.IsolatedPlayerWebView
import com.saydlawy.sahab.feature.instantwatch.player.NativeMedia3Player
import com.saydlawy.sahab.feature.instantwatch.player.PlayerSessionManager
import com.saydlawy.sahab.feature.instantwatch.player.SubtitleCue
import com.saydlawy.sahab.feature.instantwatch.player.SubtitleOverlay
import com.saydlawy.sahab.feature.instantwatch.player.SubtitleParser
import kotlinx.coroutines.delay

@Composable
fun InstantPlayerScreen(
    session: ActivePlaybackSession,
    sessionManager: PlayerSessionManager,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val securityState by sessionManager.securityMonitor.state.collectAsState()
    var reloadKey by remember { mutableIntStateOf(0) }
    var errorMessage by remember(session) { mutableStateOf<String?>(null) }

    // Subtitle & Sync state
    var showSubtitlePanel by remember { mutableStateOf(false) }
    var subtitlesVisible by remember { mutableStateOf(true) }
    var subtitleCues by remember(session) { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var subtitleOffsetMs by remember(session) { mutableLongStateOf(0L) }
    var subtitleFontSizeSp by remember { mutableIntStateOf(18) }
    var playbackPosMs by remember(session) { mutableLongStateOf(session.resumePositionMs) }
    var embedTimerRunning by remember(session) { mutableStateOf(false) }

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
            if (isEmbedSource && loaded.isNotEmpty()) {
                embedTimerRunning = true
            }
        }
    }

    val subtitleText = buildString {
        if (session.seasonNumber != null && session.episodeNumber != null) {
            append("الموسم ${session.seasonNumber} • الحلقة ${session.episodeNumber}")
        }
        val sub = when (val src = session.source) {
            is ResolvedPlaybackSource.NativeMedia3 -> src.subtitle
            is ResolvedPlaybackSource.IsolatedEmbed -> src.subtitle
            is ResolvedPlaybackSource.Rejected -> null
        }
        if (sub != null) {
            if (isNotEmpty()) append(" • ")
            append("الترجمة: ${sub.label}")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitleText.isNotEmpty()) {
                    Text(
                        text = subtitleText,
                        color = Color(0xFF94A3B8),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextButton(onClick = { showSubtitlePanel = !showSubtitlePanel }) {
                    Text(
                        text = if (subtitleCues.isNotEmpty()) "الترجمة ✓" else "الترجمة",
                        color = Color(0xFF60A5FA),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "الحماية: ${securityState.blockedCount} محظور",
                    color = Color(0xFF34D399),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(Color(0xFF064E3B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
                TextButton(onClick = onClose) {
                    Text("إغلاق", color = Color(0xFFF87171))
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (errorMessage != null) {
                Column(
                    modifier = Modifier
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

                if (showSubtitlePanel) {
                    SubtitleSyncControlPanel(
                        hasSubtitles = subtitleCues.isNotEmpty(),
                        cuesCount = subtitleCues.size,
                        visible = subtitlesVisible,
                        offsetMs = subtitleOffsetMs,
                        fontSizeSp = subtitleFontSizeSp,
                        isEmbedSource = isEmbedSource,
                        embedTimerRunning = embedTimerRunning,
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
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                    )
                }
            }
        }
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
            .background(Color(0xEB0F172A), RoundedCornerShape(14.dp))
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
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            TextButton(onClick = onClose) {
                Text("تم", color = Color(0xFF60A5FA))
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPickFile,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
            ) {
                Text("تحميل ملف SRT/VTT", color = Color.White)
            }
            if (hasSubtitles) {
                Text(
                    text = "$cuesCount سطر",
                    color = Color(0xFF34D399),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("إظهار الترجمة الخارجية", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            Switch(checked = visible, onCheckedChange = onToggleVisible)
        }

        Text(
            text = "المزامنة: ${String.format("%+.1f", offsetSec)} ثانية",
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { onAdjustOffset(-2000L) }) { Text("-2ث", color = Color.White) }
            OutlinedButton(onClick = { onAdjustOffset(-500L) }) { Text("-0.5ث", color = Color.White) }
            OutlinedButton(onClick = onResetOffset) { Text("0", color = Color(0xFF60A5FA)) }
            OutlinedButton(onClick = { onAdjustOffset(500L) }) { Text("+0.5ث", color = Color.White) }
            OutlinedButton(onClick = { onAdjustOffset(2000L) }) { Text("+2ث", color = Color.White) }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("حجم الخط: $fontSizeSp", color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { onAdjustFontSize(-2) }) { Text("-", color = Color.White) }
            OutlinedButton(onClick = { onAdjustFontSize(2) }) { Text("+", color = Color.White) }
        }

        if (isEmbedSource && hasSubtitles) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onToggleEmbedTimer) {
                    Text(if (embedTimerRunning) "إيقاف مؤقت الترجمة" else "تشغيل مؤقت الترجمة", color = Color(0xFF34D399))
                }
                OutlinedButton(onClick = onResetEmbedTimer) {
                    Text("تصفير الوقت", color = Color(0xFFFCA5A5))
                }
            }
        }
    }
}
