package sim;

/** Focused regression checks for the calibrated CPU/memory timing model. */
public final class TimingModelTest {
    private TimingModelTest() {}

    public static void main(String[] args) {
        require(NgpcTiming.CYCLES_PER_FRAME == 102_485, "frame length");
        require(Memory.regionOf(0x3FFF) == Memory.Region.UNMAPPED, "below RAM");
        require(Memory.regionOf(0x4000) == Memory.Region.RAM, "RAM start");
        require(Memory.regionOf(0x7FFF) == Memory.Region.RAM, "shared RAM end");
        require(Memory.regionOf(0x8000) == Memory.Region.K2GE, "K2GE start");
        require(Memory.regionOf(0x8FFF) == Memory.Region.K2GE, "K2GE end");
        require(Memory.regionOf(0x9000) == Memory.Region.VRAM, "VRAM start");
        require(Memory.regionOf(0xBFFF) == Memory.Region.VRAM, "VRAM end");
        require(Memory.regionOf(0x200000) == Memory.Region.CART_ROM, "cart start");
        require(Memory.regionOf(0xFF0000) == Memory.Region.BIOS, "BIOS start");

        // NOP in cartridge: 4 states * 2 clocks + one fetched byte * 3 clocks.
        byte[] nopRom = {0x00};
        Cpu nop = cpuAt(nopRom, 0x200000);
        nop.step();
        require(nop.getCycles() == 11, "cart NOP timing: " + nop.getCycles());

        // PUSH SR: 8 states * 2 + cart fetch 3 + one RAM word access 4.
        Cpu push = cpuAt(new byte[] {0x02}, 0x200000);
        push.step();
        require(push.getCycles() == 23, "RAM data timing: " + push.getCycles());

        // Display-memory write wait is per access, not per byte.
        Memory activeMemory = new Memory(new byte[0]);
        activeMemory.beginInstruction(0);
        activeMemory.writeWord(0x9000, 0x1234);
        require(activeMemory.endInstruction() == 10, "active-display word write timing");

        // The K2GE bus covers 0x8000-0xBFFF for write throttling, including
        // registers/palettes as well as scroll-plane and character RAM.
        activeMemory.beginInstruction(0);
        activeMemory.writeDword(0x8000, 0x12345678);
        require(activeMemory.endInstruction() == 10, "K2GE dword write timing");

        // During VBlank the display bus is free.
        activeMemory.beginInstruction(NgpcTiming.VISIBLE_CYCLES);
        activeMemory.writeByte(0xBFFF, 0x55);
        require(activeMemory.endInstruction() == 0, "VBlank write timing");
        System.out.println("TimingModelTest: PASS");
    }

    private static Cpu cpuAt(byte[] rom, int pc) {
        Memory memory = new Memory(rom);
        Registers registers = new Registers();
        registers.setPC(pc);
        registers.setXSP(0x6F00);
        return new Cpu(memory, registers);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}