// Pocket Master factory encoder-key test + VALUE/VOL xref trace (NDS32 LE).
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
    long ep=f.getEntryPoint().getOffset();
    if(!dumped.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<1800){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{
      DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
      DecompileResults r=d.decompileFunction(f,360,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));
      d.dispose();
    }catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private void xrefs(long addr,String label,int maxDump)throws Exception{
    p("\n=== XREFS "+label+" @ 0x"+Long.toHexString(addr)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(addr));
    LinkedHashSet<Long> eps=new LinkedHashSet<>();
    while(it.hasNext()){
      Reference r=it.next();
      Function f=getFunctionContaining(r.getFromAddress());
      p("REF from="+r.getFromAddress()+" type="+r.getReferenceType()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
      if(f!=null)eps.add(f.getEntryPoint().getOffset());
    }
    p("UNIQUE_FUNCS="+eps.size());
    int n=0;for(long x:eps){if(n++>=maxDump)break;dump(x,"xref:"+label);}
  }
  private void childList(long ep,String label)throws Exception{
    Function f=getFunctionAt(toAddr(ep));if(f==null)return;
    InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);
    LinkedHashSet<Long>s=new LinkedHashSet<>();
    while(it.hasNext()){
      Instruction i=it.next();
      for(Reference r:i.getReferencesFrom())if(r.getReferenceType().isCall()){
        long x=r.getToAddress().getOffset(); if(x>=0x10000&&x<0x160000)s.add(x);
      }
    }
    for(long x:s)p("CHILD "+label+" -> 0x"+Long.toHexString(x)+" fn="+(getFunctionAt(toAddr(x))==null?"-":getFunctionAt(toAddr(x)).getName()));
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master factory controls trace v19");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());

      // Exact stock string addresses in region b.
      xrefs(0x000d11d0L,"Please Test Enc Key",12);
      xrefs(0x000d16a9L,"Press VALUE/VOL to Search for devices",12);
      xrefs(0x000d16d0L,"Press VALUE/VOL to Reset",12);
      xrefs(0x00131174L,"menu key",12);

      // Known input accessors, for correlation with those xref'd routines.
      dump(0x5219cL,"three-way state dispatcher");
      dump(0x521bcL,"single-byte state");
      dump(0x14800L,"small 1..5 state getter used by page2");
      dump(0x153dcL,"BLE role handler using 521bc");

      // Once xrefs identify the factory-test function, child list makes its key readers obvious.
      ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(0x000d11d0L));
      while(it.hasNext()){
        Reference r=it.next(); Function f=getFunctionContaining(r.getFromAddress());
        if(f!=null){childList(f.getEntryPoint().getOffset(),"factory-enc-test");break;}
      }
    }finally{out.close();}
  }
}
