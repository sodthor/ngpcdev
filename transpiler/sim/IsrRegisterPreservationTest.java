package sim;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Regression: an __interrupt handler must preserve EVERY general/index register,
 * because it can preempt any code at any instruction. update() holds live values
 * in XIZ (and XIX/XIY), so a handler that clobbers one of those without restoring
 * it corrupts update()'s tile-address math -> track garbage + sky corruption.
 *
 * This drives each interrupt handler as the hardware would: seed all callee-visible
 * registers with sentinels, "fire" the handler (push a fake return, jump in, run to
 * reti), and require every sentinel to survive.
 */
public final class IsrRegisterPreservationTest {
    private static final int ROM_BASE = 0x200000;
    private static final int FAKE_RETURN = 0x20FFF0; // in-ROM sentinel return address

    private IsrRegisterPreservationTest() {}

    public static void main(String[] args) throws Exception {
        String romPath = args.length > 0 ? args[0] : "../mode7/MODE7.ngp";
        String mapPath = args.length > 1 ? args[1] : "../mode7/MODE7.map";
        Map<String, Integer> symbols = loadMap(mapPath);

        checkHandler(romPath, symbols, "_MYVBLINTERRUPT");

        System.out.println("IsrRegisterPreservationTest: PASS");
    }

    private static void checkHandler(String romPath, Map<String, Integer> symbols, String name)
            throws Exception {
        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory memory = new Memory(rom.data);
        Registers registers = new Registers();
        Cpu cpu = new Cpu(memory, registers);
        memory.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int address) {
                if (address == 0x6F82) return 0;
                if (address == 0x6F85) return 0;   // USR_SHUTDOWN clear
                if (address == 0x6F87) return 1;
                if (address == 0x00BC) return 0;
                return -1;
            }
            @Override public void write(int address, int value) {}
        });
        cpu.setSwiHandler((number, regs, mem) -> true);
        NgpLoader.init(rom, memory, registers);

        int handler = symbol(symbols, name);

        // Sentinels distinct enough that any partial clobber is visible.
        int sWA = 0x11112222, sBC = 0x33334444, sDE = 0x55556666, sHL = 0x77778888;
        int sIX = 0x9999AAAA, sIY = 0xBBBBCCCC, sIZ = 0xDDDDEEEE;
        registers.setXWA(sWA); registers.setXBC(sBC); registers.setXDE(sDE); registers.setXHL(sHL);
        registers.setXIX(sIX); registers.setXIY(sIY); registers.setXIZ(sIZ);

        // Simulate hardware dispatch: push the (far) return address, jump to the handler.
        int sp = registers.getXSP() - 4;
        registers.setXSP(sp);
        memory.writeDword(sp, FAKE_RETURN);
        registers.setPC(handler);

        long steps = 0;
        while (steps < 5_000_000 && registers.getPC() != FAKE_RETURN) {
            cpu.step();
            steps++;
        }
        require(registers.getPC() == FAKE_RETURN, name + " did not return via reti");

        // XWA/XHL/XDE/XBC are scratch the handler legitimately uses and restores;
        // XIX/XIY/XIZ are the ones update() relies on across an interrupt.
        requireReg(name, "XIZ", sIZ, registers.getXIZ());
        requireReg(name, "XIY", sIY, registers.getXIY());
        requireReg(name, "XIX", sIX, registers.getXIX());
        requireReg(name, "XBC", sBC, registers.getXBC());
        requireReg(name, "XDE", sDE, registers.getXDE());
        requireReg(name, "XHL", sHL, registers.getXHL());
        requireReg(name, "XWA", sWA, registers.getXWA());
        System.out.printf("  %s preserved all registers (%d instructions)%n", name, steps);
    }

    private static void requireReg(String h, String reg, int expected, int actual) {
        require(expected == actual,
            String.format("%s clobbered %s: expected %08X, got %08X", h, reg, expected, actual));
    }

    private static Map<String, Integer> loadMap(String path) throws Exception {
        Map<String, Integer> symbols = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 3 && parts[0].startsWith("_") && parts[1].equals("Int")) {
                    symbols.put(parts[0], Integer.parseInt(parts[2], 16));
                }
            }
        }
        return symbols;
    }

    private static int symbol(Map<String, Integer> symbols, String name) {
        Integer address = symbols.get(name);
        if (address == null) throw new IllegalStateException("symbol not found: " + name);
        return address;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
