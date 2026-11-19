package com.saydlawy.sahab.ui.browse.layouts.netflix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.PosterCard
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette

@Composable
internal fun NetflixTop10Row(
    screenKey: String,
    top10: List<Poster>,
    onOpenPoster: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
    onFocusedPoster: (Poster) -> Unit = {},
) {
    if (top10.isEmpty()) return
    val rowState = rememberRestorableLazyListState("${screenKey}_netflix_top10")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DotTitle("الأكثر تميزاً (Top 10)", Palette.LiveBadgeRed, fontSize = 17.sp)
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(top10, key = { index, p -> "top10_${index}_${p.id}" }) { index, poster ->
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(Modifier.padding(bottom = 8.dp)) {
                        Text(
                            text = "${index + 1}",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    PosterCard(
                        title = poster.name,
                        imageUrl = poster.image,
                        badge = poster.rating,
                        quality = qualityOf(poster.name),
                        subtitle = poster.year,
                        onClick = { onOpenPoster(poster) },
                        onLongClick = { onToggleFavorite(poster) },
                        onFocused = { onFocusedPoster(poster) },
                        modifier = Modifier
                            .width(120.dp)
                            .restorableFocus(screenKey, "top10_${index}_${poster.id}", isDefaultEntry = false),
                    )
                }
            }
        }
    }
}
