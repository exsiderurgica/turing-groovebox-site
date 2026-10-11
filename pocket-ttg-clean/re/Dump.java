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
    while(ii.hasNext()&&n++<2600){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
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
      p("Pocket TTG FX clean Effects/input trace v2");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dump(0x23c84L,"page0 enter / splash");
      dump(0x23d7cL,"page0 event / splash timeout");
      dump(0x22e80L,"page2 Effects enter");
      dump(0x23440L,"page2 Effects event");
      dump(0x22800L,"page2 common updater");
      dump(0x22628L,"page2 helper A");
      dump(0x2267cL,"page2 helper B");
      dump(0x226ccL,"page2 helper C");
      dump(0x2294cL,"page2 helper D");
      dump(0x22b98L,"page2 helper E");
      dump(0x22bf8L,"page2 helper F");
      dump(0x22ca8L,"page2 helper G");
      dump(0x22d18L,"page2 state helper");
      dump(0x5219cL,"indexed three-way hardware input accessor");
      dump(0x521bcL,"single hardware input accessor");
      dump(0x14800L,"Effects selected-block getter candidate");
      dump(0x14920L,"page/splash phase getter candidate");
      dump(0x50000L,"page2 byte state accessor");
      dump(0x5022cL,"page2 16-bit state accessor");
      dump(0x50280L,"page2 indexed 0..8 accessor");
      dump(0x502ccL,"page2 32-bit accessor");
      dump(0x50454L,"page2 final byte accessor");
      callers(0x5219cL,"indexed-input",20);
      callers(0x521bcL,"single-input",20);
      callers(0x14800L,"selected-block-getter",30);
      callers(0x22800L,"page2-common-updater",20);
    }finally{out.close();}
  }
}
