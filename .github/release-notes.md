Download the APK below and install over the existing app to preserve your PIN and selected apps.

## v1.3: cover rendering before authentication startup

- Prepare the privacy-cover widgets when the accessibility service connects.
- Submit the cover's first frame before launching the fingerprint/PIN activity, avoiding a synchronous activity-start call ahead of the cover's draw.
- Enable hardware acceleration for the cover and ignore stale handoffs after leaving an app.
- This targets our rendering path; it does not guarantee faster Android event delivery or zero-frame exposure. Phone timing is unmeasured.

## In-app updates (included since v1.2)

- **Check for updates** in Simple App Lock contacts this repository's releases only when pressed.
- Download progress appears inside the app. The APK hash, app ID, version code and signing certificate are checked before opening Android's installer.
- Android asks you to allow app installations from Simple App Lock the first time, and asks you to confirm every update.
- Internet access is used for checking/downloading updates only. No account or background update polling.
- Includes releases marked as personal test builds. Check the version offered before installing.

If you have v1.2, use Check for updates. From v1.0/v1.1, install this APK manually once; future versions can use the update button.

Faster locking still requires enabling **Simple App Lock — faster locking** under Accessibility → Installed apps. The Telegram locking delay and cover handoff still require phone testing; zero-frame exposure is not guaranteed.
