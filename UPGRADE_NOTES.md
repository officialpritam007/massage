# Liquid Chat v3.3.0

- Added incoming-call detection from Firestore.
- Added Accept/Reject flow.
- Added runtime microphone/camera permission flow for calls.
- Connected WebRTC offer/answer/ICE observation to the call signaling document.
- Fixed missing FirestoreCallSignaling repository instance.
- Auto-opens the active-call screen for incoming calls.

Note: TURN credentials are not bundled. A production deployment should configure a TURN service for networks where direct/STUN connectivity fails.

Build through GitHub Actions; Android Studio is not required.
