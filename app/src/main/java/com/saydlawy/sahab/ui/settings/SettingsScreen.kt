package com.saydlawy.sahab.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.Repository
import com.saydlawy.sahab.data.UserInfo
import com.saydlawy.sahab.data.ai.ApiKeyManager
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.*
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
    val cloudUser = container.authManager.currentUser

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
                    is Repository.SyncResult.Failure -> refreshError = result.reason
                    Repository.SyncResult.AlreadyRunning -> refreshError = "التحديث قيد التنفيذ بالفعل"
                    Repository.SyncResult.Skipped -> refreshed = true
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
                .padding(horizontal = 20.dp, vertical = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = displayMode != DisplayMode.PHONE_PORTRAIT && maxWidth >= 660.dp

            Column(
                modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                // --- قسم الحسابات ---
                SectionHeader("الحساب والملف الشخصي", Ic.Person)
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        CloudAccountPanel(cloudUser?.email, onLogout = {
                            container.authManager.signOut()
                            onLoggedOut()
                        }, modifier = Modifier.weight(1f))
                        
                        AccountPanel(account, info, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CloudAccountPanel(cloudUser?.email, onLogout = {
                            container.authManager.signOut()
                            onLoggedOut()
                        })
                        AccountPanel(account, info)
                    }
                }

                // --- قسم التخصيص والمظهر ---
                SectionHeader("التخصيص والشاشة الرئيسية", Ic.Tv)
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AppearancePanel(
                            light = light, 
                            onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } },
                            modifier = Modifier.weight(1f)
                        )
                        HomeCustomizationPanel(
                            showRecents = showRecents,
                            homeChannelsLayout = homeChannelsLayout,
                            onToggleShowRecents = { scope.launch { container.prefs.setShowRecents(!showRecents) } },
                            onSelectLayout = { layout -> scope.launch { container.prefs.setHomeChannelsLayout(layout) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        AppearancePanel(
                            light = light, 
                            onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } }
                        )
                        HomeCustomizationPanel(
                            showRecents = showRecents,
                            homeChannelsLayout = homeChannelsLayout,
                            onToggleShowRecents = { scope.launch { container.prefs.setShowRecents(!showRecents) } },
                            onSelectLayout = { layout -> scope.launch { container.prefs.setHomeChannelsLayout(layout) } },
                        )
                    }
                }

                // --- قسم الذكاء الاصطناعي ---
                SectionHeader("الذكاء الاصطناعي", Ic.Cloud)
                AiKeysPanel(container.aiKeys, Modifier.fillMaxWidth())

                // --- قسم إدارة البيانات ---
                SectionHeader("إدارة البيانات والاشتراك", Ic.Refresh)
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    strong = true,
                    padding = 22.dp,
                ) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton(
                            text = when {
                                refreshing -> "جاري تحديث الاشتراك والقنوات..."
                                refreshed -> Ar.refreshed
                                else -> "تحديث الاشتراك والقنوات (${Ar.refreshData})"
                            },
                            onClick = performRefresh,
                            modifier = Modifier.fillMaxWidth(),
                            icon = Ic.Refresh,
                            primary = true,
                        )
                        if (!refreshError.isNullOrBlank()) {
                            Text(refreshError, fontSize = 12.sp, color = Palette.Danger, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        GlassButton(
                            text = "تسجيل الخروج من الاشتراك الحالي",
                            onClick = { confirmLogout = true },
                            modifier = Modifier.fillMaxWidth(),
                            icon = Ic.Close,
                        )
                    }
                }

                Text(
                    text = Ar.version(appVersion(context).ifBlank { "1.0" }),
                    fontSize = 13.sp,
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                )
            }
        }
    }

    // نافذة تأكيد تسجيل الخروج
    if (confirmLogout) {
        Dialog(onDismissRequest = { confirmLogout = false }) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                strong = true,
                padding = 24.dp,
            ) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Ar.logoutQuestion, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(Ar.logoutHint, fontSize = 14.sp, color = Palette.TextDim, textAlign = TextAlign.Center)
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
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size(18.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Glow)
    }
}

@Composable
private fun CloudAccountPanel(email: String?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Blue.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Ic.Cloud, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("الحساب السحابي", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text(if (email != null) "متصل" else "غير متصل", fontSize = 13.sp, color = if (email != null) Palette.Emerald else Palette.Gold)
                    }
                }
                if (email != null) {
                    Tag("Cloud", Palette.Blue)
                }
            }
            
            if (email != null) {
                InfoRow("البريد الإلكتروني", email)
                GlassButton(text = "تسجيل الخروج من السحابة", onClick = onLogout, modifier = Modifier.fillMaxWidth(), icon = Ic.Close)
            } else {
                Text("لم يتم تسجيل الدخول، بياناتك لن تحفظ سحابياً.", fontSize = 13.sp, color = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun AccountPanel(account: Account?, info: UserInfo?, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Royal.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Person, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("اشتراك المشاهدة", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text(account?.user.orEmpty().ifBlank { "-" }, fontSize = 13.sp, color = Palette.Glow)
                }
            }
            val activeStatus = info?.status.orEmpty().ifBlank { "Active" }
            Tag(activeStatus, Palette.Emerald)
        }

        Spacer(Modifier.height(18.dp))
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
    }
}

@Composable
private fun AppearancePanel(light: Boolean, onToggleLight: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Royal.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Sun, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("المظهر", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text("وضع الأداء الخفيف", fontSize = 13.sp, color = Palette.TextDim)
                }
            }
            Switch(
                checked = light,
                onCheckedChange = { onToggleLight() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Palette.Royal,
                    uncheckedThumbColor = Palette.TextDim,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                ),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text("يوقف حركة الخلفية والتأثيرات البصرية لتسريع الأجهزة الضعيفة.", fontSize = 12.sp, color = Palette.TextDim)
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
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Royal.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Tv, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("الشاشة الرئيسية", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text("أقسام الواجهة وشكل القنوات", fontSize = 13.sp, color = Palette.TextDim)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        
        GlassCard(onClick = onToggleShowRecents, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), selected = showRecents) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    DotTitle("عرض قسم تابع المشاهدة", Palette.Glow, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("إظهار شريط القنوات والمحتوى المشاهد مؤخراً", fontSize = 12.sp, color = Palette.TextDim)
                }
                Switch(
                    checked = showRecents,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White, checkedTrackColor = Palette.Royal,
                        uncheckedThumbColor = Palette.TextDim, uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                    ),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Column(Modifier.fillMaxWidth()) {
            DotTitle("شكل قنوات القائمة المميزة", Palette.Royal, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val isCarousel = homeChannelsLayout == HomeChannelsLayout.CAROUSEL
                GlassCard(onClick = { onSelectLayout(HomeChannelsLayout.CAROUSEL) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), selected = isCarousel) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isCarousel, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Palette.Glow, unselectedColor = Palette.TextDim))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("سلايدر", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isCarousel) Palette.Text else Palette.TextDim)
                            Text("بانورامي", fontSize = 11.sp, color = Palette.TextDim)
                        }
                    }
                }
                val isGrid = homeChannelsLayout == HomeChannelsLayout.GRID
                GlassCard(onClick = { onSelectLayout(HomeChannelsLayout.GRID) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp), selected = isGrid) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = isGrid, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Palette.Glow, unselectedColor = Palette.TextDim))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("شبكة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isGrid) Palette.Text else Palette.TextDim)
                            Text("أيقونات", fontSize = 11.sp, color = Palette.TextDim)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AiKeysPanel(aiKeys: ApiKeyManager, modifier: Modifier = Modifier) {
    val keys by aiKeys.keys.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeId by aiKeys.activeId.collectAsStateWithLifecycle(initialValue = "")
    var showAdd by remember { mutableStateOf(false) }
    var newKeyText by remember { mutableStateOf("") }
    var newKeyLabel by remember { mutableStateOf("") }

    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DotTitle("مفاتيح Gemini", Palette.Glow, fontSize = 16.sp)
            Text("أضف مفاتيح لتوليد قصص الأفلام عند الحاجة.", fontSize = 12.sp, color = Palette.TextDim)

            keys.forEach { key ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
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

            GlassButton(text = "+ إضافة مفتاح", onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth(), primary = true)
        }
    }

    if (showAdd) {
        Dialog(onDismissRequest = { showAdd = false }) {
            GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("مفتاح Gemini جديد", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    
                    // استخدام GlassTextField بدلاً من OutlinedTextField
                    GlassTextField(value = newKeyLabel, onValueChange = { newKeyLabel = it }, placeholder = "اسم مختصر (اختياري)", modifier = Modifier.fillMaxWidth())
                    GlassTextField(value = newKeyText, onValueChange = { newKeyText = it }, placeholder = "المفتاح (AIza...)", modifier = Modifier.fillMaxWidth())
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(
                            text = "إضافة",
                            onClick = {
                                if (aiKeys.addKey(newKeyText, newKeyLabel) != null) {
                                    newKeyText = ""; newKeyLabel = ""; showAdd = false
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
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 15.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
    }
}
