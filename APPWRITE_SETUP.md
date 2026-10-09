# Appwrite Free Tier Setup

Liquid Chat uses an Appwrite Cloud Free project to provide backend functions for FCM push notifications and a secure S3-compatible private media storage gateway.

## Creating the Project

1. Sign up for [Appwrite Cloud](https://cloud.appwrite.io) and create a project (e.g. "Liquid Chat Backend").
2. Under **Storage**, create a new bucket (e.g. `liquid-chat-media`). 
   - Note the **Bucket ID**.
   - Keep permissions completely empty (Private).
3. Under **Overview > Settings > API Keys**, create a new API key named "Backend Functions" with these scopes:
   - `files.read`, `files.write` (if using standard SDK for other things)

## Functions Configuration

Create two functions using the Node.js runtime (e.g., Node.js 20.0).

### 1. FCM Sender (`appwrite/functions/fcm-sender`)
This function validates the Firebase ID token of the sender and dispatches FCM messages.

**Environment Variables:**
- `FIREBASE_SERVICE_ACCOUNT`: The JSON string of your Firebase Admin SDK service account key.
  *(Go to Firebase Console -> Project Settings -> Service Accounts -> Generate new private key. Minify the JSON into a single line string).*

**Deployment:**
```bash
cd appwrite/functions/fcm-sender
npm install
appwrite functions createDeployment --functionId <your_function_id> --activate true --entrypoint "src/main.js" --code "."
```

### 2. Storage Gateway (`appwrite/functions/storage-gateway`)
This function generates AWS S3-compatible presigned URLs for uploading and downloading media, enforcing Firestore security and conversation membership.

**Environment Variables:**
- `FIREBASE_SERVICE_ACCOUNT`: (Same as above)
- `APPWRITE_ENDPOINT`: `https://cloud.appwrite.io/v1`
- `APPWRITE_PROJECT_ID`: Your Appwrite project ID.
- `APPWRITE_API_KEY`: Your generated Appwrite API key.
- `APPWRITE_BUCKET_ID`: The Bucket ID you created.

**Deployment:**
```bash
cd appwrite/functions/storage-gateway
npm install
appwrite functions createDeployment --functionId <your_function_id> --activate true --entrypoint "src/main.js" --code "."
```

## Android App Environment Variables

In your Android project's `.env` file (copied from `.env.example`), define:
```
APPWRITE_FUNCTION_FCM_ENDPOINT=https://<your-fcm-function-domain>.appwrite.global/
APPWRITE_FUNCTION_STORAGE_ENDPOINT=https://<your-storage-gateway-domain>.appwrite.global/
```
*(Do not include the Appwrite API key or Firebase Service Account in the Android `.env` file!)*