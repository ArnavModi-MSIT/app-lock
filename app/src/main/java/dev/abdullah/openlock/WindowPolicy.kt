package dev.abdullah.openlock

/** Filters transient system/keyboard/lock windows without inspecting their contents. */
object WindowPolicy {
    enum class Kind { IGNORE, SETTINGS, APP }
    fun classify(pkg: String, className: String?, ownPackage: String, keyboard: String?): Kind = when {
        pkg.isBlank() || pkg == "android" || pkg == "com.android.systemui" || pkg == keyboard -> Kind.IGNORE
        pkg == ownPackage -> if (className == "dev.abdullah.openlock.MainActivity") Kind.SETTINGS else Kind.IGNORE
        else -> Kind.APP
    }
}
