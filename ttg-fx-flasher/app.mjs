import { inspectHtfw, makePlan, transferFirmware } from './pocket-master-core.mjs';
import {
  requestSysexAccess, listPairs, describePair, snapshot, openPair,
  enterBootloader, waitForBootloaderPair, reacquireSysexAccess, waitForNormalPair,
  bootCandidate
} from './pocket-master-webmidi.mjs';

const TARGETS = new Map([
  ['a54186186e82ecf74d6abf4990b4d7d4782cbdd2430979fd35a79f9e7ebc072b', 'TTG FX v2.0 EXSIDERURGICA MODS FULL'],
  ['c1f208574ebb8e2c7e9caf99166582eed80cd33332b31d562046a52237d7cd4d', 'TTG FX v1.3 TRUE CHAIN (rollback)'],
  ['d9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3', 'Official Pocket Master V1.3.3 (recovery)']
]);

const $ = s => document.querySelector(s);
const ui = {
  connect: $('#connectBtn'), file: $('#firmwareFile'), device: $('#deviceSelect'),
  inspect: $('#inspectBox'), boot: $('#bootBtn'), flash: $('#flashBtn'), rescan: $('#rescanBtn'),
  force: $('#forceSameBtn'), progress: $('#progress'), progressText: $('#progressText'), state: $('#state'),
  log: $('#log'), agree: $('#agree'), cancel: $('#cancelBtn'), browser: $('#browserStatus'),
  reconnectHelp: $('#reconnectHelp')
};

let access = null, firmware = null, info = null, plan = null, aborter = null;
let busy = false, pending = null, firmwareName = '';

function log(s) {
  const ts = new Date().toLocaleTimeString();
  ui.log.textContent += `[${ts}] ${s}\n`;
  ui.log.scrollTop = ui.log.scrollHeight;
}
function state(s) { ui.state.textContent = s; log(`STATE: ${s}`); }
function targetName() { return TARGETS.get(info?.sha256) || null; }
function isAllowedTarget() { return !!targetName(); }
function canBoot() { return !!(access && firmware && plan && ui.device.value && !pending); }
function canFlash() { return canBoot() && isAllowedTarget() && ui.agree.checked; }
function setBusy(v) {
  busy = v;
  ui.connect.disabled = v;
  ui.file.disabled = v;
  ui.device.disabled = v;
  ui.boot.disabled = v || !canBoot();
  ui.flash.disabled = v || !canFlash();
  ui.cancel.hidden = !v;
  updatePendingUI();
}
function updatePendingUI() {
  const waiting = !!pending && !busy;
  ui.rescan.hidden = !waiting;
  ui.force.hidden = !waiting;
  ui.reconnectHelp.hidden = !waiting;
  if (waiting) {
    ui.boot.disabled = true;
    ui.flash.disabled = true;
    ui.connect.disabled = true;
    ui.file.disabled = true;
    ui.device.disabled = true;
  }
}

function checkBrowser() {
  const ok = !!navigator.requestMIDIAccess && window.isSecureContext;
  ui.browser.textContent = ok ? 'WebMIDI/SysEx: browser candidate OK' : `WebMIDI/SysEx unavailable · secure=${window.isSecureContext}`;
  ui.browser.className = ok ? 'status ok' : 'status bad';
}

function attachAccess(a) {
  access = a;
  access.onstatechange = ev => {
    const p = ev?.port;
    if (p) log(`MIDI statechange: ${p.type} ${p.manufacturer || '?'} / ${p.name || '?'} id=${p.id} state=${p.state} conn=${p.connection}`);
    refreshDevices();
    if (pending && !busy) {
      const c = bootCandidate(access, pending.oldIds, { allowSameId: false });
      if (c) log(`New bootloader candidate appeared. Tap RESCAN / CONTINUE.`);
    }
  };
}

function refreshDevices() {
  const prior = ui.device.value;
  ui.device.innerHTML = '';
  if (!access) {
    ui.device.innerHTML = '<option value="">Connect MIDI first</option>';
    setBusy(busy);
    return;
  }
  const pairs = listPairs(access);
  for (const p of pairs) {
    const o = document.createElement('option');
    o.value = p.input.id;
    o.textContent = describePair(p);
    o._pair = p;
    ui.device.appendChild(o);
  }
  if (prior && [...ui.device.options].some(o => o.value === prior)) ui.device.value = prior;
  if (!pairs.length) ui.device.innerHTML = '<option value="">No MIDI input/output pair</option>';
  setBusy(busy);
}

function selectedPair() {
  return [...ui.device.options].find(o => o.value === ui.device.value)?._pair || null;
}

async function loadBytes(bytes, name) {
  firmware = bytes;
  firmwareName = name;
  info = await inspectHtfw(firmware);
  plan = null;
  let report = `File: ${name}\nSize: ${info.size.toLocaleString()} bytes\nMagic: ${info.isHtfw ? 'HTFW ✓' : 'NO'}\nVersion: ${info.versionTag || '?'}\nSHA-256: ${info.sha256}\nFletcher16: 0x${(info.fletcher16 ?? 0).toString(16).padStart(4,'0').toUpperCase()}\nRegions: ${info.regions.length}\n`;
  for (const r of info.regions) report += `  ${r.id}: len=0x${r.length.toString(16).toUpperCase()} CRC16=${r.crcValid ? 'OK' : 'FAIL'} stored=0x${r.storedCrc16.toString(16).padStart(4,'0').toUpperCase()}\n`;
  if (info.isHtfw && info.allRegionCrcsValid) {
    plan = await makePlan(firmware);
    report += `Data packets: ${plan.totalDataPackets.toLocaleString()}\n`;
    report += `Safety lock: ${targetName() ? targetName() + ' ✓' : 'UNKNOWN IMAGE — FLASH BLOCKED'}\n`;
  } else {
    report += 'FLASH BLOCKED: invalid HTFW/CRC.\n';
  }
  ui.inspect.textContent = report;
  log(`Selected image: ${targetName() || name}`);
  setBusy(busy);
}

async function loadFile(file) {
  await loadBytes(new Uint8Array(await file.arrayBuffer()), file.name);
}

async function automaticReconnect(mode) {
  setBusy(true);
  aborter = new AbortController();
  try {
    state(mode === 'test' ? 'ENTER BOOTLOADER · NO FLASH' : 'ENTER BOOTLOADER');
    const entered = await enterBootloader(access, plan, selectedPair(), log);
    pending = { mode, ...entered, firmwareSha: info.sha256, firmwareName };
    state('WAIT BOOTLOADER MIDI · AUTO SCAN');
    const boot = await waitForBootloaderPair(access, pending.oldIds, {
      timeoutMs: 7000,
      allowSameAfterMs: 3500,
      onLog: log,
      onPulse(elapsed, total) {
        ui.progress.value = Math.min(7, Math.floor(elapsed * 7 / total));
        ui.progressText.textContent = `reconnect ${Math.ceil(elapsed/1000)}s`;
      }
    });
    if (!boot) {
      state('BOOTLOADER WAITING · TAP RESCAN');
      log('Automatic WebMIDI re-enumeration did not expose a bootloader pair. This is now a PAUSED state, not a flash error.');
      log('Tap RESCAN / CONTINUE. On Android this fresh user gesture can make Chromium expose the new USB-MIDI identity.');
      return;
    }
    await continueWithBootPair(boot, false);
  } catch (e) {
    pending = null;
    state(e.name === 'AbortError' ? 'CANCELLED' : (mode === 'test' ? 'TEST FAILED' : 'FLASH ERROR'));
    log(`ERROR: ${e.message}`);
  } finally {
    aborter = null;
    setBusy(false);
    refreshDevices();
  }
}

async function continueWithBootPair(boot, manual) {
  if (!pending) throw new Error('No pending bootloader operation.');
  if (info?.sha256 !== pending.firmwareSha) throw new Error('Firmware changed while reconnect was pending.');
  let link = null;
  if (pending.mode === 'test') {
    state(manual ? 'OPEN BOOTLOADER MIDI · MANUAL RESCAN' : 'OPEN BOOTLOADER MIDI');
    link = await openPair(boot, 3500);
    link.close();
    const recycled = pending.oldIds.has(boot.input.id) && pending.oldIds.has(boot.output.id);
    log(`Bootloader reconnect passed: ${describePair(boot)}${recycled ? ' · recycled IDs accepted' : ''}`);
    pending = null;
    state('BOOTLOADER RECONNECT PASSED');
    log('No flash data was written. You may power-cycle to return to normal mode.');
    return;
  }

  if (!isAllowedTarget()) throw new Error('Safety lock rejected the selected firmware image.');
  state(manual ? 'OPEN BOOTLOADER MIDI · MANUAL RESCAN' : 'OPEN BOOTLOADER MIDI');
  link = await openPair(boot, 3500);
  log(`Bootloader opened: ${describePair(boot)}`);
  ui.cancel.hidden = true;
  const saved = pending;
  pending = null;
  try {
    const result = await transferFirmware(firmware, link, {
      state,
      progress(done, total) {
        const pct = Math.floor(done * 100 / total);
        ui.progress.value = pct;
        ui.progressText.textContent = `${pct}% · ${done.toLocaleString()} / ${total.toLocaleString()}`;
      },
      log
    }, aborter?.signal || null);
    link.close(); link = null;
    log(`Transfer finalized. sends=${result.packetsSent.toLocaleString()} retries=${result.retries}`);
    state('WAIT NORMAL REBOOT');
    const back = await waitForNormalPair(access, 15000);
    if (back) {
      state('FLASH COMPLETE');
      log(`Pocket Master returned: ${describePair(back)}`);
    } else {
      state('TRANSFER COMPLETE · POWER CYCLE MAY BE NEEDED');
      log('Normal MIDI port was not observed before timeout. Power-cycle once.');
    }
  } catch (e) {
    log(`Transfer target was: ${TARGETS.get(saved.firmwareSha) || saved.firmwareName}`);
    throw e;
  } finally {
    try { link?.close(); } catch (_) {}
  }
}

async function manualRescan(forceSame = false) {
  if (!pending) return;
  setBusy(true);
  aborter = new AbortController();
  try {
    state(forceSame ? 'FORCE RESCAN · ALLOW SAME MIDI IDS' : 'RESCAN BOOTLOADER MIDI');
    const fresh = await reacquireSysexAccess(access, log);
    attachAccess(fresh);
    refreshDevices();
    let boot = bootCandidate(access, pending.oldIds, { allowSameId: forceSame });
    if (!boot && !forceSame) {
      boot = await waitForBootloaderPair(access, pending.oldIds, { timeoutMs: 2200, allowSameAfterMs: 999999, onLog: log });
    }
    if (!boot) {
      state('BOOTLOADER STILL NOT VISIBLE');
      log('No new MIDI pair is visible. If the Pocket Master is visibly in update/bootloader mode, use FORCE SAME-PORT CONTINUE. Otherwise power-cycle and retry the reconnect test.');
      return;
    }
    await continueWithBootPair(boot, true);
  } catch (e) {
    state(e.name === 'AbortError' ? 'CANCELLED' : 'RECONNECT ERROR');
    log(`ERROR: ${e.message}`);
  } finally {
    aborter = null;
    setBusy(false);
    refreshDevices();
  }
}

ui.connect.onclick = async () => {
  try {
    const a = await requestSysexAccess();
    attachAccess(a);
    log('MIDI + SysEx permission granted.');
    log(snapshot(access));
    refreshDevices();
  } catch (e) {
    log(`ERROR: ${e.message}`);
    state('MIDI PERMISSION ERROR');
  }
};
ui.file.onchange = async () => { const f = ui.file.files[0]; if (f) try { await loadFile(f); } catch (e) { log(`ERROR: ${e.message}`); } };
ui.agree.onchange = () => setBusy(busy);
ui.device.onchange = () => setBusy(busy);
ui.cancel.onclick = () => aborter?.abort();
ui.boot.onclick = () => automaticReconnect('test');
ui.flash.onclick = () => automaticReconnect('flash');
ui.rescan.onclick = () => manualRescan(false);
ui.force.onclick = () => {
  if (!pending) return;
  const elapsed = Date.now() - pending.announcedAt;
  if (elapsed < 3000) { log('FORCE SAME-PORT blocked: wait at least 3 seconds after the bootloader announce.'); return; }
  if (confirm('Use the same MIDI IDs only if the Pocket Master is visibly in update/bootloader mode. Continue?')) manualRescan(true);
};

window.addEventListener('beforeunload', e => {
  if (busy || pending) { e.preventDefault(); e.returnValue = ''; }
});

checkBrowser(); refreshDevices(); setBusy(false);
