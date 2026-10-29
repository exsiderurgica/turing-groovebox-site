// EXS MT4 / minimal bilingual UI and optional donation modal.
const buttons={en:document.getElementById('lang-en'),it:document.getElementById('lang-it')};
function setLang(lang){
  if(lang!=='en'&&lang!=='it')lang='en';
  document.documentElement.lang=lang;
  document.querySelectorAll('[data-en][data-it]').forEach(el=>{
    el.textContent=el.dataset[lang];
  });
  for(const [key,button] of Object.entries(buttons)) if(button) button.setAttribute('aria-pressed',String(key===lang));
  try{localStorage.setItem('exs-mt4-language',lang)}catch(_){}
  window.dispatchEvent(new CustomEvent('mt4-language-change',{detail:{lang}}));
}
let lang='en';
try{lang=localStorage.getItem('exs-mt4-language')||'en'}catch(_){}
setLang(lang);
for(const [key,button] of Object.entries(buttons))if(button)button.addEventListener('click',()=>setLang(key));
const modal=document.getElementById('donationModal');
const close=()=>{modal.classList.remove('open');modal.setAttribute('aria-hidden','true');};
const show=()=>{modal.classList.add('open');modal.setAttribute('aria-hidden','false');document.getElementById('freeDownload')?.focus();};
document.getElementById('downloadBtn')?.addEventListener('click',show);
document.getElementById('cancelDownload')?.addEventListener('click',close);
document.getElementById('freeDownload')?.addEventListener('click',()=>{
  close();window.dispatchEvent(new CustomEvent('mt4-download-approved'));
});
document.getElementById('donateDownload')?.addEventListener('click',()=>{
  window.open('https://paypal.me/Exsiderurgica','_blank','noopener,noreferrer');
  close();window.dispatchEvent(new CustomEvent('mt4-download-approved'));
});
modal?.addEventListener('click',e=>{if(e.target===modal)close()});
document.addEventListener('keydown',e=>{if(e.key==='Escape')close()});
