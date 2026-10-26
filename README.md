# Simple App Lock

## Download APK

**[Download Simple App Lock v1.2 for Android](https://github.com/ArnavModi-MSIT/app-lock/releases/download/v1.2.0/Simple-App-Lock-v1.2.apk)**

[Release details](https://github.com/ArnavModi-MSIT/app-lock/releases/tag/v1.2.0)
· Android 9 or newer · Free · App locking works offline

Personal test build. v1.0 was reported working on a Samsung M33, but Telegram
could remain visible for 1–2 seconds before locking. v1.1 adds event-based
locking and a privacy cover; its timing needs to be checked on the phone.
Install this APK over v1.0/v1.1 to keep your PIN and selected apps.

## In-app updates

Install v1.2 manually once. After that, open Simple App Lock and tap **Check for
updates**. The app checks this repository's releases only when pressed, offers
the newer version, downloads it, then opens Android's installer. The first time,
Android asks you to allow Simple App Lock to install apps. Every installation
still requires your confirmation. There is no background update polling.

Downloads are checked against GitHub's SHA-256 digest, the package ID, a higher
versionCode and the installed app's signing certificate. Test/prerelease builds
from this personal repository are included. No GitHub login is needed.

A small offline Android app adapted from OpenLock for casual privacy. Select
apps and they ask for a fingerprint or a separate 6–12 digit app-lock PIN.
The current app stays unlocked until you switch away or turn the screen off.

## Setup on the phone

1. Install the APK and create/confirm your app-lock PIN.
2. Tap **Enable Faster locking (recommended)**. Under Accessibility → Installed
   apps, enable **Simple App Lock — faster locking**. If Android shows a
   restricted-settings block, follow [Google's instructions](https://support.google.com/android/answer/12623953)
   to allow restricted settings from this app's App info menu, then return.
3. Enrol a fingerprint in the phone's Settings if needed. The fingerprint
   toggle is on by default; the app-lock PIN is always available.
4. Choose your protected apps and turn **App lock on** on.
5. Under App info → Battery choose Unrestricted, if your Samsung offers it.
   Keep Simple App Lock out of Sleeping/Deep sleeping apps. Allow notifications
   so Android can show that protection is running.

Settings ask for authentication when reopened. System Settings and the default
home launcher are excluded from selection to avoid locking you out of recovery.
If you forget the app-lock PIN, clear Simple App Lock's storage or uninstall it,
then set up again. This removes the selected-app configuration.

The app requests internet access only for checking/downloading updates and
install-package permission to open Android's update confirmation. No camera or
device-admin permission. The faster
mode uses an accessibility service subscribed only to window-state events with
zero notification batching. Screen-content retrieval is disabled: it uses the
package/class metadata, not message text, view trees, screenshots or gestures.
An opaque accessibility overlay covers the app while the unlock screen loads,
and is removed after that screen's first rendered frame. The cover includes
retry and Go home buttons if activity startup is delayed.
It stores a salted PBKDF2 PIN verifier in private preferences, never the PIN.
Any strong biometric accepted by Android can satisfy the system prompt; on the
intended Samsung M33 the expected method is the fingerprint sensor. Fingerprint
templates are handled by Android, not this app.

## Limits

Faster mode reacts to accessibility window-change events without UsageStats
polling. If faster mode is not enabled, the original Usage access + Display over
other apps permissions still provide a slower compatibility mode (150 ms polling,
with potentially larger Android reporting delays). Delayed usage events are not
replayed while the accessibility service is connected.

Even faster mode reacts after Android reports a window change, so a zero-frame
content leak cannot be guaranteed. Notifications and protected apps'
own recent-app thumbnails are not hidden. Force-stop, permission removal,
uninstall and safe mode bypass protection. Split screen/PiP are not guaranteed.
This app does not encrypt other apps or replace Secure Folder.

## Build

On this computer the portable tools have been downloaded to `../work/tools`.
Run `./build.ps1` to use those tools without changing the system installation.

Requires JDK 17, Android SDK platform 36 and Build Tools 35.0.0.
Set `sdk.dir` in local.properties or ANDROID_HOME, then:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

APK: app/build/outputs/apk/debug/app-debug.apk. The debug build is suitable for
local testing. Keep its signing key if you want later builds to update in place.

## GitHub Actions

The Android workflow checks main-branch pushes and pull requests. Version tags
(`v1.2.0`) or a manual run with **publish** enabled build an APK using the existing
signing identity and publish it to Releases after checks pass. Increase both
versionName and versionCode before publishing another update. Already-published
versions are not overwritten.

The signing keystore is supplied through the encrypted repository secret
`APPLOCK_KEYSTORE_BASE64`. It must match the key used by the installed app.
Never commit it or replace it with a newly generated key for an update. Pull
request checks do not receive the signing key. Release jobs verify the expected
certificate fingerprint before uploading.

## Phone acceptance checks (must be performed on the actual phone)

- Open a selected app; expect fingerprint/PIN. Cancel or press Back: go home.
- With Faster locking enabled, check Telegram from its launcher icon, Recents,
  and a notification. Check both a cold start and an already-running app.
- The privacy cover should hand off to fingerprint/PIN without briefly
  revealing Telegram or getting stuck over the PIN keypad.
- Correct fingerprint/PIN reveals the app. Wrong PIN leaves it locked.
- Stay in the same app, including opening its keyboard: no repeat prompt.
- Switch to Home/another app and return: expect authentication again.
- Turn the screen off, unlock the phone and return: expect authentication.
- Open two different protected apps in succession: each asks for authentication.
- Reopen Simple App Lock: settings require authentication again.
- Restart the phone, unlock it and check protection resumes; reopen Simple App
  Lock if Samsung prevented automatic startup.
- Leave it idle for several minutes, then check a selected app again.
- Turn app lock off: selected apps open normally.
- Tap Check for updates while offline: a failure message should appear without
  affecting app locking. When a newer version is published, download it, allow
  installation if asked, and confirm Android's installer. Verify PIN, app list
  and accessibility status after the update.

Unit tests cover PIN verification and session transitions. A successful build
does not establish Samsung/Android 16 runtime compatibility.
