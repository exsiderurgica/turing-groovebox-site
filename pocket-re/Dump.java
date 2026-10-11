// Pocket Master stock key-message translator trace (NDS32 LE).
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
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
    long ep=f.getEntryPoint().getOffset(); if(!dumped.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<2200){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,420,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private Function nearestBefore(Address a){Function last=null;FunctionIterator it=currentProgram.getFunctionManager().getFunctions(true);while(it.hasNext()){Function f=it.next();if(f.getEntryPoint().compareTo(a)>0)break;last=f;}return last;}
  private void window(long addr,String why)throws Exception{
    Address a=toAddr(addr);Listing l=currentProgram.getListing();Function f=getFunctionContaining(a);if(f!=null){dumpFn(f,"contains:"+why);return;}
    p("\n=== WINDOW "+why+" target="+a+" ===");Instruction cur=l.getInstructionAt(a);if(cur==null)cur=l.getInstructionContaining(a);if(cur==null)cur=l.getInstructionAfter(a);
    if(cur==null){p("NO INSTRUCTION");return;}Instruction start=cur;for(int i=0;i<36;i++){Instruction q=l.getInstructionBefore(start.getAddress());if(q==null)break;start=q;}
    Instruction q=start;for(int i=0;i<120&&q!=null;i++){p("WIN "+q.getAddress()+"  "+q);q=l.getInstructionAfter(q.getAddress());}
  }
  private void xrefs(long addr,String label)throws Exception{
    p("\n=== XREFS "+label+" @ 0x"+Long.toHexString(addr)+" ===");ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(addr));LinkedHashSet<Function> fs=new LinkedHashSet<>();int refs=0;
    while(it.hasNext()){Reference r=it.next();refs++;Function f=getFunctionContaining(r.getFromAddress());p("REF from="+r.getFromAddress()+" type="+r.getReferenceType()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));if(f!=null)fs.add(f);else window(r.getFromAddress().getOffset(),"xref-site:"+label);}
    p("REF_COUNT="+refs+" UNIQUE_FUNCS="+fs.size());for(Function f:fs)dumpFn(f,"xref:"+label);
  }
  private void force(long ep,String name)throws Exception{
    Address a=toAddr(ep);try{disassemble(a);}catch(Exception ignored){}Function f=getFunctionAt(a);if(f==null){try{f=createFunction(a,name);}catch(Exception ignored){}}
    if(f!=null)dumpFn(f,"forced:"+name);else window(ep,"forced-window:"+name);
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master stock key-message translator v26");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      // raw-b string offset + 0x10000 runtime base
      xrefs(0x0014100cL,"MSG_NEXT");
      xrefs(0x00141018L,"MSG_PRE");
      xrefs(0x00141174L,"menu key");
      xrefs(0x00141180L,"MSG_MUTE");
      xrefs(0x0014118cL,"MSG_MAIN_VOL_UP");
      xrefs(0x0014119cL,"MSG_MAIN_VOL_DW");
      xrefs(0x000e11d0L,"Please Test Enc Key");
      xrefs(0x000e1426L,"VALUE/VOL");
      // Force code around factory encoder switch and raw Left/Right descriptor references.
      force(0x00029a80L,"factory_enc_switch_area");
      force(0x00016e80L,"key_descriptor_init_area");
      force(0x00016f00L,"key_descriptor_init_area2");
      // Core UI and message queue context.
      force(0x0005431cL,"MainApp_task");
      force(0x0002c9f0L,"GUI_global_event");
      force(0x00023440L,"GUI_page2_event");
    }finally{out.close();}
  }
}
