package dev.abdullah.openlock

import java.net.URI

object UpdatePolicy {
    private fun parts(version: String): List<Long>? {
        val core = version.removePrefix("v")
        if (!core.matches(Regex("[0-9]+(\\.[0-9]+){1,3}"))) return null
        return core.split('.').map { it.toLongOrNull() ?: return null }
    }
    fun isNewer(candidate: String, installed: String): Boolean {
        val next = parts(candidate) ?: return false
        val old = parts(installed) ?: return false
        for (i in 0 until maxOf(next.size, old.size)) {
            val difference = (next.getOrNull(i) ?: 0).compareTo(old.getOrNull(i) ?: 0)
            if (difference != 0) return difference > 0
        }
        return false
    }
    fun trustedAsset(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host == "github.com" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            uri.path.startsWith("/ArnavModi-MSIT/app-lock/releases/download/") && uri.path.endsWith(".apk")
    }.getOrDefault(false)
    fun trustedDownloadHost(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            uri.host in setOf("github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    }.getOrDefault(false)
}
