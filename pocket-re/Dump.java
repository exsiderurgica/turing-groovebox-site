// Pocket Master MainApp -> GUI task reconstruction (NDS32 LE).
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
    if(f==null){p("NOFUNC "+why);return;}if(!dumped.add(f.getEntryPoint().getOffset()))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;while(ii.hasNext()&&n++<2200){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,240,monitor);if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private Set<Long> callees(Function f){Set<Long>s=new LinkedHashSet<>();if(f==null)return s;InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);while(it.hasNext()){Instruction i=it.next();for(Reference r:i.getReferencesFrom()){if(r.getReferenceType().isCall()){long x=r.getToAddress().getOffset();if(x>=0x10000&&x<0xe185c)s.add(x);}}}return s;}
  private void children(long ep,String label)throws Exception{Function f=getFunctionAt(toAddr(ep));if(f==null)return;for(long c:callees(f)){p("CHILD "+label+" -> 0x"+Long.toHexString(c));dump(c,"child:"+label);}}
  @Override public void run()throws Exception{
    String[] a=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a.length>0?a[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master MainApp/GUI map v10");p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dump(0x5431cL,"MainApp task");children(0x5431cL,"MainApp");
      dump(0x2c4f8L,"GUI task creator");
      dump(0x2c414L,"GUI task mode1");children(0x2c414L,"GUI-mode1");
      dump(0x2c46cL,"GUI task mode0");children(0x2c46cL,"GUI-mode0");
      dump(0x2c4b0L,"GUI task mode3");children(0x2c4b0L,"GUI-mode3");
    }finally{out.close();}
  }
}
