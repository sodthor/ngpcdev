package sim;

/**
 * NGPC memory map (24-bit address space):
 *   0x000000-0x0000FF  Direct-page I/O registers
 *   0x004000-0x006FFF  Work RAM (12 KB)
 *   0x006F80-0x006FFF  System I/O registers (joypad, etc.)
 *   0x007000-0x007FFF  Z80 sound RAM
 *   0x008000-0x009FFF  2D engine registers + VRAM
 *   0x00A000-0x00BFFF  Tile RAM
 *   0x00FF0000+        Internal ROM (BIOS) – not emulated
 *   0x00200000+        Cartridge ROM
 */
public class Memory {

    private final byte[] workRam  = new byte[0x2F80]; // 0x4000-0x6F7F  (12 KB safe work RAM)
    private final byte[] ioRegs   = new byte[0x0080]; // 0x6F80-0x6FFF  (I/O registers, separate)
    private final byte[] videoRam = new byte[0x4000]; // 0x8000-0xBFFF
    private final byte[] cartRom;

    private IoDevice io;

    public Memory(byte[] romData) { this.cartRom = romData; }
    public void setIoDevice(IoDevice d) { this.io = d; }

    // ---- read ----

    public int readByte(int addr) {
        addr &= 0xFFFFFF;
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
        if (addr >= 0x8000 && addr <= 0xBFFF)
            return videoRam[addr - 0x8000] & 0xFF;
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
            // 0x7000-0x7FFF: Z80 RAM (sound) – return 0, but let io stub handshake bytes
            if (io != null) { int v = io.read(addr); if (v >= 0) return v; }
            return 0;
        }
        return 0xFF;
    }

    public int readWord(int addr) {
        return readByte(addr) | (readByte(addr + 1) << 8);
    }

    public int readDword(int addr) {
        return readWord(addr) | (readWord(addr + 2) << 16);
    }

    // ---- write ----

    public void writeByte(int addr, int v) {
        addr &= 0xFFFFFF; v &= 0xFF;
        if (addr >= 0x200000) return;               // ROM write-protected
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
            // 0x7000-0x7FFF: Z80 RAM, ignore writes
        }
    }

    public void writeWord(int addr, int v) {
        writeByte(addr, v & 0xFF); writeByte(addr + 1, (v >> 8) & 0xFF);
    }

    public void writeDword(int addr, int v) {
        writeWord(addr, v & 0xFFFF); writeWord(addr + 2, (v >>> 16) & 0xFFFF);
    }

    public byte[] getWorkRam() { return workRam; }
    public byte[] getCartRom() { return cartRom; }

    public interface IoDevice {
        /** Return >= 0 to override; -1 to use backing RAM. */
        int read(int addr);
        void write(int addr, int val);
    }
}
