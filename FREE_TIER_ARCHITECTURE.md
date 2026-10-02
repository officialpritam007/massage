# Liquid Chat — cost-aware Firebase + Cloudinary architecture

## Responsibilities

### Firebase

- Authentication: account/session identity.
- Cloud Firestore: realtime users, directory, conversations, messages, presence and typing.
- FCM: push notification delivery.
- App Check: client abuse protection when enabled for the build.
- Firebase Functions: authenticated `liquidApi` backend for server-authoritative operations.

### Cloudinary

- Authenticated profile images.
- Encrypted chat attachment bytes.
- Expiring signed downloads generated only after Firebase-backed authorization.

Firebase Storage is intentionally not used by the Android client.

## Cost guardrails

- Only the active conversation keeps message and typing streams hot.
- Initial history is bounded and older messages page on demand.
- Presence heartbeat is throttled and background transition writes offline immediately.
- Typing state is short-lived and throttled.
- Binary media never goes into Firestore.
- Chat media is capped at 9 MB because encrypted media uses Cloudinary raw assets and the connected Free plan raw-file ceiling is 10 MB.
- Repeated chat rows and message bubbles use lightweight translucent rendering instead of independent backdrop blur.

## Billing requirement

Cloud Functions for Firebase requires a Firebase Blaze project for deployment. Blaze includes no-cost usage quotas, but a billing account must be linked. Treat budget alerts/spend caps as deployment prerequisites if strict cost control is required.

## Secrets

`CLOUDINARY_API_SECRET`, Firebase service-account credentials and other server credentials must never be committed to source, embedded in BuildConfig or shipped in the APK.
