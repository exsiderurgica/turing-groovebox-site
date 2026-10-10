// Pocket Master splash -> operational page-2 UI reconstruction (NDS32 LE).
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
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;while(ii.hasNext()&&n++<2600){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,300,monitor);if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private Set<Long> callees(Function f){Set<Long>s=new LinkedHashSet<>();if(f==null)return s;InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);while(it.hasNext()){Instruction i=it.next();for(Reference r:i.getReferencesFrom()){if(r.getReferenceType().isCall()){long x=r.getToAddress().getOffset();if(x>=0x10000&&x<0xe185c)s.add(x);}}}return s;}
  private void childList(long ep,String label)throws Exception{Function f=getFunctionAt(toAddr(ep));if(f==null)return;for(long c:callees(f))p("CHILD "+label+" -> 0x"+Long.toHexString(c)+" fn="+(getFunctionAt(toAddr(c))==null?"-":getFunctionAt(toAddr(c)).getName()));}
  private void dumpChildren(long ep,String label)throws Exception{Function f=getFunctionAt(toAddr(ep));if(f==null)return;for(long c:callees(f)){p("CHILD "+label+" -> 0x"+Long.toHexString(c));dump(c,"child:"+label);}}
  @Override public void run()throws Exception{
    String[] a=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a.length>0?a[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master splash/page2 map v13");p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dump(0x23c44L,"splash GIF widget callback");dumpChildren(0x23c44L,"splash-widget");
      dump(0x23d7cL,"splash page event");
      dump(0x22e80L,"operational page2 enter");childList(0x22e80L,"page2-enter");
      dump(0x233e8L,"operational page2 leave");childList(0x233e8L,"page2-leave");
      dump(0x23440L,"operational page2 event");childList(0x23440L,"page2-event");
      dump(0x16288L,"screen switch API");
    }finally{out.close();}
  }
}
