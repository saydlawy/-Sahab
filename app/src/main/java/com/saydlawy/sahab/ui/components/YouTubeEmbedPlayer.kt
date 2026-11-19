package com.saydlawy.sahab.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val context = LocalContext.current
    var webViewRef by remember(cleanId) { mutableStateOf<WebView?>(null) }
    var embedFailed by remember(cleanId) { mutableStateOf(false) }

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

    Box(modifier = modifier) {
        if (!embedFailed) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                val js = """
                                    (function(){
                                        var txt = document.body ? (document.body.innerText || '') : '';
                                        if (txt.indexOf('153') !== -1 && txt.toLowerCase().indexOf('error') !== -1) {
                                            return 'ERR_153';
                                        }
                                        var v = document.querySelector('video');
                                        if (v) {
                                            v.playsInline = true;
                                            v.muted = ${if (muted) "true" else "false"};
                                            v.play().catch(function(){});
                                        }
                                        return 'OK';
                                    })();
                                """.trimIndent()
                                runCatching {
                                    view?.evaluateJavascript(js) { res ->
                                        if (res?.contains("ERR_153") == true) {
                                            embedFailed = true
                                        }
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    embedFailed = true
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

                        val muteParam = if (muted) 1 else 0
                        val loopParam = if (loop) "1&playlist=$cleanId" else "0"
                        val controlsParam = if (showControls) 1 else 0
                        val embedSrc =
                            "https://www.youtube.com/embed/$cleanId?autoplay=1&mute=$muteParam&playsinline=1&controls=$controlsParam&rel=0&loop=$loopParam&modestbranding=1&enablejsapi=1"
                        val html = """
                            <!DOCTYPE html>
                            <html>
                            <head>
                              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                              <style>
                                html, body, iframe {
                                  margin: 0; padding: 0; width: 100%; height: 100%;
                                  background: transparent; border: 0; overflow: hidden;
                                }
                              </style>
                            </head>
                            <body>
                              <iframe
                                src="$embedSrc"
                                allow="autoplay; encrypted-media; picture-in-picture"
                                referrerpolicy="strict-origin-when-cross-origin"
                                allowfullscreen>
                              </iframe>
                            </body>
                            </html>
                        """.trimIndent()
                        loadDataWithBaseURL(
                            "https://www.youtube.com",
                            html,
                            "text/html",
                            "utf-8",
                            null,
                        )
                        webViewRef = this
                    }
                },
                update = { wv ->
                    webViewRef = wv
                },
                modifier = Modifier.matchParentSize(),
            )
        } else {
            TextButton(
                onClick = {
                    runCatching {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/watch?v=$cleanId"),
                        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        context.startActivity(intent)
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp),
            ) {
                Text(
                    text = "▶ تشغيل التريلر على YouTube",
                    color = Color.White,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
