package sim.tests;

import sim.Memory;
import java.nio.ByteBuffer;

public final class Z80SoundDevice implements Memory.IoDevice, AutoCloseable {
    private final ByteBuffer shared = ByteBuffer.allocateDirect(0x1100);
    private final long context;
    private boolean enabled;
    private boolean nmi;
    private int remainder;

    static {
        System.load(System.getProperty("ngpc.z80.library", "/tmp/ngpc-sound-tests/libngpc_z80.so"));
    }

    private static native long create(ByteBuffer shared);
    private static native void reset(long context);
    private static native void advance(long context, int cycles, int period, boolean nmi);
    private static native long[] state(long context);
    private static native void destroy(long context);

    public Z80SoundDevice() { context = create(shared); }

    public int read(int address) {
        if (address < 256) return shared.get(0x1000 + address) & 255;
        if (address >= 0x7000 && address < 0x8000) return shared.get(address - 0x7000) & 255;
        return -1;
    }

    public void write(int address, int value) {
        if (address >= 0x7000 && address < 0x8000) shared.put(address - 0x7000, (byte) value);
        if (address < 256) {
            shared.put(0x1000 + address, (byte) value);
            if (address == 0xb8) {
                enabled = value == 0x55;
                if (value == 0xaa) { reset(context); nmi = false; }
            }
            if (address == 0xba && value != 0 && enabled) nmi = true;
        }
    }

    public void tick(int tlcsCycles) {
        if (!enabled) return;
        boolean timer = (read(0x20) & 0x88) == 0x88 && (read(0x28) & 0x0c) == 4
                && (read(0x25) & 0xf0) == 0xb0;
        int period = timer ? Math.max(1, read(0x27)) * 8 : 0;
        int elapsed = tlcsCycles + remainder;
        remainder = elapsed & 1;
        advance(context, elapsed / 2, period, nmi);
        nmi = false;
    }

    public long[] getState() { return state(context); }

    public String report() {
        long[] values = getState();
        return String.format("Z80 PC=%04X SP=%04X PSG=%d audible=%d IRQ=%d NMI=%d TLCS-IRQ=%d invalid=%d",
            values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7]);
    }

    public void close() { destroy(context); }
}