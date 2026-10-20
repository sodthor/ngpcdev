import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import sim.Cpu;
import sim.Memory;
import sim.Registers;

public final class Ir2Tlcs900CorrectnessTest {
    private static final int ROM_BASE = 0x200000;
    private static final int STACK_BASE = 0x6B00;
    private static final int DATA_BASE = 0x4000;
    private static final byte[] SEED = new byte[128];
    private static final Path TOOLCHAIN = Path.of("../../as/bin").toAbsolutePath().normalize();
    private record Fixture(String name, int bits, int expected, byte[] expectedMemory) {}
    private static final List<Fixture> fixtures = new ArrayList<>();
    private static Path output;
    private static int executions;

    private static void command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).directory(output.toFile())
                .redirectErrorStream(true).start();
        String text = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new AssertionError(String.join(" ", args) + "\n" + text);
    }

    private static void check(String name, String body, int bits, int expected,
                              byte[] expectedMemory) throws Exception {
        String ir = "define i" + bits + " @probe() {\nentry:\n" + body + "\n}\n"
                + "declare void @llvm.memmove.p0.p0.i32(ptr, ptr, i32, i1 immarg)\n"
                + "declare void @llvm.memcpy.p0.p0.i32(ptr, ptr, i32, i1 immarg)\n"
                + "declare void @llvm.memset.p0.i32(ptr, i8, i32, i1 immarg)\n";
            checkModule(name, ir, bits, expected, expectedMemory);
            }

            private static void checkModule(String name, String ir, int bits, int expected,
                            byte[] expectedMemory) throws Exception {
        verifyModule(name, ir);
        String assembly = new Ir2Tlcs900.Translator(ir, "probe").translate()
                .replaceAll("(?m)^\\s*include\\s+.*$", "")
                .replace("; ---- code ----", "\torg 0200000h\n; ---- code ----");
        Files.writeString(output.resolve(name + ".asm"), assembly);
        fixtures.add(new Fixture(name, bits, expected, expectedMemory));
        Files.write(output.resolve(name + "_opt.asm"),
                AsmOptimizer.runPasses(assembly.lines().toList(), false));
        fixtures.add(new Fixture(name + "_opt", bits, expected, expectedMemory));
    }

    private static void verifyModule(String name, String ir) throws Exception {
        Files.writeString(output.resolve(name + ".ll"), ir);
        command(System.getenv().getOrDefault("CLANG", "clang-22"), "-x", "ir", "-S",
                "-emit-llvm", name + ".ll", "-o", name + ".verified.ll");
    }

    private static String checkAllocated(String name, String body, byte[] expectedMemory) throws Exception {
        return checkAllocated(name, body, expectedMemory, true);
    }

    private static String checkAllocated(String name, String body, byte[] expectedMemory,
                                         boolean compareLegacy) throws Exception {
        String ir = "define i32 @probe() {\n" + body + "\n}\n";
        if (compareLegacy) checkModule(name, ir, 32, 17, expectedMemory);
        else verifyModule(name, ir);
        var translator = new Ir2Tlcs900.Translator(ir, "probe");
        translator.experimentalRegisterFunctions.add("probe");
        String assembly = translator.translate()
            .replaceAll("(?m)^\\s*include\\s+.*$", "")
            .replace("; ---- code ----", "\torg 0200000h\n; ---- code ----");
        require(assembly.contains("experimental loop frame"), name + ": gate not applied");
        Files.writeString(output.resolve(name + "_alloc.asm"), assembly);
        fixtures.add(new Fixture(name + "_alloc", 32, 17, expectedMemory));
        List<String> optimized = AsmOptimizer.runPasses(assembly.lines().toList(), false);
        require(protectedCode(optimized).equals(protectedCode(assembly.lines().toList())),
            name + ": protected function was rewritten");
        Files.write(output.resolve(name + "_alloc_opt.asm"), optimized);
        fixtures.add(new Fixture(name + "_alloc_opt", 32, 17, expectedMemory));
        return assembly;
    }

    private static List<List<String>> protectedCode(List<String> lines) {
        return AsmOptimizer.protectedFunctions(lines).stream()
            .map(f -> List.copyOf(lines.subList(f.start(), f.end()))).toList();
    }

    private static void execute(String name, int bits, int expected, byte[] expectedMemory) throws Exception {
        Memory memory = new Memory(Files.readAllBytes(output.resolve(name + ".bin")));
        Registers registers = new Registers();
        registers.setPC(ROM_BASE);
        registers.setXSP(STACK_BASE);
        registers.setXWA(0x12345678);
        registers.setXHL(0x76543210);
        boolean allocated = name.contains("_alloc");
        if (allocated) {
            registers.setXBC(0x13572468);
            registers.setXDE(0x24681357);
            registers.setXIX(0x10203040);
            registers.setXIY(0x50607080);
            registers.setXIZ(0x90A0B0C0);
            for (int bank = 1; bank <= 2; bank++)
                for (int pair = 0; pair < 4; pair++)
                    registers.setBankGP32(bank, pair, 0x56780000 + bank * 16 + pair);
        }
        memory.setStackBase(STACK_BASE);
        for (int index = 0; index < SEED.length; index++) memory.writeByte(DATA_BASE + index, SEED[index]);
        int returnAddress = ROM_BASE + memory.getCartRom().length;
        memory.writeDword(STACK_BASE, returnAddress);
        Cpu cpu = new Cpu(memory, registers);
        int steps = 0;
        while (registers.getPC() != returnAddress && steps++ < 100000) cpu.step();
        require(registers.getPC() == returnAddress, name + ": did not return");
        require(registers.getXSP() == STACK_BASE + 4, name + ": unbalanced stack");
        if (allocated) {
            require(registers.bank() == 0 && registers.getXBC() == 0x13572468
                && registers.getXDE() == 0x24681357 && registers.getXIX() == 0x10203040
                && registers.getXIY() == 0x50607080 && registers.getXIZ() == 0x90A0B0C0,
                name + ": primary callee-save corruption");
            for (int bank = 1; bank <= 2; bank++)
                for (int pair = 0; pair < 4; pair++)
                    require(registers.getBankGP32(bank, pair) == 0x56780000 + bank * 16 + pair,
                        name + ": bank callee-save corruption");
        }
        int mask = bits == 32 ? -1 : (1 << bits) - 1;
        int actual = registers.getXHL() & mask;
        require(actual == (expected & mask), name + ": expected " + (expected & mask) + ", got " + actual);
        if (expectedMemory != null) {
            for (int index = 0; index < expectedMemory.length; index++) {
                require(memory.readByte(DATA_BASE + index) == (expectedMemory[index] & 0xFF),
                        name + ": memory mismatch at byte " + index);
            }
        }
        executions++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message + " (artifacts: " + output + ")");
    }

    private static void booleans() throws Exception {
        for (int bits : new int[] {8, 16, 32}) {
            for (boolean truth : new boolean[] {false, true}) {
                check("sext_bool_" + bits + "_" + truth,
                        "  %r = sext i1 " + truth + " to i" + bits + "\n  ret i" + bits + " %r",
                        bits, truth ? -1 : 0, null);
            }
        }
        for (int value : new int[] {0, 1, 2, 3, 128, 255}) {
            check("trunc_bool_" + value, "  %b = trunc i8 " + value + " to i1\n"
                    + "  %r = zext i1 %b to i32\n  ret i32 %r", 32, value & 1, null);
        }
        check("trunc_branch", "  %b = trunc i8 2 to i1\n"
                + "  br i1 %b, label %yes, label %no\nyes:\n  ret i32 1\nno:\n  ret i32 0", 32, 0, null);
        check("bool_wrap", "  %b = add i1 true, true\n  %r = zext i1 %b to i32\n  ret i32 %r", 32, 0, null);
        for (int value : new int[] {0, 7}) {
            for (String predicate : List.of("eq", "ult")) {
                boolean truth = predicate.equals("eq") ? value == 0 : value < 4;
                check("masked_branch_" + predicate + "_" + value,
                    "  store volatile i32 " + value + ", ptr inttoptr (i32 16640 to ptr)\n"
                    + "  %value = load volatile i32, ptr inttoptr (i32 16640 to ptr)\n"
                    + "  %condition = icmp " + predicate + " i32 %value, " + (predicate.equals("eq") ? 0 : 4) + "\n"
                    + "  br i1 %condition, label %yes, label %no\nyes:\n"
                    + "  %left = add i32 %value, 11\n  ret i32 %left\nno:\n"
                    + "  %right = add i32 %value, 23\n  ret i32 %right", 32,
                    value + (truth ? 11 : 23), null);
            }
        }
    }

    private static void arithmetic() throws Exception {
        for (String operation : new String[] {"ashr", "sdiv", "srem"}) {
            for (int left : new int[] {-128, -7, 7, 127}) {
                for (boolean variable : new boolean[] {false, true}) {
                    int right = operation.equals("ashr") ? 1 : (left > 0 ? -2 : 2);
                    int expected = switch (operation) {
                        case "ashr" -> left >> right;
                        case "sdiv" -> left / right;
                        default -> left % right;
                    };
                    String body = variable
                            ? "  store volatile i8 " + left + ", ptr inttoptr (i32 16640 to ptr)\n"
                                + "  store volatile i8 " + right + ", ptr inttoptr (i32 16644 to ptr)\n"
                                + "  %left = load volatile i8, ptr inttoptr (i32 16640 to ptr)\n"
                                + "  %right = load volatile i8, ptr inttoptr (i32 16644 to ptr)\n"
                                + "  %r = " + operation + " i8 %left, %right\n"
                            : "  %r = " + operation + " i8 " + left + ", " + right + "\n";
                    check(operation + "_" + Math.abs(left) + "_" + (left < 0) + "_" + variable,
                            body + "  ret i8 %r", 8, expected, null);
                }
            }
        }
    }

    private static String counterLoop(int bits, long seed, String flags, String predicate, long limit) {
        String type = "i" + bits;
        boolean inverted = predicate.endsWith("uge");
        return "entry:\n  br label %header\nheader:\n"
            + "  %i = phi " + type + " [ " + seed + ", %entry ], [ %next, %latch ]\n"
            + "  br label %latch\nlatch:\n"
            + "  %next = add " + flags + type + " %i, 1\n"
            + "  %condition = icmp " + predicate + " " + type + " %i, " + limit + "\n"
            + "  br i1 %condition, label %" + (inverted ? "exit" : "header")
            + ", label %" + (inverted ? "header" : "exit") + "\nexit:\n"
            + "  ret " + type + " %i";
    }

    private static void counterRewrite(String name, String body, String expected) {
        List<String> lines = new ArrayList<>(
            ("define void @probe() {\n" + body + "\n}").lines().toList());
        var function = new Ir2Tlcs900.IrFunction("probe", "void", "", lines);
        var translator = new Ir2Tlcs900.FuncTranslator(function, Set.of(), Map.of());
        Map<String, String> replacements = translator.rebaseLoopCounterComparisons();
        require(expected == null ? replacements.isEmpty()
                : replacements.size() == 1 && replacements.containsValue(expected), name);
        translator.normalizeBody();
        String normalized = String.join("\n", lines);
        if (expected != null) require(normalized.contains(expected), name + ": normalization wiring");
        translator.normalizeBody();
        require(String.join("\n", lines).equals(normalized), name + ": normalization idempotence");
    }

    private static void counterComparisons() throws Exception {
        String loop = counterLoop(16, 0, "nuw nsw ", "ult", 15);
        String rebased = "%condition = icmp ult i16 %next, 16";
        counterRewrite("counter rebase", loop, rebased);
        counterRewrite("counter commuted increment", loop.replace("%i, 1\n", "1, %i\n"), rebased);
        counterRewrite("counter flags reversed", loop.replace("nuw nsw", "nsw nuw"), rebased);
        counterRewrite("counter nuw only", loop.replace("nuw nsw", "nuw"), rebased);
        counterRewrite("counter single-block loop",
            loop.replace("%next, %latch", "%next, %header")
                .replace("  br label %latch\nlatch:\n", ""), rebased);
        counterRewrite("counter comments and metadata",
            loop.replace("  %condition", "  ; latch comparison\n  %condition")
                .replace("%i, 15", "%i, 15, !dbg !1")
                .replace("label %exit\n", "label %exit, !llvm.loop !2\n"), rebased);
        counterRewrite("counter zero bound", counterLoop(16, 0, "nuw ", "ult", 0),
            "%condition = icmp ult i16 %next, 1");
        counterRewrite("counter samesign boundary",
            counterLoop(16, 0, "nuw ", "samesign ult", 32767),
            "%condition = icmp ult i16 %next, 32768");
        counterRewrite("counter no nowrap", loop.replace("nuw nsw ", ""), null);
        counterRewrite("counter signed nowrap only", loop.replace("nuw ", ""), null);
        counterRewrite("counter signed comparison", loop.replace("icmp ult", "icmp slt"), null);
        counterRewrite("counter byte width", loop.replace("i16", "i8"), null);
        counterRewrite("counter wide width", loop.replace("i16", "i64"), null);
        counterRewrite("counter different lhs", loop.replace("ult i16 %i", "ult i16 %next"), null);
        counterRewrite("counter different type", loop.replace("ult i16", "ult i32"), null);
        counterRewrite("counter different branch", loop.replace("br i1 %condition", "br i1 %other"), null);
        counterRewrite("counter wrong edge", loop.replace("label %header, label %exit",
            "label %exit, label %header"), null);
        counterRewrite("counter same successors", loop.replace("label %header, label %exit",
            "label %header, label %header"), null);
        counterRewrite("counter wrong recurrence", loop.replace("[ %next, %latch ]", "[ %i, %latch ]"), null);
        counterRewrite("counter wrong predecessor", loop.replace("%next, %latch", "%next, %other"), null);
        counterRewrite("counter unknown seed", loop.replace("[ 0, %entry ]", "[ %seed, %entry ]"), null);
        counterRewrite("counter extra incoming", loop.replace("[ 0, %entry ]",
            "[ 0, %entry ], [ 65535, %other ]"), null);
        counterRewrite("counter intervening instruction", loop.replace("  %condition",
            "  store volatile i16 %next, ptr inttoptr (i32 16640 to ptr)\n  %condition"), null);
        counterRewrite("counter intervening block", loop.replace("  %condition",
            "  br label %other\nother:\n  %condition"), null);
        counterRewrite("counter non-unit step", loop.replace("%i, 1\n", "%i, 2\n"), null);
        counterRewrite("counter variable step", loop.replace("%i, 1\n", "%i, %step\n"), null);
        counterRewrite("counter no induction phi", loop.replace(
            "%i = phi i16 [ 0, %entry ], [ %next, %latch ]",
            "%i = load volatile i16, ptr inttoptr (i32 16640 to ptr)"), null);
        for (int bits : new int[] {16, 32}) {
            long max = bits == 16 ? 0xFFFFL : 0xFFFFFFFFL;
            for (String predicate : List.of("ult", "uge")) {
                String cmp = "%condition = icmp " + predicate + " i" + bits + " %next, 16";
                counterRewrite("counter " + bits + " " + predicate,
                    counterLoop(bits, 0, "nuw nsw ", predicate, 15), cmp);
                counterRewrite("counter wrapping seed " + bits + " " + predicate,
                    counterLoop(bits, max, "nuw ", predicate, 15), null);
                counterRewrite("counter overflowing bound " + bits + " " + predicate,
                    counterLoop(bits, max, "nuw ", predicate, max), null);
                counterRewrite("counter upper bound " + bits + " " + predicate,
                    counterLoop(bits, max - 1, "nuw ", predicate, max - 1),
                    "%condition = icmp " + predicate + " i" + bits + " %next, " + max);
                for (long seed : new long[] {0, 14, 15, 16, max}) {
                    String name = "counter_" + bits + "_" + predicate + "_" + seed;
                    checkModule(name, "define i" + bits + " @probe() {\n"
                        + counterLoop(bits, seed, "nuw ", predicate, 15) + "\n}\n",
                        bits, (int) Math.max(seed, 15), null);
                }
                for (long limit : new long[] {0, max - 1, max}) {
                    checkModule("counter_bound_" + bits + "_" + predicate + "_" + limit,
                        "define i" + bits + " @probe() {\n"
                            + counterLoop(bits, limit, "nuw ", predicate, limit) + "\n}\n",
                        bits, (int) limit, null);
                }
                long signedMax = max >>> 1;
                checkModule("counter_signed_boundary_" + bits + "_" + predicate,
                    "define i" + bits + " @probe() {\n"
                        + counterLoop(bits, signedMax - 1, "nuw nsw ",
                            "samesign " + predicate, signedMax) + "\n}\n",
                    bits, (int) signedMax, null);
                checkModule("counter_wrap_" + bits + "_" + predicate,
                    "define i" + bits + " @probe() {\n"
                        + counterLoop(bits, max, "", predicate, 15) + "\n}\n",
                    bits, (int) max, null);
            }
        }
        System.out.println("Counter comparison rewrite guards: PASS");
    }

    private static void loopAllocation() throws Exception {
        for (int iterations : new int[] {1, 2, 5}) {
            byte[] expected = SEED.clone();
            int a = (iterations & 1) == 1 ? 11 : 29;
            int b = (iterations & 1) == 1 ? 29 : 11;
            for (int k = 0; k < 4; k++) {
                expected[k] = (byte) (a >>> (8 * k));
                expected[k + 4] = (byte) (b >>> (8 * k));
            }
            checkAllocated("loop_swap_" + iterations, """
                entry:
                  br label %header
                header:
                  %a = phi i32 [ 11, %entry ], [ %b, %latch ]
                  %b = phi i32 [ 29, %entry ], [ %a, %latch ]
                  %i = phi i16 [ 0, %entry ], [ %next, %latch ]
                  store i32 %a, ptr inttoptr (i32 16384 to ptr)
                  store i32 %b, ptr inttoptr (i32 16388 to ptr)
                  br label %latch
                latch:
                  %next = add nuw i16 %i, 1
                  %again = icmp ult i16 %next, LIMIT
                  br i1 %again, label %header, label %exit
                exit:
                  ret i32 17
                """.replace("LIMIT", Integer.toString(iterations)), expected);
        }
        for (int width : new int[] {8, 16, 32}) {
            byte[] expected = SEED.clone();
            int result = width == 8 ? 253 : width == 16 ? 65533 : -3;
            for (int k = 0; k < 4; k++) expected[k] = (byte) (result >>> (k * 8));
            checkAllocated("loop_narrow_" + width, """
                entry:
                  br label %header
                header:
                  %i = phi i16 [ 0, %entry ], [ %next, %header ]
                  %n = add iWIDTH -4, 1
                  %r = CONVERSION
                  store i32 %r, ptr inttoptr (i32 16384 to ptr)
                  %next = add nuw i16 %i, 1
                  %again = icmp ult i16 %next, 3
                  br i1 %again, label %header, label %exit
                exit:
                  ret i32 17
                """.replace("WIDTH", Integer.toString(width))
                    .replace("CONVERSION", width == 32 ? "add i32 %n, 0" : "zext i" + width + " %n to i32"),
                expected);
        }
        byte[] expected = SEED.clone();
        expected[0] = 11; expected[1] = 0; expected[2] = 0; expected[3] = 0;
        checkAllocated("loop_diamond", """
            entry:
              br label %header
            header:
              %i = phi i32 [ 0, %entry ], [ %next, %join ]
              %acc = phi i32 [ 0, %entry ], [ %chosen, %join ]
              %bit = and i32 %i, 1
              %odd = icmp ne i32 %bit, 0
              br i1 %odd, label %yes, label %no
            yes:
              %left = add i32 %acc, 4
              br label %join
            no:
              %right = add i32 %acc, 1
              br label %join
            join:
              %chosen = phi i32 [ %left, %yes ], [ %right, %no ]
              store i32 %chosen, ptr inttoptr (i32 16384 to ptr)
              %next = add i32 %i, 1
              %again = icmp ult i32 %next, 5
              br i1 %again, label %header, label %exit
            exit:
              ret i32 17
            """, expected);
        StringBuilder pressure = new StringBuilder("entry:\n");
        int sum = 0;
        for (int n = 0; n < 20; n++) {
            pressure.append("  %p").append(n).append(" = inttoptr i32 ")
                .append(DATA_BASE + 4 * n).append(" to ptr\n  %v").append(n)
                .append(" = load i32, ptr %p").append(n).append("\n");
            for (int k = 0; k < 4; k++) sum += (SEED[4 * n + k] & 255) << (8 * k);
        }
        pressure.append("  br label %header\nheader:\n"
            + "  %i = phi i32 [ 0, %entry ], [ %next, %header ]\n");
        for (int n = 1; n < 20; n++)
            pressure.append("  %s").append(n).append(" = add i32 ")
                .append(n == 1 ? "%v0" : "%s" + (n - 1)).append(", %v").append(n).append("\n");
        pressure.append("  store i32 %s19, ptr inttoptr (i32 16384 to ptr)\n"
            + "  %next = add i32 %i, 1\n  %again = icmp ult i32 %next, 3\n"
            + "  br i1 %again, label %header, label %exit\nexit:\n  ret i32 17");
        expected = SEED.clone();
        for (int k = 0; k < 4; k++) expected[k] = (byte) (sum >>> (8 * k));
        checkAllocated("loop_pressure", pressure.toString(), expected);
        expected = SEED.clone();
        for (int k = 0; k < 4; k++) expected[k] = (byte) (20 >>> (8 * k));
        String nested = """
            entry:
              br label %outer
            outer:
              %row = phi i32 [ 0, %entry ], [ %nr, %rowexit ]
              br label %header
            header:
              %i = phi i32 [ 0, %outer ], [ %next, %header ]
              %color = load i8, ptr inttoptr (i32 16400 to ptr)
              %wide = zext i8 %color to i32
              %r = add i32 %wide, %row
              store i32 %r, ptr inttoptr (i32 16384 to ptr)
              %next = add i32 %i, 1
              %again = icmp ult i32 %next, 3
              br i1 %again, label %header, label %rowexit
            rowexit:
              %nr = add i32 %row, 1
              %more = icmp ult i32 %nr, 4
              br i1 %more, label %outer, label %exit
            exit:
              ret i32 17
            """;
        checkAllocated("loop_nested_byte", nested, expected);
        expected = SEED.clone();
        for (int k = 0; k < 4; k++) expected[k] = (byte) (23 >>> (8 * k));
        checkAllocated("loop_phi_external", """
            entry:
              %outside = add i32 19, 4
              br label %header
            header:
              %i = phi i32 [ 0, %entry ], [ %next, %latch ]
              %carry = phi i32 [ 1, %entry ], [ %outside, %latch ]
              store i32 %carry, ptr inttoptr (i32 16384 to ptr)
              br label %latch
            latch:
              %next = add i32 %i, 1
              %again = icmp ult i32 %next, 3
              br i1 %again, label %header, label %exit
            exit:
              ret i32 17
            """, expected);
        for (String predicate : List.of("eq", "ne", "ult", "uge", "ugt", "ule")) {
            expected = SEED.clone();
            for (int k = 0; k < 4; k++) expected[k] = (byte) (2 >>> (8 * k));
            boolean exitOnTrue = Set.of("eq", "uge", "ugt").contains(predicate);
            int limit = Set.of("ugt", "ule").contains(predicate) ? 2 : 3;
            checkAllocated("loop_predicate_" + predicate,
                "entry:\n  br label %header\nheader:\n"
                + "  %i = phi i32 [ 0, %entry ], [ %next, %header ]\n"
                + "  store i32 %i, ptr inttoptr (i32 16384 to ptr)\n"
                + "  %next = add i32 %i, 1\n  %again = icmp " + predicate + " i32 %next, " + limit + "\n"
                + "  br i1 %again, label %" + (exitOnTrue ? "exit" : "header")
                + ", label %" + (exitOnTrue ? "header" : "exit")
                + "\nexit:\n  ret i32 17", expected);
        }
        for (String operation : List.of("zext i8 -1 to i32", "sext i1 true to i32",
                "ashr i8 -128, 3", "icmp ult i8 254, -1")) {
            int result = operation.startsWith("zext") ? 255 : operation.startsWith("sext") ? -1
                : operation.startsWith("ashr") ? 240 : 1;
            expected = SEED.clone();
            for (int k = 0; k < 4; k++) expected[k] = (byte) (result >>> (8 * k));
            String resultType = operation.startsWith("ashr") ? "i8" : operation.startsWith("icmp") ? "i1" : "i32";
            checkAllocated("loop_immediate_" + operation.substring(0, 4),
                "entry:\n  br label %header\nheader:\n"
                + "  %i = phi i32 [ 0, %entry ], [ %next, %header ]\n"
                + "  %v = " + operation + "\n"
                + (resultType.equals("i32") ? "  %r = add i32 %v, 0\n"
                    : "  %r = zext " + resultType + " %v to i32\n")
                + "  store i32 %r, ptr inttoptr (i32 16384 to ptr)\n"
                + "  %next = add i32 %i, 1\n  %again = icmp ult i32 %next, 2\n"
                + "  br i1 %again, label %header, label %exit\nexit:\n  ret i32 17", expected);
        }
        loopInstructionSelection();
        loopOptimizerIsolation();
        for (String body : List.of(
                nested.replace("%next = add i32 %i, 1", "%next = call i32 @other()"),
                nested.replace("ret i32 17", "ret i32 %next"),
                nested.replace("%next = add i32 %i, 1", "%next = shl i32 %i, %row"))) {
            var translator = new Ir2Tlcs900.Translator("define i32 @probe() {\n" + body + "\n}", "probe");
            translator.experimentalRegisterFunctions.add("probe");
            boolean rejected = false;
            try {
                translator.translate();
            } catch (IllegalArgumentException unsupported) {
                require(unsupported.getMessage().contains("no supported closed loop"),
                    "unexpected allocator rejection: " + unsupported.getMessage());
                rejected = true;
            }
            require(rejected, "unsupported experimental loop was accepted");
        }
    }

    private static void loopOptimizerIsolation() throws Exception {
        List<String> marked = List.of("; experimental-loop-register-allocation",
            "_before:", "\tld xwa, 7", "\tld xwa, 7", "\tret",
            AsmOptimizer.PROTECTED_BEGIN + "allocated", "_allocated:",
            "\tld xwa, 0", "\tld a, (xde)", "\tld xde, xwa",
            "\tld xwa, xwa", "\tjrl .Lbb_alloc_used", ".Lbb_alloc_used:",
            ".Lbb_alloc_unreferenced:", "\tcall _C9H_needed", "\tret",
            AsmOptimizer.PROTECTED_END + "allocated",
            "_after:", ".Lbb_after_target:", "\tld xwa, 7", "\tld xwa, 7", "\tret",
            AsmOptimizer.PROTECTED_BEGIN + "second", "_second:",
            "\tjrl .Lbb_after_target", ".Lbb_second_unreferenced:", "\tret",
            AsmOptimizer.PROTECTED_END + "second",
            "_C9H_needed:", "\tret", "_C9H_unused:", "\tret", "\tend");
        List<String> result = AsmOptimizer.runPasses(marked, false);
        require(protectedCode(result).equals(protectedCode(marked)), "allocated function changed");
        require(result.stream().filter("\tld xwa, 7"::equals).count() == 2,
            "ordinary functions did not regain peephole optimization");
        require(result.contains(".Lbb_after_target:"),
            "label cleanup lost a reference from a protected function");
        require(protectedCode(AsmOptimizer.runPasses(result, false)).equals(protectedCode(marked)),
            "repeated optimization changed protected functions");
        result = Ir2Tlcs900.stripUnusedRuntimeHelpers(result);
        require(result.contains("_C9H_needed:") && !result.contains("_C9H_unused:"),
            "runtime cleanup lost a reference inside a protected function");
        require(protectedCode(result).equals(protectedCode(marked)), "runtime cleanup changed protected code");
        List<String> old = List.of("; experimental-loop-register-allocation", "_old:", "\tld xwa, xwa", "\tret");
        require(AsmOptimizer.runPasses(old, false).equals(old), "old unfenced experimental output was rewritten");
        for (List<String> invalid : List.of(
                List.of(AsmOptimizer.PROTECTED_BEGIN + "a", "_a:"),
                List.of(AsmOptimizer.PROTECTED_END + "a"),
                List.of(AsmOptimizer.PROTECTED_BEGIN + "a", "_a:", AsmOptimizer.PROTECTED_END + "b"),
                List.of(AsmOptimizer.PROTECTED_BEGIN + "a", "_a:",
                    AsmOptimizer.PROTECTED_BEGIN + "b", "_b:"),
                List.of(AsmOptimizer.PROTECTED_BEGIN + "a", "_a:", AsmOptimizer.PROTECTED_END + "a"))) {
            boolean rejected = false;
            try { AsmOptimizer.runPasses(invalid, false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "malformed/missing-marker function fences were accepted");
        }
        byte[] expected = SEED.clone();
        int divisor = 0, helperInput = 0;
        for (int k = 0; k < 4; k++) {
            divisor |= (SEED[k] & 255) << (8 * k);
            helperInput |= (SEED[12 + k] & 255) << (8 * k);
        }
        int quotient = Integer.MIN_VALUE / divisor;
        for (int k = 0; k < 4; k++) {
            expected[4 + k] = (byte) (quotient >>> (8 * k));
            expected[16 + k] = (byte) ((helperInput + 1) >>> (8 * k));
        }
        String assembly = checkAllocated("loop_isolation_mixed", """
            entry:
              call void @ordinary()
              %d = load volatile i32, ptr inttoptr (i32 16384 to ptr)
              %q = sdiv i32 -2147483648, %d
              br label %header
            header:
              %i = phi i32 [ 0, %entry ], [ %next, %header ]
              store i32 %q, ptr inttoptr (i32 16388 to ptr)
              %next = add i32 %i, 1
              %again = icmp ult i32 %next, 3
              br i1 %again, label %header, label %exit
            exit:
              ret i32 17
            }
            define void @ordinary() {
            entry:
              %value = load volatile i32, ptr inttoptr (i32 16396 to ptr)
              %next = add i32 %value, 1
              store i32 %next, ptr inttoptr (i32 16400 to ptr)
              ret void
            """, expected);
        String legacy = Files.readString(output.resolve("loop_isolation_mixed.asm"));
        Pattern ordinary = Pattern.compile("(?ms)^_ordinary:\\R.*?^\\tret$");
        var before = ordinary.matcher(legacy);
        var after = ordinary.matcher(assembly);
        require(before.find() && after.find() && before.group().equals(after.group()),
            "ordinary helper translation differs from the default pipeline");
    }

    private static void loopInstructionSelection() throws Exception {
        for (int width : new int[] {8, 16, 32})
            for (String op : List.of("add", "sub", "and", "or", "xor")) {
                int mask = width == 32 ? -1 : (1 << width) - 1;
                int left = 0x1234fff8 & mask, literal = 0x55555555 & mask, right = 2 ^ literal;
                int result = (switch (op) {
                    case "add" -> left + right; case "sub" -> left - right;
                    case "and" -> left & right; case "or" -> left | right; default -> left ^ right;
                }) & mask;
                byte[] expected = SEED.clone();
                for (int k = 0; k < width / 8; k++) {
                    expected[k] = (byte) (result >>> (8 * k));
                    expected[k + 4] = (byte) (left >>> (8 * k));
                }
                String assembly = checkAllocated("loop_rhs_home_" + op + "_" + width, """
                    entry:
                      %held = add iWIDTH LEFT, 0
                      br label %header
                    header:
                      %i = phi i32 [ 0, %entry ], [ %next, %header ]
                      %index = CONVERSION
                      %rhs = xor iWIDTH %index, LITERAL
                      %r = OP iWIDTH %held, %rhs
                      store iWIDTH %r, ptr inttoptr (i32 16384 to ptr)
                      store iWIDTH %held, ptr inttoptr (i32 16388 to ptr)
                      %next = add i32 %i, 1
                      %again = icmp ult i32 %next, 3
                      br i1 %again, label %header, label %exit
                    exit:
                      ret i32 17
                    """.replace("OP", op).replace("WIDTH", Integer.toString(width))
                        .replace("LEFT", Integer.toString(left)).replace("LITERAL", Integer.toString(literal))
                        .replace("CONVERSION", width == 32 ? "add i32 %i, 0"
                            : "trunc i32 %i to i" + width), expected);
                if (!op.equals("sub"))
                    require(Pattern.compile("(?m)^\\t" + op
                        + " x(?:de|bc|hl|ix|iy|iz), x(?:de|bc|hl|wa)[12]$")
                        .matcher(assembly).find(), op + ": missing direct commuted ALU");
            }
        for (int width : new int[] {8, 16, 32}) {
            byte[] expected = SEED.clone();
            int position = 0;
            for (int step = 1; step <= 3; step++) {
                position += step * (width / 8);
                int value = 0;
                for (int k = 0; k < width / 8; k++) value |= (expected[position + k] & 255) << (8 * k);
                for (int k = 0; k < 4; k++) expected[k] = (byte) (value >>> (8 * k));
            }
            String assembly = checkAllocated("loop_gep_base_" + width, """
                entry:
                  br label %header
                header:
                  %p = phi ptr [ inttoptr (i32 16384 to ptr), %entry ], [ %address, %header ]
                  %i = phi i32 [ 0, %entry ], [ %next, %header ]
                  %offset = add i32 %i, 1
                  %address = getelementptr iWIDTH, ptr %p, i32 %offset
                  %value = load iWIDTH, ptr %address
                  %wide = CONVERSION
                  store i32 %wide, ptr inttoptr (i32 16384 to ptr)
                  %next = add i32 %i, 1
                  %again = icmp ult i32 %next, 3
                  br i1 %again, label %header, label %exit
                exit:
                  ret i32 17
                """.replace("WIDTH", Integer.toString(width))
                    .replace("CONVERSION", width == 32 ? "add i32 %value, 0"
                        : "zext i" + width + " %value to i32"), expected);
            if (width == 8)
                require(Pattern.compile("(?m)^\\tadd x(?:de|bc|hl|ix|iy|iz), x(?:de|bc|hl|ix|iy|iz)$")
                    .matcher(assembly).find(), "unscaled GEP still copies its index through scratch");
        }
        byte[] expected = SEED.clone();
        for (int k = 0; k < 4; k++) expected[k] = (byte) (36 >>> (8 * k));
        checkAllocated("loop_gep_index_home", """
            entry:
              %base = inttoptr i32 16416 to ptr
              br label %header
            header:
              %i = phi i32 [ 0, %entry ], [ %next, %header ]
              %offset = add i32 %i, 1
              %address = getelementptr i8, ptr %base, i32 %offset
              %value = load i8, ptr %address
              %wide = zext i8 %value to i32
              store i32 %wide, ptr inttoptr (i32 16384 to ptr)
              %next = add i32 %i, 1
              %again = icmp ult i32 %next, 3
              br i1 %again, label %header, label %exit
            exit:
              ret i32 17
            """, expected);
        for (int width : new int[] {8, 16, 32})
            for (int set : new int[] {0, 1}) {
                int bit = width == 8 ? 7 : 15;
                expected = SEED.clone();
                for (int k = 0; k < 4; k++) expected[k] = (byte) (set >>> (8 * k));
                String assembly = checkAllocated("loop_direct_bit_" + width + "_" + set, """
                    entry:
                      br label %header
                    header:
                      %i = phi i32 [ 0, %entry ], [ %next, %latch ]
                      %value = add iWIDTH VALUE, 0
                      %masked = and iWIDTH %value, MASK
                      %test = icmp ne iWIDTH %masked, 0
                      br i1 %test, label %yes, label %no
                    yes:
                      store i32 1, ptr inttoptr (i32 16384 to ptr)
                      br label %latch
                    no:
                      store i32 0, ptr inttoptr (i32 16384 to ptr)
                      br label %latch
                    latch:
                      %next = add i32 %i, 1
                      %again = icmp ult i32 %next, 2
                      br i1 %again, label %header, label %exit
                    exit:
                      ret i32 17
                    """.replace("WIDTH", Integer.toString(width))
                        .replace("VALUE", Integer.toString(set == 0 ? 1 : 1 << bit))
                        .replace("MASK", Integer.toString(1 << bit)), expected);
                require(Pattern.compile("(?m)^\\tbit " + bit + ", (?:de|bc|hl|ix|iy|iz)$")
                    .matcher(assembly).find(), "bit test still copies its primary source through scratch");
            }
        expected = SEED.clone();
        for (int k = 0; k < 4; k++) expected[k] = (byte) (1 >>> (8 * k));
        // Legacy byte-immediate emission cannot assemble the out-of-width constant.
        String assembly = checkAllocated("loop_bit_outside_width", """
            entry:
              br label %header
            header:
              %i = phi i32 [ 0, %entry ], [ %next, %latch ]
              %masked = and i8 -1, 256
              %test = icmp eq i8 %masked, 0
              br i1 %test, label %yes, label %no
            yes:
              store i32 1, ptr inttoptr (i32 16384 to ptr)
              br label %latch
            no:
              store i32 0, ptr inttoptr (i32 16384 to ptr)
              br label %latch
            latch:
              %next = add i32 %i, 1
              %again = icmp ult i32 %next, 2
              br i1 %again, label %header, label %exit
            exit:
              ret i32 17
            """, expected, false);
        require(!assembly.contains("\tbit 8,"), "out-of-width mask was fused before truncation");
    }

    private static void memory() throws Exception {
        for (String kind : new String[] {"memset", "memcpy", "memmove"}) {
            for (int length : new int[] {0, 1, 4, 40}) {
                for (boolean variable : new boolean[] {false, true}) {
                    int directions = kind.equals("memmove") ? 4 : 1;
                    for (int direction = 0; direction < directions; direction++) {
                        int source = kind.equals("memmove") ? (direction == 1 ? 1 : 0) : 64;
                        int destination = kind.equals("memmove") ? (direction == 0 ? 1 : direction == 3 ? 64 : 0) : 0;
                        byte[] expected = SEED.clone();
                        if (kind.equals("memset")) Arrays.fill(expected, destination, destination + length, (byte) 165);
                        else System.arraycopy(SEED, source, expected, destination, length);
                        String count = variable ? "%len" : Integer.toString(length);
                        String body = variable
                                ? "  store volatile i32 " + length + ", ptr inttoptr (i32 16640 to ptr)\n"
                                    + "  %len = load volatile i32, ptr inttoptr (i32 16640 to ptr)\n"
                                : "";
                        String intrinsic = kind.equals("memset") ? "memset.p0.i32" : kind + ".p0.p0.i32";
                        String auxiliary = kind.equals("memset") ? "i8 165"
                                : "ptr inttoptr (i32 " + (DATA_BASE + source) + " to ptr)";
                        body += "  call void @llvm." + intrinsic + "(ptr inttoptr (i32 "
                                + (DATA_BASE + destination) + " to ptr), " + auxiliary
                                + ", i32 " + count + ", i1 false)\n"
                                + "  %r = load volatile i8, ptr inttoptr (i32 " + (DATA_BASE + destination) + " to ptr)\n"
                                + "  ret i8 %r";
                        check(kind + "_" + length + "_" + variable + "_" + direction, body, 8,
                                expected[destination] & 0xFF, expected);
                    }
                }
            }
        }
    }

    private static void localArrays() throws Exception {
        for (String type : new String[] {"i8", "[80 x i8]", "[10 x [8 x i8]]"}) {
            checkModule("local_pointer_call_" + type.replaceAll("[^a-zA-Z0-9]", "_"),
                    "define i8 @probe() {\nentry:\n"
                    + "  %first = alloca " + type + ", align 1\n"
                    + "  %second = alloca i8, align 1\n"
                    + "  store volatile i8 23, ptr %first\n"
                    + "  store volatile i8 41, ptr %second\n"
                    + "  call void @update(ptr %first, i8 7, ptr %second, i16 80)\n"
                    + "  %left = load volatile i8, ptr %first\n"
                    + "  %right = load volatile i8, ptr %second\n"
                    + "  %result = add i8 %left, %right\n"
                    + "  ret i8 %result\n}\n"
                    + "define void @update(ptr %first, i8 %unused, ptr %second, i16 %size) {\nentry:\n"
                    + "  store volatile i8 42, ptr %first\n"
                    + "  store volatile i8 17, ptr %second\n"
                    + "  ret void\n}\n", 8, 59, null);
        }
        for (String operation : new String[] {"add", "sub", "shl", "mul"}) {
            int left = operation.equals("add") ? 1 : operation.equals("sub") ? 0 : 128;
            int right = operation.equals("add") ? 255 : operation.equals("sub") ? 1 : operation.equals("shl") ? 1 : 2;
            int wrapped = operation.equals("sub") ? 255 : 0;
            check("byte_wrap_compare_" + operation,
                    "  store volatile i8 " + left + ", ptr inttoptr (i32 16640 to ptr)\n"
                    + "  store volatile i8 " + right + ", ptr inttoptr (i32 16641 to ptr)\n"
                    + "  %left = load volatile i8, ptr inttoptr (i32 16640 to ptr)\n"
                    + "  %right = load volatile i8, ptr inttoptr (i32 16641 to ptr)\n"
                    + "  %wrapped = " + operation + " i8 %left, %right\n"
                    + "  %equal = icmp eq i8 %wrapped, " + wrapped + "\n"
                    + "  %result = zext i1 %equal to i8\n"
                    + "  ret i8 %result", 8, 1, null);
        }
        checkModule("pointer_argument_stores", "define i32 @probe() {\nentry:\n"
            + "  store volatile i8 16, ptr inttoptr (i32 16384 to ptr)\n"
            + "  store volatile i8 24, ptr inttoptr (i32 16385 to ptr)\n"
            + "  %result = call i32 @update(ptr inttoptr (i32 16384 to ptr), ptr inttoptr (i32 16385 to ptr))\n"
            + "  ret i32 %result\n}\n"
            + "define i32 @update(ptr %first, ptr %second) {\nentry:\n"
            + "  %left = load volatile i8, ptr %first\n"
            + "  %right = load volatile i8, ptr %second\n"
            + "  %nextLeft = add i8 %left, 8\n"
            + "  %nextRight = add i8 %right, 8\n"
            + "  store volatile i8 %nextLeft, ptr %first\n"
            + "  store volatile i8 %nextRight, ptr %second\n"
            + "  %actualLeft = load volatile i8, ptr %first\n"
            + "  %actualRight = load volatile i8, ptr %second\n"
            + "  %wideLeft = zext i8 %actualLeft to i32\n"
            + "  %wideRight = zext i8 %actualRight to i32\n"
            + "  %total = add i32 %wideLeft, %wideRight\n"
            + "  ret i32 %total\n}\n", 32, 56, null);
        for (String type : new String[] {"[32 x i8]", "[2 x [16 x i8]]"}) {
            check(type.equals("[32 x i8]") ? "local_array" : "nested_local_array",
                    "  %first = alloca " + type + ", align 1\n"
                    + "  %second = alloca [9 x i8], align 1\n"
                    + "  call void @llvm.memset.p0.i32(ptr %first, i8 65, i32 32, i1 false)\n"
                    + "  call void @llvm.memset.p0.i32(ptr %second, i8 66, i32 9, i1 false)\n"
                    + "  %address = getelementptr i8, ptr %first, i32 4\n"
                    + "  %value = load volatile i8, ptr %address\n"
                    + "  ret i8 %value", 8, 65, null);
        }
    }

    private static void nestedAddresses() throws Exception {
        checkModule("constant_pointer_return",
            "define i32 @probe() {\nentry:\n"
            + "  %pointer = call ptr @field()\n"
            + "  %result = ptrtoint ptr %pointer to i32\n"
            + "  ret i32 %result\n}\n"
            + "define ptr @field() {\nentry:\n"
            + "  ret ptr getelementptr (i8, ptr @probe, i32 3)\n}\n",
            32, ROM_BASE + 3, null);
        check("promoted_loop_condition",
            "  store volatile i8 0, ptr inttoptr (i32 16389 to ptr)\n"
            + "  br label %loop\nloop:\n"
            + "  %index = phi i32 [ 0, %entry ], [ %next, %loop ]\n"
            + "  %address = getelementptr i8, ptr inttoptr (i32 16384 to ptr), i32 %index\n"
            + "  %value = load volatile i8, ptr %address\n"
            + "  %done = icmp eq i8 %value, 0\n"
            + "  %next = add i32 %index, 1\n"
            + "  br i1 %done, label %exit, label %loop\nexit:\n"
            + "  ret i32 %index", 32, 5, null);
        for (String type : new String[] {"i8", "i16", "i32", "[8 x i8]", "[4 x i16]"}) {
            int expected = 3 + 5 * (type.startsWith("[") ? 8 : type.equals("i8") ? 1 : type.equals("i16") ? 2 : 4);
            check("nested_base_" + type.replaceAll("[^a-zA-Z0-9]", "_"),
                    "  store volatile i32 5, ptr inttoptr (i32 16640 to ptr)\n"
                    + "  %index = load volatile i32, ptr inttoptr (i32 16640 to ptr)\n"
                    + "  %address = getelementptr " + type
                    + ", ptr getelementptr (i8, ptr @probe, i32 3), i32 %index\n"
                    + "  %result = ptrtoint ptr %address to i32\n"
                    + "  ret i32 %result", 32, ROM_BASE + expected, null);
        }
        for (String base : new String[] {"@probe", "%base", "inttoptr (i32 2097152 to ptr)"}) {
            for (String index : new String[] {"%index", "5", "-1"}) {
                check("word_address_" + base.replaceAll("[^a-zA-Z0-9]", "_") + "_"
                                + index.replaceAll("[^a-zA-Z0-9]", "_"),
                        "  %base = getelementptr i8, ptr @probe, i32 0\n"
                        + "  store volatile i32 5, ptr inttoptr (i32 16640 to ptr)\n"
                        + "  %index = load volatile i32, ptr inttoptr (i32 16640 to ptr)\n"
                        + "  %address = getelementptr i32, ptr " + base + ", i32 " + index + "\n"
                        + "  %result = ptrtoint ptr %address to i32\n"
                        + "  ret i32 %result", 32, ROM_BASE + (index.equals("-1") ? -4 : 20), null);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        String[] packs = {"Classic Levels", "Classic II Levels", "Children's Pack",
                "Confusion Pack", "Impossible Pack", "Panic Pack", "Twister Levels",
                "Variety Pack", "Variety II Pack"};
        List<String> rows = new ArrayList<>();
        List<String> expectedPackBytes = new ArrayList<>();
        for (String pack : packs) {
            rows.add("[20 x i8] c\"" + pack + "\\00".repeat(20 - pack.length()) + "\"");
            for (int index = 0; index < 20; index++) {
                expectedPackBytes.add(Integer.toString(index < pack.length() ? pack.charAt(index) : 0));
            }
        }
        require(Ir2Tlcs900.flattenArrayItems("[20 x i8]", String.join(", ", rows))
                .equals(expectedPackBytes), "Vexed fixed-width pack names and zero padding");
        require(Ir2Tlcs900.decodeCString("\\CD^\\07\\\\\\87!\\BD\\06")
            .equals(List.of(205, 94, 7, 92, 135, 33, 189, 6)), "Binary string backslash decoding");
        require(Ir2Tlcs900.decodeCString("\\5C\\\\\\00\\FF")
            .equals(List.of(92, 92, 0, 255)), "Adjacent binary string escapes");
        if (args.length > 0 && args[0].equals("--strings-only")) {
            System.out.println("Ir2Tlcs900CorrectnessTest: PASS (binary string escapes and fixed-width rows)");
            return;
        }
        output = Files.createTempDirectory("ngpc-correctness-");
        for (int index = 0; index < SEED.length; index++) SEED[index] = (byte) (index + 1);
        System.out.println("Correctness test artifacts: " + output);
        if (args.length > 0 && args[0].equals("--allocator-only")) {
            loopAllocation();
        } else if (args.length > 0 && args[0].equals("--counter-only")) {
            counterComparisons();
        } else {
            localArrays();
            nestedAddresses();
            if (args.length == 0 || !args[0].equals("--alloca-only")) {
                booleans();
                arithmetic();
                memory();
                counterComparisons();
                loopAllocation();
            }
        }
        String toolchain = "Z:" + TOOLCHAIN.toString().replace('/', '\\');
        StringBuilder batch = new StringBuilder("@echo off\r\n");
        for (Fixture fixture : fixtures) {
            batch.append('"').append(toolchain).append("\\asl.exe\" ")
                .append(fixture.name()).append(".asm\r\nif errorlevel 1 exit /b 1\r\n");
            batch.append('"').append(toolchain).append("\\p2bin.exe\" -r $-$ ")
                .append(fixture.name()).append(".p ").append(fixture.name())
                .append(".bin\r\nif errorlevel 1 exit /b 1\r\n");
        }
        Files.writeString(output.resolve("assemble.bat"), batch);
        System.out.println("Assembling " + fixtures.size() + " raw/optimized fixtures");
        command("wine", "cmd", "/c", "assemble.bat");
        for (Fixture fixture : fixtures) {
            execute(fixture.name(), fixture.bits(), fixture.expected(), fixture.expectedMemory());
        }
        System.out.println("Ir2Tlcs900CorrectnessTest: PASS (" + executions + " assembled executions)");
    }
}