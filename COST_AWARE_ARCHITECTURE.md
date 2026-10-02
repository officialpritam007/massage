# Liquid Chat — cost-aware Firebase + Cloudinary architecture

Liquid Chat uses each provider for a distinct responsibility and avoids storing large binaries in Firestore.

## Firebase

- Firebase Authentication: account and session identity.
- Cloud Firestore: 1-to-1 conversation metadata, realtime message snapshots, presence and typing state.
- Firebase Cloud Messaging: push delivery.
- Firebase App Check: abuse protection where supported.
- Firebase Functions `liquidApi`: authenticated server-authoritative mutations, notification dispatch and Cloudinary signing.
- Firebase Storage is intentionally not used by the Android client.

## Cloudinary

- Profile photos and encrypted chat-media objects.
- Chat media is uploaded as authenticated assets after the Firebase backend issues a short-lived signed upload authorization.
- Authorized reads use short-lived signed delivery URLs.
- The Android client limits chat attachments to 9 MB, below the connected Free-plan 10 MB raw-asset limit.

## Cost guardrails

1. Firestore realtime listeners stay bounded and older message history is paginated.
2. Typing/presence writes are throttled.
3. Large binary data never goes into Firestore.
4. Cloudinary transformations are not required for encrypted chat-media objects.
5. Server secrets are stored in Firebase Functions secrets, never in the app.
6. Monitor Firebase/Google Cloud billing settings and Cloudinary usage. Firebase Functions deployment may require a billing-enabled Firebase/Google Cloud project even when actual usage remains within no-cost quotas.

## Security boundary

Android authenticates with Firebase. The backend verifies the Firebase ID token and conversation authorization before issuing Cloudinary upload or delivery authorization. Cloudinary API secrets remain server-side.
