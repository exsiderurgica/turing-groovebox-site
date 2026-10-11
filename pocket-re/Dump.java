// Pocket Master clean boot/page0 + physical control descriptor resolver (NDS32 LE).
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
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private void around(long addr,String why)throws Exception{
    Address a=toAddr(addr); Listing l=currentProgram.getListing();
    Function f=getFunctionContaining(a);
    p("\n=== WINDOW "+why+" target="+a+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint())+" ===");
    if(f!=null){dumpFn(f,"containing:"+why);return;}
    Instruction cur=l.getInstructionAt(a);
    if(cur==null)cur=l.getInstructionContaining(a);
    if(cur==null){cur=l.getInstructionAfter(a);}
    if(cur==null){p("NO INSTRUCTION AROUND "+a);return;}
    Instruction start=cur;
    for(int i=0;i<24;i++){Instruction q=l.getInstructionBefore(start.getAddress());if(q==null)break;start=q;}
    Instruction q=start;
    for(int i=0;i<72 && q!=null;i++){p("WIN "+q.getAddress()+"  "+q);q=l.getInstructionAfter(q.getAddress());}
  }
  private void xrefs(long addr,String label)throws Exception{
    p("\n=== XREFS "+label+" @ 0x"+Long.toHexString(addr)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(addr));
    LinkedHashSet<Function> fs=new LinkedHashSet<>();
    while(it.hasNext()){
      Reference r=it.next(); Function f=getFunctionContaining(r.getFromAddress());
      p("REF from="+r.getFromAddress()+" type="+r.getReferenceType()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
      if(f!=null)fs.add(f); else around(r.getFromAddress().getOffset(),"xref-site:"+label);
    }
    p("UNIQUE_FUNCS="+fs.size());
    for(Function f:fs)dumpFn(f,"xref:"+label);
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master clean boot/control resolver v24");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      dump(0x00023c84L,"GUI page0 enter / boot page");
      dump(0x00023d38L,"GUI page0 leave");
      dump(0x00023d7cL,"GUI page0 event");
      dump(0x00023c44L,"GUI page0 widget event");
      dump(0x00016288L,"screen/page switch API candidate");
      xrefs(0x000dfc64L,"Left string");
      xrefs(0x000dfc6cL,"Right string");
      xrefs(0x000e0600L,"Left descriptor pointer slot");
      xrefs(0x000e0604L,"Right descriptor pointer slot");
      xrefs(0x000e11d0L,"Please Test Enc Key string");
      xrefs(0x000e1426L,"VALUE/VOL string");
      around(0x00029b14L,"instruction referencing encoder-test string");
      dump(0x000a7bf0L,"factory UI event dispatcher");
      dump(0x0005431cL,"MainApp task");
      dump(0x0002c414L,"GUI task mode1");
      dump(0x0002c46cL,"GUI task mode0");
      dump(0x0002c4b0L,"GUI task mode3");
    }finally{out.close();}
  }
}
