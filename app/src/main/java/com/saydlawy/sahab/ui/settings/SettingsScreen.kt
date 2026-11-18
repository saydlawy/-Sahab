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
    val scope = rememberCoroutineScope()
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
        "cloud" -> {
            CloudAccountPanel(
                email = cloudUser?.email,
                onLogin = onOpenCloudLogin,
                onLogout = {
                    container.authManager.signOut()
                    onLoggedOut()
                }
            )
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
                        scope.launch(Dispatchers.IO) {
                            container.repo.syncCatalog(force = true)
                            container.catalogSync.syncMovies(container.repo)
                            val chStats = container.channelSync.sync(container.repo, forceFullRefresh = true)
                            if (chStats.isSuccess) container.prefs.setLastChannelSyncTime(System.currentTimeMillis())

                            val epgStats = if (chStats.isSuccess || chStats.total > 0) {
                                container.epgSync.sync().also { st ->
                                    if (st.isSuccess) container.prefs.setLastEpgSyncTime(System.currentTimeMillis())
                                }
                            } else {
                                com.saydlawy.sahab.data.channel.EpgSync.Stats(errors = listOf("skipped"))
                            }

                            val msg = buildString {
                                append("القنوات: ")
                                when {
                                    chStats.isSuccess && chStats.total > 0 ->
                                        append("${chStats.total} قناة (${chStats.enriched} بـ EPG)")
                                    chStats.hasPartialFailure -> append("جزئي (${chStats.errors.size} خطأ)")
                                    else -> append("فشل")
                                }
                                append("\nالبرامج: ")
                                when {
                                    epgStats.isSuccess && epgStats.programsUpserted > 0 ->
                                        append("${epgStats.programsUpserted} برنامج")
                                    epgStats.hasPartialFailure -> append("جزئي (${epgStats.errors.size} خطأ)")
                                    else -> append("فشل")
                                }
                            }
                            withContext(Dispatchers.Main) {
                                Toast.makeText(appCtx, msg, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
        }
        "ai" -> {
            AiKeysPanel(container.aiKeys)
        }
    }
}

