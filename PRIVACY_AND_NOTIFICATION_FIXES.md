# Liquid Chat 4.2.2 changes

The video corrections in `VIDEO_FIXES_2026-10-03.md` are included in this branch. This version adds the latest requested preview/notification and permanent-deletion behavior. The earlier report's ordinary-logout key preservation is superseded: both logout and account deletion now require verified cloud erasure and destroy local identities.

## Chat previews and notifications

- New conversation summaries include the authenticated encrypted envelope, type and revision of the latest message, never its decrypted text. Security rules tie the envelope to the actual message. Edits update the revision, preventing stale cached text.
- A shared local decoder checks sender fingerprints and signatures and uses the account's device key. Trusted historical fingerprints remain usable. It serves history, chat-list previews and notification workers.
- Legacy summaries fetch only their latest message with bounded retry. Hidden latest messages remain hidden during presence refresh. Missing keys show an unavailable state instead of pretending ciphertext was recovered.
- The messaging service accepts data-only `recipient_id`, `conversation_id`, `message_id`. The worker checks the current account, authenticated server membership/message, deletion/expiry, mute/block settings and preview preference before displaying a locally decoded notification. IDs/receipt jobs are account-scoped. Tapping opens the correct conversation; signing out cancels notifications/work and resets push registration.
- Background Firestore summary updates can enqueue notifications while the process is alive. A trusted sender is still required for fully killed-app FCM delivery; no sender was deployed here. Send only IDs, without an FCM `notification` object or plaintext body:

```json
{"message":{"token":"recipient-device-token","data":{"recipient_id":"recipient-uid","conversation_id":"cid","message_id":"mid"},"android":{"priority":"high"}}}
```

## Deletion

New owner-only rules require recent authentication for queuing a job. Queued accounts cannot recreate their profile or send new data, and other participants cannot append new messages/conversations involving a queued account. The privileged cleanup worker derives scope, validates media ownership before destructive work, verifies cloud destruction and recursively deletes nested data. Android persists pending/verified state so a relaunch does not restore old chats, and wipes local state after acknowledgement. Deletion unavailable/pending/blocked states are visible and never report success.

See [PERMANENT_DELETION_SETUP.md](PERMANENT_DELETION_SETUP.md). The worker remains disabled by default; administrator credentials, rules deployment, media inventory/backups and killed-app push setup still require activation/acceptance testing. Android uninstall alone cannot trigger cloud deletion.

## Validation

- Cleanup-worker ownership/failure/resume suite: **11 tests passed locally**, including a valid-signed-response attack against another account's media namespace and refusal to claim complete erasure without an orphan-media inventory review.
- Firestore suite adds authenticated-envelope matching, monotonic receipts, private token/proof rules, recent-auth deletion, and frozen-account/peer-write checks.
- Android suite adds preview privacy, decrypted text/media fallback and encrypted-edit cache invalidation tests.
- GitHub Actions runs all three suites and builds a 4.2.2 / 422 debug APK. The final CI results are recorded in the PR.
- Physical two-device notification/Google/password/deletion testing and real provider backup/inventory verification remain required. Already lost private keys and missing legacy media cannot be reconstructed.
