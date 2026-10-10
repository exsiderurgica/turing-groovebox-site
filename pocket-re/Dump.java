// Focused headless report for Pocket Master main/UI firmware (HTFW region b).
// Goal: locate GUI_MAIN_TASK creation and reconstruct the application/UI entry path.
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
    private final Set<Long> dumped = new LinkedHashSet<>();
    private final Set<Long> candidateFns = new LinkedHashSet<>();

    private void p(String s){ out.println(s); println(s); }

    private void dumpFunction(Function f, String why) throws Exception {
        if (f == null || !dumped.add(f.getEntryPoint().getOffset())) return;
        p("\n--- FUNCTION " + f.getName() + " @ " + f.getEntryPoint() + " reason=" + why + " ---");
        InstructionIterator ii = currentProgram.getListing().getInstructions(f.getBody(), true);
        int c = 0;
        while (ii.hasNext() && c++ < 320) {
            Instruction i = ii.next();
            p("ASM " + i.getAddress() + "  " + i);
        }
        try {
            DecompInterface d = new DecompInterface();
            d.openProgram(currentProgram);
            DecompileResults r = d.decompileFunction(f, 90, monitor);
            if (r != null && r.decompileCompleted() && r.getDecompiledFunction() != null) {
                p("C-BEGIN"); p(r.getDecompiledFunction().getC()); p("C-END");
            } else p("C-FAIL " + (r == null ? "null" : r.getErrorMessage()));
            d.dispose();
        } catch (Exception e) { p("C-EXCEPTION " + e); }
    }

    private Address findAscii(String text) throws Exception {
        byte[] pat = text.getBytes(StandardCharsets.US_ASCII);
        Memory mem = currentProgram.getMemory();
        Address a = mem.findBytes(mem.getMinAddress(), pat, null, true, monitor);
        p("STRING \"" + text + "\" -> " + a);
        return a;
    }

    private void refsAndPointers(String label, Address target) throws Exception {
        if (target == null) return;
        ReferenceManager rm = currentProgram.getReferenceManager();
        int refs = 0;
        ReferenceIterator it = rm.getReferencesTo(target);
        while (it.hasNext()) {
            Reference r = it.next(); refs++;
            Address from = r.getFromAddress();
            Function f = getFunctionContaining(from);
            p("REF " + label + " from=" + from + " type=" + r.getReferenceType() + " func=" + (f==null?"-":f.getName()+"@"+f.getEntryPoint()));
            if (f != null && from.getOffset() < 0xe185cL) { candidateFns.add(f.getEntryPoint().getOffset()); dumpFunction(f, "xref:"+label); }
        }
        p("REFCOUNT " + label + "=" + refs);

        long v = target.getOffset();
        byte[] le = new byte[]{(byte)v,(byte)(v>>8),(byte)(v>>16),(byte)(v>>24)};
        Memory mem = currentProgram.getMemory();
        Address cur = mem.getMinAddress();
        int ptrs = 0;
        while (cur != null && ptrs < 64) {
            Address hit = mem.findBytes(cur, le, null, true, monitor);
            if (hit == null) break;
            ptrs++;
            p("PTR " + label + " @ " + hit + " value=" + target);
            ReferenceIterator pit = rm.getReferencesTo(hit);
            while (pit.hasNext()) {
                Reference pr = pit.next();
                Function pf = getFunctionContaining(pr.getFromAddress());
                p(" PTRREF from=" + pr.getFromAddress() + " type=" + pr.getReferenceType() + " func=" + (pf==null?"-":pf.getName()+"@"+pf.getEntryPoint()));
                if (pf != null && pr.getFromAddress().getOffset() < 0xe185cL) { candidateFns.add(pf.getEntryPoint().getOffset()); dumpFunction(pf, "ptr-xref:"+label); }
            }
            try { cur = hit.add(1); } catch (Exception e) { break; }
        }
        p("PTRCOUNT " + label + "=" + ptrs);
    }

    private boolean scalarEquals(Instruction ins, long value) {
        for (int op=0; op<ins.getNumOperands(); op++) {
            Scalar s = ins.getScalar(op);
            if (s != null && s.getUnsignedValue() == value) return true;
        }
        return false;
    }

    private void huntAddressConstruction(String label, Address target) throws Exception {
        if (target == null) return;
        long a = target.getOffset();
        long hi = a >>> 12;
        long lo = a & 0xfffL;
        p(String.format("ADDRHUNT %s target=0x%x sethi=0x%x low=0x%x", label, a, hi, lo));
        Listing listing = currentProgram.getListing();
        InstructionIterator ii = listing.getInstructions(true);
        int n=0;
        while (ii.hasNext()) {
            Instruction ins = ii.next();
            if (ins.getAddress().getOffset() >= 0xe185cL) continue;
            if (!ins.getMnemonicString().equalsIgnoreCase("sethi")) continue;
            if (!scalarEquals(ins, hi)) continue;
            Function f = getFunctionContaining(ins.getAddress());
            Instruction q = ins;
            boolean foundLow = false;
            StringBuilder seq = new StringBuilder();
            for (int k=0; k<10; k++) {
                if (q == null) break;
                seq.append(q.getAddress()).append(":").append(q).append(" | ");
                if (k > 0 && (q.getMnemonicString().equalsIgnoreCase("ori") || q.getMnemonicString().toLowerCase().contains("addi")) && scalarEquals(q, lo)) { foundLow=true; break; }
                q = listing.getInstructionAfter(q.getAddress());
            }
            p("SETHI-CAND " + label + " @ " + ins.getAddress() + " func=" + (f==null?"-":f.getName()+"@"+f.getEntryPoint()) + " lowMatch=" + foundLow + " seq=" + seq);
            if (foundLow && f != null) { n++; candidateFns.add(f.getEntryPoint().getOffset()); dumpFunction(f, "constructed-address:"+label); }
        }
        p("ADDRHUNT_MATCHES " + label + "=" + n);
    }

    private void callersOfCandidates() throws Exception {
        p("\n=== CALLERS OF GUI CANDIDATE FUNCTIONS ===");
        ReferenceManager rm = currentProgram.getReferenceManager();
        List<Long> snapshot = new ArrayList<>(candidateFns);
        for (long ep : snapshot) {
            Address a = toAddr(ep);
            ReferenceIterator ri = rm.getReferencesTo(a);
            int n=0;
            while (ri.hasNext()) {
                Reference r=ri.next();
                if (!r.getReferenceType().isCall()) continue;
                n++;
                Function cf=getFunctionContaining(r.getFromAddress());
                p("CALLER target="+a+" from="+r.getFromAddress()+" func="+(cf==null?"-":cf.getName()+"@"+cf.getEntryPoint()));
                if (cf!=null && cf.getEntryPoint().getOffset()<0xe185cL) dumpFunction(cf,"caller-of-gui-candidate");
            }
            p("CALLERCOUNT target="+a+"="+n);
        }
    }

    @Override public void run() throws Exception {
        String[] args=getScriptArgs();
        out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(args.length>0?args[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
        try {
            p("Pocket Master GUI task trace v4");
            p("language="+currentProgram.getLanguageID()+" imageBase="+currentProgram.getImageBase()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
            Address gui=findAscii("GUI_MAIN_TASK");
            Address screen=findAscii("Screen Light");
            Address midi=findAscii("Pocket Master MIDI");
            Address sdk=findAscii("MVsilicon B1 SDK");
            Address build=findAscii("8.7.1 build @ Apr 29 2025 16:04:02");

            refsAndPointers("GUI_MAIN_TASK",gui);
            huntAddressConstruction("GUI_MAIN_TASK",gui);
            refsAndPointers("Screen Light",screen);
            huntAddressConstruction("Screen Light",screen);
            refsAndPointers("Pocket Master MIDI",midi);
            refsAndPointers("MVsilicon B1 SDK",sdk);
            refsAndPointers("build-string",build);
            callersOfCandidates();

            p("\n=== VECTOR / BOOT ENTRY SNAPSHOT ===");
            for (int off=0; off<=0xa0; off+=4) {
                Address a=toAddr(0x10000L+off);
                Instruction ins=currentProgram.getListing().getInstructionAt(a);
                p("VECTOR "+a+"  "+(ins==null?"-":ins.toString()));
            }
        } finally { out.close(); }
    }
}
