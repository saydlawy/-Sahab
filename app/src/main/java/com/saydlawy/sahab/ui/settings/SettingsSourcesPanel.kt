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


internal fun hostOf(server: String): String = server.substringAfter("://").trimEnd('/')

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
    }
}



@Composable
internal fun SourcesPanel(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val activeAccount by container.prefs.account.collectAsStateWithLifecycle(initialValue = null)
    var accounts by remember { mutableStateOf<List<Account>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isSwitching by remember { mutableStateOf(false) }

    LaunchedEffect(activeAccount) {
        accounts = container.prefs.getAccounts()
        isSwitching = false
    }

    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Palette.Royal.copy(alpha = 0.28f)).border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Ic.Tv, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("المصادر والاشتراكات", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                        Text("اختر المصدر النشط أو أضف مصدراً جديداً", fontSize = 13.sp, color = Palette.TextDim)
                    }
                }
                GlassButton(
                    text = "+ إضافة",
                    onClick = { showAddDialog = true },
                    primary = true,
                    icon = Ic.Cloud
                )
            }

            accounts.forEach { acc ->
                val isActive = acc.id == activeAccount?.id
                GlassCard(
                    onClick = {
                        if (!isActive && !isSwitching) {
                            isSwitching = true
                            container.switchAccount(acc)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    selected = isActive
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(acc.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(if (acc.type == "M3U") "رابط M3U مباشر" else acc.server, fontSize = 12.sp, color = Palette.TextDim)
                        }
                        
                        if (!isActive) {
                            if (isSwitching) {
                                CircularProgressIndicator(color = Palette.Glow, modifier = Modifier.size(24.dp))
                            } else {
                                GlassButton(text = "تفعيل", onClick = {
                                    isSwitching = true
                                    scope.launch(Dispatchers.IO) {
                                        // التبديل الذكي: مسح الكاش ثم تفعيل الحساب الجديد
                                        container.switchAccount(acc)
                                        withContext(Dispatchers.Main) { isSwitching = false }
                                    }
                                })
                            }
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Tag("نشط", Palette.Emerald)
                            Spacer(Modifier.width(8.dp))
                        }
                        
                        GlassButton(text = "حذف", onClick = {
                            scope.launch {
                                container.prefs.deleteAccount(acc.id)
                                val newList = container.prefs.getAccounts()
                                accounts = newList
                                container.userSyncService.saveAccounts(newList)
                                if (acc.id == activeAccount?.id) {
                                    val next = newList.firstOrNull()
                                    if (next != null) {
                                        container.switchAccount(next)
                                    } else {
                                        container.clearAllLocalData(clearPrefsAccount = true)
                                    }
                                }
                            }
                        })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            container = container,
            onDismiss = { showAddDialog = false },
            onAdded = { newAcc ->
                scope.launch(Dispatchers.IO) {
                    accounts = container.prefs.getAccounts()
                    container.userSyncService.saveAccounts(accounts)
                    container.switchAccount(newAcc)
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
internal fun AddSourceDialog(container: AppContainer, onDismiss: () -> Unit, onAdded: (Account) -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("XTREAM") }
    var name by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var m3uUrl by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 22.dp) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("إضافة مصدر جديد", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassCard(onClick = { selectedTab = "XTREAM" }, modifier = Modifier.weight(1f), selected = selectedTab == "XTREAM") {
                        Text("سيرفر Xtream", modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center, color = if (selectedTab == "XTREAM") Color.White else Palette.TextDim)
                    }
                    GlassCard(onClick = { selectedTab = "M3U" }, modifier = Modifier.weight(1f), selected = selectedTab == "M3U") {
                        Text("رابط M3U", modifier = Modifier.fillMaxWidth().padding(12.dp), textAlign = TextAlign.Center, color = if (selectedTab == "M3U") Color.White else Palette.TextDim)
                    }
                }

                GlassTextField(value = name, onValueChange = { name = it }, placeholder = "اسم المصدر (مثال: اشتراكي)", modifier = Modifier.fillMaxWidth())
                
                if (selectedTab == "XTREAM") {
                    GlassTextField(value = server, onValueChange = { server = it }, placeholder = "عنوان السيرفر (http://...)", modifier = Modifier.fillMaxWidth())
                    GlassTextField(value = user, onValueChange = { user = it }, placeholder = "اسم المستخدم", modifier = Modifier.fillMaxWidth())
                    GlassTextField(value = pass, onValueChange = { pass = it }, placeholder = "كلمة المرور", password = true, modifier = Modifier.fillMaxWidth())
                } else {
                    GlassTextField(value = m3uUrl, onValueChange = { m3uUrl = it }, placeholder = "رابط M3U المباشر (http://...)", modifier = Modifier.fillMaxWidth())
                }
                errorMsg?.let { msg ->
                    Text(msg, fontSize = 12.sp, color = Palette.Gold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(
                        text = if (loading) "جاري الفحص..." else "حفظ",
                        onClick = {
                            if (loading) return@GlassButton
                            val validUrl = if (selectedTab == "XTREAM") {
                                server.trim().startsWith("http") && user.isNotBlank() && pass.isNotBlank()
                            } else {
                                m3uUrl.trim().startsWith("http")
                            }
                            if (!validUrl) {
                                errorMsg = "تأكد من إدخال رابط صحيح يبدأ بـ http مع كامل البيانات."
                                return@GlassButton
                            }
                            errorMsg = null
                            loading = true
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val acc = if (selectedTab == "XTREAM") {
                                        Account(name = name.ifBlank { "Xtream Server" }, type = "XTREAM", server = server.trim(), user = user.trim(), pass = pass)
                                    } else {
                                        Account(name = name.ifBlank { "M3U List" }, type = "M3U", m3uUrl = m3uUrl.trim())
                                    }
                                    container.repo.login(acc)
                                    container.prefs.saveAccount(acc)
                                    withContext(Dispatchers.Main) {
                                        loading = false
                                        onAdded(acc)
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        loading = false
                                        errorMsg = e.message?.take(120) ?: "تعذر الاتصال بالمصدر، تأكد من الرابط والبيانات."
                                    }
                                }
                            }
                        },
                        primary = true,
                        modifier = Modifier.weight(1f)
                    )
                    GlassButton(text = "إلغاء", onClick = onDismiss, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun ActiveSourceDetailsPanel(
    container: AppContainer,
    account: Account?,
    info: UserInfo?,
    onSyncNow: () -> Unit,
) {
    if (account == null) return

    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
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
                    Text("تفاصيل المصدر النشط", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text(if (account.type == "M3U") "رابط M3U" else account.server, fontSize = 13.sp, color = Palette.Glow)
                }
            }
            if (account.type == "XTREAM" && info != null) {
                Tag(info.status.ifBlank { "Active" }, Palette.Emerald)
            }
        }
        Spacer(Modifier.height(14.dp))
        
        if (account.type == "XTREAM") {
            InfoRow("الخادم", hostOf(account.server))
            InfoRow("اسم المستخدم", account.user)
            if (info != null) {
                InfoRow("الحالة", if (info.status.isBlank()) "Active" else info.status)
                val expiry = info.expiry
                InfoRow("ينتهي الاشتراك", if (expiry == null || expiry <= 0L) Ar.subscriptionOpenShort else formatDate(expiry))
                if (info.maxConnections.isNotBlank()) {
                    val maxText = if (info.maxConnections == "0") "غير محدود" else info.maxConnections
                    InfoRow("الأجهزة المتصلة", "${info.activeConnections.ifBlank { "0" }} / $maxText")
                }
            }
        } else {
            InfoRow("الرابط", account.m3uUrl.take(40) + "...")
        }
        
        Spacer(Modifier.height(12.dp))
        GlassButton(
            text = "تحديث ومزامنة القنوات الآن",
            onClick = onSyncNow,
            icon = Ic.Refresh,
            primary = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}


