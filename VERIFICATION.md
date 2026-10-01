# Verification report — 4.0.0 (iOS 26 Liquid Glass release)

## Verified in CI

GitHub Actions workflow `Build APK` on branch `chatgpt/ios26-liquid-glass`:

- Run `36821131815` (commit `4a5a70f`) — **success**, all steps green:
  - `Run unit tests` (`gradle testDebugUnitTest`) ✅
  - `Build Debug APK` (`gradle assembleDebug`) ✅
  - `Upload APK` ✅ — artifact `liquid-chat-debug-apk` (~27.1 MB, not expired)
- Earlier runs `36817107062` (commit `5fd7d99`) and `36815085820` (commit `c29c481`) verified the same three steps for the config-driven chrome pass and the initial compile fix.

The `4a5a70f` run covers the final review pass: per-group bubble spacing, search hit jump/highlight, privacy-gated media loading, cached video frames, grabber swipe-dismiss on sheets, sheet-based edit/forward/voice-preview/delete-chat surfaces, persisted auto-save ids, the `syncIssue` listener diagnostic, the directory identity fallback helper, and grouped read-only list rows (`frost = false`).

Static checks run locally before each push (no Android SDK in this workspace, so Gradle is CI-only):

- Brace/paren balance check on every edited Kotlin file (string/comment aware).
- Icon-import integrity scan across `app/src/main/java`.
- Unused-import scan on the touched files (clean).
- Region scans confirming the chat header, composer, bottom navigation and
  attachment sheet contain no hardcoded colour/alpha values for background, border or shadow.

## Backend policy checks (unchanged from the earlier pass)

- Backend JavaScript syntax check (`node --check`).
- Four authenticated API policy tests: pair authorization, deterministic Appwrite IDs, message validation and presence freshness.
- Firestore emulator rules: four tests covering private data, participant messages, typing ownership/expiry and protected server fields.

## Not yet verified (device gate)

The debug APK builds and unit tests pass, but nothing here replaces a two-device acceptance run.
Two accounts on two Android devices (ideally one Android 16 device for blur performance):

1. Liquid Glass: Appearance slider (intensity, blur, border/depth, accent) visibly changes header, composer, bottom navigation and sheets; Clear and Regular presets both readable in light and dark.
2. Chat header: unified back/identity/menu bar; compact transition on scroll; online / last-seen subtitle correct; typing never appears in the header.
3. Bubbles: content-fit width, grouping of consecutive messages, sent/received tint split, timestamp + animated delivery ticks, failed message retry pill.
4. Media: no black letterboxing, thumbnail follows the real aspect ratio, shimmer while downloading, upload spinner while sending, optional caption (no generic "Photo" text anywhere).
5. Save to gallery: viewer button, message long-press action, and Settings → Privacy → Auto-save received media; confirm no duplicate gallery entries across app restarts.
6. Reply, voice note (cancel/lock/pause/preview/speed/scrub), attachment sheet, search highlight, archive/favorites, notification tap after restart, privacy toggles.
7. Regressions: optimistic send, ordering, offline outbox, reconnect, delivered/read receipts, delete for me / for everyone / delete chat with media revoke and no resurrection after restart or re-login.
8. Performance: scrolling a long chat list and a 500+ message conversation stays smooth with glass enabled.

Production release still requires Firebase/Appwrite configuration, a private release key and the two-device acceptance test. This report does not claim zero errors, production signing, live backend deployment, or exact iOS rendering.
