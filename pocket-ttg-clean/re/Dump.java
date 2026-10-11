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
    long ep=f.getEntryPoint().getOffset(); if(!dumped.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true); int n=0;
    while(ii.hasNext()&&n++<2200){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{
      DecompInterface d=new DecompInterface(); d.openProgram(currentProgram);
      DecompileResults r=d.decompileFunction(f,480,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage())); d.dispose();
    }catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private void callers(long ep,String label)throws Exception{
    p("\n=== CALLERS "+label+" @0x"+Long.toHexString(ep)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(ep));
    LinkedHashSet<Function> fs=new LinkedHashSet<>();
    while(it.hasNext()){
      Reference r=it.next();
      Function f=getFunctionContaining(r.getFromAddress());
      p("REF "+r.getReferenceType()+" from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
      if(f!=null && r.getReferenceType().isCall()) fs.add(f);
    }
    for(Function f:fs) dumpFn(f,"caller-of:"+label);
  }
  private void refsRange(long lo,long hi,String label){
    p("\n=== REFERENCES INTO "+label+" [0x"+Long.toHexString(lo)+",0x"+Long.toHexString(hi)+") ===");
    int c=0;
    for(long a=lo;a<hi;a++){
      ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(a));
      while(it.hasNext()){
        Reference r=it.next();
        p("RANGE-REF to="+toAddr(a)+" type="+r.getReferenceType()+" from="+r.getFromAddress());c++;
      }
    }
    p("RANGE-REF-COUNT "+label+" = "+c);
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_clean_boot_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket TTG FX runtime control + code-cave audit v6");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());

      refsRange(0x1101aeL,0x110544L,"large-zero-cave");
      refsRange(0x10f314L,0x10f656L,"old-zero-cave");

      long[] api={0x4e96cL,0x4e978L,0x4e980L,0x4ea04L,0x4ea74L,0x4ea98L,0x4eb1cL,0x4ecbcL,0x4ecc4L,0x4ecd4L,0x4ecf0L,0x4ed04L,0x4d884L};
      String[] nm={"load-preset","route-get","route-copy","enable-get","enable-copy","enable-ptr","select-algorithm","param-block-base","param-touch","param-get","selected-alg-get","param-block-ptr","param-set-core"};
      for(int i=0;i<api.length;i++){dump(api[i],nm[i]);callers(api[i],nm[i]);}
    }finally{out.close();}
  }
}
