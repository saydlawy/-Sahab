package com.saydlawy.sahab.ui.browse.layouts.netflix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.ui.browse.layouts.prime.formatHeroRuntime
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.TrailerAutoPlayBox
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

private val NetflixRed = Color(0xFFE50914)
private val NetflixMatchGreen = Color(0xFF46D369)

/** Full-bleed Netflix TV Hero backdrop fading toward the START/bottom title block. */
@Composable
internal fun NetflixBackdrop(details: FocusedPosterDetails?, modifier: Modifier = Modifier) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bg = Palette.CinematicBg
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(if (compact) 1f else 0.72f)
                .fillMaxHeight(if (compact) 0.44f else 0.70f),
        ) {
            if (details != null) {
                TrailerAutoPlayBox(
                    trailerUrl = details.trailerUrl,
                    posterUrl = details.backdropUrl.ifBlank { details.poster.image },
                    title = details.poster.name,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            val sideColors = if (rtl) {
                listOf(Color.Transparent, bg.copy(alpha = 0.60f), bg)
            } else {
                listOf(bg, bg.copy(alpha = 0.60f), Color.Transparent)
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            if (compact) listOf(bg.copy(alpha = 0.30f), bg.copy(alpha = 0.30f)) else sideColors,
                        ),
                    ),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bg.copy(alpha = 0.32f),
                            0.32f to Color.Transparent,
                            if (compact) 0.44f to bg else 0.60f to bg.copy(alpha = 0.94f),
                            1.0f to bg,
                        ),
                    ),
                ),
        )
    }
}

@Composable
internal fun NetflixHeroInfo(
    details: FocusedPosterDetails?,
    series: Boolean,
    isFavorite: Boolean,
    onOpen: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = details ?: return
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NetflixRed)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            ) {
                Text("N", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            Text(
                text = if (series) "سلسلة مميزة" else "فيلم سينمائي",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        Text(
            text = item.poster.name,
            color = Color.White,
            fontSize = if (compact) 20.sp else 26.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val metaParts = listOf(
            item.year,
            item.genre.substringBefore(",").trim(),
            formatHeroRuntime(item.runtime),
        ).filter { it.isNotBlank() }
        if (item.rating.isNotBlank() || metaParts.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (item.rating.isNotBlank()) {
                    Text(
                        text = "تقييم ${item.rating}",
                        color = NetflixMatchGreen,
                        fontSize = if (compact) 12.sp else 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("  •  "),
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = Ar.details,
                icon = Ic.Play,
                primary = true,
                onClick = { onOpen(item.poster) },
            )
            GlassButton(
                text = if (isFavorite) "في قائمتي" else "+ قائمتي",
                icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                onClick = { onToggleFavorite(item.poster) },
            )
        }
    }
}
