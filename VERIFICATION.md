# Verification — Liquid Chat 4.2.0 free direct mode

## CI

The same revision must pass:

- Firestore security-rule emulator tests.
- Android unit tests.
- Debug APK assembly.
- APK artifact upload.

## Cloudinary setup gate

Before media testing, confirm unsigned preset `liquid_chat_unsigned` exists in cloud `mthzgqhv`.

## Two-device acceptance

Verify with two Firebase accounts:

- registration/login;
- realtime text send/receive;
- reply/edit/reactions;
- Sending → Sent → Delivered → Read;
- typing and presence;
- direct photo upload and display;
- profile photo upload/change/remove;
- voice/video upload if those formats are enabled;
- delete-for-me and sender delete-for-everyone;
- archive/favorite/mute/disappearing settings;
- logout and sign-in again.

## Expected limitations

- No Firebase Functions deployment.
- No reliable FCM push when the app process is fully killed.
- Direct Cloudinary media is public-by-URL.
- Firestore deletion does not guarantee deletion of the Cloudinary asset itself.
