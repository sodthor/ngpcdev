package sim.tests;

import sim.Memory;
import sim.Cpu;
import sim.Registers;

public class SoundMemoryTest {
    public static void main(String[] args) {
        for (int size : new int[] {1, 2}) {
            for (int pair : new int[] {3, 5}) {
                for (int operation = 0x10; operation <= 0x13; operation++) {
                    checkTransfer(size, pair, operation);
                }
            }
        }
        Memory memory = new Memory(new byte[0]);
        int[] writes = new int[2];
        memory.setIoDevice(new Memory.IoDevice() {
            public int read(int address) { return -1; }
            public void write(int address, int value) {
                if (address >= 0x7000 && address <= 0x7fff) writes[0]++;
                if (address == 0xbc && value == 0xff) writes[1]++;
            }
        });
        for (int offset = 0; offset < 4096; offset++) {
            memory.writeByte(0x7000 + offset, offset ^ (offset >>> 8));
        }
        for (int offset = 0; offset < 4096; offset++) {
            int expected = (offset ^ (offset >>> 8)) & 255;
            if (memory.readByte(0x7000 + offset) != expected) {
                throw new AssertionError("Sound RAM mismatch at " + offset);
            }
        }
        memory.writeWord(0x7006, 0x1234);
        if (memory.readWord(0x7006) != 0x1234) throw new AssertionError("Word write");
        memory.writeByte(0xbc, 0xff);
        if (writes[0] != 4098 || writes[1] != 1) throw new AssertionError("I/O tracing");
        System.out.println("SoundMemoryTest: PASS");
    }

    private static void checkTransfer(int size, int pair, int operation) {
        byte[] code = {(byte) (0x80 + (size == 2 ? 16 : 0) + pair), (byte) operation};
        Memory memory = new Memory(code);
        Registers registers = new Registers();
        registers.setPC(0x200000);
        registers.setXBC(0xabcd0003);
        registers.setXDE(0x7008);
        registers.setXHL(0x4008);
        registers.setXIX(0x7108);
        registers.setXIY(0x4108);
        registers.setFlagC(true);
        registers.setFlagH(true);
        registers.setFlagN(true);
        for (int offset = 0; offset < 16; offset++) {
            memory.writeByte(0x4000 + offset, offset + 1);
            memory.writeByte(0x4100 + offset, offset + 33);
        }
        int source = registers.getDword32(pair);
        int destination = registers.getDword32(pair - 1);
        Cpu cpu = new Cpu(memory, registers);
        cpu.step();
        int count = (operation & 1) != 0 ? 3 : 1;
        int stride = operation >= 0x12 ? -size : size;
        for (int index = 0; index < count; index++) {
            for (int offset = 0; offset < size; offset++) {
                if (memory.readByte(destination + index * stride + offset)
                        != memory.readByte(source + index * stride + offset)) {
                    throw new AssertionError("Block transfer register pair " + pair);
                }
            }
        }
        if (registers.getDword32(pair) != source + count * stride
                || registers.getDword32(pair - 1) != destination + count * stride
                || registers.getXBC() != (0xabcd0000 | (3 - count))) {
            throw new AssertionError("Block transfer register update");
        }
        if (!registers.flagC() || registers.flagH() || registers.flagN()
                || registers.flagV() != (count != 3)) throw new AssertionError("Block transfer flags");
        if (cpu.getCycles() != ((operation & 1) != 0 ? 110 : 26)) {
            throw new AssertionError("Block transfer cycles");
        }
    }
}