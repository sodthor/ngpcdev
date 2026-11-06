import java.util.List;

public final class AsmOptimizerComputedCopiesTest {
    public static void main(String[] args) {
        List<String> input = List.of("\tld wa, rwa1", "\tinc 1, wa", "\tld rwa1, wa", "\tcp wa, 16");
        List<String> expected = List.of("\tinc 1, rwa1", "\tld wa, rwa1", "\tcp wa, 16");
        if (!AsmOptimizer.foldComputedRegisterCopies(input).equals(expected))
            throw new AssertionError("counter reload must remain");
        for (List<String> hazard : List.of(
                List.of("ld wa, rwa1", "inc 1, wa", "ld rbc1, wa"),
                List.of("ld wa, rwa1", ".entry:", "inc 1, wa", "ld rwa1, wa"),
                List.of("ld wa, rwa1", "inc 1, a", "ld rwa1, wa")))
            if (!AsmOptimizer.foldComputedRegisterCopies(hazard).equals(hazard))
                throw new AssertionError("unsafe counter rewrite");
        List<String> compute = List.of("ld xwa, xix", "sla 2, xwa", "ld xhl, xwa", "ld xwa, 0");
        if (!AsmOptimizer.foldComputedRegisterCopies(compute).equals(
                List.of("\tld xhl, xix", "sla 2, xhl", "ld xwa, 0")))
            throw new AssertionError("computed copy");
        List<String> live = List.of("ld xwa, xix", "sla 2, xwa", "ld xhl, xwa", "add xbc, xwa");
        if (!AsmOptimizer.foldComputedRegisterCopies(live).equals(live))
            throw new AssertionError("live temporary");
        if (!AsmOptimizer.foldComputedRegisterCopies(expected).equals(expected))
            throw new AssertionError("not stable");
        System.out.println("AsmOptimizerComputedCopiesTest: PASS");
    }
}
