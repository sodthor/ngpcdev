package sim;

/**
 * NGPC memory map (24-bit address space):
 *   0x000000-0x0000FF  TLCS-900/H on-chip I/O
 *   0x004000-0x006FFF  Work RAM + system RAM/vector page
 *   0x007000-0x007FFF  Shared Z80 RAM
 *   0x008000-0x008FFF  K2GE registers, sprite RAM and palette RAM
 *   0x009000-0x009FFF  SCR1/SCR2 scroll-plane RAM
 *   0x00A000-0x00BFFF  Character/tile RAM
 *   0x200000-0x3FFFFF  Cartridge ROM/flash
 *   0xFF0000-0xFFFFFF  Internal BIOS ROM (not backed here)
 */
public class Memory {

    public enum Region { IO_PAGE, RAM, K2GE, VRAM, CART_ROM, BIOS, UNMAPPED }

    private final byte[] workRam  = new byte[0x2F80]; // 0x4000-0x6F7F  (12 KB safe work RAM)
    private final byte[] ioRegs   = new byte[0x0080]; // 0x6F80-0x6FFF  (I/O registers, separate)
    private final byte[] soundRam = new byte[0x1000];
    private final byte[] videoRam = new byte[0x4000]; // 0x8000-0xBFFF
    private final byte[] cartRom;

    private IoDevice io;

    // Timing is accumulated only while Cpu.step() executes. Debug/tool reads therefore
    // do not perturb the emulated clock. The C++ core also models BIU prefetch overlap
    // and measured block-copy costs; this compact simulator applies region costs additively.
    private long accessWait;
    private boolean timingEnabled;
    private boolean timingSuppressed;
    private boolean inVBlank;
    private boolean executingFromCart;

    // RAM consumption tracking
    private int minStackAddrSeen = 0x006F80;  // will be set by setStackBase(); conservative upper bound
    private int maxDataAddrSeen  = 0x004000;  // data starts here

    public Memory(byte[] romData) { this.cartRom = romData; }
    public void setIoDevice(IoDevice d) { this.io = d; }

    public static Region regionOf(int address) {
        int addr = address & 0xFFFFFF;
        if (addr <= 0x0000FF) return Region.IO_PAGE;
        if (addr >= 0x004000 && addr <= 0x007FFF) return Region.RAM;
        if (addr >= 0x008000 && addr <= 0x008FFF) return Region.K2GE;
        if (addr >= 0x009000 && addr <= 0x00BFFF) return Region.VRAM;
        if ((addr >= 0x200000 && addr <= 0x3FFFFF)
                || (addr >= 0x800000 && addr <= 0x9FFFFF)) return Region.CART_ROM;
        if (addr >= 0xFF0000) return Region.BIOS;
        return Region.UNMAPPED;
    }

    void beginInstruction(long totalCycles) {
        accessWait = 0;
        timingEnabled = true;
        inVBlank = NgpcTiming.isInVBlank(totalCycles);
        executingFromCart = false;
    }

    void setInstructionAddress(int address) {
        executingFromCart = regionOf(address) == Region.CART_ROM;
    }

    boolean suppressTiming(boolean suppress) {
        boolean old = timingSuppressed;
        timingSuppressed = suppress;
        return old;
    }

    long endInstruction() {
        timingEnabled = false;
        timingSuppressed = false;
        long result = accessWait;
        accessWait = 0;
        return result;
    }

    private void chargeDataAccess(int addr) {
        if (!timingEnabled || timingSuppressed || !executingFromCart) return;
        Region region = regionOf(addr);
        if (region == Region.RAM || region == Region.CART_ROM) {
            accessWait += NgpcTiming.DATA_ACCESS_CYCLES;
            if (region == Region.CART_ROM) accessWait += NgpcTiming.CART_DATA_WAIT;
        } else if (region == Region.BIOS) {
            accessWait += NgpcTiming.BIOS_WAIT;
        }
    }

    private void chargeDisplayWrite(int addr) {
        if (!timingEnabled || timingSuppressed || inVBlank) return;
        int a = addr & 0xFFFFFF;
        if (a >= 0x8000 && a <= 0xBFFF)
            accessWait += NgpcTiming.VRAM_WRITE_WAIT_PER_ACCESS;
    }

    /** Instruction fetch; cartridge wait is charged per fetched byte. */
    public int readFetchByte(int addr) {
        if (timingEnabled && !timingSuppressed) {
            Region region = regionOf(addr);
            if (region == Region.CART_ROM) accessWait += NgpcTiming.CART_FETCH_WAIT_PER_BYTE;
            else if (region == Region.BIOS) accessWait += NgpcTiming.BIOS_WAIT;
        }
        boolean old = timingSuppressed;
        timingSuppressed = true;
        try { return readByte(addr); }
        finally { timingSuppressed = old; }
    }

    // ---- read ----

    public int readByte(int addr) {
        addr &= 0xFFFFFF;
        chargeDataAccess(addr);
        if (addr >= 0x200000) {
            int off = addr - 0x200000;
            return off < cartRom.length ? cartRom[off] & 0xFF : 0xFF;
        }
        if (addr >= 0xFF0000) return 0xFF;          // BIOS not modelled
        if (addr < 0x100) {
            // Direct-page I/O registers (0x0000-0x00FF). Route through the io
            // device so the harness can model e.g. the Z80 handshake byte at
            // 0x00BC; fall back to 0xFF when unhandled.
            if (io != null) { int v = io.read(addr); if (v >= 0) return v; }
            return 0xFF;
        }
        if (addr >= 0x8000 && addr <= 0xBFFF) {
            if (io != null) { int v = io.read(addr); if (v >= 0) return v; }
            return videoRam[addr - 0x8000] & 0xFF;
        }
        if (addr >= 0x4000 && addr <= 0x7FFF) {
            if (addr >= 0x6F80 && addr <= 0x6FFF) {
                // I/O registers – separate from work RAM
                if (io != null) { int v = io.read(addr); if (v >= 0) return v; }
                return ioRegs[addr - 0x6F80] & 0xFF;
            }
            if (addr <= 0x6F7F) {
                if (io != null) { int v = io.read(addr); if (v >= 0) return v; }
                return workRam[addr - 0x4000] & 0xFF;
            }
            if (io != null) { int v = io.read(addr); if (v >= 0) return v; }
            return soundRam[addr - 0x7000] & 0xFF;
        }
        return 0xFF;
    }

    public int readWord(int addr) {
        chargeDataAccess(addr);
        boolean old = timingSuppressed;
        timingSuppressed = true;
        try { return readByte(addr) | (readByte(addr + 1) << 8); }
        finally { timingSuppressed = old; }
    }

    public int readDword(int addr) {
        chargeDataAccess(addr);
        boolean old = timingSuppressed;
        timingSuppressed = true;
        try { return readWord(addr) | (readWord(addr + 2) << 16); }
        finally { timingSuppressed = old; }
    }

    // ---- write ----

    public void writeByte(int addr, int v) {
        addr &= 0xFFFFFF; v &= 0xFF;
        chargeDataAccess(addr);
        chargeDisplayWrite(addr);
        if (addr >= 0x200000) return;               // ROM write-protected
        if (addr < 0x100) {
            if (io != null) io.write(addr, v);
            return;
        }
        if (addr >= 0x8000 && addr <= 0xBFFF) {
            if (io != null) io.write(addr, v);
            videoRam[addr - 0x8000] = (byte) v;
            return;
        }
        if (addr >= 0x4000 && addr <= 0x7FFF) {
            if (addr >= 0x6F80 && addr <= 0x6FFF) {
                if (io != null) io.write(addr, v);
                ioRegs[addr - 0x6F80] = (byte) v;
                return;
            }
            if (addr <= 0x6F7F) {
                if (io != null) io.write(addr, v);
                workRam[addr - 0x4000] = (byte) v;
            }
            if (addr >= 0x7000) {
                if (io != null) io.write(addr, v);
                soundRam[addr - 0x7000] = (byte) v;
            }
        }
    }

    public void writeWord(int addr, int v) {
        chargeDataAccess(addr);
        chargeDisplayWrite(addr);
        boolean old = timingSuppressed;
        timingSuppressed = true;
        try { writeByte(addr, v & 0xFF); writeByte(addr + 1, (v >> 8) & 0xFF); }
        finally { timingSuppressed = old; }
    }

    public void writeDword(int addr, int v) {
        chargeDataAccess(addr);
        chargeDisplayWrite(addr);
        boolean old = timingSuppressed;
        timingSuppressed = true;
        try { writeWord(addr, v & 0xFFFF); writeWord(addr + 2, (v >>> 16) & 0xFFFF); }
        finally { timingSuppressed = old; }
    }

    public byte[] getWorkRam() { return workRam; }
    public byte[] getCartRom() { return cartRom; }

    // ---- RAM consumption tracking ----

    /** Track the lowest address the stack has reached (stack grows downward). */
    public void trackStackAccess(int xspValue) {
        int sp = xspValue & 0xFFFFFF;
        if (sp >= 0x4000 && sp < minStackAddrSeen) {
            minStackAddrSeen = sp;
            if (maxDataAddrSeen > 0x004000) checkRamCollision();
        }
    }

    /** Set the initial stack base so the report reflects the true headroom. */
    public void setStackBase(int xspValue) {
        minStackAddrSeen = xspValue & 0xFFFFFF;
    }

    /** Set the known end of BSS/data so collision detection has a ground truth.
     *  Must be called after loading a map file if collision detection is desired. */
    public void setDataEnd(int addr) {
        maxDataAddrSeen = addr & 0xFFFFFF;
    }

    /** Check if stack has grown into the data/BSS region. */
    private void checkRamCollision() {
        int gap = minStackAddrSeen - maxDataAddrSeen;
        if (gap <= 0) {
            System.err.printf("[RAM CRITICAL] Stack (0x%06X) has collided with data (0x%06X)! Gap=%d bytes%n",
                minStackAddrSeen, maxDataAddrSeen, gap);
            throw new RuntimeException("RAM exhausted: stack overwrote data");
        } else if (gap < 256) {
            System.out.printf("[RAM WARNING] Stack (0x%06X) approaching data (0x%06X). Gap=%d bytes%n",
                minStackAddrSeen, maxDataAddrSeen, gap);
        }
    }

    /** Get current RAM usage report. */
    public String getRamReport() {
        int stackBase = NgpLoader.STACK_INIT;
        int stackUsed = stackBase - minStackAddrSeen;
        int dataUsed = maxDataAddrSeen - 0x004000 + 1;
        int totalUsed = stackUsed + dataUsed;
        int totalRam = 0x6F80 - 0x4000;
        int gap = minStackAddrSeen - maxDataAddrSeen;
        return String.format(
            "[RAM Report] Stack: 0x%06X->0x%06X (%d bytes used)\n" +
            "             Data:  0x%06X->0x%06X (%d bytes used)\n" +
            "             Total: %d/%d bytes (%.1f%%), Gap: %d bytes",
            stackBase, minStackAddrSeen, stackUsed,
            0x004000, maxDataAddrSeen, dataUsed,
            totalUsed, totalRam, (totalUsed * 100.0 / totalRam), gap
        );
    }

    public interface IoDevice {
        /** Return >= 0 to override; -1 to use backing RAM. */
        int read(int addr);
        void write(int addr, int val);
    }
}
