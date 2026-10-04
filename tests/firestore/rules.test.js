import {test,before,after} from 'node:test';
import {readFileSync} from 'node:fs';
import {deepStrictEqual, strictEqual} from 'node:assert';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from '@firebase/rules-unit-testing';
import {
  collection,
  arrayUnion,
  doc,
  deleteDoc,
  deleteField,
  getDoc,
  getDocs,
  increment,
  query,
  runTransaction,
  setDoc,
  serverTimestamp,
  updateDoc,
  where,
  writeBatch
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
    text: 'Hello',
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

test('profile photo privacy cannot leave a public directory photo behind', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const good = 'https://res.cloudinary.com/mthzgqhv/image/upload/v1/liquid-chat/avatar-private.jpg';

  await assertSucceeds(updateDoc(doc(alice, 'users/alice'), {
    privacy: {profilePhotoVisibility: 'Everyone'}
  }));
  await assertSucceeds(updateDoc(doc(alice, 'directory/alice'), {photoUrl: good}));

  await assertFails(updateDoc(doc(alice, 'users/alice'), {
    privacy: {profilePhotoVisibility: 'Nobody'}
  }));

  const hide = writeBatch(alice);
  hide.update(doc(alice, 'users/alice'), {
    privacy: {profilePhotoVisibility: 'Nobody'}
  });
  hide.update(doc(alice, 'directory/alice'), {photoUrl: deleteField()});
  await assertSucceeds(hide.commit());

  await assertFails(updateDoc(doc(alice, 'directory/alice'), {photoUrl: good}));

  const show = writeBatch(alice);
  show.update(doc(alice, 'users/alice'), {
    privacy: {profilePhotoVisibility: 'Everyone'}
  });
  show.update(doc(alice, 'directory/alice'), {photoUrl: good});
  await assertSucceeds(show.commit());
});

test('legacy public presence can be stripped while private presence remains usable', async () => {
  await env.withSecurityRulesDisabled(async c => {
    const db = c.firestore();
    await updateDoc(doc(db, 'users/alice'), {
      photoUrl: 'https://legacy.example.com/avatar.jpg'
    });
    await updateDoc(doc(db, 'directory/alice'), {
      photoUrl: 'https://legacy.example.com/avatar.jpg'
    });
  });

  const alice = env.authenticatedContext('alice').firestore();

  await assertSucceeds(updateDoc(
    doc(alice, 'users/alice'),
    {installationId: 'install-123'}
  ));

  await assertSucceeds(updateDoc(
    doc(alice, 'directory/alice'),
    {
      isOnline: deleteField(),
      onlineVisible: deleteField(),
      lastSeenVisible: deleteField(),
      heartbeatAt: deleteField(),
      lastSeen: deleteField()
    }
  ));

  const now = Date.now();
  await assertSucceeds(setDoc(
    doc(alice, 'conversations/pair/presence/alice'),
    {
      isOnline: true,
      onlineVisible: true,
      lastSeenVisible: true,
      heartbeatAt: now,
      lastSeen: now
    }
  ));

  await assertFails(updateDoc(
    doc(alice, 'users/alice'),
    {photoUrl: 'https://legacy.example.com/new-avatar.jpg'}
  ));
});

test('legacy owner without a public directory can repair it and start a conversation', async () => {
  await env.withSecurityRulesDisabled(async c => {
    const db = c.firestore();
    await setDoc(doc(db, 'users/legacy'), {
      uid: 'legacy',
      displayName: 'Legacy',
      username: 'legacy',
      bio: ''
    });
  });

  const legacy = env.authenticatedContext('legacy').firestore();
  await assertSucceeds(setDoc(doc(legacy, 'directory/legacy'), {
    uid: 'legacy',
    displayName: 'Legacy',
    username: 'legacy',
    bio: ''
  }));

  await assertSucceeds(setDoc(doc(legacy, 'conversations/legacy-bob'), {
    ...conversation,
    participantIds: ['legacy', 'bob']
  }));
});

test('conversation bootstrap merge is non destructive and supports first send', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const cid = 'bootstrap-send';

  await assertSucceeds(setDoc(
    doc(alice, `conversations/${cid}`),
    {participantIds: ['alice', 'bob']},
    {merge: true}
  ));

  const send = writeBatch(alice);
  send.set(doc(alice, `conversations/${cid}/messages/m1`), baseMessage());
  send.update(doc(alice, `conversations/${cid}`), {
    lastMessageId: 'm1',
    lastMessageSenderId: 'alice',
    lastMessageText: 'Hello',
    lastMessageTime: Date.now(),
    lastMessageRevision: Date.now(),
    lastMessageType: 'TEXT',
    'unreadCounts.bob': increment(1)
  });
  await assertSucceeds(send.commit());

  await assertSucceeds(setDoc(
    doc(alice, `conversations/${cid}`),
    {participantIds: ['alice', 'bob']},
    {merge: true}
  ));

  const saved = await getDoc(doc(alice, `conversations/${cid}`));
  strictEqual(saved.data().lastMessageId, 'm1');
  strictEqual(saved.data().unreadCounts.bob, 1);
});

test('a stale local conversation can be server-rebootstrapped before a retry send', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const cid = 'stale-rebootstrap';

  await env.withSecurityRulesDisabled(async c => {
    const admin = c.firestore();
    await setDoc(doc(admin, `conversations/${cid}`), conversation);
    await deleteDoc(doc(admin, `conversations/${cid}`));
  });

  await assertSucceeds(setDoc(
    doc(alice, `conversations/${cid}`),
    {participantIds: ['alice', 'bob']},
    {merge: true}
  ));

  const send = writeBatch(alice);
  send.set(doc(alice, `conversations/${cid}/messages/retry-1`), baseMessage());
  send.update(doc(alice, `conversations/${cid}`), {
    lastMessageId: 'retry-1',
    lastMessageSenderId: 'alice',
    lastMessageText: 'Hello',
    lastMessageTime: Date.now(),
    lastMessageRevision: Date.now(),
    lastMessageType: 'TEXT',
    'unreadCounts.bob': increment(1)
  });
  await assertSucceeds(send.commit());
});

test('participants can create valid plaintext text messages', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(doc(alice, 'conversations/newpair'), conversation));
  await assertSucceeds(setDoc(
    doc(alice, 'conversations/newpair/messages/m1'),
    baseMessage()
  ));
});

test('new text writes reject empty bodies and encrypted envelopes', async () => {
  const alice = env.authenticatedContext('alice').firestore();

  await assertFails(setDoc(
    doc(alice, 'conversations/pair/messages/empty-plaintext'),
    baseMessage({text: ''})
  ));

  await assertFails(setDoc(
    doc(alice, 'conversations/pair/messages/new-encrypted'),
    {...baseMessage({text: ''}), e2ee}
  ));
});

test('atomic message send batch can update plaintext preview and unread count', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const cid = 'atomic-send';
  await assertSucceeds(setDoc(doc(alice, `conversations/${cid}`), conversation));

  const send = writeBatch(alice);
  send.set(doc(alice, `conversations/${cid}/messages/m1`), baseMessage());
  send.update(doc(alice, `conversations/${cid}`), {
    lastMessageId: 'm1',
    lastMessageSenderId: 'alice',
    lastMessageText: 'Hello',
    lastMessageTime: Date.now(),
    lastMessageRevision: Date.now(),
    lastMessageType: 'TEXT',
    'unreadCounts.bob': increment(1)
  });
  await assertSucceeds(send.commit());

  const saved = await getDoc(doc(alice, `conversations/${cid}`));
  strictEqual(saved.data().lastMessageId, 'm1');
  strictEqual(saved.data().unreadCounts.bob, 1);
});

test('direct Cloudinary media URL is allowed and arbitrary URL is rejected', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const media = baseMessage({
    type: 'IMAGE',
    mediaUrl: 'https://res.cloudinary.com/mthzgqhv/image/upload/v1/liquid-chat/photo.jpg'
  });
  await assertSucceeds(setDoc(
    doc(alice, 'conversations/pair/messages/photo-ok'),
    media
  ));

  await assertFails(setDoc(
    doc(alice, 'conversations/pair/messages/photo-bad'),
    {...media, mediaUrl: 'https://example.com/not-cloudinary.jpg'}
  ));
});

test('disappearing messages require a future bounded expiry and can be deleted after expiry', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const bob = env.authenticatedContext('bob').firestore();
  const createdAt = Date.now();

  await assertFails(setDoc(
    doc(alice, 'conversations/pair/messages/already-expired'),
    baseMessage({createdAt, expiresAt: createdAt - 1})
  ));

  await assertSucceeds(setDoc(
    doc(alice, 'conversations/pair/messages/future-expiry'),
    baseMessage({createdAt, expiresAt: createdAt + 60_000})
  ));

  await env.withSecurityRulesDisabled(async c => {
    await updateDoc(
      doc(c.firestore(), 'conversations/pair/messages/future-expiry'),
      {expiresAt: Date.now() - 1}
    );
  });

  await assertSucceeds(deleteDoc(
    doc(bob, 'conversations/pair/messages/future-expiry')
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

test('delete-for-me atomically hides the latest message and its preview, and safely retries', async () => {
  const cid = 'delete-latest';
  await env.withSecurityRulesDisabled(async c => {
    await setDoc(doc(c.firestore(), `conversations/${cid}`), {
      ...conversation, lastMessageId: 'last', lastMessageText: 'Hello'
    });
    await setDoc(doc(c.firestore(), `conversations/${cid}/messages/last`), baseMessage());
  });
  const bob = env.authenticatedContext('bob').firestore();
  const messageRef = doc(bob, `conversations/${cid}/messages/last`);
  const chatRef = doc(bob, `conversations/${cid}`);
  const hide = () => runTransaction(bob, async tx => {
    const message = await tx.get(messageRef);
    const chat = await tx.get(chatRef);
    if (!message.exists()) return;
    if (!message.data().hiddenFor.includes('bob')) {
      tx.update(messageRef, {hiddenFor: arrayUnion('bob')});
    }
    if (chat.data().lastMessageId === 'last') {
      tx.update(chatRef, {'hiddenLastFor.bob': 'last'});
    }
  });
  await assertSucceeds(hide());
  await assertSucceeds(hide());
  deepStrictEqual((await getDoc(messageRef)).data().hiddenFor, ['bob']);
  strictEqual((await getDoc(chatRef)).data().hiddenLastFor.bob, 'last');
  strictEqual((await getDoc(chatRef)).data().lastMessageId, 'last');
  const alice = env.authenticatedContext('alice').firestore();
  strictEqual((await getDoc(doc(alice, `conversations/${cid}`))).data().hiddenLastFor.alice, undefined);
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

test('block is enforced server-side for messages, typing and new conversations', async () => {
  await env.withSecurityRulesDisabled(async c => {
    const db = c.firestore();
    await updateDoc(doc(db, 'users/alice'), {blockedUserIds: ['bob']});
  });

  const bob = env.authenticatedContext('bob').firestore();
  await assertFails(setDoc(
    doc(bob, 'conversations/pair/messages/blocked-send'),
    baseMessage({senderId: 'bob', senderName: 'Bob'})
  ));
  await assertFails(setDoc(
    doc(bob, 'conversations/pair/typing/bob'),
    {until: Date.now() + 6000}
  ));
  await assertFails(setDoc(
    doc(bob, 'conversations/pair/presence/bob'),
    {
      isOnline: true,
      onlineVisible: true,
      lastSeenVisible: true,
      heartbeatAt: Date.now(),
      lastSeen: Date.now()
    }
  ));
  await assertFails(setDoc(
    doc(bob, 'conversations/blocked-new'),
    {...conversation, participantIds: ['alice', 'bob']}
  ));

  await env.withSecurityRulesDisabled(async c => {
    await updateDoc(doc(c.firestore(), 'users/alice'), {blockedUserIds: []});
  });
});

test('private presence hides heartbeat and last-seen when visibility is disabled', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const bob = env.authenticatedContext('bob').firestore();

  await assertSucceeds(setDoc(
    doc(alice, 'conversations/pair/presence/alice'),
    {
      isOnline: false,
      onlineVisible: false,
      lastSeenVisible: false,
      heartbeatAt: 0,
      lastSeen: 0
    }
  ));
  await assertSucceeds(getDoc(doc(bob, 'conversations/pair/presence/alice')));

  await assertFails(setDoc(
    doc(alice, 'conversations/pair/presence/alice'),
    {
      isOnline: false,
      onlineVisible: false,
      lastSeenVisible: false,
      heartbeatAt: Date.now(),
      lastSeen: Date.now()
    }
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

test('plaintext preview references a real message and legacy encryption cannot be added', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const cid = 'preview-plaintext';
  await setDoc(doc(alice, `conversations/${cid}`), conversation);

  const send = writeBatch(alice);
  send.set(doc(alice, `conversations/${cid}/messages/last`), baseMessage());
  send.update(doc(alice, `conversations/${cid}`), {
    lastMessageId: 'last',
    lastMessageSenderId: 'alice',
    lastMessageText: 'Hello',
    lastMessageType: 'TEXT',
    lastMessageRevision: Date.now()
  });
  await assertSucceeds(send.commit());

  await assertFails(updateDoc(doc(alice, `conversations/${cid}`), {
    lastMessageId: 'missing',
    lastMessageSenderId: 'alice',
    lastMessageType: 'TEXT'
  }));
  await assertFails(updateDoc(doc(alice, `conversations/${cid}`), {
    lastMessageE2ee: e2ee
  }));

  await assertSucceeds(updateDoc(
    doc(alice, `conversations/${cid}/messages/last`),
    {text: 'Edited plaintext', isEdited: true}
  ));
});

test('legacy encrypted message can only migrate to plaintext on sender edit', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const cid = 'legacy-migrate';
  await env.withSecurityRulesDisabled(async ctx => {
    const admin = ctx.firestore();
    await setDoc(doc(admin, `conversations/${cid}`), conversation);
    await setDoc(doc(admin, `conversations/${cid}/messages/legacy`), {
      ...baseMessage({text: ''}),
      e2ee
    });
  });

  await assertSucceeds(updateDoc(
    doc(alice, `conversations/${cid}/messages/legacy`),
    {text: 'Migrated plaintext', e2ee: deleteField(), isEdited: true}
  ));
  await assertFails(updateDoc(
    doc(alice, `conversations/${cid}/messages/legacy`),
    {text: '', e2ee: {...e2ee, e2eeCiphertext: 'new-ciphertext'}, isEdited: true}
  ));
});

test('a delivery acknowledgement cannot downgrade an already read message', async () => {
  const bob = env.authenticatedContext('bob').firestore();
  await env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), 'conversations/pair/messages/read'), baseMessage({status: 'READ'})));
  await assertFails(updateDoc(doc(bob, 'conversations/pair/messages/read'), {status: 'DELIVERED'}));
  await assertSucceeds(updateDoc(doc(bob, 'conversations/pair/messages/read'), {status: 'READ'}));
});

test('device tokens and immutable upload proofs are private to their owner', async () => {
  const alice = env.authenticatedContext('alice').firestore();
  const bob = env.authenticatedContext('bob').firestore();
  const device = 'users/alice/devices/phone';
  await assertSucceeds(setDoc(doc(alice, device), {token: 'private-token', platform: 'android', updatedAt: serverTimestamp()}));
  await assertFails(getDoc(doc(bob, device)));
  await assertFails(setDoc(doc(bob, device), {token: 'stolen', platform: 'android', updatedAt: serverTimestamp()}));
  const path = 'users/alice/mediaUploads/asset-one';
  const receipt = {assetId: 'asset-one', publicId: 'liquid-chat/one', version: 123,
    resourceType: 'image', secureUrl: 'https://res.cloudinary.com/mthzgqhv/image/upload/v123/liquid-chat/one.jpg',
    signature: 'a'.repeat(40), createdAt: serverTimestamp()};
  await assertSucceeds(setDoc(doc(alice, path), receipt));
  await assertSucceeds(setDoc(doc(alice, path), receipt)); // Retry after an ambiguous network response.
  await assertFails(getDoc(doc(bob, path)));
  await assertFails(updateDoc(doc(alice, path), {publicId: 'someone-else'}));
  await assertFails(deleteDoc(doc(alice, path)));
});

test('only a recently authenticated owner can queue deletion and cannot forge completion', async () => {
  const recent = Math.floor(Date.now() / 1000);
  const owner = env.authenticatedContext('purge-owner', {auth_time: recent}).firestore();
  const stale = env.authenticatedContext('stale-owner', {auth_time: recent - 600}).firestore();
  const bob = env.authenticatedContext('bob', {auth_time: recent}).firestore();
  const pending = uid => ({uid, status: 'pending', requestedAt: serverTimestamp(), proofId: 'a'.repeat(64)});
  await assertFails(setDoc(doc(owner, 'accountDeletionRequests/purge-owner'), {...pending('purge-owner'), proofId: 'guessable'}));
  await assertFails(setDoc(doc(stale, 'accountDeletionRequests/stale-owner'), pending('stale-owner')));
  await assertFails(setDoc(doc(bob, 'accountDeletionRequests/purge-owner'), pending('purge-owner')));
  await assertFails(setDoc(doc(owner, 'accountDeletionRequests/purge-owner'), {...pending('purge-owner'), conversationIds: ['pair']}));
  await assertSucceeds(setDoc(doc(owner, 'accountDeletionRequests/purge-owner'), pending('purge-owner')));
  await assertFails(updateDoc(doc(owner, 'accountDeletionRequests/purge-owner'), {status: 'complete'}));
  await assertFails(deleteDoc(doc(owner, 'accountDeletionRequests/purge-owner')));
  await assertFails(getDoc(doc(bob, 'accountDeletionRequests/purge-owner')));
  await assertSucceeds(getDoc(doc(owner, 'accountDeletionRequests/purge-owner')));
});

test('queued deletion freezes both participants and closes profile, username and report write paths', async () => {
  const recent = Math.floor(Date.now() / 1000);
  const owner = env.authenticatedContext('purge-freeze', {auth_time: recent}).firestore();
  const bob = env.authenticatedContext('bob').firestore();
  const cid = 'purge-frozen-chat';
  await env.withSecurityRulesDisabled(async c => {
    const db = c.firestore();
    await setDoc(doc(db, 'users/purge-freeze'), {uid: 'purge-freeze', displayName: 'Old user'});
    await setDoc(doc(db, `conversations/${cid}`), {...conversation, participantIds: ['purge-freeze', 'bob']});
  });
  await setDoc(doc(owner, 'accountDeletionRequests/purge-freeze'), {uid: 'purge-freeze', status: 'pending', requestedAt: serverTimestamp(), proofId: 'b'.repeat(64)});
  await assertFails(getDoc(doc(owner, 'users/purge-freeze')));
  await assertFails(updateDoc(doc(owner, 'users/purge-freeze'), {displayName: 'Restored'}));
  await assertFails(setDoc(doc(owner, 'usernames/purge_freeze'), {uid: 'purge-freeze'}));
  await assertFails(setDoc(doc(owner, 'reports/purge-freeze'), {reporterId: 'purge-freeze', reportedUid: 'bob', reason: 'New report', createdAt: Date.now()}));
  await assertFails(setDoc(doc(bob, `conversations/${cid}/messages/after-request`), baseMessage({senderId: 'bob'})));
  await assertFails(setDoc(doc(bob, 'conversations/purge-new-chat'), {...conversation, participantIds: ['purge-freeze', 'bob']}));
  await env.withSecurityRulesDisabled(c => updateDoc(doc(c.firestore(), 'accountDeletionRequests/purge-freeze'), {status: 'failed', error: 'Provider unavailable'}));
  await assertSucceeds(updateDoc(doc(owner, 'accountDeletionRequests/purge-freeze'), {status: 'pending'}));
  await env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), 'accountDeletionRequests/purge-freeze'), {uid: 'purge-freeze', status: 'complete'}));
  await assertFails(deleteDoc(doc(owner, 'accountDeletionRequests/purge-freeze')));
});

test('deleted owners with still-valid tokens cannot remove the gate or recreate data', async () => {
  const stale = env.authenticatedContext('deleted-owner').firestore();
  await env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), 'accountDeletionRequests/deleted-owner'), {
    uid: 'deleted-owner', status: 'revoking', proofId: 'c'.repeat(64), readyAt: Date.now() + 4200000
  }));
  await assertFails(deleteDoc(doc(stale, 'accountDeletionRequests/deleted-owner')));
  await assertFails(setDoc(doc(stale, 'users/deleted-owner'), {uid: 'deleted-owner', displayName: 'Restored'}));
  await assertFails(setDoc(doc(stale, 'directory/deleted-owner'), {uid: 'deleted-owner', displayName: 'Restored'}));
});

test('random deletion proof is readable after Auth deletion but cannot be listed or forged', async () => {
  const anonymous = env.unauthenticatedContext().firestore();
  const owner = env.authenticatedContext('proof-owner', {auth_time: Math.floor(Date.now() / 1000)}).firestore();
  const proofId = 'd'.repeat(64);
  await env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), `deletionProofs/${proofId}`), {status: 'revoking', readyAt: Date.now() + 4200000}));
  await assertSucceeds(getDoc(doc(anonymous, `deletionProofs/${proofId}`)));
  await assertFails(getDocs(collection(anonymous, 'deletionProofs')));
  await assertFails(getDoc(doc(anonymous, 'deletionProofs/guessable')));
  await assertFails(setDoc(doc(anonymous, `deletionProofs/${'e'.repeat(64)}`), {status: 'complete'}));
  await assertFails(updateDoc(doc(anonymous, `deletionProofs/${proofId}`), {status: 'complete'}));
  await assertFails(deleteDoc(doc(anonymous, `deletionProofs/${proofId}`)));
  await assertFails(setDoc(doc(owner, 'accountDeletionRequests/proof-owner'), {uid: 'proof-owner', status: 'pending', proofId, requestedAt: serverTimestamp()}));
  await env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), `deletionProofs/${proofId}`), {status: 'complete', completedAt: serverTimestamp()}));
  await assertSucceeds(deleteDoc(doc(anonymous, `deletionProofs/${proofId}`)));
});

test('a peer cannot recreate conversations or reports referring to a deleted directory', async () => {
  const bob = env.authenticatedContext('bob').firestore();
  await assertFails(setDoc(doc(bob, 'conversations/deleted-peer'), {...conversation, participantIds: ['bob', 'gone-owner']}));
  await assertFails(setDoc(doc(bob, 'reports/deleted-peer'), {reporterId: 'bob', reportedUid: 'gone-owner', reason: 'Gone user', createdAt: Date.now()}));
});
