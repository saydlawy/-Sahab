package com.saydlawy.sahab.ui.login

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.ui.LocalContainer
import com.saydlawy.sahab.ui.appViewModel
import com.saydlawy.sahab.ui.components.*
import com.saydlawy.sahab.ui.theme.Palette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudLoginState(
    val email: String = "",
    val pass: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val isRegisterMode: Boolean = false
)

class CloudLoginViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(CloudLoginState())
    val state = _state.asStateFlow()

    fun onEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun onPass(value: String) = _state.update { it.copy(pass = value, error = null) }
    fun toggleMode() = _state.update { it.copy(isRegisterMode = !it.isRegisterMode, error = null) }
    fun onError(message: String) = _state.update { it.copy(loading = false, error = message) }

    fun signInWithGoogle(idToken: String, onSuccess: () -> Unit) {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                container.authManager.signInWithGoogle(idToken)
                _state.update { it.copy(loading = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "فشل تسجيل الدخول بجوجل") }
            }
        }
    }

    fun submit(onSuccess: () -> Unit) {
        val current = _state.value
        if (current.email.isBlank() || current.pass.isBlank()) {
            _state.update { it.copy(error = "يرجى إدخال البريد وكلمة المرور") }
            return
        }
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                if (current.isRegisterMode) {
                    container.authManager.signUpWithEmail(current.email, current.pass)
                } else {
                    container.authManager.signInWithEmail(current.email, current.pass)
                }
                _state.update { it.copy(loading = false) }
                onSuccess()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "حدث خطأ") }
            }
        }
    }
}

@Composable
fun CloudLoginScreen(
    onSuccess: () -> Unit,
    onSkip: () -> Unit,
    skipLabel: String = "تخطي الآن (بدون حفظ سحابي)",
) {
    val container = LocalContainer.current
    val vm = appViewModel { CloudLoginViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val serverClientId = stringResource(id = com.saydlawy.sahab.R.string.default_web_client_id)

    val emailFocus = remember { FocusRequester() }
    val passFocus = remember { FocusRequester() }
    val loginFocus = remember { FocusRequester() }
    val googleFocus = remember { FocusRequester() }
    val skipFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { emailFocus.requestFocus() }
    }

    val scope = rememberCoroutineScope()
    val googleSignIn: () -> Unit = {
        scope.launch {
            try {
                val credentialManager = CredentialManager.create(context)
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setServerClientId(serverClientId)
                    .setFilterByAuthorizedAccounts(false)
                    .build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(googleIdOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is androidx.credentials.CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                    val token = googleIdTokenCredential.idToken
                    vm.signInWithGoogle(token, onSuccess)
                } else {
                    vm.onError("تعذّر التحقق من بيانات اعتماد Google")
                }
            } catch (e: Exception) {
                if (e !is androidx.credentials.exceptions.GetCredentialCancellationException) {
                    vm.onError("فشل تسجيل الدخول عبر Google: ${e.localizedMessage ?: e.javaClass.simpleName}")
                }
            }
        }
    }

    // الخلفية شفافة ليعمل DreamyBackground من AppRoot
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .imePadding() // رفع الشاشة مع لوحة المفاتيح
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            GlassPanel(
                shape = RoundedCornerShape(32.dp),
                strong = true,
                padding = 28.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BrandLogo(size = 80.dp, bright = true)
                    Text("حساب سحاب", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Palette.Text)
                    Text(
                        "سجل دخولك لحفظ اشتراكك ومفضلتك سحابياً",
                        fontSize = 14.sp, color = Palette.TextDim, textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(8.dp))

                    GlassTextField(
                        value = state.email,
                        onValueChange = vm::onEmail,
                        placeholder = "البريد الإلكتروني",
                        keyboardType = KeyboardType.Email,
                        modifier = Modifier.fillMaxWidth().focusRequester(emailFocus).focusProperties { down = passFocus }
                    )

                    GlassTextField(
                        value = state.pass,
                        onValueChange = vm::onPass,
                        placeholder = "كلمة المرور",
                        keyboardType = KeyboardType.Password,
                        password = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(passFocus).focusProperties { down = loginFocus; up = emailFocus }
                    )

                    state.error?.let {
                        Text(it, color = Palette.Danger, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }

                    GlassButton(
                        text = if (state.loading) "جاري الاتصال..." else if (state.isRegisterMode) "إنشاء حساب" else "تسجيل الدخول",
                        onClick = { if (!state.loading) vm.submit(onSuccess) },
                        primary = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(loginFocus).focusProperties { down = googleFocus; up = passFocus }
                    )

                    TextButton(onClick = vm::toggleMode) {
                        Text(if (state.isRegisterMode) "لديك حساب؟ سجل دخولك" else "ليس لديك حساب؟ أنشئ حساباً", color = Palette.Glow)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            GlassButton(
                text = "تسجيل الدخول باستخدام Google",
                onClick = googleSignIn,
                primary = false,
                modifier = Modifier.fillMaxWidth().focusRequester(googleFocus).focusProperties { down = skipFocus; up = loginFocus }
            )

            Spacer(Modifier.height(10.dp))

            GlassButton(
                text = skipLabel,
                onClick = onSkip,
                primary = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(skipFocus)
                    .focusProperties { up = googleFocus },
            )
        }
    }
}
