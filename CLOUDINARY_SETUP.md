# Firebase + Cloudinary setup

Liquid Chat uses:

- Firebase Authentication for identity.
- Cloud Firestore for realtime users, conversations, messages, typing and delivery/read state.
- Firebase Cloud Messaging for push notifications.
- Firebase Functions `liquidApi` for server-authoritative actions and Cloudinary authorization.
- Cloudinary for profile photos and encrypted chat media.

## Cloudinary secrets

The Android APK must never contain the Cloudinary API secret.

Set these Firebase Functions secrets in project `liquid-chat-v2`:

```bash
firebase functions:secrets:set CLOUDINARY_CLOUD_NAME
firebase functions:secrets:set CLOUDINARY_API_KEY
firebase functions:secrets:set CLOUDINARY_API_SECRET
```

Do not put `CLOUDINARY_API_SECRET` in GitHub Actions variables, Gradle properties, the APK, Firestore or source control.

## Deploy the backend

Manual deployment:

```bash
firebase use liquid-chat-v2
firebase deploy --only functions:liquidApi,firestore:rules
```

GitHub automation:

- `.github/workflows/deploy-firebase-function.yml` deploys `liquidApi` after matching changes reach `main`.
- `.github/workflows/deploy-firebase-rules.yml` publishes Firestore rules.

Set GitHub repository variable `LIQUID_API_URL` to the deployed HTTPS endpoint. The normal default is:

`https://us-central1-liquid-chat-v2.cloudfunctions.net/liquidApi`

## Media limits

The connected Cloudinary Free plan reports a 10 MB maximum raw-asset size. Encrypted chat attachments are stored as raw assets, so the Android client caps attachments at 9 MB to leave room for encryption overhead.

## Security model

Chat attachments are encrypted on-device before upload. Firebase verifies the signed-in user and conversation membership before providing Cloudinary upload authorization or a signed delivery URL. Cloudinary secret operations and deletion remain server-side.

## Live verification

After installing the current APK:

1. Upload/change a profile photo.
2. Send a small photo or voice message.
3. Confirm a new Cloudinary asset exists under `liquid-chat/`.
4. Open the media from the receiving account.
5. Delete/replace it and confirm the backend revokes or removes the asset as expected.
