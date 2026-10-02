import {initializeApp, cert, getApps} from 'firebase-admin/app';
import {v2 as cloudinary} from 'cloudinary';
import {getAuth} from 'firebase-admin/auth';
import {getFirestore, FieldValue} from 'firebase-admin/firestore';
import {getMessaging} from 'firebase-admin/messaging';
import {randomUUID} from 'node:crypto';
import {assertPair, validateMessage} from './policy.js';

const env = process.env;

function init() {
  if (!getApps().length) {
    if (env.FIREBASE_SERVICE_ACCOUNT_JSON) {
      initializeApp({credential: cert(JSON.parse(env.FIREBASE_SERVICE_ACCOUNT_JSON))});
    } else {
      initializeApp();
    }
  }
  cloudinary.config({
    cloud_name: env.CLOUDINARY_CLOUD_NAME,
    api_key: env.CLOUDINARY_API_KEY,
    api_secret: env.CLOUDINARY_API_SECRET,
    secure: true
  });
  return getFirestore();
}

function assertCloudinaryConfigured() {
  if (!env.CLOUDINARY_CLOUD_NAME || !env.CLOUDINARY_API_KEY || !env.CLOUDINARY_API_SECRET) {
    throw new Error('Cloudinary backend configuration is incomplete');
  }
}

async function destroyCloudinary(meta) {
  if (!meta?.publicId) return;
  assertCloudinaryConfigured();
  await cloudinary.uploader.destroy(meta.publicId, {
    resource_type: meta.resourceType || 'raw',
    type: meta.deliveryType || 'authenticated',
    invalidate: true
  });
}

async function allowed(db, cid, uid) {
  const ref = db.doc('conversations/' + cid);
  const snap = await ref.get();
  if (!snap.exists) throw new Error('Conversation not found');
  const c = snap.data();
  assertPair(c.participantIds, uid);
  const other = c.participantIds.find(x => x !== uid);
  const [a, b] = await Promise.all([db.doc('users/' + uid).get(), db.doc('users/' + other).get()]);
  if ((a.data()?.blockedUserIds || []).includes(other) || (b.data()?.blockedUserIds || []).includes(uid)) {
    throw new Error('Messaging is unavailable for this contact');
  }
  return {ref, c, other};
}

function sanitizeWaveform(value) {
  if (!Array.isArray(value)) return [];
  return value.map(Number).filter(Number.isFinite).map(x => Math.max(0.05, Math.min(1, x))).slice(0, 80);
}

function expectedMimePrefix(type) {
  if (type === 'IMAGE') return 'image/';
  if (type === 'VIDEO') return 'video/';
  if (type === 'VOICE') return 'audio/';
  return '';
}

function previewFor(m) {
  if (!m) return '';
  if (m.type === 'TEXT') return String(m.text || '').slice(0, 500);
  if (m.type === 'IMAGE') return 'Photo';
  if (m.type === 'VIDEO') return 'Video';
  if (m.type === 'VOICE') return 'Voice message';
  return 'Document';
}

async function refreshConversationSummary(ref) {
  const q = await ref.collection('messages').orderBy('createdAt', 'desc').limit(60).get();
  const now = Date.now();
  const doc = q.docs.find(d => {
    const m = d.data();
    return !m.deletedForEveryone && !(m.expiresAt && Number(m.expiresAt) <= now);
  });
  if (!doc) {
    await ref.update({lastMessageId: '', lastMessageText: '', lastMessageTime: 0, lastMessageSenderId: ''});
    return;
  }
  const m = doc.data();
  await ref.update({
    lastMessageId: doc.id,
    lastMessageText: previewFor(m),
    lastMessageTime: Number(m.createdAt) || now,
    lastMessageSenderId: m.senderId || ''
  });
}

async function revokeMediaFromConversation(db, mediaUrl, cid) {
  if (!String(mediaUrl || '').startsWith('cloudinary:')) return;
  const fileId = String(mediaUrl).replace('cloudinary:', '');
  const mr = db.doc('media/' + fileId);
  const snap = await mr.get();
  if (!snap.exists) return;
  const meta = snap.data();
  const forwards = (meta.forwardedTo || []).filter(x => x !== cid);
  const originalStillAuthorized = meta.conversationId && meta.conversationId !== cid;
  if (!originalStillAuthorized && forwards.length === 0) {
    await destroyCloudinary(meta).catch(() => {});
    await mr.delete().catch(() => {});
    return;
  }
  await mr.update({
    forwardedTo: forwards,
    revokedFrom: FieldValue.arrayUnion(cid)
  });
}

async function deleteOwnedProfileMedia(db, uid, mediaUrl) {
  if (!String(mediaUrl || '').startsWith('cloudinary:')) return;
  const fileId = String(mediaUrl).replace('cloudinary:', '');
  if (!/^[a-z0-9]{32}$/.test(fileId)) return;
  const mr = db.doc('media/' + fileId);
  const snap = await mr.get();
  if (!snap.exists) return;
  const meta = snap.data();
  if (meta?.owner !== uid || meta?.conversationId) return;
  await destroyCloudinary(meta).catch(() => {});
  await mr.delete().catch(() => {});
}

function isPermanentTokenError(code) {
  return code === 'messaging/registration-token-not-registered'
    || code === 'messaging/invalid-registration-token'
    || code === 'messaging/invalid-argument';
}

async function notify(db, cid, id) {
  const ref = db.doc(`conversations/${cid}/messages/${id}`);
  const m = (await ref.get()).data();
  if (!m || m.notifiedAt || m.deletedForEveryone) return;
  const c = (await db.doc('conversations/' + cid).get()).data();
  const uid = c.participantIds.find(x => x !== m.senderId);
  if ((m.hiddenFor || []).includes(uid)) {
    await ref.update({notifiedAt: Date.now()});
    return;
  }
  const userRef = db.doc('users/' + uid);
  const u = (await userRef.get()).data();
  if (!u || u.notifications?.messages === false || (c.mutedFor || []).includes(uid) || (u.blockedUserIds || []).includes(m.senderId)) {
    await ref.update({notifiedAt: Date.now()});
    return;
  }

  const tokenEntries = Object.entries(u.tokens || {})
    .filter(([, token]) => typeof token === 'string' && token.length > 0)
    .slice(0, 10);
  if (!tokenEntries.length) {
    throw new Error('No active notification token');
  }

  const tokens = tokenEntries.map(([, token]) => token);
  const showPreview = u.notifications?.showPreview !== false;
  const result = await getMessaging().sendEachForMulticast({
    tokens,
    android: {priority: 'high'},
    data: {
      type: 'message',
      conversationId: cid,
      messageId: id,
      title: showPreview ? (m.senderName || 'Liquid Chat') : 'Liquid Chat',
      body: showPreview
        ? (
            m.e2ee
              ? 'Encrypted message'
              : (m.type === 'TEXT' ? String(m.text || '').slice(0, 120) : m.type.toLowerCase() + ' message')
          )
        : 'New message',
      vibration: String(u.notifications?.vibration !== false),
      preview: String(showPreview)
    }
  });

  const tokenDeletes = {};
  let transientFailures = 0;
  result.responses.forEach((response, index) => {
    if (response.success) return;
    const code = response.error?.code || '';
    if (isPermanentTokenError(code)) {
      tokenDeletes[`tokens.${tokenEntries[index][0]}`] = FieldValue.delete();
    } else {
      transientFailures += 1;
    }
  });
  if (Object.keys(tokenDeletes).length) await userRef.update(tokenDeletes).catch(() => {});

  if (result.successCount === 0 && transientFailures > 0) throw new Error('Push delivery temporarily unavailable');
  await ref.update({notifiedAt: Date.now()});
}

async function notifyDeletion(db, cid, id, actorUid) {
  const c = (await db.doc('conversations/' + cid).get()).data();
  if (!c?.participantIds) return;
  for (const targetUid of c.participantIds.filter(x => x !== actorUid)) {
    const u = (await db.doc('users/' + targetUid).get()).data();
    const tokens = Object.values(u?.tokens || {}).filter(x => typeof x === 'string').slice(0, 10);
    if (!tokens.length) continue;
    await getMessaging().sendEachForMulticast({
      tokens,
      android: {priority: 'high'},
      data: {type: 'message_deleted', conversationId: cid, messageId: id}
    }).catch(() => {});
  }
}

async function deleteQueryDocs(query) {
  while (true) {
    const snap = await query.limit(200).get();
    if (snap.empty) break;
    const batch = snap.docs[0].ref.firestore.batch();
    for (const doc of snap.docs) batch.delete(doc.ref);
    await batch.commit();
    if (snap.size < 200) break;
  }
}

async function purgeUserData(db, uid, claims, nextInstallationId, now) {
  const own = db.doc('users/' + uid);
  const old = (await own.get()).data() || {};

  const conversations = await db.collection('conversations')
    .where('participantIds', 'array-contains', uid)
    .get();

  for (const conversation of conversations.docs) {
    const cid = conversation.id;

    const conversationMedia = await db.collection('media')
      .where('conversationId', '==', cid)
      .get();
    for (const media of conversationMedia.docs) {
      await destroyCloudinary(media.data()).catch(() => {});
      await media.ref.delete().catch(() => {});
    }

    await deleteQueryDocs(
      db.collection('messageTombstones').where('conversationId', '==', cid)
    ).catch(() => {});
    await deleteQueryDocs(
      db.collection('messageHiddenTombstones').where('conversationId', '==', cid)
    ).catch(() => {});

    await db.recursiveDelete(conversation.ref);
  }

  const ownedMedia = await db.collection('media').where('owner', '==', uid).get();
  for (const media of ownedMedia.docs) {
    await destroyCloudinary(media.data()).catch(() => {});
    await media.ref.delete().catch(() => {});
  }

  await deleteQueryDocs(db.collection('reports').where('reporterId', '==', uid)).catch(() => {});

  await Promise.all([
    db.doc('_rate/' + uid).delete().catch(() => {}),
    db.doc('_reportRate/' + uid).delete().catch(() => {})
  ]);

  const previousUsername = String(old.username || '').trim().toLowerCase();
  const cleanInstallationId = String(nextInstallationId || '').trim().slice(0, 80);
  if (!cleanInstallationId) throw new Error('Missing installation identity');

  if (previousUsername) {
    await db.doc('usernames/' + previousUsername).delete().catch(() => {});
  }

  const cleanEmail = String(claims.email || old.email || '').slice(0, 160);
  const createdAt = Number(old.createdAt) || now;

  // Keep only the authentication-linked shell needed to sign back in. All app profile,
  // chat, media, privacy/settings and E2EE identity data starts fresh.
  await own.set({
    uid,
    displayName: 'User',
    username: '',
    bio: '',
    email: cleanEmail,
    phoneNumber: '',
    createdAt,
    installationId: cleanInstallationId,
    dataEpoch: now,
    tokens: {},
    blockedUserIds: [],
    appearance: {},
    privacy: {},
    notifications: {}
  });

  await db.doc('directory/' + uid).set({
    uid,
    displayName: 'User',
    username: '',
    bio: '',
    createdAt
  });

  return {ok: true, dataEpoch: now};
}

export default async ({req, res, error}) => {
  try {
    const db = init();
    const token = (req.headers.authorization || '').replace(/^Bearer /, '');
    if (!token) return res.json({error: 'Missing Firebase ID token'}, 401);
    const claims = await getAuth().verifyIdToken(token, true);
    const uid = claims.uid;
    const p = typeof req.bodyJson === 'object' ? req.bodyJson : JSON.parse(req.bodyText || '{}');
    const own = db.doc('users/' + uid);
    const now = Date.now();

    const rate = db.doc('_rate/' + uid);
    await db.runTransaction(async t => {
      const d = (await t.get(rate)).data();
      const same = d?.minute === Math.floor(now / 60000);
      if (same && d.count >= 180) throw new Error('Too many requests. Try again shortly.');
      t.set(rate, {minute: Math.floor(now / 60000), count: same ? d.count + 1 : 1});
    });

    if (p.action === 'usernameCheck') {
      const username = String(p.username || '').trim().toLowerCase();
      if (!/^[a-z0-9_.]{3,32}$/.test(username)) {
        throw new Error('Username must have 3–32 letters, numbers, dots or underscores');
      }
      const reserved = await db.doc('usernames/' + username).get();
      return res.json({available: !reserved.exists || reserved.data()?.uid === uid});
    }

    if (p.action === 'profile') {
      const old = (await own.get()).data() || {};
      const name = String(p.displayName ?? old.displayName ?? claims.name ?? 'User').trim().slice(0, 60);
      const username = String(p.username ?? old.username ?? uid.slice(0, 10)).trim().toLowerCase();
      if (!/^[a-z0-9_.]{3,32}$/.test(username)) throw new Error('Username must have 3–32 letters, numbers, dots or underscores');
      const pub = db.doc('directory/' + uid);
      await db.runTransaction(async t => {
        const nameRef = db.doc('usernames/' + username);
        const reserved = await t.get(nameRef);
        if (reserved.exists && reserved.data().uid !== uid) throw new Error('Username already taken');
        t.set(nameRef, {uid});
        const data = {
          uid,
          displayName: name,
          username,
          bio: String(p.bio ?? old.bio ?? '').slice(0, 160),
          email: claims.email || '',
          phoneNumber: String(p.phoneNumber ?? old.phoneNumber ?? '').slice(0, 30),
          createdAt: old.createdAt || now
        };
        t.set(own, data, {merge: true});
        t.set(pub, {uid, displayName: name, username, bio: data.bio, createdAt: data.createdAt}, {merge: true});
      });
      return res.json({ok: true});
    }

    if (p.action === 'profilePhotoDelete') {
      const old = (await own.get()).data() || {};
      const previousPhoto = String(old.photoUrl || '');
      await deleteOwnedProfileMedia(db, uid, previousPhoto);
      await Promise.all([
        own.set({photoUrl: FieldValue.delete()}, {merge: true}),
        db.doc('directory/' + uid).set({photoUrl: FieldValue.delete()}, {merge: true})
      ]);
      return res.json({ok: true});
    }

    if (p.action === 'conversation') {
      if (typeof p.otherUid !== 'string' || p.otherUid === uid || !(await db.doc('users/' + p.otherUid).get()).exists) {
        throw new Error('Invalid contact');
      }
      const ids = [uid, p.otherUid].sort();
      const cid = ids.join('_');
      const ref = db.doc('conversations/' + cid);
      await db.runTransaction(async t => {
        const s = await t.get(ref);
        if (!s.exists) {
          t.set(ref, {
            participantIds: ids,
            lastMessageId: '',
            lastMessageTime: 0,
            lastMessageText: '',
            lastMessageSenderId: '',
            unreadCounts: {},
            deletedFor: [],
            deletedBefore: {}
          });
        }
      });
      await allowed(db, cid, uid);
      return res.json({id: cid});
    }

    if (p.action === 'uploadBegin') {
      assertCloudinaryConfigured();
      if (p.conversationId) await allowed(db, p.conversationId, uid);
      const fileId = randomUUID().replaceAll('-', '');
      const encrypted = Boolean(p.conversationId && p.encrypted === true);
      const resourceType = encrypted ? 'raw' : 'image';
      const deliveryType = 'authenticated';
      const publicId = `liquid-chat/${uid}/${fileId}`;
      const timestamp = Math.floor(Date.now() / 1000);
      const signature = cloudinary.utils.api_sign_request(
        {timestamp, public_id: publicId, type: deliveryType},
        env.CLOUDINARY_API_SECRET
      );
      await db.doc('media/' + fileId).set({
        owner: uid,
        conversationId: p.conversationId || null,
        createdAt: now,
        ready: false,
        encrypted,
        originalMime: String(p.originalMime || '').slice(0, 120),
        publicId,
        resourceType,
        deliveryType,
        forwardedTo: [],
        revokedFrom: []
      });
      return res.json({
        fileId,
        uploadUrl: `https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/${resourceType}/upload`,
        apiKey: env.CLOUDINARY_API_KEY,
        timestamp,
        signature,
        publicId,
        resourceType,
        deliveryType
      });
    }

    if (p.action === 'uploadFinish') {
      assertCloudinaryConfigured();
      if (!/^[a-z0-9]{32}$/.test(p.fileId || '')) throw new Error('Invalid file');
      const metaRef = db.doc('media/' + p.fileId);
      const meta = (await metaRef.get()).data();
      if (meta?.owner !== uid) throw new Error('File access denied');
      if (p.publicId && p.publicId !== meta.publicId) throw new Error('Invalid Cloudinary asset');
      if (p.resourceType && p.resourceType !== meta.resourceType) throw new Error('Invalid Cloudinary resource type');
      const asset = await cloudinary.api.resource(meta.publicId, {
        resource_type: meta.resourceType,
        type: meta.deliveryType || 'authenticated'
      });
      const maxStoredSize = 10 * 1024 * 1024;
      if (!asset?.bytes || asset.bytes > maxStoredSize) throw new Error('Incomplete or oversized file');
      if (!meta.conversationId && !String(meta.originalMime || '').startsWith('image/')) {
        throw new Error('Profile photo must be an image');
      }
      if (meta.conversationId) await allowed(db, meta.conversationId, uid);
      await metaRef.update({
        ready: true,
        mimeType: String(meta.originalMime || ''),
        storageMimeType: meta.encrypted ? 'application/octet-stream' : String(meta.originalMime || ''),
        size: Number(asset.bytes || 0),
        version: Number(asset.version || p.version || 0),
        format: String(asset.format || (meta.resourceType === 'raw' ? 'bin' : 'jpg'))
      });
      if (!meta.conversationId) {
        const previousPhoto = String((await own.get()).data()?.photoUrl || '');
        const nextPhoto = 'cloudinary:' + p.fileId;
        await own.set({photoUrl: nextPhoto}, {merge: true});
        await db.doc('directory/' + uid).set({photoUrl: nextPhoto}, {merge: true});
        if (previousPhoto && previousPhoto !== nextPhoto) {
          await deleteOwnedProfileMedia(db, uid, previousPhoto);
        }
      }
      return res.json({url: 'cloudinary:' + p.fileId});
    }

    if (p.action === 'uploadAbort') {
      if (!/^[a-z0-9]{32}$/.test(p.fileId || '')) return res.json({ok: true});
      const metaRef = db.doc('media/' + p.fileId);
      const snap = await metaRef.get();
      if (!snap.exists) return res.json({ok: true});
      const meta = snap.data();
      if (meta?.owner !== uid) throw new Error('File access denied');
      if (meta.ready) throw new Error('Completed media cannot be aborted');
      await destroyCloudinary(meta).catch(() => {});
      await metaRef.delete().catch(() => {});
      return res.json({ok: true});
    }

    if (p.action === 'mediaAccess') {
      assertCloudinaryConfigured();
      const meta = (await db.doc('media/' + p.fileId).get()).data();
      if (!meta?.ready || meta.deleted) throw new Error('File not available');
      if (meta.conversationId) {
        let ok = false;
        const revoked = new Set(meta.revokedFrom || []);
        const mediaRef = 'cloudinary:' + p.fileId;
        for (const cid of [meta.conversationId, ...(meta.forwardedTo || [])]) {
          if (!cid || revoked.has(cid)) continue;
          try {
            const access = await allowed(db, cid, uid);
            const cutoff = Number(access.c.deletedBefore?.[uid] || 0);
            const candidates = await access.ref.collection('messages')
              .where('mediaUrl', '==', mediaRef)
              .limit(20)
              .get();
            const visible = candidates.docs.some(doc => {
              const message = doc.data();
              const createdAt = Number(message.createdAt || 0);
              return !message.deletedForEveryone
                && !(message.hiddenFor || []).includes(uid)
                && !(message.expiresAt && Number(message.expiresAt) <= now)
                && (!cutoff || createdAt > cutoff);
            });
            if (!visible) continue;
            ok = true;
            break;
          } catch {}
        }
        if (!ok) throw new Error('Media access denied');
      } else if (meta.owner !== uid) {
        const u = (await db.doc('users/' + meta.owner).get()).data();
        if (u?.privacy?.profilePhotoVisibility === 'Nobody' || u?.blockedUserIds?.includes(uid)) {
          throw new Error('Photo is private');
        }
      }
      const expiresAt = Math.floor(Date.now() / 1000) + 300;
      const url = cloudinary.utils.private_download_url(
        meta.publicId,
        meta.format || (meta.resourceType === 'raw' ? 'bin' : 'jpg'),
        {
          resource_type: meta.resourceType || 'raw',
          type: meta.deliveryType || 'authenticated',
          expires_at: expiresAt,
          attachment: false
        }
      );
      return res.json({url, validForMs: 240000});
    }
    if (p.action === 'report') {
      if (!p.otherUid || p.otherUid === uid || String(p.reason || '').trim().length < 4) throw new Error('Please enter a report reason');
      const reportRate = db.doc('_reportRate/' + uid);
      await db.runTransaction(async t => {
        const previous = (await t.get(reportRate)).data();
        const hour = Math.floor(now / 3600000);
        const sameHour = previous?.hour === hour;
        const count = sameHour ? Number(previous?.count || 0) : 0;
        if (count >= 5) throw new Error('Report limit reached. Try again later.');
        t.set(reportRate, {hour, count: count + 1, updatedAt: now});
      });
      await db.collection('reports').add({
        reporterId: uid,
        targetUid: p.otherUid,
        reason: String(p.reason).slice(0, 1000),
        createdAt: now,
        state: 'open'
      });
      return res.json({ok: true});
    }

    if (p.action === 'purgeUserData') {
      const result = await purgeUserData(
        db,
        uid,
        claims,
        p.installationId,
        now
      );
      return res.json(result);
    }

    if (p.action === 'deleteAccount') {
      if (now / 1000 - claims.auth_time > 300) throw new Error('Please sign out and sign in again before deleting your account');
      const cs = await db.collection('conversations').where('participantIds', 'array-contains', uid).get();
      for (const c of cs.docs) await db.recursiveDelete(c.ref);
      const ms = await db.collection('media').where('owner', '==', uid).get();
      for (const m of ms.docs) {
        await destroyCloudinary(m.data()).catch(() => {});
        await m.ref.delete();
      }
      const u = (await own.get()).data();
      if (u?.username) await db.doc('usernames/' + u.username).delete();
      await db.doc('_rate/' + uid).delete();
      await db.doc('_reportRate/' + uid).delete();
      await db.doc('directory/' + uid).delete();
      await own.delete();
      await getAuth().deleteUser(uid);
      return res.json({ok: true});
    }

    if (p.action === 'notifyExisting') {
      const access = await allowed(db, String(p.conversationId || ''), uid);
      const messageId = String(p.messageId || '');
      if (!messageId) throw new Error('Missing message');
      const messageRef = access.ref.collection('messages').doc(messageId);
      const message = (await messageRef.get()).data();
      if (!message || message.senderId !== uid) throw new Error('Message unavailable');
      await notify(db, p.conversationId, messageId)
        .then(() => messageRef.update({notificationPending: false}))
        .catch(() => {});
      return res.json({ok: true});
    }

    if (p.action === 'send' && typeof p.otherUid === 'string' && p.otherUid !== uid) {
      const ids = [uid, p.otherUid].sort();
      if (p.conversationId !== ids.join('_')) throw new Error('Invalid conversation');
      if (!(await db.doc('users/' + p.otherUid).get()).exists) throw new Error('Contact unavailable');
      const cr = db.doc('conversations/' + p.conversationId);
      await db.runTransaction(async t => {
        if (!(await t.get(cr)).exists) {
          t.set(cr, {
            participantIds: ids,
            lastMessageId: '',
            lastMessageTime: 0,
            lastMessageText: '',
            lastMessageSenderId: '',
            unreadCounts: {},
            deletedFor: [],
            deletedBefore: {}
          });
        }
      });
    }

    const {ref, c, other} = await allowed(db, p.conversationId, uid);

    if (p.action === 'deleteChat') {
      await ref.update({
        deletedFor: FieldValue.arrayUnion(uid),
        [`deletedBefore.${uid}`]: now,
        [`unreadCounts.${uid}`]: 0
      });
      return res.json({ok: true, deletedBefore: now});
    }

    if (p.action === 'authorizeForwardMedia') {
      const source = await allowed(db, String(p.conversationId || ''), uid);
      const target = await allowed(db, String(p.targetId || ''), uid);
      const sourceMessage = await source.ref.collection('messages')
        .doc(String(p.messageId || ''))
        .get();
      const message = sourceMessage.data();
      if (
        !message ||
        message.deletedForEveryone ||
        (message.hiddenFor || []).includes(uid) ||
        !String(message.mediaUrl || '').startsWith('cloudinary:')
      ) {
        throw new Error('Media unavailable');
      }

      const fileId = String(message.mediaUrl).replace('cloudinary:', '');
      const mediaRef = db.doc('media/' + fileId);
      const media = (await mediaRef.get()).data();
      if (!media?.ready) throw new Error('Media unavailable');

      await mediaRef.update({
        forwardedTo: FieldValue.arrayUnion(target.ref.id),
        revokedFrom: FieldValue.arrayRemove(target.ref.id)
      });
      return res.json({ok: true});
    }

    if (p.action === 'forward') {
      const sourceSnap = await ref.collection('messages').doc(String(p.messageId)).get();
      const source = sourceSnap.data();
      if (!source || source.deletedForEveryone || (source.hiddenFor || []).includes(uid) || source.expiresAt && source.expiresAt < now) {
        throw new Error('Message unavailable');
      }
      const target = await allowed(db, p.targetId, uid);
      if (source.mediaUrl?.startsWith('cloudinary:')) {
        const mr = db.doc('media/' + source.mediaUrl.replace('cloudinary:', ''));
        await mr.update({forwardedTo: FieldValue.arrayUnion(p.targetId)});
      }
      const u = (await own.get()).data();
      validateMessage({id: p.id, type: source.type, text: source.text});
      const tomb = db.doc(`messageTombstones/${p.targetId}_${p.id}`);
      await db.runTransaction(async t => {
        const dest = target.ref.collection('messages').doc(p.id);
        if ((await t.get(tomb)).exists) return;
        if ((await t.get(dest)).exists) return;
        t.set(dest, {
          senderId: uid,
          senderName: u?.displayName || 'User',
          text: source.text,
          type: source.type,
          mediaUrl: source.mediaUrl || '',
          voiceDurationSeconds: source.voiceDurationSeconds || 0,
          waveform: sanitizeWaveform(source.waveform),
          createdAt: now,
          status: 'SENT',
          isDeleted: false,
          deletedForEveryone: false,
          hiddenFor: [],
          isEdited: false,
          isPinned: false,
          reactions: [],
          notificationPending: true
        });
        t.update(target.ref, {
          lastMessageId: p.id,
          lastMessageText: previewFor(source),
          lastMessageTime: now,
          lastMessageSenderId: uid,
          [`unreadCounts.${target.other}`]: FieldValue.increment(1),
          deletedFor: FieldValue.arrayRemove(uid, target.other),
          [`hiddenLastFor.${uid}`]: FieldValue.delete(),
          [`hiddenLastFor.${target.other}`]: FieldValue.delete()
        });
      });
      return res.json({ok: true});
    }

    if (p.action === 'send') {
      validateMessage(p);
      if (p.mediaUrl) {
        const mm = (await db.doc('media/' + String(p.mediaUrl).replace('cloudinary:', '')).get()).data();
        if (!mm?.ready || mm.owner !== uid || mm.conversationId !== p.conversationId) throw new Error('Invalid media attachment');
        const prefix = expectedMimePrefix(p.type);
        const mime = String(mm.mimeType || '').toLowerCase();
        if (prefix && !mime.startsWith(prefix)) throw new Error(`Attachment type does not match ${p.type.toLowerCase()} message`);
        if (p.type === 'FILE' && !mime) throw new Error('Unknown attachment type');
      }
      if (p.type !== 'TEXT' && !p.mediaUrl) throw new Error('Upload media first');
      const u = (await own.get()).data();
      const mref = ref.collection('messages').doc(p.id);
      const tomb = db.doc(`messageTombstones/${p.conversationId}_${p.id}`);
      await db.runTransaction(async t => {
        if ((await t.get(tomb)).exists) return;
        const existing = await t.get(mref);
        const current = await t.get(ref);
        if (existing.exists) {
          if (existing.data().senderId !== uid) throw new Error('Invalid message ID');
          return;
        }
        const hiddenFor = [];
        for (const participant of c.participantIds) {
          const hidden = await t.get(db.doc(`messageHiddenTombstones/${p.conversationId}_${p.id}_${participant}`));
          if (hidden.exists) hiddenFor.push(participant);
        }
        const sec = current.data().disappearingSeconds || 0;
        const m = {
          senderId: uid,
          senderName: u?.displayName || 'User',
          text: p.text,
          type: p.type,
          mediaUrl: p.mediaUrl || '',
          voiceDurationSeconds: Math.min(600, Math.max(0, Number(p.voiceDurationSeconds) || 0)),
          waveform: p.type === 'VOICE' ? sanitizeWaveform(p.waveform) : [],
          createdAt: now,
          status: 'SENT',
          isDeleted: false,
          deletedForEveryone: false,
          hiddenFor,
          isEdited: false,
          isPinned: false,
          reactions: [],
          notificationPending: true
        };
        if (sec) m.expiresAt = now + sec * 1000;
        if (p.replyToId) {
          m.replyToId = String(p.replyToId);
          m.replyToText = String(p.replyToText || '').slice(0, 500);
          m.replyToSender = String(p.replyToSender || '').slice(0, 60);
        }
        t.set(mref, m);
        t.update(ref, {
          lastMessageId: p.id,
          lastMessageText: previewFor(m),
          lastMessageTime: now,
          lastMessageSenderId: uid,
          [`unreadCounts.${other}`]: FieldValue.increment(1),
          deletedFor: FieldValue.arrayRemove(uid, other),
          [`hiddenLastFor.${uid}`]: FieldValue.delete(),
          [`hiddenLastFor.${other}`]: FieldValue.delete()
        });
      });
      const created = await mref.get();
      if (created.exists) {
        await notify(db, p.conversationId, p.id).then(() => mref.update({notificationPending: false})).catch(() => {});
      }
      return res.json({ok: true, tombstoned: !created.exists});
    }

    const mref = ref.collection('messages').doc(String(p.messageId));

    if (p.action === 'deleteForMe') {
      const hiddenTomb = db.doc(`messageHiddenTombstones/${p.conversationId}_${p.messageId}_${uid}`);
      await db.runTransaction(async t => {
        const snap = await t.get(mref);
        const current = await t.get(ref);
        t.set(hiddenTomb, {
          conversationId: p.conversationId,
          messageId: String(p.messageId),
          uid,
          deletedAt: now
        }, {merge: true});
        if (snap.exists) t.update(mref, {hiddenFor: FieldValue.arrayUnion(uid)});
        if (current.data()?.lastMessageId === String(p.messageId)) {
          t.update(ref, {[`hiddenLastFor.${uid}`]: String(p.messageId)});
        }
      });
      return res.json({ok: true});
    }

    if (p.action === 'deleteForEveryone' || p.action === 'delete') {
      let mediaUrl = '';
      await db.runTransaction(async t => {
        const s = await t.get(mref);
        const m = s.data();
        if (m && m.senderId !== uid) throw new Error('Only the sender can do this');
        mediaUrl = m?.mediaUrl || '';
        const tomb = db.doc(`messageTombstones/${p.conversationId}_${p.messageId}`);
        t.set(tomb, {
          conversationId: p.conversationId,
          messageId: String(p.messageId),
          senderId: m?.senderId || uid,
          deletedBy: uid,
          deletedAt: now,
          type: m?.type || 'TEXT',
          mediaRef: mediaUrl || ''
        }, {merge: true});
        if (s.exists) t.delete(mref);
      });
      await revokeMediaFromConversation(db, mediaUrl, p.conversationId);
      await refreshConversationSummary(ref);
      await notifyDeletion(db, p.conversationId, String(p.messageId), uid);
      return res.json({ok: true});
    }

    if (['edit', 'react', 'pin', 'receipt'].includes(p.action)) {
      await db.runTransaction(async t => {
        const s = await t.get(mref);
        const m = s.data();
        if (!m || m.deletedForEveryone || (m.hiddenFor || []).includes(uid)) throw new Error('Message unavailable');
        const currentConversation = p.action === 'edit' ? await t.get(ref) : null;

        if (p.action === 'edit') {
          if (m.senderId !== uid) throw new Error('Only the sender can do this');
          if (m.type !== 'TEXT' || !String(p.text).trim() || String(p.text).length > 8000) throw new Error('Invalid edit');
          t.update(mref, {text: p.text, isEdited: true});
          if (currentConversation?.data()?.lastMessageId === String(p.messageId)) {
            t.update(ref, {lastMessageText: String(p.text).slice(0, 500)});
          }
        } else if (p.action === 'receipt') {
          if (m.senderId === uid) return;
          const u = (await t.get(own)).data();
          const nextStatus = p.status === 'READ' && u?.privacy?.readReceipts !== false ? 'READ' : 'DELIVERED';
          const rank = {SENT: 1, DELIVERED: 2, READ: 3};
          const currentStatus = Object.hasOwn(rank, m.status) ? m.status : 'SENT';
          if (rank[nextStatus] > rank[currentStatus]) t.update(mref, {status: nextStatus});
        } else if (p.action === 'pin') {
          t.update(mref, {isPinned: !m.isPinned});
        } else {
          if (!['❤️', '👍', '😂', '😮', '😢', '🙏'].includes(p.emoji)) throw new Error('Invalid reaction');
          const rs = (m.reactions || []).map(x => ({...x, userIds: x.userIds.filter(id => id !== uid)}));
          const had = (m.reactions || []).some(x => x.emoji === p.emoji && x.userIds.includes(uid));
          if (!had) {
            let r = rs.find(x => x.emoji === p.emoji);
            if (!r) {
              r = {emoji: p.emoji, userIds: []};
              rs.push(r);
            }
            r.userIds.push(uid);
          }
          t.update(mref, {reactions: rs.filter(x => x.userIds.length)});
        }
      });
      return res.json({ok: true});
    }

    if (p.action === 'conversationSetting') {
      if (['archivedFor', 'mutedFor', 'favoriteFor'].includes(p.field)) {
        await ref.update({[p.field]: p.value ? FieldValue.arrayUnion(uid) : FieldValue.arrayRemove(uid)});
      } else if (p.field === 'disappearingSeconds' && [0, 86400, 604800, 7776000].includes(p.value)) {
        await ref.update({disappearingSeconds: p.value});
      } else if (p.field === 'read') {
        await ref.update({[`unreadCounts.${uid}`]: 0});
      } else {
        throw new Error('Invalid setting');
      }
      return res.json({ok: true});
    }

    throw new Error('Unknown action');
  } catch (e) {
    error(e.message);
    return res.json({error: e.message || 'Request failed'}, e.code === 401 ? 401 : 400);
  }
};

export async function cleanup({res, error}) {
  try {
    const db = init();
    const expired = await db.collectionGroup('messages')
      .where('expiresAt', '>', 0)
      .where('expiresAt', '<=', Date.now())
      .limit(100)
      .get();
    for (const d of expired.docs) {
      const m = d.data();
      if (typeof m.expiresAt !== 'number') continue;
      const cid = d.ref.parent.parent.id;
      await revokeMediaFromConversation(db, m.mediaUrl, cid);
      await d.ref.delete();
      await refreshConversationSummary(d.ref.parent.parent);
    }

    const pending = await db.collectionGroup('messages')
      .where('notificationPending', '==', true)
      .limit(50)
      .get();
    for (const d of pending.docs) {
      try {
        await notify(db, d.ref.parent.parent.id, d.id);
        await d.ref.update({notificationPending: false});
      } catch {
        // Keep notificationPending=true; the next cleanup invocation retries transient failures.
      }
    }

    return res.json({ok: true});
  } catch (e) {
    error(e.message);
    return res.json({error: 'Cleanup failed'}, 500);
  }
}
