// Clean-branch Ghidra seed for Pocket Master section b, runtime base 0x10000.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.mem.MemoryBlock;

public class Seed extends GhidraScript {
  private void seed(long addr,String name){
    Address a=toAddr(addr);
    try{currentProgram.getSymbolTable().addExternalEntryPoint(a);}catch(Exception ignored){}
    try{
      disassemble(a);
      if(getFunctionAt(a)==null){try{createFunction(a,name);}catch(Exception ignored){}}
    }catch(Exception ignored){}
  }
  @Override public void run() throws Exception {
    println("POCKET_TTG_CLEAN_SEED language="+currentProgram.getLanguageID());
    for(MemoryBlock b:currentProgram.getMemory().getBlocks()){try{b.setRead(true);b.setExecute(true);}catch(Exception ignored){}}
    for(int off=0;off<=0xa0;off+=4)seed(0x10000L+off,String.format("vector_%03x",off));
    seed(0x5fb18L,"app_bootstrap");
    seed(0x5431cL,"MainApp_task");
    seed(0x2c414L,"GUI_task_mode1");
    seed(0x2c46cL,"GUI_task_mode0");
    seed(0x2c4b0L,"GUI_task_mode3");
    seed(0x23c84L,"GUI_page0_enter");
    seed(0x23d38L,"GUI_page0_leave");
    seed(0x23d7cL,"GUI_page0_event");
    seed(0x22e80L,"GUI_page2_enter");
    seed(0x233e8L,"GUI_page2_leave");
    seed(0x23440L,"GUI_page2_event");
    seed(0x16288L,"GUI_screen_switch");
  }
}
