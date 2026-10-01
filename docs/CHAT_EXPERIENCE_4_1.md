# Liquid Chat 4.1.0

This change addresses the two recorded sessions: repeated image loading, oversized chat bubbles, unstable unread markers, and voice preview/send errors. It also adds the approved recording gestures, typing-to-message transition and app-wide deletion effect.

## Included

- Account-scoped, stable media cache files and Coil keys; concurrent downloads share a lock. Local recording URIs bypass HTTP resolution. Cached chat state survives listener switches; scroll and unread anchors are saved per conversation.
- Full-message delivery acknowledgement from persistent, network-constrained WorkManager jobs after FCM reception, independent of online presence and notification preferences. The server validates membership, applies monotonic receipts and counts newly seen messages transactionally. Only visible messages in the resumed conversation are marked read; read-receipt privacy is preserved.
- FCM token cleanup, transient push retry jobs, recipient scoping and duplicate notification suppression. Muted chats still receive silent data pushes.
- Hold the microphone to record; slide left to discard with dust, slide up to lock, release an unlocked recording to send. Locked recording supports pause/resume and stop-to-preview. Drafts survive leaving the screen and process restart; failed uploads remain retryable. Playback does not start automatically when speed is initialized, pauses off-screen, and coordinates a single active recording.
- AAC/M4A voice uploads are validated by their actual MP4 audio tracks, not an unreliable storage MIME label. Video tracks and malformed containers are rejected. Existing compatible voice attachments are normalized when sent.
- Content-sized bubbles, six-line Read more/Read less, stable typing-bubble identity when the message arrives, dark-theme text contrast, and reduced-motion alternatives.
- Shared dust/collapse/retry component for message deletion, conversation deletion, voice-draft/recording discard, upload cancellation, camera discard/retake, profile-photo removal, recent-search clearing, temporary-cache clearing and account deletion. Destructive server actions run after the animation; failures retain the item and expose Retry.
- Backend edit transaction reads before writes. Existing authentication, participant and media access controls remain enforced. No paid service or billing-tier change is introduced.

## Validation

Automated checks: backend syntax and authorization/media unit tests; a real generated AAC/M4A container check; Android compilation and unit/Robolectric tests, including receipt status ordering, typing-row continuity, deletion failure/retry timing and Read more UI; debug APK assembly in GitHub Actions.

A real two-device acceptance pass is still required. In particular, exercise:

1. Recipient foreground/background, screen locked, Doze, muted chat, notification permission denied, offline/reconnect, and token rotation. Single tick means server acceptance; double tick means a device fetched the message. Android force-stop and vendor battery restrictions can prevent background work until the app can run again; internet connectivity alone cannot guarantee instant acknowledgement.
2. Short and long recordings, denied microphone permission, left-cancel/up-lock gestures, pause/resume, preview speed controls, upload failure/retry and returning to an unsent draft after restart.
3. Long/short text, typing handoff, unread anchor while new messages arrive, reading older history, keyboard opening, dark/light mode and reduced motion.
4. Each deletion surface, repeated taps, offline failure and Retry. Test dust frame rate on a lower-end device; automated compilation does not establish visual smoothness.

The APK and backend change must be used together. Appwrite's existing `firebase-appwrite-bridge` function is the backend; the legacy Firebase Functions directory is not deployed by this change.
