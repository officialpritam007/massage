# GitHub update — Liquid Chat 4.0.0

আগের repository-এর backup/branch রাখুন। ZIP extract করে `LiquidChat` folder-এর **ভেতরের** ফাইল repository root-এ বসান; আরেকটি nested app folder বানাবেন না। GitHub-এ এই কাজ স্বয়ংক্রিয়ভাবে push করা হয়নি।

## কী রাখবেন

- নিজের সঠিক `app/google-services.json`।
- application ID `com.aistudio.liquidchat.vwnxkp`।
- আগের app-এর signing key; এই ZIP-এ original debug key আছে। Public release-এর private key/password repository-তে দেবেন না।
- Git history এবং আপনার অন্য unrelated files।
- Firebase users/chats ও Appwrite-এর existing media। Database/bucket delete করবেন না।

## পুরোনো source থেকে কী সরাবেন

নিচের obsolete paths নতুন ZIP-এ নেই। শুধু নতুন files upload করলে GitHub পুরোনো files নিজে delete করে না, তাই এগুলো আগে সরান:

- root-এর `ChatRepository.kt`, `ConversationScreen.kt`, `AppwriteStorageService.kt`, `app-build.gradle.kts`, `libs.versions.toml` — duplicate loose copies; `app/` ও `gradle/`-এর প্রকৃত files রাখুন।
- `functions/` — পুরোনো Firebase Functions source। এটি সরানো মানে deployed function বন্ধ হওয়া নয়।
- `appwrite-functions/firebase-appwrite-bridge/` — নতুন `liquid-api` দিয়ে প্রতিস্থাপিত।
- `app/src/main/java/com/example/data/calls/` এবং `app/src/main/java/com/example/security/`।
- `storage.rules`, `LIQUID_CHAT_UPDATE_NOTES.md`, `UPGRADE_NOTES.md` — পুরোনো setup নির্দেশনা।
- `app/src/test/java/com/example/ExampleUnitTest.kt` — placeholder test।

`app/src`, `app/build.gradle.kts`, `gradle/libs.versions.toml`, root build/settings files, `.github/workflows/build-apk.yml`, Firestore rules/indexes এবং docs নতুন ZIP থেকে replace করুন। `appwrite-functions/liquid-api/`, `tests/` ও `scripts/` যোগ করুন।

`local.properties`, `.env`, build folders, node_modules, service-account JSON বা private release keystore upload করবেন না।

## Backend বসানোর ক্রম

1. `APPWRITE_SETUP.md` পড়ে নতুন authenticated API ও private scheduled function configure/deploy করুন। দুইটি function-ই একই `liquid-api` source ব্যবহার করে; entry point আলাদা।
2. `scripts/migrate-profiles.md` অনুযায়ী existing users-এর public directory তৈরি করুন। Migration-এর আগে database backup রাখুন।
3. নতুন Firestore rules/indexes deploy করুন। পুরোনো app direct message writes করে থাকলে নতুন rules-এর পরে সেটি আর compatible হবে না—দুই account-এই নতুন APK লাগবে।
4. GitHub Actions repository variables-এ `LIQUID_API_URL`, `APPWRITE_ENDPOINT`, `APPWRITE_PROJECT_ID`, `APPWRITE_BUCKET_ID` দিন। API key বা Firebase service-account JSON এখানে নয়; সেগুলো server secret।
5. Actions → Build APK → Run workflow। সফল হলে artifact থেকে APK নিন।
6. `VERIFICATION.md`-এর two-device checks শেষ করুন।

পুরোনো deployed Firebase Functions বা পুরোনো Appwrite bridge তখনই disable করবেন, যখন নতুন app দিয়ে send/upload/notification ঠিক কাজ করছে এবং পুরোনো clients update করেছে। পুরোনো media URLs স্বয়ংক্রিয়ভাবে Appwrite-এ migrate করা হয়নি।

## Release সীমা

এই update Android-এ reference-inspired frosted glass UI; Apple-এর native iOS rendering-এর pixel-identical copy নয়। Auth/API configuration ছাড়া শুধু ZIP upload করলেই live messaging চালু হবে না। Signed public release এবং বাস্তব ডিভাইসের পরীক্ষা আলাদা release gate।
