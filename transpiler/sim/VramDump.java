import sim.*;
import java.io.*;
import java.util.*;

public class VramDump {
    static final int ROM_BASE = 0x200000;
    static final int VBL_VECTOR = 0x6FCC;
    static final long CYCLES_PER_VBL = NgpcTiming.CYCLES_PER_FRAME;

    public static void main(String[] args) throws Exception {
        String romPath = args[0];
        String mapPath = args[1];
        String outPath = args[2];

        // load map (both formats)
        Map<String,Integer> sym = new HashMap<>();
        try (BufferedReader r = new BufferedReader(new FileReader(mapPath))) {
            String line;
            while ((line=r.readLine())!=null) {
                String[] p = line.trim().split("\s+");
                if (p.length>=3 && p[0].startsWith("_") && p[1].equals("Int"))
                    try { sym.put(p[0].toUpperCase(), Integer.parseInt(p[2],16)); } catch(Exception e){}
                else if (p.length>=2 && p[0].startsWith("_"))
                    try { sym.put(p[0].toUpperCase(), Integer.parseInt(p[1],16)); } catch(Exception e){}
            }
        }
        int updateAddr = sym.get("_UPDATE");

        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory mem = new Memory(rom.data);
        Registers reg = new Registers();
        Cpu cpu = new Cpu(mem, reg);
        int romEnd = ROM_BASE + rom.data.length;
        final long[] ic = {0};
        mem.setIoDevice(new Memory.IoDevice() {
            public int read(int a) {
                if (a==0x6F82) return 0;
                if (a==0x6F87) return 1;
                if (a==0x00BC) return 0;
                if (a==0x70DE) return (int)(ic[0]&0xFF);
                return -1;
            }
            public void write(int a,int v){}
        });
        cpu.setSwiHandler((n,r,m)->true);
        NgpLoader.init(rom, mem, reg);

        long nextVbl = CYCLES_PER_VBL;
        // boot to _update
        while (ic[0] < 500_000_000L && reg.getPC() != updateAddr) {
            if (cpu.getCycles() >= nextVbl) { nextVbl+=CYCLES_PER_VBL; fireVbl(reg,mem,romEnd); }
            cpu.step(); ic[0]++;
            int pc=reg.getPC();
            if (pc<ROM_BASE||pc>=romEnd) { reg.setPC(mem.readDword(reg.getXSP())&0xFFFFFF); reg.setXSP(reg.getXSP()+4); }
        }
        if (reg.getPC()!=updateAddr) { System.err.println("boot failed"); System.exit(1); }
        int retAddr = mem.readDword(reg.getXSP())&0xFFFFFF;
        // run one _update
        while (reg.getPC() != retAddr) {
            if (cpu.getCycles() >= nextVbl) { nextVbl+=CYCLES_PER_VBL; fireVbl(reg,mem,romEnd); }
            cpu.step(); ic[0]++;
        }
        // dump tile RAM 0xA000-0xC000
        try (FileOutputStream fos = new FileOutputStream(outPath)) {
            for (int a=0xA000;a<0xC000;a++) fos.write(mem.readByte(a)&0xFF);
        }
        System.out.println("Dumped VRAM to "+outPath+" after "+ic[0]+" instr");
    }
    static void fireVbl(Registers reg, Memory mem, int romEnd) {
        int h=mem.readDword(VBL_VECTOR)&0xFFFFFF;
        if (h==0||h<ROM_BASE||h>=romEnd) return;
        int xsp=reg.getXSP(); xsp-=2; mem.writeWord(xsp,reg.getSR()); xsp-=4; mem.writeDword(xsp,reg.getPC());
        reg.setXSP(xsp); reg.setPC(h);
    }
}
