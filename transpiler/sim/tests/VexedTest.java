package sim.tests;

import sim.Cpu;
import sim.Memory;
import sim.NgpLoader;
import sim.Registers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VexedTest {
    private final Map<String, Integer> symbols = new HashMap<>();
    private final Memory memory;
    private final Registers registers = new Registers();
    private final Cpu cpu;
    private boolean animationClock;

    private VexedTest(String rom, String map) throws Exception {
        memory = new Memory(Arrays.copyOf(NgpLoader.load(rom).data, 0x200000));
        cpu = new Cpu(memory, registers);
        for (String line : Files.readAllLines(Path.of(map))) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length >= 3 && fields[1].equals("Int")) {
                symbols.put(fields[0], Integer.parseInt(fields[2], 16));
            }
        }
    }

    private int symbol(String name) {
        Integer address = symbols.get(name);
        if (address == null) throw new AssertionError("Missing symbol " + name);
        return address;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void call(String name, int... words) {
        invoke(name, false, words);
    }

    private void invoke(String name, boolean interrupt, int... words) {
        int stack = 0x6b00;
        memory.writeDword(stack, 0x123456);
        if (interrupt) memory.writeWord(stack + 4, 0x0800);
        for (int index = 0; index < words.length; index++) {
            memory.writeWord(stack + 4 + 2 * index, words[index]);
        }
        registers.setSR(0x0800);
        if (interrupt) {
            for (int bank = 0; bank < 4; bank++) {
                for (int pair = 0; pair < 4; pair++) registers.setBankGP32(bank, pair,
                        0x12345678 + bank * 0x1010101 + pair * 0x11111111);
            }
            registers.setXIX(0x23456789);
            registers.setXIY(0x3456789a);
            registers.setXIZ(0x456789ab);
        }
        registers.setXSP(stack);
        registers.setPC(symbol(name));
        for (int steps = 0; steps < 2_000_000; steps++) {
            if (animationClock && (steps & 127) == 0) {
                int counter = symbol("_VBCounter");
                memory.writeByte(counter, memory.readByte(counter) + 1);
            }
            if (registers.getPC() == 0x123456) {
                require(registers.getXSP() == stack + (interrupt ? 6 : 4), "Unbalanced stack: " + name);
                if (interrupt) {
                    for (int bank = 0; bank < 4; bank++) {
                        for (int pair = 0; pair < 4; pair++) require(registers.getBankGP32(bank, pair)
                                == 0x12345678 + bank * 0x1010101 + pair * 0x11111111,
                                name + " clobbered bank " + bank + " register " + pair);
                    }
                    require(registers.getXIX() == 0x23456789 && registers.getXIY() == 0x3456789a
                            && registers.getXIZ() == 0x456789ab, name + " clobbered index registers");
                    require(registers.getSR() == 0x0800, name + " clobbered status register");
                }
                return;
            }
            cpu.step();
        }
        throw new AssertionError(String.format("%s timed out at %06X", name, registers.getPC()));
    }

    private List<String> table(String header, String name, int width, int count) {
        Matcher declaration = Pattern.compile("const char " + name + "\\[[^;]+?=\\s*\\{(.*?)\\};",
                Pattern.DOTALL).matcher(header);
        require(declaration.find(), "Missing source array " + name);
        Matcher literal = Pattern.compile("\"([^\"]*)\"").matcher(declaration.group(1));
        List<String> rows = new ArrayList<>();
        while (literal.find()) rows.add(literal.group(1));
        require(rows.size() == count, name + " source row count " + rows.size());
        int address = symbol("_" + name);
        for (int row = 0; row < count; row++) {
            String text = rows.get(row);
            require(text.length() < width, name + " oversized source row " + row);
            for (int column = 0; column < width; column++) {
                int expected = column < text.length() ? text.charAt(column) : 0;
                int actual = memory.readByte(address + row * width + column);
                require(actual == expected, String.format(
                        "%s row=%d column=%d ROM=%02X source=%02X", name, row, column, actual, expected));
            }
        }
        System.out.printf("%s: PASS (%d rows, stride %d)%n", name, count, width);
        return rows;
    }

    private int tile(char letter) {
        if (letter == ' ') return 0;
        int glyph = Character.toLowerCase(letter) - 'a' + 10;
        if (letter == '\'') glyph = 0;
        int number = 39 * 2 + glyph + 1;
        return number + (memory.readByte(symbol("_VEXED_PALIDX1") + number - 1) << 9);
    }

    private void text(List<String> packs) {
        int data = symbol("_DATA");
        for (int pack = 0; pack < packs.size(); pack++) {
            for (int offset = 0; offset < 280; offset++) memory.writeByte(data + offset, 0);
            memory.writeByte(data + 8 + 11, pack);
            call("_vexed_printInfos", 0);
            String text = packs.get(pack);
            for (int column = 0; column < 20; column++) {
                int expected = column < text.length() ? tile(text.charAt(column)) : 0;
                int actual = memory.readWord(0x9000 + 2 * (12 * 32 + 1 + column));
                require(actual == expected, String.format(
                        "Pack %d '%s' column=%d tile=%04X expected=%04X", pack, text, column, actual, expected));
            }
        }
        System.out.println("Pack menu tile rendering: PASS");
    }

    private void boards(List<String> levels) {
        int data = symbol("_DATA");
        for (int index = 0; index < levels.size(); index++) {
            String[] rows = levels.get(index).split("/");
            require(rows.length == 8, "Source board row count: " + index);
            int[][] expected = new int[10][8];
            int count = 0;
            for (int row = 0; row < 8; row++) {
                int column = 0;
                for (int offset = 0; offset < rows[row].length(); offset++) {
                    char value = rows[row].charAt(offset);
                    if (Character.isDigit(value)) {
                        int length = value - '0';
                        if (value == '1' && offset + 1 < rows[row].length()
                                && rows[row].charAt(offset + 1) == '0') {
                            length = 10;
                            offset++;
                        }
                        column += length;
                    } else {
                        int block = value == '~' ? 9 : value == 'a' ? 2 : value == 'b' ? 1 : value - 'a' + 1;
                        expected[column++][row] = block;
                        if (block != 9) count++;
                    }
                }
                require(column == 10, "Source board width: " + index);
            }
            int source = symbol("_vexed_level_struct") + 80 * index;
            call("_vexed_getLevel", source & 65535, source >>> 16);
            for (int column = 0; column < 10; column++) {
                for (int row = 0; row < 8; row++) {
                    int actual = memory.readByte(data + 8 + 13 + column * 8 + row);
                    require(actual == expected[column][row], String.format(
                            "Board %d x=%d y=%d actual=%d expected=%d", index, column, row, actual, expected[column][row]));
                }
            }
            require(memory.readByte(data + 200) == count, "Board block count: " + index);
            memory.writeWord(data + 8, index);
            call("_vexed_showBoard");
            for (int column = 0; column < 10; column++) {
                for (int row = 0; row < 8; row++) {
                    int block = expected[column][row];
                    for (int part = 0; part < 4; part++) {
                        int number = block == 9 ? 0 : block * 2 + 1 + (part & 1) + (part / 2) * 39;
                        int wanted = number == 0 ? 0 : number
                                + (memory.readByte(symbol("_VEXED_PALIDX1") + number - 1) << 9);
                        int address = 0x9000 + 2 * ((row * 2 + 2 + part / 2) * 32
                                + column * 2 + (part & 1));
                        int actual = memory.readWord(address);
                        require(actual == wanted, String.format(
                                "Rendered board %d x=%d y=%d part=%d tile=%04X expected=%04X",
                                index, column, row, part, actual, wanted));
                    }
                }
            }
        }
        System.out.printf("Level decoding and rendering: PASS (%d boards)%n", levels.size());
    }

    private int paletteTile(String palette, int number, int offset) {
        return number + (memory.readByte(symbol(palette) + number + offset) << 9);
    }

    private void saves() {
        int destination = symbol("_data");
        for (int index = 0; index < 1024; index++) memory.writeByte(destination + index, 165);
        call("_getSavedData");
        require(memory.readDword(destination) == 0xcafebabe, "New save magic");
        for (int index = 4; index < 1024; index++) {
            require(memory.readByte(destination + index) == 0, "New save initialization byte " + index);
        }
        byte[] cartridge = memory.getCartRom();
        for (int index = 0; index < 1024; index++) cartridge[0x1e0000 + index] = (byte) (index * 37 + 11);
        for (int index = 0; index < 4; index++) cartridge[0x1e0000 + index] = (byte) (0xcafebabe >>> (index * 8));
        call("_getSavedData");
        for (int index = 0; index < 1024; index++) {
            require(memory.readByte(destination + index) == (cartridge[0x1e0000 + index] & 255),
                    "Saved progress copy byte " + index);
        }
        System.out.println("Shared save loading: PASS (empty and populated storage)");
    }

    private void gems() {
        int board = symbol("_DATA") + 19;
        for (int column = 0; column < 8; column++) {
            for (int row = 0; row < 8; row++) {
                int value = (column + row * 3) % 8;
                if (row == column) value = 255;
                if (row + column == 7) value = 254;
                memory.writeByte(board + column * 8 + row, value);
            }
        }
        call("_gems_showBoard", 0);
        for (int column = 0; column < 8; column++) {
            for (int row = 0; row < 8; row++) {
                int block = memory.readByte(board + column * 8 + row);
                for (int part = 0; part < 4; part++) {
                    int number = block >= 254 ? 0 : (block & 127) * 2 + 1
                            + (part & 1) + (part / 2) * 29;
                    int expected = paletteTile("_gems_palidx", number, -1);
                    int address = 0x9000 + 2 * ((row * 2 + part / 2) * 32
                            + column * 2 + 3 + (part & 1));
                    require(memory.readWord(address) == expected, String.format(
                            "Gems x=%d y=%d part=%d tile=%04X expected=%04X",
                            column, row, part, memory.readWord(address), expected));
                }
            }
        }
        System.out.println("Gems gameplay tile rendering: PASS");
        for (int difficulty = 0; difficulty < 4; difficulty++) {
            memory.writeByte(symbol("_DATA") + 17, difficulty);
            call("_gems_initBoard");
            for (int column = 0; column < 8; column++) {
                for (int row = 0; row < 8; row++) {
                    int value = memory.readByte(board + column * 8 + row);
                    require(value < difficulty + 5, "Gems generated value out of range");
                    require(column == 0 || value != memory.readByte(board + (column - 1) * 8 + row),
                            "Gems generated matching horizontal neighbors");
                    require(row == 0 || value != memory.readByte(board + column * 8 + row - 1),
                            "Gems generated matching vertical neighbors");
                }
            }
        }
        System.out.println("Gems board initialization: PASS (4 difficulties)");
    }

    private void gemsMatches() {
        int board = symbol("_DATA") + 19;
        Random random = new Random(0x47454d53);
        for (int fixture = 0; fixture < 385; fixture++) {
            int[] values = new int[64];
            for (int column = 0; column < 8; column++) {
                for (int row = 0; row < 8; row++) values[column * 8 + row] = (column + row) % 5;
            }
            if (fixture >= 129) {
                for (int cell = 0; cell < 64; cell++) {
                    int value = random.nextInt(10);
                    values[cell] = value == 8 ? 254 : value == 9 ? 255 : value;
                }
            } else if (fixture > 0) {
                int cell = (fixture - 1) % 64;
                boolean horizontal = fixture <= 64;
                int column = horizontal ? Math.min(cell / 8, 5) : cell / 8;
                int row = horizontal ? cell % 8 : Math.min(cell % 8, 5);
                for (int part = 0; part < 3; part++) {
                    values[(column + (horizontal ? part : 0)) * 8 + row + (horizontal ? 0 : part)] = 7;
                }
            }
            boolean[] removed = new boolean[64];
            boolean hasMatch = false;
            for (int column = 0; column < 8; column++) {
                for (int row = 0; row < 8; row++) {
                    int cell = column * 8 + row;
                    if (values[cell] == 255) removed[cell] = true;
                    if (column < 6 && values[cell] == values[cell + 8] && values[cell] == values[cell + 16]) {
                        removed[cell] = removed[cell + 8] = removed[cell + 16] = true;
                        hasMatch = true;
                    }
                    if (row < 6 && values[cell] == values[cell + 1] && values[cell] == values[cell + 2]) {
                        removed[cell] = removed[cell + 1] = removed[cell + 2] = true;
                        hasMatch = true;
                    }
                    memory.writeByte(board + cell, values[cell]);
                }
            }
            call("_gems_findBlock");
            require(registers.getL() == (hasMatch ? 1 : 0), "Gems findBlock fixture " + fixture
                    + " returned " + registers.getL());
            call("_gems_removeGems");
            int count = 0;
            for (int cell = 0; cell < 64; cell++) {
                if (removed[cell]) count++;
                require(memory.readByte(board + cell) == (removed[cell] ? 255 : values[cell]),
                        "Gems removal fixture " + fixture + " cell " + cell);
            }
            require(registers.getL() == count, "Gems removal count fixture " + fixture);
        }
        System.out.println("Gems match detection and removal: PASS (385 boards)");
    }

    private void gemsCascade() {
        int data = symbol("_DATA");
        for (int offset = 0; offset < 288; offset++) memory.writeByte(data + offset, 0);
        for (int column = 0; column < 8; column++) {
            for (int row = 0; row < 8; row++) memory.writeByte(data + 19 + column * 8 + row, (column + row) % 5);
        }
        memory.writeByte(data + 8, 64);
        memory.writeByte(data + 9, 64);
        byte[] cursor = new byte[9];
        for (int field = 0; field < cursor.length; field++) cursor[field] = (byte) memory.readByte(data + 8 + field);
        for (int column = 2; column < 5; column++) memory.writeByte(data + 19 + column * 8 + 4, 3);
        animationClock = true;
        try {
            call("_gems_addNewGems");
        } finally {
            animationClock = false;
        }
        require(registers.getHL() >= 3, "Gems cascade did not count initial match");
        require(memory.readByte(data + 87) == 0, "Gems cascade left refill flag set");
        for (int field = 0; field < cursor.length; field++) require(memory.readByte(data + 8 + field) == (cursor[field] & 255),
                "Gems cascade corrupted cursor field " + field);
        for (int cell = 0; cell < 64; cell++) require(memory.readByte(data + 19 + cell) < 5,
                "Gems cascade left invalid gem at " + cell);
        call("_gems_findBlock");
        require(registers.getL() == 0, "Gems cascade left a match unresolved");
        System.out.println("Gems animated removal and refill, cursor isolation: PASS");
    }

    private void cursors() {
        int data = symbol("_DATA");
        for (boolean vexed : new boolean[] {false, true}) {
            String name = vexed ? "_vexed_myVBLinterrupt" : "_gems_myVBLinterrupt";
            int base = vexed ? 10 : 8;
            int maximum = vexed ? 144 : 112;
            int fixtures = 0;
            for (int column = 0; column <= maximum / 16; column++) {
                for (int row = 0; row < 8; row++) {
                    for (int keys = 0; keys < 32; keys++) {
                        for (int offset = 0; offset < 288; offset++) memory.writeByte(data + offset, 0);
                        memory.writeByte(data + 4, 1);
                        memory.writeByte(data + 6, 7);
                        if (!vexed) memory.writeByte(data + 87, 1);
                        int[] expected = {column * 16, row * 16, 0, 0, 0, 0, 0, 1, 0};
                        for (int field = 0; field < expected.length; field++) {
                            memory.writeByte(data + base + field, expected[field]);
                        }
                        for (int frame = 0; frame < 20; frame++) {
                            int joypad = frame == 0 ? keys : 0;
                            memory.writeByte(0x6f82, joypad);
                            boolean moved = expected[8] != 0;
                            if (expected[8] != 0) {
                                expected[0] = (expected[0] + expected[5]) & 255;
                                expected[1] = (expected[1] + expected[6]) & 255;
                                if (expected[0] == expected[2] && expected[1] == expected[3]) expected[8] = 0;
                            } else if (expected[7] != 0 && (joypad & 16) != 0) {
                                if ((joypad & 4) != 0 && expected[0] > 0) expected[4] = 4;
                                else if ((joypad & 8) != 0 && expected[0] < maximum) expected[4] = 8;
                                else if (!vexed && (joypad & 1) != 0 && expected[1] > 0) expected[4] = 1;
                                else if (!vexed && (joypad & 2) != 0 && expected[1] < 112) expected[4] = 2;
                                if (expected[4] != 0) expected[7] = 0;
                            } else if (expected[4] == 0) {
                                expected[2] = expected[0];
                                expected[3] = expected[1];
                                if ((joypad & 4) != 0) expected[2] = Math.max(0, expected[2] - 16);
                                if ((joypad & 8) != 0) expected[2] = Math.min(maximum, expected[2] + 16);
                                if ((joypad & 1) != 0) expected[3] = Math.max(0, expected[3] - 16);
                                if ((joypad & 2) != 0) expected[3] = Math.min(112, expected[3] + 16);
                                if (expected[2] != expected[0] || expected[3] != expected[1]) {
                                    expected[5] = Integer.compare(expected[2], expected[0]) & 255;
                                    expected[6] = Integer.compare(expected[3], expected[1]) & 255;
                                    expected[8] = 1;
                                }
                            }
                            invoke(name, true);
                            if (moved) {
                                for (int part = 0; part < 4; part++) {
                                    int wantedX = expected[0] + (vexed ? 0 : 24) + (part & 1) * 8;
                                    int wantedY = expected[1] + (vexed ? 16 : 0) + (part / 2) * 8;
                                    require(memory.readByte(0x8802 + part * 4) == wantedX
                                            && memory.readByte(0x8803 + part * 4) == wantedY,
                                            name + " misplaced cursor sprite " + part + " frame " + frame);
                                }
                            }
                            for (int field = 0; field < expected.length; field++) {
                                int actual = memory.readByte(data + base + field);
                                require(actual == expected[field], String.format(
                                        "%s x=%d y=%d keys=%02X frame=%d field=%d actual=%d expected=%d",
                                        name, column, row, keys, frame, field, actual, expected[field]));
                            }
                        }
                        fixtures++;
                    }
                }
            }
            System.out.printf("%s cursor state and bounds: PASS (%d inputs, 20 frames each)%n",
                    vexed ? "Vexed" : "Gems", fixtures);
        }
    }

    private void dynamateCursors() {
        int data = symbol("_DATA");
        int pointer = 0x6000;
        int fixtures = 0;
        animationClock = true;
        try {
            for (boolean blocked : new boolean[] {false, true}) {
                for (int column = 0; column < 16; column++) {
                    for (int row = 0; row < 16; row++) {
                        for (int direction = 0; direction < 4; direction++) {
                            int stepX = direction == 0 ? -1 : direction == 1 ? 1 : 0;
                            int stepY = direction == 2 ? -1 : direction == 3 ? 1 : 0;
                            for (int cell = 0; cell < 256; cell++) memory.writeByte(data + 8 + cell, 0);
                            int obstacleX = column + stepX * 3;
                            int obstacleY = row + stepY * 3;
                            if (blocked && obstacleX >= 0 && obstacleX < 16 && obstacleY >= 0 && obstacleY < 16) {
                                memory.writeByte(data + 8 + obstacleY * 16 + obstacleX, 16);
                            }
                            int wantedX = column;
                            int wantedY = row;
                            while (wantedX + stepX >= 0 && wantedX + stepX < 16
                                    && wantedY + stepY >= 0 && wantedY + stepY < 16
                                    && memory.readByte(data + 8 + (wantedY + stepY) * 16 + wantedX + stepX) == 0) {
                                wantedX += stepX;
                                wantedY += stepY;
                            }
                            memory.writeByte(pointer - 1, 165);
                            memory.writeByte(pointer, column * 8);
                            memory.writeByte(pointer + 1, row * 8);
                            memory.writeByte(pointer + 2, 90);
                            call("_dynamate_fastMove", pointer, 0, pointer + 1, 0, stepX & 255, stepY & 255);
                            require(registers.getL() == 1, "Dynamate fast cursor return");
                            require(memory.readByte(pointer) == wantedX * 8 && memory.readByte(pointer + 1) == wantedY * 8,
                                    String.format("Dynamate cursor x=%d y=%d direction=%d blocked=%s actual=%d,%d expected=%d,%d",
                                            column, row, direction, blocked, memory.readByte(pointer), memory.readByte(pointer + 1),
                                            wantedX * 8, wantedY * 8));
                            require(memory.readByte(pointer - 1) == 165 && memory.readByte(pointer + 2) == 90,
                                    "Dynamate cursor overwrote adjacent memory");
                            if (column != wantedX || row != wantedY) require(memory.readByte(0x8802) == wantedX * 8 + 16
                                    && memory.readByte(0x8803) == wantedY * 8 + 8, "Dynamate misplaced cursor sprite");
                            fixtures++;
                        }
                    }
                }
            }
        } finally {
            animationClock = false;
        }
        System.out.printf("Dynamate fast cursor movement: PASS (%d paths)%n", fixtures);
    }

    private void vexedMove() {
        int data = symbol("_DATA");
        int board = data + 21;
        for (int offset = 0; offset < 288; offset++) memory.writeByte(data + offset, 0);
        for (int column = 0; column < 10; column++) {
            for (int row = 0; row < 8; row++) memory.writeByte(board + column * 8 + row,
                    column == 0 || column == 9 || row == 7 ? 0 : 9);
        }
        memory.writeByte(board + 4 * 8 + 3, 1);
        memory.writeByte(board + 6 * 8 + 6, 1);
        memory.writeByte(data + 200, 2);
        animationClock = true;
        try {
            call("_vexed_push", 4, 3, 5);
            require(memory.readByte(board + 4 * 8 + 3) == 9 && memory.readByte(board + 5 * 8 + 3) == 1,
                    "Vexed first push board mismatch");
            call("_vexed_gravity");
            require(memory.readByte(board + 5 * 8 + 3) == 9 && memory.readByte(board + 5 * 8 + 6) == 1,
                    "Vexed first move gravity mismatch");
            call("_vexed_remove");
            require(memory.readByte(board + 5 * 8 + 6) == 9 && memory.readByte(board + 6 * 8 + 6) == 9,
                    "Vexed first move removal mismatch");
            require(memory.readByte(data + 200) == 0, "Vexed first move block count");
            call("_vexed_check");
            require(registers.getL() == 0, "Vexed first move did not settle");
        } finally {
            animationClock = false;
        }
        System.out.println("Vexed first push, gravity and removal: PASS");
    }

    private void dynamateSelection() {
        int data = symbol("_DATA");
        int pointer = 0x6000;
        for (int direction = 0; direction < 4; direction++) {
            for (int cell = 0; cell < 256; cell++) memory.writeByte(data + 8 + cell, 16);
            memory.writeByte(pointer, 4);
            memory.writeByte(pointer + 1, 5);
            memory.writeByte(pointer + 2, 255);
            memory.writeByte(pointer + 3, 165);
            memory.writeByte(0x6f82, 16 | new int[] {1, 8, 2, 4}[direction]);
            call("_dynamate_getMove", pointer, 0, pointer + 1, 0, pointer + 2, 0, 0);
            require(registers.getL() == 1, "Dynamate selection action return");
            require(memory.readByte(pointer) == 4 && memory.readByte(pointer + 1) == 5,
                    "Dynamate selection reset coordinates to " + memory.readByte(pointer) + "," + memory.readByte(pointer + 1));
            require(memory.readByte(pointer + 2) == direction && memory.readByte(pointer + 3) == 165,
                    "Dynamate selection direction/guard mismatch");
        }
        memory.writeByte(0x6f82, 0);
        System.out.println("Dynamate occupied-cell selection and coordinate writeback: PASS");
        animationClock = true;
        int fixtures = 0;
        try {
            for (int column = 0; column < 16; column++) {
                for (int row = 0; row < 16; row++) {
                    for (int direction = 0; direction < 4; direction++) {
                        int joypad = new int[] {1, 8, 2, 4}[direction];
                        int wantedX = Math.max(0, Math.min(15, column + (direction == 1 ? 1 : direction == 3 ? -1 : 0)));
                        int wantedY = Math.max(0, Math.min(15, row + (direction == 2 ? 1 : direction == 0 ? -1 : 0)));
                        memory.writeByte(pointer, column);
                        memory.writeByte(pointer + 1, row);
                        memory.writeByte(pointer + 2, 255);
                        memory.setIoDevice(new Memory.IoDevice() {
                            private int reads;
                            public int read(int address) {
                                if (address != 0x6f82) return -1;
                                return reads++ == 0 ? joypad : joypad | 16;
                            }
                            public void write(int address, int value) {}
                        });
                        call("_dynamate_getMove", pointer, 0, pointer + 1, 0, pointer + 2, 0, 0);
                        require(registers.getL() == 1 && memory.readByte(pointer + 2) == direction,
                                "Dynamate move-then-select action/direction");
                        require(memory.readByte(pointer) == wantedX && memory.readByte(pointer + 1) == wantedY,
                                String.format("Dynamate getMove x=%d y=%d direction=%d actual=%d,%d expected=%d,%d",
                                        column, row, direction, memory.readByte(pointer), memory.readByte(pointer + 1), wantedX, wantedY));
                        require(memory.readByte(0x8802) == wantedX * 8 + 16 && memory.readByte(0x8803) == wantedY * 8 + 8,
                                "Dynamate move-then-select sprite mismatch");
                        fixtures++;
                    }
                }
            }
        } finally {
            animationClock = false;
            memory.setIoDevice(null);
            memory.writeByte(0x6f82, 0);
        }
        System.out.printf("Dynamate move-then-select input path: PASS (%d cases)%n", fixtures);
        for (int direction = 0; direction < 4; direction++) {
            for (int offset = 0; offset < 288; offset++) memory.writeByte(data + offset, 0);
            memory.writeByte(data + 8 + 5 * 16 + 4, 16);
            memory.writeByte(pointer, 4);
            memory.writeByte(pointer + 1, 5);
            memory.writeByte(pointer + 2, 255);
            memory.writeByte(0x6f82, 16 | new int[] {1, 8, 2, 4}[direction]);
            call("_dynamate_getMove", pointer, 0, pointer + 1, 0, pointer + 2, 0, 0);
            require(registers.getL() == 1 && memory.readByte(pointer + 2) == direction,
                "Dynamate engine move selection");
            call("_dm_init_step", memory.readByte(pointer), memory.readByte(pointer + 1), memory.readByte(pointer + 2), 0);
            call("_dm_step");
            require(registers.getL() == 0, "Dynamate engine stopped before first step");
            int destination = (5 + (direction == 2 ? 1 : direction == 0 ? -1 : 0)) * 16
                + 4 + (direction == 1 ? 1 : direction == 3 ? -1 : 0);
            for (int offset = 0; offset < 256; offset++) {
                require(memory.readByte(data + 8 + offset) == (offset == destination ? 16 : 0),
                        "Dynamate engine first-step board mismatch");
            }
        }
        memory.writeByte(0x6f82, 0);
        System.out.println("Dynamate selected block first engine step: PASS (4 directions)");
    }

    private void dynamate() {
        call("_dm_field");
        require(registers.getXHL() == symbol("_DATA") + 8, "Dynamate board pointer");
        for (int level = 0; level < 30; level++) {
            int source = symbol("_dml_levels") + level * 264 + 8;
            call("_dm_init_level", source & 65535, source >>> 16);
            for (int index = 0; index < 256; index++) {
                require(memory.readByte(symbol("_DATA") + 8 + index) == memory.readByte(source + index),
                        "Dynamate decoded board " + level + " cell " + index);
            }
            call("_dynamate_draw_level");
            for (int index = 0; index < 256; index++) {
                int piece = memory.readByte(source + index);
                int base = piece & 127;
                int number = switch (base) {
                    case 0 -> 0;
                    case 16 -> 1;
                    case 17 -> 2;
                    case 18 -> 3;
                    case 24 -> 15;
                    case 25 -> 4;
                    case 32 -> 7;
                    case 33 -> 6;
                    case 34 -> 8;
                    case 35 -> 9;
                    case 36 -> (piece & 128) != 0 ? 17 : 11;
                    case 37 -> 10;
                    case 38 -> 18;
                    case 48 -> (piece & 128) != 0 ? 5 : 14;
                    case 49 -> 16;
                    default -> throw new AssertionError("Unknown Dynamate piece " + piece);
                };
                int overlay = base == 36 ? 12 : (piece & 128) != 0 && piece != 176 && piece != 177 ? 13 : 0;
                int cell = (index / 16 + 1) * 32 + index % 16 + 2;
                require(memory.readWord(0x9800 + cell * 2) == paletteTile("_sprites_palidx", number, 0),
                        "Dynamate background level " + level + " cell " + index);
                require(memory.readWord(0x9000 + cell * 2) == paletteTile("_sprites_palidx", overlay, 0),
                        "Dynamate overlay level " + level + " cell " + index);
            }
        }
        System.out.println("Dynamate level loading and both tile planes: PASS (30 boards)");
    }

    public static void main(String[] args) throws Exception {
        require(args.length == 3, "Usage: VexedTest ROM MAP levels.h");
        VexedTest test = new VexedTest(args[0], args[1]);
        test.vexedMove();
        test.dynamateSelection();
        test.cursors();
        test.dynamateCursors();
        test.gemsMatches();
        test.gemsCascade();
        String header = Files.readString(Path.of(args[2]));
        List<String> packs = test.table(header, "vexed_packs", 20, 9);
        List<String> levels = test.table(header, "vexed_level_struct", 80, 538);
        test.table(header, "vexed_level_names", 20, 538);
        test.table(header, "vexed_level_solutions", 170, 538);
        test.text(packs);
        test.boards(levels);
        test.gems();
        test.dynamate();
        test.saves();
        System.out.println("VexedTest: PASS");
    }
}