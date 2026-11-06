import java.util.*;

/** Admission/rejection and shape checks; assembled matrices live in the compiler suite. */
public final class LoopRegisterAllocatorOptimizationTest {
    private static int checks;
    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static LoopRegisterAllocator.Plan plan(String body) {
        List<Ir2Tlcs900.FuncTranslator.BasicBlock> blocks = new ArrayList<>();
        String label = null;
        List<String> instructions = new ArrayList<>();
        for (String line : body.strip().split("\n")) {
            String code = line.strip();
            if (code.endsWith(":")) {
                if (label != null) blocks.add(new Ir2Tlcs900.FuncTranslator.BasicBlock(label, instructions));
                label = code.substring(0, code.length() - 1);
                instructions = new ArrayList<>();
            } else if (!code.isEmpty()) instructions.add(code);
        }
        blocks.add(new Ir2Tlcs900.FuncTranslator.BasicBlock(label, instructions));
        return LoopRegisterAllocator.plan(blocks);
    }
    private static String emit(LoopRegisterAllocator.Plan plan) {
        List<String> lines = new ArrayList<>();
        plan.emit(lines, ".Ltest_", (v, t, delta) -> lines.add("\tld xwa, 0"));
        return String.join("\n", lines);
    }
    private static final String BOUNDS = """
        entry:
          %x = add i32 11, 0
          %y = add i32 22, 0
          br label %header
        header:
          %i = phi i16 [ 0, %entry ], [ %next, %latch ]
          %both = or i32 %x, %y
          %ok = icmp ult i32 %both, 131072
          br i1 %ok, label %inside, label %outside
        inside:
          %shift = shl i32 %y, 1
          %masked = and i32 %shift, 261632
          store i32 %masked, ptr inttoptr (i32 16384 to ptr)
          br label %latch
        outside:
          store i32 %x, ptr inttoptr (i32 16384 to ptr)
          br label %latch
        latch:
          %next = add i16 %i, 1
          %more = icmp ult i16 %next, 4
          br i1 %more, label %header, label %exit
        exit:
          ret i32 17
        """;
    private static String boundsAssembly(String body) {
        var p = plan(body);
        p.homes.put("%x", "xbc"); p.homes.put("%y", "xhl"); p.homes.put("%both", "xde");
        return emit(p);
    }
    private static void bounds() {
        String positive = boundsAssembly(BOUNDS);
        require(positive.contains("ld de, qbc\n\tor de, qhl\n\tcp de, 2"), "upper-word bounds");
        require(positive.contains("sla 9, "), "dominating bound used for address");
        for (String predicate : List.of("eq", "ne", "ugt", "ule"))
            require(!boundsAssembly(BOUNDS.replace("icmp ult i32 %both", "icmp " + predicate + " i32 %both"))
                .contains("qbc"), "unsupported bounds predicate " + predicate);
        require(!boundsAssembly(BOUNDS.replace("131072", "131073")).contains("qbc"), "unaligned bound");
        require(!boundsAssembly(BOUNDS.replace("%ok = icmp", "store i32 %both, ptr inttoptr (i32 16400 to ptr)\n%ok = icmp"))
            .contains("qbc"), "live OR result");
        require(!boundsAssembly(BOUNDS.replace("131072", "262144")).contains("sla 9, "), "insufficient bound");
        require(!boundsAssembly(BOUNDS.replace("261632", "260608")).contains("sla 9, "), "noncontiguous mask");
        require(!boundsAssembly(BOUNDS.replace("br label %latch\nlatch:", "br label %inside\nlatch:"))
            .contains("sla 9, "), "extra entry to in-bounds block");
        var p = plan(BOUNDS);
        p.homes.put("%x", "xbc1"); p.homes.put("%y", "xhl");
        require(!emit(p).contains("qbc1"), "banked upper source fallback");
        String uge = BOUNDS.replace("icmp ult i32 %both", "icmp uge i32 %both")
            .replace("br i1 %ok, label %inside, label %outside", "br i1 %ok, label %outside, label %inside");
        require(boundsAssembly(uge).contains("sla 9, "), "inverted branch bound");
    }
    private static void pointers() {
        String body = """
            entry:
              br label %header
            header:
              %p = phi ptr [ inttoptr (i32 16384 to ptr), %entry ], [ %pn, %latch ]
              %i = phi i16 [ 0, %entry ], [ %next, %latch ]
              %pn = getelementptr i8, ptr %p, i32 4
              %q = getelementptr i8, ptr %p, i32 8
              store i16 27, ptr %pn
              %value = load i16, ptr %q
              store i16 %value, ptr inttoptr (i32 16400 to ptr)
              br label %latch
            latch:
              %next = add i16 %i, 1
              %more = icmp ult i16 %next, 3
              br i1 %more, label %header, label %exit
            exit:
              ret i32 17
            """;
        var p = plan(body);
        require(!p.types.containsKey("%pn") && !p.types.containsKey("%q"), "address-only GEPs removed before allocation");
        require(p.phiOffsets.get(new LoopRegisterAllocator.Plan.PhiEdge("%p", "latch")) == 4, "pointer edge adjustment");
        String output = emit(p);
        require(output.contains("+4)") && output.contains("+8)"), "displacement memory accesses");
        require(plan(body.replace("i32 4\n", "i32 32768\n")).types.containsKey("%pn"), "large displacement rejected");
        require(plan(body.replace("i32 4\n", "i32 -4\n")).phiOffsets
            .get(new LoopRegisterAllocator.Plan.PhiEdge("%p", "latch")) == -4, "negative displacement");
        String extra = body.replace("store i16 27, ptr %pn", "%other = getelementptr i8, ptr %pn, i32 1\nstore i16 27, ptr %other");
        require(plan(extra).types.containsKey("%pn"), "base needed by another GEP remains materialized");
        var word = plan(body.replace("%pn = getelementptr i8", "%pn = getelementptr i16"));
        require(word.phiOffsets.get(new LoopRegisterAllocator.Plan.PhiEdge("%p", "latch")) == 8, "scaled displacement");
    }
    private static void words() {
        var p = plan(BOUNDS);
        p.homes.put("%i", "xwa1"); p.homes.put("%next", "xwa1");
        String assembly = emit(p);
        require(assembly.contains("\tinc 1, rwa1\n\tcp rwa1, 4"), "banked word counter steps and compares in place");
        require(!assembly.contains("add xwa, 1\n\tand xwa, 0ffffh"), "no truncation mask for coalesced word arithmetic");
    }
    private static void context() {
        List<String> input = List.of("\tpush xwa", "\tpush xhl2", "\tadd xsp, -24\t; experimental loop frame",
            "\tld xwa, (xsp+44)", "\tld (xsp+0), xwa", ".Lloop:", "\tld xwa, (xsp+0)",
            "\tadd xsp, 24", "\tpop xhl2", "\tpop xwa");
        List<String> output = new ArrayList<>(input);
        LoopRegisterAllocator.compactLoopContext(output, 24);
        require(!output.contains("\tpush xhl2"), "unused regional save");
        require(output.contains("\tadd xsp, -4\t; experimental loop frame"), "unused regional tail");
        require(output.contains("\tld xwa, (xsp+20)"), "input shifted by frame and saves");
        List<String> repeated = new ArrayList<>(output);
        LoopRegisterAllocator.compactLoopContext(repeated, 4);
        require(repeated.equals(output), "context idempotence");
        for (String barrier : List.of("\tcall _other", "\tlda xwa, xsp+0", "\tld xwa, (xsp+26)",
                "\tld xwa, (xsp+22)", "\tld xwa, sr", "\tadd xsp, 2")) {
            List<String> guarded = new ArrayList<>(input);
            guarded.add(6, barrier);
            List<String> before = new ArrayList<>(guarded);
            LoopRegisterAllocator.compactLoopContext(guarded, 24);
            require(guarded.equals(before), "context barrier " + barrier);
        }
        for (String alias : List.of("rhl2", "qhl2", "rh2", "ql2")) {
            List<String> guarded = new ArrayList<>(input);
            guarded.add(6, "\tld wa, " + alias);
            LoopRegisterAllocator.compactLoopContext(guarded, 24);
            require(guarded.contains("\tpush xhl2"), "regional bank alias " + alias);
        }
    }
    private static List<String> outer(String operation) {
        return new ArrayList<>(List.of("; experimental-loop-register-allocation",
            "; experimental-register-function-begin probe", "_probe:", "\tpush xhl2", "\tpush xbc",
            "\tadd xsp, -8\t; frame: 8 bytes", "\tld xbc, (xsp+20)", operation,
            "\tadd xsp, 8", "\tpop xbc", "\tpop xhl2", "\tret",
            "; experimental-register-function-end probe", "\tend"));
    }
    private static void outerSaves() {
        List<String> result = AsmOptimizer.removeUnusedAllocatedBankSaves(outer("\tld (xsp+0), xbc"));
        require(!result.contains("\tpush xhl2"), "unused outer save");
        require(result.contains("\tld xbc, (xsp+16)"), "caller argument shifted");
        require(AsmOptimizer.removeUnusedAllocatedBankSaves(result).equals(result), "outer idempotence");
        for (String operation : List.of("\tld wa, qhl2", "\tld a, rh2", "\tcall _other",
                "\tlda xwa, xsp+0", "\tld xwa, (xsp+12)", "\tld xwa, (xsp+6)", "\tld wa, sr", "\tadd xsp, 2")) {
            List<String> input = outer(operation);
            require(AsmOptimizer.removeUnusedAllocatedBankSaves(input).equals(input), "outer barrier " + operation);
        }
        List<String> helper = outer("\tpush xde");
        helper.add(helper.indexOf("\tpush xde"), "\tcp xbc, 0");
        int callAt = helper.indexOf("\tpush xde") + 1;
        helper.addAll(callAt, List.of("\tpush xwa", "\tpush xhl", "\tcall _C9H_divls",
            "\tinc 8, xsp", "\tld xwa, xhl", "\tpop xde"));
        helper.add(helper.size() - 1, "_C9H_divls:");
        helper.add(helper.size() - 1, "\tret");
        require(!AsmOptimizer.removeUnusedAllocatedBankSaves(helper).contains("\tpush xhl2"), "verified bank-neutral helper");
        helper.add(helper.size() - 2, "\tld xhl2, 1");
        require(AsmOptimizer.removeUnusedAllocatedBankSaves(helper).equals(helper), "bank-clobbering helper");
    }
    private static void scratch() {
        List<String> input = new ArrayList<>(List.of("\tld xix, xwa", "\tor xde, xix", "\tld ix, qbc",
            "\tcp ix, 2", "\tld xix, 0", "\tld xwa, 0", "\tpop xix", "\tpop xwa"));
        LoopRegisterAllocator.forwardScratchValues(input);
        require(!input.contains("\tld xix, xwa") && input.contains("\tor xde, xwa"), "low-word overwrite kills only low-word dependency");
        List<String> counter = new ArrayList<>(List.of("\tld xix, xwa", "\tor xde, xix", "\tld ix, qbc",
            "\tcp ix, 2", "\tld wa, rwa1", "\tinc 1, wa", "\tld rwa1, wa", "\tcp wa, 4",
            "\tld xix, 0", "\tpop xix", "\tpop xwa"));
        LoopRegisterAllocator.forwardScratchValues(counter);
        require(!counter.contains("\tld xix, xwa"), "word counter does not create a false index dependency");
        List<String> liveHigh = new ArrayList<>(List.of("\tld xix, xwa", "\tor xde, xix", "\tld ix, qbc",
            "\tld xwa, 99", "\tld de, qix", "\tld xix, 0", "\tld xwa, 0", "\tpop xix", "\tpop xwa"));
        List<String> before = new ArrayList<>(liveHigh);
        LoopRegisterAllocator.forwardScratchValues(liveHigh);
        require(liveHigh.equals(before), "live upper-word dependency retained");
    }
    private static final String TEXTURE = """
        entry:
          %x = add i32 11, 0
          %y = add i32 22, 0
          %base = add i32 16384, 0
          br label %header
        header:
          %i = phi i16 [ 0, %entry ], [ %next, %latch ]
          %acc = phi i32 [ 0, %entry ], [ %merged, %latch ]
          %both = or i32 %x, %y
          %ok = icmp ult i32 %both, 131072
          br i1 %ok, label %inside, label %latch
        inside:
          %column = lshr i32 %x, 8
          %shift = shl i32 %y, 1
          %row = and i32 %shift, 261632
          %p = getelementptr i8, ptr %base, i32 %column
          %q = getelementptr i8, ptr %p, i32 %row
          %byte = load i8, ptr %q
          %wide = zext i8 %byte to i32
          store i32 %wide, ptr inttoptr (i32 16400 to ptr)
          br label %latch
        latch:
          %merged = phi i32 [ 3, %header ], [ %wide, %inside ]
          %next = add i16 %i, 1
          %more = icmp ult i16 %next, 4
          br i1 %more, label %header, label %exit
        exit:
          ret i32 17
        """;
    private static String textureAssembly(String body) {
        var p = plan(body);
        p.homes.put("%x", "xbc"); p.homes.put("%y", "xhl"); p.homes.put("%both", "xde");
        p.homes.put("%column", "xix"); p.homes.put("%row", "xiz"); p.homes.put("%shift", "xiz");
        p.homes.put("%base", "xde2");
        return emit(p);
    }
    private static void indexed() {
        require(textureAssembly(TEXTURE).contains("\tadd xiz, xde2\n\tld xwa, 0\n\tld a, (xiz+ix)"), "indexed byte load");
        require(!textureAssembly(TEXTURE.replace("131072", "16777216")).contains("+ix)"), "index above signed word");
        require(!textureAssembly(TEXTURE.replace("store i32 %wide", "store i32 %row, ptr inttoptr (i32 16404 to ptr)\nstore i32 %wide"))
            .contains("+ix)"), "live row index is not clobbered");
    }
    private static List<String> narrowSite(String after) {
        return new ArrayList<>(List.of("\tld xwa, 0", "\tld a, (xiz+ix)", "\tbit 2, wa", "\tjrl Z, .Llow",
            ".Lhigh:", "\tsub wa, 3", "\tsla 16, xwa", ".Llow:", "\tor xde, xwa", after, "\tld xwa, 0", "\tpop xwa"));
    }
    private static void narrow() {
        List<String> site = narrowSite("\tadd xhl, xbc1");
        LoopRegisterAllocator.narrowByteAccumulatorOr(site);
        require(site.equals(List.of("\tld a, (xiz+ix)", "\tbit 2, a", "\tjrl Z, .Llow", ".Lhigh:", "\tld w, 0",
            "\tsub wa, 3", "\tsla 16, xwa", "\tor xde, xwa", ".Llow:", "\tor e, a", "\tadd xhl, xbc1",
            "\tld xwa, 0", "\tpop xwa")), "byte-lane accumulator OR");
        for (String barrier : List.of("\tld (xiy+0), xwa", "\tjrl Z, .Llow", "\tadc xhl, 1", "\tld b, qwa")) {
            List<String> guarded = narrowSite(barrier);
            List<String> before = new ArrayList<>(guarded);
            LoopRegisterAllocator.narrowByteAccumulatorOr(guarded);
            require(guarded.equals(before), "narrow barrier " + barrier);
        }
        List<String> entered = narrowSite("\tadd xhl, xbc1");
        entered.add(0, "\tjrl .Lhigh");
        List<String> before = new ArrayList<>(entered);
        LoopRegisterAllocator.narrowByteAccumulatorOr(entered);
        require(entered.equals(before), "extra entry to the high path");
    }
    private static List<String> latch(String compare, String exitTail) {
        return new ArrayList<>(List.of(".Lhead:", "\tinc 1, rwa1", compare, "\tjrl C, .Ledge", "\tjrl .Lexit",
            ".Ledge:", "\tadd xiy, 010h", "\tjrl .Lhead", ".Lexit:", exitTail, "\tret"));
    }
    private static void latchCopies() {
        List<String> site = latch("\tcp rwa1, 010h", "\tpop xiy");
        LoopRegisterAllocator.hoistLatchEdgeCopies(site);
        require(site.equals(List.of(".Lhead:", "\tinc 1, rwa1", "\tadd xiy, 010h", "\tcp rwa1, 010h",
            "\tjrl C, .Lhead", ".Lexit:", "\tpop xiy", "\tret")), "latch edge copy hoisted");
        for (List<String> guarded : List.of(latch("\tcp iy, 010h", "\tpop xiy"), latch("\tcp rwa1, 010h", "\tld (xhl), iy"))) {
            List<String> before = new ArrayList<>(guarded);
            LoopRegisterAllocator.hoistLatchEdgeCopies(guarded);
            require(guarded.equals(before), "latch barrier " + guarded.get(2) + " / " + guarded.get(9));
        }
    }
    private static void entryValues() {
        var p = plan(BOUNDS);
        p.homes.put("%i", "xwa1"); p.homes.put("%next", "xwa1");
        require(p.entryValues.equals(Map.of("%i", "0")) && p.entryPredecessor.equals("entry"), "entry value recorded");
        require(p.substituteEntryValues(), "constant entry value substituted");
        require(emit(p).contains("\tld xwa1, 0"), "constant entry value written directly");
        String twoEntries = BOUNDS.replace("br label %header\n", "br i1 true, label %header, label %side\nside:\nbr label %header\n")
            .replace("[ 0, %entry ]", "[ 0, %entry ], [ 1, %side ]");
        try {
            var q = plan(twoEntries);
            require(!q.substituteEntryValues(), "two outside predecessors");
        } catch (IllegalArgumentException rejected) {
            checks++;
        }
    }
    private static void emptyFrame() {
        List<String> input = new ArrayList<>(List.of("\tpush xwa", "\tadd xsp, -8\t; experimental loop frame",
            "\tld xwa, (xsp+12)", "\tadd xsp, 8", "\tpop xwa"));
        LoopRegisterAllocator.compactLoopContext(input, 8);
        require(input.equals(List.of("\tpush xwa", "\t; experimental loop frame: 0 bytes", "\tld xwa, (xsp+4)", "\tpop xwa")),
            "empty regional frame elided");
    }
    private static List<String> context(String afterExit, boolean isVoid) {
        return new ArrayList<>(List.of("; experimental-loop-register-allocation",
            "; experimental-register-function-begin probe", "_probe:", "\tpush xbc", "\tpush xde",
            "\tadd xsp, -4\t; frame: 4 bytes", "\tld xbc, 5", ".Louter:",
            "\tpush xwa", "\tpush xbc", "\tpush xde", "\t; experimental loop frame: 0 bytes",
            "\tld xwa, (xsp+12)", "\tld xde, xwa", "\tld xbc, xde",
            "\tpop xde", "\tpop xbc", "\tpop xwa", LoopRegisterAllocator.CONTEXT_END,
            afterExit, "\tdec 1, bc", "\tjrl NZ, .Louter",
            "\tadd xsp, 4", "\tpop xde", "\tpop xbc", "\tret",
            "; experimental-register-function-end probe", "\tend"));
    }
    private static void contextSaves() {
        List<String> result = AsmOptimizer.trimAllocatedLoopSaves(context("\tld xde, 0", true), Set.of("probe"));
        require(result.stream().filter("\tpush xbc"::equals).count() == 2, "live counter save retained");
        require(result.stream().filter("\tpush xde"::equals).count() == 1 && !result.contains("\tpush xwa"),
            "dead context saves dropped");
        require(result.contains("\tld xwa, (xsp+4)"), "enclosing frame access shifted");
        require(AsmOptimizer.trimAllocatedLoopSaves(context("\tld xde, 0", false), Set.of())
            .contains("\tpush xwa"), "non-void return value retained");
        for (String barrier : List.of("\tld (xsp+0), xde", "\tcall _other", "\tadd xbc, xde", "\tjp (xhl)")) {
            List<String> input = context(barrier, true);
            require(AsmOptimizer.trimAllocatedLoopSaves(input, Set.of("probe")).stream()
                .filter("\tpush xde"::equals).count() == 2, "context barrier " + barrier);
        }
        List<String> selfRead = context("\tld xde, 0", true);
        selfRead.set(selfRead.indexOf("\tld xwa, (xsp+12)"), "\tld xwa, (xsp+4)");
        require(AsmOptimizer.trimAllocatedLoopSaves(selfRead, Set.of("probe")).equals(selfRead), "saved slot read");
    }
    public static void main(String[] args) {
        bounds(); pointers(); words(); context(); outerSaves(); scratch();
        indexed(); narrow(); latchCopies(); entryValues(); emptyFrame(); contextSaves();
        System.out.println("LoopRegisterAllocatorOptimizationTest: PASS (" + checks + " checks)");
    }
}
