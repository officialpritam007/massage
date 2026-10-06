# Liquid Chat 4.4.0 — redesign and release verification

## User experience

- Ocean/navy light and dark palettes, tinted glass navigation and composer controls.
- Flat chat/contact/search content rows, floating pill navigation, clearer empty states.
- Search filters for people, chats and cached messages, with working result navigation.
- Polished sign-in/registration form, keyboard navigation and local password-reset results.
- Responsive message bubbles, accessible reply gestures/actions and themed action sheets.
- Appearance live preview, selectable accents, reduced motion and opaque fallback controls.
- Settings counts reflect actual loaded chats/favorites/blocks rather than invented lifetime metrics.
- Profiles resolve cached contacts correctly and never show arbitrary chat media in your own profile.
- Offline/server warnings occupy layout space instead of covering navigation actions.

## Runtime and backend reliability

- Message jumps and history anchors resolve by stable ID, with the optional loader counted correctly.
- Attachment previews survive failure, retry and cancellation. Voice/media sends preserve replies.
- Retry ownership includes account, conversation, URI and media type; changing account clears retries.
- New attempts replace stale failed callbacks and route disposal suppresses stale retry prompts.
- Disabling vibration retains notification sound and respects Android notification-channel settings.
- Server diagnostics use bounded server reads, distinguish configuration from live verification,
  and report push/media privacy limitations explicitly.
- No administrator credential is added to Android, and no paid service is activated.

## High refresh rate

The app requests the highest supported refresh rate at the current resolution with
an Android 7 compatible display lookup. Android 14+ uses a refresh preference rather
than pinning a mode. Power, thermal and system policy still decide the active rate.

The background mesh is static and cached, eliminating three ambient infinite
animations and whole-screen shader redraws. Backdrop blur belongs to controls;
message/list rows avoid it. Debug `LiquidPerf` logs use rendered Window FrameMetrics
and distinguish actual deadlines from estimated budgets and dropped metric reports.

Sustained 120fps is **unverified**. Profile the optimized build on a physical 120Hz
phone with long-history scrolling, incoming messages, attachment/voice playback,
keyboard transitions, navigation, large text and reduced effects. Compare p95/p99
frame timings against the active display budget (8.33ms at 120Hz), including thermal
and battery-saving conditions. An emulator/debug build does not certify 120fps.

## Reproducible checks

The checked-in Gradle 9.3.1 wrapper verifies the distribution SHA-256. CI runs:

1. Firestore emulator security-rule integration tests.
2. Trusted cloud-cleanup worker tests.
3. Android unit/Compose interaction tests, including retry/session isolation,
   history/reply anchors, server timeouts, notification channels and API24 startup.
4. Debug APK assembly and artifact upload.
5. R8/resource-shrunk release assembly and unsigned artifact upload.

Release builds remain unsigned unless `KEYSTORE_PATH`, `STORE_PASSWORD` and
`KEY_PASSWORD` are supplied securely. The release alias remains `upload`; keep the
existing signing identity for updates. Never commit signing material or credentials.

## Production gates still requiring live evidence

- Two real authenticated devices must pass send/receive, offline retry, media/voice,
  reply/edit/reactions, read receipts, block, logout and deletion acceptance tests.
- No always-on trusted FCM sender is deployed. Closed-process realtime push is not
  provided by this free direct architecture; force-stopped apps are additionally
  subject to Android restrictions. GitHub maintenance jobs are not instant senders.
- Media delivery URLs are public to anyone who receives the URL. Messages are
  participant-restricted plaintext in Firestore, without end-to-end encryption.
- Verify live Firestore rule/index deployment, cleanup readiness and the Cloudinary
  owned preset; register the release SHA-256/App Check provider before enforcement.
- Play-ready signing, distribution, privacy disclosures and physical 120Hz profiling
  remain release gates. Do not label this source as a fully deployed production service.

## Design/performance references

- [Apple Liquid Glass principles](https://developer.apple.com/videos/play/wwdc2025/219/)
- [Android adaptive refresh rate](https://developer.android.com/develop/ui/views/animations/adaptive-refresh-rate)
- [Compose performance](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Window FrameMetrics](https://developer.android.com/reference/android/view/FrameMetrics)
