// Pocket Master active-screen + page cleanup helper verification (NDS32 LE).
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Dump extends GhidraScript {
  private PrintWriter out;
  private void p(String s){out.println(s);println(s);}
  private void dump(long ep,String why)throws Exception{
    Function f=getFunctionAt(toAddr(ep));
    if(f==null){p("NOFUNC 0x"+Long.toHexString(ep)+" "+why);return;}
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<500){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
    DecompileResults r=d.decompileFunction(f,180,monitor);
    if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
    else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));
    d.dispose();
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master active-screen/delete helper trace v16");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dump(0x2cce0L,"called immediately before page2 root creation");
      dump(0x33980L,"called by stock page2_leave on ctx+8 root");
      dump(0x436b0L,"called immediately before active-screen getter in page2_enter");
    }finally{out.close();}
  }
}
