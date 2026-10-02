# GitHub update — Liquid Chat 4.1.1

এই repository-তে current runtime architecture হলো **Firebase + Cloudinary**। পুরনো Appwrite backend source আর ব্যবহার করা হবে না।

## Current source of truth

- Android app: `app/`
- Firebase backend: `firebase-functions/liquid-api/`
- Firestore rules: `firestore.rules`
- Cloudinary setup: `CLOUDINARY_SETUP.md`
- APK build: `.github/workflows/build-apk.yml`
- Firebase Function deploy: `.github/workflows/deploy-firebase-function.yml`
- Firestore rules deploy: `.github/workflows/deploy-firebase-rules.yml`

## GitHub configuration

Repository variable:

- `LIQUID_API_URL` → deployed Firebase `liquidApi` HTTPS URL.

Repository secret:

- `FIREBASE_SERVICE_ACCOUNT_JSON` → service account JSON used only by the deployment workflow.

Cloudinary API secret GitHub-এ দেবেন না।

## Firebase Functions secrets

Firebase project `liquid-chat-v2`-এ একবার configure করুন:

```bash
firebase functions:secrets:set CLOUDINARY_CLOUD_NAME
firebase functions:secrets:set CLOUDINARY_API_KEY
firebase functions:secrets:set CLOUDINARY_API_SECRET
```

এরপর `main` branch-এ backend change merge হলে Firebase Function deployment workflow চলবে।

## Media limit

Cloudinary Free plan-এর connected account-এ raw asset limit 10 MB। Encrypted chat attachment raw asset হিসেবে যায়, তাই Android client limit 9 MB রাখা হয়েছে যাতে encryption overhead-এর পরেও upload safe থাকে।

## Release check

1. GitHub Actions-এর Android build সবুজ হতে হবে।
2. Firebase Function deployment সবুজ হতে হবে।
3. দুইটি real account/device দিয়ে text + photo + voice/video media + delivered/read + background notification test করতে হবে।
4. Cloudinary Media Library-তে `liquid-chat/` asset তৈরি হচ্ছে কিনা verify করতে হবে।
5. Delete-for-everyone/profile-photo replacement-এর পরে Cloudinary asset cleanup verify করতে হবে।

Private release keystore, service-account JSON, Cloudinary API secret বা local `.env` repository-তে commit করবেন না।
