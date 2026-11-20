package com.saydlawy.sahab.ui.browse.layouts.netflix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.data.Poster
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.browse.FocusedPosterDetails
import com.saydlawy.sahab.ui.browse.layouts.prime.formatHeroRuntime
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.RemoteImage
import com.saydlawy.sahab.ui.components.SahabIcon
import com.saydlawy.sahab.ui.components.TrailerAutoPlayBox
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar

private val NetflixRed = Color(0xFFE50914)
private val NetflixMatchGreen = Color(0xFF46D369)

/** Full-bleed Netflix TV Hero backdrop fading toward the START/bottom title block and during scroll. */
@Composable
internal fun NetflixBackdrop(
    details: FocusedPosterDetails?,
    scrollAlpha: Float = 1f,
    modifier: Modifier = Modifier,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bg = Palette.CinematicBg
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val clampedAlpha = scrollAlpha.coerceIn(0f, 1f)

    Box(modifier.fillMaxSize()) {
        if (clampedAlpha > 0.02f) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(if (compact) 1f else 0.68f)
                    .fillMaxHeight(if (compact) 0.44f else 0.72f)
                    .graphicsLayer { alpha = clampedAlpha },
            ) {
                if (details != null) {
                    val primaryImage = details.backdropUrl.ifBlank { details.poster.image }
                    val isLikelyPortraitPoster = details.backdropUrl.isBlank() ||
                        details.backdropUrl == details.poster.image
                    if (details.trailerUrl.isNotBlank() || !isLikelyPortraitPoster) {
                        TrailerAutoPlayBox(
                            trailerUrl = details.trailerUrl,
                            posterUrl = primaryImage,
                            title = details.poster.name,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // بوستر طولي بدون خلفية عرضية: نعرضه بمحاذاة علوية تمنع قصّ رؤوس الممثلين
                        RemoteImage(
                            url = primaryImage,
                            fallbackText = details.poster.name,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            targetWidthPx = 1280,
                            targetHeightPx = 720,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                val sideStops = if (rtl) {
                    arrayOf(0.0f to bg.copy(alpha = 0.35f), 0.14f to Color.Transparent, 0.70f to bg.copy(alpha = 0.68f), 1.0f to bg)
                } else {
                    arrayOf(0.0f to bg, 0.30f to bg.copy(alpha = 0.68f), 0.86f to Color.Transparent, 1.0f to bg.copy(alpha = 0.35f))
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (compact) Brush.horizontalGradient(listOf(bg.copy(alpha = 0.32f), bg.copy(alpha = 0.32f)))
                            else Brush.horizontalGradient(colorStops = sideStops),
                        ),
                )
            }
        }
        // تدرج علوي ناعم يفصل شريط التصنيفات عن الوجوه + تدرج سفلي لدمج البانر مع الصفوف
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to bg.copy(alpha = 0.72f),
                            0.14f to bg.copy(alpha = 0.18f),
                            0.36f to Color.Transparent,
                            if (compact) 0.44f to bg else 0.64f to bg.copy(alpha = 0.94f),
                            1.0f to bg,
                        ),
                    ),
                ),
        )
    }
}

@Composable
private fun NetflixHeroButton(
    text: String,
    icon: ImageVector,
    primary: Boolean,
    screenKey: String,
    focusKey: String,
    isDefaultEntry: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val isTv = LocalIsTv.current
    val scale by animateFloatAsState(
        targetValue = if (focused && isTv) 1.05f else 1f,
        animationSpec = tween(130),
        label = "nfHeroBtnScale",
    )
    val shape = RoundedCornerShape(8.dp)
    val bgColor = when {
        primary && focused -> Color(0xFFFF1F2C)
        primary -> NetflixRed
        focused -> Color.White.copy(alpha = 0.28f)
        else -> Color.White.copy(alpha = 0.14f)
    }
    val borderColor = when {
        focused -> Color.White
        primary -> Color.Transparent
        else -> Color.White.copy(alpha = 0.28f)
    }

    Row(
        modifier = Modifier
            .height(38.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .restorableFocus(screenKey, focusKey, isDefaultEntry = isDefaultEntry)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(bgColor)
            .border(if (focused) 2.dp else 1.dp, borderColor, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SahabIcon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun NetflixHeroInfo(
    screenKey: String,
    details: FocusedPosterDetails?,
    series: Boolean,
    isFavorite: Boolean,
    hasRecents: Boolean = false,
    onOpen: (Poster) -> Unit,
    onToggleFavorite: (Poster) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = details ?: return
    val compact = LocalDisplayMode.current == DisplayMode.PHONE_PORTRAIT
    val cleanRating = item.rating.trim().takeIf {
        it.isNotBlank() && it != "0" && it != "0.0" && it != "0.00"
    }.orEmpty()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NetflixRed)
                    .padding(horizontal = 6.dp, vertical = 1.5.dp),
            ) {
                Text("N", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
            Text(
                text = if (series) "سلسلة مميزة" else "فيلم سينمائي",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
        }
        Text(
            text = item.poster.name,
            color = Color.White,
            fontSize = if (compact) 19.sp else 23.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val metaParts = listOf(
            item.year,
            item.genre.substringBefore(",").trim(),
            formatHeroRuntime(item.runtime),
        ).filter { it.isNotBlank() }
        if (cleanRating.isNotBlank() || metaParts.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cleanRating.isNotBlank()) {
                    Text(
                        text = "تقييم $cleanRating",
                        color = NetflixMatchGreen,
                        fontSize = if (compact) 12.sp else 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString("  •  "),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = if (compact) 11.sp else 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (item.plot.isNotBlank()) {
            Text(
                text = item.plot,
                color = Color.White.copy(alpha = 0.86f),
                fontSize = if (compact) 11.5.sp else 12.5.sp,
                lineHeight = if (compact) 16.sp else 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NetflixHeroButton(
                text = Ar.details,
                icon = Ic.Play,
                primary = true,
                screenKey = screenKey,
                focusKey = "nf_hero_details_${item.poster.id}",
                isDefaultEntry = !hasRecents,
                onClick = { onOpen(item.poster) },
            )
            NetflixHeroButton(
                text = if (isFavorite) "في قائمتي" else "+ قائمتي",
                icon = if (isFavorite) Ic.Heart else Ic.HeartOutline,
                primary = false,
                screenKey = screenKey,
                focusKey = "nf_hero_fav_${item.poster.id}",
                isDefaultEntry = false,
                onClick = { onToggleFavorite(item.poster) },
            )
        }
    }
}
