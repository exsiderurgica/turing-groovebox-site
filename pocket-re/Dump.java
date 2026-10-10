// Pocket Master GUI task map (NDS32 LE).
// Map creator 0x2c4f8, its callers, and the three GUI_MAIN_TASK entry points.
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
  private PrintWriter out; private final Set<Long> dumped=new LinkedHashSet<>();
  private void p(String s){out.println(s);println(s);}
  private void dumpFn(Function f,String why)throws Exception{
    if(f==null){p("NOFUNC "+why);return;} if(!dumped.add(f.getEntryPoint().getOffset()))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<1400){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,180,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dumpFn(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private void callers(long ep,String label)throws Exception{
    Address a=toAddr(ep);ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(a);int n=0;
    while(it.hasNext()){Reference r=it.next();if(!r.getReferenceType().isCall())continue;n++;Function f=getFunctionContaining(r.getFromAddress());p("CALLER "+label+" from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));if(f!=null)dumpFn(f,"caller:"+label);}p("CALLERCOUNT "+label+"="+n);
  }
  private void refsTo(long ep,String label)throws Exception{
    Address a=toAddr(ep);ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(a);int n=0;while(it.hasNext()){Reference r=it.next();n++;Function f=getFunctionContaining(r.getFromAddress());p("REFTO "+label+" from="+r.getFromAddress()+" type="+r.getReferenceType()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));}p("REFTOCOUNT "+label+"="+n);
  }
  @Override public void run()throws Exception{
    String[] a=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a.length>0?a[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master GUI task map v9");p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dumpFn(0x2c4f8L,"GUI_MAIN_TASK creator");callers(0x2c4f8L,"gui-task-creator");
      dumpFn(0x2c414L,"GUI entry mode 1");refsTo(0x2c414L,"gui-entry-2c414");
      dumpFn(0x2c46cL,"GUI entry mode 0");refsTo(0x2c46cL,"gui-entry-2c46c");
      dumpFn(0x2c4b0L,"GUI entry mode 3");refsTo(0x2c4b0L,"gui-entry-2c4b0");
      dumpFn(0x681e4L,"RTOS task-create API");callers(0x681e4L,"task-create-api");
    }finally{out.close();}
  }
}
