package com.saydlawy.sahab.ui.components

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.ui.theme.LocalThemeCustomization
import com.saydlawy.sahab.ui.theme.Palette

/** Shared icon entry point so theme scale and container treatment apply consistently. */
@Composable
fun SahabIcon(
    imageVector: ImageVector,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current,
) {
    val customization = LocalThemeCustomization.current
    val cornerRadius = if (customization.iconContainerShape == "square") 3.dp else 8.dp
    val styled = when (customization.iconStyle) {
        "filled" -> modifier.drawBehind {
            val radius = if (customization.iconContainerShape == "circle") size.minDimension / 2 else cornerRadius.toPx()
            drawRoundRect(color = Palette.Glass, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        }
        "outlined" -> modifier.drawBehind {
            val radius = if (customization.iconContainerShape == "circle") size.minDimension / 2 else cornerRadius.toPx()
            drawRoundRect(color = Palette.GlassBorder, cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius), style = Stroke(width = 1.dp.toPx()))
        }
        else -> modifier
    }
    val scale = customization.iconScale.coerceIn(0.7f, 1.4f)
    val scaledModifier = if (scale < 0.99f || scale > 1.01f) {
        styled.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    } else {
        styled
    }
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = scaledModifier,
        tint = tint,
    )
}
