// Headless evidence report for Pocket Master section b.
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
            if (f != null && from.getOffset() < 0xe185cL) dumpFunction(f, "xref-" + label);
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
                if (pf != null && rr.getFromAddress().getOffset() < 0xe185cL) dumpFunction(pf, "ptr-xref-"+label);
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
        while(ii.hasNext() && c<220) {
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

    private void huntCodeRefs() throws Exception {
        p("\n=== CODE REFERENCES INTO HIGH UI/DATA AREA ===");
        InstructionIterator ii=currentProgram.getListing().getInstructions(true);
        int n=0;
        while(ii.hasNext()) {
            Instruction ins=ii.next();
            long from=ins.getAddress().getOffset();
            if(from >= 0xe185cL) continue; // resource/data archive, not executable application code
            for(Reference r: ins.getReferencesFrom()) {
                long to=r.getToAddress().getOffset();
                if(to>=0xd0000L && to<=0x16bb5fL) {
                    Function f=getFunctionContaining(ins.getAddress());
                    p("UIREF from="+ins.getAddress()+" to="+r.getToAddress()+" type="+r.getReferenceType()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint())+" ins="+ins);
                    if(to>=0x16a000L && f!=null) dumpFunction(f,"high-ui-data-ref");
                    if(++n>=1500) { p("UIREF truncated at 1500"); return; }
                }
            }
        }
        p("UIREF total="+n);
    }

    private void huntGpAndImmediates() throws Exception {
        p("\n=== GP / HIGH IMMEDIATE HUNT ===");
        InstructionIterator ii=currentProgram.getListing().getInstructions(true);
        int gp=0, hi=0;
        while(ii.hasNext()) {
            Instruction ins=ii.next();
            long from=ins.getAddress().getOffset();
            if(from >= 0xe185cL) continue;
            String txt=ins.toString();
            String low=txt.toLowerCase();
            if((low.contains("$gp") || low.contains(" gp") || low.contains("gp,")) && gp<1200) {
                Function f=getFunctionContaining(ins.getAddress());
                p("GP "+ins.getAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint())+"  "+txt);
                gp++;
            }
            for(int op=0; op<ins.getNumOperands(); op++) {
                Scalar s=ins.getScalar(op);
                if(s==null) continue;
                long v=s.getUnsignedValue();
                // Useful constants for direct/hi-part loads of UI addresses and RGB values.
                if((v>=0x16a0L && v<=0x16c0L) || (v>=0x16a000L && v<=0x16bb5fL) ||
                   v==0xfc9000L || v==0x3c70ffL || v==0xff3636L || v==0xff6c37L ||
                   v==0xffc53fL || v==0x2cff56L || v==0x43fddeL || v==0x1abdffL || v==0xac2fffL) {
                    Function f=getFunctionContaining(ins.getAddress());
                    p("IMM "+ins.getAddress()+" v=0x"+Long.toHexString(v)+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint())+"  "+txt);
                    if(f!=null && hi<120) { dumpFunction(f,"ui-immediate"); hi++; }
                }
            }
        }
        p("GP printed="+gp+" immediate_functions="+hi);
    }

    @Override
    public void run() throws Exception {
        String[] args=getScriptArgs();
        File f=new File(args.length>0?args[0]:"pocket_ui_report.txt");
        out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8));
        try {
            p("Pocket Master UI reverse-engineering evidence report v2");
            p("language="+currentProgram.getLanguageID()+" compiler="+currentProgram.getCompilerSpec().getCompilerSpecID());
            p("imageBase="+currentProgram.getImageBase());
            for(MemoryBlock mb: currentProgram.getMemory().getBlocks()) p("BLOCK "+mb.getName()+" "+mb.getStart()+".."+mb.getEnd()+" size="+mb.getSize()+" x="+mb.isExecute()+" r="+mb.isRead()+" w="+mb.isWrite());
            long funcs=currentProgram.getFunctionManager().getFunctionCount();
            long ins=0; InstructionIterator all=currentProgram.getListing().getInstructions(true); while(all.hasNext()){all.next();ins++;}
            p("function_count="+funcs+" instruction_count="+ins);

            String[] strings={"GUI_MAIN_TASK","../UserSources/Components/WinManager/WinManager.c","English","MOVE","VOL","Effects","Settings","Position","Preset VOL"};
            for(String s:strings){
                List<Address> a=findBytes(s.getBytes(StandardCharsets.US_ASCII));
                p("STRING "+s+" hits="+a);
                for(Address x:a) refsTo("str:"+s,x);
            }

            refsTo("English-language-block", addr(0x16b1e0L));
            refsTo("category-inactive-table", addr(0x16b328L));
            refsTo("category-selected-table", addr(0x16b34cL));
            refsTo("category-label-table", addr(0x16b370L));
            refsTo("candidate-rgb888-table", addr(0x16b398L));

            long[] inactive={0x102a74L,0xff3c0L,0xfcf48L,0xfaad0L,0x101838L,0xfe184L,0x1005fcL,0xfbd0cL,0x103cb0L};
            long[] selected={0x10ce90L,0x1097dcL,0x107364L,0x104eecL,0x10bc54L,0x1085a0L,0x10aa18L,0x106128L,0x10e0ccL};
            long[] labels={0x114e28L,0x114b30L,0x1148e8L,0x114658L,0x114d78L,0x114a28L,0x114c38L,0x1147a8L,0x114f30L};
            for(int i=0;i<inactive.length;i++) refsTo("inactive-desc-"+i,addr(inactive[i]));
            for(int i=0;i<selected.length;i++) refsTo("selected-desc-"+i,addr(selected[i]));
            for(int i=0;i<labels.length;i++) refsTo("label-desc-"+i,addr(labels[i]));

            huntCodeRefs();
            huntGpAndImmediates();

            p("\n=== FIRST FUNCTIONS ===");
            FunctionIterator fi=currentProgram.getFunctionManager().getFunctions(true); int q=0;
            while(fi.hasNext() && q<80){ Function fn=fi.next(); p("FUNC "+fn.getEntryPoint()+" "+fn.getName()+" body="+fn.getBody()); q++; }
        } finally { out.close(); }
    }
}
