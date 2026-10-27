// SPDX-License-Identifier: GPL-3.0-only
// EXS MT4 browser flasher. Transport uses the existing TTG A0.3 implementation
// based on Felucca/SLOOP (Leo Kuroshita / isod89, GPL-3.0-only).
// Only this exact v0.30 P5.5 candidate is authorized by hash in this isolated experimental test page; OTA identity handshake remains mandatory.
import {productOf,logicalImage,sha256hex} from '../fm1-test-a03/fm1pkg.js';
import {Updater,OUR_LOADER,FM1_PORT} from '../fm1-test-a03/fm1ota.js';

const $=id=>document.getElementById(id);
let release=null, bytes=null, verified=false, busy=false, midiAccess=null, probeIdentity=null, wake=null;
const log=s=>{const t=$('log'); t.textContent+=new Date().toLocaleTimeString()+' '+String(s)+'\n';t.scrollTop=t.scrollHeight};
const status=(id,message,cls='')=>{const el=$(id);el.textContent=message;el.className='status '+cls};
const pct=x=>$('progressBar').style.width=Math.max(0,Math.min(100,x))+'%';
const isOta=id=>/^ota-/i.test(id.model||'');
function updateButtons(){
 const unlocked=!!release && !busy;
 $('probeBtn').disabled=busy;
 $('firmware').disabled=!unlocked;
 $('downloadBtn').disabled=!unlocked||!release.firmware_url;
 $('ack1').disabled=!unlocked;
 $('ack2').disabled=!unlocked;
 $('flashBtn').disabled=!unlocked||!verified||!$('ack1').checked||!$('ack2').checked||!probeIdentity;
}
function validRelease(r){
 if(!r||r.schema!==1||r.flash_enabled!==true||r.ota_enabled!==true)return false;
 if(typeof r.release!=='string'||!/^MT4 v0\.[0-9]+ P[0-9]+(?:\.[0-9]+)?$/.test(r.release))return false;
 if(typeof r.product!=='string'||!/^FM-1_[0-9]{3}$/.test(r.product))return false;
 if(!Number.isSafeInteger(r.size)||r.size<300000||r.size>1200000)return false;
 if(typeof r.sha256!=='string'||!/^[0-9a-f]{64}$/.test(r.sha256))return false;
 if(r.firmware_url!=null&& !/^\.\/(?:firmware\/)?[a-zA-Z0-9_.-]+\.fwsc$/.test(r.firmware_url))return false;
 return true;
}
async function loadRelease(){
 try{
  const resp=await fetch('./release.json',{cache:'no-store'});
  if(!resp.ok)throw new Error('HTTP '+resp.status);
  const data=await resp.json();
  if(!validRelease(data)){
   release=null;
   status('releaseStatus','Nessuna release MT4 OTA abilitata. Diagnostica USB disponibile; scrittura bloccata.','warn');
   $('manifestData').textContent='P5.1 GOLD compilata con OTA=1; handshake da verificare su FM-1 reale · Nessuna release autorizzata.';
  }else{
   release=data;
   status('releaseStatus',data.release+' SPERIMENTALE (boot FM-1 non verificato). SHA-256 obbligatorio.','ok');
   $('manifestData').textContent='Prodotto: '+data.product+'\nDimensione: '+data.size+' byte\nSHA-256: '+data.sha256;
   log('Release autorizzata: '+data.release);
  }
 }catch(e){release=null;status('releaseStatus','Manifest non disponibile: flash disabilitato. '+e.message,'err');log('Manifest: '+e.message)}
 updateButtons();
}
async function loadFirmware(data,name){
 bytes=null;verified=false;updateButtons();
 try{
  if(!release)throw new Error('Non è stata ancora pubblicata una release abilitata.');
  if(!/\.fwsc$/i.test(name))throw new Error('Seleziona un file .fwsc, non uno ZIP o BIN.');
  if(data.length!==release.size)throw new Error('Dimensione non corrispondente alla release pubblicata.');
  const id=productOf(data);
  if(id!==release.product)throw new Error('Firmware per '+id+', atteso '+release.product);
  const hash=await sha256hex(data);
  if(hash!==release.sha256)throw new Error('SHA-256 diverso dalla release MT4: file rifiutato.');
  if(logicalImage(data).length<300000)throw new Error('Pacchetto FM-1 incompleto.');
  bytes=data; verified=true;
  status('fileStatus',name+' verificato · '+id+' · SHA-256 '+hash,'ok');
  log('Firmware accepted '+name+' '+hash);
 }catch(e){
  status('fileStatus','FILE RIFIUTATO: '+e.message,'err');
  log('File check failed: '+e.message);
 }
 updateButtons();
}
$('firmware').addEventListener('change',async()=>{
 const file=$('firmware').files?.[0];if(!file)return;
 try{await loadFirmware(new Uint8Array(await file.arrayBuffer()),file.name)}catch(e){status('fileStatus',e.message,'err')}
});
$('downloadBtn').addEventListener('click',async()=>{
 if(!release?.firmware_url||busy)return;
 $('downloadBtn').disabled=true;
 try{
  status('fileStatus','Scaricamento firmware pubblicato…','warn');
  const r=await fetch(release.firmware_url,{cache:'no-store'});
  if(!r.ok)throw new Error('HTTP '+r.status);
  const data=new Uint8Array(await r.arrayBuffer());
  const name=release.firmware_url.split('/').pop();
  await loadFirmware(data,name);
 }catch(e){status('fileStatus','Impossibile scaricare: '+e.message,'err')}
 updateButtons();
});
$('ack1').addEventListener('change',updateButtons);
$('ack2').addEventListener('change',updateButtons);

async function getMidi(){
 if(!window.isSecureContext)throw new Error('Apri via HTTPS, non come file locale.');
 if(!navigator.requestMIDIAccess)throw new Error('Questo browser non dispone di WebMIDI: usa Chrome aggiornato, non il browser interno di un app.');
 if(midiAccess)return midiAccess;
 midiAccess=await navigator.requestMIDIAccess({sysex:true});
 return midiAccess;
}
function describeMidi(access){
 const ins=[...access.inputs.values()].filter(p=>p.state!=='disconnected').map(p=>p.name||'?');
 const outs=[...access.outputs.values()].filter(p=>p.state!=='disconnected').map(p=>p.name||'?');
 return 'INGRESSI: '+(ins.join(', ')||'nessuno')+' · USCITE: '+(outs.join(', ')||'nessuna');
}
$('probeBtn').addEventListener('click',async()=>{
 probeIdentity=null;updateButtons();busy=true;updateButtons();
 try{
  status('probeStatus','Richiedo autorizzazione MIDI/SysEx…','warn');
  const access=await getMidi();
  const seen=describeMidi(access);log(seen);
  const p=[...access.inputs.values()].filter(x=>x.state==='connected'&&FM1_PORT.test(x.name||''));
  if(!p.length)throw new Error('Nessuna porta FM-1 riconosciuta. '+seen);
  status('probeStatus','Porta USB-MIDI riconosciuta: interrogo l’identità (nessun flash)…','warn');
  const u=new Updater(access,log);
  const found=await u.find();
  if(!found){
   // USB-MIDI enumeration is successful, but no OTA identity reply exists.
   // P3/P4 OTA=0 cannot answer. P5 GOLD built OTA=1; if no reply, diagnose physical SysEx or firmware path.
   // Do not mistake "port visible" for OTA readiness. Flash remains gated.
   probeIdentity=null;
   status('probeStatus','USB-MIDI RICONOSCIUTO ✓ · Porta Felucca disponibile. Handshake OTA: NESSUNA RISPOSTA. Se hai installato la P5 GOLD con OTA=1, non considerare ancora pronto il flash da Chrome: va diagnosticata la mancata risposta SysEx. Nessuna scrittura eseguita. '+seen,'warn');
   log('USB-MIDI enumeration PASS; OTA handshake unavailable; NO FLASH. '+seen);
   return;
  }
  if(isOta(found.id)&&!OUR_LOADER(found.id)){
   const name=found.id.text;found.link.close();
   throw new Error('Loader estraneo '+name+': utilizzare il suo updater originale, non MT4.');
  }
  probeIdentity=found.id.text;
  found.link.close();
  status('probeStatus','FM-1 riconosciuto: '+probeIdentity+(isOta(found.id)?' (loader OTA recuperabile).':' (handshake attivo).'),'ok');
  log('Identity OK: '+probeIdentity);
 }catch(e){probeIdentity=null;status('probeStatus','Collegamento non pronto: '+e.message,'err');log('Probe failed: '+e.message)}
 finally{busy=false;updateButtons()}
});
function stage(name,detail){
 const titles={start:'FM-1 riconosciuto. Preparazione OTA…',verify:'Verifica iniziale / caricamento loader…',loader:'Attendo il loader OTA dopo il riavvio…',write:'SCRITTURA FLASH IN CORSO: NON SCOLLEGARE.',reboot:'Scrittura conclusa, controllo riavvio…',done:'Aggiornamento verificato e completato.'};
 status('flashStatus',titles[name]||name,name==='done'?'ok':'warn');
 if(name==='start')pct(5);
 if(name==='verify')pct(Math.min(32,8+Number(detail||0)/7));
 if(name==='loader')pct(35);
 if(name==='write')pct(Math.min(91,38+Number(detail||0)/9));
 if(name==='reboot')pct(94);
 if(name==='done')pct(100);
 log(name+(detail===undefined?'':' '+detail));
}
$('flashBtn').addEventListener('click',async()=>{
 if(!release||!verified||!bytes||busy||!probeIdentity||!$('ack1').checked||!$('ack2').checked)return;
 if(!confirm('INSTALLAZIONE MT4: hai verificato il firmware e la batteria? Se inizi, NON scollegare FM-1 fino al riavvio. Continuare?'))return;
 busy=true;updateButtons();pct(1);
 try{
  try{wake=await navigator.wakeLock?.request('screen')}catch(e){log('Wake-lock unavailable: '+e.message)}
  if(productOf(bytes)!==release.product || await sha256hex(bytes)!==release.sha256)throw new Error('Controllo finale del pacchetto non riuscito.');
  const access=await getMidi(),u=new Updater(access,log),image=logicalImage(bytes);
  const current=await u.find();
  if(!current)throw new Error('Il FM-1 non risponde all’identità OTA. Nessun comando di scrittura inviato. Con P5 GOLD compilata OTA=1 occorre diagnosticare la risposta SysEx prima di tentare il flash.');
  if(current.id.text!==probeIdentity){current.link.close();throw new Error('Identità FM-1 cambiata dopo la diagnosi. Riconnetti e ripeti il controllo.')}
  const booting=isOta(current.id);const safeBoot=booting&&OUR_LOADER(current.id);
  current.link.close();
  if(booting&&!safeBoot)throw new Error('Loader sconosciuto: flash bloccato.');
  if(booting){
   const done=await u.resume(image,stage,{product:release.product});
   if(!done)throw new Error('Ripresa OTA non riuscita; non scollegare senza verificare lo stato del loader.');
  }else{
   await u.install(image,release.product,stage,{allowUnverifiedFelucca:false});
  }
 }catch(e){status('flashStatus','INSTALLAZIONE NON CONFERMATA: '+(e.message||e)+(e.detail?' · '+e.detail:''),'err');log('Flash failure: '+(e.code||'')+' '+(e.message||e))}
 finally{try{await wake?.release()}catch(e){}wake=null;busy=false;updateButtons()}
});
if(!window.isSecureContext)status('probeStatus','Questa pagina richiede HTTPS.','err');
loadRelease();
