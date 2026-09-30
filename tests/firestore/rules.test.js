import {test,before,after} from 'node:test';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment,assertFails,assertSucceeds} from '@firebase/rules-unit-testing';
import {doc,getDoc,setDoc,updateDoc} from 'firebase/firestore';
let env;
before(async()=>{
 env=await initializeTestEnvironment({projectId:'demo-liquid-chat',firestore:{host:'127.0.0.1',port:8080,rules:readFileSync('../../firestore.rules','utf8')}});
 await env.withSecurityRulesDisabled(async c=>{
  const db=c.firestore();
  await setDoc(doc(db,'users/alice'),{email:'private@example.com',tokens:{},privacy:{}});
  await setDoc(doc(db,'directory/alice'),{displayName:'Alice',isOnline:false,onlineVisible:true,lastSeenVisible:true,heartbeatAt:0,lastSeen:0});
  await setDoc(doc(db,'conversations/pair'),{participantIds:['alice','bob']});
  await setDoc(doc(db,'conversations/pair/messages/m1'),{senderId:'alice',text:'Private'});
  await setDoc(doc(db,'_sessions/alice'),{secret:'private'});
 });
});
after(async()=>env?.cleanup());
test('public directory requires login, private profile requires owner',async()=>{
 const bob=env.authenticatedContext('bob').firestore(),alice=env.authenticatedContext('alice').firestore();
 await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(),'directory/alice')));
 await assertSucceeds(getDoc(doc(bob,'directory/alice')));
 await assertFails(getDoc(doc(bob,'users/alice')));
 await assertSucceeds(getDoc(doc(alice,'users/alice')));
});
test('only participants can read messages; no client can forge messages',async()=>{
 const alice=env.authenticatedContext('alice').firestore(),eve=env.authenticatedContext('eve').firestore();
 await assertSucceeds(getDoc(doc(alice,'conversations/pair/messages/m1')));
 await assertFails(getDoc(doc(eve,'conversations/pair/messages/m1')));
 await assertFails(setDoc(doc(alice,'conversations/pair/messages/forged'),{senderId:'bob',text:'forged'}));
 await assertFails(updateDoc(doc(alice,'conversations/pair'),{participantIds:['alice','eve']}));
});
test('typing is own, participant-only and expires within 15 seconds',async()=>{
 const alice=env.authenticatedContext('alice').firestore(),eve=env.authenticatedContext('eve').firestore();
 await assertSucceeds(setDoc(doc(alice,'conversations/pair/typing/alice'),{until:Date.now()+6000}));
 await assertFails(setDoc(doc(alice,'conversations/pair/typing/bob'),{until:Date.now()+6000}));
 await assertFails(setDoc(doc(eve,'conversations/pair/typing/eve'),{until:Date.now()+6000}));
 await assertFails(setDoc(doc(alice,'conversations/pair/typing/alice'),{until:Date.now()+60000}));
});
test('preferences may change but identity and server secrets are protected',async()=>{
 const alice=env.authenticatedContext('alice').firestore();
 await assertSucceeds(updateDoc(doc(alice,'users/alice'),{privacy:{readReceipts:false}}));
 await assertFails(updateDoc(doc(alice,'users/alice'),{email:'forged@example.com'}));
 await assertFails(getDoc(doc(alice,'_sessions/alice')));
 await assertFails(setDoc(doc(alice,'media/forged'),{owner:'alice',ready:true}));
});
