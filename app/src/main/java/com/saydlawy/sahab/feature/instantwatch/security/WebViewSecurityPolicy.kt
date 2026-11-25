package com.saydlawy.sahab.feature.instantwatch.security

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView

class WebViewSecurityPolicy {

    @SuppressLint("SetJavaScriptEnabled")
    fun applyIsolatedSettings(webView: WebView) {
        val settings = webView.settings

        // Required for embedded video players; NO @JavascriptInterface bridge is ever added.
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false

        // Block popups and multiple windows
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)

        // Block local file and content URI access
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        @Suppress("DEPRECATION")
        run {
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
        }

        // Enforce HTTPS-only (never allow mixed content)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setGeolocationEnabled(false)
        settings.databaseEnabled = false
        settings.saveFormData = false

        // Isolate cookies from the rest of the app
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(false)
        cookieManager.setAcceptThirdPartyCookies(webView, false)
    }

    fun clearSessionData(webView: WebView) {
        runCatching {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }
}
