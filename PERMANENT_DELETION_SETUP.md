# Activating permanent logout and account deletion

Version 4.2.2 treats **Log out** and **Delete account** as irreversible account erasure, as requested. This is a change from 4.2.1's ordinary session logout. The app explains the consequences and requests password/Google verification before queuing a new deletion. It does not report success until a trusted worker confirms cloud cleanup and the local wipe completes.

## Configuration

1. Review and merge the code; deploy the repository's `firestore.rules` to the existing `liquid-chat-v2` Firebase project. The new private device/asset subcollections and deletion gate need these rules. No Firebase Functions or Storage deployment is required.
2. Add GitHub repository Actions secrets: `FIREBASE_SERVICE_ACCOUNT_JSON`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`. The service account must belong to `liquid-chat-v2` and have Firestore read/write plus Firebase Authentication user-delete permissions. Never paste these credentials into chat, source code or Android configuration.
3. Run **Cloud Account Cleanup** manually with `check_only = true`. This validates credentials and makes no deletion or runtime-enablement changes.
4. Set the repository Actions variable `ENABLE_CLOUD_ACCOUNT_CLEANUP` to `true`, then run the workflow with `check_only = false`. This publishes the worker heartbeat and enables queuing in the app. The scheduled worker checks pending jobs every 15 minutes. Delays are possible; it is not an immediate HTTP endpoint.
5. Test with dedicated disposable accounts and uploaded assets before rolling out 4.2.2. Keep administrator secrets limited to the trusted main-branch workflow. The workflow does not process jobs from PR/fork branches.

If the heartbeat is absent or over an hour old, the app refuses a new deletion and keeps the current session intact. A queued deletion is irreversible and freezes both participants' writes to affected conversations. On timeout/error the app shows the remaining status and allows checking/retrying; it never says that logout completed.

## Media ownership and backups

New uploads save Cloudinary's response signature, asset ID, public ID, version and URL in a private owner-only Firestore registry. The worker verifies the signature and checks the provider's asset identity before destroying media. The registry includes uploads whose chat/photo references were later removed. Retried writes cannot change the ownership proof.

Old unsigned uploads did not save these receipts. The worker blocks deletion when an old referenced upload cannot be verified. An operator must establish ownership independently in Cloudinary before adding its original versioned URL to the optional `LEGACY_MEDIA_APPROVALS_JSON` Actions secret:

```json
{"account-uid": ["https://res.cloudinary.com/mthzgqhv/image/upload/v123/verified-owned-asset.jpg"]}
```

Never approve a URL merely because the client put it in a profile or message. Unreferenced old uploads cannot be discovered reliably from Firestore alone and require an inventory review in Cloudinary. If an old provider is involved, erase its owned objects and backups there as well. This worker supports the configured Cloudinary environment only.

Existing Cloudinary backups/version records block completion. Remove them with the provider's administrator tools and retry. The worker checks deleted-asset records too; a missing original URL is not proof that a restorable backup is gone. CDN invalidation is requested during destruction, but provider caches can take time to expire. Firestore exports, managed backups/PITR and any operator-managed backups require their own retention review; a live-database delete cannot erase an independent export. External copies downloaded by another person or an offline device cannot be remotely guaranteed erased.

## Scope and recovery

The server derives conversation IDs from membership; clients cannot submit deletion targets. A server manifest is saved before recursive deletion so orphaned subcollections remain discoverable after a crash. Cleanup removes the account's private profile/device/asset trees, public directory, usernames, reports involving that account, blocked-list references, whole one-to-one conversation trees and Firebase Auth user. Unrelated conversations/accounts are retained.

A temporary owner-only `accountDeletionRequests` receipt contains status and a UID until the app acknowledges completion, then the app deletes it. If the Auth session token expires before acknowledgement, an operator must remove this completed receipt; do not recreate the old profile to restore access. When local deletion has already been verified/acknowledged, it can resume without the deleted Firebase account. Android local cleanup also resets FCM/installations, removes encryption/cache keys, closes/clears Firestore and Room, cancels/prunes work, dismisses notifications and clears app-owned preferences/files. The deletion marker is cleared last, allowing local cleanup to resume after process death.

The app disables OS backup/transfer. **Uninstall has no remote cleanup callback**: complete deletion in the app first. Administrator credentials, a heartbeat, deployed rules and backup/inventory review have not been configured or verified by these source changes alone.

## Reference documentation

- [Android package removal behavior](https://developer.android.com/reference/android/content/Intent#ACTION_PACKAGE_REMOVED)
- [Cloudinary response signatures](https://cloudinary.com/documentation/signatures)
- [Cloudinary deletion and backup APIs](https://cloudinary.com/documentation/admin_api)
- [Firestore recursive deletion](https://cloud.google.com/python/docs/reference/firestore/latest/google.cloud.firestore_v1.client.Client#google_cloud_firestore_v1_client_Client_recursive_delete)
- [Firestore Android termination and persistence clearing](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/FirebaseFirestore)
