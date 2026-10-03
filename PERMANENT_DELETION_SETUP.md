# Activating permanent logout and account deletion

Version 4.2.2 treats **Log out** and **Delete account** as irreversible account erasure, as requested. This is a change from 4.2.1's ordinary session logout. The app explains the consequences and requests password/Google verification before queuing a new deletion. It does not report success until a trusted worker confirms cloud cleanup and the local wipe completes.

## Configuration

1. Review and merge the code; deploy the repository's `firestore.rules` to the existing `liquid-chat-v2` Firebase project. The new private device/asset subcollections and deletion gate need these rules. No Firebase Functions or Storage deployment is required.
2. Add GitHub repository Actions secrets: `FIREBASE_SERVICE_ACCOUNT_JSON`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. The service account must belong to `liquid-chat-v2` and have Firestore read/write plus Firebase Authentication user-delete permissions. Never paste these credentials into chat, source code or Android configuration.
3. Publishing changes to the worker/workflow on `main` automatically runs **Cloud Account Cleanup** in check-only mode. You can also run it manually with `check_only = true`. This validates credentials and makes no deletion or runtime-enablement changes. Fix missing/invalid credentials before enabling processing.
4. Set the repository Actions variable `ENABLE_CLOUD_ACCOUNT_CLEANUP` to `true`, then run the workflow with `check_only = false`. This publishes the worker heartbeat and enables queuing in the app. The scheduled worker checks pending jobs every 15 minutes. Delays are possible; it is not an immediate HTTP endpoint.
5. Test with dedicated disposable accounts and uploaded assets before rolling out 4.2.2. Keep administrator secrets limited to the trusted main-branch workflow. The workflow does not process jobs from PR/fork branches.

If the heartbeat is absent or over an hour old, the app refuses a new deletion and keeps the current session intact. A queued deletion is irreversible and freezes both participants' writes to affected conversations. After acceptance the app signs out of Firebase and checks a saved random deletion proof without requiring the deleted Auth account. On timeout/error it shows the remaining status and allows checking again; it never reports completed data erasure early. Failed jobs are retried by the worker after configuration/ownership review is corrected.

## Media ownership and backups

New uploads request a random public ID under `liquid-chat/accounts/<Firebase UID>/<32 hex characters>` and save Cloudinary's response signature, asset ID, public ID, version and URL in a private owner-only Firestore registry. The worker verifies the signature, exact owner namespace and provider asset identity before destroying media. A response signature by itself is insufficient: unsigned duplicate uploads can return an existing asset's response. The registry includes uploads whose chat/photo references were later removed. Retried writes cannot change the ownership proof.

The unsigned upload preset must preserve the supplied public ID: disable **Disallow public ID**, filename-based IDs and automatic public-ID prefixes/fixed-mode folders that rewrite this path. An asset folder in dynamic folder mode may remain `liquid-chat`; it must not alter the delivery public ID. Test image/video/raw uploads and their returned IDs. A misconfigured upload is recorded privately for operator cleanup but is not attached to a chat or profile; the app displays an error.

Old unsigned uploads did not save these receipts, and removed references can leave undiscoverable old uploads. To avoid claiming complete erasure after deleting only today's references, **each account requires an operator inventory/backup review** in `LEGACY_MEDIA_APPROVALS_JSON` before cleanup can finish. Establish ownership independently, erase/review unreferenced legacy assets and backups, and include any still-referenced legacy original URLs that the worker is authorized to destroy:

```json
{"account-uid": {"inventoryReviewed": true, "urls": ["https://res.cloudinary.com/mthzgqhv/image/upload/v123/verified-owned-asset.jpg"]}}
```

Never approve a URL merely because the client put it in a profile or message. Do not set `inventoryReviewed` until unreferenced old uploads and all provider/database backups have been reviewed and erased as needed. An account with no legacy data still needs this trusted approval; the client cannot assert it. If an old provider is involved, erase its owned objects and backups there as well. This worker supports the configured Cloudinary environment only. Fully automatic erasure without this review requires migrating away from untracked unsigned legacy uploads; it is not guaranteed by this build.

Existing Cloudinary backups/version records block completion. Remove them with the provider's administrator tools and retry. The worker checks deleted-asset records too; a missing original URL is not proof that a restorable backup is gone. CDN invalidation is requested during destruction, but provider caches can take time to expire. Firestore exports, managed backups/PITR and any operator-managed backups require their own retention review; a live-database delete cannot erase an independent export. External copies downloaded by another person or an offline device cannot be remotely guaranteed erased.

## Scope and recovery

The server derives conversation IDs from membership; clients cannot submit deletion targets. A server manifest is saved before recursive deletion so orphaned subcollections remain discoverable after a crash. Cleanup removes the account's private profile/device/asset trees, public directory, usernames, reports involving that account, blocked-list references, whole one-to-one conversation trees and Firebase Auth user. Unrelated conversations/accounts are retained.

A temporary owner-only `accountDeletionRequests` gate retains the UID while cleanup is pending. After deleting Firebase Auth, the worker keeps this gate for **70 minutes**, covering the one-hour lifetime of ID tokens on other devices plus a ten-minute margin. Clients can never delete the gate; their old tokens therefore cannot recreate profiles, directory entries or chats during that interval. After expiry, the worker atomically removes the gate and publishes completion in `deletionProofs/<256-bit random ID>`. Final completion takes at least 70 minutes after cloud cleanup, plus scheduled-worker delay. New conversations/reports also require a live peer directory entry, preventing peers from recreating records for the deleted account later.

The random proof contains only status/timestamps or a generic cleanup error, **no UID, profile, message or deletion targets**. Rules permit getting one known random proof but deny listing, client creation and updates. The app persists its random ID before requesting deletion, reads it without Auth after relaunch, and deletes it only once completion is verified. No operator intervention or recreation of the deleted Auth user is needed to resume. The completion flag is stored before acknowledgement, so a crash during device cleanup can also resume after the proof has been removed.

Android local cleanup resets FCM/installations, removes encryption/cache keys, closes/clears Firestore and Room, cancels/prunes work, dismisses notifications and clears app-owned preferences/files. The deletion marker is cleared last, allowing local cleanup to resume after process death. Startup recovery does not offer a session-only logout that would bypass this behavior.

The app disables OS backup/transfer. **Uninstall has no remote cleanup callback**: complete deletion in the app first. Administrator credentials, a heartbeat, deployed rules and backup/inventory review have not been configured or verified by these source changes alone.

## Reference documentation

- [Android package removal behavior](https://developer.android.com/reference/android/content/Intent#ACTION_PACKAGE_REMOVED)
- [Firebase ID-token lifetime and session revocation](https://firebase.google.com/docs/auth/admin/manage-sessions)
- [Cloudinary response signatures](https://cloudinary.com/documentation/signatures)
- [Cloudinary deletion and backup APIs](https://cloudinary.com/documentation/admin_api)
- [Firestore recursive deletion](https://cloud.google.com/python/docs/reference/firestore/latest/google.cloud.firestore_v1.client.Client#google_cloud_firestore_v1_client_Client_recursive_delete)
- [Firestore Android termination and persistence clearing](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore)
