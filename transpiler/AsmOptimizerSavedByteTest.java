import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerSavedByteTest {
    private static List<String> probe(String... tail) {
        List<String> lines = new ArrayList<>(List.of("_probe:", "\tld ix, wa", "\tld xwa, xbc",
                "\tld xhl, 07000h", "\tadd xhl, xwa", "\tld wa, ix", "\tand wa, 0ffh"));
        for (String line : tail) lines.add("\t" + line);
        lines.add("\tret");
        return lines;
    }
    public static void main(String[] args) {
        List<String> input = probe("ld (xhl+0), a", "ld xwa, xbc", "ld ix, 0", "cp xwa, 1");
        List<String> folded = AsmOptimizer.foldSavedByteAddress(input);
        if (folded.size() != input.size() - 3 || !folded.contains("\tadd xhl, xbc")
                || !folded.get(1).equals("\tand wa, 0ffh")) throw new AssertionError("not folded");
        if (!AsmOptimizer.foldSavedByteAddress(folded).equals(folded)) throw new AssertionError("not stable");
        for (String use : List.of("push xwa", "jr Z, .Lread", "call _other")) {
            List<String> guarded = probe(use, "ld xwa, 0", "ld ix, 0", "cp wa, 0");
            if (!AsmOptimizer.foldSavedByteAddress(guarded).equals(guarded))
                throw new AssertionError("unsafe fold: " + use);
        }
        for (String use : List.of("push ix", "push xix")) {
            List<String> live = probe(use, "ld xwa, 0", "inc 1, xwa", "cp wa, 0");
            List<String> fallback = AsmOptimizer.foldSavedByteAddress(live);
            if (fallback.size() != live.size() - 2 || !fallback.get(1).equals("\tld ix, wa")
                    || !fallback.contains("\tadd xhl, xbc")) throw new AssertionError("live scratch fallback");
        }
        List<String> entry = new ArrayList<>(input);
        entry.set(3, ".Lentry:");
        if (!AsmOptimizer.foldSavedByteAddress(entry).equals(entry)) throw new AssertionError("label crossed");
        System.out.println("AsmOptimizerSavedByteTest: PASS");
    }
}
