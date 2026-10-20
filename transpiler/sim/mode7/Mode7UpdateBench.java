package sim.mode7;

import sim.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Measures cycles and instructions consumed by _update (the mode-7 renderer)
 * in both MODE7.ngp (LLVM transpiler) and MODE7_CC900.ngp (cc900 reference).
 *
 * Both ROMs call _update via `call _update` at address 0x24D9BE.
 * VBL interrupts are injected at 60 Hz throughout so that scan-line-polling
 * wait loops exit naturally.  After boot, joypad UP (0x01) is injected via
 * the IoDevice so that the main loop always calls _update on every iteration.
 *
 * Usage:
 *   java sim.mode7.Mode7UpdateBench [rom] [map] [--calls N] [--skip N]
 *
 *   --calls N  : number of _update calls to measure (default 5)
 *   --skip  N  : skip first N _update calls before measuring (default 0)
 */
public final class Mode7UpdateBench {

    private static final int  ROM_BASE        = 0x200000;
    private static final int  VBL_VECTOR_ADDR = 0x6FCC;
    private static final long CPU_HZ          = 6_144_000L;
    private static final int  VBL_HZ          = 60;
    private static final long CYCLES_PER_VBL  = NgpcTiming.CYCLES_PER_FRAME;

    private Mode7UpdateBench() {}

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
        String romPath   = "../mode7/MODE7.ngp";
        String mapPath   = "../mode7/MODE7.map";
        int    numCalls  = 5;
        int    skipCalls = 0;
        int    positional = 0;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--calls" -> numCalls  = Integer.parseInt(args[++i]);
                case "--skip"  -> skipCalls = Integer.parseInt(args[++i]);
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
        // Find all CALL/CALR sites that dispatch to updateAddr in the ROM bytes.
        int updateCallSite = findCallSite(rom.data, updateAddr);
        if (updateCallSite < 0)
            System.err.printf("WARN: no CALL/CALR to _UPDATE (0x%06X) found — boot uses direct PC match only%n", updateAddr);
        else
            System.out.printf("call site: 0x%06X -> 0x%06X%n", updateCallSite, updateAddr);

        // Load C9H division function addresses if they exist in the symbol table
        int divluAddr = -1, divlsAddr = -1, divuAddr = -1, divAddr = -1;
        try { divluAddr = symbol(symbols, "_C9H_DIVLU"); } catch (Exception ignored) {}
        try { divlsAddr = symbol(symbols, "_C9H_DIVLS"); } catch (Exception ignored) {}
        try { divuAddr = symbol(symbols, "_C9H_DIVU"); } catch (Exception ignored) {}
        try { divAddr = symbol(symbols, "_C9H_DIV"); } catch (Exception ignored) {}

        CallStats callStats = new CallStats();

        Memory    mem  = new Memory(rom.data);
        Registers reg  = new Registers();
        Cpu       cpu  = new Cpu(mem, reg);
        final int romEnd = ROM_BASE + rom.data.length;

        // postBoot[0]: flip to true once we've reached _update the first time.
        // After that the IoDevice returns J_UP so the main loop calls update() every frame.
        final long[]    instrRef = {0};
        final boolean[] postBoot = {false};
        mem.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int address) {
                // 0x6F82 = JOYPAD / scan-line register.  Bits 4/5/6 gate VBL in the main loop
                // (all zero → fall through).  Bits 0-3 are joypad buttons.
                // During boot: return 0 (no joypad, scan-line bits clear → exits poll loops).
                // After boot:  return 0x01 (J_UP) so b=1 and _update is called every iteration.
                if (address == 0x6F82) return postBoot[0] ? 0x01 : 0;
                if (address == 0x6F87) return 1;                           // language = English
                if (address == 0x00BC) return 0;                           // Z80 handshake
                if (address == 0x70DE) return (int)(instrRef[0] & 0xFF);   // driver heartbeat
                return -1;
            }
            @Override public void write(int address, int value) {}
        });
        cpu.setSwiHandler((n, r, m) -> true);
        NgpLoader.init(rom, mem, reg);

        long nextVbl    = CYCLES_PER_VBL;
        long instrCount = 0;
        long vblCount   = 0;

        // ── Phase 1: boot ────────────────────────────────────────────────────
        // Run with VBL injection until the CPU first reaches the _update entry.
        // For the CC900 ROM main() spends most of its init inside _update already
        // (it calls _update before the "steady-state" loop starts), so we just
        // watch for the CALL instruction at UPDATE_CALL_SITE — the step that fires
        // the CALL advances PC to updateAddr, which is where we start measuring.
        System.out.printf("Booting %s ...%n", baseName(romPath));
        long bootStartMs = System.currentTimeMillis();
        boolean booted = false;
        final long MAX_BOOT_INSTR = 500_000_000L;

        while (instrCount < MAX_BOOT_INSTR) {
            // VBL injection: if cycles have passed the next VBL threshold, fire the handler.
            if (cpu.getCycles() >= nextVbl) {
                nextVbl += CYCLES_PER_VBL;
                vblCount++;
                fireVbl(reg, mem, romEnd);
            }

            int pc = reg.getPC();
            if (pc == updateAddr) {
                // Direct branch / fall-through into _update (LLVM ROM first iteration)
                booted = true; break;
            }
            if (updateCallSite >= 0 && pc == updateCallSite) {
                // Execute the CALL/CALR instruction; PC becomes updateAddr
                cpu.step(); instrCount++; instrRef[0] = instrCount;
                if (reg.getPC() == updateAddr) { booted = true; break; }
                continue; // VBL check next iteration
            }

            recordFunctionCalls(pc, callStats, divluAddr, divlsAddr, divuAddr, divAddr);
            cpu.step(); instrCount++; instrRef[0] = instrCount;
            int newPc = reg.getPC();
            if (newPc < ROM_BASE || newPc >= romEnd) {
                // External call (BIOS / sound library): stub by returning immediately.
                int xsp = reg.getXSP();
                reg.setPC(mem.readDword(xsp) & 0xFFFFFF);
                reg.setXSP(xsp + 4);
            }
        }

        if (!booted) {
            System.err.printf("ERROR: _update not reached after %,d instructions.%n", instrCount);
            System.exit(1);
        }
        postBoot[0] = true;    // now inject joypad UP so main loop always calls _update
        System.out.printf("Boot done in %,d instr / %,d VBL frames (%d ms)%n",
            instrCount, vblCount, System.currentTimeMillis() - bootStartMs);

        // ── Phase 2: measure _update calls ───────────────────────────────────
        long   totalCyc   = 0;
        long   totalInstr = 0;
        long   minCyc     = Long.MAX_VALUE;
        long   maxCyc     = 0;
        long[] callCyc    = new long[skipCalls + numCalls];
        long[] callInstr  = new long[skipCalls + numCalls];
        int    callsDone  = 0;

        System.out.printf("Measuring %d _update call(s) (skipping first %d)...%n",
            numCalls, skipCalls);
        long measureStartMs = System.currentTimeMillis();

        while (callsDone < skipCalls + numCalls) {
            // Invariant: PC == updateAddr
            if (reg.getPC() != updateAddr) {
                System.err.printf("ERROR: expected PC=0x%06X, got 0x%06X (call %d)%n",
                    updateAddr, reg.getPC(), callsDone);
                System.exit(1);
            }
            long retAddr = mem.readDword(reg.getXSP()) & 0xFFFFFF;
            long i0 = instrCount;
            long c0 = cpu.getCycles();

            // Run _update to its return (injecting VBL mid-update as needed)
            while (reg.getPC() != retAddr) {
                if (cpu.getCycles() >= nextVbl) {
                    nextVbl += CYCLES_PER_VBL;
                    vblCount++;
                    fireVbl(reg, mem, romEnd);
                }
                recordFunctionCalls(reg.getPC(), callStats, divluAddr, divlsAddr, divuAddr, divAddr);
                cpu.step(); instrCount++; instrRef[0] = instrCount;
                int pc = reg.getPC();
                if (pc < ROM_BASE || pc >= romEnd) {
                    System.err.printf("ERROR: _update left ROM at PC=0x%06X%n", pc);
                    System.exit(1);
                }
            }

            long di = instrCount - i0;
            long dc = cpu.getCycles() - c0;
            callCyc[callsDone]   = dc;
            callInstr[callsDone] = di;
            callsDone++;

            boolean measure = (callsDone > skipCalls);
            if (measure) {
                totalCyc   += dc;
                totalInstr += di;
                if (dc < minCyc) minCyc = dc;
                if (dc > maxCyc) maxCyc = dc;
            }
            if (callsDone >= skipCalls + numCalls) break;

            // ── inter-call: run from return site until the next _update entry ──
            boolean nextFound = false;
            long advanceLimit = instrCount + 20_000_000L;
            while (instrCount < advanceLimit) {
                if (cpu.getCycles() >= nextVbl) {
                    nextVbl += CYCLES_PER_VBL;
                    vblCount++;
                    fireVbl(reg, mem, romEnd);
                }
                int pc = reg.getPC();
                if (pc == updateAddr) { nextFound = true; break; }
                if (updateCallSite >= 0 && pc == updateCallSite) {
                    cpu.step(); instrCount++; instrRef[0] = instrCount;
                    if (reg.getPC() == updateAddr) { nextFound = true; break; }
                    continue;
                }
                recordFunctionCalls(pc, callStats, divluAddr, divlsAddr, divuAddr, divAddr);
                cpu.step(); instrCount++; instrRef[0] = instrCount;
                int newPc = reg.getPC();
                if (newPc < ROM_BASE || newPc >= romEnd) {
                    int xsp = reg.getXSP();
                    reg.setPC(mem.readDword(xsp) & 0xFFFFFF);
                    reg.setXSP(xsp + 4);
                }
            }
            if (!nextFound) {
                System.err.printf("ERROR: no next _update call after call %d (20M instr window)%n",
                    callsDone);
                System.exit(1);
            }
        }

        long elapsedMs = System.currentTimeMillis() - measureStartMs;

        System.out.println();
        System.out.printf("=== Mode7 _update Benchmark: %s ===%n", baseName(romPath));
        System.out.printf("  calls measured  : %d%n", numCalls);
        System.out.printf("  cycles/call     : mean %,12d   min %,12d   max %,12d%n",
            totalCyc / numCalls, minCyc, maxCyc);
        System.out.printf("  instr/call      : mean %,12d%n", totalInstr / numCalls);
        System.out.printf("  frames/call     : %.3f  (at %d Hz / %.3f MHz)%n",
            (double)(totalCyc / numCalls) / CYCLES_PER_VBL, VBL_HZ, CPU_HZ / 1e6);
        System.out.printf("  sim wall time   : %,d ms for %d calls%n", elapsedMs, numCalls);

        if (numCalls > 1) {
            System.out.printf("  per-call cycles :");
            for (int i = skipCalls; i < skipCalls + numCalls; i++) {
                System.out.printf("  %,d", callCyc[i]);
            }
            System.out.println();
        }

        callStats.printStats();
    }

    private static void recordFunctionCalls(int pc, CallStats stats, int divluAddr,
                                           int divlsAddr, int divuAddr, int divAddr) {
        if (divluAddr >= 0 && pc == divluAddr) stats.recordCall("_C9H_DIVLU");
        if (divlsAddr >= 0 && pc == divlsAddr) stats.recordCall("_C9H_DIVLS");
        if (divuAddr >= 0 && pc == divuAddr) stats.recordCall("_C9H_DIVU");
        if (divAddr >= 0 && pc == divAddr) stats.recordCall("_C9H_DIV");
    }

    /**
     * Scan ROM for the last CALL (0x1D) or CALR (0x1E) instruction whose target
     * equals updateAddr.  Returns the instruction's ROM address, or -1 if none found.
     * Prefers the last occurrence (closest to the main loop in memory order).
     */
    private static int findCallSite(byte[] romData, int updateAddr) {
        int found = -1;
        for (int i = 0; i < romData.length - 4; i++) {
            int b = romData[i] & 0xFF;
            if (b == 0x1D) {                        // CALL d24 (absolute)
                int tgt = (romData[i+1] & 0xFF)
                        | ((romData[i+2] & 0xFF) << 8)
                        | ((romData[i+3] & 0xFF) << 16);
                if (tgt == updateAddr) found = i + ROM_BASE;
            } else if (b == 0x1E) {                 // CALR d16 (PC-relative, signed)
                int rel = (short)((romData[i+1] & 0xFF) | ((romData[i+2] & 0xFF) << 8));
                int tgt = (ROM_BASE + i + 3 + rel) & 0xFFFFFF;
                if (tgt == updateAddr) found = i + ROM_BASE;
            }
        }
        return found;
    }

    private static void fireVbl(Registers reg, Memory mem, int romEnd) {
        int handler = mem.readDword(VBL_VECTOR_ADDR) & 0xFFFFFF;
        if (handler == 0 || handler < ROM_BASE || handler >= romEnd) return;
        int xsp = reg.getXSP();
        xsp -= 2; mem.writeWord(xsp, reg.getSR());
        xsp -= 4; mem.writeDword(xsp, reg.getPC());
        mem.trackStackAccess(xsp);
        reg.setXSP(xsp);
        reg.setPC(handler);
    }

    private static String baseName(String p) {
        int i = Math.max(p.lastIndexOf('/'), p.lastIndexOf('\\'));
        return i >= 0 ? p.substring(i + 1) : p;
    }

    private static Map<String, Integer> loadMap(String path) throws Exception {
        Map<String, Integer> symbols = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String t = line.trim();
                // ASL format: "_SYMBOL  Int  HEXADDR  ..."
                String[] parts = t.split("\\s+");
                if (parts.length >= 3 && parts[0].startsWith("_") && parts[1].equals("Int")) {
                    try { symbols.put(parts[0].toUpperCase(), Integer.parseInt(parts[2], 16)); }
                    catch (NumberFormatException ignored) {}
                }
                // Toshiba CC900 linker format: "  _symbol  HEXADDR  section  ..."
                else if (parts.length >= 2 && parts[0].startsWith("_")) {
                    try {
                        int addr = Integer.parseInt(parts[1], 16);
                        symbols.put(parts[0].toUpperCase(), addr);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return symbols;
    }

    private static int symbol(Map<String, Integer> symbols, String name) {
        Integer address = symbols.get(name);
        if (address == null) throw new IllegalStateException("symbol not found: " + name);
        return address;
    }
}
