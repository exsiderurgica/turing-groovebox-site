// Public MT4 stats (independent of TTG counters).
// Visits: one hit per browser tab/session.
// Downloads: one hit for each verified firmware download initiated by this page.
// This is a public, unauthenticated counter; numbers are indicative, not an audit.
const NS='exsiderurgica-hidden-site-stats-93f6c1b0';
const API='https://abacus.jasoncameron.dev';
const KEYS={visits:'mt4-visits',downloads:'mt4-downloads'};
const VISIT_KEY='exsiderurgica_mt4_visit_counted';
const FORMAT=new Intl.NumberFormat(document.documentElement.lang==='it'?'it-IT':'en-US');

function display(type,value){
  const el=document.getElementById(type==='visits'?'mt4VisitCount':'mt4DownloadCount');
  if(!el)return;
  const number=Number(value);
  if(Number.isSafeInteger(number)&&number>=0)
    el.textContent=new Intl.NumberFormat(document.documentElement.lang==='it'?'it-IT':'en-US').format(number);
}
async function counter(type,increment){
  const url=API+'/'+(increment?'hit':'get')+'/'+NS+'/'+KEYS[type];
  try{
    const response=await fetch(url,{cache:'no-store'});
    if(response.status===404&&!increment){display(type,0);return;}
    if(!response.ok)throw new Error('Stats '+response.status);
    const data=await response.json();
    display(type,data.value);
  }catch(_){
    if(increment)counterRead(type);
  }
}
async function counterRead(type){await counter(type,false);}
async function registerVisit(){
  let counted=false;
  try{counted=sessionStorage.getItem(VISIT_KEY)==='1';if(!counted)sessionStorage.setItem(VISIT_KEY,'1');}
  catch(_){/* Do not block the site if browser storage is unavailable. */}
  await counter('visits',!counted);
}
window.addEventListener('mt4-gold-download-started',()=>{
  // Fired only after checksum verification and an actual browser download was initiated.
  void counter('downloads',true);
});
window.addEventListener('mt4-language-change',()=>{
  // Reformat already visible figures for the selected language.
  for(const [key,id] of [['visits','mt4VisitCount'],['downloads','mt4DownloadCount']]){
    const el=document.getElementById(id);
    if(!el||!/^[\d.,\s]+$/.test(el.textContent))continue;
    const numeric=el.textContent.replace(/[^0-9]/g,'');
    if(numeric)display(key,Number(numeric));
  }
});
void registerVisit();
void counterRead('downloads');
