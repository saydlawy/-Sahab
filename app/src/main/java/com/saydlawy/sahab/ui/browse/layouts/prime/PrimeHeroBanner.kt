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
import androidx.compose.ui.Modifier
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
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(if (compact) 1f else 0.74f)
                .fillMaxHeight(if (compact) 0.46f else 0.76f),
        ) {
            if (details != null) {
                RemoteImage(
                    url = details.backdropUrl.ifBlank { details.poster.image },
                    fallbackUrl = details.poster.image,
                    fallbackText = details.poster.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Physical gradient: dark on the title (start) side, clear on the artwork side.
            val colors = if (rtl) {
                listOf(Color.Transparent, bg.copy(alpha = 0.55f), bg)
            } else {
                listOf(bg, bg.copy(alpha = 0.55f), Color.Transparent)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(if (compact) listOf(bg.copy(alpha = 0.25f), bg.copy(alpha = 0.25f)) else colors)),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bg.copy(alpha = 0.35f),
                            0.35f to Color.Transparent,
                            if (compact) 0.46f to bg else 0.62f to bg.copy(alpha = 0.92f),
                            1.0f to bg,
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
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
        if (rank != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "#$rank في الأعلى تقييماً",
                    color = PrimeCyan,
                    fontSize = if (compact) 12.sp else 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                SahabIcon(Ic.CheckCircle, null, Modifier.size(15.dp), PrimeCyan)
            }
        }
        Text(
            text = item.poster.name,
            color = Color.White,
            fontSize = if (compact) 22.sp else 30.sp,
            fontWeight = FontWeight.Black,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val ratingValue = item.rating.toDoubleOrNull()
        val metaTail = listOf(
            item.year,
            item.genre.substringBefore(",").trim(),
            item.runtime,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SahabIcon(Ic.CheckCircle, null, Modifier.size(16.dp), PrimeCyan)
            Text(
                text = "متاح ضمن اشتراكك",
                color = Color.White,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
