import java.util.ArrayList;
import java.util.List;

public final class AsmOptimizerPhiAccumulatorTest {
    private static List<String> fixture() {
        return List.of(
            "_test:", "\tadd xsp, -64", "\tjr Z, .Lin", "\tld xwa, 3",
            "\tld (xsp+30), xwa", "\tjr .Ljoin", ".Lin:", "\tld xhl, xbc",
            "\tld a, (xhl+0)", "\tbit 2, a", "\tjr NZ, .Lflip",
            "\tld (xsp+30), xwa", "\tjr .Ljoin", ".Lflip:", "\tsla 16, xwa",
            "\tld (xsp+30), xwa", ".Ljoin:", "\tld xwa, xbc1", "\tadd xwa, xde",
            "\tld xwa1, xwa", "\tld xwa, xiz", "\tadd xwa, xix", "\tld xhl1, xwa",
            "\tld xwa, (xsp+30)", "\tsla 2, xwa", "\tjr Z, .Lother",
            "\tld xhl, xbc", "\tjr .Ldone", ".Lother:", "\tld xhl, xde", ".Ldone:",
            "\tadd xsp, 64", "\tret");
    }

    private static void reject(String name, int position, String instruction) {
        List<String> original = new ArrayList<>(fixture());
        original.set(position, instruction);
        if (!AsmOptimizer.forwardPhiAccumulator(original).equals(original)) throw new AssertionError(name);
    }

    private static List<String> shiftedFixture() {
        return List.of("_shifted:", "\tadd xsp, -216", "\tsla 2, xwa", "\tld (xsp+192), xwa",
            "\tld xwa, xde1", "\tor xwa, xde2", "\tcp xwa, 020000h", "\tjr C, .Linside",
            "\tld xwa, (xsp+192)", "\tor xwa, 3", "\tld xhl, xwa", "\tjr .Ljoin",
            ".Linside:", "\tld xwa, xde2", "\tsrl 8, xwa", "\tld xhl, xbc", "\tadd xhl, xwa",
            "\tld xwa, xde1", "\tsla 1, xwa", "\tand xwa, 03fe00h", "\tadd xhl, xwa",
            "\tld a, (xhl+0)", "\textz wa", "\textz xwa", "\tbit 2, a", "\tjr Z, .Lplain",
            "\tsla 16, xwa", "\tadd xwa, 0fffd0000h", "\tor xwa, (xsp+192)", "\tld xhl, xwa",
            "\tjr .Ljoin", ".Lplain:", "\tor xwa, (xsp+192)", "\tld xhl, xwa", ".Ljoin:",
            "\tld xwa, xde2", "\tadd xwa, xde", "\tld xde2, xwa", "\tld xwa, xde1", "\tadd xwa, xix",
            "\tld xde1, xwa", "\tld xwa, xhl", "\tsla 2, xwa", "\tld (xsp+192), xwa",
            "\tld xbc2, xwa", "\tadd xsp, 216", "\tret");
    }

    private static void rejectShifted(String name, int position, String instruction) {
        List<String> original = new ArrayList<>(shiftedFixture());
        original.set(position, instruction);
        if (!AsmOptimizer.forwardShiftedAccumulator(original).equals(original)) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        List<String> original = fixture();
        List<String> optimized = AsmOptimizer.forwardPhiAccumulator(original);
        if (optimized.equals(original) || !optimized.contains("\tld xwa, xhl"))
            throw new AssertionError("valid PHI must be forwarded through XHL");
        if (optimized.stream().filter("\tld xhl, xwa"::equals).count() != 3
                || optimized.stream().anyMatch(line -> line.contains("(xsp+30)")))
            throw new AssertionError("all three stores and the reload must use registers");
        reject("live scratch on one path", 29, "\tadd xhl, xde");
        reject("scratch clobber before reload", 19, "\tld xhl, xwa");
        reject("extra incoming edge", 5, "\tjr .Lother");
        reject("different reload slot", 23, "\tld xwa, (xsp+32)");
        reject("partial overlapping access", 24, "\tld wa, (xsp+32)");
        reject("escaped frame", 7, "\tlda xhl, (xsp+4)");
        reject("call before scratch overwrite", 26, "\tcall _other");
        reject("register-indirect branch", 25, "\tjp xde");
        List<String> multiple = new ArrayList<>(original);
        for (String line : original) multiple.add(line.replace("_test:", "_other:").replace(".L", ".Lsecond"));
        List<String> forwarded = AsmOptimizer.forwardPhiAccumulators(multiple);
        if (forwarded.stream().filter("\tld xhl, xwa"::equals).count() != 6)
            throw new AssertionError("all eligible joins must be forwarded");
        if (AsmOptimizer.forwardPhiAccumulators(forwarded) != forwarded)
            throw new AssertionError("forwarding must reach a fixed point");
        reject("live low scratch alias", 29, "\tadd hl, de");
        reject("call before reload", 18, "\tcall _other");
        reject("push in active frame", 7, "\tpush xbc");
        reject("temporary stack adjustment", 7, "\tadd xsp, 4");
        reject("second frame allocation", 7, "\tadd xsp, -64");
        reject("slot outside frame", 1, "\tadd xsp, -16");
        List<String> division = new ArrayList<>(original);
        division.addAll(7, List.of("\tpush xde", "\tpush xwa", "\tpush xhl", "\tcall _C9H_divls",
                "\tinc 8, xsp", "\tld xwa, xhl", "\tpop xde"));
        if (AsmOptimizer.forwardPhiAccumulator(division).equals(division))
            throw new AssertionError("balanced division fallback must be allowed");
        division.set(11, "\tinc 4, xsp");
        if (!AsmOptimizer.forwardPhiAccumulator(division).equals(division))
            throw new AssertionError("unbalanced division fallback must be rejected");
        division.set(11, "\tinc 8, xsp");
        division.add(10, "\tld wa, (xsp+32)");
        if (!AsmOptimizer.forwardPhiAccumulator(division).equals(division))
            throw new AssertionError("frame access inside division stack window must be rejected");
        List<String> stackCoordinates = new ArrayList<>(original);
        stackCoordinates.set(19, "\tld (xsp+40), xwa");
        stackCoordinates.set(22, "\tld (xsp+44), xwa");
        List<String> finalPixel = AsmOptimizer.forwardPhiAccumulator(stackCoordinates);
        if (finalPixel.equals(stackCoordinates) || finalPixel.stream().anyMatch(line -> line.contains("(xsp+30)"))
                || !finalPixel.contains("\tld (xsp+40), xwa") || !finalPixel.contains("\tld (xsp+44), xwa"))
            throw new AssertionError("final-pixel PHI must forward across unchanged stack coordinate writes");
        List<String> overlappingCoordinates = new ArrayList<>(stackCoordinates);
        overlappingCoordinates.set(19, "\tld (xsp+32), xwa");
        if (!AsmOptimizer.forwardPhiAccumulator(overlappingCoordinates).equals(overlappingCoordinates))
            throw new AssertionError("coordinate write overlapping the accumulator must be rejected");
        List<String> indirectCoordinates = new ArrayList<>(stackCoordinates);
        indirectCoordinates.set(19, "\tld (xhl+0), xwa");
        if (!AsmOptimizer.forwardPhiAccumulator(indirectCoordinates).equals(indirectCoordinates))
            throw new AssertionError("scratch-addressed coordinate write must be rejected");
        List<String> liveFinal = new ArrayList<>(stackCoordinates);
        liveFinal.set(29, "\tadd hl, de");
        if (!AsmOptimizer.forwardPhiAccumulator(liveFinal).equals(liveFinal))
            throw new AssertionError("live scratch on final-pixel successor must be rejected");
        System.out.println("AsmOptimizerPhiAccumulatorTest: PASS (24 cases)");
        List<String> shifted = shiftedFixture();
        List<String> promoted = AsmOptimizer.forwardShiftedAccumulator(shifted);
        if (promoted.equals(shifted) || promoted.stream().filter(line -> line.contains("(xsp+192)")).count() != 1
                || promoted.stream().filter("\tor xwa, xbc2"::equals).count() != 2)
            throw new AssertionError("shifted accumulator must use XBC2 on all three paths");
        rejectShifted("live bank register", 44, "\tld xwa, xbc2");
        rejectShifted("live bank sub-register", 44, "\tld wa, rbc2");
        rejectShifted("call before bank overwrite", 44, "\tcall _other");
        rejectShifted("extra region predecessor", 44, "\tjr .Linside");
        rejectShifted("indirect region predecessor", 44, "\tjp xde");
        rejectShifted("missing following overwrite", 43, "\tld (xsp+196), xwa");
        rejectShifted("different color spill", 28, "\tor xwa, (xsp+194)");
        rejectShifted("different accumulator shift", 2, "\tsla 3, xwa");
        rejectShifted("escaping frame pointer", 44, "\tlda xbc2, (xsp+192)");
        rejectShifted("shifted slot outside frame", 1, "\tadd xsp, -32");
        List<String> fallback = new ArrayList<>(shifted);
        fallback.addAll(44, List.of("\tdivs xwa, hl", "\tpush xde", "\tpush xwa", "\tpush xhl",
                "\tcall _C9H_divls", "\tinc 8, xsp", "\tld xwa, xhl", "\tpop xde"));
        fallback.addAll(List.of("_C9H_divls:", "\tcall _C9H_divlu", "\tret", "_C9H_divlu:", "\tld xhl, xwa", "\tret"));
        if (AsmOptimizer.forwardShiftedAccumulator(fallback).equals(fallback))
            throw new AssertionError("verified bank-neutral division fallback must be allowed");
        fallback.set(fallback.size() - 2, "\tld xwa, rbc2");
        if (!AsmOptimizer.forwardShiftedAccumulator(fallback).equals(fallback))
            throw new AssertionError("nested helper bank use must be rejected");
        fallback.set(fallback.size() - 2, "\tincf");
        if (!AsmOptimizer.forwardShiftedAccumulator(fallback).equals(fallback))
            throw new AssertionError("helper bank switching must be rejected");
        fallback.set(fallback.size() - 2, "\tld sr, wa");
        if (!AsmOptimizer.forwardShiftedAccumulator(fallback).equals(fallback))
            throw new AssertionError("helper status-register writes must be rejected");
        List<String> epilogue = new ArrayList<>(shifted);
        epilogue.set(44, "\tld xwa, xhl");
        epilogue.addAll(46, List.of("\tpop xwa2", "\tpop xbc2"));
        if (AsmOptimizer.forwardShiftedAccumulator(epilogue).equals(epilogue))
            throw new AssertionError("unrelated epilogue restore before XBC2 restore must be allowed");
        epilogue.set(46, "\tld wa, rbc2");
        if (!AsmOptimizer.forwardShiftedAccumulator(epilogue).equals(epilogue))
            throw new AssertionError("bank read before epilogue restore must be rejected");
        List<String> longJumps = shifted.stream().map(line -> line.replace("\tjr ", "\tjrl ")).toList();
        List<String> longResult = AsmOptimizer.forwardShiftedAccumulator(longJumps);
        if (longResult.equals(longJumps) || !longResult.get(7).equals(longJumps.get(7)))
            throw new AssertionError("long jumps must match without rewriting their instructions");
        List<String> shiftedMultiple = new ArrayList<>(shifted);
        for (String line : shifted) shiftedMultiple.add(line.replace("_shifted:", "_shifted_other:")
                .replace(".L", ".Lsecond"));
        List<String> shiftedAll = AsmOptimizer.forwardShiftedAccumulators(shiftedMultiple);
        if (shiftedAll.stream().filter(line -> line.contains("(xsp+192)")).count() != 2)
            throw new AssertionError("all independent shifted regions must be forwarded");
        if (AsmOptimizer.forwardShiftedAccumulators(shiftedAll) != shiftedAll)
            throw new AssertionError("shifted forwarding must reach a fixed point");
        System.out.println("Shifted accumulator: PASS (20 cases)");
        List<String> scheduled = AsmOptimizer.rescheduleAccumulatorCoordinates(optimized);
        if (scheduled.size() != optimized.size() - 4 || scheduled.contains("\tld xwa, xhl")
                || !scheduled.contains("\tld xhl, xbc1") || !scheduled.contains("\tld xwa1, xhl")
                || !scheduled.contains("\tld xhl1, xhl"))
            throw new AssertionError("coordinate scratch must replace all four accumulator copies");
        if (AsmOptimizer.rescheduleAccumulatorCoordinates(scheduled) != scheduled)
            throw new AssertionError("coordinate scheduling must be idempotent");
        for (String instruction : List.of("\tadd xhl, xde", "\tld wa, hl", "\tcall _other")) {
            List<String> unsafe = new ArrayList<>(optimized);
            unsafe.set(26, instruction);
            if (!AsmOptimizer.rescheduleAccumulatorCoordinates(unsafe).equals(unsafe))
                throw new AssertionError("live scratch or call must block coordinate scheduling: " + instruction);
        }
        List<String> extraEdge = new ArrayList<>(optimized);
        extraEdge.set(25, "\tjr .Ljoin");
        if (!AsmOptimizer.rescheduleAccumulatorCoordinates(extraEdge).equals(extraEdge))
            throw new AssertionError("extra join predecessor must block coordinate scheduling");
        List<String> scratchInput = new ArrayList<>(optimized);
        scratchInput.set(18, "\tadd xwa, xhl");
        if (!AsmOptimizer.rescheduleAccumulatorCoordinates(scratchInput).equals(scratchInput))
            throw new AssertionError("coordinate scratch dependency must be rejected");
        List<String> longScheduled = AsmOptimizer.rescheduleAccumulatorCoordinates(
                optimized.stream().map(line -> line.replace("\tjr ", "\tjrl ")).toList());
        if (longScheduled.size() != scheduled.size() || !longScheduled.contains("\tjrl .Ljoin"))
            throw new AssertionError("long jumps must be preserved when scheduling");
        List<String> bankInput = new ArrayList<>(optimized);
        bankInput.set(17, "\tld xwa, xhl1");
        if (!AsmOptimizer.rescheduleAccumulatorCoordinates(bankInput).contains("\tld xhl, xhl1"))
            throw new AssertionError("banked scratch input must keep its register identity");
        for (String instruction : List.of("\tld xwa, xwa", "\tld xhl, xwa")) {
            List<String> unsafe = new ArrayList<>(optimized);
            unsafe.set(17, instruction);
            if (!AsmOptimizer.rescheduleAccumulatorCoordinates(unsafe).equals(unsafe))
                throw new AssertionError("primary register dependencies must be rejected: " + instruction);
        }
        List<String> indirect = new ArrayList<>(optimized);
        indirect.set(25, "\tjp xde");
        if (!AsmOptimizer.rescheduleAccumulatorCoordinates(indirect).equals(indirect))
            throw new AssertionError("indirect predecessors must be rejected");
        List<String> scheduleMultiple = new ArrayList<>(optimized);
        for (String line : optimized) scheduleMultiple.add(line.replace("_test:", "_other:").replace(".L", ".Lsecond"));
        if (AsmOptimizer.rescheduleAccumulatorCoordinates(scheduleMultiple).size() != scheduleMultiple.size() - 8)
            throw new AssertionError("all independent coordinate joins must be scheduled");
        List<String> scheduledFinal = AsmOptimizer.rescheduleAccumulatorCoordinates(finalPixel);
        if (scheduledFinal.size() != finalPixel.size() - 4
                || !scheduledFinal.contains("\tld (xsp+40), xhl")
                || !scheduledFinal.contains("\tld (xsp+44), xhl")
                || scheduledFinal.contains("\tld xwa, xhl"))
            throw new AssertionError("final-pixel scheduling must keep accumulator in XWA and store coordinate values");
        List<String> addressedFinal = new ArrayList<>(finalPixel);
        addressedFinal.set(19, "\tld (xhl+0), xwa");
        if (!AsmOptimizer.rescheduleAccumulatorCoordinates(addressedFinal).equals(addressedFinal))
            throw new AssertionError("scratch-addressed stack output must block final-pixel scheduling");
        System.out.println("Coordinate scheduling: PASS (15 cases)");
    }
}