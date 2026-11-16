package com.saydlawy.sahab.ui.live

import com.saydlawy.sahab.data.channel.EpgProgramEntity
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.FavItem
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.data.Program
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalAppScale
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.Chip
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.EpgDialog
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.LoadingBox
import com.saydlawy.sahab.ui.components.MessageBox
import com.saydlawy.sahab.ui.components.ProgressLine
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.SideNavItem
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.components.rememberNow
import com.saydlawy.sahab.ui.player.SahabPlayer
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.ChannelLogoResolver
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatClock
import com.saydlawy.sahab.util.formatNowMillis
import com.saydlawy.sahab.util.normalizeForSearch
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import androidx.compose.runtime.Immutable

@Composable
internal fun ChannelColumn(
    state: LiveUiState,
    channels: List<Channel>,
    favoriteIds: Set<String>,
    previewId: String?,
    onPreview: (Channel) -> Unit,
    onPlayAt: (Int) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onGuide: (Channel) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberRestorableLazyListState(
        "live_channels_${state.selectedId ?: ALL_CAT_ID}_${state.query.trim()}"
    )

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = appDp(6), vertical = appDp(4)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(appDp(8)),
            ) {
                Box(
                    Modifier
                        .size(appDp(8))
                        .clip(CircleShape)
                        .background(Palette.LiveRed)
                )
                Text(
                    Ar.channelList,
                    fontSize = appSp(15),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                )
            }
            if (channels.isNotEmpty()) {
                Tag(Ar.itemsCount(channels.size), Palette.FocusCyan, fontSize = appSp(10))
            }
        }

        Spacer(Modifier.height(appDp(4)))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.busy && channels.isEmpty() -> LoadingBox()
                state.error != null && channels.isEmpty() -> MessageBox(
                    state.error,
                    actionLabel = Ar.retry,
                    onAction = onRetry,
                )
                channels.isEmpty() -> MessageBox(
                    if (state.selectedId == FAV_CAT_ID) Ar.noFavorites else Ar.noChannels,
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().focusRestorer(),
                    contentPadding = PaddingValues(vertical = appDp(4)),
                    verticalArrangement = Arrangement.spacedBy(appDp(8)),
                ) {
                    itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
                        ChannelRow(
                            index = index + 1,
                            channel = channel,
                            isPreviewed = channel.id == previewId,
                            isFavorite = channel.id in favoriteIds,
                            isDefaultEntry = index == 0,
                            onActivate = { onPlayAt(index) },
                            onToggleFavorite = { onToggleFavorite(channel) },
                        )
                    }
                }
            }
        }
    }
}

@kotlin.OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelRow(
    index: Int,
    channel: Channel,
    isPreviewed: Boolean,
    isFavorite: Boolean,
    isDefaultEntry: Boolean = false,
    onActivate: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val container = LocalContainer.current
    val dao = remember(container) { container.channelDb.channelDao() }
    val programs by produceState<Pair<EpgProgramEntity?, EpgProgramEntity?>>(
        initialValue = null to null,
        channel.id,
    ) {
        while (true) {
            val t = System.currentTimeMillis() / 1000
            val cur = runCatching { dao.currentProgramFor(channel.id, t) }.getOrNull()
            val nxt = runCatching { dao.nextProgramFor(channel.id, t) }.getOrNull()
            value = cur to nxt
            kotlinx.coroutines.delay(60_000L)
        }
    }
    val program = programs.first
    val next = programs.second
    var focused by remember { mutableStateOf(false) }
    val active = focused || isPreviewed
    val shape = RoundedCornerShape(appDp(16))
    val borderColor = when {
        focused -> Palette.FocusCyan
        isPreviewed -> Palette.FocusCyan.copy(alpha = 0.65f)
        else -> Palette.CardBorder
    }
    val bgColor = when {
        focused -> Palette.CardSurfaceElevated
        isPreviewed -> Palette.Royal.copy(alpha = 0.28f)
        else -> Palette.CardSurface.copy(alpha = 0.92f)
    }
    val borderWidth = if (focused) appDp(2) else appDp(1)

    val primaryLogoUrl = remember(channel.icon, channel.name) {
        ChannelLogoResolver.resolvePrimaryUrl(channel.icon, channel.name)
    }
    val internetFallbackLogoUrl = remember(channel.name) {
        ChannelLogoResolver.resolveInternetLogoUrl(channel.name)
    }
    val (badgeText, _) = remember(channel.name) {
        ChannelLogoResolver.resolveBrandStyle(channel.name)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .restorableFocus(
                screenKey = Routes.LIVE,
                itemKey = "live_ch_${channel.id}",
                isDefaultEntry = isDefaultEntry,
            )
            .then(
                if (active) {
                    Modifier.shadow(
                        elevation = appDp(12),
                        shape = shape,
                        ambientColor = Palette.FocusCyan,
                        spotColor = Palette.FocusCyan,
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(bgColor)
            .border(borderWidth, borderColor, shape)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                onClick = onActivate,
                onLongClick = onToggleFavorite,
            )
            .padding(horizontal = appDp(10), vertical = appDp(8)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appDp(10)),
    ) {
        // Reference-style Circular White Logo Badge with Internet Logo Resolution
        Box(
            modifier = Modifier
                .size(appDp(44))
                .clip(CircleShape)
                .background(Color.White)
                .border(
                    width = if (focused) appDp(2) else appDp(1),
                    color = if (focused) Palette.FocusCyan else Color.White.copy(alpha = 0.85f),
                    shape = CircleShape,
                )
                .padding(appDp(4)),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                url = primaryLogoUrl,
                fallbackUrl = internetFallbackLogoUrl,
                fallbackText = badgeText,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Fit,
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(appDp(3))) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(appDp(6)),
            ) {
                Text(
                    text = index.toString().padStart(2, '0'),
                    fontSize = appSp(10),
                    fontWeight = FontWeight.ExtraBold,
                    color = if (active) Palette.FocusCyan else Palette.TextDim,
                )
                Text(
                    text = channel.name,
                    fontSize = appSp(14),
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(appDp(6)),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(appDp(5)))
                        .background(Palette.LiveRed)
                        .padding(horizontal = appDp(5), vertical = appDp(1)),
                ) {
                    Text(
                        text = "مباشر",
                        fontSize = appSp(8),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                    )
                }
                Text(
                    text = if (program != null && program.title.isNotBlank()) program.title else "بث مباشر",
                    fontSize = appSp(11),
                    color = if (program != null) Palette.FocusCyan else Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (next != null && next.title.isNotBlank()) {
                Text(
                    text = "${Ar.nextProgramLabel} ${next.title}",
                    fontSize = appSp(10),
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (isFavorite) {
            Icon(
                imageVector = Ic.Heart,
                contentDescription = null,
                tint = Palette.LiveRed,
                modifier = Modifier.size(appDp(15)),
            )
        }

        if (isPreviewed) {
            LiveBadge()
        }

        qualityOf(channel.name)?.let { q ->
            Tag(q, if (q == "4K") Palette.Gold else Palette.Glow, fontSize = appSp(10))
        }
    }
}

@Composable
internal fun LiveBadge() {
    val infiniteTransition = rememberInfiniteTransition(label = "livePulse")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Live.copy(alpha = 0.22f))
            .border(appDp(1), Palette.Live.copy(alpha = 0.45f), RoundedCornerShape(50))
            .padding(horizontal = appDp(8), vertical = appDp(3)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(appDp(4)),
    ) {
        Box(
            Modifier
                .size(appDp(6))
                .clip(CircleShape)
                .background(Palette.Live.copy(alpha = dotAlpha)),
        )
        Text(Ar.liveBadge, fontSize = appSp(10), fontWeight = FontWeight.Bold, color = Palette.Live)
    }
}

