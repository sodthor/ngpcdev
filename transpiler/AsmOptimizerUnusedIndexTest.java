import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerUnusedIndexTest {
    private static List<String> probe(String register, String... body) {
        List<String> lines = new ArrayList<>(List.of("_probe:", "\tpush " + register));
        for (String line : body) lines.add("\t" + line);
        lines.add("\tpop " + register);
        lines.add("\tret");
        return lines;
    }
    public static void main(String[] args) {
        for (String reg : List.of("ix", "iy", "iz", "xix", "xiy", "xiz")) {
            List<String> input = probe(reg, "ld " + reg + ", " + (reg.startsWith("x") ? "xwa" : "wa"), "ld wa, 1");
            List<String> output = AsmOptimizer.removeUnusedIndexSaves(input);
            if (!output.equals(List.of("_probe:", "\tld wa, 1", "\tret"))) throw new AssertionError("not removed " + reg);
            if (!AsmOptimizer.removeUnusedIndexSaves(output).equals(output)) throw new AssertionError("unstable");
        }
        List<String> syscall = probe("ix", "ld ix, wa", "ldb rw3,4", "ldb rb3,1", "ldb rc3,6", "swi 1");
        if (AsmOptimizer.removeUnusedIndexSaves(syscall).size() != syscall.size() - 3)
            throw new AssertionError("INTLVSET cleanup");
        for (String hazard : List.of("ld wa, ix", "ld xwa, xix", "ld a, (xix)", "add ix, 1",
                "ld ix, (xhl)", "ld xix, xwa", "ld a, (xsp+8)", "call _other", "swi 1", "ldf 1",
                "unknown", "jr .Lmissing", "ld qix, 0")) {
            List<String> input = probe("ix", "ld ix, wa", hazard);
            if (!AsmOptimizer.removeUnusedIndexSaves(input).equals(input)) throw new AssertionError("unsafe " + hazard);
        }
        System.out.println("AsmOptimizerUnusedIndexTest: PASS");
    }
}
