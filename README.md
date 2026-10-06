# Liquid Chat 4.4.0

A Kotlin/Jetpack Compose **1-to-1 realtime messaging app** using a zero-paid-backend architecture.

The 4.4 redesign uses ocean-toned Liquid Glass controls, readable message surfaces,
floating navigation, categorized search and a live appearance preview. It also fixes
attachment retry ownership, reply/history scrolling, password-reset feedback,
minimum-Android refresh-rate compatibility and notification sound/vibration separation.
See [REDESIGN_4_4_0.md](REDESIGN_4_4_0.md) for verification and release gates.

**Developer:** Pritam Pal · **Support:** officialpritam07@gmail.com  
© 2026 Pritam Pal

## Architecture

- **Firebase Authentication** — account/session identity.
- **Cloud Firestore** — private profiles, privacy-filtered directory data, 1-to-1 conversations, messages, typing, per-conversation presence, settings and delivery/read state.
- **Cloudinary Free** — direct unsigned media uploads. Firestore stores the returned `secure_url`.
- **Firebase App Check** — Play Integrity is initialized by default (`APP_CHECK_PROVIDER=auto`); enforcement is enabled separately in Firebase Console after registration and metrics validation.
- **No Firebase Functions, Firebase Storage or paid custom backend.**

## Messaging

- Optimistic send queue with retry.
- Realtime Firestore receive.
- Sending → Sent → Delivered → Read state.
- Reply, edit, pin, star, reactions, delete-for-me and sender-only delete-for-everyone.
- Favorites, archive, mute, disappearing timer and chat wallpaper.
- Text messages are stored as plaintext Firestore fields and protected by Firebase Authentication plus participant-only Firestore rules.
- Chat-list previews and notifications use the same plaintext message fields; FCM carries IDs and the app re-checks authenticated server state before displaying notifications.
- Room provides a persistent local message cache for instant chat reopen/offline viewing. Cloudinary returns HTTPS delivery URLs for media.

## Cloudinary

Android uploads directly to `https://api.cloudinary.com/v1_1/<cloud-name>/auto/upload`
with an **unsigned upload preset**. The APK contains only the public cloud name and preset name,
never a Cloudinary API secret.

Current defaults:

- Cloud name: `mthzgqhv`
- Upload preset: `liquid_chat_owned_v1`
- Client attachment cap: 9 MB

See [CLOUDINARY_SETUP.md](CLOUDINARY_SETUP.md).

## Free-mode limitations

- Reliable push delivery while the Android process is fully killed requires a trusted sender/backend. FCM HTTP v1 credentials are never embedded in the APK; this backendless build does not claim killed-app FCM push.
- Delete-for-everyone removes the Firestore message and local cache. Without a trusted Cloudinary Admin backend, the uploaded Cloudinary object cannot be guaranteed to be remotely destroyed.
- Removing/replacing a profile photo removes its Firestore reference but may leave the old Cloudinary object orphaned.
- Anyone who obtains a direct Cloudinary media URL can fetch that asset.
- Free operation depends on remaining inside Firebase and Cloudinary free quotas.

## CI

GitHub Actions validates free-tier configuration, runs Firestore security-rule emulator tests, Android unit tests and trusted cleanup-worker tests, builds the debug APK and uploads the artifact.

Firestore rules deploy independently; Firebase Functions are not part of this architecture.

## Session logout / account deletion

Log out ends only the current device session: it removes local message cache, pending work, notifications and the device FCM registration while preserving the cloud account and conversations for a later sign-in. Delete account remains the irreversible path that erases the account, one-to-one conversation trees and owned uploads after trusted cleanup. Permanent deletion requires recent authentication and never reports success before cloud verification.

Final verification includes a **70-minute wait after Auth deletion**, plus worker scheduling, so tokens on other devices expire before the UID gate is removed. A random status proof without a UID allows the app to resume without the deleted Auth account. Keep the app installed until the local wipe is confirmed.

A trusted GitHub Actions worker checks deletion requests every 15 minutes. The owner-authorized one-time inventory reset disables the old unsigned preset, erases existing app data/uploads and Auth users, and creates a server-only inventory certificate. New accounts use the owned upload preset and automatically qualify for verified deletion. Scoped orphan uploads are discovered even if a receipt was not saved. No administrator credentials are shipped in Android. See [PERMANENT_DELETION_SETUP.md](PERMANENT_DELETION_SETUP.md).

Android does not run the removed application on uninstall. Uninstall clears its local sandbox, but cannot by itself trigger remote deletion. Complete the in-app permanent deletion before uninstalling. OS backup/transfer is disabled for this application.

FCM data-only reception and private token registration are implemented. Fully killed-app push still needs a trusted sender; no push server has been deployed. See the [4.2.3 change report](PRIVACY_AND_NOTIFICATION_FIXES.md) for activation and acceptance checks.

## Privacy hardening in 4.3.1

- Profile-photo visibility is enforced at the Firestore boundary: switching to **Nobody** atomically removes the public directory photo while retaining the owner's private profile reference.
- A modified client cannot republish a directory photo while the private profile says the photo is hidden.
- App Check `auto` initializes Play Integrity for installed APKs. Register the signing SHA-256 and configure App Check for your distribution channel before enabling enforcement.
- See `APP_CHECK_SETUP.md` and `TRUSTED_PUSH_SETUP.md`.
