# Firebase + Cloudinary setup

Liquid Chat now uses:

- Firebase Authentication for identity.
- Cloud Firestore for realtime users, conversations, messages, typing and delivery/read state.
- Firebase Cloud Messaging for push notifications.
- Cloudinary for profile photos and encrypted chat media.
- Firebase Functions `liquidApi` for server-authoritative actions and signed Cloudinary access.

## Cloudinary secrets

The Android APK must never contain the Cloudinary API secret.

Set these Firebase Functions secrets:

```bash
firebase functions:secrets:set CLOUDINARY_CLOUD_NAME
firebase functions:secrets:set CLOUDINARY_API_KEY
firebase functions:secrets:set CLOUDINARY_API_SECRET
```

The connected Cloudinary account can remain on the Free plan as long as usage stays within its current quota.

## Deploy the backend

Use the Firebase project `liquid-chat-v2`.

```bash
firebase use liquid-chat-v2
firebase deploy --only functions:liquidApi,firestore:rules
```

After deployment, copy the HTTPS URL for `liquidApi` into the GitHub repository variable:

`LIQUID_API_URL`

The expected URL is normally similar to:

`https://us-central1-liquid-chat-v2.cloudfunctions.net/liquidApi`

Do not put `CLOUDINARY_API_SECRET` in GitHub Actions, Gradle properties, the APK, Firestore, or source control.

## Existing Appwrite media

The code migration changes all **new** uploads to Cloudinary. Existing Firestore messages or profiles whose media URL starts with `appwrite:` are legacy data and are not copied automatically.

Before removing the old Appwrite project, either migrate those assets to Cloudinary and rewrite their Firestore media references, or accept that those old attachments will no longer be available. Do not delete the old Appwrite bucket until that migration is completed and verified.

## Security model

Chat attachments are encrypted on-device before upload. Cloudinary stores the encrypted bytes as authenticated assets. Firebase verifies the signed-in user and conversation membership before returning an authenticated Cloudinary delivery URL. Profile photos use the same authenticated backend path and respect the existing profile-photo privacy checks.

Cloudinary API secret operations (signing and deletion) occur only inside Firebase Functions.
