import {test, before, after} from 'node:test';
import {readFileSync} from 'node:fs';
import {initializeTestEnvironment, assertFails, assertSucceeds} from '@firebase/rules-unit-testing';
import {createMockUserToken} from '@firebase/util';
const serverTimestamp = () => ({'.sv':'timestamp'});
async function write(uid, data) {
 const token = createMockUserToken({sub:uid,user_id:uid},'demo-liquid-chat');
 return fetch('http://127.0.0.1:9000/presence/alice.json?ns=demo-liquid-chat&auth='+token, {method:'PUT', body:JSON.stringify(data)});
}
async function accepted(uid,data) { if (!(await write(uid,data)).ok) throw new Error('Valid presence write rejected'); }
async function denied(uid,data) { if ((await write(uid,data)).ok) throw new Error('Invalid presence write accepted'); }
let env;
before(async () => {
  env = await initializeTestEnvironment({projectId:'demo-liquid-chat', database:{host:'127.0.0.1', port:9000, rules:readFileSync('../../database.rules.json','utf8')}});
});
after(async () => env?.cleanup());
const state = () => ({uid:'alice', isOnline:true, onlineVisible:true, lastSeenVisible:true, heartbeatAt:serverTimestamp(), lastSeen:serverTimestamp()});
test('owner can publish heartbeat and offline; others cannot impersonate', async () => {
  await accepted('alice',state());
  await accepted('alice',{...state(),isOnline:false});
  await denied('bob',state());
  if ((await fetch('http://127.0.0.1:9000/presence/alice.json?ns=demo-liquid-chat')).ok) throw new Error('Unauthenticated read allowed');
});
test('unknown fields and leaking hidden last-seen are rejected', async () => {
  await denied('alice',{...state(),email:'private'});
  await denied('alice',{...state(),lastSeenVisible:false});
  await accepted('alice',{...state(),isOnline:false,onlineVisible:false,lastSeenVisible:false,lastSeen:0});
});
