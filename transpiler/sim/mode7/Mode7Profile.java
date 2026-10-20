package sim.mode7;

import sim.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** One-shot PC profiler for a single _update call. Prints hottest PCs. */
public final class Mode7Profile {
    private static final int ROM_BASE = 0x200000;
    public static void main(String[] args) throws Exception {
        String romPath = args.length > 0 ? args[0] : "../mode7/MODE7.ngp";
        String mapPath = args.length > 1 ? args[1] : "../mode7/MODE7.map";
        Map<String,Integer> sym = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(mapPath))) {
            String l; while ((l=r.readLine())!=null){String[] p=l.trim().split("\\s+");
                if(p.length>=3&&p[0].startsWith("_")&&p[1].equals("Int")) sym.put(p[0],Integer.parseInt(p[2],16));}
        }
        int updateAddr = sym.get("_UPDATE");
        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory mem = new Memory(rom.data); Registers reg = new Registers(); Cpu cpu = new Cpu(mem, reg);
        final long[] instr={0};
        mem.setIoDevice(new Memory.IoDevice(){
            public int read(int a){ if(a==0x6F82)return 0; if(a==0x6F87)return 1; if(a==0x00BC)return 0;
                if(a==0x70DE)return (int)(instr[0]&0xFF); return -1;}
            public void write(int a,int v){}});
        cpu.setSwiHandler((n,rr,m)->true);
        NgpLoader.init(rom, mem, reg);
        while (reg.getPC()!=updateAddr){ cpu.step(); instr[0]++; }
        long ret = mem.readDword(reg.getXSP())&0xFFFFFF;
        Map<Integer,Long> hist = new HashMap<>();
        long steps0=0;
        while (reg.getPC()!=ret){ int pc=reg.getPC(); hist.merge(pc,1L,Long::sum); cpu.step(); steps0++; }
        final long steps=steps0;
        System.out.printf("profiled 1 _update call: %,d instructions, %d distinct PCs%n", steps, hist.size());
        // Top 40 hottest PCs, as offset from _update.
        hist.entrySet().stream()
            .sorted((a,b)->Long.compare(b.getValue(),a.getValue()))
            .limit(40)
            .forEach(e-> System.out.printf("  PC=0x%06X (upd+0x%04X)  %,10d  %.1f%%%n",
                e.getKey(), e.getKey()-updateAddr, e.getValue(), 100.0*e.getValue()/steps));
        // Also bucket by 16-byte region to see hot clusters.
        TreeMap<Integer,Long> buckets = new TreeMap<>();
        for (var e: hist.entrySet()) buckets.merge((e.getKey()-updateAddr)&~0xF, e.getValue(), Long::sum);
        System.out.println("--- hot 16-byte buckets (offset from _update) ---");
        buckets.entrySet().stream().sorted((a,b)->Long.compare(b.getValue(),a.getValue())).limit(20)
            .forEach(e-> System.out.printf("  upd+0x%04X  %,10d  %.1f%%%n", e.getKey(), e.getValue(), 100.0*e.getValue()/steps));
    }
}
