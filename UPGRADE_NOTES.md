# Liquid Chat v3.5.0 — Phase 7

- Persistent blocked-user list in Firestore
- Blocked Users management UI
- Persistent notification preferences
- Account deletion flow (Firestore profile + profile image + Firebase Auth)
- Existing WebRTC, media, typing, presence, settings and Liquid Glass features retained

Build from GitHub Actions; Android Studio is not required.


## Phase 8 / v3.8.0
- Conversation archive/unarchive is persisted per user in Firestore via `archivedFor`.
- Home chat list hides archived chats from All/Unread/Favorites views.
- Added an Archived filter for quick access.
- Search, media upload, typing, presence, delivery/read state and calling foundations are retained.
- GitHub Actions remains the recommended build path; Android Studio is not required.
