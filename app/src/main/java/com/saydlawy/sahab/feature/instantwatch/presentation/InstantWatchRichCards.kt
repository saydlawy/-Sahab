package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.compose.foundation.BorderStroke
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
import com.saydlawy.sahab.feature.instantwatch.data.*
import java.util.Locale

@Composable
internal fun BackdropMediaCarouselSection(
    title: String,
    subtitle: String,
    items: List<InstantMediaItem>,
    horizontalPad: Dp,
    isTv: Boolean,
    onSelect: (InstantMediaItem) -> Unit,
    onFocusItem: (InstantMediaItem) -> Unit,
) {
    val cardW = if (isTv) 276.dp else 236.dp
    val cardH = if (isTv) 156.dp else 134.dp
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = title, horizontalPad = horizontalPad, subtitle = subtitle, accentColor = Color(0xFF38BDF8))
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { "bd:${title}:${it.contentKey}" }) { item ->
                BackdropMediaCard(item, cardW, cardH, { onSelect(item) }, { onFocusItem(item) })
            }
        }
    }
}

@Composable
private fun BackdropMediaCard(
    item: InstantMediaItem,
    cardWidth: Dp,
    imageHeight: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val genreLabel = TmdbCatalog.primaryGenreLabel(item.genreIds)
    Card(
        modifier = Modifier
            .width(cardWidth)
            .scale(if (isFocused) 1.04f else 1f)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF38BDF8) else Color(0xFF263449),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(imageHeight).background(Color(0xFF0F172A))) {
            val img = item.backdropUrl ?: item.posterUrl
            if (!img.isNullOrBlank()) {
                AsyncImage(model = img, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x220B1120), Color(0x990B1120), Color(0xF20B1120))),
                ),
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!genreLabel.isNullOrBlank()) {
                    Surface(color = Color(0xCC1E293B), shape = RoundedCornerShape(6.dp)) {
                        Text(genreLabel, color = Color(0xFFE2E8F0), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
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
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    item.rating?.let {
                        Text("★ ${String.format(Locale.US, "%.1f", it)}", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    item.releaseYear?.let {
                        Text("$it", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
internal fun TopTenDetailedCarouselSection(
    title: String,
    subtitle: String,
    items: List<InstantMediaItem>,
    horizontalPad: Dp,
    isTv: Boolean,
    onSelect: (InstantMediaItem) -> Unit,
    onFocusItem: (InstantMediaItem) -> Unit,
) {
    val cardW = if (isTv) 320.dp else 284.dp
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title = title, horizontalPad = horizontalPad, subtitle = subtitle, accentColor = Color(0xFFFBBF24))
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items.take(10), key = { _, it -> "top10:${it.contentKey}" }) { idx, item ->
                TopTenDetailedCard(item, idx + 1, cardW, { onSelect(item) }, { onFocusItem(item) })
            }
        }
    }
}

@Composable
private fun TopTenDetailedCard(
    item: InstantMediaItem,
    rank: Int,
    cardWidth: Dp,
    onClick: () -> Unit,
    onFocused: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val genreLabel = TmdbCatalog.primaryGenreLabel(item.genreIds)
    Card(
        modifier = Modifier
            .width(cardWidth)
            .scale(if (isFocused) 1.03f else 1f)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color(0xFF38BDF8) else Color(0xFF263449),
                shape = RoundedCornerShape(14.dp),
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
    ) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.width(78.dp).height(112.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0F172A)),
            ) {
                if (!item.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Surface(color = Color(0xF0FBBF24), shape = RoundedCornerShape(bottomEnd = 8.dp), modifier = Modifier.align(Alignment.TopStart)) {
                    Text("#$rank", color = Color(0xFF0B1120), fontSize = 11.sp, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    item.rating?.let {
                        Text("★ ${String.format(Locale.US, "%.1f", it)}", color = Color(0xFFFBBF24), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (item.mediaType == InstantMediaType.TV) "مسلسل" else "فيلم", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (!genreLabel.isNullOrBlank()) {
                        Text("• $genreLabel", color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1)
                    }
                }
                item.releaseYear?.let {
                    Text("إصدار $it", color = Color(0xFF64748B), fontSize = 10.sp)
                }
                if (item.overview.isNotBlank()) {
                    Text(item.overview, color = Color(0xFFCBD5E1), fontSize = 11.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun CollectionsCarouselSection(
    collections: List<InstantCollectionTile>,
    horizontalPad: Dp,
    onSelect: (InstantCollectionTile) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "سلاسل الأفلام الشهيرة",
            subtitle = "شاهد كل أجزاء السلسلة بالترتيب",
            horizontalPad = horizontalPad,
            accentColor = Color(0xFFFBBF24),
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(collections, key = { "col:${it.collectionId}" }) { col ->
                var isFocused by remember { mutableStateOf(false) }
                val accent = Color(col.accentHex)
                Card(
                    modifier = Modifier
                        .width(210.dp)
                        .height(92.dp)
                        .scale(if (isFocused) 1.04f else 1f)
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) Color(0xFF38BDF8) else accent.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSelect(col) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(accent.copy(alpha = 0.28f), Color(0xFF162033)),
                                ),
                            )
                            .padding(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.CenterStart),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Surface(color = accent.copy(alpha = 0.22f), shape = RoundedCornerShape(6.dp)) {
                                Text(
                                    text = "سلسلة أفلام",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Text(
                                text = col.title,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = col.subtitle,
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RegionsCarouselSection(
    regions: List<InstantRegionTile>,
    horizontalPad: Dp,
    onSelect: (InstantRegionTile) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "تصفّح حسب الدولة واللغة",
            subtitle = "أفلام ومسلسلات من حول العالم",
            horizontalPad = horizontalPad,
            accentColor = Color(0xFF38BDF8),
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(regions, key = { "reg:${it.id}" }) { reg ->
                var isFocused by remember { mutableStateOf(false) }
                val accent = Color(reg.accentHex)
                Card(
                    modifier = Modifier
                        .width(164.dp)
                        .height(78.dp)
                        .scale(if (isFocused) 1.04f else 1f)
                        .border(
                            width = if (isFocused) 2.dp else 1.dp,
                            color = if (isFocused) Color(0xFF38BDF8) else accent.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSelect(reg) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.25f), Color(0xFF162033))))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(reg.title, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                        Text(reg.subtitle, color = Color(0xFFCBD5E1), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
internal fun GenresCarouselSection(
    genres: List<InstantGenreTile>,
    horizontalPad: Dp,
    onSelect: (InstantGenreTile) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeader(
            title = "تصفّح حسب التصنيف",
            subtitle = "أفلام ومسلسلات حسب نوعك المفضل",
            horizontalPad = horizontalPad,
            accentColor = Color(0xFFFBBF24),
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(genres, key = { "gen:${it.id}" }) { genre ->
                var isFocused by remember { mutableStateOf(false) }
                val accent = Color(genre.accentHex)
                Surface(
                    modifier = Modifier
                        .scale(if (isFocused) 1.05f else 1f)
                        .onFocusChanged { isFocused = it.isFocused }
                        .clickable { onSelect(genre) },
                    color = accent.copy(alpha = 0.20f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(if (isFocused) 2.dp else 1.dp, if (isFocused) Color(0xFF38BDF8) else accent.copy(alpha = 0.6f)),
                ) {
                    Text(
                        text = genre.title,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}
