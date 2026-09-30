# Liquid Chat 4.0.0

A Kotlin/Jetpack Compose, Android-native **1-to-1** real-time messaging app with an original Liquid Glass-inspired UI.

**Developer:** Pritam Pal · **Support:** officialpritam07@gmail.com  
© 2026 Pritam Pal

## Product scope

- 1-to-1 private messaging only. Group chat, Communities and Updates are out of scope.
- Firebase Authentication + Firestore realtime snapshots + FCM push delivery.
- Authenticated Appwrite media storage with short-lived access tokens.
- Server-authoritative message mutations and permanent deletion tombstones.

## Included behaviour

- All / Favorites / Archived / Settings floating navigation.
- Balanced frosted surfaces, crystal wallpaper, light/dark themes and expressive spring motion.
- Remote-only typing bubble in the message area; it expands into the received message bubble.
- Online / last-seen presence with privacy controls and direct offline write when the app stops.
- Swipe-to-reply and voice recording with lock, cancel, pause/resume and preview-before-send.
- Durable local send queue, optimistic messages, idempotent server writes and explicit retry states.
- Sending / Sent / Delivered / Read / Failed message delivery states.
- Authenticated Appwrite uploads, private photo/video/voice retrieval and cache-aware playback.
- Reply, edit, delete-for-me, delete-for-everyone, reactions, pin, star and forward actions.
- Permanent Delete Chat for the current account using server-side `deletedFor` / `deletedBefore` state.
- Search, profile, block/report, privacy and notification settings.
- Developer diagnostics for Firebase Auth, Firestore, FCM, backend and Appwrite configuration.
- Calls, video calls, groups, Updates and Communities are not part of the current product scope.

## Permanent deletion model

Deletion is **not** based on a local `hidden:*` preference as the source of truth.

- **Delete for me:** server records a per-user hidden tombstone and updates `hiddenFor` when the message still exists.
- **Delete for everyone:** server records a global tombstone, deletes the message document, revokes media access and invalidates remote notifications.
- **Delete chat:** server records the account in `deletedFor` and a `deletedBefore` cutoff. Old listeners, pagination and cache rebuilds are filtered by that server state.
- Legacy device-local hidden flags are treated only as migration input and are converted to server-backed deletion.

## Build and configure

Read [APPWRITE_SETUP.md](APPWRITE_SETUP.md) before building. GitHub Actions verifies unit tests and builds a debug APK on `main`, hardening branches and pull requests. New uploads use Appwrite; Firebase remains responsible for identity, message snapshots and push delivery.

Read [GITHUB_UPDATE_BN.md](GITHUB_UPDATE_BN.md) before replacing an existing repository. Read [VERIFICATION.md](VERIFICATION.md) for verification status and remaining deployment gates.

## Important behaviour

- Send queues resume while the app process is active or when the app is reopened. Force-stopping Android prevents background execution; reopening resumes pending sends.
- Search covers loaded conversations/messages; use **Load earlier messages** for older history.
- Favorites are account-synced. Message stars, drafts and wallpaper are currently device-local.
- This app does not claim end-to-end encryption, payments, linked devices or cloud backup.
- Appwrite media links are short-lived and authorization is re-checked by the backend before a new token is issued.
- Account deletion requires recent authentication and performs server-side cleanup.
- The debug signing key is not a private production release key. Configure protected release signing before public distribution.

## Backend and security

`appwrite-functions/liquid-api/src/main.js` is the authenticated HTTP API. Firebase Admin credentials and the Appwrite API key stay server-side. Android receives only Firebase ID tokens, short-lived Appwrite JWTs and short-lived media access URLs.

`firestore.rules` limits Android to its own private settings, public directory presence, participant-only conversation reads and participant typing state. Conversation/message mutations, tombstones, media metadata, rate limits, sessions, usernames and reports remain server-authoritative.

`storage.rules` deliberately blocks legacy Firebase Storage paths because current chat media is Appwrite-authoritative; this prevents old client paths from bypassing participant validation and deletion revocation.

`appwrite-functions/liquid-api/src/scheduled.js` is the private scheduled cleanup/retry entry point. `src/migrate.js` handles legacy profile migration without deleting chat history.
