# Liquid Chat complete UI update

## 4.2.4 ZIP integration review — 2026-10-03

- Compared `liquid_chat_ios26_update.zip` against current `main`, commit `cadc2b21bb4fd38c43d047398e0066a660d4c786`. The archive changes five UI/navigation files only.
- Included the updated Chats header, search field, archive row, pinned indicator, direct contact-to-chat action, and diagonal glass highlights.
- Restored the missing shared `GlassBottomBar` in `ui/components`; the uploaded screens imported it but the archive contained no implementation. Chats, Contacts and Settings now share the same selected-tab animation and unread badge.
- Added accessible selected-tab semantics and regression checks for tab callbacks and unread-count updates.
- Preserved the Chats navigation entry and saved state when returning from Settings.
- Bumped Android version to 4.2.4 / 424.
- Firebase, Cloudinary, notification/decryption, account cleanup, security rules and existing workflows are unchanged by this UI integration.
- The 24 trusted-cleanup unit tests and JSON/TOML/XML parse checks passed locally. Android unit tests, Firestore emulator tests and APK assembly are verified by the `Build Android APK` workflow before advancing `main`.

The notes below describe earlier UI work and its original verification status.

## Included source changes

- Replaced the plain background with a shared royal-blue to deep-navy gradient in Dark Mode and a light-blue gradient in Light Mode.
- Updated shared glass surfaces and controls to use the global corner-roundness setting, including an expanded 0–64 dp range and Boxy / Balanced / Rounded / Extra Round presets in Appearance.
- Added a live corner-roundness preview and fixed the Appearance screen header's status-bar inset.
- Added consistent safe-area handling to Search and Profile headers.
- Changed bottom navigation to Chats / Favorites / Archived / Settings and kept the Settings bottom bar visible.
- Removed Groups and Group Chat routes/screens from the app UI, removed group results from Search, and removed group-related visible settings copy. Group backend/model code is retained where it may be needed for existing stored data or shared repository compatibility.
- Added microphone recorder exception handling, live recording timer, pulsing dot, animated seven-bar waveform, upload status, and Reduced Motion behavior.
- Added an animated typing-dot indicator and retained typing timeout/cleanup logic.
- Preserved developer/contact details as requested.

## Verification status

- Kotlin parser check: no syntax/`expecting` diagnostics were reported for the changed source files in a parser-only invocation.
- Full Android/Gradle build: **not run** in this environment because an Android SDK/Gradle project build environment was not available.
- Runtime/device/Firebase behavior: **not tested here**. Run the repository's GitHub Actions workflow and test the resulting APK on a device before release.
