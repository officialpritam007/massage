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

## v4.0.0 UI cleanup / navigation
- Light Liquid Glass background changed to a soft ice-blue tone.
- Home screen redesigned toward the supplied reference: Chats header, more/settings action, camera, new-chat button, glass search field, floating glass navigation.
- Bottom navigation is now Chats | Groups | Settings.
- Removed Updates/Status navigation and UI.
- Removed Audio Call, Video Call, Calls screen, Active Call screen, and call buttons from conversation/contact profile UI.
- Removed Archive UI and archived conversation state from the active chat model/repository flow.
- Chat deletion remains available from the chat overflow menu.
- Media sharing remains available; chat media continues to use Firebase Storage architecture.
- The call/status backend source and legacy Firestore structures are retained for this iteration but are no longer reachable from the app UI.
