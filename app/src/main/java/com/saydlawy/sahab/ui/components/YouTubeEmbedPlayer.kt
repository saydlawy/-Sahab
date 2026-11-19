package com.saydlawy.sahab.ui.components

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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

private val YOUTUBE_VIDEO_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")
private const val APP_ORIGIN_BASE_URL = "https://com.saydlawy.sahab"

fun extractYouTubeVideoId(rawUrl: String): String? {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return null
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
    val host = uri.host?.lowercase()?.trimEnd('.') ?: return null

    val candidate: String? = when {
        host == "youtu.be" || host == "www.youtu.be" -> {
            uri.pathSegments.firstOrNull { it.isNotBlank() }
        }
        host == "youtube.com" || host.endsWith(".youtube.com") -> {
            uri.getQueryParameter("v")?.takeIf { it.isNotBlank() } ?: run {
                val segs = uri.pathSegments
                val markerIdx = segs.indexOfFirst {
                    it == "embed" || it == "shorts" || it == "live" || it == "v"
                }
                if (markerIdx >= 0 && markerIdx + 1 < segs.size) segs[markerIdx + 1] else null
            }
        }
        else -> null
    }?.trim()

    return candidate?.takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) }
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
    val cleanId = videoId.trim().takeIf { YOUTUBE_VIDEO_ID_REGEX.matches(it) } ?: return

    val context = LocalContext.current
    var webViewRef by remember(cleanId) { mutableStateOf<WebView?>(null) }
    var embedFailed by remember(cleanId) { mutableStateOf(false) }
    var sessionKey by remember(cleanId) { mutableIntStateOf(0) }

    fun releaseWebView() {
        val wv = webViewRef
        webViewRef = null
        wv?.let { view ->
            runCatching {
                view.stopLoading()
                view.loadUrl("about:blank")
                view.destroy()
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        webViewRef?.let { view ->
            runCatching {
                view.onPause()
                view.pauseTimers()
            }
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!embedFailed) {
            webViewRef?.let { view ->
                runCatching {
                    view.resumeTimers()
                    view.onResume()
                }
            }
        }
    }

    DisposableEffect(cleanId) {
        onDispose {
            releaseWebView()
        }
    }

    Box(modifier = modifier) {
        if (!embedFailed) {
            key("$cleanId-$sessionKey") {
                AndroidView(
                    factory = { ctx ->
                    WebView(ctx).apply {
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?,
                            ) {
                                if (request?.isForMainFrame == true) {
                                    embedFailed = true
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: RenderProcessGoneDetail?,
                            ): Boolean {
                                releaseWebView()
                                if (detail?.didCrash() == true) {
                                    sessionKey += 1
                                } else {
                                    embedFailed = true
                                }
                                return true
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false

                        val muteParam = if (muted) 1 else 0
                        val loopParam = if (loop) "1&playlist=$cleanId" else "0"
                        val controlsParam = if (showControls) 1 else 0
                        val embedSrc =
                            "https://www.youtube.com/embed/$cleanId?autoplay=1&mute=$muteParam&playsinline=1&controls=$controlsParam&rel=0&loop=$loopParam&modestbranding=1&origin=$APP_ORIGIN_BASE_URL"
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
                            APP_ORIGIN_BASE_URL,
                            html,
                            "text/html",
                            "utf-8",
                            null,
                        )
                        webViewRef = this
                    }
                },
                    modifier = Modifier.matchParentSize(),
                )
            }
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
