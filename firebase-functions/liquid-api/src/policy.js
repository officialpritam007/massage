import {createHash} from 'node:crypto';
export const appUser = uid => 'u' + createHash('sha256').update(uid).digest('hex').slice(0,31);
export function assertPair(ids, uid) {
  if (!Array.isArray(ids) || ids.length !== 2 || new Set(ids).size !== 2 || !ids.includes(uid)) throw new Error('Conversation access denied');
}
export function validateMessage(m) {
  if (!/^[a-zA-Z0-9_-]{8,80}$/.test(m.id || '')) throw new Error('Invalid message ID');
  if (!['TEXT','IMAGE','VIDEO','VOICE','FILE'].includes(m.type)) throw new Error('Unsupported message type');
  if (typeof m.text !== 'string' || m.text.length > 8000 || (m.type === 'TEXT' && !m.text.trim())) throw new Error('Message must contain 1–8000 characters');
}
export const fresh = (time, now, ttl) => typeof time === 'number' && time > now - ttl && time <= now + 5000;
