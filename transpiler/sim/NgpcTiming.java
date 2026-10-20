package sim;

/** Hardware timing constants shared by the simulator and its benchmark harnesses. */
public final class NgpcTiming {
    public static final long CPU_HZ = 6_144_000L;
    public static final int CYCLES_PER_SCANLINE = 515;
    public static final int SCANLINES_PER_FRAME = 199; // RAS.V is measured to run 0..198.
    public static final int VISIBLE_SCANLINES = 152;
    public static final long CYCLES_PER_FRAME =
        (long) CYCLES_PER_SCANLINE * SCANLINES_PER_FRAME;
    public static final long VISIBLE_CYCLES =
        (long) CYCLES_PER_SCANLINE * VISIBLE_SCANLINES;

    /** Toshiba instruction-list values are states; one state is two CPU clocks. */
    public static final int INSTRUCTION_STATE_SCALE = 2;

    /** Calibrated ngpcraft C++ timing configuration. */
    public static final int CART_FETCH_WAIT_PER_BYTE = 3;
    public static final int CART_DATA_WAIT = 0;
    public static final int BIOS_WAIT = 0;
    public static final int DATA_ACCESS_CYCLES = 4;
    public static final int VRAM_WRITE_WAIT_PER_ACCESS = 10;

    private NgpcTiming() {}

    public static boolean isInVBlank(long totalCycles) {
        return Math.floorMod(totalCycles, CYCLES_PER_FRAME) >= VISIBLE_CYCLES;
    }
}
