import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerPhiLayoutTest {
    public static void main(String[] args) {
        List<String> input = List.of("\tcp xwa, 020h", "\tjr NZ, .Lbb_main_phi_edge_1",
                "\t; exit copies", "\tld xiz, 020h", "\tld xwa, 0", "\tld (xsp+12), xwa",
                "\tjr .Lbb_main_36", ".Lbb_main_phi_edge_1:", "\t; loop copies",
                "\tld xix, (xsp+8)", "\tjrl .Lbb_main_22", ".Lbb_main_36:");
        List<String> expected = List.of("\tcp xwa, 020h", "\tjr Z, .Lbb_main_phi_edge_1",
                "\t; loop copies", "\tld xix, (xsp+8)", "\tjrl .Lbb_main_22",
                ".Lbb_main_phi_edge_1:", "\t; exit copies", "\tld xiz, 020h",
                "\tld xwa, 0", "\tld (xsp+12), xwa", ".Lbb_main_36:");
        if (!AsmOptimizer.layoutPhiCopyBranches(input).equals(expected))
            throw new AssertionError("layout: " + AsmOptimizer.layoutPhiCopyBranches(input));
        List<String> external = new ArrayList<>(input);
        external.add("\tjrl .Lbb_main_phi_edge_1");
        if (!AsmOptimizer.layoutPhiCopyBranches(external).equals(external))
            throw new AssertionError("extra entry");
        for (String hazard : List.of("\tcall _unknown", ".extra:", "\tld xsp, xwa")) {
            List<String> unsafe = new ArrayList<>(input);
            unsafe.set(3, hazard);
            if (!AsmOptimizer.layoutPhiCopyBranches(unsafe).equals(unsafe))
                throw new AssertionError("unsafe: " + hazard);
        }
        if (!AsmOptimizer.layoutPhiCopyBranches(expected).equals(expected))
            throw new AssertionError("not stable");
        System.out.println("AsmOptimizerPhiLayoutTest: PASS");
    }
}
