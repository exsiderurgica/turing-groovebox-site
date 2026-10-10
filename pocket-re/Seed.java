// Ghidra headless seed for Pocket Master section b, runtime base 0x10000.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.mem.MemoryBlock;

public class Seed extends GhidraScript {
  private void seed(long addr,String name){
    Address a=toAddr(addr);try{currentProgram.getSymbolTable().addExternalEntryPoint(a);}catch(Exception ignored){}
    try{boolean ok=disassemble(a);println("seed "+name+" "+a+" disasm="+ok);if(getFunctionAt(a)==null){try{createFunction(a,name);}catch(Exception ignored){}}}catch(Exception e){println("seed "+name+" note="+e.getMessage());}
  }
  @Override public void run()throws Exception{
    println("POCKET_RE SEED language="+currentProgram.getLanguageID()+" base="+toAddr(0x10000L));
    for(MemoryBlock b:currentProgram.getMemory().getBlocks()){try{b.setRead(true);b.setExecute(true);}catch(Exception ignored){}}
    for(int off=0;off<=0xa0;off+=4)seed(0x10000L+off,String.format("vector_%03x",off));
    seed(0x5fb18L,"app_bootstrap");seed(0x5fb7cL,"app_bootstrap_after_unknown");seed(0x5fcc2L,"app_bootstrap_error_tail");
    seed(0x5431cL,"MainApp_task");
    seed(0x2c414L,"GUI_task_mode1");seed(0x2c46cL,"GUI_task_mode0");seed(0x2c4b0L,"GUI_task_mode3");
  }
}
