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
import androidx.compose.ui.platform.LocalDensity
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
@Composable
internal fun LiveChannelsDrawer(
    playlist: List<PlayItem>,
    currentIndex: Int,
    currentProgram: Program?,
    compact: Boolean = false,
    drawerFocus: FocusRequester,
    onSelectChannel: (Int) -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(if (compact) 320.dp else 360.dp)
            .fillMaxHeight()
            .background(OsdSurfaceLowest.copy(alpha = 0.90f))
            .border(1.dp, OsdOutlineVariant.copy(alpha = 0.40f), RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                Icon(Ic.List, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(20.dp))
                Text(
                    "قائمة القنوات (${currentIndex + 1}/${playlist.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            OsdIconSquareButton(Ic.Close, Ar.close, onClose, size = 34.dp, circle = true)
        }

        val channelListState = rememberLazyListState()
        LaunchedEffect(currentIndex) {
            if (currentIndex > 0) {
                channelListState.scrollToItem((currentIndex - 1).coerceAtLeast(0))
            }
        }

        LazyColumn(
            state = channelListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        onClose()
                        true
                    } else {
                        false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val anchorIdx = currentIndex.takeIf { it in playlist.indices } ?: 0
            itemsIndexed(
                playlist,
                key = { idx, c -> "${c.id}_$idx" },
                contentType = { _, _ -> "drawer_channel_row" },
            ) { idx, ch ->
                val active = idx == currentIndex
                val isFocusAnchor = idx == anchorIdx
                var focused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isFocusAnchor) Modifier.focusRequester(drawerFocus) else Modifier)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                active -> OsdPrimaryContainer.copy(alpha = 0.42f)
                                focused -> OsdSurfaceHighest.copy(alpha = 0.92f)
                                else -> OsdSurfaceHigh.copy(alpha = 0.70f)
                            },
                        )
                        .border(
                            width = if (active || focused) 1.5.dp else 1.dp,
                            color = if (active || focused) OsdCyan else Color.Transparent,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onSelectChannel(idx) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        String.format(Locale.US, "%02d", idx + 1),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (active) OsdCyan else OsdOnSurfaceVariant,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            ch.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OsdOnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (active && currentProgram != null) {
                            Text(
                                currentProgram.title,
                                fontSize = 11.sp,
                                color = OsdCyan,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (active) {
                        Icon(Ic.Equalizer, contentDescription = null, tint = OsdCyan, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ============================================================================
// 3. SIDE EPISODES DRAWER (Matches media-player/screen.png & code.html)
// ============================================================================

@Composable
internal fun VodEpisodesDrawer(
    currentItem: PlayItem,
    playlist: List<PlayItem>,
    currentIndex: Int,
    seriesDetails: SeriesDetails?,
    progressFlow: StateFlow<PlaybackProgress>,
    compact: Boolean = false,
    drawerFocus: FocusRequester,
    onSelectPlaylistIndex: (Int) -> Unit,
    onSelectEpisode: (Episode) -> Unit,
    onClose: () -> Unit,
) {
    val allEpisodes = seriesDetails?.episodes.orEmpty()
    val seasons = remember(seriesDetails) { seriesDetails?.seasons.orEmpty() }
    val currentEpisodeObj = remember(allEpisodes, currentItem.id) {
        allEpisodes.firstOrNull { it.id == currentItem.id }
    }
    var selectedSeason by remember(currentEpisodeObj, seasons) {
        mutableIntStateOf(currentEpisodeObj?.season ?: seasons.firstOrNull() ?: 1)
    }

    val seasonEpisodes = remember(allEpisodes, selectedSeason) {
        if (allEpisodes.isEmpty()) emptyList()
        else allEpisodes.filter { it.season == selectedSeason }
    }

    val totalCount = if (allEpisodes.isNotEmpty()) allEpisodes.size else playlist.size

    val episodeListState = rememberLazyListState()
    LaunchedEffect(selectedSeason, currentItem.id, currentIndex) {
        val targetIndex = if (seasonEpisodes.isNotEmpty()) {
            seasonEpisodes.indexOfFirst { it.id == currentItem.id }.takeIf { it >= 0 } ?: 0
        } else {
            currentIndex.coerceIn(0, (playlist.size - 1).coerceAtLeast(0))
        }
        if (targetIndex > 0) {
            episodeListState.scrollToItem((targetIndex - 1).coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .width(if (compact) 320.dp else 380.dp)
            .widthIn(max = 400.dp)
            .fillMaxHeight()
            .background(OsdSurfaceLowest.copy(alpha = 0.90f))
            .border(1.dp, OsdOutlineVariant.copy(alpha = 0.40f), RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp))
            .padding(if (compact) 14.dp else 18.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Ic.Series, contentDescription = null, tint = OsdPrimary, modifier = Modifier.size(24.dp))
                Column {
                    Text(
                        if (currentItem.kind == Kind.EPISODE) "حلقات المسلسل" else "قائمة التشغيل",
                        fontSize = if (compact) 16.sp else 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Text(
                        "${currentItem.title} • $totalCount حلقات",
                        fontSize = 11.sp,
                        color = OsdOutline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OsdIconSquareButton(
                icon = Ic.Close,
                description = Ar.close,
                onClick = onClose,
                size = 36.dp,
                circle = true,
            )
        }

        if (seasons.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
            ) {
                items(seasons, key = { it }) { seasonNum ->
                    val selected = seasonNum == selectedSeason
                    var seasonFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) OsdPrimary else OsdSurfaceHigh.copy(alpha = 0.80f))
                            .onFocusChanged { seasonFocused = it.isFocused }
                            .border(
                                width = if (seasonFocused) 1.5.dp else 1.dp,
                                color = if (seasonFocused) OsdCyan else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { selectedSeason = seasonNum }
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                    ) {
                        Text(
                            Ar.season(seasonNum),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) OsdOnPrimary else OsdOnSurface,
                        )
                    }
                }
            }
        }

        LazyColumn(
            state = episodeListState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && (event.key == Key.Back || event.key == Key.Escape)) {
                        onClose()
                        true
                    } else {
                        false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (seasonEpisodes.isNotEmpty()) {
                val anchorEpIdx = seasonEpisodes.indexOfFirst { it.id == currentItem.id }.let { if (it >= 0) it else 0 }
                itemsIndexed(seasonEpisodes, key = { _, ep -> ep.id }) { epIdx, ep ->
                    val isCurrent = ep.id == currentItem.id
                    val isFocusAnchor = epIdx == anchorEpIdx
                    val isWatched = currentEpisodeObj != null &&
                        (ep.season < currentEpisodeObj.season ||
                            (ep.season == currentEpisodeObj.season && ep.number < currentEpisodeObj.number))
                    val isNext = currentEpisodeObj != null &&
                        ep.season == currentEpisodeObj.season &&
                        ep.number == currentEpisodeObj.number + 1
                    val progState = if (isCurrent) progressFlow.collectAsStateWithLifecycle().value else null
                    val curPos = progState?.position ?: 0L
                    val curDur = progState?.duration ?: 0L

                    EpisodeDrawerCard(
                        number = ep.number,
                        title = ep.title,
                        plot = ep.plot.ifBlank { "حلقة كاملة بجودة عالية من ${currentItem.title}" },
                        imageUrl = ep.image.ifBlank { seriesDetails?.cover.orEmpty() }.ifBlank { currentItem.icon },
                        durationText = ep.duration,
                        isCurrent = isCurrent,
                        isWatched = isWatched,
                        isNext = isNext,
                        progressFraction = if (isCurrent && curDur > 0L) (curPos.toFloat() / curDur).coerceIn(0.05f, 1f) else if (isWatched) 1f else 0f,
                        remainingMinutes = if (isCurrent && curDur > curPos) ((curDur - curPos) / 60_000L).toInt().coerceAtLeast(1) else 0,
                        modifier = if (isFocusAnchor) Modifier.focusRequester(drawerFocus) else Modifier,
                        onClick = { onSelectEpisode(ep) },
                    )
                }
            } else {
                val anchorPlayIdx = currentIndex.takeIf { it in playlist.indices } ?: 0
                itemsIndexed(playlist, key = { i, p -> "${p.id}_$i" }) { idx, p ->
                    val isCurrent = idx == currentIndex
                    val isFocusAnchor = idx == anchorPlayIdx
                    val isWatched = idx < currentIndex
                    val isNext = idx == currentIndex + 1
                    val progState = if (isCurrent) progressFlow.collectAsStateWithLifecycle().value else null
                    val curPos = progState?.position ?: 0L
                    val curDur = progState?.duration ?: 0L

                    EpisodeDrawerCard(
                        number = idx + 1,
                        title = p.subtitle.ifBlank { p.title },
                        plot = p.title,
                        imageUrl = p.icon,
                        durationText = if (isCurrent && curDur > 0L) formatDuration(curDur) else "",
                        isCurrent = isCurrent,
                        isWatched = isWatched,
                        isNext = isNext,
                        progressFraction = if (isCurrent && curDur > 0L) (curPos.toFloat() / curDur).coerceIn(0.05f, 1f) else if (isWatched) 1f else 0f,
                        remainingMinutes = if (isCurrent && curDur > curPos) ((curDur - curPos) / 60_000L).toInt().coerceAtLeast(1) else 0,
                        modifier = if (isFocusAnchor) Modifier.focusRequester(drawerFocus) else Modifier,
                        onClick = { onSelectPlaylistIndex(idx) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun EpisodeDrawerCard(
    number: Int,
    title: String,
    plot: String,
    imageUrl: String,
    durationText: String,
    isCurrent: Boolean,
    isWatched: Boolean,
    isNext: Boolean,
    progressFraction: Float,
    remainingMinutes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isCurrent -> OsdPrimaryContainer.copy(alpha = 0.24f)
        focused -> OsdSurfaceHigh.copy(alpha = 0.90f)
        else -> OsdSurfaceContainer.copy(alpha = 0.65f)
    }
    val borderColor = when {
        isCurrent || focused -> OsdPrimary
        else -> Color.Transparent
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(bg)
                .border(if (isCurrent || focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                .onFocusChanged { focused = it.isFocused }
                .clickable(onClick = onClick)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(114.dp)
                    .height(70.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(OsdSurfaceLowest),
            ) {
                val density = LocalDensity.current
                val thumbWPx = remember(density) { with(density) { 114.dp.roundToPx() } }
                val thumbHPx = remember(density) { with(density) { 70.dp.roundToPx() } }
                RemoteImage(
                    url = imageUrl,
                    fallbackText = title,
                    targetWidthPx = thumbWPx,
                    targetHeightPx = thumbHPx,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            when {
                                isCurrent -> OsdPrimaryContainer.copy(alpha = 0.42f)
                                isWatched -> OsdSurfaceLowest.copy(alpha = 0.60f)
                                else -> OsdSurfaceLowest.copy(alpha = 0.30f)
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val overlayIcon = when {
                        isCurrent -> Ic.VolumeUp
                        isWatched -> Ic.CheckCircle
                        else -> Ic.Play
                    }
                    val overlayTint = if (isWatched) OsdCyan else Color.White
                    Icon(overlayIcon, contentDescription = null, tint = overlayTint, modifier = Modifier.size(24.dp))
                }

                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(OsdSurfaceHighest),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progressFraction.coerceIn(0.05f, 1f))
                                .background(OsdCyan),
                        )
                    }
                } else if (durationText.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(OsdSurfaceLowest.copy(alpha = 0.85f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(durationText, fontSize = 10.sp, color = OsdOnSurface)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                when {
                    isCurrent -> Text(
                        "الحلقة الحالية قيد المشاهدة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdPrimary,
                    )
                    isWatched -> Text(
                        "تمت المشاهدة بالكامل",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdCyan,
                    )
                    isNext -> Text(
                        "الحلقة التالية",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OsdPrimary,
                    )
                }

                Text(
                    "$number. $title",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) Color.White else OsdOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    plot,
                    fontSize = 12.sp,
                    color = if (isCurrent) OsdTertiary else OsdOutline,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                if (isCurrent && remainingMinutes > 0) {
                    Text("متبقي $remainingMinutes دقيقة", fontSize = 11.sp, color = OsdPrimary)
                }
            }
        }
    }
}

