# GitHub update — Liquid Chat 4.0.0 · iOS 26 Liquid Glass overhaul

## এই release-এ কী এসেছে

- **Design system:** opaque white/blue card কমিয়ে layered translucent glass — adaptive blur, tint, border highlight, depth shadow ও সূক্ষ্ম noise/refraction; light ও dark দুটো theme-এই readable। Clear ও Regular দুটো preset আগের মতোই।
- **Chrome config-driven:** chat header, composer, bottom navigation ও সব sheet/dialog এখন Appearance-এর slider (glass intensity, blur, border/depth, accent) থেকে সরাসরি চলে। কোনো hardcoded tint/alpha আর নেই।
- **Message bubble:** content অনুযায়ী width (viewport-এর max ~78%), consecutive grouping (একই sender + ৪ মিনিট window), natural corners/tail, sent = accent glass tint, received = neutral glass tint।
- **Timestamp + delivery tick:** bubble-এর ভেতরে একটাই compact bottom-right row; Sending → Sent → Delivered → Read animation; fail হলে "Not delivered • tap to retry" pill।
- **Photo/video bubble:** thumbnail আসল aspect-ratio-তে (কালো খালি জায়গা নেই), rounded clipping, download shimmer, upload spinner, optional caption। caption ছাড়া "Photo" জাতীয় placeholder লেখা আর দেখায় না।
- **Save to Gallery:** viewer-এর Download button, message long-press → **Save to gallery**, আর Settings → Privacy-তে **Auto-save received media** (default বন্ধ)। Android 10+ এ MediaStore ব্যবহার করে; media আগের মতোই Appwrite-এ private থাকে, Firebase Storage-এ কিছু যায় না।
- **Reply UI:** compact translucent quote block — accent edge, sender name, এক লাইনে truncate, original photo/video হলে thumbnail।
- **Composer ও attachment sheet:** floating glass composer, text লিখলে mic → send orb morph; `+` চাপলে iOS-ঘরানার frosted bottom sheet (Photo / Camera / Video / File)।
- **Chat list ও search:** compact glass rows (pin, draft, delivery tick, unread badge, timestamp), thinner floating bottom nav with liquid capsule, search results-এ match highlight। Search-এ কোনো message-এ tap করলে সেই chat খুলে ঠিক ওই message-এ scroll হয়ে সাময়িকভাবে accent highlight হয়।
- **শেষ polish pass:** grouped bubble-এর spacing আলাদা (একই sender-এর bubble কাছাকাছি, নতুন sender-এর আগে ফাঁকা), swipe-to-reply করার সময় পাশে reply glyph, sent/received bubble দুই দিক থেকে animate হয়ে ঢোকে, bottom sheet grabber টেনে নিচে swipe করলে dismiss, Locked recording chip, Settings-এ duplicate auto-save toggle সরানো, diagnostics-এ realtime listener health দেখানো।
- **Typography, scrim, haptics:** হালকা system-font hierarchy, wallpaper-এর উপর adaptive readability scrim, send/reaction/swipe/delete/sheet/tab-এ haptics, Reduced Motion দিলে ভারী animation বন্ধ।
- **অপরিবর্তিত:** Firebase (Auth/Firestore/presence/typing/FCM) + Appwrite (private media) split, server-authoritative deletion, developer diagnostics ও startup recovery, 1-to-1 scope (group/call নেই)।

## Push / merge অবস্থা

- Commit **`4a5a70f`** branch **`chatgpt/ios26-liquid-glass`**-এ push করা আছে; `main` (`327368b`) অপরিবর্তিত।
- GitHub Actions run **`36821131815`** সম্পূর্ণ সবুজ: Run unit tests ✅, Build Debug APK ✅, Upload APK ✅ (artifact `liquid-chat-debug-apk`, ~27.1 MB)।
- Merge/PR আপনার সিদ্ধান্ত — আমি কোনো PR খুলিনি বা `main`-এ merge করিনি। Branch merge করলে հետের commits-ও একসাথে যাবে (এই release-এর সব UI কাজ এই branch-এ)।

## Repository-এ আগেই হয়ে যাওয়া cleanup

আগের update note-এ যে loose files সরানোর কথা ছিল, সেগুলো এই branch-এ **আগেই সরানো হয়েছে**: root-এর `ChatRepository.kt`, `ConversationScreen.kt`, `AppwriteStorageService.kt`, `app-build.gradle.kts`, `libs.versions.toml` আর নেই; পুরোনো `appwrite-functions/firebase-appwrite-bridge/`, `data/calls/`, `security/` ও placeholder test-ও নেই।

নিচের পুরোনো paths এখনো repository-তে আছে এবং এগুলো রাখা হয়েছে কেবল ইতিহাস/compatibility-র জন্য; নতুন ZIP দিয়ে replace করলে আবার চেক করুন: `functions/` (পুরোনো Firebase Functions source), `storage.rules` (legacy Firebase Storage block), `LIQUID_CHAT_UPDATE_NOTES.md`, `UPGRADE_NOTES.md`।

## কী রাখবেন

- নিজের সঠিক `app/google-services.json`।
- application ID `com.aistudio.liquidchat.vwnxkp`।
- আগের app-এর signing key; এই repo-তে original debug key আছে। Public release-এর private key/password repository-তে দেবেন না।
- Git history এবং আপনার অন্য unrelated files।
- Firebase users/chats ও Appwrite-এর existing media। Database/bucket delete করবেন না।

`local.properties`, `.env`, build folders, node_modules, service-account JSON বা private release keystore upload করবেন না।

## Backend বসানোর ক্রম

1. `APPWRITE_SETUP.md` পড়ে নতুন authenticated API ও private scheduled function configure/deploy করুন। দুইটি function-ই একই `liquid-api` source ব্যবহার করে; entry point আলাদা।
2. `scripts/migrate-profiles.md` অনুযায়ী existing users-এর public directory তৈরি করুন। Migration-এর আগে database backup রাখুন।
3. নতুন Firestore rules/indexes deploy করুন। পুরোনো app direct message writes করে থাকলে নতুন rules-এর পরে সেটি আর compatible হবে না—দুই account-এই নতুন APK লাগবে।
4. GitHub Actions repository variables-এ `LIQUID_API_URL`, `APPWRITE_ENDPOINT`, `APPWRITE_PROJECT_ID`, `APPWRITE_BUCKET_ID` দিন। API key বা Firebase service-account JSON এখানে নয়; সেগুলো server secret।
5. Actions → Build APK → Run workflow (অথবা নতুন commit push)। সফল হলে artifact থেকে APK নিন।
6. `VERIFICATION.md`-এর two-device checks শেষ করুন — বিশেষ করে Liquid Glass slider (Appearance), Save to gallery, media bubble aspect-ratio ও voice-note flow।

পুরোনো deployed Firebase Functions বা পুরোনো Appwrite bridge তখনই disable করবেন, যখন নতুন app দিয়ে send/upload/notification ঠিক কাজ করছে এবং পুরোনো clients update করেছে। পুরোনো media URLs স্বয়ংক্রিয়ভাবে Appwrite-এ migrate করা হয়নি।

## Release সীমা

এই update reference-inspired Liquid Glass UI; Apple-এর native iOS rendering-এর pixel-identical copy নয়। Auth/API configuration ছাড়া শুধু upload করলেই live messaging চালু হবে না। Signed public release এবং বাস্তব ডিভাইসের পরীক্ষা আলাদা release gate। Save to gallery Android 10 (API 29) বা তার উপরে চলে; পুরোনো ডিভাইসে স্পষ্ট error দেখায়।
