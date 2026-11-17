package com.saydlawy.sahab.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.data.Prefs
import com.saydlawy.sahab.data.ThemeCustomization
import com.saydlawy.sahab.data.ThemePreferences
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.ui.theme.ThemeColorLabels
import com.saydlawy.sahab.ui.theme.ThemeColorTokenNames
import com.saydlawy.sahab.ui.theme.themeBaseColor
import kotlinx.coroutines.launch

private data class ThemeChoice(val id: String, val title: String, val description: String)

private val themeChoices = listOf(
    ThemeChoice("cosmic_glass", "الثيم الافتراضي الحالي", "مظهر التطبيق الحالي"),
    ThemeChoice("cosmic_glass_pro", "Cosmic Glass Pro", "المقترح الأول: معالجة كونية وزجاجية"),
    ThemeChoice("oled_cinema", "سينما OLED", "أسطح سوداء وتركيز سينمائي"),
    ThemeChoice("dynamic_bento", "بنتو ديناميكي", "أسطح زجاجية وتكوينات مرنة"),
)

private val customizableParts = listOf(
    "home_spotlight" to "المحتوى البارز في الرئيسية",
    "home_highlights" to "بطاقات Bento في الرئيسية",
    "channel_logo" to "شعار القناة في قائمة البث",
    "home_continue" to "متابعة المشاهدة",
    "home_recommendations" to "المقترحات",
    "home_categories" to "التصنيفات السريعة",
    "home_live_channels" to "قنوات البث في الرئيسية",
    "home_matches" to "المباريات",
    "live_preview" to "معاينة البث",
    "live_epg" to "دليل البرامج",
    "player_controls" to "أزرار المشغل",
    "player_timeline" to "الخط الزمني للمشغل",
    "sports_scores" to "بطاقات المباريات والنتائج",
    "details_metadata" to "معلومات الفيلم والمسلسل",
    "details_actions" to "إجراءات تفاصيل الفيلم والمسلسل",
    "global_navigation" to "التنقل العام",
)

private val movableParts = listOf(
    "channel_logo" to "شعار القناة",
    "global_navigation" to "التنقل العام",
    "home_sections" to "ترتيب أقسام الرئيسية",
    "home_highlights" to "ترتيب بطاقات Bento",
)

@Composable
internal fun ThemeCustomizationPanel(prefs: Prefs) {
    val saved by prefs.themePreferences.collectAsStateWithLifecycle(initialValue = ThemePreferences())
    val scope = rememberCoroutineScope()
    var editingTheme by remember(saved.selectedThemeId) { mutableStateOf(saved.selectedThemeId) }
    val current = saved.customization(editingTheme)
    val selected = saved.selectedThemeId

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("الثيمات", color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("اختر ثيماً للتطبيق، ثم حدد ثيماً من القائمة لتخصيص إعداداته بشكل مستقل.", color = Palette.TextDim, fontSize = 14.sp)

        themeChoices.forEach { choice ->
            GlassCard(
                onClick = {
                    editingTheme = choice.id
                    scope.launch { prefs.setSelectedTheme(choice.id) }
                },
                selected = selected == choice.id,
                modifier = Modifier.fillMaxWidth(),
            ) { focused ->
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(choice.title, color = Palette.Text, fontWeight = FontWeight.Bold)
                    Text(choice.description, color = Palette.TextDim, fontSize = 12.sp)
                    if (selected == choice.id) Text("مطبق حالياً", color = Palette.Glow, fontSize = 12.sp)
                    else if (focused) Text("اضغط للتطبيق", color = Palette.Glow, fontSize = 12.sp)
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("تخصيص: ${themeChoices.firstOrNull { it.id == editingTheme }?.title.orEmpty()}", color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("الألوان (اكتب قيمة HEX مثل #8E45FF)", color = Palette.TextDim, fontSize = 13.sp)
                ThemeColorTokenNames.forEach { token ->
                    val override = current.colorOverrides[token]
                    val baseColor = themeBaseColor(editingTheme, token)
                    val shown = override ?: baseColor.toHex()
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(parseColor(shown) ?: baseColor))
                        Text(ThemeColorLabels[token] ?: token, color = Palette.Text, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        var value by remember(editingTheme, token, override) { mutableStateOf(shown) }
                        BasicTextField(
                            value = value,
                            onValueChange = { typed ->
                                value = typed
                                if (parseColor(typed) != null) updateTheme(prefs, scope, editingTheme) { it.copy(colorOverrides = it.colorOverrides + (token to typed)) }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(color = Palette.Text, fontSize = 12.sp),
                            modifier = Modifier.width(92.dp).clip(RoundedCornerShape(8.dp)).background(Palette.CardSurface).padding(9.dp),
                        )
                    }
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("الخط والأيقونات", color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                ChoiceRow("الخط", listOf("cairo" to "القاهرة", "sans" to "بسيط", "serif" to "نسخي", "mono" to "أحادي"), current.fontFamily) {
                    updateTheme(prefs, scope, editingTheme) { cur -> cur.copy(fontFamily = it) }
                }
                LabeledSlider("حجم الخط", current.fontScale, 0.8f..1.35f, "${(current.fontScale * 100).toInt()}٪") {
                    updateTheme(prefs, scope, editingTheme) { cur -> cur.copy(fontScale = it) }
                }
                ChoiceRow("شكل الأيقونة", listOf("plain" to "بسيطة", "filled" to "معبأة", "outlined" to "محددة"), current.iconStyle) {
                    updateTheme(prefs, scope, editingTheme) { cur -> cur.copy(iconStyle = it) }
                }
                ChoiceRow("حاوية الأيقونة", listOf("rounded" to "مستديرة", "circle" to "دائرية", "square" to "مربعة"), current.iconContainerShape) {
                    updateTheme(prefs, scope, editingTheme) { cur -> cur.copy(iconContainerShape = it) }
                }
                LabeledSlider("حجم الأيقونات", current.iconScale, 0.7f..1.4f, "${(current.iconScale * 100).toInt()}٪") {
                    updateTheme(prefs, scope, editingTheme) { cur -> cur.copy(iconScale = it) }
                }
                LabeledSlider("استدارة البطاقات", current.cornerRadius, 0f..36f, "${current.cornerRadius.toInt()} dp") {
                    updateTheme(prefs, scope, editingTheme) { cur -> cur.copy(cornerRadius = it) }
                }
                LabeledSlider("شفافية الزجاج", current.glassOpacity, 0.2f..1f, "${(current.glassOpacity * 100).toInt()}٪") {
                    updateTheme(prefs, scope, editingTheme) { cur -> cur.copy(glassOpacity = it) }
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("إظهار عناصر الشاشات", color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                customizableParts.forEach { (key, title) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, color = Palette.Text, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = current.componentVisibility[key] ?: true, onCheckedChange = { visible ->
                            updateTheme(prefs, scope, editingTheme) { it.copy(componentVisibility = it.componentVisibility + (key to visible)) }
                        })
                    }
                }
                Text("أماكن العناصر", color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                movableParts.forEach { (key, title) ->
                    val placements = when (key) {
                        "channel_logo" -> listOf("start" to "يمين الصف", "end" to "يسار الصف")
                        "home_sections" -> listOf("default" to "الترتيب الحالي", "live_first" to "البث أولاً", "movies_first" to "الأفلام أولاً", "matches_first" to "المباريات أولاً")
                        "home_highlights" -> listOf("start" to "البث يميناً", "center" to "الأفلام وسطاً", "end" to "المباريات يميناً")
                        else -> listOf("start" to "البداية", "center" to "الوسط", "end" to "النهاية")
                    }
                    val defaultPlacement = when (key) {
                        "channel_logo" -> "start"
                        "global_navigation" -> "center"
                        "home_sections" -> "default"
                        "home_highlights" -> "start"
                        else -> "end"
                    }
                    ChoiceRow(title, placements, current.componentPlacement[key] ?: defaultPlacement) { place ->
                        updateTheme(prefs, scope, editingTheme) { it.copy(componentPlacement = it.componentPlacement + (key to place)) }
                    }
                }
            }
        }

        Text("إعادة تخصيص هذا الثيم إلى إعداداته الافتراضية", color = Palette.Glow, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
            scope.launch { prefs.resetThemeCustomization(editingTheme) }
        }.padding(12.dp))
    }
}

@Composable
private fun ChoiceRow(title: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Palette.TextDim, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (key, label) ->
                val active = selected == key
                Text(label, color = if (active) Color.White else Palette.TextDim, fontSize = 11.sp,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (active) Palette.Royal else Palette.CardSurface)
                        .clickable { onSelect(key) }.padding(horizontal = 10.dp, vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun LabeledSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, label: String, onValue: (Float) -> Unit) {
    var localValue by remember(value) { mutableStateOf(value) }
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = Palette.TextDim, fontSize = 13.sp)
            Text(label, color = Palette.Text, fontSize = 12.sp)
        }
        Slider(
            value = localValue.coerceIn(range.start, range.endInclusive),
            onValueChange = { localValue = it },
            onValueChangeFinished = { onValue(localValue) },
            valueRange = range,
        )
    }
}

private fun updateTheme(
    prefs: Prefs,
    scope: kotlinx.coroutines.CoroutineScope,
    themeId: String,
    transform: (ThemeCustomization) -> ThemeCustomization,
) {
    scope.launch { prefs.updateThemeCustomization(themeId, transform) }
}

private fun parseColor(raw: String): Color? {
    val hex = raw.removePrefix("#")
    val value = hex.toLongOrNull(16) ?: return null
    return when (hex.length) {
        6 -> Color((0xFF000000L or value).toInt())
        8 -> Color(value.toInt())
        else -> null
    }
}

private fun Color.toHex(): String = "#%06X".format(0xFFFFFF and (toArgb()))
private fun Color.toArgb(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())
