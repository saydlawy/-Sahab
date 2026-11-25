package com.saydlawy.sahab.feature.instantwatch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saydlawy.sahab.feature.instantwatch.player.PlayerSessionManager
import kotlinx.coroutines.launch

@Composable
fun InstantWatchSettingsPanel(
    sessionManager: PlayerSessionManager,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val settings by sessionManager.preferences.settings.collectAsState()
    var feedback by remember { mutableStateOf<String?>(null) }

    var subdlKey by remember(settings.subdlApiKey) { mutableStateOf(settings.subdlApiKey) }
    var openSubKey by remember(settings.openSubtitlesApiKey) { mutableStateOf(settings.openSubtitlesApiKey) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF111827), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "إعدادات المشاهدة الفورية",
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "وحدة مشاهدة معزولة بالكامل تعتمد على TMDB والمشغل المحمي دون التأثير على IPTV.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
        )

        SettingToggleRow(
            title = "تفعيل المشاهدة الفورية",
            subtitle = "إظهار وإتاحة ميزة المشاهدة الفورية للمستخدم المسجل",
            checked = settings.enabled,
            onCheckedChange = { enabled ->
                sessionManager.preferences.setEnabled(enabled)
                if (!enabled) sessionManager.onServiceDisabledOrSignedOut()
            },
        )

        SettingToggleRow(
            title = "الترجمة العربية التلقائية",
            subtitle = "البحث التلقائي عن الترجمة العربية عبر المحرك المستقل",
            checked = settings.autoArabicSubtitles,
            onCheckedChange = { sessionManager.preferences.setAutoArabicSubtitles(it) },
        )

        SettingToggleRow(
            title = "حفظ تقدم المشاهدة محلياً",
            subtitle = "الاحتفاظ بآخر المشاهدات وموضع التوقف على الجهاز فقط",
            checked = settings.saveProgressLocally,
            onCheckedChange = { sessionManager.preferences.setSaveProgressLocally(it) },
        )

        HorizontalDivider(color = Color(0xFF1E293B))

        Text(
            text = "مفاتيح محركات الترجمة (اختياري)",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "أضف مفتاح SubDL أو OpenSubtitles الرسمي للحصول على ترجمات عربية بشرية أدق. بدون مفاتيح يعمل المصدر المجاني تلقائياً.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF94A3B8),
        )

        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = Color(0xFF3B82F6),
            unfocusedBorderColor = Color(0xFF334155),
            focusedLabelColor = Color(0xFF93C5FD),
            unfocusedLabelColor = Color(0xFF94A3B8),
            cursorColor = Color(0xFF60A5FA),
        )

        OutlinedTextField(
            value = subdlKey,
            onValueChange = { subdlKey = it },
            label = { Text("مفتاح SubDL API") },
            placeholder = { Text("اختياري — الأفضل للترجمات العربية", color = Color(0xFF64748B)) },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = openSubKey,
            onValueChange = { openSubKey = it },
            label = { Text("مفتاح OpenSubtitles API") },
            placeholder = { Text("اختياري — لاستبعاد الترجمة الآلية", color = Color(0xFF64748B)) },
            singleLine = true,
            colors = fieldColors,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = {
                sessionManager.preferences.setSubtitleApiKeys(subdlKey, openSubKey)
                feedback = "تم حفظ مفاتيح الترجمة بنجاح."
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
        ) {
            Text("حفظ مفاتيح الترجمة", color = Color.White)
        }

        HorizontalDivider(color = Color(0xFF1E293B))

        Spacer(Modifier.height(2.dp))

        OutlinedButton(
            onClick = {
                scope.launch {
                    sessionManager.orchestrator.clearHistory()
                    feedback = "تم مسح سجل المشاهدة الفورية بنجاح."
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF87171)),
        ) {
            Text("مسح سجل المشاهدة الفورية")
        }

        if (!feedback.isNullOrBlank()) {
            Text(
                text = feedback.orEmpty(),
                color = Color(0xFF34D399),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
