package com.saydlawy.sahab.feature.instantwatch.security

import android.net.Uri
import java.net.InetAddress
import java.util.Locale

enum class PolicyVerdict {
    ALLOW,
    BLOCK_INSECURE_SCHEME,
    BLOCK_LOCAL_NETWORK,
    BLOCK_EXTERNAL_INTENT,
    BLOCK_EXECUTABLE_DOWNLOAD,
    BLOCK_UNTRUSTED_DOMAIN,
    BLOCK_MALFORMED_URL,
}

data class SecurityCheckResult(
    val verdict: PolicyVerdict,
    val reason: String,
    val host: String? = null,
) {
    val isAllowed: Boolean get() = verdict == PolicyVerdict.ALLOW
}

class StreamSecurityPolicy(
    private val trustedPlayerHosts: Set<String> = DEFAULT_TRUSTED_PLAYER_HOSTS,
    private val blockedAdHosts: Set<String> = DEFAULT_BLOCKED_AD_HOSTS,
) {
    fun validateUrl(rawUrl: String, requireTrustedHost: Boolean = false): SecurityCheckResult {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return SecurityCheckResult(PolicyVerdict.BLOCK_MALFORMED_URL, "Empty URL")
        }

        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return SecurityCheckResult(PolicyVerdict.BLOCK_MALFORMED_URL, "Unparseable URL")

        val scheme = uri.scheme?.lowercase(Locale.US).orEmpty()
        if (scheme in BLOCKED_SCHEMES) {
            return SecurityCheckResult(
                PolicyVerdict.BLOCK_EXTERNAL_INTENT,
                "Blocked external/intent scheme: $scheme",
            )
        }
        if (scheme != "https") {
            return SecurityCheckResult(
                PolicyVerdict.BLOCK_INSECURE_SCHEME,
                "Only HTTPS is allowed (got: $scheme)",
            )
        }

        val host = uri.host?.lowercase(Locale.US)?.trimEnd('.')
            ?: return SecurityCheckResult(PolicyVerdict.BLOCK_MALFORMED_URL, "Missing host")

        if (isLocalOrPrivateHost(host)) {
            return SecurityCheckResult(
                PolicyVerdict.BLOCK_LOCAL_NETWORK,
                "Blocked local/private network host: $host",
                host,
            )
        }

        val path = uri.path?.lowercase(Locale.US).orEmpty()
        if (BLOCKED_EXECUTABLE_EXTENSIONS.any { path.endsWith(it) }) {
            return SecurityCheckResult(
                PolicyVerdict.BLOCK_EXECUTABLE_DOWNLOAD,
                "Blocked executable download path: $path",
                host,
            )
        }

        if (matchesHostSet(host, blockedAdHosts) || host.contains("flaxfer")) {
            return SecurityCheckResult(
                PolicyVerdict.BLOCK_UNTRUSTED_DOMAIN,
                "Blocked known ad/redirect host: $host",
                host,
            )
        }

        if (requireTrustedHost && !matchesHostSet(host, trustedPlayerHosts)) {
            return SecurityCheckResult(
                PolicyVerdict.BLOCK_UNTRUSTED_DOMAIN,
                "Host is not in trusted player hosts: $host",
                host,
            )
        }

        return SecurityCheckResult(PolicyVerdict.ALLOW, "Allowed", host)
    }

    fun isTrustedPlayerHost(host: String?): Boolean {
        val normalized = host?.lowercase(Locale.US)?.trimEnd('.') ?: return false
        return matchesHostSet(normalized, trustedPlayerHosts)
    }

    fun isLocalOrPrivateHost(host: String): Boolean {
        val h = host.lowercase(Locale.US).trim('[', ']')
        if (h == "localhost" || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".lan")) {
            return true
        }
        if (h == "::1" || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) {
            return true
        }
        val ipv4Match = IPV4_REGEX.matchEntire(h)
        if (ipv4Match != null) {
            val a = ipv4Match.groupValues[1].toIntOrNull() ?: return true
            val b = ipv4Match.groupValues[2].toIntOrNull() ?: return true
            return a == 0 || a == 10 || a == 127 ||
                (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 100 && b in 64..127)
        }
        return runCatching {
            val addr = InetAddress.getByName(h)
            addr.isAnyLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress
        }.getOrDefault(false)
    }

    private fun matchesHostSet(host: String, set: Set<String>): Boolean =
        set.any { domain -> host == domain || host.endsWith(".$domain") }

    companion object {
        private val IPV4_REGEX = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

        private val BLOCKED_SCHEMES = setOf(
            "intent", "market", "tel", "mailto", "sms", "geo",
            "file", "content", "javascript", "data", "about", "blob",
            "tg", "whatsapp", "fb", "twitter", "play",
        )

        private val BLOCKED_EXECUTABLE_EXTENSIONS = setOf(
            ".apk", ".aab", ".xapk", ".apks", ".apkm",
            ".exe", ".msi", ".bat", ".cmd", ".sh", ".dex", ".so", ".jar", ".dmg",
        )

        val DEFAULT_TRUSTED_PLAYER_HOSTS = setOf(
            "vidlink.pro",
            "vidsrc.cc",
            "embed.su",
            "player.autoembed.cc",
            "autoembed.cc",
            "autoembed.sbs",
            "2embed.cc",
            "www.2embed.cc",
            "tmdb.org",
            "themoviedb.org",
            "opensubtitles.com",
            "opensubtitles.org",
        )

        val DEFAULT_BLOCKED_AD_HOSTS = setOf(
            "doubleclick.net",
            "googlesyndication.com",
            "googleadservices.com",
            "adservice.google.com",
            "popads.net",
            "popcash.net",
            "propellerads.com",
            "exoclick.com",
            "adsterra.com",
            "juicyads.com",
            "trafficjunky.com",
            "clickadu.com",
            "hilltopads.net",
            "monetag.com",
        )
    }
}
