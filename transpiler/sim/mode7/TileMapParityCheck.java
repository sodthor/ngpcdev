package sim.mode7;

import sim.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/** One-off check: compares SCROLL_PLANE_1 tilemap bytes at the first _SLEEP call
 *  between two ROMs (pre-fix vs post-fix), to confirm the u16TruncSafeValues
 *  hardware-mul change did not alter the tile data written during initLevel. */
public final class TileMapParityCheck {
    private static final int ROM_BASE = 0x200000;
    private static final int SCROLL_PLANE_1 = 0x9000;
    private static final int SCROLL_PLANE_BYTES = 32 * 32 * 2;

    public static void main(String[] args) throws Exception {
        byte[] a = dumpAtSleep(args[0], args[1]);
        byte[] b = dumpAtSleep(args[2], args[3]);
        if (a.length != b.length) {
            System.out.println("MISMATCH: different dump lengths");
            System.exit(1);
        }
        int diffs = 0;
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                diffs++;
                if (diffs <= 10) {
                    System.out.printf("diff at offset %04X: old=%02X new=%02X%n", i, a[i], b[i]);
                }
            }
        }
        System.out.println(diffs == 0 ? "PARITY OK: tilemap bytes identical" : "MISMATCH: " + diffs + " differing bytes");
        System.exit(diffs == 0 ? 0 : 1);
    }

    private static byte[] dumpAtSleep(String romPath, String mapPath) throws Exception {
        Map<String, Integer> symbols = loadMap(mapPath);
        int sleepPc = symbols.get("_SLEEP");

        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory memory = new Memory(rom.data);
        Registers registers = new Registers();
        Cpu cpu = new Cpu(memory, registers);
        memory.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int address) {
                if (address == 0x6F82) return 0;
                if (address == 0x6F87) return 1;
                return -1;
            }
            @Override public void write(int address, int value) {}
        });
        cpu.setSwiHandler((number, regs, mem) -> true);
        NgpLoader.init(rom, memory, registers);

        int romEnd = ROM_BASE + rom.data.length;
        long instr = 0;
        while (registers.getPC() != sleepPc) {
            cpu.step();
            instr++;
            int pc = registers.getPC();
            if (pc < ROM_BASE || pc >= romEnd) throw new IllegalStateException("left ROM at " + Integer.toHexString(pc));
            if (instr > 5_000_000) throw new IllegalStateException("never reached _SLEEP");
        }

        byte[] out = new byte[SCROLL_PLANE_BYTES];
        for (int i = 0; i < SCROLL_PLANE_BYTES; i++) out[i] = (byte) memory.readByte(SCROLL_PLANE_1 + i);
        return out;
    }

    private static Map<String, Integer> loadMap(String path) throws Exception {
        Map<String, Integer> symbols = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] f = line.trim().split("\\s+");
                if (f.length >= 3 && f[0].startsWith("_") && f[1].equals("Int")) {
                    try { symbols.put(f[0], Integer.parseInt(f[2], 16)); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return symbols;
    }
}
