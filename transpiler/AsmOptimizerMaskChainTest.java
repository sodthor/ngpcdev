import java.util.*;

public final class AsmOptimizerMaskChainTest {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static List<String> chain(String word, String low) {
        return List.of("\tand " + word + ", 0ffh", "\tand " + low + ", 1",
            "\tand " + word + ", 255", "\txor " + low + ", 1",
            "\tand " + word + ", 0xff", "\textz " + word);
    }
    record State(long value, String flags) {}
    // Independent mixed-width execution. A flag-setting operation is recorded
    // by its opcode, operand width and result, preserving all logical flags.
    private static State evaluate(List<String> code, String word, String low, long initial) {
        long value = initial;
        String flags = "incoming";
        for (String raw : code) {
            String text = raw.split(";", 2)[0].strip();
            if (text.isEmpty()) continue;
            if (text.equals("extz " + word)) { value &= ~0xff00L; continue; }
            int comma = text.indexOf(',');
            if (comma < 0) continue;
            String op = text.substring(0, text.indexOf(' '));
            String reg = text.substring(text.indexOf(' ') + 1, comma).strip();
            int width = reg.equals(low) ? 8 : 16;
            long mask = (1L << width) - 1;
            String number = text.substring(comma + 1).strip();
            long immediate = number.endsWith("h") ? Long.parseLong(number.substring(0, number.length() - 1), 16)
                : number.startsWith("0x") ? Long.parseLong(number.substring(2), 16) : Long.parseLong(number);
            long result = switch (op) {
                case "and" -> value & immediate & mask;
                case "or" -> (value | immediate) & mask;
                case "xor" -> (value ^ immediate) & mask;
                default -> throw new AssertionError(op);
            };
            value = value & ~mask | result;
            flags = op + ":" + width + ":" + result;
        }
        return new State(value, flags);
    }
    public static void main(String[] args) {
        for (String word : List.of("wa", "bc", "de", "hl")) {
            String low = switch (word) { case "wa" -> "a"; case "bc" -> "c"; case "de" -> "e"; default -> "l"; };
            List<String> input = chain(word, low);
            List<String> expected = List.of("\tand " + low + ", 1", "\txor " + low + ", 1", "\tand " + word + ", 0xff");
            List<String> output = AsmPatterns.simplifyByteMaskChains(input);
            require(output.equals(expected), "three instructions removed for " + word);
            require(AsmPatterns.simplifyByteMaskChains(output).equals(output), "idempotent");
            require(Ir2Tlcs900.removeRedundantMaskBeforeMask(input).equals(output), "shared compiler pass");
            for (int value = 0; value < 65536; value++) {
                long initial = 0xabcd0000L | value;
                require(evaluate(input, word, low, initial).equals(evaluate(output, word, low, initial)), "mixed-width values/flags " + word + " " + value);
            }
            for (String barrier : List.of("\tjr Z, .Lexit", "\tscc Z, " + low, "\tpush sr", "\tcall _other", ".Lentry:",
                    "\tld " + low + ", " + word.charAt(0), "\tld xix, x" + word, "\tld " + low + ", (x" + word + ")",
                    "\tand " + word + ", 256", "\tunknown")) {
                List<String> blocked = List.of(input.get(0), barrier, input.get(2));
                require(AsmPatterns.simplifyByteMaskChains(blocked).equals(blocked), "live word/flags/barrier " + barrier);
            }
            List<String> liveFlags = List.of(input.get(0), "\textz " + word, "\tpush sr");
            require(AsmPatterns.simplifyByteMaskChains(liveFlags).equals(List.of(input.get(0), "\tpush sr")), "keep AND flags; EXTZ is neutral");
            List<String> byteFlags = List.of(input.get(0), input.get(3), "\textz " + word, "\tpush sr");
            require(AsmPatterns.simplifyByteMaskChains(byteFlags).equals(byteFlags.subList(1, byteFlags.size())), "byte XOR flags overwrite first mask");
            List<String> comments = new ArrayList<>(input);
            comments.set(0, input.get(0) + " ; normalize");
            List<String> commented = AsmPatterns.simplifyByteMaskChains(comments);
            require(commented.contains("\t; normalize"), "comments retained");
            List<String> pipeline = new ArrayList<>(List.of("_probe:"));
            pipeline.addAll(input);
            pipeline.addAll(List.of("\tpush " + word, "\tcall _opaque", "\tret"));
            List<String> optimized = AsmOptimizer.runPasses(pipeline, false);
            require(optimized.contains(expected.get(0)) && optimized.contains(expected.get(1)) && optimized.contains(expected.get(2))
                && !optimized.contains("\textz " + word), "production pipeline");
        }
        System.out.println("AsmOptimizerMaskChainTest: PASS (" + checks + " checks)");
    }
}
