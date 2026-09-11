package sim;

/** Run the full boot with VBL, then dump the track-tile region of TILE_RAM. */
public final class FullRunDump {
    public static void main(String[] args) throws Exception {
        String romPath = args.length > 0 ? args[0] : "../mode7/MODE7.ngp";
        long maxInstr = args.length > 1 ? Long.parseLong(args[1]) : 3_000_000L;
        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory mem = new Memory(rom.data);
        Registers reg = new Registers();
        Cpu cpu = new Cpu(mem, reg);
        final long[] instr = {0};
        mem.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int a) {
                if (a == 0x6F82) return 0;
                if (a == 0x6F87) return 1;
                if (a == 0x00BC) return 0;
                if (a == 0x70DE) return (int)(instr[0] & 0xFF);
                return -1;
            }
            @Override public void write(int a, int v) {}
        });
        cpu.setSwiHandler((n, r, m) -> true);
        NgpLoader.init(rom, mem, reg);

        final int ROM_BASE = 0x200000, ROM_END = ROM_BASE + rom.data.length;
        final long cyclesPerVbl = 6_144_000L / 60;
        long nextVbl = cyclesPerVbl, prevCyc = 0, cyc = 0, vbl = 0;
        while (instr[0] < maxInstr) {
            if (cyc >= nextVbl) {
                nextVbl += cyclesPerVbl;
                int h = mem.readDword(0x6FCC) & 0xFFFFFF;
                if (h != 0) {
                    int xsp = reg.getXSP();
                    xsp -= 2; mem.writeWord(xsp, reg.getSR());
                    xsp -= 4; mem.writeDword(xsp, reg.getPC());
                    reg.setXSP(xsp); reg.setPC(h);
                }
                vbl++;
            }
            cpu.step();
            instr[0]++;
            long nc = cpu.getCycles(); cyc += nc - prevCyc; prevCyc = nc;
            int pc = reg.getPC();
            if (pc < ROM_BASE || pc >= ROM_END) {
                System.out.printf("LEFT ROM at instr %d PC=%06X%n", instr[0], pc);
                break;
            }
        }
        System.out.printf("Ran %d instr, %d VBL. teta=%d skyX=%d worldX=%d worldY=%d SCR2_X=%d%n",
            instr[0], vbl,
            mem.readWord(0x4004)&0xFFFF, mem.readWord(0x400E)&0xFFFF,
            mem.readDword(0x4000), mem.readDword(0x4006),
            mem.readByte(0x8034)&0xFF);

        // Dump track plane-1 tile words: for y=1..8, x=0..15
        int TILE = 0xA000;
        int nonzero = 0, total = 0;
        for (int a = TILE; a < TILE + 0x1000; a += 2) { total++; if ((mem.readWord(a)&0xFFFF)!=0) nonzero++; }
        System.out.printf("TILE_RAM 0x%04X..: %d/%d nonzero words%n", TILE, nonzero, total);
        for (int y = 1; y <= 4; y++) {
            int ta = TILE + ((((y>>3)<<7)+(y&7)+8)*2);
            System.out.printf("y=%d:", y);
            for (int x = 0; x < 8; x++, ta += 16)
                System.out.printf(" %04X", mem.readWord(ta)&0xFFFF);
            System.out.println();
        }
    }
}
