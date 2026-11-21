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
import androidx.compose.foundation.layout.heightIn
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
import androidx.media3.common.TrackSelectionParameters
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

internal enum class TrackSection {
    ALL,
    QUALITY,
    AUDIO,
    SUBTITLES,
}

// Design tokens from ExoPlayer/DESIGN.md & media-player/DESIGN.md
@OptIn(UnstableApi::class)
@Composable
internal fun TracksDialog(
    tracks: Tracks,
    exo: ExoPlayer,
    trackSelectionParams: TrackSelectionParameters = exo.trackSelectionParameters,
    initialSection: TrackSection = TrackSection.ALL,
    onDismiss: () -> Unit,
) {
    val video = remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO } }
    val audio = remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO } }
    val text = remember(tracks) { tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT } }
    val videoEntries = remember(video) {
        video.flatMapIndexed { gIdx, group ->
            (0 until group.length)
                .filter { group.isTrackSupported(it) }
                .map { i -> Triple(gIdx, group, i) }
        }
    }
    val audioEntries = remember(audio) {
        audio.flatMapIndexed { gIdx, group ->
            (0 until group.length)
                .filter { group.isTrackSupported(it) }
                .map { i -> Triple(gIdx, group, i) }
        }
    }
    val textEntries = remember(text) {
        text.flatMapIndexed { gIdx, group ->
            (0 until group.length)
                .filter { group.isTrackSupported(it) }
                .map { i -> Triple(gIdx, group, i) }
        }
    }
    val hasAnySection = video.isNotEmpty() || audioEntries.isNotEmpty() || text.isNotEmpty()
    val textOff = trackSelectionParams.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    val hasVideoOverride = video.any { g -> trackSelectionParams.overrides.containsKey(g.mediaTrackGroup) }

    val focusTargetKey = remember(initialSection, video.size, audioEntries.size, text.size) {
        when (initialSection) {
            TrackSection.QUALITY -> if (video.isNotEmpty()) "v_auto" else if (audioEntries.isNotEmpty()) "a_0" else if (text.isNotEmpty()) "t_off" else "close"
            TrackSection.AUDIO -> if (audioEntries.isNotEmpty()) "a_0" else if (video.isNotEmpty()) "v_auto" else if (text.isNotEmpty()) "t_off" else "close"
            TrackSection.SUBTITLES -> if (text.isNotEmpty()) "t_off" else if (video.isNotEmpty()) "v_auto" else if (audioEntries.isNotEmpty()) "a_0" else "close"
            TrackSection.ALL -> if (video.isNotEmpty()) "v_auto" else if (audioEntries.isNotEmpty()) "a_0" else if (text.isNotEmpty()) "t_off" else "close"
        }
    }
    val scrollIndex = remember(initialSection, videoEntries.size, audioEntries.size) {
        when (initialSection) {
            TrackSection.AUDIO -> if (video.isNotEmpty()) 2 + videoEntries.size else 0
            TrackSection.SUBTITLES -> (if (video.isNotEmpty()) 2 + videoEntries.size else 0) +
                (if (audioEntries.isNotEmpty()) 1 + audioEntries.size else 0)
            else -> 0
        }
    }
    val listState = rememberLazyListState()
    val initialTrackFocus = remember { FocusRequester() }
    LaunchedEffect(initialSection, focusTargetKey) {
        if (scrollIndex > 0) {
            runCatching { listState.scrollToItem(scrollIndex) }
        }
        withFrameNanos { }
        withFrameNanos { }
        runCatching { initialTrackFocus.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(OsdSurfaceLow)
                    .border(1.5.dp, OsdPrimary.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
                    .padding(20.dp),
            ) {
                Text(Ar.tracks, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OsdOnSurface)
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp),
                ) {
                    if (video.isNotEmpty()) {
                        item(key = "hdr_video") {
                            Text(Ar.videoQuality, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OsdPrimary)
                        }
                        item(key = "v_auto") {
                            TrackRow(
                                label = Ar.qualityAuto,
                                selected = !hasVideoOverride,
                                modifier = if (focusTargetKey == "v_auto") Modifier.focusRequester(initialTrackFocus) else Modifier,
                                onClick = {
                                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        .build()
                                    onDismiss()
                                },
                            )
                        }
                        itemsIndexed(
                            videoEntries,
                            key = { idx, (gIdx, _, i) -> "v_${gIdx}_${i}_$idx" },
                        ) { _, (_, group, i) ->
                            TrackRow(
                                label = SahabPlayer.videoQualityLabel(group.getTrackFormat(i)),
                                selected = hasVideoOverride && group.isTrackSelected(i),
                                onClick = {
                                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                        .build()
                                    onDismiss()
                                },
                            )
                        }
                    }
                    if (audioEntries.isNotEmpty()) {
                        item(key = "hdr_audio") {
                            Text(Ar.audio, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OsdCyan)
                        }
                        itemsIndexed(
                            audioEntries,
                            key = { idx, (gIdx, _, i) -> "a_${gIdx}_${i}_$idx" },
                        ) { idx, (_, group, i) ->
                            TrackRow(
                                label = trackLabel(group.getTrackFormat(i), i),
                                selected = group.isTrackSelected(i),
                                modifier = if (idx == 0 && focusTargetKey == "a_0") Modifier.focusRequester(initialTrackFocus) else Modifier,
                                onClick = {
                                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                        .build()
                                    onDismiss()
                                },
                            )
                        }
                    }
                    if (text.isNotEmpty()) {
                        item(key = "hdr_text") {
                            Text(Ar.subtitles, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = OsdTertiary)
                        }
                        item(key = "t_off") {
                            TrackRow(
                                label = Ar.subtitlesOff,
                                selected = textOff || text.none { g -> (0 until g.length).any { g.isTrackSelected(it) } },
                                modifier = if (focusTargetKey == "t_off") Modifier.focusRequester(initialTrackFocus) else Modifier,
                                onClick = {
                                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .build()
                                    onDismiss()
                                },
                            )
                        }
                        itemsIndexed(
                            textEntries,
                            key = { idx, (gIdx, _, i) -> "t_${gIdx}_${i}_$idx" },
                        ) { _, (_, group, i) ->
                            TrackRow(
                                label = trackLabel(group.getTrackFormat(i), i),
                                selected = !textOff && group.isTrackSelected(i),
                                onClick = {
                                    exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                        .build()
                                    onDismiss()
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                GlassButton(
                    Ar.close,
                    onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    autoFocus = !hasAnySection,
                )
            }
        }
    }
}

@Composable
internal fun TrackRow(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        selected = selected,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = OsdOnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

internal fun trackLabel(format: Format, position: Int): String {
    val languageCode = format.language
    val language = if (languageCode.isNullOrBlank() || languageCode == "und") {
        null
    } else {
        val name = Locale.forLanguageTag(languageCode).getDisplayLanguage(Locale.forLanguageTag("ar"))
        name.ifBlank { languageCode }
    }
    return listOfNotNull(format.label, language).distinct().joinToString(" - ")
        .ifBlank { Ar.trackNumber(position + 1) }
}
