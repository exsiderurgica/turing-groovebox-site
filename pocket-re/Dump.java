// Pocket Master hardware input accessor + caller trace (NDS32 LE).
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
    while(ii.hasNext()&&n++<1000){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{
      DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
      DecompileResults r=d.decompileFunction(f,240,monitor);
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
    int n=0;
    for(long x:eps){if(n++>=maxDump)break;dump(x,"caller-of:"+label);}
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master hardware input map v17");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());

      // Functions polled by the operational page-2 event loop.
      dump(0x50000L,"page2 polled byte accessor / candidate knob-or-state");
      dump(0x5022cL,"page2 polled 16-bit accessor / candidate encoder-or-preset");
      dump(0x521bcL,"page2 polled single-byte accessor / candidate VALUE button state");
      dump(0x5219cL,"page2 indexed input accessor, called with indices 0,1,2");
      dump(0x50280L,"page2 indexed 0..8 accessor");
      dump(0x502ccL,"page2 32-bit accessor");
      dump(0x50454L,"page2 final byte accessor");

      callers(0x5219cL,"indexed-input-5219c",16);
      callers(0x521bcL,"single-input-521bc",16);
      callers(0x50000L,"state-50000",10);
      callers(0x5022cL,"state-5022c",10);

      // Existing page callbacks for context.
      dump(0x23440L,"operational page2 event");
      dump(0x16288L,"screen switch API");
    }finally{out.close();}
  }
}
