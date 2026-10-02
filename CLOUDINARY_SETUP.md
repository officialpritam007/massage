# Firebase + Cloudinary setup

Liquid Chat 4.2 uses Firebase for identity/realtime/backend logic and Cloudinary for media.

## Required GitHub Secrets

Add these repository secrets:

- `FIREBASE_SERVICE_ACCOUNT_JSON`
- `CLOUDINARY_CLOUD_NAME`
- `CLOUDINARY_API_KEY`
- `CLOUDINARY_API_SECRET`

Never put the Cloudinary API secret in Android source, Gradle BuildConfig, Firestore, or an APK.

## Firebase project

The configured project is:

`liquid-chat-v2`

Firebase Functions deployment requires the Firebase project to use the Blaze plan. Blaze has no-cost usage quotas, but a billing account must be linked before Functions can be deployed.

## Automatic backend deployment

The workflow:

`.github/workflows/deploy-firebase-api.yml`

publishes the three Cloudinary values to Firebase Secret Manager and deploys `liquidApi` whenever the Firebase backend changes on `main`.

If any required GitHub Secret is missing, the workflow exits cleanly with warnings instead of exposing credentials or breaking the Android build.

## Android backend URL

Set the repository variable:

`LIQUID_API_URL`

to the deployed HTTPS URL of the `liquidApi` function. The standard URL is normally:

`https://us-central1-liquid-chat-v2.cloudfunctions.net/liquidApi`

The Android build workflow already uses that URL as its fallback.

## Media model

- Profile photos are optimized images stored as authenticated Cloudinary assets.
- Chat photos, video, voice and documents are encrypted on-device first, then uploaded as authenticated raw assets.
- The connected Cloudinary Free plan raw-file ceiling is 10 MB, so Liquid Chat limits encrypted attachments to **9 MB** to leave encryption overhead.
- Download access is authorized by Firebase and returned as an expiring signed Cloudinary URL.
- Failed uploads trigger best-effort remote cleanup.

## Appwrite

Appwrite is no longer part of the active runtime architecture. Legacy Appwrite code and migration files are intentionally removed. No old Appwrite media is required for this app state.

## Verification

After deployment, open **Settings → Developer Diagnostics** while signed in. The **Firebase API + Cloudinary** row performs an authenticated live health check and should show PASS.

Then test two accounts/devices with text, photo, video, document, voice, notification delivery, delete-for-everyone and account deletion before treating the build as a public release.
