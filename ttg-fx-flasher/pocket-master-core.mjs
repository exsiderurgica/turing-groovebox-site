export const OFFICIAL_V133_SHA256 = 'd9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3';
export const DATA_CHUNK = 19;
export const VERSION_REPEAT = 10;
export const ACK_TIMEOUT_MS = 1500;
export const MAX_ATTEMPTS = 3;

const REGION_TABLE = 0x38;
const REGION_DESC_SIZE = 16;

export function u16le(bytes, o) {
  return bytes[o] | (bytes[o + 1] << 8);
}

export function u32le(bytes, o) {
  if (o < 0 || o + 4 > bytes.length) return null;
  return (bytes[o] | (bytes[o + 1] << 8) | (bytes[o + 2] << 16) | (bytes[o + 3] << 24)) >>> 0;
}

export function fletcher16(bytes, offset = 8) {
  let sum1 = 0, sum2 = 0;
  for (let i = offset; i < bytes.length; i++) {
    sum1 = (sum1 + bytes[i]) % 255;
    sum2 = (sum2 + sum1) % 255;
  }
  return ((sum2 << 8) | sum1) & 0xffff;
}

export function crc16Modbus(bytes, offset = 0, length = bytes.length - offset) {
  let crc = 0xffff;
  for (let i = offset; i < offset + length; i++) {
    crc ^= bytes[i];
    for (let b = 0; b < 8; b++) crc = (crc & 1) ? ((crc >>> 1) ^ 0xA001) : (crc >>> 1);
  }
  return crc & 0xffff;
}

export function crc8(bytes, offset = 0, length = bytes.length - offset) {
  let crc = 0;
  for (let i = offset; i < offset + length; i++) {
    crc ^= bytes[i];
    for (let b = 0; b < 8; b++) crc = (crc & 0x80) ? (((crc << 1) ^ 0x07) & 0xff) : ((crc << 1) & 0xff);
  }
  return crc & 0xff;
}

export function nibbleEncode(raw) {
  const out = new Uint8Array(raw.length * 2);
  let j = 0;
  for (const v of raw) {
    out[j++] = (v >>> 4) & 0x0f;
    out[j++] = v & 0x0f;
  }
  return out;
}

export function nibbleDecode(payload) {
  if (payload.length % 2) throw new Error('Odd nibble payload length');
  const out = new Uint8Array(payload.length / 2);
  for (let i = 0, j = 0; i < payload.length; i += 2, j++) {
    const hi = payload[i], lo = payload[i + 1];
    if (hi > 0x0f || lo > 0x0f) throw new Error('Non-nibble byte in SysEx payload');
    out[j] = (hi << 4) | lo;
  }
  return out;
}

export function buildDecoded(field1, sequence, body) {
  if (body.length > 255) throw new Error('Body too long');
  const raw = new Uint8Array(4 + body.length);
  raw[1] = field1 & 0xff;
  raw[2] = sequence & 0xff;
  raw[3] = body.length & 0xff;
  raw.set(body, 4);
  raw[0] = crc8(raw, 1, raw.length - 1);
  return raw;
}

export function parseDecoded(raw) {
  if (raw.length < 4) throw new Error('Packet shorter than 4-byte header');
  const declared = raw[3];
  if (raw.length !== 4 + declared) throw new Error(`Length mismatch: ${raw.length} vs ${4 + declared}`);
  const expected = crc8(raw, 1, raw.length - 1);
  return {
    crc: raw[0], field1: raw[1], sequence: raw[2], length: declared,
    body: raw.slice(4), crcValid: expected === raw[0]
  };
}

export function wrapDecoded(decoded) {
  const n = nibbleEncode(decoded);
  const out = new Uint8Array(n.length + 2);
  out[0] = 0xF0;
  out.set(n, 1);
  out[out.length - 1] = 0xF7;
  return out;
}

export function parseFullSysex(full) {
  if (!full || full.length < 4 || full[0] !== 0xF0 || full[full.length - 1] !== 0xF7) return null;
  try { return parseDecoded(nibbleDecode(full.slice(1, -1))); } catch (_) { return null; }
}

export function isObservedAck(packet) {
  return !!packet && packet.crcValid && packet.field1 === 0x01 && packet.sequence === 0x00 && packet.length === 3 &&
    packet.body[0] === 0x14 && packet.body[1] === 0x08 && packet.body[2] === 0x00;
}

export async function sha256Hex(bytes) {
  if (globalThis.crypto?.subtle) {
    const buf = await crypto.subtle.digest('SHA-256', bytes);
    return [...new Uint8Array(buf)].map(x => x.toString(16).padStart(2, '0')).join('');
  }
  const { createHash } = await import('node:crypto');
  return createHash('sha256').update(bytes).digest('hex');
}

export async function inspectHtfw(bytes) {
  const magic = bytes.length >= 4 && String.fromCharCode(...bytes.slice(0, 4)) === 'HTFW';
  const topLevelField = bytes.length >= 8 ? u32le(bytes, 4) : null;
  const fletcher = bytes.length >= 8 ? fletcher16(bytes, 8) : null;
  const sha256 = await sha256Hex(bytes);
  let versionTag = null;
  if (magic && bytes.length >= 0x20 && bytes[0x1c] === 0x56) {
    versionTag = `V${bytes[0x1d]}${bytes[0x1e]}${bytes[0x1f]}`;
  }
  const count = magic && bytes.length >= 0x24 ? u16le(bytes, 0x22) : 0;
  const payloadBase = count > 0 && count <= 32 && REGION_TABLE + count * REGION_DESC_SIZE <= bytes.length
    ? REGION_TABLE + count * REGION_DESC_SIZE : null;
  const regions = [];
  if (payloadBase != null) {
    for (let i = 0; i < count; i++) {
      const o = REGION_TABLE + i * REGION_DESC_SIZE;
      const storedCrc16 = ((bytes[o] << 8) | bytes[o + 1]) & 0xffff;
      const id = String.fromCharCode(bytes[o + 3]);
      const address = u32le(bytes, o + 4);
      const payloadOffset = u32le(bytes, o + 8);
      const length = u32le(bytes, o + 12);
      const start = payloadBase + payloadOffset;
      const end = start + length;
      const calculatedCrc16 = start >= 0 && end <= bytes.length ? crc16Modbus(bytes, start, length) : -1;
      regions.push({ id, storedCrc16, address, payloadOffset, length, calculatedCrc16, crcValid: calculatedCrc16 === storedCrc16 });
    }
  }
  return {
    isHtfw: magic, size: bytes.length, topLevelField, fletcher16: fletcher, sha256,
    looksLikeV133: magic && bytes.length === 2056820 && topLevelField === 0x0001AC24 && versionTag === 'V133',
    versionTag, payloadBase, regions, allRegionCrcsValid: regions.length > 0 && regions.every(r => r.crcValid)
  };
}

export function regionBytes(bytes, info, region) {
  if (info.payloadBase == null) throw new Error('No HTFW payload base');
  const start = info.payloadBase + region.payloadOffset;
  const end = start + region.length;
  if (start < 0 || end > bytes.length) throw new Error(`Region ${region.id} outside file`);
  return bytes.slice(start, end);
}

function u32leBytes(v) {
  return new Uint8Array([v & 0xff, (v >>> 8) & 0xff, (v >>> 16) & 0xff, (v >>> 24) & 0xff]);
}

function concat(...parts) {
  const len = parts.reduce((n, p) => n + p.length, 0);
  const out = new Uint8Array(len);
  let o = 0;
  for (const p of parts) { out.set(p, o); o += p.length; }
  return out;
}

export async function makePlan(bytes) {
  const info = await inspectHtfw(bytes);
  if (!info.isHtfw) throw new Error('Not an HTFW image');
  if (!info.allRegionCrcsValid) throw new Error('One or more regional CRC16 checks failed');
  if (!info.regions.length) throw new Error('No HTFW regions');
  const version = info.versionTag;
  if (!version || version.length !== 4 || version[0] !== 'V') throw new Error(`Unsupported version format: ${version}`);
  const enc = new TextEncoder();
  const versionBody = concat(new Uint8Array([0x11, 0x69]), enc.encode(version + '\0'));
  const versionDecoded = buildDecoded(0x01, 0x00, versionBody);
  const startDecoded = buildDecoded(0x01, 0x00, new Uint8Array([0x11, 0x60]));
  const regions = info.regions.map(r => {
    const streamLength = r.length + 2;
    const packetCount = Math.ceil(streamLength / DATA_CHUNK);
    const setupBody = concat(
      new Uint8Array([0x11, 0x61, r.id.charCodeAt(0), 0, 0, 0]),
      u32leBytes(packetCount), u32leBytes(streamLength)
    );
    const finishBody = new Uint8Array([0x11, 0x6e, r.id.charCodeAt(0), 0]);
    return { region: r, streamLength, packetCount,
      setupDecoded: buildDecoded(0x01, 0x00, setupBody),
      finishDecoded: buildDecoded(0x01, 0x00, finishBody) };
  });
  const finalDecoded = buildDecoded(0x01, 0x00, new Uint8Array([0x11, 0x6e, 0x6e, 0]));
  const totalDataPackets = regions.reduce((n, r) => n + r.packetCount, 0);
  const totalOutboundFrames = VERSION_REPEAT + 1 + regions.reduce((n, r) => n + 2 + r.packetCount, 0) + 1;
  return { info, versionDecoded, startDecoded, regions, finalDecoded, totalDataPackets, totalOutboundFrames };
}

export async function* regionFrames(bytes, plan, plannedRegion) {
  yield plannedRegion.setupDecoded;
  const raw = regionBytes(bytes, plan.info, plannedRegion.region);
  const stream = concat(new Uint8Array([0x11, plannedRegion.region.id.charCodeAt(0)]), raw);
  let offset = 0, index = 0;
  while (offset < stream.length) {
    const n = Math.min(DATA_CHUNK, stream.length - offset);
    yield buildDecoded(0xff, index & 0xff, stream.slice(offset, offset + n));
    offset += n;
    index++;
  }
  if (index !== plannedRegion.packetCount) throw new Error('Packet count mismatch');
  yield plannedRegion.finishDecoded;
}

export function hex(bytes) { return [...bytes].map(x => x.toString(16).padStart(2, '0').toUpperCase()).join(' '); }

export async function transferFirmware(firmware, session, observer = {}, signal = null) {
  const plan = await makePlan(firmware);
  const totalAckedFrames = 1 + plan.regions.reduce((n, r) => n + r.packetCount + 2, 0) + 1;
  let sent = 0, retries = 0, done = 0;
  const state = s => observer.state?.(s);
  const progress = () => observer.progress?.(done, totalAckedFrames);
  const log = s => observer.log?.(s);
  const cancelled = () => signal?.aborted;

  async function txAck(label, decoded) {
    let lastProblem = 'no ACK';
    for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      if (cancelled()) throw new DOMException('Operation cancelled', 'AbortError');
      session.send(wrapDecoded(decoded)); sent++;
      const deadline = performance.now() + ACK_TIMEOUT_MS;
      while (performance.now() < deadline) {
        const remaining = Math.max(1, Math.floor(deadline - performance.now()));
        const p = await session.readPacket(remaining);
        if (!p) break;
        if (isObservedAck(p)) {
          if (attempt > 1) {
            retries += attempt - 1;
            while (await session.readPacket(15)) { }
          }
          return;
        }
        lastProblem = `unexpected packet field=${p.field1.toString(16)} seq=${p.sequence.toString(16)} len=${p.length} crc=${p.crcValid}`;
      }
      log(`${label}: ACK timeout/invalid response, attempt ${attempt}/${MAX_ATTEMPTS}`);
    }
    throw new Error(`${label} failed after ${MAX_ATTEMPTS} attempts (${lastProblem})`);
  }

  state('START'); await txAck('START', plan.startDecoded); done++; progress();
  for (const pr of plan.regions) {
    state(`REGION ${pr.region.id} SETUP`); await txAck(`REGION ${pr.region.id} SETUP`, pr.setupDecoded); done++; progress();
    const raw = regionBytes(firmware, plan.info, pr.region);
    const stream = concat(new Uint8Array([0x11, pr.region.id.charCodeAt(0)]), raw);
    let offset = 0, index = 0;
    state(`REGION ${pr.region.id} DATA`);
    while (offset < stream.length) {
      if (cancelled()) throw new DOMException('Operation cancelled', 'AbortError');
      const n = Math.min(DATA_CHUNK, stream.length - offset);
      const decoded = buildDecoded(0xff, index & 0xff, stream.slice(offset, offset + n));
      await txAck(`REGION ${pr.region.id} DATA #${index}`, decoded);
      offset += n; index++; done++;
      if ((index & 0x7f) === 0 || offset === stream.length) progress();
    }
    if (index !== pr.packetCount) throw new Error('Packet count mismatch');
    state(`REGION ${pr.region.id} FINISH`); await txAck(`REGION ${pr.region.id} FINISH`, pr.finishDecoded); done++; progress();
  }
  state('FINALIZE'); await txAck('GLOBAL FINISH', plan.finalDecoded); done++; progress();
  return { packetsSent: sent, retries, plan };
}
