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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.LocalLightEffects
import com.saydlawy.sahab.ui.theme.Palette

private val IdleBorder = Palette.GlassBorder
private val PrimaryBorder = Palette.Glow.copy(alpha = 0.40f)

/** Asks for keyboard/remote focus once, on televisions only. */
@Composable
fun Modifier.initialFocus(enabled: Boolean): Modifier {
    val requester = remember { FocusRequester() }
    val isTv = LocalIsTv.current
    if (enabled && isTv) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { requester.requestFocus() }
        }
    }
    return this.focusRequester(requester)
}

/**
 * The basic frosted-glass surface of the Sahab identity.
 * Idle: translucent dark glass with a ghost border.
 * Selected / focused: royal → deep-blue gradient with a glowing cyan border (glass-panel-active).
 * Focus also grows the card a little and adds a soft cyan glow, so it reads well from the sofa.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    fill: Brush? = null,
    autoFocus: Boolean = false,
    focusScale: Float = 1.04f,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focusedRaw by interaction.collectIsFocusedAsState()
    val isTv = LocalIsTv.current
    val focused = focusedRaw && isTv
    val pressed by interaction.collectIsPressedAsState()
    val light = LocalLightEffects.current

    val targetScale = when {
        pressed -> 0.97f
        focused -> focusScale
        else -> 1f
    }
    val spec: AnimationSpec<Float> = if (light) snap() else tween(140)
    val scale by animateFloatAsState(targetScale, spec, label = "glassScale")

    val background: Brush = when {
        fill != null -> fill
        focused -> Palette.ActiveFill
        selected -> if (isTv) Palette.ActiveFill else Brush.verticalGradient(listOf(Palette.Royal.copy(alpha = 0.28f), Palette.Royal.copy(alpha = 0.20f)))
        else -> Brush.verticalGradient(listOf(Palette.Glass, Palette.Glass))
    }
    val borderColor = when {
        focused -> Palette.Glow
        selected -> if (isTv) Palette.Glow.copy(alpha = 0.65f) else Palette.Royal.copy(alpha = 0.75f)
        fill != null -> PrimaryBorder
        else -> IdleBorder
    }
    val borderWidth = if (focused) 2.dp else if (selected) 1.dp else 1.dp

    Box(
        modifier = modifier
            .initialFocus(autoFocus)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (focused && !light) {
                    Modifier.shadow(
                        elevation = 18.dp,
                        shape = shape,
                        ambientColor = Palette.Glow,
                        spotColor = Palette.Glow,
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        content(focused)
    }
}

/** A non-interactive glass panel (cards that hold other controls). */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    strong: Boolean = false,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (strong) Palette.GlassStrong else Palette.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(padding),
        content = content,
    )
}

@Composable
fun GlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    autoFocus: Boolean = false,
    trailingIcon: ImageVector? = null,
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 54.dp)
            .then(
                if (primary) Modifier.shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Palette.Royal, spotColor = Palette.Royal)
                else Modifier,
            ),
        shape = RoundedCornerShape(18.dp),
        fill = if (primary) Palette.Accent else null,
        autoFocus = autoFocus,
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 26.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1)
            if (trailingIcon != null) {
                Spacer(Modifier.width(10.dp))
                Icon(trailingIcon, contentDescription = null, tint = Palette.Text, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    selected: Boolean = false,
    tint: Color = Palette.Text,
    autoFocus: Boolean = false,
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = CircleShape,
        selected = selected,
        autoFocus = autoFocus,
        focusScale = 1.08f,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier
                .align(Alignment.Center)
                .size(size * 0.44f),
        )
    }
}

/** Pill-shaped selector (categories, seasons). */
@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        selected = selected,
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) Palette.Text else Palette.TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}

/** Small coloured tag: "4K", "HD", "مباشر", ratings… */
@Composable
fun Tag(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    fontSize: TextUnit = 11.sp,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (filled) color else color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = if (filled) 1f else 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, color = if (filled) Color.White else color, maxLines = 1)
    }
}

/** A small coloured dot followed by a bold label, as used for section titles in the reference. */
@Composable
fun DotTitle(text: String, dot: Color, modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp, color: Color = Palette.Text) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            GlassIconButton(Ic.Back, com.saydlawy.sahab.util.Ar.back, onBack, size = 48.dp)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = title,
            style = TextStyle(brush = Palette.BrandText, fontSize = 26.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    DotTitle(text, Palette.Gold, modifier.padding(bottom = 12.dp), fontSize = 20.sp)
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Palette.Glow, trackColor = Palette.Royal.copy(alpha = 0.25f))
    }
}

@Composable
fun MessageBox(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Palette.Glass)
                .border(1.dp, Palette.Glow.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Cloud, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.size(16.dp))
        Text(
            text = message,
            fontSize = 17.sp,
            color = Palette.TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.size(18.dp))
            GlassButton(actionLabel, onAction, primary = true, autoFocus = true)
        }
    }
}

@Composable
fun RemoteImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackText: String = "",
) {
    Box(modifier.background(Color.White.copy(alpha = 0.05f))) {
        if (url.isNotBlank()) {
            SubcomposeAsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = { FallbackLetter(fallbackText) },
                error = { FallbackLetter(fallbackText) },
            )
        } else {
            FallbackLetter(fallbackText)
        }
    }
}

@Composable
private fun FallbackLetter(text: String) {
    val letter = text.trim().firstOrNull()?.uppercaseChar() ?: return
    val gradient = remember(letter) {
        val palettes = listOf(
            listOf(Palette.Royal, Palette.Blue),
            listOf(Color(0xFF3B2A7A), Palette.Royal),
            listOf(Palette.Blue, Palette.Glow),
            listOf(Color(0xFF1E1B4B), Palette.Royal),
            listOf(Color(0xFF0F2A5C), Palette.Blue),
        )
        palettes[letter.code % palettes.size]
    }
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter.toString(),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.15f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .clip(RoundedCornerShape(50))
                .background(Palette.Progress),
        )
    }
}

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
        items(itemCount) {
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
    progress: Float? = null,
    onFocused: (() -> Unit)? = null,
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        onLongClick = onLongClick,
        focusScale = 1.06f,
    ) { focused ->
        if (focused && onFocused != null) {
            LaunchedEffect(Unit) { onFocused() }
        }
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                RemoteImage(
                    url = imageUrl,
                    fallbackText = title,
                    modifier = if (contentScale == ContentScale.Fit) Modifier.fillMaxSize().padding(14.dp) else Modifier.fillMaxSize(),
                    contentScale = contentScale,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Transparent, Palette.DeepBlue.copy(alpha = 0.75f)),
                            ),
                        ),
                )
                if (badge.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(7.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.65f))
                            .border(1.dp, Palette.Gold.copy(alpha = 0.35f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ic.Star, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(badge, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Gold, maxLines = 1)
                    }
                }
                if (!quality.isNullOrBlank()) {
                    Tag(
                        quality,
                        if (quality == "4K") Palette.Gold else Palette.Glow,
                        Modifier.align(Alignment.TopEnd).padding(7.dp),
                        fontSize = 10.sp,
                    )
                }
                if (progress != null) {
                    ProgressLine(
                        progress,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        height = 3.dp,
                    )
                }
            }
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 2.dp, bottom = if (subtitle.isBlank()) 10.dp else 0.dp),
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = Palette.Glow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                )
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
        Regex("\\bHD\\b").containsMatchIn(u) -> "HD"
        else -> null
    }
}
