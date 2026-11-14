import java.util.*;

/** Pointer and scalar-alloca homes must not share load semantics. */
public final class Ir2Tlcs900PromotedPointerTest {
    public static void main(String[] args) {
        for (String home : List.of("bc", "de", "ix", "iy", "iz"))
            for (String type : List.of("i8", "i16", "i32", "ptr")) {
                var ft = new Ir2Tlcs900.FuncTranslator(
                    new Ir2Tlcs900.IrFunction("probe", "void", "", new ArrayList<>()), Set.of(), Map.of());
                switch (home) {
                    case "bc" -> { ft.bcSlotName = "p"; ft.bcSlotBits = 32; }
                    case "de" -> { ft.deSlotName = "p"; ft.deSlotBits = 32; }
                    case "ix" -> { ft.ixSlotName = "p"; ft.ixSlotBits = 32; }
                    case "iy" -> { ft.iySlotName = "p"; ft.iySlotBits = 32; }
                    default -> { ft.izSlotName = "p"; ft.izSlotBits = 32; }
                }
                ft.slots.put("result", new int[]{0, 4});
                ft.emitLoad("result", type, "%p", Map.of());
                String reg = type.equals("i8") ? "a" : type.equals("i16") ? "wa" : "xwa";
                if (!ft.out.contains("\tld xhl, x" + home)
                        || !ft.out.contains("\tld " + reg + ", (xhl+0)"))
                    throw new AssertionError("missing dereference " + home + " " + type + " " + ft.out);
                ft.out.clear();
                ft.allocaSlots.add("p");
                ft.waSlotName = null;
                ft.xhlSlotName = null;
                ft.emitLoad("result", type, "%p", Map.of());
                if (ft.out.stream().anyMatch(line -> line.contains("(xhl")))
                    throw new AssertionError("promoted scalar alloca was dereferenced " + home + " " + type);
            }
        System.out.println("Ir2Tlcs900PromotedPointerTest: PASS (20 pointer-home/width cases and scalar-alloca controls)");
    }
}