package sim.mode7;

import sim.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mode 7 MOTION benchmark: measures _update cost while the camera is moving.
 *
 * The existing oracles (Mode7GraphicsTest / CycleBench) never press the joypad, so
 * update() runs exactly once in the stationary startup state. But update() cost is
 * data-dependent on (teta, worldX, worldY): the inner PIXEL branch takes different
 * paths per camera position. This harness drives a joypad motion script (VBL at 60 Hz),
 * measures instructions+cycles for EVERY _update call, and reports mean/min/max/total.
 *
 * Usage:
 *   java sim.mode7.Mode7MotionBench [rom] [map] [--script m:f,...] [--verify] [--warmup n] [--max n]
 *
 * Joypad masks (from ngpc.h): UP=0x01 DOWN=0x02 LEFT=0x04 RIGHT=0x08.
 * UP/DOWN move worldX/worldY; LEFT/RIGHT rotate teta. Any of them re-triggers update().
 */
public final class Mode7MotionBench {
    private static final int ROM_BASE = 0x200000;
    private static final int TILE_RAM = 0xA000;
    private static final int VBL_VECTOR_ADDR = 0x6FCC;
    private static final long CPU_HZ = 6_144_000L;
    private static final int  VBL_HZ = 60;

    private Mode7MotionBench() {}

    private static class CallStats {
        final Map<String, Integer> callCounts = new TreeMap<>();

        void recordCall(String funcName) {
            callCounts.put(funcName, callCounts.getOrDefault(funcName, 0) + 1);
        }

        void printStats() {
            if (callCounts.isEmpty()) return;
            System.out.println();
            System.out.println("  Function call counts:");
            for (Map.Entry<String, Integer> e : callCounts.entrySet()) {
                System.out.printf("    %-20s : %,d%n", e.getKey(), e.getValue());
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String romPath = "../mode7/MODE7.ngp";
        String mapPath = "../mode7/MODE7.map";
        // Default racing script: drive straight, then turn-while-moving both ways, then straight.
        String scriptSpec = "01:240,05:240,09:240,01:240";
        boolean verify = false;
        long warmup = 0;                 // input allowed as soon as _update is first reached
        long maxInstrs = 200_000_000L;   // safety ceiling
        int positional = 0;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--script" -> scriptSpec = args[++i];
                case "--verify" -> verify = true;
                case "--warmup" -> warmup = Long.parseLong(args[++i]);
                case "--max"    -> maxInstrs = Long.parseLong(args[++i]);
                default -> {
                    if (positional == 0) romPath = args[i];
                    else if (positional == 1) mapPath = args[i];
                    positional++;
                }
            }
        }

        Map<String, Integer> symbols = loadMap(mapPath);
        int updateAddr = symbol(symbols, "_UPDATE");

        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory memory = new Memory(rom.data);
        Registers registers = new Registers();
        Cpu cpu = new Cpu(memory, registers);
        final int romEnd = ROM_BASE + rom.data.length;

        // Load C9H division function addresses if they exist in the symbol table
        int divluAddr = -1, divlsAddr = -1, divuAddr = -1, divAddr = -1;
        try { divluAddr = symbol(symbols, "_C9H_DIVLU"); } catch (Exception ignored) {}
        try { divlsAddr = symbol(symbols, "_C9H_DIVLS"); } catch (Exception ignored) {}
        try { divuAddr = symbol(symbols, "_C9H_DIVU"); } catch (Exception ignored) {}
        try { divAddr = symbol(symbols, "_C9H_DIV"); } catch (Exception ignored) {}

        CallStats callStats = new CallStats();

        // Build per-VBL-frame joypad schedule.
        final int[] scriptMasks = parseScript(scriptSpec);
        final long[] instr = {0};
        final int[]  frame = {0};
        final long   warmupF = warmup;
        memory.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int address) {
                if (address == 0x6F82) {
                    if (instr[0] < warmupF) return 0;
                    int f = frame[0];
                    return (f < scriptMasks.length) ? scriptMasks[f] : 0;
                }
                if (address == 0x6F87) return 1;
                if (address == 0x00BC) return 0;
                if (address == 0x70DE) return (int) (instr[0] & 0xFF);
                return -1;
            }
            @Override public void write(int address, int value) {}
        });
        cpu.setSwiHandler((n, r, m) -> true);
        NgpLoader.init(rom, memory, registers);

        // Phase 1: run main until it first reaches _update (startup complete, in main loop).
        while (instr[0] < maxInstrs && registers.getPC() != updateAddr) {
            cpu.step();
            instr[0]++;
            int pc = registers.getPC();
            require(pc >= ROM_BASE && pc < romEnd,
                String.format("startup left ROM at PC=0x%06X after %,d instr", pc, instr[0]));
        }
        require(registers.getPC() == updateAddr, "main did not reach _update");

        // Phase 2: measure every _update call while driving the joypad, VBL at 60 Hz.
        final long cyclesPerVbl = NgpcTiming.CYCLES_PER_FRAME;
        long nextVbl = cyclesPerVbl;
        long vblCount = 0;
        long calls = 0, sumInstr = 0, sumCyc = 0;
        long minCyc = Long.MAX_VALUE, maxCyc = 0, firstCyc = -1;
        long verifyPass = 0, verifyFail = 0;
        long startTime = System.currentTimeMillis();

        // The script ends when we've consumed all frames after warmup; stop shortly after.
        int totalFrames = scriptMasks.length;

        while (instr[0] < maxInstrs) {
            // Fire VBL if due, advancing the script frame.
            if (cpu.getCycles() >= nextVbl) {
                nextVbl += cyclesPerVbl;
                vblCount++;
                fireVbl(registers, memory);
                if (instr[0] >= warmupF && frame[0] < totalFrames) frame[0]++;
                if (frame[0] >= totalFrames) break; // script exhausted → done
            }

            if (registers.getPC() == updateAddr) {
                // Measure this _update call: from entry to its return address.
                long ret = memory.readDword(registers.getXSP()) & 0xFFFFFF;
                long i0 = instr[0], c0 = cpu.getCycles();
                while (instr[0] < maxInstrs && registers.getPC() != ret) {
                    recordFunctionCalls(registers.getPC(), callStats, divluAddr, divlsAddr, divuAddr, divAddr);
                    cpu.step();
                    instr[0]++;
                    int pc = registers.getPC();
                    require(pc >= ROM_BASE && pc < romEnd,
                        String.format("update left ROM at PC=0x%06X after %,d instr (call %d)",
                            pc, instr[0], calls + 1));
                    // VBL can fire mid-update; keep the clock coherent.
                    if (cpu.getCycles() >= nextVbl) {
                        nextVbl += cyclesPerVbl;
                        vblCount++;
                        fireVbl(registers, memory);
                        if (instr[0] >= warmupF && frame[0] < totalFrames) frame[0]++;
                    }
                }
                long di = instr[0] - i0, dc = cpu.getCycles() - c0;
                calls++; sumInstr += di; sumCyc += dc;
                if (dc < minCyc) minCyc = dc;
                if (dc > maxCyc) maxCyc = dc;
                if (firstCyc < 0) firstCyc = dc;

                if (verify) {
                    if (verifyTrackTiles(memory, symbols)) verifyPass++;
                    else verifyFail++;
                }
                continue;
            }

            recordFunctionCalls(registers.getPC(), callStats, divluAddr, divlsAddr, divuAddr, divAddr);
            cpu.step();
            instr[0]++;
            int pc = registers.getPC();
            require(pc >= ROM_BASE && pc < romEnd,
                String.format("loop left ROM at PC=0x%06X after %,d instr", pc, instr[0]));
        }

        long elapsed = System.currentTimeMillis() - startTime;
        int wx = memory.readDword(symbol(symbols, "_WORLDX"));
        int wy = memory.readDword(symbol(symbols, "_WORLDY"));
        int teta = memory.readWord(symbol(symbols, "_TETA")) & 0xFFFF;

        System.out.println("=== Mode7 Motion Benchmark ===");
        System.out.printf("script            : %s  (%d frames)%n", scriptSpec, totalFrames);
        System.out.printf("total instr run   : %,d in %d ms (%.0f KIPS)%n",
            instr[0], elapsed, elapsed > 0 ? instr[0] / (double) elapsed : instr[0]);
        System.out.printf("VBL frames fired  : %,d (%.2f sim seconds)%n",
            vblCount, (double) vblCount / VBL_HZ);
        System.out.printf("_update calls     : %,d%n", calls);
        if (calls > 0) {
            System.out.printf("  cycles/call     : mean %,d  min %,d  max %,d  (first/static %,d)%n",
                sumCyc / calls, minCyc, maxCyc, firstCyc);
            System.out.printf("  instr/call      : mean %,d%n", sumInstr / calls);
            System.out.printf("  total cycles    : %,d  (= mean x calls, the per-motion load)%n", sumCyc);
        }
        if (verify) {
            System.out.printf("  verify          : %d PASS / %d FAIL%s%n",
                verifyPass, verifyFail, verifyFail == 0 ? "  (motion codegen correct)" : "  <<< MISMATCH");
        }
        System.out.printf("final camera      : worldX=%d worldY=%d teta=%d%n", wx, wy, teta);
        if (verify && verifyFail > 0) System.exit(1);

        callStats.printStats();
    }

    private static void recordFunctionCalls(int pc, CallStats stats, int divluAddr,
                                           int divlsAddr, int divuAddr, int divAddr) {
        if (divluAddr >= 0 && pc == divluAddr) stats.recordCall("_C9H_DIVLU");
        if (divlsAddr >= 0 && pc == divlsAddr) stats.recordCall("_C9H_DIVLS");
        if (divuAddr >= 0 && pc == divuAddr) stats.recordCall("_C9H_DIVU");
        if (divAddr >= 0 && pc == divAddr) stats.recordCall("_C9H_DIV");
    }

    // --- joypad script: "mask:frames,mask:frames,..." (hex mask, decimal frames) ---
    private static int[] parseScript(String spec) {
        List<Integer> sched = new ArrayList<>();
        for (String seg : spec.split(",")) {
            String[] kv = seg.trim().split(":");
            int mask = Integer.parseInt(kv[0].trim(), 16);
            int frames = Integer.parseInt(kv[1].trim());
            for (int f = 0; f < frames; f++) sched.add(mask);
        }
        int[] out = new int[sched.size()];
        for (int k = 0; k < out.length; k++) out[k] = sched.get(k);
        return out;
    }

    // --- VBL: push SR then PC, jump to handler at vector 0x6FCC (RETI restores) ---
    private static void fireVbl(Registers reg, Memory mem) {
        int handler = mem.readDword(VBL_VECTOR_ADDR) & 0xFFFFFF;
        if (handler == 0) return;
        int xsp = reg.getXSP();
        xsp -= 2; mem.writeWord(xsp, reg.getSR());
        xsp -= 4; mem.writeDword(xsp, reg.getPC());
        mem.trackStackAccess(xsp);
        reg.setXSP(xsp);
        reg.setPC(handler);
    }

    // --- reference model: recompute expected tile RAM from live camera state ---
    // Mirrors update() in main.c; ported from Mode7GraphicsTest.verifyTrackTiles.
    private static boolean verifyTrackTiles(Memory memory, Map<String, Integer> symbols) {
        int worldX = memory.readDword(symbol(symbols, "_WORLDX"));
        int worldY = memory.readDword(symbol(symbols, "_WORLDY"));
        int angle  = memory.readWord(symbol(symbols, "_TETA"));
        int track  = memory.readDword(symbol(symbols, "_TRACK"));
        int cosine = symbol(symbols, "_TCOS");
        int sine   = symbol(symbols, "_TSIN");

        int far1X = worldX + (trig(memory, cosine, angle - 64) * 31 >> 7);
        int near1X = worldX + (trig(memory, cosine, angle - 64) * 5 >> 7);
        int far1Y = worldY + (trig(memory, sine, angle - 64) * 31 >> 7);
        int near1Y = worldY + (trig(memory, sine, angle - 64) * 5 >> 7);
        int far2X = worldX + (trig(memory, cosine, angle + 64) * 31 >> 7);
        int near2X = worldX + (trig(memory, cosine, angle + 64) * 5 >> 7);
        int far2Y = worldY + (trig(memory, sine, angle + 64) * 31 >> 7);
        int near2Y = worldY + (trig(memory, sine, angle + 64) * 5 >> 7);
        int fn1X = (far1X - near1X) << 6;
        int fn1Y = (far1Y - near1Y) << 6;
        int fn2X = (far2X - near2X) << 6;
        int fn2Y = (far2Y - near2Y) << 6;

        for (int y = 1; y < 64; y++) {
            int startX = fn1X / y + near1X;
            int startY = fn1Y / y + near1Y;
            int deltaX = fn2X / y + near2X - startX;
            int deltaY = fn2Y / y + near2Y - startY;
            int tileAddress = TILE_RAM + ((((y >> 3) << 7) + (y & 7) + 8) * 2);
            int sampleX = startX << 7;
            int sampleY = startY << 7;
            for (int x = 0; x < 16; x++, tileAddress += 16) {
                int tile0 = 0, tile1 = 0;
                for (int pixel = 0; pixel < 8; pixel++) {
                    if ((sampleX & 0xFFFE0000) != 0 || (sampleY & 0xFFFE0000) != 0) {
                        tile0 |= 3;
                    } else {
                        int color = memory.readByte(track
                            + (sampleX >> 8) + ((sampleY & 0x0001FF00) << 1));
                        if ((color & 4) != 0) tile1 |= color - 3;
                        else tile0 |= color;
                    }
                    sampleX += deltaX;
                    sampleY += deltaY;
                    if (pixel != 7) { tile0 <<= 2; tile1 <<= 2; }
                }
                if (memory.readWord(tileAddress) != (tile0 & 0xFFFF)) return false;
                if (memory.readWord(tileAddress + 2048) != (tile1 & 0xFFFF)) return false;
            }
        }
        return true;
    }

    private static int trig(Memory memory, int table, int angle) {
        return (short) memory.readWord(table + ((angle & 511) * 2));
    }

    private static Map<String, Integer> loadMap(String path) throws Exception {
        Map<String, Integer> symbols = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3 && parts[0].startsWith("_") && parts[1].equals("Int")) {
                    // transpiler asl format: _NAME  Int  HEXADDR
                    symbols.put(parts[0], Integer.parseInt(parts[2], 16));
                } else if (parts.length >= 2 && parts[0].startsWith("_")
                        && parts[1].matches("[0-9A-Fa-f]{4,8}")) {
                    // cc900 tulink format: _name  HEXADDR  [section]
                    symbols.put(parts[0], Integer.parseInt(parts[1], 16));
                }
            }
        }
        return symbols;
    }

    private static int symbol(Map<String, Integer> symbols, String name) {
        Integer address = symbols.get(name);
        if (address == null) address = symbols.get(name.toLowerCase());
        if (address == null) address = symbols.get(name.toUpperCase());
        if (address == null) {
            // case-insensitive fallback for mixed-case cc900 symbols (_worldX etc.)
            String lower = name.toLowerCase();
            for (Map.Entry<String, Integer> e : symbols.entrySet())
                if (e.getKey().toLowerCase().equals(lower)) { address = e.getValue(); break; }
        }
        if (address == null) throw new IllegalStateException("symbol not found: " + name);
        return address;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
