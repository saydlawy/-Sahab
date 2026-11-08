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

/** Soft colour clouds drifting slowly behind every screen. Static in light mode. */
@Composable
fun DreamyBackground() {
    if (LocalLightEffects.current) {
        CloudCanvas(phase = { 0f })
    } else {
        val transition = rememberInfiniteTransition(label = "clouds")
        val phase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(70_000, easing = LinearEasing), RepeatMode.Restart),
            label = "phase",
        )
        CloudCanvas(phase = { phase })
    }
}

@Composable
private fun CloudCanvas(phase: () -> Float) {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Brush.verticalGradient(listOf(Palette.BgTop, Palette.Bg), startY = 0f, endY = size.height))
        val t = phase() * 2f * PI.toFloat()
        cloud(Palette.Violet, 0.20f + 0.07f * sin(t), 0.18f + 0.06f * cos(t), 0.62f, 0.50f)
        cloud(Palette.Pink, 0.82f + 0.06f * cos(t), 0.30f + 0.07f * sin(t), 0.50f, 0.34f)
        cloud(Palette.Cyan, 0.30f + 0.08f * cos(t + 2f), 0.88f + 0.05f * sin(t + 1f), 0.55f, 0.28f)
        cloud(Palette.Peach, 0.90f + 0.05f * sin(t + 3f), 0.90f + 0.04f * cos(t), 0.42f, 0.20f)
    }
}

private fun DrawScope.cloud(color: Color, cx: Float, cy: Float, radiusFraction: Float, alpha: Float) {
    val center = Offset(size.width * cx, size.height * cy)
    val radius = size.maxDimension * radiusFraction
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
