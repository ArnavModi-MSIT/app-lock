package dev.abdullah.openlock

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.WindowManager
import android.widget.*
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executors

class MainActivity : FragmentActivity() {
    private lateinit var store: ConfigStore
    private val worker = Executors.newSingleThreadExecutor()
    private var authorized = false
    private var gatePending = false
    private var apps: List<AppRow> = emptyList()
    private var renderVersion = 0
    private lateinit var updater: UpdateController
    private var updateStatus: TextView? = null
    private data class AppRow(val pkg: String, val name: String, val icon: Drawable)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ConfigStore(this)
        updater = UpdateController(this, { authorized }) { updateStatus?.text = it }
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    override fun onResume() {
        super.onResume()
        if (!store.hasPin()) { showSetup(); return }
        if (!authorized) {
            setContentView(label("Unlock app lock settings", 24f))
            if (!gatePending) {
                gatePending = true
                @Suppress("DEPRECATION")
                startActivityForResult(Intent(this, LockActivity::class.java)
                    .putExtra(LockActivity.EXTRA_PACKAGE, packageName)
                    .putExtra(LockActivity.EXTRA_SETTINGS, true), AUTH)
            }
        } else { showApps(); updater.onAuthorizedResume() }
    }
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTH) {
            gatePending = false
            authorized = resultCode == Activity.RESULT_OK
            if (!authorized) finish()
        }
    }
    override fun onStop() {
        super.onStop()
        authorized = false
        updater.onStop()
        renderVersion++
        if (store.hasPin()) setContentView(label("Settings locked", 24f))
    }
    private fun pinField(hintText: String) = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        filters = arrayOf(android.text.InputFilter.LengthFilter(12))
    }
    private fun showSetup() {
        val root = column()
        root.addView(label("Simple App Lock", 28f))
        root.addView(label("Choose apps that should ask for your fingerprint or PIN when opened."))
        root.addView(label("Create an app-lock PIN (6–12 digits). This can be different from your phone PIN."))
        val first = pinField("New PIN"); val confirm = pinField("Confirm PIN")
        val error = label("")
        root.addView(first); root.addView(confirm); root.addView(error)
        val save = button("Save PIN") {}
        save.setOnClickListener {
            val pin = first.text.toString()
            when {
                !PinVerifier.valid(pin) -> error.text = "Use 6–12 digits."
                pin != confirm.text.toString() -> error.text = "PINs do not match."
                else -> {
                    save.isEnabled = false
                    worker.execute {
                        val result = runCatching { store.setPin(pin) }
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            if (result.isSuccess) {
                                authorized = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                                if (authorized) showApps()
                            }
                            else { error.text = "Could not save PIN. Try again."; save.isEnabled = true }
                        }
                    }
                }
            }
        }
        root.addView(save)
        root.addView(label("No account, ads or camera. Internet is used only when you check/download updates. If you forget this PIN, clear this app’s storage and set it up again."))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) }); fitInsets(root)
    }
    private fun showApps() {
        if (!authorized || isFinishing) return
        val version = ++renderVersion
        val root = column()
        root.addView(label("Simple App Lock", 28f))
        val ready = Access.ready(this)
        val fast = Access.fastEnabled(this)
        val status = when {
            !ready -> "Setup needed: enable Faster locking below."
            store.enabled && fast -> "App lock is on · faster window-event detection."
            store.enabled && OpenLockMonitorService.running -> "App lock is on · slower compatibility mode. Enable Faster locking to reduce the delay."
            store.enabled -> "App lock is enabled. Starting protection…"
            else -> "App lock is off. Choose apps, then turn it on."
        }
        val protectionStatus = label(status)
        root.addView(protectionStatus)
        root.addView(Switch(this).apply {
            text = "App lock on"; isChecked = store.enabled
            setOnCheckedChangeListener { _, checked ->
                if (checked && !Access.ready(this@MainActivity)) {
                    isChecked = false
                    toast("Enable Faster locking, or grant Usage access and Display over other apps.")
                } else {
                    store.enabled = checked
                    if (checked) startMonitor() else {
                        stopService(Intent(this@MainActivity, OpenLockMonitorService::class.java))
                        LockSession.relock()
                        FastLockService.clearCover()
                    }
                    protectionStatus.text = if (checked) {
                        if (Access.fastEnabled(this@MainActivity)) "App lock is on · faster window-event detection."
                        else "App lock is on · slower compatibility mode. Enable Faster locking to reduce the delay."
                    } else "App lock is off."
                }
            }
        })
        root.addView(Switch(this).apply {
            text = "Use fingerprint (PIN remains available)"; isChecked = store.biometric
            setOnCheckedChangeListener { _, checked -> store.biometric = checked }
        })
        root.addView(button(if (fast) "Faster locking · enabled" else "Enable Faster locking (recommended)") {
            openSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(label("In Accessibility → Installed apps, enable ‘Simple App Lock — faster locking’. It detects app window changes without reading messages or screen contents.", 14f))
        root.addView(label("If Android blocks it: App info → ⋮ → Allow restricted settings, then enable the service. Keep the compatibility permissions below only if needed.", 14f))
        root.addView(button(if (Access.usage(this)) "Compatibility: Usage access · allowed" else "Compatibility: allow Usage access") {
            openSettings(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })
        root.addView(button(if (Settings.canDrawOverlays(this)) "Compatibility: display over apps · allowed" else "Compatibility: display over other apps") {
            openSettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        root.addView(button("Battery settings") {
            openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })
        root.addView(label("On Samsung: App info → Battery → Unrestricted. Remove this app from Sleeping/Deep sleeping apps if necessary.", 14f))
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            root.addView(button("Allow protection notification") { requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101) })
        }
        root.addView(button("Change app-lock PIN") { changePin() })
        val installedVersion = packageManager.getPackageInfo(packageName, 0).versionName
        root.addView(button("Check for updates · v$installedVersion") { updater.check() })
        updateStatus = label(updater.status, 14f)
        root.addView(updateStatus)
        root.addView(label("Protected apps", 24f))
        root.addView(label("Apps relock after you switch away or turn the screen off. App detection can have a short delay.", 14f))
        val search = EditText(this).apply { hint = "Search apps"; inputType = InputType.TYPE_CLASS_TEXT }
        root.addView(search)
        val list = column().apply { setPadding(0, 0, 0, 0) }
        root.addView(list)
        fun drawList() {
            list.removeAllViews()
            val query = search.text.toString().trim()
            val selected = store.packages()
            apps.filter { it.name.contains(query, true) || it.pkg.contains(query, true) }
                .sortedWith(compareByDescending<AppRow> { it.pkg in selected }.thenBy { it.name.lowercase() })
                .forEach { app ->
                    val row = LinearLayout(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = dp(64) }
                    row.addView(ImageView(this).apply { setImageDrawable(app.icon); importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(dp(40), dp(40)))
                    row.addView(CheckBox(this).apply {
                        text = app.name; isChecked = app.pkg in selected
                        setPadding(dp(12), dp(8), 0, dp(8))
                        setOnCheckedChangeListener { _, checked -> store.setLocked(app.pkg, checked) }
                    }, LinearLayout.LayoutParams(0, -2, 1f))
                    list.addView(row)
                }
            if (apps.isEmpty()) list.addView(label("Loading app list…"))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { drawList() }
            override fun afterTextChanged(s: Editable?) {}
        })
        drawList()
        setContentView(ScrollView(this).apply { addView(root) }); fitInsets(root)
        if (store.enabled && ready) startMonitor()
        worker.execute {
            val launcher = packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            val blocked = setOf(packageName, launcher, "com.android.settings", "com.android.systemui")
            val loaded = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .filter { it.activityInfo.packageName !in blocked }
                .distinctBy { it.activityInfo.packageName }
                .map { AppRow(it.activityInfo.packageName, it.loadLabel(packageManager).toString(), it.loadIcon(packageManager)) }
            runOnUiThread {
                if (authorized && !isDestroyed && version == renderVersion) { apps = loaded; drawList() }
            }
        }
    }
    private fun startMonitor() {
        runCatching { startForegroundService(Intent(this, OpenLockMonitorService::class.java)) }
            .onFailure { toast("Could not start protection. Reopen the app and check permissions.") }
    }
    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure { toast("Open this permission from your phone’s Settings.") }
    }
    private fun changePin() {
        val root = column()
        val current = pinField("Current app-lock PIN")
        val next = pinField("New PIN (6–12 digits)")
        val confirm = pinField("Confirm new PIN")
        val error = label("")
        root.addView(current); root.addView(next); root.addView(confirm); root.addView(error)
        val dialog = AlertDialog.Builder(this).setTitle("Change PIN").setView(root)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val old = current.text.toString(); val new = next.text.toString()
                if (!PinVerifier.valid(new) || new != confirm.text.toString()) { error.text = "Enter matching 6–12 digit PINs."; return@setOnClickListener }
                if (store.cooldownRemaining() > 0) { error.text = "Wait 30 seconds before trying again."; return@setOnClickListener }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                worker.execute {
                    val valid = store.verify(old)
                    if (valid) { store.setPin(new); store.resetAttempts() } else store.failedAttempt()
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        if (valid) { dialog.dismiss(); toast("PIN changed") }
                        else { error.text = "Current PIN is incorrect."; dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true }
                    }
                }
            }
        }
        dialog.show()
    }
    private fun toast(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    override fun onDestroy() { updater.close(); worker.shutdown(); super.onDestroy() }
    companion object { private const val AUTH = 100 }
}
