package sim;

/** Disassemble a raw byte window of the ROM at a runtime address. */
public final class DumpBytes {
    public static void main(String[] args) throws Exception {
        String romPath = args[0];
        int addr = Integer.parseInt(args[1], 16);
        int count = args.length > 2 ? Integer.parseInt(args[2]) : 64;
        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory mem = new Memory(rom.data);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i % 16 == 0) sb.append(String.format("%n%06X:", addr + i));
            sb.append(String.format(" %02X", mem.readByte(addr + i) & 0xFF));
        }
        System.out.println(sb);
    }
}
