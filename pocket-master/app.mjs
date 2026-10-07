import {
  OFFICIAL_V133_SHA256, inspectHtfw, makePlan, transferFirmware
} from './pocket-master-core.mjs';
import {
  requestSysexAccess, listPairs, describePair, openPair, enterBootloader,
  waitForBootloaderPair, waitForNormalPair
} from './pocket-master-webmidi.mjs';

const $ = s => document.querySelector(s);
const ui = {
  connect: $('#connectBtn'), file: $('#firmwareFile'), device: $('#deviceSelect'),
  inspect: $('#inspectBox'), boot: $('#bootBtn'), flash: $('#flashBtn'),
  progress: $('#progress'), progressText: $('#progressText'), state: $('#state'),
  log: $('#log'), agree: $('#agree'), cancel: $('#cancelBtn'), browser: $('#browserStatus')
};

let access = null, firmware = null, info = null, plan = null, aborter = null, busy = false;

function log(s) { const ts = new Date().toLocaleTimeString(); ui.log.textContent += `[${ts}] ${s}\n`; ui.log.scrollTop = ui.log.scrollHeight; }
function state(s) { ui.state.textContent = s; log(`STATE: ${s}`); }
function canBoot() { return !!(access && firmware && plan && ui.device.value); }
function canFlash() { return canBoot() && info?.sha256 === OFFICIAL_V133_SHA256 && ui.agree.checked; }
function setBusy(v) {
  busy = v;
  ui.connect.disabled = v; ui.file.disabled = v; ui.device.disabled = v;
  ui.boot.disabled = v || !canBoot(); ui.flash.disabled = v || !canFlash(); ui.cancel.hidden = !v;
}

function checkBrowser() {
  const ok = !!navigator.requestMIDIAccess && window.isSecureContext;
  ui.browser.textContent = ok ? 'WebMIDI/SysEx: browser candidate OK' : `WebMIDI/SysEx unavailable · secure=${window.isSecureContext}`;
  ui.browser.className = ok ? 'ok' : 'bad';
}

function refreshDevices() {
  const prior = ui.device.value;
  ui.device.innerHTML = '';
  if (!access) { ui.device.innerHTML = '<option value="">Connect MIDI first</option>'; return; }
  const pairs = listPairs(access);
  for (const p of pairs) {
    const o = document.createElement('option');
    o.value = p.input.id; o.textContent = describePair(p); o._pair = p; ui.device.appendChild(o);
  }
  if (prior && [...ui.device.options].some(o => o.value === prior)) ui.device.value = prior;
  if (!pairs.length) ui.device.innerHTML = '<option value="">No MIDI input/output pair</option>';
  setBusy(busy);
}

function selectedPair() { return [...ui.device.options].find(o => o.value === ui.device.value)?._pair || null; }

async function loadFile(file) {
  firmware = new Uint8Array(await file.arrayBuffer());
  info = await inspectHtfw(firmware);
  plan = null;
  let report = `File: ${file.name}\nSize: ${info.size.toLocaleString()} bytes\nMagic: ${info.isHtfw ? 'HTFW ✓' : 'NO'}\nVersion: ${info.versionTag || '?'}\nSHA-256: ${info.sha256}\nFletcher16: 0x${(info.fletcher16 ?? 0).toString(16).padStart(4,'0').toUpperCase()}\nRegions: ${info.regions.length}\n`;
  for (const r of info.regions) report += `  ${r.id}: len=0x${r.length.toString(16).toUpperCase()} CRC16=${r.crcValid ? 'OK' : 'FAIL'}\n`;
  if (info.isHtfw && info.allRegionCrcsValid) {
    plan = await makePlan(firmware);
    report += `Data packets: ${plan.totalDataPackets.toLocaleString()}\n`;
    report += `Safety lock: ${info.sha256 === OFFICIAL_V133_SHA256 ? 'OFFICIAL V1.3.3 ✓ — flash enabled after confirmation' : 'CUSTOM/UNKNOWN — WRITE BLOCKED in v0.1'}\n`;
  } else report += 'WRITE BLOCKED: invalid HTFW/CRC.\n';
  ui.inspect.textContent = report;
  setBusy(busy);
}

ui.connect.onclick = async () => {
  try {
    access = await requestSysexAccess();
    access.onstatechange = () => refreshDevices();
    log('MIDI + SysEx permission granted.'); refreshDevices();
  } catch (e) { log(`ERROR: ${e.message}`); state('MIDI PERMISSION ERROR'); }
};

ui.file.onchange = async () => { const f = ui.file.files[0]; if (f) try { await loadFile(f); } catch (e) { log(`ERROR: ${e.message}`); } };
ui.agree.onchange = () => setBusy(busy);
ui.device.onchange = () => setBusy(busy);
ui.cancel.onclick = () => aborter?.abort();

ui.boot.onclick = async () => {
  if (!canBoot()) return;
  setBusy(true); aborter = new AbortController();
  try {
    state('ENTER BOOTLOADER · NO FLASH');
    const oldIds = await enterBootloader(access, plan, selectedPair(), log);
    state('WAIT BOOTLOADER MIDI');
    const boot = await waitForBootloaderPair(access, oldIds, 12000, log);
    const link = await openPair(boot); link.close();
    state('BOOTLOADER RECONNECT PASSED');
    log('No flash data was written. Power-cycle the Pocket Master to return to normal mode.');
  } catch (e) { state('TEST FAILED'); log(`ERROR: ${e.message}`); }
  finally { aborter = null; setBusy(false); refreshDevices(); }
};

ui.flash.onclick = async () => {
  if (!canFlash()) return;
  setBusy(true); aborter = new AbortController(); ui.progress.value = 0; ui.progressText.textContent = '0%';
  let link = null;
  try {
    if (info.sha256 !== OFFICIAL_V133_SHA256) throw new Error('v0.1 write lock accepts only the exact official V1.3.3 firmware.');
    state('ENTER BOOTLOADER');
    const oldIds = await enterBootloader(access, plan, selectedPair(), log);
    state('WAIT BOOTLOADER MIDI');
    const boot = await waitForBootloaderPair(access, oldIds, 12000, log);
    link = await openPair(boot);
    log(`Bootloader opened: ${describePair(boot)}`);
    const result = await transferFirmware(firmware, link, {
      state,
      progress(done,total) {
        const pct = Math.floor(done * 100 / total);
        ui.progress.value = pct;
        ui.progressText.textContent = `${pct}% · ${done.toLocaleString()} / ${total.toLocaleString()}`;
      },
      log
    }, aborter.signal);
    link.close(); link = null;
    log(`Transfer finalized. sends=${result.packetsSent.toLocaleString()} retries=${result.retries}`);
    state('WAIT NORMAL REBOOT');
    const back = await waitForNormalPair(access, 15000);
    if (back) { state('FLASH COMPLETE'); log(`Pocket Master returned: ${describePair(back)}`); }
    else { state('TRANSFER COMPLETE · POWER CYCLE MAY BE NEEDED'); log('Normal MIDI port was not observed before timeout. Power-cycle once.'); }
  } catch (e) {
    state(e.name === 'AbortError' ? 'CANCELLED' : 'FLASH ERROR'); log(`ERROR: ${e.message}`);
  } finally {
    try { link?.close(); } catch (_) {}
    aborter = null; setBusy(false); refreshDevices();
  }
};

window.addEventListener('beforeunload', e => { if (busy) { e.preventDefault(); e.returnValue = ''; } });
checkBrowser(); refreshDevices(); setBusy(false);
