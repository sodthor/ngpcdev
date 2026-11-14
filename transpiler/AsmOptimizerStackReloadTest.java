import java.util.*;

/** Dead private-stack reads must not survive address materialization. */
public final class AsmOptimizerStackReloadTest {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static List<String> fixture(String... instructions) {
        var code = new ArrayList<String>(List.of(AsmPatterns.BANK_ZERO_ABI,
            "_generic_store:", AsmPatterns.VOID_SCRATCH_ABI, "\tadd xsp, -16"));
        code.addAll(List.of(instructions));
        code.addAll(List.of("\tadd xsp, 16", "\tret"));
        return code;
    }
    public static void main(String[] args) throws Exception {
        var original = fixture("\txor w, w", "\tld a, (xsp+34)",
            "\tld xhl, (xsp+4)", "\tld a, (xsp+34)", "\tld (xhl+0), a");
        var optimized = AsmOptimizer.runPasses(original, false);
        require(!optimized.contains("\txor w, w"), "dead high byte and flags");
        require(optimized.stream().filter("\tld a, (xsp+34)"::equals).count() == 1, "one palette load");
        require(optimized.contains("\tld xhl, (xsp+4)") && optimized.contains("\tld (xhl+0), a"), "address and store retained");
        var module = new ArrayList<>(original);
        module.addAll(List.of("_other:", ".Lreused:", "\tret", "_third:", ".Lreused:", "\tret"));
        require(!AsmOptimizer.runPasses(module, false).contains("\txor w, w"), "unrelated duplicate local labels");
        var duplicate = fixture("\txor w, w", "\tjr .Lambiguous", ".Lambiguous:", "\tcp wa, 0", ".Lambiguous:");
        require(!AsmPatterns.flagsDeadAlongSuccessors(duplicate, 4), "same-function duplicate labels block proof");
        var foreign = fixture("\txor w, w", "\tjr .Lforeign");
        foreign.addAll(List.of("_other:", ".Lforeign:", "\tcp wa, 0", "\tret"));
        require(!AsmPatterns.flagsDeadAlongSuccessors(foreign, 4), "no cross-function flag proof");
        for (String reg : List.of("a", "w", "b", "c", "d", "e", "h", "l", "wa", "bc", "de", "hl", "ix", "iy", "iz",
                "xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
            String first = "\tld " + reg + ", (xsp+8)";
            var dead = fixture(first, "\tnop", "\tld " + reg + ", (xsp+12)", "\tld (xsp+0), " + reg);
            require(!AsmPatterns.eliminateDeadRegisterCopies(dead).contains(first), "dead stack read " + reg);
            var used = fixture(first, "\tld (xsp+0), " + reg, "\tld " + reg + ", (xsp+12)");
            require(AsmPatterns.eliminateDeadRegisterCopies(used).contains(first), "live stack read " + reg);
            for (String source : List.of("(08100h)", "(xhl+0)", "(_device)", "(xsp+)")) {
                String read = "\tld " + reg + ", " + source;
                var device = fixture(read, "\tld " + reg + ", 0");
                require(AsmPatterns.eliminateDeadRegisterCopies(device).contains(read), "observable/unsupported read " + source);
            }
            for (String barrier : List.of("\tcall _unknown", "\tunknown", "\tld sr, 0")) {
                var blocked = fixture(first, barrier, "\tld " + reg + ", 0");
                require(AsmPatterns.eliminateDeadRegisterCopies(blocked).contains(first), "barrier " + barrier);
            }
        }
        var partial = fixture("\tld wa, (xsp+8)", "\tld a, (xsp+12)", "\tld (xhl), wa");
        require(AsmPatterns.eliminateDeadRegisterCopies(partial).contains("\tld wa, (xsp+8)"), "live upper byte");
        var flags = fixture("\txor w, w", "\tld a, (xsp+8)", "\tld xhl, (xsp+4)",
            "\tld a, (xsp+8)", "\tadc xhl, 0", "\tld (xhl), a");
        require(AsmOptimizer.runPasses(flags, false).contains("\txor w, w"), "live XOR carry");
        var high = fixture("\txor w, w", "\tld a, (xsp+8)", "\tld xhl, (xsp+4)",
            "\tld a, (xsp+8)", "\tld (xhl), wa");
        require(AsmOptimizer.runPasses(high, false).contains("\txor w, w"), "live high byte");
        var branch = fixture("\tld a, (xsp+8)", "\tjr Z, .Luse", "\tld a, 0", "\tjr .Lend",
            ".Luse:", "\tld (xhl), a", ".Lend:");
        require(AsmPatterns.eliminateDeadRegisterCopies(branch).contains("\tld a, (xsp+8)"), "live on one successor");
        var noAbi = new ArrayList<>(original);
        noAbi.remove(AsmPatterns.BANK_ZERO_ABI);
        require(AsmPatterns.eliminateDeadRegisterCopies(noAbi).equals(noAbi), "no stack/bank ABI assumption");
        System.out.println("AsmOptimizerStackReloadTest: PASS (" + checks + " checks)");
    }
}