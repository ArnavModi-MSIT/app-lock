package dev.abdullah.openlock

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LockSessionTest {
    @After fun reset() { LockSession.relock() }
    @Test fun unlockDoesNotUnlockOtherApps() {
        LockSession.markUnlocked("app.a")
        assertTrue(LockSession.isUnlocked("app.a"))
        assertFalse(LockSession.isUnlocked("app.b"))
    }
    @Test fun switchingAppsRelocksThePreviousApp() {
        LockSession.markUnlocked("app.a")
        LockSession.observeApp("app.b")
        LockSession.observeApp("app.a")
        assertFalse(LockSession.isUnlocked("app.a"))
    }
    @Test fun stayingInTheSameAppDoesNotPromptAgain() {
        LockSession.markUnlocked("app.a")
        LockSession.observeApp("app.a")
        assertTrue(LockSession.isUnlocked("app.a"))
    }
    @Test fun screenOffClearsTheSession() {
        LockSession.markUnlocked("app.a")
        LockSession.relock()
        assertFalse(LockSession.isUnlocked("app.a"))
    }
}
