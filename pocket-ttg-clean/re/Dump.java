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
  private void dumpFn(Function f,String why)throws Exception{
    if(f==null){p("NOFUNC "+why);return;}
    long ep=f.getEntryPoint().getOffset(); if(!dumped.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true); int n=0;
    while(ii.hasNext()&&n++<3000){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{
      DecompInterface d=new DecompInterface(); d.openProgram(currentProgram);
      DecompileResults r=d.decompileFunction(f,480,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();
    }catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_clean_boot_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket TTG FX runtime write-side trace v5");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());

      LinkedHashSet<Function> writers=new LinkedHashSet<>();
      p("\n=== GP-RELATIVE STORES IN 0x4d000..0x50000 ===");
      InstructionIterator ii=currentProgram.getListing().getInstructions(toAddr(0x4d000),true);
      while(ii.hasNext()){
        Instruction ins=ii.next(); long a=ins.getAddress().getOffset(); if(a>=0x50000)break;
        String m=ins.getMnemonicString().toLowerCase(); String s=ins.toString().toLowerCase();
        boolean store=m.startsWith("sbi")||m.startsWith("shi")||m.startsWith("swi")||m.startsWith("smw")||m.startsWith("sdi");
        if(store && s.contains(".gp")){
          Function f=getFunctionContaining(ins.getAddress());
          p("STOREGP "+ins.getAddress()+" "+ins+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
          if(f!=null) writers.add(f);
        }
      }
      p("writers="+writers.size());
      for(Function f:writers) dumpFn(f,"gp-writer-4d000-50000");

      /* Getter cluster + exact neighbor function entries found by walking the function table. */
      FunctionManager fm=currentProgram.getFunctionManager();
      p("\n=== ALL FUNCTION ENTRIES 0x4df00..0x4ef80 ===");
      FunctionIterator fi=fm.getFunctions(toAddr(0x4df00),true);
      int n=0;
      while(fi.hasNext()){
        Function f=fi.next(); long e=f.getEntryPoint().getOffset(); if(e>=0x4ef80)break;
        p("ENTRY "+f.getEntryPoint()+" "+f.getName()+" body="+f.getBody());
        if(n++<120) dumpFn(f,"runtime-cluster-entry");
      }

      dump(0x4e978L,"selection-array-getter");
      dump(0x4ea04L,"enable-array-getter");
      dump(0x4ead4L,"enable-bitmask-getter");
    }finally{out.close();}
  }
}
