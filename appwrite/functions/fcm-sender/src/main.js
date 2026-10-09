const { Client, Users } = require('node-appwrite');
const admin = require('firebase-admin');

// Ensure Firebase is initialized only once
if (!admin.apps.length) {
    admin.initializeApp({
        credential: admin.credential.cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT))
    });
}

module.exports = async ({ req, res, log, error }) => {
    // Only accept POST
    if (req.method !== 'POST') {
        return res.json({ success: false, message: 'Method not allowed' }, 405);
    }

    try {
        const payload = JSON.parse(req.body);
        const { senderUid, conversationId, messageId, text, type } = payload;

        if (!senderUid || !conversationId || !messageId) {
            return res.json({ success: false, message: 'Missing required fields' }, 400);
        }

        // Validate sender token via Appwrite Headers if needed, or rely on Firebase JWT
        // In this architecture, Firebase is Auth, Appwrite is Storage/Functions.
        // The client should send their Firebase ID token in the authorization header.
        const authHeader = req.headers['authorization'];
        if (!authHeader || !authHeader.startsWith('Bearer ')) {
            return res.json({ success: false, message: 'Missing auth token' }, 401);
        }

        const idToken = authHeader.split('Bearer ')[1];
        const decodedToken = await admin.auth().verifyIdToken(idToken);

        if (decodedToken.uid !== senderUid) {
            return res.json({ success: false, message: 'Unauthorized sender' }, 403);
        }

        const db = admin.firestore();

        // 1. Verify the message actually exists in Firestore and was sent by senderUid
        const messageRef = db.doc(`conversations/${conversationId}/messages/${messageId}`);
        const messageSnap = await messageRef.get();

        if (!messageSnap.exists) {
            return res.json({ success: false, message: 'Message not found' }, 404);
        }

        const messageData = messageSnap.data();
        if (messageData.senderId !== senderUid) {
            return res.json({ success: false, message: 'Message sender mismatch' }, 403);
        }

        // 2. Find the other participant in the conversation
        const convRef = db.doc(`conversations/${conversationId}`);
        const convSnap = await convRef.get();

        if (!convSnap.exists) {
            return res.json({ success: false, message: 'Conversation not found' }, 404);
        }

        const convData = convSnap.data();
        const participantIds = convData.participantIds || [];
        const recipientUid = participantIds.find(uid => uid !== senderUid);

        if (!recipientUid) {
            return res.json({ success: false, message: 'Recipient not found' }, 404);
        }

        // 3. Check if recipient has blocked sender or vice versa
        const recipientRef = db.doc(`users/${recipientUid}`);
        const recipientSnap = await recipientRef.get();
        if (recipientSnap.exists) {
            const blockedUserIds = recipientSnap.data().blockedUserIds || [];
            if (blockedUserIds.includes(senderUid)) {
                return res.json({ success: true, message: 'Sender blocked, no notification sent' });
            }
        }

        const senderRef = db.doc(`users/${senderUid}`);
        const senderSnap = await senderRef.get();
        if (senderSnap.exists) {
            const senderBlockedUserIds = senderSnap.data().blockedUserIds || [];
            if (senderBlockedUserIds.includes(recipientUid)) {
                 return res.json({ success: true, message: 'Recipient blocked by sender' });
            }
        }

        // Check conversation mute status
        if ((convData.mutedFor || []).includes(recipientUid)) {
            return res.json({ success: true, message: 'Conversation muted' });
        }

        // 4. Fetch recipient's FCM tokens
        const devicesRef = db.collection(`users/${recipientUid}/devices`);
        const devicesSnap = await devicesRef.get();
        const tokens = devicesSnap.docs.map(doc => doc.data().token).filter(t => !!t);

        if (tokens.length === 0) {
            return res.json({ success: true, message: 'No devices found for recipient' });
        }

        // 5. Send FCM Notification
        const notificationText = text || (type ? `Sent a ${type.toLowerCase()}` : 'New message');

        const payloadFcm = {
            notification: {
                title: senderSnap.exists ? senderSnap.data().displayName || 'User' : 'New Message',
                body: notificationText,
            },
            data: {
                conversationId: conversationId,
                messageId: messageId,
                type: 'NEW_MESSAGE'
            },
            tokens: tokens
        };

        const response = await admin.messaging().sendMulticast(payloadFcm);

        // 6. Cleanup invalid tokens
        if (response.failureCount > 0) {
            const failedTokens = [];
            response.responses.forEach((resp, idx) => {
                if (!resp.success) {
                    if (resp.error.code === 'messaging/invalid-registration-token' ||
                        resp.error.code === 'messaging/registration-token-not-registered') {
                        failedTokens.push(tokens[idx]);
                    }
                }
            });

            if (failedTokens.length > 0) {
                const batch = db.batch();
                devicesSnap.docs.forEach(doc => {
                    if (failedTokens.includes(doc.data().token)) {
                        batch.delete(doc.ref);
                    }
                });
                await batch.commit();
            }
        }

        return res.json({ success: true, sentCount: response.successCount });
    } catch (err) {
        error('Error sending push: ' + err.message);
        return res.json({ success: false, message: 'Internal error' }, 500);
    }
};
