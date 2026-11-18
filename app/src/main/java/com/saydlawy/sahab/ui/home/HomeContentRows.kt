package com.saydlawy.sahab.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.Kind
import com.saydlawy.sahab.data.PlayItem
import com.saydlawy.sahab.data.PlayRequest
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.ChannelLogoResolver

// ============================================================================
// SECTION B — تابع المشاهدة (Continue Watching)
// ============================================================================
@Composable
internal fun ContinueWatchingSection(
    items: List<ContinueCardModel>,
    hasRealRecents: Boolean,
    onClearRecents: () -> Unit,
) {
    val isTvOrTablet = LocalDisplayMode.current == DisplayMode.TABLET_OR_TV
    val horizPad = if (isTvOrTablet) 24.dp else 14.dp
    val cardWidth = if (isTvOrTablet) 232.dp else 198.dp
    val cardHeight = if (isTvOrTablet) 114.dp else 102.dp
    val rowState = rememberRestorableLazyListState("home_continue_watching_row")

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Ic.Clock,
                    contentDescription = null,
                    tint = Palette.TextMuted,
                    modifier = Modifier.size(if (isTvOrTablet) 18.dp else 16.dp),
                )
                Text(
                    text = "تابع المشاهدة",
                    color = Color.White,
                    fontSize = if (isTvOrTablet) 17.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
            if (hasRealRecents) {
                Text(
                    text = "مسح السجل",
                    color = Palette.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClearRecents)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = horizPad, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { index, item ->
                ContinueWatchingCard(
                    item = item,
                    useRedBar = index % 2 == 0,
                    modifier = Modifier
                        .size(width = cardWidth, height = cardHeight)
                        .restorableFocus(
                            screenKey = Routes.HOME,
                            itemKey = item.id,
                            isDefaultEntry = index == 0,
                        ),
                )
            }
        }
    }
}

@Composable
internal fun ContinueWatchingCard(
    item: ContinueCardModel,
    useRedBar: Boolean,
    modifier: Modifier = Modifier,
) {
    val pct = (item.progress * 100).toInt().coerceIn(5, 99)
    val barColor = if (useRedBar) Palette.LiveBadgeRed else Palette.FocusCyan

    GlassCard(
        onClick = item.onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        focusScale = 1.04f,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.CardSurface),
        ) {
            RemoteImage(
                url = item.imageUrl,
                fallbackText = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Palette.CinematicBg.copy(alpha = 0.10f),
                                0.45f to Palette.CinematicBg.copy(alpha = 0.40f),
                                0.80f to Palette.CinematicBg.copy(alpha = 0.90f),
                                1.0f to Palette.CinematicBg.copy(alpha = 0.97f),
                            ),
                        ),
                    ),
            )

            if (focused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.5.dp, Palette.FocusCyan, RoundedCornerShape(12.dp)),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Ic.Play,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "تشغيل",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    if (item.progress > 0f) {
                        Text(
                            text = "$pct%",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                if (item.progress > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.22f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(item.progress.coerceIn(0.05f, 1f))
                                .height(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(barColor),
                        )
                    }
                }

                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ============================================================================
// SECTION C — القنوات المباشرة (Live Channels)
// ============================================================================
@Composable
internal fun FeaturedChannelsSection(
    layout: HomeChannelsLayout,
    channels: List<Channel>,
    onPlay: (PlayRequest) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return
    val displayChannels = channels
    val isTvOrTablet = LocalDisplayMode.current == DisplayMode.TABLET_OR_TV
    val horizPad = if (isTvOrTablet) 24.dp else 14.dp
    val focusNav = LocalFocusNavigation.current
    val rowState = rememberRestorableLazyListState("home_live_channels_row")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Ic.Tv,
                    contentDescription = null,
                    tint = Palette.TextMuted,
                    modifier = Modifier.size(if (isTvOrTablet) 18.dp else 16.dp),
                )
                Text(
                    text = "القنوات المباشرة",
                    color = Color.White,
                    fontSize = if (isTvOrTablet) 17.sp else 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Text(
                text = "عرض الكل",
                color = Palette.FocusCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .restorableFocus(Routes.HOME, "home_live_see_all")
                    .clickable(onClick = onSeeAll)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        if (layout == HomeChannelsLayout.GRID) {
            val cols = if (isTvOrTablet) 6 else 3
            val rows = displayChannels.take(12).chunked(cols)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizPad),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rows.forEachIndexed { rIdx, rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowItems.forEachIndexed { cIdx, ch ->
                            val itemKey = "home_ch_${ch.id}"
                            LiveChannelReferenceCard(
                                channel = ch,
                                index = rIdx * cols + cIdx,
                                onClick = {
                                    focusNav.markNavigateFrom(Routes.HOME, itemKey)
                                    onPlay(
                                        PlayRequest(
                                            listOf(PlayItem(Kind.LIVE, ch.id, ch.name, icon = ch.icon, url = ch.url)),
                                            0,
                                        ),
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(if (isTvOrTablet) 90.dp else 80.dp)
                                    .restorableFocus(Routes.HOME, itemKey),
                            )
                        }
                        repeat(cols - rowItems.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            val cardW = if (isTvOrTablet) 132.dp else 112.dp
            val cardH = if (isTvOrTablet) 90.dp else 80.dp
            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = horizPad, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(displayChannels, key = { _, ch -> "home_ch_${ch.id}" }) { index, ch ->
                    val itemKey = "home_ch_${ch.id}"
                    LiveChannelReferenceCard(
                        channel = ch,
                        index = index,
                        onClick = {
                            focusNav.markNavigateFrom(Routes.HOME, itemKey)
                            onPlay(
                                PlayRequest(
                                    listOf(PlayItem(Kind.LIVE, ch.id, ch.name, icon = ch.icon, url = ch.url)),
                                    0,
                                ),
                            )
                        },
                        modifier = Modifier
                            .size(width = cardW, height = cardH)
                            .restorableFocus(Routes.HOME, itemKey),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LiveChannelReferenceCard(
    channel: Channel,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focusedRaw by interaction.collectIsFocusedAsState()
    val isTv = LocalIsTv.current
    val focused = focusedRaw && isTv
    val pressed by interaction.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.96f
            focused -> 1.06f
            else -> 1f
        },
        animationSpec = tween(140),
        label = "chScale",
    )

    val shape = RoundedCornerShape(14.dp)
    val accentColors = listOf(
        Palette.LiveBadgeRed,
        Color(0xFF10B981),
        Color(0xFF8B5CF6),
        Palette.FocusCyan,
    )
    val bottomAccent = accentColors[index % accentColors.size]
    val circleSize = if (LocalDisplayMode.current == DisplayMode.TABLET_OR_TV) 54.dp else 46.dp

    val primaryLogoUrl = remember(channel.icon, channel.name) {
        ChannelLogoResolver.resolvePrimaryUrl(channel.icon, channel.name)
    }
    val fallbackLogoUrl = remember(channel.name) {
        ChannelLogoResolver.resolveInternetLogoUrl(channel.name)
    }
    val (brandLabel, brandColor) = remember(channel.name) {
        ChannelLogoResolver.resolveBrandStyle(channel.name)
    }
    var activeLogoUrl by remember(primaryLogoUrl, fallbackLogoUrl) { mutableStateOf(primaryLogoUrl) }
    var logoFailed by remember(primaryLogoUrl, fallbackLogoUrl) { mutableStateOf(primaryLogoUrl.isBlank()) }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(if (focused) Palette.CardSurfaceElevated else Palette.CardSurface)
            .border(
                width = if (focused) 2.5.dp else 1.dp,
                color = if (focused) Palette.FocusCyan else Palette.CardBorderIdle,
                shape = shape,
            )
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        // Centered circular channel badge with automatic internet logo resolution
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-2).dp)
                .size(circleSize)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!logoFailed && activeLogoUrl.isNotBlank()) {
                AsyncImage(
                    model = activeLogoUrl,
                    contentDescription = channel.name,
                    contentScale = ContentScale.Fit,
                    onError = {
                        if (fallbackLogoUrl != null && activeLogoUrl != fallbackLogoUrl) {
                            activeLogoUrl = fallbackLogoUrl
                        } else {
                            logoFailed = true
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(brandColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = brandLabel,
                        color = brandColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Red "مباشر" pill badge at TopEnd matching reference image
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 6.dp, end = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.LiveBadgeRed)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = "مباشر",
                color = Color.White,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // Subtle bottom progress/accent line matching reference image
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .height(2.5.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.14f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(bottomAccent),
            )
        }
    }
}
