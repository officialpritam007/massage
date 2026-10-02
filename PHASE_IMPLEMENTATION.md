# Direct messaging reliability and glass redesign

## Phase 1 — correctness

- FCM data messages enqueue bounded, account-scoped WorkManager work. The worker resolves authorized message, thread and sender records, respects hidden/deleted/expired/muted/blocked state, and decrypts locally before showing a private notification. A push sender is still required; this repository's backendless configuration does not send FCM messages.
- Snippet hydration checks the exact current message ID and never restores a hidden last-message preview.
- Typing uses a three-second monotonic deadline, refreshed only by changed server events. Initial cached history does not set presence online.
- Fifteen-second presence heartbeat, RTDB disconnect registration before advertising online, explicit immediate offline, privacy flags and Firestore fallback. Set the GitHub repository variable `FIREBASE_DATABASE_URL` to an existing RTDB instance and deploy `database.rules.json` before using RTDB. No instance is created by this change.
- New messages and summary times use Firebase server timestamps. Legacy number timestamps remain readable. Old documents without a server timestamp cannot recover their historical server send time.
- SingleTop conversation routes and media guards preserve the chat navigation stack. Initial decryption has a fixed 140 x 36 dp shimmer; typing morph text is bounded.
- One API-24-compatible message clock formatter, sender-ID based attribution, and cancellation/account guards around snapshot decoding.

Verification: Firestore and RTDB emulator suite: 13 passing assertions/tests. Android Kotlin compilation, deterministic timing tests and the full Robolectric suite pass.

Two-device acceptance remains required for real FCM delivery, RTDB disconnect timing, keyboard motion and frame-time measurements. No 120 FPS claim is made without device measurements. The existing cryptography is the app's signed EC/AES-GCM envelope, not the Signal double ratchet.
