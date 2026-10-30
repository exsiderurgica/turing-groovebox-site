// SPDX-License-Identifier: GPL-3.0-only
// EXS MT4 integrated GOLD flasher: verified firmware only, OTA fail-closed.
// The transport reuses the original Felucca/SLOOP-derived WebMIDI engine.
import {logicalImage,sha256hex} from '../fm1-test-a03/fm1pkg.js';
import {Updater,OUR_LOADER,FM1_PORT} from '../fm1-test-a03/fm1ota.js';
import {inspectFwsc,compatibleDevice} from '../mt4-flasher/fwsc-check.mjs';

const $=id=>document.getElementById(id);
const SHA='986585ed3b980b799577fc11c23a50c48c9e302446f96b9aacdebacaf659f566';
const NAME='EXS_MT4_v030_P747_REAL_AMBIENT_PATTERNS_GOLD.fwsc';
const FW_URL='https://raw.githubusercontent.com/exsiderurgica/EXS-FM1/main/public-firmware/'+NAME;
const i=(en,it)=>document.documentElement.lang==='it'?it:en;
let loaded=null,access=null,identity=null,busy=false,wake=null;
const isOta=x=>/^ota-/i.test(x?.model||'');
function status(id,en,it,kind=''){
  const el=$(id);
  el.textContent=i(en,it);el.className='status '+kind;
}
function log(s){const el=$('log');el.textContent+=new Date().toLocaleTimeString()+' '+String(s)+'\n';el.scrollTop=el.scrollHeight}
function bar(n){$('progressBar').style.width=Math.max(0,Math.min(100,n))+'%'}
function controls(){
  $('downloadBtn').disabled=busy;
  $('prepareBtn').disabled=busy;
  $('probeBtn').disabled=busy;
  $('ack1').disabled=busy||!loaded;
  $('ack2').disabled=busy||!loaded;
  $('flashBtn').disabled=busy||!loaded||!compatibleDevice(identity,loaded.meta)||
    !$('ack1').checked||!$('ack2').checked;
}
async function getGold(){
  if(loaded)return loaded;
  const response=await fetch(FW_URL,{cache:'no-store'});
  if(!response.ok)throw new Error(i('GOLD download is not published yet (HTTP '+response.status+').','Download GOLD non disponibile (HTTP '+response.status+').'));
  const raw=new Uint8Array(await response.arrayBuffer());
  const meta=inspectFwsc(raw,NAME);
  const hash=await sha256hex(raw);
  if(meta.product!=='FM-1_986'||raw.length!==609912||hash!==SHA)
    throw new Error(i('GOLD firmware hash, size or model mismatch. Nothing will be flashed.','Hash, dimensione o modello GOLD non corrispondenti. Flash bloccato.'));
  loaded={raw,meta,hash};
  log('GOLD verified / '+raw.length+' bytes / '+hash);
  return loaded;
}
async function loadGold(){
  if(busy)return;
  busy=true;controls();
  try{
    status('fileStatus','Downloading and verifying GOLD…','Scarico e verifico GOLD…','warn');
    await getGold();
    status('fileStatus','P7.4.7 GOLD verified / SHA-256 OK','P7.4.7 GOLD verificata / SHA-256 OK','ok');
  }catch(e){status('fileStatus',e.message,e.message,'err');log('GOLD: '+e.message)}
  finally{busy=false;controls()}
}
$('prepareBtn').addEventListener('click',loadGold);
window.addEventListener('mt4-download-approved',async()=>{
  if(busy)return;
  busy=true;controls();
  try{
    status('fileStatus','Verifying official GOLD before download…','Verifico la GOLD prima del download…','warn');
    const gold=await getGold();
    const blob=new Blob([gold.raw],{type:'application/octet-stream'});
    const link=document.createElement('a'),obj=URL.createObjectURL(blob);
    link.href=obj;link.download=NAME;document.body.append(link);link.click();link.remove();
    window.dispatchEvent(new CustomEvent('mt4-gold-download-started'));

    setTimeout(()=>URL.revokeObjectURL(obj),60000);
    status('fileStatus','Verified GOLD download started / ready to flash','Download GOLD verificato avviato / pronto al flash','ok');
  }catch(e){status('fileStatus',e.message,e.message,'err');log('Download: '+e.message)}
  finally{busy=false;controls()}
});
$('ack1').addEventListener('change',controls);
$('ack2').addEventListener('change',controls);
async function midi(){
  if(!window.isSecureContext)throw Error(i('Open the HTTPS website in Chrome Android.','Apri il sito HTTPS in Chrome Android.'));
  if(!navigator.requestMIDIAccess)throw Error(i('WebMIDI unavailable. Use Chrome Android (not an in-app browser).','WebMIDI non disponibile. Usa Chrome Android, non un browser interno.'));
  if(!access)access=await navigator.requestMIDIAccess({sysex:true});
  return access;
}
$('probeBtn').addEventListener('click',async()=>{
  if(busy)return;
  identity=null;busy=true;controls();
  try{
    status('probeStatus','Requesting USB MIDI / SysEx…','Richiedo USB MIDI / SysEx…','warn');
    const m=await midi();
    const ports=[...m.inputs.values()].filter(x=>x.state!=='disconnected'&&FM1_PORT.test(x.name||''));
    log('USB MIDI: '+[...m.inputs.values()].map(x=>x.name).join(', '));
    if(!ports.length)throw Error(i('FM1 MIDI port not found.','Porta MIDI FM1 non trovata.'));
    const update=new Updater(m,log),dev=await update.find();
    if(!dev)throw Error(i('No OTA handshake. Flash is blocked.','Handshake OTA assente. Flash bloccato.'));
    if(isOta(dev.id)&&!OUR_LOADER(dev.id)){
      const name=dev.id.text;dev.link.close();
      throw Error(i('Unknown OTA loader: ','Loader OTA sconosciuto: ')+name);
    }
    identity=dev.id;dev.link.close();
    if(loaded&&!compatibleDevice(identity,loaded.meta))throw Error(i('Firmware does not match the connected FM1.','Firmware non compatibile con FM1 collegato.'));
    status('probeStatus','OTA identity verified: '+identity.text,'Identità OTA verificata: '+identity.text,'ok');
    log('OTA identity: '+identity.text);
  }catch(e){identity=null;status('probeStatus',e.message,e.message,'err');log('Probe: '+e.message)}
  finally{busy=false;controls()}
});
function stage(name,detail){
  const messages={
    start:[i('FM1 detected, starting OTA…','FM1 rilevato, avvio OTA…'),5],
    verify:[i('Verifying and loading firmware…','Verifica e caricamento firmware…'),20],
    loader:[i('Waiting for OTA loader…','Attendo loader OTA…'),40],
    write:[i('WRITING — DO NOT DISCONNECT','SCRITTURA — NON SCOLLEGARE'),55],
    reboot:[i('Waiting for reboot verification…','Attendo verifica riavvio…'),95],
    done:[i('Firmware installed and verified.','Firmware installato e verificato.'),100]
  };
  const v=messages[name]||[String(name),0];
  let n=v[1];
  if(name==='verify')n=Math.min(35,10+Number(detail||0)/6);
  if(name==='write')n=Math.min(90,42+Number(detail||0)/8);
  status('flashStatus',v[0],v[0],name==='done'?'ok':'warn');bar(n);log(name+(detail===undefined?'':' '+detail));
}
$('flashBtn').addEventListener('click',async()=>{
  if(busy||!loaded||!identity||!compatibleDevice(identity,loaded.meta)||
     !$('ack1').checked||!$('ack2').checked)return;
  const message=i(
    'FLASH MT4 P7.4.7 GOLD\nSHA-256: '+SHA.slice(0,16)+'…\n\nA backup is ready. Keep power and USB connected throughout the update. Continue?',
    'INSTALLA MT4 P7.4.7 GOLD\nSHA-256: '+SHA.slice(0,16)+'…\n\nBackup disponibile. Mantieni alimentazione e USB collegate. Continuare?'
  );
  if(!window.confirm(message))return;
  busy=true;controls();bar(2);
  try{
    try{wake=await navigator.wakeLock?.request('screen')}catch(e){log('WakeLock: '+e.message)}
    const meta=inspectFwsc(loaded.raw,NAME);
    if(meta.product!=='FM-1_986'||await sha256hex(loaded.raw)!==SHA)
      throw Error(i('GOLD failed final integrity check.','GOLD non supera la verifica finale.'));
    const m=await midi(),updater=new Updater(m,log),current=await updater.find();
    if(!current)throw Error(i('OTA identity no longer available.','Identità OTA non più disponibile.'));
    const same=current.id.text===identity.text;
    const matching=compatibleDevice(current.id,meta);
    const boot=isOta(current.id);
    const trusted=!boot||OUR_LOADER(current.id);
    current.link.close();
    if(!same||!matching||!trusted)
      throw Error(i('OTA identity changed or is incompatible.','Identità OTA cambiata o incompatibile.'));
    const image=logicalImage(loaded.raw);
    if(boot){
      const ok=await updater.resume(image,stage,{product:meta.product});
      if(!ok)throw Error(i('OTA resume did not complete.','Ripresa OTA incompleta.'));
    }else await updater.install(image,meta.product,stage,{allowUnverifiedFelucca:false});
  }catch(e){
    status('flashStatus',
      'INSTALLATION NOT VERIFIED: '+e.message+' / If the OTA loader is already active, do not repeatedly disconnect or switch to UBOOT.',
      'INSTALLAZIONE NON VERIFICATA: '+e.message+' / Se il loader OTA è attivo, non scollegare ripetutamente e non passare a UBOOT.','err');
    log('FLASH ERROR: '+e.message);
  }finally{
    try{await wake?.release()}catch(_){}
    wake=null;busy=false;controls();
  }
});
window.addEventListener('mt4-language-change',()=>{
  // Do not erase technical error/status messages when changing language.
  controls();
});
if(!window.isSecureContext)status('probeStatus','HTTPS required for WebMIDI.','HTTPS necessario per WebMIDI.','err');
controls();
