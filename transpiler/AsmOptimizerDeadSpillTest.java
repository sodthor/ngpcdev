import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerDeadSpillTest {
    private static List<String> fixture(String... body) {
        List<String> lines = new ArrayList<>(List.of("_test:", "\tadd xsp, -32"));
        lines.addAll(List.of(body));
        lines.add("\tadd xsp, 32");
        lines.add("\tret");
        return lines;
    }

    private static void check(boolean removed, String name, String... body) {
        List<String> original = fixture(body);
        List<String> optimized = AsmOptimizer.eliminateOverwrittenSpills(original);
        boolean present = optimized.contains("\tld (xsp+8), xwa");
        if (present == removed) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        List<String> maskedBranch = List.of("_test:", "\tcp xwa, 020000h", "\tscc C, a",
            "\tand wa, 0ffh", "\tor a, a", "\tjrl NZ, .Linside",
            "\tld xwa, xde", "\tor xwa, 3", "\tret", ".Linside:",
            "\tld xwa, xbc", "\tand xwa, 4", "\tret");
        List<String> directBranch = Ir2Tlcs900.simplifyBoolBranchPatterns(maskedBranch);
        if (!directBranch.contains("\tjrl C, .Linside") || directBranch.size() != maskedBranch.size() - 3)
            throw new AssertionError("masked boolean branch must use the original compare flags");
        List<String> invertedBranch = new ArrayList<>(maskedBranch);
        invertedBranch.set(5, "\tjrl Z, .Linside");
        if (!Ir2Tlcs900.simplifyBoolBranchPatterns(invertedBranch).contains("\tjrl NC, .Linside"))
            throw new AssertionError("zero branch must invert the SCC condition");
        for (int successor : new int[] {6, 10}) {
            for (String live : List.of("\tld (xsp+8), wa", "\tadc xwa, 0", "\tcustom xde")) {
                List<String> blocked = new ArrayList<>(maskedBranch);
                blocked.add(successor, live);
                if (!Ir2Tlcs900.simplifyBoolBranchPatterns(blocked).equals(blocked))
                    throw new AssertionError("live successor state must prevent folding: " + successor + " " + live);
            }
        }
        for (String barrier : List.of("\tand wa, 254", ".Lentry:")) {
            List<String> blocked = new ArrayList<>(maskedBranch);
            blocked.set(3, barrier);
            if (!Ir2Tlcs900.simplifyBoolBranchPatterns(blocked).equals(blocked))
                throw new AssertionError("noncanonical mask or incoming edge must prevent folding");
        }
        List<String> unresolved = new ArrayList<>(maskedBranch);
        unresolved.set(5, "\tjrl NZ, .Lmissing");
        if (!Ir2Tlcs900.simplifyBoolBranchPatterns(unresolved).equals(unresolved))
            throw new AssertionError("unresolved successor must prevent folding");
        List<String> fallthroughLabel = new ArrayList<>(maskedBranch);
        fallthroughLabel.add(6, ".Lbb_test_fallthrough:");
        if (!Ir2Tlcs900.simplifyBoolBranchPatterns(
                Ir2Tlcs900.removeUnreferencedLabels(fallthroughLabel)).contains("\tjrl C, .Linside"))
            throw new AssertionError("unreferenced fallthrough labels must be cleaned before the late fold");
        List<String> mergedFlags = new ArrayList<>(maskedBranch);
        mergedFlags.set(7, "\tjrl .Lmerge");
        mergedFlags.addAll(List.of(".Lmerge:", "\tadd xde, 1", "\tret"));
        if (!Ir2Tlcs900.simplifyBoolBranchPatterns(mergedFlags).contains("\tjrl C, .Linside"))
            throw new AssertionError("resolved unconditional merge must permit a proven flag overwrite");
        for (String barrier : List.of("\tadc xde, 0", "\tbit 2, a", "\tcall _other",
            "\tjrl .Lmerge", "\tjrl .Lmissing", "\tpop sr", "\tpush sr", "\tcustom xde")) {
            List<String> blocked = new ArrayList<>(mergedFlags);
            blocked.set(blocked.size() - 2, barrier);
            if (!Ir2Tlcs900.simplifyBoolBranchPatterns(blocked).equals(blocked))
                throw new AssertionError("merge must preserve live or unknown flags: " + barrier);
        }
        System.out.println("Masked boolean branches: PASS (21 cases)");

        for (String destination : List.of("a", "w", "wa", "xwa")) {
            List<String> reload = List.of("_test:", "\tld xwa, xde", "\tcp xwa, 7",
                "\tscc Z, " + destination, "\tld (xsp+8), " + destination,
                "\tld xwa, xde", "\tadd xwa, 1", "\tret");
            if (AsmOptimizer.eliminateRedundantRegReload(reload).stream()
                .filter("\tld xwa, xde"::equals).count() != 2)
            throw new AssertionError("SCC must invalidate register reload through " + destination);
            List<String> constants = List.of("_test:", "\tld xwa, 7", "\tscc Z, " + destination,
                "\tld xwa, 7", "\tret");
            if (AsmOptimizer.eliminateRedundantRegLoads(constants).stream()
                .filter("\tld xwa, 7"::equals).count() != 2)
            throw new AssertionError("SCC must invalidate constant reload through " + destination);
        }
        System.out.println("SCC register invalidation: PASS (8 cases)");
        check(true, "straight-line overwrite", "\tld (xsp+8), xwa", "\tld (xsp+8), xhl");
        check(true, "overwrite on both edges", "\tld (xsp+8), xwa", "\tjr Z, .Lother",
            "\tld (xsp+8), xhl", "\tjr .Ldone", ".Lother:", "\tld (xsp+8), xde", ".Ldone:");
        check(false, "read on one edge", "\tld (xsp+8), xwa", "\tjr Z, .Lother",
            "\tld (xsp+8), xhl", "\tjr .Ldone", ".Lother:", "\tld xde, (xsp+8)", ".Ldone:");
        check(false, "overlapping read", "\tld (xsp+8), xwa", "\tld wa, (xsp+10)", "\tld (xsp+8), xhl");
        check(false, "partial overwrite", "\tld (xsp+8), xwa", "\tld (xsp+8), wa", "\tld xde, (xsp+8)");
        check(true, "split complete overwrite", "\tld (xsp+8), xwa", "\tld (xsp+8), wa", "\tld (xsp+10), hl");
        check(false, "call barrier", "\tld (xsp+8), xwa", "\tcall _other", "\tld (xsp+8), xhl");
        check(false, "stack movement", "\tld (xsp+8), xwa", "\tpush xhl", "\tld (xsp+8), xhl");
        check(false, "escaping frame", "\tld (xsp+8), xwa", "\tlda xhl, (xsp+8)", "\tld (xsp+8), xhl");
        check(false, "loop read", "\tld (xsp+8), xwa", ".Lloop:", "\tld wa, (xsp+8)",
            "\tjr NZ, .Lloop", "\tld (xsp+8), xhl");
        check(false, "unresolved branch", "\tld (xsp+8), xwa", "\tjp xhl", "\tld (xsp+8), xhl");
        check(false, "unknown operation", "\tld (xsp+8), xwa", "\tcustom xhl", "\tld (xsp+8), xhl");
        check(false, "copied stack pointer", "\tld (xsp+8), xwa", "\tld xhl, xsp", "\tld (xsp+8), xhl");
        check(false, "adjusted stack", "\tld (xsp+8), xwa", "\tinc 4, xsp", "\tld (xsp+8), xhl");
        check(false, "overlap below slot", "\tld (xsp+8), xwa", "\tld xde, (xsp+6)", "\tld (xsp+8), xhl");
        check(false, "alternate exit", "\tld (xsp+8), xwa", "\tjr Z, .Lexit",
            "\tld (xsp+8), xhl", ".Lexit:");
        List<String> argument = fixture("\tld (xsp+36), xwa", "\tld (xsp+36), xhl");
        if (!AsmOptimizer.eliminateOverwrittenSpills(argument).equals(argument))
            throw new AssertionError("stores beyond the private frame must remain");
        List<String> noFrame = List.of("_test:", "\tld (xsp+8), xwa", "\tld (xsp+8), xhl", "\tret");
        if (!AsmOptimizer.eliminateOverwrittenSpills(noFrame).equals(noFrame))
            throw new AssertionError("functions without a private frame must remain unchanged");
        System.out.println("AsmOptimizerDeadSpillTest: PASS (18 cases)");
        for (String operation : List.of("and", "or", "xor")) {
            for (String register : List.of("wa", "xwa")) {
                String store = "\tld (xsp+8), " + register;
                List<String> logicalRead = fixture(store, "\t" + operation + " " + register + ", (xsp+8)",
                    "\tld (xsp+8), xhl");
                if (!Ir2Tlcs900.eliminateSpills(logicalRead).contains(store))
                    throw new AssertionError("internal spill cleanup must retain " + operation + " " + register + " operand");
            }
            String store = "\tld (xsp+8), wa";
            List<String> shiftedRead = fixture(store, "\tpush wa", "\t" + operation + " wa, (xsp+10)",
                "\tpop wa", "\tld (xsp+8), hl");
            if (!Ir2Tlcs900.eliminateSpills(shiftedRead).contains(store))
                throw new AssertionError("internal spill cleanup must track logical reads after push: " + operation);
        }
        List<String> unused = fixture("\tld (xsp+8), wa", "\tld (xsp+8), hl");
        if (Ir2Tlcs900.eliminateSpills(unused).contains("\tld (xsp+8), wa"))
            throw new AssertionError("internal cleanup must still remove overwritten unused stores");
        System.out.println("Internal logical spill reads: PASS (10 cases)");
    }
}