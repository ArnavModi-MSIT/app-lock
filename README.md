# Simple App Lock

## Download APK

**[Download Simple App Lock for Android](https://github.com/ArnavModi-MSIT/app-lock/releases/download/v1.0.0/Simple-App-Lock-v1.0.apk)**

[Release details](https://github.com/ArnavModi-MSIT/app-lock/releases/tag/v1.0.0)
· Android 9 or newer · Free and offline

Personal test build. Build checks and eight unit tests pass; testing on the
Samsung M33 is still pending. Download the APK on your phone and open it to install.

A small offline Android app adapted from OpenLock for casual privacy. Select
apps and they ask for a fingerprint or a separate 6–12 digit app-lock PIN.
The current app stays unlocked until you switch away or turn the screen off.

## Setup on the phone

1. Install the APK and create/confirm your app-lock PIN.
2. Grant Usage access and Display over other apps using the buttons.
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

The app requests no internet, camera, accessibility or device-admin permission.
It stores a salted PBKDF2 PIN verifier in private preferences, never the PIN.
Any strong biometric accepted by Android can satisfy the system prompt; on the
intended Samsung M33 the expected method is the fingerprint sensor. Fingerprint
templates are handled by Android, not this app.

## Limits

App detection uses UsageStats, polling at 150 ms while the display is awake and
unlocked. That is a polling interval, not a guaranteed response time: a selected
app may briefly appear before the lock screen. Notifications and protected apps'
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

## Phone acceptance checks (must be performed on the actual phone)

- Open a selected app; expect fingerprint/PIN. Cancel or press Back: go home.
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

Unit tests cover PIN verification and session transitions. A successful build
does not establish Samsung/Android 16 runtime compatibility.
