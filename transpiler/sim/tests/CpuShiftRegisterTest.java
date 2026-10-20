package sim.tests;

import sim.*;

public final class CpuShiftRegisterTest {
    private static int checks;
    private static final int[] VALUES = {0, 1, -1, 0x80000000, 0x8001, 0x12345678, 0xa5};

    public static void main(String[] args) {
        for (int addressing = 0; addressing < 5; addressing++)
          for (int size : new int[] {1, 2, 4})
            for (int operation = 0; operation < 8; operation++)
                for (boolean variable : new boolean[] {false, true})
                    for (int encoded = 0; encoded < 32; encoded++)
                        for (int value : VALUES)
                            for (boolean carry : new boolean[] {false, true})
                                shift(addressing, size, operation, variable, encoded, value, carry);
        for (int current = 0; current < 4; current++)
            for (int region : new int[] {0, 0x10, 0x20, 0x30, 0xd0, 0xe0, 0xf0})
                for (int pair = 0; pair < 4; pair++)
                    for (int size : new int[] {1, 2, 4})
                        for (int offset = 0; offset < 4; offset += size)
                            for (boolean write : new boolean[] {false, true})
                                registerFile(current, region + pair * 4 + offset, size, write);
        for (int address : new int[] {0x40, 0x80, 0xc0}) {
            Cpu cpu = new Cpu(new Memory(bytes(0xd7, address, 0x88)), initialized(0));
            boolean rejected = false;
            try { cpu.step(); } catch (Cpu.CpuException expected) { rejected = true; }
            require(rejected, "invalid register address must fail explicitly");
        }
        for (int dividend : new int[] {0x80000000, 0xabcdef01, 0xfffffffe})
            for (int divisor : new int[] {0x8001, 0xffff})
                for (boolean immediate : new boolean[] {false, true})
                    unsignedDivide(dividend, divisor, immediate);
        System.out.printf("CpuShiftRegisterTest: PASS (%d checks)%n", checks);
    }

    private static void shift(int addressing, int size, int operation, boolean variable, int encoded,
                              int value, boolean carry) {
        Registers r = initialized(2);
        int index = switch (addressing) {
            case 0, 2 -> 9; case 1 -> 15; case 3 -> 4; default -> 17;
        };
        int lane = addressing == 0 || size == 4 ? 0 : (4 - size) * 8;
        long mask = (1L << (size * 8)) - 1;
        int field = (int) mask << lane;
        int initial = (0xa1b2c3d4 & ~field) | ((value & (int) mask) << lane);
        if (index < 16) r.setBankGP32(index / 4, index % 4, initial);
        else r.setDword32(index - 12, initial);
        int prefix = size == 1 ? 0xcb : size == 2 ? 0xd9 : 0xe9;
        int opcode = (variable ? 0xf8 : 0xe8) + operation;
        byte[] code;
        if (addressing == 0) {
            code = variable ? bytes(prefix, opcode) : bytes(prefix, opcode, encoded);
        } else {
            int address = switch (addressing) {
                case 1 -> 0x3c; case 2 -> 0xe4; case 3 -> 0xd0; default -> 0xf4;
            };
            address += lane / 8;
            prefix = size == 1 ? 0xc7 : size == 2 ? 0xd7 : 0xe7;
            code = variable ? bytes(prefix, address, opcode) : bytes(prefix, address, opcode, encoded);
        }
        r.setA(encoded);
        r.setFlagC(carry);
        int count = (encoded & 15) == 0 ? 16 : encoded & 15;
        long v = value & mask;
        int bits = size * 8;
        long result;
        boolean outCarry;
        if (operation < 4) {
            int width = bits + (operation >= 2 ? 1 : 0);
            long ringMask = (1L << width) - 1;
            long ring = v | (operation >= 2 && carry ? 1L << bits : 0);
            int n = count % width;
            boolean left = (operation & 1) == 0;
            long rotated = left ? (ring << n) | (ring >>> (width - n))
                : (ring >>> n) | (ring << (width - n));
            rotated &= ringMask;
            result = rotated & mask;
            outCarry = operation >= 2 ? (rotated & (1L << bits)) != 0
                : left ? (result & 1) != 0 : (result & (1L << (bits - 1))) != 0;
        } else {
            boolean negative = (v & (1L << (bits - 1))) != 0;
            if (operation == 4 || operation == 6) {
                result = (v << count) & mask;
                outCarry = count <= bits && ((v >>> (bits - count)) & 1) != 0;
            } else {
                result = operation == 5 ? ((negative ? v | ~mask : v) >> count) & mask : v >>> count;
                outCarry = count <= bits ? ((v >>> (count - 1)) & 1) != 0
                    : operation == 5 && negative;
            }
        }
        Cpu cpu = new Cpu(new Memory(code), r);
        cpu.step();
        int actualRegister = index < 16 ? r.getBankGP32(index / 4, index % 4)
            : r.getDword32(index - 12);
        long actual = (actualRegister >>> lane) & mask;
        require(actual == result && r.flagC() == outCarry,
            "shift result/carry: addressing=" + addressing + " size=" + size
                + " op=" + operation + " count=" + encoded);
        require(r.flagS() == ((result & (1L << (bits - 1))) != 0)
            && r.flagZ() == (result == 0) && !r.flagH() && !r.flagN(), "shift flags");
        if (size < 4) require(r.flagV() == ((Long.bitCount(result) & 1) == 0), "shift parity");
        require(r.getPC() == NgpLoader.ROM_BASE + code.length, "instruction length");
        require((actualRegister & ~field) == (initial & ~field), "preserve sibling register lanes");
    }

    private static void unsignedDivide(int dividend, int divisor, boolean immediate) {
        byte[] code = immediate ? bytes(0xd8, 0x0a, divisor & 255, divisor >>> 8)
            : bytes(0xd9, 0x50);
        Registers r = initialized(0);
        r.setXWA(dividend);
        r.setBC(divisor);
        new Cpu(new Memory(code), r).step();
        int quotient = Integer.divideUnsigned(dividend, divisor);
        int remainder = Integer.remainderUnsigned(dividend, divisor);
        boolean overflow = quotient > 0xffff;
        require(r.flagV() == overflow, "unsigned DIV overflow");
        if (!overflow) require(r.getXWA() == (remainder << 16 | quotient),
            "DIV must treat the full 32-bit dividend as unsigned");
        require(r.getPC() == NgpLoader.ROM_BASE + code.length, "DIV instruction length");
    }

    private static void registerFile(int current, int address, int size, boolean write) {
        Registers r = initialized(current);
        int bank = address < 0x40 ? address >> 4
            : address < 0xe0 ? (current + 3) & 3 : current;
        int pair = (address & 15) >> 2;
        int index = address >= 0xf0 ? 16 + pair : bank * 4 + pair;
        int lane = (address & (4 - size)) * 8;
        int mask = size == 4 ? -1 : (1 << (size * 8)) - 1;
        int[] expected = new int[20];
        for (int b = 0; b < 4; b++)
            for (int p = 0; p < 4; p++) expected[b * 4 + p] = r.getBankGP32(b, p);
        for (int p = 0; p < 4; p++) expected[16 + p] = r.getDword32(4 + p);
        int accumulator = current * 4;
        if (write) {
            int source = expected[accumulator] & mask;
            expected[index] = (expected[index] & ~(mask << lane)) | (source << lane);
        } else {
            int source = (expected[index] >>> lane) & mask;
            expected[accumulator] = (expected[accumulator] & ~mask) | source;
        }
        int prefix = size == 1 ? 0xc7 : size == 2 ? 0xd7 : 0xe7;
        int opcode = write ? (size == 1 ? 0x99 : 0x98) : (size == 1 ? 0x89 : 0x88);
        int flags = r.getSR();
        new Cpu(new Memory(bytes(prefix, address, opcode)), r).step();
        for (int b = 0; b < 4; b++)
            for (int p = 0; p < 4; p++)
                require(r.getBankGP32(b, p) == expected[b * 4 + p], "bank/lane register access");
        for (int p = 0; p < 4; p++)
            require(r.getDword32(4 + p) == expected[16 + p], "index register lane access");
        require(r.getSR() == flags && r.getPC() == NgpLoader.ROM_BASE + 3,
            "register LD preserves status and consumes three bytes");
    }

    private static Registers initialized(int bank) {
        Registers r = new Registers();
        r.setPC(NgpLoader.ROM_BASE);
        r.setSR(0x0897 | bank << 8);
        for (int b = 0; b < 4; b++)
            for (int p = 0; p < 4; p++) r.setBankGP32(b, p, 0xa1b2c3d4 ^ b * 0x11111111 ^ p * 0x01020304);
        for (int p = 0; p < 4; p++) r.setDword32(4 + p, 0x12345678 ^ p * 0x11111111);
        return r;
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
