/** Run once locally using a service-account environment variable. Never deploy with a public domain. */
import {initializeApp,cert} from 'firebase-admin/app';
import {getFirestore} from 'firebase-admin/firestore';
initializeApp({credential:cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT_JSON))});
const db=getFirestore();
let cursor;let count=0;
while(true){let q=db.collection('users').orderBy('__name__').limit(200);if(cursor)q=q.startAfter(cursor);const snap=await q.get();if(snap.empty)break;
 for(const d of snap.docs){const u=d.data();let username=String(u.username||d.id.slice(0,10)).toLowerCase().replace(/[^a-z0-9_.]/g,'').slice(0,32);if(username.length<3)username='user_'+d.id.slice(0,12);
  await db.runTransaction(async t=>{let nr=db.doc('usernames/'+username);const n=await t.get(nr);if(n.exists&&n.data().uid!==d.id){username='user_'+d.id.slice(0,20).toLowerCase();nr=db.doc('usernames/'+username);}
   const photo=String(u.photoUrl||'');
   t.set(nr,{uid:d.id});t.set(d.ref,{username},{merge:true});
   t.set(db.doc('directory/'+d.id),{uid:d.id,username,displayName:u.displayName||'Contact',bio:u.bio||'',photoUrl:photo.includes('images.unsplash.com')?'':photo,isOnline:false,onlineVisible:u.privacy?.onlineVisibility!=='Nobody',lastSeenVisible:u.privacy?.lastSeenVisibility!=='Nobody',lastSeen:u.privacy?.lastSeenVisibility==='Nobody'?0:(u.lastSeen||0),heartbeatAt:0,createdAt:u.createdAt||Date.now()},{merge:true});
  });count++;
 }cursor=snap.docs.at(-1);
}
console.log(`Migrated ${count} user profiles. No messages or existing media were deleted.`);
