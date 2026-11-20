package com.saydlawy.sahab.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.LocalLightEffects
import com.saydlawy.sahab.ui.theme.Palette

@Composable
fun SkeletonGrid(
    columnWidth: Dp,
    modifier: Modifier = Modifier,
    itemCount: Int = 12,
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.24f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    LazyVerticalGrid(
        columns = GridCells.Adaptive(columnWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
        userScrollEnabled = false,
    ) {
        items(itemCount, key = { it }) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Palette.Royal.copy(alpha = alpha)),
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.7f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = alpha * 0.8f)),
                )
            }
        }
    }
}

/** Movie / series poster tile: glass frame, rating tag, optional quality tag and progress. */
@Composable
fun PosterCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String = "",
    onLongClick: (() -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Crop,
    quality: String? = null,
    subtitle: String = "",
    genre: String = "",
    progress: Float? = null,
    onFocused: (() -> Unit)? = null,
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        onLongClick = onLongClick,
        focusScale = 1.05f,
    ) { focused ->
        if (focused && onFocused != null) {
            LaunchedEffect(Unit) { onFocused() }
        }
        val isTv = LocalIsTv.current
        var posterSize by remember { mutableStateOf(IntSize.Zero) }
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.CardSurface)
                .onSizeChanged { posterSize = it },
        ) {
            RemoteImage(
                url = imageUrl,
                fallbackText = title,
                modifier = if (contentScale == ContentScale.Fit) Modifier.fillMaxSize().padding(14.dp) else Modifier.fillMaxSize(),
                contentScale = contentScale,
                targetWidthPx = posterSize.width.takeIf { it > 0 },
                targetHeightPx = posterSize.height.takeIf { it > 0 },
            )
            // Deep bottom gradient matching reference movie cards (simplified on TV for fast D-pad scroll)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (isTv) {
                            Brush.verticalGradient(
                                listOf(
                                    Color.Transparent,
                                    Palette.CinematicBg.copy(alpha = 0.45f),
                                    Palette.CinematicBg.copy(alpha = 0.95f),
                                ),
                            )
                        } else {
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    0.48f to Palette.CinematicBg.copy(alpha = 0.20f),
                                    0.76f to Palette.CinematicBg.copy(alpha = 0.80f),
                                    1.0f to Palette.CinematicBg.copy(alpha = 0.97f),
                                ),
                            )
                        },
                    ),
            )
            // Focused inner cyan rim highlight matching reference "سيد الظلام" card
            if (focused) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(2.5.dp, Palette.FocusCyan, RoundedCornerShape(14.dp)),
                )
            }

            // Top-left (in RTL: TopEnd) TMDB genre badge
            if (genre.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Palette.CinematicBg.copy(alpha = 0.78f))
                        .border(0.8.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = genre,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Bottom overlay content: Title, Subtitle, and Bottom Row (Star Rating on right, Quality pill on left)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (badge.isNotBlank() || subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Bottom-right in RTL: TMDB Gold star rating
                        if (badge.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Ic.Star,
                                    contentDescription = null,
                                    tint = Palette.Gold,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = badge,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Palette.Gold,
                                    maxLines = 1,
                                )
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        // Bottom-left in RTL: TMDB Year
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Palette.TextMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
                if (progress != null) {
                    Spacer(Modifier.height(4.dp))
                    ProgressLine(
                        progress,
                        Modifier.fillMaxWidth(),
                        height = 3.dp,
                    )
                }
            }
        }
    }
}

/** Guesses a quality tag from a title ("… 4K", "… FHD", "… HD"). */
fun qualityOf(name: String): String? {
    val u = name.uppercase()
    return when {
        "4K" in u || "UHD" in u -> "4K"
        "FHD" in u || "1080" in u -> "FHD"
        HD_REGEX.containsMatchIn(u) -> "HD"
        else -> null
    }
}
