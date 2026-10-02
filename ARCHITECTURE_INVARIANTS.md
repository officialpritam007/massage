# Liquid Chat runtime invariants

These rules are part of the implementation contract. Changes that violate them are regressions.

## Source of truth

- The Android app source lives only under `app/src/main/**`.
- Do not add duplicate Android source files at the repository root.
- `firestore.rules` must be exercised by the emulator tests in `tests/firestore` before an APK can build.

## Core messaging

- Firebase Auth is the account/session source of truth.
- Firestore is the realtime source of truth for users, directory, conversations, messages, typing and delivery/read state.
- Text chat must not depend on Appwrite availability.
- The recent-message realtime listener stays fixed; older history is loaded with one-shot pages and merged without restarting the listener.
- A failed outgoing message becomes `FAILED` with Retry. It must never remain `SENDING` indefinitely.
- Background delivery acknowledgements write directly to Firestore and must not depend on Appwrite.

## Error handling

- Background listener, presence, token and retry failures are nonblocking sync warnings.
- Only user-initiated destructive/privacy operations may block the UI with a modal.
- Optional Appwrite media quota errors must never cover or disable text chat.
- Permission failures retry the Firebase auth token before surfacing a nonblocking warning.

## E2EE

- New text messages store an empty plaintext `text` field plus the signed `e2ee` payload.
- Reply metadata and edited text remain inside the encrypted payload.
- Chat media is encrypted on-device before Appwrite storage; Firestore stores only wrapped media-key metadata.
- Appwrite notification calls receive identifiers only, never plaintext message text.
- E2EE private identity material never leaves the device and is protected by Android Keystore.
- A peer without a published E2EE key cannot receive a new message; the UI must explain that encryption setup is pending.

## Privacy lifecycle

- Android backup/device-transfer restore is disabled.
- Logout is destructive: remote purge must succeed before local logout finishes.
- Reinstall/clear-data creates a new installation identity. On the next sign-in, old Liquid Chat remote data is purged before normal sync begins.
- App update does not change the installation identity.
- Destructive privacy failures are blocking; normal sync failures are not.

## Backend boundaries

- Appwrite is used for encrypted media, reports, destructive purge, profile-photo storage and generic notification dispatch.
- Appwrite availability must not be required for Firebase Auth, Firestore text messaging, typing, read/delivery receipts or chat-list sync.
- Function deployment should be scoped to `appwrite-functions/liquid-api/**` so Android-only commits do not consume function-build quota.

## CI gate

A merge to `main` is acceptable only when the latest cumulative workflow passes:

1. Firestore security-rule integration tests.
2. Appwrite backend syntax/policy tests.
3. Android unit tests.
4. Debug APK assembly.
5. Artifact upload.
