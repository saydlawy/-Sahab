package com.saydlawy.sahab.ui.player

import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.data.channel.EpgProgramEntity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Episode
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.data.SeriesDetails
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.components.rememberNow
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.findActivity
import com.saydlawy.sahab.util.formatDuration
import com.saydlawy.sahab.util.formatNowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import com.saydlawy.sahab.util.ChannelLogoResolver

// Design tokens from ExoPlayer/DESIGN.md & media-player/DESIGN.md
// ============================================================================
// MODE A: PHONE PORTRAIT PLAYER LAYOUT (Vertical: 16:9 Video + Info + List)
// ============================================================================

@OptIn(UnstableApi::class)
@Composable
internal fun PhonePortraitPlayerScreen(
    exo: ExoPlayer,
    item: PlayItem,
    playlist: List<PlayItem>,
    currentIndex: Int,
    isLive: Boolean,
    canSeek: Boolean = !isLive,
    isPlaying: Boolean,
    buffering: Boolean,
    reconnecting: Boolean,
    error: String?,
    progressFlow: StateFlow<PlaybackProgress>,
    playbackSpeed: Float,
    tracks: Tracks,
    textOff: Boolean,
    hasVideoOverride: Boolean,
    currentProgram: Program?,
    nextProgram: Program?,
    seriesDetails: SeriesDetails?,
    controlsVisible: Boolean,
    resizeMode: Int,
    onToggleControls: () -> Unit,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectPlaylistIndex: (Int) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenGuide: () -> Unit,
    onEnterLandscape: () -> Unit,
    onRetry: () -> Unit,
) {
    val videoBadge = remember(tracks, item.title) {
        SahabPlayer.videoBadge(tracks, qualityOf(item.title) ?: "FHD")
    }
    val audioLabel = remember(tracks) { SahabPlayer.activeAudioLabel(tracks) }
    val qualityLabel = remember(tracks, hasVideoOverride) {
        SahabPlayer.activeQualityButtonLabel(tracks, hasVideoOverride, isLive)
    }

    val allEpisodes = seriesDetails?.episodes.orEmpty()
    val seasons = remember(seriesDetails) { seriesDetails?.seasons.orEmpty() }
    val currentEpObj = remember(allEpisodes, item.id) { allEpisodes.firstOrNull { it.id == item.id } }
    var selectedSeason by remember(currentEpObj, seasons) {
        mutableIntStateOf(currentEpObj?.season ?: seasons.firstOrNull() ?: 1)
    }
    val seasonEpisodes = remember(allEpisodes, selectedSeason) {
        if (allEpisodes.isEmpty()) emptyList() else allEpisodes.filter { it.season == selectedSeason }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OsdSurfaceLowest),
    ) {
        // 1. Top 16:9 Video Viewport
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(Color.Black),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        player = exo
                    }
                },
                update = { it.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onToggleControls() })
                    },
            )

            if ((buffering || reconnecting) && error == null) {
                CircularProgressIndicator(
                    color = OsdCyan,
                    trackColor = Color.White.copy(alpha = 0.15f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // Touch Overlay inside 16:9 Video
            if (controlsVisible && error == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.48f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    // Top Row inside video: Back + Live/Quality + Fullscreen Landscape Button
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OsdIconSquareButton(
                            icon = Ic.Back,
                            description = Ar.back,
                            onClick = onBack,
                            size = 38.dp,
                            circle = true,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isLive) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Palette.Live)
                                        .padding(horizontal = 10.dp, vertical = 3.dp),
                                ) {
                                    Text(Ar.liveBadge, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                            FormatChip(videoBadge, OsdPrimary)
                            OsdIconSquareButton(
                                icon = Ic.Aspect,
                                description = Ar.fullscreen,
                                onClick = onEnterLandscape,
                                size = 38.dp,
                                circle = true,
                            )
                        }
                    }

                    // Center Transport Row (LTR for standard media order)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalArrangement = Arrangement.spacedBy(18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (playlist.size > 1) {
                                OsdIconSquareButton(Ic.Previous, Ar.previous, onPrev, size = 40.dp, circle = true)
                            }
                            if (canSeek) {
                                OsdIconSquareButton(Ic.Previous, "-10", { onSeekBy(-SEEK_STEP_MS) }, size = 42.dp, circle = true, label = "-10")
                            }
                            OsdPrimaryPlayButton(
                                isPlaying = isPlaying,
                                onClick = onTogglePlay,
                                size = 56.dp,
                                cornerRadius = 28.dp,
                            )
                            if (canSeek) {
                                OsdIconSquareButton(Ic.Next, "+10", { onSeekBy(SEEK_STEP_MS) }, size = 42.dp, circle = true, label = "+10")
                            }
                            if (playlist.size > 1) {
                                OsdIconSquareButton(Ic.Next, Ar.next, onNext, size = 40.dp, circle = true)
                            }
                        }
                    }

                    // Bottom Scrubber inside video (isolated progress recomposition)
                    PhonePortraitTimelineSection(
                        progressFlow = progressFlow,
                        isLive = isLive,
                        canSeek = canSeek,
                        onSeekTo = onSeekTo,
                        onSeekBy = onSeekBy,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    )
                }
            }

            error?.let { message ->
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(message, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    GlassButton(Ar.retry, onRetry, primary = true)
                }
            }
        }

        // 2. Channel / Title Metadata & Quick Action Toolbar (Below Video)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(OsdSurfaceLow)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val primaryLogoUrl = remember(item.icon, item.title) {
                    ChannelLogoResolver.resolvePrimaryUrl(item.icon, item.title)
                }
                if (primaryLogoUrl.isNotBlank()) {
                    RemoteImage(
                        url = primaryLogoUrl,
                        fallbackText = item.title,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(OsdSurfaceHighest)
                            .padding(4.dp),
                        contentScale = if (isLive) ContentScale.Fit else ContentScale.Crop,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        item.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val sub = if (isLive) {
                        currentProgram?.title?.takeIf { it.isNotBlank() }
                            ?: nextProgram?.let { "${Ar.nextShowing}: ${it.title}" }
                            ?: "بث مباشر فائق الوضوح"
                    } else {
                        item.subtitle.ifBlank { "مشاهدة مباشرة عبر مشغل سحاب Media3" }
                    }
                    Text(
                        sub,
                        fontSize = 13.sp,
                        color = OsdPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Scrollable Action Pills Row (Never crowded!)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                item {
                    OsdPillButton(
                        label = Ar.fullscreen,
                        leadingIcon = Ic.Aspect,
                        iconTint = OsdOnPrimary,
                        highlighted = true,
                        onClick = onEnterLandscape,
                    )
                }
                item {
                    OsdPillButton(
                        label = audioLabel,
                        leadingIcon = Ic.Equalizer,
                        iconTint = OsdCyan,
                        onClick = onOpenTracks,
                    )
                }
                item {
                    OsdPillButton(
                        label = qualityLabel,
                        leadingIcon = Ic.Quality,
                        iconTint = OsdPrimary,
                        onClick = onOpenTracks,
                    )
                }
                if (isLive) {
                    item {
                        OsdPillButton(
                            label = Ar.guide,
                            leadingIcon = Ic.Clock,
                            iconTint = OsdTertiary,
                            onClick = onOpenGuide,
                        )
                    }
                } else if (canSeek) {
                    item {
                        OsdPillButton(
                            label = String.format(Locale.US, "%.2fx", playbackSpeed).replace(".00x", ".0x"),
                            leadingIcon = Ic.Speed,
                            iconTint = OsdCyan,
                            onClick = onCycleSpeed,
                        )
                    }
                }
                item {
                    OsdPillButton(
                        label = "أبعاد الشاشة",
                        leadingIcon = Ic.Aspect,
                        iconTint = OsdOnSurfaceVariant,
                        onClick = onCycleResize,
                    )
                }
            }
        }

        // 3. Vertical Channels or Episodes List (Takes remaining screen height without overlapping video)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isLive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "دليل القنوات السريع",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdPrimary,
                    )
                    Text(
                        "${currentIndex + 1} / ${playlist.size}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdCyan,
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    itemsIndexed(playlist, key = { idx, c -> "${c.id}_$idx" }) { idx, ch ->
                        val active = idx == currentIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (active) OsdPrimaryContainer.copy(alpha = 0.38f)
                                    else OsdSurfaceContainer.copy(alpha = 0.85f),
                                )
                                .border(
                                    width = if (active) 1.5.dp else 1.dp,
                                    color = if (active) OsdPrimary else OsdOutlineVariant.copy(alpha = 0.30f),
                                    shape = RoundedCornerShape(14.dp),
                                )
                                .clickable { onSelectPlaylistIndex(idx) }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                String.format(Locale.US, "%02d", idx + 1),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) OsdCyan else OsdOnSurfaceVariant,
                            )
                            RemoteImage(
                                url = ch.icon,
                                fallbackText = ch.title,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(OsdSurfaceHighest)
                                    .padding(3.dp),
                                contentScale = ContentScale.Fit,
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    ch.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else OsdOnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    if (active && currentProgram != null) currentProgram.title else "بث مباشر",
                                    fontSize = 12.sp,
                                    color = if (active) OsdCyan else OsdOnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (active) {
                                Icon(Ic.Equalizer, contentDescription = null, tint = OsdCyan, modifier = Modifier.size(20.dp))
                            } else {
                                val q = qualityOf(ch.title) ?: "HD"
                                FormatChip(q, if (q == "4K") OsdPrimary else OsdOnSurfaceVariant)
                            }
                        }
                    }
                }
            } else {
                // Series Episodes or Movie Playlist in Portrait
                if (seasons.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(seasons, key = { it }) { sNum ->
                            val selected = sNum == selectedSeason
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) OsdPrimary else OsdSurfaceHigh)
                                    .clickable { selectedSeason = sNum }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    Ar.season(sNum),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) OsdOnPrimary else OsdOnSurface,
                                )
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (seasonEpisodes.isNotEmpty()) {
                        itemsIndexed(seasonEpisodes, key = { _, ep -> ep.id }) { _, ep ->
                            val isCurrent = ep.id == item.id
                            val isWatched = currentEpObj != null &&
                                (ep.season < currentEpObj.season ||
                                    (ep.season == currentEpObj.season && ep.number < currentEpObj.number))
                            val isNext = currentEpObj != null &&
                                ep.season == currentEpObj.season &&
                                ep.number == currentEpObj.number + 1
                            val progState = if (isCurrent) progressFlow.collectAsStateWithLifecycle().value else null
                            val curPos = progState?.position ?: 0L
                            val curDur = progState?.duration ?: 0L
                            EpisodeDrawerCard(
                                number = ep.number,
                                title = ep.title,
                                plot = ep.plot.ifBlank { "حلقة كاملة من ${item.title}" },
                                imageUrl = ep.image.ifBlank { seriesDetails?.cover.orEmpty() }.ifBlank { item.icon },
                                durationText = ep.duration.ifBlank { "45:00" },
                                isCurrent = isCurrent,
                                isWatched = isWatched,
                                isNext = isNext,
                                progressFraction = if (isCurrent && curDur > 0L) (curPos.toFloat() / curDur).coerceIn(0.05f, 1f) else 0.45f,
                                remainingMinutes = if (isCurrent && curDur > curPos) ((curDur - curPos) / 60_000L).toInt().coerceAtLeast(1) else 25,
                                onClick = { onSelectEpisode(ep) },
                            )
                        }
                    } else {
                        itemsIndexed(playlist, key = { i, p -> "${p.id}_$i" }) { idx, p ->
                            val isCurrent = idx == currentIndex
                            val progState = if (isCurrent) progressFlow.collectAsStateWithLifecycle().value else null
                            val curPos = progState?.position ?: 0L
                            val curDur = progState?.duration ?: 0L
                            EpisodeDrawerCard(
                                number = idx + 1,
                                title = p.subtitle.ifBlank { p.title },
                                plot = p.title,
                                imageUrl = p.icon,
                                durationText = if (isCurrent && curDur > 0L) formatDuration(curDur) else "FHD",
                                isCurrent = isCurrent,
                                isWatched = idx < currentIndex,
                                isNext = idx == currentIndex + 1,
                                progressFraction = if (isCurrent && curDur > 0L) (curPos.toFloat() / curDur).coerceIn(0.05f, 1f) else 0.5f,
                                remainingMinutes = 25,
                                onClick = { onSelectPlaylistIndex(idx) },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// MODE B & C: UNIFIED FULLSCREEN OSD (Live TV, Movies, Series — Phone & TV)
// ============================================================================

@Composable
internal fun PhonePortraitTimelineSection(
    progressFlow: StateFlow<PlaybackProgress>,
    isLive: Boolean,
    canSeek: Boolean = !isLive,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prog by progressFlow.collectAsStateWithLifecycle()
    val position = prog.position
    val bufferedPosition = prog.bufferedPosition
    val duration = prog.duration
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val progressFraction = if (duration > 0L) (position.toFloat() / duration).coerceIn(0f, 1f) else 0.85f
        val bufferedFraction = if (duration > 0L) (bufferedPosition.toFloat() / duration).coerceIn(progressFraction, 1f) else 0.95f
        GradientTimelineBar(
            progress = progressFraction,
            buffered = bufferedFraction,
            showChapters = false,
            onSeekToFraction = if (canSeek && duration > 0L) {
                { frac -> onSeekTo((duration * frac).toLong()) }
            } else null,
            onSeekBy = onSeekBy,
        )
        if (!isLive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatDuration(position), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(formatDuration(duration), fontSize = 11.sp, color = OsdOnSurfaceVariant)
            }
        }
    }
}

