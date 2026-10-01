import test from 'node:test';
import assert from 'node:assert/strict';
import {isAudioOnlyMp4} from '../src/media-policy.js';
const box = (name, ...parts) => {
  const body = Buffer.concat(parts), head = Buffer.alloc(8);
  head.writeUInt32BE(body.length + 8); head.write(name, 4); return Buffer.concat([head, body]);
};
const track = type => box('trak', box('mdia', box('hdlr', Buffer.alloc(8), Buffer.from(type), Buffer.alloc(12))));
test('Android audio-only MP4 is accepted despite video/mp4 storage detection', () => {
  assert.equal(isAudioOnlyMp4(box('moov', track('soun'))), true);
});
test('video and mixed audio/video cannot be disguised as voice notes', () => {
  for (const tracks of [[track('vide')], [track('soun'), track('vide')]])
    assert.equal(isAudioOnlyMp4(box('moov', ...tracks)), false);
});
test('empty, malformed and oversized container boxes are rejected', () => {
  for (const bytes of [Buffer.alloc(0), Buffer.from('fake.m4a'), Buffer.from([255,255,255,255,109,111,111,118])])
    assert.equal(isAudioOnlyMp4(bytes), false);
  const truncated = box('moov', track('soun')).subarray(0, 20);
  assert.equal(isAudioOnlyMp4(truncated), false);
});
