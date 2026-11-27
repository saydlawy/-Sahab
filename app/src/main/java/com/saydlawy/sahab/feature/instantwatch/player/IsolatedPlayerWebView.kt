package com.saydlawy.sahab.feature.instantwatch.player

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import java.util.Collections
import com.saydlawy.sahab.feature.instantwatch.security.NavigationGuard
import com.saydlawy.sahab.feature.instantwatch.security.SecurityEventType
import com.saydlawy.sahab.feature.instantwatch.security.SecurityMonitor
import com.saydlawy.sahab.feature.instantwatch.security.WebViewSecurityPolicy

data class SniffedStream(
    val url: String,
    val headers: Map<String, String>,
    val label: String = "",
)

private fun inferStreamLabel(url: String): String {
    val lower = url.lowercase()
    val quality = when {
        "2160" in lower || "4k" in lower -> "4K"
        "1080" in lower -> "1080p"
        "720" in lower -> "720p"
        "480" in lower -> "480p"
        "360" in lower -> "360p"
        "master" in lower || "index.m3u8" in lower || "playlist.m3u8" in lower -> "تلقائي (Master)"
        else -> null
    }
    val format = when {
        ".m3u8" in lower || "m3u8?" in lower || "master.txt" in lower -> "HLS"
        ".mpd" in lower -> "DASH"
        ".mp4" in lower -> "MP4"
        else -> "Stream"
    }
    return if (quality != null) "$quality • $format" else format
}

data class EmbedPlaybackSync(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
)

class WebViewPlayerController {
    internal var onToggle: (() -> Unit)? = null
    internal var onSeekBy: ((Int) -> Unit)? = null
    internal var onToggleMute: (() -> Unit)? = null

    fun togglePlayback() {
        onToggle?.invoke()
    }

    fun seekBy(deltaSec: Int) {
        onSeekBy?.invoke(deltaSec)
    }

    fun toggleMute() {
        onToggleMute?.invoke()
    }
}

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
        if (!window.__sahabUserMuted && (main.muted || main.volume === 0)) {
          main.muted = false;
          main.volume = 1.0;
        }
        var d = isFinite(main.duration) ? main.duration : 0;
        if (d === 0 || d > 30 || vids.length === 1) {
          send(main.currentTime, d, !main.paused && !main.ended && main.readyState > 2);
        }
      }
    } catch (_) {}
  }, 300);
})();
"""

private const val JS_TOGGLE_PLAYBACK =
    "(function(){var vs=document.querySelectorAll('video');var m=null;for(var i=0;i<vs.length;i++){var v=vs[i];if(!m||(v.duration||0)>(m.duration||0)){m=v;}}if(!m){return false;}if(m.paused){var p=m.play();if(p&&p.catch){p.catch(function(){});}}else{m.pause();}return true;})()"

private const val JS_TOGGLE_MUTE =
    "(function(){var vs=document.querySelectorAll('video');if(!vs||vs.length===0){return 'none';}var m=vs[0];for(var i=0;i<vs.length;i++){if((vs[i].duration||0)>(m.duration||0)){m=vs[i];}}var nextMuted=!m.muted;window.__sahabUserMuted=nextMuted;for(var j=0;j<vs.length;j++){vs[j].muted=nextMuted;if(!nextMuted){vs[j].volume=1.0;}}return nextMuted?'muted':'unmuted';})()"

private fun jsSeekBy(deltaSec: Int): String =
    "(function(d){var vs=document.querySelectorAll('video');var m=null;for(var i=0;i<vs.length;i++){var v=vs[i];if(!m||(v.duration||0)>(m.duration||0)){m=v;}}if(!m){return false;}m.currentTime=Math.max(0,m.currentTime+d);return true;})($deltaSec)"

/**
 * WebView that understands a TV remote: OK / Play-Pause toggles the page's <video>, LEFT / RIGHT seek by
 * 10 seconds, and UP hands focus back to the Compose top bar so the user is never trapped inside the page.
 */
private class RemoteAwareWebView(context: Context) : WebView(context) {
    init {
        // Keep D-pad focus owned by the Compose control layer so TV remotes never get trapped inside ads/iframes.
        isFocusable = false
        isFocusableInTouchMode = false
    }

    fun triggerTogglePlayback() {
        evaluateJavascript(JS_TOGGLE_PLAYBACK) { result ->
            // No <video> reachable (e.g. cross-origin iframe / custom play button): tap the page center.
            if (result != "true") tapCenter()
        }
    }

    fun triggerSeekBy(deltaSec: Int) {
        evaluateJavascript(jsSeekBy(deltaSec)) { result ->
            if (result != "true") {
                // Cross-origin iframe fallback: dispatch standard arrow key directly to Chromium.
                val keyCode = if (deltaSec >= 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                val now = SystemClock.uptimeMillis()
                super.dispatchKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
                super.dispatchKeyEvent(KeyEvent(now, now + 40L, KeyEvent.ACTION_UP, keyCode, 0))
            }
        }
    }

    private fun tapCenter() {
        val now = SystemClock.uptimeMillis()
        val x = width / 2f
        val y = height / 2f
        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(now, now + 50L, MotionEvent.ACTION_UP, x, y, 0)
        dispatchTouchEvent(down)
        dispatchTouchEvent(up)
        down.recycle()
        up.recycle()
    }
}

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
    onRequestExitFocus: (() -> Unit)? = null,
    focusRequester: FocusRequester = remember { FocusRequester() },
    controller: WebViewPlayerController? = null,
) {
    val sniffedUrls = remember { Collections.synchronizedSet(LinkedHashSet<String>()) }
    val latestNavGuard by rememberUpdatedState(navigationGuard)
    val latestOnError by rememberUpdatedState(onPlaybackError)
    val latestOnSniffed by rememberUpdatedState(onStreamSniffed)
    val latestOnSync by rememberUpdatedState(onPlaybackSync)
    val latestExit by rememberUpdatedState(onRequestExitFocus)

    var hudBadge by remember { mutableStateOf<String?>(null) }
    var hudPing by remember { mutableIntStateOf(0) }
    LaunchedEffect(hudPing) {
        if (hudBadge != null) {
            delay(1_800L)
            hudBadge = null
        }
    }

    LaunchedEffect(embedUrl, reloadKey) {
        sniffedUrls.clear()
    }

    val context = LocalContext.current
    val webView = remember(context) {
        RemoteAwareWebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            securityPolicy.applyIsolatedSettings(this)

            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun onTimeUpdate(posMs: Long, durMs: Long, playing: Boolean) {
                        val syncCb = latestOnSync
                        if (syncCb != null && posMs >= 0L) {
                            post {
                                syncCb(EmbedPlaybackSync(posMs, durMs.coerceAtLeast(0L), playing))
                            }
                        }
                    }
                },
                "SahabSyncBridge",
            )

            setDownloadListener { url, _, _, _, _ ->
                latestNavGuard.onExecutableDownloadBlocked(url.orEmpty())
            }

            webChromeClient = object : WebChromeClient() {
                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: android.os.Message?,
                ): Boolean {
                    latestNavGuard.onPopupAttempt(view?.url)
                    return false
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request ?: return
                    latestNavGuard.onPermissionRequestDenied(request.resources ?: emptyArray())
                    request.deny()
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?,
                ) {
                    latestNavGuard.onPermissionRequestDenied(arrayOf("geolocation"))
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
                    return latestNavGuard.shouldBlockNavigation(targetUrl, isMain)
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    request ?: return null
                    val blocked = latestNavGuard.interceptRequest(request)
                    if (blocked != null) return blocked

                    val sniffCb = latestOnSniffed
                    if (sniffCb != null && sniffedUrls.size < 12) {
                        val reqUrl = request.url?.toString().orEmpty()
                        val lower = reqUrl.lowercase()
                        val pathPart = lower.substringBefore('?')
                        val isNonVideoAsset =
                            pathPart.endsWith(".json") ||
                            pathPart.endsWith(".webmanifest") ||
                            pathPart.endsWith(".js") ||
                            pathPart.endsWith(".css") ||
                            pathPart.endsWith(".png") ||
                            pathPart.endsWith(".jpg") ||
                            pathPart.endsWith(".jpeg") ||
                            pathPart.endsWith(".webp") ||
                            pathPart.endsWith(".svg") ||
                            pathPart.endsWith(".ico") ||
                            pathPart.endsWith(".woff") ||
                            pathPart.endsWith(".woff2") ||
                            pathPart.endsWith(".vtt") ||
                            pathPart.endsWith(".srt")
                        val isVideoStream = !isNonVideoAsset && (
                            lower.contains(".m3u8") ||
                            lower.contains("m3u8?") ||
                            lower.contains("master.txt") ||
                            lower.contains(".mp4") ||
                            lower.contains(".mpd")
                        ) &&
                            !lower.contains("google") &&
                            !lower.contains("doubleclick") &&
                            !lower.contains("ads") &&
                            !lower.contains("analytics") &&
                            !lower.contains("banner") &&
                            !lower.contains("thumb")
                        if (isVideoStream && reqUrl.startsWith("http") && sniffedUrls.add(reqUrl)) {
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
                            val stream = SniffedStream(reqUrl, hdrs, inferStreamLabel(reqUrl))
                            view?.post {
                                sniffCb(stream)
                            }
                        }
                    }
                    return null
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (latestOnSync != null) {
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
                        latestOnError(reason)
                    }
                }
            }
        }
    }

    DisposableEffect(webView, controller) {
        controller?.onToggle = {
            webView.triggerTogglePlayback()
            hudBadge = "تشغيل / إيقاف"
            hudPing++
        }
        controller?.onSeekBy = { delta ->
            webView.triggerSeekBy(delta)
            hudBadge = if (delta >= 0) "+${delta} ث ⏩" else "⏪ ${delta} ث"
            hudPing++
        }
        controller?.onToggleMute = {
            webView.evaluateJavascript(JS_TOGGLE_MUTE) { res ->
                hudBadge = if (res?.contains("muted") == true && !res.contains("unmuted")) "تم كتم الصوت 🔇" else "تم فتح الصوت 🔊"
                hudPing++
            }
        }
        onDispose {
            controller?.onToggle = null
            controller?.onSeekBy = null
            controller?.onToggleMute = null
        }
    }

    DisposableEffect(webView) {
        onDispose {
            securityPolicy.clearSessionData(webView)
            webView.destroy()
        }
    }

    Box(
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { ev ->
                val k = ev.key
                val isHandled =
                    k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter ||
                        k == Key.MediaPlayPause || k == Key.MediaPlay || k == Key.MediaPause ||
                        k == Key.DirectionLeft || k == Key.DirectionRight ||
                        k == Key.MediaRewind || k == Key.MediaFastForward ||
                        k == Key.DirectionUp || k == Key.DirectionDown
                if (!isHandled) return@onPreviewKeyEvent false
                if (ev.type == KeyEventType.KeyDown) {
                    when (k) {
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                        Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                            webView.triggerTogglePlayback()
                            hudBadge = "تشغيل / إيقاف"
                            hudPing++
                        }
                        Key.DirectionLeft, Key.MediaRewind -> {
                            webView.triggerSeekBy(-10)
                            hudBadge = "⏪ -10 ث"
                            hudPing++
                        }
                        Key.DirectionRight, Key.MediaFastForward -> {
                            webView.triggerSeekBy(10)
                            hudBadge = "+10 ث ⏩"
                            hudPing++
                        }
                        Key.DirectionUp -> {
                            latestExit?.invoke()
                        }
                        Key.DirectionDown -> {
                            hudBadge = "▲ القائمة العلوية  •  ◀ ▶ تقديم/تأخير  •  OK تشغيل"
                            hudPing++
                        }
                    }
                }
                true
            },
    ) {
        AndroidView(
            factory = { webView },
            update = { view ->
                val tagKey = "$embedUrl#$reloadKey"
                if (view.tag != tagKey) {
                    view.tag = tagKey
                    sniffedUrls.clear()
                    if (!latestNavGuard.shouldBlockNavigation(embedUrl, isForMainFrame = true)) {
                        view.loadUrl(embedUrl)
                    } else {
                        latestOnError("تم منع تحميل رابط التشغيل لعدم توافقه مع سياسة الأمان.")
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = hudBadge != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xCC0B1120), RoundedCornerShape(999.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = hudBadge.orEmpty(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
