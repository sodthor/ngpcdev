import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerColorBitTest {
    private static List<String> fixture() {
        return List.of(
            "_test:",
            "\tld a, (xhl+0)",
            "\textz wa",
            "\textz xwa",
            "\tld (xsp+22), xwa",
            "\tand xwa, 4",
            "\tjr NZ, .Lflip",
            "\tld xwa, (xsp+22)",
            "\tld (xsp+30), xwa",
            "\tjr .Lmerge",
            ".Lflip:",
            "\tld xwa, (xsp+22)",
            "\tsla 16, xwa",
            "\tadd xwa, 0fffd0000h",
            "\tld (xsp+30), xwa",
            ".Lmerge:",
            "\tld xwa, xbc1",
            "\tadd xwa, xde",
            "\tret"
        );
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        List<String> byteMask = List.of("_test:", "\tld a, (xhl+0)", "\tand wa, 0ffh",
            "\textz wa", "\textz xwa", "\tand xwa, 4", "\tret");
        List<String> withoutMask = AsmOptimizer.eliminateByteMasksBeforeExtz(byteMask);
        require(!withoutMask.contains("\tand wa, 0ffh"), "redundant byte mask must disappear");
        require(withoutMask.contains("\textz wa"), "byte canonicalization must remain");
        for (String next : List.of("\tjr Z, .Lexit", "\tadc xwa, 0", "\tcall _other", ".Lentry:",
            "\tpush sr", "\tld bc, sr")) {
            List<String> barrier = List.of("_test:", "\tand wa, 0ffh", "\textz wa", next, "\tret");
            require(AsmOptimizer.eliminateByteMasksBeforeExtz(barrier).equals(barrier),
                "live or unknown flags must retain the mask: " + next);
        }
        List<String> narrowMask = List.of("_test:", "\tand wa, 1", "\textz wa", "\tand xwa, 4", "\tret");
        require(AsmOptimizer.eliminateByteMasksBeforeExtz(narrowMask).equals(narrowMask),
            "boolean truncation is not redundant");

        List<String> original = fixture();
        List<String> optimized = AsmOptimizer.foldColorAndBitTestToRegBit(original);
        require(optimized.contains("\tbit 2, a"), "stack color must use a non-destructive test");
        require(!optimized.contains("\tld xwa, (xsp+22)"), "both color reloads must disappear");
        require(optimized.contains("\tld (xsp+22), xwa"), "retain the potentially live stack store");
        require(optimized.size() == original.size() - 2, "remove exactly two reloads");

        List<String> extraEntry = new ArrayList<>(original);
        extraEntry.add(1, "\tjr .Lflip");
        require(AsmOptimizer.foldColorAndBitTestToRegBit(extraEntry).equals(extraEntry),
            "an extra predecessor must prevent folding");

        List<String> liveFlags = new ArrayList<>(original);
        liveFlags.set(17, "\tadc xwa, xde");
        require(AsmOptimizer.foldColorAndBitTestToRegBit(liveFlags).equals(liveFlags),
            "live carry flags must prevent folding");

        List<String> wrongReload = new ArrayList<>(original);
        wrongReload.set(11, "\tld xwa, (xsp+24)");
        require(AsmOptimizer.foldColorAndBitTestToRegBit(wrongReload).equals(wrongReload),
            "a different reload slot must prevent folding");

        System.out.println("AsmOptimizerColorBitTest: PASS");
    }
}