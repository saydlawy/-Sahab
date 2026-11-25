package com.saydlawy.sahab.feature.instantwatch.presentation

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedPlaybackSource
import com.saydlawy.sahab.feature.instantwatch.player.ActivePlaybackSession
import com.saydlawy.sahab.feature.instantwatch.player.IsolatedPlayerWebView
import com.saydlawy.sahab.feature.instantwatch.player.NativeMedia3Player
import com.saydlawy.sahab.feature.instantwatch.player.PlayerSessionManager

@Composable
fun InstantPlayerScreen(
    session: ActivePlaybackSession,
    sessionManager: PlayerSessionManager,
    onProgressUpdate: (positionMs: Long, durationMs: Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val securityState by sessionManager.securityMonitor.state.collectAsState()
    var reloadKey by remember { mutableIntStateOf(0) }
    var errorMessage by remember(session) { mutableStateOf<String?>(null) }

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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
                            onProgressUpdate = onProgressUpdate,
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
            }
        }
    }
}
