package com.saydlawy.sahab.feature.instantwatch.presentation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.shape.CircleShape
import com.saydlawy.sahab.feature.instantwatch.player.WebViewPlayerController
import com.saydlawy.sahab.feature.instantwatch.player.Media3PlayerController
import com.saydlawy.sahab.feature.instantwatch.player.VideoResizeMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType
import com.saydlawy.sahab.feature.instantwatch.data.OnlineSubtitleOption
import com.saydlawy.sahab.feature.instantwatch.data.SubtitleSourceFilter
import com.saydlawy.sahab.feature.instantwatch.domain.EmbedServer
import com.saydlawy.sahab.feature.instantwatch.domain.ResolvedPlaybackSource
import com.saydlawy.sahab.feature.instantwatch.player.ActivePlaybackSession
import com.saydlawy.sahab.feature.instantwatch.player.IsolatedPlayerWebView
import com.saydlawy.sahab.feature.instantwatch.player.SniffedStream
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
    val settings by sessionManager.preferences.settings.collectAsState()
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
    var playbackDurMs by remember(session) { mutableLongStateOf(0L) }
    var currentClock by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("hh:mm a", Locale("ar"))
        while (true) {
            currentClock = fmt.format(Date())
            delay(30_000L)
        }
    }
    var embedTimerRunning by remember(session) { mutableStateOf(false) }
    var autoSyncActive by remember(session, reloadKey) { mutableStateOf(false) }
    var lastSavedProgressAt by remember(session) { mutableLongStateOf(0L) }

    // Full subtitle control panel state
    var selectedSource by remember { mutableStateOf(SubtitleSourceFilter.ALL) }
    var manualQuery by remember(session) {
        mutableStateOf(session.item.originalTitle.ifBlank { session.item.title })
    }
    var onlineSearching by remember { mutableStateOf(false) }
    var onlineOptions by remember(session) { mutableStateOf<List<OnlineSubtitleOption>>(emptyList()) }
    var onlineStatus by remember(session) { mutableStateOf<String?>(null) }
    var previewOptionId by remember(session) { mutableStateOf<String?>(null) }
    var previewText by remember(session) { mutableStateOf<String?>(null) }
    val downloadedCache = remember(session) { mutableStateMapOf<String, String>() }

    var selectedServer by remember(session) { mutableStateOf(EmbedServer.VIDLINK) }
    var showServerPanel by remember { mutableStateOf(false) }
    var showEnginePanel by remember { mutableStateOf(false) }
    var useExoPlayer by remember(session, selectedServer) { mutableStateOf(false) }
    var sniffedStreams by remember(session, selectedServer, reloadKey) { mutableStateOf<List<SniffedStream>>(emptyList()) }
    var selectedSniffedStream by remember(session, selectedServer, reloadKey) { mutableStateOf<SniffedStream?>(null) }
    val sniffedStream = selectedSniffedStream ?: sniffedStreams.firstOrNull()
    var exoStatusMessage by remember(session, selectedServer, reloadKey) { mutableStateOf<String?>(null) }
    val isEmbedSource = session.source is ResolvedPlaybackSource.IsolatedEmbed
    val serverPanelFocus = remember { FocusRequester() }
    val enginePanelFocus = remember { FocusRequester() }
    val subtitlePanelFocus = remember { FocusRequester() }
    val topBarFocus = remember { FocusRequester() }
    val playerFocus = remember { FocusRequester() }
    val webViewController = remember(session, selectedServer, reloadKey) { WebViewPlayerController() }
    val media3Controller = remember(session, selectedServer, reloadKey, useExoPlayer) { Media3PlayerController() }
    val torrentSettings by sessionManager.torrentBridge.settings.collectAsState()
    val audioTracks by media3Controller.audioTracks.collectAsState()
    val embeddedSubtitleTracks by media3Controller.subtitleTracks.collectAsState()
    val videoResizeMode by media3Controller.resizeMode.collectAsState()
    var showTracksPanel by remember { mutableStateOf(false) }
    val tracksPanelFocus = remember { FocusRequester() }
    var resizeBadgeText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(resizeBadgeText) {
        if (resizeBadgeText != null) {
            delay(1_400L)
            resizeBadgeText = null
        }
    }
    var nativeIsPlaying by remember(session, selectedServer, reloadKey, useExoPlayer) { mutableStateOf(true) }
    val requestTopBarFocus: () -> Unit = {
        showTopBar = true
        scope.launch {
            delay(80L)
            runCatching { topBarFocus.requestFocus() }
        }
    }
    // Give the player's own top bar the initial focus (the navigation rail is hidden while playing).
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        keyboardController?.hide()
        delay(450L)
        runCatching { topBarFocus.requestFocus() }
    }
    // After any panel closes, return focus to the top bar instead of an arbitrary chip.
    val anyPanelOpen = showSubtitlePanel || showServerPanel || showEnginePanel || showTracksPanel
    var hadPanelOpen by remember { mutableStateOf(false) }
    LaunchedEffect(anyPanelOpen) {
        if (!anyPanelOpen) {
            keyboardController?.hide()
        }
        if (hadPanelOpen && !anyPanelOpen) {
            delay(120L)
            if (showTopBar) {
                runCatching { topBarFocus.requestFocus() }
            } else {
                runCatching { playerFocus.requestFocus() }
            }
        }
        hadPanelOpen = anyPanelOpen
    }
    var controlsPing by remember { mutableIntStateOf(0) }
    LaunchedEffect(showTopBar, anyPanelOpen, controlsPing) {
        if (showTopBar && !anyPanelOpen) {
            delay(5_000L)
            showTopBar = false
        }
    }
    LaunchedEffect(showTopBar, anyPanelOpen, useExoPlayer) {
        if (!showTopBar && !anyPanelOpen) {
            delay(120L)
            runCatching { playerFocus.requestFocus() }
        }
    }

    // True full-screen (hide system status & navigation bars while playing)
    DisposableEffect(context) {
        val window = (context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Tiered BackHandler: Close panels -> Show top bar -> Close player
    BackHandler(enabled = true) {
        when {
            showTracksPanel -> showTracksPanel = false
            showSubtitlePanel -> showSubtitlePanel = false
            showServerPanel -> showServerPanel = false
            showEnginePanel -> showEnginePanel = false
            showTopBar -> showTopBar = false
            else -> onClose()
        }
    }

    LaunchedEffect(showEnginePanel) {
        if (showEnginePanel) {
            runCatching { enginePanelFocus.requestFocus() }
        }
    }
    LaunchedEffect(showTracksPanel) {
        if (showTracksPanel) {
            runCatching { tracksPanelFocus.requestFocus() }
        }
    }

    LaunchedEffect(showServerPanel) {
        if (showServerPanel) {
            runCatching { serverPanelFocus.requestFocus() }
        }
    }

    LaunchedEffect(showSubtitlePanel) {
        if (showSubtitlePanel) {
            runCatching { subtitlePanelFocus.requestFocus() }
        }
    }

    LaunchedEffect(isEmbedSource, embedTimerRunning, autoSyncActive, subtitleCues.isNotEmpty()) {
        if (isEmbedSource && embedTimerRunning && !autoSyncActive && subtitleCues.isNotEmpty()) {
            while (true) {
                delay(250L)
                playbackPosMs += 250L
            }
        }
    }

    LaunchedEffect(session) {
        if (isEmbedSource || session.releaseHint.isNullOrBlank()) return@LaunchedEffect
        if (!settings.autoArabicSubtitles || subtitleCues.isNotEmpty()) return@LaunchedEffect
        delay(1800L)
        if (subtitleCues.isNotEmpty()) return@LaunchedEffect
        val hasEmbeddedArabic = embeddedSubtitleTracks.any { track ->
            val l = track.label.lowercase()
            l.contains("arab") || l.contains("عرب") || l == "ar" || l == "ara"
        }
        if (hasEmbeddedArabic) return@LaunchedEffect

        val resolvedImdbId = session.item.imdbId?.takeIf { it.isNotBlank() }
            ?: sessionManager.tmdbRepository
                .getDetails(session.item.tmdbId, session.item.mediaType)
                ?.imdbId
        val found = sessionManager.onlineSubtitleFetcher.searchArabic(
            tmdbId = session.item.tmdbId,
            imdbId = resolvedImdbId,
            isTv = session.item.mediaType == InstantMediaType.TV,
            seasonNumber = session.seasonNumber,
            episodeNumber = session.episodeNumber,
            sourceFilter = SubtitleSourceFilter.ALL,
            subdlApiKey = settings.subdlApiKey,
            openSubtitlesApiKey = settings.openSubtitlesApiKey,
            releaseHint = session.releaseHint,
        )
        if (found.isEmpty()) return@LaunchedEffect
        onlineOptions = found

        for (opt in found.take(3)) {
            val raw = downloadedCache[opt.id]
                ?: sessionManager.onlineSubtitleFetcher.downloadSubtitleText(
                    option = opt,
                    openSubtitlesApiKey = settings.openSubtitlesApiKey,
                )?.also { downloadedCache[opt.id] = it }
                ?: continue
            if (sessionManager.onlineSubtitleFetcher.isLikelyPersian(raw)) continue
            val parsed = SubtitleParser.parse(raw)
            if (parsed.isNotEmpty()) {
                subtitleCues = parsed
                subtitlesVisible = true
                onlineStatus = "تمت المزامنة تلقائياً: ${opt.label} (${parsed.size} سطر)"
                break
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
                        headers = session.headers,
                        preferArabicEmbeddedSubtitles = torrentSettings.preferArabicSubtitle,
                        disableEmbeddedSubtitles = subtitleCues.isNotEmpty(),
                        onRequestExitFocus = requestTopBarFocus,
                        focusRequester = playerFocus,
                        controller = media3Controller,
                        showInternalOsd = false,
                        onToggleOverlay = {
                            showTopBar = !showTopBar
                            if (showTopBar) controlsPing++
                        },
                        onPlaybackStateChanged = { playing, dur ->
                            nativeIsPlaying = playing
                            if (dur > 0L) playbackDurMs = dur
                        },
                        subtitle = src.subtitle,
                        resumePositionMs = session.resumePositionMs,
                        onProgressUpdate = { pos, dur ->
                            playbackPosMs = pos
                            if (dur > 0L) playbackDurMs = dur
                            onProgressUpdate(pos, dur)
                        },
                        onPlaybackError = { err -> errorMessage = err },
                        onPositionTick = { pos -> playbackPosMs = pos },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                is ResolvedPlaybackSource.IsolatedEmbed -> {
                    val activeEmbedUrl = remember(src.embedUrl, selectedServer, session) {
                        selectedServer.buildUrl(
                            defaultVidLinkUrl = src.embedUrl,
                            tmdbId = session.item.tmdbId,
                            mediaType = session.item.mediaType,
                            seasonNumber = session.seasonNumber,
                            episodeNumber = session.episodeNumber,
                        )
                    }
                    val captured = sniffedStream
                    if (useExoPlayer && captured != null) {
                        NativeMedia3Player(
                            streamUrl = captured.url,
                            onRequestExitFocus = requestTopBarFocus,
                            focusRequester = playerFocus,
                            controller = media3Controller,
                            showInternalOsd = false,
                            onToggleOverlay = {
                                showTopBar = !showTopBar
                                if (showTopBar) controlsPing++
                            },
                            onPlaybackStateChanged = { playing, dur ->
                                nativeIsPlaying = playing
                                if (dur > 0L) playbackDurMs = dur
                            },
                            subtitle = src.subtitle,
                            resumePositionMs = playbackPosMs.takeIf { it > 0L } ?: session.resumePositionMs,
                            onProgressUpdate = { pos, dur ->
                                playbackPosMs = pos
                                if (dur > 0L) playbackDurMs = dur
                                onProgressUpdate(pos, dur)
                            },
                            onPlaybackError = { err ->
                                exoStatusMessage = "رفض السيرفر التشغيل في ExoPlayer: $err"
                                useExoPlayer = false
                                showEnginePanel = true
                            },
                            headers = captured.headers,
                            onPositionTick = { pos ->
                                autoSyncActive = true
                                playbackPosMs = pos
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        IsolatedPlayerWebView(
                            embedUrl = activeEmbedUrl,
                            reloadKey = reloadKey,
                            navigationGuard = sessionManager.navigationGuard,
                            securityPolicy = sessionManager.webViewSecurityPolicy,
                            securityMonitor = sessionManager.securityMonitor,
                            onPlaybackError = { err -> errorMessage = err },
                            onStreamSniffed = { stream ->
                                if (sniffedStreams.none { it.url == stream.url }) {
                                    sniffedStreams = sniffedStreams + stream
                                    if (selectedSniffedStream == null) {
                                        selectedSniffedStream = stream
                                    }
                                }
                                exoStatusMessage = null
                            },
                            onRequestExitFocus = requestTopBarFocus,
                            focusRequester = playerFocus,
                            controller = webViewController,
                            onPlaybackSync = { sync ->
                                autoSyncActive = true
                                playbackPosMs = sync.positionMs
                                if (sync.durationMs > 0L) playbackDurMs = sync.durationMs
                                embedTimerRunning = sync.isPlaying
                                val now = System.currentTimeMillis()
                                if (sync.durationMs > 60_000L && sync.positionMs > 0L && now - lastSavedProgressAt > 10_000L) {
                                    lastSavedProgressAt = now
                                    onProgressUpdate(sync.positionMs, sync.durationMs)
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
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

        // Cinematic responsive floating top bar
        val isCompactWidth = LocalConfiguration.current.screenWidthDp < 600
        val playerTitle = remember(session) {
            buildString {
                append(session.item.title)
                if (session.seasonNumber != null && session.episodeNumber != null) {
                    append(" • م${session.seasonNumber} ح${session.episodeNumber}")
                }
            }
        }
        if (showTopBar && !anyPanelOpen) {
            // 1. Floating Top Pure-Black Glass Card (Clock on left, Title + Back circle on right)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = if (isCompactWidth) 14.dp else 24.dp, vertical = 14.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xCC0A0A0D))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = currentClock,
                        color = Color(0xFFD4D4D8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Text(
                        text = playerTitle,
                        color = Color.White,
                        fontSize = if (isCompactWidth) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                    CinemaIconCircleButton(
                        iconType = "back",
                        sizeDp = 38,
                        onClick = onClose,
                    )
                }
            }

            // 2. Floating Bottom Pure-Black Glass Card (exact match to target screenshot)
            run {
                val isNativeActive = !isEmbedSource || useExoPlayer
                val isCurrentlyPlaying = if (isNativeActive) nativeIsPlaying else embedTimerRunning
                val triggerTogglePlay = {
                    controlsPing++
                    if (isNativeActive) media3Controller.togglePlayback() else webViewController.togglePlayback()
                }
                val triggerSeekDelta: (Int) -> Unit = { delta ->
                    controlsPing++
                    if (isNativeActive) media3Controller.seekBy(delta) else webViewController.seekBy(delta)
                }
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompactWidth) 14.dp else 24.dp, vertical = 16.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xD90A0A0D))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val safeDur = playbackDurMs.coerceAtLeast(0L)
                        val safePos = playbackPosMs.coerceIn(0L, if (safeDur > 0L) safeDur else Long.MAX_VALUE)
                        val progress = if (safeDur > 0L) (safePos.toFloat() / safeDur.toFloat()).coerceIn(0f, 1f) else 0f
                        val remaining = (safeDur - safePos).coerceAtLeast(0L)

                        // Progress Track with Red/White Target Thumb
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(20.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            val trackW = maxWidth
                            val thumbSize = 18.dp
                            val thumbOffset = ((trackW - thumbSize) * progress).coerceAtLeast(0.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x2EFFFFFF)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progress)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE11D48)),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .offset(x = thumbOffset)
                                    .size(thumbSize)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE11D48))
                                    .border(1.dp, Color(0x66FFFFFF), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                        }

                        // Time Row (00:05 / 1:39:07   ...   -1:39:01)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = formatOverlayClock(safePos),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (safeDur > 0L) {
                                    Text(
                                        text = " / ${formatOverlayClock(safeDur)}",
                                        color = Color(0xFF9CA3AF),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                            if (safeDur > 0L) {
                                Text(
                                    text = "-${formatOverlayClock(remaining)}",
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 12.sp,
                                )
                            }
                        }

                        // Controls Row
                        if (isCompactWidth) {
                            // Portrait: Transport row on top, tool icons row below
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            ) {
                                CinemaIconCircleButton(
                                    iconType = "prev",
                                    sizeDp = 38,
                                    onClick = {
                                        controlsPing++
                                        triggerSeekDelta(-30)
                                    },
                                )
                                CinemaIconCircleButton(
                                    textLabel = "-10",
                                    sizeDp = 40,
                                    onClick = {
                                        controlsPing++
                                        triggerSeekDelta(-10)
                                    },
                                )
                                CinemaHeroPlayButton(
                                    isPlaying = isCurrentlyPlaying,
                                    modifier = Modifier.focusRequester(topBarFocus),
                                    onClick = {
                                        controlsPing++
                                        triggerTogglePlay()
                                    },
                                )
                                CinemaIconCircleButton(
                                    textLabel = "+10",
                                    sizeDp = 40,
                                    onClick = {
                                        controlsPing++
                                        triggerSeekDelta(10)
                                    },
                                )
                                CinemaIconCircleButton(
                                    iconType = "next",
                                    sizeDp = 38,
                                    onClick = {
                                        controlsPing++
                                        triggerSeekDelta(30)
                                    },
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                            ) {
                                CinemaIconSquareButton(
                                    iconType = "hide",
                                    onClick = { showTopBar = false },
                                )
                                CinemaIconSquareButton(
                                    iconType = "server",
                                    onClick = {
                                        controlsPing++
                                        showServerPanel = !showServerPanel
                                        if (showServerPanel) {
                                            showEnginePanel = false
                                            showSubtitlePanel = false
                                        }
                                    },
                                )
                                CinemaIconSquareButton(
                                    iconType = "cc",
                                    active = subtitleCues.isNotEmpty(),
                                    onClick = {
                                        controlsPing++
                                        showSubtitlePanel = !showSubtitlePanel
                                    },
                                )
                                CinemaIconSquareButton(
                                    iconType = "quality",
                                    active = useExoPlayer || sniffedStream != null,
                                    onClick = {
                                        controlsPing++
                                        showEnginePanel = !showEnginePanel
                                        if (showEnginePanel) {
                                            showServerPanel = false
                                            showSubtitlePanel = false
                                            showTracksPanel = false
                                        }
                                    },
                                )
                                if (isNativeActive) {
                                    CinemaIconSquareButton(
                                        iconType = "audio",
                                        active = audioTracks.size > 1 || embeddedSubtitleTracks.isNotEmpty(),
                                        onClick = {
                                            controlsPing++
                                            showTracksPanel = !showTracksPanel
                                            if (showTracksPanel) {
                                                showServerPanel = false
                                                showSubtitlePanel = false
                                                showEnginePanel = false
                                            }
                                        },
                                    )
                                    CinemaIconSquareButton(
                                        iconType = "resize",
                                        active = videoResizeMode != VideoResizeMode.FIT,
                                        onClick = {
                                            controlsPing++
                                            val modes = VideoResizeMode.entries
                                            val next = modes[(modes.indexOf(videoResizeMode) + 1) % modes.size]
                                            media3Controller.cycleResizeMode()
                                            resizeBadgeText = "الأبعاد: ${next.labelAr}"
                                        },
                                    )
                                } else {
                                    CinemaIconSquareButton(
                                        iconType = "audio",
                                        onClick = {
                                            controlsPing++
                                            webViewController.toggleMute()
                                        },
                                    )
                                }
                            }
                        } else {
                            // Landscape / TV: Exact single-row layout matching screenshot
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.align(Alignment.CenterStart),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    CinemaIconSquareButton(
                                        iconType = "hide",
                                        onClick = { showTopBar = false },
                                    )
                                    CinemaIconSquareButton(
                                        iconType = "server",
                                        onClick = {
                                            controlsPing++
                                            showServerPanel = !showServerPanel
                                            if (showServerPanel) {
                                                showEnginePanel = false
                                                showSubtitlePanel = false
                                            }
                                        },
                                    )
                                    CinemaIconSquareButton(
                                        iconType = "cc",
                                        active = subtitleCues.isNotEmpty(),
                                        onClick = {
                                            controlsPing++
                                            showSubtitlePanel = !showSubtitlePanel
                                        },
                                    )
                                    CinemaIconSquareButton(
                                        iconType = "quality",
                                        active = useExoPlayer || sniffedStream != null,
                                        onClick = {
                                            controlsPing++
                                            showEnginePanel = !showEnginePanel
                                            if (showEnginePanel) {
                                                showServerPanel = false
                                                showSubtitlePanel = false
                                                showTracksPanel = false
                                            }
                                        },
                                    )
                                    if (isNativeActive) {
                                        CinemaIconSquareButton(
                                            iconType = "audio",
                                            active = audioTracks.size > 1 || embeddedSubtitleTracks.isNotEmpty(),
                                            onClick = {
                                                controlsPing++
                                                showTracksPanel = !showTracksPanel
                                                if (showTracksPanel) {
                                                    showServerPanel = false
                                                    showSubtitlePanel = false
                                                    showEnginePanel = false
                                                }
                                            },
                                        )
                                        CinemaIconSquareButton(
                                            iconType = "resize",
                                            active = videoResizeMode != VideoResizeMode.FIT,
                                            onClick = {
                                                controlsPing++
                                                val modes = VideoResizeMode.entries
                                                val next = modes[(modes.indexOf(videoResizeMode) + 1) % modes.size]
                                                media3Controller.cycleResizeMode()
                                                resizeBadgeText = "الأبعاد: ${next.labelAr}"
                                            },
                                        )
                                    } else {
                                        CinemaIconSquareButton(
                                            iconType = "audio",
                                            onClick = {
                                                controlsPing++
                                                webViewController.toggleMute()
                                            },
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.align(Alignment.Center),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CinemaIconCircleButton(
                                        iconType = "prev",
                                        sizeDp = 40,
                                        onClick = {
                                            controlsPing++
                                            triggerSeekDelta(-30)
                                        },
                                    )
                                    CinemaIconCircleButton(
                                        textLabel = "-10",
                                        sizeDp = 42,
                                        onClick = {
                                            controlsPing++
                                            triggerSeekDelta(-10)
                                        },
                                    )
                                    CinemaHeroPlayButton(
                                        isPlaying = isCurrentlyPlaying,
                                        modifier = Modifier.focusRequester(topBarFocus),
                                        onClick = {
                                            controlsPing++
                                            triggerTogglePlay()
                                        },
                                    )
                                    CinemaIconCircleButton(
                                        textLabel = "+10",
                                        sizeDp = 42,
                                        onClick = {
                                            controlsPing++
                                            triggerSeekDelta(10)
                                        },
                                    )
                                    CinemaIconCircleButton(
                                        iconType = "next",
                                        sizeDp = 40,
                                        onClick = {
                                            controlsPing++
                                            triggerSeekDelta(30)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (!anyPanelOpen) {
            CinemaIconCircleButton(
                iconType = "quality",
                sizeDp = 38,
                onClick = {
                    showTopBar = true
                    controlsPing++
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp),
            )
        }

        if (showEnginePanel && isEmbedSource) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .onPreviewKeyEvent { it.key == Key.DirectionLeft || it.key == Key.DirectionRight }
                    .widthIn(max = 360.dp)
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF50F172A))
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
                        text = "اختر مشغل الفيديو",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    CompactBarChip(
                        label = "إغلاق",
                        bgColor = Color(0xFF1E293B),
                        textColor = Color(0xFF60A5FA),
                        onClick = { showEnginePanel = false },
                    )
                }

                // Option 1: WebView
                run {
                    val active = !useExoPlayer
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(enginePanelFocus)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (focused) 2.dp else 0.dp,
                                color = if (focused) Color(0xFF60A5FA) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(if (active) Color(0xFF2563EB) else if (focused) Color(0xFF334155) else Color(0xFF1E293B))
                            .onFocusChanged { focused = it.isFocused }
                            .clickable {
                                useExoPlayer = false
                                showEnginePanel = false
                            }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (active) "مشغل الويب المحمي (WebView)  ✓" else "مشغل الويب المحمي (WebView)",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "الأكثر استقراراً مع السيرفرات المحمية + مزامنة تلقائية للترجمة وحظر للإعلانات.",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                        )
                    }
                }

                // Option 2: ExoPlayer
                run {
                    val active = useExoPlayer
                    val ready = sniffedStream != null
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (focused) 2.dp else 0.dp,
                                color = if (focused) Color(0xFF60A5FA) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(if (active) Color(0xFF059669) else if (focused) Color(0xFF334155) else Color(0xFF1E293B))
                            .onFocusChanged { focused = it.isFocused }
                            .clickable {
                                if (ready) {
                                    exoStatusMessage = null
                                    useExoPlayer = true
                                    showEnginePanel = false
                                } else {
                                    exoStatusMessage = "شغّل الفيديو في WebView لثوانٍ أولاً حتى يتم التقاط رابط البث (.m3u8)."
                                }
                            }
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = when {
                                active -> "المشغل الأصلي (ExoPlayer)  ✓"
                                ready -> "المشغل الأصلي (ExoPlayer) — جاهز ✓"
                                else -> "المشغل الأصلي (ExoPlayer) — بانتظار الرابط"
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = if (ready) {
                                "تم التقاط ${sniffedStreams.size} رابط بث مباشر مع الـ Cookies. اضغط للتبديل إلى ExoPlayer."
                            } else {
                                "ينتظر التقاط رابط البث (.m3u8) من السيرفر؛ ابدأ تشغيل الفيديو في WebView ثم جرّب التبديل."
                            },
                            color = if (ready) Color(0xFFA7F3D0) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                        )
                    }
                }

                if (sniffedStreams.isNotEmpty()) {
                    Text(
                        text = "الجودات / الروابط الملتقطة (${sniffedStreams.size})",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    sniffedStreams.forEachIndexed { idx, stream ->
                        val isSelected = useExoPlayer && sniffedStream?.url == stream.url
                        var qFocused by remember { mutableStateOf(false) }
                        val label = stream.label.ifBlank { "رابط #${idx + 1}" }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (qFocused) 2.dp else 0.dp,
                                    color = if (qFocused) Color(0xFF60A5FA) else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .background(
                                    when {
                                        isSelected -> Color(0xFF065F46)
                                        qFocused -> Color(0xFF334155)
                                        else -> Color(0xFF1E293B)
                                    },
                                )
                                .onFocusChanged { qFocused = it.isFocused }
                                .clickable {
                                    selectedSniffedStream = stream
                                    exoStatusMessage = null
                                    useExoPlayer = true
                                    showEnginePanel = false
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${idx + 1}. $label",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                            if (isSelected) {
                                Text(
                                    text = "نشط ✓",
                                    color = Color(0xFF6EE7B7),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }

                if (!exoStatusMessage.isNullOrBlank()) {
                    Text(
                        text = exoStatusMessage.orEmpty(),
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF450A0A))
                            .padding(8.dp),
                    )
                }
            }
        }

        resizeBadgeText?.let { badge ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 74.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xE60F172A))
                    .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(text = badge, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (showTracksPanel) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .onPreviewKeyEvent { it.key == Key.DirectionLeft || it.key == Key.DirectionRight }
                    .widthIn(max = 360.dp)
                    .heightIn(max = 460.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF50F172A))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(16.dp))
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "الصوت والترجمة المدمجة",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    CompactBarChip(
                        label = "إغلاق",
                        bgColor = Color(0xFF1E293B),
                        textColor = Color(0xFF60A5FA),
                        onClick = { showTracksPanel = false },
                    )
                }

                Text(
                    text = "مسارات الصوت (${audioTracks.size})",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (audioTracks.isEmpty()) {
                    Text(
                        text = "لا توجد مسارات صوت إضافية في هذا الملف.",
                        color = Color(0xFF64748B),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    audioTracks.forEachIndexed { idx, track ->
                        var focused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (idx == 0) Modifier.focusRequester(tracksPanelFocus) else Modifier)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (focused) 2.dp else 0.dp,
                                    color = if (focused) Color(0xFF60A5FA) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .background(
                                    when {
                                        track.isSelected -> Color(0xFF2563EB)
                                        focused -> Color(0xFF334155)
                                        else -> Color(0xFF1E293B)
                                    },
                                )
                                .onFocusChanged { focused = it.isFocused }
                                .clickable {
                                    media3Controller.selectAudioTrack(track.groupIndex, track.trackIndex)
                                    showTracksPanel = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = if (track.isSelected) "✓ ${track.label}" else track.label,
                                color = Color.White,
                                fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                Text(
                    text = "الترجمة المدمجة (${embeddedSubtitleTracks.size})",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                run {
                    val noneSelected = embeddedSubtitleTracks.none { it.isSelected }
                    var offFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (audioTracks.isEmpty()) Modifier.focusRequester(tracksPanelFocus) else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (offFocused) 2.dp else 0.dp,
                                color = if (offFocused) Color(0xFF60A5FA) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(
                                when {
                                    noneSelected -> Color(0xFF475569)
                                    offFocused -> Color(0xFF334155)
                                    else -> Color(0xFF1E293B)
                                },
                            )
                            .onFocusChanged { offFocused = it.isFocused }
                            .clickable {
                                media3Controller.disableEmbeddedSubtitle()
                                showTracksPanel = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = if (noneSelected) "✓ إيقاف الترجمة المدمجة" else "إيقاف الترجمة المدمجة",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                embeddedSubtitleTracks.forEach { track ->
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (focused) 2.dp else 0.dp,
                                color = if (focused) Color(0xFF60A5FA) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(
                                when {
                                    track.isSelected -> Color(0xFF059669)
                                    focused -> Color(0xFF334155)
                                    else -> Color(0xFF1E293B)
                                },
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .clickable {
                                subtitleCues = emptyList()
                                media3Controller.selectEmbeddedSubtitle(track.groupIndex, track.trackIndex)
                                showTracksPanel = false
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = if (track.isSelected) "✓ ${track.label}" else track.label,
                            color = Color.White,
                            fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }

        if (showServerPanel && isEmbedSource) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .onPreviewKeyEvent { it.key == Key.DirectionLeft || it.key == Key.DirectionRight }
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF50F172A))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "اختر سيرفر التشغيل",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    CompactBarChip(
                        label = "إغلاق",
                        bgColor = Color(0xFF1E293B),
                        textColor = Color(0xFF60A5FA),
                        onClick = { showServerPanel = false },
                    )
                }
                EmbedServer.entries.forEachIndexed { idx, server ->
                    val active = server == selectedServer
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (server == selectedServer) Modifier.focusRequester(serverPanelFocus) else Modifier)
                            .clip(RoundedCornerShape(10.dp))
                            .border(
                                width = if (focused) 2.dp else 0.dp,
                                color = if (focused) Color(0xFF60A5FA) else Color.Transparent,
                                shape = RoundedCornerShape(10.dp),
                            )
                            .background(
                                when {
                                    active -> Color(0xFF2563EB)
                                    focused -> Color(0xFF334155)
                                    else -> Color(0xFF1E293B)
                                }
                            )
                            .onFocusChanged { focused = it.isFocused }
                            .clickable {
                                selectedServer = server
                                showServerPanel = false
                                errorMessage = null
                                reloadKey++
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = if (active) "${server.label}  ✓" else server.label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        )
                    }
                }
            }
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
                autoSyncActive = autoSyncActive,
                selectedSource = selectedSource,
                manualQuery = manualQuery,
                onlineSearching = onlineSearching,
                onlineOptions = onlineOptions,
                onlineStatus = onlineStatus,
                previewOptionId = previewOptionId,
                previewText = previewText,
                onSelectSource = { selectedSource = it },
                onManualQueryChange = { manualQuery = it },
                onSearchOnline = { useManualQuery ->
                    scope.launch {
                        onlineSearching = true
                        previewOptionId = null
                        previewText = null
                        onlineStatus = "جارٍ البحث عن ترجمة عربية..."
                        val resolvedImdbId = session.item.imdbId?.takeIf { it.isNotBlank() }
                            ?: sessionManager.tmdbRepository
                                .getDetails(session.item.tmdbId, session.item.mediaType)
                                ?.imdbId
                        val found = sessionManager.onlineSubtitleFetcher.searchArabic(
                            tmdbId = session.item.tmdbId,
                            imdbId = resolvedImdbId,
                            isTv = session.item.mediaType == InstantMediaType.TV,
                            seasonNumber = session.seasonNumber,
                            episodeNumber = session.episodeNumber,
                            manualQuery = if (useManualQuery) manualQuery else null,
                            sourceFilter = selectedSource,
                            subdlApiKey = settings.subdlApiKey,
                            openSubtitlesApiKey = settings.openSubtitlesApiKey,
                            releaseHint = session.releaseHint,
                        )
                        onlineOptions = found
                        onlineStatus = when {
                            found.isNotEmpty() -> "تم العثور على ${found.size} ترجمة، اضغط معاينة أو تطبيق:"
                            selectedSource == SubtitleSourceFilter.SUBDL && settings.subdlApiKey.isBlank() ->
                                "أضف مفتاح SubDL API من إعدادات المشاهدة الفورية أولاً."
                            selectedSource == SubtitleSourceFilter.OPENSUB_OFFICIAL && settings.openSubtitlesApiKey.isBlank() ->
                                "أضف مفتاح OpenSubtitles API من إعدادات المشاهدة الفورية أولاً."
                            else -> "لم يتم العثور على ترجمة عربية. جرّب البحث اليدوي أو تغيير المصدر."
                        }
                        onlineSearching = false
                    }
                },
                onPreviewOnline = { option ->
                    scope.launch {
                        onlineSearching = true
                        val cached = downloadedCache[option.id]
                            ?: sessionManager.onlineSubtitleFetcher.downloadSubtitleText(
                                option = option,
                                openSubtitlesApiKey = settings.openSubtitlesApiKey,
                            )?.also { downloadedCache[option.id] = it }

                        if (cached == null) {
                            onlineStatus = "تعذر تحميل المعاينة لهذا الخيار."
                        } else {
                            previewOptionId = option.id
                            val isPersian = sessionManager.onlineSubtitleFetcher.isLikelyPersian(cached)
                            val sample = sessionManager.onlineSubtitleFetcher.extractPreviewLines(cached)
                            previewText = if (isPersian) "⚠️ تحذير: يبدو أن النص فارسي:\n$sample" else sample
                        }
                        onlineSearching = false
                    }
                },
                onSelectOnline = { option ->
                    scope.launch {
                        onlineSearching = true
                        onlineStatus = "جارٍ تطبيق الترجمة..."
                        val text = downloadedCache[option.id]
                            ?: sessionManager.onlineSubtitleFetcher.downloadSubtitleText(
                                option = option,
                                openSubtitlesApiKey = settings.openSubtitlesApiKey,
                            )?.also { downloadedCache[option.id] = it }

                        val isPersian = text != null && sessionManager.onlineSubtitleFetcher.isLikelyPersian(text)
                        val parsed = if (isPersian) emptyList() else text?.let { SubtitleParser.parse(it) }.orEmpty()
                        if (isPersian) {
                            onlineStatus = "تم رفض هذه الترجمة لأنها فارسية، جرّب خياراً آخر."
                        } else if (parsed.isNotEmpty()) {
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
                    .padding(horizontal = 14.dp, vertical = 44.dp)
                    .widthIn(max = 390.dp)
                    .focusRequester(subtitlePanelFocus),
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
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .scale(if (focused) 1.06f else 1f)
            .defaultMinSize(minHeight = 36.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color(0xFF38BDF8) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp),
            )
            .background(if (focused) bgColor.copy(alpha = 1f) else bgColor.copy(alpha = 0.92f))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) Color.White else textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
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
    autoSyncActive: Boolean,
    selectedSource: SubtitleSourceFilter,
    manualQuery: String,
    onlineSearching: Boolean,
    onlineOptions: List<OnlineSubtitleOption>,
    onlineStatus: String?,
    previewOptionId: String?,
    previewText: String?,
    onSelectSource: (SubtitleSourceFilter) -> Unit,
    onManualQueryChange: (String) -> Unit,
    onSearchOnline: (useManualQuery: Boolean) -> Unit,
    onPreviewOnline: (OnlineSubtitleOption) -> Unit,
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
            .heightIn(max = 520.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xF50F172A))
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
                text = "لوحة تحكم الترجمة",
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

        // Source selector row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SubtitleSourceFilter.entries.forEach { src ->
                val isSelected = src == selectedSource
                var tabFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .border(if (tabFocused) 2.dp else 0.dp, if (tabFocused) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B))
                        .onFocusChanged { tabFocused = it.isFocused }
                        .clickable { onSelectSource(src) }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = src.label,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
            }
        }

        // Manual search input
        OutlinedTextField(
            value = manualQuery,
            onValueChange = onManualQueryChange,
            placeholder = { Text("بحث يدوي باسم الفيلم أو النسخة...", color = Color(0xFF64748B), fontSize = 12.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color(0xFF3B82F6),
                unfocusedBorderColor = Color(0xFF334155),
                cursorColor = Color(0xFF60A5FA),
            ),
            modifier = Modifier.fillMaxWidth().tvFieldEscape(),
        )

        // Action buttons: Auto search | Manual search | Local file
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = { onSearchOnline(false) },
                enabled = !onlineSearching,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (onlineSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("بحث تلقائي", color = Color.White, fontSize = 11.sp, maxLines = 1)
                }
            }
            OutlinedButton(
                onClick = { onSearchOnline(true) },
                enabled = !onlineSearching && manualQuery.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("بحث بالاسم", color = Color(0xFF93C5FD), fontSize = 11.sp, maxLines = 1)
            }
            OutlinedButton(
                onClick = onPickFile,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text("ملف محلي", color = Color(0xFFCBD5E1), fontSize = 11.sp, maxLines = 1)
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
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                onlineOptions.take(8).forEach { opt ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            var labelFocused by remember { mutableStateOf(false) }
                            Text(
                                text = "[${opt.sourceTag}] ${opt.label}",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(if (labelFocused) 2.dp else 0.dp, if (labelFocused) Color(0xFF38BDF8) else Color.Transparent, RoundedCornerShape(6.dp))
                                    .onFocusChanged { labelFocused = it.isFocused }
                                    .clickable { onSelectOnline(opt) }
                                    .padding(4.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                CompactBarChip(
                                    label = "معاينة",
                                    bgColor = Color(0xFF334155),
                                    textColor = Color(0xFFE2E8F0),
                                    onClick = { onPreviewOnline(opt) },
                                )
                                CompactBarChip(
                                    label = "تطبيق",
                                    bgColor = Color(0xFF1D4ED8),
                                    textColor = Color.White,
                                    onClick = { onSelectOnline(opt) },
                                )
                            }
                        }

                        if (previewOptionId == opt.id && !previewText.isNullOrBlank()) {
                            Text(
                                text = previewText,
                                color = Color(0xFFFDE68A),
                                fontSize = 11.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E293B))
                                    .padding(6.dp),
                            )
                        }
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

        // Sync controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "المزامنة: ${String.format("%+.1f", offsetSec)} ثانية",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (autoSyncActive) "مزامنة تلقائية ✓" else "مؤقت يدوي",
                color = if (autoSyncActive) Color(0xFF34D399) else Color(0xFFFBBF24),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SyncStepButton("-30ث", Modifier.weight(1f)) { onAdjustOffset(-30_000L) }
            SyncStepButton("-10ث", Modifier.weight(1f)) { onAdjustOffset(-10_000L) }
            SyncStepButton("+10ث", Modifier.weight(1f)) { onAdjustOffset(10_000L) }
            SyncStepButton("+30ث", Modifier.weight(1f)) { onAdjustOffset(30_000L) }
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
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .scale(if (focused) 1.04f else 1f)
            .defaultMinSize(minHeight = 40.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color(0xFF38BDF8) else Color(0xFF334155),
                shape = RoundedCornerShape(10.dp),
            )
            .background(
                when {
                    focused -> Color(0xFF2563EB)
                    highlight -> Color(0xFF1E3A8A)
                    else -> Color(0xFF1E293B)
                }
            )
            .onFocusChanged { focused = it.isFocused }
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

private fun formatOverlayClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val h = total / 3600L
    val m = (total % 3600L) / 60L
    val s = total % 60L
    return if (h > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}

@Composable
private fun CinemaHeroPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(54.dp)
            .scale(if (focused) 1.12f else 1f)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFBE123C), Color(0xFFE11D48), Color(0xFFF9A8D4)),
                ),
            )
            .border(
                width = if (focused) 2.5.dp else 1.5.dp,
                color = if (focused) Color.White else Color(0x99FBCFE8),
                shape = CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            if (isPlaying) {
                val barW = size.width * 0.26f
                val barH = size.height * 0.72f
                val top = (size.height - barH) / 2f
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.18f, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(3f, 3f),
                )
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width * 0.56f, top),
                    size = Size(barW, barH),
                    cornerRadius = CornerRadius(3f, 3f),
                )
            } else {
                val path = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.14f)
                    lineTo(size.width * 0.86f, size.height * 0.50f)
                    lineTo(size.width * 0.24f, size.height * 0.86f)
                    close()
                }
                drawPath(path, color = Color.White)
            }
        }
    }
}

@Composable
private fun CinemaIconCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconType: String? = null,
    textLabel: String? = null,
    sizeDp: Int = 40,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .scale(if (focused) 1.10f else 1f)
            .clip(CircleShape)
            .background(if (focused) Color(0xFF2D2D34) else Color(0xCC18181C))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else Color(0x26FFFFFF),
                shape = CircleShape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (textLabel != null) {
            Text(
                text = textLabel,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        } else if (iconType != null) {
            CinemaVectorGlyph(iconType = iconType, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CinemaIconSquareButton(
    iconType: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(40.dp)
            .scale(if (focused) 1.10f else 1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFF2D2D34) else Color(0xCC18181C))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> Color.White
                    active -> Color(0x8810B981)
                    else -> Color(0x24FFFFFF)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        CinemaVectorGlyph(iconType = iconType, modifier = Modifier.size(18.dp))
        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981)),
            )
        }
    }
}

@Composable
private fun CinemaVectorGlyph(
    iconType: String,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
        val c = Color.White
        when (iconType) {
            "back" -> {
                val y = size.height * 0.5f
                drawLine(c, Offset(size.width * 0.20f, y), Offset(size.width * 0.78f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.54f, size.height * 0.24f), Offset(size.width * 0.80f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.54f, size.height * 0.76f), Offset(size.width * 0.80f, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "prev" -> {
                drawLine(c, Offset(size.width * 0.24f, size.height * 0.22f), Offset(size.width * 0.24f, size.height * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(size.width * 0.76f, size.height * 0.22f)
                    lineTo(size.width * 0.36f, size.height * 0.50f)
                    lineTo(size.width * 0.76f, size.height * 0.78f)
                    close()
                }
                drawPath(path, color = c)
            }
            "next" -> {
                drawLine(c, Offset(size.width * 0.76f, size.height * 0.22f), Offset(size.width * 0.76f, size.height * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                val path = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.22f)
                    lineTo(size.width * 0.64f, size.height * 0.50f)
                    lineTo(size.width * 0.24f, size.height * 0.78f)
                    close()
                }
                drawPath(path, color = c)
            }
            "cc" -> {
                drawRoundRect(
                    color = c,
                    topLeft = Offset(size.width * 0.10f, size.height * 0.20f),
                    size = Size(size.width * 0.80f, size.height * 0.60f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = stroke,
                )
                drawLine(c, Offset(size.width * 0.24f, size.height * 0.54f), Offset(size.width * 0.52f, size.height * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.62f, size.height * 0.54f), Offset(size.width * 0.76f, size.height * 0.54f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.24f, size.height * 0.68f), Offset(size.width * 0.76f, size.height * 0.68f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "quality" -> {
                val w = size.width * 0.16f
                drawRoundRect(c, Offset(size.width * 0.16f, size.height * 0.52f), Size(w, size.height * 0.34f), CornerRadius(3f, 3f))
                drawRoundRect(c, Offset(size.width * 0.42f, size.height * 0.20f), Size(w, size.height * 0.66f), CornerRadius(3f, 3f))
                drawRoundRect(c, Offset(size.width * 0.68f, size.height * 0.38f), Size(w, size.height * 0.48f), CornerRadius(3f, 3f))
            }
            "server" -> {
                drawLine(c, Offset(size.width * 0.14f, size.height * 0.30f), Offset(size.width * 0.86f, size.height * 0.30f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.14f, size.height * 0.50f), Offset(size.width * 0.86f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.14f, size.height * 0.70f), Offset(size.width * 0.86f, size.height * 0.70f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(c, radius = size.minDimension * 0.11f, center = Offset(size.width * 0.36f, size.height * 0.30f))
                drawCircle(c, radius = size.minDimension * 0.11f, center = Offset(size.width * 0.66f, size.height * 0.50f))
                drawCircle(c, radius = size.minDimension * 0.11f, center = Offset(size.width * 0.42f, size.height * 0.70f))
            }
            "hide" -> {
                drawRoundRect(
                    color = c,
                    topLeft = Offset(size.width * 0.12f, size.height * 0.20f),
                    size = Size(size.width * 0.76f, size.height * 0.60f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = stroke,
                )
                drawRoundRect(
                    color = c,
                    topLeft = Offset(size.width * 0.48f, size.height * 0.46f),
                    size = Size(size.width * 0.28f, size.height * 0.22f),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
            "audio" -> {
                val path = Path().apply {
                    moveTo(size.width * 0.16f, size.height * 0.38f)
                    lineTo(size.width * 0.34f, size.height * 0.38f)
                    lineTo(size.width * 0.56f, size.height * 0.20f)
                    lineTo(size.width * 0.56f, size.height * 0.80f)
                    lineTo(size.width * 0.34f, size.height * 0.62f)
                    lineTo(size.width * 0.16f, size.height * 0.62f)
                    close()
                }
                drawPath(path, color = c)
                drawLine(c, Offset(size.width * 0.70f, size.height * 0.34f), Offset(size.width * 0.70f, size.height * 0.66f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.84f, size.height * 0.24f), Offset(size.width * 0.84f, size.height * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "resize" -> {
                drawLine(c, Offset(size.width * 0.16f, size.height * 0.36f), Offset(size.width * 0.16f, size.height * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.16f, size.height * 0.16f), Offset(size.width * 0.36f, size.height * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.64f, size.height * 0.16f), Offset(size.width * 0.84f, size.height * 0.16f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.84f, size.height * 0.16f), Offset(size.width * 0.84f, size.height * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.16f, size.height * 0.64f), Offset(size.width * 0.16f, size.height * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.16f, size.height * 0.84f), Offset(size.width * 0.36f, size.height * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.64f, size.height * 0.84f), Offset(size.width * 0.84f, size.height * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width * 0.84f, size.height * 0.84f), Offset(size.width * 0.84f, size.height * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}
