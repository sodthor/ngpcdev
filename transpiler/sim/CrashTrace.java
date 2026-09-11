package sim;

import java.util.ArrayDeque;
import java.util.Deque;

/** Trace the last instructions before the program leaves ROM, to locate the crash. */
public final class CrashTrace {
    public static void main(String[] args) throws Exception {
        String romPath = args.length > 0 ? args[0] : "../mode7/MODE7.ngp";
        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory mem = new Memory(rom.data);
        Registers reg = new Registers();
        Cpu cpu = new Cpu(mem, reg);
        mem.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int a) {
                if (a == 0x6F82) return 0; if (a == 0x6F85) return 0;
                if (a == 0x6F87) return 1; if (a == 0x00BC) return 0;
                return -1;
            }
            @Override public void write(int a, int v) {}
        });
        cpu.setSwiHandler((n, r, m) -> true);
        NgpLoader.init(rom, mem, reg);

        final int ROM_BASE = 0x200000, ROM_END = ROM_BASE + rom.data.length;
        Deque<String> hist = new ArrayDeque<>();
        long instr = 0;
        int prevPc = reg.getPC();
        while (instr < 2_000_000) {
            int pc = reg.getPC();
            if (pc < ROM_BASE || pc >= ROM_END) {
                System.out.printf("LEFT ROM at instr %d: PC=%06X (from %06X) SP=%08X%n",
                    instr, pc, prevPc, reg.getXSP());
                System.out.println("--- last " + hist.size() + " instructions (oldest first) ---");
                for (String s : hist) System.out.println(s);
                return;
            }
            hist.addLast(String.format("[%d] PC=%06X op=%02X %02X %02X SP=%08X XWA=%08X XBC=%08X XDE=%08X XHL=%08X XIX=%08X XIY=%08X XIZ=%08X",
                instr, pc, mem.readByte(pc)&0xFF, mem.readByte(pc+1)&0xFF, mem.readByte(pc+2)&0xFF,
                reg.getXSP(), reg.getXWA(), reg.getXBC(), reg.getXDE(), reg.getXHL(),
                reg.getXIX(), reg.getXIY(), reg.getXIZ()));
            if (hist.size() > 60) hist.removeFirst();
            prevPc = pc;
            cpu.step();
            instr++;
        }
        System.out.println("no crash within limit");
    }
}
