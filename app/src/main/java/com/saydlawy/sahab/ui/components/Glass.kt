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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected as semSelected
import androidx.compose.ui.semantics.semantics
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
import coil3.size.Precision
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.LocalLightEffects
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization

internal val IdleBorder get() = Palette.GlassBorder
internal val PrimaryBorder get() = Palette.Glow.copy(alpha = 0.40f)
internal val IdleGlassFill: Brush get() = SolidColor(Palette.Glass)
internal val PhoneSelectedGlassFill: Brush get() = Brush.verticalGradient(
    listOf(Palette.Royal.copy(alpha = 0.28f), Palette.Royal.copy(alpha = 0.20f)),
)
internal val PosterBottomShade: Brush get() = Brush.verticalGradient(
    listOf(Color.Transparent, Color.Transparent, Palette.DeepBlue.copy(alpha = 0.75f)),
)
internal val HD_REGEX = Regex("\\bHD\\b")

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
 * Adds a visible cyan focus ring & tint for clickable controls on Android TV.
 */
@Composable
fun Modifier.tvFocusHighlight(
    shape: Shape = RoundedCornerShape(10.dp),
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    return this
        .clip(shape)
        .then(
            if (focused) {
                Modifier
                    .background(Palette.FocusCyan.copy(alpha = 0.16f))
                    .border(2.dp, Palette.FocusCyan, shape)
            } else {
                Modifier
            },
        )
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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
    shape: Shape? = null,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    fill: Brush? = null,
    autoFocus: Boolean = false,
    focusScale: Float = 1.04f,
    role: Role = Role.Button,
    contentDescription: String? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val resolvedShape = shape ?: RoundedCornerShape(LocalThemeCustomization.current.cornerRadius.dp)
    val interaction = remember { MutableInteractionSource() }
    val focusedRaw by interaction.collectIsFocusedAsState()
    val isTv = LocalIsTv.current
    val focused = focusedRaw
    val pressed by interaction.collectIsPressedAsState()
    val light = LocalLightEffects.current

    val targetScale = when {
        pressed -> 0.97f
        focused && isTv -> focusScale
        else -> 1f
    }
    val spec: AnimationSpec<Float> = if (light) snap() else tween(140)
    val scale by animateFloatAsState(targetScale, spec, label = "glassScale")

    val background: Brush = when {
        fill != null -> fill
        focused -> Palette.ActiveFill
        selected -> if (isTv) Palette.ActiveFill else PhoneSelectedGlassFill
        else -> IdleGlassFill
    }
    val borderColor = when {
        focused -> Palette.FocusCyan
        selected -> if (isTv) Palette.FocusCyan.copy(alpha = 0.75f) else Palette.Royal.copy(alpha = 0.75f)
        fill != null -> PrimaryBorder
        else -> IdleBorder
    }
    val borderWidth = if (focused) 2.5.dp else if (selected) 1.5.dp else 1.dp

    Box(
        modifier = modifier
            .initialFocus(autoFocus)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(
                if (focused && !isTv && !light) {
                    Modifier.shadow(
                        elevation = 16.dp,
                        shape = resolvedShape,
                        ambientColor = Palette.FocusCyan,
                        spotColor = Palette.FocusCyan,
                    )
                } else Modifier,
            )
            .clip(resolvedShape)
            .background(background)
            .border(borderWidth, borderColor, resolvedShape)
            .semantics(mergeDescendants = true) {
                contentDescription?.let { this.contentDescription = it }
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                role = role,
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
    shape: Shape? = null,
    strong: Boolean = false,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val resolvedShape = shape ?: RoundedCornerShape(LocalThemeCustomization.current.cornerRadius.dp)
    Column(
        modifier = modifier
            .clip(resolvedShape)
            .background(if (strong) Palette.GlassStrong else Palette.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.10f), resolvedShape)
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
    val isTv = LocalIsTv.current
    val light = LocalLightEffects.current
    GlassCard(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 54.dp)
            .then(
                if (primary && !isTv && !light) {
                    Modifier.shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = Palette.Royal, spotColor = Palette.Royal)
                } else {
                    Modifier
                },
            ),
        shape = RoundedCornerShape(18.dp),
        fill = if (primary) Palette.Accent else null,
        autoFocus = autoFocus,
        role = Role.Button,
        contentDescription = text,
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
        role = Role.Button,
        contentDescription = description,
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
        modifier = modifier.semantics(mergeDescendants = true) { semSelected = selected },
        shape = RoundedCornerShape(50),
        selected = selected,
        role = Role.Tab,
        contentDescription = text,
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
    alignment: Alignment = Alignment.Center,
    fallbackText: String = "",
    fallbackUrl: String? = null,
    targetWidthPx: Int? = null,
    targetHeightPx: Int? = null,
) {
    val primaryUrl = url.trim().ifBlank { fallbackUrl?.trim().orEmpty() }
    val secondaryUrl = fallbackUrl?.trim()?.takeIf { it.isNotBlank() && it != primaryUrl }

    Box(modifier.background(Palette.CardSurface)) {
        if (primaryUrl.isNotBlank()) {
            val context = LocalContext.current
            // 0 = primary, 1 = secondary fallback, 2 = failed
            var loadStage by remember(primaryUrl, secondaryUrl) { mutableIntStateOf(0) }
            if (loadStage == 2) {
                FallbackLetter(fallbackText)
            } else {
                val activeUrl = if (loadStage == 1 && secondaryUrl != null) secondaryUrl else primaryUrl
                val request = remember<ImageRequest>(context, activeUrl, targetWidthPx, targetHeightPx) {
                    ImageRequest.Builder(context)
                        .data(activeUrl)
                        .precision(Precision.INEXACT)
                        .apply {
                            if (targetWidthPx != null && targetHeightPx != null && targetWidthPx > 0 && targetHeightPx > 0) {
                                size(targetWidthPx, targetHeightPx)
                            }
                        }
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = contentScale,
                    alignment = alignment,
                    onError = {
                        loadStage = if (loadStage == 0 && secondaryUrl != null) 1 else 2
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            FallbackLetter(fallbackText)
        }
    }
}

@Composable
internal fun FallbackLetter(text: String) {
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

