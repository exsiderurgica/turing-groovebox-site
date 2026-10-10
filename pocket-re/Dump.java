// Targeted headless report for Pocket Master region b (NDS32 LE).
// Follow GUI_MAIN_TASK neighborhood, reset entry, and the candidate function that
// constructs the string-table address around 0x13405c.
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.scalar.Scalar;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Dump extends GhidraScript {
  private PrintWriter out;
  private final Set<Long> dumped=new LinkedHashSet<>();
  private void p(String s){out.println(s);println(s);}
  private void dumpFn(long ep,String why)throws Exception{dumpFn(getFunctionAt(toAddr(ep)),why);}
  private void dumpFn(Function f,String why)throws Exception{
    if(f==null){p("NOFUNC reason="+why);return;}
    if(!dumped.add(f.getEntryPoint().getOffset()))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<500){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,120,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void dumpCallers(long ep,String why)throws Exception{
    Address a=toAddr(ep);ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(a);int n=0;
    while(ri.hasNext()){Reference r=ri.next();if(!r.getReferenceType().isCall())continue;n++;Function f=getFunctionContaining(r.getFromAddress());
      p("CALLER "+why+" target="+a+" from="+r.getFromAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));if(f!=null)dumpFn(f,"caller:"+why);}
    p("CALLERCOUNT "+why+"="+n);
  }
  private Address findAscii(String s)throws Exception{Address a=currentProgram.getMemory().findBytes(currentProgram.getMemory().getMinAddress(),s.getBytes(StandardCharsets.US_ASCII),null,true,monitor);p("STRING "+s+" -> "+a);return a;}
  private boolean scalarEq(Instruction i,long v){for(int op=0;op<i.getNumOperands();op++){Scalar s=i.getScalar(op);if(s!=null&&s.getUnsignedValue()==v)return true;}return false;}
  private void huntNear(Address target,int radius)throws Exception{
    long t=target.getOffset(); Listing l=currentProgram.getListing();InstructionIterator ii=l.getInstructions(true);int n=0;
    while(ii.hasNext()){Instruction i=ii.next();long pc=i.getAddress().getOffset();if(pc>=0xe185cL)continue;if(!i.getMnemonicString().equalsIgnoreCase("sethi"))continue;
      for(long a=t-radius;a<=t+radius;a++){
        long hi=a>>>12,lo=a&0xfffL;if(!scalarEq(i,hi))continue;Instruction q=i;
        for(int k=0;k<8&&q!=null;k++){if(k>0&&(q.getMnemonicString().equalsIgnoreCase("ori")||q.getMnemonicString().toLowerCase().contains("addi"))&&scalarEq(q,lo)){
          Function f=getFunctionContaining(i.getAddress());p(String.format("NEAR-ADDR target=0x%x actual=0x%x at=%s func=%s",t,a,i.getAddress(),f==null?"-":f.getName()+"@"+f.getEntryPoint()));if(f!=null)dumpFn(f,"near-GUI-string-address");n++;break;}q=l.getInstructionAfter(q.getAddress());}
      }
    }p("NEAR-ADDR matches="+n);
  }
  private void dumpVector()throws Exception{p("\n=== VECTOR TABLE ===");for(int off=0;off<=0xa0;off+=4){Address a=toAddr(0x10000L+off);Instruction i=currentProgram.getListing().getInstructionAt(a);p("VECTOR "+a+"  "+(i==null?"-":i));}}
  @Override public void run()throws Exception{
    String[] a=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a.length>0?a[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{p("Pocket Master application entry trace v5");p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      Address gui=findAscii("GUI_MAIN_TASK");huntNear(gui,32);
      dumpFn(0x2c160L,"known GUI-string-table constructor candidate");dumpCallers(0x2c160L,"0x2c160");
      dumpFn(0x11d28L,"reset vector target");dumpCallers(0x11d28L,"reset-target");
      dumpFn(0x626acL,"initialized-data-copy");dumpCallers(0x626acL,"data-copy");
      dumpVector();
    }finally{out.close();}
  }
}
