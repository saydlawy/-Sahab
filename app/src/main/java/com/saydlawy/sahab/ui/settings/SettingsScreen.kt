package com.saydlawy.sahab.ui.settings

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.ai.ApiKeyManager
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.UserInfo
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.player.SahabPlayer
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.appVersion
import com.saydlawy.sahab.util.formatDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val light by container.prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)
    val showRecents by container.prefs.showRecents.collectAsStateWithLifecycle(initialValue = true)
    val homeChannelsLayout by container.prefs.homeChannelsLayout.collectAsStateWithLifecycle(initialValue = HomeChannelsLayout.CAROUSEL)
    var confirmLogout by remember { mutableStateOf(false) }
    var loggingOut by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshed by remember { mutableStateOf(false) }
    var refreshError by remember { mutableStateOf<String?>(null) }
    var accountInfo by remember { mutableStateOf(container.repo.userInfo) }
    val account: Account? = container.repo.account
    val info: UserInfo? = accountInfo ?: container.repo.userInfo
    val context = LocalContext.current

    val performRefresh: () -> Unit = {
        if (!refreshing) {
            refreshing = true
            refreshed = false
            refreshError = null
            scope.launch {
                val result = container.repo.refreshSubscription()
                refreshing = false
                when (result) {
                    is Repository.SyncResult.Success -> {
                        accountInfo = container.repo.userInfo
                        refreshed = true
                    }
                    is Repository.SyncResult.Failure -> {
                        refreshError = result.reason
                    }
                    Repository.SyncResult.AlreadyRunning -> {
                        refreshError = "التحديث قيد التنفيذ بالفعل"
                    }
                    Repository.SyncResult.Skipped -> {
                        refreshed = true
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SahabTopBar(current = Routes.SETTINGS, onBack = onBack)

        val displayMode = LocalDisplayMode.current
        val maxWidth = LocalWindowInfo.current.widthDp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = displayMode != DisplayMode.PHONE_PORTRAIT && maxWidth >= 660.dp

            Column(
                modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AccountPanel(account, info, Modifier.weight(1f))
                        SystemControlsColumn(
                            light = light,
                            refreshing = refreshing,
                            refreshed = refreshed,
                            refreshError = refreshError,
                            onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } },
                            onRefresh = performRefresh,
                            onLogout = { confirmLogout = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    AccountPanel(account, info, Modifier.fillMaxWidth())
                    SystemControlsColumn(
                        light = light,
                        refreshing = refreshing,
                        refreshed = refreshed,
                        refreshError = refreshError,
                        onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } },
                        onRefresh = performRefresh,
                        onLogout = { confirmLogout = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HomeCustomizationPanel(
                    showRecents = showRecents,
                    homeChannelsLayout = homeChannelsLayout,
                    onToggleShowRecents = { scope.launch { container.prefs.setShowRecents(!showRecents) } },
                    onSelectLayout = { layout -> scope.launch { container.prefs.setHomeChannelsLayout(layout) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                AiKeysPanel(container.aiKeys, Modifier.fillMaxWidth())

                Text(
                    text = Ar.version(appVersion(context).ifBlank { "1.0" }),
                    fontSize = 13.sp,
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }

    if (confirmLogout) {
        Dialog(onDismissRequest = { confirmLogout = false }) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                strong = true,
                padding = 24.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        Ar.logoutQuestion,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Ar.logoutHint,
                        fontSize = 14.sp,
                        color = Palette.TextDim,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton(
                            text = if (loggingOut) "..." else Ar.logout,
                            onClick = {
                                if (!loggingOut) {
                                    loggingOut = true
                                    confirmLogout = false
                                    scope.launch {
                                        logout(container, context)
                                        onLoggedOut()
                                    }
                                }
                            },
                            primary = true,
                        )
                        GlassButton(text = Ar.cancel, onClick = { confirmLogout = false }, autoFocus = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountPanel(account: Account?, info: UserInfo?, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        strong = true,
        padding = 22.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Royal.copy(alpha = 0.28f))
                        .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Person, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(Ar.account, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text(account?.user.orEmpty().ifBlank { "-" }, fontSize = 13.sp, color = Palette.Glow)
                }
            }
            val activeStatus = info?.status.orEmpty().ifBlank { "Active" }
            Tag(activeStatus, Palette.Emerald)
        }

        Spacer(Modifier.height(18.dp))

        InfoRow(Ar.server, hostOf(account?.server.orEmpty()))
        InfoRow(Ar.username, account?.user.orEmpty())
        if (info != null) {
            InfoRow(Ar.status, if (info.status.isBlank()) "Active" else info.status)
            val expiry = info.expiry
            InfoRow(
                Ar.expiry,
                if (expiry == null || expiry <= 0L) Ar.subscriptionOpenShort else formatDate(expiry),
            )
            if (info.maxConnections.isNotBlank()) {
                InfoRow(Ar.connections, "${info.activeConnections.ifBlank { "0" }} / ${info.maxConnections}")
            }
        }
    }
}

@Composable
private fun SystemControlsColumn(
    light: Boolean,
    refreshing: Boolean,
    refreshed: Boolean,
    refreshError: String?,
    onToggleLight: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(
            onClick = onToggleLight,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            selected = light,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    DotTitle(Ar.lightMode, Palette.Glow, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(Ar.lightModeHint, fontSize = 13.sp, color = Palette.TextDim)
                }
                Switch(
                    checked = light,
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

        GlassButton(
            text = when {
                refreshing -> "جاري تحديث الاشتراك والقنوات..."
                refreshed -> Ar.refreshed
                else -> "تحديث الاشتراك والقنوات (${Ar.refreshData})"
            },
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            icon = Ic.Refresh,
            primary = true,
        )
        if (!refreshError.isNullOrBlank()) {
            Text(refreshError, fontSize = 12.sp, color = Palette.Danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        GlassButton(
            text = Ar.logout,
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            icon = Ic.Close,
        )
    }
}

@Composable
private fun HomeCustomizationPanel(
    showRecents: Boolean,
    homeChannelsLayout: HomeChannelsLayout,
    onToggleShowRecents: () -> Unit,
    onSelectLayout: (HomeChannelsLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        strong = true,
        padding = 22.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Royal.copy(alpha = 0.28f))
                        .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Tv, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("تخصيص الشاشة الرئيسية", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text("التحكم في أقسام الشاشة الرئيسية وشكل القنوات", fontSize = 13.sp, color = Palette.TextDim)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 1. Switch for RecentsStrip
        GlassCard(
            onClick = onToggleShowRecents,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            selected = showRecents,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    DotTitle("عرض قسم تابع المشاهدة", Palette.Glow, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("إظهار شريط القنوات والمحتوى المشاهد مؤخراً", fontSize = 12.sp, color = Palette.TextDim)
                }
                Switch(
                    checked = showRecents,
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

        Spacer(Modifier.height(16.dp))

        // 2. RadioGroup for Featured Channels Layout
        Column(Modifier.fillMaxWidth()) {
            DotTitle("شكل قنوات القائمة المميزة", Palette.Royal, fontSize = 15.sp)
            Spacer(Modifier.height(6.dp))
            Text("اختر أسلوب عرض القنوات الرئيسية الموصى بها", fontSize = 12.sp, color = Palette.TextDim)
            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Option Carousel
                val isCarousel = homeChannelsLayout == HomeChannelsLayout.CAROUSEL
                GlassCard(
                    onClick = { onSelectLayout(HomeChannelsLayout.CAROUSEL) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    selected = isCarousel,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isCarousel,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Palette.Glow,
                                unselectedColor = Palette.TextDim,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("سلايدر متحرك", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isCarousel) Palette.Text else Palette.TextDim)
                            Text("بانورامي ثلاثي الأبعاد", fontSize = 11.sp, color = Palette.TextDim)
                        }
                    }
                }

                // Option Grid
                val isGrid = homeChannelsLayout == HomeChannelsLayout.GRID
                GlassCard(
                    onClick = { onSelectLayout(HomeChannelsLayout.GRID) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    selected = isGrid,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isGrid,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Palette.Glow,
                                unselectedColor = Palette.TextDim,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("شبكة أيقونات", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isGrid) Palette.Text else Palette.TextDim)
                            Text("مربعات متراصة منظمة", fontSize = 11.sp, color = Palette.TextDim)
                        }
                    }
                }
            }
        }
    }
}

private suspend fun logout(container: AppContainer, context: Context) {
    container.playback = null
    withContext(Dispatchers.IO) {
        container.repo.clearCache(clearDisk = true)
        container.repo.setAccount(null)
        container.prefs.clearAccount()
        SahabPlayer.clearMediaCache(context)
    }
}

private fun hostOf(server: String): String = server.substringAfter("://").trimEnd('/')

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
    }
}

@Composable
private fun AiKeysPanel(aiKeys: ApiKeyManager, modifier: Modifier = Modifier) {
    val keys by aiKeys.keys.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by aiKeys.activeId.collectAsStateWithLifecycle(initialValue = "")
    var showAdd by remember { mutableStateOf(false) }
    var newKeyText by remember { mutableStateOf("") }
    var newKeyLabel by remember { mutableStateOf("") }

    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        strong = true,
        padding = 22.dp,
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DotTitle("مفاتيح الذكاء الاصطناعي", Palette.Glow, fontSize = 16.sp)
            Text(
                "أضف مفاتيح Gemini لتوليد قصص الأفلام والمسلسلات عند الحاجة.",
                fontSize = 12.sp,
                color = Palette.TextDim,
            )

            keys.forEach { key ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (key.id == activeId) Palette.Royal.copy(alpha = 0.20f)
                            else Color.White.copy(alpha = 0.04f)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(key.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text(
                            key.value.take(8) + "…" + key.value.takeLast(4),
                            fontSize = 11.sp,
                            color = Palette.TextDim,
                        )
                        if (key.last429Ms > 0) {
                            Text("⚠️ استنفد الحصة مؤخرًا", fontSize = 10.sp, color = Color(0xFFF59E0B))
                        }
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
                onClick = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
            )
        }
    }

    if (showAdd) {
        Dialog(onDismissRequest = { showAdd = false }) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                strong = true,
                padding = 22.dp,
            ) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("مفتاح Gemini جديد", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    OutlinedTextField(
                        value = newKeyLabel,
                        onValueChange = { newKeyLabel = it },
                        label = { Text("اسم مختصر (اختياري)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = newKeyText,
                        onValueChange = { newKeyText = it },
                        label = { Text("المفتاح (AIza...)") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "إضافة",
                            onClick = {
                                if (aiKeys.addKey(newKeyText, newKeyLabel) != null) {
                                    newKeyText = ""
                                    newKeyLabel = ""
                                    showAdd = false
                                }
                            },
                            primary = true,
                        )
                        GlassButton(text = "إلغاء", onClick = { showAdd = false })
                    }
                }
            }
        }
    }
}
