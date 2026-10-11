import { inspectHtfw, makePlan, transferFirmware } from '../pocket-master-flasher/pocket-master-core.mjs';
import {
  requestSysexAccess, listPairs, describePair, snapshot, openPair,
  enterBootloader, waitForBootloaderPair, reacquireSysexAccess, waitForNormalPair,
  bootCandidate
} from '../pocket-master-flasher/pocket-master-webmidi.mjs';

const LIVE_SHA256 = '1e9c005fe312de7fd8ae1148ec47b986394b8e5c5a058785154a4e0601675fb9';
const OFFICIAL_SHA256 = 'd9112f12a37e3731e540b325d2b9788f005de7f640c129ac30465ea1dc57e7a3';
const TARGETS = new Map([
  [LIVE_SHA256, 'TTG v1.7 LIVE STATE v0.1 · stock page2 + NDS32 hook'],
  [OFFICIAL_SHA256, 'Official Pocket Master V1.3.3 · recovery']
]);

const $ = s => document.querySelector(s);
const ui = {
  connect: $('#connectBtn'), file: $('#firmwareFile'), device: $('#deviceSelect'),
  inspect: $('#inspectBox'), flash: $('#flashBtn'), rescan: $('#rescanBtn'), force: $('#forceSameBtn'),
  progress: $('#progress'), progressText: $('#progressText'), state: $('#state'), log: $('#log'),
  agree: $('#agree'), cancel: $('#cancelBtn'), browser: $('#browserStatus'), reconnectHelp: $('#reconnectHelp')
};
let access=null, firmware=null, info=null, plan=null, aborter=null, pending=null, busy=false, firmwareName='';

function log(s){const t=new Date().toLocaleTimeString();ui.log.textContent+=`[${t}] ${s}\n`;ui.log.scrollTop=ui.log.scrollHeight;}
function state(s){ui.state.textContent=s;log(`STATE: ${s}`);}
function targetName(){return TARGETS.get(info?.sha256)||null;}
function canFlash(){return !!(access&&firmware&&plan&&ui.device.value&&!pending&&targetName()&&ui.agree.checked);}
function updatePendingUI(){const waiting=!!pending&&!busy;ui.rescan.hidden=!waiting;ui.force.hidden=!waiting;ui.reconnectHelp.hidden=!waiting;if(waiting){ui.flash.disabled=true;ui.connect.disabled=true;ui.file.disabled=true;ui.device.disabled=true;}}
function setBusy(v){busy=v;ui.connect.disabled=v;ui.file.disabled=v;ui.device.disabled=v;ui.flash.disabled=v||!canFlash();ui.cancel.hidden=!v;updatePendingUI();}
function attachAccess(a){access=a;access.onstatechange=e=>{const p=e?.port;if(p)log(`MIDI: ${p.type} ${p.manufacturer||'?'} / ${p.name||'?'} id=${p.id} state=${p.state}`);refreshDevices();if(pending&&!busy&&bootCandidate(access,pending.oldIds,{allowSameId:false}))log('Bootloader candidate visible. Tap RESCAN / CONTINUE.');};}
function refreshDevices(){const old=ui.device.value;ui.device.innerHTML='';if(!access){ui.device.innerHTML='<option value="">Connect MIDI first</option>';setBusy(busy);return;}const pairs=listPairs(access);for(const p of pairs){const o=document.createElement('option');o.value=p.input.id;o.textContent=describePair(p);o._pair=p;ui.device.appendChild(o);}if(old&&[...ui.device.options].some(o=>o.value===old))ui.device.value=old;if(!pairs.length)ui.device.innerHTML='<option value="">No MIDI input/output pair</option>';setBusy(busy);}
function selectedPair(){return [...ui.device.options].find(o=>o.value===ui.device.value)?._pair||null;}

async function loadBytes(bytes,name){firmware=bytes;firmwareName=name;info=await inspectHtfw(bytes);plan=null;let r=`File: ${name}\nSize: ${info.size.toLocaleString()} bytes\nMagic: ${info.isHtfw?'HTFW ✓':'NO'}\nVersion: ${info.versionTag||'?'}\nSHA-256: ${info.sha256}\nFletcher16: 0x${info.fletcher16.toString(16).padStart(4,'0').toUpperCase()}\n`;
for(const x of info.regions)r+=`  ${x.id}: len=0x${x.length.toString(16).toUpperCase()} CRC16=${x.crcValid?'OK':'FAIL'} stored=0x${x.storedCrc16.toString(16).padStart(4,'0').toUpperCase()}\n`;
if(info.isHtfw&&info.allRegionCrcsValid){plan=await makePlan(bytes);r+=`Safety lock: ${targetName()?targetName()+' ✓':'UNKNOWN IMAGE — FLASH BLOCKED'}\n`;}else r+='FLASH BLOCKED: invalid HTFW/CRC.\n';ui.inspect.textContent=r;log(`Selected: ${targetName()||name}`);setBusy(busy);}

async function continueFlash(pair,manual){if(!pending)throw new Error('No pending bootloader operation.');if(info?.sha256!==pending.firmwareSha)throw new Error('Firmware changed while reconnect was pending.');if(!targetName())throw new Error('Safety lock rejected image.');state(manual?'OPEN BOOTLOADER · MANUAL':'OPEN BOOTLOADER');let link=await openPair(pair,3500);const saved=pending;pending=null;try{const result=await transferFirmware(firmware,link,{state,progress(done,total){const pct=Math.floor(done*100/total);ui.progress.value=pct;ui.progressText.textContent=`${pct}% · ${done.toLocaleString()} / ${total.toLocaleString()}`;},log},aborter?.signal||null);link.close();link=null;log(`Transfer finalized. sends=${result.packetsSent.toLocaleString()} retries=${result.retries}`);state('WAIT NORMAL REBOOT');const back=await waitForNormalPair(access,15000);if(back){state('FLASH COMPLETE');log(`Pocket returned: ${describePair(back)}`);if(saved.firmwareSha===LIVE_SHA256)log('TEST: TTG v1.7 shell should remain. Look for live line TTG M? V???? S? P? K? and move/press the hardware controls.');}else{state('TRANSFER COMPLETE · POWER CYCLE MAY BE NEEDED');log('Power-cycle once, then test.');}}finally{try{link?.close();}catch(_){}}}

async function startFlash(){if(!canFlash())return;setBusy(true);aborter=new AbortController();try{state('ENTER BOOTLOADER');const entered=await enterBootloader(access,plan,selectedPair(),log);pending={mode:'flash',...entered,firmwareSha:info.sha256,firmwareName};state('WAIT BOOTLOADER MIDI');const boot=await waitForBootloaderPair(access,pending.oldIds,{timeoutMs:7000,allowSameAfterMs:3500,onLog:log,onPulse(elapsed,total){ui.progress.value=Math.min(7,Math.floor(elapsed*7/total));ui.progressText.textContent=`reconnect ${Math.ceil(elapsed/1000)}s`;}});if(!boot){state('BOOTLOADER WAITING · TAP RESCAN');log('Paused before flash data. Tap RESCAN / CONTINUE.');return;}await continueFlash(boot,false);}catch(e){state(e.name==='AbortError'?'CANCELLED':'FLASH/BOOT ERROR');log(`ERROR: ${e.message}`);}finally{aborter=null;setBusy(false);refreshDevices();}}
async function manualRescan(forceSame=false){if(!pending)return;setBusy(true);aborter=new AbortController();try{state(forceSame?'FORCE RESCAN · SAME IDS':'RESCAN BOOTLOADER');attachAccess(await reacquireSysexAccess(access,log));refreshDevices();let boot=bootCandidate(access,pending.oldIds,{allowSameId:forceSame});if(!boot&&!forceSame)boot=await waitForBootloaderPair(access,pending.oldIds,{timeoutMs:2200,allowSameAfterMs:999999,onLog:log});if(!boot){state('BOOTLOADER STILL NOT VISIBLE');log('If Pocket is visibly in update mode, use FORCE SAME-PORT CONTINUE.');return;}await continueFlash(boot,true);}catch(e){state(e.name==='AbortError'?'CANCELLED':'RECONNECT ERROR');log(`ERROR: ${e.message}`);}finally{aborter=null;setBusy(false);refreshDevices();}}

ui.connect.onclick=async()=>{try{attachAccess(await requestSysexAccess());log('MIDI + SysEx permission granted.');log(snapshot(access));refreshDevices();}catch(e){state('MIDI PERMISSION ERROR');log(`ERROR: ${e.message}`);}};
ui.file.onchange=async()=>{const f=ui.file.files[0];if(f)try{await loadBytes(new Uint8Array(await f.arrayBuffer()),f.name);}catch(e){log(`ERROR: ${e.message}`);}};
ui.agree.onchange=()=>setBusy(busy);ui.device.onchange=()=>setBusy(busy);ui.flash.onclick=startFlash;ui.cancel.onclick=()=>aborter?.abort();ui.rescan.onclick=()=>manualRescan(false);ui.force.onclick=()=>{if(!pending)return;const elapsed=Date.now()-(pending.announcedAt||0);if(elapsed<3000){log('Wait at least 3 seconds after bootloader announce.');return;}if(confirm('Use same MIDI IDs only if Pocket is visibly in update mode. Continue?'))manualRescan(true);};
window.addEventListener('beforeunload',e=>{if(busy||pending){e.preventDefault();e.returnValue='';}});
ui.browser.textContent=(navigator.requestMIDIAccess&&window.isSecureContext)?'WebMIDI/SysEx: browser candidate OK':`WebMIDI/SysEx unavailable · secure=${window.isSecureContext}`;
refreshDevices();setBusy(false);
