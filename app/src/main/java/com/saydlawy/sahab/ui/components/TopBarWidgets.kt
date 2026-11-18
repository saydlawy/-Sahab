package com.saydlawy.sahab.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalTabNavigator
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.home.components.CompactTopBarInfo
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.formatNowMillis
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
internal fun CinematicNavTabsRow(
    current: String?,
    onTab: (String) -> Unit,
    compact: Boolean,
    scrollable: Boolean,
    modifier: Modifier = Modifier,
) {
    if (scrollable) {
        val listState = rememberLazyListState()
        val activeIdx = remember(current) {
            NavTabs.indexOfFirst { it.route == current }.coerceAtLeast(0)
        }
        LaunchedEffect(activeIdx) {
            listState.animateScrollToItem((activeIdx - 1).coerceAtLeast(0))
        }
        LazyRow(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(NavTabs, key = { _, t -> t.route }) { _, tab ->
                CinematicNavTab(
                    label = tab.label,
                    selected = tab.route == current,
                    compact = compact,
                    onClick = { if (tab.route != current) onTab(tab.route) },
                )
            }
        }
    } else {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavTabs.forEach { tab ->
                CinematicNavTab(
                    label = tab.label,
                    selected = tab.route == current,
                    compact = compact,
                    onClick = { if (tab.route != current) onTab(tab.route) },
                )
            }
        }
    }
}

@Composable
internal fun CinematicNavTab(
    label: String,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val isTv = com.saydlawy.sahab.ui.LocalIsTv.current
    val interaction = remember { MutableInteractionSource() }
    val focusedRaw by interaction.collectIsFocusedAsState()
    val focused = focusedRaw && isTv
    val hovered by interaction.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(140),
        label = "tabScale",
    )
    val textColor by animateColorAsState(
        targetValue = when {
            selected || focused -> Color.White
            hovered -> Color.White.copy(alpha = 0.9f)
            else -> Palette.TextMuted
        },
        animationSpec = tween(140),
        label = "tabText",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = when {
            selected -> if (compact) 26.dp else 36.dp
            focused -> if (compact) 20.dp else 28.dp
            else -> 0.dp
        },
        animationSpec = tween(180),
        label = "tabIndWidth",
    )

    val shape = RoundedCornerShape(10.dp)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clip(shape)
            .background(
                when {
                    focused -> Palette.FocusCyan.copy(alpha = 0.18f)
                    else -> Color.Transparent
                },
            )
            .border(
                width = if (focused) 2.dp else 0.dp,
                color = if (focused) Palette.FocusCyan else Color.Transparent,
                shape = shape,
            )
            .hoverable(interaction)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(
                horizontal = if (compact) 10.dp else 14.dp,
                vertical = if (compact) 5.dp else 7.dp,
            ),
    ) {
        Text(
            text = label,
            fontSize = if (compact) 13.sp else 15.sp,
            fontWeight = if (selected || focused) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        // Glowing cyan/blue underline bar matching reference image
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(3.dp)
                .shadow(
                    elevation = if (selected || focused) 6.dp else 0.dp,
                    shape = RoundedCornerShape(50),
                    spotColor = Palette.FocusCyan,
                )
                .clip(RoundedCornerShape(50))
                .background(
                    if (selected || focused) {
                        Brush.horizontalGradient(listOf(Palette.FocusCyan, Palette.Royal))
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    },
                ),
        )
    }
}

@Composable
internal fun TopBarProfileButton(
    active: Boolean,
    onClick: () -> Unit,
    size: Dp = 36.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(140), label = "profScale")

    Box(
        modifier = Modifier
            .size(size)
            .scale(scale)
            .shadow(if (focused || active) 8.dp else 0.dp, CircleShape, spotColor = Palette.FocusCyan)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = if (active || focused) {
                        listOf(Palette.FocusCyan, Palette.Royal)
                    } else {
                        listOf(Color(0xFF28314E), Color(0xFF1A2035))
                    },
                ),
            )
            .border(
                width = if (focused) 2.2.dp else 1.5.dp,
                color = if (focused || active) Palette.FocusCyan else Color.White.copy(alpha = 0.35f),
                shape = CircleShape,
            )
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onClick()
                    true
                } else {
                    false
                }
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Ic.Settings,
            contentDescription = Ar.settings,
            tint = Color.White,
            modifier = Modifier.size(size * 0.50f),
        )
    }
}
