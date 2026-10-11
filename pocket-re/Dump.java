// Pocket Master page2 existing widget/label map for a non-destructive TTG event hook.
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Dump extends GhidraScript {
  private PrintWriter out;
  private final Set<Long> dumped=new LinkedHashSet<>();
  private void p(String s){out.println(s);println(s);}
  private void dump(long ep,String why)throws Exception{
    Function f=getFunctionAt(toAddr(ep));if(f==null){p("NOFUNC 0x"+Long.toHexString(ep)+" "+why);return;}
    if(!dumped.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;while(ii.hasNext()&&n++<1800){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,360,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master page2 existing-label map v21");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dump(0x22800L,"page2 common updater called at end of enter/event");
      dump(0x22628L,"page2 helper A");
      dump(0x2267cL,"page2 helper B");
      dump(0x226ccL,"page2 helper C");
      dump(0x2294cL,"page2 helper D");
      dump(0x22b98L,"page2 helper E");
      dump(0x22bf8L,"page2 helper F");
      dump(0x22ca8L,"page2 helper G");
      dump(0x22d18L,"page2 connectivity/state helper");
      dump(0x337b0L,"get child by index candidate");
      dump(0x48cd0L,"label set text");
      dump(0x23440L,"page2 event callsite context");
    }finally{out.close();}
  }
}
