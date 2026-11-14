package com.saydlawy.sahab.ui.login

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.saydlawy.sahab.AppContainer
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
fun CloudLoginScreen(onSuccess: () -> Unit, onSkip: () -> Unit) {
    val vm = appViewModel { CloudLoginViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(Palette.DeepBlue), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 400.dp)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            BrandLogo(Modifier.size(80.dp))
            Text(
                "حساب سحاب",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Text
            )
            Text(
                "سجل دخولك لحفظ اشتراكك ومفضلتك في السحابة",
                fontSize = 14.sp,
                color = Palette.TextDim,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Box(Modifier.height(16.dp))

            GlassTextField(
                value = state.email,
                onValueChange = vm::onEmail,
                placeholder = "البريد الإلكتروني",
                keyboardType = KeyboardType.Email,
                modifier = Modifier.fillMaxWidth()
            )

            GlassTextField(
                value = state.pass,
                onValueChange = vm::onPass,
                placeholder = "كلمة المرور",
                keyboardType = KeyboardType.Password,
                password = true,
                modifier = Modifier.fillMaxWidth()
            )

            if (state.error != null) {
                Text(state.error, color = Palette.Danger, fontSize = 13.sp)
            }

            GlassButton(
                text = if (state.loading) "جاري الاتصال..." else if (state.isRegisterMode) "إنشاء حساب جديد" else "تسجيل الدخول",
                onClick = { if (!state.loading) vm.submit(onSuccess) },
                primary = true,
                modifier = Modifier.fillMaxWidth()
            )

            TextButton(onClick = vm::toggleMode) {
                Text(
                    if (state.isRegisterMode) "لديك حساب بالفعل؟ سجل دخولك" else "ليس لديك حساب؟ أنشئ حساباً",
                    color = Palette.Glow
                )
            }

            Box(Modifier.height(8.dp))

            // Placeholder for Google Sign-in
            GlassButton(
                text = "تسجيل الدخول باستخدام Google",
                onClick = { /* سيتم ربطها لاحقاً */ },
                primary = false,
                modifier = Modifier.fillMaxWidth()
            )

            Box(Modifier.height(16.dp))

            TextButton(onClick = onSkip) {
                Text("تخطي الآن (لن يتم حفظ البيانات سحابياً)", color = Palette.TextDim)
            }
        }
    }
}
