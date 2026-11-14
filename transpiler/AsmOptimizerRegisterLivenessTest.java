import java.util.*;
import java.nio.file.*;
import sim.Cpu;
import sim.Memory;
import sim.Registers;

/** CFG/partial-register regressions for the declared bank-zero compiler ABI. */
public final class AsmOptimizerRegisterLivenessTest {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static List<String> fixture(String... body) {
        List<String> lines = new ArrayList<>(List.of(AsmPatterns.BANK_ZERO_ABI, "_test:"));
        lines.addAll(List.of(body));
        lines.add("\tret");
        return lines;
    }
    public static void main(String[] args) throws Exception {
        for (String escape : List.of("\tlda xhl, xsp+4", "\tld xhl, xsp", "\tcall _reader")) {
            List<String> input = fixture("\tld wa, 32", "\tld (xsp+4), wa", escape,
                "\tld wa, 40", "\tld (xsp+4), wa", "\tld wa, (xsp+4)");
            require(Ir2Tlcs900.eliminateSpills(input).contains("\tld (xsp+4), wa"),
                "escaped local store must survive " + escape);
        }
        // Identical textual stack offsets designate different cells after a
        // push/pop. In particular two distinct outgoing call arguments must
        // not collapse to two pushes of the same accumulator value.
        for (String barrier : List.of("\tpush wa", "\tpush xwa", "\tpop bc",
                "\tadd xsp, -2", "\tcall _opaque", "\tld (xhl), wa")) {
            for (String destination : List.of("wa", "bc")) {
                List<String> input = fixture("\tld wa, 17", "\tld (xsp+4), wa",
                    barrier, "\tld " + destination + ", (xsp+4)", "\tpush " + destination);
                require(Ir2Tlcs900.eliminateSpills(input).contains("\tld " + destination + ", (xsp+4)"),
                    "spill reload must not cross stack/call/alias barrier " + barrier + " " + destination);
            }
        }
        for (String neutral : List.of("ld xhl, xwa2", "ld xhl, xwa", "ld hl, wa", "ld l, a", "ld xwa2, xwa",
                "add xhl, xwa", "cp xwa, xhl", "cp wa, hl", "extz xhl", "scc Z, l", "ld rwa2, 7"))
            require(!Ir2Tlcs900.FuncTranslator.asmWritesWA(neutral), "source/banked operand does not invalidate WA " + neutral);
        for (String write : List.of("ld xwa, xhl", "ld wa, hl", "ld a, l", "ld w, 0", "add wa, hl", "sub xwa, xhl",
                "sla 1, wa", "inc 1, xwa", "scc Z, a", "ld xwa0, 0", "ld ra0, 7", "ldf 1", "call _foo", "opaque"))
            require(Ir2Tlcs900.FuncTranslator.asmWritesWA(write), "actual write/effect invalidates WA " + write);
        Ir2Tlcs900.FuncTranslator residency = new Ir2Tlcs900.FuncTranslator(
            new Ir2Tlcs900.IrFunction("bank_compare", "void", "", new ArrayList<>()), Set.of(), Map.of());
        residency.regOnly.add("lhs");
        residency.waSlotName = "lhs";
        residency.emit("ld xhl, xwa2");
        require("lhs".equals(residency.waSlotName), "banked RHS load preserves register-only LHS residency");
        residency.loadToWA("%lhs", "i32", Map.of());
        residency.emit("add wa, hl");
        require(residency.waSlotName == null, "real WA destination write invalidates resident SSA value");
        Ir2Tlcs900.FuncTranslator rawCall = new Ir2Tlcs900.FuncTranslator(
            new Ir2Tlcs900.IrFunction("raw_caller", "void", "", new ArrayList<>()), Set.of(), Map.of());
        rawCall.rawAsmCallees = Set.of("raw");
        rawCall.emitCall(null, "void", "raw", "i16 %p", Map.of("p", new int[]{4, 16}));
        require(rawCall.out.contains("\tpush xde") && rawCall.out.contains("\tpop xde")
            && rawCall.out.contains("\tpush xwa2") && rawCall.out.contains("\tpop xwa2"), "raw call preserves complete callee-saved register state");
        require(rawCall.out.contains("\tld wa, (xsp+56)") && rawCall.out.contains("\tadd xsp, 2"), "raw call rebases caller argument loads without changing callee entry arguments");
        for (String group : List.of("wa", "bc", "de", "hl")) for (int bank = 0; bank < 4; bank++) {
            String reg = "x" + group + bank, word = "r" + group + bank;
            String low = "r" + group.substring(1) + bank, high = "r" + group.substring(0, 1) + bank;
            String store = "\tld " + reg + ", xix";
            List<String> dead = fixture(store, "\tcp iz, 0", "\tjr Z, .Lother", "\tld " + reg + ", 0",
                "\tjr .Ljoin", ".Lother:", "\tpop " + reg, ".Ljoin:");
            require(!AsmPatterns.eliminateDeadRegisterCopies(dead).contains(store), "dead on both paths " + reg);
            for (String read : List.of(reg, word, low, high, "(" + reg + "+4)")) {
                List<String> live = fixture(store, "\tld xiz, " + read, "\tpop " + reg);
                require(AsmPatterns.eliminateDeadRegisterCopies(live).equals(live), "alias/address read " + read);
            }
            List<String> partial = fixture(store, "\tld " + word + ", 0", "\tld xiz, " + reg);
            require(AsmPatterns.eliminateDeadRegisterCopies(partial).equals(partial), "partial overwrite " + reg);
            List<String> onePath = new ArrayList<>(dead);
            onePath.set(8, "\tld xiz, " + reg);
            require(AsmPatterns.eliminateDeadRegisterCopies(onePath).contains(store), "one path live " + reg);
            for (String barrier : List.of("\tcall _opaque", "\tswi 1", "\tincf", "\tld sr, 0", "\tunknown")) {
                List<String> blocked = fixture(store, barrier, "\tpop " + reg);
                require(AsmPatterns.eliminateDeadRegisterCopies(blocked).equals(blocked), "barrier " + barrier);
            }
            String save = "\tld " + reg + ", xix", reload = "\tld xix, " + reg;
            List<String> available = fixture(save, "\tld xiy, xiz", reload, "\tpop " + reg);
            List<String> output = AsmPatterns.eliminateAvailableRegisterCopies(available);
            require(!output.contains(reload), "available source " + reg);
            require(!AsmPatterns.eliminateDeadRegisterCopies(output).contains(save), "dead save after reload removal " + reg);
            List<String> modified = fixture(save, "\tld ix, 1", reload, "\tpop " + reg);
            require(AsmPatterns.eliminateAvailableRegisterCopies(modified).equals(modified), "source partial write");
            List<String> unmarked = new ArrayList<>(available);
            unmarked.remove(0);
            require(AsmPatterns.eliminateAvailableRegisterCopies(unmarked).equals(unmarked), "require ABI declaration");
        }
        List<String> sample = fixture("\tld xbc1, xwa", "\tld xhl, xhl1", "\tld xwa, xbc1",
            "\tadd xhl, xwa", "\tld xbc1, xwa", "\tld xbc1, xwa", "\tpop xbc1");
        List<String> sampleResult = AsmPatterns.eliminateDeadRegisterCopies(AsmPatterns.eliminateAvailableRegisterCopies(sample));
        require(sampleResult.stream().noneMatch(s -> s.startsWith("\tld xbc1") || s.equals("\tld xwa, xbc1")), "SAMPLE redundant bank copies");
        List<String> ordinaryAlias = fixture("\tld xbc0, xwa", "\tld wa, bc", "\tpop xbc0");
        require(AsmPatterns.eliminateDeadRegisterCopies(ordinaryAlias).equals(ordinaryAlias), "ordinary bank-zero alias");
        List<String> loop = fixture(".Lloop:", "\tld xbc1, xwa", "\tcp wa, 0", "\tjr NZ, .Lloop", "\tpop xbc1");
        require(!AsmPatterns.eliminateDeadRegisterCopies(loop).contains("\tld xbc1, xwa"), "loop overwrite and exit restore");
        List<String> implicitDividend = fixture("\tld w, c", "\tdiv a, e", "\tld wa, 0");
        require(AsmPatterns.eliminateDeadRegisterCopies(implicitDividend).equals(implicitDividend), "DIV reads implicit wider dividend");
        List<String> unsupported = fixture("\tld xbc1, xwa", "\tld wa, qbc1", "\tpop xbc1");
        require(AsmPatterns.eliminateDeadRegisterCopies(unsupported).equals(unsupported), "unsupported upper-bank alias is a barrier");
        List<String> autoUpdate = fixture("\tld xbc1, xwa", "\tld a, (xbc1+)", "\tpop xbc1");
        require(AsmPatterns.eliminateDeadRegisterCopies(autoUpdate).equals(autoUpdate), "auto-update address is a barrier");
        List<String> immediate = fixture("\tld xiy, 42", "\tpop xiy");
        require(!AsmPatterns.eliminateDeadRegisterCopies(immediate).contains("\tld xiy, 42"), "dead immediate register value");
        List<String> memoryRead = fixture("\tld xiy, (01200h)", "\tpop xiy");
        require(AsmPatterns.eliminateDeadRegisterCopies(memoryRead).equals(memoryRead), "preserve dead memory/MMIO read");
        List<String> module = new ArrayList<>(List.of(AsmPatterns.BANK_ZERO_ABI,
            "_caller:", "\tpushw 7", "\tcall _test", "\tadd xsp, 2", "\tret",
            "_test:", "\tpush xbc1", "\tpush ix", "\tadd xsp, -8",
            "\tld wa, (xsp+18)", "\tld rbc1, wa", "\tld ix, wa", "\tld (xsp+0), wa",
            "\tld wa, 1", "\tld (xsp+2), wa", "\tld wa, rbc1", "\tcp wa, 0", "\tpushw 1", "\tld wa, (xsp+20)",
            "\tcall _leaf", "\tadd xsp, 2", "\tld wa, (xsp+18)", "\tadd xsp, 8",
            "\tpop ix", "\tpop xbc1", "\tret", "_leaf:", "\tld wa, 0", "\tret"));
        List<String> clean = AsmPatterns.cleanupRegisterValuesAndSaves(module);
        require(!clean.contains("\tld ix, wa") && !clean.contains("\tpush ix") && !clean.contains("\tpop ix"), "dead index across proven callee and remove saves");
        require(clean.contains("\tpush rbc1") && clean.contains("\tpop rbc1") && !clean.contains("\tpush xbc1"), "narrow bank save to live word");
        require(clean.contains("\tld wa, (xsp+14)") && clean.contains("\tld wa, (xsp+16)"), "rebase incoming arguments including nested push");
        require(clean.contains("\tld (xsp+0), wa"), "retain frame-local offset");
        require(AsmPatterns.cleanupRegisterValuesAndSaves(clean).equals(clean), "cleanup fixed point");
        List<String> opaque = new ArrayList<>(module);
        opaque.set(module.indexOf("\tcall _leaf"), "\tcall _unknown");
        require(AsmPatterns.cleanupRegisterValuesAndSaves(opaque).contains("\tpush ix"), "opaque call retains register/save");
        List<String> carryCaller = new ArrayList<>(module);
        carryCaller.set(4, "\tjr Z, .Lexit");
        require(AsmPatterns.cleanupRegisterValuesAndSaves(carryCaller).contains("\tpush xbc1"), "preserve live return flags during stack relocation");
        List<String> escaped = new ArrayList<>(module);
        escaped.set(13, "\tlda xhl, (xsp+0)");
        require(AsmPatterns.cleanupRegisterValuesAndSaves(escaped).contains("\tpush xbc1"), "escaping stack pointer prevents relocation");
        List<String> aliasCallee = new ArrayList<>(module);
        aliasCallee.set(module.lastIndexOf("\tld wa, 0"), "\tld wa, ix");
        require(AsmPatterns.cleanupRegisterValuesAndSaves(aliasCallee).contains("\tld ix, wa"), "callee reads stored lane");
        List<String> unbalancedCallee = new ArrayList<>(module);
        unbalancedCallee.add(unbalancedCallee.size() - 1, "\tpushw 1");
        require(AsmPatterns.cleanupRegisterValuesAndSaves(unbalancedCallee).contains("\tpush ix"), "unbalanced callee stack blocks cleanup");
        for (String result : List.of("xwa", "xbc", "xde", "xhl")) {
            String temp = result.equals("xhl") ? "xbc" : "xhl";
            for (String op : List.of("add", "sub", "adc", "sbc", "and", "or", "xor")) {
                List<String> shuffle = fixture("\tld " + temp + ", " + result, "\tld " + result + ", xiz",
                    "\t" + op + " " + temp + ", " + result, "\tld " + result + ", " + temp, "\tld " + temp + ", 0");
                List<String> folded = AsmPatterns.foldOperandRegisterShuffles(shuffle);
                require(folded.contains("\t" + op + " " + result + ", xiz") && folded.size() == shuffle.size() - 3, "ALU shuffle " + op + " " + result);
                List<String> live = new ArrayList<>(shuffle);
                live.set(6, "\tld xix, " + temp);
                require(AsmPatterns.foldOperandRegisterShuffles(live).equals(live), "live ALU temporary");
            }
            String word = result.substring(1), tempWord = temp.substring(1);
            List<String> operand = fixture("\tld xbc2, " + result, "\tld " + word + ", (04002h)", "\textz " + result,
                "\tld " + temp + ", " + result, "\tld " + result + ", xbc2", "\tmul " + result + ", " + tempWord,
                "\tld " + temp + ", 0", "\tpop xbc2");
            require(AsmPatterns.foldOperandRegisterShuffles(operand).contains("\tld " + tempWord + ", (04002h)"), "direct word operand " + result);
            List<String> upperLive = new ArrayList<>(operand);
            upperLive.set(8, "\tld xix, " + temp);
            require(AsmPatterns.foldOperandRegisterShuffles(upperLive).equals(upperLive), "retain extension for live upper word");
            List<String> addressed = new ArrayList<>(operand);
            addressed.set(3, "\tld " + word + ", (xbc2)");
            require(AsmPatterns.foldOperandRegisterShuffles(addressed).equals(addressed), "memory address uses removed save");
            List<String> mixedCase = new ArrayList<>(operand);
            mixedCase.set(3, "\tld " + word + ", (_MixedCaseWidth)");
            require(AsmPatterns.foldOperandRegisterShuffles(mixedCase).contains("\tld " + tempWord + ", (_MixedCaseWidth)"), "preserve case-sensitive memory symbol");
        }
        for (String counter : List.of("bc", "de", "hl", "ix", "iy", "iz", "xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
            String temp = AsmPatterns.width(counter) == 16 ? "wa" : "xwa";
            List<String> loopCounter = fixture(".Lloop:", "\tld " + temp + ", " + counter, "\tinc 1, " + temp,
                "\tcp " + temp + ", 7", "\tjr NC, .Lexit", "\tld " + counter + ", " + temp,
                "\tjrl .Lloop", ".Lexit:", "\tld " + temp + ", 0", "\tpop " + counter);
            List<String> folded = AsmPatterns.foldCounterCopyBackedges(loopCounter);
            require(folded.contains("\tinc 1, " + counter) && folded.contains("\tjrl C, .Lloop")
                && folded.size() == loopCounter.size() - 3, "counter backedge " + counter);
            List<String> liveCounter = new ArrayList<>(loopCounter);
            liveCounter.set(10, "\tld xwa, " + counter);
            require(AsmPatterns.foldCounterCopyBackedges(liveCounter).equals(liveCounter), "old counter live on exit");
            List<String> liveTemp = new ArrayList<>(loopCounter);
            liveTemp.set(10, "\tld xiz, " + temp);
            require(AsmPatterns.foldCounterCopyBackedges(liveTemp).equals(liveTemp), "scratch result live on exit");
            List<String> backedgeTemp = new ArrayList<>(loopCounter);
            backedgeTemp.add(3, "\tcp " + temp + ", 0");
            require(AsmPatterns.foldCounterCopyBackedges(backedgeTemp).equals(backedgeTemp), "scratch result live at loop entry");
        }
        for (String wide : List.of("xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz", "xbc1", "xde2")) {
            String word = wide.matches("x(?:wa|bc|de|hl)[0-3]") ? "r" + wide.substring(1) : wide.substring(1);
            String extension = "\textz " + wide;
            List<String> narrowOnly = fixture(extension, "\tcp " + word + ", 0", "\tjr Z, .Lexit",
                "\tld " + wide + ", 0", "\tjr .Ldone", ".Lexit:", "\tld " + wide + ", 1", ".Ldone:");
            require(!AsmPatterns.eliminateDeadZeroExtensions(narrowOnly).contains(extension), "dead upper half across both paths " + wide);
            for (String read : List.of("\tld xiz, " + wide, "\tld a, (" + wide + "+0)", "\tcall _opaque")) {
                List<String> live = fixture(extension, read, "\tld " + wide + ", 0");
                require(AsmPatterns.eliminateDeadZeroExtensions(live).equals(live), "keep live/unknown upper half " + read);
            }
            List<String> repeatedExtension = fixture(extension, "\tcp " + word + ", 0", extension, "\tld xwa, " + wide);
            require(AsmPatterns.eliminateDeadZeroExtensions(repeatedExtension).stream().filter(extension::equals).count() == 1, "later extension kills earlier upper write");
        }
        for (String word : List.of("wa", "bc", "de", "hl")) {
            String extension = "\textz " + word;
            List<String> lowOnly = fixture(extension, "\tcp " + word.substring(1) + ", 0", "\tld " + word + ", 0");
            require(!AsmPatterns.eliminateDeadZeroExtensions(lowOnly).contains(extension), "dead byte-extension high byte");
        }
        List<String> returnExtension = new ArrayList<>(List.of(AsmPatterns.BANK_ZERO_ABI, "_caller:", "\tcall _word",
            "\tcp hl, 0", "\tld xhl, 7", "\tld (04000h), hl", "\tret", "_word:", "\tld hl, 7", "\textz xhl", "\tret"));
        require(!AsmPatterns.eliminateDeadZeroExtensions(returnExtension).contains("\textz xhl"), "prove narrow-only return use at caller");
        List<String> wideReturn = new ArrayList<>(returnExtension);
        wideReturn.set(3, "\tcp xhl, 0");
        require(AsmPatterns.eliminateDeadZeroExtensions(wideReturn).contains("\textz xhl"), "wide return is live");
        List<String> escapedReturn = new ArrayList<>(returnExtension);
        escapedReturn.add("\tdd _word");
        require(AsmPatterns.eliminateDeadZeroExtensions(escapedReturn).contains("\textz xhl"), "address-taken function blocks return proof");
        List<String> liveMultiplier = fixture("\textz xhl", "\tmul xwa, hl", "\tld xhl, 0");
        require(!AsmPatterns.eliminateDeadZeroExtensions(liveMultiplier).contains("\textz xhl"), "word multiplier does not read upper word");
        List<String> wideDividend = fixture("\textz xhl", "\tdiv hl, wa", "\tld xhl, 0");
        require(AsmPatterns.eliminateDeadZeroExtensions(wideDividend).equals(wideDividend), "implicit wide dividend observes upper word");
        List<String> killingCall = new ArrayList<>(List.of(AsmPatterns.BANK_ZERO_ABI, "_caller:", "\textz xhl", "\tcall _kill",
            "\tld (04000h), hl", "\tret", "_kill:", "\tld hl, 7", "\textz xhl", "\tret"));
        require(AsmPatterns.eliminateDeadZeroExtensions(killingCall).stream().filter("\textz xhl"::equals).count() == 1, "callee kills upper input before reading");
        List<String> readingCall = new ArrayList<>(killingCall);
        readingCall.add(readingCall.indexOf("_kill:") + 1, "\tld xwa, xhl");
        require(AsmPatterns.eliminateDeadZeroExtensions(readingCall).stream().filter("\textz xhl"::equals).count() == 2, "callee reads upper input before kill");
        List<String> recursiveKill = new ArrayList<>(killingCall);
        recursiveKill.add(recursiveKill.indexOf("_kill:") + 1, "\tcall _kill");
        require(AsmPatterns.eliminateDeadZeroExtensions(recursiveKill).contains("\textz xhl"), "recursive call-kill proof terminates conservatively");
        for (String word : List.of("wa", "bc", "de", "hl", "ix", "iy", "iz", "rwa1", "rhl2")) {
            for (int mask = 0; mask < 256; mask++) {
                List<String> masked = fixture("\textz " + word, "\tand " + word + ", " + mask, "\tret");
                List<String> folded = AsmPatterns.eliminateDeadZeroExtensions(masked);
                require(!folded.contains("\textz " + word) && folded.contains("\tand " + word + ", " + mask), "mask kills extension " + word + " " + mask);
            }
            List<String> liveMask = fixture("\textz " + word, "\tand " + word + ", 256", "\tret");
            require(AsmPatterns.eliminateDeadZeroExtensions(liveMask).equals(liveMask), "mask retains high input bit " + word);
        }
        List<String> wideMask = fixture("\textz xhl", "\tand xhl, 65535", "\tret");
        require(!AsmPatterns.eliminateDeadZeroExtensions(wideMask).contains("\textz xhl"), "wide mask kills upper word");
        List<String> interveningRead = fixture("\textz wa", "\tcp wa, 0", "\tand wa, 255", "\tret");
        require(AsmPatterns.eliminateDeadZeroExtensions(interveningRead).equals(interveningRead), "read before mask keeps extension");
        for (String reg : List.of("a", "w", "wa", "bc", "de", "hl", "ix", "xwa", "xhl", "rwa1", "xde2")) {
            String mask = "\tand " + reg + ", 15";
            List<String> repeated = fixture(mask, "\tld xiy, 0", mask, "\tret");
            require(AsmPatterns.eliminateRepeatedRegisterMasks(repeated).stream().filter(mask::equals).count() == 1, "repeated mask " + reg);
            for (String barrier : List.of("\tld " + reg + ", 7", "\tcp wa, 0", "\tcall _opaque", ".Lboundary:", "\tldf 1")) {
                List<String> blocked = fixture(mask, barrier, mask, "\tret");
                require(AsmPatterns.eliminateRepeatedRegisterMasks(blocked).equals(blocked), "mask barrier " + barrier);
            }
        }
        List<String> copyMask = fixture("\tand wa, 255", "\tld rde1, wa", "\tand wa, 0ffh", "\tret");
        require(AsmPatterns.eliminateRepeatedRegisterMasks(copyMask).size() == copyMask.size() - 1, "copy preserves mask value and flags");
        List<String> byteWriteMask = fixture("\tand wa, 255", "\tld a, 7", "\tand wa, 255", "\tret");
        require(AsmPatterns.eliminateRepeatedRegisterMasks(byteWriteMask).equals(byteWriteMask), "partial alias write blocks repeated mask");
        for (String source : List.of("xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz")) {
            String destination = source.equals("xhl") ? "xbc" : "xhl";
            List<String> loadRoute = fixture("\tld " + source.substring(1) + ", (_MixedCaseWidth)", "\textz " + source,
                "\tld (xsp+64), " + source, "\tld " + destination + ", " + source, "\tld " + source + ", 0");
            List<String> routed = AsmPatterns.foldOperandRegisterShuffles(loadRoute);
            require(routed.size() == loadRoute.size() - 1 && routed.contains("\tld " + destination.substring(1) + ", (_MixedCaseWidth)")
                && routed.contains("\textz " + destination) && routed.contains("\tld (xsp+64), " + destination), "route narrow load/extension/store " + source);
            List<String> liveSource = new ArrayList<>(loadRoute);
            liveSource.set(6, "\tcp " + source + ", 0");
            require(AsmPatterns.foldOperandRegisterShuffles(liveSource).equals(liveSource), "live source prevents load routing");
            List<String> partialKill = new ArrayList<>(loadRoute);
            partialKill.set(6, "\tld " + source.substring(1) + ", 0");
            require(AsmPatterns.foldOperandRegisterShuffles(partialKill).equals(partialKill), "partial source kill insufficient");
        }
        for (String word : List.of("wa", "bc", "de", "hl", "rwa1", "rbc2")) {
            String low = word.startsWith("r") ? "r" + word.substring(2) : word.substring(1);
            List<String> deadMask = fixture("\tand " + word + ", 255", "\tld rl2, " + low,
                "\tld " + word + ", 0", "\tcp ix, 0");
            require(!AsmPatterns.eliminateDeadRegisterMasks(deadMask).contains("\tand " + word + ", 255"), "mask high byte dead with live low copy " + word);
            List<String> lowReset = fixture("\tand " + word + ", 255", "\tld " + low + ", 0", "\tcp " + word + ", 0");
            require(AsmPatterns.eliminateDeadRegisterMasks(lowReset).equals(lowReset), "low reset does not kill high byte " + word);
            List<String> flagsLive = fixture("\tand " + word + ", 255", "\tld " + word + ", 0", "\tscc Z, a");
            require(AsmPatterns.eliminateDeadRegisterMasks(flagsLive).equals(flagsLive), "mask flags live " + word);
            List<String> changedLow = fixture("\tand " + word + ", 127", "\tld rl2, " + low,
                "\tld " + word + ", 0", "\tcp ix, 0");
            require(AsmPatterns.eliminateDeadRegisterMasks(changedLow).equals(changedLow), "partial low mask affects live low byte " + word);
        }
        for (String word : List.of("wa", "bc", "de", "hl", "rwa1", "rbc2", "xwa", "xhl")) {
            String low = word.startsWith("r") ? "r" + word.substring(2) : word.startsWith("x") ? word.substring(2) : word.substring(1);
            List<String> narrowCompare = fixture("\tand " + word + ", 255", "\tcp " + word + ", 1",
                "\tjr NZ, .Lexit", "\tld " + word + ", 0", "\tcp ix, 0", "\tjr .Ldone",
                ".Lexit:", "\tld " + word + ", 0", "\tcp ix, 0", ".Ldone:");
            require(AsmPatterns.foldMaskedByteComparisons(narrowCompare).contains("\tcp " + low + ", 1"), "narrow masked compare " + word);
            List<String> signedCompare = new ArrayList<>(narrowCompare);
            signedCompare.set(4, "\tjr LT, .Lexit");
            require(AsmPatterns.foldMaskedByteComparisons(signedCompare).equals(signedCompare), "signed flags prevent compare narrowing");
            List<String> laterSigned = new ArrayList<>(narrowCompare);
            laterSigned.add(5, "\tscc MI, a");
            require(AsmPatterns.foldMaskedByteComparisons(laterSigned).equals(laterSigned), "later nonzero flags remain live");
        }
        for (String reg : List.of("a", "c", "l", "ra1", "wa", "xwa", "xhl")) {
            List<String> zeroTest = fixture("\tand " + reg + ", 7", "\tcp " + reg + ", 0", "\tscc Z, a", "\tcp ix, 0");
            require(!AsmPatterns.foldMaskedByteComparisons(zeroTest).contains("\tcp " + reg + ", 0"), "logical Z replaces compare zero " + reg);
            List<String> carryTest = new ArrayList<>(zeroTest);
            carryTest.set(4, "\tscc C, a");
            require(AsmPatterns.foldMaskedByteComparisons(carryTest).stream().map(AsmPatterns::parse)
                .anyMatch(n -> n.op().equals("cp") && n.right().equals("0") && !n.left().equals("ix")), "zero compare retained for carry");
        }
        List<String> deadHighZero = fixture("\txor w, w", "\tld a, 5", "\tand a, 7", "\tld wa, 0", "\tcp ix, 0");
        require(!AsmPatterns.eliminateDeadRegisterZeroes(deadHighZero).contains("\txor w, w"), "dead high-byte zero removed");
        List<String> highZeroLive = fixture("\txor w, w", "\tld a, 5", "\tcp wa, 0");
        require(AsmPatterns.eliminateDeadRegisterZeroes(highZeroLive).equals(highZeroLive), "high byte zero needed by word compare");
        List<String> wideLogicalZ = fixture("\tand xwa, 7", "\tcp a, 0", "\tscc Z, l", "\tcp ix, 0");
        require(!AsmPatterns.foldMaskedByteComparisons(wideLogicalZ).contains("\tcp a, 0"), "masked wide result has same byte Z");
        List<String> xorKillsHigh = fixture("\tand wa, 255", "\tcp a, 1", "\tjr NZ, .Lexit",
            "\tld xwa, 0", "\tjr .Ldone", ".Lexit:", "\txor w, w", ".Ldone:", "\tcp ix, 0");
        require(!AsmPatterns.eliminateDeadRegisterMasks(xorKillsHigh).contains("\tand wa, 255"), "self XOR kills old high byte on successor");
        for (String temp : List.of("a", "w", "c", "l", "wa", "bc", "hl", "ix", "xwa", "xhl", "rwa1", "ra2", "xbc2")) {
            int size = AsmPatterns.registerBytes(temp).size();
            String source = size == 1 ? "b" : size == 2 ? "de" : "xde";
            List<String> copyStore = fixture("\tld " + temp + ", " + source, "\tld (xsp+8), " + temp,
                "\tjrl .Lexit", ".Lexit:", "\tld " + temp + ", 0");
            List<String> directStore = AsmPatterns.foldRegisterCopyToStackStores(copyStore);
            require(directStore.size() == copyStore.size() - 1 && directStore.contains("\tld (xsp+8), " + source), "direct register stack store " + temp);
            List<String> liveCopy = new ArrayList<>(copyStore);
            liveCopy.add(6, "\tld " + source + ", " + temp);
            require(AsmPatterns.foldRegisterCopyToStackStores(liveCopy).equals(liveCopy), "live copied value keeps transfer " + temp);
        }
        List<String> liveWideAlias = fixture("\tld a, b", "\tld (xsp+8), a", "\tcp wa, 0", "\tld a, 0");
        require(AsmPatterns.foldRegisterCopyToStackStores(liveWideAlias).equals(liveWideAlias), "wide alias reads copied low byte");
        List<String> liveExitCopy = fixture("\tld a, b", "\tld (xsp+8), a", "\tjr NZ, .Lexit", "\tld a, 0",
            "\tjr .Ldone", ".Lexit:", "\tld c, a", ".Ldone:");
        require(AsmPatterns.foldRegisterCopyToStackStores(liveExitCopy).equals(liveExitCopy), "copied byte live on one successor");
        for (String bankSource : List.of("ra1", "rb2", "rwa2", "rde1", "xwa2", "xbc1", "xde2", "xhl3")) {
            int size = AsmPatterns.registerBytes(bankSource).size();
            String ordinary = size == 1 ? "a" : size == 2 ? "wa" : "xwa";
            List<String> bankStore = fixture("\tld " + ordinary + ", " + bankSource, "\tld (xsp+0), " + ordinary,
                "\tld " + ordinary + ", 0");
            require(AsmPatterns.foldRegisterCopyToStackStores(bankStore).equals(bankStore), "memory store cannot encode explicit-bank source " + bankSource);
        }
        for (String result : List.of("a", "c", "h", "wa", "hl", "ix", "xwa", "xhl", "ra1", "rwa2", "xbc2")) {
            int size = AsmPatterns.registerBytes(result).size();
            String temp = size == 1 ? "e" : size == 2 ? "de" : "xde";
            for (String op : List.of("and", "or", "xor")) {
                List<String> stackOperand = fixture("\tld " + temp + ", " + result, "\tld " + result + ", (xsp+34)",
                    "\t" + op + " " + result + ", " + temp, "\tld " + temp + ", 0");
                List<String> folded = AsmPatterns.foldCommutativeStackOperands(stackOperand);
                if (AsmPatterns.width(result) == 0) require(folded.equals(stackOperand), "memory ALU cannot encode explicit-bank destination " + result);
                else require(folded.size() == stackOperand.size() - 2 && folded.contains("\t" + op + " " + result + ", (xsp+34)"), "commutative memory operand " + op + " " + result);
                List<String> liveTemp = new ArrayList<>(stackOperand);
                liveTemp.set(5, "\tcp " + temp + ", 0");
                require(AsmPatterns.foldCommutativeStackOperands(liveTemp).equals(liveTemp), "live saved operand keeps copies");
            }
        }
        List<String> noncommutative = fixture("\tld l, a", "\tld a, (xsp+34)", "\tsub a, l", "\tld l, 0");
        require(AsmPatterns.foldCommutativeStackOperands(noncommutative).equals(noncommutative), "operand order matters for subtraction");
        List<String> aliasedAddress = fixture("\tld l, a", "\tld a, (xhl)", "\tand a, l", "\tld l, 0");
        require(AsmPatterns.foldCommutativeStackOperands(aliasedAddress).equals(aliasedAddress), "removed save feeds non-stack address");
        for (String destination : List.of("xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz", "xbc1", "xde2")) {
            String source = destination.equals("xhl") ? "xde" : "xhl";
            String low = destination.matches("x(?:wa|bc|de|hl)[0-3]") ? "r" + destination.substring(1) : destination.substring(1);
            List<String> narrowCopy = fixture("\tld " + destination + ", " + source, "\tcp " + low + ", 0", "\tld " + destination + ", 0");
            require(AsmPatterns.narrowRegisterCopiesWithDeadUpperHalves(narrowCopy).contains("\tld " + low + ", " + source.substring(1)), "narrow dead-upper copy " + destination);
            List<String> upperRead = fixture("\tld " + destination + ", " + source, "\tcp " + destination + ", 0", "\tld " + destination + ", 0");
            require(AsmPatterns.narrowRegisterCopiesWithDeadUpperHalves(upperRead).equals(upperRead), "wide read keeps full copy " + destination);
        }
        List<String> partialOverwriteSave = fixture("\tld xde2, xwa", "\tld rde2, 7", "\tld xwa, xde2",
            "\tcp wa, 0", "\tld xwa, 0", "\tpop xde2");
        List<String> narrowSave = AsmPatterns.cleanupRegisterValuesAndSaves(partialOverwriteSave);
        require(!narrowSave.contains("\tld xde2, xwa"), "narrow later copy exposes dead wide save after word overwrite");
        List<String> highStillLive = fixture("\tld xde2, xwa", "\tld rde2, 7", "\tcp xde2, 0", "\tpop xde2");
        require(AsmPatterns.cleanupRegisterValuesAndSaves(highStillLive).contains("\tld xde2, xwa"), "word overwrite alone does not kill high saved half");
        List<String> narrowAtCaller = new ArrayList<>(List.of(AsmPatterns.BANK_ZERO_ABI, "_caller:", "\tcall _copy",
            "\tcp wa, 0", "\tld xwa, 0", "\tret", "_copy:", "\tld xwa, xde2", "\tret"));
        require(AsmPatterns.narrowRegisterCopiesWithDeadUpperHalves(narrowAtCaller).contains("\tld wa, rde2"), "caller proves dead upper copy return lanes");
        List<String> wideAtCaller = new ArrayList<>(narrowAtCaller);
        wideAtCaller.set(3, "\tcp xwa, 0");
        require(AsmPatterns.narrowRegisterCopiesWithDeadUpperHalves(wideAtCaller).contains("\tld xwa, xde2"), "wide caller return use keeps full copy");
        for (String scratch : List.of("xwa", "xhl")) {
            List<String> voidExtension = fixture(AsmPatterns.VOID_SCRATCH_ABI, "\textz " + scratch, "\tcp " + scratch.substring(1) + ", 0");
            require(!AsmPatterns.eliminateDeadZeroExtensions(voidExtension).contains("\textz " + scratch), "declared void scratch upper half dead at return " + scratch);
            List<String> wideUse = fixture(AsmPatterns.VOID_SCRATCH_ABI, "\textz " + scratch, "\tcp " + scratch + ", 0");
            require(AsmPatterns.eliminateDeadZeroExtensions(wideUse).contains("\textz " + scratch), "void metadata does not bypass in-function wide read");
        }
        List<String> preservedVoid = fixture(AsmPatterns.VOID_SCRATCH_ABI, "\textz xbc", "\tcp bc, 0");
        require(AsmPatterns.eliminateDeadZeroExtensions(preservedVoid).contains("\textz xbc"), "void ABI preserves callee-saved bytes");
        List<String> bankVoid = fixture(AsmPatterns.VOID_SCRATCH_ABI, "\textz xhl1", "\tcp rhl1, 0");
        require(AsmPatterns.eliminateDeadZeroExtensions(bankVoid).contains("\textz xhl1"), "void ABI does not clobber explicit bank one");
        List<String> noDeclaration = fixture("\textz xhl", "\tcp hl, 0");
        require(AsmPatterns.eliminateDeadZeroExtensions(noDeclaration).contains("\textz xhl"), "unannotated assembly remains conservative");
        List<String> misplacedDeclaration = fixture("\tld hl, 0", AsmPatterns.VOID_SCRATCH_ABI, "\textz xhl", "\tcp hl, 0");
        require(AsmPatterns.eliminateDeadZeroExtensions(misplacedDeclaration).contains("\textz xhl"), "ABI metadata must be at entry");
        List<String> voidFlags = fixture(AsmPatterns.VOID_SCRATCH_ABI, "\tand wa, 255");
        require(AsmPatterns.eliminateDeadRegisterMasks(voidFlags).contains("\tand wa, 255"), "void register ABI does not declare return flags dead");
        String abiIr = "define void @plain() {\nentry:\n  ret void\n}\n"
            + "define i16 @value() {\nentry:\n  ret i16 7\n}\n"
            + "define void @DummyInterrupt() {\nentry:\n  ret void\n}\n"
            + "define void @raw_metadata_test() {\nentry:\n  call void asm sideeffect \"ld xde, 0\", \"\"()\n  ret void\n}\n";
        String abiAsm = new Ir2Tlcs900.Translator(abiIr, "abi-test").translate();
        require(abiAsm.contains("_plain:\n" + AsmPatterns.VOID_SCRATCH_ABI), "translator emits void scratch ABI at entry");
        require(abiAsm.lines().filter(AsmPatterns.VOID_SCRATCH_ABI::equals).count() == 1, "nonvoid, ISR and raw-asm functions do not receive void scratch ABI");
        List<String> stackRoundtrip = fixture("\tld (xsp+42), xwa", "\tld wa, bc", "\textz xwa",
            "\tld (xsp+46), xwa", "\tld xhl, (xsp+42)", "\tadd xhl, xwa", "\tld (xsp+50), xhl",
            "\tld xhl, 09000h", "\tld (xsp+42), xhl");
        List<String> directStack = AsmPatterns.foldDeadStackRoundtrips(stackRoundtrip);
        require(directStack.contains("\tld xhl, xwa") && !directStack.contains("\tld xhl, (xsp+42)")
            && !directStack.contains("\tld (xsp+42), xwa"), "route dead stack roundtrip directly to destination");
        for (String barrier : List.of("\tcp hl, 0", "\tld h, 0", "\tld wa, (xsp+43)", "\tld (xsp+44), wa",
                "\tld a, (xix)", "\tld (xix), a", "\tcall _opaque", "\tadd xsp, 2", "\tpush wa", ".Lother:")) {
            List<String> blocked = new ArrayList<>(stackRoundtrip);
            blocked.add(3, barrier);
            require(AsmPatterns.foldDeadStackRoundtrips(blocked).equals(blocked), "stack roundtrip barrier " + barrier);
        }
        List<String> escapedStack = new ArrayList<>(stackRoundtrip);
        escapedStack.add(2, "\tld xix, xsp");
        require(AsmPatterns.foldDeadStackRoundtrips(escapedStack).equals(escapedStack), "escaped stack address");
        List<String> laterRead = new ArrayList<>(stackRoundtrip);
        laterRead.add(laterRead.indexOf("\tld (xsp+42), xhl"), "\tld xwa, (xsp+42)");
        require(AsmPatterns.foldDeadStackRoundtrips(laterRead).equals(laterRead), "stack value read before overwrite");
        if (Arrays.asList(args).contains("--execution")) {
            executionChecks(module);
            List<String> arithmetic = fixture("\tpush xbc2", "\tld wa, 5", "\textz xwa", "\tld xde, 2",
                "\tld xhl, xwa", "\tld xwa, xde", "\tadd xhl, xwa", "\tld xwa, xhl", "\tld xbc2, xwa",
                "\tld wa, (04002h)", "\textz xwa", "\tld xhl, xwa", "\tld xwa, xbc2", "\tmul xwa, hl",
                "\tld xhl, xwa", "\tld (04000h), wa", "\tpop xbc2");
            assembledChecks(arithmetic);
            List<String> counterLoop = fixture("\tpush bc", "\tld bc, 0", ".Lloop:", "\tld wa, bc", "\tinc 1, wa",
                "\tcp wa, 7", "\tjr NC, .Lexit", "\tld bc, wa", "\tjr .Lloop", ".Lexit:",
                "\tld wa, 7", "\tld (04000h), wa", "\tpop bc");
            assembledChecks(counterLoop);
            assembledChecks(returnExtension);
            assembledChecks(killingCall);
            List<String> stackExecution = fixture("\tadd xsp, -12", "\tld xwa, 4", "\tld (xsp+0), xwa",
                "\tld xwa, 3", "\tld (xsp+4), xwa", "\tld xhl, (xsp+0)", "\tadd xhl, xwa",
                "\tld (xsp+0), xhl", "\tld (04000h), hl", "\tld xwa, 0", "\tld xhl, 0", "\tadd xsp, 12");
            require(AsmPatterns.foldDeadStackRoundtrips(stackExecution).contains("\tld xhl, xwa"), "execution fixture exercises stack fold");
            assembledChecks(stackExecution);
            assembledChecks(fixture("\tld a, 7", "\textz wa", "\tand wa, 255", "\tld (04000h), wa", "\tld xwa, 0"));
            assembledChecks(fixture("\tld wa, 7", "\tand wa, 255", "\tld rde1, wa", "\tand wa, 255", "\tld (04000h), wa"));
            List<String> loadExecution = fixture("\tadd xsp, -4", "\tld wa, (04002h)", "\textz xwa",
                "\tld (xsp+0), xwa", "\tld xhl, xwa", "\tld xwa, 6", "\tadd xhl, xwa",
                "\tld (04000h), hl", "\tld xhl, (xsp+0)", "\tld xhl, 0", "\tadd xsp, 4");
            require(AsmPatterns.foldOperandRegisterShuffles(loadExecution).contains("\tld hl, (04002h)"), "execution fixture exercises routed load");
            assembledChecks(loadExecution);
            List<String> deadMaskExecution = fixture("\tld a, 7", "\tand wa, 255", "\tld rl2, a",
                "\tld (04000h), a", "\tld wa, 0", "\tcp wa, 0");
            require(!AsmPatterns.eliminateDeadRegisterMasks(deadMaskExecution).contains("\tand wa, 255"), "execution fixture exercises dead mask");
            assembledChecks(deadMaskExecution);
            List<String> byteCompareExecution = fixture("\tand wa, 255", "\tcp wa, 1", "\tscc NZ, l",
                "\tld wa, 7", "\tld (04000h), wa", "\tcp ix, 0");
            require(AsmPatterns.foldMaskedByteComparisons(byteCompareExecution).contains("\tcp a, 1"), "execution fixture exercises byte comparison");
            assembledChecks(byteCompareExecution);
            List<String> zeroCompareExecution = fixture("\txor w, w", "\tld a, 7", "\tand a, 7", "\tand wa, 255",
                "\tcp wa, 0", "\tscc Z, l", "\tld wa, 7", "\tld (04000h), wa", "\tcp ix, 0");
            List<String> zeroCompareOptimized = AsmPatterns.cleanupRegisterValuesAndSaves(zeroCompareExecution);
            require(!zeroCompareOptimized.contains("\tcp wa, 0") && !zeroCompareOptimized.contains("\tcp a, 0")
                && !zeroCompareOptimized.contains("\txor w, w") && !zeroCompareOptimized.contains("\tand wa, 255"), "execution fixture exercises full zero-test cleanup");
            assembledChecks(zeroCompareExecution);
            List<String> directStoreExecution = fixture("\tadd xsp, -4", "\tld b, 7", "\tld a, b", "\tld (xsp+0), a",
                "\tld a, 0", "\tld c, (xsp+0)", "\tld (04000h), c", "\tadd xsp, 4");
            require(AsmPatterns.foldRegisterCopyToStackStores(directStoreExecution).contains("\tld (xsp+0), b"), "execution fixture exercises direct stack store");
            assembledChecks(directStoreExecution);
            for (String op : List.of("and", "or", "xor")) {
                int initial = op.equals("and") ? 7 : op.equals("or") ? 3 : 4;
                int memoryOperand = op.equals("and") ? 255 : op.equals("or") ? 4 : 3;
                List<String> commutativeExecution = fixture("\tadd xsp, -4", "\tld a, " + memoryOperand, "\tld (xsp+0), a",
                    "\tld a, " + initial, "\tld l, a", "\tld a, (xsp+0)", "\t" + op + " a, l",
                    "\tld (04000h), a", "\tld l, 0", "\tadd xsp, 4");
                require(AsmPatterns.foldCommutativeStackOperands(commutativeExecution).contains("\t" + op + " a, (xsp+0)"), "execution fixture exercises commutative " + op);
                assembledChecks(commutativeExecution);
            }
            List<String> partialSaveExecution = fixture("\tpush xde2", "\tld xde2, xwa", "\tld rde2, 7", "\tld xwa, xde2",
                "\tld (04000h), wa", "\tld xwa, 0", "\tpop xde2");
            require(!AsmPatterns.cleanupRegisterValuesAndSaves(partialSaveExecution).contains("\tld xde2, xwa"), "execution fixture removes partially overwritten wide save");
            assembledChecks(partialSaveExecution);
            List<String> bankStoreExecution = fixture("\tadd xsp, -4", "\tld xwa2, 7", "\tld xwa, xwa2", "\tld (xsp+0), xwa",
                "\tld xwa, 0", "\tld wa, (xsp+0)", "\tld (04000h), wa", "\tld xwa, 0", "\tadd xsp, 4");
            require(AsmPatterns.foldRegisterCopyToStackStores(bankStoreExecution).equals(bankStoreExecution), "execution fixture keeps encodable bank-to-ordinary store bridge");
            assembledChecks(bankStoreExecution);
            List<String> voidExecution = new ArrayList<>(List.of(AsmPatterns.BANK_ZERO_ABI, "_caller:", "\tcall _void",
                "\tld xhl, 0", "\tld xwa, 7", "\tld (04000h), wa", "\tcp wa, 7", "\tret",
                "_void:", AsmPatterns.VOID_SCRATCH_ABI, "\tld hl, 7", "\textz xhl", "\tcp hl, 7", "\tret"));
            require(!AsmPatterns.eliminateDeadZeroExtensions(voidExecution).contains("\textz xhl"), "execution fixture exercises void return metadata");
            assembledChecks(voidExecution);
            Ir2Tlcs900.FuncTranslator rawExecutionCall = new Ir2Tlcs900.FuncTranslator(
                new Ir2Tlcs900.IrFunction("caller", "void", "", new ArrayList<>()), Set.of(), Map.of());
            rawExecutionCall.rawAsmCallees = Set.of("raw");
            rawExecutionCall.emitCall(null, "void", "raw", "i16 7", Map.of());
            List<String> rawExecution = new ArrayList<>(List.of(AsmPatterns.BANK_ZERO_ABI, "_caller:", "\tld e, 7"));
            rawExecution.addAll(rawExecutionCall.out);
            rawExecution.addAll(List.of("\tcp hl, 7", "\tjr Z, .Larg_ok", "\tld e, 0", ".Larg_ok:", "\tld (04000h), e", "\tret",
                "_raw:", "\tld hl, (xsp+4)", "\tld xde, 01234h", "\tld xwa2, 05678h", "\tret"));
            assembledChecks(rawExecution);
        }
        System.out.println("AsmOptimizerRegisterLivenessTest: PASS (" + checks + " checks)");
    }

    private static void executionChecks(List<String> module) throws Exception {
        List<String> original = new ArrayList<>(module);
        original.add(original.indexOf("\tadd xsp, 8"), "\tld (04000h), wa");
        assembledChecks(original);
    }

    private static void assembledChecks(List<String> original) throws Exception {
        List<String> optimized = AsmPatterns.cleanupRegisterValuesAndSaves(original);
        Path dir = Files.createTempDirectory("register-liveness-execution-");
        byte[][] binaries = new byte[2][];
        for (int variant = 0; variant < 2; variant++) {
            String name = "case" + variant;
            List<String> assembly = new ArrayList<>(List.of("\tcpu 93C141", "\tmaxmode on", "\torg 0200000h"));
            assembly.addAll(variant == 0 ? original : optimized);
            assembly.add("\tend");
            Files.write(dir.resolve(name + ".asm"), assembly);
            run(dir, Path.of("../../as/bin/asl.exe").toAbsolutePath().normalize().toString(), name + ".asm");
            run(dir, Path.of("../../as/bin/p2bin.exe").toAbsolutePath().normalize().toString(), "-r", "$-$", name + ".p", name + ".bin");
            binaries[variant] = Files.readAllBytes(dir.resolve(name + ".bin"));
        }
        for (int seed = 0; seed < 16; seed++) {
            List<Integer> before = execute(binaries[0], seed), after = execute(binaries[1], seed);
            require(before.equals(after), "assembled register/stack/return-flags equivalence seed " + seed);
        }
        System.out.println("Register liveness assembled execution: PASS (32 executions; artifacts " + dir + ")");
    }

    private static void run(Path dir, String... command) throws Exception {
        Path log = dir.resolve("command.log");
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (process.waitFor() != 0) throw new AssertionError(Arrays.toString(command) + "\n" + Files.readString(log));
    }

    private static List<Integer> execute(byte[] binary, int seed) {
        Memory memory = new Memory(binary);
        memory.writeWord(0x4002, 1);
        Registers registers = new Registers();
        Random random = new Random(seed);
        for (int bank = 0; bank < 4; bank++) for (int pair = 0; pair < 4; pair++) registers.setBankGP32(bank, pair, random.nextInt());
        registers.setXIX(random.nextInt()); registers.setXIY(random.nextInt()); registers.setXIZ(random.nextInt());
        registers.setPC(0x200000); registers.setXSP(0x6b00); memory.setStackBase(0x6b00);
        int done = 0x200000 + binary.length;
        memory.writeDword(0x6b00, done);
        Cpu cpu = new Cpu(memory, registers);
        int steps = 0;
        while (registers.getPC() != done && steps++ < 10000) cpu.step();
        require(registers.getPC() == done && registers.getXSP() == 0x6b04, "assembled balanced return");
        require(memory.readWord(0x4000) == 7, "assembled rebased argument/local access");
        List<Integer> result = new ArrayList<>(List.of(registers.getSR(), registers.getXIX(), registers.getXIY(), registers.getXIZ(), registers.getXSP()));
        for (int bank = 0; bank < 4; bank++) for (int pair = 0; pair < 4; pair++) result.add(registers.getBankGP32(bank, pair));
        return result;
    }
}
