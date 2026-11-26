package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaItem
import com.saydlawy.sahab.feature.instantwatch.data.InstantMediaType
import com.saydlawy.sahab.feature.instantwatch.data.TmdbCatalog
import java.util.Locale

@Composable
fun SearchResultsScreen(
    query: String,
    results: List<InstantMediaItem>,
    onSelect: (InstantMediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val config = LocalConfiguration.current
    val horizontalPad = if (config.screenWidthDp >= 840) 24.dp else 16.dp

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120))
            .padding(horizontal = horizontalPad, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            var backFocused by remember { mutableStateOf(false) }
            Surface(
                color = if (backFocused) Color(0xFF2563EB) else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .border(
                        width = if (backFocused) 2.dp else 1.dp,
                        color = if (backFocused) Color(0xFF38BDF8) else Color(0xFF334155),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .onFocusChanged { backFocused = it.isFocused }
                    .clickable(onClick = onBack),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "← رجوع",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "نتائج البحث (${results.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "اختر العمل المطلوب لـ: $query",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "لا توجد نتائج مطابقة لـ \"$query\"",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                items(results, key = { it.contentKey }) { item ->
                    SearchResultCard(item = item, onClick = { onSelect(item) })
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: InstantMediaItem,
    onClick: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val genreLabel = TmdbCatalog.primaryGenreLabel(item.genreIds)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isFocused) 1.02f else 1f)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF38BDF8) else Color(0xFF263449),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(74.dp)
                    .height(108.dp)
                    .clip(RoundedCornerShape(10.dp))
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
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = if (item.mediaType == InstantMediaType.TV) Color(0xDD1D4ED8) else Color(0xCCFBBF24),
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = if (item.mediaType == InstantMediaType.TV) "مسلسل" else "فيلم",
                            color = if (item.mediaType == InstantMediaType.TV) Color.White else Color(0xFF0B1120),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    item.rating?.let { score ->
                        Text(
                            text = "★ ${String.format(Locale.US, "%.1f", score)}",
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    item.releaseYear?.let { year ->
                        Text(
                            text = "$year",
                            color = Color(0xFFCBD5E1),
                            fontSize = 11.sp,
                        )
                    }
                    if (!genreLabel.isNullOrBlank()) {
                        Text(
                            text = "• $genreLabel",
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (item.overview.isNotBlank()) {
                    Text(
                        text = item.overview,
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
