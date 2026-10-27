// SPDX-License-Identifier: GPL-3.0-only
// EXS MT4 universal Chrome / Android flasher. The user selects a trusted .fwsc.
// NO pinned version, release.json dependency or hardcoded SHA-256.
// OTA remains fail-closed on device identity / model / foreign loader mismatch.
// Uses TTG A0.3 transport, derived from Felucca / SLOOP (GPL-3.0-only).
import {logicalImage,sha256hex} from '../fm1-test-a03/fm1pkg.js';
import {Updater,OUR_LOADER,FM1_PORT} from '../fm1-test-a03/fm1ota.js';
import {inspectFwsc,compatibleDevice} from './fwsc-check.mjs';

const $=id=>document.getElementById(id);
let selected=null, busy=false, midiAccess=null, probeIdentity=null, wake=null;
function log(message){
  const el=$('log');
  el.textContent+=new Date().toLocaleTimeString()+' '+String(message)+'\n';
  el.scrollTop=el.scrollHeight;
}
function status(id,message,cls=''){
  const el=$(id);el.textContent=message;el.className='status '+cls;
}
const pct=x=>$('progressBar').style.width=Math.max(0,Math.min(100,x))+'%';
const isOta=id=>/^ota-/i.test(id?.model||'');
function updateButtons(){
  $('probeBtn').disabled=busy;
  $('firmware').disabled=busy;
  $('ack1').disabled=busy||!selected;
  $('ack2').disabled=busy||!selected;
  const compatible=selected && compatibleDevice(probeIdentity,selected.meta);
  $('flashBtn').disabled=busy||!selected||!compatible||
    !$('ack1').checked||!$('ack2').checked;
}
async function getMidi(){
  if(!window.isSecureContext)
    throw new Error('Serve HTTPS: apri il sito pubblico in Chrome.');
  if(!navigator.requestMIDIAccess)
    throw new Error('WebMIDI non disponibile. Usa Chrome Android aggiornato, non il browser interno delle app.');
  if(!midiAccess) midiAccess=await navigator.requestMIDIAccess({sysex:true});
  return midiAccess;
}
function describeMidi(access){
  const ins=[...access.inputs.values()].filter(x=>x.state!=='disconnected').map(x=>x.name||'?');
  const outs=[...access.outputs.values()].filter(x=>x.state!=='disconnected').map(x=>x.name||'?');
  return 'INGRESSI: '+(ins.join(', ')||'nessuno')+' · USCITE: '+(outs.join(', ')||'nessuna');
}
async function inspectLocal(file){
  selected=null;
  $('ack1').checked=$('ack2').checked=false;
  pct(0);
  updateButtons();
  try{
    if(!file)throw new Error('Nessun file selezionato.');
    const raw=new Uint8Array(await file.arrayBuffer());
    const meta=inspectFwsc(raw,file.name);  // true pinned header + loader checks
    const hash=await sha256hex(raw);
    selected={raw,meta,hash,name:file.name};
    status('fileStatus','File FM-1 riconosciuto: '+file.name+' · '+meta.product,'ok');
    $('manifestData').textContent=
      'Firmware locale: '+file.name+'\n' +
      'Prodotto: '+meta.product+'\n'+
      'Dimensione: '+meta.size+' byte\n'+
      'SHA-256 calcolato sul file:\n'+hash+
      '\n\nSHA-256 = impronta del file, NON prova di autenticità.\n' +
      'Nessun confronto con una vecchia versione: P5.8 e successive consentite.';
    log('Selected '+file.name+' product '+meta.product+' SHA-256 '+hash);
    if(probeIdentity && !compatibleDevice(probeIdentity,meta))
      status('probeStatus','Dispositivo collegato NON compatibile col firmware selezionato. Flash bloccato.','err');
  }catch(e){
    $('manifestData').textContent='';
    status('fileStatus','FILE RIFIUTATO: '+e.message,'err');
    log('Local FWSC validation failed: '+e.message);
  }
  updateButtons();
}
$('firmware').addEventListener('change',async()=>{
  await inspectLocal($('firmware').files?.[0]);
});
$('ack1').addEventListener('change',updateButtons);
$('ack2').addEventListener('change',updateButtons);

$('probeBtn').addEventListener('click',async()=>{
  probeIdentity=null;busy=true;updateButtons();
  try{
    status('probeStatus','Richiedo MIDI/SysEx in Chrome…','warn');
    const access=await getMidi(),seen=describeMidi(access);log(seen);
    const ports=[...access.inputs.values()].filter(x=>
      x.state!=='disconnected'&&FM1_PORT.test(x.name||''));
    if(!ports.length)throw new Error('Porta FM-1 non rilevata. '+seen);
    status('probeStatus','FM-1 USB-MIDI trovato. Attendo identità OTA (non scrivo nulla)…','warn');
    const updater=new Updater(access,log);
    const dev=await updater.find();
    if(!dev)
      throw new Error('USB-MIDI visibile ma handshake OTA senza risposta. Non è sicuro flashare: ripristina o verifica il firmware attuale.');
    if(isOta(dev.id)&&!OUR_LOADER(dev.id)){
      const id=dev.id.text;dev.link.close();
      throw new Error('Loader OTA sconosciuto ('+id+'). Nessuna scrittura.');
    }
    probeIdentity=dev.id;dev.link.close();
    if(selected && !compatibleDevice(probeIdentity,selected.meta))
      throw new Error('Firmware '+selected.meta.product+' incompatibile con '+probeIdentity.text);
    status('probeStatus','Identità OTA verificata: '+probeIdentity.text+
      (isOta(probeIdentity)?' (modalità recupero)':''),'ok');
    log('Handshake OK '+probeIdentity.text);
  }catch(e){
    probeIdentity=null;
    status('probeStatus','FLASH BLOCCATO: '+e.message,'err');
    log('Probe failed: '+e.message);
  }finally{busy=false;updateButtons();}
});
function stage(name,detail){
  const captions={
    start:'FM-1 riconosciuto. Preparazione OTA…',
    verify:'Verifica firmware / caricamento loader…',
    loader:'Cambio alla modalità OTA: attendo ri-enumerazione USB…',
    write:'SCRITTURA IN CORSO — NON SCOLLEGARE.',
    reboot:'Scrittura conclusa: verifico il riavvio.',
    done:'Installazione completata e identità firmware verificata.'
  };
  status('flashStatus',captions[name]||name,name==='done'?'ok':'warn');
  if(name==='start')pct(5);
  if(name==='verify')pct(Math.min(35,10+Number(detail||0)/6));
  if(name==='loader')pct(40);
  if(name==='write')pct(Math.min(90,42+Number(detail||0)/8));
  if(name==='reboot')pct(95);
  if(name==='done')pct(100);
  log(name+(detail===undefined?'':' '+detail));
}
$('flashBtn').addEventListener('click',async()=>{
  if(!selected||busy||!probeIdentity||!compatibleDevice(probeIdentity,selected.meta)||
    !$('ack1').checked||!$('ack2').checked)return;
  const item=selected;
  const suffix=item.hash.slice(0,12);
  if(!confirm('INSTALLAZIONE SPERIMENTALE MT4\n'+item.name+'\n'+item.meta.product+
    '\nSHA-256: '+suffix+'…\n\nHai un backup e sei certo della provenienza del file? NON scollegare il cavo durante la scrittura. Procedere?'))return;
  busy=true;updateButtons();pct(1);
  try{
    try{wake=await navigator.wakeLock?.request('screen')}catch(e){log('Wake lock: '+e.message)}
    const second=inspectFwsc(item.raw,item.name);
    if(second.product!==item.meta.product || await sha256hex(item.raw)!==item.hash)
      throw new Error('Il firmware non supera la seconda verifica locale.');
    const access=await getMidi(),updater=new Updater(access,log);
    const current=await updater.find();
    if(!current)throw new Error('Identità OTA non più disponibile; nessuna scrittura.');
    const same=current.id.text===probeIdentity.text;
    const compatible=compatibleDevice(current.id,item.meta);
    const booting=isOta(current.id);
    const safeBoot=!booting||OUR_LOADER(current.id);
    current.link.close();
    if(!same||!compatible||!safeBoot)
      throw new Error('L’identità del dispositivo è cambiata, incompatibile o sconosciuta. Nessuna scrittura.');
    const image=logicalImage(item.raw);
    if(booting){
      const ok=await updater.resume(image,stage,{product:item.meta.product});
      if(!ok)throw new Error('Ripresa OTA non completata.');
    }else{
      await updater.install(image,item.meta.product,stage,{allowUnverifiedFelucca:false});
    }
  }catch(e){
    status('flashStatus','INSTALLAZIONE NON CONFERMATA: '+e.message+
      ' · Se il loader è già partito NON usare UBOOT o scollegare ripetutamente: controlla lo stato OTA.','err');
    log('Flash failed '+(e.code||'')+': '+e.message);
  }finally{
    try{await wake?.release()}catch(_){}
    wake=null;busy=false;updateButtons();
  }
});
status('releaseStatus','Flasher universale MT4: nessun firmware preimpostato o SHA-256 P5.1 fisso.','ok');
if(!window.isSecureContext)
  status('probeStatus','Apri questa pagina HTTPS in Chrome Android.','err');
updateButtons();
