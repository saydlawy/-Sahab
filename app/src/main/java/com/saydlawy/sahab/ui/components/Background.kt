package com.saydlawy.sahab.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.saydlawy.sahab.ui.LocalLightEffects
import com.saydlawy.sahab.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Deep cosmic background with slowly drifting glow / royal / blue nebulas. Static in light mode. */
@Composable
fun DreamyBackground() {
    if (LocalLightEffects.current) {
        CosmicCanvas(phase = { 0f })
    } else {
        val transition = rememberInfiniteTransition(label = "cosmos")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(80_000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
        CosmicCanvas(phase = { phase })
    }
}

@Composable
private fun CosmicCanvas(phase: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Palette.BgTop, Palette.Bg, Palette.Bg), startY = 0f, endY = size.height))
        val t = phase() * 2f * PI.toFloat()
        // Top-right glow (cyan → royal), like the reference "earth glow".
        nebula(Palette.Glow, 0.88f + 0.04f * sin(t), -0.05f + 0.05f * cos(t), 0.55f, 0.16f)
        nebula(Palette.Royal, 0.75f + 0.05f * cos(t), 0.10f + 0.04f * sin(t), 0.45f, 0.12f)
        // Left royal cloud.
        nebula(Palette.Royal, 0.05f + 0.05f * cos(t + 2f), 0.55f + 0.06f * sin(t + 1f), 0.42f, 0.13f)
        // Bottom blue haze.
        nebula(Palette.Blue, 0.65f + 0.06f * sin(t + 3f), 1.02f + 0.03f * cos(t), 0.50f, 0.12f)
    }
}

private fun DrawScope.nebula(color: Color, cx: Float, cy: Float, radiusFraction: Float, alpha: Float) {
    val center = Offset(size.width * cx, size.height * cy)
    val radius = size.maxDimension * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.35f), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
