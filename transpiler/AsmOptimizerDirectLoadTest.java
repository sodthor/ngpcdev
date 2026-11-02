import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerDirectLoadTest {
    private static int cases;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        cases++;
    }

    private static List<String> probe(String load, String copy, String... tail) {
        List<String> lines = new ArrayList<>(List.of("_probe:", "\t" + load, "\t" + copy));
        for (String instruction : tail) lines.add("\t" + instruction);
        lines.add("\tret");
        return lines;
    }

    private static void folded(List<String> input, String expected) {
        List<String> output = AsmOptimizer.foldDirectLoadCopies(input);
        require(output.size() < input.size() && output.contains("\t" + expected), "fold: " + input);
        require(AsmOptimizer.foldDirectLoadCopies(output).equals(output), "idempotence: " + input);
    }

    private static void retained(List<String> input) {
        require(AsmOptimizer.foldDirectLoadCopies(input).equals(input), "guard: " + input);
    }

    public static void main(String[] args) {
        for (String source : List.of("1", "-1", "012345678h", "0x1234", "_track",
                "(_track)", "(04000h)", "(xsp+172)", "(xix)", "(xhl+wa)", "(xbc-4)", "xde", "xde1")) {
            folded(probe("ld xwa, " + source, "ld xbc, xwa", "ld xwa, 0"), "ld xbc, " + source);
        }
        for (String[] pair : List.of(new String[]{"xhl", "xiy"}, new String[]{"wa", "hl"},
                new String[]{"ix", "iy"}, new String[]{"a", "e"}, new String[]{"w", "b"})) {
            folded(probe("ld " + pair[0] + ", 7", "ld " + pair[1] + ", " + pair[0],
                "ld " + pair[0] + ", 0"), "ld " + pair[1] + ", 7");
        }
        folded(probe("ld wa, (xhl+0)", "ld hl, wa", "ld a, 0", "ld w, 0", "push xwa", "pop xwa"),
            "ld hl, (xhl+0)");
        folded(probe("ld a, (xix)", "ld e, a", "ld a, 0", "push xwa", "pop xwa"), "ld e, (xix)");
        folded(probe("ld w, 7", "ld b, w", "ld e, a", "ld w, 0"), "ld b, 7");
        folded(probe("ld xwa, (xwa)", "ld xbc, xwa", "ld xwa, 0"), "ld xbc, (xwa)");
        folded(probe("ld xwa, (xbc)", "ld xbc, xwa", "ld xwa, 0"), "ld xbc, (xbc)");
        folded(probe("ld xwa, 1", "ld xiy, xwa", ".Ljoin:", "; phi", "ld xwa, (xsp+172)"), "ld xiy, 1");
        folded(probe("ld xwa, 1", "ld xiy, xwa", "jr .Ljoin", ".Ljoin:", "ld xwa, 0"), "ld xiy, 1");
        folded(probe("ld xwa, 1", "ld xiy, xwa", "jr NZ, .Lother", "ld xwa, 0", "ret",
            ".Lother:", "ld xwa, 2"), "ld xiy, 1");
        folded(probe("ld xwa, 1", "ld xiy, xwa", "pop xwa"), "ld xiy, 1");
        folded(probe("ld xbc, 1", "ld xiy, xbc", "jr C, .Lother", "ld xbc, 0", "ret",
            ".Lother:", "ld xbc, 2"), "ld xiy, 1");

        for (String use : List.of("ld xhl, xwa", "ld hl, wa", "ld l, a", "ld l, w",
                "ld xhl, (xwa)", "ld a, (xhl+wa)", "push xwa", "add xwa, 1",
                "ld xwa, (xwa)", "ld xhl, xwa0", "ld hl, rwa0", "ld hl, qwa0",
                "ld a, rw15", "ld hl, qwa", "ld sr, 0", "pop sr", "push sr", "ldf 1",
                "call _other", "calr _other", "unknown", "ldir", "mul xbc, 2", "ret",
                "jp (xix)", "jr .Lmissing")) {
            retained(probe("ld xwa, 1", "ld xiy, xwa", use, "ld xwa, 0"));
        }
        retained(probe("ld xwa, 1", "ld xiy, xwa", "ld wa, 0", "ld xhl, xwa", "ld xwa, 0"));
        retained(probe("ld wa, 1", "ld hl, wa", "ld a, 0", "ld e, w", "ld wa, 0"));
        retained(probe("ld xwa, 1", "ld xiy, xwa", "jr NZ, .Lread", "ld xwa, 0", "ret",
            ".Lread:", "push wa", "ld xwa, 0"));
        retained(probe("ld xwa, 1", "ld xiy, xwa", "jr .Lloop", ".Lloop:", "jr Z, .Lread",
            "jr .Lloop", ".Lread:", "ld xhl, xwa", "ld xwa, 0"));
        retained(probe("ld xwa, 1", "ld xiy, xwa", "jr .Ljoin", ".Ljoin:", "nop"));
        retained(probe("ld xwa, 1", "ld xiy, xwa", "jr .Ljoin", ".Ljoin:", "nop", ".Ljoin:", "ld xwa, 0"));
        retained(probe("ld xwa, 1", "ld xsp, xwa", "ld xwa, 0"));
        retained(probe("ld xsp, 1", "ld xiy, xsp", "ld xsp, 0"));
        retained(probe("ld wa, 1", "ld xhl, wa", "ld wa, 0"));
        retained(probe("ld a, 1", "ld w, a", "ld a, 0"));
        for (String source : List.of("(xix+)", "(xix-)", "(-xix)", "(xix+:4)", "(xix,xiy)", "wa", "sr")) {
            retained(probe("ld xwa, " + source, "ld xiy, xwa", "ld xwa, 0"));
        }
        List<String> entry = probe("ld xwa, 1", "ld xiy, xwa", "ld xwa, 0");
        entry.add(2, ".Lentry:");
        retained(entry);
        List<String> comments = List.of("_probe:", "\tLD XWA, (_track) ; load", "\t; phi", "",
            "\tLD XBC, XWA ; copy", ".Ljoin:", "\tld xwa, 0", "\tret");
        List<String> commented = AsmOptimizer.foldDirectLoadCopies(comments);
        require(commented.contains("\tld xbc, (_track) ; load") && commented.contains("\t; copy")
            && commented.contains("\t; phi"), "comments and case preserved");
        require(AsmOptimizer.foldDirectLoadCopies(commented).equals(commented), "commented idempotence");
        List<String> chain = probe("ld xwa, 7", "ld xbc, xwa", "ld xiy, xbc", "ld xwa, 0", "ld xbc, 0");
        folded(chain, "ld xiy, 7");
        List<String> original = new ArrayList<>(chain);
        AsmOptimizer.foldDirectLoadCopies(chain);
        require(chain.equals(original), "input not mutated");
        List<String> marked = List.of("; experimental-loop-register-allocation",
            AsmOptimizer.PROTECTED_BEGIN + "probe", "_probe:", "\tld xwa, (_track)", "\tld xbc, xwa",
            "\t; phi copies", "\tld xwa, 1", "\tld xiy, xwa", ".Ljoin:", "\tld xwa, (xsp+172)",
            "\tret", AsmOptimizer.PROTECTED_END + "probe");
        List<String> optimized = AsmOptimizer.runPasses(marked, false);
        require(optimized.contains("\tld xbc, (_track)") && optimized.contains("\tld xiy, 1"), "global pass enabled");
        require(!optimized.contains("\tld xbc, xwa") && !optimized.contains("\tld xiy, xwa"), "MODE7 pair removed");
        List<String> old = new ArrayList<>(marked);
        old.removeIf(line -> line.startsWith("; experimental-register-function-"));
        require(AsmOptimizer.runPasses(old, false).equals(old), "unfenced experimental output retained");
        System.out.println("AsmOptimizerDirectLoadTest: PASS (" + cases + " checks)");
    }
}
