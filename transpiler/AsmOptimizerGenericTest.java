import java.util.*;

/** Register permutations and negative safety cases independent of any game. */
public final class AsmOptimizerGenericTest {
    private static int checks;
    private static void require(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
    private static List<String> fixture(String... body) {
        List<String> out = new ArrayList<>(List.of("_arbitrary_helper:", "\tadd xsp, -64"));
        out.addAll(List.of(body));
        out.addAll(List.of("\tadd xsp, 64", "\tret"));
        return out;
    }
    // Independent execution of the local LD/ALU patterns, including carry.
    private static Map<String, Long> execute(List<String> code, String a, String b, int width, long seed) {
        Map<String, Long> values = new HashMap<>();
        Random random = new Random(seed);
        long mask = (1L << width) - 1;
        values.put(a, random.nextLong() & mask);
        values.put(b, random.nextLong() & mask);
        values.put("(xsp+12)", random.nextLong() & mask);
        boolean carry = random.nextBoolean();
        for (String raw : code) {
            String line = raw.strip();
            if (!line.startsWith("ld ") && !line.matches("(?:add|adc|sub|sbc|and|or|xor) .*")) continue;
            int space = line.indexOf(' '), comma = line.indexOf(',');
            String op = line.substring(0, space), dst = line.substring(space + 1, comma).strip(), src = line.substring(comma + 1).strip();
            if (dst.equals("xsp")) continue;
            long right = values.containsKey(src) ? values.get(src) : Long.parseLong(src);
            if (op.equals("ld")) { values.put(dst, right & mask); continue; }
            long left = values.get(dst), result = switch (op) {
                case "add" -> left + right;
                case "adc" -> left + right + (carry ? 1 : 0);
                case "sub" -> left - right;
                case "sbc" -> left - right - (carry ? 1 : 0);
                case "and" -> left & right;
                case "or" -> left | right;
                case "xor" -> left ^ right;
                default -> throw new AssertionError(op);
            };
            carry = result < 0 || result > mask;
            values.put(dst, result & mask);
        }
        values.put("carry", carry ? 1L : 0L);
        return values;
    }
    public static void main(String[] args) throws Exception {
        Ir2Tlcs900PromotedPointerTest.main(new String[0]);
        AsmOptimizerStackReloadTest.main(new String[0]);
        AsmOptimizerShiftMaskTest.main(new String[0]);
        AsmOptimizerMemoryCounterTest.main(new String[0]);
        for (int width : new int[]{16, 32}) {
            List<String> registers = width == 16 ? List.of("wa", "bc", "de", "hl", "ix", "iy", "iz")
                : List.of("xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz");
            for (String result : registers) for (String temp : registers) {
                if (result.equals(temp)) continue;
                for (String op : List.of("add", "adc", "sub", "sbc", "and", "or", "xor")) {
                    List<String> original = fixture("\tld " + temp + ", " + result, "\tld " + result + ", (xsp+12)",
                        "\t" + op + " " + temp + ", " + result, "\tld " + result + ", " + temp, "\tld " + temp + ", 0");
                    List<String> optimized = AsmOptimizer.foldXhlLoadAddRoundtrip(original);
                    require(optimized.contains("\t" + op + " " + result + ", (xsp+12)"), "register-role fold " + temp + " " + result);
                    for (int seed = 0; seed < 32; seed++) require(execute(original, result, temp, width, seed).equals(
                        execute(optimized, result, temp, width, seed)), "ALU semantics " + op);
                    for (String hazard : List.of("\tld " + result + ", " + temp, "\tpush " + temp,
                            "\tcall _unknown", "\tunknown", ".Lalternate_entry:", "\tld rfp, 1")) {
                        List<String> blocked = new ArrayList<>(original);
                        blocked.set(blocked.size() - 3, hazard);
                        require(AsmOptimizer.foldXhlLoadAddRoundtrip(blocked).equals(blocked), "live/barrier " + hazard);
                    }
                }
                List<String> inverse = fixture("\tld " + temp + ", " + result, "\tld " + result + ", " + temp);
                require(AsmOptimizer.eliminateRegisterRoundtrip(inverse).size() == inverse.size() - 1, "inverse copies");
            }
        }
        for (String wide : List.of("xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
            for (int amount = -8; amount <= 8; amount++) {
                if (amount == 0) continue;
                List<String> input = fixture("\tsla 16, " + wide, "\tadd " + wide + ", " + ((long) amount << 16), "\tcp " + wide + ", 0");
                List<String> result = AsmOptimizer.foldShiftedWordSubtract(input);
                require(result.contains("\t" + (amount < 0 ? "dec " : "inc ") + Math.abs(amount) + ", " + wide.substring(1)), "shift constant " + amount);
                List<String> live = new ArrayList<>(input);
                live.set(4, "\tadc " + wide + ", 0");
                require(AsmOptimizer.foldShiftedWordSubtract(live).equals(live), "shift carry remains live");
            }
        }
        List<String> overlap = fixture("\tld (xsp+8), xde", "\tld xde, (xsp+10)", "\tadd xde, (xsp+8)");
        require(AsmOptimizer.foldSpillReloadAdd(overlap).equals(overlap), "overlapping spill sources");
        List<String> unread = fixture("\tld (xsp+8), xde", "\tcp wa, 0");
        require(!AsmOptimizer.eliminateUnreadPrivateStores(unread).contains("\tld (xsp+8), xde"), "unread private slot");
        for (String access : List.of("\tld bc, (xsp+10)", "\tlda xhl, (xsp+8)", "\tld xhl, xsp", "\tpush wa", "\tcall _other", "\tunknown")) {
            List<String> live = fixture("\tld (xsp+8), xde", "\tjr .Llater", ".Llater:", access);
            require(AsmOptimizer.eliminateUnreadPrivateStores(live).equals(live), "slot safety " + access);
        }
        for (String destination : List.of("xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
            List<String> read = fixture("\tld " + destination + ", (01200h)", "\tld " + destination + ", (01200h)");
            require(AsmOptimizer.eliminateRedundantRegReload(read).equals(read), "memory/MMIO reads must remain");
            for (String change : List.of("\tsla 1, " + destination, "\tscc Z, " + destination, "\tcall _other", "\tunknown")) {
                List<String> constants = fixture("\tld " + destination + ", 5", change, "\tld " + destination + ", 5");
                require(AsmOptimizer.eliminateRedundantRegLoads(constants).equals(constants), "cache invalidation " + change);
            }
        }
        for (String reg : List.of("xwa", "xbc", "xde", "xhl")) for (int bit = 0; bit < 8; bit++) {
            List<String> input = fixture("\tld (xsp+8), " + reg, "\tand " + reg + ", " + (1 << bit), "\tjr NZ, .Lset",
                "\tld " + reg + ", (xsp+8)", "\tcp " + reg + ", 0", "\tjr .Ldone", ".Lset:",
                "\tld " + reg + ", (xsp+8)", "\tcp " + reg + ", 1", ".Ldone:");
            List<String> output = AsmPatterns.foldRestoredSingleBitTests(input);
            require(output.size() == input.size() - 2 && output.stream().anyMatch(l -> l.strip().startsWith("bit ")), "generic bit diamond");
            List<String> flags = new ArrayList<>(input);
            flags.set(6, "\tadc " + reg + ", 0");
            require(AsmPatterns.foldRestoredSingleBitTests(flags).equals(flags), "bit live flags");
        }
        for (String pair : List.of("wa", "bc", "de", "hl")) {
            String low = switch (pair) { case "wa" -> "a"; case "bc" -> "c"; case "de" -> "e"; default -> "l"; };
            List<String> load = fixture("\tld " + low + ", (01200h)", "\textz " + pair, "\textz x" + pair);
            require(AsmOptimizer.fuseZeroExtendedByteLoads(load).contains("\tld x" + pair + ", 0"), "byte fusion " + pair);
            List<String> addressed = fixture("\tld " + low + ", (x" + pair + "+0)", "\textz " + pair, "\textz x" + pair);
            require(AsmOptimizer.fuseZeroExtendedByteLoads(addressed).equals(addressed), "byte fusion address dependency");
            List<String> mask = fixture("\tand " + pair + ", 255", "\textz " + pair, "\tcp " + pair + ", 0");
            require(!AsmOptimizer.eliminateByteMasksBeforeExtz(mask).contains("\tand " + pair + ", 255"), "byte mask " + pair);
            List<String> zero = fixture("\tld " + pair + ", 0", "\tld " + low + ", 7", "\textz " + pair);
            require(!AsmOptimizer.eliminateDeadWaZeroBeforeExtz(zero).contains("\tld " + pair + ", 0"), "dead word zero " + pair);
        }
        for (String pair : List.of("wa", "bc", "de", "hl")) {
            String high = pair.substring(0, 1), low = pair.substring(1);
            String clear = "\txor " + high + ", " + high;
            for (String wide : List.of(pair, "x" + pair)) for (String mask : List.of("0", "1", "0ffh")) {
                String normalize = "\tand " + wide + ", " + mask;
                List<String> input = fixture(normalize, "\tld " + low + ", 7", clear, "\tor " + low + ", " + low);
                require(!AsmOptimizer.eliminateRedundantXorWW(input).contains(clear), "mask proves high zero " + wide);
                for (String barrier : List.of("\tld " + high + ", 1", "\tld " + pair + ", 1",
                        "\tld x" + pair + ", 1", "\tinc 1, " + pair, "\tcall _other", ".Lentry:", "\tunknown")) {
                    List<String> blocked = fixture(normalize, barrier, clear, "\tor " + low + ", " + low);
                    require(AsmOptimizer.eliminateRedundantXorWW(blocked).contains(clear), "high zero invalidation " + barrier);
                }
                List<String> live = fixture(normalize, clear, "\tjr Z, .Lentry");
                require(AsmOptimizer.eliminateRedundantXorWW(live).equals(live), "retain live XOR flags");
            }
            List<String> nonzero = fixture("\tand " + pair + ", 0100h", clear, "\tcp wa, 0");
            require(AsmOptimizer.eliminateRedundantXorWW(nonzero).equals(nonzero), "mask retains high bits");
        }
        List<String> sampleClear = fixture("\tand wa, 0ffh", "\tld e, a", "\txor w, w", "\tld a, e", "\tor a, a", "\tjr Z, .Ldone");
        require(!AsmOptimizer.eliminateRedundantXorWW(sampleClear).contains("\txor w, w"), "SAMPLE high-byte re-clear");
        for (String pair : List.of("wa", "bc", "de", "hl")) {
            String low = pair.substring(1), temp = low.equals("e") ? "a" : "e";
            String save = "\tld " + temp + ", " + low, reload = "\tld " + low + ", " + temp;
            List<String> copies = fixture(save, reload, "\tor " + low + ", " + low,
                "\tjr Z, .Lother", reload, "\tand " + pair + ", 255", "\tjr .Ljoin",
                ".Lother:", reload, ".Ljoin:", "\tcp wa, 0");
            List<String> simplified = AsmPatterns.eliminateRedundantLoads(copies);
            require(simplified.stream().filter(reload::equals).count() == 1, "inverse copies across fallthrough only");
            for (String writer : List.of("\tld " + low + ", 1", "\tld " + temp + ", 1", "\tcall _other", "\tunknown")) {
                List<String> blocked = fixture(save, writer, reload);
                require(AsmPatterns.eliminateRedundantLoads(blocked).equals(blocked), "copy invalidation " + writer);
            }
            List<String> masks = fixture("\tand " + pair + ", 255", "\tor " + low + ", " + low,
                "\tjr Z, .Lother", "\tand " + pair + ", 255", "\tjr .Ljoin",
                ".Lother:", "\tand " + pair + ", 255", ".Ljoin:", "\tcp wa, 0");
            List<String> normalized = AsmPatterns.eliminateRepeatedByteZeroes(masks);
            require(normalized.stream().filter(("\tand " + pair + ", 255")::equals).count() == 2, "fallthrough mask only");
            List<String> liveMask = fixture("\tand " + pair + ", 255", "\tand " + pair + ", 255", "\tjr Z, .Lother");
            require(AsmPatterns.eliminateRepeatedByteZeroes(liveMask).equals(liveMask), "live mask flags");
            List<String> neutral = fixture("\tand " + pair + ", 255", "\tand " + pair + ", 255",
                "\tld rl1, " + low, "\tjr .Ljoin", ".Ljoin:", "\textz xwa", "\tinc 1, wa", "\tcp wa, 0");
            require(AsmPatterns.eliminateRepeatedByteZeroes(neutral).size() == neutral.size() - 1, "flag-neutral operations after mask");
            List<String> carry = new ArrayList<>(neutral);
            carry.set(carry.size() - 3, "\tadc wa, 0");
            require(AsmPatterns.eliminateRepeatedByteZeroes(carry).equals(carry), "carry live through neutral operations");
            List<String> test = fixture("\tand " + pair + ", 255", save, "\tor " + low + ", " + low,
                "\tjr Z, .Lother", "\tcp wa, 0", "\tjr .Ljoin", ".Lother:", "\tcp wa, 1", ".Ljoin:");
            require(!AsmOptimizer.removeRedundantOrAfterAndForZeroBranch(test).contains("\tor " + low + ", " + low), "saved byte zero test");
            List<String> liveTest = new ArrayList<>(test);
            liveTest.set(4, "\tadc wa, 0");
            require(AsmOptimizer.removeRedundantOrAfterAndForZeroBranch(liveTest).equals(liveTest), "saved byte test flags live");
        }
        for (String byteReg : List.of("a", "w", "b", "c", "d", "e", "h", "l")) {
            String clear = "\txor " + byteReg + ", " + byteReg;
            List<String> repeated = fixture(clear, "\tnop", clear, "\tcp wa, 0");
            require(AsmOptimizer.eliminateRedundantXorWW(repeated).stream().filter(clear::equals).count() == 1, "repeated byte zero");
            List<String> unknown = fixture(clear, "\tunknown", clear, "\tcp wa, 0");
            require(AsmOptimizer.eliminateRedundantXorWW(unknown).equals(unknown), "unknown byte writer");
            List<String> bitFlags = fixture(clear, "\tld wa, 1", clear, "\tbit 2, a", "\tadc wa, 0");
            require(AsmOptimizer.eliminateRedundantXorWW(bitFlags).equals(bitFlags), "BIT preserves live carry");
        }
        for (String saved : List.of("bc", "de", "hl", "ix", "iy", "iz"))
            for (String pointer : List.of("xbc", "xde", "xhl", "xix", "xiy", "xiz"))
                for (String index : List.of("xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
                    if (pointer.equals("x" + saved) || index.equals("x" + saved) || index.equals(pointer)) continue;
                    List<String> input = fixture("\tld " + saved + ", wa", "\tld xwa, " + index,
                        "\tld " + pointer + ", 01200h", "\tadd " + pointer + ", xwa", "\tld wa, " + saved,
                        "\tand wa, 255", "\tld x" + saved + ", 0", "\tld xwa, 0", "\tcp wa, 0");
                    require(AsmOptimizer.foldSavedByteAddress(input).contains("\tadd " + pointer + ", " + index), "address register roles");
                }
        List<String> notDiamond = fixture("\tld (xsp+8), xwa", "\tand xwa, 1", "\tjr NZ, .Lset", "\tld xwa, (xsp+8)",
            "\tadd xwa, 2", ".Lset:", "\tld xwa, (xsp+8)", "\tcp xwa, 0");
        require(AsmPatterns.foldRestoredSingleBitTests(notDiamond).equals(notDiamond), "fallthrough into restore is not a diamond");
        for (int triples : new int[]{0, 1, 3}) {
            List<String> phi = new ArrayList<>(List.of("_generic_phi:", "\tadd xsp, -64", "\tjr Z, .Lother",
                "\tld (xsp+8), xwa", "\tjr .Ljoin", ".Lother:", "\tld (xsp+8), xwa", ".Ljoin:"));
            for (int i = 0; i < triples; i++) phi.addAll(List.of("\tld xwa, xde", "\tadd xwa, xix", "\tld xiy, xwa"));
            phi.addAll(List.of("\tld xwa, (xsp+8)", "\tld xhl, 0", "\tadd xsp, 64", "\tret"));
            List<String> output = AsmOptimizer.forwardPhiAccumulator(phi);
            require(output.contains("\tld xwa, xhl") && output.stream().noneMatch(l -> l.contains("(xsp+8)")), "arbitrary PHI prefix/predecessors");
        }
        for (String barrier : List.of("store i32 9, ptr inttoptr (i32 16384 to ptr), align 4",
                "%p = getelementptr i8, ptr inttoptr (i32 16384 to ptr), i32 4\nstore i32 9, ptr %p, align 4",
                "store i32 9, ptr %pointer, align 4", "call void @unknown()", "fence seq_cst")) {
            List<String> body = new ArrayList<>(List.of("%first = load i32, ptr @arbitraryGlobal, align 4"));
            body.addAll(List.of(barrier.split("\n")));
            body.add("%second = load i32, ptr @arbitraryGlobal, align 4");
            List<String> original = new ArrayList<>(body);
            Ir2Tlcs900.Translator.elideRedundantGlobalLoads(new Ir2Tlcs900.IrFunction("generic", "i32", "", body));
            require(body.equals(original), "integer/unknown pointer alias " + barrier);
        }
        System.out.println("AsmOptimizerGenericTest: PASS (" + checks + " checks)");
        if (args.length > 0 && args[0].equals("--suite")) {
            for (String test : List.of("AsmOptimizerRegisterLivenessTest", "AsmOptimizerAddressTest", "AsmOptimizerMaskChainTest", "AsmOptimizerBankPromotionTest", "AsmOptimizerByteTest",
                    "AsmOptimizerColorBitTest", "AsmOptimizerComputedCopiesTest", "AsmOptimizerDeadSpillTest", "AsmOptimizerDirectLoadTest",
                    "AsmOptimizerFollowupTest", "AsmOptimizerPhiAccumulatorTest", "AsmOptimizerPhiLayoutTest", "AsmOptimizerSavedByteTest",
                    "AsmOptimizerUnusedIndexTest", "AsmOptimizerZeroExtendTest", "LoopRegisterAllocatorOptimizationTest"))
                Class.forName(test).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        }
    }
}
