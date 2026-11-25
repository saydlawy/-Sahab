package com.saydlawy.sahab.feature.instantwatch.security

import android.net.Uri
import java.util.Locale

enum class RequestCategory {
    VIDEO_STREAM,
    SUBTITLE,
    IMAGE,
    PLAYER_ASSET,
    AD_OR_TRACKER,
    REDIRECT_OR_UNKNOWN,
}

data class InspectedRequest(
    val url: String,
    val category: RequestCategory,
    val allowed: Boolean,
    val reason: String,
)

class RequestInspector(
    private val policy: StreamSecurityPolicy,
) {
    fun inspect(
        url: String,
        isForMainFrame: Boolean = false,
        acceptHeader: String? = null,
    ): InspectedRequest {
        val policyCheck = policy.validateUrl(url, requireTrustedHost = false)
        if (!policyCheck.isAllowed) {
            return InspectedRequest(
                url = url,
                category = RequestCategory.AD_OR_TRACKER,
                allowed = false,
                reason = policyCheck.reason,
            )
        }

        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val host = uri?.host?.lowercase(Locale.US).orEmpty()
        val path = uri?.path?.lowercase(Locale.US).orEmpty()
        val query = uri?.query?.lowercase(Locale.US).orEmpty()
        val accept = acceptHeader?.lowercase(Locale.US).orEmpty()

        val category = classify(host, path, query, accept)

        if (category == RequestCategory.AD_OR_TRACKER) {
            return InspectedRequest(
                url = url,
                category = category,
                allowed = false,
                reason = "Blocked ad/tracker pattern on $host",
            )
        }

        if (isForMainFrame && !policy.isTrustedPlayerHost(host)) {
            return InspectedRequest(
                url = url,
                category = RequestCategory.REDIRECT_OR_UNKNOWN,
                allowed = false,
                reason = "Blocked untrusted main-frame navigation to $host",
            )
        }

        return InspectedRequest(
            url = url,
            category = category,
            allowed = true,
            reason = "Allowed (${category.name})",
        )
    }

    private fun classify(
        host: String,
        path: String,
        query: String,
        accept: String,
    ): RequestCategory {
        if (VIDEO_EXTENSIONS.any { path.endsWith(it) } ||
            path.contains("/hls/") || path.contains("/dash/") ||
            accept.contains("video/") || accept.contains("mpegurl")
        ) {
            return RequestCategory.VIDEO_STREAM
        }

        if (SUBTITLE_EXTENSIONS.any { path.endsWith(it) } || accept.contains("text/vtt")) {
            return RequestCategory.SUBTITLE
        }

        if (IMAGE_EXTENSIONS.any { path.endsWith(it) } || accept.contains("image/")) {
            return RequestCategory.IMAGE
        }

        if (AD_KEYWORDS.any { path.contains(it) || query.contains(it) || host.startsWith("ads.") || host.contains(".ads.") }) {
            return RequestCategory.AD_OR_TRACKER
        }

        if (PLAYER_ASSET_EXTENSIONS.any { path.endsWith(it) } || policy.isTrustedPlayerHost(host)) {
            return RequestCategory.PLAYER_ASSET
        }

        return RequestCategory.REDIRECT_OR_UNKNOWN
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(".m3u8", ".mpd", ".mp4", ".webm", ".ts", ".m4s", ".m4v", ".mkv")
        private val SUBTITLE_EXTENSIONS = setOf(".vtt", ".srt", ".ass", ".ssa", ".ttml")
        private val IMAGE_EXTENSIONS = setOf(".jpg", ".jpeg", ".png", ".webp", ".gif", ".svg", ".ico")
        private val PLAYER_ASSET_EXTENSIONS = setOf(".js", ".css", ".wasm", ".woff", ".woff2", ".json")
        private val AD_KEYWORDS = setOf(
            "/popunder", "/popup", "/adserver", "/adclick", "/advert",
            "/banner_ad", "/vast", "/vpaid", "clickid=", "pop_id=",
        )
    }
}
