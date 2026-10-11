// Pocket Master page-2 LVGL helper ABI trace (NDS32 LE).
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
    Function f=getFunctionAt(toAddr(ep));
    if(f==null){p("NOFUNC 0x"+Long.toHexString(ep)+" "+why);return;}
    if(!dumped.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<700){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{
      DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
      DecompileResults r=d.decompileFunction(f,180,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));
      d.dispose();
    }catch(Exception e){p("C-EXCEPTION "+e);}
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master page-2 LVGL helper trace v15");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dump(0x2ca44L,"root/screen object used by page2");
      dump(0x47880L,"page2 child object creator A");
      dump(0x4838cL,"page2 child object creator B");
      dump(0x47998L,"page2 child object creator C/image candidate");
      dump(0x2f15cL,"page2 position setter candidate");
      dump(0x2f1dcL,"page2 size setter candidate");
      dump(0x32810L,"page2 style attach candidate");
      dump(0x479b8L,"page2 image/source setter candidate");
      dump(0x334b4L,"page2 style property candidate");
      dump(0x336e4L,"page2 style property candidate");
      dump(0x33540L,"page2 style property candidate");
      dump(0x33524L,"page2 style property candidate");
      dump(0x33578L,"page2 style property candidate");
      dump(0x3355cL,"page2 style property candidate");
    }finally{out.close();}
  }
}
