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
import androidx.compose.ui.platform.LocalContext
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
    var sniffedStream by remember(session, selectedServer, reloadKey) { mutableStateOf<SniffedStream?>(null) }
    var exoStatusMessage by remember(session, selectedServer, reloadKey) { mutableStateOf<String?>(null) }
    val isEmbedSource = session.source is ResolvedPlaybackSource.IsolatedEmbed
    val serverPanelFocus = remember { FocusRequester() }
    val enginePanelFocus = remember { FocusRequester() }
    val subtitlePanelFocus = remember { FocusRequester() }

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
            showSubtitlePanel -> showSubtitlePanel = false
            showServerPanel -> showServerPanel = false
            showEnginePanel -> showEnginePanel = false
            !showTopBar -> showTopBar = true
            else -> onClose()
        }
    }

    LaunchedEffect(showEnginePanel) {
        if (showEnginePanel) {
            runCatching { enginePanelFocus.requestFocus() }
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
                            subtitle = null,
                            resumePositionMs = playbackPosMs.takeIf { it > 0L } ?: session.resumePositionMs,
                            onProgressUpdate = { pos, dur ->
                                playbackPosMs = pos
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
                                sniffedStream = stream
                                exoStatusMessage = null
                            },
                            onPlaybackSync = { sync ->
                                autoSyncActive = true
                                playbackPosMs = sync.positionMs
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

        // Compact floating top bar
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
                    if (isEmbedSource) {
                        CompactBarChip(
                            label = if (useExoPlayer) "المشغل: Exo ✓" else if (sniffedStream != null) "المشغل: Web • Exo جاهز" else "المشغل: WebView",
                            bgColor = if (useExoPlayer) Color(0xFF065F46) else Color(0xFF1E3A8A),
                            textColor = if (useExoPlayer) Color(0xFF6EE7B7) else Color(0xFFBFDBFE),
                            onClick = {
                                showEnginePanel = !showEnginePanel
                                if (showEnginePanel) {
                                    showServerPanel = false
                                    showSubtitlePanel = false
                                }
                            },
                        )
                        CompactBarChip(
                            label = "سيرفر: ${selectedServer.label}",
                            bgColor = Color(0xFF312E81),
                            textColor = Color(0xFFC7D2FE),
                            onClick = {
                                showServerPanel = !showServerPanel
                                if (showServerPanel) {
                                    showEnginePanel = false
                                    showSubtitlePanel = false
                                }
                            },
                        )
                    }
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

        if (showEnginePanel && isEmbedSource) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
                    .widthIn(max = 360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF50F172A))
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
                            .focusable()
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
                            .focusable()
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
                                "تم التقاط رابط البث المباشر مع الـ Cookies. اضغط للتبديل إلى ExoPlayer."
                            } else {
                                "ينتظر التقاط رابط البث (.m3u8) من السيرفر؛ ابدأ تشغيل الفيديو في WebView ثم جرّب التبديل."
                            },
                            color = if (ready) Color(0xFFA7F3D0) else Color(0xFF94A3B8),
                            fontSize = 11.sp,
                        )
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

        if (showServerPanel && isEmbedSource) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 16.dp)
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
                            .then(if (idx == 0) Modifier.focusRequester(serverPanelFocus) else Modifier)
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
                            .focusable()
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
            .clip(RoundedCornerShape(6.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color(0xFF93C5FD) else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .background(if (focused) bgColor.copy(alpha = 1f) else bgColor)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B))
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
            modifier = Modifier.fillMaxWidth(),
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
                            Text(
                                text = "[${opt.sourceTag}] ${opt.label}",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(end = 6.dp),
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
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Color(0xFF60A5FA) else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
            )
            .background(
                when {
                    focused -> Color(0xFF2563EB)
                    highlight -> Color(0xFF1E3A8A)
                    else -> Color(0xFF1E293B)
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
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
