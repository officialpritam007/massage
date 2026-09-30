# Liquid Chat — Free-tier hybrid architecture

Liquid Chat uses Firebase and Appwrite for different responsibilities so the same workload is not duplicated across providers.

## Firebase

- Firebase Authentication: account/session identity.
- Cloud Firestore: 1-to-1 conversation metadata, realtime message snapshots, presence and typing state.
- Firebase Cloud Messaging: push delivery.
- Firebase App Check: abuse protection for supported Firebase client traffic.
- Firebase Storage is intentionally not used by the Android client.
- Firebase Cloud Functions are not required for the core chat backend.

## Appwrite

- Appwrite Function `liquid-api`: authenticated server-authoritative chat mutations and profile/media operations.
- Appwrite Storage: image, video, voice and file payloads.
- Private media is resolved through short-lived authorized access; permanent deletion revokes/removes media server-side.

## Free-tier guardrails

1. Only the open conversation should keep a message + typing listener active. The chat list itself relies on the conversation summary listener.
2. Initial message history is intentionally bounded and older messages are loaded incrementally.
3. Presence writes are heartbeat-based while foregrounded; moving to background writes offline immediately.
4. Typing writes stay throttled and expire automatically.
5. Large binary payloads never go into Firestore. Firestore stores metadata and Appwrite media identifiers/URLs only.
6. Do not add Firebase Storage or Firebase Functions dependencies unless the architecture is deliberately changed.
7. Server secrets (Firebase Admin service account and Appwrite API key) must never be committed to the Android app or public repository.

## Security boundary

Android reads permitted Firestore data under `firestore.rules`. Conversation/message mutations go through the authenticated Appwrite API, which verifies the Firebase ID token and participant authorization. Appwrite credentials and Firebase Admin credentials remain server-side.
