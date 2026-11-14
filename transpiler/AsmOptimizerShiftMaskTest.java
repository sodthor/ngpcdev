import java.nio.file.*;
import java.util.*;
import sim.Cpu;
import sim.Memory;
import sim.Registers;

/** Logical-shift masks: exhaustive byte algebra, negative gates, real opcodes. */
public final class AsmOptimizerShiftMaskTest {
    private static int checks;
    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static List<String> fold(List<String> lines) {
        return AsmPatterns.experimentalFoldLogicalRightLeftShifts(
            AsmPatterns.narrowZeroExtendedByteShifts(AsmPatterns.foldMasksThroughLogicalShifts(lines)));
    }
    public static void main(String[] args) throws Exception {
        Random random = new Random(900);
        for (String reg : List.of("a", "c", "e", "l", "wa", "bc", "de", "hl", "ix", "iy", "iz",
                "xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
            int bits = AsmPatterns.width(reg);
            long widthMask = (1L << bits) - 1;
            for (int count = 1; count <= 16; count++) {
                long before = random.nextLong() & widthMask, after = random.nextLong() & widthMask;
                List<String> input = List.of("\tand " + reg + ", " + before,
                    "\tsrl " + count + ", " + reg, "\tand " + reg + ", " + after);
                List<String> output = fold(input);
                require(output.size() == 2, "mask fold " + reg);
                long combined = AsmOptimizer.parseAsmImmediate(AsmPatterns.parse(output.get(1)).right()).orElseThrow();
                for (int seed = 0; seed < 256; seed++) {
                    long value = bits == 8 ? seed : random.nextLong() & widthMask;
                    require(((value & before) >>> count & after) == ((value >>> count) & combined),
                        "mask algebra " + reg + " " + count);
                }
                require(fold(output).equals(output), "stable mask fold");
            }
        }
        for (String word : List.of("wa", "bc", "de", "hl")) {
            String low = word.substring(1), high = word.substring(0, 1);
            for (String clear : List.of("\tld " + word + ", 0", "\txor " + high + ", " + high)) {
                for (int count = 1; count <= 8; count++) {
                    List<String> input = List.of(clear, "\tld " + low + ", (xsp+4)",
                        "\tsrl " + count + ", " + word, "\tand " + word + ", 0ffh", "\tcp " + word + ", 7");
                    require(fold(input).equals(List.of(clear, input.get(1), "\tsrl " + count + ", " + low, input.get(4))),
                        "byte narrowing " + word);
                    List<String> marshalled = new ArrayList<>(input);
                    marshalled.add(4, "\tpush " + word);
                    require(!fold(marshalled).contains(input.get(3)), "flags dead across argument push");
                    for (String hazard : List.of("\tjr Z, .Ltarget", "\tadc wa, 0", "\tpush sr",
                            "\tcall _opaque", "\tunknown", "\tret")) {
                        List<String> blocked = new ArrayList<>(input);
                        blocked.set(4, hazard);
                        require(fold(blocked).equals(blocked), "flags live " + hazard);
                    }
                }
            }
        }
        for (String hazard : List.of("\tsra 4, wa", "\trrc 4, wa", "\tsrl 0, wa", "\tsrl 17, wa",
                "\tsrl 4, bc", ".Lentry:", "\tcall _opaque")) {
            List<String> input = List.of("\tand wa, 255", hazard, "\tand wa, 255");
            require(fold(input).equals(input), "mask barrier " + hazard);
        }
        require(fold(List.of("\tand wa, 255 ; first", "\tsrl 4, wa", "\tand wa, 255 ; last"))
            .equals(List.of("\t; first", "\tsrl 4, wa", "\tand wa, 0fh ; last")), "comments retained");
        rightLeftChecks();
        mergeChecks();
        if (args.length > 0 && args[0].equals("--assemble")) assembled();
        System.out.println("AsmOptimizerShiftMaskTest: PASS (" + checks + " checks)");
    }
    private static void mergeChecks() {
        for (String op : List.of("sla", "srl", "sra"))
            for (String reg : List.of("wa", "bc", "de", "hl", "ix", "iy", "iz", "xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
                List<String> input = List.of("\t" + op + " 7, " + reg, "\t" + op + " 1, " + reg,
                    "\tld (xsp+12), " + reg, "\tld a, (xsp+30)", "\textz wa", "\tor wa, (xsp+12)");
                var output = AsmOptimizer.mergeConsecutiveShifts(input);
                require(output.size() == input.size() - 1 && output.get(0).equals("\t" + op + " 8, " + reg), "merge through flag-neutral EXTZ " + op + " " + reg);
                require(AsmOptimizer.mergeConsecutiveShifts(output).equals(output), "merged shift stable");
                for (String hazard : List.of("\tadc wa, 0", "\tsbc wa, 0", "\tjr Z, .Ltarget", "\tpush sr", "\tcall _opaque", "\tunknown", "\tret")) {
                    var blocked = new ArrayList<>(input);
                    blocked.set(5, hazard);
                    require(AsmOptimizer.mergeConsecutiveShifts(blocked).equals(blocked), "EXTZ does not kill live flags " + hazard);
                }
            }
        var separated = List.of("\tsla 7, wa", "\tadd xhl, 1", "\tsla 1, wa", "\tld (xhl), wa", "\tret");
        require(AsmOptimizer.mergeConsecutiveShifts(separated).equals(separated), "do not move shifts across flag writer");
        var unknown = List.of("\tsla 7, wa", "\tunknown", "\tsla 1, wa", "\tcp wa, 0");
        require(AsmOptimizer.mergeConsecutiveShifts(unknown).equals(unknown), "unknown between shifts");
        var sprite = new ArrayList<String>(List.of(AsmPatterns.BANK_ZERO_ABI, "_generic_writer:", AsmPatterns.VOID_SCRATCH_ABI));
        sprite.addAll(List.of("\tld a, (xsp+32)", "\textz wa", "\tsla 7, wa", "\tsla 1, wa",
            "\tld (xsp+12), wa", "\tld a, (xsp+30)", "\textz wa", "\tor wa, (xsp+12)", "\tld (xhl), wa", "\tret"));
        var result = AsmOptimizer.runPasses(sprite, false);
        require(result.contains("\tsla 8, wa") && !result.contains("\tsla 7, wa"), "whole pipeline merges portable-C writer pattern");
    }
    private static void rightLeftChecks() {
        Random random = new Random(901);
        for (String reg : List.of("w", "a", "b", "c", "d", "e", "h", "l",
                "wa", "bc", "de", "hl", "ix", "iy", "iz",
                "xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
            int bits = AsmPatterns.width(reg);
            long widthMask = (1L << bits) - 1;
            for (int right = 1; right < Math.min(bits - 1, 16); right++)
                for (int left = right + 1; left <= Math.min(bits - 1, 16); left++) {
                    List<String> input = List.of("\tsrl " + right + ", " + reg,
                        "\tsla " + left + ", " + reg, "\tadd xiz, xbc");
                    List<String> output = fold(input);
                    require(AsmPatterns.parse(output.get(0)).op().equals("and"), "right/left fold " + reg);
                    long mask = AsmOptimizer.parseAsmImmediate(AsmPatterns.parse(output.get(0)).right()).orElseThrow();
                    for (int seed = 0; seed < 256; seed++) {
                        long value = bits == 8 ? seed : seed < bits ? 1L << seed : random.nextLong() & widthMask;
                        require(((value >>> right) << left & widthMask)
                            == ((value & mask) << (left - right) & widthMask), "right/left algebra " + reg);
                    }
                    require(fold(output).equals(output), "right/left idempotence");
                }
        }
        List<String> input = List.of("\tsrl 8, xiz ; row", "\tsla 9, xiz ; stride", "\tadd xiz, xde2");
        require(fold(input).equals(List.of("\tand xiz, 07fffff00h ; row", "\tsla 1, xiz ; stride", input.get(2))),
            "MODE7 general mask and comments");
        for (String hazard : List.of("\tadc xiz, xbc", "\tsbc xiz, xbc", "\tjr C, .Lnext",
                "\tscc Z, a", "\tpush sr", "\tld rfp, 1", "\tcall _opaque", "\tunknown", "\tret")) {
            List<String> blocked = List.of(input.get(0), input.get(1), hazard);
            require(fold(blocked).equals(blocked), "right/left live flags " + hazard);
        }
        for (String hazard : List.of("\tsla 9, xiy", "\tsra 9, xiz", "\tsla 8, xiz",
                "\tsla 7, xiz", "\tsla 17, xiz", ".Lentry:")) {
            List<String> blocked = List.of(input.get(0), hazard, input.get(2));
            require(fold(blocked).equals(blocked), "right/left invalid pair " + hazard);
        }
        for (List<String> blocked : List.of(
                List.of("srl 0, xiz", "sla 9, xiz", "add xiz, xbc"),
                List.of("srl 1, a", "sla 8, a", "add xiz, xbc"),
                List.of("srl 8, xiz", ".Lentry:", "sla 9, xiz", "add xiz, xbc"),
                List.of("srl 8, xiz", "nop", "sla 9, xiz", "add xiz, xbc")))
            require(fold(blocked).equals(blocked), "right/left barrier");
    }
    private static void assembled() throws Exception {
        Path dir = Files.createTempDirectory("shift-mask-execution-");
        for (int shape = 0; shape < 5; shape++) {
            List<String> body = switch (shape) {
                case 0 -> List.of("\tand wa, 255", "\tsrl 4, wa", "\tand wa, 255", "\tld (04000h), wa", "\tpush sr", "\tpop bc", "\tld (04002h), bc");
                case 1 -> List.of("\txor w, w", "\tld a, c", "\tsrl 3, wa", "\tand wa, 255", "\tcp wa, 7", "\tld (04000h), wa", "\tpush sr", "\tpop bc", "\tld (04002h), bc");
                case 2 -> List.of("\tand xwa, 0ffffh", "\tsrl 8, xwa", "\tand xwa, 0ffh", "\tld (04000h), wa", "\tpush sr", "\tpop bc", "\tld (04002h), bc");
                case 3 -> List.of("\tsrl 8, xwa", "\tsla 9, xwa", "\tadd xwa, xbc", "\tld (04000h), wa", "\tpush sr", "\tpop bc", "\tld (04002h), bc");
                default -> List.of("\textz wa", "\tsla 7, wa", "\tsla 1, wa", "\tld (04004h), wa",
                    "\tld a, c", "\textz wa", "\tor wa, (04004h)", "\tld (04000h), wa", "\tpush sr", "\tpop bc", "\tld (04002h), bc");
            };
            byte[][] binary = new byte[2][];
            for (int variant = 0; variant < 2; variant++) {
                String name = "case" + shape + "_" + variant;
                List<String> code = new ArrayList<>(List.of("\tcpu 93C141", "\tmaxmode on", "\torg 0200000h"));
                code.addAll(variant == 0 ? body : shape == 4 ? AsmOptimizer.mergeConsecutiveShifts(body) : fold(body));
                code.addAll(List.of("\tret", "\tend"));
                Files.write(dir.resolve(name + ".asm"), code);
                run(dir, "asl.exe", name + ".asm");
                run(dir, "p2bin.exe", "-r", "$-$", name + ".p", name + ".bin");
                binary[variant] = Files.readAllBytes(dir.resolve(name + ".bin"));
            }
            for (int value = 0; value < 256; value++) for (int flags : new int[]{0, 0xff})
                require(execute(binary[0], value, flags).equals(execute(binary[1], value, flags)),
                    "assembled result/flags " + shape + " " + value);
        }
        System.out.println("Shift/mask assembled execution: PASS (5120 executions; artifacts " + dir + ")");
    }
    private static void run(Path dir, String tool, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("wine", Path.of("../../as/bin", tool).toAbsolutePath().normalize().toString()));
        command.addAll(List.of(args));
        Path log = dir.resolve("command.log");
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (process.waitFor() != 0) throw new AssertionError(Files.readString(log));
    }
    private static List<Integer> execute(byte[] binary, int value, int flags) {
        Memory memory = new Memory(binary);
        Registers registers = new Registers();
        registers.setSR(flags);
        registers.setXWA(0xa5a50000 | value);
        registers.setXBC(value);
        registers.setXSP(0x6b00);
        memory.writeDword(0x6b00, 0x123456);
        registers.setPC(0x200000);
        Cpu cpu = new Cpu(memory, registers);
        for (int step = 0; step < 100; step++) {
            if (registers.getPC() == 0x123456) return List.of(registers.getXWA(), registers.getXBC(),
                registers.getXSP(), memory.readWord(0x4000), memory.readWord(0x4002));
            cpu.step();
        }
        throw new AssertionError("execution did not return");
    }
}