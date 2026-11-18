package com.saydlawy.sahab.ui.login

import android.net.Uri
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.ui.DisplayMode
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.LocalDisplayMode
import com.saydlawy.sahab.ui.LocalIsTv
import com.saydlawy.sahab.ui.LocalWindowInfo
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.BrandLogo
import com.saydlawy.sahab.ui.components.BrandMark
import com.saydlawy.sahab.ui.components.ClockPill
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.GlassTextField
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.appVersion
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val vm = appViewModel { LoginViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val container = LocalContainer.current
    val light by container.prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isTv = LocalIsTv.current
    val version = appVersion(context).ifBlank { "1.0" }

    val displayMode = LocalDisplayMode.current
    val maxWidth = LocalWindowInfo.current.widthDp

    Box(
        Modifier
            .fillMaxSize()
            .imePadding(),
    ) {
        val wide = displayMode == DisplayMode.TABLET_OR_TV && maxWidth >= 820.dp
        val hPad = if (wide) 36.dp else 18.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = hPad, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top minimal bar
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                BrandMark(subtitle = Ar.firstSetup, logoSize = if (wide) 48.dp else 40.dp, titleSize = if (wide) 20 else 18)
                ClockPill(extra = Ar.versionShort(version))
            }

            Spacer(Modifier.height(16.dp))

            // Main center setup & login card
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                GlassPanel(
                    modifier = Modifier.widthIn(max = 1040.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    strong = true,
                    padding = if (wide) 34.dp else 22.dp,
                ) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        BrandLogo(size = if (wide) 76.dp else 64.dp, bright = true, icon = Ic.Play)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = Ar.welcomeTitle,
                            style = TextStyle(
                                brush = Palette.BrandText,
                                fontSize = if (wide) 26.sp else 20.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = Ar.welcomeBody,
                            fontSize = 14.sp,
                            color = Palette.TextDim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 620.dp),
                        )
                    }

                    Spacer(Modifier.height(26.dp))

                    // Account Type Tabs
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassCard(
                            onClick = { vm.onAccountTypeChange("XTREAM") },
                            shape = RoundedCornerShape(12.dp),
                            selected = state.accountType == "XTREAM",
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text("Xtream", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = if (state.accountType == "XTREAM") Color.White else Palette.TextDim, fontWeight = FontWeight.Bold)
                        }
                        GlassCard(
                            onClick = { vm.onAccountTypeChange("M3U") },
                            shape = RoundedCornerShape(12.dp),
                            selected = state.accountType == "M3U",
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text("رابط M3U", Modifier.padding(horizontal = 24.dp, vertical = 10.dp), color = if (state.accountType == "M3U") Color.White else Palette.TextDim, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (state.accountType == "M3U") {
                        LabeledInput(
                            label = "رابط M3U المباشر",
                            dot = Palette.Glow,
                            value = state.server,
                            onValueChange = vm::onServer,
                            placeholder = "http://example.com/playlist.m3u",
                            icon = Ic.Link,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Done,
                            onImeAction = { vm.submit(onLoggedIn) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        
                    if (wide) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            LabeledInput(
                                label = Ar.serverLabel,
                                dot = Palette.Glow,
                                value = state.server,
                                onValueChange = vm::onServer,
                                placeholder = "http://portal.server.com:8080",
                                icon = Ic.Link,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledInput(
                                label = Ar.userLabel,
                                dot = Palette.Royal,
                                value = state.user,
                                onValueChange = vm::onUser,
                                placeholder = Ar.userPlaceholder,
                                icon = Ic.Person,
                                imeAction = ImeAction.Next,
                                modifier = Modifier.weight(1f),
                            )
                            LabeledInput(
                                label = Ar.passLabel,
                                dot = Palette.Gold,
                                value = state.pass,
                                onValueChange = vm::onPass,
                                placeholder = "••••••••",
                                icon = Ic.Lock,
                                password = true,
                                imeAction = ImeAction.Done,
                                onImeAction = { vm.submit(onLoggedIn) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            LabeledInput(
                                label = Ar.serverLabel,
                                dot = Palette.Glow,
                                value = state.server,
                                onValueChange = vm::onServer,
                                placeholder = "http://portal.server.com:8080",
                                icon = Ic.Link,
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Next,
                            )
                            LabeledInput(
                                label = Ar.userLabel,
                                dot = Palette.Royal,
                                value = state.user,
                                onValueChange = vm::onUser,
                                placeholder = Ar.userPlaceholder,
                                icon = Ic.Person,
                                imeAction = ImeAction.Next,
                            )
                            LabeledInput(
                                label = Ar.passLabel,
                                dot = Palette.Gold,
                                value = state.pass,
                                onValueChange = vm::onPass,
                                placeholder = "••••••••",
                                icon = Ic.Lock,
                                password = true,
                                imeAction = ImeAction.Done,
                                onImeAction = { vm.submit(onLoggedIn) },
                            )
                        }
                    }

                    
                    }

                    val trimmedServer = state.server.trim()
                    val isInsecureHttp = trimmedServer.isNotEmpty() &&
                        !trimmedServer.startsWith("https://", ignoreCase = true)
                    if (isInsecureHttp) {
                        Spacer(Modifier.height(14.dp))
                        HttpSecurityWarningBanner()
                    }

                    val error = state.error
                    if (error != null) {
                        Spacer(Modifier.height(16.dp))
                        ErrorBanner(error)
                    }

                    Spacer(Modifier.height(24.dp))

                    // Action bar: M3U tip card on the right (RTL start), primary submit button on the left (RTL end).
                    if (wide) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            M3uTipCard()
                            SubmitControl(state.loading, isTv) { vm.submit(onLoggedIn) }
                        }
                    } else {
                        Column(
                            Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            SubmitControl(state.loading, isTv, Modifier.fillMaxWidth()) { vm.submit(onLoggedIn) }
                            M3uTipCard(Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bottom status & quick options bar
            LoginFooter(
                loading = state.loading,
                hasError = state.error != null,
                light = light,
                onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } },
            )
        }
    }
}

@Composable
private fun LabeledInput(
    label: String,
    dot: Color,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    password: Boolean = false,
    onImeAction: () -> Unit = {},
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DotTitle(label, dot, fontSize = 14.sp)
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            leading = icon,
            keyboardType = keyboardType,
            imeAction = imeAction,
            password = password,
            ltr = true,
            onImeAction = onImeAction,
        )
    }
}

@Composable
private fun M3uTipCard(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Gold.copy(alpha = 0.18f))
                .border(1.dp, Palette.Gold.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Ic.Ticket, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(Ar.m3uTipTitle, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            Text(Ar.m3uTipBody, fontSize = 11.sp, color = Palette.TextDim)
        }
    }
}

@Composable
private fun SubmitControl(loading: Boolean, isTv: Boolean, modifier: Modifier = Modifier, onSubmit: () -> Unit) {
    if (loading) {
        Box(modifier.padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Palette.Glow, modifier = Modifier.size(44.dp))
        }
    } else {
        GlassButton(
            text = Ar.loginInstall,
            onClick = onSubmit,
            modifier = modifier,
            primary = true,
            autoFocus = isTv,
            trailingIcon = Ic.ArrowLeft,
        )
    }
}

@Composable
private fun LoginFooter(
    loading: Boolean,
    hasError: Boolean,
    light: Boolean,
    onToggleLight: () -> Unit,
) {
    val dotColor = when {
        hasError -> Palette.Live
        loading -> Palette.Gold
        else -> Palette.Emerald
    }
    val statusText = when {
        hasError -> Ar.statusFailed
        loading -> Ar.statusConnecting
        else -> Ar.statusReady
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Glass)
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DotTitle(statusText, dotColor, fontSize = 13.sp, color = dotColor)
        GlassCard(
            onClick = onToggleLight,
            shape = RoundedCornerShape(12.dp),
            selected = light,
            focusScale = 1.03f,
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Ic.Settings, contentDescription = null, tint = Palette.Royal, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(Ar.lightMode, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
            }
        }
    }
}

@Composable
private fun HttpSecurityWarningBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Gold.copy(alpha = 0.14f))
            .border(1.dp, Palette.Gold.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Ic.Warning, contentDescription = null, tint = Palette.Gold, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = "تنبيه: الاتصال عبر HTTP غير مشفّر. يُفضّل استخدام https:// إذا كان مزود الخدمة يدعمه.",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.Text,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Live.copy(alpha = 0.16f))
            .border(1.dp, Palette.Live.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Palette.Live),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.Text,
            modifier = Modifier.weight(1f),
        )
    }
}
