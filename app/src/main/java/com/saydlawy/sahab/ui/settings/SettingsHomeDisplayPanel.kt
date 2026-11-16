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
internal fun HomeCustomizationPanel(
    showRecents: Boolean,
    showLiveChannels: Boolean,
    showRecommended: Boolean,
    showQuickCategories: Boolean,
    showMatches: Boolean,
    homeChannelsLayout: HomeChannelsLayout,
    onToggleShowRecents: () -> Unit,
    onToggleShowLive: () -> Unit,
    onToggleShowRecommended: () -> Unit,
    onToggleShowQuickCategories: () -> Unit,
    onToggleShowMatches: () -> Unit,
    onSelectLayout: (HomeChannelsLayout) -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier, shape = RoundedCornerShape(24.dp), strong = true, padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Royal.copy(alpha = 0.28f))
                        .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Settings, null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("التحكم في أقسام الشاشة الرئيسية", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Palette.Text)
                    Text("إظهار أو إخفاء أي قسم وتخصيص طريقة العرض", fontSize = 12.sp, color = Palette.TextDim)
                }
            }

            SettingsSwitchRow(
                title = "عرض قسم تابع المشاهدة",
                subtitle = "إظهار شريط استكمال مشاهدة الأفلام والمسلسلات والقنوات",
                checked = showRecents,
                itemKey = "settings_toggle_recents",
                onToggle = onToggleShowRecents,
            )

            SettingsSwitchRow(
                title = "عرض قسم القنوات المباشرة",
                subtitle = "إظهار شريط القنوات المباشرة المميزة في الصفحة الرئيسية",
                checked = showLiveChannels,
                itemKey = "settings_toggle_live_channels",
                onToggle = onToggleShowLive,
            )

            SettingsSwitchRow(
                title = "عرض قسم أفلام مقترحة لك (أحدث الإضافات)",
                subtitle = "إظهار أحدث الأفلام المضافة من سيرفر Xtream وقاعدة بيانات Firebase",
                checked = showRecommended,
                itemKey = "settings_toggle_recommended",
                onToggle = onToggleShowRecommended,
            )

            SettingsSwitchRow(
                title = "عرض بطاقات التصفح السريع",
                subtitle = "إظهار بطاقات البث المباشر — الأفلام — المسلسلات",
                checked = showQuickCategories,
                itemKey = "settings_toggle_quick_cats",
                onToggle = onToggleShowQuickCategories,
            )

            SettingsSwitchRow(
                title = "عرض قسم مباريات اليوم",
                subtitle = "إظهار جدول مباريات اليوم المباشرة والقادمة في أسفل الرئيسية",
                checked = showMatches,
                itemKey = "settings_toggle_matches",
                onToggle = onToggleShowMatches,
            )

            Spacer(Modifier.height(4.dp))
            DotTitle("طريقة عرض القنوات المباشرة في الرئيسية", Palette.Royal, fontSize = 15.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val isCarousel = homeChannelsLayout == HomeChannelsLayout.CAROUSEL
                GlassCard(
                    onClick = { onSelectLayout(HomeChannelsLayout.CAROUSEL) },
                    modifier = Modifier
                        .weight(1f)
                        .restorableFocus(Routes.SETTINGS, "settings_layout_carousel"),
                    shape = RoundedCornerShape(16.dp),
                    selected = isCarousel,
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isCarousel,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Palette.Glow, unselectedColor = Palette.TextDim),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("سلايدر أفقي", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isCarousel) Palette.Text else Palette.TextDim)
                            Text("شريط متحرك", fontSize = 11.sp, color = Palette.TextDim)
                        }
                    }
                }
                val isGrid = homeChannelsLayout == HomeChannelsLayout.GRID
                GlassCard(
                    onClick = { onSelectLayout(HomeChannelsLayout.GRID) },
                    modifier = Modifier
                        .weight(1f)
                        .restorableFocus(Routes.SETTINGS, "settings_layout_grid"),
                    shape = RoundedCornerShape(16.dp),
                    selected = isGrid,
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = isGrid,
                            onClick = null,
                            colors = RadioButtonDefaults.colors(selectedColor = Palette.Glow, unselectedColor = Palette.TextDim),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("شبكة", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (isGrid) Palette.Text else Palette.TextDim)
                            Text("صفوف متعددة", fontSize = 11.sp, color = Palette.TextDim)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            GlassButton(
                text = "مسح سجل المشاهدة بالكامل",
                onClick = onClearRecents,
                modifier = Modifier
                    .fillMaxWidth()
                    .restorableFocus(Routes.SETTINGS, "settings_clear_recents_btn"),
            )
            val cacheCtx = androidx.compose.ui.platform.LocalContext.current
            val cacheScope = rememberCoroutineScope()
            var cacheCleared by remember { mutableStateOf(false) }
            GlassButton(
                text = if (cacheCleared) "تم مسح كاش الصور والفيديو" else "مسح كاش الصور والفيديو",
                onClick = {
                    cacheScope.launch(Dispatchers.IO) {
                        com.saydlawy.sahab.ui.player.SahabPlayer.clearMediaCache(cacheCtx)
                        runCatching { java.io.File(cacheCtx.cacheDir, "image_cache").deleteRecursively() }
                        withContext(Dispatchers.Main) { cacheCleared = true }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .restorableFocus(Routes.SETTINGS, "settings_clear_media_cache_btn"),
            )
            val weatherSvc = com.saydlawy.sahab.ui.LocalContainer.current.weatherService
            var cityInput by remember { mutableStateOf(weatherSvc.getManualCity()) }
            var cityStatus by remember { mutableStateOf<String?>(null) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassTextField(
                    value = cityInput,
                    onValueChange = { cityInput = it; cityStatus = null },
                    placeholder = "مدينة الطقس (اتركه فارغاً للتلقائي)",
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    text = "حفظ",
                    onClick = {
                        cacheScope.launch {
                            val ok = weatherSvc.setManualCity(cityInput)
                            cityStatus = if (ok) {
                                if (cityInput.isBlank()) "تم التفعيل التلقائي" else "تم تثبيت المدينة"
                            } else "لم يتم العثور على المدينة"
                        }
                    },
                    modifier = Modifier.restorableFocus(Routes.SETTINGS, "settings_save_weather_city_btn"),
                )
            }
            cityStatus?.let { st ->
                Text(st, fontSize = 11.sp, color = Palette.Gold)
            }
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    itemKey: String,
    onToggle: () -> Unit,
) {
    GlassCard(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .restorableFocus(Routes.SETTINGS, itemKey),
        shape = RoundedCornerShape(16.dp),
        selected = checked,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                DotTitle(title, if (checked) Palette.FocusCyan else Palette.TextDim, fontSize = 14.sp)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, fontSize = 11.5.sp, color = Palette.TextDim)
            }
            Switch(
                checked = checked,
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
}

