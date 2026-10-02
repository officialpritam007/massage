import {test,before,after} from 'node:test';
import {readFileSync} from 'node:fs';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from '@firebase/rules-unit-testing';
import {
  collection,
  doc,
  getDoc,
  getDocs,
  query,
  setDoc,
  updateDoc,
  where
} from 'firebase/firestore';

let env;

const conversation = {
  participantIds: ['alice', 'bob'],
  lastMessageId: '',
  lastMessageTime: 0,
  lastMessageText: '',
  lastMessageSenderId: '',
  unreadCounts: {},
  deletedFor: [],
  deletedBefore: {},
  hiddenLastFor: {},
  archivedFor: [],
  mutedFor: [],
  favoriteFor: [],
  disappearingSeconds: 0
};

const e2ee = {
  e2eeVersion: 1,
  e2eeEphemeralKey: 'ephemeral',
  e2eeCiphertext: 'ciphertext',
  e2eeContentIv: 'content-iv',
  e2eeSenderWrappedKey: 'sender-key',
  e2eeSenderWrapIv: 'sender-iv',
  e2eeRecipientWrappedKey: 'recipient-key',
  e2eeRecipientWrapIv: 'recipient-iv',
  e2eeSenderKeyId: 'alice-key',
  e2eeRecipientKeyId: 'bob-key',
  e2eeSenderPublicKey: 'alice-public',
  e2eeSignature: 'signature'
};

function baseMessage(extra = {}) {
  return {
    senderId: 'alice',
    senderName: 'Alice',
    text: '',
    type: 'TEXT',
    mediaUrl: '',
    voiceDurationSeconds: 0,
    waveform: [],
    createdAt: Date.now(),
    status: 'SENT',
    isDeleted: false,
    deletedForEveryone: false,
    hiddenFor: [],
    isEdited: false,
    isPinned: false,
    reactions: [],
    e2ee,
    ...extra
  };
}

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-liquid-chat',
    firestore: {
      host: '127.0.0.1',
      port: 8080,
      rules: readFileSync('../../firestore.rules', 'utf8')
    }
  });

  await env.withSecurityRulesDisabled(async c => {
    const db = c.firestore();
    await setDoc(doc(db, 'users/alice'), {
      uid: 'alice',
      displayName: 'Alice',
      username: 'alice',
      email: 'private@example.com',
      tokens: {},
      privacy: {}
    });
    await setDoc(doc(db, 'users/bob'), {
      uid: 'bob',
      displayName: 'Bob',
      username: 'bob',
      email: 'bob@example.com',
      tokens: {},
      privacy: {}
    });
    await setDoc(doc(db, 'directory/alice'), {
      uid: 'alice',
      displayName: 'Alice',
      username: 'alice',
      bio: '',
      e2eePublicKey: 'alice-public',
      e2eeKeyId: 'alice-key',
      isOnline: false,
      onlineVisible: true,
      lastSeenVisible: true,
      heartbeatAt: 0,
      lastSeen: 0
    });
    await setDoc(doc(db, 'directory/bob'), {
      uid: 'bob',
      displayName: 'Bob',
      username: 'bob',
      bio: '',
      e2eePublicKey: 'bob-public',
      e2eeKeyId: 'bob-key',
      isOnline: false,
      onlineVisible: true,
      lastSeenVisible: true,
      heartbeatAt: 0,
      lastSeen: 0
    });
    await setDoc(doc(db, 'conversations/pair'), conversation);
    await setDoc(doc(db, 'conversations/pair/messages/legacy'), {
      senderId: 'alice',
      text: 'Legacy message'
    });
  });
});

after(async () => env?.cleanup());

test('directory is signed-in readable and private profile is owner-only', async () => {
  const bob = env.authenticatedContext('bob').firestore();
  const alice = env.authenticatedContext('alice').firestore();
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), 'directory/alice')));
  await assertSucceeds(getDoc(doc(bob, 'directory/alice')));
  await assertFails(getDoc(doc(bob, 'users/alice')));
  await assertSucceeds(getDoc(doc(alice, 'users/alice')));
});

test('chat list arrayContains query is authorized', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const q = query(
    collection(alice, 'conversations'),
    where('participantIds', 'array-contains', 'alice')
  );
  const result = await assertSucceeds(getDocs(q));
  if (result.docs.every(d => d.id !== 'pair')) throw new Error('pair conversation missing');
});

test('profile photo accepts only the configured Cloudinary environment', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const good = 'https://res.cloudinary.com/mthzgqhv/image/upload/v1/liquid-chat/avatar.jpg';
  await assertSucceeds(updateDoc(doc(alice, 'users/alice'), {photoUrl: good}));
  await assertSucceeds(updateDoc(doc(alice, 'directory/alice'), {photoUrl: good}));
  await assertFails(updateDoc(doc(alice, 'users/alice'), {photoUrl: 'https://example.com/avatar.jpg'}));
});

test('participants can create valid E2EE text messages', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(doc(alice, 'conversations/newpair'), conversation));
  await assertSucceeds(setDoc(
    doc(alice, 'conversations/newpair/messages/m1'),
    baseMessage()
  ));
});

test('direct Cloudinary media URL is allowed and arbitrary URL is rejected', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const media = baseMessage({
    type: 'IMAGE',
    mediaUrl: 'https://res.cloudinary.com/mthzgqhv/image/upload/v1/liquid-chat/photo.jpg'
  });
  delete media.e2ee;

  await assertSucceeds(setDoc(
    doc(alice, 'conversations/pair/messages/photo-ok'),
    media
  ));

  await assertFails(setDoc(
    doc(alice, 'conversations/pair/messages/photo-bad'),
    {...media, mediaUrl: 'https://example.com/not-cloudinary.jpg'}
  ));
});

test('recipient delivery, delete-for-me and reaction updates are authorized', async () => {
  const bob = env.authenticatedContext('bob').firestore();
  await env.withSecurityRulesDisabled(async c => {
    await setDoc(
      doc(c.firestore(), 'conversations/pair/messages/e2ee'),
      baseMessage()
    );
  });

  await assertSucceeds(updateDoc(
    doc(bob, 'conversations/pair/messages/e2ee'),
    {status: 'DELIVERED'}
  ));

  await assertSucceeds(updateDoc(
    doc(bob, 'conversations/pair/messages/e2ee'),
    {hiddenFor: ['bob']}
  ));

  await assertSucceeds(updateDoc(
    doc(bob, 'conversations/pair/messages/e2ee'),
    {reactions: [{emoji: '👍', userIds: ['bob']}]}
  ));
});

test('only participants can read messages and sender identity cannot be forged', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const eve = env.authenticatedContext('eve').firestore();

  await assertSucceeds(getDoc(doc(alice, 'conversations/pair/messages/legacy')));
  await assertFails(getDoc(doc(eve, 'conversations/pair/messages/legacy')));

  await assertFails(setDoc(
    doc(alice, 'conversations/pair/messages/forged'),
    baseMessage({senderId: 'bob', senderName: 'Bob'})
  ));
});

test('typing remains owner and participant constrained', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const eve = env.authenticatedContext('eve').firestore();

  await assertSucceeds(setDoc(
    doc(alice, 'conversations/pair/typing/alice'),
    {until: Date.now() + 6000}
  ));
  await assertFails(setDoc(
    doc(alice, 'conversations/pair/typing/bob'),
    {until: Date.now() + 6000}
  ));
  await assertFails(setDoc(
    doc(eve, 'conversations/pair/typing/eve'),
    {until: Date.now() + 6000}
  ));
  await assertFails(setDoc(
    doc(alice, 'conversations/pair/typing/alice'),
    {until: Date.now() + 60000}
  ));
});

test('signed-in user can submit a constrained report but cannot spoof reporter', async () => {
  const alice = env.authenticatedContext('alice').firestore();

  await assertSucceeds(setDoc(doc(alice, 'reports/r1'), {
    reporterId: 'alice',
    reportedUid: 'bob',
    reason: 'Spam messages',
    createdAt: Date.now()
  }));

  await assertFails(setDoc(doc(alice, 'reports/r2'), {
    reporterId: 'bob',
    reportedUid: 'alice',
    reason: 'Forged report',
    createdAt: Date.now()
  }));
});
