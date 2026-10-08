// Headless evidence report for Pocket Master section b.
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import ghidra.program.model.symbol.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class Dump extends GhidraScript {
    private PrintWriter out;
    private final Set<Long> decompiledEntries = new LinkedHashSet<>();

    private void p(String s) { out.println(s); println(s); }

    private List<Address> findBytes(byte[] needle) throws Exception {
        List<Address> result = new ArrayList<>();
        Memory mem = currentProgram.getMemory();
        for (MemoryBlock block : mem.getBlocks()) {
            if (!block.isInitialized() || block.getSize() > Integer.MAX_VALUE) continue;
            byte[] hay = new byte[(int)block.getSize()];
            int got = mem.getBytes(block.getStart(), hay);
            if (got <= 0) continue;
            int lim = got - needle.length;
            for (int i = 0; i <= lim; i++) {
                boolean yes = true;
                for (int j = 0; j < needle.length; j++) {
                    if (hay[i+j] != needle[j]) { yes = false; break; }
                }
                if (yes) result.add(block.getStart().add(i));
            }
        }
        return result;
    }

    private byte[] le32(long v) {
        return new byte[] {(byte)v,(byte)(v>>8),(byte)(v>>16),(byte)(v>>24)};
    }

    private void refsTo(String label, Address target) throws Exception {
        p("\n=== TARGET " + label + " @ " + target + " ===");
        ReferenceIterator it = currentProgram.getReferenceManager().getReferencesTo(target);
        int n = 0;
        while (it.hasNext()) {
            Reference r = it.next(); n++;
            Address from = r.getFromAddress();
            Function f = getFunctionContaining(from);
            p("REF " + from + " type=" + r.getReferenceType() + " func=" + (f==null?"-":f.getName()+"@"+f.getEntryPoint()));
            if (f != null) dumpFunction(f, "xref-" + label);
        }
        p("xref_count=" + n);

        List<Address> rawPtrs = findBytes(le32(target.getOffset()));
        p("raw_le32_pointer_count=" + rawPtrs.size());
        for (int i=0; i<Math.min(rawPtrs.size(), 80); i++) {
            Address a = rawPtrs.get(i);
            Function f = getFunctionContaining(a);
            p("PTR " + a + " func=" + (f==null?"-":f.getName()+"@"+f.getEntryPoint()));
            ReferenceIterator pit = currentProgram.getReferenceManager().getReferencesTo(a);
            int k=0;
            while (pit.hasNext() && k<20) {
                Reference rr=pit.next(); k++;
                Function pf=getFunctionContaining(rr.getFromAddress());
                p("  PTR-XREF " + rr.getFromAddress() + " type=" + rr.getReferenceType() + " func=" + (pf==null?"-":pf.getName()+"@"+pf.getEntryPoint()));
                if (pf != null) dumpFunction(pf, "ptr-xref-"+label);
            }
        }
    }

    private void dumpFunction(Function f, String why) throws Exception {
        long ep = f.getEntryPoint().getOffset();
        if (!decompiledEntries.add(ep)) return;
        p("\n--- FUNCTION " + f.getName() + " @ " + f.getEntryPoint() + " reason=" + why + " ---");
        Listing listing=currentProgram.getListing();
        InstructionIterator ii=listing.getInstructions(f.getBody(), true);
        int c=0;
        while(ii.hasNext() && c<180) {
            Instruction ins=ii.next();
            p("ASM " + ins.getAddress() + "  " + ins.toString());
            c++;
        }
        try {
            DecompInterface di=new DecompInterface();
            di.toggleCCode(true); di.toggleSyntaxTree(true); di.openProgram(currentProgram);
            DecompileResults dr=di.decompileFunction(f, 60, monitor);
            if (dr != null && dr.decompileCompleted() && dr.getDecompiledFunction()!=null) {
                p("C-BEGIN"); p(dr.getDecompiledFunction().getC()); p("C-END");
            } else {
                p("C-DECOMPILE-FAILED " + (dr==null?"null":dr.getErrorMessage()));
            }
            di.dispose();
        } catch(Exception e) { p("C-EXCEPTION " + e); }
    }

    private Address addr(long x){ return toAddr(x); }

    @Override
    public void run() throws Exception {
        String[] args=getScriptArgs();
        File f=new File(args.length>0?args[0]:"pocket_ui_report.txt");
        out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
        try {
            p("Pocket Master UI reverse-engineering evidence report");
            p("language="+currentProgram.getLanguageID()+" compiler="+currentProgram.getCompilerSpec().getCompilerSpecID());
            p("imageBase="+currentProgram.getImageBase());
            for(MemoryBlock b: currentProgram.getMemory().getBlocks()) p("BLOCK "+b.getName()+" "+b.getStart()+".."+b.getEnd()+" size="+b.getSize()+" x="+b.isExecute()+" r="+b.isRead()+" w="+b.isWrite());
            long funcs=currentProgram.getFunctionManager().getFunctionCount();
            long ins=0; InstructionIterator all=currentProgram.getListing().getInstructions(true); while(all.hasNext()){all.next();ins++;}
            p("function_count="+funcs+" instruction_count="+ins);

            String[] strings={"GUI_MAIN_TASK","../UserSources/Components/WinManager/WinManager.c","English","MOVE","VOL","Effects","Settings","Position","Preset VOL"};
            for(String s:strings){
                List<Address> a=findBytes(s.getBytes(StandardCharsets.US_ASCII));
                p("STRING "+s+" hits="+a);
                for(Address x:a) refsTo("str:"+s,x);
            }

            // Known stable UI structures from V1.3.3 static mapping, runtime base = b offset + 0x10000.
            refsTo("English-language-block", addr(0x16b1e0L));
            refsTo("category-inactive-table", addr(0x16b328L));
            refsTo("category-selected-table", addr(0x16b34cL));
            refsTo("category-label-table", addr(0x16b370L));
            refsTo("candidate-rgb888-table", addr(0x16b398L));

            // LVGL image descriptors which visually decode to the stock category cards.
            long[] cards={0x0faadcL,0x0fbd18L,0x0fcf54L,0x0fe190L,0x0ff3ccL,0x100608L,0x101844L,0x102a80L,0x103cbcL,
                          0x104ef8L,0x106134L,0x107370L,0x1085acL,0x1097e8L,0x10aa24L,0x10bc60L,0x10ce9cL,0x10e0d8L};
            for(int i=0;i<cards.length;i++) refsTo("card-payload-"+i, addr(cards[i]));

            p("\n=== FIRST FUNCTIONS ===");
            FunctionIterator fi=currentProgram.getFunctionManager().getFunctions(true); int q=0;
            while(fi.hasNext() && q<150){ Function fn=fi.next(); p("FUNC "+fn.getEntryPoint()+" "+fn.getName()+" body="+fn.getBody()); q++; }
        } finally { out.close(); }
    }
}
