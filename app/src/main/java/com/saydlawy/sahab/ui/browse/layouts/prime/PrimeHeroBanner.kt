package com.saydlawy.sahab.ui.browse.layouts.prime

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabIcon
import com.saydlawy.sahab.ui.theme.Palette
import kotlin.math.roundToInt

/**
 * Full-bleed cinematic backdrop. The artwork sits on the END side (left in RTL) and fades toward
 * the START side where the title block lives, then fades into the page background at the bottom.
 */
@Composable
internal fun PrimeBackdrop(details: FocusedPosterDetails?, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bg = Palette.CinematicBg
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val hasWideBackdrop = details != null &&
        details.backdropUrl.isNotBlank() &&
        details.backdropUrl != details.poster.image
    Box(modifier.fillMaxSize()) {
        if (details != null) {
            if (hasWideBackdrop) {
                RemoteImage(
                    url = details.backdropUrl,
                    fallbackUrl = details.poster.image,
                    fallbackText = details.poster.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RemoteImage(
                    url = details.poster.image,
                    fallbackText = details.poster.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(26.dp)
                        .graphicsLayer { alpha = 0.42f },
                )
                val posterShape = RoundedCornerShape(12.dp)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = if (compact) 12.dp else 28.dp)
                        .fillMaxWidth(if (compact) 0.34f else 0.22f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    RemoteImage(
                        url = details.poster.image,
                        fallbackText = details.poster.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(if (compact) 0.44f else 0.48f)
                            .clip(posterShape)
                            .border(1.dp, Color.White.copy(alpha = 0.24f), posterShape),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.14f)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                    ) {
                        RemoteImage(
                            url = details.poster.image,
                            fallbackText = details.poster.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleY = -1f
                                    alpha = 0.24f
                                },
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, bg))),
                        )
                    }
                }
            }
        }
        val sideStops = if (rtl) {
            arrayOf(
                0.0f to Color.Transparent,
                0.42f to bg.copy(alpha = 0.18f),
                0.70f to bg.copy(alpha = 0.78f),
                1.0f to bg.copy(alpha = 0.96f),
            )
        } else {
            arrayOf(
                0.0f to bg.copy(alpha = 0.96f),
                0.30f to bg.copy(alpha = 0.78f),
                0.58f to bg.copy(alpha = 0.18f),
                1.0f to Color.Transparent,
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (compact) {
                        Brush.verticalGradient(listOf(bg.copy(alpha = 0.30f), bg.copy(alpha = 0.75f)))
                    } else {
                        Brush.horizontalGradient(colorStops = sideStops)
                    },
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bg.copy(alpha = 0.55f),
                            0.18f to Color.Transparent,
                            0.52f to Color.Transparent,
                            0.78f to bg.copy(alpha = 0.72f),
                            1.0f to bg.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )
    }
}

internal val PrimeCyan = Color(0xFF00A8E1)

/** Title block: rank badge, big title, meta line, synopsis and the "included" line. */
@Composable
internal fun PrimeHeroInfo(
    details: FocusedPosterDetails?,
    rank: Int?,
    modifier: Modifier = Modifier,
) {
    val item = details ?: return
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SahabIcon(Ic.CheckCircle, null, Modifier.size(14.dp), PrimeCyan)
            val badgeText = if (rank != null) "#$rank في الأعلى تقييماً  •  متاح ضمن اشتراكك" else "متاح ضمن اشتراكك"
            Text(
                text = badgeText,
                color = PrimeCyan,
                fontSize = if (compact) 11.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = item.poster.name,
            color = Color.White,
            fontSize = if (compact) 19.sp else 24.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val ratingValue = item.rating.toDoubleOrNull()
        val formattedRuntime = formatHeroRuntime(item.runtime)
        val metaTail = listOf(
            item.year,
            item.genre.substringBefore(",").trim(),
            formattedRuntime,
        ).filter { it.isNotBlank() }.joinToString("  •  ")
        if (ratingValue != null || metaTail.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ratingValue != null && ratingValue > 0.0) {
                    val filled = (ratingValue / 2.0).roundToInt().coerceIn(0, 5)
                    Text(
                        text = "★".repeat(filled) + "☆".repeat(5 - filled),
                        color = Palette.Gold,
                        fontSize = if (compact) 12.sp else 14.sp,
                    )
                    Text(
                        text = item.rating,
                        color = Color.White,
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (metaTail.isNotBlank()) {
                    Text(
                        text = metaTail,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = if (compact) 12.sp else 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (item.plot.isNotBlank()) {
            Text(
                text = item.plot,
                color = Color.White.copy(alpha = 0.88f),
                fontSize = if (compact) 12.sp else 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

    }
}

/** Formats raw IPTV duration strings (e.g. "01:25:23.648" or "105") into clean Arabic runtime text. */
internal fun formatHeroRuntime(raw: String): String {
    val clean = raw.trim().substringBefore('.').trim()
    if (clean.isBlank()) return ""
    val parts = clean.split(":")
    if (parts.size == 3) {
        val h = parts[0].toIntOrNull() ?: return clean
        val m = parts[1].toIntOrNull() ?: return clean
        return when {
            h > 0 && m > 0 -> "${h}س ${m}د"
            h > 0 -> "${h}س"
            m > 0 -> "${m}د"
            else -> ""
        }
    }
    if (parts.size == 2) {
        val m = parts[0].toIntOrNull() ?: return clean
        return if (m > 0) "${m}د" else ""
    }
    clean.toIntOrNull()?.let { mins ->
        val h = mins / 60
        val m = mins % 60
        return when {
            h > 0 && m > 0 -> "${h}س ${m}د"
            h > 0 -> "${h}س"
            m > 0 -> "${m}د"
            else -> ""
        }
    }
    return clean
}
