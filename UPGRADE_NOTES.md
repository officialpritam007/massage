# Liquid Chat v3.5.0 — Phase 7

- Persistent blocked-user list in Firestore
- Blocked Users management UI
- Persistent notification preferences
- Account deletion flow (Firestore profile + profile image + Firebase Auth)
- Existing WebRTC, media, typing, presence, settings and Liquid Glass features retained

Build from GitHub Actions; Android Studio is not required.


## Phase 9 / v3.9.0
- Conversation archive/unarchive is persisted per user in Firestore via `archivedFor`.
- Home chat list hides archived chats from All/Unread/Favorites views.
- Added an Archived filter for quick access.
- Search, media upload, typing, presence, delivery/read state and calling foundations are retained.
- GitHub Actions remains the recommended build path; Android Studio is not required.


## Phase 9 / v3.9.0
- Per-chat mute state persisted in Firestore.
- Disappearing messages: Off, 24 hours, 7 days, or 30 days.
- Message expiration timestamps are filtered from realtime chat lists.
- Per-chat wallpaper preference persisted for future visual themes.
- Chat settings are accessible from the conversation header.
