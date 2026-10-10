// Pocket Master region-b bootstrap trace (NDS32 LE).
// Focus: reset -> runtime init -> application bootstrap 0x5fb18 -> direct callees/task setup.
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
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
    if(!dumped.add(f.getEntryPoint().getOffset()))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<800){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,150,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dumpFn(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private Set<Long> directCallees(Function f)throws Exception{
    Set<Long> outSet=new LinkedHashSet<>(); if(f==null)return outSet;
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);
    while(ii.hasNext()){
      Instruction ins=ii.next();
      for(Reference r:ins.getReferencesFrom()){
        if(!r.getReferenceType().isCall())continue;
        long to=r.getToAddress().getOffset();
        if(to>=0x10000L&&to<0xe185cL)outSet.add(to);
      }
    }
    return outSet;
  }
  private void dumpCalleeTree(long root,int depth)throws Exception{
    Set<Long> seen=new LinkedHashSet<>();
    ArrayDeque<long[]> q=new ArrayDeque<>();q.add(new long[]{root,0});
    while(!q.isEmpty()){
      long[] x=q.removeFirst();long ep=x[0];int d=(int)x[1];if(!seen.add(ep))continue;
      Function f=getFunctionAt(toAddr(ep));
      p("TREE depth="+d+" ep=0x"+Long.toHexString(ep)+" fn="+(f==null?"-":f.getName()));
      if(f==null)continue;
      dumpFn(f,"bootstrap-tree-depth-"+d);
      if(d>=depth)continue;
      for(long c:directCallees(f))q.addLast(new long[]{c,d+1});
    }
  }
  private Address findAscii(String s)throws Exception{
    Address a=currentProgram.getMemory().findBytes(currentProgram.getMemory().getMinAddress(),s.getBytes(StandardCharsets.US_ASCII),null,true,monitor);
    p("STRING \""+s+"\" -> "+a);return a;
  }
  private void refsToString(String s)throws Exception{
    Address a=findAscii(s);if(a==null)return;
    ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(a);int n=0;
    while(ri.hasNext()){Reference r=ri.next();n++;Function f=getFunctionContaining(r.getFromAddress());p("STRREF "+s+" from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));if(f!=null)dumpFn(f,"string-ref:"+s);}
    p("STRREFCOUNT "+s+"="+n);
  }
  @Override public void run()throws Exception{
    String[] a=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a.length>0?a[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master bootstrap/task trace v6");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dumpFn(0x11d28L,"reset-entry");
      dumpFn(0x5fb18L,"application-bootstrap-from-reset");
      dumpCalleeTree(0x5fb18L,2);
      refsToString("GUI_MAIN_TASK");
      refsToString("DRUM Audio task");
      refsToString("Flash Task");
      refsToString("User Msg Task");
      refsToString("SPIS Audio task");
      refsToString("Current Task will be deleted");
      refsToString("task name:%s");
    }finally{out.close();}
  }
}
