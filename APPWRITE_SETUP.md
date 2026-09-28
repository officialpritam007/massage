# Liquid Chat — Firebase Auth + Appwrite Storage Bridge

This patch keeps Firebase Auth/Firestore/FCM and replaces Firebase Storage with Appwrite Storage.

## IDs already configured

- Appwrite endpoint: `https://sgp.cloud.appwrite.io/v1`
- Appwrite project: `6abae44b0030a4b3c0b4`
- Appwrite bucket: `6abae4e300352b37c209`
- Firebase project: `liquid-chat-v2`
- Android package: `com.aistudio.liquidchat.vwnxkp`

## 1. Appwrite bucket permissions

Open:
`Storage → Liquid Chat Media → Settings → Permissions`

Keep **File Security = ON**.

At bucket level grant **CREATE** to **Users** only.

Do NOT grant bucket-level READ/UPDATE/DELETE. File-level permissions will control who can read each file.

## 2. Create the Firebase/Appwrite bridge Function

In Appwrite:
`Functions → Create function`

Use:
- Name: `Firebase Appwrite Bridge`
- Function ID: `firebase-appwrite-bridge`
- Runtime: Node.js 22
- Execute access: `Any`
- Entrypoint: `src/main.js`
- Build command: `npm install`

If using GitHub deployment, point the function root directory to:
`appwrite-functions/firebase-appwrite-bridge`

Deploy the function.

## 3. Function scopes

Function → Settings → Scopes:
- `users.read`
- `users.write`

Do not give broader scopes.

Appwrite provides the function's ephemeral API key automatically; it is not put into the Android app.

## 4. Function environment variable

Add:

`FIREBASE_WEB_API_KEY = <Firebase Web API Key>`

You can find the Web API Key in:
Firebase Console → Project settings → General → Your apps / Web API Key.

The function uses Firebase's `accounts:lookup` REST endpoint to validate the Firebase ID token.

## 5. Copy the generated Function URL

After deployment, open:
`Functions → firebase-appwrite-bridge → Domains`

Copy the generated `https://....appwrite.run` URL.

In:
`app/src/main/java/com/example/data/repository/AppwriteStorageService.kt`

replace:

`REPLACE_WITH_APPWRITE_FUNCTION_URL`

with the generated URL.

## 6. What the code does

1. Android signs in with Firebase.
2. Android obtains the Firebase ID token.
3. The Appwrite Function validates that token.
4. The Function creates/gets an Appwrite user whose ID is the same Firebase UID.
5. The Function returns a short-lived Appwrite JWT.
6. Android uses that JWT with Appwrite Storage.
7. Each uploaded file receives read permission for the Firebase conversation participants and write permission for the sender.
8. Firestore stores the Appwrite file-view URL in `mediaUrl`.

No Appwrite secret API key is shipped in the APK.

## 7. Android files changed by this patch

- `ChatRepository.kt`
- `AppwriteStorageService.kt` (new)
- `ConversationScreen.kt`
- `app/build.gradle.kts`
- `gradle/libs.versions.toml`

Firebase Storage dependency is removed.
