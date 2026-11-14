package com.saydlawy.sahab.ui.settings

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
    val context = LocalContext.current
    val displayMode = LocalDisplayMode.current
    val isWide = displayMode == DisplayMode.TABLET_OR_TV
    
    // تحديد القسم المفتوح حالياً (null يعني القائمة الرئيسية على الهاتف)
    var selectedCategory by remember { mutableStateOf<String?>(if (isWide) "account" else null) }

    // التعامل مع زر الرجوع في الهاتف
    BackHandler(enabled = !isWide && selectedCategory != null) {
        selectedCategory = null
    }

    Column(Modifier.fillMaxSize()) {
        if (isWide || selectedCategory == null) {
            SahabTopBar(current = Routes.SETTINGS, onBack = if (isWide) onBack else { { onBack() } })
        }

        if (isWide) {
            // --- تصميم التلفاز والتابلت: قائمة جانبية وتفاصيل ---
            Row(
                Modifier.fillMaxSize().padding(20.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // القائمة الجانبية
                Column(
                    Modifier
                        .weight(0.32f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SettingsMenuItem("الحساب والاشتراك", "المزامنة السحابية وبيانات Xtream", Ic.Person, selectedCategory == "account") { selectedCategory = "account" }
                    SettingsMenuItem("التخصيص والمظهر", "الشاشة الرئيسية، القنوات، والأداء", Ic.Tv, selectedCategory == "customize") { selectedCategory = "customize" }
                    SettingsMenuItem("الذكاء الاصطناعي", "إدارة مفاتيح Gemini", Ic.Cloud, selectedCategory == "ai") { selectedCategory = "ai" }
                    
                    Spacer(Modifier.height(8.dp))
                    GlassButton(text = Ar.logout, onClick = { onLoggedOut() }, icon = Ic.Close, modifier = Modifier.fillMaxWidth())
                }

                // منطقة التفاصيل
                Box(Modifier.weight(0.7f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    SettingsDetailContent(selectedCategory, container, context, onLoggedOut)
                }
            }
        } else {
            // --- تصميم الهاتف ---
            if (selectedCategory == null) {
                // قائمة الهاتف
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SettingsMenuItem("الحساب والاشتراك", "المزامنة السحابية وبيانات Xtream", Ic.Person, false) { selectedCategory = "account" }
                    SettingsMenuItem("التخصيص والمظهر", "الشاشة الرئيسية، القنوات، والأداء", Ic.Tv, false) { selectedCategory = "customize" }
                    SettingsMenuItem("الذكاء الاصطناعي", "إدارة مفاتيح Gemini", Ic.Cloud, false) { selectedCategory = "ai" }
                    
                    Spacer(Modifier.height(24.dp))
                    GlassButton(text = Ar.logout, onClick = { onLoggedOut() }, icon = Ic.Close, modifier = Modifier.fillMaxWidth())
                    
                    Text(
                        text = Ar.version(appVersion(context).ifBlank { "1.0" }),
                        fontSize = 13.sp, color = Palette.TextDim, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    )
                }
            } else {
                // صفحة تفاصيل الهاتف
                Column(Modifier.fillMaxSize()) {
                    SahabTopBar(current = Routes.SETTINGS, onBack = { selectedCategory = null })
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
                    ) {
                        SettingsDetailContent(selectedCategory, container, context, onLoggedOut)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(title: String, subtitle: String, icon: ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    GlassCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
        shape = RoundedCornerShape(20.dp),
        selected = isSelected || focused,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected || focused) Palette.Royal.copy(alpha = 0.4f) else Palette.Royal.copy(alpha = 0.2f))
                    .border(1.dp, Palette.FocusCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Palette.FocusCyan, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                Text(subtitle, fontSize = 12.sp, color = Palette.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // سهم التوجيه
            Text("<", fontSize = 22.sp, color = Palette.Glow, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SettingsDetailContent(category: String?, container: AppContainer, context: Context, onLoggedOut: () -> Unit) {
    val scope = rememberCoroutineScope()
    val cloudUser = container.authManager.currentUser
    val account: Account? = container.repo.account
    val info: UserInfo? = container.repo.userInfo
    var confirmLogout by remember { mutableStateOf(false) }

    when (category) {
        "account" -> {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                CloudAccountPanel(cloudUser?.email, onLogout = { container.authManager.signOut() })
                AccountPanel(account, info)
            }
        }
        "customize" -> {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                val light by container.prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)
                val showRecents by container.prefs.showRecents.collectAsStateWithLifecycle(initialValue = true)
                val homeChannelsLayout by container.prefs.homeChannelsLayout.collectAsStateWithLifecycle(initialValue = HomeChannelsLayout.CAROUSEL)
                
                AppearancePanel(light = light, onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } })
                HomeCustomizationPanel(
                    showRecents = showRecents,
                    homeChannelsLayout = homeChannelsLayout,
                    onToggleShowRecents = { scope.launch { container.prefs.setShowRecents(!showRecents) } },
                    onSelectLayout = { layout -> scope.launch { container.prefs.setHomeChannelsLayout(layout) } },
                )
            }
        }
        "ai" -> {
            AiKeysPanel(container.aiKeys)
        }
    }
}

// --- المكونات السابقة (لم نغيرها) ---

@Composable
private fun CloudAccountPanel(email: String?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Blue.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Ic.Cloud, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("الحساب السحابي", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text(if (email != null) "متصل" else "غير متصل", fontSize = 13.sp, color = if (email != null) Palette.Emerald else Palette.Gold)
                    }
                }
                if (email != null) Tag("Cloud", Palette.Blue)
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
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Royal.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
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
            if (info.maxConnections.isNotBlank()) InfoRow("الأجهزة المتصلة", "${info.activeConnections.ifBlank { "0" }} / ${info.maxConnections}")
        }
    }
}

@Composable
private fun AppearancePanel(light: Boolean, onToggleLight: () -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Royal.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Icon(Ic.Sun, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("المظهر", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text("وضع الأداء الخفيف", fontSize = 13.sp, color = Palette.TextDim)
                }
            }
            Switch(checked = light, onCheckedChange = { onToggleLight() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Palette.Royal, uncheckedThumbColor = Palette.TextDim, uncheckedTrackColor = Color.White.copy(alpha = 0.12f)))
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
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Royal.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
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
                Switch(checked = showRecents, onCheckedChange = null, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Palette.Royal, uncheckedThumbColor = Palette.TextDim, uncheckedTrackColor = Color.White.copy(alpha = 0.12f)))
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
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (key.id == activeId) Palette.Royal.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.04f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(key.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text(key.value.take(8) + "…" + key.value.takeLast(4), fontSize = 11.sp, color = Palette.TextDim)
                    }
                    if (key.id == activeId) Tag("نشط", Palette.Emerald) else GlassButton(text = "تفعيل", onClick = { aiKeys.setActive(key.id) })
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
                    GlassTextField(value = newKeyLabel, onValueChange = { newKeyLabel = it }, placeholder = "اسم مختصر (اختياري)", modifier = Modifier.fillMaxWidth())
                    GlassTextField(value = newKeyText, onValueChange = { newKeyText = it }, placeholder = "المفتاح (AIza...)", modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton(text = "إضافة", onClick = { if (aiKeys.addKey(newKeyText, newKeyLabel) != null) { newKeyText = ""; newKeyLabel = ""; showAdd = false } }, primary = true)
                        GlassButton(text = "إلغاء", onClick = { showAdd = false })
                    }
                }
            }
        }
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
