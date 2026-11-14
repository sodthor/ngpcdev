import java.nio.file.*;
import java.util.*;
import sim.*;

public final class AsmOptimizerMemoryCounterTest {
    static long executionCycles;
    public static void main(String[] args) throws Exception {
        for (String reg : List.of("a", "w", "b", "c", "d", "e", "h", "l"))
            for (String op : List.of("inc", "dec", "add", "sub")) for (int count = 1; count <= 8; count++) {
                String instruction = op.matches("add|sub") ? op + " " + reg + ", " + count : op + " " + count + ", " + reg;
                String foldedOp = op.equals("add") ? "inc" : op.equals("sub") ? "dec" : op;
                List<String> input = List.of("\tld " + reg + ", (_Counter)", "\t" + instruction,
                    "\tld (_Counter), " + reg, "\tld " + reg + ", 0", "\tcp wa, 0");
                List<String> folded = AsmPatterns.foldByteMemoryCounters(input);
                if (!folded.equals(List.of("\t" + foldedOp + "b " + count + ", (_Counter)", input.get(3), input.get(4))))
                    throw new AssertionError("counter fold " + input);
                for (String hazard : List.of("\tpush " + reg, "\tadc wa, 0", "\tjr Z, .Lnext", "\tcall _opaque", "\tret")) {
                    List<String> blocked = new ArrayList<>(input);
                    blocked.set(3, hazard);
                    if (!AsmPatterns.foldByteMemoryCounters(blocked).equals(blocked)) throw new AssertionError("live/barrier " + hazard);
                }
                if (!AsmPatterns.foldByteMemoryCounters(folded).equals(folded)) throw new AssertionError("idempotence");
            }
        for (String address : List.of("(xhl)", "(06f82h)", "(xsp+4)")) {
            List<String> blocked = List.of("ld a, " + address, "inc 1, a", "ld " + address + ", a", "ld xwa, 0", "cp wa, 0");
            if (!AsmPatterns.foldByteMemoryCounters(blocked).equals(blocked)) throw new AssertionError("address " + address);
        }
        for (String op : List.of("add", "sub")) for (int count : new int[]{9, 255}) {
            List<String> input = List.of("ld a, (_Counter)", op + " a, " + count,
                "ld (_Counter), a", "ld xwa, 0", "cp wa, 0");
            if (!AsmPatterns.foldByteMemoryCounters(input).get(0).equals(op + "b (_Counter), " + Ir2Tlcs900.fmtImm(count)))
                throw new AssertionError("large immediate " + op + count);
        }
        if (args.length > 0 && args[0].equals("--assemble")) executeChecks();
        System.out.println("AsmOptimizerMemoryCounterTest: PASS");
    }
    static void executeChecks() throws Exception {
        Path dir = Files.createTempDirectory("memory-counter-");
        int executions = 0;
        for (String op : List.of("inc", "dec", "add", "sub")) for (int count : new int[]{1, 8}) {
            byte[][] bins = new byte[2][];
            String instruction = op.matches("add|sub") ? op + " a, " + count : op + " " + count + ", a";
            // LD does not overwrite flags: compare the final arithmetic flags
            // directly, including carry differences between ADD and INC.
            List<String> body = List.of("\tld a, (_Counter)", "\t" + instruction,
                "\tld (_Counter), a", "\tld xwa, 0", "\tret");
            for (int variant = 0; variant < 2; variant++) {
                String name = op + count + "_" + variant;
                List<String> asm = new ArrayList<>(List.of("\tcpu 93C141", "\tmaxmode on", "_Counter equ 04000h", "\torg 0200000h"));
                asm.addAll(variant == 0 ? body : AsmPatterns.foldByteMemoryCounters(body));
                asm.add("\tend");
                Files.write(dir.resolve(name + ".asm"), asm);
                for (String tool : List.of("asl.exe", "p2bin.exe")) {
                    List<String> command = new ArrayList<>(List.of("wine", Path.of("../../as/bin", tool).toAbsolutePath().normalize().toString()));
                    command.addAll(tool.equals("asl.exe") ? List.of(name + ".asm") : List.of("-r", "$-$", name + ".p", name + ".bin"));
                    Path log = dir.resolve("command.log");
                    Process p = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                    if (p.waitFor() != 0) throw new AssertionError(Files.readString(log));
                }
                bins[variant] = Files.readAllBytes(dir.resolve(name + ".bin"));
            }
            for (int value = 0; value < 256; value++) for (int flags : new int[]{0, 255}) {
                List<Integer> before = run(bins[0], value, flags), after = run(bins[1], value, flags);
                if (!before.equals(after)) throw new AssertionError("execution " + op + count + " " + value);
                executions += 2;
            }
            run(bins[0], 10, 0); long beforeCycles = executionCycles;
            run(bins[1], 10, 0); long afterCycles = executionCycles;
            System.out.printf("%s %d: bytes %d -> %d; modeled cycles %d -> %d (including common LD/RET)%n",
                op, count, bins[0].length, bins[1].length, beforeCycles, afterCycles);
        }
        System.out.println("Memory counter execution: PASS (" + executions + " executions; " + dir + ")");
    }
    static List<Integer> run(byte[] bin, int value, int flags) {
        Memory m = new Memory(bin);
        Registers r = new Registers();
        r.setSR(flags); r.setXWA(0x12345678); r.setXSP(0x6b00); r.setPC(0x200000);
        m.writeByte(0x4000, value); m.writeByte(0x4001, 0xab); m.writeDword(0x6b00, 0x20fff0);
        Cpu cpu = new Cpu(m, r);
        for (int i = 0; i < 100; i++) {
            if (r.getPC() == 0x20fff0) {
                executionCycles = cpu.getCycles();
                return List.of(m.readByte(0x4000), m.readByte(0x4001), r.getXWA(), r.getXSP(), r.getSR());
            }
            cpu.step();
        }
        throw new AssertionError("return");
    }
}