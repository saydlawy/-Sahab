package com.saydlawy.sahab.ui.components

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

fun extractYouTubeVideoId(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return null
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val host = uri.host?.lowercase().orEmpty()
    if (host.contains("youtu.be")) {
        return uri.pathSegments.firstOrNull { it.isNotBlank() }?.take(15)
    }
    if (host.contains("youtube.com")) {
        uri.getQueryParameter("v")?.takeIf { it.isNotBlank() }?.let { return it.take(15) }
        val segs = uri.pathSegments
        val markerIdx = segs.indexOfFirst { it == "embed" || it == "shorts" || it == "live" || it == "v" }
        if (markerIdx >= 0 && markerIdx + 1 < segs.size) {
            return segs[markerIdx + 1].take(15)
        }
    }
    return null
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    loop: Boolean = true,
    showControls: Boolean = false,
) {
    val cleanId = videoId.trim()
    if (cleanId.isEmpty()) return

    var webViewRef by remember(cleanId) { mutableStateOf<WebView?>(null) }

    fun releaseWebView() {
        webViewRef?.let { wv ->
            webViewRef = null
            runCatching {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.destroy()
            }
        }
    }

    LaunchedEffect(muted, webViewRef) {
        webViewRef?.let { wv ->
            val js = if (muted) {
                "(function(){var v=document.querySelector('video');if(v){v.muted=true;}var b=document.querySelector('.ytp-mute-button');if(b&&b.getAttribute('aria-label')&&b.getAttribute('aria-label').toLowerCase().indexOf('unmute')!==-1){b.click();}})();"
            } else {
                "(function(){var v=document.querySelector('video');if(v){v.muted=false;v.volume=1.0;}var b=document.querySelector('.ytp-mute-button');if(b&&b.getAttribute('aria-label')&&b.getAttribute('aria-label').toLowerCase().indexOf('mute ')!==-1){b.click();}})();"
            }
            runCatching { wv.evaluateJavascript(js, null) }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        releaseWebView()
    }

    DisposableEffect(cleanId) {
        onDispose {
            releaseWebView()
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val js = "(function(){var v=document.querySelector('video');if(v){v.playsInline=true;v.muted=" +
                            (if (muted) "true" else "false") +
                            ";v.play().catch(function(){});}})();"
                        runCatching { view?.evaluateJavascript(js, null) }
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                val muteParam = if (muted) 1 else 0
                val loopParam = if (loop) "1&playlist=$cleanId" else "0"
                val controlsParam = if (showControls) 1 else 0
                val embedUrl = "https://www.youtube.com/embed/$cleanId?autoplay=1&mute=$muteParam&playsinline=1&controls=$controlsParam&rel=0&loop=$loopParam&modestbranding=1"
                loadUrl(embedUrl)
                webViewRef = this
            }
        },
        update = { wv ->
            webViewRef = wv
        },
        modifier = modifier,
    )
}
