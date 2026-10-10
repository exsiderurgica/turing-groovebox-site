// Pocket Master normal GUI framework/page-0 reconstruction (NDS32 LE).
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
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
    while(ii.hasNext()&&n++<2200){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,240,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private Set<Long> callees(Function f){Set<Long>s=new LinkedHashSet<>();if(f==null)return s;InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);while(it.hasNext()){Instruction i=it.next();for(Reference r:i.getReferencesFrom()){if(r.getReferenceType().isCall()){long x=r.getToAddress().getOffset();if(x>=0x10000&&x<0xe185c)s.add(x);}}}return s;}
  private void children(long ep,String label)throws Exception{Function f=getFunctionAt(toAddr(ep));if(f==null)return;for(long c:callees(f)){p("CHILD "+label+" -> 0x"+Long.toHexString(c));dump(c,"child:"+label);}}
  private void callers(long ep,String label)throws Exception{ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(ep));int n=0;while(it.hasNext()){Reference r=it.next();if(!r.getReferenceType().isCall())continue;n++;Function f=getFunctionContaining(r.getFromAddress());p("CALLER "+label+" from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));}p("CALLERCOUNT "+label+"="+n);}
  @Override public void run()throws Exception{
    String[] a=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a.length>0?a[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master normal GUI/page0 map v11");p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dump(0x16330L,"register screen triple");callers(0x16330L,"screen-register");
      dump(0x162e8L,"select/enter screen");callers(0x162e8L,"screen-select");children(0x162e8L,"screen-select");
      dump(0x1634cL,"register global GUI callbacks");
      dump(0x16120L,"GUI framework init");children(0x16120L,"GUI-framework-init");
      dump(0x23c84L,"normal page0 callback A");children(0x23c84L,"page0-A");
      dump(0x23d38L,"normal page0 callback B");children(0x23d38L,"page0-B");
      dump(0x23d7cL,"normal page0 callback C");children(0x23d7cL,"page0-C");
      dump(0x15824L,"display/input init");children(0x15824L,"display-input-init");
      dump(0x51008L,"display init child");children(0x51008L,"display-init-child");
      dump(0x157c8L,"display init tail");children(0x157c8L,"display-init-tail");
      dump(0x186ccL,"GUI init child 1");children(0x186ccL,"gui-init1");
      dump(0x18b68L,"GUI init child 2");children(0x18b68L,"gui-init2");
      dump(0x4fa60L,"GUI init child 3");children(0x4fa60L,"gui-init3");
      dump(0x4ff00L,"GUI init child 4");children(0x4ff00L,"gui-init4");
      dump(0x4fa24L,"GUI init child 5");children(0x4fa24L,"gui-init5");
    }finally{out.close();}
  }
}
