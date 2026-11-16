package com.saydlawy.sahab.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalTabNavigator
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.home.components.CompactTopBarInfo
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.LocalThemePreferences
import com.saydlawy.sahab.ui.theme.isVisible
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatNowMillis
import kotlinx.coroutines.delay
import java.util.Locale

internal data class NavTabItem(val route: String, val label: String)

internal val NavTabs = listOf(
    NavTabItem(Routes.HOME, Ar.home),
    NavTabItem(Routes.LIVE, "البث المباشر"),
    NavTabItem(Routes.MOVIES, "الأفلام"),
    NavTabItem(Routes.SERIES, "المسلسلات"),
    NavTabItem(Routes.SPORTS, Ar.sports),
    NavTabItem(Routes.FAVORITES, Ar.favorites),
)

/** The rounded-square gradient tile with the Sahab cloud + play symbol. */
@Composable
fun BrandLogo(
    size: Dp = 44.dp,
    bright: Boolean = false,
    icon: ImageVector = Ic.Cloud,
) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(12.dp, shape, ambientColor = Palette.Royal, spotColor = Palette.FocusCyan)
            .clip(shape)
            .background(
                if (bright) {
                    Palette.LogoBright
                } else {
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1), Color(0xFF1D4ED8)),
                        start = Offset.Zero,
                        end = Offset(120f, 120f),
                    )
                },
            )
            .border(1.dp, Color.White.copy(alpha = 0.28f), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon == Ic.Cloud) {
            Canvas(modifier = Modifier.size(size * 0.68f)) {
                val w = this.size.width
                val h = this.size.height
                val cloud = Path().apply {
                    moveTo(w * 0.25f, h * 0.72f)
                    lineTo(w * 0.76f, h * 0.72f)
                    cubicTo(w * 0.90f, h * 0.72f, w * 0.94f, h * 0.50f, w * 0.80f, h * 0.46f)
                    cubicTo(w * 0.78f, h * 0.26f, w * 0.52f, h * 0.22f, w * 0.44f, h * 0.38f)
                    cubicTo(w * 0.30f, h * 0.36f, w * 0.18f, h * 0.50f, w * 0.25f, h * 0.72f)
                    close()
                }
                drawPath(
                    path = cloud,
                    color = Color.White.copy(alpha = 0.95f),
                    style = Stroke(width = w * 0.075f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
                val play = Path().apply {
                    moveTo(w * 0.46f, h * 0.43f)
                    lineTo(w * 0.46f, h * 0.65f)
                    lineTo(w * 0.65f, h * 0.54f)
                    close()
                }
                drawPath(play, color = Color.White)
            }
        } else {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
        }
    }
}

/** Logo + "سحاب" wordmark + "(Sahab IPTV)" caption matching reference layout while preserving Sahab identity. */
@Composable
fun BrandMark(
    subtitle: String? = "(Sahab IPTV)",
    logoSize: Dp = 42.dp,
    titleSize: Int = 20,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandLogo(logoSize)
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
            Text(
                text = "سحاب",
                color = Color.White,
                fontSize = titleSize.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Palette.TextMuted,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Ticking "now" epoch millis value, refreshed every 30s. */
@Composable
fun rememberNow(periodMs: Long = 30_000L): Long {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Glass pill: "09:41 م • extra" (extra defaults to today's date). */
@Composable
fun ClockPill(extra: String? = null, modifier: Modifier = Modifier) {
    val now = rememberNow()
    val ar = Locale.forLanguageTag("ar")
    val time = formatNowMillis(now, "hh:mm a", ar)
    val trailing = extra ?: formatNowMillis(now, "EEEE، d MMMM", ar)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.Glass)
            .border(1.dp, Palette.GlassBorder, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Text, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(6.dp).clip(CircleShape).background(Palette.Glow))
        Spacer(Modifier.width(10.dp))
        Text(trailing, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.Glow, maxLines = 1)
    }
}

/**
 * The top bar shared by every main screen: brand, section tabs with glowing cyan underline,
 * live weather + clock, notification bell, search, and settings avatar button.
 */
@Composable
fun SahabTopBar(
    current: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String = "(Sahab IPTV)",
    showTabs: Boolean = true,
) {
    val onTab = LocalTabNavigator.current
    val displayMode = LocalDisplayMode.current
    val maxWidth = LocalWindowInfo.current.widthDp
    val customization = LocalThemeCustomization.current
    val adaptiveOledNavigation = LocalThemePreferences.current.selectedThemeId == "oled_cinema" &&
        displayMode != DisplayMode.PHONE_LANDSCAPE
    val showNavigation = showTabs && customization.isVisible("global_navigation") && !adaptiveOledNavigation
    val navigationPlacement = customization.componentPlacement["global_navigation"] ?: "center"

    Box(modifier.fillMaxWidth()) {
        when (displayMode) {
            DisplayMode.TABLET_OR_TV -> {
                val roomy = maxWidth >= 960.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        GlassIconButton(Ic.Back, Ar.back, onBack, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                    }
                    BrandMark(
                        subtitle = if (roomy) "(Sahab IPTV)" else null,
                        logoSize = 40.dp,
                        titleSize = 20,
                    )
                    if (showNavigation) {
                        if (navigationPlacement == "center" || navigationPlacement == "end") {
                            Spacer(Modifier.weight(1f))
                        } else {
                            Spacer(Modifier.width(24.dp))
                        }
                        CinematicNavTabsRow(
                            current = current,
                            onTab = onTab,
                            compact = false,
                            scrollable = false,
                        )
                        if (navigationPlacement == "center") Spacer(Modifier.weight(1f))
                        if (navigationPlacement == "start") Spacer(Modifier.weight(1f))
                    }
                    if (!showNavigation || navigationPlacement == "end") Spacer(Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (roomy) {
                            CompactTopBarInfo()
                        }
                        TopBarProfileButton(
                            active = current == Routes.SETTINGS,
                            onClick = { if (current != Routes.SETTINGS) onTab(Routes.SETTINGS) },
                            size = 38.dp,
                        )
                    }
                }
            }

            DisplayMode.PHONE_LANDSCAPE -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (onBack != null) {
                        GlassIconButton(Ic.Back, Ar.back, onBack, size = 36.dp)
                    }
                    BrandMark(subtitle = null, logoSize = 32.dp, titleSize = 16)
                    if (showNavigation) {
                        CinematicNavTabsRow(
                            current = current,
                            onTab = onTab,
                            compact = true,
                            scrollable = true,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    CompactTopBarInfo()
                    TopBarProfileButton(
                        active = current == Routes.SETTINGS,
                        onClick = { if (current != Routes.SETTINGS) onTab(Routes.SETTINGS) },
                        size = 34.dp,
                    )
                }
            }

            DisplayMode.PHONE_PORTRAIT -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onBack != null) {
                            GlassIconButton(Ic.Back, Ar.back, onBack, size = 36.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        BrandMark(subtitle = "(Sahab IPTV)", logoSize = 34.dp, titleSize = 17)
                        Spacer(Modifier.weight(1f))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CompactTopBarInfo()
                            TopBarProfileButton(
                                active = current == Routes.SETTINGS,
                                onClick = { if (current != Routes.SETTINGS) onTab(Routes.SETTINGS) },
                                size = 34.dp,
                            )
                        }
                    }
                    if (showNavigation) {
                        CinematicNavTabsRow(
                            current = current,
                            onTab = onTab,
                            compact = true,
                            scrollable = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

