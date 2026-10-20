package sim.tests;

import sim.Memory;

public final class VideoMemoryTest {
    public static void main(String[] args) {
        Memory memory = new Memory(new byte[0]);
        for (int address = 0x8000; address <= 0xbfff; address++) memory.writeByte(address, address);
        for (int address = 0x8000; address <= 0xbfff; address++)
            require(memory.readByte(address) == (address & 255), "video RAM without I/O");
        int[] raster = {152};
        memory.setIoDevice(new Memory.IoDevice() {
            public int read(int address) { return address == 0x8009 ? raster[0] : -1; }
            public void write(int address, int value) {}
        });
        for (int address = 0x8000; address <= 0xbfff; address++)
            require(memory.readByte(address) == (address == 0x8009 ? 152 : address & 255),
                "video I/O override/fallback");
        raster[0] = 0;
        require(memory.readByte(0x8009) == 0 && memory.readWord(0x8008) == 8, "changing raster value");
        memory.writeWord(0x9000, 0xcafe);
        memory.writeDword(0xa000, 0xabcdef01);
        require(memory.readWord(0x9000) == 0xcafe && memory.readDword(0xa000) == 0xabcdef01,
            "tilemap/graphics remain readable with I/O installed");
        memory.setIoDevice(null);
        require(memory.readByte(0x8009) == 9, "I/O must not overwrite backing video RAM");
        System.out.println("VideoMemoryTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
