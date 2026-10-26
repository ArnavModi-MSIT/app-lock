package dev.abdullah.openlock

/** Only the current app stays unlocked. Transient system windows are filtered by the monitor. */
object LockSession {
    private var unlocked: String? = null
    @Synchronized fun markUnlocked(pkg: String) { unlocked = pkg }
    @Synchronized fun isUnlocked(pkg: String): Boolean = unlocked == pkg
    @Synchronized fun observeApp(pkg: String) { if (unlocked != pkg) unlocked = null }
    @Synchronized fun relock() { unlocked = null }
}
