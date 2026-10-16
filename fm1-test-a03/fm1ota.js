// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Leo Kuroshita (@kurogedelic), Hügelton Instruments
// Source pinned from SLOOP/Felucca tooling: https://github.com/isod89/sloop-fm1

const HS_QUERY = [0xF0, 0x00, 0x32, 0x45, 0x00, 0x00, 0x00, 0x40, 0x7F, 0xF7];
const UPGRADE = [0xF0, 0x22, 0x24, 0x35, 0x7F, 0xF7];
const MAXDATA = 512;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const IS_OTA = (id) => /^ota-/i.test(id.model);
export const OUR_LOADER = (id) => /^ota-FM-1_9\d\d$/i.test(id.text);
export const FM1_PORT = /fm-1|felucca|sloop|clipper|ota|composite|sinco|usb-midi/i;
function fail(code, msg, detail = "") { const e = new Error(msg); e.code = code; e.detail = detail; return e; }

export function pack7(data) {
  const out = []; let acc = 0, nb = 0;
  for (const b of data) {
    acc |= b << nb; nb += 8;
    while (nb >= 7) { out.push(acc & 0x7F); acc >>>= 7; nb -= 7; }
  }
  if (nb) out.push(acc & 0x7F);
  return out;
}

export function unpack7(s) {
  const out = []; let acc = 0, nb = 0;
  for (const b of s) {
    acc |= (b & 0x7F) << nb; nb += 7;
    while (nb >= 8) { out.push(acc & 0xFF); acc >>>= 8; nb -= 8; }
  }
  return out;
}

function response(addr, data, fl = 0) {
  const len = data.length;
  const body = [0x00, 0x59, 0x30, (len + 8) & 0xFF, ((len + 8) >> 8) & 0xFF, 0, fl,
    addr & 0xFF, (addr >>> 8) & 0xFF, (addr >>> 16) & 0xFF, (addr >>> 24) & 0xFF,
    len & 0xFF, (len >> 8) & 0xFF, 0, ...data];
  let s = 0;
  for (let i = 6; i < body.length; i++) s += body[i];
  body.push(~s & 0xFF);
  return [0xF0, ...pack7(body), 0xF7];
}

function parseRequest(pkt) {
  if (pkt[0] !== 0xF0 || pkt[pkt.length - 1] !== 0xF7) return null;
  const u = unpack7(pkt.slice(1, -1));
  if (u.length !== 15 || u[0] !== 0 || u[1] !== 0x59 || u[2] !== 0x30) return null;
  let s = 0;
  for (let i = 6; i < 14; i++) s += u[i];
  if ((~s & 0xFF) !== u[14]) return null;
  return { fl: u[6], addr: (u[7] | (u[8] << 8) | (u[9] << 16) | (u[10] << 24)) >>> 0, len: u[11] | (u[12] << 8) | (u[13] << 16) };
}

function parseIdentity(pkt) {
  const d = unpack7(pkt.slice(1, -1));
  if (d.length !== 34 || d[0] !== 0 || d[1] !== 0x59 || d[2] !== 0x11) return null;
  const txt = String.fromCharCode(...d.slice(6, 33)).replace(/\0+$/, "");
  const m = /^([^_]+)_(\d+)$/.exec(txt);
  return m ? { text: txt, model: m[1], version: parseInt(m[2], 10) } : null;
}

class Link {
  constructor(input, output) {
    this.input = input; this.output = output; this.q = []; this.waiter = null;
    input.onmidimessage = (ev) => {
      if (ev.data[0] !== 0xF0) return;
      this.q.push(Array.from(ev.data));
      if (this.waiter) { const w = this.waiter; this.waiter = null; w(); }
    };
  }
  get lost() { return this.input.state === "disconnected" || this.output.state === "disconnected"; }
  send(bytes) { try { this.output.send(bytes); return true; } catch (_) { return false; } }
  drain() { this.q.length = 0; }
  async read(ms) {
    if (!this.q.length) {
      let tm;
      await Promise.race([new Promise((r) => { this.waiter = r; }), new Promise((r) => { tm = setTimeout(r, ms); })]);
      clearTimeout(tm);
      this.waiter = null;
    }
    return this.q.shift() || null;
  }
  close() { this.input.onmidimessage = null; }
}

async function handshake(link, tries = 3) {
  link.drain();
  for (let t = 0; t < tries; t++) {
    if (!link.send(HS_QUERY)) return null;
    const end = Date.now() + 1000;
    while (Date.now() < end) {
      const p = await link.read(end - Date.now());
      if (!p) break;
      const id = parseIdentity(p);
      if (id) return id;
    }
  }
  return null;
}

export class Updater {
  constructor(access, log) { this.access = access; this.log = log || (() => {}); }
  async find(filter) {
    const outs = [...this.access.outputs.values()];
    for (const input of this.access.inputs.values()) {
      if (input.state === "disconnected") continue;
      if (!FM1_PORT.test(input.name || "")) continue;
      const output = outs.find((o) => o.state !== "disconnected" && o.name === input.name) ||
        (outs.filter((o) => o.state !== "disconnected").length === 1 ? outs.find((o) => o.state !== "disconnected") : null);
      if (!output) continue;
      try { await input.open(); await output.open(); } catch (_) { continue; }
      const link = new Link(input, output);
      const id = await handshake(link, 2);
      if (id && (!filter || filter(id))) return { link, id, name: input.name };
      link.close();
    }
    return null;
  }
  async openFeluccaDirect() {
    const ins = [...this.access.inputs.values()].filter((p) => p.state !== "disconnected" && /felucca/i.test(p.name || ""));
    const outs = [...this.access.outputs.values()].filter((p) => p.state !== "disconnected");
    if (ins.length !== 1) return null;
    const input = ins[0];
    const output = outs.find((o) => o.name === input.name) || outs.find((o) => /felucca/i.test(o.name || "")) || (outs.length === 1 ? outs[0] : null);
    if (!output) return null;
    try { await input.open(); await output.open(); } catch (_) { return null; }
    return { link: new Link(input, output), id: null, name: input.name };
  }
  seen() {
    const ins = [...this.access.inputs.values()].filter((p) => p.state !== "disconnected").map((p) => p.name || "?");
    const outs = [...this.access.outputs.values()].filter((p) => p.state !== "disconnected").map((p) => p.name || "?");
    if (!ins.length && !outs.length) return " (MIDI: none)";
    return ` (MIDI IN: ${ins.join(", ") || "none"}; OUT: ${outs.join(", ") || "none"})`;
  }
  async waitFor(filter, ms) {
    const end = Date.now() + ms;
    while (Date.now() < end) {
      const dev = await this.find(filter);
      if (dev) return dev;
      await sleep(1000);
    }
    return null;
  }
  async serve(link, image, finishAddr, idleMs, onProgress) {
    let served = 0, last = Date.now();
    for (;;) {
      const pkt = await link.read(1000);
      if (!pkt) {
        if (link.lost) return { served, finished: false, lost: true };
        if (Date.now() - last > idleMs) return { served, finished: false };
        continue;
      }
      const r = parseRequest(pkt);
      if (!r) continue;
      last = Date.now();
      if (r.addr === 0xE0000000 || r.addr === 0xF0000000) {
        link.send(response(r.addr, [...new TextEncoder().encode("success"), 0]));
        if (r.addr === finishAddr) return { served, finished: true };
        continue;
      }
      if (r.len > MAXDATA || r.addr + r.len > image.length) throw fail("badreq", `bad request ${r.addr.toString(16)}+${r.len}`);
      await sleep(10);
      if (!link.send(response(r.addr, Array.from(image.subarray(r.addr, r.addr + r.len)), r.fl))) return { served, finished: false, lost: true };
      served++;
      if (onProgress) onProgress(served, r.addr);
    }
  }
  async install(image, product, onStep, opts = {}) {
    const step = onStep || (() => {});
    let dev = await this.find((id) => !IS_OTA(id));
    let verified = true;
    if (!dev && opts.allowUnverifiedFelucca) {
      dev = await this.openFeluccaDirect();
      if (dev) {
        verified = false;
        this.log(`Identity handshake unavailable; using direct OTA bootstrap on ${dev.name}`);
      }
    }
    if (!dev) throw fail("notfound", "FM-1 not found (USB cable, and no other app using it?)", this.seen());
    const [model] = product.split("_");
    if (verified && dev.id.model !== model) { dev.link.close(); throw fail("model", `the device is ${dev.id.text}, the package is for ${product}`); }
    step("start", verified ? dev.id.text : `${dev.name} · direct OTA`);
    if (!dev.link.send(UPGRADE)) { dev.link.close(); throw fail("send", "Could not send the FM-1 upgrade command", this.seen()); }
    await sleep(300);
    const s1 = await this.serve(dev.link, image, 0xE0000000, verified ? 8000 : 12000, (n) => step("verify", n));
    dev.link.close();
    if (!s1.finished) throw fail(s1.lost ? "lost" : "stopped", `the device ${s1.lost ? "was disconnected" : "stopped"} after ${s1.served} requests: nothing was written`, this.seen());
    step("loader");
    await sleep(3000);
    const ota = await this.waitFor(IS_OTA, 30000);
    if (!ota) throw fail("noloader", "the update loader did not appear. Replug the USB cable and press Install again: the device stays in update mode until it is finished.", this.seen());
    const s2 = await this.write(ota, image, step);
    if (!s2.finished) throw fail(s2.lost ? "lost" : "stopped", `the loader ${s2.lost ? "was disconnected" : "stopped"} after ${s2.served} requests. Replug and press Install again to resume.`);
    step("reboot");
    await sleep(3000);
    const back = await this.waitFor((id) => !IS_OTA(id), 40000);
    if (!back) throw fail("noreturn", "the device did not come back: power-cycle it", this.seen());
    back.link.close();
    if (back.id.text !== product) throw fail("mismatch", `installed, but the device reports ${back.id.text}`, back.id.text);
    step("done", back.id.text);
    return back.id.text;
  }
  async write(ota, image, step) {
    ota.link.send(UPGRADE);
    await sleep(2000);
    const s2 = await this.serve(ota.link, image, 0xF0000000, 180000, (n) => step("write", n));
    ota.link.close();
    return s2;
  }
  async resume(image, onStep, opts = {}) {
    const ota = await this.find(IS_OTA);
    if (!ota) return false;
    if (!OUR_LOADER(ota.id) && !opts.product) {
      ota.link.close();
      throw fail("foreign", `the FM-1 is in the update mode of another firmware (${ota.id.text}): finish that update with its own updater`, ota.id.text);
    }
    const step = onStep || (() => {});
    if (!(await this.write(ota, image, step)).finished) return false;
    if (opts.product) {
      step("reboot");
      await sleep(3000);
      const back = await this.waitFor((id) => !IS_OTA(id), 40000);
      if (!back) throw fail("noreturn", "the FM-1 did not come back after the write: power-cycle it", this.seen());
      back.link.close();
      if (back.id.text !== opts.product) throw fail("mismatch", `written, but the FM-1 reports ${back.id.text}`, back.id.text);
      step("done", back.id.text);
    }
    return true;
  }
}
