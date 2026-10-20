package sim.bomberman;

import java.security.MessageDigest;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.Map;
import sim.*;

/** Runs the real cc900 ROM, with independent game-state and VRAM checks. */
public final class BombermanCc900Test {
    private static int checks;
    private static int calls;

    private static final class Machine {
        final NgpLoader.NgpRom rom;
        final Memory mem;
        final Registers reg = new Registers();
        final Cpu cpu;
        final Map<String, Integer> symbols;
        final BitSet executed = new BitSet();
        int joypad;
        long instructions;

        Machine(String romPath, String mapPath, boolean raster) throws Exception {
            symbols = BombermanDrive.loadMap(mapPath);
            rom = NgpLoader.load(romPath);
            mem = new Memory(rom.data);
            cpu = new Cpu(mem, reg);
            mem.setIoDevice(new Memory.IoDevice() {
                public int read(int address) {
                    return switch (address) {
                        case 0x6f82 -> joypad;
                        case 0x6f85, 0x6f91, 0xbc -> 0;
                        case 0x6f87 -> 1;
                        case 0x70de -> (int) instructions & 255;
                        case 0x8009 -> raster ? (int) ((cpu.getCycles() / 512 + 152) % 199) : 0;
                        default -> -1;
                    };
                }
                public void write(int address, int value) {}
            });
            cpu.setSwiHandler((n, r, m) -> {
                require(n == 1, "unexpected BIOS SWI " + n);
                return true;
            });
            NgpLoader.init(rom, mem, reg);
            if (raster) require(mem.readByte(0x8009) == 152, "raster I/O device must supply RASV reads");
            mem.setDataEnd(symbols.values().stream().filter(a -> a >= 0x4000 && a < 0x6c00)
                .mapToInt(Integer::intValue).max().orElseThrow() + 3);
        }

        int address(String name) { return BombermanDrive.symbol(symbols, "_" + name); }
        int get(String name) { return mem.readByte(address(name)); }
        void set(String name, int value) { mem.writeByte(address(name), value); }
        int cell(String name, int x, int y) { return address(name) + x * 15 + y; }
        int tile(int x, int y) { return mem.readByte(cell("curmap", x, y)); }

        void step() {
            int pc = reg.getPC();
            if (pc < NgpLoader.ROM_BASE || pc >= NgpLoader.ROM_BASE + rom.data.length)
                throw new AssertionError(String.format("PC left ROM: %06X", pc));
            require(!cpu.isHalted(), "CPU halted");
            executed.set(pc - NgpLoader.ROM_BASE);
            cpu.step();
            instructions++;
        }

        int call(String name, int... arguments) {
            int sp = reg.getXSP();
            for (int i = arguments.length - 1; i >= 0; i--) {
                reg.setXSP(reg.getXSP() - 2);
                mem.writeWord(reg.getXSP(), arguments[i]);
            }
            reg.setXSP(reg.getXSP() - 4);
            mem.writeDword(reg.getXSP(), 0x1ffffc);
            reg.setPC(address(name));
            long limit = instructions + 2_000_000;
            while (reg.getPC() != 0x1ffffc && instructions < limit) step();
            require(reg.getPC() == 0x1ffffc, name + " did not return");
            require(reg.getXSP() == sp - arguments.length * 2, name + " unbalanced stack");
            reg.setXSP(sp);
            calls++;
            return reg.getHL();
        }

        void level(int level) {
            set("lvl", level);
            set("boss", (level + 1) % 3 == 0 ? 1 : 0);
            call("initChar");
            call("initMap");
        }
    }

    public static void main(String[] args) throws Exception {
        String rom = args.length > 0 ? args[0] : "../bomberman/BOMBERMAN_CC900.ngp";
        String map = args.length > 1 ? args[1] : "../bomberman/BOMBERMAN_CC900.map";
        Machine fixture = new Machine(rom, map, false);
        require(fixture.address("setSprite") != fixture.address("SetSprite"),
            "case-sensitive cc900 symbols must remain distinct");
        System.out.println("ROM SHA256: " + HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(fixture.rom.data)));
        graphics(fixture);
        movementAndItems(fixture);
        bombs(fixture);
        random(fixture);
        enemies(fixture);
        naturalGame(rom, map, false);
        naturalGame(rom, map, true);
        System.out.printf("BombermanCc900Test: PASS (%d assertions, %d real ROM calls)%n", checks, calls);
    }

    private static void graphics(Machine m) {
        for (int level = 0; level < 12; level++) {
            m.level(level);
            m.call("loadGfx");
            m.call("loadPals");
            int decor = m.address("decors") + level * 34;
            int perso = m.address("PERSO_IMG");
            int enemy = m.address("enmy_img") + level * 34;
            int common = m.address("COMMON_IMG");
            int destination = 0xa000;
            for (int descriptor : new int[] {decor, perso}) {
                int words = m.mem.readWord(descriptor + 4) / 2 * 8;
                destination = words(m, destination, m.mem.readDword(descriptor + 6), words);
                destination = words(m, destination, m.mem.readDword(descriptor + 10), words);
            }
            destination = words(m, destination, m.mem.readDword(enemy + 6),
                m.mem.readWord(enemy + 4) * 8);
            destination = words(m, destination, m.mem.readDword(common + 6),
                m.mem.readWord(common + 4) * 8);
            require(destination <= 0xc000, "tile graphics exceed VRAM");
            words(m, 0x8280, m.mem.readDword(decor + 16), m.mem.readByte(decor + 14) * 4);
            words(m, 0x8300, m.mem.readDword(decor + 22), m.mem.readByte(decor + 20) * 4);
            int palette = words(m, 0x8200, m.mem.readDword(perso + 16),
                m.mem.readByte(perso + 14) * 4);
            palette = words(m, palette, m.mem.readDword(perso + 22), m.mem.readByte(perso + 20) * 4);
            if (m.get("boss") != 0) {
                for (int offset : new int[] {14, 20}) {
                    int source = m.mem.readDword(perso + offset + 2);
                    for (int i = 0; i < m.mem.readByte(perso + offset) * 4; i++, palette += 2) {
                        int c = m.mem.readWord(source + i * 2);
                        int average = ((c & 15) + ((c >> 4) & 15) + ((c >> 8) & 15)) / 3;
                        require(m.mem.readWord(palette) == average * (level < 6 ? 0x111 : 0x11),
                            "boss palette arithmetic");
                    }
                }
            } else {
                palette = words(m, palette, m.mem.readDword(enemy + 16), m.mem.readByte(enemy + 14) * 4);
                words(m, palette, m.mem.readDword(common + 16), m.mem.readByte(common + 14) * 4);
            }
            for (int x = 0; x < 15; x++)
                for (int y = 0; y < 15; y++) {
                    require(m.tile(x, y) == m.mem.readByte(m.address("maps") + level * 225 + y * 15 + x),
                        "map initialization/transposition");
                    require(m.mem.readByte(m.cell("curbombs", x, y)) == 64
                        && m.mem.readByte(m.cell("curflag", x, y)) == 0
                        && m.mem.readByte(m.cell("curmark", x, y)) == 0, "map metadata initialization");
                }
            for (int position : new int[] {16, 96, 160, 208}) {
                m.mem.writeByte(m.address("chars") + 2, position);
                m.mem.writeByte(m.address("chars") + 3, position);
                for (int i = 0; i < 225; i++) {
                    m.mem.writeByte(m.address("curmod") + i, 1);
                    m.mem.writeByte(m.address("curflag") + i, i % 4);
                }
                m.call("showBoard");
                board(m, decor, position);
            }
        }
        System.out.println("Graphics: PASS (12 levels, graphics/palettes, maps, scrolling and flips)");
    }

    private static int words(Machine m, int destination, int source, int count) {
        for (int i = 0; i < count; i++)
            require(m.mem.readWord(destination + i * 2) == m.mem.readWord(source + i * 2),
                String.format("graphics/palette mismatch at %06X from %06X", destination + i * 2, source + i * 2));
        return destination + count * 2;
    }

    private static void board(Machine m, int decor, int position) {
        int sx = Math.max(0, Math.min(80, position - 80));
        int sy = position <= 80 ? 0 : position >= 168 ? 88 : position - 80;
        require(m.get("scr_x") == sx && m.get("scr_y") == sy, "camera position");
        require(m.mem.readByte(0x8032) == sx && m.mem.readByte(0x8033) == sy
            && m.mem.readByte(0x8034) == sx && m.mem.readByte(0x8035) == sy, "hardware scroll registers");
        int width = m.mem.readWord(decor);
        for (int x = sx / 16; x < Math.min(15, sx / 16 + 12); x++)
            for (int y = sy / 16; y < Math.min(15, sy / 16 + 12); y++) {
                int k = m.tile(x, y) * 2;
                int base = k / width * width * 2 + k % width;
                int flag = m.mem.readByte(m.cell("curflag", x, y));
                int flip = flag == 1 ? 0x8000 : flag == 3 ? 0x4000 : 0;
                for (int dy = 0; dy < 2; dy++)
                    for (int dx = 0; dx < 2; dx++) {
                        int tile = base + (flag == 3 ? 1 - dy : dy) * width + (flag == 1 ? 1 - dx : dx);
                        int offset = ((y * 2 + dy) * 32 + x * 2 + dx) * 2;
                        for (int plane = 0; plane < 2; plane++) {
                            int palette = m.mem.readByte(m.mem.readDword(decor + 26 + plane * 4) + tile);
                            require(m.mem.readWord(0x9000 + plane * 0x800 + offset)
                                == tile + plane * 128 + palette * 512 + flip, "board tile content");
                        }
                    }
                require(m.mem.readByte(m.cell("curmod", x, y)) == 0, "dirty tile consumed");
            }
    }

    private static void movementAndItems(Machine m) {
        int character = m.address("chars");
        for (int direction : new int[] {1, 2, 4, 8}) {
            m.level(0);
            m.joypad = direction;
            for (int i = 0; i < 16; i++) m.call("moveChar");
            require(m.mem.readByte(character + 2) == (direction == 8 ? 32 : 16)
                && m.mem.readByte(character + 3) == (direction == 2 ? 32 : 16), "movement/wall collision");
        }
        m.joypad = 0;
        for (int item : new int[] {12, 13, 14, 15, 16, 31}) {
            m.level(0);
            m.mem.writeWord(m.address("score"), 0);
            m.mem.writeByte(m.cell("curmap", 1, 1), item);
            m.call("checkItem", 0, 1, 1, 0);
            int expected = switch (item) {
                case 12, 13 -> 2; case 14 -> 512; case 15, 16 -> 255; default -> 253;
            };
            int offset = switch (item) {
                case 12 -> 6; case 13 -> 8; case 14 -> 4; case 15 -> 14; default -> 12;
            };
            int actual = item == 14 ? m.mem.readWord(character + offset) : m.mem.readByte(character + offset);
            require(actual == expected, "item/death/exit behavior " + item);
            if (item < 16) require(m.tile(1, 1) == 11 && m.mem.readWord(m.address("score")) == 5,
                "item consumption and score");
        }
        System.out.println("Gameplay fixtures: PASS (four directions, collision, items, death and exit)");
    }

    private static void bombs(Machine m) {
        m.level(0);
        m.call("addBomb", 0, 2, 1, 1, 1);
        require(m.get("nb_bombs") == 1 && m.tile(1, 1) == 8
            && m.mem.readByte(m.address("chars") + 7) == 1, "bomb placement and ownership");
        for (int i = 0; i < 8; i++) m.call("mngBombs");
        require(m.tile(1, 1) == 9 && m.mem.readByte(m.address("bombs") + 1) == 1, "bomb countdown");
        for (int i = 0; i < 8; i++) m.call("mngBombs");
        require(m.get("nb_bombs") == 0 && m.tile(1, 1) == 16 && m.tile(2, 1) == 18
            && m.tile(1, 2) == 26 && m.mem.readByte(m.address("chars") + 7) == 0,
            "bomb explosion and removal");
        m.level(0);
        m.call("addBomb", 0, 1, 1, 1, 2);
        m.call("addBomb", 255, 20, 2, 1, 2);
        for (int i = 0; i < 8; i++) m.call("mngBombs");
        require(m.get("nb_bombs") == 0 && m.tile(4, 1) == 3 && m.tile(3, 1) == 17
            && m.mem.readByte(m.cell("curbombs", 1, 1)) == 64
            && m.mem.readByte(m.cell("curbombs", 2, 1)) == 64, "chain reaction and destroyed brick");
        m.level(2);
        openArena(m);
        m.call("addBomb", 255, 1, 7, 7, 3);
        for (int x = 1; x < 14; x++)
            for (int y = 1; y < 14; y++) {
                int expected = x == 7 && y == 7 ? 4
                    : (x == 7 && Math.abs(y - 7) <= 3) || (y == 7 && Math.abs(x - 7) <= 3) ? 1 : 0;
                require(m.mem.readByte(m.cell("curmark", x, y)) == expected, "boss danger marking");
            }
        for (int i = 0; i < 8; i++) m.call("mngBombs");
        for (int i = 0; i < 225; i++)
            require(m.mem.readByte(m.address("curmark") + i) == 0, "signed danger unmarking");
        System.out.println("Bombs: PASS (countdown, explosion, chain reaction, brick destruction, boss marks)");
    }

    private static void naturalGame(String rom, String map, boolean raster) throws Exception {
        Machine m = new Machine(rom, map, raster);
        String[] names = {"showIntro", "showTitle", "menu", "game", "moveChar", "moveEnemy", "mngBombs", "explode",
            raster ? "hc_showEmu" : "hc_showHW", raster ? "mp_play_emu" : "mp_play_hw"};
        int[] addresses = new int[names.length], hits = new int[names.length];
        for (int i = 0; i < names.length; i++) addresses[i] = m.address(names[i]);
        long nextVbl = NgpcTiming.CYCLES_PER_FRAME, frames = 0, firstGame = -1;
        int savedSp = 0, savedPc = 0, savedSr = 0;
        int[] savedRegisters = null;
        boolean inIsr = false, moved = false, paused = false, resumed = false;
        int player = m.address("chars");
        while (frames < 1000 && m.instructions < 30_000_000) {
            if (inIsr && m.reg.getXSP() > savedSp - 6) {
                require(m.reg.getXSP() == savedSp && m.reg.getPC() == savedPc && m.reg.getSR() == savedSr,
                    "VBL must restore PC, SP and SR");
                for (int i = 0; i < 7; i++)
                    require(m.reg.getDword32(i) == savedRegisters[i], "VBL register preservation " + i);
                inIsr = false;
            }
            if (m.cpu.getCycles() >= nextVbl) {
                nextVbl += NgpcTiming.CYCLES_PER_FRAME;
                frames++;
                if (firstGame < 0) m.joypad = frames % 12 < 4 ? 16 : 0;
                else {
                    long age = frames - firstGame;
                    m.joypad = age < 12 ? 8 : age < 24 ? 4 : age < 36 ? 2 : age < 48 ? 1
                        : age < 52 || (age >= 60 && age < 64) ? 32 : age >= 80 && age < 96 ? 16 : 0;
                    if (age > 48 && m.mem.readWord(m.address("paused")) != 0) paused = true;
                    if (paused && age > 64 && m.mem.readWord(m.address("paused")) == 0) resumed = true;
                    moved |= m.mem.readByte(player + 2) != 16 || m.mem.readByte(player + 3) != 16;
                }
                int handler = m.mem.readDword(0x6fcc) & 0xffffff;
                if (!inIsr && handler != 0) {
                    savedSp = m.reg.getXSP(); savedPc = m.reg.getPC(); savedSr = m.reg.getSR();
                    savedRegisters = new int[7];
                    for (int i = 0; i < 7; i++) savedRegisters[i] = m.reg.getDword32(i);
                    m.mem.writeWord(savedSp - 2, savedSr);
                    m.mem.writeDword(savedSp - 6, savedPc);
                    m.mem.trackStackAccess(savedSp - 6);
                    m.reg.setXSP(savedSp - 6); m.reg.setPC(handler);
                    inIsr = true;
                }
            }
            for (int i = 0; i < addresses.length; i++)
                if (m.reg.getPC() == addresses[i]) {
                    hits[i]++;
                    if (i == 4 && firstGame < 0) firstGame = frames;
                }
            m.step();
        }
        require(frames == 1000, "natural run exhausted its instruction limit");
        for (int i = 0; i < names.length; i++) require(hits[i] > 0, "did not execute " + names[i]);
        require(moved && paused && resumed, "movement and pause/resume must occur");
        require(m.get("lives") < 3, "player death did not consume a life");
        require(m.get("in_emu") == (raster ? 1 : 0), "high-color mode detection");
        System.out.printf("Natural game (%s): PASS (%d instructions, %d frames, %d unique PCs, %d explosions)%n",
            raster ? "raster" : "zero RASV", m.instructions, frames, m.executed.cardinality(), hits[7]);
        System.out.println(m.mem.getRamReport());
    }

    private static void random(Machine m) {
        int seed = 0x900;
        m.call("SeedRandom", seed);
        seed = seed * 20077 + 12345;
        for (int i = 0; i < 1000; i++) {
            int bound = new int[] {1, 16, 31, 100, 32766}[i % 5];
            seed = seed * 20077 + 12345;
            int expected = ((seed >>> 16) & 32767) * bound >>> 15;
            int actual = m.call("GetRandom", bound);
            require(actual == expected && m.mem.readDword(m.address("RAND_RandomData")) == seed,
                "random/multiply helper: iteration=" + i + " expected=" + expected + " actual=" + actual);
        }
        System.out.println("Random: PASS (1000 real multiply/RNG helper executions)");
    }

    private static void enemies(Machine m) {
        boolean flying = false, bossBomb = false;
        m.call("SeedRandom", 0x900);
        for (int scenario = 0; scenario < 13; scenario++) {
            m.level(scenario == 12 ? 2 : scenario);
            if (scenario == 12) {
                openArena(m);
                m.mem.writeByte(m.address("chars") + 18 + 2, 112);
                m.mem.writeByte(m.address("chars") + 18 + 3, 112);
            }
            for (int tick = 0; tick < 160; tick++) {
                m.call("updateBoard");
                m.call("moveEnemy");
                m.call("mngBombs");
                require(m.get("nb_bombs") <= 64, "enemy bomb capacity");
                for (int i = 1; i < 8; i++) {
                    int entity = m.address("chars") + i * 18;
                    if (m.mem.readByte(entity + 12) == 0) continue;
                    flying |= m.mem.readByte(entity + 16) != 0;
                    if (m.mem.readByte(entity + 12) == 1) {
                        int x = m.mem.readByte(entity + 2), y = m.mem.readByte(entity + 3);
                        require(x >= 16 && x <= 208 && y >= 16 && y <= 208, "enemy arena bounds");
                    }
                    if (m.get("boss") != 0) m.call("setMainSprite", 12, i, 0);
                    else {
                        int sprites = m.call("setSprite", 12, i) & 255;
                        require(sprites == 0 || sprites == 4 || sprites == 6,
                            "enemy sprite count: scenario=" + scenario + " tick=" + tick
                                + " entity=" + i + " result=" + sprites);
                    }
                }
                for (int i = 0; i < m.get("nb_bombs"); i++) {
                    int bomb = m.address("bombs") + i * 7;
                    int x = m.mem.readByte(bomb + 4), y = m.mem.readByte(bomb + 5);
                    require(x > 0 && x < 14 && y > 0 && y < 14, "enemy bomb coordinates");
                    require(m.mem.readByte(m.cell("curbombs", x, y)) == i, "compacted bomb index");
                    bossBomb |= m.get("boss") != 0 && m.mem.readByte(bomb) > 0 && m.mem.readByte(bomb) < 8;
                }
            }
        }
        require(flying && bossBomb,
            "must exercise flying enemies and boss bomb AI: flying=" + flying + " bossBomb=" + bossBomb);
        System.out.println("Enemies: PASS (12 levels, flying enemies, boss AI, sprites and bomb indices)");
    }

    private static void openArena(Machine m) {
        for (int x = 0; x < 15; x++)
            for (int y = 0; y < 15; y++)
                m.mem.writeByte(m.cell("curmap", x, y), x == 0 || x == 14 || y == 0 || y == 14 ? 0 : 11);
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
