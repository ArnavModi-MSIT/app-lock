package dev.abdullah.openlock

import android.content.Context

/** Only a salted PIN verifier, selected packages and two switches; never the PIN itself. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("simple_openlock", Context.MODE_PRIVATE)
    fun hasPin(): Boolean = !prefs.getString("hash", null).isNullOrEmpty() && !prefs.getString("salt", null).isNullOrEmpty()
    fun setPin(pin: String) {
        require(PinVerifier.valid(pin))
        val salt = PinVerifier.newSalt()
        check(prefs.edit().putString("salt", salt).putString("hash", PinVerifier.hash(pin, salt)).commit())
    }
    fun verify(pin: String): Boolean {
        val salt = prefs.getString("salt", null) ?: return false
        val hash = prefs.getString("hash", null) ?: return false
        return PinVerifier.verify(pin, salt, hash)
    }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) { prefs.edit().putBoolean("enabled", value).apply() }
    var biometric: Boolean
        get() = prefs.getBoolean("biometric", true)
        set(value) { prefs.edit().putBoolean("biometric", value).apply() }
    fun packages(): Set<String> = prefs.getStringSet("packages", emptySet())!!.toSet()
    fun setLocked(pkg: String, locked: Boolean) {
        val next = packages().toMutableSet()
        if (locked) next.add(pkg) else next.remove(pkg)
        prefs.edit().putStringSet("packages", next).apply()
        LockSession.relock()
    }
    fun cooldownRemaining(): Long = (prefs.getLong("retryAfter", 0) - System.currentTimeMillis()).coerceAtLeast(0)
    fun failedAttempt() {
        val attempts = prefs.getInt("attempts", 0) + 1
        val edit = prefs.edit().putInt("attempts", attempts)
        if (attempts >= 5) edit.putLong("retryAfter", System.currentTimeMillis() + 30_000)
        edit.apply()
    }
    fun resetAttempts() { prefs.edit().remove("attempts").remove("retryAfter").apply() }
}
