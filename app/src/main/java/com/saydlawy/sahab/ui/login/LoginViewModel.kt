package com.saydlawy.sahab.ui.login

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saydlawy.sahab.AppContainer
import com.saydlawy.sahab.data.Account
import com.saydlawy.sahab.util.Ar
import com.saydlawy.sahab.util.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val accountType: String = "XTREAM",
    val server: String = "",
    val user: String = "",
    val pass: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class LoginViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    fun onAccountTypeChange(type: String) {
        _state.update { it.copy(accountType = type, error = null) }
    }

    fun onServer(value: String) {
        // A full playlist link (…/get.php?username=…&password=…) fills in all three fields at once.
        val parsed = if (_state.value.accountType == "XTREAM" && value.contains("username=") && value.contains("password=")) {
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

        if (current.accountType == "M3U") {
            val url = current.server.trim()
            if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                _state.update { it.copy(error = "الرجاء إدخال رابط M3U صالح يبدأ بـ http") }
                return
            }
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch {
                try {
                    val account = Account(name = "M3U Playlist", type = "M3U", m3uUrl = url)
                    container.repo.login(account)
                    container.prefs.saveAccount(account)
                    container.userSyncService.saveAccounts(container.prefs.getAccounts())
                    _state.update { it.copy(loading = false) }
                    onSuccess()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    _state.update { it.copy(loading = false, error = e.userMessage()) }
                }
            }
            return
        }

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
                // حفظ في السحابة إذا كان المستخدم مسجل دخول
                container.userSyncService.saveXtreamAccount(account)
                container.userSyncService.saveAccounts(container.prefs.getAccounts())
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
