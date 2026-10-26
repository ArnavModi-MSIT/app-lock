# Simple App Lock

Personal-use derivative of [OpenLock](https://github.com/MalicKAbdullah/openlock),
commit `14bf88a` (v1.7.0 source), copyright 2026 Abdullah Malik, MIT licensed.
The included LICENSE retains the original notice.

Reuses the design of OpenLock's native usage monitor, PIN verifier, lock screen,
and boot receiver. The Flutter application and shared-suite dependencies have
been replaced with a small native Kotlin setup/app-selection screen. Camera,
intruder logs, network updates, schedules, decoys, backups and device admin are
omitted. Original downloaded source is outside this directory and unchanged.

v1.1 adds an accessibility window-event detector and an opaque handoff cover to
reduce the delay reported with usage-access polling on a Samsung M33.

AndroidX dependencies have their own licenses, supplied by their publishers.
