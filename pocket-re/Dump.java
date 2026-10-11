// Pocket Master physical controls resolver: LEFT / RIGHT / VALUE-VOL / encoder factory strings.
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
    while(ii.hasNext()&&n++<2200){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{
      DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
      DecompileResults r=d.decompileFunction(f,420,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));
      d.dispose();
    }catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void xrefs(long addr,String label)throws Exception{
    p("\n=== XREFS "+label+" @ 0x"+Long.toHexString(addr)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(addr));
    LinkedHashSet<Function> fs=new LinkedHashSet<>();
    while(it.hasNext()){
      Reference r=it.next();
      Function f=getFunctionContaining(r.getFromAddress());
      p("REF from="+r.getFromAddress()+" type="+r.getReferenceType()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
      if(f!=null)fs.add(f);
    }
    p("UNIQUE_FUNCS="+fs.size());
    for(Function f:fs)dumpFn(f,"xref:"+label);
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master physical control resolver v22");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      // These are Ghidra addresses: raw b offset + 0x10000.
      xrefs(0x000dfc64L,"Left string");
      xrefs(0x000dfc6cL,"Right string");
      xrefs(0x000e11d0L,"Please Test Enc Key string");
      xrefs(0x000e1426L,"VALUE/VOL string");
      // Context around page manager / operational page event.
      dump(0x00023440L,"page2 event");
      dump(0x00023c80L,"nearby UI/event dispatcher candidate");
      dump(0x000a7bf0L,"factory UI event dispatcher previously found");
    }finally{out.close();}
  }
}
