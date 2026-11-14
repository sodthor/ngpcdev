import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Packed globals may contain strings whose bytes look like aggregate delimiters. */
public final class Ir2Tlcs900PackedInitializerTest {
    public static void main(String[] args) {
        String init = "<{ [3 x i8], [2 x i8] }> <{ [3 x i8] c\"}]{\", [2 x i8] zeroinitializer }>";
        int end = Ir2Tlcs900.findPackedStructEnd(init, 0);
        if (end != init.indexOf(">") || Ir2Tlcs900.findPackedStructEnd(init, end + 2) != init.length() - 1)
            throw new AssertionError("quoted delimiter confused packed aggregate boundary");
        List<String> out = new ArrayList<>();
        Ir2Tlcs900.emitInitializer(init, out, Map.of());
        if (!out.equals(List.of("\tdb 125, 93, 123", "\tdb 0, 0")))
            throw new AssertionError("packed bytes: " + out);
        String escaped = "<{ [2 x i8] }> <{ [2 x i8] c\"\\22}\" }>";
        if (Ir2Tlcs900.findPackedStructEnd(escaped, escaped.indexOf(" <{") + 1) != escaped.length() - 1)
            throw new AssertionError("escaped quote confused packed aggregate boundary");
        System.out.println("Ir2Tlcs900PackedInitializerTest: PASS");
    }
}