package com.saydlawy.sahab.feature.instantwatch.player

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
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

data class SniffedStream(
    val url: String,
    val headers: Map<String, String>,
)

data class EmbedPlaybackSync(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
)

private const val SMART_SYNC_JS = """
(function() {
  if (window.__sahabSyncInstalled) return;
  window.__sahabSyncInstalled = true;
  var lastSent = 0;
  function send(posSec, durSec, playing) {
    if (!window.SahabSyncBridge || typeof posSec !== 'number' || isNaN(posSec) || posSec < 0) return;
    var now = Date.now();
    if (now - lastSent < 220) return;
    lastSent = now;
    var d = (typeof durSec === 'number' && isFinite(durSec) && durSec > 0) ? durSec : 0;
    window.SahabSyncBridge.onTimeUpdate(Math.floor(posSec * 1000), Math.floor(d * 1000), !!playing);
  }
  window.addEventListener('message', function(e) {
    try {
      var msg = (typeof e.data === 'string') ? JSON.parse(e.data) : e.data;
      if (!msg || typeof msg !== 'object') return;
      var payload = msg.data || msg;
      if (typeof payload.currentTime === 'number') {
        var playing = payload.event !== 'pause' && payload.event !== 'ended';
        send(payload.currentTime, payload.duration || 0, playing);
      } else if (payload.progress && typeof payload.progress.watched === 'number') {
        send(payload.progress.watched, payload.progress.duration || 0, true);
      }
    } catch (_) {}
  });
  setInterval(function() {
    try {
      var vids = document.querySelectorAll('video');
      if (!vids || vids.length === 0) return;
      var main = null;
      for (var i = 0; i < vids.length; i++) {
        var v = vids[i];
        if (!v || isNaN(v.currentTime)) continue;
        var dur = isFinite(v.duration) ? v.duration : 0;
        if (!main || dur > (isFinite(main.duration) ? main.duration : 0)) {
          main = v;
        }
      }
      if (main) {
        var d = isFinite(main.duration) ? main.duration : 0;
        if (d === 0 || d > 30 || vids.length === 1) {
          send(main.currentTime, d, !main.paused && !main.ended && main.readyState > 2);
        }
      }
    } catch (_) {}
  }, 300);
})();
"""

@Composable
fun IsolatedPlayerWebView(
    embedUrl: String,
    reloadKey: Int,
    navigationGuard: NavigationGuard,
    securityPolicy: WebViewSecurityPolicy,
    securityMonitor: SecurityMonitor,
    onPlaybackError: (String) -> Unit,
    modifier: Modifier = Modifier,
    onStreamSniffed: ((SniffedStream) -> Unit)? = null,
    onPlaybackSync: ((EmbedPlaybackSync) -> Unit)? = null,
) {
    var alreadySniffed = remember(embedUrl, reloadKey) { false }
    val context = LocalContext.current
    val webView = remember(context) {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            securityPolicy.applyIsolatedSettings(this)

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onTimeUpdate(posMs: Long, durMs: Long, playing: Boolean) {
                        if (onPlaybackSync != null && posMs >= 0L) {
                            post {
                                onPlaybackSync(EmbedPlaybackSync(posMs, durMs.coerceAtLeast(0L), playing))
                            }
                        }
                    }
                },
                "SahabSyncBridge",
            )

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
                    val blocked = navigationGuard.interceptRequest(request)
                    if (blocked != null) return blocked

                    if (onStreamSniffed != null && !alreadySniffed) {
                        val reqUrl = request.url?.toString().orEmpty()
                        val lower = reqUrl.lowercase()
                        val isVideoStream = (lower.contains(".m3u8") || lower.contains("master.txt")) &&
                            !lower.contains("google") &&
                            !lower.contains("doubleclick") &&
                            !lower.contains("ads") &&
                            !lower.contains("analytics") &&
                            !lower.contains("banner") &&
                            !lower.contains("thumb")
                        if (isVideoStream && reqUrl.startsWith("http")) {
                            alreadySniffed = true
                            val hdrs = HashMap<String, String>()
                            request.requestHeaders?.forEach { (k, v) ->
                                if (k.equals("Referer", true) ||
                                    k.equals("Origin", true) ||
                                    k.equals("User-Agent", true) ||
                                    k.equals("Cookie", true)
                                ) {
                                    hdrs[k] = v
                                }
                            }
                            if (!hdrs.containsKey("Cookie")) {
                                val cookie = runCatching {
                                    CookieManager.getInstance().getCookie(reqUrl)
                                }.getOrNull()
                                if (!cookie.isNullOrBlank()) {
                                    hdrs["Cookie"] = cookie
                                }
                            }
                            view?.post {
                                onStreamSniffed(SniffedStream(reqUrl, hdrs))
                            }
                        }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (onPlaybackSync != null) {
                        view?.evaluateJavascript(SMART_SYNC_JS, null)
                    }
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
