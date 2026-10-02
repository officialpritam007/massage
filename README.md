# Liquid Chat 4.2.0

A Kotlin/Jetpack Compose **1-to-1 realtime messaging app** using a zero-paid-backend architecture.

**Developer:** Pritam Pal · **Support:** officialpritam07@gmail.com  
© 2026 Pritam Pal

## Architecture

- **Firebase Authentication** — account/session identity.
- **Cloud Firestore** — profiles, directory, 1-to-1 conversations, messages, typing, presence, settings and delivery/read state.
- **Cloudinary Free** — direct unsigned media uploads. Firestore stores the returned `secure_url`.
- **No Firebase Functions, Firebase Storage or paid custom backend.**

## Messaging

- Optimistic send queue with retry.
- Realtime Firestore receive.
- Sending → Sent → Delivered → Read state.
- Reply, edit, pin, star, reactions, delete-for-me and sender-only delete-for-everyone.
- Favorites, archive, mute, disappearing timer and chat wallpaper.
- Text payloads retain the app's device-bound E2EE layer.
- Media uploads are **not E2EE** in direct unsigned mode; Cloudinary returns public delivery URLs.

## Cloudinary

Android uploads directly to `https://api.cloudinary.com/v1_1/<cloud-name>/auto/upload`
with an **unsigned upload preset**. The APK contains only the public cloud name and preset name,
never a Cloudinary API secret.

Current defaults:

- Cloud name: `mthzgqhv`
- Upload preset: `liquid_chat_unsigned`
- Client attachment cap: 9 MB

See [CLOUDINARY_SETUP.md](CLOUDINARY_SETUP.md).

## Free-mode limitations

- Reliable push delivery while the Android process is fully killed requires a trusted sender/backend. This backendless build does not claim killed-app FCM push.
- Delete-for-everyone removes the Firestore message and local cache. Without a trusted Cloudinary Admin backend, the uploaded Cloudinary object cannot be guaranteed to be remotely destroyed.
- Removing/replacing a profile photo removes its Firestore reference but may leave the old Cloudinary object orphaned.
- Anyone who obtains a direct Cloudinary media URL can fetch that asset.
- Free operation depends on remaining inside Firebase and Cloudinary free quotas.

## CI

GitHub Actions validates free-tier configuration, runs Firestore security-rule emulator tests, runs Android unit tests, builds the debug APK and uploads the artifact.

Firestore rules deploy independently; Firebase Functions are not part of this architecture.
