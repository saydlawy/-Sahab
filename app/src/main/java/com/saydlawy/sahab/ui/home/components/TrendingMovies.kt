package com.saydlawy.sahab.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import com.saydlawy.sahab.data.catalog.CatalogMovieEntity
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.qualityOf
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette

@Composable
fun TrendingMoviesSection(
    movies: List<CatalogMovieEntity>,
    onMovieClick: (CatalogMovieEntity) -> Unit,
    title: String = "أفلام مقترحة لك",
    iconEmoji: String = "🔥",
    rowKey: String = "home_trending_movies_row",
) {
    if (movies.isEmpty()) return

    val focusNav = LocalFocusNavigation.current
    val isTvOrTablet = LocalDisplayMode.current == DisplayMode.TABLET_OR_TV
    val horizPad = if (isTvOrTablet) 24.dp else 14.dp
    val cardWidth = if (isTvOrTablet) 148.dp else 130.dp
    val rowState = rememberRestorableLazyListState(rowKey)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = iconEmoji,
                fontSize = if (isTvOrTablet) 17.sp else 15.sp,
            )
            Text(
                text = title,
                color = Color.White,
                fontSize = if (isTvOrTablet) 17.sp else 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = horizPad, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(
                items = movies,
                key = { _, item -> "trending_${item.novaStreamId.ifEmpty { item.sahabId }}" },
            ) { index, movie ->
                val itemFocusKey = "trending_${movie.novaStreamId.ifEmpty { movie.sahabId }}"
                TrendingMovieCard(
                    movie = movie,
                    isNew = index < 4 || movie.year >= "2025",
                    onClick = {
                        focusNav.markNavigateFrom(Routes.HOME, itemFocusKey)
                        onMovieClick(movie)
                    },
                    modifier = Modifier
                        .width(cardWidth)
                        .restorableFocus(
                            screenKey = Routes.HOME,
                            itemKey = itemFocusKey,
                        ),
                )
            }
        }
    }
}

@Composable
private fun TrendingMovieCard(
    movie: CatalogMovieEntity,
    isNew: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val qualityTag = qualityOf(movie.titleAr)
    val ratingText = movie.rating.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
    val subtitleText = when {
        movie.genre.isNotBlank() -> movie.genre
        movie.plot.isNotBlank() -> movie.plot
        movie.year.isNotBlank() -> "إصدار ${movie.year}"
        else -> "أحدث الإضافات"
    }

    GlassCard(
        onClick = onClick,
        modifier = modifier.aspectRatio(2f / 3f),
        shape = RoundedCornerShape(14.dp),
        focusScale = 1.05f,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp))
                .background(Palette.CardSurface),
        ) {
            RemoteImage(
                url = movie.posterUrl,
                fallbackText = movie.titleAr,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Smooth cinematic dark bottom gradient matching reference image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.45f to Color(0x330B0D17),
                                0.74f to Color(0xCC0B0D17),
                                1.0f to Color(0xF80B0D17),
                            ),
                        ),
                    ),
            )

            // Top-Start "جديد" blue pill badge matching "سيد الظلام" in reference image
            if (isNew) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Palette.NewBadgeBlue)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "جديد",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            // Focused glowing cyan border highlight matching reference image
            if (focused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.5.dp, Palette.FocusCyan, RoundedCornerShape(14.dp)),
                )
            }

            // Bottom metadata overlay
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = movie.titleAr,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    color = Palette.TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Right side in RTL: Gold star + rating (only when real rating exists)
                    if (ratingText != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Ic.Star,
                                contentDescription = null,
                                tint = Palette.Gold,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = ratingText,
                                color = Palette.Gold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }

                    // Left side in RTL: Quality badge (only when real quality tag exists in title)
                    if (qualityTag != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xCC1E2336))
                                .border(0.8.dp, Color.White.copy(alpha = 0.24f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = qualityTag,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
