import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.listing.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Dump extends GhidraScript {
  private PrintWriter out;
  private final Set<Long> seen=new LinkedHashSet<>();
  private void p(String s){out.println(s);println(s);}
  private void dump(long ep,String why)throws Exception{
    Function f=getFunctionAt(toAddr(ep));
    if(f==null){p("NOFUNC 0x"+Long.toHexString(ep)+" "+why);return;}
    if(!seen.add(ep))return;
    p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" body="+f.getBody()+" ---");
    InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true);int n=0;
    while(ii.hasNext()&&n++<2600){Instruction i=ii.next();p("ASM "+i.getAddress()+"  "+i);}
    try{DecompInterface d=new DecompInterface();d.openProgram(currentProgram);DecompileResults r=d.decompileFunction(f,480,monitor);if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END");}else p("C-FAIL");d.dispose();}catch(Exception e){p("C-EXCEPTION "+e);}
  }
  @Override public void run()throws Exception{
    String[] args=getScriptArgs();out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_clean_boot_report.txt"),StandardCharsets.UTF_8));
    try{
      p("Pocket TTG FX GUI takeover trace v7");
      long[] a={
        0x22e80L,0x233e8L,0x23440L,0x22800L,
        0x2ca44L,0x2c214L,0x47880L,0x4838cL,0x47998L,
        0x48cd0L,0x2f1dcL,0x2f15cL,0x2f2bcL,0x32810L,0x328f8L,
        0x2e3d4L,0x2e3f4L,0x30748L,0x3349cL,0x334b4L,0x33524L,0x33540L,0x3355cL,0x33578L,0x33658L,0x33690L,0x336e4L,
        0x45c38L,0x46180L,0x46198L,0x461b0L,0x461c8L,0x46210L,0x46360L,0x463a8L,0x463c0L,0x46408L,0x46438L,
        0x436b0L,0x2cce0L
      };
      String[] n={
        "page2-enter","page2-leave","page2-event","page2-update",
        "root-create","nested-get","obj-create?","label-part?","child-part?",
        "set-text","set-size-pos-A","set-pos-A","align-A","style-attach","style-color",
        "create-image?","flag-helper?","draw-helper","obj-prop-3349c","obj-prop-334b4","obj-prop-33524","obj-prop-33540","obj-prop-3355c","obj-prop-33578","event-helper-33658","event-helper-33690","obj-prop-336e4",
        "style-init","style-a","style-b","style-c","style-d","style-e","style-f","style-g","style-h","style-i","style-j",
        "gui-global-init","gui-global-init2"
      };
      for(int i=0;i<a.length;i++)dump(a[i],n[i]);
    }finally{out.close();}
  }
}
