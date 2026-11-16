package com.saydlawy.sahab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
import com.saydlawy.sahab.R
import com.saydlawy.sahab.data.ThemeCustomization
import com.saydlawy.sahab.data.ThemePreferences

private fun colorMapForTheme(themeId: String): Map<String, Color> {
    val cosmic = mutableMapOf(
        "DeepBlue" to Color(0xFF0B0F24), "TextMuted" to Color(0xFF94A3B8),
        "CinematicBg" to Color(0xFF0B0D17), "CinematicBgSecondary" to Color(0xFF101322),
        "CardSurface" to Color(0xFF161A29), "CardSurfaceElevated" to Color(0xFF1E2336),
        "CardBorderIdle" to Color(0x26FFFFFF), "FocusCyan" to Color(0xFF38BDF8),
        "FocusBlueGlow" to Color(0xFF0EA5E9), "LiveBadgeRed" to Color(0xFFE53935),
        "NewBadgeBlue" to Color(0xFF2563EB), "CategorySeriesStart" to Color(0xFF5B21B6),
        "CategorySeriesEnd" to Color(0xFF2E1065), "CategoryMoviesStart" to Color(0xFF991B1B),
        "CategoryMoviesEnd" to Color(0xFF450A0A), "CategoryLiveStart" to Color(0xFF0E7490),
        "CategoryLiveEnd" to Color(0xFF083344), "Royal" to Color(0xFF8E45FF),
        "Glow" to Color(0xFF7DC4FF), "Gold" to Color(0xFFE0A23B),
        "Blue" to Color(0xFF2563EB), "Emerald" to Color(0xFF34D399),
        "Glass" to Color(0x8C12122D), "GlassStrong" to Color(0xA612122D),
        "GlassBorder" to Color(0x14FFFFFF), "InputBg" to Color(0xB30F0B1E),
        "BgTop" to Color(0xFF111433), "Text" to Color.White, "TextDim" to Color(0xFFA9AEC7),
        "DialogBg" to Color(0xF212122D), "Danger" to Color(0xFFFF6B6B),
        "ProgressStart" to Color(0xFF60A5FA),
        "OsdSurfaceLowest" to Color(0xFF0F0B1E), "OsdSurfaceLow" to Color(0xFF1D192C),
        "OsdSurfaceContainer" to Color(0xFF211D31), "OsdSurfaceHigh" to Color(0xFF2B273C),
        "OsdSurfaceHighest" to Color(0xFF363247), "OsdPrimary" to Color(0xFFDCB8FF),
        "OsdPrimaryContainer" to Color(0xFF8A2BE2), "OsdOnPrimary" to Color(0xFF480081),
        "OsdCyan" to Color(0xFF00FBFB), "OsdTertiary" to Color(0xFFC4C1FB),
        "OsdOnSurface" to Color(0xFFE7DEFA), "OsdOnSurfaceVariant" to Color(0xFFCFC2D7),
        "OsdOutline" to Color(0xFF988CA0), "OsdOutlineVariant" to Color(0xFF4C4354),
        "SportSurface" to Color(0xFF0C0919), "SportContainer" to Color(0xFF151026),
        "SportContainerHigh" to Color(0xFF1F1838), "SportContainerHighest" to Color(0xFF2B224C),
        "SportPrimary" to Color(0xFFDCB8FF), "SportViolet" to Color(0xFF8A2BE2),
        "SportVioletDark" to Color(0xFF7928CA), "SportCyan" to Color(0xFF00FBFB),
        "SportLiveRed" to Color(0xFFFF334B), "SportGold" to Color(0xFFF5A623),
        "SportEmerald" to Color(0xFF34D399), "SportTextMuted" to Color(0xFFA9AEC7),
    )
    when (themeId) {
        "cosmic_glass_pro" -> {
            cosmic["Glass"] = Color(0x9912122D)
            cosmic["GlassStrong"] = Color(0xCC12122D)
            cosmic["GlassBorder"] = Color(0x26FFFFFF)
        }
        "oled_cinema" -> {
            cosmic["DeepBlue"] = Color(0xFF050608)
            cosmic["CinematicBg"] = Color.Black
            cosmic["CinematicBgSecondary"] = Color(0xFF050608)
            cosmic["BgTop"] = Color(0xFF050608)
            cosmic["CardSurface"] = Color(0xFF0C0C0D)
            cosmic["CardSurfaceElevated"] = Color(0xFF1F2436)
            cosmic["CardBorderIdle"] = Color(0xFF1F2436)
            cosmic["Royal"] = Color(0xFFE11D48)
            cosmic["Blue"] = Color(0xFF0EA5E9)
            cosmic["FocusCyan"] = Color(0xFF0EA5E9)
            cosmic["FocusBlueGlow"] = Color(0xFF0EA5E9)
            cosmic["Glow"] = Color(0xFF0EA5E9)
            cosmic["Glass"] = Color(0xE60C0C0D)
            cosmic["GlassStrong"] = Color(0xF20C0C0D)
            cosmic["GlassBorder"] = Color(0xFF1F2436)
            cosmic["InputBg"] = Color(0xFF0C0C0D)
            cosmic["DialogBg"] = Color(0xF20C0C0D)
            cosmic["OsdSurfaceLowest"] = Color.Black
            cosmic["OsdSurfaceLow"] = Color(0xFF0C0C0D)
            cosmic["OsdSurfaceContainer"] = Color(0xFF0C0C0D)
            cosmic["OsdSurfaceHigh"] = Color(0xFF1F2436)
            cosmic["OsdSurfaceHighest"] = Color(0xFF1F2436)
            cosmic["OsdPrimaryContainer"] = Color(0xFFE11D48)
            cosmic["OsdCyan"] = Color(0xFF0EA5E9)
            cosmic["SportSurface"] = Color(0xFF050608)
            cosmic["SportContainer"] = Color(0xFF0C0C0D)
            cosmic["SportContainerHigh"] = Color(0xFF1F2436)
            cosmic["SportContainerHighest"] = Color(0xFF1F2436)
            cosmic["SportViolet"] = Color(0xFFE11D48)
            cosmic["SportVioletDark"] = Color(0xFFB9143B)
            cosmic["SportCyan"] = Color(0xFF0EA5E9)
        }
        "dynamic_bento" -> {
            cosmic["Glass"] = Color.White.copy(alpha = 0.12f)
            cosmic["GlassStrong"] = Color.White.copy(alpha = 0.12f)
            cosmic["GlassBorder"] = Color.White.copy(alpha = 0.18f)
        }
    }
    return cosmic
}

fun themeBaseColor(themeId: String, token: String): Color = colorMapForTheme(themeId)[token] ?: Color.Magenta

private fun parseThemeColor(value: String): Color? {
    val hex = value.trim().removePrefix("#")
    val parsed = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color((0xFF000000L or parsed).toInt())
        8 -> Color(parsed.toInt())
        else -> null
    }
}

private fun resolvedColors(settings: ThemePreferences): Map<String, Color> {
    val id = settings.selectedThemeId
    val base = colorMapForTheme(id).toMutableMap()
    settings.customization(id).colorOverrides.forEach { (key, value) ->
        parseThemeColor(value)?.let { color -> if (key in base) base[key] = color }
    }
    settings.customization(id).let { customization ->
        val alpha = customization.glassOpacity.coerceIn(0.15f, 1f)
        listOf("Glass", "GlassStrong").forEach { key ->
            base[key]?.let { color -> base[key] = color.copy(alpha = color.alpha * alpha) }
        }
    }
    return base
}

/** Dynamic palette backed by persisted theme state; every screen observes the same tokens. */
object Palette {
    private var colors by mutableStateOf(colorMapForTheme("cosmic_glass"))
    internal fun install(settings: ThemePreferences) { colors = resolvedColors(settings) }
    fun color(token: String): Color = colors[token] ?: Color.Magenta
    private fun token(name: String) = color(name)

    val DeepBlue get() = token("DeepBlue")
    val TextMuted get() = token("TextMuted")
    val CinematicBg get() = token("CinematicBg")
    val CinematicBgSecondary get() = token("CinematicBgSecondary")
    val CardSurface get() = token("CardSurface")
    val CardSurfaceElevated get() = token("CardSurfaceElevated")
    val CardBorderIdle get() = token("CardBorderIdle")
    val CardBorder get() = CardBorderIdle
    val FocusCyan get() = token("FocusCyan")
    val FocusBlueGlow get() = token("FocusBlueGlow")
    val LiveBadgeRed get() = token("LiveBadgeRed")
    val LiveRed get() = LiveBadgeRed
    val NewBadgeBlue get() = token("NewBadgeBlue")
    val CategorySeriesStart get() = token("CategorySeriesStart")
    val CategorySeriesEnd get() = token("CategorySeriesEnd")
    val CategoryMoviesStart get() = token("CategoryMoviesStart")
    val CategoryMoviesEnd get() = token("CategoryMoviesEnd")
    val CategoryLiveStart get() = token("CategoryLiveStart")
    val CategoryLiveEnd get() = token("CategoryLiveEnd")
    val Royal get() = token("Royal")
    val Glow get() = token("Glow")
    val Live get() = token("LiveBadgeRed")
    val Gold get() = token("Gold")
    val Blue get() = token("Blue")
    val Emerald get() = token("Emerald")
    val Glass get() = token("Glass")
    val GlassStrong get() = token("GlassStrong")
    val GlassBorder get() = token("GlassBorder")
    val InputBg get() = token("InputBg")
    val Bg get() = DeepBlue
    val BgTop get() = token("BgTop")
    val Violet get() = Royal
    val Pink get() = Glow
    val Cyan get() = Glow
    val Peach get() = Gold
    val Text get() = token("Text")
    val TextDim get() = token("TextDim")
    val DialogBg get() = token("DialogBg")
    val Danger get() = token("Danger")
    val Accent get() = Brush.horizontalGradient(listOf(Royal, Blue, Royal))
    val ActiveFill get() = Brush.horizontalGradient(listOf(Royal.copy(alpha = 0.45f), DeepBlue.copy(alpha = 0.85f)))
    val Progress get() = Brush.horizontalGradient(listOf(token("ProgressStart"), Royal))
    val BrandText get() = Brush.linearGradient(listOf(Color.White, Glow, Royal))
    val Logo get() = Brush.linearGradient(listOf(Royal, Blue))
    val LogoBright get() = Brush.linearGradient(listOf(Royal, Blue, Glow))
}

val ThemeColorTokenNames = listOf(
    "DeepBlue", "TextMuted", "CinematicBg", "CinematicBgSecondary", "CardSurface", "CardSurfaceElevated",
    "CardBorderIdle", "FocusCyan", "FocusBlueGlow", "LiveBadgeRed", "NewBadgeBlue",
    "CategorySeriesStart", "CategorySeriesEnd", "CategoryMoviesStart", "CategoryMoviesEnd", "CategoryLiveStart", "CategoryLiveEnd", "Royal", "Glow",
    "Gold", "Blue", "Emerald", "Glass", "GlassStrong", "GlassBorder", "InputBg", "BgTop", "Text",
    "TextDim", "DialogBg", "Danger", "ProgressStart", "OsdSurfaceLowest", "OsdSurfaceLow",
    "OsdSurfaceContainer", "OsdSurfaceHigh", "OsdSurfaceHighest", "OsdPrimary", "OsdPrimaryContainer",
    "OsdOnPrimary", "OsdCyan", "OsdTertiary", "OsdOnSurface", "OsdOnSurfaceVariant", "OsdOutline",
    "OsdOutlineVariant", "SportSurface", "SportContainer", "SportContainerHigh", "SportContainerHighest",
    "SportPrimary", "SportViolet", "SportVioletDark", "SportCyan", "SportLiveRed", "SportGold",
    "SportEmerald", "SportTextMuted",
)

val ThemeColorLabels = mapOf(
    "DeepBlue" to "الخلفية الأساسية", "TextMuted" to "النص الثانوي", "CinematicBg" to "الخلفية السينمائية",
    "CinematicBgSecondary" to "الخلفية الثانوية", "CardSurface" to "سطح البطاقة", "CardSurfaceElevated" to "البطاقة المرتفعة",
    "CardBorderIdle" to "حد البطاقة", "FocusCyan" to "لون التركيز", "FocusBlueGlow" to "وهج التركيز",
    "LiveBadgeRed" to "البث المباشر", "NewBadgeBlue" to "شارة الجديد", "Royal" to "اللون الأساسي",
    "CategorySeriesStart" to "تصنيف المسلسلات: البداية", "CategorySeriesEnd" to "تصنيف المسلسلات: النهاية",
    "CategoryMoviesStart" to "تصنيف الأفلام: البداية", "CategoryMoviesEnd" to "تصنيف الأفلام: النهاية",
    "CategoryLiveStart" to "تصنيف البث: البداية", "CategoryLiveEnd" to "تصنيف البث: النهاية",
    "Glow" to "لون الإبراز", "Gold" to "الذهبي والتقييم", "Blue" to "الأزرق", "Emerald" to "النجاح",
    "Glass" to "السطح الزجاجي", "GlassStrong" to "السطح الزجاجي القوي", "GlassBorder" to "حد الزجاج",
    "InputBg" to "خلفية الحقول", "BgTop" to "أعلى الخلفية", "Text" to "النص الأساسي", "TextDim" to "النص الخافت",
    "DialogBg" to "خلفية الحوار", "Danger" to "الخطأ", "ProgressStart" to "بداية التقدم",
    "OsdSurfaceLowest" to "المشغل: أغمق سطح", "OsdSurfaceLow" to "المشغل: سطح منخفض",
    "OsdSurfaceContainer" to "المشغل: الحاوية", "OsdSurfaceHigh" to "المشغل: سطح مرتفع",
    "OsdSurfaceHighest" to "المشغل: أعلى سطح", "OsdPrimary" to "المشغل: أساسي",
    "OsdPrimaryContainer" to "المشغل: الإجراء", "OsdOnPrimary" to "المشغل: نص الإجراء",
    "OsdCyan" to "المشغل: التركيز", "OsdTertiary" to "المشغل: ثالثي", "OsdOnSurface" to "المشغل: النص",
    "OsdOnSurfaceVariant" to "المشغل: نص ثانوي", "OsdOutline" to "المشغل: الحد",
    "OsdOutlineVariant" to "المشغل: حد ثانوي", "SportSurface" to "الرياضة: الخلفية",
    "SportContainer" to "الرياضة: الحاوية", "SportContainerHigh" to "الرياضة: حاوية مرتفعة",
    "SportContainerHighest" to "الرياضة: أعلى حاوية", "SportPrimary" to "الرياضة: أساسي",
    "SportViolet" to "الرياضة: بنفسجي", "SportVioletDark" to "الرياضة: بنفسجي داكن",
    "SportCyan" to "الرياضة: تركيز", "SportLiveRed" to "الرياضة: مباشر", "SportGold" to "الرياضة: ذهبي",
    "SportEmerald" to "الرياضة: نجاح", "SportTextMuted" to "الرياضة: نص ثانوي",
)

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
fun SahabTheme(
    settings: ThemePreferences = ThemePreferences(),
    content: @Composable () -> Unit,
) {
    val customization = remember(settings) { settings.customization() }
    val colors = remember(settings) { resolvedColors(settings) }
    val sourceDensity = LocalDensity.current
    val scaledDensity = remember(sourceDensity, customization.fontScale) {
        Density(density = sourceDensity.density, fontScale = (sourceDensity.fontScale * customization.fontScale).coerceIn(0.7f, 1.6f))
    }
    val selectedFont = remember(customization.fontFamily) {
        when (customization.fontFamily) {
            "sans" -> FontFamily.SansSerif
            "serif" -> FontFamily.Serif
            "mono" -> FontFamily.Monospace
            else -> CairoFamily
        }
    }
    SideEffect { Palette.install(settings) }
    val scheme = darkColorScheme(
        primary = colors["Royal"] ?: Color(0xFF8E45FF),
        secondary = colors["Glow"] ?: Color(0xFF7DC4FF),
        background = colors["DeepBlue"] ?: Color(0xFF0B0F24),
        surface = colors["CardSurface"] ?: Color(0xFF161A29),
        onSurface = colors["Text"] ?: Color.White,
        onBackground = colors["Text"] ?: Color.White,
    )
    val base = Typography()
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = selectedFont),
        displayMedium = base.displayMedium.copy(fontFamily = selectedFont),
        displaySmall = base.displaySmall.copy(fontFamily = selectedFont),
        headlineLarge = base.headlineLarge.copy(fontFamily = selectedFont),
        headlineMedium = base.headlineMedium.copy(fontFamily = selectedFont),
        headlineSmall = base.headlineSmall.copy(fontFamily = selectedFont),
        titleLarge = base.titleLarge.copy(fontFamily = selectedFont),
        titleMedium = base.titleMedium.copy(fontFamily = selectedFont),
        titleSmall = base.titleSmall.copy(fontFamily = selectedFont),
        bodyLarge = base.bodyLarge.copy(fontFamily = selectedFont),
        bodyMedium = base.bodyMedium.copy(fontFamily = selectedFont),
        bodySmall = base.bodySmall.copy(fontFamily = selectedFont),
        labelLarge = base.labelLarge.copy(fontFamily = selectedFont),
        labelMedium = base.labelMedium.copy(fontFamily = selectedFont),
        labelSmall = base.labelSmall.copy(fontFamily = selectedFont),
    )
    CompositionLocalProvider(
        LocalThemePreferences provides settings,
        LocalThemeCustomization provides customization,
        LocalDensity provides scaledDensity,
    ) {
        MaterialTheme(colorScheme = scheme, typography = typography) {
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalTextStyle provides TextStyle(
                    fontFamily = selectedFont,
                    color = colors["Text"] ?: Color.White,
                    fontSize = 16.sp,
                ),
                LocalContentColor provides (colors["Text"] ?: Color.White),
            ) {
                content()
            }
        }
    }
}

val LocalThemePreferences = compositionLocalOf { ThemePreferences() }
val LocalThemeCustomization = compositionLocalOf { ThemeCustomization() }

fun ThemeCustomization.isVisible(componentId: String): Boolean = componentVisibility[componentId] ?: true
fun ThemeCustomization.placement(componentId: String, fallback: String = "end"): String = componentPlacement[componentId] ?: fallback
