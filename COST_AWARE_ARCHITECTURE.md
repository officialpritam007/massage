# Cost-aware architecture

Target: run Liquid Chat without a paid application backend.

## Firebase

Firebase Authentication and Cloud Firestore provide the application data layer. Firestore contains user/profile data, conversation metadata, messages, typing/presence and Cloudinary media URLs.

## Cloudinary

Android uploads media directly with an unsigned preset. No Cloudinary API secret is stored in the APK. The app caps attachments at 9 MB.

Connected Cloudinary account: Free plan.

## Not used

- Firebase Cloud Functions
- Firebase Storage
- Custom paid server

## Important distinction

Free architecture means no paid backend is required by design. It is not unlimited usage: exceeding Firebase or Cloudinary free quotas can throttle or stop requests.

## Push notifications

A secure FCM send operation needs a trusted sender. Because this mode deliberately has no trusted backend, reliable push notifications to a fully killed app are not included. Realtime Firestore sync works while the process is active and catches up when reopened.
