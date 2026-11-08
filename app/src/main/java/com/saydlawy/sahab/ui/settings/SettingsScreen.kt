package com.saydlawy.sahab.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassCard
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.components.ScreenHeader
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
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
        ScreenHeader(Ar.settings, onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                GlassPanel {
                    Text(Ar.account, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Spacer(Modifier.height(10.dp))
                    InfoRow(Ar.server, hostOf(account?.server.orEmpty()))
                    InfoRow(Ar.username, account?.user.orEmpty())
                    if (info != null) {
                        InfoRow(Ar.status, if (info.status.isBlank()) "-" else info.status)
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

                GlassCard(
                    onClick = { scope.launch { container.prefs.setLightEffects(!light) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(Ar.lightMode, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                            Text(Ar.lightModeHint, fontSize = 14.sp, color = Palette.TextDim)
                        }
                        Switch(
                            checked = light,
                            onCheckedChange = null,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Palette.Violet,
                                uncheckedThumbColor = Palette.TextDim,
                                uncheckedTrackColor = Color.White.copy(alpha = 0.12f),
                            ),
                        )
                    }
                }

                GlassButton(
                    text = if (refreshed) Ar.refreshed else Ar.refreshData,
                    onClick = {
                        container.repo.clearCache()
                        refreshed = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Ic.Refresh,
                )
                GlassButton(
                    text = Ar.logout,
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Ic.Close,
                )

                Text(
                    text = Ar.version(versionName(context)),
                    fontSize = 14.sp,
                    color = Palette.TextDim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
            }
        }
    }

    if (confirmLogout) {
        Dialog(onDismissRequest = { confirmLogout = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipPanel()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Ar.logoutQuestion, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Palette.Text, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(Ar.logoutHint, fontSize = 15.sp, color = Palette.TextDim, textAlign = TextAlign.Center)
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

private suspend fun logout(container: AppContainer) {
    container.playback = null
    container.repo.setAccount(null)
    container.prefs.clearAccount()
}

private fun versionName(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
} catch (e: Exception) {
    ""
}

private fun hostOf(server: String): String = server.substringAfter("://").trimEnd('/')

@Composable
private fun GlassPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clipPanel()
            .padding(20.dp),
    ) {
        content()
    }
}

private fun Modifier.clipPanel(): Modifier = this
    .clip(RoundedCornerShape(24.dp))
    .background(Color(0xE6171335))

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 16.sp, color = Palette.TextDim, modifier = Modifier.weight(1f))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Palette.Text)
    }
}
