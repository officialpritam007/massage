# Liquid Chat — Firebase + Appwrite configuration

## Architecture used by this project

- Firebase Authentication: email/password identity
- Cloud Firestore: users, conversations, messages, status metadata, presence, typing, call signaling
- Firebase Cloud Messaging: push notifications
- Firebase Cloud Functions: notification + cleanup jobs
- Appwrite Storage: chat media, status media and profile photos
- Appwrite Function `firebase-appwrite-bridge`: validates Firebase ID tokens, mirrors Firebase users, issues Appwrite JWTs and creates file access tokens

No Appwrite secret/API key is stored in the Android APK.

## IDs currently wired into the Android source

- Appwrite endpoint: `https://sgp.cloud.appwrite.io/v1`
- Appwrite project ID: `6abae44b0030a4b3c0b4`
- Appwrite bucket ID: `6abae4e300352b37c209`
- Firebase project in `google-services.json`: `liquid-chat-v2`
- Android application ID/package: `com.aistudio.liquidchat.vwnxkp`

If you create a different Appwrite project/bucket, edit the constants at the top of:

`app/src/main/java/com/example/data/repository/AppwriteStorageService.kt`

## A. Firebase Console

### 1. Android app

Create/select Firebase project, then add Android app with package:

`com.aistudio.liquidchat.vwnxkp`

Download `google-services.json` and replace:

`app/google-services.json`

### 2. Authentication

Firebase Console -> Authentication -> Sign-in method -> Email/Password -> Enable.

### 3. Firestore

Create a Cloud Firestore database. Then deploy the repository rules and indexes:

- `firestore.rules`
- `firestore.indexes.json`

### 4. Cloud Messaging

FCM is already wired in the Android app. Each signed-in device stores its token in `users/{uid}.fcmToken`.

### 5. Firebase Functions

The `functions/` directory contains notification and cleanup functions. Deploy them with Firebase CLI/GitHub deployment when ready.

## B. Appwrite Console

### 1. Android platform

Add an Android platform with package:

`com.aistudio.liquidchat.vwnxkp`

### 2. Storage bucket

Create/select bucket ID:

`6abae4e300352b37c209`

Recommended settings:

- File Security: ON
- Bucket CREATE permission: Users
- Do not grant bucket-level READ/UPDATE/DELETE

The Android client grants file-level read/write only to the mirrored owner. Chat/status/profile URLs use Appwrite resource tokens so the existing image/video UI can load them without shipping an Appwrite secret.

### 3. Bridge Function

Create an Appwrite Function:

- Name: `Firebase Appwrite Bridge`
- Function ID: `firebase-appwrite-bridge`
- Runtime: Node.js 22
- Entrypoint: `src/main.js`
- Root directory for Git deployment: `appwrite-functions/firebase-appwrite-bridge`
- Execute access: Any (the function itself validates the Firebase ID token)

Function scopes required:

- `users.read`
- `users.write`
- `files.read`
- `tokens.write`

Environment variables:

- `FIREBASE_WEB_API_KEY` = Firebase Web API key
- `APPWRITE_BUCKET_ID` = `6abae4e300352b37c209`

Appwrite injects the project context and ephemeral function API key automatically.

After changing variables, redeploy the function.

### 4. Function URL

Appwrite Console -> Functions -> `firebase-appwrite-bridge` -> Domains.

Copy the generated `https://....appwrite.run` URL and replace:

`REPLACE_WITH_APPWRITE_FUNCTION_URL`

inside:

`app/src/main/java/com/example/data/repository/AppwriteStorageService.kt`

## C. Quick end-to-end test

1. Build/install debug APK.
2. Register two Firebase users on two devices/accounts.
3. Sign in on both.
4. Send a text message; verify it appears through Firestore.
5. Send an image; verify a new file appears in the Appwrite bucket and the image opens in chat.
6. Post a media status and upload a profile photo.
7. Verify each user document contains `fcmToken`, `isOnline` and `lastSeen`.

## Important

Do not put Firebase service-account JSON or Appwrite API keys into the Android project or GitHub repository.
