package dev.abdullah.openlock

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.util.concurrent.Executors

class UpdateController(private val activity: FragmentActivity, private val authorized: () -> Boolean, private val statusChanged: (String) -> Unit) {
    private val worker = Executors.newSingleThreadExecutor()
    private val client = UpdateClient(activity.applicationContext)
    private var busy = false
    private var dialog: AlertDialog? = null
    private var downloaded: File? = null
    private var waitingForPermission = false
    private var offerOnResume = false
    var status = "Check for a newer version from our GitHub releases."
        private set
    private fun status(text: String) { status = text; statusChanged(text) }
    private fun visible(): Boolean = !activity.isDestroyed && !activity.isFinishing && authorized()
    fun check() {
        if (busy) return
        downloaded?.takeIf { it.exists() }?.let { offerInstall(); return }
        busy = true; status("Checking for updates…")
        worker.execute {
            val result = runCatching { client.findUpdate() }
            activity.runOnUiThread {
                busy = false
                if (activity.isDestroyed) return@runOnUiThread
                result.fold(onSuccess = { update ->
                    if (update == null) status("You are up to date.")
                    else {
                        status("Version ${update.version} is available. Tap Check for updates to download.")
                        if (visible()) {
                            dialog = AlertDialog.Builder(activity).setTitle("Update available: ${update.version}")
                                .setMessage("${if (update.testing) "Personal test build. " else ""}Download ${(update.size / 1024 / 1024.0).let { "%.1f".format(it) }} MB? Android will ask you to confirm installation. Your PIN and selected apps are kept.")
                                .setNegativeButton("Later", null).setPositiveButton("Download") { _, _ -> download(update) }.show()
                        }
                    }
                }, onFailure = { status(it.message ?: "Could not check updates. Check your internet and try again.") })
            }
        }
    }
    private fun download(update: AppUpdate) {
        busy = true; status("Downloading update…")
        worker.execute {
            val result = runCatching { client.download(update) { percent ->
                activity.runOnUiThread { if (!activity.isDestroyed) status("Downloading update… $percent%") }
            } }
            activity.runOnUiThread {
                busy = false
                if (activity.isDestroyed) return@runOnUiThread
                result.fold(onSuccess = { file ->
                    downloaded = file; offerOnResume = true
                    status("Update downloaded and verified.")
                    if (visible()) offerInstall()
                }, onFailure = { status(it.message ?: "Download failed. Try again.") })
            }
        }
    }
    private fun offerInstall() {
        if (!visible()) return
        offerOnResume = false
        dialog = AlertDialog.Builder(activity).setTitle("Update ready")
            .setMessage("Install the downloaded update? Your app-lock PIN and app list will be kept.")
            .setNegativeButton("Later", null).setPositiveButton("Install") { _, _ -> install() }.show()
    }
    private fun install() {
        val file = downloaded?.takeIf { it.exists() } ?: return
        if (!activity.packageManager.canRequestPackageInstalls()) {
            dialog = AlertDialog.Builder(activity).setTitle("Allow app updates")
                .setMessage("On the next Android screen, allow Simple App Lock to install updates. Then return here and unlock settings to continue.")
                .setNegativeButton("Cancel", null).setPositiveButton("Open settings") { _, _ ->
                    waitingForPermission = true
                    runCatching { activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))) }
                        .onFailure { waitingForPermission = false; status("Open App info → Install unknown apps to allow updates.") }
                }.show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.updates", file)
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            downloaded = null
            status("Android installer opened. Confirm the update there.")
        }.onFailure { status("Could not open Android's installer. Try again.") }
    }
    fun onAuthorizedResume() {
        if (waitingForPermission) {
            waitingForPermission = false
            if (activity.packageManager.canRequestPackageInstalls()) offerInstall()
            else status("Install permission was not allowed. Tap Check for updates to retry.")
        } else if (offerOnResume) offerInstall()
    }
    fun onStop() { dialog?.dismiss(); dialog = null }
    fun close() { dialog?.dismiss(); worker.shutdown() }
}
