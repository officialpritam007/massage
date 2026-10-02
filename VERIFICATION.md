# Verification report — Liquid Chat 4.2.0

## Verified on the merged Firebase + Cloudinary baseline

- GitHub Actions Build Android APK run #380 completed successfully.
- Firestore security-rule integration tests passed.
- Firebase + Cloudinary backend JavaScript syntax validation passed.
- Android unit tests passed.
- Debug APK assembly and artifact upload passed.
- Firestore rules deployment completed successfully.

## 4.2 hardening changes

- Encrypted media storage limit aligned with the Cloudinary raw-file limit.
- Failed upload cleanup action added.
- Cloudinary media access changed to expiring signed download URLs.
- Secure Firebase API deployment workflow added.
- Repeated list/message surfaces no longer use independent live backdrop blur.
- Auth, chats home, settings and appearance screens receive modern hierarchy/polish.
- Obsolete Appwrite backend code and migration documentation removed.

## Deployment gate

The Firebase `liquidApi` function must be deployed with these protected values:

- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`

The repository workflow uses `FIREBASE_SERVICE_ACCOUNT_JSON` plus those GitHub Secrets to publish Firebase Secret Manager values and deploy the function. Firebase Functions requires the project to be on Blaze.

## Device acceptance test

Before treating 4.2.0 as a public release, test with two real accounts/devices:

- Registration, sign-in, reopen and logout.
- Text/reply/edit/reaction/delete.
- Delivered/read receipts and online/last-seen privacy.
- Typing morph.
- Photo/video/document upload, download and delete.
- Voice hold/cancel/lock/pause/preview/send/retry.
- App background push notification and notification tap.
- Block/unblock and account deletion.

A green CI build proves compilation/tests, not the live Cloudinary/Firebase credentials or real-device network behavior.
