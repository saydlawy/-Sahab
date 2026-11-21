package com.saydlawy.sahab.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette

// ============================================================================
// SECTION E — تصفح الفئات السريعة (Quick Categories)
// Order from Right to Left (RTL): البث المباشر -> الأفلام -> المسلسلات
// ============================================================================
@Composable
internal fun QuickCategoriesSection(
    onOpenLive: () -> Unit,
    onOpenMovies: () -> Unit,
    onOpenSeries: () -> Unit,
    hasDefaultEntry: Boolean = false,
) {
    val mode = LocalDisplayMode.current
    val isTvOrTablet = mode == DisplayMode.TABLET_OR_TV
    val horizPad = if (isTvOrTablet) 24.dp else 14.dp
    val cardHeight = when (mode) {
        DisplayMode.TABLET_OR_TV -> 76.dp
        DisplayMode.PHONE_LANDSCAPE -> 66.dp
        DisplayMode.PHONE_PORTRAIT -> 62.dp
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Ic.FolderGrid,
                contentDescription = null,
                tint = Palette.TextMuted,
                modifier = Modifier.size(if (isTvOrTablet) 18.dp else 16.dp),
            )
            Text(
                text = "تصفح الفئات السريعة",
                color = Color.White,
                fontSize = if (isTvOrTablet) 17.sp else 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        // Strict RTL order from Right to Left:
        // 1. البث المباشر (Right)  |  2. الأفلام (Center)  |  3. المسلسلات (Left)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizPad),
            horizontalArrangement = Arrangement.spacedBy(if (isTvOrTablet) 14.dp else 8.dp),
        ) {
            QuickCategoryBanner(
                title = "البث المباشر",
                icon = Ic.Broadcast,
                gradient = Brush.horizontalGradient(
                    colors = listOf(Palette.CategoryLiveEnd, Palette.CategoryLiveStart),
                ),
                accentColor = Color(0xFF38BDF8),
                compact = mode == DisplayMode.PHONE_PORTRAIT,
                onClick = onOpenLive,
                modifier = Modifier
                    .weight(1f)
                    .height(cardHeight)
                    .restorableFocus(Routes.HOME, "home_quick_live", isDefaultEntry = hasDefaultEntry),
            )
            QuickCategoryBanner(
                title = "الأفلام",
                icon = Ic.Movie,
                gradient = Brush.horizontalGradient(
                    colors = listOf(Palette.CategoryMoviesEnd, Palette.CategoryMoviesStart),
                ),
                accentColor = Color(0xFFF87171),
                compact = mode == DisplayMode.PHONE_PORTRAIT,
                onClick = onOpenMovies,
                modifier = Modifier
                    .weight(1f)
                    .height(cardHeight)
                    .restorableFocus(Routes.HOME, "home_quick_movies"),
            )
            QuickCategoryBanner(
                title = "المسلسلات",
                icon = Ic.Series,
                gradient = Brush.horizontalGradient(
                    colors = listOf(Palette.CategorySeriesEnd, Palette.CategorySeriesStart),
                ),
                accentColor = Color(0xFFA78BFA),
                compact = mode == DisplayMode.PHONE_PORTRAIT,
                onClick = onOpenSeries,
                modifier = Modifier
                    .weight(1f)
                    .height(cardHeight)
                    .restorableFocus(Routes.HOME, "home_quick_series"),
            )
        }
    }
}

@Composable
internal fun QuickCategoryBanner(
    title: String,
    icon: ImageVector,
    gradient: Brush,
    accentColor: Color,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        fill = gradient,
        focusScale = 1.04f,
    ) { focused ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(14.dp)),
        ) {
            if (!compact) {
                Canvas(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(96.dp),
                ) {
                    drawCircle(
                        color = accentColor.copy(alpha = 0.22f),
                        radius = size.minDimension * 0.55f,
                        center = Offset(size.width * 0.25f, size.height * 0.5f),
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.08f),
                        radius = size.minDimension * 0.35f,
                        center = Offset(size.width * 0.45f, size.height * 0.65f),
                    )
                }
            }

            if (focused) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.5.dp, Palette.FocusCyan, RoundedCornerShape(14.dp)),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 6.dp else 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (compact) Arrangement.Center else Arrangement.Start,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Color.White,
                    modifier = Modifier.size(if (compact) 17.dp else 28.dp),
                )
                Spacer(Modifier.width(if (compact) 5.dp else 12.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = if (compact) 12.sp else 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}

