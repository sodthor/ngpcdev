package sim;

import java.io.*;
import java.nio.file.*;

/**
 * Neo Geo Pocket Color ROM loader.
 *
 * NGP cartridge ROM format (mapped at 0x200000 in the NGPC address space):
 *   Bytes 0x00-0x13: "LICENSED BY SNK CORPORATION" (20 bytes)
 *   Bytes 0x14-0x17: Unused / padding
 *   Bytes 0x18-0x19: Cart ID (16-bit)
 *   Bytes 0x1A-0x1B: System flags (0x1000 = color)
 *   Bytes 0x1C-0x1F: Entry point address (little-endian 32-bit absolute address)
 *   Bytes 0x20-0x2B: Cart title (12 bytes ASCII)
 *   Bytes 0x2C-0x3F: Reserved / padding
 *   From 0x40:       ROM data (code, graphics, levels, etc.)
 *
 * Address space:
 *   0x000000-0x000000  Direct-page I/O
 *   0x004000-0x006F7F  Work RAM (main program RAM)
 *   0x006F80-0x006FFF  I/O registers (joypad, interrupt vectors, system regs)
 *   0x008000-0x009FFF  2D engine registers + VRAM
 *   0x00A000-0x00BFFF  Tile RAM
 *   0x00FF0000+        Internal ROM (BIOS)
 *   0x00200000+        Cartridge ROM (this file)
 */
public class NgpLoader {

    public static final int ROM_BASE    = 0x200000;
    public static final int ENTRY_OFF   = 0x1C;   // offset of _ptr (dd _main) in ROM file
    public static final int STACK_INIT  = 0x006C00; // initial stack pointer (top of safe work RAM, below I/O area at 0x6F80)

    public static class NgpRom {
        public final byte[] data;
        public final int entryPoint;
        public final String title;

        NgpRom(byte[] data, int entryPoint, String title) {
            this.data = data;
            this.entryPoint = entryPoint;
            this.title = title;
        }
    }

    public static NgpRom load(String path) throws IOException {
        byte[] data = Files.readAllBytes(Path.of(path));

        // Entry point: 4 bytes little-endian at offset 0x14 within the ROM
        // This is an absolute address in the NGPC address space.
        // The ROM is mapped at 0x200000, so the entry stored in the header
        // is an absolute address like 0x2016B0.
        int ep = (data[ENTRY_OFF]   & 0xFF)
               | ((data[ENTRY_OFF+1] & 0xFF) << 8)
               | ((data[ENTRY_OFF+2] & 0xFF) << 16)
               | ((data[ENTRY_OFF+3] & 0xFF) << 24);

        // Cart title at 0x1C, up to 12 bytes
        StringBuilder sb = new StringBuilder();
        for (int i = 0x1C; i < 0x28 && i < data.length; i++) {
            char c = (char)(data[i] & 0xFF);
            if (c == 0) break;
            sb.append(c);
        }

        return new NgpRom(data, ep & 0xFFFFFF, sb.toString());
    }

    /** Set up the initial CPU/memory state for running the ROM. */
    public static void init(NgpRom rom, Memory mem, Registers reg) {
        reg.setPC(rom.entryPoint);
        reg.setXSP(STACK_INIT);
        reg.setBank(0);
        // Maximum mode: set MAX bit in SR (bit 11) per TLCS-900/H spec
        reg.setSR(0x0800);
    }
}
