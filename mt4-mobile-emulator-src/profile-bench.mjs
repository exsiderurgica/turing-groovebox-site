// Offline WebAssembly performance profile on unchanged EXS MT4 P7696 GOLD.
// Isolates deterministic firmware behavior from Android audio scheduling.
import { readFileSync, writeFileSync } from "node:fs";
import { jitImports } from "./jit.js";

const mhz = 240, untilMs = 32000, quantum = 128, hz = 44100;
const pkg = readFileSync(process.env.MT4_PACKAGE);
const wasm = readFileSync(new URL("./dist/fm1_web.wasm", import.meta.url));
let emu;
const jit = {regions:0, failed:0, error:"", compileMs:0, compileMaxMs:0, bytes:0, tableSlots:0};
const {instance} = await WebAssembly.instantiate(wasm, {env:jitImports(()=>emu,jit)});
emu = instance.exports;
emu.fm1_jit(1,16);
emu.fm1_ahead(1024);
const ptr=emu.fm1_alloc(pkg.length);
new Uint8Array(emu.memory.buffer,ptr,pkg.length).set(pkg);
if(emu.fm1_boot_fwsc(ptr,pkg.length,0,0,mhz)!==0) throw Error("MT4 GOLD package boot failed");
let budget=mhz*1e6*0.02;
const playBudget=mhz*1e6*1.5*quantum/hz;
let playing=false,lastBin=0,lastWall=performance.now(),maxRender=0,short=0;
let lastRegions=0,lastCompile=0;
const bins=[];
while(emu.fm1_ms()<untilMs){
  const start=performance.now();
  const have=emu.fm1_render(quantum,budget);
  const render=performance.now()-start;
  maxRender=Math.max(maxRender,render);
  if(have>0&&!playing) { playing=true;budget=playBudget; }
  if(have<quantum)short++;
  if(emu.fm1_halted()) throw Error("Emulated CPU halted at "+emu.fm1_ms());
  const elapsed=emu.fm1_ms();
  if(elapsed>=lastBin+1000){
    const wall=performance.now();
    const vm=process.memoryUsage();
    const stats={
      emulatedMs:elapsed,
      elapsedWallMs:+(wall-lastWall).toFixed(2),
      speed:+((elapsed-lastBin)/(wall-lastWall)).toFixed(3),
      wasmMemoryMiB:+(emu.memory.buffer.byteLength/1048576).toFixed(2),
      processRssMiB:+(vm.rss/1048576).toFixed(2),
      jitRegions:jit.regions,
      jitAdded:jit.regions-lastRegions,
      jitTotalKiB:+(jit.bytes/1024).toFixed(1),
      jitCompilationMs:+(jit.compileMs-lastCompile).toFixed(2),
      jitTableSlots:emu.__indirect_function_table.length,
      idlePercent:+(emu.fm1_idle_ms()*100/elapsed).toFixed(2),
      maxRenderMs:+maxRender.toFixed(2),
      shortAudioChunks:short
    };
    bins.push(stats);
    console.log("MT4_PROFILE "+JSON.stringify(stats));
    lastWall=wall;lastBin=elapsed;lastRegions=jit.regions;lastCompile=jit.compileMs;maxRender=0;
  }
}
const out=process.env.GITHUB_WORKSPACE+"/mt4-profile-results.json";
writeFileSync(out,JSON.stringify({firmware:"P7696 GOLD",mhz,jit,bins},null,2));
console.log("MT4_PROFILE_COMPLETE "+out+" bins="+bins.length);
