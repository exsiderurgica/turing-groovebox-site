// Pocket Master three-way physical input mapping (NDS32 LE).
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
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
    long ep=f.getEntryPoint().getOffset();
    if(!dumped.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<1200){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{
      DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
      DecompileResults r=d.decompileFunction(f,300,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));
      d.dispose();
    }catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private void callers(long ep,String label,int maxDump)throws Exception{
    p("\n=== CALLERS "+label+" target=0x"+Long.toHexString(ep)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(ep));
    LinkedHashSet<Long> eps=new LinkedHashSet<>();
    while(it.hasNext()){
      Reference r=it.next();
      if(!r.getReferenceType().isCall())continue;
      Function f=getFunctionContaining(r.getFromAddress());
      p("CALL from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
      if(f!=null)eps.add(f.getEntryPoint().getOffset());
    }
    p("UNIQUE_CALLERS="+eps.size());
    int n=0;for(long x:eps){if(n++>=maxDump)break;dump(x,"caller-of:"+label);}
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master physical input resolver v18");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());

      dump(0x5219cL,"three-way input dispatcher");
      dump(0x65a78L,"input index 0 target");
      dump(0x15240L,"input index 1 target");
      dump(0x1504cL,"input index 2 target");
      callers(0x65a78L,"index0-target",16);
      callers(0x15240L,"index1-target",16);
      callers(0x1504cL,"index2-target",16);

      dump(0x521bcL,"single-byte hardware/accessory state");
      dump(0x153dcL,"consumer of 521bc");
      dump(0x14accL,"consumer action A");
      dump(0x14b00L,"consumer action B");
      dump(0x5223cL,"hardware state setter used by 538fc");
      dump(0x538fcL,"secondary hardware-state consumer");

      // Likely low-level event/state helpers near the three input targets.
      dump(0x15000L,"near input2 target");
      dump(0x15200L,"near input1 target");
      dump(0x65a00L,"near input0 target");
    }finally{out.close();}
  }
}
