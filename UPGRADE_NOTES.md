# Liquid Chat - Call/Status UI Removal

## Changes
- Removed Calls, Video Calls, incoming-call UI and call-history navigation from the app UI.
- Removed Status/Updates screen, status story row and status bottom-navigation item.
- Removed audio/video call buttons from conversation and contact profile screens.
- Bottom navigation is now: Chats, Groups, Settings.
- Removed microphone permission from the Android manifest; camera/media permissions remain for messaging attachments.
- Removed call-specific FCM intent data and changed the notification channel description to message notifications.
- Removed stale `CallsScreen.kt`, `UpdatesScreen.kt`, and `ActiveCallScreen.kt` source files.

## Important
The existing repository still contains legacy Firebase call/status model/repository code for compatibility with existing data. Those features are no longer reachable from the user-facing app UI.

## Build
This source was checked for stale references to the removed navigation/screens. A GitHub Actions build should be run to verify the full Android toolchain compile; no local Android SDK build was claimed here.
