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
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.LocalLightEffects
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.TrailerAutoPlayBox
import com.saydlawy.sahab.ui.components.SahabIcon
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.RatingSanitizer
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
    val isTv = LocalIsTv.current
    val lightMode = LocalLightEffects.current
    val hasWideBackdrop = details != null &&
        details.backdropUrl.isNotBlank() &&
        details.backdropUrl != details.poster.image
    Box(modifier.fillMaxSize()) {
        if (details != null) {
            val primaryImageUrl = if (hasWideBackdrop) details.backdropUrl else details.poster.image
            if (!isTv && !lightMode && details.trailerUrl.isNotBlank()) {
                TrailerAutoPlayBox(
                    trailerUrl = details.trailerUrl,
                    posterUrl = primaryImageUrl,
                    title = details.poster.name,
                    autoPlayDelayMs = 2_000L,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                RemoteImage(
                    url = primaryImageUrl,
                    fallbackText = details.poster.name,
                    targetWidthPx = 960,
                    targetHeightPx = 540,
                    modifier = Modifier.fillMaxSize(),
                )
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

/** Title block: rank badge (1..10 only), big title, and real metadata line. */
@Composable
internal fun PrimeHeroInfo(
    details: FocusedPosterDetails?,
    rank: Int?,
    modifier: Modifier = Modifier,
) {
    val item = details ?: return
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val validRank = rank?.takeIf { it in 1..10 }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 4.dp)) {
        if (validRank != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "#$validRank",
                    color = PrimeCyan,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = item.poster.name,
            color = Color.White,
            fontSize = if (compact) 19.sp else 24.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val ratingValue = RatingSanitizer.parseValid(item.rating)
        val formattedRating = RatingSanitizer.format(item.rating)
        val formattedRuntime = formatHeroRuntime(item.runtime)
        val realGenres = item.genre
            .split(",", "/", "،", "•")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("، ")
        val metaTail = listOf(
            item.year.trim(),
            realGenres,
            formattedRuntime.trim(),
        ).filter { it.isNotBlank() }.joinToString("  •  ")
        if (ratingValue != null || metaTail.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ratingValue != null && formattedRating.isNotBlank()) {
                    val filled = (ratingValue / 2.0).roundToInt().coerceIn(0, 5)
                    Text(
                        text = "★".repeat(filled) + "☆".repeat(5 - filled),
                        color = Palette.Gold,
                        fontSize = if (compact) 12.sp else 14.sp,
                    )
                    Text(
                        text = formattedRating,
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
