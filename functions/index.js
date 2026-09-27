const { onDocumentCreated, onDocumentUpdated } = require('firebase-functions/v2/firestore');
const { onSchedule } = require('firebase-functions/v2/scheduler');
const admin = require('firebase-admin');

admin.initializeApp();
const db = admin.firestore();

exports.notifyNewMessage = onDocumentCreated('conversations/{conversationId}/messages/{messageId}', async (event) => {
  const message = event.data?.data();
  if (!message?.senderId) return;
  const conversation = (await db.doc(`conversations/${event.params.conversationId}`).get()).data();
  const recipients = (conversation?.participantIds || []).filter((id) => id !== message.senderId);
  const tokens = [];
  for (const uid of recipients) {
    const user = (await db.doc(`users/${uid}`).get()).data();
    if (user?.fcmToken) tokens.push(user.fcmToken);
  }
  if (!tokens.length) return;
  await admin.messaging().sendEachForMulticast({
    tokens,
    data: {
      type: 'message',
      conversationId: event.params.conversationId,
      title: 'Liquid Chat',
      body: String(message.text || 'New message').slice(0, 120)
    }
  });
});

exports.cleanupExpiredStatuses = onSchedule('every 15 minutes', async () => {
  const now = Date.now();
  const snap = await db.collection('statuses').where('expiresAt', '<=', now).limit(450).get();
  if (snap.empty) return;
  const batch = db.batch();
  snap.docs.forEach((doc) => batch.delete(doc.ref));
  await batch.commit();
});

exports.markCallExpired = onDocumentUpdated('calls/{callId}', async (event) => {
  const after = event.data?.after?.data();
  if (!after || ['ended', 'completed'].includes(after.state)) return;
  const createdAt = Number(after.createdAt || 0);
  if (createdAt && Date.now() - createdAt > 120000 && after.state === 'ringing') {
    await event.data.after.ref.update({ state: 'missed', endedAt: Date.now() });
  }
});
