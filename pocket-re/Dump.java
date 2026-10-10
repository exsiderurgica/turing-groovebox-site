// Pocket Master page-2 overwrite safety audit (NDS32 LE).
// Verify whether any code/data outside each callback references an interior address.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Dump extends GhidraScript {
  private PrintWriter out;
  private void p(String s){out.println(s);println(s);}

  private boolean in(long x,long lo,long hi){return x>=lo&&x<=hi;}

  private void audit(String name,long lo,long hi)throws Exception{
    p("\n=== AUDIT "+name+" [0x"+Long.toHexString(lo)+",0x"+Long.toHexString(hi)+"] ===");
    ReferenceManager rm=currentProgram.getReferenceManager();
    int total=0, externalInterior=0, entryRefs=0;
    for(long x=lo;x<=hi;x++){
      Address a=toAddr(x);
      ReferenceIterator it=rm.getReferencesTo(a);
      while(it.hasNext()){
        Reference r=it.next(); total++;
        long from=r.getFromAddress().getOffset();
        boolean fromInside=in(from,lo,hi);
        if(x==lo) entryRefs++;
        if(x!=lo && !fromInside){
          externalInterior++;
          Function f=getFunctionContaining(r.getFromAddress());
          p("EXTERNAL_INTERIOR target="+a+" from="+r.getFromAddress()+" type="+r.getReferenceType()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint()));
        }
      }
    }
    p("SUMMARY "+name+" refs="+total+" entryRefs="+entryRefs+" externalInteriorRefs="+externalInterior);
    Function f=getFunctionAt(toAddr(lo));
    p("FUNCTION "+(f==null?"-":f.getName()+" body="+f.getBody()));
    if(f!=null){
      InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
      while(ii.hasNext()&&n++<8){Instruction i=ii.next();p("HEAD "+i.getAddress()+" "+i);}
    }
  }

  @Override public void run()throws Exception{
    String[] args=getScriptArgs();
    out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket Master page-2 overwrite audit v14");
      p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
      audit("page2-enter",0x22e80L,0x233e7L);
      audit("page2-leave",0x233e8L,0x2343fL);
      audit("page2-event",0x23440L,0x2366dL);
    }finally{out.close();}
  }
}
