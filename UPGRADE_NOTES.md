# Liquid Chat 2.6.0 Upgrade

This build keeps the existing Firebase/source architecture and upgrades the app without Android Studio.

## Included
- Premium lighter Liquid Glass surfaces and reduced heavy shadows
- 6.7-inch friendly edge-to-edge Compose layout foundation
- Card navigation removed; Settings is now the fifth bottom-navigation destination
- Settings profile photo picker -> Firebase Storage -> Firestore profile URL
- Functional privacy/read-receipts control with Firestore persistence
- Functional notification/data-storage information panels
- Online presence updates on app foreground/background
- Firestore-backed 24-hour status records
- Gallery photo status upload
- Gallery video status upload limited to 30 seconds
- Video status playback in the status viewer
- App version bumped to 2.6.0 / versionCode 260

## GitHub-only build
1. Create/open your GitHub repository.
2. Upload the contents of this project to the repository root.
3. Commit to `main`.
4. Open GitHub -> Actions -> `Build Android APK`.
5. Run the workflow if it does not start automatically.
6. Download the `liquid-chat-debug-apk` artifact.

No Android Studio is required for the included debug build workflow.

## Important next phase
For production-level parity with a modern messaging app, the next implementation phase should add real WebRTC media transport for voice/video calls, media compression/trimming, richer privacy controls, persistent appearance settings loading, and end-to-end encryption architecture.


## v2.7.0 settings persistence upgrade
- Privacy controls now expose Last Seen, Online Status, Profile Photo, Status Updates, and Group Invites choices.
- Privacy and Appearance values are loaded from the signed-in Firestore user document when the real-time profile listener starts.
- Existing GitHub Actions build flow remains unchanged; no Android Studio is required.


## v2.9.0
- Real gallery photo/video chat uploads via Firebase Storage.
- Chat media is stored under `chats/{conversationId}/{fileId}`.
- Image and video messages use the uploaded Firebase download URL.
- Storage path is aligned with the existing participant-based Storage rules.

## v3.0.0 — delivery/read + push notifications
- Conversation unread counts are persisted per recipient in `unreadCounts`.
- Incoming messages transition from SENT to DELIVERED when the recipient's listener receives them.
- Opening a conversation clears its unread count and marks incoming messages READ.
- FCM device tokens are registered in `users/{uid}.fcmToken`.
- Firebase Functions includes `notifyNewMessage` to send push notifications to recipients.
