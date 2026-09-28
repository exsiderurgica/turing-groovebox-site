(async()=>{
  try{
    const r=await fetch('release.json?ts='+Date.now(),{cache:'no-store'});
    if(!r.ok)return;
    const d=await r.json();
    document.querySelectorAll('[data-release]').forEach(el=>{
      const k=el.dataset.release;
      if(d[k]) el.textContent=d[k];
    });
  }catch(e){}
})();