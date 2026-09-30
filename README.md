# Liquid Chat 4.0.0

A Kotlin/Jetpack Compose personal messaging app with a white Liquid Glass-inspired UI.

**Developer:** Pritam Pal · **Support:** officialpritam@gmail.com  
© 2026 Pritam Pal

## This update

- All / Favorites / Archived / Settings floating navigation.
- Balanced frosted surfaces, crystal wallpaper, light/dark themes and expressive spring motion.
- Remote-only typing bubble that expands into a received text bubble; reduced-motion option.
- Online/last-seen with expiry and privacy controls.
- Swipe-to-reply and voice recording with lock/cancel gestures.
- Durable local send queue, idempotent server writes, explicit failure/retry states.
- Authenticated Appwrite uploads, private media retrieval, video/voice playback.
- Favorites/archive/mute, reply/edit/delete/reactions/pin/star/forward, search and profile actions.
- Calls, video calls, groups, Updates and Communities removed.

## Build and configure

Read [APPWRITE_SETUP.md](APPWRITE_SETUP.md) before building. GitHub Actions builds an APK
when all four repository variables are configured. New uploads use Appwrite; Firebase
remains responsible for identity, message snapshots and push delivery.

Read [GITHUB_UPDATE_BN.md](GITHUB_UPDATE_BN.md) before replacing an existing repository.
Read [VERIFICATION.md](VERIFICATION.md) for what was actually verified and remaining gates.

## Important behaviour

- Send queues resume while the app process is active or when reopened. Force-stopping
  Android prevents background execution; reopening resumes pending sends.
- Search covers loaded conversations/messages; use Load earlier messages for older history.
- Favorites are account-synced; message stars, hidden-for-me flags, drafts and wallpaper are
  currently device-local. These are cleared at logout.
- This app does not provide end-to-end encryption, payments, linked devices or cloud backup.
- Five-minute media access links can remain valid until their expiry after a privacy change.
- Account deletion asks for recent sign-in and deletes shared conversations for both parties.
- The original debug signing key is retained for update compatibility. It is not a private
  production release key. Set up your own protected release signing before public distribution.

## Backend

`appwrite-functions/liquid-api/src/main.js` is the authenticated HTTP API.
`src/scheduled.js` is the private scheduled cleanup/retry entry point.
`src/migrate.js` migrates existing public profiles without deleting chats.
