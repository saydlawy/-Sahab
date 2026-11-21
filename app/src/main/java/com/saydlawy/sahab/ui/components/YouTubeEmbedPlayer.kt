package com.saydlawy.sahab.ui.components

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

private val YOUTUBE_ID_REGEX = Regex("^[A-Za-z0-9_-]{11}$")

/** Extracts a valid 11-character YouTube video ID from standard watch, embed, shorts, or youtu.be URLs. */
fun extractYouTubeVideoId(url: String?): String? {
    val raw = url?.trim().orEmpty()
    if (raw.isEmpty()) return null
    if (YOUTUBE_ID_REGEX.matches(raw)) return raw
    val patterns = listOf(
        Regex("""(?:youtube\.com/watch\?(?:.*&)?v=)([A-Za-z0-9_-]{11})"""),
        Regex("""(?:youtu\.be/)([A-Za-z0-9_-]{11})"""),
        Regex("""(?:youtube\.com/embed/)([A-Za-z0-9_-]{11})"""),
        Regex("""(?:youtube\.com/shorts/)([A-Za-z0-9_-]{11})"""),
        Regex("""(?:youtube\.com/live/)([A-Za-z0-9_-]{11})"""),
    )
    for (p in patterns) {
        val id = p.find(raw)?.groupValues?.getOrNull(1)
        if (id != null && YOUTUBE_ID_REGEX.matches(id)) return id
    }
    return null
}

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun YouTubeEmbedPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    loop: Boolean = true,
    showControls: Boolean = false,
    onPlaying: () -> Unit = {},
) {
    val cleanId = remember(videoId) { extractYouTubeVideoId(videoId).orEmpty() }
    if (cleanId.isEmpty()) return

    val currentOnPlaying by rememberUpdatedState(onPlaying)
    var renderEpoch by remember(cleanId) { mutableIntStateOf(0) }
    var renderFailed by remember(cleanId) { mutableStateOf(false) }
    var webViewRef by remember(cleanId, renderEpoch) { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember(cleanId, renderEpoch) { mutableStateOf(false) }

    val embedHtml = remember(cleanId, loop, showControls) {
        buildYouTubeEmbedHtml(
            videoId = cleanId,
            muted = muted,
            loop = loop,
            showControls = showControls,
        )
    }

    LaunchedEffect(muted, pageLoaded, webViewRef) {
        val wv = webViewRef ?: return@LaunchedEffect
        if (!pageLoaded) return@LaunchedEffect
        val js = if (muted) {
            "if(window.ytPlayer&&ytPlayer.mute){ytPlayer.mute();}"
        } else {
            "if(window.ytPlayer&&ytPlayer.unMute){ytPlayer.unMute();ytPlayer.setVolume(100);}"
        }
        wv.evaluateJavascript(js, null)
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        webViewRef?.onPause()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        webViewRef?.onResume()
    }

    DisposableEffect(cleanId, renderEpoch) {
        onDispose {
            webViewRef?.let { wv ->
                runCatching {
                    wv.removeJavascriptInterface("SahabYtBridge")
                    wv.stopLoading()
                    wv.loadUrl("about:blank")
                    wv.clearHistory()
                    wv.removeAllViews()
                    wv.destroy()
                }
            }
            webViewRef = null
        }
    }

    Box(modifier = modifier) {
        if (!renderFailed) {
            androidx.compose.runtime.key(cleanId, renderEpoch) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            setBackgroundColor(Color.TRANSPARENT)
                            isFocusable = false
                            isFocusableInTouchMode = false
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            overScrollMode = View.OVER_SCROLL_NEVER

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                mediaPlaybackRequiresUserGesture = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    safeBrowsingEnabled = false
                                }
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                            }

                            addJavascriptInterface(
                                object {
                                    @JavascriptInterface
                                    fun onStatePlaying() {
                                        post { currentOnPlaying() }
                                    }
                                },
                                "SahabYtBridge",
                            )

                            webChromeClient = object : WebChromeClient() {}
                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    pageLoaded = true
                                }
                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    // Ignore non-fatal subresource errors
                                }
                                override fun onRenderProcessGone(
                                    view: WebView?,
                                    detail: RenderProcessGoneDetail?,
                                ): Boolean {
                                    webViewRef = null
                                    if (renderEpoch < MAX_WEBVIEW_RENDER_RETRIES) {
                                        renderEpoch++
                                    } else {
                                        renderFailed = true
                                    }
                                    return true
                                }
                            }

                            webViewRef = this
                            loadDataWithBaseURL(
                                "https://com.saydlawy.sahab",
                                embedHtml,
                                "text/html",
                                "UTF-8",
                                null,
                            )
                        }
                    },
                )
            }
        }
    }
}

private const val MAX_WEBVIEW_RENDER_RETRIES = 1

private fun buildYouTubeEmbedHtml(
    videoId: String,
    muted: Boolean,
    loop: Boolean,
    showControls: Boolean,
): String {
    val muteVal = if (muted) 1 else 0
    val loopVal = if (loop) 1 else 0
    val controlsVal = if (showControls) 1 else 0
    val playlistParam = if (loop) "playlist: '$videoId'," else ""
    return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no">
<style>
  html, body {
    margin: 0; padding: 0; width: 100%; height: 100%;
    background: transparent; overflow: hidden;
  }
  #player, iframe {
    position: fixed; top: 0; left: 0;
    width: 100%; height: 100%; border: 0;
  }
</style>
</head>
<body>
<div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var ytPlayer;
  function onYouTubeIframeAPIReady() {
    ytPlayer = new YT.Player('player', {
      videoId: '$videoId',
      playerVars: {
        autoplay: 1,
        mute: $muteVal,
        controls: $controlsVal,
        rel: 0,
        modestbranding: 1,
        playsinline: 1,
        loop: $loopVal,
        $playlistParam
        origin: 'https://com.saydlawy.sahab'
      },
      events: {
        onReady: function(e) {
          if ($muteVal === 1) { e.target.mute(); }
          e.target.playVideo();
        },
        onStateChange: function(e) {
          if (e.data === 1 && window.SahabYtBridge) {
            SahabYtBridge.onStatePlaying();
          }
        }
      }
    });
  }
</script>
</body>
</html>
""".trimIndent()
}
