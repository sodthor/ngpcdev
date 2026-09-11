package sim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Deterministic work-sensitive cycle benchmark for a built NGPC ROM.
 *
 * Two metrics, both independent of idle VBL-wait spinning (so they reflect how
 * much CPU work the generated code actually does, not the fixed frame budget):
 *
 *   1. boot_instrs / boot_cycles: work from reset until PC first reaches the
 *      main-loop entry symbol (default _UPDATE, override with --stop <sym>).
 *
 *   2. isr_cycles / isr_instrs: cycles+instructions spent *inside* the VBL
 *      interrupt handler across N frames (default 600). Idle waiting excluded.
 *
 * Lower numbers are faster. Equal-or-lower on all metrics for all ROMs is the
 * gate for any "no regression" performance change.
 *
 * Usage:
 *   java sim.CycleBench <rom.ngp> [<rom.map>] [frames] [--stop <symbol>]
 */
public final class CycleBench {

    private static final int  VBL_VECTOR_ADDR = 0x6FCC;
    private static final long CPU_HZ          = 6_144_000L;
    private static final int  VBL_HZ          = 60;
    private static final int  ROM_BASE        = 0x200000;

    private CycleBench() {}

    public static void main(String[] args) throws Exception {
        String romPath = null, mapPath = null, stopSym = "_UPDATE";
        int frames = 600;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--stop")) { stopSym = args[++i]; }
            else if (romPath == null)     { romPath = args[i]; }
            else if (args[i].endsWith(".map")) { mapPath = args[i]; }
            else { frames = Integer.parseInt(args[i]); }
        }
        if (romPath == null) {
            System.err.println("Usage: java sim.CycleBench <rom.ngp> [<rom.map>] [frames] [--stop <symbol>]");
            System.exit(2);
        }

        int stopPc = -1;
        if (mapPath != null) {
            Integer s = loadMap(mapPath).get(stopSym);
            if (s != null) stopPc = s;
        }

        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory memory = new Memory(rom.data);
        Registers registers = new Registers();
        Cpu cpu = new Cpu(memory, registers);

        final long[] instr = {0};
        memory.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int address) {
                if (address == 0x6F82) return 0;      // rSL
                if (address == 0x6F87) return 1;       // rLANG (English)
                if (address == 0x00BC) return 0;       // joypad: nothing pressed
                if (address == 0x70DE) return (int) (instr[0] & 0xFF);
                return -1;
            }
            @Override public void write(int address, int value) {}
        });
        cpu.setSwiHandler((number, regs, mem) -> true);
        NgpLoader.init(rom, memory, registers);

        final int  romEnd       = ROM_BASE + rom.data.length;
        final long cyclesPerVbl = CPU_HZ / VBL_HZ;
        final long maxInstr     = 200_000_000L;

        // Phase 1: boot cost (reset -> stop symbol).
        long bootInstr = 0, bootCyc = 0, prevCyc = 0;
        boolean reachedStop = false;
        if (stopPc >= 0) {
            while (bootInstr < maxInstr) {
                if (registers.getPC() == stopPc) { reachedStop = true; break; }
                cpu.step();
                bootInstr++; instr[0] = bootInstr;
                long nc = cpu.getCycles(); bootCyc += nc - prevCyc; prevCyc = nc;
                int pc = registers.getPC();
                if (pc < ROM_BASE || pc >= romEnd) {
                    System.out.printf("BENCH ABORTED (boot): left ROM at instr %d PC=%06X%n", bootInstr, pc);
                    System.exit(1);
                }
            }
        }
        runPhase2(cpu, registers, memory, instr, romEnd, cyclesPerVbl, maxInstr,
                  frames, prevCyc, bootInstr, bootCyc, reachedStop, baseName(romPath));
    }


    // Phase 2: per-frame ISR work over `frames` VBLs. Enter the handler on each
    // VBL; count cycles/instrs until RETI returns (XSP rises back to pre-IRQ).
    private static void runPhase2(Cpu cpu, Registers registers, Memory memory,
            long[] instr, int romEnd, long cyclesPerVbl, long maxInstr,
            int frames, long prevCyc, long bootInstr, long bootCyc,
            boolean reachedStop, String romName) {
        long isrCyc = 0, isrInstr = 0;
        long cyc = prevCyc, nextVbl = ((cyc / cyclesPerVbl) + 1) * cyclesPerVbl;
        long vbl = 0, totalInstr = bootInstr;
        boolean inIsr = false;
        int isrReturnXsp = 0;
        while (vbl < frames && totalInstr < maxInstr) {
            if (!inIsr && cyc >= nextVbl) {
                nextVbl += cyclesPerVbl;
                int handler = memory.readDword(VBL_VECTOR_ADDR) & 0xFFFFFF;
                if (handler != 0) {
                    isrReturnXsp = registers.getXSP();
                    int xsp = registers.getXSP();
                    xsp -= 2; memory.writeWord(xsp, registers.getSR());
                    xsp -= 4; memory.writeDword(xsp, registers.getPC());
                    registers.setXSP(xsp);
                    registers.setPC(handler);
                    inIsr = true;
                }
                vbl++;
            }
            cpu.step();
            totalInstr++; instr[0] = totalInstr;
            long nc = cpu.getCycles(); long d = nc - prevCyc; prevCyc = nc; cyc += d;
            if (inIsr) {
                isrCyc += d; isrInstr++;
                if (registers.getXSP() >= isrReturnXsp) inIsr = false; // RETI popped frame
            }
            int pc = registers.getPC();
            if (pc < ROM_BASE || pc >= romEnd) {
                System.out.printf("BENCH ABORTED (run): left ROM at instr %d PC=%06X frame %d%n",
                    totalInstr, pc, vbl);
                System.exit(1);
            }
        }
        System.out.printf(
            "BENCH rom=%s boot_reached=%s boot_instrs=%d boot_cycles=%d "
          + "frames=%d isr_instrs=%d isr_cycles=%d isr_cyc_per_frame=%d%n",
            romName, reachedStop, bootInstr, bootCyc,
            vbl, isrInstr, isrCyc, vbl > 0 ? isrCyc / vbl : 0);
    }

    private static String baseName(String p) {
        int i = p.lastIndexOf('/');
        return i >= 0 ? p.substring(i + 1) : p;
    }

    private static Map<String, Integer> loadMap(String path) throws Exception {
        Map<String, Integer> symbols = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.trim().split("\\s+");
                if (f.length >= 3 && f[0].startsWith("_") && f[1].equals("Int")) {
                    try { symbols.put(f[0], Integer.parseInt(f[2], 16)); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return symbols;
    }
}
