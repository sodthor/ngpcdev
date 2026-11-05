import java.util.*;

/** Guard/shape coverage for the outer-frame and narrow-successor follow-ups. */
public final class AsmOptimizerFollowupTest {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    static List<String> frame() {
        return new ArrayList<>(List.of("; experimental-loop-register-allocation",
            AsmOptimizer.PROTECTED_BEGIN + "probe", "_probe:", "\tpush xhl2", "\tpush xbc",
            "\tadd xsp, -40\t; frame: 40 bytes", "\tld xwa, 011223344h", "\tcp xwa, 0",
            "\tld (xsp+0), xwa", "\tld xwa, 055667788h", "\tld (xsp+28), xwa",
            "\tld a, 0aah", "\tld (xsp+35), a", "\tpush xbc", "\tadd xsp, -8",
            "\tld xwa, (xsp+40)", "\tld (xsp+0), xwa", "\tld (04000h), xwa",
            "\tld xwa, (xsp+64)", "\tld (04004h), xwa", "\tadd xsp, 8", "\tpop xbc",
            "\tld a, (xsp+35)", "\tld (04008h), a", "\tld xhl, 17", "\tadd xsp, 40",
            "\tpop xbc", "\tpop xhl2", "\tret", AsmOptimizer.PROTECTED_END + "probe", "\tend"));
    }
    static List<String> counter() {
        return new ArrayList<>(List.of("; experimental-loop-register-allocation",
            AsmOptimizer.PROTECTED_BEGIN + "probe", "_probe:", "\tpush xiy", "\tadd xsp, -32",
            "\tld xiy, 0", ".Lheader:", "\tld xwa, xiy", "\tld (04000h), xwa",
            "\tld xwa, xiy", "\tadd xwa, 1", "\tld (xsp+20), xwa", "\tcp xwa, 4",
            "\tjrl Z, .Lexit", "\tld xiy, (xsp+20)", "\tjrl .Lheader", ".Lexit:",
            "\tld (04000h), xwa", "\tld xhl, 17", "\tadd xsp, 32", "\tpop xiy", "\tret",
            AsmOptimizer.PROTECTED_END + "probe", "\tend"));
    }
    static List<String> bool(int value, String condition, String branch) {
        return new ArrayList<>(List.of("_probe:", "\tld xwa, 0dead1234h", "\tld xhl, " + value,
            "\tcp xhl, 3", "\tscc " + condition + ", a", "\tand wa, 0ffh", "\tor a, a",
            "\tjrl " + branch + ", .Ltrue", "\tld wa, 01234h", "\tcp wa, 0", "\tld xhl, xwa",
            "\tret", ".Ltrue:", "\tld wa, 05678h", "\tcp wa, 0", "\tld xhl, xwa", "\tret"));
    }
    private static List<String> replace(List<String> input, String old, String replacement) {
        List<String> result = new ArrayList<>(input);
        result.set(result.indexOf(old), replacement);
        return result;
    }
    public static void main(String[] args) {
        List<String> compact = AsmOptimizer.compactAllocatedFrames(frame());
        require(compact.contains("\tadd xsp, -12\t; frame: 12 bytes"), "outer holes reclaimed with alignment");
        require(!compact.contains("\tpush xhl2"), "unused outer bank save removed");
        require(compact.contains("\tld xwa, (xsp+16)"), "nested reference to outer slot remapped");
        require(compact.contains("\tld xwa, (xsp+32)"), "nested caller argument remapped");
        require(compact.contains("\tld a, (xsp+11)"), "byte lane preserved");
        require(AsmOptimizer.compactAllocatedFrames(compact).equals(compact), "frame idempotence");
        for (String barrier : List.of("\tlda xwa, xsp+0", "\tlda xwa, (xsp+0)", "\tld xwa, sr", "\tcall _other",
                "\tld xwa, (xsp+38)", "\tld xwa, (xsp+40)", "\tld xwa, (xsp+xbc)",
                "\tjrl C, .Lmissing", "\tadd xsp, 4")) {
            List<String> input = replace(frame(), "\tcp xwa, 0", barrier);
            require(AsmOptimizer.compactAllocatedFrames(input).equals(input), "frame barrier " + barrier);
        }
        List<String> flagLive = replace(frame(), "\tcp xwa, 0", "\tadc xwa, 0");
        require(AsmOptimizer.compactAllocatedFrames(flagLive).equals(flagLive), "live allocation flags");
        List<String> extz = frame();
        extz.add(extz.indexOf("\tcp xwa, 0"), "\textz xwa");
        require(AsmOptimizer.compactAllocatedFrames(extz).contains("\tadd xsp, -12\t; frame: 12 bytes"), "neutral extension before flag overwrite");
        List<String> nestedFlags = frame();
        nestedFlags.add(nestedFlags.indexOf("\tadd xsp, -8") + 1, "\tadc xwa, 0");
        require(AsmOptimizer.compactAllocatedFrames(nestedFlags).equals(nestedFlags), "live nested stack-adjust flags");
        require(AsmOptimizer.removeUnusedAllocatedBankSaves(nestedFlags).equals(nestedFlags), "save removal also relocates nested stack flags");
        List<String> bitCarry = frame();
        bitCarry.addAll(bitCarry.indexOf("\tadd xsp, -8") + 1, List.of("\tbit 2, bc", "\tadc xwa, 0"));
        require(AsmOptimizer.compactAllocatedFrames(bitCarry).equals(bitCarry), "BIT does not kill stack carry");
        List<String> extraEntry = counter();
        extraEntry.add(extraEntry.size() - 1, "\tjrl .Lheader");
        require(AsmOptimizer.forwardAllocatedCounters(extraEntry).equals(extraEntry), "external loop entry");
        List<String> forwarded = AsmOptimizer.forwardAllocatedCounters(counter());
        require(forwarded.contains("\tadd xiy, 1") && forwarded.contains("\tcp xiy, 4"), "direct counter operations");
        require(!forwarded.contains("\tld (xsp+20), xwa") && !forwarded.contains("\tld xiy, (xsp+20)"), "counter spill removed");
        require(AsmOptimizer.forwardAllocatedCounters(forwarded).equals(forwarded), "counter idempotence");
        List<String> indirect = counter();
        int reload = indirect.indexOf("\tld xiy, (xsp+20)");
        indirect.set(reload, "\tld xwa, (xsp+20)");
        indirect.add(reload + 1, "\tld xiy, xwa");
        require(AsmOptimizer.forwardAllocatedCounters(indirect).equals(forwarded), "pre-optimizer counter reload pair");
        for (String barrier : List.of("\tld xhl, xiy", "\tld hl, qiy", "\tlda xhl, (xiy+0)", "\tcall _other")) {
            List<String> input = replace(counter(), "\tld xhl, 17", barrier);
            require(AsmOptimizer.forwardAllocatedCounters(input).equals(input), "live exit counter " + barrier);
        }
        List<String> overlap = replace(counter(), "\tld (04000h), xwa", "\tld wa, (xsp+22)");
        require(AsmOptimizer.forwardAllocatedCounters(overlap).equals(overlap), "overlapping counter read");
        List<String> escape = replace(counter(), "\tld (04000h), xwa", "\tlda xwa, xsp+20");
        require(AsmOptimizer.forwardAllocatedCounters(escape).equals(escape), "escaping counter slot");
        List<String> nested = counter();
        nested.addAll(nested.indexOf("\tld xwa, xiy"), List.of("\tpush xbc", "\tld wa, (xsp+24)", "\tpop xbc"));
        require(AsmOptimizer.forwardAllocatedCounters(nested).equals(nested), "nested read of counter slot");
        for (String condition : List.of("Z", "NZ", "C", "NC")) for (String branch : List.of("Z", "NZ")) {
            List<String> fused = Ir2Tlcs900.simplifyBoolBranchPatterns(bool(2, condition, branch));
            require(fused.size() == bool(2, condition, branch).size() - 3, "narrow successor " + condition + "/" + branch);
            require(Ir2Tlcs900.simplifyBoolBranchPatterns(fused).equals(fused), "boolean idempotence");
        }
        for (String barrier : List.of("\tld hl, wa", "\tld a, (xwa)", "\tld wa, wa", "\tcall _other",
                "\tld wa, sr", "\tadc wa, 1", "\tjrl .Lmissing")) {
            List<String> input = replace(bool(2, "C", "NZ"), "\tld wa, 01234h", barrier);
            require(Ir2Tlcs900.simplifyBoolBranchPatterns(input).equals(input), "narrow successor barrier " + barrier);
        }
        List<String> partial = replace(bool(2, "C", "NZ"), "\tld wa, 01234h", "\tld a, 1");
        require(Ir2Tlcs900.simplifyBoolBranchPatterns(partial).equals(partial), "live W after byte overwrite");
        List<String> inc = bool(2, "C", "NZ");
        inc.add(inc.indexOf("\tcp wa, 0"), "\tinc 1, xbc");
        require(Ir2Tlcs900.simplifyBoolBranchPatterns(inc).size() == inc.size() - 3, "non-carry increment before full flag overwrite");
        List<String> liveAfterInc = replace(inc, "\tcp wa, 0", "\tadc xbc, 0");
        require(Ir2Tlcs900.simplifyBoolBranchPatterns(liveAfterInc).equals(liveAfterInc), "increment does not kill live carry");
        for (String shift : List.of("\tsra 1, xbc", "\tsrl 1, xbc", "\tsla 1, xbc")) {
            List<String> flags = bool(2, "C", "NZ");
            int cp = flags.indexOf("\tcp wa, 0");
            flags.set(cp, shift);
            flags.add(cp + 1, "\tld wa, sr");
            require(Ir2Tlcs900.simplifyBoolBranchPatterns(flags).equals(flags), "shift is not a full status overwrite");
        }
        System.out.println("AsmOptimizerFollowupTest: PASS (" + checks + " checks)");
    }
}
