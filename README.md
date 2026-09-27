# Liquid Chat 2.5.0

A Kotlin + Jetpack Compose real-time messaging app with a white-first Liquid Glass UI, Firebase Auth/Firestore/Storage foundations, groups, status, media/camera flows, call UI, FCM notification plumbing and device biometric support.

## Included
- White Liquid Glass design system across chat/settings/calls.
- Firebase Auth + Firestore real-time sync foundation.
- 1-to-1 messages, delivery/read state, reply/edit/delete/reactions/pin.
- Groups and group messaging foundation.
- Status/story model with `expiresAt` and scheduled cleanup function.
- FCM token registration + notification service.
- Firestore call signaling transport and Liquid Glass active-call UI.
- Android biometric gate helper.
- Storage and Firestore security rules.
- GitHub Actions debug APK build.
- Standard `debug.keystore` for local debug builds.

## Important: external services still need your project credentials
The ZIP is source-complete, but no app can legitimately contain your Firebase project secrets or TURN credentials. Before production use:
1. Replace `app/google-services.json` with the Firebase config for your own Android app/package.
2. Enable Authentication, Firestore, Storage and Cloud Messaging.
3. Deploy rules/indexes/functions with the Firebase CLI.
4. For real audio/video media, connect a WebRTC media engine to `FirestoreCallSignaling` and configure STUN/TURN servers. Firestore is signaling only; it must never transport audio/video.
5. For Play Store release, create your own release/upload keystore and configure GitHub Actions secrets.
6. Request Android 13+ notification/media permissions at runtime where needed.

## Build
```bash
gradle :app:assembleDebug
```

GitHub Actions can also build the debug APK from `.github/workflows/build-apk.yml`.
