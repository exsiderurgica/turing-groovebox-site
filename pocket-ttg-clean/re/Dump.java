import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Dump extends GhidraScript {
  private PrintWriter out;
  private final Set<Long> dumped=new LinkedHashSet<>();
  private void p(String s){out.println(s);println(s);}
  private void dumpFn(Function f,String why)throws Exception{
    if(f==null){p("NOFUNC "+why);return;}
    long ep=f.getEntryPoint().getOffset(); if(!dumped.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true); int n=0;
    while(ii.hasNext()&&n++<2500){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{
      DecompInterface d=new DecompInterface(); d.openProgram(currentProgram);
      DecompileResults r=d.decompileFunction(f,480,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage())); d.dispose();
    }catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private void callers(long ep,String label,int limit)throws Exception{
    p("\n=== CALLERS "+label+" target=0x"+Long.toHexString(ep)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(ep));
    LinkedHashSet<Long> eps=new LinkedHashSet<>();
    while(it.hasNext()){
      Reference r=it.next(); if(!r.getReferenceType().isCall())continue;
      Function f=getFunctionContaining(r.getFromAddress());
      p("CALL from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
      if(f!=null)eps.add(f.getEntryPoint().getOffset());
    }
    int n=0; for(long x:eps){if(n++>=limit)break;dump(x,"caller-of:"+label);}
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_clean_boot_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket TTG FX runtime preset/effect trace v4");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());

      dump(0x22e80L,"page2 preset-root enter");
      dump(0x23440L,"page2 preset-root event");
      dump(0x22800L,"page2 updater");

      long[] api={0x4e334L,0x4e490L,0x4e958L,0x4e978L,0x4ea04L,0x4ead4L,0x4ed14L};
      String[] nm={"4e334 refresh/commit","4e490 preset-ish state","4e958 current preset/id","4e978 selected-effect array ptr","4ea04 effect enable by index","4ead4 effect/global state","4ed14 effect/global state2"};
      for(int i=0;i<api.length;i++){dump(api[i],nm[i]);callers(api[i],nm[i],40);}

      /* Nearby functions often include the write side of the same state object. */
      long[] near={0x4e2d0L,0x4e360L,0x4e3b0L,0x4e430L,0x4e4d0L,0x4e8c0L,0x4e900L,0x4e9c0L,0x4ea40L,0x4eaa0L,0x4eb00L,0x4eb80L,0x4ec00L,0x4ec80L,0x4ed50L,0x4ee00L};
      for(long x:near) dump(x,"near-runtime-state-api");
    }finally{out.close();}
  }
}
