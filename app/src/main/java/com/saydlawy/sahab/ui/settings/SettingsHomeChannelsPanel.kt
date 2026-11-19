package com.saydlawy.sahab.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.Category
import com.saydlawy.sahab.data.Channel
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.HomeChannelsSource
import com.saydlawy.sahab.data.UserInfo
import com.saydlawy.sahab.data.ai.ApiKeyManager
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.GlassTextField
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.SearchBox
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.focus.LocalFocusNavigation
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.ChannelLogoResolver
import com.saydlawy.sahab.util.appVersion
import com.saydlawy.sahab.util.formatDate
import com.saydlawy.sahab.util.normalizeForSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


@Composable
internal fun HomeChannelsSelectionPanel(
    container: AppContainer,
    source: HomeChannelsSource,
    selectedCategoryId: String,
    customChannelIds: Set<String>,
    onSelectSource: (HomeChannelsSource) -> Unit,
    onSelectCategory: (String) -> Unit,
    onToggleCustomChannel: (String) -> Unit,
    onClearCustomChannels: () -> Unit,
) {
    val focusNav = LocalFocusNavigation.current
    var liveCategories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var allChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var showChannelPickerDialog by remember { mutableStateOf(false) }

    LaunchedEffect(container.repo.account) {
        withContext(Dispatchers.IO) {
            runCatching {
                liveCategories = container.repo.liveCategories()
                allChannels = container.repo.peekAllLiveChannels() ?: container.repo.liveChannels(null)
            }
        }
    }

    GlassPanel(shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Royal.copy(alpha = 0.28f))
                        .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Tv, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "اختيار قنوات الشاشة الرئيسية",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Palette.Text,
                    )
                    Text(
                        "حدد مصدر القنوات التي تظهر في شريط القنوات المباشرة بالصفحة الرئيسية",
                        fontSize = 12.sp,
                        color = Palette.TextDim,
                    )
                }
            }

            // Option 1: AUTO
            SourceOptionCard(
                title = "اختيار ذكي تلقائي (الافتراضي)",
                subtitle = "يعرض أشهر القنوات العربية والرياضية والإخبارية تلقائياً",
                selected = source == HomeChannelsSource.AUTO,
                itemKey = "settings_ch_source_auto",
                onClick = { onSelectSource(HomeChannelsSource.AUTO) },
            )

            // Option 2: FAVORITES
            SourceOptionCard(
                title = "قنواتي المفضلة فقط",
                subtitle = "يعرض القنوات المباشرة التي قمت بإضافتها إلى المفضلة",
                selected = source == HomeChannelsSource.FAVORITES,
                itemKey = "settings_ch_source_favs",
                onClick = { onSelectSource(HomeChannelsSource.FAVORITES) },
            )

            // Option 3: CATEGORY
            SourceOptionCard(
                title = "باقة محددة من السيرفر",
                subtitle = if (selectedCategoryId.isNotBlank()) {
                    val catName = liveCategories.firstOrNull { it.id == selectedCategoryId }?.name ?: selectedCategoryId
                    "الباقة المختارة: $catName"
                } else {
                    "اختر باقة كاملة (مثل beIN أو MBC أو OSN) لعرض قنواتها في الرئيسية"
                },
                selected = source == HomeChannelsSource.CATEGORY,
                itemKey = "settings_ch_source_category",
                onClick = { onSelectSource(HomeChannelsSource.CATEGORY) },
            )

            if (source == HomeChannelsSource.CATEGORY && liveCategories.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DotTitle("اختر الباقة المفضلة للرئيسية:", Palette.FocusCyan, fontSize = 14.sp)
                    liveCategories.take(25).chunked(2).forEach { pair ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            pair.forEach { cat ->
                                val isCatSelected = cat.id == selectedCategoryId
                                GlassCard(
                                    onClick = { onSelectCategory(cat.id) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .restorableFocus(Routes.SETTINGS, "settings_cat_pick_${cat.id}"),
                                    shape = RoundedCornerShape(12.dp),
                                    selected = isCatSelected,
                                ) {
                                    Text(
                                        text = cat.name,
                                        fontSize = 12.sp,
                                        fontWeight = if (isCatSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (isCatSelected) Color.White else Palette.TextDim,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    )
                                }
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // Option 4: CUSTOM
            SourceOptionCard(
                title = "تحديد قنوات مخصصة يدوياً (${customChannelIds.size} قناة مختارة)",
                subtitle = "اختر القنوات التي تريدها بالاسم واحدة تلو الأخرى لوضعها في الصفحة الرئيسية",
                selected = source == HomeChannelsSource.CUSTOM,
                itemKey = "settings_ch_source_custom",
                onClick = {
                    onSelectSource(HomeChannelsSource.CUSTOM)
                    focusNav.markNavigateFrom(Routes.SETTINGS, "settings_ch_source_custom")
                    showChannelPickerDialog = true
                },
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlassButton(
                    text = "تحديد القنوات يدوياً (${customChannelIds.size})",
                    onClick = {
                        onSelectSource(HomeChannelsSource.CUSTOM)
                        focusNav.markNavigateFrom(Routes.SETTINGS, "settings_open_ch_picker_btn")
                        showChannelPickerDialog = true
                    },
                    primary = true,
                    icon = Ic.Tv,
                    modifier = Modifier
                        .weight(1f)
                        .restorableFocus(Routes.SETTINGS, "settings_open_ch_picker_btn"),
                )
                if (customChannelIds.isNotEmpty()) {
                    GlassButton(
                        text = "مسح الاختيار",
                        onClick = onClearCustomChannels,
                        modifier = Modifier.restorableFocus(Routes.SETTINGS, "settings_clear_custom_ch_btn"),
                    )
                }
            }
        }
    }

    if (showChannelPickerDialog) {
        CustomChannelPickerDialog(
            allChannels = allChannels,
            selectedIds = customChannelIds,
            onToggleChannel = onToggleCustomChannel,
            onDismiss = {
                showChannelPickerDialog = false
                focusNav.restoreAfterDialogDismiss(Routes.SETTINGS, "settings_open_ch_picker_btn")
            },
        )
    }
}

@Composable
internal fun CustomChannelPickerDialog(
    allChannels: List<Channel>,
    selectedIds: Set<String>,
    onToggleChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filteredChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }

    LaunchedEffect(allChannels, query, selectedIds) {
        if (query.isNotBlank()) {
            delay(180L)
        }
        val result = withContext(Dispatchers.Default) {
            val needle = query.normalizeForSearch().trim()
            if (needle.isBlank()) {
                val selectedFirst = allChannels.filter { it.id in selectedIds }
                val others = allChannels.filter { it.id !in selectedIds }.take(150)
                selectedFirst + others
            } else {
                allChannels.asSequence()
                    .filter { it.name.normalizeForSearch().contains(needle) }
                    .take(150)
                    .toList()
            }
        }
        filteredChannels = result
    }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp),
            shape = RoundedCornerShape(24.dp),
            strong = true,
            padding = 18.dp,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "اختر قنوات الشاشة الرئيسية",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                        )
                        Text(
                            "تم تحديد ${selectedIds.size} قناة",
                            fontSize = 12.sp,
                            color = Palette.FocusCyan,
                        )
                    }
                    GlassButton(
                        text = "تم",
                        onClick = onDismiss,
                        primary = true,
                    )
                }

                SearchBox(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "ابحث عن قناة (مثل: beIN, MBC, الجزيرة، روتانا)...",
                    modifier = Modifier.fillMaxWidth(),
                )

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                ) {
                    itemsIndexed(filteredChannels, key = { idx, ch -> "${ch.id}_$idx" }) { _, ch ->
                        val checked = ch.id in selectedIds
                        val logoUrl = remember(ch.icon, ch.name) {
                            ChannelLogoResolver.resolvePrimaryUrl(ch.icon, ch.name)
                        }
                        GlassCard(
                            onClick = { onToggleChannel(ch.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            selected = checked,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Palette.FocusCyan,
                                        uncheckedColor = Palette.TextDim,
                                        checkmarkColor = Palette.CinematicBg,
                                    ),
                                )
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (logoUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = logoUrl,
                                            contentDescription = ch.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    } else {
                                        Text(
                                            text = ch.name.take(3),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Palette.CinematicBg,
                                        )
                                    }
                                }
                                Text(
                                    text = ch.name,
                                    fontSize = 14.sp,
                                    fontWeight = if (checked) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SourceOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    itemKey: String,
    onClick: () -> Unit,
) {
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .restorableFocus(Routes.SETTINGS, itemKey),
        shape = RoundedCornerShape(16.dp),
        selected = selected,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Palette.FocusCyan,
                    unselectedColor = Palette.TextDim,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (selected) Color.White else Palette.Text,
                )
                Text(
                    subtitle,
                    fontSize = 11.5.sp,
                    color = Palette.TextDim,
                )
            }
        }
    }
}

