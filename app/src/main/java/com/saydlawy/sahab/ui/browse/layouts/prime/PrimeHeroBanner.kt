package com.saydlawy.sahab.ui.browse.layouts.prime

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

@Composable
internal fun PrimeHeroBanner(
    details: FocusedPosterDetails?,
    isFavorite: Boolean,
    onOpen: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
) {
    val item = details ?: return
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val height = if (compact) 180.dp else 220.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(20.dp))
            .background(Palette.CardSurface)
            .border(1.dp, Palette.CardBorderIdle, RoundedCornerShape(20.dp)),
    ) {
        RemoteImage(
            url = item.backdropUrl.ifBlank { item.poster.image },
            fallbackUrl = item.poster.image,
            fallbackText = item.poster.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Palette.CinematicBg.copy(alpha = 0.94f),
                            0.55f to Palette.CinematicBg.copy(alpha = 0.72f),
                            1.0f to Palette.CinematicBg.copy(alpha = 0.25f),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(if (compact) 0.92f else 0.62f)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (item.rating.isNotBlank()) Tag("★ ${item.rating}", Palette.Gold)
                if (item.year.isNotBlank()) Tag(item.year, Palette.FocusCyan)
                if (item.genre.isNotBlank()) Tag(item.genre.substringBefore(","), Palette.Glow)
            }
            Text(
                text = item.poster.name,
                color = Color.White,
                fontSize = if (compact) 18.sp else 24.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.plot.isNotBlank()) {
                Text(
                    text = item.plot,
                    color = Palette.TextDim,
                    fontSize = 12.sp,
                    maxLines = if (compact) 2 else 3,
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
                    text = if (isFavorite) "في المفضلة" else Ar.favorites,
                    icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                    onClick = { onToggleFavorite(item.poster) },
                )
            }
        }
    }
}
