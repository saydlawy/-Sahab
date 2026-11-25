package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.InstantSeasonSummary

@Composable
fun EpisodePickerScreen(
    series: InstantMediaItem,
    onSelectEpisode: (seasonNumber: Int, episodeNumber: Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seasons = remember(series.seasons) {
        series.seasons.ifEmpty {
            listOf(InstantSeasonSummary(seasonNumber = 1, name = "الموسم 1", episodeCount = 24))
        }
    }
    var selectedSeasonNumber by remember(seasons) {
        mutableIntStateOf(seasons.first().seasonNumber)
    }
    val currentSeason = seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber } ?: seasons.first()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = series.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "اختر الموسم والحلقة للمشاهدة",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                )
            }
            TextButton(onClick = onBack) {
                Text("رجوع", color = Color(0xFF60A5FA))
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(seasons, key = { it.seasonNumber }) { season ->
                val selected = season.seasonNumber == selectedSeasonNumber
                FilterChip(
                    selected = selected,
                    onClick = { selectedSeasonNumber = season.seasonNumber },
                    label = {
                        Text(
                            text = "الموسم ${season.seasonNumber}",
                            color = if (selected) Color.White else Color(0xFFCBD5E1),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        containerColor = Color(0xFF1E293B),
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            items(currentSeason.episodeCount) { index ->
                val episodeNum = index + 1
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .clickable { onSelectEpisode(currentSeason.seasonNumber, episodeNum) }
                        .padding(vertical = 16.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "الحلقة $episodeNum",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
