import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.scalar.Scalar;
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
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage())); d.dispose();
    }catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dump(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private void callers(long ep,String label,int limit)throws Exception{
    p("\n=== CALLERS "+label+" target=0x"+Long.toHexString(ep)+" ===");
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(toAddr(ep));
    LinkedHashSet<Long> eps=new LinkedHashSet<>();
    while(it.hasNext()){
      Reference r=it.next(); if(!r.getReferenceType().isCall())continue;
      Function f=getFunctionContaining(r.getFromAddress());
      p("CALL from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
      if(f!=null)eps.add(f.getEntryPoint().getOffset());
    }
    int n=0; for(long x:eps){if(n++>=limit)break;dump(x,"caller-of:"+label);}
  }
  private Map<Long,LinkedHashSet<Function>> scan7fEvents() throws Exception {
    TreeMap<Long,LinkedHashSet<Function>> map=new TreeMap<>();
    p("\n=== ALL SCALAR IMMEDIATES 0x7f00..0x7fff ===");
    InstructionIterator ii=currentProgram.getListing().getInstructions(true);
    while(ii.hasNext()){
      Instruction ins=ii.next();
      for(int oi=0;oi<ins.getNumOperands();oi++){
        Object[] objs=ins.getOpObjects(oi);
        for(Object o:objs){
          if(!(o instanceof Scalar)) continue;
          long v=((Scalar)o).getUnsignedValue();
          if(v<0x7f00L || v>0x7fffL) continue;
          Function f=getFunctionContaining(ins.getAddress());
          map.computeIfAbsent(v,k->new LinkedHashSet<>()).add(f);
          p(String.format("EV 0x%04x at %s ins=%s func=%s",v,ins.getAddress(),ins.toString(),f==null?"-":f.getName()+"@"+f.getEntryPoint()));
        }
      }
    }
    p("\n=== 0x7F EVENT SUMMARY ===");
    for(Map.Entry<Long,LinkedHashSet<Function>> e:map.entrySet()){
      StringBuilder sb=new StringBuilder(String.format("0x%04x:",e.getKey()));
      for(Function f:e.getValue()) if(f!=null) sb.append(" ").append(f.getName()).append("@").append(f.getEntryPoint());
      p(sb.toString());
    }
    return map;
  }
  private void dumpEventFunctions(Map<Long,LinkedHashSet<Function>> map,long... vals)throws Exception{
    for(long v:vals){
      LinkedHashSet<Function> fs=map.get(v);
      p(String.format("\n=== FUNCTIONS USING EVENT 0x%04x ===",v));
      if(fs==null){p("none");continue;}
      for(Function f:fs) dumpFn(f,String.format("uses-event-0x%04x",v));
    }
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_clean_boot_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket TTG FX stock hardware/event trace v3");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());

      Map<Long,LinkedHashSet<Function>> ev=scan7fEvents();
      dumpEventFunctions(ev,0x7f33L,0x7f34L,0x7f35L,0x7f36L,0x7f37L,0x7f7eL,0x7f7fL);

      dump(0x23c84L,"page0 enter / splash");
      dump(0x23d7cL,"page0 event / splash timeout");
      dump(0x22e80L,"page2 Effects enter");
      dump(0x23440L,"page2 Effects event");
      dump(0x22800L,"page2 common updater");
      dump(0x22d18L,"page2 state helper");
      dump(0x5219cL,"indexed three-way hardware input accessor");
      dump(0x521bcL,"single hardware input accessor");
      dump(0x529d0L,"key state helper");
      dump(0x5299cL,"indexed key state allocator");
      dump(0x53940L,"system key message handler");

      callers(0x5219cL,"indexed-input",30);
      callers(0x521bcL,"single-input",30);
      callers(0x68bdcL,"message-send-68bdc",60);
    }finally{out.close();}
  }
}
