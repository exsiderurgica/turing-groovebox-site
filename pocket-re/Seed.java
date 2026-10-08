// Ghidra headless seed for Pocket Master section b.
// The HTFW b-section uses data pointers = section offset + 0x10000,
// therefore the raw section is imported at 0x10000.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.mem.MemoryBlock;

public class Seed extends GhidraScript {
    @Override
    public void run() throws Exception {
        Address base = toAddr(0x10000L);
        println("POCKET_RE SEED language=" + currentProgram.getLanguageID() + " base=" + base);
        for (MemoryBlock b : currentProgram.getMemory().getBlocks()) {
            try { b.setRead(true); b.setExecute(true); } catch (Exception ignored) {}
        }

        // Section b begins with an NDS32 exception/vector jump table. Seed each
        // 4-byte vector slot so auto-analysis follows more than only reset.
        for (int off = 0; off <= 0xa0; off += 4) {
            Address a = base.add(off);
            try { currentProgram.getSymbolTable().addExternalEntryPoint(a); } catch (Exception ignored) {}
            try {
                boolean ok = disassemble(a);
                println("vector " + a + " disasm=" + ok);
                if (getFunctionAt(a) == null) {
                    try { createFunction(a, String.format("vector_%03x", off)); } catch (Exception ignored) {}
                }
            } catch (Exception e) { println("vector " + a + " note=" + e.getMessage()); }
        }

        Function f = getFunctionAt(base);
        println("base function=" + f);
    }
}
