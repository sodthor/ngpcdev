import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerBankPromotionTest {
    private static List<String> probe(String... extra) {
        List<String> lines = new ArrayList<>(List.of("; experimental-loop-register-allocation", AsmOptimizer.PROTECTED_BEGIN + "probe", "_probe:",
                "\tadd xsp, -8", "\tld xwa, 7", "\tld (xsp+0), xwa", "\tld xwa, (xsp+0)",
                "\tadd xwa, (xsp+0)", "\tld xhl, (xsp+0)"));
        for (String instruction : extra) lines.add("\t" + instruction);
        lines.addAll(List.of("\tadd xsp, 8", "\tret", AsmOptimizer.PROTECTED_END + "probe"));
        return lines;
    }
    public static void main(String[] args) {
        List<String> original = probe();
        List<String> output = AsmOptimizer.promoteAllocatedFrameSlots(original);
        if (!output.contains("\tpush xwa2") || !output.contains("\tpop xwa2")
                || !output.contains("\tld xwa2, xwa") || !output.contains("\tadd xwa, xwa2"))
            throw new AssertionError("promotion missing: " + output);
        if (!AsmOptimizer.promoteAllocatedFrameSlots(output).equals(output)) throw new AssertionError("not stable");
        for (String hazard : List.of("ld a, (xsp+1)", "lda xhl, (xsp+0)", "ld xhl, xsp",
                "call _unknown", "ld xhl, (xsp+16)")) {
            List<String> input = probe(hazard);
            if (!AsmOptimizer.promoteAllocatedFrameSlots(input).equals(input))
                throw new AssertionError("unsafe: " + hazard);
        }
        List<String> uninitialized = new ArrayList<>(original);
        uninitialized.set(5, "\tld xwa, (xsp+0)");
        if (!AsmOptimizer.promoteAllocatedFrameSlots(uninitialized).equals(uninitialized))
            throw new AssertionError("uninitialized read");
        List<String> nested = probe("push xbc", "add xsp, -4", "ld xhl, (xsp+8)",
                "ld (xsp+0), xwa", "add xsp, 4", "pop xbc");
        List<String> rewritten = AsmOptimizer.promoteAllocatedFrameSlots(nested);
        if (!rewritten.contains("\tld xhl, xwa2") || !rewritten.contains("\tld (xsp+0), xwa"))
            throw new AssertionError("nested frame normalization");
        List<String> compactInput = probe("ld (xsp+4), xwa", "push xbc", "add xsp, -4",
                "ld xhl, (xsp+12)", "ld (xsp+0), xwa", "add xsp, 4", "pop xbc");
        List<String> compacted = AsmOptimizer.compactAllocatedFrames(
                AsmOptimizer.promoteAllocatedFrameSlots(compactInput));
        if (!compacted.contains("\tadd xsp, -4\t; frame: 4 bytes")
                || !compacted.contains("\tld (xsp+0), xwa")
                || !compacted.contains("\tld xhl, (xsp+8)"))
            throw new AssertionError("post-promotion compaction: " + compacted);
        if (!AsmOptimizer.compactAllocatedFrames(compacted).equals(compacted))
            throw new AssertionError("compaction not stable");
        System.out.println("AsmOptimizerBankPromotionTest: PASS");
    }
}
