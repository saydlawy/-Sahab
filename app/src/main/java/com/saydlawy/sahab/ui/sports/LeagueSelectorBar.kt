package com.saydlawy.sahab.ui.sports

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import com.saydlawy.sahab.ui.components.SahabIcon as Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.saydlawy.sahab.data.sports.League
import com.saydlawy.sahab.data.sports.Match
import com.saydlawy.sahab.data.sports.MatchStatus
import com.saydlawy.sahab.data.sports.Standing
import com.saydlawy.sahab.data.sports.Team
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyGridState
import com.saydlawy.sahab.ui.focus.rememberRestorableLazyListState
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.util.formatUtcClockToLocal

// Palette derived from sport/DESIGN.md and sport/code.html
@Composable
internal fun SportsCompactPhoneToolbar(
    availableDateFilters: List<DateFilter>,
    selectedDate: DateFilter,
    dateFilterCounts: Map<DateFilter, Int>,
    onSelectDate: (DateFilter) -> Unit,
    showSearchField: Boolean,
    searchQuery: String,
    isRefreshing: Boolean,
    showStandingsTab: Boolean = false,
    onToggleSearch: () -> Unit,
    onSearchChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onShowStandings: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            DatePickerPill(
                filters = availableDateFilters,
                selected = selectedDate,
                counts = dateFilterCounts,
                onSelect = onSelectDate,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ActionPillButton(
                    icon = Ic.List,
                    label = if (showStandingsTab) "المباريات" else "جدول الترتيب",
                    highlighted = showStandingsTab,
                    accentCyan = showStandingsTab,
                    scale = 1f,
                    isTv = false,
                    onClick = onShowStandings,
                )
                ActionPillButton(
                    icon = Ic.Refresh,
                    label = if (isRefreshing) "..." else "تحديث",
                    highlighted = false,
                    accentCyan = true,
                    spinning = isRefreshing,
                    scale = 1f,
                    isTv = false,
                    onClick = onRefresh,
                )
                ActionPillButton(
                    icon = Ic.Search,
                    label = null,
                    highlighted = showSearchField || searchQuery.isNotBlank(),
                    scale = 1f,
                    isTv = false,
                    onClick = onToggleSearch,
                )
            }
        }
        if (showSearchField) {
            SportsSearchInput(searchQuery = searchQuery, onSearchChange = onSearchChange, scale = 1f)
        }
    }
}

@Composable
internal fun DatePickerPill(
    filters: List<DateFilter>,
    selected: DateFilter,
    counts: Map<DateFilter, Int>,
    onSelect: (DateFilter) -> Unit,
    scale: Float = 1f,
    isTv: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var focusedRaw by remember { mutableStateOf(false) }
    val focused = focusedRaw && isTv
    val count = counts[selected] ?: 0
    Box {
        Row(
            modifier = Modifier
                .heightIn(min = 42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) SportContainerHighest else SportContainerHigh)
                .border(
                    width = if (focused) 2.dp else 1.dp,
                    color = if (focused) SportCyan else SportViolet.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                )
                .onFocusChanged { focusedRaw = it.isFocused }
                .clickable { expanded = true }
                .padding(horizontal = (12 * scale).dp, vertical = (8 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                selected.title,
                color = Color.White,
                fontSize = (12.5f * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (count > 0) {
                Text(
                    "$count",
                    color = SportCyan,
                    fontSize = (10.5f * scale).sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(SportSurface)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                )
            }
            Icon(
                Ic.ArrowDown,
                contentDescription = null,
                tint = if (focused) SportCyan else SportPrimary,
                modifier = Modifier.size((18 * scale).dp),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SportContainer),
        ) {
            filters.forEach { df ->
                val dfCount = counts[df] ?: 0
                val active = df == selected
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                df.title,
                                color = if (active) SportCyan else Color.White,
                                fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (dfCount > 0) "$dfCount" else "—",
                                color = if (dfCount > 0) SportPrimary else SportTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onSelect(df)
                    },
                )
            }
        }
    }
}

@Composable
internal fun SportsSearchInput(searchQuery: String, onSearchChange: (String) -> Unit, scale: Float) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchChange,
        placeholder = { Text("ابحث عن نادي أو بطولة...", color = SportTextMuted, fontSize = (13 * scale).sp) },
        leadingIcon = { Icon(Ic.Search, contentDescription = null, tint = SportCyan) },
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchChange("") }) {
                    Icon(Ic.Close, contentDescription = "مسح", tint = SportTextMuted)
                }
            }
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SportCyan,
            unfocusedBorderColor = SportViolet.copy(alpha = 0.4f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = SportContainer,
            unfocusedContainerColor = SportContainer,
        ),
    )
}

@Composable
internal fun ActionPillButton(
    icon: ImageVector,
    label: String?,
    highlighted: Boolean,
    scale: Float,
    isTv: Boolean,
    accentCyan: Boolean = false,
    spinning: Boolean = false,
    onClick: () -> Unit,
) {
    var focusedRaw by remember { mutableStateOf(false) }
    val focused = focusedRaw && isTv
    val spinAngle = if (spinning) {
        val spinTransition = rememberInfiniteTransition(label = "refreshSpin")
        val angle by spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
            label = "refreshAngle",
        )
        angle
    } else {
        0f
    }

    val bg = when {
        focused -> SportVioletGradient
        highlighted -> SportHighlightedPillBrush
        else -> SportIdlePillBrush
    }
    val borderColor = when {
        focused -> SportCyan
        accentCyan -> SportCyan.copy(alpha = 0.45f)
        highlighted -> SportViolet.copy(alpha = 0.55f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Row(
        modifier = Modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(if (focused) 2.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
            .onFocusChanged { focusedRaw = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = (if (label == null) 11f else 12f * scale).dp, vertical = (8f * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (accentCyan) SportCyan else SportPrimary,
            modifier = Modifier
                .size((18 * scale).dp)
                .rotate(if (spinning) spinAngle else 0f),
        )
        if (label != null) {
            Text(
                label,
                color = Color.White,
                fontSize = (12 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun MainCategoryBar(selected: MainCategory, onSelect: (MainCategory) -> Unit, scale: Float, isTv: Boolean) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
        items(MainCategory.values(), key = { it.name }) { category ->
            val isSelected = category == selected
            var focusedRaw by remember { mutableStateOf(false) }
            val isFocused = focusedRaw && isTv
            val fill = when {
                isSelected -> SportVioletGradient
                isFocused -> SportFocusedCategoryGradient
                else -> SportIdleCategoryBrush
            }
            Row(
                modifier = Modifier
                    .heightIn(min = 42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fill)
                    .border(
                        width = if (isFocused) 2.dp else 1.dp,
                        color = when {
                            isFocused -> SportCyan
                            isSelected -> SportPrimary.copy(alpha = 0.55f)
                            else -> Color.White.copy(alpha = 0.08f)
                        },
                        shape = RoundedCornerShape(12.dp),
                    )
                    .onFocusChanged { focusedRaw = it.isFocused }
                    .clickable { onSelect(category) }
                    .padding(horizontal = (14 * scale).dp, vertical = (8 * scale).dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isSelected) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(SportCyan))
                }
                Text(
                    category.title,
                    color = if (isSelected || isFocused) Color.White else SportTextMuted,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontSize = (12.5f * scale).sp,
                    maxLines = 1,
                )
            }
        }
    }
}


@Composable
internal fun SubLeagueBar(
    subLeagues: List<Pair<Int?, String>>,
    selectedId: Int?,
    onSelect: (Int?) -> Unit,
    scale: Float,
    isTv: Boolean,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy((8 * scale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            Text(
                "البطولات:",
                color = SportTextMuted,
                fontSize = (11.5f * scale).sp,
                fontWeight = FontWeight.Bold,
            )
        }
        items(subLeagues, key = { it.first ?: -1 }) { (id, name) ->
            val isSelected = id == selectedId
            var focusedRaw by remember { mutableStateOf(false) }
            val isFocused = focusedRaw && isTv
            Box(
                modifier = Modifier
                    .heightIn(min = if (isTv) 34.dp else 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        when {
                            isSelected -> SportViolet.copy(alpha = 0.30f)
                            isFocused -> SportContainerHigh
                            else -> SportContainer
                        },
                    )
                    .border(
                        width = if (isFocused) 1.5.dp else 1.dp,
                        color = when {
                            isFocused -> SportCyan
                            isSelected -> SportViolet.copy(alpha = 0.70f)
                            else -> Color.White.copy(alpha = 0.06f)
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                    .onFocusChanged { focusedRaw = it.isFocused }
                    .clickable { onSelect(id) }
                    .padding(horizontal = (12 * scale).dp, vertical = (7 * scale).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name,
                    color = if (isSelected) SportPrimary else Color.White.copy(alpha = 0.88f),
                    fontSize = (12 * scale).sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

