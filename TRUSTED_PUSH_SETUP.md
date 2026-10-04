# Trusted killed-app push setup

Liquid Chat registers private FCM device tokens and can receive data-only FCM messages. Reliable delivery when the Android process is fully killed still requires a trusted sender outside the APK.

## Security boundary

Never put a Firebase service-account JSON, OAuth access token, FCM server credential, Cloudinary API secret, or GitHub token in the Android app. A modified APK could extract it and send arbitrary notifications or access privileged services.

The trusted sender must use FCM HTTP v1 with server-side credentials and send only identifiers:

```json
{
  "message": {
    "token": "<recipient-device-token>",
    "data": {
      "recipient_id": "<recipient-uid>",
      "conversation_id": "<conversation-id>",
      "message_id": "<message-id>",
      "sender_id": "<sender-uid>"
    },
    "android": {
      "priority": "high"
    }
  }
}
```

The Android receiver intentionally re-reads the authenticated Firestore conversation/message before showing a notification. Do not send plaintext message bodies in the FCM payload.

## Trusted sender checks

Before sending, a backend should verify:

1. the caller is authenticated and is the message sender;
2. the message exists in the stated conversation;
3. sender and recipient are the two participants;
4. neither side has blocked the other;
5. the recipient account is active;
6. each target token came from `users/<uid>/devices/<installationId>`;
7. only the recipient's active device tokens receive the message.

## Current deployment state

No always-on trusted push runtime is deployed by this repository. GitHub Actions is suitable for builds, tests and maintenance, but it is not an instant per-message runtime. Therefore the repository does not claim WhatsApp-like killed-process push until a trusted always-on sender is deployed.

The existing Android FCM receiver/token storage can be kept unchanged when that backend is added.
