package com.saydlawy.sahab.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onOpenCloudLogin: () -> Unit = {},
) {
    val container = LocalContainer.current
    val context = LocalContext.current
    val displayMode = LocalDisplayMode.current
    val focusNav = LocalFocusNavigation.current
    val isWide = displayMode == DisplayMode.TABLET_OR_TV
    val bgEnrichStatus by container.catalogSync.bgEnrichStatus.collectAsStateWithLifecycle()

    val savedCategory = focusNav.getSelectedCategory(Routes.SETTINGS)
    var selectedCategory by remember(isWide) {
        mutableStateOf<String?>(savedCategory ?: if (isWide) "appearance" else null)
    }
    var showLogoutConfirm by remember { mutableStateOf(false) }

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

    BackHandler(enabled = isWide) {
        val activeKey = "settings_menu_${selectedCategory ?: "appearance"}"
        focusNav.restoreAfterDialogDismiss(Routes.SETTINGS, activeKey)
        onBack()
    }

    Column(Modifier.fillMaxSize()) {
        if (isWide || selectedCategory == null) {
            SahabTopBar(current = Routes.SETTINGS, onBack = onBack)
        }

        if ((bgEnrichStatus.active && bgEnrichStatus.total > 0) || bgEnrichStatus.failed > 0) {
            val hasOnlyErrors = !bgEnrichStatus.active && bgEnrichStatus.failed > 0
            val borderColor = if (hasOnlyErrors) Color(0x66F87171) else Color(0x4438BDF8)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isWide) 24.dp else 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF162033))
                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (bgEnrichStatus.active) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF38BDF8),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = buildString {
                            if (bgEnrichStatus.active) {
                                append("جارٍ إكمال بيانات المكتبة (${bgEnrichStatus.done} / ${bgEnrichStatus.total})")
                                if (bgEnrichStatus.failed > 0) append(" • فشل: ${bgEnrichStatus.failed}")
                                if (bgEnrichStatus.currentTitle.isNotBlank()) {
                                    append(" — ")
                                    append(bgEnrichStatus.currentTitle)
                                }
                            } else {
                                append("انتهى إكمال المكتبة مع أخطاء (فشل ${bgEnrichStatus.failed} من ${bgEnrichStatus.total})")
                            }
                        },
                        color = if (hasOnlyErrors) Color(0xFFFCA5A5) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (bgEnrichStatus.lastError.isNotBlank()) {
                        Text(
                            text = "آخر خطأ: ${bgEnrichStatus.lastError}",
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
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
                        .focusGroup()
                        .focusRestorer()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsMenuItem(
                        title = "المظهر وتصميم الشاشات",
                        subtitle = "تخطيط الأفلام، المسلسلات، الثيمات، والأداء",
                        icon = Ic.Settings,
                        isSelected = selectedCategory == "appearance",
                        itemKey = "settings_menu_appearance",
                        isDefault = true,
                        onClick = { selectCat("appearance") },
                    )
                    SettingsMenuItem(
                        title = "قوائم البث والتخصيص",
                        subtitle = "قنوات الرئيسية، القوائم المخصصة، والمخفية",
                        icon = Ic.Tv,
                        isSelected = selectedCategory == "custom_tv",
                        itemKey = "settings_menu_custom_tv",
                        onClick = { selectCat("custom_tv") },
                    )
                    SettingsMenuItem(
                        title = "المصادر والاشتراكات",
                        subtitle = "إدارة مصادر Xtream / M3U وتحديث الاشتراك",
                        icon = Ic.Tv,
                        isSelected = selectedCategory == "sources",
                        itemKey = "settings_menu_sources",
                        onClick = { selectCat("sources") },
                    )
                    SettingsMenuItem(
                        title = "الحساب السحابي والمزامنة",
                        subtitle = "تسجيل الدخول، رمز التشفير، والمزامنة",
                        icon = Ic.Cloud,
                        isSelected = selectedCategory == "cloud",
                        itemKey = "settings_menu_cloud",
                        onClick = { selectCat("cloud") },
                    )
                    SettingsMenuItem(
                        title = "مسارات البيانات والإحصائيات",
                        subtitle = "السحابة، Room، الكاش، وحالة الاكتمال",
                        icon = Ic.Cloud,
                        isSelected = selectedCategory == "data_flow",
                        itemKey = "settings_menu_data_flow",
                        onClick = { selectCat("data_flow") },
                    )
                    SettingsMenuItem(
                        title = "الذكاء الاصطناعي (Gemini)",
                        subtitle = "إدارة مفاتيح توليد قصص الأفلام",
                        icon = Ic.Cloud,
                        isSelected = selectedCategory == "ai",
                        itemKey = "settings_menu_ai",
                        onClick = { selectCat("ai") },
                    )

                    Spacer(Modifier.height(8.dp))
                    GlassButton(
                        text = Ar.logout,
                        onClick = {
                            focusNav.markNavigateFrom(Routes.SETTINGS, "settings_logout_btn")
                            showLogoutConfirm = true
                        },
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
                        .focusGroup()
                        .focusRestorer()
                        .verticalScroll(rememberScrollState()),
                ) {
                    SettingsDetailContent(selectedCategory ?: "appearance", container, onOpenCloudLogin, onLoggedOut)
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
                        title = "المظهر وتصميم الشاشات",
                        subtitle = "تخطيط الأفلام، المسلسلات، الثيمات، والأداء",
                        icon = Ic.Settings,
                        isSelected = false,
                        itemKey = "settings_menu_appearance",
                        onClick = { selectCat("appearance") },
                    )
                    SettingsMenuItem(
                        title = "قوائم البث والتخصيص",
                        subtitle = "قنوات الرئيسية، القوائم المخصصة، والمخفية",
                        icon = Ic.Tv,
                        isSelected = false,
                        itemKey = "settings_menu_custom_tv",
                        onClick = { selectCat("custom_tv") },
                    )
                    SettingsMenuItem(
                        title = "المصادر والاشتراكات",
                        subtitle = "إدارة مصادر Xtream / M3U وتحديث الاشتراك",
                        icon = Ic.Tv,
                        isSelected = false,
                        itemKey = "settings_menu_sources",
                        onClick = { selectCat("sources") },
                    )
                    SettingsMenuItem(
                        title = "الحساب السحابي والمزامنة",
                        subtitle = "تسجيل الدخول، رمز التشفير، والمزامنة",
                        icon = Ic.Cloud,
                        isSelected = false,
                        itemKey = "settings_menu_cloud",
                        onClick = { selectCat("cloud") },
                    )
                    SettingsMenuItem(
                        title = "مسارات البيانات والإحصائيات",
                        subtitle = "السحابة، Room، الكاش، وحالة الاكتمال",
                        icon = Ic.Cloud,
                        isSelected = false,
                        itemKey = "settings_menu_data_flow",
                        onClick = { selectCat("data_flow") },
                    )
                    SettingsMenuItem(
                        title = "الذكاء الاصطناعي (Gemini)",
                        subtitle = "إدارة مفاتيح توليد قصص الأفلام",
                        icon = Ic.Cloud,
                        isSelected = false,
                        itemKey = "settings_menu_ai",
                        onClick = { selectCat("ai") },
                    )

                    Spacer(Modifier.height(16.dp))
                    GlassButton(
                        text = Ar.logout,
                        onClick = { showLogoutConfirm = true },
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
                        SettingsDetailContent(selectedCategory, container, onOpenCloudLogin, onLoggedOut)
                    }
                }
            }
        }
    }

    if (showLogoutConfirm) {
        LogoutConfirmationDialog(
            onConfirm = {
                showLogoutConfirm = false
                onLoggedOut()
            },
            onDismiss = {
                showLogoutConfirm = false
                focusNav.restoreAfterDialogDismiss(Routes.SETTINGS, "settings_logout_btn")
            },
        )
    }
}

@Composable
private fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            strong = true,
            padding = 22.dp,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(Ar.logoutQuestion, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
                Text(Ar.logoutHint, fontSize = 13.sp, color = Palette.TextDim)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton(
                        text = Ar.logout,
                        onClick = onConfirm,
                        primary = true,
                        modifier = Modifier.weight(1f),
                    )
                    GlassButton(
                        text = Ar.cancel,
                        onClick = onDismiss,
                        autoFocus = true,
                        modifier = Modifier.weight(1f),
                    )
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
    onOpenCloudLogin: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val cloudUser = container.authManager.currentUser
    val account: Account? = container.repo.account
    val info: UserInfo? = container.repo.userInfo

    val homeChannelsSource by container.prefs.homeChannelsSource.collectAsStateWithLifecycle(initialValue = HomeChannelsSource.AUTO)
    val homeChannelsCategoryId by container.prefs.homeChannelsCategoryId.collectAsStateWithLifecycle(initialValue = "")
    val homeCustomChannelIds by container.prefs.homeCustomChannelIds.collectAsStateWithLifecycle(initialValue = emptySet())

    when (category) {
        "appearance", "customize", "themes" -> SettingsAppearancePanel(container = container)
        "custom_tv", "home_channels" -> SettingsCustomTvPanel(
            container = container,
            homeChannelsSource = homeChannelsSource,
            homeChannelsCategoryId = homeChannelsCategoryId,
            homeCustomChannelIds = homeCustomChannelIds,
        )
        "data_flow" -> SettingsDataFlowPanel(container = container)
        "cloud" -> {
            val cloudScope = androidx.compose.runtime.rememberCoroutineScope()
            val cloudBgStatus by container.catalogSync.bgEnrichStatus.collectAsStateWithLifecycle()
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                CloudAccountPanel(
                    email = cloudUser?.email,
                    onLogin = onOpenCloudLogin,
                    onLogout = {
                        container.authManager.signOut()
                        onLoggedOut()
                    }
                )
                androidx.compose.material3.Button(
                    onClick = {
                        cloudScope.launch {
                            container.catalogSync.pushLocalEnrichedToCloud()
                        }
                    },
                    enabled = !cloudBgStatus.active,
                ) {
                    Text(
                        if (cloudBgStatus.active) "جارٍ رفع البيانات للسحابة..."
                        else "رفع البيانات المحلية للسحابة (Room → Firebase)"
                    )
                }
            }
        }
        "sources" -> {
            val appCtx = LocalContext.current.applicationContext
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                SourcesPanel(container = container)
                ActiveSourceDetailsPanel(
                    container = container,
                    account = account,
                    info = info,
                    onSyncNow = {
                        container.syncCoordinator.triggerManualSync(appCtx)
                    }
                )
            }
        }
        "ai" -> {
            AiKeysPanel(container.aiKeys)
        }
    }
}

