import java.util.List;

public final class AsmOptimizerZeroExtendTest {
    public static void main(String[] args) {
        int cases = 0;
        for (String address : List.of("(xsp+30)", "(xhl)", "(xbc-4)", "(xix+bc)",
                "(09000h)", "(_screen)", "(xiy+0x10)")) {
            List<String> input = List.of("\tld a, " + address, "\textz wa", "\textz xwa");
            List<String> expected = List.of("\tld xwa, 0", input.get(0));
            if (!AsmOptimizer.fuseZeroExtendedByteLoads(input).equals(expected))
                throw new AssertionError("not folded: " + address);
            if (!AsmOptimizer.fuseZeroExtendedByteLoads(expected).equals(expected))
                throw new AssertionError("not stable: " + address);
            cases++;
        }
        for (String address : List.of("(xwa)", "(xwa+30)", "(xhl+wa)", "(xix+)", "(-xix)", "(xwa1)")) {
            List<String> input = List.of("\tld a, " + address, "\textz wa", "\textz xwa");
            if (!AsmOptimizer.fuseZeroExtendedByteLoads(input).equals(input))
                throw new AssertionError("unsafe fold: " + address);
            cases++;
        }
        List<String> barrier = List.of("\tld a, (xsp+30)", ".Lentry:", "\textz wa", "\textz xwa");
        if (!AsmOptimizer.fuseZeroExtendedByteLoads(barrier).equals(barrier))
            throw new AssertionError("crossed label");
        List<String> commented = List.of("\tLD A, (XSP+30) ; byte", "\t; keep", "",
                "\tEXTZ WA ; word", "\tEXTZ XWA ; wide");
        List<String> output = AsmOptimizer.fuseZeroExtendedByteLoads(commented);
        if (!output.equals(List.of("\tld xwa, 0", commented.get(0), "\t; keep", "",
                "\t; word", "\t; wide"))) throw new AssertionError("lost comments: " + output);
        if (!AsmOptimizer.fuseZeroExtendedByteLoads(output).equals(output))
            throw new AssertionError("commented stability");
        System.out.println("AsmOptimizerZeroExtendTest: PASS (" + (cases + 3) + " cases)");
    }
}
