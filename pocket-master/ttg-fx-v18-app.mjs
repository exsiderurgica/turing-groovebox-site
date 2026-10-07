import { OFFICIAL_V133_SHA256, inspectHtfw, makePlan, transferFirmware } from './pocket-master-core.mjs';
import { requestSysexAccess, listPairs, describePair, openPair, enterBootloader, waitForBootloaderPair, waitForNormalPair } from './pocket-master-webmidi.mjs';

const TTG_FX_V13_SHA256='c1f208574ebb8e2c7e9caf99166582eed80cd33332b31d562046a52237d7cd4d';
const TTG_FX_V18_SHA256='b3c7b55b2deb35ddf8691e3bc6fcd886c322bbf18987e452432ca4b8a10f0ac6';
const FAILED_V08_SHA256='2678e13c98145a2b33747801c462981263ff6c215d61a6fc0f9243f870b63b94';
const BLOCKED_V15_SHA256='608b4d9c7623de53b158d26ebfd252687f37769bab654e6e601377e77cb67492';
const SUPERSEDED_V16_SHA256='636c83eac7d90615e3c40aa71d6bd9985b7ca54767a61d1683010af6a1198bff';
const SUPERSEDED_V17_SHA256='918442e2f33f2e0964eee855511041991887a43940a0f64c01e86a4dbb4379f5';
const $=s=>document.querySelector(s);
const ui={connect:$('#connectBtn'),file:$('#firmwareFile'),device:$('#deviceSelect'),inspect:$('#inspectBox'),boot:$('#bootBtn'),flash:$('#flashBtn'),progress:$('#progress'),progressText:$('#progressText'),state:$('#state'),log:$('#log'),agree:$('#agree'),cancel:$('#cancelBtn'),browser:$('#browserStatus')};
let access=null,firmware=null,info=null,plan=null,aborter=null,busy=false;
const log=s=>{ui.log.textContent+=`[${new Date().toLocaleTimeString()}] ${s}\n`;ui.log.scrollTop=ui.log.scrollHeight};
const state=s=>{ui.state.textContent=s;log(`STATE: ${s}`)};
function kind(){if(!info)return null;if(info.sha256===TTG_FX_V18_SHA256)return'v18';if(info.sha256===TTG_FX_V13_SHA256)return'v13';if(info.sha256===OFFICIAL_V133_SHA256)return'stock';return null}
function label(k){return k==='v18'?'TTG FX v1.8 STRUCTURE + FILTERS':k==='v13'?'TTG FX v1.3 TRUE CHAIN':'OFFICIAL V1.3.3'}
function selectedPair(){return[...ui.device.options].find(o=>o.value===ui.device.value)?._pair||null}
const canBoot=()=>!!(access&&selectedPair());
const canFlash=()=>!!(canBoot()&&firmware&&plan&&kind()&&ui.agree.checked);
function updateLabel(){ui.flash.textContent=kind()?`3B · FLASH ${label(kind())}`:'3B · FLASH BLOCKED'}
function setBusy(v){busy=v;ui.connect.disabled=v;ui.file.disabled=v;ui.device.disabled=v;ui.boot.disabled=v||!canBoot();ui.flash.disabled=v||!canFlash();ui.cancel.hidden=!v;updateLabel()}
function refresh(){const prior=ui.device.value;ui.device.innerHTML='';if(!access){ui.device.innerHTML='<option value="">Connect MIDI first</option>';setBusy(busy);return}const pairs=listPairs(access);for(const p of pairs){const o=document.createElement('option');o.value=p.input.id;o.textContent=describePair(p);o._pair=p;ui.device.appendChild(o)}if(prior&&[...ui.device.options].some(o=>o.value===prior))ui.device.value=prior;if(!pairs.length)ui.device.innerHTML='<option value="">No MIDI input/output pair</option>';setBusy(busy)}
async function loadFile(file){
  firmware=new Uint8Array(await file.arrayBuffer());info=await inspectHtfw(firmware);plan=null;
  let r=`Build: TTG FX v1.8 STRUCTURE + FILTERS\nFile: ${file.name}\nSize: ${info.size.toLocaleString()} bytes\nMagic: ${info.isHtfw?'HTFW ✓':'NO'}\nVersion: ${info.versionTag||'?'}\nSHA-256: ${info.sha256}\nExpected v1.8: ${TTG_FX_V18_SHA256}\nFletcher16: 0x${(info.fletcher16??0).toString(16).padStart(4,'0').toUpperCase()}\nRegions: ${info.regions.length}\n`;
  for(const x of info.regions)r+=`  ${x.id}: len=0x${x.length.toString(16).toUpperCase()} CRC16=${x.crcValid?'OK':'FAIL'} stored=0x${x.storedCrc16.toString(16).padStart(4,'0').toUpperCase()}\n`;
  if(info.isHtfw&&info.allRegionCrcsValid){plan=await makePlan(firmware);r+=`Data packets: ${plan.totalDataPackets.toLocaleString()}\n`;
    if(info.sha256===FAILED_V08_SHA256)r+='Safety lock: v0.8 BLOCKED.\n';
    else if(info.sha256===BLOCKED_V15_SHA256)r+='Safety lock: v1.5 BLOCKED — hardware no-audio failure.\n';
    else if(info.sha256===SUPERSEDED_V16_SHA256||info.sha256===SUPERSEDED_V17_SHA256)r+='Safety lock: superseded visual experiment — use v1.8 or rollback v1.3.\n';
    else if(kind())r+=`Safety lock: ${label(kind())} ✓\n`;
    else r+='Safety lock: UNKNOWN/CUSTOM — WRITE BLOCKED.\n';
  }else r+='WRITE BLOCKED: invalid HTFW/CRC.\n';
  ui.inspect.textContent=r;setBusy(busy);
}
ui.connect.onclick=async()=>{try{access=await requestSysexAccess();access.onstatechange=refresh;log('MIDI + SysEx permission granted.');refresh()}catch(e){log(`ERROR: ${e.message}`);state('MIDI PERMISSION ERROR')}};
ui.file.onchange=async()=>{const f=ui.file.files[0];if(f)try{await loadFile(f)}catch(e){log(`ERROR: ${e.message}`)}};
ui.agree.onchange=()=>setBusy(busy);ui.device.onchange=()=>setBusy(busy);ui.cancel.onclick=()=>aborter?.abort();
ui.boot.onclick=async()=>{if(!canBoot())return;setBusy(true);aborter=new AbortController();try{state('ENTER BOOTLOADER · NO FLASH');const old=await enterBootloader(access,plan,selectedPair(),log);state('WAIT BOOTLOADER MIDI');const boot=await waitForBootloaderPair(access,old,12000,log);const link=await openPair(boot);link.close();state('BOOTLOADER RECONNECT PASSED')}catch(e){state('TEST FAILED');log(`ERROR: ${e.message}`)}finally{aborter=null;setBusy(false);refresh()}};
ui.flash.onclick=async()=>{if(!canFlash())return;const k=kind();setBusy(true);aborter=new AbortController();ui.progress.value=0;ui.progressText.textContent='0%';let link=null;try{log(`Selected image: ${label(k)}.`);state('ENTER BOOTLOADER');const old=await enterBootloader(access,plan,selectedPair(),log);state('WAIT BOOTLOADER MIDI');const boot=await waitForBootloaderPair(access,old,12000,log);link=await openPair(boot);const result=await transferFirmware(firmware,link,{state,progress(done,total){const pct=Math.floor(done*100/total);ui.progress.value=pct;ui.progressText.textContent=`${pct}% · ${done.toLocaleString()} / ${total.toLocaleString()}`},log},aborter.signal);link.close();link=null;log(`Transfer finalized. sends=${result.packetsSent.toLocaleString()} retries=${result.retries}`);state('WAIT NORMAL REBOOT');const back=await waitForNormalPair(access,15000);if(back){state(`FLASH COMPLETE · ${label(k)}`);log(`Pocket Master returned: ${describePair(back)}`)}else{state('TRANSFER COMPLETE · POWER CYCLE MAY BE NEEDED')}}catch(e){state(e.name==='AbortError'?'CANCELLED':'FLASH ERROR');log(`ERROR: ${e.message}`)}finally{try{link?.close()}catch(_){}aborter=null;setBusy(false);refresh()}};
window.addEventListener('beforeunload',e=>{if(busy){e.preventDefault();e.returnValue=''}});
const ok=!!navigator.requestMIDIAccess&&window.isSecureContext;ui.browser.textContent=ok?'WebMIDI/SysEx: browser candidate OK · BUILD v1.8 structure-filters':`WebMIDI/SysEx unavailable · secure=${window.isSecureContext}`;ui.browser.className=ok?'ok':'bad';refresh();setBusy(false);
