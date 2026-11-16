package com.saydlawy.sahab.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.saydlawy.sahab.ui.LocalLightEffects
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.LocalThemePreferences

/** Deep cosmic background with cached glow / royal / blue nebulas (zero per-frame allocations). */
@Composable
fun DreamyBackground() {
    val light = LocalLightEffects.current
    val themeId = LocalThemePreferences.current.selectedThemeId
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val bgBrush = Brush.verticalGradient(
                    colors = listOf(Palette.BgTop, Palette.Bg, Palette.Bg),
                    startY = 0f,
                    endY = size.height,
                )
                val nebulas = if (light || themeId == "oled_cinema") {
                    emptyList()
                } else {
                    val ambientScale = if (themeId == "dynamic_bento") 0.65f else 1f
                    listOf(
                        Triple(
                            Offset(size.width * 0.88f, size.height * -0.05f),
                            size.maxDimension * 0.55f,
                            Brush.radialGradient(
                                colors = listOf(Palette.Glow.copy(alpha = 0.16f * ambientScale), Palette.Glow.copy(alpha = 0.056f * ambientScale), Color.Transparent),
                                center = Offset(size.width * 0.88f, size.height * -0.05f),
                                radius = size.maxDimension * 0.55f,
                            ),
                        ),
                        Triple(
                            Offset(size.width * 0.75f, size.height * 0.10f),
                            size.maxDimension * 0.45f,
                            Brush.radialGradient(
                                colors = listOf(Palette.Royal.copy(alpha = 0.12f * ambientScale), Palette.Royal.copy(alpha = 0.042f * ambientScale), Color.Transparent),
                                center = Offset(size.width * 0.75f, size.height * 0.10f),
                                radius = size.maxDimension * 0.45f,
                            ),
                        ),
                        Triple(
                            Offset(size.width * 0.05f, size.height * 0.55f),
                            size.maxDimension * 0.42f,
                            Brush.radialGradient(
                                colors = listOf(Palette.Royal.copy(alpha = 0.13f * ambientScale), Palette.Royal.copy(alpha = 0.045f * ambientScale), Color.Transparent),
                                center = Offset(size.width * 0.05f, size.height * 0.55f),
                                radius = size.maxDimension * 0.42f,
                            ),
                        ),
                        Triple(
                            Offset(size.width * 0.65f, size.height * 1.02f),
                            size.maxDimension * 0.50f,
                            Brush.radialGradient(
                                colors = listOf(Palette.Blue.copy(alpha = 0.12f * ambientScale), Palette.Blue.copy(alpha = 0.042f * ambientScale), Color.Transparent),
                                center = Offset(size.width * 0.65f, size.height * 1.02f),
                                radius = size.maxDimension * 0.50f,
                            ),
                        ),
                    )
                }
                onDrawBehind {
                    drawRect(bgBrush)
                    for ((center, radius, brush) in nebulas) {
                        drawCircle(brush = brush, radius = radius, center = center)
                    }
                }
            },
    )
}
