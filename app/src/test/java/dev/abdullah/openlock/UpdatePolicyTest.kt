package dev.abdullah.openlock

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun comparesNumericallyRatherThanAlphabetically() { assertTrue(UpdatePolicy.isNewer("v1.10.0", "1.9")) }
    @Test fun equalVersionsAreNotUpdates() { assertFalse(UpdatePolicy.isNewer("1.2.0", "1.2")) }
    @Test fun neverOffersADowngrade() { assertFalse(UpdatePolicy.isNewer("1.1", "1.2")) }
    @Test fun malformedVersionsAreIgnored() {
        assertFalse(UpdatePolicy.isNewer("latest", "1.2")); assertFalse(UpdatePolicy.isNewer("999999999999999999999999.0", "1.2"))
    }
    @Test fun permitsOnlyThisRepositorysApkAssets() {
        assertTrue(UpdatePolicy.trustedAsset("https://github.com/ArnavModi-MSIT/app-lock/releases/download/v1.3.0/Simple-App-Lock-v1.3.apk"))
        listOf("https://github.com/other/repo/releases/download/v1/app.apk", "http://github.com/ArnavModi-MSIT/app-lock/releases/download/v1/app.apk", "https://github.com.evil.test/ArnavModi-MSIT/app-lock/releases/download/v1/app.apk").forEach { assertFalse(UpdatePolicy.trustedAsset(it)) }
    }
    @Test fun rejectsRedirectsToUntrustedHosts() {
        assertTrue(UpdatePolicy.trustedDownloadHost("https://release-assets.githubusercontent.com/asset"))
        assertFalse(UpdatePolicy.trustedDownloadHost("https://example.com/update.apk"))
        assertFalse(UpdatePolicy.trustedDownloadHost("https://github.com@evil.test/update.apk"))
    }
}
