# Existing account migration

The new app reads searchable profiles from `directory`, not the private `users` collection.
Each user is migrated when signing in. To make every existing contact searchable before
launch, run the supplied migration once from a trusted computer:

1. In `appwrite-functions/liquid-api`, run `npm ci`.
2. Set `FIREBASE_SERVICE_ACCOUNT_JSON` to the JSON contents of your service account using
   your shell's environment-variable mechanism. Do not commit this JSON or paste it into chat.
3. Run `node src/migrate.js`.
4. The script copies public name, username, bio and photo reference; it preserves chats.
   Duplicate/invalid usernames receive a stable `user_...` name.
5. Deploy `firestore.rules` and `firestore.indexes.json`, then distribute the new APK.

The old app will no longer be able to send messages with these rules. Upgrade both phones.
Old Firebase Storage media is left intact; do not delete its bucket or downgrade billing
until any desired media has been separately exported. This package moves NEW uploads to
Appwrite and does not automatically copy old Firebase media.
