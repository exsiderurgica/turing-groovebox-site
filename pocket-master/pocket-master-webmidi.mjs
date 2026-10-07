import { parseFullSysex, wrapDecoded, VERSION_REPEAT } from './pocket-master-core.mjs';

const sleep = ms => new Promise(r => setTimeout(r, ms));

export class MidiLink {
  constructor(input, output) {
    this.input = input; this.output = output; this.queue = []; this.waiters = [];
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

export function listPairs(access) {
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

export function scorePort(port) {
  const t = `${norm(port.manufacturer)} ${norm(port.name)}`;
  if (t.includes('pocket master')) return 100;
  if (t.includes('sonicake')) return 90;
  if (t.includes('hotone')) return 70;
  if (t.includes('usb-midi') || t.includes('usb midi')) return 20;
  return 0;
}

export function describePair(p) {
  return `${p.input.manufacturer || '?'} / ${p.input.name || 'MIDI'} · in=${p.input.id.slice(0,8)} out=${p.output.id.slice(0,8)}`;
}

export async function openPair(pair) {
  await pair.input.open(); await pair.output.open();
  return new MidiLink(pair.input, pair.output);
}

export async function requestSysexAccess() {
  if (!navigator.requestMIDIAccess) throw new Error('WebMIDI is not supported by this browser. Use Chrome/Chromium.');
  const access = await navigator.requestMIDIAccess({ sysex: true, software: false });
  if (!access.sysexEnabled) throw new Error('MIDI access was granted without SysEx permission.');
  return access;
}

export async function enterBootloader(access, plan, selectedPair, onLog = () => {}) {
  const pair = selectedPair || listPairs(access)[0];
  if (!pair) throw new Error('Pocket Master MIDI input/output pair not found.');
  if (pair.score < 70 && listPairs(access).length > 1) throw new Error('Could not uniquely identify Pocket Master. Disconnect other MIDI devices and retry.');
  const oldIds = new Set([pair.input.id, pair.output.id]);
  const link = await openPair(pair);
  onLog(`Normal MIDI: ${describePair(pair)}`);
  for (let i = 0; i < VERSION_REPEAT; i++) {
    link.send(wrapDecoded(plan.versionDecoded));
    if (i !== VERSION_REPEAT - 1) await sleep(20);
  }
  link.close();
  return oldIds;
}

export async function waitForBootloaderPair(access, oldIds, timeoutMs = 12000, onLog = () => {}) {
  const deadline = Date.now() + timeoutMs;
  let lastPairs = [];
  while (Date.now() < deadline) {
    const pairs = listPairs(access);
    lastPairs = pairs;
    let p = pairs.find(x => !oldIds.has(x.input.id) || !oldIds.has(x.output.id));
    if (!p && Date.now() + 4000 > deadline && pairs.length === 1) p = pairs[0];
    if (p) { onLog(`Bootloader MIDI candidate: ${describePair(p)}`); return p; }
    await sleep(150);
  }
  throw new Error(`Bootloader MIDI did not appear. Visible pairs: ${lastPairs.map(describePair).join(' | ') || 'none'}`);
}

export async function waitForNormalPair(access, timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const pairs = listPairs(access);
    const p = pairs.find(x => x.score >= 70) || (pairs.length === 1 ? pairs[0] : null);
    if (p) return p;
    await sleep(200);
  }
  return null;
}
