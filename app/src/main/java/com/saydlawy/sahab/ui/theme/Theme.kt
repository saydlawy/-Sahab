package com.saydlawy.sahab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.saydlawy.sahab.R

/**
 * Sahab identity (see the films / live / login reference designs):
 * deep cosmic blue, royal purple actions, glowing cyan focus, gold and live-red accents,
 * and translucent frosted-glass panels.
 */
object Palette {
    // Core identity colours
    val DeepBlue = Color(0xFF0B0F24)
    val Royal = Color(0xFF8E45FF)
    val Glow = Color(0xFF7DC4FF)
    val Live = Color(0xFFE53935)
    val Gold = Color(0xFFE0A23B)
    val Blue = Color(0xFF2563EB)
    val Emerald = Color(0xFF34D399)

    // Glass surfaces
    val Glass = Color(0x8C12122D)          // rgba(18, 18, 45, 0.55)
    val GlassStrong = Color(0xA612122D)    // rgba(18, 18, 45, 0.65)
    val GlassBorder = Color(0x14FFFFFF)    // white 8%
    val InputBg = Color(0xB30F0B1E)

    // Legacy names kept so every screen picks up the new identity.
    val Bg = DeepBlue
    val BgTop = Color(0xFF111433)
    val Violet = Royal
    val Pink = Glow
    val Cyan = Glow
    val Peach = Gold
    val Text = Color(0xFFFFFFFF)
    val TextDim = Color(0xFFA9AEC7)
    val DialogBg = Color(0xF212122D)
    val Danger = Color(0xFFFF6B6B)

    /** Primary action fill: royal → blue → royal. */
    val Accent: Brush = Brush.horizontalGradient(listOf(Royal, Blue, Royal))

    /** Active / focused glass fill (glass-panel-active). */
    val ActiveFill: Brush = Brush.horizontalGradient(
        listOf(Royal.copy(alpha = 0.45f), DeepBlue.copy(alpha = 0.85f)),
    )

    /** Progress tracks: blue-400 → royal. */
    val Progress: Brush = Brush.horizontalGradient(listOf(Color(0xFF60A5FA), Royal))

    /** Brand text gradient: white → glow → royal. */
    val BrandText: Brush = Brush.linearGradient(listOf(Color.White, Glow, Royal))

    /** Logo tile gradient. */
    val Logo: Brush = Brush.linearGradient(listOf(Royal, Blue))
    val LogoBright: Brush = Brush.linearGradient(listOf(Royal, Blue, Glow))
}

/**
 * Google Font "Cairo" (static TrueType instances bundled in res/font, so it works offline,
 * on Android TV boxes without Google Play Services, and on API 23+).
 */
val CairoFamily = FontFamily(
    Font(R.font.cairo_regular, FontWeight.Normal),
    Font(R.font.cairo_medium, FontWeight.Medium),
    Font(R.font.cairo_semibold, FontWeight.SemiBold),
    Font(R.font.cairo_bold, FontWeight.Bold),
    Font(R.font.cairo_extrabold, FontWeight.ExtraBold),
)

@Composable
fun SahabTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Palette.Royal,
        secondary = Palette.Glow,
        background = Palette.Bg,
        surface = Palette.Bg,
        onSurface = Palette.Text,
        onBackground = Palette.Text,
    )
    val base = Typography()
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = CairoFamily),
        displayMedium = base.displayMedium.copy(fontFamily = CairoFamily),
        displaySmall = base.displaySmall.copy(fontFamily = CairoFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = CairoFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = CairoFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = CairoFamily),
        titleLarge = base.titleLarge.copy(fontFamily = CairoFamily),
        titleMedium = base.titleMedium.copy(fontFamily = CairoFamily),
        titleSmall = base.titleSmall.copy(fontFamily = CairoFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = CairoFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = CairoFamily),
        bodySmall = base.bodySmall.copy(fontFamily = CairoFamily),
        labelLarge = base.labelLarge.copy(fontFamily = CairoFamily),
        labelMedium = base.labelMedium.copy(fontFamily = CairoFamily),
        labelSmall = base.labelSmall.copy(fontFamily = CairoFamily),
    )
    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalTextStyle provides TextStyle(fontFamily = CairoFamily, color = Palette.Text, fontSize = 16.sp),
            LocalContentColor provides Palette.Text,
        ) {
            content()
        }
    }
}
