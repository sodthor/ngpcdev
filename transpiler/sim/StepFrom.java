package sim;

/** Step from a given PC for N instructions, printing PC + opcode bytes + advance. */
public final class StepFrom {
    public static void main(String[] args) throws Exception {
        String romPath = args[0];
        int startPc = Integer.parseInt(args[1], 16);
        int n = args.length > 2 ? Integer.parseInt(args[2]) : 20;
        NgpLoader.NgpRom rom = NgpLoader.load(romPath);
        Memory mem = new Memory(rom.data);
        Registers reg = new Registers();
        Cpu cpu = new Cpu(mem, reg);
        mem.setIoDevice(new Memory.IoDevice() {
            @Override public int read(int a) { return -1; }
            @Override public void write(int a, int v) {}
        });
        cpu.setSwiHandler((num, r, m) -> true);
        // Give a sane stack so RET/RETI don't wander.
        reg.setXSP(0x6B00);
        reg.setPC(startPc);
        for (int i = 0; i < n; i++) {
            int pc = reg.getPC();
            System.out.printf("PC=%06X  %02X %02X %02X %02X%n", pc,
                mem.readByte(pc)&0xFF, mem.readByte(pc+1)&0xFF,
                mem.readByte(pc+2)&0xFF, mem.readByte(pc+3)&0xFF);
            cpu.step();
        }
    }
}
