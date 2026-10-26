package dev.abdullah.openlock

import android.app.Activity
import android.content.*
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executors

/** Adapted from OpenLock's native PIN/fingerprint screen, without decoys or camera. */
class LockActivity : FragmentActivity() {
    private lateinit var store: ConfigStore
    private var target = ""
    private var settingsGate = false
    private var authenticated = false
    private var pin = ""
    private var busy = false
    private var biometricInFlight = false
    private var autoPrompted = false
    private var prompt: BiometricPrompt? = null
    private lateinit var dots: TextView
    private lateinit var message: TextView
    private val worker = Executors.newSingleThreadExecutor()
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { finish() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        store = ConfigStore(this)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goHome() }
        })
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenOff, filter, RECEIVER_NOT_EXPORTED)
        else registerReceiver(screenOff, filter)
        showTarget(intent)
    }
    private fun showTarget(request: Intent) {
        target = request.getStringExtra(EXTRA_PACKAGE).orEmpty()
        settingsGate = request.getBooleanExtra(EXTRA_SETTINGS, false)
        if (target.isBlank()) { finish(); return }
        pin = ""; busy = false; authenticated = false; autoPrompted = false
        visibleTarget = target
        val root = column().apply { gravity = Gravity.CENTER }
        val name = if (settingsGate) "App lock settings" else runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(target, 0)).toString()
        }.getOrDefault("Protected app")
        root.addView(label(name, 26f).apply { gravity = Gravity.CENTER })
        root.addView(label("Unlock with fingerprint or your app-lock PIN").apply { gravity = Gravity.CENTER })
        dots = label("", 26f).apply { gravity = Gravity.CENTER }
        message = label("").apply { gravity = Gravity.CENTER; minHeight = dp(48) }
        root.addView(dots); root.addView(message)
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("Delete", "0", "Unlock")).forEach { digits ->
            root.addView(LinearLayout(this).apply {
                gravity = Gravity.CENTER
                digits.forEach { digit ->
                    addView(button(digit) {
                        if (!busy) when (digit) {
                            "Delete" -> { pin = pin.dropLast(1); updateDots() }
                            "Unlock" -> verifyPin()
                            else -> if (pin.length < 12) { pin += digit; updateDots() }
                        }
                    }, LinearLayout.LayoutParams(0, dp(64), 1f))
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        if (store.biometric) root.addView(button("Use fingerprint") { triggerBiometric() })
        root.addView(button("Go home") { goHome() })
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(root) }
        setContentView(scroll); fitInsets(root); updateDots()
        // Keep the opaque accessibility cover until our first frame is actually drawn.
        val drawingTarget = target
        if (Build.VERSION.SDK_INT >= 29) {
            root.viewTreeObserver.registerFrameCommitCallback {
                runOnUiThread { FastLockService.dismissCoverFor(drawingTarget) }
            }
        } else {
            root.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    root.viewTreeObserver.removeOnPreDrawListener(this)
                    root.postOnAnimation { FastLockService.dismissCoverFor(drawingTarget) }
                    return true
                }
            })
        }
        if (!store.hasPin()) message.text = "PIN setup is missing. Open Simple App Lock to set it up."
    }
    override fun onPostResume() {
        super.onPostResume()
        if (!autoPrompted) { autoPrompted = true; triggerBiometric() }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        prompt?.cancelAuthentication(); biometricInFlight = false
        setIntent(intent); showTarget(intent)
        window.decorView.post { if (!isFinishing) { autoPrompted = true; triggerBiometric() } }
    }
    private fun updateDots() { dots.text = "●".repeat(pin.length) + "○".repeat((6 - pin.length).coerceAtLeast(0)) }
    private fun verifyPin() {
        if (!store.hasPin()) { message.text = "Set up your PIN in Simple App Lock first."; return }
        val remaining = store.cooldownRemaining()
        if (remaining > 0) { message.text = "Try again in ${(remaining + 999) / 1000} seconds, or use fingerprint."; return }
        if (!PinVerifier.valid(pin)) { message.text = "Enter your 6–12 digit app-lock PIN."; return }
        busy = true
        val entered = pin; val verifyingTarget = target
        worker.execute {
            val valid = store.verify(entered)
            runOnUiThread {
                if (isFinishing || isDestroyed || target != verifyingTarget) return@runOnUiThread
                busy = false; pin = ""; updateDots()
                if (valid) unlock() else { store.failedAttempt(); message.text = "Wrong PIN. Try again." }
            }
        }
    }
    private fun triggerBiometric() {
        if (biometricInFlight || authenticated || !store.biometric || !store.hasPin() || isFinishing) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            message.text = "Fingerprint unavailable. Use your app-lock PIN."; return
        }
        biometricInFlight = true
        val authenticatingTarget = target
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                biometricInFlight = false
                if (!isFinishing && target == authenticatingTarget) unlock()
            }
            override fun onAuthenticationError(code: Int, error: CharSequence) {
                biometricInFlight = false
                if (!isFinishing) message.text = "Use your app-lock PIN or tap Use fingerprint."
            }
        })
        prompt!!.authenticate(BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock app").setSubtitle("Verify your fingerprint")
            .setAllowedAuthenticators(authenticators).setNegativeButtonText("Use PIN").build())
    }
    private fun unlock() {
        if (authenticated) return
        authenticated = true
        FastLockService.dismissCoverFor(target)
        store.resetAttempts()
        if (settingsGate) setResult(Activity.RESULT_OK) else LockSession.markUnlocked(target)
        finish()
    }
    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        FastLockService.dismissCoverFor(target)
        finish()
    }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !authenticated) { prompt?.cancelAuthentication(); finish() }
    }
    override fun onDestroy() {
        if (visibleTarget == target) visibleTarget = null
        prompt?.cancelAuthentication()
        unregisterReceiver(screenOff)
        worker.shutdown()
        super.onDestroy()
    }
    companion object {
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_SETTINGS = "settings"
        @Volatile var visibleTarget: String? = null
            private set
    }
}
