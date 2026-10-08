import { parseFullSysex, wrapDecoded, VERSION_REPEAT } from './pocket-master-core.mjs';

const sleep = ms => new Promise(r => setTimeout(r, ms));
const now = () => Date.now();

function withTimeout(promise, ms, label) {
  let timer;
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      timer = setTimeout(() => reject(new Error(`${label} timed out after ${ms} ms`)), ms);
    })
  ]).finally(() => clearTimeout(timer));
}

export class MidiLink {
  constructor(input, output) {
    this.input = input;
    this.output = output;
    this.queue = [];
    this.waiters = [];
    this.onMessage = ev => {
      const bytes = new Uint8Array(ev.data);
      if (bytes[0] !== 0xF0) return;
      const p = parseFullSysex(bytes);
      if (!p) return;
      if (this.waiters.length) this.waiters.shift()(p);
      else this.queue.push(p);
    };
    input.onmidimessage = this.onMessage;
  }
  send(bytes) { this.output.send([...bytes]); }
  async readPacket(ms) {
    if (this.queue.length) return this.queue.shift();
    return await new Promise(resolve => {
      let done = false;
      const finish = v => { if (done) return; done = true; clearTimeout(timer); resolve(v); };
      const timer = setTimeout(() => finish(null), ms);
      this.waiters.push(finish);
    });
  }
  close() {
    this.input.onmidimessage = null;
    try { this.input.close?.(); } catch (_) {}
    try { this.output.close?.(); } catch (_) {}
  }
}

function norm(s) { return (s || '').toLowerCase(); }
function pairKey(p) { return `${p.manufacturer || ''}|${p.name || ''}`.toLowerCase(); }

export function scorePort(port) {
  const t = `${norm(port.manufacturer)} ${norm(port.name)}`;
  if (t.includes('pocket master')) return 100;
  if (t.includes('sonicake')) return 90;
  if (t.includes('hotone')) return 70;
  if (t.includes('usb-midi') || t.includes('usb midi')) return 20;
  return 0;
}

export function listPairs(access) {
  if (!access) return [];
  const ins = [...access.inputs.values()].filter(p => p.state !== 'disconnected');
  const outs = [...access.outputs.values()].filter(p => p.state !== 'disconnected');
  const pairs = [];
  for (const input of ins) {
    let output = outs.find(o => o.name === input.name && o.manufacturer === input.manufacturer);
    if (!output) output = outs.find(o => o.name === input.name);
    if (!output && ins.length === 1 && outs.length === 1) output = outs[0];
    if (output) pairs.push({ input, output, key: pairKey(input), score: scorePort(input) });
  }
  return pairs.sort((a, b) => b.score - a.score);
}

export function describePort(p) {
  if (!p) return '(null)';
  return `${p.type || '?'} ${p.manufacturer || '?'} / ${p.name || 'MIDI'} id=${p.id} state=${p.state || '?'} conn=${p.connection || '?'}`;
}

export function describePair(p) {
  return `${p.input.manufacturer || '?'} / ${p.input.name || 'MIDI'} · in=${String(p.input.id).slice(0,12)} out=${String(p.output.id).slice(0,12)}`;
}

export function snapshot(access) {
  if (!access) return 'MIDI access: none';
  const lines = [`sysex=${!!access.sysexEnabled}`];
  for (const p of access.inputs.values()) lines.push(`IN  ${describePort(p)}`);
  for (const p of access.outputs.values()) lines.push(`OUT ${describePort(p)}`);
  return lines.join('\n');
}

export async function openPair(pair, timeoutMs = 3500) {
  if (!pair) throw new Error('No MIDI pair to open.');
  await withTimeout(Promise.resolve(pair.input.open()), timeoutMs, 'MIDI input open');
  try {
    await withTimeout(Promise.resolve(pair.output.open()), timeoutMs, 'MIDI output open');
  } catch (e) {
    try { pair.input.close?.(); } catch (_) {}
    throw e;
  }
  return new MidiLink(pair.input, pair.output);
}

export async function requestSysexAccess() {
  if (!navigator.requestMIDIAccess) throw new Error('WebMIDI is not supported by this browser. Use Chrome/Chromium.');
  const access = await navigator.requestMIDIAccess({ sysex: true, software: false });
  if (!access.sysexEnabled) throw new Error('MIDI access was granted without SysEx permission.');
  return access;
}

export async function enterBootloader(access, plan, selectedPair, onLog = () => {}) {
  const pairs = listPairs(access);
  const pair = selectedPair || pairs[0];
  if (!pair) throw new Error('Pocket Master MIDI input/output pair not found.');
  if (pair.score < 70 && pairs.length > 1) throw new Error('Could not uniquely identify Pocket Master. Disconnect other MIDI devices and retry.');
  const oldIds = new Set([pair.input.id, pair.output.id]);
  const link = await openPair(pair);
  onLog(`Normal MIDI: ${describePair(pair)}`);
  onLog(`Normal IDs: ${[...oldIds].join(' | ')}`);
  for (let i = 0; i < VERSION_REPEAT; i++) {
    link.send(wrapDecoded(plan.versionDecoded));
    if (i !== VERSION_REPEAT - 1) await sleep(20);
  }
  link.close();
  return { oldIds, announcedAt: now() };
}

export function bootCandidate(access, oldIds, { allowSameId = false } = {}) {
  const pairs = listPairs(access);
  let p = pairs.find(x => !oldIds?.has(x.input.id) || !oldIds?.has(x.output.id));
  if (!p && allowSameId && pairs.length === 1) p = pairs[0];
  return p || null;
}

export async function waitForBootloaderPair(access, oldIds, options = {}) {
  const timeoutMs = options.timeoutMs ?? 7000;
  const allowSameAfterMs = options.allowSameAfterMs ?? 3500;
  const pollMs = options.pollMs ?? 120;
  const onLog = options.onLog || (() => {});
  const onPulse = options.onPulse || (() => {});
  const started = now();
  const deadline = started + timeoutMs;
  let lastSummary = '';
  while (now() < deadline) {
    const elapsed = now() - started;
    const pairs = listPairs(access);
    const summary = pairs.map(describePair).join(' | ') || 'none';
    if (summary !== lastSummary) {
      lastSummary = summary;
      onLog(`MIDI scan: ${summary}`);
    }
    let p = pairs.find(x => !oldIds?.has(x.input.id) || !oldIds?.has(x.output.id));
    if (!p && elapsed >= allowSameAfterMs && pairs.length === 1) p = pairs[0];
    if (p) {
      onLog(`Bootloader MIDI candidate: ${describePair(p)}${oldIds?.has(p.input.id) && oldIds?.has(p.output.id) ? ' (recycled IDs)' : ' (new IDs)'}`);
      return p;
    }
    onPulse(elapsed, timeoutMs);
    await sleep(pollMs);
  }
  return null;
}

export async function reacquireSysexAccess(previousAccess, onLog = () => {}) {
  let access = previousAccess;
  try {
    access = await requestSysexAccess();
  } catch (e) {
    onLog(`Fresh MIDI request failed: ${e.message}`);
    throw e;
  }
  onLog(`Fresh MIDI access acquired${access === previousAccess ? ' (same access object)' : ' (new access object)'}.`);
  onLog(snapshot(access));
  return access;
}

export async function waitForNormalPair(access, timeoutMs = 15000) {
  const deadline = now() + timeoutMs;
  while (now() < deadline) {
    const pairs = listPairs(access);
    const p = pairs.find(x => x.score >= 70) || (pairs.length === 1 ? pairs[0] : null);
    if (p) return p;
    await sleep(200);
  }
  return null;
}
