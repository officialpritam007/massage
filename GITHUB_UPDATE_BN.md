# GitHub update — Liquid Chat 4.2.0

বর্তমান architecture:

**Firebase Auth + Cloud Firestore + Cloudinary direct upload**

Firebase Functions, Appwrite এবং Firebase Storage runtime থেকে বাদ দেওয়া হয়েছে।

## GitHub variables

চাইলে repository variables-এ দিন:

- `CLOUDINARY_CLOUD_NAME=mthzgqhv`
- `CLOUDINARY_UPLOAD_PRESET=liquid_chat_unsigned`

এগুলো secret নয়।

## Cloudinary-তে একবার যা করতে হবে

Cloudinary → Settings → Upload → Upload presets → Add upload preset:

- Signing mode: **Unsigned**
- Preset name: **liquid_chat_unsigned**
- Asset folder: **liquid-chat**
- Allowed formats সীমিত রাখুন

API Secret কখনও GitHub বা APK-তে দেবেন না।

## Build

Actions → **Build Android APK** → Run workflow.

Workflow Firestore rules test, Android unit test এবং APK build করবে।

## গুরুত্বপূর্ণ limitation

Free backendless mode-এ killed-app push notification guarantee করা যায় না। Media public Cloudinary delivery URL ব্যবহার করে। Delete-for-everyone Firestore message সরায়, কিন্তু trusted Cloudinary Admin backend না থাকায় remote Cloudinary object delete guarantee করা যায় না।
