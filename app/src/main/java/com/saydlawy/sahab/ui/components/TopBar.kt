package com.saydlawy.sahab.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalTabNavigator
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatNowMillis
import kotlinx.coroutines.delay
import java.util.Locale

private data class NavTabItem(val route: String, val label: String)

private val NavTabs = listOf(
    NavTabItem(Routes.HOME, Ar.home),
    NavTabItem(Routes.LIVE, "البث المباشر"),
    NavTabItem(Routes.MOVIES, "الأفلام"),
    NavTabItem(Routes.SERIES, "المسلسلات"),
    NavTabItem(Routes.SPORTS, Ar.sports),
    NavTabItem(Routes.FAVORITES, Ar.favorites),
)

/** The rounded-square gradient tile with the Sahab cloud. */
@Composable
fun BrandLogo(size: Dp = 48.dp, bright: Boolean = false, icon: androidx.compose.ui.graphics.vector.ImageVector = Ic.Cloud) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier
            .size(size)
            .shadow(16.dp, shape, ambientColor = Palette.Royal, spotColor = Palette.Royal)
            .clip(shape)
            .background(if (bright) Palette.LogoBright else Palette.Logo)
            .border(1.dp, Palette.Glow.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

/** Logo + "سحاب IPTV" gradient wordmark + small caption. */
@Composable
fun BrandMark(subtitle: String?, logoSize: Dp = 48.dp, titleSize: Int = 22) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BrandLogo(logoSize)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                Ar.brand,
                style = TextStyle(brush = Palette.BrandText, fontSize = titleSize.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Ticking "now" epoch millis value, refreshed every few seconds (API 23+ compatible). */
@Composable
fun rememberNow(periodMs: Long = 5_000L): Long {
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

@Composable
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    val fill: Brush = when {
        focused -> Palette.ActiveFill
        selected -> SolidColor(Palette.Royal.copy(alpha = 0.40f))
        else -> SolidColor(Color.Transparent)
    }
    val border = when {
        focused -> Palette.Glow
        selected -> Palette.Glow.copy(alpha = 0.40f)
        else -> Color.Transparent
    }
    Box(
        Modifier
            .clip(shape)
            .background(fill)
            .border(if (focused) 2.dp else 1.dp, border, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            color = if (selected || focused) Palette.Text else Palette.TextDim,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavTabsRow(current: String?, onTab: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.30f))
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavTabs.forEach { tab ->
            NavTab(tab.label, tab.route == current) { if (tab.route != current) onTab(tab.route) }
        }
    }
}

/**
 * The top bar shared by every main screen: brand, section tabs, clock pill and account buttons.
 * On phones it collapses to a compact row with the tabs scrolling underneath.
 */
@Composable
fun SahabTopBar(
    current: String?,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String = Ar.brandTagline,
    showTabs: Boolean = true,
) {
    val onTab = LocalTabNavigator.current
    val displayMode = LocalDisplayMode.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val roomy = maxWidth >= 1180.dp && displayMode == DisplayMode.TABLET_OR_TV
        when (displayMode) {
            DisplayMode.TABLET_OR_TV -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 28.dp, top = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        GlassIconButton(Ic.Back, Ar.back, onBack, size = 46.dp)
                        Spacer(Modifier.width(14.dp))
                    }
                    BrandMark(subtitle = if (roomy) subtitle else null)
                    if (showTabs) {
                        Spacer(Modifier.width(20.dp))
                        NavTabsRow(current, onTab)
                    }
                    Spacer(Modifier.weight(1f))
                    if (roomy) {
                        ClockPill()
                        Spacer(Modifier.width(14.dp))
                    }
                    GlassIconButton(
                        Ic.Settings,
                        Ar.settings,
                        { if (current != Routes.SETTINGS) onTab(Routes.SETTINGS) },
                        size = 46.dp,
                        selected = current == Routes.SETTINGS,
                        tint = Palette.TextDim,
                    )
                    Spacer(Modifier.width(10.dp))
                    GlassIconButton(
                        Ic.Person,
                        Ar.profile,
                        { if (current != Routes.SETTINGS) onTab(Routes.SETTINGS) },
                        size = 46.dp,
                        tint = Palette.TextDim,
                    )
                }
            }
            DisplayMode.PHONE_LANDSCAPE -> {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (onBack != null) {
                        GlassIconButton(Ic.Back, Ar.back, onBack, size = 40.dp)
                    }
                    BrandMark(subtitle = null, logoSize = 36.dp, titleSize = 17)
                    if (showTabs) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(NavTabs, key = { it.route }) { tab ->
                                NavTab(tab.label, tab.route == current) { if (tab.route != current) onTab(tab.route) }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    GlassIconButton(
                        Ic.Settings,
                        Ar.settings,
                        { if (current != Routes.SETTINGS) onTab(Routes.SETTINGS) },
                        size = 40.dp,
                        selected = current == Routes.SETTINGS,
                        tint = Palette.TextDim,
                    )
                }
            }
            DisplayMode.PHONE_PORTRAIT -> {
                Column(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (onBack != null) {
                            GlassIconButton(Ic.Back, Ar.back, onBack, size = 42.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        BrandMark(subtitle = null, logoSize = 40.dp, titleSize = 19)
                        Spacer(Modifier.weight(1f))
                        GlassIconButton(
                            Ic.Settings,
                            Ar.settings,
                            { if (current != Routes.SETTINGS) onTab(Routes.SETTINGS) },
                            size = 42.dp,
                            selected = current == Routes.SETTINGS,
                            tint = Palette.TextDim,
                        )
                    }
                    if (showTabs) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(NavTabs, key = { it.route }) { tab ->
                                NavTab(tab.label, tab.route == current) { if (tab.route != current) onTab(tab.route) }
                            }
                        }
                    }
                }
            }
        }
    }
}

