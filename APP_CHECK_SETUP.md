# Firebase App Check setup

Liquid Chat 4.3.1 initializes Firebase App Check with the Play Integrity provider when `APP_CHECK_PROVIDER=auto` (the repository default).

## Required Firebase configuration

1. In Firebase Console, open **Security > App Check**.
2. Register Android app `com.aistudio.liquidchat.vwnxkp` with the SHA-256 fingerprint of the certificate used to sign the APK.
3. This project commonly distributes GitHub-built APKs outside Google Play. Configure the Play Integrity advanced settings for outside-Play distribution before enforcement. Do not require Play licensing for sideload-only installs.
4. Install a 4.3.1 build and monitor App Check metrics.
5. Only after legitimate requests are showing valid App Check tokens, enable enforcement for the Firebase products the app uses, especially Cloud Firestore and Authentication.

## Debug provider

Set `APP_CHECK_PROVIDER=debug` only for controlled development or emulator testing. Register the generated debug token in Firebase Console. Never commit a debug token to this public repository and never ship the debug provider as the production configuration.

## Safe rollout

App Check provider initialization in the APK is not the same as backend enforcement. Enabling enforcement before the signing SHA-256/provider settings are correct can block login and Firestore access for legitimate installs.

The build accepts:

- `auto` — Play Integrity (recommended)
- `play_integrity` — Play Integrity explicitly
- `debug` — Firebase App Check debug provider
- `none` — no provider; intended only as a temporary recovery/testing option
