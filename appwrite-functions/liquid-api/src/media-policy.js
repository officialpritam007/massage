// Inspect ISO-BMFF track handlers rather than trusting a filename/client MIME.
// Android records AAC in an MP4 container which some storage detectors label video/mp4.
export function isAudioOnlyMp4(bytes) {
  const b = Buffer.from(bytes);
  let audio = false, video = false, boxes = 0;
  function walk(start, end, depth) {
    if (depth > 8) throw new Error('Invalid media container');
    for (let at = start; at + 8 <= end;) {
      if (++boxes > 10000) throw new Error('Invalid media container');
      let size = b.readUInt32BE(at), header = 8;
      const type = b.toString('ascii', at + 4, at + 8);
      if (size === 1) {
        if (at + 16 > end) throw new Error('Invalid media container');
        size = Number(b.readBigUInt64BE(at + 8)); header = 16;
      } else if (size === 0) size = end - at;
      if (!Number.isSafeInteger(size) || size < header || at + size > end) throw new Error('Invalid media container');
      if (['moov', 'trak', 'mdia'].includes(type)) walk(at + header, at + size, depth + 1);
      if (type === 'hdlr' && size >= header + 12) {
        const handler = b.toString('ascii', at + header + 8, at + header + 12);
        audio ||= handler === 'soun'; video ||= handler === 'vide';
      }
      at += size;
    }
  }
  try { walk(0, b.length, 0); } catch { return false; }
  return audio && !video;
}
