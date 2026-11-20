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
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
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
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.findActivity
import com.saydlawy.sahab.util.formatDuration
import com.saydlawy.sahab.util.formatNowMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import com.saydlawy.sahab.util.ChannelLogoResolver

// Design tokens from ExoPlayer/DESIGN.md & media-player/DESIGN.md
@Composable
internal fun UnifiedFullscreenPlayerOsd(
    item: PlayItem,
    isLive: Boolean,
    canSeek: Boolean = !isLive,
    playlistCount: Int,
    currentProgram: Program?,
    isPlaying: Boolean,
    progressFlow: StateFlow<PlaybackProgress>,
    playbackSpeed: Float,
    showDrawer: Boolean,
    activeBitrateText: String? = null,
    focusTick: Int = 0,
    playFocus: FocusRequester,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleResize: () -> Unit,
    onOpenTracks: () -> Unit,
    onOpenAudioTracks: () -> Unit = onOpenTracks,
    onOpenSubtitleTracks: () -> Unit = onOpenTracks,
    onToggleDrawer: () -> Unit,
) {
    val nowTime = formatNowMillis(rememberNow(), "hh:mm a", Locale.US)
    val subtitleText = if (isLive) {
        currentProgram?.title?.takeIf { it.isNotBlank() } ?: item.subtitle
    } else {
        item.subtitle
    }
    val hasDrawer = if (isLive) playlistCount > 1 else (item.kind == Kind.EPISODE || playlistCount > 1)
    val speedDesc = String.format(Locale.US, "%.2fx", playbackSpeed).replace(".00x", "x").replace("50x", "5x")

    LaunchedEffect(focusTick, showDrawer) {
        if (!showDrawer) {
            withFrameNanos { }
            withFrameNanos { }
            runCatching { playFocus.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        OsdSurfaceLowest.copy(alpha = 0.65f),
                        Color.Transparent,
                        OsdSurfaceLowest.copy(alpha = 0.78f),
                    ),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        // 1. Unified Compact Top Bar: Back -> Title & Subtitle -> Clock
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(OsdSurfaceLow.copy(alpha = 0.78f))
                .border(1.dp, OsdOutlineVariant.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                OsdIconSquareButton(
                    icon = Ic.Back,
                    description = Ar.back,
                    onClick = onBack,
                    size = 36.dp,
                    circle = true,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitleText.isNotBlank()) {
                        Text(
                            subtitleText,
                            fontSize = 11.5.sp,
                            color = OsdOnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!activeBitrateText.isNullOrBlank()) {
                    FormatChip(label = activeBitrateText, textColor = OsdCyan)
                }
                Text(
                    nowTime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OsdOnSurface,
                    maxLines = 1,
                )
            }
        }

        // 2. Unified Compact Bottom Bar: Timeline + 1 Row of Controls
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(OsdSurfaceLow.copy(alpha = 0.85f))
                .border(1.dp, OsdOutlineVariant.copy(alpha = 0.32f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (LocalThemeCustomization.current.isVisible("player_timeline")) {
                FullscreenTimelineSection(
                    progressFlow = progressFlow,
                    isLive = isLive,
                    canSeek = canSeek,
                    currentProgram = currentProgram,
                    onSeekTo = onSeekTo,
                    onSeekBy = onSeekBy,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Start (Physical Right in RTL): Channels / Episodes Drawer Button
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (hasDrawer) {
                        OsdPillButton(
                            label = if (isLive) "قائمة القنوات" else if (item.kind == Kind.EPISODE) "قائمة الحلقات" else "قائمة التشغيل",
                            leadingIcon = Ic.List,
                            iconTint = if (showDrawer) OsdOnPrimary else OsdPrimary,
                            highlighted = showDrawer,
                            onClick = onToggleDrawer,
                        )
                    }
                }

                // Center: Previous -> -10 -> Play/Pause -> +10 -> Next (LTR transport order)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OsdIconSquareButton(
                            icon = Ic.Previous,
                            description = Ar.previous,
                            onClick = onPrev,
                            size = 38.dp,
                            circle = true,
                        )
                        if (canSeek) {
                            OsdIconSquareButton(
                                icon = Ic.Previous,
                                description = "-10",
                                label = "-10",
                                onClick = { onSeekBy(-SEEK_STEP_MS) },
                                size = 38.dp,
                                circle = true,
                            )
                        }
                        OsdPrimaryPlayButton(
                            isPlaying = isPlaying,
                            onClick = onTogglePlay,
                            size = 48.dp,
                            cornerRadius = 24.dp,
                            showOuterRing = true,
                            focusRequester = playFocus,
                        )
                        if (canSeek) {
                            OsdIconSquareButton(
                                icon = Ic.Next,
                                description = "+10",
                                label = "+10",
                                onClick = { onSeekBy(SEEK_STEP_MS) },
                                size = 38.dp,
                                circle = true,
                            )
                        }
                        OsdIconSquareButton(
                            icon = Ic.Next,
                            description = Ar.next,
                            onClick = onNext,
                            size = 38.dp,
                            circle = true,
                        )
                    }
                }

                // End (Physical Left in RTL): Pure icon-only Audio, Subtitles, Speed, Aspect Ratio
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OsdIconSquareButton(
                            icon = Ic.Equalizer,
                            description = "${Ar.audio} — المسارات الصوتية",
                            onClick = onOpenAudioTracks,
                            size = 38.dp,
                        )
                        OsdIconSquareButton(
                            icon = Ic.Subtitles,
                            description = "${Ar.subtitles} — الترجمة",
                            onClick = onOpenSubtitleTracks,
                            size = 38.dp,
                        )
                        if (canSeek) {
                            OsdIconSquareButton(
                                icon = Ic.Speed,
                                description = "السرعة ($speedDesc)",
                                onClick = onCycleSpeed,
                                size = 38.dp,
                            )
                        }
                        OsdIconSquareButton(
                            icon = Ic.Aspect,
                            description = "أبعاد العرض",
                            onClick = onCycleResize,
                            size = 38.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun FullscreenTimelineSection(
    progressFlow: StateFlow<PlaybackProgress>,
    isLive: Boolean,
    canSeek: Boolean = !isLive,
    currentProgram: Program?,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
) {
    val prog by progressFlow.collectAsStateWithLifecycle()
    val position = prog.position
    val bufferedPosition = prog.bufferedPosition
    val duration = prog.duration
    val nowSec = if (isLive && currentProgram != null) rememberNow(30_000L) / 1000L else 0L

    val progressFraction = if (isLive) {
        if (currentProgram != null && currentProgram.end > currentProgram.start) {
            ((nowSec - currentProgram.start).toFloat() / (currentProgram.end - currentProgram.start)).coerceIn(0.08f, 0.96f)
        } else {
            0.85f
        }
    } else if (duration > 0L) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val bufferedFraction = if (duration > 0L) {
        (bufferedPosition.toFloat() / duration).coerceIn(progressFraction, 1f)
    } else {
        (progressFraction + 0.08f).coerceAtMost(1f)
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        GradientTimelineBar(
            progress = progressFraction,
            buffered = bufferedFraction,
            showChapters = false,
            onSeekToFraction = if (canSeek && duration > 0L) {
                { frac -> onSeekTo((duration * frac).toLong()) }
            } else null,
            onSeekBy = onSeekBy,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    formatDuration(position.coerceAtLeast(0L)),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                if (!isLive && duration > 0L) {
                    Text("/", fontSize = 11.5.sp, color = OsdOutline)
                    Text(
                        formatDuration(duration),
                        fontSize = 11.5.sp,
                        color = OsdOnSurfaceVariant,
                    )
                }
            }

            if (isLive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(OsdCyan))
                    Text(
                        "مباشر",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdCyan,
                    )
                }
            } else {
                val rem = (duration - position).coerceAtLeast(0L)
                Text(
                    "-${formatDuration(rem)}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = OsdTertiary,
                )
            }
        }
    }
}

// ============================================================================
// 4. SHARED OSD MICRO-COMPONENTS & GRADIENT TIMELINE
// ============================================================================

@Composable
internal fun GradientTimelineBar(
    progress: Float,
    buffered: Float,
    showChapters: Boolean,
    onSeekToFraction: ((Float) -> Unit)?,
    onSeekBy: (Long) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFraction ?: progress).coerceIn(0f, 1f)
    val bufShown = buffered.coerceIn(shown, 1f)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(22.dp)
                .semantics {
                    contentDescription = "شريط تقدم التشغيل"
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                }
                .then(
                    if (onSeekToFraction != null) {
                        Modifier
                            .onKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                                    onSeekBy(SEEK_STEP_MS)
                                    true
                                } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                                    onSeekBy(-SEEK_STEP_MS)
                                    true
                                } else {
                                    false
                                }
                            }
                            .focusable(interactionSource = interaction)
                            .pointerInput(onSeekToFraction) {
                                detectTapGestures { offset ->
                                    if (size.width > 0) {
                                        onSeekToFraction((offset.x / size.width).coerceIn(0f, 1f))
                                    }
                                }
                            }
                            .pointerInput(onSeekToFraction) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        dragFraction?.let { onSeekToFraction(it) }
                                        dragFraction = null
                                    },
                                    onDragCancel = { dragFraction = null },
                                ) { change, _ ->
                                    change.consume()
                                    if (size.width > 0) {
                                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                    }
                                }
                            }
                    } else Modifier,
                ),
        ) {
            val trackHeight = 7.dp.toPx()
            val top = (size.height - trackHeight) / 2f
            val radius = CornerRadius(trackHeight / 2f, trackHeight / 2f)

            drawRoundRect(
                color = OsdSurfaceHighest.copy(alpha = 0.85f),
                topLeft = Offset(0f, top),
                size = Size(size.width, trackHeight),
                cornerRadius = radius,
            )

            drawRoundRect(
                color = OsdOutlineVariant.copy(alpha = 0.65f),
                topLeft = Offset(0f, top),
                size = Size(size.width * bufShown, trackHeight),
                cornerRadius = radius,
            )

            val activeWidth = (size.width * shown).coerceAtLeast(trackHeight)
            drawRoundRect(
                brush = TimelineProgressBrush,
                topLeft = Offset(0f, top),
                size = Size(activeWidth, trackHeight),
                cornerRadius = radius,
            )

            if (showChapters) {
                val chapters = floatArrayOf(0.15f, 0.42f, 0.85f)
                for (ch in chapters) {
                    val x = size.width * ch
                    drawLine(
                        color = OsdSurfaceLowest,
                        start = Offset(x, top),
                        end = Offset(x, top + trackHeight),
                        strokeWidth = 2.dp.toPx(),
                    )
                }
            }

            val thumbX = (size.width * shown).coerceIn(8.dp.toPx(), size.width - 8.dp.toPx())
            val center = Offset(thumbX, size.height / 2f)
            val outerRadius = if (focused || dragFraction != null) 10.dp.toPx() else 8.dp.toPx()
            drawCircle(color = OsdPrimaryContainer, radius = outerRadius + 2.dp.toPx(), center = center)
            drawCircle(color = Color.White, radius = outerRadius, center = center)
            drawCircle(color = OsdPrimaryContainer, radius = 3.5.dp.toPx(), center = center)
        }
    }
}

@Composable
internal fun OsdPrimaryPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    size: Dp,
    cornerRadius: Dp,
    showOuterRing: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = (if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(size)
            .shadow(
                elevation = if (focused) 24.dp else 14.dp,
                shape = shape,
                ambientColor = if (focused) OsdCyan else OsdPrimaryContainer,
                spotColor = if (focused) OsdCyan else OsdPrimary,
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (focused) listOf(OsdCyan, OsdPrimary)
                    else listOf(OsdPrimaryContainer, OsdPrimary),
                ),
            )
            .border(
                width = if (focused) 2.5.dp else if (showOuterRing) 2.dp else 1.dp,
                color = if (focused) OsdCyan else OsdPrimary.copy(alpha = 0.65f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying) Ic.Pause else Ic.Play,
            contentDescription = if (isPlaying) Ar.pause else Ar.play,
            tint = if (focused) OsdOnPrimary else Color.White,
            modifier = Modifier.size(size * 0.48f),
        )
    }
}

@Composable
internal fun OsdIconSquareButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 40.dp,
    circle: Boolean = false,
    label: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = if (circle) CircleShape else RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(if (focused) OsdPrimary else OsdSurfaceHigh.copy(alpha = 0.85f))
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else OsdOutlineVariant.copy(alpha = 0.35f),
                shape = shape,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            Text(
                label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (focused) OsdOnPrimary else OsdOnSurface,
            )
        } else {
            Icon(
                icon,
                contentDescription = description,
                tint = if (focused) OsdOnPrimary else OsdOnSurface,
                modifier = Modifier.size(size * 0.48f),
            )
        }
    }
}

@Composable
internal fun OsdPillButton(
    label: String,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    iconTint: Color = OsdOnSurface,
    highlighted: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused || highlighted -> OsdPrimary
        else -> OsdSurfaceHigh.copy(alpha = 0.88f)
    }
    val contentColor = if (focused || highlighted) OsdOnPrimary else OsdOnSurface
    val activeIconTint = if (focused || highlighted) OsdOnPrimary else iconTint

    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) Color.White else OsdOutlineVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = activeIconTint, modifier = Modifier.size(16.dp))
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
        )
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = activeIconTint, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
internal fun FormatChip(label: String, textColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(OsdSurfaceHighest)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
        )
    }
}

// ============================================================================
// 5. TRACKS DIALOG (Video Quality + Audio Tracks + Subtitles)
// ============================================================================

