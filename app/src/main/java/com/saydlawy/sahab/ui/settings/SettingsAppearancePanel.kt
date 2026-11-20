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
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.HomeChannelsLayout
import com.saydlawy.sahab.data.ThemeCustomization
import com.saydlawy.sahab.data.ThemePreferences
import com.saydlawy.sahab.data.VodCustomGrouping
import com.saydlawy.sahab.data.VodLayoutStyle
import com.saydlawy.sahab.data.VodOrganizationMode
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.focus.restorableFocus
import com.saydlawy.sahab.ui.theme.OledAccentPresets
import com.saydlawy.sahab.ui.theme.Palette
import kotlinx.coroutines.launch

private data class ThemeOption(val id: String, val title: String, val subtitle: String)

private val themeOptions = listOf(
    ThemeOption("cosmic_glass", "سحاب الكوني (الافتراضي)", "خلفيات كونية زجاجية متوازنة"),
    ThemeOption("cosmic_glass_pro", "Cosmic Glass Pro", "زجاج معزز وحدود أكثر وضوحاً"),
    ThemeOption("oled_cinema", "سينما OLED الخالصة", "أسود خالص #000000 وتركيز سينمائي"),
    ThemeOption("dynamic_bento", "بنتو ديناميكي", "بطاقات بارزة وتكوين مرن"),
)

@Composable
internal fun SettingsAppearancePanel(container: AppContainer) {
    val scope = rememberCoroutineScope()
    val prefs = container.prefs
    val saved by prefs.themePreferences.collectAsStateWithLifecycle(initialValue = ThemePreferences())
    val moviesLayout by prefs.moviesLayoutStyle.collectAsStateWithLifecycle(initialValue = VodLayoutStyle.CLASSIC)
    val seriesLayout by prefs.seriesLayoutStyle.collectAsStateWithLifecycle(initialValue = VodLayoutStyle.CLASSIC)
    val vodOrgMode by prefs.vodOrganizationMode.collectAsStateWithLifecycle(initialValue = VodOrganizationMode.DEFAULT)
    val vodCustomGrouping by prefs.vodCustomGrouping.collectAsStateWithLifecycle(initialValue = VodCustomGrouping.GENRE)
    val light by prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)

    val showRecents by prefs.showRecents.collectAsStateWithLifecycle(initialValue = true)
    val showLiveChannels by prefs.showLiveChannelsOnHome.collectAsStateWithLifecycle(initialValue = true)
    val showRecommended by prefs.showRecommendedOnHome.collectAsStateWithLifecycle(initialValue = true)
    val showQuickCategories by prefs.showQuickCategoriesOnHome.collectAsStateWithLifecycle(initialValue = true)
    val homeChannelsLayout by prefs.homeChannelsLayout.collectAsStateWithLifecycle(initialValue = HomeChannelsLayout.CAROUSEL)

    val activeThemeId = saved.selectedThemeId
    val customization = saved.customization(activeThemeId)

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SettingsCard(
            title = "ترتيب الأفلام والمسلسلات",
            subtitle = "اختر بين باقات السيرفر الافتراضية أو التصنيف المخصص الموحد (النوع، السنة، الممثل)",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppearanceChoiceRow(
                    title = "وضع الترتيب",
                    options = listOf(
                        VodOrganizationMode.DEFAULT.name to "الوضع الافتراضي",
                        VodOrganizationMode.CUSTOM.name to "الوضع المخصص",
                    ),
                    selected = vodOrgMode.name,
                    onSelect = { raw ->
                        val mode = runCatching { VodOrganizationMode.valueOf(raw) }.getOrDefault(VodOrganizationMode.DEFAULT)
                        scope.launch { prefs.setVodOrganizationMode(mode) }
                    },
                )
                if (vodOrgMode == VodOrganizationMode.CUSTOM) {
                    AppearanceChoiceRow(
                        title = "تصنيف الأفلام والمسلسلات حسب",
                        options = listOf(
                            VodCustomGrouping.GENRE.name to "النوع (أكشن، دراما...)",
                            VodCustomGrouping.YEAR.name to "السنة",
                            VodCustomGrouping.CAST.name to "الممثل",
                        ),
                        selected = vodCustomGrouping.name,
                        onSelect = { raw ->
                            val grouping = runCatching { VodCustomGrouping.valueOf(raw) }.getOrDefault(VodCustomGrouping.GENRE)
                            scope.launch { prefs.setVodCustomGrouping(grouping) }
                        },
                    )
                }
            }
        }

        VodLayoutSelectorPanel(
            title = "تخطيط شاشة الأفلام",
            subtitle = "اختر التصميم الخاص بقسم الأفلام بشكل مستقل",
            selected = moviesLayout,
            keyPrefix = "movies_layout",
            onSelect = { style -> scope.launch { prefs.setMoviesLayoutStyle(style) } },
        )

        VodLayoutSelectorPanel(
            title = "تخطيط شاشة المسلسلات",
            subtitle = "اختر التصميم الخاص بقسم المسلسلات بشكل مستقل",
            selected = seriesLayout,
            keyPrefix = "series_layout",
            onSelect = { style -> scope.launch { prefs.setSeriesLayoutStyle(style) } },
        )

        GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DotTitle("ثيم التطبيق", Palette.Glow, fontSize = 18.sp)
                themeOptions.forEach { opt ->
                    val isSelected = activeThemeId == opt.id
                    GlassCard(
                        onClick = { scope.launch { prefs.setSelectedTheme(opt.id) } },
                        selected = isSelected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .restorableFocus(Routes.SETTINGS, "settings_theme_${opt.id}"),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(opt.title, color = Palette.Text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(opt.subtitle, color = Palette.TextDim, fontSize = 12.sp)
                            }
                            if (isSelected) Tag("مطبق", Palette.Emerald)
                        }
                    }
                }

                if (activeThemeId == "oled_cinema") {
                    Spacer(Modifier.height(4.dp))
                    DotTitle("لون التركيز في سينما OLED", Palette.FocusCyan, fontSize = 15.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusGroup(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OledAccentPresets.forEach { preset ->
                            val selectedAccent = saved.oledAccentPreset == preset.id
                            GlassCard(
                                onClick = { scope.launch { prefs.setOledAccentPreset(preset.id) } },
                                selected = selectedAccent,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .restorableFocus(Routes.SETTINGS, "settings_oled_accent_${preset.id}"),
                            ) {
                                Column(
                                    Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(preset.accent)
                                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                                    )
                                    Text(
                                        text = preset.nameAr,
                                        color = if (selectedAccent) Color.White else Palette.TextDim,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DotTitle("الخط والزجاج", Palette.Royal, fontSize = 17.sp)
                AppearanceChoiceRow(
                    title = "نوع الخط",
                    options = listOf("cairo" to "القاهرة", "sans" to "بسيط", "serif" to "نسخي", "mono" to "أحادي"),
                    selected = customization.fontFamily,
                ) { font ->
                    scope.launch { prefs.updateThemeCustomization(activeThemeId) { it.copy(fontFamily = font) } }
                }
                AppearanceSlider(
                    title = "حجم الخط",
                    value = customization.fontScale,
                    range = 0.8f..1.35f,
                    label = "${(customization.fontScale * 100).toInt()}٪",
                ) { scale ->
                    scope.launch { prefs.updateThemeCustomization(activeThemeId) { it.copy(fontScale = scale) } }
                }
                AppearanceSlider(
                    title = "استدارة البطاقات",
                    value = customization.cornerRadius,
                    range = 0f..36f,
                    label = "${customization.cornerRadius.toInt()} dp",
                ) { radius ->
                    scope.launch { prefs.updateThemeCustomization(activeThemeId) { it.copy(cornerRadius = radius) } }
                }
                AppearanceSlider(
                    title = "شفافية الزجاج",
                    value = customization.glassOpacity,
                    range = 0.2f..1f,
                    label = "${(customization.glassOpacity * 100).toInt()}٪",
                ) { opacity ->
                    scope.launch { prefs.updateThemeCustomization(activeThemeId) { it.copy(glassOpacity = opacity) } }
                }
                var resetFocused by remember { mutableStateOf(false) }
                Text(
                    text = "إعادة ضبط إعدادات هذا الثيم",
                    color = if (resetFocused) Palette.FocusCyan else Palette.Glow,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .onFocusChanged { resetFocused = it.isFocused }
                        .then(
                            if (resetFocused) Modifier.border(1.5.dp, Palette.FocusCyan, RoundedCornerShape(10.dp))
                            else Modifier,
                        )
                        .clickable { scope.launch { prefs.resetThemeCustomization(activeThemeId) } }
                        .padding(vertical = 8.dp, horizontal = 10.dp),
                )
            }
        }

        AppearancePanel(
            light = light,
            onToggleLight = { scope.launch { prefs.setLightEffects(!light) } },
        )

        HomeCustomizationPanel(
            showRecents = showRecents,
            showLiveChannels = showLiveChannels,
            showRecommended = showRecommended,
            showQuickCategories = showQuickCategories,
            homeChannelsLayout = homeChannelsLayout,
            onToggleShowRecents = { scope.launch { prefs.setShowRecents(!showRecents) } },
            onToggleShowLive = { scope.launch { prefs.setShowLiveChannelsOnHome(!showLiveChannels) } },
            onToggleShowRecommended = { scope.launch { prefs.setShowRecommendedOnHome(!showRecommended) } },
            onToggleShowQuickCategories = { scope.launch { prefs.setShowQuickCategoriesOnHome(!showQuickCategories) } },
            onSelectLayout = { layout -> scope.launch { prefs.setHomeChannelsLayout(layout) } },
            onClearRecents = {
                scope.launch {
                    prefs.clearRecentsForAccount(prefs.currentActiveAccountId())
                }
            },
        )
    }
}

@Composable
private fun VodLayoutSelectorPanel(
    title: String,
    subtitle: String,
    selected: VodLayoutStyle,
    keyPrefix: String,
    onSelect: (VodLayoutStyle) -> Unit,
) {
    GlassPanel(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            DotTitle(title, Palette.FocusCyan, fontSize = 18.sp)
            Text(subtitle, color = Palette.TextDim, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VodLayoutStyle.entries.forEach { style ->
                    val active = selected == style
                    GlassCard(
                        onClick = { onSelect(style) },
                        selected = active,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .restorableFocus(Routes.SETTINGS, "${keyPrefix}_${style.id}"),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LayoutWireframePreview(style, active)
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(style.labelAr, color = Palette.Text, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                                if (active) Tag("نشط", Palette.Emerald)
                            }
                            Text(style.descAr, color = Palette.TextDim, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutWireframePreview(style: VodLayoutStyle, active: Boolean) {
    val barColor = if (active) Palette.FocusCyan.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.16f)
    val cardColor = if (active) Palette.Royal.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.10f)
    Column(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (style) {
            VodLayoutStyle.CLASSIC -> {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.width(18.dp).height(34.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(3) {
                            Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(4.dp)).background(cardColor))
                        }
                    }
                }
            }
            VodLayoutStyle.PRIME -> {
                Box(Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(4) {
                        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(3.dp)).background(cardColor))
                    }
                }
            }
            VodLayoutStyle.NETFLIX -> {
                Box(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(5) {
                        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(3.dp)).background(cardColor))
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceChoiceRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = Palette.TextDim, fontSize = 13.sp)
        Row(
            modifier = Modifier.focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (key, label) ->
                val active = selected == key
                var chipFocused by remember { mutableStateOf(false) }
                Text(
                    text = label,
                    color = if (active || chipFocused) Color.White else Palette.TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                active -> Palette.Royal
                                chipFocused -> Palette.CardSurfaceElevated
                                else -> Palette.CardSurface
                            },
                        )
                        .onFocusChanged { chipFocused = it.isFocused }
                        .border(
                            width = if (chipFocused) 2.dp else 1.dp,
                            color = if (chipFocused) Palette.FocusCyan else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { onSelect(key) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AppearanceSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    onValue: (Float) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val layoutDirection = LocalLayoutDirection.current
    var localValue by remember(value) { mutableStateOf(value) }
    var sliderFocused by remember { mutableStateOf(false) }
    val step = ((range.endInclusive - range.start) / 20f).coerceAtLeast(0.02f)

    fun applyDelta(delta: Float): Boolean {
        val next = (localValue + delta).coerceIn(range.start, range.endInclusive)
        localValue = next
        onValue(next)
        return true
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .onFocusChanged { sliderFocused = it.isFocused }
            .border(
                width = if (sliderFocused) 1.5.dp else 0.dp,
                color = if (sliderFocused) Palette.FocusCyan else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = if (sliderFocused) 8.dp else 0.dp, vertical = if (sliderFocused) 4.dp else 0.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                title,
                color = if (sliderFocused) Palette.FocusCyan else Palette.TextDim,
                fontSize = 13.sp,
            )
            Text(label, color = Palette.Text, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(
                value = localValue.coerceIn(range.start, range.endInclusive),
                onValueChange = { localValue = it },
                onValueChangeFinished = { onValue(localValue) },
                valueRange = range,
                modifier = Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.nativeKeyEvent.keyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> focusManager.moveFocus(FocusDirection.Down)
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> focusManager.moveFocus(FocusDirection.Up)
                            android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (layoutDirection == LayoutDirection.Rtl) applyDelta(step) else applyDelta(step)
                            }
                            android.view.KeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (layoutDirection == LayoutDirection.Rtl) applyDelta(-step) else applyDelta(-step)
                            }
                            else -> false
                        }
                    } else {
                        false
                    }
                },
            )
        }
    }
}
