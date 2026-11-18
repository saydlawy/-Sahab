package com.saydlawy.sahab.ui.theme

import androidx.compose.ui.graphics.Color

data class OledAccentPreset(
    val id: String,
    val nameAr: String,
    val accent: Color,
    val focus: Color,
    val glow: Color,
)

val OledAccentPresets: List<OledAccentPreset> = listOf(
    OledAccentPreset(
        id = "oled_cyan",
        nameAr = "أزرق سماوي",
        accent = Color(0xFF0EA5E9),
        focus = Color(0xFF38BDF8),
        glow = Color(0xFF7DD3FC),
    ),
    OledAccentPreset(
        id = "oled_prime",
        nameAr = "أزرق برايم",
        accent = Color(0xFF00A8E1),
        focus = Color(0xFF00C2FF),
        glow = Color(0xFF38BDF8),
    ),
    OledAccentPreset(
        id = "oled_netflix",
        nameAr = "أحمر سينمائي",
        accent = Color(0xFFE50914),
        focus = Color(0xFFFF3B30),
        glow = Color(0xFFFB7185),
    ),
    OledAccentPreset(
        id = "oled_gold",
        nameAr = "ذهبي فاخر",
        accent = Color(0xFFE0A23B),
        focus = Color(0xFFF59E0B),
        glow = Color(0xFFFBBF24),
    ),
    OledAccentPreset(
        id = "oled_emerald",
        nameAr = "زمردي",
        accent = Color(0xFF10B981),
        focus = Color(0xFF34D399),
        glow = Color(0xFF6EE7B7),
    ),
    OledAccentPreset(
        id = "oled_violet",
        nameAr = "بنفسجي ملكي",
        accent = Color(0xFF8E45FF),
        focus = Color(0xFFA855F7),
        glow = Color(0xFFC084FC),
    ),
)

fun oledAccentPresetFor(id: String): OledAccentPreset =
    OledAccentPresets.firstOrNull { it.id == id } ?: OledAccentPresets.first()

fun applyOledCinemaTokens(base: MutableMap<String, Color>, accentPresetId: String) {
    val preset = oledAccentPresetFor(accentPresetId)
    base["DeepBlue"] = Color.Black
    base["CinematicBg"] = Color.Black
    base["CinematicBgSecondary"] = Color(0xFF050507)
    base["BgTop"] = Color.Black
    base["CardSurface"] = Color(0xFF0A0A0D)
    base["CardSurfaceElevated"] = Color(0xFF14141A)
    base["CardBorderIdle"] = Color(0xFF1F1F29)
    base["Royal"] = preset.accent
    base["Blue"] = preset.glow
    base["FocusCyan"] = preset.focus
    base["FocusBlueGlow"] = preset.focus
    base["Glow"] = preset.glow
    base["Glass"] = Color(0xE60A0A0D)
    base["GlassStrong"] = Color(0xF50A0A0D)
    base["GlassBorder"] = Color(0xFF1F1F29)
    base["InputBg"] = Color(0xFF0A0A0D)
    base["DialogBg"] = Color(0xF50A0A0D)
    base["ProgressStart"] = preset.focus
    base["OsdSurfaceLowest"] = Color.Black
    base["OsdSurfaceLow"] = Color(0xFF0A0A0D)
    base["OsdSurfaceContainer"] = Color(0xFF0A0A0D)
    base["OsdSurfaceHigh"] = Color(0xFF14141A)
    base["OsdSurfaceHighest"] = Color(0xFF14141A)
    base["OsdPrimaryContainer"] = preset.accent
    base["OsdCyan"] = preset.focus
}
