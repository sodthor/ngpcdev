package sim.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import sim.Cpu;
import sim.Memory;
import sim.NgpLoader;
import sim.Registers;

/** Exercises the actual Gems move/refill/level-up path, including VBlank interrupts. */
public final class GemsLevelUpTest {
    private final Map<String, Integer> symbols;
    private final Memory memory;
    private final Registers registers = new Registers();
    private final Cpu cpu;
    private final int data;
    private final int gems;
    private final int board;
    private final int gaugeAddress;
    private final int timer;
    private final int refillFlag;
    private final int swapsLeft;
    private int levelUpSounds;
    private int refills;
    private boolean sawFullGauge;
    private boolean interrupts;
    private boolean inInterrupt;
    private int interruptedPC;
    private long nextInterrupt;
    private long instructions;

    private GemsLevelUpTest(String rom, String map) throws Exception {
        symbols = new HashMap<>();
        String pending = null;
        for (String line : Files.readAllLines(Path.of(map))) {
            String[] fields = line.strip().split("\\s+");
            if (fields[0].startsWith("_")) {
                pending = fields[0];
                int column = fields.length > 1 && fields[1].equals("Int") ? 2 : 1;
                if (fields.length > column && fields[column].matches("[0-9A-Fa-f]{4,8}")) {
                    symbols.put(pending, Integer.parseInt(fields[column], 16));
                    pending = null;
                }
            } else if (pending != null && fields[0].matches("[0-9A-Fa-f]{4,8}")) {
                symbols.put(pending, Integer.parseInt(fields[0], 16));
                pending = null;
            }
        }
        memory = new Memory(Arrays.copyOf(NgpLoader.load(rom).data, 0x200000));
        cpu = new Cpu(memory, registers);
        data = symbol("_DATA");
        // cc900 aligns the board array and following word fields to two bytes.
        boolean cc900 = Files.readString(Path.of(map)).contains("Toshiba Unified Linker");
        gems = data + 8;
        board = gems + (cc900 ? 12 : 11);
        gaugeAddress = gems + (cc900 ? 76 : 75);
        timer = gems + (cc900 ? 78 : 76);
        refillFlag = gems + (cc900 ? 81 : 79);
        swapsLeft = gems + (cc900 ? 84 : 82);
    }

    private int symbol(String name) {
        Integer address = symbols.get(name);
        require(address != null, "Missing symbol " + name);
        return address;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void step() {
        if (inInterrupt && registers.getPC() == interruptedPC) inInterrupt = false;
        if (interrupts && !inInterrupt && instructions >= nextInterrupt) {
            interruptedPC = registers.getPC();
            int stack = registers.getXSP() - 6;
            memory.writeDword(stack, interruptedPC);
            memory.writeWord(stack + 4, registers.getSR());
            registers.setXSP(stack);
            registers.setPC(symbol("_gems_myVBLinterrupt"));
            inInterrupt = true;
            nextInterrupt = instructions + 4096;
        }
        if (!interrupts && (instructions & 127) == 0) {
            int counter = symbol("_VBCounter");
            memory.writeByte(counter, memory.readByte(counter) + 1);
        }
        if (registers.getPC() == symbol("_SL_PlaySFX")
                && memory.readWord(registers.getXSP() + 4) == 16) levelUpSounds++;
        if (registers.getPC() == symbol("_gems_addNewGems")) {
            refills++;
        }
        if (memory.readByte(gaugeAddress) == 128) sawFullGauge = true;
        cpu.step();
        instructions++;
    }

    private void call(String name, int... words) {
        memory.writeDword(0x6b00, 0x123456);
        for (int i = 0; i < words.length; i++) memory.writeWord(0x6b04 + i * 2, words[i]);
        registers.setSR(0x0800);
        registers.setXSP(0x6b00);
        registers.setPC(symbol(name));
        inInterrupt = false;
        nextInterrupt = instructions + 4096;
        long limit = instructions + 10_000_000;
        while (registers.getPC() != 0x123456 && instructions < limit) step();
        require(registers.getPC() == 0x123456, name + " timed out");
        require(registers.getXSP() == 0x6b04, name + " unbalanced stack");
    }

    private void reset(int gauge, int level, boolean timeTrial) {
        for (int i = 0; i < 288; i++) memory.writeByte(data + i, 0);
        memory.writeByte(data + 4, 1);
        memory.writeByte(data + 6, 7);
        memory.writeByte(gems, 64);
        memory.writeByte(gems + 1, 48);
        memory.writeByte(gems + 4, 2); // Down: swap (4,3) with (4,4).
        memory.writeByte(gems + 9, level);
        memory.writeByte(gems + 10, timeTrial ? 1 : 0);
        memory.writeByte(gaugeAddress, gauge);
        memory.writeWord(timer, 300);
        memory.writeByte(swapsLeft, 10);
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) memory.writeByte(board + x * 8 + y, (x + y) % 5);
        }
        memory.writeByte(board + 2 * 8 + 4, 3);
        memory.writeByte(board + 3 * 8 + 4, 3);
        memory.writeByte(board + 4 * 8 + 4, 4);
        memory.writeByte(board + 4 * 8 + 3, 3);
        memory.writeDword(symbol("_RAND_RandomData"), 0);
        levelUpSounds = refills = 0;
        sawFullGauge = false;
    }

    private void gaugeRendering() {
        for (int gauge = 0; gauge <= 128; gauge++) {
            reset(gauge, 0, false);
            // A blank initial map hides missing clear writes after the gauge falls.
            for (int y = 1; y <= 17; y++) memory.writeWord(0x9000 + 2 * (y * 32 + 1), 0x1845);
            call("_gems_showBoard", 0);
            int full = gauge / 8;
            for (int y = 1; y <= 17; y++) {
                int tile = y > 17 - full ? 69 : y == 17 - full ? 70 + gauge % 8 : 0;
                int expected = tile == 0 ? 0
                    : tile + (memory.readByte(symbol("_gems_palidx") + tile - 1) << 9);
                int actual = memory.readWord(0x9000 + 2 * (y * 32 + 1));
                require(actual == expected, String.format(
                    "Gauge %d row %d: tile=%04X expected=%04X", gauge, y, actual, expected));
            }
            require(memory.readByte(gaugeAddress) == gauge, "Rendering changed gauge");
        }
        System.out.println("Gems gauge rendering: PASS (129 values)");
    }

    private void boardClearing() {
        reset(41, 1, false);
        for (int cell = 0; cell < 32 * 32; cell++) memory.writeWord(0x9000 + cell * 2, 0x1845);
        call("_gems_hideBoard");
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                int expected = y < 16 && x >= 3 && x < 19 ? 0 : 0x1845;
                require(memory.readWord(0x9000 + (y * 32 + x) * 2) == expected,
                    "Hide board failed to clear only its tile cells");
            }
        }
        System.out.println("Gems board hiding/zero-tile clearing: PASS");
    }

    private void levelUps() {
        int cases = 0;
        for (boolean realInterrupts : new boolean[]{false, true}) {
            interrupts = realInterrupts;
            for (boolean timeTrial : new boolean[]{false, true}) {
                for (int level = 0; level < 4; level++) {
                    for (int gauge : new int[]{124, 125, 126, 128}) {
                        reset(gauge, level, timeTrial);
                        String context = "gauge=" + gauge + " level=" + level + " timeTrial="
                            + timeTrial + " interrupts=" + realInterrupts;
                        call("_gems_doMove");
                        require(registers.getL() == 1, "Valid move rejected: " + context);
                        boolean expected = gauge >= 125;
                        require(levelUpSounds == (expected ? 1 : 0),
                            "Level-up notification count " + levelUpSounds + ": " + context);
                        require(memory.readByte(gems + 9) == Math.min(3, level + (expected ? 1 : 0)),
                            "Wrong difficulty after move: " + context);
                        require(refills == (expected ? 2 : 1), "Wrong refill count: " + context);
                        require(memory.readByte(gaugeAddress) < 128, "Gauge stuck full: " + context);
                        require(memory.readByte(refillFlag) == 0, "Refill flag stuck: " + context);
                        require(memory.readByte(swapsLeft) >= (timeTrial ? 10 : 9),
                            "Wrong remaining swaps: " + context);
                        for (int cell = 0; cell < 64; cell++) require(memory.readByte(board + cell)
                            < 5 + level + (expected ? 1 : 0), "Invalid gem: " + context);
                        cases++;
                    }
                }
            }
        }
        System.out.printf("Gems successful swaps and level-up: PASS (%d cases)%n", cases);
    }

    private static boolean match(int[] cells) {
        for (int x = 0; x < 8; x++) {
            for (int y = 0; y < 8; y++) {
                int i = x * 8 + y;
                if (x < 6 && cells[i] == cells[i + 8] && cells[i] == cells[i + 16]) return true;
                if (y < 6 && cells[i] == cells[i + 1] && cells[i] == cells[i + 2]) return true;
            }
        }
        return false;
    }

    private void naturalProgress() {
        int moves = 0;
        for (int seed : new int[]{0, 1, 0x12345678, -1}) {
            reset(0, 0, false);
            memory.writeDword(symbol("_RAND_RandomData"), seed);
            interrupts = true;
            call("_gems_initBoard");
            for (int move = 0; move < 200 && memory.readByte(gems + 9) < 2; move++) {
                int[] cells = new int[64];
                for (int i = 0; i < cells.length; i++) cells[i] = memory.readByte(board + i);
                require(!match(cells), "Unresolved match before move");
                int selected = -1, direction = 0;
                search:
                for (int i = 0; i < 64; i++) {
                    for (int offset : new int[]{1, 8}) {
                        if (offset == 1 && i % 8 == 7 || offset == 8 && i / 8 == 7) continue;
                        int other = i + offset;
                        int swap = cells[i]; cells[i] = cells[other]; cells[other] = swap;
                        boolean valid = match(cells);
                        swap = cells[i]; cells[i] = cells[other]; cells[other] = swap;
                        if (valid) { selected = i; direction = offset == 1 ? 2 : 8; break search; }
                    }
                }
                if (selected < 0) break;
                memory.writeByte(gems, selected / 8 * 16);
                memory.writeByte(gems + 1, selected % 8 * 16);
                memory.writeByte(gems + 4, direction);
                // Keep the fixture alive while exercising the actual move and interrupt logic.
                memory.writeWord(timer, 300);
                memory.writeByte(swapsLeft, 100);
                int levelBefore = memory.readByte(gems + 9);
                int soundsBefore = levelUpSounds;
                sawFullGauge = false;
                call("_gems_doMove");
                require(registers.getL() == 1, "Natural valid move rejected");
                require(!sawFullGauge || levelUpSounds > soundsBefore,
                    "Natural full gauge did not trigger level-up: seed=" + seed + " move=" + move
                        + " gauge=" + memory.readByte(gaugeAddress) + " level=" + levelBefore);
                require(memory.readByte(gems + 9) == levelBefore + levelUpSounds - soundsBefore,
                    "Natural level-up did not change difficulty");
                moves++;
            }
        }
        System.out.printf("Gems natural board progression: PASS (%d successful moves)%n", moves);
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 2 || args.length == 3 && args[2].equals("--gauge-only"),
            "Usage: GemsLevelUpTest ROM MAP [--gauge-only]");
        GemsLevelUpTest test = new GemsLevelUpTest(args[0], args[1]);
        test.gaugeRendering();
        if (args.length == 3) return;
        test.boardClearing();
        test.levelUps();
        test.naturalProgress();
        System.out.println("GemsLevelUpTest: PASS");
    }
}
