package com.saydlawy.sahab.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.data.UserInfo
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.Routes
import com.saydlawy.sahab.ui.components.DotTitle
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.GlassPanel
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.SahabTopBar
import com.saydlawy.sahab.ui.components.Tag
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.appVersion
import com.saydlawy.sahab.util.formatDate
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit, onLoggedOut: () -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    val light by container.prefs.lightEffects.collectAsStateWithLifecycle(initialValue = false)
    var confirmLogout by remember { mutableStateOf(false) }
    var refreshed by remember { mutableStateOf(false) }
    val account: Account? = container.repo.account
    val info: UserInfo? = container.repo.userInfo
    val context = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        SahabTopBar(current = Routes.SETTINGS, onBack = onBack)

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val wide = maxWidth >= 860.dp

            Column(
                modifier = Modifier.widthIn(max = 980.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (wide) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AccountPanel(account, info, Modifier.weight(1f))
                        SystemControlsColumn(
                            light = light,
                            refreshed = refreshed,
                            onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } },
                            onRefresh = {
                                container.repo.clearCache(clearDisk = true)
                                container.appScope.launch { container.repo.prewarmCatalog() }
                                refreshed = true
                            },
                            onLogout = { confirmLogout = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    AccountPanel(account, info, Modifier.fillMaxWidth())
                    SystemControlsColumn(
                        light = light,
                        refreshed = refreshed,
                        onToggleLight = { scope.launch { container.prefs.setLightEffects(!light) } },
                        onRefresh = {
                            container.repo.clearCache(clearDisk = true)
                            container.appScope.launch { container.repo.prewarmCatalog() }
                            refreshed = true
                        },
                        onLogout = { confirmLogout = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Text(
                    text = Ar.version(appVersion(context).ifBlank { "1.0" }),
                    fontSize = 13.sp,
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }

    if (confirmLogout) {
        Dialog(onDismissRequest = { confirmLogout = false }) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                strong = true,
                padding = 24.dp,
            ) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        Ar.logoutQuestion,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Palette.Text,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Ar.logoutHint,
                        fontSize = 14.sp,
                        color = Palette.TextDim,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlassButton(
                            text = Ar.logout,
                            onClick = {
                                confirmLogout = false
                                scope.launch {
                                    logout(container)
                                    onLoggedOut()
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
private fun AccountPanel(account: Account?, info: UserInfo?, modifier: Modifier = Modifier) {
    GlassPanel(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        strong = true,
        padding = 22.dp,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.Royal.copy(alpha = 0.28f))
                        .border(1.dp, Palette.Glow.copy(alpha = 0.4f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Ic.Person, contentDescription = null, tint = Palette.Glow, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(Ar.account, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text(account?.user.orEmpty().ifBlank { "-" }, fontSize = 13.sp, color = Palette.Glow)
                }
            }
            val activeStatus = info?.status.orEmpty().ifBlank { "Active" }
            Tag(activeStatus, Palette.Emerald)
        }

        Spacer(Modifier.height(18.dp))

        InfoRow(Ar.server, hostOf(account?.server.orEmpty()))
        InfoRow(Ar.username, account?.user.orEmpty())
        if (info != null) {
            InfoRow(Ar.status, if (info.status.isBlank()) "Active" else info.status)
            val expiry = info.expiry
            InfoRow(
                Ar.expiry,
                if (expiry == null || expiry <= 0L) Ar.subscriptionOpenShort else formatDate(expiry),
            )
            if (info.maxConnections.isNotBlank()) {
                InfoRow(Ar.connections, "${info.activeConnections.ifBlank { "0" }} / ${info.maxConnections}")
            }
        }
    }
}

@Composable
private fun SystemControlsColumn(
    light: Boolean,
    refreshed: Boolean,
    onToggleLight: () -> Unit,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        GlassCard(
            onClick = onToggleLight,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            selected = light,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    DotTitle(Ar.lightMode, Palette.Glow, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(Ar.lightModeHint, fontSize = 13.sp, color = Palette.TextDim)
                }
                Switch(
                    checked = light,
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

        GlassButton(
            text = if (refreshed) Ar.refreshed else Ar.refreshData,
            onClick = onRefresh,
            modifier = Modifier.fillMaxWidth(),
            icon = Ic.Refresh,
            primary = true,
        )
        GlassButton(
            text = Ar.logout,
            onClick = onLogout,
            modifier = Modifier.fillMaxWidth(),
            icon = Ic.Close,
        )
    }
}

private suspend fun logout(container: AppContainer) {
    container.playback = null
    container.repo.clearCache(clearDisk = true)
    container.repo.setAccount(null)
    container.prefs.clearAccount()
}

private fun hostOf(server: String): String = server.substringAfter("://").trimEnd('/')

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
    }
}
