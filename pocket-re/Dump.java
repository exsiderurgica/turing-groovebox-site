// Pocket Master region-b bootstrap continuation trace (NDS32 LE).
// Focus: reset -> bootstrap -> forced continuation at 0x5fb78 -> application/task setup.
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
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<1200){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,180,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dumpFn(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private Set<Long> directCallees(Function f)throws Exception{
    Set<Long>s=new LinkedHashSet<>();if(f==null)return s;InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);
    while(ii.hasNext()){Instruction i=ii.next();for(Reference r:i.getReferencesFrom()){if(r.getReferenceType().isCall()){long x=r.getToAddress().getOffset();if(x>=0x10000&&x<0xe185c)s.add(x);}}}return s;
  }
  private void dumpChildren(long ep,String why)throws Exception{
    Function f=getFunctionAt(toAddr(ep));if(f==null)return;for(long c:directCallees(f)){p("CHILD "+why+" -> 0x"+Long.toHexString(c));dumpFn(c,"child-of:"+why);}
  }
  private Address findAscii(String s)throws Exception{Address a=currentProgram.getMemory().findBytes(currentProgram.getMemory().getMinAddress(),s.getBytes(StandardCharsets.US_ASCII),null,true,monitor);p("STRING \""+s+"\" -> "+a);return a;}
  private void refsToString(String s)throws Exception{Address a=findAscii(s);if(a==null)return;ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(a);int n=0;while(it.hasNext()){Reference r=it.next();n++;Function f=getFunctionContaining(r.getFromAddress());p("STRREF "+s+" from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));if(f!=null)dumpFn(f,"strref:"+s);}p("STRREFCOUNT "+s+"="+n);}
  @Override public void run()throws Exception{
    String[] a=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a.length>0?a[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master bootstrap continuation trace v7");p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dumpFn(0x11d28L,"reset-entry");dumpFn(0x5fb18L,"bootstrap-head");
      dumpFn(0x5fb78L,"bootstrap-tail");dumpChildren(0x5fb78L,"bootstrap-tail");
      dumpFn(0x5fcc2L,"bootstrap-error-tail");dumpChildren(0x5fcc2L,"bootstrap-error-tail");
      refsToString("GUI_MAIN_TASK");refsToString("DRUM Audio task");refsToString("Flash Task");refsToString("User Msg Task");refsToString("SPIS Audio task");refsToString("task name:%s");
    }finally{out.close();}
  }
}
