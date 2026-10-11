// Pocket Master physical key/accessor resolver (NDS32 LE).
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
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
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<1800){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,360,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private Function nearestBefore(Address a){
    Function last=null; FunctionIterator it=currentProgram.getFunctionManager().getFunctions(true);
    while(it.hasNext()){Function f=it.next(); if(f.getEntryPoint().compareTo(a)>0)break; last=f;} return last;
  }
  private void around(long addr,String why)throws Exception{
    Address a=toAddr(addr); Function f=getFunctionContaining(a); if(f==null)f=nearestBefore(a);
    p("\n=== NEAREST "+why+" target="+a+" -> "+(f==null?"-":f.getName()+"@"+f.getEntryPoint())+" ===");
    if(f!=null)dumpFn(f,"nearest:"+why);
  }
  private void callers(long addr,String label,int max)throws Exception{
    p("\n=== CALLERS "+label+" @ 0x"+Long.toHexString(addr)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(addr)); LinkedHashSet<Function> fs=new LinkedHashSet<>();
    while(it.hasNext()){Reference r=it.next(); if(!r.getReferenceType().isCall())continue; Function f=getFunctionContaining(r.getFromAddress());
      p("CALL from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint())); if(f!=null)fs.add(f);}
    int n=0; for(Function f:fs){if(n++>=max)break;dumpFn(f,"caller:"+label);} p("UNIQUE_CALLERS="+fs.size());
  }
  private void refsRange(long lo,long hi,String label)throws Exception{
    p("\n=== REFS RANGE "+label+" 0x"+Long.toHexString(lo)+"..0x"+Long.toHexString(hi)+" ===");
    ReferenceManager rm=currentProgram.getReferenceManager(); LinkedHashSet<Function> fs=new LinkedHashSet<>();
    for(long a=lo;a<=hi;a+=4){ReferenceIterator it=rm.getReferencesTo(toAddr(a));while(it.hasNext()){Reference r=it.next();Function f=getFunctionContaining(r.getFromAddress());
      p("REF target=0x"+Long.toHexString(a)+" from="+r.getFromAddress()+" type="+r.getReferenceType()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint())); if(f!=null)fs.add(f);}}
    int n=0;for(Function f:fs){if(n++>=24)break;dumpFn(f,"table-user:"+label);}p("UNIQUE_FUNCS="+fs.size());
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master physical key resolver v25");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      long[] acc={0x146ccL,0x14800L,0x14920L,0x50000L,0x5022cL,0x50250L,0x50280L,0x502ccL,0x50454L};
      String[] names={"state146cc","select14800","phase14920","state50000","word5022c","block50250","indexed50280","word502cc","state50454"};
      for(int i=0;i<acc.length;i++){dump(acc[i],names[i]);callers(acc[i],names[i],10);}
      refsRange(0x000e05d0L,0x000e0630L,"Left/Right descriptor neighborhood");
      around(0x00029b14L,"factory encoder test text switch");
      dump(0x00023c44L,"splash callback");
      dump(0x00023440L,"page2 event");
      dump(0x0005431cL,"MainApp task");
    }finally{out.close();}
  }
}
