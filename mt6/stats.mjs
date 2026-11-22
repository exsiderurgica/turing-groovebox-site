// EXS/MT6 public statistics. Preserve the original Abacus namespace and keys:
// changing them would make the existing historic totals disappear.
const NS='exsiderurgica-hidden-site-stats-93f6c1b0';
const API='https://abacus.jasoncameron.dev';
const KEYS={visits:'mt4-visits',downloads:'mt4-downloads'};
const VISIT_KEY='exsiderurgica_mt6_visit_counted';
const CACHE_KEY='exsiderurgica_mt6_stats_last_good_v1';
const IDS={visits:'mt6VisitCount',downloads:'mt6DownloadCount'};
const TYPES=['visits','downloads'];
const fresh={visits:false,downloads:false};
const shown={visits:false,downloads:false};
let initialLoading=true;
let failedHit=false;

const section=document.getElementById('stats');
const meta=section?.querySelector('.meta');
const message=document.createElement('p');
message.className='small muted';
message.id='mt6StatsStatus';
message.setAttribute('role','status');
message.setAttribute('aria-live','polite');
const retry=document.createElement('button');
retry.type='button';
retry.className='btn';
retry.id='mt6StatsRetry';
retry.style.display='none';
meta?.insertAdjacentElement('afterend',message);
message.insertAdjacentElement('afterend',retry);

function it(){return document.documentElement.lang==='it';}
function format(value){return new Intl.NumberFormat(it()?'it-IT':'en-US').format(value);}
function valid(value){
  if(typeof value!=='number'&&(typeof value!=='string'||!/^[0-9]+$/.test(value)))return null;
  const n=Number(value);
  return Number.isSafeInteger(n)&&n>=0?n:null;
}
function display(type,value,isFresh){
  const el=document.getElementById(IDS[type]);
  const n=valid(value);
  if(!el||n===null)return false;
  el.textContent=format(n);
  el.dataset.statsState=isFresh?'live':'cached';
  shown[type]=true;
  if(isFresh){
    fresh[type]=true;
    try{
      const cache=JSON.parse(localStorage.getItem(CACHE_KEY)||'{}');
      cache[type]={value:n,at:Date.now()};
      localStorage.setItem(CACHE_KEY,JSON.stringify(cache));
    }catch(_){/* Browser storage is optional. */}
  }
  return true;
}
function showCached(){
  try{
    const cache=JSON.parse(localStorage.getItem(CACHE_KEY)||'{}');
    for(const type of TYPES){
      if(cache[type]&&valid(cache[type].value)!==null)
        display(type,cache[type].value,false);
    }
  }catch(_){/* No local snapshot available. */}
}
function refreshMessage(){
  if(!section)return;
  retry.textContent=it()?'RIPROVA':'RETRY';
  if(initialLoading){
    message.textContent=it()?'Caricamento statistiche…':'Loading statistics…';
    return;
  }
  const unavailable=TYPES.some(type=>!fresh[type]);
  retry.style.display=unavailable?'inline-flex':'none';
  if(unavailable){
    const cached=TYPES.some(type=>!fresh[type]&&shown[type]);
    message.textContent=it()
      ? (cached?'Statistiche temporaneamente non disponibili. I valori non aggiornati sono quelli salvati su questo dispositivo.':'Statistiche temporaneamente non disponibili. Riprova per aggiornare i contatori.')
      : (cached?'Stats temporarily unavailable. Older figures are saved on this device.':'Stats temporarily unavailable. Retry to refresh the counters.');
  }else if(failedHit){
    message.textContent=it()
      ? 'Contatori aggiornati, ma non è stato possibile confermare la registrazione di un accesso o download.'
      : 'Counters updated, but a visit or download could not be confirmed as recorded.';
  }else{
    message.textContent='';
  }
}
async function request(type,action){
  const controller=new AbortController();
  const timer=setTimeout(()=>controller.abort(),8000);
  try{
    const response=await fetch(API+'/'+action+'/'+NS+'/'+KEYS[type],{
      cache:'no-store',signal:controller.signal
    });
    // A missing historic key is an outage/data-loss signal, not a real zero.
    if(action==='get'&&response.status===404)throw new Error('Abacus counter missing');
    if(!response.ok)throw new Error('Abacus HTTP '+response.status);
    const data=await response.json();
    const number=valid(data?.value);
    if(number===null)throw new Error('Invalid Abacus counter response');
    return number;
  }finally{
    clearTimeout(timer);
  }
}
async function read(type){
  fresh[type]=false;
  for(let attempt=0;attempt<3;attempt++){
    if(attempt)await new Promise(resolve=>setTimeout(resolve,attempt*700));
    try{
      display(type,await request(type,'get'),true);
      refreshMessage();
      return;
    }catch(_){/* Reads are safe to retry; never retry a hit blindly. */}
  }
  refreshMessage();
}
async function registerVisit(){
  let counted=false;
  try{counted=sessionStorage.getItem(VISIT_KEY)==='1';}
  catch(_){/* Storage blocked: still show the public count. */}
  if(counted){await read('visits');return;}
  try{
    const value=await request('visits','hit');
    display('visits',value,true);
    // Mark counted only AFTER Abacus acknowledges the increment.
    try{sessionStorage.setItem(VISIT_KEY,'1');}catch(_){}
  }catch(_){
    // A timed-out hit might have reached the server. Avoid duplicate increments.
    failedHit=true;
    await read('visits');
  }
}
async function registerDownload(){
  try{
    display('downloads',await request('downloads','hit'),true);
  }catch(_){
    // Do not replay an uncertain hit. Refresh the displayed value instead.
    failedHit=true;
    await read('downloads');
  }
  refreshMessage();
}
retry.addEventListener('click',async()=>{
  retry.disabled=true;
  await Promise.all(TYPES.map(read));
  retry.disabled=false;
  refreshMessage();
});
window.addEventListener('mt6-gold-download-started',()=>{
  void registerDownload();
});
window.addEventListener('mt6-language-change',()=>{
  for(const type of TYPES){
    const el=document.getElementById(IDS[type]);
    if(el&&shown[type]){
      const n=valid(el.textContent.replace(/[^0-9]/g,''));
      if(n!==null)el.textContent=format(n);
    }
  }
  refreshMessage();
});
showCached();
refreshMessage();
void Promise.all([registerVisit(),read('downloads')]).then(()=>{
  initialLoading=false;
  refreshMessage();
});
