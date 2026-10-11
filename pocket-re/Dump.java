// Pocket Master: find REAL code sequences loading control-test strings (NDS32 LE).
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
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<2200){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,420,monitor);
      if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}
      else p("C-FAIL "+(r==null?"null":r.getErrorMessage()));d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  private void scanLoad(long target,String label)throws Exception{
    long hi=target>>>12, lo=target&0xfffL;
    p("\n=== SCAN ADDRESS LOAD "+label+" target=0x"+Long.toHexString(target)+" hi=0x"+Long.toHexString(hi)+" lo=0x"+Long.toHexString(lo)+" ===");
    InstructionIterator it=currentProgram.getListing().getInstructions(true);
    LinkedHashSet<Long> funcs=new LinkedHashSet<>();
    while(it.hasNext()){
      Instruction a=it.next(); String sa=a.toString().toLowerCase(Locale.ROOT);
      if(!sa.startsWith("sethi ") || !sa.contains("0x"+Long.toHexString(hi))) continue;
      String reg=null; int sp=sa.indexOf(' '), comma=sa.indexOf(','); if(sp>=0&&comma>sp) reg=sa.substring(sp+1,comma).trim();
      Instruction b=a;
      for(int k=0;k<6;k++){
        b=b.getNext(); if(b==null)break; String sb=b.toString().toLowerCase(Locale.ROOT);
        if(sb.startsWith("ori ") && reg!=null && sb.contains(reg) && sb.contains("0x"+Long.toHexString(lo))){
          Function f=getFunctionContaining(a.getAddress());
          p("MATCH sethi="+a.getAddress()+" '"+a+"' ori="+b.getAddress()+" '"+b+"' func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
          if(f!=null)funcs.add(f.getEntryPoint().getOffset());
        }
      }
    }
    int n=0;for(long ep:funcs){if(n++>=10)break;dumpFn(getFunctionAt(toAddr(ep)),"real-load:"+label);}
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master real control-string loader trace v20");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      scanLoad(0x000cf864L,"Left");
      scanLoad(0x000cf86cL,"Right");
      scanLoad(0x000d11d0L,"Please Test Enc Key");
      scanLoad(0x000d1406L,"VALUE/VOL");
      scanLoad(0x000d16a8L,"Press VALUE/VOL prompt");
      scanLoad(0x00131174L,"menu key");
    }finally{out.close();}
  }
}
