package com.saydlawy.sahab.data

import kotlinx.serialization.Serializable

/** A theme keeps its own visual edits so switching presets never overwrites another theme. */
@Serializable
data class ThemeCustomization(
    val colorOverrides: Map<String, String> = emptyMap(),
    val fontFamily: String = "cairo",
    val fontScale: Float = 1f,
    val iconStyle: String = "plain",
    val iconScale: Float = 1f,
    val iconContainerShape: String = "rounded",
    val cornerRadius: Float = 20f,
    val glassOpacity: Float = 1f,
    val componentVisibility: Map<String, Boolean> = emptyMap(),
    val componentPlacement: Map<String, String> = emptyMap(),
)

@Serializable
data class ThemePreferences(
    val selectedThemeId: String = "cosmic_glass",
    val customizations: Map<String, ThemeCustomization> = emptyMap(),
) {
    fun customization(themeId: String = selectedThemeId): ThemeCustomization =
        customizations[themeId] ?: defaultThemeCustomization(themeId)
}

fun defaultThemeCustomization(themeId: String): ThemeCustomization = when (themeId) {
    "cosmic_glass_pro" -> ThemeCustomization(iconStyle = "outlined", iconContainerShape = "rounded", cornerRadius = 22f, glassOpacity = 0.92f)
    "oled_cinema" -> ThemeCustomization(iconStyle = "plain", iconContainerShape = "square", cornerRadius = 12f, glassOpacity = 1f)
    "dynamic_bento" -> ThemeCustomization(iconStyle = "filled", iconContainerShape = "rounded", cornerRadius = 28f, glassOpacity = 0.78f)
    else -> ThemeCustomization()
}
