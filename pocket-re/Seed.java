// Ghidra headless seed for Pocket Master section b.
// The HTFW b-section uses data pointers = section offset + 0x10000,
// therefore the raw section is imported at 0x10000.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.mem.MemoryBlock;

public class Seed extends GhidraScript {
    private void seed(long addr, String name) {
        Address a = toAddr(addr);
        try { currentProgram.getSymbolTable().addExternalEntryPoint(a); } catch (Exception ignored) {}
        try {
            boolean ok = disassemble(a);
            println("seed " + name + " " + a + " disasm=" + ok);
            if (getFunctionAt(a) == null) {
                try { createFunction(a, name); } catch (Exception ignored) {}
            }
        } catch (Exception e) { println("seed " + name + " note=" + e.getMessage()); }
    }
    @Override
    public void run() throws Exception {
        Address base = toAddr(0x10000L);
        println("POCKET_RE SEED language=" + currentProgram.getLanguageID() + " base=" + base);
        for (MemoryBlock b : currentProgram.getMemory().getBlocks()) {
            try { b.setRead(true); b.setExecute(true); } catch (Exception ignored) {}
        }
        for (int off = 0; off <= 0xa0; off += 4) seed(0x10000L + off, String.format("vector_%03x", off));
        // Auto-analysis stops in the middle of the application bootstrap because of
        // mixed-width NDS32 flow. Seed the validated continuation bytes explicitly.
        seed(0x5fb18L, "app_bootstrap");
        seed(0x5fb78L, "app_bootstrap_tail");
        seed(0x5fcc2L, "app_bootstrap_error_tail");
        Function f = getFunctionAt(base);
        println("base function=" + f);
    }
}
