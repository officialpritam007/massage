# Verification checklist — Liquid Chat 4.1.1

## CI checks

The Android build workflow must pass all of these from the same revision:

- Firestore security-rule emulator tests.
- Firebase + Cloudinary backend JavaScript syntax validation.
- Android unit tests.
- Debug APK assembly.
- APK artifact upload.

The Firebase backend deployment workflow must also complete successfully on `main` whenever `firebase-functions/liquid-api/**`, `firebase.json` or the deployment workflow changes.

## Live acceptance checks

Use two physical Android devices/accounts and verify:

- registration/login and cached-session reopen;
- text send/receive, reply, typing bubble, ordering and retry;
- Sending → Sent → Delivered → Read transitions;
- background push delivery and notification tap;
- profile-photo upload/change/delete;
- photo/video/voice attachment upload, download, playback and cache reuse;
- delete-for-me and delete-for-everyone;
- privacy/block behaviour;
- reinstall/logout/account-deletion cleanup.

## Cloudinary acceptance

- New app media appears under the `liquid-chat/` public-id namespace.
- Chat media uses authenticated delivery and is not publicly retrievable without the signed URL.
- Attachments above the app's 9 MB limit are rejected before upload.
- Deleted/replaced media is removed or revoked by the backend.

## Release boundary

A successful APK build proves compile/test integrity, not live backend correctness. Treat a revision as production-ready only after the deployment and two-device checks above pass with the actual Firebase project and Cloudinary account.
