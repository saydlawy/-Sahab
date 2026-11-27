package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.saydlawy.sahab.feature.instantwatch.data.*
import com.saydlawy.sahab.feature.instantwatch.local.InstantCustomWatchlist

internal sealed class InstantBrowseTarget {
    data class Person(val personId: Int, val name: String) : InstantBrowseTarget()
    data class Genre(val tile: InstantGenreTile) : InstantBrowseTarget()
    data class Region(val tile: InstantRegionTile) : InstantBrowseTarget()
    data class Collection(val tile: InstantCollectionTile) : InstantBrowseTarget()
    data class CustomList(val watchlist: InstantCustomWatchlist) : InstantBrowseTarget()
}

private enum class BrowseTypeTab(val label: String) { ALL("الكل"), MOVIES("أفلام"), TV("مسلسلات") }
private enum class BrowseSortOption(val label: String, val apiSort: String) {
    POPULAR("الأشهر", "popularity.desc"),
    TOP_RATED("الأعلى تقييماً", "vote_average.desc"),
    NEWEST("الأحدث", "primary_release_date.desc"),
}

@Composable
internal fun InstantBrowseScreen(
    target: InstantBrowseTarget,
    tmdbRepository: TmdbRepository,
    onSelectItem: (InstantMediaItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val config = LocalConfiguration.current
    val isTv = config.screenWidthDp >= 840
    val horizontalPad = if (isTv) 24.dp else 14.dp
    val cardWidth = if (isTv) 154.dp else 132.dp
    val cardHeight = if (isTv) 210.dp else 182.dp

    var isLoading by remember(target) { mutableStateOf(true) }
    var rawItems by remember(target) { mutableStateOf<List<InstantMediaItem>>(emptyList()) }
    var personProfile by remember(target) { mutableStateOf<InstantPersonProfile?>(null) }
    var collectionDetails by remember(target) { mutableStateOf<InstantCollectionDetails?>(null) }
    var typeTab by remember(target) { mutableStateOf(BrowseTypeTab.ALL) }
    var sortOption by remember(target) { mutableStateOf(BrowseSortOption.POPULAR) }

    val supportsSort = target is InstantBrowseTarget.Genre || target is InstantBrowseTarget.Region
    val supportsTypeTabs = target !is InstantBrowseTarget.Collection

    LaunchedEffect(target, sortOption) {
        isLoading = true
        when (target) {
            is InstantBrowseTarget.Person -> {
                val p = tmdbRepository.getPersonProfileAndCredits(target.personId)
                personProfile = p
                rawItems = p?.credits.orEmpty()
            }
            is InstantBrowseTarget.Genre -> {
                rawItems = tmdbRepository.discoverByGenreBoth(
                    movieGenreId = target.tile.movieGenreId,
                    tvGenreId = target.tile.tvGenreId,
                    sortBy = sortOption.apiSort,
                )
            }
            is InstantBrowseTarget.Region -> {
                rawItems = tmdbRepository.discoverByRegion(
                    language = target.tile.originalLanguage,
                    country = target.tile.originCountry,
                    sortBy = sortOption.apiSort,
                )
            }
            is InstantBrowseTarget.Collection -> {
                val c = tmdbRepository.getCollectionDetails(target.tile.collectionId)
                collectionDetails = c
                rawItems = c?.parts.orEmpty()
            }
            is InstantBrowseTarget.CustomList -> {
                rawItems = target.watchlist.items.map { e ->
                    InstantMediaItem(
                        tmdbId = e.tmdbId,
                        mediaType = if (e.mediaType == "tv") InstantMediaType.TV else InstantMediaType.MOVIE,
                        title = e.title,
                        originalTitle = e.originalTitle,
                        releaseYear = e.releaseYear,
                        posterUrl = e.posterUrl,
                        rating = e.rating,
                    )
                }
            }
        }
        isLoading = false
    }

    val filteredItems = remember(rawItems, typeTab) {
        when (typeTab) {
            BrowseTypeTab.ALL -> rawItems
            BrowseTypeTab.MOVIES -> rawItems.filter { it.mediaType == InstantMediaType.MOVIE }
            BrowseTypeTab.TV -> rawItems.filter { it.mediaType == InstantMediaType.TV }
        }
    }

    val headerTitle = when (target) {
        is InstantBrowseTarget.Person -> personProfile?.name?.ifBlank { target.name } ?: target.name
        is InstantBrowseTarget.Genre -> "تصنيف: ${target.tile.title}"
        is InstantBrowseTarget.Region -> target.tile.title
        is InstantBrowseTarget.Collection -> collectionDetails?.name?.ifBlank { target.tile.title } ?: target.tile.title
        is InstantBrowseTarget.CustomList -> target.watchlist.name
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B1120)),
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0F172A))
                .padding(horizontal = horizontalPad, vertical = 12.dp),
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
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
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
                    text = headerTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${filteredItems.size} عمل",
                    color = Color(0xFF94A3B8),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Filter & Sort Bar
        if (supportsTypeTabs || supportsSort) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(bottom = 10.dp),
                contentPadding = PaddingValues(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (supportsTypeTabs) {
                    items(BrowseTypeTab.entries.size) { idx ->
                        val tab = BrowseTypeTab.entries[idx]
                        FilterChip(
                            selected = typeTab == tab,
                            onClick = { typeTab = tab },
                            label = { Text(tab.label, fontWeight = if (typeTab == tab) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2563EB),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFFCBD5E1),
                            ),
                        )
                    }
                }
                if (supportsSort) {
                    items(BrowseSortOption.entries.size) { idx ->
                        val opt = BrowseSortOption.entries[idx]
                        FilterChip(
                            selected = sortOption == opt,
                            onClick = { sortOption = opt },
                            label = { Text(opt.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFBBF24),
                                selectedLabelColor = Color(0xFF0B1120),
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFFCBD5E1),
                            ),
                        )
                    }
                }
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF38BDF8))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = cardWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = horizontalPad, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Person Bio Header
                personProfile?.let { p ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF162033))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A))
                                    .border(1.dp, Color(0xFF38BDF8), CircleShape),
                            ) {
                                if (!p.profileUrl.isNullOrBlank()) {
                                    AsyncImage(model = p.profileUrl, contentDescription = p.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(p.name, color = Color.White, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (p.biography.isNotBlank()) {
                                    Text(p.biography, color = Color(0xFFCBD5E1), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }

                // Collection Overview Header
                collectionDetails?.takeIf { it.overview.isNotBlank() }?.let { c ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = c.overview,
                            color = Color(0xFFCBD5E1),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF162033))
                                .padding(12.dp),
                        )
                    }
                }

                items(filteredItems, key = { "br:${it.contentKey}" }) { item ->
                    MediaPosterCard(
                        item = item,
                        cardWidth = cardWidth,
                        imageHeight = cardHeight,
                        onClick = { onSelectItem(item) },
                        onFocused = {},
                    )
                }
            }
        }
    }
}
