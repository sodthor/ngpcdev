import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerAddressTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static List<String> address(String x, String y) {
        return List.of("_probe:", "\tsrl 8, xwa", "\tld " + x + ", xwa",
            "\tld xwa, xiz", "\tsla 1, xwa", "\tand xwa, 03fe00h",
            "\tld " + y + ", xwa", "\tld xhl, xbc",
            "\tld xwa, " + x, "\tadd xhl, xwa", "\tld xwa, " + y,
            "\tadd xhl, xwa", "\tld a, (xhl+0)", "\tld xde1, 0",
            "\tld xde2, 0", "\tret");
    }

    public static void main(String[] args) {
        List<String> input = address("xde1", "xde2");
        List<String> result = AsmOptimizer.scheduleAddressOffsets(input);
        require(!result.contains("\tld xde1, xwa") && !result.contains("\tld xde2, xwa"),
            "dead bank homes removed");
        require(result.indexOf("\tadd xhl, xwa") < result.indexOf("\tld xwa, xiz"),
            "consume X before computing Y");
        require(result.contains("_probe:"), "labels retain their formatting");
        require(AsmOptimizer.scheduleAddressOffsets(result).equals(result), "idempotent");
        for (String read : List.of("ld a, re1", "ld wa, rde1", "ld xwa, xde1")) {
            List<String> live = new ArrayList<>(input);
            live.add(13, "\t" + read);
            require(AsmOptimizer.scheduleAddressOffsets(live).contains("\tld xde1, xwa"),
                "live bank alias retained: " + read);
        }
        for (String barrier : List.of("call _other", "push sr", "unknown")) {
            List<String> guarded = new ArrayList<>(input);
            guarded.add(13, "\t" + barrier);
            require(AsmOptimizer.scheduleAddressOffsets(guarded).contains("\tld xde1, xwa"),
                "unknown effects retain bank home");
        }
        List<String> stack = address("(xsp+192)", "(xsp+208)");
        require(AsmOptimizer.scheduleAddressOffsets(stack).contains("\tld (xsp+192), xwa"),
            "stack homes retained for separate alias-aware cleanup");
        List<String> intermediate = new ArrayList<>(stack);
        intermediate.add(10, "\tld (xsp+212), xhl");
        require(AsmOptimizer.scheduleAddressOffsets(intermediate).contains("\tld (xsp+212), xhl"),
            "intermediate address remains available to later consumers");
        List<String> entry = new ArrayList<>(input);
        entry.add(4, ".Lentry:");
        require(AsmOptimizer.scheduleAddressOffsets(entry).equals(entry), "do not move across labels");
        List<String> overlap = address("(xsp+192)", "(xsp+194)");
        require(AsmOptimizer.scheduleAddressOffsets(overlap).equals(overlap), "overlapping homes rejected");
        for (String source : List.of("xhl", "xwa", "xde1")) {
            List<String> dependent = new ArrayList<>(input);
            dependent.set(3, "\tld xwa, " + source);
            require(AsmOptimizer.scheduleAddressOffsets(dependent).equals(dependent),
                "dependent Y source rejected");
        }
        for (String pointer : List.of("xhl", "xix+12", "xbc")) {
            List<String> load = List.of("\tld a, (" + pointer + ")", "\textz wa", "\textz xwa");
            require(AsmOptimizer.fuseZeroExtendedByteLoads(load).size() == 2, "byte-load fusion");
        }
        for (String pointer : List.of("xwa", "xhl+wa", "xhl+a")) {
            List<String> load = List.of("\tld a, (" + pointer + ")", "\textz wa", "\textz xwa");
            require(AsmOptimizer.fuseZeroExtendedByteLoads(load).equals(load), "dependent address retained");
        }
        List<String> color = List.of("\tsla 16, xwa", "\tadd xwa, 0fffd0000h", "\tor xwa, xbc2");
        require(AsmOptimizer.foldShiftedWordSubtract(color).get(0).equals("\tdec 3, wa"), "narrow subtract");
        for (String consumer : List.of("jr C, .Lexit", "adc xwa, 0", "bit 2, a",
                "push sr", "call _other", ".Lentry:", "ret")) {
            List<String> guarded = List.of(color.get(0), color.get(1), "\t" + consumer);
            require(AsmOptimizer.foldShiftedWordSubtract(guarded).equals(guarded), "live flags retained");
        }
        for (int value = 0; value < 256; value++)
            require((value << 16) - 196608 == (((value - 3) & 65535) << 16),
                "shift/subtract equivalence");
        System.out.println("AsmOptimizerAddressTest: PASS");
    }
}
