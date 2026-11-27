package com.saydlawy.sahab.feature.torrent.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saydlawy.sahab.feature.torrent.data.TorrentFeatureController
import com.saydlawy.sahab.feature.torrent.domain.TorrentEngineMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TorrentSettingsPanel(
    controller: TorrentFeatureController,
    modifier: Modifier = Modifier,
) {
    val settings by controller.settings.collectAsState()
    val scope = rememberCoroutineScope()

    var torrUrl by remember(settings.torrServerUrl) { mutableStateOf(settings.torrServerUrl) }
    var torrUser by remember(settings.torrServerUser) { mutableStateOf(settings.torrServerUser) }
    var torrPass by remember(settings.torrServerPass) { mutableStateOf(settings.torrServerPass) }
    var debridKey by remember(settings.debridApiKey) { mutableStateOf(settings.debridApiKey) }
    var showDebridKey by remember { mutableStateOf(false) }

    var newAddonUrl by remember { mutableStateOf("") }
    var newAddonName by remember { mutableStateOf("") }
    var isTestingServer by remember { mutableStateOf(false) }
    var isAddingAddon by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A),
        border = BorderStroke(1.dp, Color(0xFF334155)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "مصادر 4K / Blu-ray (تورنت)",
                    color = Color(0xFF38BDF8),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "تشغيل مباشر بدون إعلانات عبر إضافات Stremio مع TorrServer أو Debrid.",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                )
            }

            SettingsToggleRow(
                title = "تفعيل مصادر 4K / Blu-ray",
                subtitle = "إظهار زر المصادر في صفحة تفاصيل الأفلام والمسلسلات",
                checked = settings.enabled,
                onCheckedChange = { controller.preferences.setEnabled(it) },
            )

            if (settings.enabled) {
                SettingsToggleRow(
                    title = "تفضيل الترجمة العربية المدمجة",
                    subtitle = "اختيار المسار العربي تلقائياً داخل ملفات MKV / Blu-ray",
                    checked = settings.preferArabicSubtitle,
                    onCheckedChange = { controller.preferences.setPreferArabicSubtitle(it) },
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "محرك التشغيل",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TorrentEngineMode.entries.forEach { mode ->
                            SettingsActionChip(
                                label = mode.labelAr,
                                selected = settings.engineMode == mode,
                                onClick = { controller.preferences.setEngineMode(mode) },
                            )
                        }
                    }
                }

                if (settings.engineMode == TorrentEngineMode.AUTO || settings.engineMode == TorrentEngineMode.TORRSERVER) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "إعدادات TorrServer (مجاني)",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DarkTextField(
                            value = torrUrl,
                            onValueChange = { torrUrl = it },
                            label = "عنوان السيرفر (مثال: http://127.0.0.1:8090 أو IP اللابتوب)",
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            DarkTextField(
                                value = torrUser,
                                onValueChange = { torrUser = it },
                                label = "اسم المستخدم (اختياري)",
                                modifier = Modifier.weight(1f),
                            )
                            DarkTextField(
                                value = torrPass,
                                onValueChange = { torrPass = it },
                                label = "كلمة المرور (اختياري)",
                                isSecret = true,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SettingsActionChip(
                                label = "حفظ إعدادات السيرفر",
                                selected = true,
                                onClick = {
                                    controller.preferences.setTorrServer(torrUrl, torrUser, torrPass)
                                    statusIsError = false
                                    statusMessage = "تم حفظ عنوان TorrServer."
                                },
                            )
                            SettingsActionChip(
                                label = if (isTestingServer) "جارٍ الاختبار..." else "اختبار الاتصال",
                                selected = false,
                                enabled = !isTestingServer,
                                onClick = {
                                    controller.preferences.setTorrServer(torrUrl, torrUser, torrPass)
                                    isTestingServer = true
                                    statusMessage = null
                                    scope.launch {
                                        val res = controller.testTorrServerConnection()
                                        isTestingServer = false
                                        res.onSuccess { msg ->
                                            statusIsError = false
                                            statusMessage = "TorrServer: $msg"
                                        }.onFailure { err ->
                                            statusIsError = true
                                            statusMessage = "فشل الاتصال بـ TorrServer: ${err.message ?: "تحقق من العنوان"}"
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                if (settings.engineMode != TorrentEngineMode.TORRSERVER) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF111827), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "مفتاح Debrid (Real-Debrid / TorBox)",
                            color = Color(0xFFE2E8F0),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        DarkTextField(
                            value = debridKey,
                            onValueChange = { debridKey = it },
                            label = "مفتاح API",
                            isSecret = !showDebridKey,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SettingsActionChip(
                                label = "حفظ المفتاح",
                                selected = true,
                                onClick = {
                                    controller.preferences.setDebridApiKey(debridKey)
                                    statusIsError = false
                                    statusMessage = "تم حفظ مفتاح Debrid محلياً."
                                },
                            )
                            SettingsActionChip(
                                label = if (showDebridKey) "إخفاء المفتاح" else "إظهار المفتاح",
                                selected = false,
                                onClick = { showDebridKey = !showDebridKey },
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111827), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "إضافات Stremio (لجلب روابط 4K / Blu-ray)",
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    DarkTextField(
                        value = newAddonUrl,
                        onValueChange = { newAddonUrl = it },
                        label = "رابط Manifest (https://.../manifest.json)",
                    )
                    DarkTextField(
                        value = newAddonName,
                        onValueChange = { newAddonName = it },
                        label = "اسم الإضافة (اختياري - يُجلب تلقائياً)",
                    )
                    SettingsActionChip(
                        label = if (isAddingAddon) "جارٍ التحقق والإضافة..." else "+ إضافة مصدر Stremio",
                        selected = true,
                        enabled = !isAddingAddon,
                        onClick = {
                            val cleanUrl = newAddonUrl.trim()
                            if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
                                statusIsError = true
                                statusMessage = "يجب أن يبدأ رابط الإضافة بـ http:// أو https://"
                                return@SettingsActionChip
                            }
                            isAddingAddon = true
                            statusMessage = null
                            scope.launch {
                                val resolvedName = newAddonName.trim().ifBlank {
                                    controller.addonClient.fetchManifestName(cleanUrl).getOrDefault("")
                                }
                                controller.preferences.addAddon(resolvedName, cleanUrl)
                                newAddonUrl = ""
                                newAddonName = ""
                                isAddingAddon = false
                                statusIsError = false
                                statusMessage = "تمت إضافة المصدر بنجاح."
                            }
                        },
                    )

                    if (settings.addons.isEmpty()) {
                        Text(
                            text = "لا توجد إضافات مضافة بعد. أضف رابط manifest.json لبدء جلب الجودات.",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            settings.addons.forEach { addon ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = addon.name,
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = addon.manifestUrl,
                                                color = Color(0xFF94A3B8),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        SettingsActionChip(
                                            label = if (addon.enabled) "مفعّل ✓" else "معطّل",
                                            selected = addon.enabled,
                                            onClick = { controller.preferences.toggleAddon(addon.id, !addon.enabled) },
                                        )
                                        SettingsActionChip(
                                            label = "حذف",
                                            selected = false,
                                            isDanger = true,
                                            onClick = { controller.preferences.removeAddon(addon.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage!!,
                        color = if (statusIsError) Color(0xFFFCA5A5) else Color(0xFF6EE7B7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(12.dp),
        color = if (focused) Color(0xFF1E293B) else Color(0xFF111827),
        border = BorderStroke(if (focused) 2.dp else 1.dp, if (focused) Color(0xFF38BDF8) else Color(0xFF334155)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(text = title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = Color(0xFF94A3B8), fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
private fun DarkTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color(0xFFE2E8F0),
            focusedBorderColor = Color(0xFF38BDF8),
            unfocusedBorderColor = Color(0xFF475569),
            focusedLabelColor = Color(0xFF38BDF8),
            unfocusedLabelColor = Color(0xFF94A3B8),
            cursorColor = Color(0xFF38BDF8),
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun SettingsActionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    isDanger: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isDanger && focused -> Color(0xFFB91C1C)
        isDanger -> Color(0xFF7F1D1D)
        selected -> Color(0xFF0284C7)
        focused -> Color(0xFF334155)
        else -> Color(0xFF1E293B)
    }
    val border = if (focused) Color(0xFF38BDF8) else Color(0xFF475569)
    Surface(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(if (focused) 2.dp else 1.dp, border),
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
