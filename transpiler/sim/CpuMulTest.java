package sim;

/** Regression checks for TLCS-900/H multiply register decoding. */
public final class CpuMulTest {
    private CpuMulTest() {}

    public static void main(String[] args) {
        // CD 43 = MUL BC,E: the encoded destination byte is C, while the
        // 16-bit product is written to its containing pair, BC.
        Memory memory = new Memory(new byte[] {(byte) 0xCD, 0x43});
        Registers registers = new Registers();
        registers.setPC(NgpLoader.ROM_BASE);
        registers.setC(15);
        registers.setE(4);
        registers.setXHL(0x12345678);

        Cpu cpu = new Cpu(memory, registers);
        cpu.step();

        require(registers.getBC() == 60,
            "MUL BC,E must calculate C * E into BC; got " + registers.getBC());
        require(registers.getXHL() == 0x12345678,
            "MUL BC,E must not overwrite XHL");
        require(registers.getPC() == NgpLoader.ROM_BASE + 2,
            "MUL BC,E must consume exactly two bytes");

        // D8 09 1F 00 = MULS XWA,31. The following bytes must remain a
        // separate instruction rather than being consumed as a dword immediate.
        memory = new Memory(new byte[] {(byte) 0xD8, 0x09, 0x1F, 0x00, (byte) 0xE8, (byte) 0xED, 0x07});
        registers = new Registers();
        registers.setPC(NgpLoader.ROM_BASE);
        registers.setWA(0xFFFE);
        cpu = new Cpu(memory, registers);
        cpu.step();

        require(registers.getXWA() == -62,
            "MULS XWA,31 must sign-multiply WA by the immediate; got " + registers.getXWA());
        require(registers.getPC() == NgpLoader.ROM_BASE + 4,
            "MULS XWA,#word must consume exactly four bytes");
        cpu.step();
        require(registers.getXWA() == -1,
            "the instruction following MULS must remain decodable");

        System.out.println("CpuMulTest: PASS");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}