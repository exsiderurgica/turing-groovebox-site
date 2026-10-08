// Ghidra headless seed for Pocket Master section b.
// The HTFW b-section uses data pointers = section offset + 0x10000,
// therefore the raw section is imported at 0x10000.
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;

public class Seed extends GhidraScript {
    @Override
    public void run() throws Exception {
        Address base = toAddr(0x10000L);
        println("POCKET_RE SEED language=" + currentProgram.getLanguageID() + " base=" + base);
        try {
            currentProgram.getSymbolTable().addExternalEntryPoint(base);
        } catch (Exception e) {
            println("entry-point note: " + e.getMessage());
        }
        boolean ok = disassemble(base);
        println("disassemble(base)=" + ok);
        Function f = getFunctionAt(base);
        if (f == null) {
            try {
                f = createFunction(base, "pocket_b_entry");
                println("created function at " + base + ": " + f);
            } catch (Exception e) {
                println("createFunction(base) note: " + e.getMessage());
            }
        }
    }
}
