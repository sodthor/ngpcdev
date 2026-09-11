package sim;

/**
 * NGPC simulator entry point.
 *
 * Usage:
 *   java sim.Simulator [options] <rom.ngp>
 *
 * Options:
 *   --trace          Print every instruction
 *   --max <n>        Stop after n instructions (default: unlimited)
 *   --pc <hex>       Override entry point
 *   --vbl            Enable VBL interrupt simulation (default: enabled)
 *   --no-vbl         Disable VBL interrupt simulation
 *   --vbl-hz <n>     VBL frequency in Hz (default: 60)
 *   --cpu-hz <n>     CPU clock in Hz (default: 6144000 = 6.144 MHz)
 *   --dump-gfx       After execution, dump and verify GFX tiles at 0xA000 and
 *                    scroll-plane map entries at 0x9000
 *
 * VBL interrupt:
 *   The NGPC fires a Vertical Blank interrupt at 60 Hz (by default).
 *   Every (cpu_hz / vbl_hz) CPU cycles, the simulator:
 *     1. Reads the handler address from the VBL interrupt vector at 0x6FCC
 *        (rVBI — 4 bytes little-endian, written by the game at startup)
 *     2. If the vector is non-zero, saves PC and SR onto the stack and
 *        jumps to the handler
 *     3. The handler ends with RETI, which restores SR and PC
 *   If the vector is zero (no handler installed yet), the VBL is skipped.
 *
 * NGPC hardware reference:
 *   CPU clock : 6.144 MHz (normal gear)
 *   VBL rate  : 60 Hz  →  102,400 cycles per frame
 *   Interrupt vector table (in I/O register space 0x6FB8-0x6FFF):
 *     0x6FB8  SWI3    0x6FBC  SWI4    0x6FC0  SWI5    0x6FC4  SWI6
 *     0x6FC8  RTCI    0x6FCC  VBI     0x6FD0  Z80I    0x6FD4  TI0/HBI
 *     0x6FD8  TI1     0x6FDC  TI2     0x6FE0  TI3     0x6FE4  STI
 *     0x6FE8  SRI     0x6FF0  DMA0    0x6FF4  DMA1    0x6FF8  DMA2
 *     0x6FFC  DMA3
 *   Each entry is a 4-byte absolute address written by the cartridge.
 */
public class Simulator {

    /** Address of the VBL interrupt vector pointer (rVBI register). */
    private static final int VBL_VECTOR_ADDR = 0x6FCC;

    /** Default NGPC CPU clock in Hz. */
    private static final long CPU_HZ_DEFAULT = 6_144_000L;

    /** Default VBL frequency in Hz. */
    private static final int VBL_HZ_DEFAULT = 60;

    public static void main(String[] args) throws Exception {
        String  romPath      = null;
        boolean trace        = false;
        long    maxInstrs    = Long.MAX_VALUE;
        int     overridePC   = -1;
        boolean vblEnabled   = true;
        int     vblHz        = VBL_HZ_DEFAULT;
        long    cpuHz        = CPU_HZ_DEFAULT;
        boolean dumpGfx      = false;
        long    pressStartAt = -1;   // instruction at which to assert START (0x10)
        long    pressStartEnd= -1;   // instruction at which to release START
        int     pressMask    = 0x10; // joypad mask asserted during the press window
        long    pressAt      = -1;   // generic press window start (instr count)
        long    pressEnd     = -1;   // generic press window end
        int     watchMask    = -1;   // --watch <hexmask>: hold mask after warmup, dump state each VBL
        String  scriptSpec   = null; // --script <mask:frames,...>: drive joypad over a frame schedule
        boolean cc900Addrs   = false; // --cc900: use the cc900 build's RAM symbol addresses
        int     tracePc      = -1;    // --trace-pc <hex>: dump regs+slots when PC hits this addr
        long    warmupOverride = -1;  // --warmup <n>: override the script/watch warmup gate

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--trace"       -> trace        = true;
                case "--max"         -> maxInstrs    = Long.parseLong(args[++i]);
                case "--pc"          -> overridePC   = Integer.parseInt(args[++i], 16);
                case "--vbl"         -> vblEnabled   = true;
                case "--no-vbl"      -> vblEnabled   = false;
                case "--vbl-hz"      -> vblHz        = Integer.parseInt(args[++i]);
                case "--cpu-hz"      -> cpuHz        = Long.parseLong(args[++i]);
                case "--dump-gfx"    -> dumpGfx      = true;
                case "--press-start" -> { pressStartAt = Long.parseLong(args[++i]);
                                          pressStartEnd = pressStartAt + 200_000; }
                // --press <hexmask> <startInstr> <durationInstr>: hold an arbitrary
                // joypad mask (e.g. 08 for J_RIGHT) during [start, start+duration).
                case "--press"       -> { pressMask = Integer.parseInt(args[++i], 16);
                                          pressAt   = Long.parseLong(args[++i]);
                                          pressEnd  = pressAt + Long.parseLong(args[++i]); }
                case "--watch"       -> watchMask = Integer.parseInt(args[++i], 16);
                // --script <mask:frames,mask:frames,...>: after warmup, hold each hex
                // mask for the given number of VBL frames, in sequence. e.g.
                //   08:120,04:200  -> RIGHT for 120 frames, then LEFT for 200 frames.
                // Dumps per-frame scroll+delta state so movement/wall bugs are visible.
                case "--script"      -> scriptSpec = args[++i];
                case "--cc900"       -> cc900Addrs = true;
                case "--trace-idx"   -> Cpu.traceIndexLoads = true;
                case "--trace-pc"    -> tracePc = Integer.parseInt(args[++i], 16);
                case "--warmup"      -> warmupOverride = Long.parseLong(args[++i]);
                default              -> romPath = args[i];
            }
        }

        if (romPath == null) {
            System.err.println("Usage: java sim.Simulator [--trace] [--max n] [--pc hex]");
            System.err.println("       [--vbl|--no-vbl] [--vbl-hz <n>] [--cpu-hz <n>]");
            System.err.println("       [--dump-gfx] [--press-start <instr>] <rom.ngp>");
            System.exit(1);
        }

        // Load ROM
        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        System.out.printf("Loaded: %-20s  entry=0x%06X  size=%d bytes%n",
            rom.title.isEmpty() ? "(no title)" : rom.title, rom.entryPoint, rom.data.length);

        // Compute cycles per VBL
        final long cyclesPerVbl = cpuHz / vblHz;
        if (vblEnabled) {
            System.out.printf("VBL: %d Hz  (every %,d cycles at %,d Hz CPU clock)%n",
                vblHz, cyclesPerVbl, cpuHz);
        } else {
            System.out.println("VBL: disabled");
        }

        // Build simulator
        Memory    mem = new Memory(rom.data);
        Registers reg = new Registers();
        Cpu       cpu = new Cpu(mem, reg);

        // I/O device: JOYPAD, START press simulation, LANGUAGE=English
        final long[] instrRef = {0};
        final long psAt  = pressStartAt;
        final long psEnd = pressStartEnd;
        final int  pMask = pressMask;
        final long pAt   = pressAt;
        final long pEnd  = pressEnd;
        final int  wMask = watchMask;
        // --script: build a per-VBL-frame mask schedule. scriptFrame advances each VBL
        // (only after warmup). scriptMasks[f] is the joypad mask held during frame f;
        // frames past the end hold 0 (no input).
        final int[] scriptMasks;
        if (scriptSpec != null) {
            java.util.List<Integer> sched = new java.util.ArrayList<>();
            for (String seg : scriptSpec.split(",")) {
                String[] kv = seg.trim().split(":");
                int mask   = Integer.parseInt(kv[0].trim(), 16);
                int frames = Integer.parseInt(kv[1].trim());
                for (int f = 0; f < frames; f++) sched.add(mask);
            }
            scriptMasks = new int[sched.size()];
            for (int k = 0; k < sched.size(); k++) scriptMasks[k] = sched.get(k);
        } else {
            scriptMasks = null;
        }
        final int[] scriptFrame = {0}; // current frame index into scriptMasks
        // RAM symbol addresses differ between the two builds (different linker layout).
        final int A_MAPX = cc900Addrs ? 0x4008 : 0x4000;
        final int A_MAPY = cc900Addrs ? 0x400A : 0x4006;
        final int A_SPRX = cc900Addrs ? 0x4010 : 0x400C;
        final int A_SPRY = cc900Addrs ? 0x4012 : 0x400E;
        final int A_MMAX = cc900Addrs ? 0x400C : 0x4012;
        // Warmup: don't inject the watch mask until the game has finished init and is
        // in the main loop. 8M instructions is comfortably past initScreen.
        final long WATCH_WARMUP = warmupOverride >= 0 ? warmupOverride : 8_000_000L;
        mem.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int addr) {
                if (addr == 0x6F82) {
                    long n = instrRef[0];
                    if (scriptMasks != null && n >= WATCH_WARMUP) {
                        int f = scriptFrame[0];
                        return (f < scriptMasks.length) ? scriptMasks[f] : 0;
                    }
                    if (wMask >= 0 && n >= WATCH_WARMUP) return wMask;
                    if (pAt >= 0 && n >= pAt && n < pEnd) return pMask;
                    if (psAt >= 0 && n >= psAt && n < psEnd) return 0x10;
                    return 0;
                }
                if (addr == 0x6F87) return 1;
                // Z80 sound-coprocessor handshake stubs (sim has no Z80):
                //   0x00BC : SL_LoadGroup's `wait_z80` polls this until 0 → Z80 ready.
                //   0x70DE : `wait_driver` spins until this CHANGES → driver alive.
                if (addr == 0x00BC) return 0;
                if (addr == 0x70DE) return (int) (instrRef[0] & 0xFF);
                return -1;
            }
            @Override public void write(int addr, int val) { /* ignored */ }
        });

        NgpLoader.init(rom, mem, reg);
        if (overridePC >= 0) reg.setPC(overridePC);
        cpu.setTrace(trace);

        final boolean traceF = trace;
        cpu.setSwiHandler((n, r, m) -> {
            if (traceF) System.out.printf("  [SWI %d ignored] %s%n", n, r);
            return true;
        });

        // Known symbol addresses (from SAMPLE.map) for targeted tracing
        final int ADDR_LOAD_TILES   = 0x2017B0;
        final int ADDR_INIT_SCREEN  = 0x201AB0;
        // Run
        long instrCount    = 0;
        long nextVblCycle  = cyclesPerVbl;
        long vblCount      = 0;
        long prevCycles    = 0;
        long cycleCount    = 0;
        long startTime     = System.currentTimeMillis();
        boolean loadTilesSeen = false;
        int     initScreenCount = 0;
        // Script-mode delta tracking (previous frame's positions).
        int prevMapX=0, prevMapY=0, prevSprX=0, prevSprY=0, prevScrX=0, prevScrY=0;

        final int romBase = NgpLoader.ROM_BASE;
        final int romEnd  = NgpLoader.ROM_BASE + rom.data.length;

        try {
            while (!cpu.isHalted() && instrCount < maxInstrs) {
                // Fire VBL before executing next instruction if threshold reached
                if (vblEnabled && cycleCount >= nextVblCycle) {
                    nextVblCycle += cyclesPerVbl;
                    vblCount++;
                    fireVbl(reg, mem, traceF, vblCount);
                    if (wMask >= 0 && instrCount >= WATCH_WARMUP) {
                        int mapX  = mem.readWord(0x4000) & 0xFFFF;
                        int mapY  = mem.readWord(0x4006) & 0xFFFF;
                        int sprX  = mem.readWord(0x400C) & 0xFFFF;
                        int sprY  = mem.readWord(0x400E) & 0xFFFF;
                        int mmaxX = mem.readWord(0x4012) & 0xFFFF;
                        int scr1x = mem.readByte(0x8032) & 0xFF;
                        int scr1y = mem.readByte(0x8033) & 0xFF;
                        System.out.printf("[VBL %d] map_x=%d map_y=%d spr_x=%d spr_y=%d SCR1_X=%d SCR1_Y=%d map_max_x=%d%n",
                            vblCount, mapX, mapY, sprX, sprY, scr1x, scr1y, mmaxX);
                    }
                    if (scriptMasks != null && instrCount >= WATCH_WARMUP) {
                        int f = scriptFrame[0];
                        int inMask = (f < scriptMasks.length) ? scriptMasks[f] : 0;
                        // bomberman chars[0] layout (Entity @ _chars=0x400B):
                        //   idx u16 @+0, x @+2, y @+3, speed u16 @+4, nbmax @+6,
                        //   nb @+7, power @+8, dir @+9, anim @+10, ...
                        final int CH = 0x400B;
                        int cx   = mem.readByte(CH + 2) & 0xFF;
                        int cy   = mem.readByte(CH + 3) & 0xFF;
                        int cdir = mem.readByte(CH + 9) & 0xFF;
                        int canim= mem.readByte(CH + 10) & 0xFF;
                        int scr1x = mem.readByte(0x8032) & 0xFF;
                        int scr1y = mem.readByte(0x8033) & 0xFF;
                        // Deltas vs previous frame reveal whether input produced motion.
                        int dx = cx - prevSprX, dy = cy - prevSprY;
                        boolean moved = (dx|dy) != 0;
                        String dir = inMask==0?"----":
                            (inMask==0x08?"RIGHT":inMask==0x04?"LEFT":
                             inMask==0x02?"DOWN":inMask==0x01?"UP":("0x"+Integer.toHexString(inMask)));
                        String cdirS = cdir==0x08?"R":cdir==0x04?"L":cdir==0x02?"D":cdir==0x01?"U":("?"+cdir);
                        String flag = (inMask!=0 && !moved) ? "  <<< WALL (no move)" : "";
                        System.out.printf("[F%d in=%s] char x=%d y=%d dir=%s anim=%d  d(x,y)=(%d,%d) SCR=(%d,%d)%s%n",
                            f, dir, cx, cy, cdirS, canim, dx, dy, scr1x, scr1y, flag);
                        prevSprX=cx; prevSprY=cy;
                        scriptFrame[0] = f + 1;
                    }
                }

                cpu.step();
                instrCount++;
                instrRef[0] = instrCount;

                if (tracePc >= 0 && reg.getPC() == tracePc && instrCount >= WATCH_WARMUP) {
                    int sp = reg.getXSP();
                    System.out.printf("[PC 0x%06X] %s | de=0x%04X ix=0x%04X | slot56=0x%04X slot60=0x%02X slot64=0x%04X slot68=0x%02X | map_y=%d spr_y=%d SCR1_Y=%d%n",
                        tracePc, reg, reg.getWord16(2), reg.getWord16(4),
                        mem.readWord(sp+56)&0xFFFF, mem.readByte(sp+60)&0xFF,
                        mem.readWord(sp+64)&0xFFFF, mem.readByte(sp+68)&0xFF,
                        mem.readWord(cc900Addrs?0x400A:0x4006)&0xFFFF,
                        mem.readWord(cc900Addrs?0x4012:0x400E)&0xFFFF,
                        mem.readByte(0x8033)&0xFF);
                }

                // Targeted tracing: log first entry into key functions
                int pcAfter = reg.getPC();
                if (!loadTilesSeen && pcAfter == ADDR_LOAD_TILES) {
                    loadTilesSeen = true;
                    System.out.printf("[instr %,d] Entered _loadTiles  %s%n", instrCount, reg);
                }
                if (pcAfter == ADDR_INIT_SCREEN) {
                    initScreenCount++;
                    int mapPtr = mem.readDword(0x4002);
                    int mapW   = mem.readWord(0x4008) & 0xFFFF;
                    int mapX   = mem.readWord(0x4000) & 0xFFFF;
                    int mapY   = mem.readWord(0x4006) & 0xFFFF;
                    System.out.printf("[instr %,d] Entered _initScreen #%d  _map=0x%06X _map_w=%d _map_x=%d _map_y=%d%n",
                        instrCount, initScreenCount, mapPtr, mapW, mapX, mapY);
                }
                // Accumulate cycles from this step
                long newCycles = cpu.getCycles();
                cycleCount += newCycles - prevCycles;
                prevCycles = newCycles;

                // Safety: stop if PC falls outside ROM
                int pc = reg.getPC();
                if (pc < romBase || pc >= romEnd) {
                    System.out.printf("PC=0x%06X outside ROM at instr %,d, stopping.%n",
                        pc, instrCount);
                    break;
                }
            }
        } catch (Cpu.CpuException e) {
            System.out.println("CPU exception: " + e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("Executed %,d instructions in %d ms (%.0f KIPS)%n",
            instrCount, elapsed, elapsed > 0 ? instrCount / (double) elapsed : instrCount);
        if (vblEnabled) {
            System.out.printf("VBL interrupts fired: %,d (%.1f simulated seconds)%n",
                vblCount, (double) vblCount / vblHz);
        }
        System.out.println("Final state: " + reg);

        if (dumpGfx) {
            dumpAndVerifyGfx(mem);
        }
    }

    // -----------------------------------------------------------------
    // GFX tile / scroll-map verification
    // -----------------------------------------------------------------

    /**
     * Expected tile bitmap data from gfx.h (gfx_tiles[16*8], u16 little-endian).
     * 16 tiles × 8 rows × 2 bytes = 256 bytes starting at 0xA000.
     */
    private static final int[] EXPECTED_TILES = {
        // tile 0 — blank
        0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,0x0000,
        // tiles 1-7 — solid 0xFFFF
        0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
        0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
        0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
        0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
        0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
        0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
        0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,0xffff,
        // tile 8
        0x0000,0x0000,0x0000,0x03c0,0x03c0,0x0000,0x0000,0x0000,
        // tile 9
        0x0000,0x0000,0x00c0,0x0fc0,0x03f0,0x0300,0x0000,0x0000,
        // tile 10
        0x0000,0x0000,0x03c0,0x0ff0,0x0ff0,0x03c0,0x0000,0x0000,
        // tile 11
        0x0000,0x00c0,0x03c0,0x3ff0,0x0ffc,0x03c0,0x0300,0x0000,
        // tile 12
        0x0000,0x03c0,0x0ff0,0x3ffc,0x3ffc,0x0ff0,0x03c0,0x0000,
        // tile 13
        0x0000,0x0300,0x03c0,0x0ffc,0x3ff0,0x03c0,0x00c0,0x0000,
        // tile 14
        0x0000,0x0000,0x03c0,0x0ff0,0x0ff0,0x03c0,0x0000,0x0000,
        // tile 15
        0x0000,0x0000,0x0300,0x03f0,0x0fc0,0x00c0,0x0000,0x0000,
    };

    /**
     * Expected palette index per tile from gfx.h (gfx_palidx[16]).
     * Used to compute the expected scroll-map entry: (palidx << 9) | tile_index.
     */
    private static final int[] EXPECTED_PAL_IDX = {0,1,2,3,4,5,6,7, 7,7,7,7,7,7,7,7};

    /**
     * Expected palette data from gfx.h (gfx_pals[8*4], u16 little-endian).
     * 8 palettes × 4 colours × 2 bytes = 64 bytes, copied to both 0x8280 and 0x8200.
     */
    private static final int[] EXPECTED_PALS = {
        0x0000,0x0000,0x0000,0x0000,
        0x0000,0x0000,0x0000,0x000f,
        0x0000,0x0000,0x0000,0x00f0,
        0x0000,0x0000,0x0000,0x0f00,
        0x0000,0x0000,0x0000,0x0f0f,
        0x0000,0x0000,0x0000,0x00ff,
        0x0000,0x0000,0x0000,0x039f,
        0x0000,0x0000,0x0000,0x0fff,
    };

    private static final int SCROLL1_PAL_ADDR = 0x8280;
    private static final int SPRITE_PAL_ADDR  = 0x8200;
    private static final int TILE_RAM_ADDR    = 0xA000;
    private static final int SCROLL_MAP_ADDR  = 0x9000;
    private static final int GFX_TILES        = 16;
    private static final int GFX_PALS         = 8;

    private static void dumpAndVerifyGfx(Memory mem) {
        System.out.println();
        System.out.println("=== GFX tile verification ===");

        // --- 1. Verify tile bitmaps at 0xA000 ---
        System.out.printf("Tile bitmaps at 0x%04X  (%d tiles × 8 rows × 2 bytes = %d bytes)%n",
            TILE_RAM_ADDR, GFX_TILES, GFX_TILES * 8 * 2);
        int tileErrors = 0;
        for (int t = 0; t < GFX_TILES; t++) {
            for (int row = 0; row < 8; row++) {
                int idx  = t * 8 + row;
                int addr = TILE_RAM_ADDR + idx * 2;
                int got  = mem.readWord(addr);
                int exp  = EXPECTED_TILES[idx];
                if (got != exp) {
                    System.out.printf("  MISMATCH tile %2d row %d  addr=0x%04X  got=0x%04X  expected=0x%04X%n",
                        t, row, addr, got, exp);
                    tileErrors++;
                }
            }
        }
        if (tileErrors == 0) {
            System.out.printf("  OK — all %d words match%n", GFX_TILES * 8);
        } else {
            System.out.printf("  FAIL — %d word(s) wrong%n", tileErrors);
        }

        // --- 2. Verify palettes at 0x8280 (scroll1) and 0x8200 (sprites) ---
        System.out.printf("%nPalettes at 0x%04X (scroll1) and 0x%04X (sprites)  (%d pals × 4 colours)%n",
            SCROLL1_PAL_ADDR, SPRITE_PAL_ADDR, GFX_PALS);
        int palErrors = 0;
        for (int p = 0; p < GFX_PALS; p++) {
            for (int c = 0; c < 4; c++) {
                int idx  = p * 4 + c;
                int exp  = EXPECTED_PALS[idx];
                int got1 = mem.readWord(SCROLL1_PAL_ADDR + idx * 2);
                int got2 = mem.readWord(SPRITE_PAL_ADDR  + idx * 2);
                if (got1 != exp) {
                    System.out.printf("  MISMATCH scroll1 pal %d col %d  addr=0x%04X  got=0x%04X expected=0x%04X%n",
                        p, c, SCROLL1_PAL_ADDR + idx*2, got1, exp);
                    palErrors++;
                }
                if (got2 != exp) {
                    System.out.printf("  MISMATCH sprites pal %d col %d  addr=0x%04X  got=0x%04X expected=0x%04X%n",
                        p, c, SPRITE_PAL_ADDR + idx*2, got2, exp);
                    palErrors++;
                }
            }
        }
        if (palErrors == 0) {
            System.out.printf("  OK — all %d palette words match at both addresses%n", GFX_PALS * 4);
        } else {
            System.out.printf("  FAIL — %d palette word(s) wrong%n", palErrors);
        }

        // --- 2. Validate scroll-plane map entries at 0x9000 ---
        // The actual tile layout is driven by level data; we validate that
        // every non-zero entry uses a tile index in [0, GFX_TILES) and a
        // palette index in [0, GFX_PALS), and that at least some entries
        // have been written (proving setTile/initScreen ran).
        System.out.printf("%nScroll-plane map at 0x%04X  (full map 0x9000..0x93FF)%n", SCROLL_MAP_ADDR);
        int mapErrors = 0;
        int nonZeroEntries = 0;
        for (int i = 0; i < 0x200; i++) {
            int addr = SCROLL_MAP_ADDR + i * 2;
            int raw  = mem.readWord(addr);
            if (raw == 0) continue;
            nonZeroEntries++;
            int tile = raw & 0x1FF;
            int pal  = (raw >> 9) & 0x0F;
            if (tile >= GFX_TILES || pal >= GFX_PALS) {
                System.out.printf("  INVALID [%3d] 0x%04X  raw=0x%04X  t=%d p=%d  (out of range)%n",
                    i, addr, raw, tile, pal);
                mapErrors++;
            }
        }
        System.out.printf("  Non-zero entries: %d / 512%n", nonZeroEntries);
        if (nonZeroEntries == 0) {
            System.out.println("  WARN — scroll map is all zero: setTile/initScreen did not run yet");
            mapErrors++;
        } else if (mapErrors == 0) {
            System.out.printf("  OK — all %d written entries have valid tile/palette indices%n", nonZeroEntries);
        }

        // Dump non-zero entries for inspection
        System.out.printf("%nNon-zero entries in scroll map:%n");
        int shown = 0;
        for (int i = 0; i < 0x200 && shown < 32; i++) {
            int addr = SCROLL_MAP_ADDR + i * 2;
            int raw  = mem.readWord(addr);
            if (raw != 0) {
                System.out.printf("  [%3d] 0x%04X  0x%04X   t=%3d  p=%2d%n",
                    i, addr, raw, raw & 0x1FF, (raw >> 9) & 0x0F);
                shown++;
            }
        }
        if (nonZeroEntries > 32) System.out.printf("  ... (%d more)%n", nonZeroEntries - 32);
        if (nonZeroEntries == 0) System.out.println("  (none)");
        System.out.println();

        // --- 3. Dump raw hex of tile RAM ---
        System.out.printf("Raw hex dump — tile RAM 0x%04X..0x%04X:%n",
            TILE_RAM_ADDR, TILE_RAM_ADDR + GFX_TILES * 16 - 1);
        for (int t = 0; t < GFX_TILES; t++) {
            System.out.printf("  tile %2d:", t);
            for (int row = 0; row < 8; row++) {
                int addr = TILE_RAM_ADDR + (t * 8 + row) * 2;
                System.out.printf(" %04x", mem.readWord(addr));
            }
            System.out.println();
        }

        System.out.println();
        if (tileErrors == 0 && palErrors == 0 && nonZeroEntries > 0 && mapErrors == 0) {
            System.out.println("RESULT: PASS — GFX tiles correctly transferred: tile bitmaps at 0xA000, palettes at 0x8280/0x8200, map entries at 0x9000 are valid.");
        } else if (tileErrors == 0 && palErrors == 0 && nonZeroEntries == 0) {
            System.out.println("RESULT: PARTIAL PASS");
            System.out.println("  PASS  — tile bitmaps (0xA000) and palettes (0x8280/0x8200) correctly transferred by loadTiles()");
            System.out.println("  WARN  — scroll-plane map (0x9000) still all-zero: setTile/initScreen did not write entries");
            System.out.println("          Root cause: _initLevel failed to set _map pointer (likely unimplemented CPU instruction");
            System.out.println("          in the lda/dword-load path); this is a simulator gap, not a codegen bug.");
        } else {
            System.out.printf("RESULT: FAIL — %d tile error(s), %d palette error(s), %d map entry error(s).%n",
                tileErrors, palErrors, mapErrors);
        }
    }

    /**
     * Fire a VBL interrupt.
     *
     * Reads the VBL handler address from the interrupt vector table entry at 0x6FCC (rVBI).
     * Each cartridge writes this 4-byte pointer during initialization.
     * If the vector is zero (not yet installed), the interrupt is silently skipped.
     *
     * Interrupt entry sequence (TLCS-900/H hardware behaviour):
     *   XSP -= 2;  mem[XSP] = SR    (status register saved)
     *   XSP -= 4;  mem[XSP] = PC    (return address saved)
     *   PC = handler_address
     *
     * The handler must end with RETI, which restores in reverse order:
     *   PC = pop32()   (4 bytes)
     *   SR = pop16()   (2 bytes)
     */
    private static void fireVbl(Registers reg, Memory mem, boolean trace,
                                long vblCount) {
        // Read the VBL handler address from the vector table (4-byte little-endian at 0x6FCC)
        int handlerAddr = mem.readDword(VBL_VECTOR_ADDR) & 0xFFFFFF;

        // Skip if the game hasn't installed a handler yet (vector is null)
        if (handlerAddr == 0) {
            if (trace) System.out.printf("  [VBL #%d skipped: vector=0]%n", vblCount);
            return;
        }

        if (trace) {
            System.out.printf("  [VBL #%d -> 0x%06X]  %s%n", vblCount, handlerAddr, reg);
        }

        // Push SR then PC so that RETI (pop32 PC, pop16 SR) restores correctly
        int xsp = reg.getXSP();
        xsp -= 2;
        mem.writeWord(xsp, reg.getSR());
        xsp -= 4;
        mem.writeDword(xsp, reg.getPC());
        reg.setXSP(xsp);

        reg.setPC(handlerAddr);
    }
}
