# Firebase + Appwrite setup (Liquid Chat 4.0.0)

This is a new authenticated API, not the old JWT-only bridge. Both phones must use 4.0.0.
No live service has been deployed by this ZIP. Never put a service account or Appwrite API
key in Android source, `google-services.json`, or GitHub repository files.

## 1. Firebase

- Keep your existing Firebase project and Android application ID
  `com.aistudio.liquidchat.vwnxkp`.
- Enable Email/Password Authentication and create a Firestore database if needed.
- Keep the correct `app/google-services.json` for that application ID.
- Deploy the included `firestore.rules` and `firestore.indexes.json`. Wait for indexes
  to finish building. You may paste the rules in Firebase Console; indexes can be deployed
  with `firebase deploy --only firestore --project YOUR_PROJECT_ID`.
- Run the one-time profile migration in `scripts/migrate-profiles.md` for existing users.
- Create a service account for the Appwrite functions. Required capabilities are Firestore
  data access, Firebase Authentication user/token administration and FCM message sending.
  Keep its JSON only in protected Appwrite function environment variables.
- Firebase Cloud Functions and Firebase Storage are NOT used for new uploads.

## 2. Appwrite storage

Create/use your Appwrite Cloud project and a bucket:

- File security: ON.
- Bucket permissions: authenticated users may CREATE files only. Do not grant bucket-level
  read/update/delete to `any` or `users`.
- Maximum file size: 25 MB. Allow image, video, audio and document types you intend to send.
- Profile images are resized to at most 1600 pixels and JPEG compressed on Android 9+.
- Completed files lose all direct client read/update/delete permissions. Downloads use a five-minute token
  issued only after verifying Firebase identity, conversation membership and privacy.

Add the Android platform using the existing application ID.

Create a server API key with `users.read`, `users.write`, `sessions.write` if offered by your
Appwrite version, `files.read`, `files.write`, and `tokens.write`. Use only server-side.
Do not enable public downloads to solve a permission error.

## 3. Deploy the API function

Create an Appwrite Function with a Node.js 22 runtime:

- Repository: your existing GitHub repository, branch `main`.
- Root directory: `appwrite-functions/liquid-api`.
- Entry point: `src/main.js`.
- Build command: `npm ci`.
- Execution permissions: Any (the handler independently verifies every Firebase ID token).
- Timeout: 120 seconds or the highest supported value on your plan.
- Create an HTTP domain for this function.

Environment variables:

| Name | Value |
|---|---|
| `FIREBASE_SERVICE_ACCOUNT_JSON` | Entire Firebase service account JSON, protected/secret |
| `APPWRITE_API_KEY` | Server API key, protected/secret |
| `APPWRITE_ENDPOINT` | Your regional endpoint, e.g. `https://sgp.cloud.appwrite.io/v1` |
| `APPWRITE_PROJECT_ID` | Your project ID |
| `APPWRITE_BUCKET_ID` | Your bucket ID |

Use the NEW API function domain as `LIQUID_API_URL`. Do not point the app to the old
`firebase-appwrite-bridge` domain unless that function has actually been redeployed with
this new code and environment.

A request without a Firebase bearer token must return `401 Missing Firebase ID token`.
That response is expected security behaviour. The app obtains a real Firebase token after
login and supplies it automatically; an Appwrite execution console cannot invent it.

## 4. Cleanup and notification retries

Create a SECOND function from the same root directory and environment:

- Entry point: `src/scheduled.js`.
- Schedule: every 15 minutes (`*/15 * * * *`).
- Execute permissions: EMPTY; no public HTTP domain.
- Build command: `npm ci`.

This handles expired messages and pending notification delivery. Do not expose this entry
point publicly. Cleanup processes bounded batches and may take multiple runs after a large
backlog. Reports are stored in the private Firestore `reports` collection for your review;
there is no automatic moderation service.

## 5. GitHub repository variables

Settings → Secrets and variables → Actions → Variables → New repository variable:

- `LIQUID_API_URL`: deployed API function HTTPS domain
- `APPWRITE_ENDPOINT`
- `APPWRITE_PROJECT_ID`
- `APPWRITE_BUCKET_ID`

These are public configuration identifiers, not server keys. The workflow refuses to
publish a misconfigured APK when these values are missing.

## 6. Verification with two accounts

Install the new APK on both phones. Register/sign in with different accounts.
Verify text, reply, typing, read receipts, camera/gallery, voice, file, favorites/archive,
notification tap, reconnect/restart and blocking. Check Appwrite execution logs for any
failure. Do not claim production readiness from a build-success badge alone.

## Free-plan limitations

Firebase Auth (email/password), Firestore and FCM can be used within their no-cost quotas.
Appwrite Free has storage, bandwidth, execution and inactivity limits. This is not an
unlimited or SLA-backed free production service. Presence heartbeat is throttled to about
25 seconds; offline state expires after about 45 seconds. Typing expires in about 6 seconds.
The new architecture avoids a Firebase Storage/Cloud Functions billing dependency, but
existing billed resources must be reviewed separately in your consoles.

Official pricing references (checked 29 September 2026):
- https://firebase.google.com/pricing
- https://appwrite.io/docs/advanced/billing/free
- https://appwrite.io/pricing
