# Liquid Chat runtime invariants — free direct mode

## Source of truth

- Firebase Auth owns identity/session state.
- Cloud Firestore owns profiles, directory, conversations, messages, typing, presence and delivery/read state.
- Cloudinary owns media bytes.
- Firestore stores Cloudinary `secure_url` values; no binary payload is stored in Firestore.
- No Firebase Functions, Appwrite or Firebase Storage runtime dependency is allowed.

## Security

- Cloudinary API secrets must never ship in Android or be committed to GitHub.
- Direct uploads use an unsigned preset with narrow format/folder restrictions.
- Firestore rules restrict media URLs to the configured Cloudinary environment.
- Text messages retain the app's E2EE payload.
- Media is not E2EE in free direct-upload mode.
- Sender-only message deletion is enforced by Firestore rules.

## Reliability

- Optimistic outgoing messages remain queued locally until Firestore accepts them.
- Firestore realtime snapshots are message truth.
- Delivery/read receipts are participant-constrained Firestore updates.
- Media failures must not block text chat.

## Cost

- The architecture does not require a billing-enabled Firebase Functions deployment.
- Zero monetary cost still depends on remaining inside provider free quotas.

## CI gate

A release candidate requires Firestore rule tests, Android unit tests, debug APK assembly, artifact upload and two-device live acceptance testing.
