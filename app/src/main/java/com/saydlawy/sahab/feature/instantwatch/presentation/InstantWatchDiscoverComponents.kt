package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType
import com.saydlawy.sahab.feature.instantwatch.local.InstantWatchHistoryEntity
import java.util.Locale

@Composable
internal fun SectionHeader(
    title: String,
    horizontalPad: Dp,
    subtitle: String? = null,
    accentColor: Color = Color(0xFFFBBF24),
) {
    Column(modifier = Modifier.padding(horizontal = horizontalPad)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(accentColor),
            )
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = 12.dp, top = 2.dp),
            )
        }
    }
}

@Composable
internal fun MediaCarouselSection(
    title: String,
    items: List<InstantMediaItem>,
    horizontalPad: Dp,
    cardWidth: Dp,
    cardImageHeight: Dp,
    onSelect: (InstantMediaItem) -> Unit,
    onFocusItem: (InstantMediaItem) -> Unit,
    subtitle: String? = null,
    showRank: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = title,
            horizontalPad = horizontalPad,
            subtitle = subtitle,
            accentColor = if (showRank) Color(0xFF38BDF8) else Color(0xFFFBBF24),
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { _, it -> "${title}:${it.contentKey}" }) { index, item ->
                MediaPosterCard(
                    item = item,
                    cardWidth = cardWidth,
                    imageHeight = cardImageHeight,
                    rank = if (showRank && index < 10) index + 1 else null,
                    onClick = { onSelect(item) },
                    onFocused = { onFocusItem(item) },
                )
            }
        }
    }
}

@Composable
internal fun MediaPosterCard(
    item: InstantMediaItem,
    cardWidth: Dp,
    imageHeight: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    rank: Int? = null,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .width(cardWidth)
            .scale(if (isFocused) 1.05f else 1f)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF38BDF8) else Color(0xFF263449),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .focusable()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(imageHeight)
                    .background(Color(0xFF0F172A)),
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xEE162033)),
                            ),
                        ),
                )
                if (rank != null) {
                    Surface(
                        color = Color(0xE6FBBF24),
                        shape = RoundedCornerShape(bottomEnd = 10.dp),
                        modifier = Modifier.align(Alignment.TopStart),
                    ) {
                        Text(
                            text = "#$rank",
                            color = Color(0xFF0B1120),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Surface(
                    color = if (item.mediaType == InstantMediaType.TV) Color(0xDD1D4ED8) else Color(0xCC0F172A),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                ) {
                    Text(
                        text = if (item.mediaType == InstantMediaType.TV) "مسلسل" else "فيلم",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.rating?.let { score ->
                        Text(
                            text = "★ ${String.format(Locale.US, "%.1f", score)}",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    item.releaseYear?.let { year ->
                        Text(
                            text = "$year",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "التفاصيل والمشاهدة",
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(vertical = 5.dp, horizontal = 8.dp),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun RecentWatchCard(
    entry: InstantWatchHistoryEntity,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .width(136.dp)
            .scale(if (isFocused) 1.05f else 1f)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF38BDF8) else Color(0xFF1E293B),
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(166.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A)),
            ) {
                if (!entry.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = entry.posterUrl,
                        contentDescription = entry.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = entry.title,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.seasonNumber != null && entry.episodeNumber != null) {
                    "متابعة • م${entry.seasonNumber} ح${entry.episodeNumber}"
                } else {
                    "متابعة المشاهدة"
                },
                color = Color(0xFF60A5FA),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

internal enum class InstantQuickFilter(
    val label: String,
    val movieGenreId: Int? = null,
    val tvGenreId: Int? = null,
) {
    ALL("الكل"),
    MOVIES("أفلام"),
    TV("مسلسلات"),
    ACTION("أكشن", movieGenreId = 28, tvGenreId = 10759),
    DRAMA("دراما", movieGenreId = 18, tvGenreId = 18),
    COMEDY("كوميدي", movieGenreId = 35, tvGenreId = 35),
    ANIME("أنمي", movieGenreId = 16, tvGenreId = 16),
    TOP_RATED("أعلى تقييم"),
}

@Composable
internal fun InstantWatchTopBar(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSearch: () -> Unit,
    onPasteAndSearch: () -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    isLoading: Boolean,
    statusMessage: String?,
    selectedFilter: InstantQuickFilter,
    onFilterSelected: (InstantQuickFilter) -> Unit,
    focusedItem: InstantMediaItem?,
    isLandscape: Boolean,
    isTv: Boolean,
    horizontalPad: Dp,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(horizontal = horizontalPad, vertical = if (isLandscape && !isTv) 8.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isLandscape && !isTv) 6.dp else 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "المشاهدة الفورية",
                    color = Color.White,
                    style = if (isLandscape && !isTv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Surface(color = Color(0xFF064E3B), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = "متصل",
                        color = Color(0xFF34D399),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRefresh) { Text("تحديث", color = Color(0xFF94A3B8)) }
                TextButton(onClick = onBack) { Text("رجوع", color = Color(0xFF60A5FA)) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(
                        text = "اسم فيلم/مسلسل، رابط TMDB أو IMDb، أو رقم...",
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                trailingIcon = {
                    if (inputText.isNotEmpty()) {
                        TextButton(onClick = { onInputChange("") }) { Text("✕", color = Color(0xFF94A3B8)) }
                    }
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search,
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { onSearch() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color(0xFF38BDF8),
                    focusedContainerColor = Color(0xFF0B1120),
                    unfocusedContainerColor = Color(0xFF0B1120),
                ),
            )
            OutlinedButton(
                onClick = onPasteAndSearch,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            ) {
                Text("لصق", color = Color(0xFFE2E8F0), fontWeight = FontWeight.Medium)
            }
            Button(
                onClick = onSearch,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("بحث", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (!statusMessage.isNullOrBlank()) {
            Text(text = statusMessage, color = Color(0xFFFCA5A5), style = MaterialTheme.typography.bodySmall)
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(InstantQuickFilter.entries) { filter ->
                val selected = selectedFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(filter.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF2563EB),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color(0xFFCBD5E1),
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selected,
                        borderColor = Color(0xFF334155),
                        selectedBorderColor = Color(0xFF60A5FA),
                    ),
                )
            }
        }

        if (isTv && focusedItem != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = focusedItem.title,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (focusedItem.mediaType == InstantMediaType.TV) "مسلسل" else "فيلم",
                            color = Color(0xFF38BDF8),
                            style = MaterialTheme.typography.labelSmall,
                        )
                        focusedItem.releaseYear?.let {
                            Text("• $it", color = Color(0xFF94A3B8), style = MaterialTheme.typography.labelSmall)
                        }
                        focusedItem.rating?.let {
                            Text(
                                text = "• ★ ${String.format(Locale.US, "%.1f", it)}",
                                color = Color(0xFFFBBF24),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    if (focusedItem.overview.isNotBlank()) {
                        Text(
                            text = focusedItem.overview,
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
