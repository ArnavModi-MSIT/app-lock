package dev.abdullah.openlock

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppUpdate(val version: String, val url: String, val sha256: String, val size: Long, val testing: Boolean)

/** User-triggered HTTPS requests only. No credentials, telemetry, or background checks. */
class UpdateClient(private val context: Context) {
    private fun connection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 20_000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", "Simple-App-Lock")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    fun findUpdate(): AppUpdate? {
        val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0"
        val conn = connection("https://api.github.com/repos/ArnavModi-MSIT/app-lock/releases?per_page=20")
        val json = try {
            if (conn.responseCode in listOf(403, 429)) throw IOException("GitHub's request limit was reached. Try again later.")
            if (conn.responseCode != 200) throw IOException("Could not check updates. Try again later.")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally { conn.disconnect() }
        var best: AppUpdate? = null
        val releases = JSONArray(json)
        for (i in 0 until releases.length()) {
            val release = releases.getJSONObject(i)
            if (release.optBoolean("draft")) continue
            val version = release.optString("tag_name").removePrefix("v")
            if (!UpdatePolicy.isNewer(version, installed) || (best != null && !UpdatePolicy.isNewer(version, best.version))) continue
            val assets = release.optJSONArray("assets") ?: continue
            for (j in 0 until assets.length()) {
                val asset = assets.getJSONObject(j)
                if (!asset.optString("name").startsWith("Simple-App-Lock-v")) continue
                val url = asset.optString("browser_download_url")
                val digest = asset.optString("digest")
                val size = asset.optLong("size")
                if (UpdatePolicy.trustedAsset(url) && digest.matches(Regex("sha256:[0-9a-fA-F]{64}")) && size in 1..MAX_APK_SIZE) {
                    best = AppUpdate(version, url, digest.substringAfter(':').lowercase(), size, release.optBoolean("prerelease"))
                    break
                }
            }
        }
        return best
    }
    fun download(update: AppUpdate, progress: (Int) -> Unit): File {
        require(UpdatePolicy.trustedAsset(update.url))
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val partial = File(dir, "update.part.apk")
        val ready = File(dir, "update.apk")
        var url = update.url
        try {
            for (redirect in 0..5) {
                if (!UpdatePolicy.trustedDownloadHost(url)) throw IOException("Unexpected download destination.")
                val conn = connection(url).apply { setRequestProperty("Accept", "application/octet-stream") }
                try {
                    if (conn.responseCode in listOf(301, 302, 303, 307, 308)) {
                        url = URL(URL(url), conn.getHeaderField("Location") ?: throw IOException("Invalid download redirect.")).toString()
                        continue
                    }
                    if (conn.responseCode != 200) throw IOException("Could not download update. Try again.")
                    val digest = MessageDigest.getInstance("SHA-256")
                    var received = 0L; var reported = -1
                    conn.inputStream.use { input -> partial.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            if (received > MAX_APK_SIZE || received > update.size) throw IOException("Unexpected update size.")
                            output.write(buffer, 0, count); digest.update(buffer, 0, count)
                            val percent = (received * 100 / update.size).toInt()
                            if (percent != reported) { progress(percent); reported = percent }
                        }
                    } }
                    val actual = digest.digest().joinToString("") { "%02x".format(it) }
                    if (received != update.size || actual != update.sha256) throw IOException("Update verification failed. Please try downloading again.")
                    verifyPackage(partial)
                    if (ready.exists() && !ready.delete()) throw IOException("Could not replace the previous download.")
                    if (!partial.renameTo(ready)) throw IOException("Could not save the update.")
                    return ready
                } finally { conn.disconnect() }
            }
            throw IOException("Too many download redirects.")
        } finally { partial.delete() }
    }
    private fun verifyPackage(apk: File) {
        val pm = context.packageManager
        val current = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val next = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("Downloaded file is not a valid APK.")
        if (next.packageName != context.packageName || next.longVersionCode <= current.longVersionCode) throw IOException("This APK is not a newer version of this app.")
        val installedSigners = current.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        val newSigners = next.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet()
        if (installedSigners.isNullOrEmpty() || installedSigners != newSigners) throw IOException("Update has a different signing certificate. Installation was blocked.")
    }
    companion object { private const val MAX_APK_SIZE = 64L * 1024 * 1024 }
}
