package dev.abdullah.openlock

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import android.provider.Settings
import android.util.Log

/** Persistent notification and optional usage-access fallback for event-based locking. */
class OpenLockMonitorService : Service() {
    private lateinit var store: ConfigStore
    private lateinit var usage: UsageStatsManager
    private lateinit var power: PowerManager
    private val handler = Handler(Looper.getMainLooper())
    private var lastQuery = 0L
    private var foreground: String? = null
    private var lastLaunch = 0L
    private var lastTarget: String? = null
    private var keyboardPackage: String? = null
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LockSession.relock()
            foreground = null
            lastQuery = 0L
            lastTarget = null
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            try {
                if (!store.enabled) { stopSelf(); return }
                if (power.isInteractive && !getSystemService(KeyguardManager::class.java).isKeyguardLocked) tick()
            } catch (e: Exception) { Log.w("SimpleAppLock", "App detection unavailable", e) }
            handler.postDelayed(this, 150)
        }
    }
    override fun onCreate() {
        super.onCreate()
        store = ConfigStore(this)
        usage = getSystemService(UsageStatsManager::class.java)
        power = getSystemService(PowerManager::class.java)
        keyboardPackage = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.substringBefore('/')
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
        LockSession.relock()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "App lock", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_lock).setContentTitle("App lock is on")
            .setContentText("Tap to choose protected apps").setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, notification)
        if (!store.enabled || !store.hasPin() || !Access.ready(this)) { stopSelf(); return START_NOT_STICKY }
        running = true
        handler.removeCallbacks(poll)
        handler.post(poll)
        return START_STICKY
    }
    private fun tick() {
        if (!Access.ready(this)) { stopSelf(); return }
        // Never replay delayed UsageStats transitions over the accessibility event stream.
        if (FastLockService.connected) {
            foreground = null; lastQuery = 0L; lastTarget = null
            return
        }
        if (!Access.usage(this) || !Settings.canDrawOverlays(this)) return
        val now = System.currentTimeMillis()
        // Retain the last app between events; an app can stay open for hours.
        val events = usage.queryEvents(if (lastQuery == 0L) now - 60_000 else lastQuery - 1, now)
        lastQuery = now
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName ?: continue
                if (pkg == "com.android.systemui" || pkg == "android" || pkg == keyboardPackage) continue
                // Process every transition, including a quick Home -> same app.
                if (pkg == packageName) {
                    if (event.className != LockActivity::class.java.name) LockSession.relock()
                } else LockSession.observeApp(pkg)
                foreground = pkg
            }
        }
        val pkg = foreground ?: return
        if (pkg == packageName || pkg !in store.packages() || LockSession.isUnlocked(pkg)) return
        if (LockActivity.visibleTarget == pkg) return
        if (lastTarget == pkg && SystemClock.elapsedRealtime() - lastLaunch < 600) return
        lastTarget = pkg
        lastLaunch = SystemClock.elapsedRealtime()
        startActivity(Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(LockActivity.EXTRA_PACKAGE, pkg)
        })
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        unregisterReceiver(screenOff)
        running = false
        LockSession.relock()
        super.onDestroy()
    }
    companion object {
        private const val CHANNEL = "simple_app_lock"
        @Volatile var running = false
            private set
    }
}
