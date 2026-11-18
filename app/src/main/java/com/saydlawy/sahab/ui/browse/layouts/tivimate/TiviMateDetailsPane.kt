package com.saydlawy.sahab.ui.browse.layouts.tivimate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

@Composable
internal fun TiviMateDetailsPane(
    details: FocusedPosterDetails?,
    isFavorite: Boolean,
    onOpen: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        strong = true,
        padding = 16.dp,
    ) {
        if (details == null) {
            Text(Ar.pickTitle, color = Palette.TextDim, fontSize = 14.sp)
            return@GlassPanel
        }
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            RemoteImage(
                url = details.backdropUrl.ifBlank { details.poster.image },
                fallbackUrl = details.poster.image,
                fallbackText = details.poster.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Text(
                text = details.poster.name,
                color = Palette.Text,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (details.rating.isNotBlank()) Tag("★ ${details.rating}", Palette.Gold)
                if (details.year.isNotBlank()) Tag(details.year, Palette.FocusCyan)
                if (details.runtime.isNotBlank()) Tag(details.runtime, Palette.Glow)
            }
            if (details.genre.isNotBlank()) {
                Text(details.genre, color = Palette.Glow, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (details.plot.isNotBlank()) {
                Text(details.plot, color = Palette.TextDim, fontSize = 12.5.sp, lineHeight = 19.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                GlassButton(
                    text = Ar.details,
                    icon = Ic.Play,
                    primary = true,
                    onClick = { onOpen(details.poster) },
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = if (isFavorite) "مفضلة" else Ar.favorites,
                    icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                    onClick = { onToggleFavorite(details.poster) },
                )
            }
        }
    }
}
