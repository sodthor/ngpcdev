package sim.tests;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import sim.*;

/** Executes the actual cc900 ROM helpers, without replacing them with host arithmetic. */
public final class Cc900DivisionTest {
    private static byte[] rom;
    private static Map<String, Integer> symbols;
    private static int executions;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected cc900 ROM and linker map");
        rom = Files.readAllBytes(Path.of(args[0]));
        symbols = new HashMap<>();
        String pending = null;
        for (String line : Files.readAllLines(Path.of(args[1]))) {
            String[] fields = line.strip().split("\\s+");
            if (fields[0].startsWith("C9H_")) {
                if (fields.length == 1) pending = fields[0];
                else symbols.put(fields[0], Integer.parseInt(fields[1], 16));
            } else if (pending != null && fields[0].matches("[0-9A-Fa-f]{4,8}")) {
                symbols.put(pending, Integer.parseInt(fields[0], 16));
                pending = null;
            }
        }
        int[] values = {Integer.MIN_VALUE, -123456789, -65537, -32769, -1664, -1,
            0, 1, 2, 63, 32767, 32768, 65535, 65536, 123456789, Integer.MAX_VALUE};
        for (int left : values)
            for (int right : values)
                if (right != 0) check(left, right);
        Random random = new Random(900);
        for (int i = 0; i < 1000; i++) {
            int right = random.nextInt();
            if (right != 0) check(random.nextInt(), right);
        }
        System.out.printf("Cc900DivisionTest: PASS (%d helper executions)%n", executions);
    }

    private static void check(int left, int right) {
        execute("C9H_divlu", left, right, Integer.divideUnsigned(left, right));
        execute("C9H_remlu", left, right, Integer.remainderUnsigned(left, right));
        if (left != Integer.MIN_VALUE || right != -1) {
            execute("C9H_divls", left, right, left / right);
            execute("C9H_remls", left, right, left % right);
        }
    }

    private static void execute(String name, int left, int right, int expected) {
        Integer address = symbols.get(name);
        if (address == null) throw new IllegalArgumentException("Missing helper " + name);
        Memory memory = new Memory(rom);
        Registers r = new Registers();
        int bank = executions & 3;
        for (int b = 0; b < 4; b++)
            for (int p = 0; p < 4; p++) r.setBankGP32(b, p, bankValue(b, p));
        r.setPC(address);
        r.setXSP(0x6b00);
        r.setSR(0x0800 | bank << 8);
        r.setXWA(left);
        r.setXBC(right);
        r.setXDE(0xabcdef01);
        r.setXIY(0x12345678);
        memory.setStackBase(0x6b00);
        memory.writeDword(0x6b00, 0x200000);
        Cpu cpu = new Cpu(memory, r);
        int steps = 0;
        while (r.getPC() != 0x200000 && steps++ < 10000) cpu.step();
        if (r.getPC() != 0x200000 || r.getXSP() != 0x6b04
                || r.getXHL() != expected || r.getXIY() != 0x12345678 || r.bank() != bank)
            throw new AssertionError(name + "(" + left + "," + right + "): expected "
                + expected + ", got " + r.getXHL() + ", PC=" + Integer.toHexString(r.getPC()));
        for (int b = 0; b < 4; b++)
            for (int p = 0; p < 4; p++)
                if (b != bank && r.getBankGP32(b, p) != bankValue(b, p))
                    throw new AssertionError(name + " corrupted alternate bank " + b);
        executions++;
    }

    private static int bankValue(int bank, int pair) {
        return 0xa1b2c3d4 ^ bank * 0x11111111 ^ pair * 0x01020304;
    }
}
