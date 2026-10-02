import {test,before,after} from 'node:test';
import {readFileSync} from 'node:fs';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
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

const mediaE2ee = {
  e2eeVersion: 1,
  e2eeEphemeralKey: 'media-ephemeral',
  e2eeSenderWrappedKey: 'sender-media-key',
  e2eeSenderWrapIv: 'sender-media-iv',
  e2eeRecipientWrappedKey: 'recipient-media-key',
  e2eeRecipientWrapIv: 'recipient-media-iv',
  e2eeSenderKeyId: 'alice-key',
  e2eeRecipientKeyId: 'bob-key',
  e2eeFileIv: 'file-iv',
  e2eeSenderPublicKey: 'alice-public',
  e2eeSignature: 'media-signature'
};

before(async()=>{
  env=await initializeTestEnvironment({
    projectId:'demo-liquid-chat',
    firestore:{
      host:'127.0.0.1',
      port:8080,
      rules:readFileSync('../../firestore.rules','utf8')
    }
  });

  await env.withSecurityRulesDisabled(async c=>{
    const db=c.firestore();
    await setDoc(doc(db,'users/alice'),{
      uid:'alice',
      displayName:'Alice',
      username:'alice',
      email:'private@example.com',
      tokens:{},
      privacy:{}
    });
    await setDoc(doc(db,'users/bob'),{
      uid:'bob',
      displayName:'Bob',
      username:'bob',
      email:'bob@example.com',
      tokens:{},
      privacy:{}
    });
    await setDoc(doc(db,'directory/alice'),{
      uid:'alice',
      displayName:'Alice',
      username:'alice',
      bio:'',
      e2eePublicKey:'alice-public',
      e2eeKeyId:'alice-key',
      isOnline:false,
      onlineVisible:true,
      lastSeenVisible:true,
      heartbeatAt:0,
      lastSeen:0
    });
    await setDoc(doc(db,'directory/bob'),{
      uid:'bob',
      displayName:'Bob',
      username:'bob',
      bio:'',
      e2eePublicKey:'bob-public',
      e2eeKeyId:'bob-key',
      isOnline:false,
      onlineVisible:true,
      lastSeenVisible:true,
      heartbeatAt:0,
      lastSeen:0
    });
    await setDoc(doc(db,'conversations/pair'),conversation);
    await setDoc(doc(db,'conversations/pair/messages/legacy'),{
      senderId:'alice',
      text:'Legacy message'
    });
    await setDoc(doc(db,'_sessions/alice'),{secret:'private'});
  });
});

after(async()=>env?.cleanup());

test('public directory requires login, private profile requires owner',async()=>{
  const bob=env.authenticatedContext('bob').firestore();
  const alice=env.authenticatedContext('alice').firestore();
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'directory/alice')));
  await assertSucceeds(getDoc(doc(bob,'directory/alice')));
  await assertFails(getDoc(doc(bob,'users/alice')));
  await assertSucceeds(getDoc(doc(alice,'users/alice')));
});

test('the exact chats-list arrayContains query is authorized',async()=>{
  const alice=env.authenticatedContext('alice').firestore();
  const q=query(
    collection(alice,'conversations'),
    where('participantIds','array-contains','alice')
  );
  const result=await assertSucceeds(getDocs(q));
  if(result.docs.every(d=>d.id!=='pair')) throw new Error('pair conversation missing');
});

test('profile, token, E2EE identity and presence bootstrap writes are authorized',async()=>{
  const alice=env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(
    doc(alice,'users/alice'),
    {tokens:{device:'token'}},
    {merge:true}
  ));
  await assertSucceeds(setDoc(
    doc(alice,'users/alice'),
    {e2eePublicKey:'new-public',e2eeKeyId:'new-key'},
    {merge:true}
  ));
  await assertSucceeds(setDoc(
    doc(alice,'directory/alice'),
    {e2eePublicKey:'new-public',e2eeKeyId:'new-key'},
    {merge:true}
  ));
  await assertSucceeds(setDoc(
    doc(alice,'directory/alice'),
    {
      isOnline:true,
      onlineVisible:true,
      lastSeenVisible:true,
      heartbeatAt:Date.now(),
      lastSeen:Date.now()
    },
    {merge:true}
  ));
});

test('participants can create a direct conversation and a valid E2EE text message',async()=>{
  const alice=env.authenticatedContext('alice').firestore();
  await assertSucceeds(setDoc(doc(alice,'conversations/newpair'),conversation));

  const message={
    senderId:'alice',
    senderName:'Alice',
    text:'',
    type:'TEXT',
    mediaUrl:'',
    voiceDurationSeconds:0,
    waveform:[],
    createdAt:Date.now(),
    status:'SENT',
    isDeleted:false,
    deletedForEveryone:false,
    hiddenFor:[],
    isEdited:false,
    isPinned:false,
    reactions:[],
    notificationPending:true,
    e2ee
  };
  await assertSucceeds(setDoc(
    doc(alice,'conversations/newpair/messages/m1'),
    message
  ));

  await assertSucceeds(updateDoc(
    doc(alice,'conversations/newpair'),
    {
      lastMessageId:'m1',
      lastMessageText:'Encrypted message',
      lastMessageTime:Date.now(),
      lastMessageSenderId:'alice'
    }
  ));
});

test('media messages require Cloudinary references and permanent delete is backend-only',async()=>{
  const alice=env.authenticatedContext('alice').firestore();

  const validMedia={
    senderId:'alice',
    senderName:'Alice',
    text:'',
    type:'IMAGE',
    mediaUrl:'cloudinary:0123456789abcdef0123456789abcdef',
    voiceDurationSeconds:0,
    waveform:[],
    createdAt:Date.now(),
    status:'SENT',
    isDeleted:false,
    deletedForEveryone:false,
    hiddenFor:[],
    isEdited:false,
    isPinned:false,
    reactions:[],
    notificationPending:true,
    mediaE2ee
  };

  await assertSucceeds(setDoc(
    doc(alice,'conversations/pair/messages/media-valid'),
    validMedia
  ));

  await assertFails(setDoc(
    doc(alice,'conversations/pair/messages/media-external'),
    {...validMedia,mediaUrl:'https://tracker.example/file.jpg'}
  ));

  await assertFails(deleteDoc(
    doc(alice,'conversations/pair/messages/media-valid')
  ));
});

test('profile photo reference is backend-owned',async()=>{
  const alice=env.authenticatedContext('alice').firestore();
  await assertFails(setDoc(
    doc(alice,'directory/alice'),
    {photoUrl:'https://tracker.example/avatar.jpg'},
    {merge:true}
  ));
});

test('recipient delivery receipt and delete-for-me update are authorized',async()=>{
  const bob=env.authenticatedContext('bob').firestore();
  await env.withSecurityRulesDisabled(async c=>{
    await setDoc(doc(c.firestore(),'conversations/pair/messages/e2ee'),{
      senderId:'alice',
      senderName:'Alice',
      text:'',
      type:'TEXT',
      mediaUrl:'',
      voiceDurationSeconds:0,
      waveform:[],
      createdAt:Date.now(),
      status:'SENT',
      isDeleted:false,
      deletedForEveryone:false,
      hiddenFor:[],
      isEdited:false,
      isPinned:false,
      reactions:[],
      notificationPending:true,
      e2ee
    });
  });
  await assertSucceeds(updateDoc(
    doc(bob,'conversations/pair/messages/e2ee'),
    {status:'DELIVERED'}
  ));
  await assertSucceeds(updateDoc(
    doc(bob,'conversations/pair/messages/e2ee'),
    {hiddenFor:['bob']}
  ));
});

test('only participants can read messages and sender identity cannot be forged',async()=>{
  const alice=env.authenticatedContext('alice').firestore();
  const eve=env.authenticatedContext('eve').firestore();
  await assertSucceeds(getDoc(doc(alice,'conversations/pair/messages/legacy')));
  await assertFails(getDoc(doc(eve,'conversations/pair/messages/legacy')));
  await assertFails(setDoc(
    doc(alice,'conversations/pair/messages/forged'),
    {
      senderId:'bob',
      senderName:'Bob',
      text:'',
      type:'TEXT',
      mediaUrl:'',
      voiceDurationSeconds:0,
      waveform:[],
      createdAt:Date.now(),
      status:'SENT',
      isDeleted:false,
      deletedForEveryone:false,
      hiddenFor:[],
      isEdited:false,
      isPinned:false,
      reactions:[],
      notificationPending:true,
      e2ee
    }
  ));
  await assertFails(updateDoc(doc(alice,'conversations/pair'),{
    participantIds:['alice','eve']
  }));
});

test('typing is own, participant-only and expires within 15 seconds',async()=>{
  const alice=env.authenticatedContext('alice').firestore();
  const eve=env.authenticatedContext('eve').firestore();
  await assertSucceeds(setDoc(
    doc(alice,'conversations/pair/typing/alice'),
    {until:Date.now()+6000}
  ));
  await assertFails(setDoc(
    doc(alice,'conversations/pair/typing/bob'),
    {until:Date.now()+6000}
  ));
  await assertFails(setDoc(
    doc(eve,'conversations/pair/typing/eve'),
    {until:Date.now()+6000}
  ));
  await assertFails(setDoc(
    doc(alice,'conversations/pair/typing/alice'),
    {until:Date.now()+60000}
  ));
});

test('private server collections stay inaccessible from Android clients',async()=>{
  const alice=env.authenticatedContext('alice').firestore();
  await assertFails(getDoc(doc(alice,'_sessions/alice')));
  await assertFails(setDoc(doc(alice,'media/forged'),{owner:'alice',ready:true}));
});
