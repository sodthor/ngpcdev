package sim.sample2;

import sim.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Regression test for SAMPLE_2's loop-carried sprite animation countdown. */
public final class Sample2SpriteAnimationTest {
    private static final int ROM_BASE = 0x200000;
    private static final int SPRITE_RAM = 0x8800;

    private Sample2SpriteAnimationTest() {}

    public static void main(String[] args) throws Exception {
        String romPath = args.length > 0 ? args[0] : "../SAMPLE_2/SAMPLE.ngp";
        String mapPath = args.length > 1 ? args[1] : "../SAMPLE_2/SAMPLE.map";
        Map<String, Integer> symbols = loadMap(mapPath);

        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory memory = new Memory(rom.data);
        Registers registers = new Registers();
        Cpu cpu = new Cpu(memory, registers);
        memory.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int address) {
                if (address == 0x6F82) return 0; // no joypad input
                return -1;
            }

            @Override public void write(int address, int value) {}
        });
        cpu.setSwiHandler((number, regs, mem) -> true);
        NgpLoader.init(rom, memory, registers);

        int sleep = symbol(symbols, "_SLEEP");
        int sprAnim = symbol(symbols, "_SPR_ANIM");
        int romEnd = ROM_BASE + rom.data.length;
        int sleepCalls = 0;
        Set<Integer> observedAnimations = new HashSet<>();
        Set<Integer> observedTiles = new HashSet<>();
        long instructions = 0;

        while (instructions < 5_000_000 && sleepCalls < 12) {
            if (registers.getPC() == sleep) {
                if (sleepCalls == 0) verifyLevelTiles(memory, symbols);
                int tile = memory.readByte(SPRITE_RAM)
                    | (memory.readByte(SPRITE_RAM + 1) << 8);
                observedAnimations.add(memory.readWord(sprAnim));
                observedTiles.add(tile);

                // Sleep waits for a VBL interrupt. Model its observable completion
                // without requiring interrupt scheduling in the CPU simulator.
                int returnAddress = memory.readDword(registers.getXSP()) & 0xFFFFFF;
                registers.setXSP(registers.getXSP() + 4);
                registers.setPC(returnAddress);
                sleepCalls++;
                continue;
            }

            int previousPc = registers.getPC();
            try {
                cpu.step();
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("RAM exhausted")) {
                    System.out.printf("Sample2SpriteAnimationTest: FAILED (collision after %d iterations, %d instructions)%n",
                        sleepCalls, instructions);
                    System.out.println(memory.getRamReport());
                    throw new AssertionError("RAM exhaustion: " + e.getMessage(), e);
                }
                throw e;
            }
            instructions++;
            int pc = registers.getPC();
            require(pc >= ROM_BASE && pc < romEnd,
                String.format("execution left ROM at 0x%06X from 0x%06X", pc, previousPc));
        }

        require(sleepCalls == 12, "main did not complete 12 animation iterations");
        require(observedAnimations.equals(Set.of(1, 2)),
            "unexpected spr_anim sequence across 12 iterations: " + observedAnimations);
        require(observedTiles.size() >= 2,
            "sprite tile did not change across main-loop iterations: " + observedTiles);

        System.out.println("SAMPLE_2 level tiles: PASS (graphics, palettes, 420 initial map entries)");
        System.out.printf("Sample2SpriteAnimationTest: PASS (%d iterations, %d animation states, %d sprite tiles)%n",
            sleepCalls, observedAnimations.size(), observedTiles.size());
        System.out.println(memory.getRamReport());
    }

    private static void verifyLevelTiles(Memory memory, Map<String, Integer> symbols) {
        verifyLevelTiles(memory, symbols, 21, 20);
    }

    static void verifyLevelTiles(Memory memory, Map<String, Integer> symbols, int columns, int rows) {
        int graphics = symbol(symbols, "_GFX_TILES");
        int palettes = symbol(symbols, "_GFX_PALS");
        int paletteIndices = symbol(symbols, "_GFX_PALIDX");
        for (int offset = 0; offset < 16 * 16; offset += 2) {
            require(memory.readWord(0xA000 + offset) == memory.readWord(graphics + offset),
                String.format("tile graphics mismatch at byte %d: RAM %04X, ROM %04X", offset,
                    memory.readWord(0xA000 + offset), memory.readWord(graphics + offset)));
        }
        for (int offset = 0; offset < 8 * 8; offset += 2) {
            int expected = memory.readWord(palettes + offset);
            require(memory.readWord(0x8280 + offset) == expected && memory.readWord(0x8200 + offset) == expected,
                "tile/sprite palette mismatch at byte " + offset);
        }
        int map = memory.readDword(symbol(symbols, "_MAP"));
        int width = memory.readWord(symbol(symbols, "_MAP_W"));
        int mapX = memory.readWord(symbol(symbols, "_MAP_X"));
        int mapY = memory.readWord(symbol(symbols, "_MAP_Y"));
        int scrollColumn = memory.readByte(0x8032) >>> 3;
        int scrollRow = memory.readByte(0x8033) >>> 3;
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int tile = memory.readByte(map + (mapY + row) * width + mapX + column);
                require(tile < 16, "level map contains invalid tile " + tile);
                int expected = (memory.readByte(paletteIndices + tile) << 9) + tile;
                int index = ((scrollColumn + column) & 31) + (((scrollRow + row) & 31) * 32);
                int actual = memory.readWord(0x9000 + 2 * index);
                require(actual == expected, String.format("level tile mismatch at (%d,%d): got %04X, expected %04X",
                    column, row, actual, expected));
            }
        }
    }

    private static Map<String, Integer> loadMap(String path) throws Exception {
        Map<String, Integer> symbols = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length >= 3 && fields[0].startsWith("_")) {
                    try {
                        symbols.put(fields[0].toUpperCase(), Integer.parseInt(fields[2], 16));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return symbols;
    }

    private static int symbol(Map<String, Integer> symbols, String name) {
        Integer value = symbols.get(name.toUpperCase());
        if (value == null) throw new IllegalArgumentException("missing map symbol " + name);
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
