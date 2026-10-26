package dev.abdullah.openlock

import org.junit.Assert.*
import org.junit.Test

class WindowPolicyTest {
    private val own = "local.personal.simpleopenlock"
    @Test fun protectedAppWindowsAreRoutedWithoutTheirContents() {
        assertEquals(WindowPolicy.Kind.APP, WindowPolicy.classify("org.telegram.messenger", "org.telegram.ui.LaunchActivity", own, "keyboard"))
    }
    @Test fun systemPromptAndKeyboardDoNotInvalidateAnUnlock() {
        listOf("android", "com.android.systemui", "keyboard").forEach {
            assertEquals(WindowPolicy.Kind.IGNORE, WindowPolicy.classify(it, null, own, "keyboard"))
        }
    }
    @Test fun ownLockWindowDoesNotRetriggerAuthentication() {
        assertEquals(WindowPolicy.Kind.IGNORE, WindowPolicy.classify(own, "dev.abdullah.openlock.LockActivity", own, null))
    }
    @Test fun settingsReentryInvalidatesTheLastUnlock() {
        assertEquals(WindowPolicy.Kind.SETTINGS, WindowPolicy.classify(own, "dev.abdullah.openlock.MainActivity", own, null))
    }
    @Test fun launcherCountsAsLeavingAProtectedApp() {
        assertEquals(WindowPolicy.Kind.APP, WindowPolicy.classify("com.sec.android.app.launcher", "Launcher", own, null))
    }
}
