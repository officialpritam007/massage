# Video review and fixes — Liquid Chat 4.2.1

Reviewed the recording from 00:00 to its end at 02:57.61, including every half-second frame and all 96 detected scene transitions. The recording shows version 4.2.0 (420). These changes use the current `main` Firebase Auth / Firestore / direct Cloudinary architecture.

| Video evidence | Correction |
| --- | --- |
| 00:00 onward: unavailable encrypted text and generic encrypted chat previews | Preserve the account identity on ordinary logout; refuse to silently replace incomplete or unreadable saved keys; publish both identity documents atomically; cache successfully decrypted payloads and previews with an Android Keystore key; retain previously observed peer fingerprints when a contact rotates keys. Unknown keys still require validation. |
| 00:02 onward: blank oversized photo tiles; 00:18: unavailable legacy photo; 00:35: error text inside small avatars | Serialize downloads for each URL, use unique temporary files and SHA-256 cache names, preserve the prior complete cache during refresh, shorten download timeouts, show compact loading/error tiles, and use initials inside failed avatars. Unsupported legacy URLs display an explicit unavailable state. |
| 00:10 and 01:44–01:57: invisible Share/Block buttons; 01:34: unreadable archive row in light mode | Preserve secondary button translucency, set explicit foreground contrast, and use theme-aware archive colors and a visible active archive label. |
| 00:37 and 01:32: “14 Messages sent” despite more lifetime messages | Label the statistic “Loaded sent messages”; the app only counts messages currently loaded on this phone. This statistic is unrelated to Firestore quota usage. |
| 01:08–01:18: appearance rolls back; blue accent forces dark mode; controls remain green; light status bar loses contrast | Persist local appearance, debounce writes, reject stale or uncommitted echoes while a newer choice is pending, retry unsaved preferences, honor dark mode independently of the selected accent, apply the accent to Material controls, and set system-bar icon contrast. |
| Repeated navigation throughout: previous page text visible through the next page | Remove crossfades between translucent glass pages; use single-top navigation for repeated taps. |
| 02:02–02:14: delete-for-me fails with the transaction reads-before-writes error | Read the message and conversation before queuing either write; hide the message and latest preview in the same transaction. An emulator regression test also verifies repeat deletion is safe and the other participant's preview is unchanged. |
| 02:25–02:34: search opens a blank screen | Focus the search field and show an empty-state explanation that message search covers history loaded on this device. |
| 02:39–02:47: whole-chat removal succeeds | Retain the existing successful deletion flow. |

Additional sync corrections prevent duplicate conversation setup and lifecycle presence writes, keep optimistic pending rows from overwriting acknowledged delivery state, decode history off the UI thread, cancel obsolete decode work, and avoid marking new messages read while the user is viewing older history or the app is paused.

## Read/write usage

Fifty sent messages do **not** equal fifty document operations. Sending updates a message and conversation summary; transactions read documents; receipts, typing, presence, listener changes/reconnections and rule lookups add operations. Daily free Firestore quotas are project-wide: 50,000 document reads and 20,000 document writes, with the quota day resetting around midnight Pacific time. See [Firebase quotas](https://firebase.google.com/docs/firestore/quotas) and [listener/rules billing](https://firebase.google.com/docs/firestore/pricing).

The previous directory listener watched up to 200 contacts and received everyone's presence changes. Contacts now use a cache-first one-shot fetch with a ten-minute refresh interval. Only the active chat/profile watches its peer; message, typing and peer listeners are removed when leaving those pages. Duplicate lifecycle presence writes, unnecessary unread resets, duplicate receipt attempts, repeated key publication and redundant conversation setup are reduced. A real `RESOURCE_EXHAUSTED` response pauses nonessential writes/outbox retries with bounded backoff and retains queued messages.

No production usage chart, quota log or actual resource-exhausted error appears in this recording. The confirmed delete error is a transaction-order bug. These client changes cannot reverse document operations already billed or raise a provider quota.

## Validation and remaining checks

- Firestore emulator integration suite: 11 passing tests, including atomic delete/retry and existing privacy/security checks.
- Android checks: `testDebugUnitTest` (10 passing tests) and `assembleDebug`. APK signing and version 4.2.1 / 421 were also verified.
- Install the 4.2.1 APK over the existing debug-signed application without uninstalling or clearing app data, so account keys survive the update.
- Physical two-device acceptance testing and production quota measurement remain required before treating this as a fully accepted release. They were not performed in this workspace.
- Already-lost private keys and deleted/unavailable media cannot be reconstructed by an app update. No encryption validation was disabled to hide these failures.
- No paid backend, Functions, Firebase Storage or new database service was added.
