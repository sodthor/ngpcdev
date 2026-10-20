package sim.mode7;

import sim.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/** Mode 7 startup and first-frame graphics regression harness. */
public final class Mode7GraphicsTest {
    private static final int ROM_BASE = 0x200000;
    private static final int TRACK_MAP_1 = 0x9000;
    private static final int TRACK_MAP_2 = 0x9800;
    private static final int TILE_RAM = 0xA000;
    private static final int SKY_TILE = 257;
    private static final int SKY_TILE_WORDS = 182 * 8;
    private static final int SKY_MAP_WORDS = 32 * 8;
    private static final int RETURN_SENTINEL = ROM_BASE;

    private Mode7GraphicsTest() {}

    public static void main(String[] args) throws Exception {
        String romPath = args.length > 0 ? args[0] : "../mode7/MODE7.ngp";
        String mapPath = args.length > 1 ? args[1] : "../mode7/MODE7.map";
        Map<String, Integer> symbols = loadMap(mapPath);

        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory memory = new Memory(rom.data);
        Registers registers = new Registers();
        Cpu cpu = new Cpu(memory, registers);
        final long[] instructions = {0};
        memory.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int address) {
                if (address == 0x6F82) return 0;
                if (address == 0x6F87) return 1;
                if (address == 0x00BC) return 0;
                if (address == 0x70DE) return (int) instructions[0] & 0xFF;
                return -1;
            }

            @Override public void write(int address, int value) {}
        });
        cpu.setSwiHandler((number, regs, mem) -> true);
        NgpLoader.init(rom, memory, registers);

        int romEnd = ROM_BASE + rom.data.length;
        int updateAddress = symbol(symbols, "_UPDATE");
        int soundInitAddress = symbol(symbols, "_SL_SOUNDINIT");
        int playBgmAddress = symbol(symbols, "_SL_PLAYBGM");
        int externalCalls = 0;
        try {
            while (instructions[0] < 2_000_000 && registers.getPC() != updateAddress) {
                if (registers.getPC() == soundInitAddress || registers.getPC() == playBgmAddress) {
                    int returnAddress = memory.readDword(registers.getXSP()) & 0xFFFFFF;
                    registers.setXSP(registers.getXSP() + 4);
                    registers.setPC(returnAddress);
                    externalCalls++;
                    continue;
                }
                int previousPc = registers.getPC();
                cpu.step();
                instructions[0]++;
                int pc = registers.getPC();
                if (pc < ROM_BASE || pc >= romEnd) {
                    int returnAddress = memory.readDword(registers.getXSP()) & 0xFFFFFF;
                    require(returnAddress >= ROM_BASE && returnAddress < romEnd,
                        String.format("external call at 0x%06X from 0x%06X has invalid return 0x%06X",
                            pc, previousPc, returnAddress));
                    registers.setXSP(registers.getXSP() + 4);
                    registers.setPC(returnAddress);
                    externalCalls++;
                }
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("RAM exhausted")) {
                System.out.printf("Mode7GraphicsTest: FAILED (boot collision after %,d instructions)%n", instructions[0]);
                System.out.println(memory.getRamReport());
                throw new AssertionError("RAM exhaustion during boot: " + e.getMessage(), e);
            }
            throw e;
        }
        require(registers.getPC() == updateAddress, "main did not reach update");

        verifyStartupGraphics(memory, symbols);
        byte[] skyBefore = snapshot(memory, TILE_RAM + SKY_TILE * 16, SKY_TILE_WORDS * 2);
        int updateReturn = memory.readDword(registers.getXSP()) & 0xFFFFFF;

        long updateInstructions = 0;
        long updateStartCycles = cpu.getCycles();
        try {
            while (updateInstructions < 2_000_000 && registers.getPC() != updateReturn) {
                int previousPc = registers.getPC();
                cpu.step();
                updateInstructions++;
                int pc = registers.getPC();
                require(pc >= ROM_BASE && pc < romEnd,
                    String.format("update left ROM at PC=0x%06X from 0x%06X with SP=0x%06X after %,d instructions",
                        pc, previousPc, registers.getXSP(), updateInstructions));
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("RAM exhausted")) {
                System.out.printf("Mode7GraphicsTest: FAILED (update collision after %,d instructions)%n", updateInstructions);
                System.out.println(memory.getRamReport());
                throw new AssertionError("RAM exhaustion during update: " + e.getMessage(), e);
            }
            throw e;
        }
        require(registers.getPC() == updateReturn,
            String.format("update did not return to main: PC=0x%06X SP=0x%06X after %,d instructions",
                registers.getPC(), registers.getXSP(), updateInstructions));

        int nonzeroTrackWords = 0;
        for (int address = TILE_RAM; address < TILE_RAM + 0x1000; address += 2) {
            if (memory.readWord(address) != 0) nonzeroTrackWords++;
        }
        require(nonzeroTrackWords > 100, "update did not generate track tile data");
        verifyTrackTiles(memory, symbols);
        require(java.util.Arrays.equals(skyBefore,
            snapshot(memory, TILE_RAM + SKY_TILE * 16, SKY_TILE_WORDS * 2)),
            "update corrupted sky tile data");

        long updateCycles = cpu.getCycles() - updateStartCycles;
        System.out.printf("Mode7GraphicsTest: PASS (boot %,d instructions, %d BIOS calls, "
                + "update %,d instructions / %,d cycles, %d track words)%n",
            instructions[0], externalCalls, updateInstructions, updateCycles, nonzeroTrackWords);
        System.out.println(memory.getRamReport());
    }

    private static void verifyStartupGraphics(Memory memory, Map<String, Integer> symbols) {
        for (int index = 0; index < 128; index++) {
            int mapOffset = ((index / 16 + 8) * 32 + index % 16) * 2;
            require(memory.readWord(TRACK_MAP_1 + mapOffset) == index + 1,
                "incorrect scroll-plane 1 track entry " + index);
            require(memory.readWord(TRACK_MAP_2 + mapOffset) == index + 129,
                "incorrect scroll-plane 2 track entry " + index);
        }

        int skySource = symbol(symbols, "_SKY_TILES");
        int skyDestination = TILE_RAM + SKY_TILE * 16;
        for (int index = 0; index < SKY_TILE_WORDS; index++) {
            require(memory.readWord(skyDestination + index * 2) == memory.readWord(skySource + index * 2),
                "incorrect sky tile word " + index);
        }

        int skyIndexes = symbol(symbols, "_SKY_IDX1");
        int skyFlips = symbol(symbols, "_SKY_FLIP1");
        int skyPalettes = symbol(symbols, "_SKY_PALIDX1");
        for (int index = 0; index < SKY_MAP_WORDS; index++) {
            int expected = SKY_TILE + memory.readWord(skyIndexes + index * 2)
                + memory.readWord(skyFlips + index * 2)
                + ((memory.readByte(skyPalettes + index) + 1) << 9);
            require(memory.readWord(TRACK_MAP_2 + index * 2) == (expected & 0xFFFF),
                "incorrect sky map entry " + index);
        }
    }

    private static void verifyTrackTiles(Memory memory, Map<String, Integer> symbols) {
        int worldX = memory.readDword(symbol(symbols, "_WORLDX"));
        int worldY = memory.readDword(symbol(symbols, "_WORLDY"));
        int angle = memory.readWord(symbol(symbols, "_TETA"));
        int track = memory.readDword(symbol(symbols, "_TRACK"));
        int cosine = symbol(symbols, "_TCOS");
        int sine = symbol(symbols, "_TSIN");

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
                int tile0 = 0;
                int tile1 = 0;
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
                    if (pixel != 7) {
                        tile0 <<= 2;
                        tile1 <<= 2;
                    }
                }
                require(memory.readWord(tileAddress) == (tile0 & 0xFFFF),
                    String.format("track plane 1 mismatch at x=%d y=%d", x, y));
                require(memory.readWord(tileAddress + 2048) == (tile1 & 0xFFFF),
                    String.format("track plane 2 mismatch at x=%d y=%d", x, y));
            }
        }
    }

    private static int trig(Memory memory, int table, int angle) {
        return (short) memory.readWord(table + ((angle & 511) * 2));
    }

    private static byte[] snapshot(Memory memory, int address, int length) {
        byte[] result = new byte[length];
        for (int index = 0; index < length; index++) {
            result[index] = (byte) memory.readByte(address + index);
        }
        return result;
    }

    private static Map<String, Integer> loadMap(String path) throws Exception {
        Map<String, Integer> symbols = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3 && parts[0].startsWith("_") && parts[1].equals("Int")) {
                    symbols.put(parts[0], Integer.parseInt(parts[2], 16));
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

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
