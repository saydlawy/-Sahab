package com.saydlawy.sahab.ui.login

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.GlassButton
import com.saydlawy.sahab.ui.components.GlassTextField
import com.saydlawy.sahab.ui.components.Ic
import com.saydlawy.sahab.ui.theme.Palette
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import com.saydlawy.sahab.ui.LocalIsTv

data class LoginUiState(
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun onServer(value: String) {
        // A full playlist link (…/get.php?username=…&password=…) fills in all three fields at once.
        val parsed = if (value.contains("username=") && value.contains("password=")) {
            runCatching {
                val uri = Uri.parse(value.trim())
                Pair(uri.getQueryParameter("username").orEmpty(), uri.getQueryParameter("password").orEmpty())
            }.getOrNull()
        } else null
        _state.update {
            if (parsed != null && parsed.first.isNotBlank()) {
                it.copy(server = value, user = parsed.first, pass = parsed.second, error = null)
            } else {
                it.copy(server = value, error = null)
            }
        }
    }

    fun onUser(value: String) = _state.update { it.copy(user = value, error = null) }

    fun onPass(value: String) = _state.update { it.copy(pass = value, error = null) }

    fun submit(onSuccess: () -> Unit) {
        val current = _state.value
        if (current.loading) return
        val server = normalizeServer(current.server)
        if (server == null) {
            _state.update { it.copy(error = Ar.errBadUrl) }
            return
        }
        if (current.user.isBlank() || current.pass.isBlank()) {
            _state.update { it.copy(error = Ar.errFillAll) }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val account = Account(server, current.user.trim(), current.pass.trim())
                container.repo.login(account)
                container.prefs.saveAccount(account)
                _state.update { it.copy(loading = false) }
                onSuccess()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    /** Keeps only scheme, host and port: "host:8080/anything" becomes "http://host:8080". */
    private fun normalizeServer(raw: String): String? {
        var text = raw.trim().replace(" ", "")
        if (text.isEmpty()) return null
        if (!text.startsWith("http://", ignoreCase = true) && !text.startsWith("https://", ignoreCase = true)) {
            text = "http://$text"
        }
        val match = Regex("^(https?://[^/?#]+)", RegexOption.IGNORE_CASE).find(text) ?: return null
        return match.groupValues[1].trimEnd('/')
    }
}

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val vm = appViewModel { LoginViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val isTv = LocalIsTv.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        val wide = maxWidth >= 720.dp
        val hPad = if (wide) 48.dp else 24.dp
        val vPad = if (wide) 32.dp else 24.dp

        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = vPad),
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandPanel(Modifier.weight(0.4f))
                LoginForm(state, vm, onLoggedIn, isTv, Modifier.weight(0.6f))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = hPad, vertical = vPad),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandPanel(Modifier)
                Spacer(Modifier.height(24.dp))
                LoginForm(state, vm, onLoggedIn, isTv, Modifier.widthIn(max = 520.dp))
            }
        }
    }
}

@Composable
private fun BrandPanel(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(Palette.Violet.copy(alpha = 0.35f), Color.Transparent),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Palette.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Ic.Play, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = Ar.appName,
            style = TextStyle(
                brush = Brush.linearGradient(listOf(Palette.Violet, Palette.Pink, Palette.Cyan)),
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = Ar.loginHint,
            fontSize = 16.sp,
            color = Palette.TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun LoginForm(
    state: LoginUiState,
    vm: LoginViewModel,
    onLoggedIn: () -> Unit,
    isTv: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassTextField(
            value = state.server,
            onValueChange = vm::onServer,
            placeholder = Ar.serverUrl,
            leading = Ic.Globe,
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Next,
            ltr = true,
        )
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            value = state.user,
            onValueChange = vm::onUser,
            placeholder = Ar.username,
            leading = Ic.Person,
            imeAction = ImeAction.Next,
            ltr = true,
        )
        Spacer(Modifier.height(12.dp))
        GlassTextField(
            value = state.pass,
            onValueChange = vm::onPass,
            placeholder = Ar.password,
            leading = Ic.Lock,
            imeAction = ImeAction.Done,
            password = true,
            ltr = true,
            onImeAction = { vm.submit(onLoggedIn) },
        )

        val error = state.error
        if (error != null) {
            Spacer(Modifier.height(14.dp))
            ErrorBanner(error)
        }

        Spacer(Modifier.height(22.dp))
        if (state.loading) {
            CircularProgressIndicator(color = Palette.Pink, modifier = Modifier.size(44.dp))
        } else {
            GlassButton(
                text = Ar.login,
                onClick = { vm.submit(onLoggedIn) },
                modifier = Modifier.fillMaxWidth(),
                primary = true,
                autoFocus = isTv,
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Danger.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Palette.Danger),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = Palette.Danger,
            modifier = Modifier.weight(1f),
        )
    }
}
