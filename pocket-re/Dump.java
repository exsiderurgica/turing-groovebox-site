// Pocket Master PRE/NEXT dispatcher reconstruction (NDS32 LE).
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Dump extends GhidraScript {
  private PrintWriter out;
  private void p(String s){out.println(s);println(s);}
  private void range(long lo,long hi,String label){
    p("\n=== RANGE "+label+" 0x"+Long.toHexString(lo)+"..0x"+Long.toHexString(hi)+" ===");
    Listing l=currentProgram.getListing();Address a=toAddr(lo);Instruction q=l.getInstructionAt(a);if(q==null)q=l.getInstructionAfter(a);
    int n=0;while(q!=null && q.getAddress().getOffset()<=hi && n++<2400){p("ASM "+q.getAddress()+"  "+q);q=l.getInstructionAfter(q.getAddress());}
  }
  private void refs(long target,String label){
    p("\n=== REFS "+label+" target=0x"+Long.toHexString(target)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(target));
    while(it.hasNext()){Reference r=it.next();p("REF from="+r.getFromAddress()+" type="+r.getReferenceType());}
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master PRE/NEXT event resolver v27");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      // Complete dispatcher neighborhood around the known PRE/NEXT print/action blocks.
      range(0x00052900L,0x00053100L,"key / media message dispatcher");
      refs(0x00052e34L,"PRE action block");
      refs(0x00052ed0L,"NEXT action block");
      refs(0x00052e50L,"FF action block");
      refs(0x00052f1cL,"FB action block");
      refs(0x00052e08L,"FF/FB END block");
      // Encoder events are already statically verified in this handler.
      range(0x00053940L,0x00053d24L,"system key message handler");
    }finally{out.close();}
  }
}
