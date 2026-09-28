# Liquid Chat Final Fix Pass

This source pass implements the confirmed messaging/UI fixes on top of v4.0.5.

- Real voice-message recording using Android MediaRecorder, Firebase Storage upload, Firestore message metadata, and real playback with MediaPlayer.
- Group Leave waits for Firebase success before removing the group locally and reports success/failure.
- Offline chat headers show formatted Last seen time instead of only Offline.
- Keyboard handling uses adjustResize + Compose IME padding so the composer follows the keyboard without moving the top bar.
- Home floating new-chat button removed; Home filters are All / Unread / Favorites / Archived.
- Home/Groups/Settings/Conversation top bars use status-bar-safe spacing.
- Bottom navigation is icon-only and safe-area aware.
- Calls, Status/Updates, and Business user-facing routes/screens remain removed.
- Light-mode glass surfaces are more opaque/readable.
- Glass appearance controls include refraction, frosted transparency, boxy-to-rounded corner radius, border strength, live preview, and reset-to-default.
- Settings privacy wording no longer claims end-to-end encryption.
- Settings includes developer/contact footer.

Build note: this environment does not contain the Android SDK/Gradle distribution, so an Android APK build was not locally verified. Use the included GitHub Actions workflow for the authoritative build result.
