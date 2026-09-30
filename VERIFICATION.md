# Verification report — 4.0.0

Completed locally:

- Backend JavaScript syntax check (`node --check`).
- Four authenticated API policy tests: pair authorization, deterministic Appwrite IDs, message validation and presence freshness.
- Firestore emulator rules: four tests covering private data, participant messages, typing ownership/expiry and protected server fields.
- Android resource/configuration tasks resolved. Final Kotlin compile could not complete in this restricted runner because Gradle needed additional Maven artifacts while the network proxy was unavailable (`Network is unreachable` for KSP/Kotlin artifacts).

Before release, GitHub Actions must pass `assembleDebug`, `testDebugUnitTest` and `lintDebug`. Then test on two physical Android devices with two accounts: registration/login, text/reply/typing, read receipts, photo/video/document/voice, cancel/lock recording, favorites/archive, notification tap after restart, privacy/block, disappearing messages and account deletion.

This report does not claim zero errors, production release signing, live Firebase/Appwrite deployment, or exact iOS rendering. Production release requires Firebase/Appwrite configuration, a private release key and the two-device acceptance test.
