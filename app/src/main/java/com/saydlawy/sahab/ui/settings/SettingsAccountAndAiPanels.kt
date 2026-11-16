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
internal fun CloudAccountPanel(email: String?, onLogout: () -> Unit, modifier: Modifier = Modifier) {
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
internal fun AccountPanel(
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
internal fun AppearancePanel(light: Boolean, onToggleLight: () -> Unit, modifier: Modifier = Modifier) {
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
internal fun AiKeysPanel(aiKeys: ApiKeyManager, modifier: Modifier = Modifier) {
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
            if (!aiKeys.isEncrypted) {
                Text(
                    "تنبيه: جهازك لا يدعم التشفير الآمن للمفاتيح، تم الحفظ في وضع احتياطي غير مشفر.",
                    fontSize = 12.sp,
                    color = Palette.Gold,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Palette.Gold.copy(alpha = 0.12f))
                        .padding(10.dp),
                )
            }
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

