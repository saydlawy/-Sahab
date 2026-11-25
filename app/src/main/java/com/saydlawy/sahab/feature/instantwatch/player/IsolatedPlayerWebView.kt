package com.saydlawy.sahab.feature.instantwatch.player

import android.view.ViewGroup
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.saydlawy.sahab.feature.instantwatch.security.NavigationGuard
import com.saydlawy.sahab.feature.instantwatch.security.SecurityEventType
import com.saydlawy.sahab.feature.instantwatch.security.SecurityMonitor
import com.saydlawy.sahab.feature.instantwatch.security.WebViewSecurityPolicy

@Composable
fun IsolatedPlayerWebView(
    embedUrl: String,
    reloadKey: Int,
    navigationGuard: NavigationGuard,
    securityPolicy: WebViewSecurityPolicy,
    securityMonitor: SecurityMonitor,
    onPlaybackError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            securityPolicy.applyIsolatedSettings(this)

            setDownloadListener { url, _, _, _, _ ->
                navigationGuard.onExecutableDownloadBlocked(url.orEmpty())
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?,
                ): Boolean {
                    navigationGuard.onPopupAttempt(view?.url)
                    return false
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request ?: return
                    navigationGuard.onPermissionRequestDenied(request.resources ?: emptyArray())
                    request.deny()
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?,
                ) {
                    navigationGuard.onPermissionRequestDenied(arrayOf("geolocation"))
                    callback?.invoke(origin, false, false)
                }
            }

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    val targetUrl = request?.url?.toString()
                    val isMain = request?.isForMainFrame ?: true
                    return navigationGuard.shouldBlockNavigation(targetUrl, isMain)
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    request ?: return null
                    return navigationGuard.interceptRequest(request)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        val reason = error?.description?.toString()?.ifBlank { null }
                            ?: "فشل تحميل المشغل بعد تطبيق الحماية."
                        securityMonitor.record(SecurityEventType.PLAYBACK_FAILED_AFTER_PROTECTION, reason)
                        onPlaybackError(reason)
                    }
                }
            }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            securityPolicy.clearSessionData(webView)
            webView.destroy()
        }
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            val tagKey = "$embedUrl#$reloadKey"
            if (view.tag != tagKey) {
                view.tag = tagKey
                if (!navigationGuard.shouldBlockNavigation(embedUrl, isForMainFrame = true)) {
                    view.loadUrl(embedUrl)
                } else {
                    onPlaybackError("تم منع تحميل رابط التشغيل لعدم توافقه مع سياسة الأمان.")
                }
            }
        },
        modifier = modifier,
    )
}
