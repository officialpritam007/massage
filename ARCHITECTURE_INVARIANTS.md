# Liquid Chat runtime invariants

These rules are part of the implementation contract. Changes that violate them are regressions.

## Source of truth

- Android application source lives under `app/src/main/**`.
- Firebase Authentication is the account/session source of truth.
- Cloud Firestore is the realtime source of truth for users, directory, conversations, messages, typing and delivery/read state.
- Firebase Cloud Messaging handles push delivery.
- Cloudinary stores profile images and encrypted chat-media payloads.
- Server-authoritative actions live only in `firebase-functions/liquid-api/**`.

## Messaging reliability

- The recent-message realtime listener remains fixed; older history is loaded in pages and merged without restarting it.
- A failed outgoing message becomes `FAILED` with Retry; it must not remain `SENDING` indefinitely.
- Background delivery/read acknowledgements write through the approved Firebase path and must not depend on media availability.
- Media failures must never block text chat.

## Media and E2EE

- New text-message plaintext fields remain empty when the signed E2EE payload is present.
- Reply metadata and edited text remain inside the encrypted payload.
- Chat media is encrypted on-device before Cloudinary upload.
- Firestore stores media metadata and wrapped media-key metadata, not the plaintext attachment.
- Cloudinary API-secret operations are server-side only.
- The Android client limits attachments to 9 MB so encrypted `raw` assets remain below the connected Cloudinary Free-plan 10 MB raw-file ceiling.
- Private media is returned through short-lived signed Cloudinary delivery URLs after Firebase authorization checks.

## Privacy lifecycle

- Android backup/device-transfer restore is disabled.
- Logout/account deletion uses the authenticated backend for remote cleanup before local state is cleared.
- Reinstall/clear-data creates a new installation identity; the next sign-in follows the app's remote cleanup policy.
- Destructive privacy failures may block the UI; background sync failures should not.

## Deployment boundary

- `firebase.json` points Functions to `firebase-functions/liquid-api`.
- `.github/workflows/deploy-firebase-function.yml` deploys `liquidApi` from `main`.
- `.github/workflows/deploy-firebase-rules.yml` publishes Firestore rules.
- Cloudinary secrets are Firebase Functions secrets and must never be placed in the APK or committed to GitHub.
- Legacy Appwrite and old root `functions/` sources are not part of the runtime.

## CI gate

A change is release-candidate quality only when:

1. Firestore security-rule integration tests pass.
2. Firebase + Cloudinary backend JavaScript syntax validation passes.
3. Android unit tests pass.
4. Debug APK assembly passes.
5. The APK artifact is uploaded.
6. Two-device live tests pass for messaging, delivery/read state, notifications, profile photos and media upload/download/delete.
