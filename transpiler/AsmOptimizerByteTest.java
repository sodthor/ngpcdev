import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

public final class AsmOptimizerByteTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        List<UnaryOperator<List<String>>> passes = List.of(
            AsmOptimizer::removeRedundantOrAfterAndForZeroBranch,
            Ir2Tlcs900::removeRedundantOrAfterAndForZeroBranch);
        for (var pass : passes) {
            for (String reg : List.of("wa", "xwa")) {
                for (int mask : new int[]{0, 1, 127, 128, 255}) {
                    List<String> input = List.of("_probe:", "\tand " + reg + ", " + mask,
                        "\tor a, a", "\tjr Z, .Lzero", "\tld wa, 17", "\tcp wa, 0",
                        "\tret", ".Lzero:", "\tld wa, 23", "\tcp wa, 0", "\tret");
                    List<String> result = pass.apply(input);
                    require(!result.contains("\tor a, a"), "dead flags: " + reg + ", " + mask);
                    require(pass.apply(result).equals(result), "idempotence");
                    for (int successor : new int[]{4, 8}) {
                        for (String use : List.of("jr MI, .Lzero", "adc wa, 0", "push sr",
                                "ld wa, sr", "bit 0, a", "call _other", "ret", "unknown")) {
                            List<String> guarded = new ArrayList<>(input);
                            guarded.add(successor, "\t" + use);
                            require(pass.apply(guarded).equals(guarded), "live/unknown flags: " + use);
                        }
                    }
                    List<String> wide = new ArrayList<>(input);
                    wide.set(1, "\tand " + reg + ", 256");
                    require(pass.apply(wide).equals(wide), "nonzero upper byte");
                    List<String> entry = new ArrayList<>(input);
                    entry.add(2, ".Lentry:");
                    require(pass.apply(entry).equals(entry), "alternate entry before OR");
                }
            }
        }
        System.out.println("AsmOptimizerByteTest: PASS");
    }
}
