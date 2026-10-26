package dev.abdullah.openlock

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Event-based detection: does not request or retrieve an accessibility node tree. */
class FastLockService : AccessibilityService() {
    private lateinit var store: ConfigStore
    private lateinit var windows: WindowManager
    private var cover: View? = null
    private var coveredTarget: String? = null
    private var keyboard: String? = null
    private var lastApp: String? = null
    private var lastLaunch = 0L
    private var pendingTarget: String? = null
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_USER_PRESENT) {
                // A still-open app may not emit a fresh window event after phone unlock.
                if (store.enabled && store.hasPin()) lastApp?.let { protect(it) }
            } else { LockSession.relock(); hideCover(); pendingTarget = null }
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        store = ConfigStore(this)
        windows = getSystemService(WindowManager::class.java)
        keyboard = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        instance = this
        LockSession.relock()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF).apply { addAction(Intent.ACTION_USER_PRESENT) }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
        if (store.enabled && store.hasPin()) runCatching {
            startForegroundService(Intent(this, OpenLockMonitorService::class.java))
        }
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::store.isInitialized || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!store.enabled || !store.hasPin()) { hideCover(); return }
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) { hideCover(); return }
        val pkg = event.packageName?.toString() ?: return
        when (WindowPolicy.classify(pkg, event.className?.toString(), packageName, keyboard)) {
            WindowPolicy.Kind.IGNORE -> return
            WindowPolicy.Kind.SETTINGS -> { LockSession.relock(); hideCover(); pendingTarget = null; lastApp = null; return }
            WindowPolicy.Kind.APP -> Unit
        }
        lastApp = pkg
        protect(pkg)
    }
    private fun protect(pkg: String) {
        // Launcher/other-app transitions immediately invalidate the last unlock.
        LockSession.observeApp(pkg)
        if (pkg !in store.packages() || LockSession.isUnlocked(pkg)) {
            hideCover(); pendingTarget = null; return
        }
        if (LockActivity.visibleTarget == pkg) return
        if (pendingTarget == pkg && SystemClock.elapsedRealtime() - lastLaunch < 700) return
        pendingTarget = pkg
        showCover(pkg)
        launchPrompt(pkg)
    }
    private fun launchPrompt(pkg: String) {
        lastLaunch = SystemClock.elapsedRealtime()
        runCatching {
            startActivity(Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(LockActivity.EXTRA_PACKAGE, pkg)
            })
        }.onFailure { Log.w("SimpleAppLock", "Tap cover to open verification", it) }
    }
    private fun showCover(pkg: String) {
        if (coveredTarget == pkg && cover != null) return
        hideCover()
        coveredTarget = pkg
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.rgb(10, 12, 16))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(TextView(context).apply {
                text = "App locked"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            })
            addView(Button(context).apply {
                text = "Open fingerprint / PIN"; isAllCaps = false
                setOnClickListener { coveredTarget?.let { launchPrompt(it) } }
            })
            addView(Button(context).apply {
                text = "Go home"; isAllCaps = false
                setOnClickListener { performGlobalAction(GLOBAL_ACTION_HOME); hideCover(); pendingTarget = null }
            })
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        runCatching { windows.addView(root, params); cover = root }
            .onFailure { coveredTarget = null; Log.w("SimpleAppLock", "Could not show privacy cover", it) }
    }
    private fun hideCover() {
        cover?.let { runCatching { windows.removeViewImmediate(it) } }
        cover = null; coveredTarget = null
    }
    override fun onInterrupt() { hideCover() }
    override fun onDestroy() {
        hideCover()
        if (instance === this) instance = null
        if (::store.isInitialized) runCatching { unregisterReceiver(screenOff) }
        super.onDestroy()
    }
    companion object {
        private var instance: FastLockService? = null
        val connected: Boolean get() = instance != null
        fun dismissCoverFor(pkg: String) {
            instance?.let { if (it.coveredTarget == pkg) it.hideCover() }
        }
        fun clearCover() { instance?.hideCover() }
    }
}
