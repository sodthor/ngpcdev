package sim.bomberman;

import sim.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Drive bomberman single-player in the sim: stub IO so it sees NO link cable
 * (USR_SHUTDOWN=0, link status=0, port2=0), inject VBL at 60Hz, and feed the
 * joypad so the title/menu advance. Report whether game()/moveChar() is reached.
 */
public final class BombermanDrive {
    // Key bits (HW_JOYPAD 0x6F82): UP=1 DOWN=2 LEFT=4 RIGHT=8 A=0x10 B=0x20 OPTION=0x40
    static final int J_A = 0x10, J_B = 0x20, J_OPTION = 0x40;

    public static void main(String[] args) throws Exception {
        String romPath = args.length > 0 ? args[0] : "../bomberman/BOMBERMAN.ngp";
        long maxInstr = args.length > 1 ? Long.parseLong(args[1]) : 30_000_000L;
        String mapPath = args.length > 2 ? args[2] : romPath.replaceFirst("(?i)\\.ngp$", ".map");
        Map<String, Integer> symbols = loadMap(mapPath);
        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory mem = new Memory(rom.data);
        Registers reg = new Registers();
        Cpu cpu = new Cpu(mem, reg);

        final long[] instr = {0};
        final int[] joypad = {0};
        mem.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int a) {
                if (a == 0x6F82) return joypad[0];   // joypad
                if (a == 0x6F85) return 0;            // USR_SHUTDOWN: never shut down
                if (a == 0x6F91) return 0;            // link/serial status: no link
                if (a == 0x6F87) return 1;
                if (a == 0x00BC) return 0;
                if (a == 0x70DE) return (int)(instr[0] & 0xFF); // free-running counter (timer delay loops)
                return -1;
            }
            @Override public void write(int a, int v) {}
        });
        cpu.setSwiHandler((n, r, m) -> true);
        NgpLoader.init(rom, mem, reg);

        final int GAME = symbol(symbols, "_game"), MOVECHAR = symbol(symbols, "_moveChar"),
            DUELGAME = symbol(symbols, "_duelGame");
        final int ROM_BASE = 0x200000, ROM_END = ROM_BASE + rom.data.length;
        final long cyclesPerVbl = NgpcTiming.CYCLES_PER_FRAME;
        long nextVbl = cyclesPerVbl, prevCyc = 0, cyc = 0, vbl = 0;
        boolean reachedGame = false, reachedMove = false, reachedDuel = false;
        // Track how long PC stalls in a tight range (stuck-loop detector).
        int stuckPc = -1; long stuckSince = 0;
        java.util.HashMap<Integer,Long> hist = new java.util.HashMap<>();
        long ckMenu=0;
        final int MENU = symbol(symbols, "_menu");
        boolean inIsr=false; int isrSp=0;

        while (instr[0] < maxInstr) {
            // Clear the ISR-busy flag once the injected handler has returned
            // (RETI pops back, so XSP rises above where we injected).
            if (inIsr && reg.getXSP() > isrSp) inIsr = false;
            if (cyc >= nextVbl) {
                nextVbl += cyclesPerVbl;
                int h = mem.readDword(0x6FCC) & 0xFFFFFF;
                if (h != 0 && !inIsr) {
                    int xsp = reg.getXSP();
                    xsp -= 2; mem.writeWord(xsp, reg.getSR());
                    xsp -= 4; mem.writeDword(xsp, reg.getPC());
                    mem.trackStackAccess(xsp);
                    reg.setXSP(xsp); reg.setPC(h);
                    inIsr = true; isrSp = xsp;   // block re-entry until RETI
                }
                vbl++;
                // Hold A pressed on odd frames, release on even (auto-fire through menus).
                joypad[0] = (vbl % 12 < 4) ? J_A : 0;  // clean 4-on/8-off press-release A cadence
            }
            int pc = reg.getPC();
            if (pc == GAME && !reachedGame)   { reachedGame = true; System.out.printf(">>> REACHED game() at instr %d vbl %d%n", instr[0], vbl); }
            if (pc == MOVECHAR && !reachedMove){ reachedMove = true; System.out.printf(">>> REACHED moveChar() at instr %d vbl %d%n", instr[0], vbl); }
            if (pc == DUELGAME && !reachedDuel){ reachedDuel = true; System.out.printf(">>> reached duelGame() at instr %d vbl %d%n", instr[0], vbl); }

            if ((instr[0] & 0x3F) == 0) hist.merge(pc & 0xFFFF00, 1L, Long::sum);
            cpu.step();
            instr[0]++;
            long nc = cpu.getCycles(); cyc += nc - prevCyc; prevCyc = nc;

            int npc = reg.getPC();
            if (npc==MENU) ckMenu++;
            if (npc < ROM_BASE || npc >= ROM_END) {
                throw new AssertionError(String.format("LEFT ROM at instr %d PC=%06X (from %06X)",
                    instr[0], npc, pc));
            }
            // Stuck detection: PC oscillating within a 64-byte window for >2M instr.
            if (stuckPc < 0 || Math.abs(npc - stuckPc) > 64) { stuckPc = npc; stuckSince = instr[0]; }
            else if (instr[0] - stuckSince > 2_000_000L) {
                throw new AssertionError(String.format("STUCK near PC=%06X for %d instr (IZ=%08X)",
                    npc, instr[0]-stuckSince, reg.getXIZ()));
            }
        }
        System.out.printf("Ran %d instr, %d VBL. reachedGame=%b reachedMove=%b reachedDuel=%b%n",
            instr[0], vbl, reachedGame, reachedMove, reachedDuel);
        hist.entrySet().stream().sorted((a,b)->Long.compare(b.getValue(),a.getValue())).limit(12)
            .forEach(e->System.out.printf("  hot %06X..: %d%n", e.getKey(), e.getValue()));
        System.out.printf("checkpoints: menu=%d%n", ckMenu);
        System.out.printf("final PC=%06X IZ=%08X (VBCounter=%d)%n",
            reg.getPC(), reg.getXIZ(), mem.readByte(symbol(symbols, "_VBCounter"))&0xFF);
        // VRAM signature for differential testing (fold-on vs fold-off builds).
        long sMap=0, sTile=0, sPal=0, nz=0;
        for (int a=0x9000; a<0x9800; a++) { int v=mem.readByte(a)&0xFF; sMap = sMap*31 + v; if(v!=0) nz++; }
        for (int a=0xA000; a<0xC000; a++) { int v=mem.readByte(a)&0xFF; sTile= sTile*31 + v; if(v!=0) nz++; }
        for (int a=0x8200; a<0x8300; a++) { int v=mem.readByte(a)&0xFF; sPal = sPal*31 + v; if(v!=0) nz++; }
        System.out.printf("VRAM sig: map=%016X tile=%016X pal=%016X nonzero=%d%n", sMap, sTile, sPal, nz);
        // Palette dump (0x8200..0x821F) — a black screen usually = all-zero / all-black palette.
        StringBuilder pal = new StringBuilder("PAL 8200: ");
        for (int a=0x8200; a<0x8220; a++) pal.append(String.format("%02X ", mem.readByte(a)&0xFF));
        System.out.println(pal);
        if (!reachedGame || !reachedMove || reachedDuel || cpu.isHalted())
            throw new AssertionError("Single-player startup/gameplay checkpoints not satisfied");
    }

    static Map<String, Integer> loadMap(String path) throws Exception {
        Map<String, Integer> symbols = new HashMap<>();
        String pending = null;
        for (String line : Files.readAllLines(Path.of(path))) {
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
        return symbols;
    }

    static int symbol(Map<String, Integer> symbols, String name) {
        Integer address = symbols.get(name);
        if (address != null) return address;
        for (Map.Entry<String, Integer> entry : symbols.entrySet()) {
            if (!entry.getKey().equalsIgnoreCase(name)) continue;
            if (address != null && !address.equals(entry.getValue()))
                throw new IllegalArgumentException("Ambiguous map symbol " + name);
            address = entry.getValue();
        }
        if (address == null) throw new IllegalArgumentException("Missing map symbol " + name);
        return address;
    }
}
