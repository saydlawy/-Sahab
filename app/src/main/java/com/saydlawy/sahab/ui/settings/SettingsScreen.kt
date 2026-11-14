package com.saydlawy.sahab.ui.settings

import android.content.Context
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import coil.compose.AsyncImage
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val displayMode = LocalDisplayMode.current
    val focusNav = LocalFocusNavigation.current
    val isWide = displayMode == DisplayMode.TABLET_OR_TV

    val savedCategory = focusNav.getSelectedCategory(Routes.SETTINGS)
    var selectedCategory by remember(isWide) {
        mutableStateOf<String?>(savedCategory ?: if (isWide) "customize" else null)
    }

    val selectCat: (String?) -> Unit = { cat ->
        selectedCategory = cat
        if (cat != null) {
            focusNav.setSelectedCategory(Routes.SETTINGS, cat)
        }
    }

    BackHandler(enabled = !isWide && selectedCategory != null) {
        val prevCat = selectedCategory
        selectedCategory = null
        if (prevCat != null) {
            focusNav.restoreAfterDialogDismiss(Routes.SETTINGS, "settings_menu_$prevCat")
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (isWide || selectedCategory == null) {
            SahabTopBar(current = Routes.SETTINGS, onBack = onBack)
        }

        if (isWide) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                // Right column in RTL: Settings Sidebar
                Column(
                    Modifier
                        .weight(0.32f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsMenuItem(
                        title = "التخصيص والتحكم الكامل",
                        subtitle = "أقسام الرئيسية، المظهر، والأداء",
                        icon = Ic.Settings,
                        isSelected = selectedCategory == "customize",
                        itemKey = "settings_menu_customize",
                        isDefault = true,
                        onClick = { selectCat("customize") },
                    )
                    SettingsMenuItem(
                        title = "قنوات الشاشة الرئيسية",
                        subtitle = "اختيار وتحديد القنوات المعروضة بالرئيسية",
                        icon = Ic.Tv,
                        isSelected = selectedCategory == "home_channels",
                        itemKey = "settings_menu_home_channels",
                        onClick = { selectCat("home_channels") },
                    )
                    SettingsMenuItem(
                        title = "الحساب والاشتراك",
                        subtitle = "المزامنة السحابية وبيانات Xtream",
                        icon = Ic.Person,
                        isSelected = selectedCategory == "account",
                        itemKey = "settings_menu_account",
                        onClick = { selectCat("account") },
                    )
                    SettingsMenuItem(
                        title = "الذكاء الاصطناعي",
                        subtitle = "إدارة مفاتيح Gemini لتوليد القصص",
                        icon = Ic.Cloud,
                        isSelected = selectedCategory == "ai",
                        itemKey = "settings_menu_ai",
                        onClick = { selectCat("ai") },
                    )

                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        text = Ar.logout,
                        onClick = onLoggedOut,
                        icon = Ic.Close,
                        modifier = Modifier
                            .fillMaxWidth()
                            .restorableFocus(Routes.SETTINGS, "settings_logout_btn"),
                    )
                }

                // Left column in RTL: Active Settings Panel
                Box(
                    Modifier
                        .weight(0.68f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SettingsDetailContent(selectedCategory ?: "customize", container, context, onLoggedOut)
                }
            }
        } else {
            if (selectedCategory == null) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsMenuItem(
                        title = "التخصيص والتحكم الكامل",
                        subtitle = "أقسام الرئيسية، المظهر، والأداء",
                        icon = Ic.Settings,
                        isSelected = false,
                        itemKey = "settings_menu_customize",
                        onClick = { selectCat("customize") },
                    )
                    SettingsMenuItem(
                        title = "قنوات الشاشة الرئيسية",
                        subtitle = "اختيار وتحديد القنوات المعروضة بالرئيسية",
                        icon = Ic.Tv,
                        isSelected = false,
                        itemKey = "settings_menu_home_channels",
                        onClick = { selectCat("home_channels") },
                    )
                    SettingsMenuItem(
                        title = "الحساب والاشتراك",
                        subtitle = "المزامنة السحابية وبيانات Xtream",
                        icon = Ic.Person,
                        isSelected = false,
                        itemKey = "settings_menu_account",
                        onClick = { selectCat("account") },
                    )
                    SettingsMenuItem(
                        title = "الذكاء الاصطناعي",
                        subtitle = "إدارة مفاتيح Gemini لتوليد القصص",
                        icon = Ic.Cloud,
                        isSelected = false,
                        itemKey = "settings_menu_ai",
                        onClick = { selectCat("ai") },
                    )

                    Spacer(Modifier.height(16.dp))
                    GlassButton(
                        text = Ar.logout,
                        onClick = onLoggedOut,
                        icon = Ic.Close,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Text(
                        text = Ar.version(appVersion(context).ifBlank { "1.0" }),
                        fontSize = 13.sp,
                        color = Palette.TextDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    SahabTopBar(
                        current = Routes.SETTINGS,
                        onBack = {
                            val prev = selectedCategory
                            selectedCategory = null
                            if (prev != null) {
                                focusNav.restoreAfterDialogDismiss(Routes.SETTINGS, "settings_menu_$prev")
                            }
                        },
                    )
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        SettingsDetailContent(selectedCategory, container, context, onLoggedOut)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    itemKey: String,
    isDefault: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    GlassCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .restorableFocus(Routes.SETTINGS, itemKey, isDefaultEntry = isDefault)
            .onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(18.dp),
        selected = isSelected || focused,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected || focused) Palette.Royal.copy(alpha = 0.4f) else Palette.Royal.copy(alpha = 0.2f))
                    .border(1.dp, Palette.FocusCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Palette.FocusCyan, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = Palette.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("<", fontSize = 20.sp, color = Palette.Glow, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsDetailContent(
    category: String?,
    container: AppContainer,
    context: Context,
    onLoggedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val cloudUser = container.authManager.currentUser
    val account: Account? = container.repo.account
    val info: UserInfo? = container.repo.userInfo

    val light by container.prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)
    val showRecents by container.prefs.showRecents.collectAsStateWithLifecycle(initialValue = true)
    val showLiveChannels by container.prefs.showLiveChannelsOnHome.collectAsStateWithLifecycle(initialValue = true)
    val showRecommended by container.prefs.showRecommendedOnHome.collectAsStateWithLifecycle(initialValue = true)
    val showQuickCategories by container.prefs.showQuickCategoriesOnHome.collectAsStateWithLifecycle(initialValue = true)
    val showMatches by container.prefs.showMatchesOnHome.collectAsStateWithLifecycle(initialValue = true)
    val homeChannelsLayout by container.prefs.homeChannelsLayout.collectAsStateWithLifecycle(initialValue = HomeChannelsLayout.CAROUSEL)
    val homeChannelsSource by container.prefs.homeChannelsSource.collectAsStateWithLifecycle(initialValue = HomeChannelsSource.AUTO)
    val homeChannelsCategoryId by container.prefs.homeChannelsCategoryId.collectAsStateWithLifecycle(initialValue = "")
    val homeCustomChannelIds by container.prefs.homeCustomChannelIds.collectAsStateWithLifecycle(initialValue = emptySet())

    when (category) {
        "account" -> {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                CloudAccountPanel(cloudUser?.email, onLogout = { container.authManager.signOut() })
                AccountPanel(
                    account = account,
                    info = info,
                    onSyncNow = {
                        scope.launch(Dispatchers.IO) {
                            container.repo.syncCatalog(force = true)
                            container.catalogSync.syncMovies(container.repo)
                        }
                    },
                )
            }
        }
        "home_channels" -> {
            HomeChannelsSelectionPanel(
                container = container,
                source = homeChannelsSource,
                selectedCategoryId = homeChannelsCategoryId,
                customChannelIds = homeCustomChannelIds,
                onSelectSource = { src -> scope.launch { container.prefs.setHomeChannelsSource(src) } },
                onSelectCategory = { catId ->
                    scope.launch {
                        container.prefs.setHomeChannelsCategoryId(catId)
                        container.prefs.setHomeChannelsSource(HomeChannelsSource.CATEGORY)
                    }
                },
                onToggleCustomChannel = { chId ->
                    scope.launch {
                        container.prefs.toggleHomeCustomChannel(chId)
                        container.prefs.setHomeChannelsSource(HomeChannelsSource.CUSTOM)
                    }
                },
                onClearCustomChannels = {
                    scope.launch { container.prefs.setHomeCustomChannelIds(emptySet()) }
                },
            )
        }
        "customize" -> {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                HomeCustomizationPanel(
                    showRecents = showRecents,
                    showLiveChannels = showLiveChannels,
                    showRecommended = showRecommended,
                    showQuickCategories = showQuickCategories,
                    showMatches = showMatches,
                    homeChannelsLayout = homeChannelsLayout,
                    onToggleShowRecents = { scope.launch { container.prefs.setShowRecents(!showRecents) } },
                    onToggleShowLive = { scope.launch { container.prefs.setShowLiveChannelsOnHome(!showLiveChannels) } },
                    onToggleShowRecommended = { scope.launch { container.prefs.setShowRecommendedOnHome(!showRecommended) } },
                    onToggleShowQuickCategories = { scope.launch { container.prefs.setShowQuickCategoriesOnHome(!showQuickCategories) } },
                    onToggleShowMatches = { scope.launch { container.prefs.setShowMatchesOnHome(!showMatches) } },
                    onSelectLayout = { layout -> scope.launch { container.prefs.setHomeChannelsLayout(layout) } },
                    onClearRecents = { scope.launch { container.prefs.restoreRecents(emptyList()) } },
                )
                AppearancePanel(
                    light = light,
                    onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } },
                )
            }
        }
        "ai" -> {
            AiKeysPanel(container.aiKeys)
        }
    }
}

@Composable
private fun HomeChannelsSelectionPanel(
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
private fun CustomChannelPickerDialog(
    allChannels: List<Channel>,
    selectedIds: Set<String>,
    onToggleChannel: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filteredChannels = remember(allChannels, query, selectedIds) {
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
                    items(filteredChannels, key = { it.id }) { ch ->
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
private fun SourceOptionCard(
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

@Composable
private fun HomeCustomizationPanel(
    showRecents: Boolean,
    showLiveChannels: Boolean,
    showRecommended: Boolean,
    showQuickCategories: Boolean,
    showMatches: Boolean,
    homeChannelsLayout: HomeChannelsLayout,
    onToggleShowRecents: () -> Unit,
    onToggleShowLive: () -> Unit,
    onToggleShowRecommended: () -> Unit,
    onToggleShowQuickCategories: () -> Unit,
    onToggleShowMatches: () -> Unit,
    onSelectLayout: (HomeChannelsLayout) -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Royal.copy(alpha = 0.28f))
                        .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Settings, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("التحكم في أقسام الشاشة الرئيسية", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
                    Text("إظهار أو إخفاء أي قسم وتخصيص طريقة العرض", fontSize = 12.sp, color = Palette.TextDim)
                }
            }

            SettingsSwitchRow(
                title = "عرض قسم تابع المشاهدة",
                subtitle = "إظهار شريط استكمال مشاهدة الأفلام والمسلسلات والقنوات",
                checked = showRecents,
                itemKey = "settings_toggle_recents",
                onToggle = onToggleShowRecents,
            )

            SettingsSwitchRow(
                title = "عرض قسم القنوات المباشرة",
                subtitle = "إظهار شريط القنوات المباشرة المميزة في الصفحة الرئيسية",
                checked = showLiveChannels,
                itemKey = "settings_toggle_live_channels",
                onToggle = onToggleShowLive,
            )

            SettingsSwitchRow(
                title = "عرض قسم أفلام مقترحة لك (أحدث الإضافات)",
                subtitle = "إظهار أحدث الأفلام المضافة من سيرفر Xtream وقاعدة بيانات Firebase",
                checked = showRecommended,
                itemKey = "settings_toggle_recommended",
                onToggle = onToggleShowRecommended,
            )

            SettingsSwitchRow(
                title = "عرض بطاقات التصفح السريع",
                subtitle = "إظهار بطاقات البث المباشر — الأفلام — المسلسلات",
                checked = showQuickCategories,
                itemKey = "settings_toggle_quick_cats",
                onToggle = onToggleShowQuickCategories,
            )

            SettingsSwitchRow(
                title = "عرض قسم مباريات اليوم",
                subtitle = "إظهار جدول مباريات اليوم المباشرة والقادمة في أسفل الرئيسية",
                checked = showMatches,
                itemKey = "settings_toggle_matches",
                onToggle = onToggleShowMatches,
            )

            Spacer(Modifier.height(4.dp))
            DotTitle("طريقة عرض القنوات المباشرة في الرئيسية", Palette.Royal, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val isCarousel = homeChannelsLayout == HomeChannelsLayout.CAROUSEL
                GlassCard(
                    onClick = { onSelectLayout(HomeChannelsLayout.CAROUSEL) },
                    modifier = Modifier
                        .weight(1f)
                        .restorableFocus(Routes.SETTINGS, "settings_layout_carousel"),
                    shape = RoundedCornerShape(16.dp),
                    selected = isCarousel,
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isCarousel,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Palette.Glow, unselectedColor = Palette.TextDim),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("سلايدر أفقي", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isCarousel) Palette.Text else Palette.TextDim)
                            Text("شريط متحرك", fontSize = 11.sp, color = Palette.TextDim)
                        }
                    }
                }
                val isGrid = homeChannelsLayout == HomeChannelsLayout.GRID
                GlassCard(
                    onClick = { onSelectLayout(HomeChannelsLayout.GRID) },
                    modifier = Modifier
                        .weight(1f)
                        .restorableFocus(Routes.SETTINGS, "settings_layout_grid"),
                    shape = RoundedCornerShape(16.dp),
                    selected = isGrid,
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isGrid,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Palette.Glow, unselectedColor = Palette.TextDim),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("شبكة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isGrid) Palette.Text else Palette.TextDim)
                            Text("صفوف متعددة", fontSize = 11.sp, color = Palette.TextDim)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            GlassButton(
                text = "مسح سجل المشاهدة بالكامل",
                onClick = onClearRecents,
                modifier = Modifier
                    .fillMaxWidth()
                    .restorableFocus(Routes.SETTINGS, "settings_clear_recents_btn"),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    itemKey: String,
    onToggle: () -> Unit,
) {
    GlassCard(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .restorableFocus(Routes.SETTINGS, itemKey),
        shape = RoundedCornerShape(16.dp),
        selected = checked,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                DotTitle(title, if (checked) Palette.FocusCyan else Palette.TextDim, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 11.5.sp, color = Palette.TextDim)
            }
            Switch(
                checked = checked,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Palette.Royal,
                    uncheckedThumbColor = Palette.TextDim,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                ),
            )
        }
    }
}

@Composable
private fun CloudAccountPanel(email: String?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Palette.Blue.copy(alpha = 0.28f))
                            .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Ic.Cloud, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("الحساب السحابي", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text(
                            if (email != null) "متصل" else "غير متصل",
                            fontSize = 13.sp,
                            color = if (email != null) Palette.Emerald else Palette.Gold,
                        )
                    }
                }
                if (email != null) Tag("Cloud", Palette.Blue)
            }
            if (email != null) {
                InfoRow("البريد الإلكتروني", email)
                GlassButton(
                    text = "تسجيل الخروج من السحابة",
                    onClick = onLogout,
                    modifier = Modifier
                        .fillMaxWidth()
                        .restorableFocus(Routes.SETTINGS, "settings_cloud_logout_btn"),
                    icon = Ic.Close,
                )
            } else {
                Text("لم يتم تسجيل الدخول، بياناتك تحفظ محلياً على الجهاز.", fontSize = 13.sp, color = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun AccountPanel(
    account: Account?,
    info: UserInfo?,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Royal.copy(alpha = 0.28f))
                        .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Person, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("اشتراك المشاهدة (Xtream)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text(account?.user.orEmpty().ifBlank { "-" }, fontSize = 13.sp, color = Palette.Glow)
                }
            }
            val activeStatus = info?.status.orEmpty().ifBlank { "Active" }
            Tag(activeStatus, Palette.Emerald)
        }
        Spacer(Modifier.height(14.dp))
        InfoRow("الخادم", hostOf(account?.server.orEmpty()))
        InfoRow("اسم المستخدم", account?.user.orEmpty())
        if (info != null) {
            InfoRow("الحالة", if (info.status.isBlank()) "Active" else info.status)
            val expiry = info.expiry
            InfoRow("ينتهي الاشتراك", if (expiry == null || expiry <= 0L) Ar.subscriptionOpenShort else formatDate(expiry))
            if (info.maxConnections.isNotBlank()) {
                InfoRow("الأجهزة المتصلة", "${info.activeConnections.ifBlank { "0" }} / ${info.maxConnections}")
            }
        }
        Spacer(Modifier.height(12.dp))
        GlassButton(
            text = "تحديث ومزامنة قوائم القنوات والأفلام الآن",
            onClick = onSyncNow,
            icon = Ic.Refresh,
            primary = true,
            modifier = Modifier
                .fillMaxWidth()
                .restorableFocus(Routes.SETTINGS, "settings_sync_now_btn"),
        )
    }
}

@Composable
private fun AppearancePanel(light: Boolean, onToggleLight: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        SettingsSwitchRow(
            title = "وضع الأداء الخفيف والسرعة القصوى",
            subtitle = "إيقاف حركات الخلفية والتأثيرات الثقيلة لتسريع التنقل على التلفاز والأجهزة",
            checked = light,
            itemKey = "settings_toggle_light_mode",
            onToggle = onToggleLight,
        )
    }
}

@Composable
private fun AiKeysPanel(aiKeys: ApiKeyManager, modifier: Modifier = Modifier) {
    val focusNav = LocalFocusNavigation.current
    val keys by aiKeys.keys.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by aiKeys.activeId.collectAsStateWithLifecycle(initialValue = "")
    var showAdd by remember { mutableStateOf(false) }
    var newKeyText by remember { mutableStateOf("") }
    var newKeyLabel by remember { mutableStateOf("") }

    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DotTitle("مفاتيح Gemini", Palette.Glow, fontSize = 16.sp)
            Text("أضف مفاتيح لتوليد قصص الأفلام باللغة العربية عند الحاجة.", fontSize = 12.sp, color = Palette.TextDim)
            keys.forEach { key ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (key.id == activeId) Palette.Royal.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(key.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text(key.value.take(8) + "…" + key.value.takeLast(4), fontSize = 11.sp, color = Palette.TextDim)
                    }
                    if (key.id == activeId) {
                        Tag("نشط", Palette.Emerald)
                    } else {
                        GlassButton(text = "تفعيل", onClick = { aiKeys.setActive(key.id) })
                    }
                    Spacer(Modifier.width(6.dp))
                    GlassButton(text = "حذف", onClick = { aiKeys.removeKey(key.id) })
                }
            }
            GlassButton(
                text = "+ إضافة مفتاح",
                onClick = {
                    focusNav.markNavigateFrom(Routes.SETTINGS, "settings_add_ai_key_btn")
                    showAdd = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .restorableFocus(Routes.SETTINGS, "settings_add_ai_key_btn"),
                primary = true,
            )
        }
    }

    if (showAdd) {
        Dialog(
            onDismissRequest = {
                showAdd = false
                focusNav.restoreAfterDialogDismiss(Routes.SETTINGS, "settings_add_ai_key_btn")
            },
        ) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("مفتاح Gemini جديد", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    GlassTextField(value = newKeyLabel, onValueChange = { newKeyLabel = it }, placeholder = "اسم مختصر (اختياري)", modifier = Modifier.fillMaxWidth())
                    GlassTextField(value = newKeyText, onValueChange = { newKeyText = it }, placeholder = "المفتاح (AIza...)", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "إضافة",
                            onClick = {
                                if (aiKeys.addKey(newKeyText, newKeyLabel) != null) {
                                    newKeyText = ""
                                    newKeyLabel = ""
                                    showAdd = false
                                    focusNav.restoreAfterDialogDismiss(Routes.SETTINGS, "settings_add_ai_key_btn")
                                }
                            },
                            primary = true,
                        )
                        GlassButton(
                            text = "إلغاء",
                            onClick = {
                                showAdd = false
                                focusNav.restoreAfterDialogDismiss(Routes.SETTINGS, "settings_add_ai_key_btn")
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun hostOf(server: String): String = server.substringAfter("://").trimEnd('/')

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
    }
}
