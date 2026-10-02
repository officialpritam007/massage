# Liquid Chat 4.2.0

Android-native 1-to-1 realtime messaging built with Kotlin, Jetpack Compose, Firebase and Cloudinary.

**Developer:** Pritam Pal · **Support:** officialpritam07@gmail.com  
© 2026 Pritam Pal

## Stack

- Firebase Authentication — account identity and sessions.
- Cloud Firestore — realtime users, conversations, messages, presence and typing.
- Firebase Cloud Messaging — push delivery.
- Firebase Functions `liquidApi` — authenticated server-authoritative mutations and media authorization.
- Cloudinary — authenticated profile media and encrypted chat attachments.
- Android Keystore + app crypto — device-held identity keys and encrypted message/media payloads.

## Product scope

Liquid Chat focuses on private 1-to-1 messaging. Group chat, Communities, Updates and payment features are intentionally outside the current scope.

## Current experience

- Modern light/dark Liquid Glass system with balanced blur, translucent surfaces and spring motion.
- Performance-aware chat list and message bubbles that avoid per-row live blur.
- Optimistic realtime messaging with durable local outbox and explicit retry states.
- Sending → Sent → Delivered → Read status flow.
- Remote typing bubble in the message area with message-morph animation.
- Reply, edit, reactions, pin, star, forward, delete-for-me and delete-for-everyone.
- Voice recording with hold, cancel, lock, pause/resume, preview and retry.
- Photo, video and document attachments with client-side media encryption.
- Cloudinary authenticated storage with server-authorized, expiring download URLs.
- Online/last-seen privacy, read-receipt controls, blocking, reports and notifications.
- Favorites, archive, search, profile editing and appearance presets.
- Developer diagnostics for Firebase Auth, Firestore, FCM and backend configuration.

## Media limits

The connected Cloudinary Free plan allows up to 10 MB for raw assets. Encrypted chat media is uploaded as raw encrypted bytes, so Liquid Chat applies a **9 MB** attachment limit to leave encryption overhead headroom. Images are optimized before encryption where possible.

## Security model

New protected text/media operations use peer key metadata and device-side encryption. Firebase ID tokens authorize server requests; Cloudinary API secrets never ship inside the APK. Media access is re-authorized by `liquidApi`, which returns an expiring signed Cloudinary download URL.

This implementation has not been independently cryptographically audited, so do not describe it as a formally audited secure messenger.

## Deletion model

- **Delete for me:** records account-specific hidden state so deleted history does not reappear after restart or sync.
- **Delete for everyone:** validates sender authority, removes the message, revokes/removes media when no authorized conversation still references it and sends deletion notification metadata.
- **Delete chat:** records a per-account cutoff so older history remains hidden after resync.
- **Logout / reinstall privacy reset:** destructive server cleanup must succeed before the local account state is cleared.

## Build

GitHub Actions runs:

1. Firestore security-rule emulator tests.
2. Firebase + Cloudinary backend syntax validation.
3. Android unit tests.
4. Debug APK assembly.
5. APK artifact upload.

See [CLOUDINARY_SETUP.md](CLOUDINARY_SETUP.md) for backend deployment and required secrets. See [VERIFICATION.md](VERIFICATION.md) for the current verification status.

## Deployment note

Firebase Cloud Functions deployment requires the Firebase project to use the Blaze plan. The app can stay within no-cost usage quotas, but a billing account is required by Firebase to deploy Functions. The workflow `.github/workflows/deploy-firebase-api.yml` keeps Cloudinary credentials out of source control and skips deployment cleanly until its required GitHub Secrets are configured.

