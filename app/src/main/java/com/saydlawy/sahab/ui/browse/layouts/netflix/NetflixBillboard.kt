package com.saydlawy.sahab.ui.browse.layouts.netflix

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassIconButton
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

@Composable
internal fun NetflixBillboard(
    featured: List<Poster>,
    favoriteIds: Set<String>,
    onOpen: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
) {
    if (featured.isEmpty()) return
    var index by remember(featured) { mutableIntStateOf(0) }
    val active = featured[index.coerceIn(0, featured.lastIndex)]
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 190.dp else 240.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Palette.CardSurface)
            .border(1.dp, Palette.CardBorderIdle, RoundedCornerShape(22.dp)),
    ) {
        RemoteImage(
            url = active.image,
            fallbackText = active.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Palette.CinematicBg.copy(alpha = 0.15f),
                            0.45f to Palette.CinematicBg.copy(alpha = 0.55f),
                            1.0f to Palette.CinematicBg.copy(alpha = 0.96f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag("مميز اليوم", Palette.LiveBadgeRed, filled = true)
                if (active.rating.isNotBlank()) Tag("★ ${active.rating}", Palette.Gold)
                if (active.year.isNotBlank()) Tag(active.year, Palette.FocusCyan)
            }
            Text(
                text = active.name,
                color = Color.White,
                fontSize = if (compact) 19.sp else 25.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        text = Ar.details,
                        icon = Ic.Play,
                        primary = true,
                        onClick = { onOpen(active) },
                    )
                    GlassButton(
                        text = if (active.id in favoriteIds) "في المفضلة" else Ar.favorites,
                        icon = if (active.id in favoriteIds) Ic.Heart else Ic.HeartOutline,
                        onClick = { onToggleFavorite(active) },
                    )
                }
                if (featured.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassIconButton(
                            icon = Ic.Back,
                            description = Ar.previous,
                            size = 40.dp,
                            onClick = { index = if (index <= 0) featured.lastIndex else index - 1 },
                        )
                        GlassIconButton(
                            icon = Ic.Play,
                            description = Ar.next,
                            size = 40.dp,
                            onClick = { index = (index + 1) % featured.size },
                        )
                    }
                }
            }
        }
    }
}
