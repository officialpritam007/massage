import test from 'node:test';
import assert from 'node:assert/strict';
import {appUser,assertPair,validateMessage,fresh} from '../src/policy.js';
test('only members of a two-person conversation are authorized',()=>{
 assert.doesNotThrow(()=>assertPair(['alice','bob'],'alice'));
 for(const ids of [['alice'],['alice','alice'],['alice','bob','eve'],['bob','eve']])assert.throws(()=>assertPair(ids,'alice'));
});
test('Firebase UID maps deterministically into valid isolated Appwrite identities',()=>{
 assert.equal(appUser('long:firebase/uid'),appUser('long:firebase/uid'));
 assert.notEqual(appUser('alice'),appUser('bob'));
 assert.match(appUser('user'),/^[a-z0-9]{32}$/);
});
test('message validation rejects empty text, unsupported types and path injection',()=>{
 const m={id:'message_123',type:'TEXT',text:'hello'};
 assert.doesNotThrow(()=>validateMessage(m));
 for(const change of [{id:'../private'},{text:''},{text:'x'.repeat(8001)},{type:'CALL'}])assert.throws(()=>validateMessage({...m,...change}));
 assert.doesNotThrow(()=>validateMessage({...m,type:'VOICE',text:'Voice message'}));
});
test('stale and future presence cannot be treated as fresh',()=>{
 assert.equal(fresh(99900,100000,45000),true);
 assert.equal(fresh(1000,100000,45000),false);
 assert.equal(fresh(200000,100000,45000),false);
});
