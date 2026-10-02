# Liquid Chat runtime invariants

These are implementation contracts. A change that violates them is a regression.

## Source of truth

- Android source lives under `app/src/main/**`.
- Firebase Auth is the identity/session source of truth.
- Firestore is the realtime source of truth for users, directory, conversations, messages, typing and delivery/read state.
- Firestore rules must pass emulator tests before the APK build step.

## Messaging reliability

- Optimistic messages must transition out of `SENDING`; failures become `FAILED` with Retry.
- The recent-message realtime listener stays stable while older pages are merged without restarting it.
- Text chat does not depend on Cloudinary availability.
- Background delivery/read acknowledgements use Firestore and FCM paths, not media storage.
- Listener failures are isolated and surfaced as nonblocking sync warnings.

## Encryption and media

- New protected text messages store ciphertext/key metadata rather than plaintext message content.
- Chat attachment bytes are encrypted on-device before Cloudinary upload.
- E2EE private identity material stays device-side and Android Keystore-backed.
- A peer without a published encryption key cannot receive new protected content.
- Cloudinary API credentials stay server-side.
- Media download URLs are generated only after participant/profile authorization and expire.
- Encrypted chat attachments must stay within the 9 MB client limit.

## Privacy lifecycle

- Android backup/device-transfer restore remains disabled.
- Logout and reinstall privacy reset are destructive server operations; failures must not silently pretend cleanup succeeded.
- Delete-for-me, delete-for-everyone and delete-chat state must survive restart/resync.

## UI performance

- Repeated LazyColumn chat rows and message bubbles must not each run a live backdrop blur.
- Expensive glass blur is reserved for high-value chrome such as headers, composer, dialogs and navigation.
- Reduced-motion mode must avoid spring-heavy/infinite visual motion where practical.

## Backend boundary

- `firebase-functions/liquid-api` is the active authenticated server backend.
- Cloudinary is the active media store.
- Appwrite runtime code is obsolete and must not be reintroduced.
- The API verifies Firebase identity and server-side authorization before destructive/media actions.

## CI gate

A release candidate requires:

1. Firestore rules integration tests.
2. Backend JavaScript syntax validation.
3. Android unit tests.
4. Debug APK assembly.
5. Artifact upload.
6. Two-device acceptance testing for login, realtime chat, receipts, media, voice, notifications and deletion.
