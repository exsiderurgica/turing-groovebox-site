// Focused headless report: trace the final initialized-data block copied from
// section b into GP-relative RAM, and find exactly which fields live code uses.
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
import java.util.regex.*;

public class Dump extends GhidraScript {
    private PrintWriter out;
    private final Set<Long> dumped = new LinkedHashSet<>();
    private static final long SRC0=0x16aec0L, SRC1=0x16bb60L;
    private static final long GP0=-0xc140L, GP1=-0xb4a0L;
    private static final Pattern NEGHEX=Pattern.compile("-0x([0-9a-fA-F]+)");
    private void p(String s){ out.println(s); println(s); }
    private long sourceFor(long off){ return SRC0 + (off-GP0); }
    private String raw(long a,int n) throws Exception {
        byte[] b=new byte[n]; currentProgram.getMemory().getBytes(toAddr(a),b);
        StringBuilder sb=new StringBuilder(); for(byte x:b) sb.append(String.format("%02x",x&255)); return sb.toString();
    }
    private void dumpFn(Function f,String why) throws Exception {
        if(f==null || !dumped.add(f.getEntryPoint().getOffset())) return;
        p("\n--- FUNCTION "+f.getName()+" @ "+f.getEntryPoint()+" reason="+why+" ---");
        InstructionIterator ii=currentProgram.getListing().getInstructions(f.getBody(),true); int c=0;
        while(ii.hasNext()&&c++<260){ Instruction i=ii.next(); p("ASM "+i.getAddress()+"  "+i); }
        try{
            DecompInterface d=new DecompInterface(); d.openProgram(currentProgram);
            DecompileResults r=d.decompileFunction(f,60,monitor);
            if(r!=null&&r.decompileCompleted()&&r.getDecompiledFunction()!=null){ p("C-BEGIN");p(r.getDecompiledFunction().getC());p("C-END"); }
            else p("C-FAIL "+(r==null?"null":r.getErrorMessage())); d.dispose();
        }catch(Exception e){p("C-EXCEPTION "+e);}
    }
    private void target(String name,long src){
        long off=GP0+(src-SRC0); p(String.format("MAP %-24s src=0x%06x gp=%s0x%x",name,src,off<0?"-":"+",Math.abs(off)));
    }
    @Override public void run() throws Exception {
        String[] a=getScriptArgs(); out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(a.length>0?a[0]:"pocket_ui_report.txt"),StandardCharsets.UTF_8));
        try{
            p("Pocket Master relocated UI/data trace v3");
            p("language="+currentProgram.getLanguageID()+" functions="+currentProgram.getFunctionManager().getFunctionCount());
            p(String.format("INIT COPY: flash [0x%x,0x%x) -> GP [%s0x%x,%s0x%x), len=0x%x",SRC0,SRC1,GP0<0?"-":"+",Math.abs(GP0),GP1<0?"-":"+",Math.abs(GP1),SRC1-SRC0));
            target("English language block",0x16b1e0L);
            target("inactive card table",0x16b328L);
            target("selected card table",0x16b34cL);
            target("label image table",0x16b370L);
            target("candidate RGB table",0x16b398L);

            // Prove the copy initializer and retain its decompilation.
            dumpFn(getFunctionAt(toAddr(0x626acL)),"initialized-data-copy");

            Map<Long,List<Instruction>> uses=new TreeMap<>();
            InstructionIterator it=currentProgram.getListing().getInstructions(true);
            int allgp=0;
            while(it.hasNext()){
                Instruction ins=it.next(); long pc=ins.getAddress().getOffset(); if(pc>=0xe185cL) continue;
                String s=ins.toString(); String lo=s.toLowerCase();
                if(!lo.contains(".gp") && !lo.contains(" gp") && !lo.contains("$gp")) continue;
                allgp++;
                Matcher m=NEGHEX.matcher(lo); boolean matched=false;
                while(m.find()){
                    long off=-Long.parseLong(m.group(1),16);
                    if(off>=GP0 && off<GP1){ uses.computeIfAbsent(off,k->new ArrayList<>()).add(ins); matched=true; }
                }
                // addi.gp may hide its immediate in the textual renderer: preserve refs/operands.
                if(lo.startsWith("addi.gp") || lo.startsWith("lwi.gp") || lo.startsWith("lhi.gp") || lo.startsWith("lbi.gp") || lo.startsWith("swi.gp") || lo.startsWith("shi.gp") || lo.startsWith("sbi.gp")){
                    StringBuilder extra=new StringBuilder();
                    for(Reference r:ins.getReferencesFrom()) extra.append(" ref=").append(r.getToAddress()).append('/').append(r.getReferenceType());
                    if(!matched) p("GP-OTHER "+ins.getAddress()+" "+s+extra);
                }
            }
            p("\n=== RELOCATED INITIALIZED-DATA GP USES ===");
            p("all GP-ish instructions="+allgp+" unique relocated offsets="+uses.size());
            for(Map.Entry<Long,List<Instruction>> e:uses.entrySet()){
                long off=e.getKey(),src=sourceFor(off);
                String bytes=""; try{bytes=raw(src,Math.min(24,(int)(SRC1-src)));}catch(Exception x){bytes="ERR";}
                p(String.format("FIELD gp=-0x%x src=0x%x raw=%s uses=%d",Math.abs(off),src,bytes,e.getValue().size()));
                for(Instruction ins:e.getValue()){
                    Function f=getFunctionContaining(ins.getAddress());
                    p(" USE "+ins.getAddress()+" func="+(f==null?"-":f.getName()+"@"+f.getEntryPoint())+"  "+ins);
                    if(src>=0x16b000L || (off>=-0xbd20L && off<=-0xbbc0L)) dumpFn(f,"relocated-ui-field@"+Long.toHexString(src));
                }
            }

            // Exact ranges we care about for the one definitive graphical test.
            long[][] ranges={{0x16b1e0L,0x16b328L},{0x16b328L,0x16b34cL},{0x16b34cL,0x16b370L},{0x16b370L,0x16b394L},{0x16b398L,0x16b3e0L}};
            String[] names={"LANGUAGE","INACTIVE","SELECTED","LABELS","RGB"};
            p("\n=== RANGE ACCESS SUMMARY ===");
            for(int r=0;r<ranges.length;r++){
                int n=0; Set<Long> fs=new LinkedHashSet<>();
                for(Map.Entry<Long,List<Instruction>> e:uses.entrySet()){
                    long src=sourceFor(e.getKey()); if(src>=ranges[r][0]&&src<ranges[r][1]){
                        n+=e.getValue().size(); for(Instruction ins:e.getValue()){Function f=getFunctionContaining(ins.getAddress());if(f!=null)fs.add(f.getEntryPoint().getOffset());}
                    }
                }
                p(String.format("RANGE %s [0x%x,0x%x) accesses=%d funcs=%s",names[r],ranges[r][0],ranges[r][1],n,fs));
            }
        } finally { out.close(); }
    }
}
