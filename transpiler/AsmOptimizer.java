import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.OptionalLong;

/**
 * Standalone TLCS-900 assembly post-processor for transpiler-generated .asm files.
 *
 * Usage:
 *   java AsmOptimizer input.asm [output.asm] [-v] [--dry-run]
 *
 * With no output file: writes back to input.asm.
 * -v          : print per-pass statistics to stderr.
 * --dry-run   : print statistics but do not write any file.
 */
public class AsmOptimizer {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: java AsmOptimizer input.asm [output.asm] [-v] [--dry-run] [--pre-opt-output pre.asm]");
            System.exit(1);
        }

        String inputPath = null, outputPath = null, preOptOutput = null;
        boolean verbose = false, dryRun = false;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if ("-v".equals(a))          { verbose = true; }
            else if ("--dry-run".equals(a)) { dryRun = true; verbose = true; }
            else if ("--pre-opt-output".equals(a) && i + 1 < args.length) { preOptOutput = args[++i]; }
            else if (inputPath == null)  { inputPath = a; }
            else if (outputPath == null) { outputPath = a; }
        }
        if (inputPath == null) { System.err.println("No input file"); System.exit(1); }
        if (outputPath == null) outputPath = inputPath;

        List<String> lines = Files.readAllLines(Path.of(inputPath));
        int before = lines.size();

        if (preOptOutput != null && !dryRun) {
            Files.writeString(Path.of(preOptOutput), String.join("\n", lines) + "\n");
        }

        lines = runPasses(lines, verbose);

        int after = lines.size();
        if (verbose) {
            System.err.printf("[AsmOptimizer] total: %d → %d lines (-%d)%n",
                    before, after, before - after);
        }

        if (!dryRun) {
            Files.writeString(Path.of(outputPath), String.join("\n", lines) + "\n");
        }
    }

    static List<String> runPasses(List<String> lines, boolean verbose) {
        if (lines.contains("; experimental-loop-register-allocation") && protectedFunctions(lines).isEmpty()) {
            System.err.println("[AsmOptimizer] old experimental output without function fences: leaving unchanged");
            return new ArrayList<>(lines);
        }
        if (!protectedFunctions(lines).isEmpty())
            System.err.println("[AsmOptimizer] optimizing legacy code and global passes inside experimental functions");
        // global=true includes fenced functions; locality alone does not establish semantic safety.
        record Pass(String name, java.util.function.UnaryOperator<List<String>> fn, boolean global) {
            Pass(String name, java.util.function.UnaryOperator<List<String>> fn) { this(name, fn, false); }
        }
        List<Pass> passes = List.of(
            new Pass("removeUnreferencedLabels[1]",  AsmOptimizer::removeUnreferencedLabels),
            new Pass("peepholeEliminate",            AsmOptimizer::peepholeEliminate, true),
            new Pass("eliminateRedundantRegReload",  AsmOptimizer::eliminateRedundantRegReload),
            new Pass("foldAddSubToIncDec",           AsmOptimizer::foldAddSubToIncDec),
            new Pass("foldImmLoadThroughAcc",        AsmOptimizer::foldImmLoadThroughAcc),
            new Pass("eliminateDeadWaZeroBeforeExtz",AsmOptimizer::eliminateDeadWaZeroBeforeExtz),
            new Pass("eliminateDeadSlotStore",          AsmOptimizer::eliminateDeadSlotStore),
            new Pass("eliminateRedundantXorWW",      AsmOptimizer::eliminateRedundantXorWW),
            new Pass("eliminateByteMasksBeforeExtz", AsmOptimizer::eliminateByteMasksBeforeExtz),
            // mergeConsecutiveShifts: RE-ENABLED. Collapses runs of same-reg shifts
            // into minimal count-1..16 instructions (0=>16 nibble, emitted as literal 16;
            // asl rejects a literal 0). The earlier revert was an implementation defect:
            // the decomposer capped chunks at 8, so `sra 8; sra 8` (>>16) re-expanded to
            // the same two instrs and never merged, while the single-instr `sra 16` form
            // DOES render correctly on real MODE7 hardware (confirmed by hand-merge).
            // Flags are guarded dead before merging. See memory asl-shift-count-write-16-not-0.
            new Pass("mergeConsecutiveShifts",       AsmOptimizer::mergeConsecutiveShifts),
            new Pass("foldTrackAddressComputation",  AsmOptimizer::foldTrackAddressComputation),
            new Pass("foldColorSaveToXhl",           AsmOptimizer::foldColorSaveToXhl),
            new Pass("eliminateDeadPhiOutputStores",   AsmOptimizer::eliminateDeadPhiOutputStores),
            new Pass("foldColorAndBitTestToRegBit",  AsmOptimizer::foldColorAndBitTestToRegBit),
            new Pass("hoistBoundsCheckBeforeAccShift", AsmOptimizer::hoistBoundsCheckBeforeAccShift),
            new Pass("foldIncrCounterPhiRoundtrip",   AsmOptimizer::foldIncrCounterPhiRoundtrip),
            new Pass("removeRedundantExtz",          AsmOptimizer::removeRedundantExtz),
            new Pass("foldReloadIntoAluMemOperand",  AsmOptimizer::foldReloadIntoAluMemOperand),
            new Pass("foldPushImmediate",            AsmOptimizer::foldPushImmediate),
            new Pass("simplifyZeroCompareBranches",  AsmOptimizer::simplifyZeroCompareBranches),
            new Pass("foldStackLoadMovePairs",       AsmOptimizer::foldStackLoadMovePairs, true),
            new Pass("removeRedundantOrAfterAnd",    AsmOptimizer::removeRedundantOrAfterAndForZeroBranch),
            new Pass("removeUnreferencedLabels[2]",  AsmOptimizer::removeUnreferencedLabels),
            new Pass("eliminateRedundantRegLoads",   AsmOptimizer::eliminateRedundantRegLoads),
            new Pass("removeUnusedFrame",            AsmOptimizer::removeUnusedFrame),
            new Pass("foldXhlAccumulatorRoundtrip",  AsmOptimizer::foldXhlAccumulatorRoundtrip, true),
            new Pass("eliminateRegisterRoundtrip",   AsmOptimizer::eliminateRegisterRoundtrip, true),
            new Pass("foldXhlLoadAddRoundtrip",      AsmOptimizer::foldXhlLoadAddRoundtrip, true),
            new Pass("foldSpillReloadAdd",           AsmOptimizer::foldSpillReloadAdd, true),
            new Pass("foldXhlImmAddToMem",           AsmOptimizer::foldXhlImmAddToMem, true),
            new Pass("invertCondJumpPair",           AsmOptimizer::invertCondJumpPair, true),
            new Pass("foldColorAndBitTestToRegBit[2]", AsmOptimizer::foldColorAndBitTestToRegBit),
            new Pass("eliminateDeadPhiOutputStores[2]", AsmOptimizer::eliminateDeadPhiOutputStores),
            new Pass("eliminateOverwrittenSpills", AsmOptimizer::eliminateOverwrittenSpills),
            new Pass("forwardPhiAccumulators", AsmOptimizer::forwardPhiAccumulators),
            new Pass("forwardShiftedAccumulators", AsmOptimizer::forwardShiftedAccumulators),
            new Pass("rescheduleAccumulatorCoordinates", AsmOptimizer::rescheduleAccumulatorCoordinates),
            new Pass("scheduleAddressOffsets", AsmOptimizer::scheduleAddressOffsets),
            new Pass("eliminateOverwrittenSpills[2]", AsmOptimizer::eliminateOverwrittenSpills),
            new Pass("foldShiftedWordSubtract", AsmOptimizer::foldShiftedWordSubtract),
            new Pass("fuseZeroExtendedByteLoads", AsmOptimizer::fuseZeroExtendedByteLoads)
        );

        for (Pass p : passes) {
            int before = lines.size();
            lines = p.name().startsWith("removeUnreferencedLabels")
                ? removeLabelsOutsideProtected(lines, p.fn())
                : p.global()
                    ? p.fn().apply(lines)
                    : optimizeUnprotected(lines, p.fn());
            if (verbose && lines.size() != before) {
                System.err.printf("[AsmOptimizer] %-40s %d → %d (-%d)%n",
                        p.name(), before, lines.size(), before - lines.size());
            }
        }
        return lines;
    }

    static final String PROTECTED_BEGIN = "; experimental-register-function-begin ";
    static final String PROTECTED_END = "; experimental-register-function-end ";
    record ProtectedFunction(int start, int end, String name) {}

    static List<ProtectedFunction> protectedFunctions(List<String> lines) {
        List<ProtectedFunction> result = new ArrayList<>();
        int start = -1;
        String name = null;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("; experimental-register-function-")) {
                if (line.startsWith(PROTECTED_BEGIN)) {
                    String next = line.substring(PROTECTED_BEGIN.length());
                    if (start >= 0 || !next.matches("[\\w.$]+") || i + 1 >= lines.size()
                            || !lines.get(i + 1).equals("_" + next + ":"))
                        throw new IllegalArgumentException("invalid experimental function fence at line " + (i + 1));
                    start = i;
                    name = next;
                } else if (line.startsWith(PROTECTED_END)) {
                    if (start < 0 || !line.substring(PROTECTED_END.length()).equals(name))
                        throw new IllegalArgumentException("unmatched experimental function fence at line " + (i + 1));
                    result.add(new ProtectedFunction(start, i + 1, name));
                    start = -1;
                } else {
                    throw new IllegalArgumentException("invalid experimental function fence at line " + (i + 1));
                }
            }
        }
        if (start >= 0) throw new IllegalArgumentException("unterminated experimental function fence: " + name);
        if (!result.isEmpty() && !lines.contains("; experimental-loop-register-allocation"))
            throw new IllegalArgumentException("experimental function fences require the module marker");
        return result;
    }

    static List<String> optimizeUnprotected(List<String> lines,
                                            java.util.function.UnaryOperator<List<String>> pass) {
        List<ProtectedFunction> functions = protectedFunctions(lines);
        if (functions.isEmpty()) return pass.apply(lines);
        List<String> result = new ArrayList<>();
        int from = 0;
        for (ProtectedFunction function : functions) {
            if (from < function.start) result.addAll(pass.apply(new ArrayList<>(lines.subList(from, function.start))));
            result.addAll(lines.subList(function.start, function.end));
            from = function.end;
        }
        if (from < lines.size()) result.addAll(pass.apply(new ArrayList<>(lines.subList(from, lines.size()))));
        return result;
    }

    static List<String> removeLabelsOutsideProtected(List<String> lines,
                                                     java.util.function.UnaryOperator<List<String>> pass) {
        List<String> pinned = new ArrayList<>(lines);
        for (ProtectedFunction function : protectedFunctions(lines))
            for (String line : lines.subList(function.start, function.end)) {
                String code = line.split(";", 2)[0].strip();
                if (code.matches("\\.Lbb_[\\w.$]+:"))
                    pinned.add("\tjrl " + code.substring(0, code.length() - 1));
            }
        int extra = pinned.size() - lines.size();
        List<String> result = pass.apply(pinned);
        return new ArrayList<>(result.subList(0, result.size() - extra));
    }

    // =========================================================================
    // Pass: removeUnreferencedLabels
    // Drop .Lbb_* labels with zero references (enables other passes).
    // =========================================================================
    static List<String> removeUnreferencedLabels(List<String> lines) {
        Pattern pLabel  = Pattern.compile("^(\\.Lbb_\\w+):.*$");
        Pattern pAnyRef = Pattern.compile("(?<![\\w.])(\\.Lbb_\\w+)");
        Map<String, Integer> refCount = new HashMap<>();
        for (String line : lines) {
            Matcher mL = pLabel.matcher(line.trim());
            if (mL.matches()) {
                refCount.putIfAbsent(mL.group(1), 0);
                continue;
            }
            Matcher m = pAnyRef.matcher(line);
            while (m.find()) refCount.merge(m.group(1), 1, Integer::sum);
        }
        List<String> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            Matcher mL = pLabel.matcher(line.trim());
            if (mL.matches() && refCount.getOrDefault(mL.group(1), 0) <= 0) continue;
            out.add(line);
        }
        return out;
    }

    // =========================================================================
    // Pass: peepholeEliminate
    // Remove `jrl .Lbb_N` immediately before `.Lbb_N:` (fall-through jump removal).
    // =========================================================================
    static List<String> peepholeEliminate(List<String> lines) {
        Map<String, Integer> refCount = new HashMap<>();
        Pattern pJrl    = Pattern.compile("^\\s+jrl?\\s+(\\.Lbb_\\S+)\\s*$");
        Pattern pLabel  = Pattern.compile("^(\\.Lbb_\\S+):.*$");
        Pattern pAnyRef = Pattern.compile("(?<![\\w.])(\\.Lbb_\\w+)");
        for (String line : lines) {
            Matcher m = pAnyRef.matcher(line);
            while (m.find()) refCount.merge(m.group(1), 1, Integer::sum);
        }
        int n = lines.size();
        boolean[] keep = new boolean[n];
        Arrays.fill(keep, true);
        for (int i = 0; i < n - 1; i++) {
            if (!keep[i]) continue;
            Matcher mJ = pJrl.matcher(lines.get(i));
            if (!mJ.matches()) continue;
            String target = mJ.group(1);
            int next = i + 1;
            while (next < n && lines.get(next).isBlank()) next++;
            if (next >= n) continue;
            Matcher mL = pLabel.matcher(lines.get(next));
            if (!mL.matches() || !mL.group(1).equals(target)) continue;
            keep[i] = false;
            refCount.merge(target, -1, Integer::sum);
            if (refCount.getOrDefault(target, 0) <= 0) keep[next] = false;
        }
        List<String> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) if (keep[i]) result.add(lines.get(i));
        return mergeConsecutiveLabels(result);
    }

    static List<String> mergeConsecutiveLabels(List<String> lines) {
        Pattern pLabel = Pattern.compile("^(\\.Lbb_\\w+):$");
        Map<String, String> alias = new LinkedHashMap<>();
        int n = lines.size();
        for (int i = 0; i < n - 1; i++) {
            Matcher mC = pLabel.matcher(lines.get(i).trim());
            Matcher mN = pLabel.matcher(lines.get(i + 1).trim());
            if (mC.matches() && mN.matches()) alias.put(mC.group(1), mN.group(1));
        }
        if (alias.isEmpty()) return lines;
        for (String k : new ArrayList<>(alias.keySet())) {
            String v = alias.get(k);
            while (alias.containsKey(v)) v = alias.get(v);
            alias.put(k, v);
        }
        Pattern pAnyRef = Pattern.compile("(\\.Lbb_\\w+)");
        List<String> out = new ArrayList<>(n);
        for (String line : lines) {
            Matcher mL = pLabel.matcher(line.trim());
            if (mL.matches() && alias.containsKey(mL.group(1))) continue;
            Matcher mR = pAnyRef.matcher(line);
            StringBuffer sb = new StringBuffer();
            boolean changed = false;
            while (mR.find()) {
                String rep = alias.getOrDefault(mR.group(1), mR.group(1));
                mR.appendReplacement(sb, Matcher.quoteReplacement(rep));
                if (!rep.equals(mR.group(1))) changed = true;
            }
            mR.appendTail(sb);
            out.add(changed ? sb.toString() : line);
        }
        return out;
    }

    // =========================================================================
    // Pass: eliminateRedundantRegReload (intra-block CSE)
    //
    // Within a basic block (bounded by labels, branches, calls, ret):
    //   ld DST, SRC    ; first load
    //   <no clobber of DST or SRC>
    //   ld DST, SRC    ; drop this — DST already holds SRC
    //
    // DST/SRC may be register-to-register or stack-relative (xsp+N) loads.
    // For stack-relative sources, xsp delta is tracked.
    // Conservative: any unrecognized write to DST or SRC flushes the entry.
    // =========================================================================
    static List<String> eliminateRedundantRegReload(List<String> lines) {
        // Patterns
        Pattern pLd = Pattern.compile(
            "^\\s+ld\\s+(x?[a-z][a-z0-9]*),\\s*(.+?)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        Pattern pBranch = Pattern.compile(
            "^\\s+(?:jr|jrl|jp|call|calr|ret|reti|halt|djnz|swi)\\b",
            Pattern.CASE_INSENSITIVE);
        Pattern pStackAdj = Pattern.compile(
            "^\\s+add\\s+xsp,\\s*(-?(?:0x[0-9a-f]+|[0-9][0-9a-f]*h|[0-9]+))\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pPush = Pattern.compile("^\\s+push(?:w)?\\s+(\\S+)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pPop  = Pattern.compile("^\\s+pop\\s+(\\S+)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        // Writes to a register as FIRST operand: ld/add/sub/and/or/xor/lda/adc/sbc DST, ...
        Pattern pWritesDst = Pattern.compile(
            "^\\s+(?:ld|add|sub|adc|sbc|and|or|xor|lda|mul|muls|div|divs)\\s+(x?[a-z][a-z0-9]*),",
            Pattern.CASE_INSENSITIVE);
        // Writes to a register as SECOND operand: sla/srl/sra/sll/neg/extz/exts/inc/dec N, DST
        Pattern pWritesDst2 = Pattern.compile(
            "^\\s+(?:scc|sla|srl|sra|sll|neg|extz|exts|inc|dec)\\s+[^,]+,\\s*(x?[a-z][a-z0-9]*)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pXspLoad = Pattern.compile(
            "^\\(xsp\\+(-?\\d+)\\)$", Pattern.CASE_INSENSITIVE);

        // cache: key = "DST:normalizedSRC" -> spDeltaAtCapture
        // We only cache when the source is a simple register name or (xsp+N) form.
        // For (xsp+N): we store the original N and the spDelta at capture time.
        // On a later ld DST, (xsp+M): check if M == N + (currentDelta - captureDelta).

        // key format for reg-reg: "dst:src" (both lowercased)
        // key format for stack:   "dst:xsp+<originalN>"
        // value: spDelta at capture
        Map<String, Long> cache = new LinkedHashMap<>();
        long spDelta = 0;

        List<String> out = new ArrayList<>(lines);

        for (int i = 0; i < out.size(); i++) {
            String line = out.get(i);
            if (line == null) continue;
            String stripped = line.strip();

            // Blank / comment — skip
            if (stripped.isEmpty() || stripped.startsWith(";")) continue;

            // Label — flush cache (join point)
            if (pLabel.matcher(stripped).matches()) {
                cache.clear();
                spDelta = 0;
                continue;
            }

            // Branch/call/ret — flush cache
            if (pBranch.matcher(line).find()) {
                cache.clear();
                spDelta = 0;
                continue;
            }

            // push — adjusts xsp and may clobber pushed register in cache
            Matcher mPush = pPush.matcher(line);
            if (mPush.matches()) {
                String pushReg = mPush.group(1).toLowerCase(Locale.ROOT);
                long width = pushReg.startsWith("x") ? 4 : 2;
                // pushw is always 2 bytes (immediate push)
                if (line.trim().toLowerCase(Locale.ROOT).startsWith("pushw")) width = 2;
                spDelta -= width;
                // invalidate cached entries where DST or SRC == pushReg
                invalidateCacheForReg(cache, pushReg);
                continue;
            }

            // pop
            Matcher mPop = pPop.matcher(line);
            if (mPop.matches()) {
                String popReg = mPop.group(1).toLowerCase(Locale.ROOT);
                long width = popReg.startsWith("x") ? 4 : 2;
                spDelta += width;
                // pop writes to popReg — invalidate all entries mentioning it as dst OR src
                invalidateCacheForReg(cache, popReg);
                continue;
            }

            // add xsp, K
            Matcher mAdj = pStackAdj.matcher(line);
            if (mAdj.matches()) {
                try {
                    spDelta += parseSignedImm(mAdj.group(1));
                } catch (NumberFormatException e) {
                    cache.clear(); // conservative
                }
                continue;
            }

            // Check for ld DST, SRC
            Matcher mLd = pLd.matcher(line);
            if (mLd.matches()) {
                String dst = mLd.group(1).toLowerCase(Locale.ROOT);
                String src = mLd.group(2).trim().toLowerCase(Locale.ROOT);

                // Reject complex addressing except plain reg or (xsp+N)
                boolean isReg = src.matches("[a-z][a-z0-9]*");
                boolean isXspRel = src.matches("\\(xsp\\+\\d+\\)");
                boolean isGlobal = src.matches("\\(\\w+\\)");  // (_globalVar)

                if (isReg || isGlobal) {
                    String key = dst + ":" + src;
                    if (cache.containsKey(key)) {
                        // Redundant reload — drop it
                        out.set(i, null);
                        continue;
                    }
                    // Cache this load
                    cache.put(key, spDelta);
                    // Writing to dst invalidates any other cache entries with dst as key
                    invalidateCacheForDst(cache, dst);
                    cache.put(key, spDelta); // re-insert after invalidation
                } else if (isXspRel) {
                    Matcher mN = pXspLoad.matcher(src);
                    if (mN.matches()) {
                        long origN = Long.parseLong(mN.group(1));
                        String key = dst + ":xsp+" + origN;
                        // Check if we have a cached entry for (dst, some xsp slot)
                        // that resolves to the same physical address
                        String found = null;
                        for (Map.Entry<String, Long> e : cache.entrySet()) {
                            if (!e.getKey().startsWith(dst + ":xsp+")) continue;
                            String cachedSrc = e.getKey().substring(dst.length() + 1);
                            if (!cachedSrc.startsWith("xsp+")) continue;
                            long cachedOrigN = Long.parseLong(cachedSrc.substring(4));
                            long cacheDelta = e.getValue();
                            // Physical address at time of this load: cachedOrigN + cacheDelta
                            // Physical address now: origN + spDelta
                            if (cachedOrigN + cacheDelta == origN + spDelta) {
                                found = e.getKey();
                                break;
                            }
                        }
                        if (found != null) {
                            out.set(i, null);
                            continue;
                        }
                        invalidateCacheForDst(cache, dst);
                        cache.put(key, spDelta);
                    }
                } else {
                    // Complex source — still invalidate dst
                    invalidateCacheForDst(cache, dst);
                }
                continue;
            }

            // Block-move instructions (ldir/ldirw/lddr/lddrw/ldi/ldib/etc.) clobber their
            // operand register pairs and bc (loop counter) — flush entire cache conservatively.
            if (stripped.toLowerCase(Locale.ROOT).matches("(?:ldir|lddr|ldiw|lddw|ldirw|lddrw|ldib|ldirb|lddb|lddrb|ldi|ldd)\\b.*")) {
                cache.clear();
                continue;
            }

            // Any other write — invalidate cache entries for any register written
            Matcher mW = pWritesDst.matcher(line);
            if (mW.find()) {
                String writtenReg = mW.group(1).toLowerCase(Locale.ROOT);
                invalidateCacheForReg(cache, writtenReg);
            }
            // Shifts and unary ops write to the SECOND operand: sla N, REG
            Matcher mW2 = pWritesDst2.matcher(line);
            if (mW2.find()) {
                String writtenReg = mW2.group(1).toLowerCase(Locale.ROOT);
                invalidateCacheForReg(cache, writtenReg);
            }

            // If line stores to an xsp-relative address, invalidate stack-sourced cache entries
            // for the specific slot (and conservatively all if slot can't be determined).
            // Note: stores to (xhl+N) or (xde+N) do NOT affect (xsp+N) slots.
            if (line.contains("ld (xsp+") || line.contains("ld (XSP+")) {
                // Specific slot store: ld (xsp+N), R  → invalidate entries sourced from that slot
                Matcher mSlotStore = Pattern.compile(
                    "^\\s+ld\\s+\\(xsp\\+(\\d+)\\),\\s*(\\S+)\\s*(?:;.*)?$",
                    Pattern.CASE_INSENSITIVE).matcher(line);
                if (mSlotStore.matches()) {
                    long storedN = Long.parseLong(mSlotStore.group(1));
                    final long spDeltaSnap = spDelta;
                    // Invalidate any cache entry whose physical address resolves to this slot
                    cache.entrySet().removeIf(e -> {
                        String key = e.getKey();
                        int colon = key.indexOf(':');
                        if (colon < 0) return false;
                        String src = key.substring(colon + 1);
                        if (!src.startsWith("xsp+")) return false;
                        long cachedOrigN = Long.parseLong(src.substring(4));
                        long cacheDelta = e.getValue();
                        return cachedOrigN + cacheDelta == storedN + spDeltaSnap;
                    });
                } else {
                    // Unknown xsp store form — conservatively flush all stack entries
                    cache.entrySet().removeIf(e -> e.getKey().contains(":xsp+"));
                }
            }
            // Stores to (_global) invalidate global-sourced entries
            if (line.matches("^\\s+ld\\s+\\(_\\w+\\),.*$")) {
                cache.entrySet().removeIf(e -> {
                    String src = e.getKey().substring(e.getKey().indexOf(':') + 1);
                    return src.startsWith("(");
                });
            }
        }

        out.removeIf(Objects::isNull);
        return out;
    }

    private static void invalidateCacheForDst(Map<String, Long> cache, String dst) {
        // Remove all entries where the destination overlaps with dst (aliases).
        Set<String> aliases = registerAliases(dst);
        cache.entrySet().removeIf(e -> {
            int colon = e.getKey().indexOf(':');
            return colon >= 0 && aliases.contains(e.getKey().substring(0, colon));
        });
    }

    private static void invalidateCacheForReg(Map<String, Long> cache, String reg) {
        // Remove all entries where dst or src mentions reg (and their sub-registers)
        Set<String> aliases = registerAliases(reg);
        cache.entrySet().removeIf(e -> {
            String key = e.getKey();
            int colon = key.indexOf(':');
            if (colon < 0) return false;
            String d = key.substring(0, colon);
            String s = key.substring(colon + 1);
            return aliases.contains(d) || aliases.contains(s);
        });
    }

    // Returns the set of register names that overlap with the given register.
    // E.g. xwa overlaps wa, w, a; xhl overlaps hl, h, l; etc.
    private static Set<String> registerAliases(String reg) {
        reg = reg.toLowerCase(Locale.ROOT);
        // Banked registers only alias themselves (xbc1, etc.)
        if (reg.endsWith("1") || reg.endsWith("2")) return Set.of(reg);
        // Primary 32-bit and their sub-registers
        Map<String, Set<String>> groups = Map.of(
            "xwa", Set.of("xwa", "wa", "w", "a"),
            "xhl", Set.of("xhl", "hl", "h", "l"),
            "xbc", Set.of("xbc", "bc", "b", "c"),
            "xde", Set.of("xde", "de", "d", "e"),
            "xix", Set.of("xix", "ix"),
            "xiy", Set.of("xiy", "iy"),
            "xiz", Set.of("xiz", "iz"),
            "xsp", Set.of("xsp", "sp")
        );
        // Find which group this register belongs to
        for (Set<String> group : groups.values()) {
            if (group.contains(reg)) return group;
        }
        return Set.of(reg);
    }

    // =========================================================================
    // Pass: mergeConsecutiveShifts
    //
    // Collapse runs of consecutive sla/srl/sra N, REG into a minimal sequence
    // of ≤8-count shifts (TLCS-900 max per instruction).
    //
    // Key wins in _update:
    //   sla 7 + sla 7 + sla 2 → sla 8 + sla 8  (saves 1 instruction, x8 in loop)
    //   srl 7 + srl 1          → srl 8           (saves 1 instruction, x8 in loop)
    //   srl 7 + srl 7 + srl 2  → srl 8 + srl 8  (saves 1 instruction)
    //
    // Safety: intermediate flags must be dead (checked via flagsAreDeadAfter).
    // Barrier: label, branch, call, ret, any instruction touching REG.
    // =========================================================================
    static List<String> mergeConsecutiveShifts(List<String> lines) {
        Pattern pShift = Pattern.compile(
            "^(\\s+)(sla|srl|sra)\\s+([1-9]|[1-9][0-9]*),\\s*(x?[a-z][a-z0-9]*)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");

        List<String> out = new ArrayList<>(lines);
        boolean[] drop = new boolean[out.size()];

        int i = 0;
        while (i < out.size()) {
            if (drop[i] || out.get(i) == null) { i++; continue; }
            Matcher m0 = pShift.matcher(out.get(i));
            if (!m0.matches()) { i++; continue; }

            String indent = m0.group(1);
            String mnem   = m0.group(2).toLowerCase(Locale.ROOT);
            String reg    = m0.group(4).toLowerCase(Locale.ROOT);
            int total = Integer.parseInt(m0.group(3));

            // Collect the run: consecutive shifts with same mnemonic and register,
            // with no flag consumer between them.
            List<Integer> runIdx = new ArrayList<>();
            runIdx.add(i);

            for (int j = i + 1; j < out.size(); j++) {
                if (drop[j] || out.get(j) == null) continue;
                String l = out.get(j).strip();
                if (l.isEmpty() || l.startsWith(";")) continue;
                // Label or branch terminates the run
                if (pLabel.matcher(l).matches()) break;
                if (l.toLowerCase(Locale.ROOT).matches("(?:jr|jrl|jp|call|calr|ret|reti|halt|djnz|swi).*")) break;
                // Check if this is another shift of same kind+reg
                Matcher mn = pShift.matcher(out.get(j));
                if (mn.matches()
                        && mn.group(2).equalsIgnoreCase(mnem)
                        && mn.group(4).equalsIgnoreCase(reg)) {
                    // Check that intermediate flags are dead at each step
                    // (the shift at runIdx.last sets flags; we check they're dead before j)
                    int prevRunLine = runIdx.get(runIdx.size() - 1);
                    if (!flagsAreDeadAfterLine(out, prevRunLine, pLabel)) break;
                    total += Integer.parseInt(mn.group(3));
                    runIdx.add(j);
                    continue;
                }
                // Any other instruction touching any alias of reg terminates the run.
                // Use word-boundary-like check: reg must appear as a whole token.
                // Check all aliases (e.g. wa → {wa, w, a}).
                boolean touchesReg = false;
                for (String alias : registerAliases(reg)) {
                    if (Pattern.compile("(?<![\\w.])"+Pattern.quote(alias)+"(?![\\w.])",
                            Pattern.CASE_INSENSITIVE).matcher(l).find()) {
                        touchesReg = true; break;
                    }
                }
                if (touchesReg) break;
                // flag consumers also terminate
                String t = l.toLowerCase(Locale.ROOT);
                if (asmConsumesFlags(t)) break;
            }

            // Only rewrite if run has ≥2 instructions
            if (runIdx.size() < 2) { i++; continue; }

            // The final merged shift produces a different carry flag than the original last
            // shift (carry = different bit position). Only merge if flags are dead after the
            // last run instruction (i.e., no consumer of the post-shift flags).
            int lastRunLine = runIdx.get(runIdx.size() - 1);
            if (!flagsAreDeadAfterLine(out, lastRunLine, pLabel)) { i++; continue; }

            // Decompose total into <=16-count instructions. TLCS-900 SLA/SRL/SRA
            // take a 4-bit immediate count encoded as 1..15 and 0 => 16, so a SINGLE
            // instruction can shift by any amount 1..16 (`sra 16, xde` = EA ED 00,
            // `sra 8, xde` = EA ED 08, verified with asl -L + p2bin on as/bin).
            //
            // CRITICAL: the assembler range-checks the SOURCE literal as 1..16 and
            // REJECTS a literal `0` ("range overflow"). To emit the all-zeros count
            // nibble you must write the mnemonic count 16, NOT 0. So `sra 8; sra 8`
            // (>>16) folds to `sra 16` — never `sra 0`, which would not assemble.
            // See memory asl-shift-count-write-16-not-0. The hand-merged `sra 16`
            // form was confirmed to render correctly on real MODE7 hardware.
            List<String> replacement = new ArrayList<>();
            int rem = total;
            while (rem > 0) {
                int chunk = Math.min(rem, 16);
                replacement.add(indent + mnem + " " + chunk + ", " + reg);
                rem -= chunk;
            }

            // If the count of replacements >= run size, no net saving (can happen if total > 8*8)
            if (replacement.size() >= runIdx.size()) { i++; continue; }

            // Drop all run lines, emit new instructions at the first line
            for (int idx : runIdx) drop[idx] = true;
            // Insert replacement lines at position of first run line
            // We'll reconstruct below; use the first line slot for the first replacement,
            // and mark others null to be dropped, then re-insert remainder.
            // Simpler: rebuild via null + insertion list approach.
            out.set(runIdx.get(0), String.join("\n", replacement));
            for (int k = 1; k < runIdx.size(); k++) out.set(runIdx.get(k), null);

            i = runIdx.get(0) + 1;
        }

        // Expand any multi-line entries and filter nulls
        List<String> result = new ArrayList<>();
        for (String s : out) {
            if (s == null) continue;
            if (s.contains("\n")) {
                for (String part : s.split("\n", -1)) result.add(part);
            } else {
                result.add(s);
            }
        }
        return result;
    }

    // =========================================================================
    // Pass: removeRedundantExtz
    //
    // Drop consecutive duplicate `extz REG` / `extz REG` (idempotent).
    // =========================================================================
    static List<String> removeRedundantExtz(List<String> lines) {
        Pattern pExtz  = Pattern.compile("^\\s+extz\\s+(x?[a-z][a-z0-9]*)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 1 < out.size(); i++) {
            if (out.get(i) == null) continue;
            Matcher m1 = pExtz.matcher(out.get(i));
            if (!m1.matches()) continue;
            String reg = m1.group(1).toLowerCase(Locale.ROOT);
            // Find next significant line
            int j = i + 1;
            while (j < out.size()) {
                if (out.get(j) == null) { j++; continue; }
                String s = out.get(j).strip();
                if (s.isEmpty() || s.startsWith(";")) { j++; continue; }
                break;
            }
            if (j >= out.size()) continue;
            if (pLabel.matcher(out.get(j).strip()).matches()) continue;
            Matcher m2 = pExtz.matcher(out.get(j));
            if (m2.matches() && m2.group(1).equalsIgnoreCase(reg)) {
                out.set(j, null);
            }
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Helper: word/dword register test for the logical mem-source size guard.
    // Byte registers are a single letter a..l, optionally bank-suffixed
    // (a, b1, l2, ...). Everything else the ALU regex admits (wa.., bc.., x..)
    // is word or dword width, where the sim decodes logical R,(mem) forms.
    // =========================================================================
    private static final Pattern BYTE_REG =
        Pattern.compile("^[a-l]\\d?$", Pattern.CASE_INSENSITIVE);

    static boolean isWordOrDwordReg(String reg) {
        return !BYTE_REG.matcher(reg).matches();
    }

    // =========================================================================
    // Pass: foldReloadIntoAluMemOperand
    //
    // ld xwa,(xsp+N) ; add R, xwa  →  add R, (xsp+N)  (drop the reload)
    // Ported from Ir2Tlcs900.java.
    //
    // Covers both the arithmetic (add/adc/sub/sbc) and logical (and/or/xor)
    // R,(mem) source forms. Operand order is preserved (R OP xwa → R OP (mem)),
    // so this fold does not rely on commutativity. The logical R,(mem) forms
    // exist on TLCS-900 (word/dword prefix, op2 0xC0/0xD0/0xE0) and the sim
    // decodes them for msz>=2 — see memory [[mode7-logical-mem-operand-alu]].
    // =========================================================================
    static List<String> foldReloadIntoAluMemOperand(List<String> lines) {
        Pattern pLoad = Pattern.compile(
            "^\\s+ld\\s+(xwa|wa),\\s*(\\(xsp\\+(\\d+)\\))\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pAlu = Pattern.compile(
            "^\\s+(add|adc|sub|sbc|and|or|xor)\\s+(x?[a-z]+\\d?),\\s*(xwa|wa)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        Pattern pTouchesAcc = Pattern.compile(
            "(?<![\\w.])(xwa|wa|w|a)(?![\\w.])", Pattern.CASE_INSENSITIVE);
        Pattern pStackDisturb = Pattern.compile(
            "^\\s+(push|pop)\\b|(?<![\\w.])xsp(?![\\w.])", Pattern.CASE_INSENSITIVE);
        Pattern pMemStore = Pattern.compile("^\\s+ld\\s+\\(", Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) continue;
            Matcher mLoad = pLoad.matcher(out.get(i));
            if (!mLoad.matches()) continue;
            String acc   = mLoad.group(1).toLowerCase(Locale.ROOT);
            String memOp = mLoad.group(2);
            long n = Long.parseLong(mLoad.group(3));
            if (n < 0 || n > 0x7FFF) continue;

            int consumerAt = -1;
            boolean bail = false;
            for (int j = i + 1; j < out.size(); j++) {
                if (out.get(j) == null) continue;
                String core = out.get(j).split(";", 2)[0];
                if (core.trim().isEmpty()) continue;
                if (pLabel.matcher(core.trim()).matches()) { bail = true; break; }
                String lc = core.trim().toLowerCase(Locale.ROOT);
                if (lc.startsWith("jr") || lc.startsWith("jp") || lc.startsWith("jrl")
                        || lc.startsWith("call") || lc.startsWith("calr") || lc.startsWith("ret")
                        || lc.startsWith("djnz") || lc.startsWith("reti") || lc.startsWith("halt")
                        || lc.startsWith("swi")) { bail = true; break; }

                Matcher mAlu = pAlu.matcher(core);
                if (mAlu.matches() && mAlu.group(3).equalsIgnoreCase(acc)) {
                    // The logical R,(mem) source forms are decoded by the sim
                    // only at word/dword size (msz>=2); a byte-size logical
                    // mem-source uses the 8F prefix shared with bit-ops/JP and
                    // must not be emitted. Restrict and/or/xor to word (wa..)
                    // or dword (x..) destinations. Arithmetic ops are unaffected.
                    String aluOp  = mAlu.group(1).toLowerCase(Locale.ROOT);
                    String aluDst = mAlu.group(2).toLowerCase(Locale.ROOT);
                    if (registerAliases(acc).contains(aluDst)) { bail = true; break; }
                    boolean logical = aluOp.equals("and") || aluOp.equals("or") || aluOp.equals("xor");
                    if (logical && !isWordOrDwordReg(aluDst)) { bail = true; break; }
                    consumerAt = j; break;
                }
                if (pStackDisturb.matcher(core).find()) { bail = true; break; }
                if (pMemStore.matcher(core).find())     { bail = true; break; }
                if (pTouchesAcc.matcher(core).find())   { bail = true; break; }
            }

            if (bail || consumerAt < 0) continue;
            if (!accDeadAfter(out, consumerAt, acc, pLabel)) continue;

            String consumer = out.get(consumerAt);
            String repl = consumer.replaceFirst(
                ",\\s*(?i:" + acc + ")(?![\\w.])",
                Matcher.quoteReplacement(", " + memOp));
            out.set(consumerAt, repl);
            out.set(i, null);
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: foldPushImmediate
    //
    // ld wa, K ; push wa  →  pushw K   (when WA dead after push and 0 <= K <= 0xFFFF)
    // Ported from Ir2Tlcs900.java.
    // =========================================================================
    static List<String> foldPushImmediate(List<String> lines) {
        Pattern pLdWa = Pattern.compile(
            "^\\s+ld\\s+wa,\\s*(-?(?:0x[0-9a-f]+|[0-9][0-9a-f]*h|[0-9]+))\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pPushWa = Pattern.compile("^\\s+push\\s+wa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLabel  = Pattern.compile("^[.\\w].*:.*$");
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 1 < out.size(); i++) {
            if (out.get(i) == null || out.get(i + 1) == null) continue;
            Matcher mLd = pLdWa.matcher(out.get(i));
            if (!mLd.matches()) continue;
            if (!pPushWa.matcher(out.get(i + 1)).matches()) continue;
            long v;
            try { v = parseSignedImm(mLd.group(1)); } catch (NumberFormatException e) { continue; }
            if (v < 0 || v > 0xFFFF) continue;
            boolean deadViaCall = false;
            for (int k = i + 2; k < out.size(); k++) {
                if (out.get(k) == null) continue;
                String c = out.get(k).split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (c.isEmpty()) continue;
                deadViaCall = c.startsWith("call") || c.startsWith("calr");
                break;
            }
            if (!deadViaCall && !accDeadAfter(out, i + 1, "wa", pLabel)) continue;
            out.set(i, null);
            out.set(i + 1, "\tpushw " + mLd.group(1));
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: simplifyZeroCompareBranches
    //
    // cp {a|wa|xwa}, 0 ; jr Z/NZ, L  →  or {reg},{reg} ; jr Z/NZ, L
    // Ported from Ir2Tlcs900.java.
    // =========================================================================
    static List<String> eliminateByteMasksBeforeExtz(List<String> lines) {
        Pattern mask = Pattern.compile("^\\s+and\\s+wa,\\s*(?:0ffh|255|0xff)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern extend = Pattern.compile("^\\s+extz\\s+wa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern label = Pattern.compile("^[.\\w].*:.*$");
        List<String> out = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            int flagsStart = index + 1;
            if (index + 2 < lines.size() && lines.get(index + 2).matches("(?i)^\\s+extz\\s+xwa\\s*(?:;.*)?$"))
                flagsStart++;
            if (index + 1 < lines.size() && mask.matcher(lines.get(index)).matches()
                    && extend.matcher(lines.get(index + 1)).matches()
                    && flagsAreDeadAfterLine(lines, flagsStart, label)) continue;
            out.add(lines.get(index));
        }
        return out;
    }

    static List<String> simplifyZeroCompareBranches(List<String> lines) {
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        Pattern pBr = Pattern.compile("^\\s+jr(?:l)?\\s+(Z|NZ),\\s*(\\S+)\\s*(?:;.*)?$");
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            String raw = out.get(i);
            String core = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (!core.startsWith("cp ")) continue;
            String ops = core.substring(3).trim();
            int comma = ops.indexOf(',');
            if (comma < 0) continue;
            String lhs = ops.substring(0, comma).trim();
            String rhs = ops.substring(comma + 1).trim();
            if (!(lhs.equals("a") || lhs.equals("wa") || lhs.equals("xwa"))) continue;
            if (!(rhs.equals("0") || rhs.equals("0h") || rhs.equals("00h")
                    || rhs.equals("0000h") || rhs.equals("00000000h"))) continue;
            int j = i + 1;
            while (j < out.size() && out.get(j).matches("^\\s*(;.*)?$")) j++;
            if (j >= out.size()) continue;
            if (pLabel.matcher(out.get(j).trim()).matches()) continue;
            if (!pBr.matcher(out.get(j)).matches()) continue;
            out.set(i, "\tor " + lhs + ", " + lhs);
        }
        return out;
    }

    // =========================================================================
    // Utilities (ported from Ir2Tlcs900.java)
    // =========================================================================

    static boolean flagsAreDeadAfterLine(List<String> lines, int startIdx, Pattern pLabel) {
        for (int k = startIdx + 1; k < lines.size(); k++) {
            String raw = lines.get(k);
            if (raw == null) continue;
            if (raw.matches("^\\s*(;.*)?$")) continue;
            String t = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (pLabel.matcher(t).matches()) return false;
            if (t.matches(".*\\b(?:sr|f|rfp)\\b.*")) return false;
            if (asmConsumesFlags(t)) return false;
            if (asmClobbersFlags(t)) return true;
            if (asmNeutralForFlags(t)) continue;
            return false;
        }
        return false;
    }

    static boolean accDeadAfter(List<String> out, int consumerAt, String acc, Pattern pLabel) {
        boolean pendA = true, pendW = true, pendU = acc.equalsIgnoreCase("xwa");
        Pattern pMentionAcc = Pattern.compile(
            "(?<![\\w.])(xwa|wa|w|a)(?![\\w.])", Pattern.CASE_INSENSITIVE);
        Pattern pLdXwa = Pattern.compile("^\\s+ld\\s+xwa,\\s*(.*)$", Pattern.CASE_INSENSITIVE);
        Pattern pLdWa  = Pattern.compile("^\\s+ld\\s+wa,\\s*(.*)$",  Pattern.CASE_INSENSITIVE);
        Pattern pLdA   = Pattern.compile("^\\s+ld\\s+a,\\s*(.*)$",   Pattern.CASE_INSENSITIVE);
        Pattern pLdW   = Pattern.compile("^\\s+ld\\s+w,\\s*(.*)$",   Pattern.CASE_INSENSITIVE);
        Pattern pXorWW = Pattern.compile("^\\s+xor\\s+w,\\s*w\\s*$", Pattern.CASE_INSENSITIVE);
        Pattern pExtWa = Pattern.compile("^\\s+ext[zs]\\s+(?:wa|xwa)\\s*$", Pattern.CASE_INSENSITIVE);

        for (int k = consumerAt + 1; k < out.size(); k++) {
            if (out.get(k) == null) continue;
            String core = out.get(k).split(";", 2)[0];
            if (core.trim().isEmpty()) continue;
            if (pLabel.matcher(core.trim()).matches()) return false;
            String lc = core.trim().toLowerCase(Locale.ROOT);
            if (lc.startsWith("jr") || lc.startsWith("jp") || lc.startsWith("jrl")
                    || lc.startsWith("call") || lc.startsWith("calr") || lc.startsWith("ret")
                    || lc.startsWith("djnz") || lc.startsWith("reti") || lc.startsWith("halt")
                    || lc.startsWith("swi")) return false;

            Matcher m;
            if ((m = pLdXwa.matcher(core)).matches()) {
                if (!pMentionAcc.matcher(m.group(1)).find()) { pendA = pendW = pendU = false; }
                else return false;
            } else if ((m = pLdWa.matcher(core)).matches()) {
                if (!pMentionAcc.matcher(m.group(1)).find()) { pendA = pendW = false; }
                else return false;
            } else if ((m = pLdA.matcher(core)).matches()) {
                if (!pMentionAcc.matcher(m.group(1)).find()) { pendA = false; }
                else return false;
            } else if ((m = pLdW.matcher(core)).matches()) {
                if (!pMentionAcc.matcher(m.group(1)).find()) { pendW = false; }
                else return false;
            } else if (pXorWW.matcher(core).matches()) {
                pendW = false;
            } else if (pExtWa.matcher(core).matches()) {
                if (pendA || pendW) return false;
                pendU = false;
            } else if (pMentionAcc.matcher(core).find()) {
                return false;
            }
            if (!pendA && !pendW && !pendU) return true;
        }
        return false;
    }

    static boolean asmConsumesFlags(String t) {
        if (t.startsWith("scc ")) return true;
        if (t.startsWith("adc ") || t.startsWith("sbc ")) return true;
        if (t.startsWith("ret ")) {
            String cc = t.substring(4).trim();
            return !cc.isEmpty();
        }
        Matcher mJ = Pattern.compile("^(?:jr|jrl|jp)\\s+([a-z]+)\\s*,").matcher(t);
        if (mJ.find()) {
            String cc = mJ.group(1).toUpperCase(Locale.ROOT);
            Set<String> cond = Set.of("Z","NZ","LT","GE","GT","LE","C","NC","UGT","ULE","ULT","UGE","EQ","NE");
            return cond.contains(cc);
        }
        return false;
    }

    static boolean asmClobbersFlags(String t) {
        return t.startsWith("cp ")
            || t.startsWith("add ") || t.startsWith("sub ")
            || t.startsWith("and ") || t.startsWith("or ") || t.startsWith("xor ")
            || t.startsWith("sla ") || t.startsWith("sra ") || t.startsWith("srl ")
            || t.startsWith("neg ")
            || t.startsWith("bit ");
    }

    static boolean asmNeutralForFlags(String t) {
        if (t.startsWith("ld ") || t.startsWith("lda ")) return true;
        if (t.startsWith("push ") || t.startsWith("pop ") || t.startsWith("pushw ")) return true;
        if (t.equals("di") || t.equals("ei")) return true;
        return false;
    }

    static long parseSignedImm(String s) {
        s = s.trim();
        boolean neg = s.startsWith("-");
        if (neg) s = s.substring(1);
        long v;
        if (s.startsWith("0x") || s.startsWith("0X")) v = Long.parseLong(s.substring(2), 16);
        else if (s.endsWith("h") || s.endsWith("H"))  v = Long.parseLong(s.substring(0, s.length() - 1), 16);
        else v = Long.parseLong(s);
        return neg ? -v : v;
    }

    static OptionalLong parseAsmImmediate(String s) {
        try { return OptionalLong.of(parseSignedImm(s)); }
        catch (NumberFormatException e) { return OptionalLong.empty(); }
    }

    // =========================================================================
    // Pass: foldStackLoadMovePairs
    //
    // ld xwa, (xsp+N) ; ld xhl|xbc|xde, xwa  →  ld xhl|xbc|xde, (xsp+N)
    // ld  wa, (xsp+N) ; ld  hl| bc| de,  wa  →  ld  hl| bc| de, (xsp+N)
    // Ported from Ir2Tlcs900.java.
    // =========================================================================
    static List<String> foldStackLoadMovePairs(List<String> lines) {
        Pattern pLdWaStack  = Pattern.compile("^\\s+ld\\s+wa,\\s*(\\(xsp\\+[^)]+\\))\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pLdXwaStack = Pattern.compile("^\\s+ld\\s+xwa,\\s*(\\(xsp\\+[^)]+\\))\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pLdFromWa   = Pattern.compile("^\\s+ld\\s+(hl|bc|de),\\s*wa\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pLdFromXwa  = Pattern.compile("^\\s+ld\\s+(xhl|xbc|xde|xiz|xiy|xix),\\s*xwa\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");

        List<String> out = new ArrayList<>(lines);
        boolean[] drop = new boolean[out.size()];
        for (int i = 0; i < out.size(); i++) {
            if (drop[i]) continue;
            Matcher mWa  = pLdWaStack.matcher(out.get(i));
            Matcher mXwa = pLdXwaStack.matcher(out.get(i));
            if (!mWa.matches() && !mXwa.matches()) continue;
            int j = i + 1;
            while (j < out.size() && out.get(j).matches("^\\s*(;.*)?$")) j++;
            if (j >= out.size() || pLabel.matcher(out.get(j).trim()).matches()) continue;
            if (mWa.matches()) {
                Matcher mDst = pLdFromWa.matcher(out.get(j));
                if (mDst.matches() && accDeadAfter(out, j, "wa", pLabel)) {
                    out.set(i, "\tld " + mDst.group(1).toLowerCase(Locale.ROOT) + ", " + mWa.group(1));
                    drop[j] = true;
                }
            } else {
                Matcher mDst = pLdFromXwa.matcher(out.get(j));
                if (mDst.matches() && accDeadAfter(out, j, "xwa", pLabel)) {
                    out.set(i, "\tld " + mDst.group(1).toLowerCase(Locale.ROOT) + ", " + mXwa.group(1));
                    drop[j] = true;
                }
            }
        }
        List<String> shrunk = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) if (!drop[i]) shrunk.add(out.get(i));
        return shrunk;
    }

    // =========================================================================
    // Pass: removeRedundantOrAfterAndForZeroBranch
    //
    // and REG, IMM ; or REG, REG ; jr Z/NZ, L  →  drop the `or REG, REG`
    // `and` already sets Z/NZ, so the self-test OR is redundant.
    // Also: and wa, IMM8 ; or xwa, xwa when upper16(xwa) is provably zero.
    // Ported from Ir2Tlcs900.java.
    // =========================================================================
    static List<String> removeRedundantOrAfterAndForZeroBranch(List<String> lines) {
        Pattern pAnd   = Pattern.compile("^\\s+and\\s+(a|wa|xwa),\\s*([^\\s;]+)\\s*(?:;.*)?$",
                                         Pattern.CASE_INSENSITIVE);
        Pattern pOrSelf = Pattern.compile("^\\s+or\\s+(a|wa|xwa),\\s*(a|wa|xwa)\\s*(?:;.*)?$",
                                          Pattern.CASE_INSENSITIVE);
        Pattern pJ     = Pattern.compile("^\\s+(?:jr|jrl|jp)\\s+([a-z]+)\\s*,\\s*([^;]+?)\\s*(?:;.*)?$",
                                         Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");

        List<String> out = new ArrayList<>(lines);
        boolean[] xwaUpper16Zero = computeXwaUpper16ZeroBefore(out);
        boolean[] drop = new boolean[out.size()];

        for (int i = 0; i < out.size(); i++) {
            Matcher mAnd = pAnd.matcher(out.get(i));
            if (!mAnd.matches()) continue;
            String andReg = mAnd.group(1).toLowerCase(Locale.ROOT);
            int j = i + 1;
            while (j < out.size() && out.get(j).matches("^\\s*(;.*)?$")) j++;
            if (j >= out.size() || pLabel.matcher(out.get(j).trim()).matches()) continue;
            Matcher mOr = pOrSelf.matcher(out.get(j));
            if (!mOr.matches()) continue;
            String lhs = mOr.group(1).toLowerCase(Locale.ROOT);
            String rhs = mOr.group(2).toLowerCase(Locale.ROOT);
            if (!lhs.equals(rhs)) continue;
            boolean sameReg   = lhs.equals(andReg);
            boolean waToXwa   = andReg.equals("wa") && lhs.equals("xwa") && xwaUpper16Zero[i];
            if (!(sameReg || waToXwa)) continue;
            int k = j + 1;
            while (k < out.size() && out.get(k).matches("^\\s*(;.*)?$")) k++;
            if (k >= out.size() || pLabel.matcher(out.get(k).trim()).matches()) continue;
            Matcher mJ = pJ.matcher(out.get(k));
            if (!mJ.matches()) continue;
            String cc = mJ.group(1).toUpperCase(Locale.ROOT);
            if (!(cc.equals("Z") || cc.equals("NZ") || cc.equals("EQ") || cc.equals("NE"))) continue;
            drop[j] = true;
        }

        List<String> shrunk = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) if (!drop[i]) shrunk.add(out.get(i));
        return shrunk;
    }

    // =========================================================================
    // Pass: foldAddSubToIncDec
    //
    // add R, N  →  inc N, R   (N = 1..8, carry flag dead after)
    // sub R, N  →  dec N, R   (N = 1..8, carry flag dead after)
    //
    // TLCS-900: INC/DEC preserve carry; ADD/SUB set it.
    // Only applied when carry is dead (clobbered before next consumer).
    // =========================================================================
    static List<String> foldAddSubToIncDec(List<String> lines) {
        Pattern pAdd = Pattern.compile(
            "^(\\s+)(add|sub)\\s+(x?[a-z][a-z0-9]*),\\s*([1-8])\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            String line = out.get(i);
            if (line == null) continue;
            Matcher m = pAdd.matcher(line);
            if (!m.matches()) continue;
            String indent = m.group(1);
            String op     = m.group(2).toLowerCase(Locale.ROOT);
            String reg    = m.group(3).toLowerCase(Locale.ROOT);
            int    n      = Integer.parseInt(m.group(4));

            // Check carry dead after this instruction
            if (!carryDeadAfter(out, i, pLabel)) continue;

            String mnem = op.equals("add") ? "inc" : "dec";
            out.set(i, indent + mnem + " " + n + ", " + reg);
        }
        return out;
    }

    // Returns true if the carry flag is provably dead (clobbered before any consumer)
    // starting from the line AFTER startIdx.
    private static boolean carryDeadAfter(List<String> lines, int startIdx, Pattern pLabel) {
        for (int k = startIdx + 1; k < lines.size(); k++) {
            String raw = lines.get(k);
            if (raw == null) continue;
            if (raw.matches("^\\s*(;.*)?$")) continue;
            String t = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (pLabel.matcher(t).matches()) return false; // join point — conservative
            // Carry consumers
            if (t.startsWith("adc ") || t.startsWith("sbc ")) return false;
            if (t.matches("(?:jr|jrl|jp)\\s+(c|nc|ugt|ult|uge|ule)\\b.*")) return false;
            if (t.startsWith("rla") || t.startsWith("rra") || t.startsWith("rl ")
                    || t.startsWith("rr ") || t.startsWith("rlc ") || t.startsWith("rrc ")) return false;
            // Branches/calls — conservative
            if (t.matches("(?:jr|jrl|jp|call|calr|ret|reti|halt|djnz|swi).*")) return false;
            // Carry clobbers (any flag-setting instruction)
            if (t.startsWith("add ") || t.startsWith("sub ") || t.startsWith("and ")
                    || t.startsWith("or ")  || t.startsWith("xor ") || t.startsWith("cp ")
                    || t.startsWith("sla ") || t.startsWith("srl ") || t.startsWith("sra ")
                    || t.startsWith("neg ") || t.startsWith("bit ")) return true;
            // Flag-neutral: ld, push, pop, inc, dec — keep scanning
        }
        return false;
    }

    // =========================================================================
    // Pass: foldImmLoadThroughAcc
    //
    // ld xwa, N ; ld xREG, xwa  →  ld xREG, N   (when xwa dead after)
    //
    // The transpiler routes all immediate loads through xwa.  TLCS-900 can
    // load an immediate directly into any 32-bit register.
    // =========================================================================
    static List<String> foldImmLoadThroughAcc(List<String> lines) {
        Pattern pLdXwaImm = Pattern.compile(
            "^(\\s+)ld\\s+xwa,\\s*(-?(?:0x[0-9a-fA-F]+|[0-9][0-9a-fA-F]*[hH]?|[0-9]+))\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pLdRegXwa = Pattern.compile(
            "^(\\s+)ld\\s+(xhl|xbc|xde|xix|xiy|xiz),\\s*xwa\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) continue;
            Matcher mImm = pLdXwaImm.matcher(out.get(i));
            if (!mImm.matches()) continue;
            String indent = mImm.group(1);
            String imm    = mImm.group(2);

            // Find next significant line
            int j = i + 1;
            while (j < out.size()) {
                if (out.get(j) == null) { j++; continue; }
                if (out.get(j).matches("^\\s*(;.*)?$")) { j++; continue; }
                break;
            }
            if (j >= out.size()) continue;
            if (pLabel.matcher(out.get(j).trim()).matches()) continue;

            Matcher mMov = pLdRegXwa.matcher(out.get(j));
            if (!mMov.matches()) continue;
            String dstReg = mMov.group(2).toLowerCase(Locale.ROOT);

            // xwa must be dead after the move
            if (!accDeadAfter(out, j, "xwa", pLabel)) continue;

            // Fold: replace move with direct immediate load, drop the ld xwa,N
            out.set(j, mMov.group(1) + "ld " + dstReg + ", " + imm);
            out.set(i, null);
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: eliminateRedundantXorWW
    //
    //   xor w, w            ; clears the high byte of wa (zero-extends a byte in a)
    //   ld  a, <src>        ; touches a / other regs, NEVER w / wa / xwa
    //   ld  e, a
    //   ...
    //   xor w, w            ; <- REDUNDANT: w is still 0, drop it
    //
    // The transpiler emits `xor w, w` before every byte->word zero-extend. When
    // several byte operations run back to back with no word-write to w in between,
    // every `xor w, w` after the first is a dead re-clear.
    //
    // SAFETY:
    //   1. w must still be provably 0 at the second xor -- NOTHING between the two
    //      xors writes w / wa / xwa (a barrier or a w-writer aborts the window).
    //   2. `xor w, w` SETS FLAGS (Z=1, C=0). Removing it changes the flag state at
    //      that point, so the flags it produces must be DEAD. Uses
    //      flagsAreDeadAfterLine().
    //
    // Bomberman: 233 removals. MODE7: 0 (word-math hot path). SAMPLE_2: 6.
    // =========================================================================
    static List<String> eliminateRedundantXorWW(List<String> lines) {
        Pattern pXorWW = Pattern.compile("^\\s+xor\\s+w,\\s*w\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pWritesW = Pattern.compile(
            // Any op whose DESTINATION is w/wa/xwa. Two operand shapes:
            //   `op REG, ...`      (ld/add/pop/...): register is first operand
            //   `op COUNT, REG`    (sla/sra/srl/inc/dec/rlc/...): register is SECOND operand
            // Must catch BOTH — `sla 2, wa` and `inc 1, wa` write wa. (The old pattern only
            // saw the first shape AND used .matches() on a prefix-anchored regex, so it never
            // fired at all → non-redundant `xor w,w` were deleted → mode7 _SetPalette black screen.)
            "^\\s+(?:ld|add|sub|adc|sbc|and|or|xor|neg|cpl|mul|muls|div|divs|ext[sz]|pop|rld|rrd|inc|dec|sla|sra|srl|rlc|rrc|rl|rr)\\s+(?:[^,;]+,\\s*)?(?:w|wa|xwa)(?![\\w])",
            Pattern.CASE_INSENSITIVE);
        Pattern pLabel  = Pattern.compile("^[.\\w].*:.*$");
        Pattern pBranch = Pattern.compile(
            "^\\s+(?:jr|jrl|jp|call|calr|ret|reti|halt|djnz|swi)\\b", Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) continue;
            if (!pXorWW.matcher(out.get(i)).matches()) continue;

            for (int j = i + 1; j < out.size(); j++) {
                String raw = out.get(j);
                if (raw == null) continue;
                String s = raw.strip();
                if (s.isEmpty() || s.startsWith(";")) continue;
                if (pLabel.matcher(s).matches() || pBranch.matcher(raw).find()) break;

                if (pXorWW.matcher(raw).matches()) {
                    // w is still 0 here. Drop this xor iff its flags are dead.
                    if (flagsAreDeadAfterLine(out, j, pLabel)) {
                        out.set(j, null);
                    }
                    // Either way, w is (still) 0 past this point; keep scanning.
                    continue;
                }
                if (pWritesW.matcher(raw).find()) break;
            }
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: eliminateDeadWaZeroBeforeExtz
    //
    // ld wa, 0           ← dead store: extz will zero w anyway
    // [ld a, X | ld w, X | (blank/comment)]   ← optional intermediate writes
    // extz wa / extz xwa ← zeroes w unconditionally
    //
    // The `ld wa, 0` is a dead store when extz follows within a window and
    // nothing between reads wa/w (only writes to a or w are allowed between).
    // =========================================================================
    static List<String> eliminateDeadWaZeroBeforeExtz(List<String> lines) {
        Pattern pLdWa0  = Pattern.compile("^\\s+ld\\s+wa,\\s*0\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pExtzWa = Pattern.compile("^\\s+extz\\s+(?:wa|xwa)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // Reads wa or w (not a write to them): any instruction that uses wa/w as a SOURCE
        // Conservative: flag anything mentioning xwa/wa/w except writes-only forms
        Pattern pWriteAorW = Pattern.compile(
            "^\\s+ld\\s+[aw],\\s*.+$", Pattern.CASE_INSENSITIVE);
        Pattern pMentionsWaOrW = Pattern.compile(
            "(?<![.\\w])(?:xwa|wa|w)(?![.\\w])", Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        Pattern pBranch = Pattern.compile(
            "^\\s+(?:jr|jrl|jp|call|calr|ret|reti|halt|djnz|swi)\\b", Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) continue;
            if (!pLdWa0.matcher(out.get(i)).matches()) continue;

            // Scan forward up to 5 instructions for extz wa
            boolean found = false;
            boolean safe  = true;
            for (int j = i + 1; j < Math.min(i + 6, out.size()); j++) {
                String raw = out.get(j);
                if (raw == null) continue;
                String s = raw.strip();
                if (s.isEmpty() || s.startsWith(";")) continue;
                if (pLabel.matcher(s).matches() || pBranch.matcher(raw).find()) {
                    safe = false; break;
                }
                if (pExtzWa.matcher(raw).matches()) {
                    found = true; break;
                }
                // ld a, X or ld w, X: writes to a or w — OK, don't read old wa
                if (pWriteAorW.matcher(raw).matches()) continue;
                // Anything else mentioning wa or w would be reading the zero — not safe to drop
                if (pMentionsWaOrW.matcher(s).find()) {
                    safe = false; break;
                }
            }
            if (found && safe) {
                out.set(i, null);
            }
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    static boolean[] computeXwaUpper16ZeroBefore(List<String> lines) {
        Pattern pLabel         = Pattern.compile("^[.\\w].*:.*$");
        Pattern pLdXwaImm      = Pattern.compile("^ld\\s+xwa,\\s*([^\\s;]+)\\s*$", Pattern.CASE_INSENSITIVE);
        Pattern pAndXwaImm     = Pattern.compile("^and\\s+xwa,\\s*([^\\s;]+)\\s*$", Pattern.CASE_INSENSITIVE);
        Pattern pWriteXwa      = Pattern.compile(
            "^(?:ld|add|sub|and|or|xor|adc|sbc|exts|mul|div|inc|dec|neg)\\s+xwa(?:\\s|,).*$",
            Pattern.CASE_INSENSITIVE);
        Pattern pShiftXwa      = Pattern.compile("^(?:sla|sra|srl)\\s+[^,]+,\\s*xwa\\s*$",
                                                  Pattern.CASE_INSENSITIVE);

        boolean[] before = new boolean[lines.size()];
        boolean upperZero = false;
        for (int i = 0; i < lines.size(); i++) {
            before[i] = upperZero;
            String t = lines.get(i).split(";", 2)[0].trim();
            if (t.isEmpty()) continue;
            if (pLabel.matcher(t).matches()) { upperZero = false; continue; }
            String lc = t.toLowerCase(Locale.ROOT);
            if (lc.equals("extz xwa") || lc.equals("xor xwa, xwa")) { upperZero = true; continue; }
            Matcher mLd = pLdXwaImm.matcher(t);
            if (mLd.matches()) {
                OptionalLong imm = parseAsmImmediate(mLd.group(1));
                upperZero = imm.isPresent() && ((imm.getAsLong() & 0xFFFFFFFFL) <= 0xFFFFL);
                continue;
            }
            Matcher mAnd = pAndXwaImm.matcher(t);
            if (mAnd.matches()) {
                OptionalLong imm = parseAsmImmediate(mAnd.group(1));
                upperZero = imm.isPresent() && ((imm.getAsLong() & 0xFFFFFFFFL) <= 0xFFFFL);
                continue;
            }
            if (pWriteXwa.matcher(t).matches() || pShiftXwa.matcher(t).matches()) upperZero = false;
        }
        return before;
    }

    // =========================================================================
    // Pass: eliminateDeadSlotStore
    //
    // Removes dead stores of the form:
    //
    //   ld (xsp+N), xhl     ; transpiler spills intermediate GEP result to stack
    //   add xhl, (xsp+M)    ; xhl is still alive (immediately used)
    //   ld a, (xhl+0)       ; xhl used as pointer => confirms intermediate-store pattern
    //
    // Scanning forward within the current basic block: if (xsp+N) is not read
    // before the block ends, the store is safe to drop.
    //
    // Safety gates:
    //   - Trigger only on `ld (xsp+N), xhl` (xhl specifically).
    //   - Belt-and-suspenders: verify next two instructions are add xhl,... + ld ?,(xhl...).
    //   - Stop scanning at any label or branch (end of basic block).
    //   - Stop scanning if push/pop or xsp appears (stack shift).
    // =========================================================================
    static List<String> eliminateDeadSlotStore(List<String> lines) {
        // Pattern: ld (xsp+N), xhl  (the candidate dead store)
        Pattern pStoreXhl = Pattern.compile(
            "^(\\s+)ld\\s+\\(xsp\\+(\\d+)\\),\\s*xhl\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        // Pattern: anything containing (xsp+N) — used for read detection
        Pattern pAnyRef = Pattern.compile("\\(xsp\\+(\\d+)\\)");
        // Pattern: label
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        // Pattern: control flow / stack disturbing
        Pattern pHazard = Pattern.compile(
            "^\\s+(?:jr|jrl|jp|call|calr|ret|reti|djnz|halt|swi|push|pop)\\b"
            + "|(?<![\\w.])xsp(?![\\w.])",
            Pattern.CASE_INSENSITIVE);

        // Belt-and-suspenders: verify the two instructions after the store are
        // `add xhl, ...` then `ld ?, (xhl...)`.
        Pattern pAddXhl = Pattern.compile(
            "^\\s+add\\s+xhl,\\s*.+$", Pattern.CASE_INSENSITIVE);
        Pattern pLoadViaXhl = Pattern.compile(
            "^\\s+ld\\s+[a-z]+,\\s*\\(xhl[^)]*\\)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) continue;
            Matcher mStore = pStoreXhl.matcher(out.get(i));
            if (!mStore.matches()) continue;
            long slotN = Long.parseLong(mStore.group(2));

            // Verify the next two non-null non-comment lines are: add xhl, ... ; ld ?, (xhl...)
            int next1 = -1, next2 = -1;
            for (int j = i + 1; j < out.size() && next2 < 0; j++) {
                String s = out.get(j);
                if (s == null) continue;
                String t = s.strip();
                if (t.isEmpty() || t.startsWith(";")) continue;
                if (next1 < 0) { next1 = j; }
                else           { next2 = j; }
            }
            if (next1 < 0 || next2 < 0) continue;
            if (!pAddXhl.matcher(out.get(next1)).matches()) continue;
            if (!pLoadViaXhl.matcher(out.get(next2)).matches()) continue;

            // Scan forward within the current basic block.
            // If (xsp+N) is NOT read in the remainder of this block, the store is dead.
            boolean safe = true;
            for (int j = i + 1; j < out.size(); j++) {
                String s = out.get(j);
                if (s == null) continue;
                String t = s.split(";", 2)[0].trim();
                if (t.isEmpty()) continue;
                if (pLabel.matcher(t).matches()) break;   // end of block
                if (pHazard.matcher(s).find()) break;     // branch/call → end of block
                Matcher mRef = pAnyRef.matcher(t);
                while (mRef.find()) {
                    if (Long.parseLong(mRef.group(1)) == slotN) {
                        safe = false; break;
                    }
                }
                if (!safe) break;
            }

            if (safe) out.set(i, null);
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: foldTrackAddressComputation
    //
    // In the mode7 inner loop, each pixel's track lookup computes:
    //   track[(aX >> 8) + ((aY & FMASK_INV) << 1)]
    // The transpiler emits two redundant spills through stack slots before
    // adding to XHL:
    //
    //   srl 8, xwa
    //   ld (xsp+A), xwa          ; spill X_int
    //   ld xwa, <reg_or_slot>    ; load Y  (single load instruction)
    //   sla 1, xwa
    //   and xwa, 03fe00h
    //   ld (xsp+B), xwa          ; spill Y_masked
    //   ld xhl, xbc              ; load track base pointer
    //   add xhl, (xsp+A)         ; add X_int via memory
    //   add xhl, (xsp+B)         ; add Y_masked via memory
    //
    // Replace with register-to-register adds (saves 2 instr per pixel):
    //
    //   srl 8, xwa
    //   ld xhl, xbc              ; moved earlier
    //   add xhl, xwa             ; add X_int directly
    //   ld xwa, <reg_or_slot>    ; load Y
    //   sla 1, xwa
    //   and xwa, 03fe00h
    //   add xhl, xwa             ; add Y_masked directly
    //
    // Preconditions (all checked):
    //   - The 9-instruction window is contiguous (no labels or branches).
    //   - Slots A and B are not read anywhere else in the window before their
    //     respective add-from-mem instructions (they're write-then-read once).
    //   - The "load Y" instruction is a single 'ld xwa, ...' that does not
    //     reference xhl or xbc.
    //   - After the substitution, A and B are no longer read in this window.
    //     (The stores to A and B are dropped; remaining uses of A or B later
    //     in the block are NOT affected — those stores remain in place.)
    // =========================================================================
    static List<String> fuseZeroExtendedByteLoads(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 2 < out.size(); i++) {
            String load = out.get(i).split(";", 2)[0].strip();
            if (!load.matches("ld a, \\(x(?:hl|bc|de|ix|iy|iz)(?:\\+[0-9]+)?\\)")
                    || !out.get(i + 1).strip().equals("extz wa")
                    || !out.get(i + 2).strip().equals("extz xwa")) continue;
            out.set(i, "\tld xwa, 0");
            out.set(i + 1, "\t" + load);
            out.remove(i + 2);
        }
        return out;
    }

    static List<String> foldShiftedWordSubtract(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 1 < out.size(); i++) {
            if (!out.get(i).strip().equals("sla 16, xwa")
                    || !out.get(i + 1).strip().equals("add xwa, 0fffd0000h")
                    || !shiftedSubtractFlagsDead(out, i + 2)) continue;
            out.set(i, "\tdec 3, wa");
            out.set(i + 1, "\tsla 16, xwa");
        }
        return out;
    }

    private static boolean shiftedSubtractFlagsDead(List<String> lines, int start) {
        for (int i = start; i < lines.size(); i++) {
            String code = lines.get(i).split(";", 2)[0].strip();
            if (code.isEmpty()) continue;
            if (code.matches(".*\\b(?:sr|f|rfp)\\b.*")) return false;
            if (code.matches("^(?:ld|lda|extz|exts)\\s+.*")) continue;
            return code.matches("^(?:cp|add|sub|and|or|xor)\\s+.*");
        }
        return false;
    }

    static List<String> scheduleAddressOffsets(List<String> lines) {
        List<String> code = new ArrayList<>();
        List<String> original = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String line : lines) {
            String instruction = line.split(";", 2)[0].strip();
            pending.append(line).append('\n');
            if (!instruction.isEmpty()) {
                code.add(instruction);
                original.add(pending.toString());
                pending.setLength(0);
            }
        }
        Map<String, Integer> labels = new HashMap<>();
        for (int i = 0; i < code.size(); i++)
            if (code.get(i).endsWith(":"))
                labels.put(code.get(i).substring(0, code.get(i).length() - 1), i);
        Pattern home = Pattern.compile("ld (x(?:wa|bc|de|hl)[12]|\\(xsp\\+\\d+\\)), xwa");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < code.size(); i++) {
            if (!code.get(i).equals("srl 8, xwa") || i + 9 >= code.size()) {
                out.add(original.get(i));
                continue;
            }
            Matcher first = home.matcher(code.get(i + 1));
            Matcher second = home.matcher(code.get(i + 5));
            String yLoad = code.get(i + 2);
            if (!first.matches() || !second.matches()
                    || !yLoad.matches("ld xwa, x(?:wa|bc|de|hl)[12]|ld xwa, x(?:bc|de|ix|iy|iz)")
                    || !code.get(i + 3).equals("sla 1, xwa")
                    || !code.get(i + 4).equals("and xwa, 03fe00h")
                    || !code.get(i + 6).equals("ld xhl, xbc")) {
                out.add(original.get(i));
                continue;
            }
            String xHome = first.group(1), yHome = second.group(1);
            if (xHome.equals(yHome) || overlappingAddressHomes(xHome, yHome)
                    || yLoad.equals("ld xwa, " + xHome)) {
                out.add(original.get(i));
                continue;
            }
            int next = addressAddEnd(code, i + 7, xHome);
            String intermediate = null;
            if (next >= 0 && next < code.size()
                    && code.get(next).matches("ld \\(xsp\\+\\d+\\), xhl")) {
                intermediate = code.get(next++);
                String slot = intermediate.substring(3, intermediate.indexOf(','));
                if (overlappingAddressHomes(slot, xHome) || overlappingAddressHomes(slot, yHome)) next = -1;
            }
            int end = next < 0 ? -1 : addressAddEnd(code, next, yHome);
            if (end < 0 || end >= code.size() || !code.get(end).equals("ld a, (xhl+0)")) {
                out.add(original.get(i));
                continue;
            }
            out.add("\tsrl 8, xwa");
            appendAddressHome(out, code, labels, end, xHome);
            out.add("\tld xhl, xbc");
            out.add("\tadd xhl, xwa");
            if (intermediate != null) out.add("\t" + intermediate);
            out.add("\t" + yLoad);
            out.add("\tsla 1, xwa");
            out.add("\tand xwa, 03fe00h");
            appendAddressHome(out, code, labels, end, yHome);
            out.add("\tadd xhl, xwa");
            i = end - 1;
        }
        out.add(pending.toString());
        return out.stream().flatMap(String::lines).toList();
    }

    private static int addressAddEnd(List<String> code, int start, String home) {
        if (start >= code.size()) return -1;
        if (home.startsWith("(") && code.get(start).equals("add xhl, " + home)) return start + 1;
        if (start + 1 < code.size() && code.get(start).equals("ld xwa, " + home)
                && code.get(start + 1).equals("add xhl, xwa")) return start + 2;
        return -1;
    }

    private static boolean overlappingAddressHomes(String a, String b) {
        if (!a.startsWith("(") || !b.startsWith("(")) return a.equals(b);
        int x = Integer.parseInt(a.substring(5, a.length() - 1));
        int y = Integer.parseInt(b.substring(5, b.length() - 1));
        return Math.abs(x - y) < 4;
    }

    private static void appendAddressHome(List<String> out, List<String> code,
            Map<String, Integer> labels, int end, String home) {
        if (!home.startsWith("(")) {
            String pair = home.substring(1, 3), bank = home.substring(3);
            Pattern aliases = Pattern.compile("\\b(?:x" + pair + bank + "|r" + pair + bank
                + "|r" + pair.charAt(0) + bank + "|r" + pair.charAt(1) + bank + ")\\b");
            if (scratchDeadOnAllPaths(code, end, code.size(), labels, aliases, home)) return;
        }
        out.add("\tld " + home + ", xwa");
    }

    static List<String> foldTrackAddressComputation(List<String> lines) {
        // srl 8, xwa
        Pattern pSrl8 = Pattern.compile(
            "^(\\s+)srl\\s+8,\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // ld (xsp+A), xwa  — spill
        Pattern pSpillA = Pattern.compile(
            "^\\s+ld\\s+\\(xsp\\+(\\d+)\\),\\s*xwa\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        // ld xwa, <anything except instructions with xhl/xbc> — load Y
        Pattern pLoadXwa = Pattern.compile(
            "^\\s+ld\\s+xwa,\\s*(.+?)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // sla 1, xwa
        Pattern pSla1 = Pattern.compile(
            "^\\s+sla\\s+1,\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // and xwa, 03fe00h
        Pattern pAnd3fe = Pattern.compile(
            "^\\s+and\\s+xwa,\\s*03fe00h\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // ld (xsp+B), xwa  — spill (same regex as A)
        // ld xhl, xbc
        Pattern pLdXhlXbc = Pattern.compile(
            "^\\s+ld\\s+xhl,\\s*xbc\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // add xhl, (xsp+N)
        Pattern pAddXhlSlot = Pattern.compile(
            "^\\s+add\\s+xhl,\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 8 < out.size(); i++) {
            String l0 = out.get(i);
            if (l0 == null) continue;
            Matcher m0 = pSrl8.matcher(l0);
            if (!m0.matches()) continue;
            String indent = m0.group(1);

            // Gather the next 8 lines (skip nulls — shouldn't be any mid-block)
            String[] w = new String[8];
            int[] idx = new int[8];
            int filled = 0;
            for (int j = i + 1; j < out.size() && filled < 8; j++) {
                String s = out.get(j);
                if (s == null) continue;
                w[filled] = s;
                idx[filled] = j;
                filled++;
            }
            if (filled < 8) continue;

            // Match: w[0]=ld(xsp+A),xwa  w[1]=ld xwa,<Y>  w[2]=sla 1,xwa
            //        w[3]=and xwa,03fe00h  w[4]=ld(xsp+B),xwa
            //        w[5]=ld xhl,xbc      w[6]=add xhl,(xsp+A)  w[7]=add xhl,(xsp+B)
            Matcher mA = pSpillA.matcher(w[0]);
            if (!mA.matches()) continue;
            int slotA = Integer.parseInt(mA.group(1));

            Matcher mLY = pLoadXwa.matcher(w[1]);
            if (!mLY.matches()) continue;
            String yOperand = mLY.group(1).trim();
            // The Y load must not reference xhl (bare — the register we just set) or xbc.
            // Bank registers xhl1/xhl2 are safe — they're independent of xhl.
            String yLower = yOperand.toLowerCase();
            if (yLower.matches(".*\\bxhl\\b.*") || yLower.matches(".*\\bxbc\\b.*")) continue;

            if (!pSla1.matcher(w[2]).matches()) continue;
            if (!pAnd3fe.matcher(w[3]).matches()) continue;

            Matcher mB = pSpillA.matcher(w[4]);  // same pattern
            if (!mB.matches()) continue;
            int slotB = Integer.parseInt(mB.group(1));
            if (slotA == slotB) continue;  // should be different slots

            if (!pLdXhlXbc.matcher(w[5]).matches()) continue;

            Matcher mAdd1 = pAddXhlSlot.matcher(w[6]);
            if (!mAdd1.matches()) continue;
            int addSlot1 = Integer.parseInt(mAdd1.group(1));

            Matcher mAdd2 = pAddXhlSlot.matcher(w[7]);
            if (!mAdd2.matches()) continue;
            int addSlot2 = Integer.parseInt(mAdd2.group(1));

            // Verify: add1 uses slotA, add2 uses slotB (in either order)
            boolean add1isA = (addSlot1 == slotA && addSlot2 == slotB);
            boolean add1isB = (addSlot1 == slotB && addSlot2 == slotA);
            if (!add1isA && !add1isB) continue;

            // Rewrite: srl 8,xwa / ld xhl,xbc / add xhl,xwa / ld xwa,Y / sla 1 / and / add xhl,xwa
            // (drop the two spills and the two add-from-mem, replace with 2 reg-reg adds)
            out.set(i,      indent + "srl 8, xwa");
            out.set(idx[0], indent + "ld xhl, xbc");   // was: ld (xsp+A), xwa
            out.set(idx[1], indent + "add xhl, xwa");  // was: ld xwa, <Y>  -- but Y load must follow!
            // We need to keep the Y load — insert it after the add, but we're replacing in-place.
            // Actually: shift the Y load to idx[1], put the add at a new slot.
            // Since we can't insert lines easily, restructure by placing into the 9 slots:
            //   i=srl8, idx[0]=ld xhl,xbc, idx[1]=add xhl,xwa (X), idx[2]=ld xwa,Y
            //   idx[3]=sla 1,xwa (unchanged), idx[4]=and xwa,03fe00h (unchanged)
            //   idx[5]=add xhl,xwa (Y) [was: ld (xsp+B),xwa], idx[6]=null, idx[7]=null
            // But idx[5] was "ld xhl, xbc" and idx[6/7] were the two adds.
            // We need to reuse the 9 available slots.
            out.set(i,      indent + "srl 8, xwa");          // slot 0
            out.set(idx[0], indent + "ld xhl, xbc");         // slot 1: was spill-A
            out.set(idx[1], indent + "add xhl, xwa");        // slot 2: add X; was ld-Y
            // idx[2] is sla 1,xwa — shift it to hold ld xwa,Y
            out.set(idx[2], indent + "ld xwa, " + yOperand); // slot 3: ld Y; was sla 1
            // idx[3] is and xwa — shift it to hold sla 1
            out.set(idx[3], indent + "sla 1, xwa");          // slot 4: sla1; was and
            // idx[4] is spill-B — shift it to hold and
            out.set(idx[4], indent + "and xwa, 03fe00h");    // slot 5: and; was spill-B
            // idx[5] was ld xhl,xbc — now holds add xhl,xwa (Y)
            out.set(idx[5], indent + "add xhl, xwa");        // slot 6: add Y
            // idx[6] and idx[7] were the two add xhl,(xsp+N) — now dead
            out.set(idx[6], null);
            out.set(idx[7], null);
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: foldColorSaveToXhl
    //
    // In the mode7 PIXEL inner loop, after a track-byte load through XHL, the
    // color byte is zero-extended to XWA and then spilled to a stack slot for
    // use across a branch:
    //
    //   ld a, (xhl+0)
    //   extz wa
    //   extz xwa
    //   ld (xsp+N), xwa        ; spill color to stack
    //   and xwa, 4             ; test transparency bit (clobbers xwa)
    //   jr Z, .notTransparent
    //   ld xwa, (xsp+N)        ; reload color (transparent path)
    //   sla 16, xwa
    //   add xwa, 0fffd0000h
    //   or xwa, <acc>
    //   ...
    //   jr .done
    //   .notTransparent:
    //   ld xwa, <acc>
    //   or xwa, (xsp+N)        ; use color via mem operand
    //
    // XHL is free at this point (just used to address the track byte).
    // Replace the stack slot with XHL:
    //
    //   ld a, (xhl+0)
    //   extz wa
    //   extz xwa
    //   ld xhl, xwa            ; save color in XHL (reg-reg, 4 cyc vs 8 cyc)
    //   and xwa, 4
    //   jr Z, .notTransparent
    //   ld xwa, xhl            ; reload from reg (4 cyc vs 8 cyc)
    //   sla 16, xwa
    //   ...
    //   .notTransparent:
    //   ld xwa, <acc>
    //   or xwa, xhl            ; reg-reg (4 cyc vs ~12 cyc mem operand)
    //
    // Correctness: XHL stays dead from the save until the end of the two
    // descendant blocks (confirmed: the next use of XHL is the NEXT pixel's
    // foldTrackAddressComputation sequence).
    //
    // Only fires when slot N is used EXACTLY as: one save (the ld (xsp+N),xwa
    // above) and two uses (ld xwa,(xsp+N) in the transparent path and
    // or xwa,(xsp+N) in the non-transparent path) within the scan window.
    // Any other pattern aborts the match.
    // =========================================================================
    static List<String> foldColorSaveToXhl(List<String> lines) {
        // Anchor: ld a, (xhl+0)
        Pattern pLdA   = Pattern.compile("^(\\s+)ld\\s+a,\\s*\\(xhl\\+0\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pExtzWa  = Pattern.compile("^\\s+extz\\s+wa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pExtzXwa = Pattern.compile("^\\s+extz\\s+xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // ld (xsp+N), xwa  — the spill
        Pattern pSpill = Pattern.compile("^\\s+ld\\s+\\(xsp\\+(\\d+)\\),\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // and xwa, 4
        Pattern pAnd4  = Pattern.compile("^\\s+and\\s+xwa,\\s*4\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // jr Z, label  (or jr NZ, or jrl variants)
        Pattern pJrZ   = Pattern.compile("^\\s+jrl?\\s+(?:Z|NZ),\\s*(\\S+)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // ld xwa, (xsp+N)
        Pattern pReload = Pattern.compile("^\\s+ld\\s+xwa,\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // or xwa, (xsp+N)
        Pattern pOrMem  = Pattern.compile("^\\s+or\\s+xwa,\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // any reference to (xsp+N) — used for safety check
        Pattern pAnySlot = Pattern.compile("\\(xsp\\+(\\d+)\\)");
        // label line
        Pattern pLabel  = Pattern.compile("^[.\\w].*:\\s*(?:;.*)?$");
        // hazard: push/pop/call/ret (shift stack or destroy context)
        Pattern pHazard = Pattern.compile("^\\s+(?:call|ret|reti|push|pop|swi|djnz)\\b", Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);
        outer:
        for (int i = 0; i + 4 < out.size(); i++) {
            String l0 = out.get(i);
            if (l0 == null) continue;
            Matcher m0 = pLdA.matcher(l0);
            if (!m0.matches()) continue;
            String indent = m0.group(1);

            // Collect next 4 lines without nulls
            int[] ni = new int[4]; String[] nl = new String[4];
            int got = 0;
            for (int j = i+1; j < out.size() && got < 4; j++) {
                if (out.get(j) == null) continue;
                ni[got] = j; nl[got] = out.get(j); got++;
            }
            if (got < 4) continue;

            if (!pExtzWa.matcher(nl[0]).matches())  continue;
            if (!pExtzXwa.matcher(nl[1]).matches()) continue;
            Matcher mSpill = pSpill.matcher(nl[2]);
            if (!mSpill.matches()) continue;
            int slotN = Integer.parseInt(mSpill.group(1));
            if (!pAnd4.matcher(nl[3]).matches()) continue;

            // Next line should be jr Z / jr NZ
            int jrIdx = -1; String jrLine = null;
            for (int j = ni[3]+1; j < out.size(); j++) {
                String s = out.get(j); if (s == null) continue;
                String t = s.strip();
                if (t.isEmpty() || t.startsWith(";")) continue;
                Matcher mJr = pJrZ.matcher(s);
                if (!mJr.matches()) continue outer; // next non-comment must be the branch
                jrIdx = j; jrLine = s;
                break;
            }
            if (jrIdx < 0) continue;

            // Now scan forward up to 40 lines looking for:
            //   exactly one ld xwa,(xsp+slotN)   [transparent reload]
            //   exactly one or xwa,(xsp+slotN)    [non-transparent use]
            //   no other references to (xsp+slotN) writes
            //   no hazards that would change xhl
            int reloadIdx = -1, orIdx = -1;
            int writes = 0; // other writes to slot N
            for (int j = jrIdx+1; j < out.size() && j < jrIdx+50; j++) {
                String s = out.get(j); if (s == null) continue;
                String t = s.strip();
                if (t.isEmpty() || t.startsWith(";")) continue;
                // Write to xhl = hazard (but label is OK)
                if (pLabel.matcher(t).matches()) continue; // labels OK within scan
                // Check for xhl write hazard (but not ld xhl,xwa which we intend to insert,
                // and not add xhl,xwa — those are the track address computation for next pixel,
                // which is the end of our window)
                String tLow = t.toLowerCase();
                if (tLow.matches(".*\\bld\\s+xhl,.*") && !tLow.contains("ld xhl, xwa")) {
                    // A different ld xhl,... would clobber xhl — only safe if it's after
                    // both our uses (reload and or).
                    if (reloadIdx >= 0 && orIdx >= 0) break; // both found — window done
                    continue outer; // xhl clobbered before both uses
                }
                // Check for push/pop/call/ret that shifts stack or changes PC
                if (pHazard.matcher(s).find()) continue outer;
                // Check slot N references
                Matcher mRef = pAnySlot.matcher(t);
                while (mRef.find()) {
                    if (Integer.parseInt(mRef.group(1)) != slotN) continue;
                    // It's our slot
                    Matcher mRel = pReload.matcher(s);
                    Matcher mOr  = pOrMem.matcher(s);
                    boolean isStore = t.matches("\\s*ld\\s+\\(xsp\\+" + slotN + "\\).*");
                    if (isStore) { writes++; continue outer; } // another write = unsafe
                    if (mRel.matches()) {
                        if (reloadIdx >= 0) continue outer; // two reloads — unexpected
                        reloadIdx = j;
                    } else if (mOr.matches()) {
                        if (orIdx >= 0) continue outer; // two or-uses — unexpected
                        orIdx = j;
                    } else {
                        continue outer; // some other use pattern — bail
                    }
                }
                // If we found both uses, the window is complete
                if (reloadIdx >= 0 && orIdx >= 0) break;
            }
            if (reloadIdx < 0 || orIdx < 0) continue; // didn't find both uses

            // Apply: replace spill with ld xhl,xwa; replace reload with ld xwa,xhl; replace or-mem with or xwa,xhl
            out.set(ni[2], indent + "ld xhl, xwa");              // was: ld (xsp+N), xwa
            out.set(reloadIdx, indent + "ld xwa, xhl");          // was: ld xwa, (xsp+N)
            out.set(orIdx,     indent + "or xwa, xhl");          // was: or xwa, (xsp+N)
        }
        return out;
    }

    // =========================================================================
    // Pass: foldColorAndBitTestToRegBit
    //
    // After foldColorSaveToXhl, B-block tile-lookup sequences have:
    //
    //   ld a, (xhl+0)
    //   extz wa
    //   extz xwa
    //   ld xhl, xwa          ; save color in XHL  ← foldColorSaveToXhl output
    //   and xwa, 4           ; test flip bit (clobbers xwa)
    //   jr Z, .LABEL_NF      ; jump if no-flip
    //   ld xwa, xhl          ; restore color (flip path)
    //   sla 16, xwa
    //   add xwa, 0fffd0000h
    //   or xwa, (xsp+N)      ; merge with acc low (mem-folded)
    //   ld (xsp+M), xwa
    //   jr .NEXT
    //   .LABEL_NF:
    //   ld xwa, (xsp+N)      ; reload acc low
    //   or xwa, xhl          ; merge color from xhl
    //   ld (xsp+M), xwa
    //
    // Since XWA holds the color byte after extz xwa, bit 2 of A (the byte
    // register) is the same as bit 2 of XWA. The `bit 2, a` instruction tests
    // this bit without modifying any register, so XWA stays = color:
    //
    //   ld a, (xhl+0)
    //   extz wa
    //   extz xwa              ; xwa = color, a = color byte
    //   bit 2, a              ; test flip bit, non-destructive (Z=1 if no-flip)
    //   jr Z, .LABEL_NF
    //   ; flip path: xwa = color unchanged
    //   sla 16, xwa
    //   add xwa, 0fffd0000h
    //   or xwa, (xsp+N)
    //   ld (xsp+M), xwa
    //   jr .NEXT
    //   .LABEL_NF:
    //   or xwa, (xsp+N)       ; color | acc (xwa still = color)
    //   ld (xsp+M), xwa
    //
    // Removes 3 instructions per B-block tile lookup (ld xhl,xwa + ld xwa,xhl
    // in flip path + ld xwa,(xsp+N) in no-flip path that collapses into the
    // or-mem).
    // =========================================================================
    static List<String> foldColorAndBitTestToRegBit(List<String> lines) {
        Pattern pExtzWa  = Pattern.compile("^\\s+extz\\s+wa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pExtzXwa = Pattern.compile("^\\s+extz\\s+xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pSaveXhl = Pattern.compile("^(\\s+)ld\\s+xhl,\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pSaveStack = Pattern.compile("^(\\s+)ld\\s+\\(xsp\\+(\\d+)\\),\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        // Bank-register save/restore: ld <bankReg>, xwa / ld xwa, <bankReg>
        // bankReg = xwa1..xwa3, xbc1..xbc3, xde1..xde3, xhl1..xhl3
        Pattern pSaveBank    = Pattern.compile("^(\\s+)ld\\s+(x(?:wa|bc|de|hl)[123]),\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pRestoreBankAny = Pattern.compile("^\\s+ld\\s+xwa,\\s*(x(?:wa|bc|de|hl)[123])\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pOrBankAny   = Pattern.compile("^\\s+or\\s+xwa,\\s*(x(?:wa|bc|de|hl)[123])\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pAnd4    = Pattern.compile("^\\s+and\\s+xwa,\\s*4\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pJrZ     = Pattern.compile("^\\s+jrl?\\s+(Z|NZ),\\s*(\\S+)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pJrUnc   = Pattern.compile("^\\s+jrl?\\s+(\\S+)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLdA     = Pattern.compile("^\\s+ld\\s+a,\\s*\\(xhl\\+0\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pRestoreXhl = Pattern.compile("^\\s+ld\\s+xwa,\\s*xhl\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLoadMem = Pattern.compile("^\\s+ld\\s+xwa,\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pOrXhl   = Pattern.compile("^\\s+or\\s+xwa,\\s*xhl\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pOrMem   = Pattern.compile("^\\s+or\\s+xwa,\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLabel   = Pattern.compile("^[.\\w].*:\\s*(?:;.*)?$");
        Pattern pHazard  = Pattern.compile("^\\s+(?:call|ret|reti|push|pop|swi|djnz)\\b", Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);
        outer:
        for (int i = 0; i + 4 < out.size(); i++) {
            String l0 = out.get(i);
            if (l0 == null) continue;
            // Anchor: ld a, (xhl+0)
            if (!pLdA.matcher(l0).matches()) continue;

            // Collect: extz wa, extz xwa, ld xhl xwa, and xwa 4
            int[] ni = new int[4]; String[] nl = new String[4];
            int got = 0;
            for (int j = i+1; j < out.size() && got < 4; j++) {
                String s = out.get(j); if (s == null) continue;
                String t = s.strip();
                if (t.isEmpty() || t.startsWith(";") || pLabel.matcher(t).matches()) continue;
                ni[got] = j; nl[got] = s; got++;
            }
            if (got < 4) continue;
            if (!pExtzWa.matcher(nl[0]).matches())  continue;
            if (!pExtzXwa.matcher(nl[1]).matches()) continue;

            Matcher stackSave = pSaveStack.matcher(nl[2]);
            if (stackSave.matches() && pAnd4.matcher(nl[3]).matches()) {
                int[] positions = new int[12];
                String[] sequence = new String[12];
                int count = 0;
                for (int scan = ni[3] + 1; scan < out.size() && count < sequence.length; scan++) {
                    String candidate = out.get(scan);
                    if (candidate == null || candidate.isBlank() || candidate.strip().startsWith(";")) continue;
                    positions[count] = scan;
                    sequence[count++] = candidate.strip();
                }
                if (count == sequence.length) {
                    Matcher branch = pJrZ.matcher("\t" + sequence[0]);
                    Matcher mergeJump = pJrUnc.matcher("\t" + sequence[3]);
                    String reload = "ld xwa, (xsp+" + stackSave.group(2) + ")";
                    if (branch.matches() && branch.group(1).equalsIgnoreCase("NZ")
                            && mergeJump.matches()
                            && sequence[1].equals(reload) && sequence[5].equals(reload)
                            && pSaveStack.matcher("\t" + sequence[2]).matches()
                            && sequence[4].equals(branch.group(2) + ":")
                            && sequence[6].equals("sla 16, xwa")
                            && sequence[7].equals("add xwa, 0fffd0000h")
                            && sequence[8].equals(sequence[2])
                            && sequence[9].equals(mergeJump.group(1) + ":")
                            && sequence[10].startsWith("ld xwa, ")
                            && sequence[11].startsWith("add xwa, ")) {
                        Pattern targetRef = Pattern.compile("(?<![\\w.])" + Pattern.quote(branch.group(2)) + "(?![\\w.])");
                        int references = 0;
                        for (String candidate : out) {
                            if (candidate == null) continue;
                            Matcher reference = targetRef.matcher(candidate.split(";", 2)[0]);
                            while (reference.find()) references++;
                        }
                        if (references == 2) {
                            out.set(ni[3], stackSave.group(1) + "bit 2, a");
                            out.set(positions[1], null);
                            out.set(positions[5], null);
                        }
                    }
                }
                continue;
            }

            // Determine save type: xhl (B-block) or bank register (A-block)
            Matcher mSaveXhl  = pSaveXhl.matcher(nl[2]);
            Matcher mSaveBank = pSaveBank.matcher(nl[2]);
            boolean isXhlSave  = mSaveXhl.matches();
            boolean isBankSave = !isXhlSave && mSaveBank.matches();
            if (!isXhlSave && !isBankSave) continue;
            String indent   = isXhlSave ? mSaveXhl.group(1) : mSaveBank.group(1);
            String bankReg  = isBankSave ? mSaveBank.group(2).toLowerCase() : null; // e.g. "xde1"
            if (!pAnd4.matcher(nl[3]).matches()) continue;

            // Next non-comment/label must be jr Z or jr NZ
            int jrIdx = -1; Matcher mJrZ = null;
            for (int j = ni[3]+1; j < out.size(); j++) {
                String s = out.get(j); if (s == null) continue;
                String t = s.strip();
                if (t.isEmpty() || t.startsWith(";")) continue;
                Matcher m = pJrZ.matcher(s);
                if (!m.matches()) continue outer;
                jrIdx = j; mJrZ = m; break;
            }
            if (jrIdx < 0) continue;

            String cc       = mJrZ.group(1);   // "Z" or "NZ"
            String brTarget = mJrZ.group(2);   // branch target label

            if (isXhlSave) {
                // ── B-block path: jr Z → no-flip label, sacc on stack ──
                if (!cc.equalsIgnoreCase("Z")) continue;
                String nfLabel = brTarget;

                int restoreIdx  = -1;
                int nfLabelIdx  = -1;
                int loadSaccIdx = -1; String loadSaccSlot = null;
                int orXhlIdx    = -1;

                for (int j = jrIdx+1; j < out.size() && j < jrIdx+60; j++) {
                    String s = out.get(j); if (s == null) continue;
                    String t = s.strip();
                    if (t.isEmpty() || t.startsWith(";")) continue;
                    if (pHazard.matcher(s).find()) continue outer;
                    if (pLabel.matcher(t).matches()) {
                        String lbl = t.replaceFirst(":.*", "").strip();
                        if (lbl.equals(nfLabel)) nfLabelIdx = j;
                        continue;
                    }
                    if (nfLabelIdx < 0) {
                        if (restoreIdx < 0 && pRestoreXhl.matcher(s).matches()) restoreIdx = j;
                    } else {
                        if (loadSaccIdx < 0) {
                            Matcher mLd = pLoadMem.matcher(s);
                            if (mLd.matches()) { loadSaccIdx = j; loadSaccSlot = mLd.group(1); continue; }
                            continue outer;
                        } else if (orXhlIdx < 0) {
                            if (pOrXhl.matcher(s).matches()) { orXhlIdx = j; }
                            else continue outer;
                        } else { break; }
                    }
                }
                if (restoreIdx < 0 || nfLabelIdx < 0 || loadSaccIdx < 0 || orXhlIdx < 0) continue;

                // Confirm flip-path or-mem uses same slot as loadSacc
                int flipOrIdx = -1;
                for (int j = restoreIdx+1; j < nfLabelIdx; j++) {
                    String s = out.get(j); if (s == null) continue;
                    String t = s.strip();
                    if (t.isEmpty() || t.startsWith(";") || pLabel.matcher(t).matches()) continue;
                    Matcher mOr = pOrMem.matcher(s);
                    if (mOr.matches()) {
                        if (!mOr.group(1).equals(loadSaccSlot)) continue outer;
                        flipOrIdx = j; break;
                    }
                }
                if (flipOrIdx < 0) continue;

                out.set(ni[2], null);                                              // delete ld xhl,xwa
                out.set(ni[3], indent + "bit 2, a");                              // and xwa,4 → bit 2,a
                out.set(restoreIdx, null);                                         // delete ld xwa,xhl (flip)
                out.set(loadSaccIdx, indent + "or xwa, (xsp+" + loadSaccSlot + ")"); // ld+or → or mem
                out.set(orXhlIdx, null);                                           // delete or xwa,xhl

            } else {
                // ── A-block path: bank register save, Z or NZ form ──
                final String bReg = bankReg;

                if (cc.equalsIgnoreCase("Z")) {
                    // block 99 style: jr Z, <nfLabel>; flip falls through; no-flip at label
                    // no-flip path: ld xwa,<saccBankReg>; or xwa,<bankReg>  →  or xwa,<saccBankReg>
                    String nfLabel = brTarget;

                    int restoreFlipIdx = -1;
                    int nfLabelIdx     = -1;
                    int loadSaccBRIdx  = -1; String saccBReg = null;
                    int orBankIdx      = -1;

                    for (int j = jrIdx+1; j < out.size() && j < jrIdx+80; j++) {
                        String s = out.get(j); if (s == null) continue;
                        String t = s.strip();
                        if (t.isEmpty() || t.startsWith(";")) continue;
                        if (pHazard.matcher(s).find()) continue outer;
                        if (pLabel.matcher(t).matches()) {
                            String lbl = t.replaceFirst(":.*", "").strip();
                            if (lbl.equals(nfLabel)) nfLabelIdx = j;
                            continue;
                        }
                        if (nfLabelIdx < 0) {
                            // flip path (fall-through): find ld xwa, <bankReg>
                            if (restoreFlipIdx < 0) {
                                Matcher mR = pRestoreBankAny.matcher(s);
                                if (mR.matches() && mR.group(1).equalsIgnoreCase(bReg)) {
                                    restoreFlipIdx = j;
                                }
                            }
                        } else {
                            // no-flip path: ld xwa,<saccBankReg> then or xwa,<bankReg>
                            if (loadSaccBRIdx < 0) {
                                Matcher mR = pRestoreBankAny.matcher(s);
                                if (mR.matches() && !mR.group(1).equalsIgnoreCase(bReg)) {
                                    loadSaccBRIdx = j; saccBReg = mR.group(1).toLowerCase();
                                    continue;
                                }
                                continue outer; // first stmt in no-flip must be ld xwa,<saccReg>
                            } else if (orBankIdx < 0) {
                                Matcher mO = pOrBankAny.matcher(s);
                                if (mO.matches() && mO.group(1).equalsIgnoreCase(bReg)) {
                                    orBankIdx = j;
                                } else continue outer;
                            } else { break; }
                        }
                    }
                    if (restoreFlipIdx < 0 || nfLabelIdx < 0 || loadSaccBRIdx < 0 || orBankIdx < 0) continue;

                    // Confirm flip-path also has or xwa,<saccBankReg>
                    boolean saccOrFoundInFlip = false;
                    for (int j = restoreFlipIdx+1; j < nfLabelIdx; j++) {
                        String s = out.get(j); if (s == null) continue;
                        String t = s.strip();
                        if (t.isEmpty() || t.startsWith(";") || pLabel.matcher(t).matches()) continue;
                        Matcher mO = pOrBankAny.matcher(s);
                        if (mO.matches() && mO.group(1).equalsIgnoreCase(saccBReg)) {
                            saccOrFoundInFlip = true; break;
                        }
                    }
                    if (!saccOrFoundInFlip) continue;

                    out.set(ni[2], null);                                          // delete ld bankReg,xwa
                    out.set(ni[3], indent + "bit 2, a");                           // and xwa,4 → bit 2,a
                    out.set(restoreFlipIdx, null);                                 // delete ld xwa,bankReg (flip)
                    // no-flip: ld xwa,saccBReg + or xwa,bankReg → or xwa,saccBReg
                    out.set(loadSaccBRIdx, indent + "or xwa, " + saccBReg);
                    out.set(orBankIdx, null);                                      // delete or xwa,bankReg

                } else {
                    // NZ form: jr NZ, <flipLabel>; no-flip falls through; flip at label
                    // block 77 style: no sacc merge; both paths just store color to phi slot
                    String flipLabel = brTarget;

                    // Find all ld xwa,<bankReg> in no-flip (fall-through) and flip (at label)
                    // No-flip: scan from jr to the next unconditional-jr or flipLabel definition
                    // Flip: scan from flipLabel onwards

                    int restoreNFIdx   = -1;
                    int flipLabelIdx   = -1;
                    int restoreFlipIdx = -1;

                    for (int j = jrIdx+1; j < out.size() && j < jrIdx+80; j++) {
                        String s = out.get(j); if (s == null) continue;
                        String t = s.strip();
                        if (t.isEmpty() || t.startsWith(";")) continue;
                        if (pHazard.matcher(s).find()) continue outer;
                        if (pLabel.matcher(t).matches()) {
                            String lbl = t.replaceFirst(":.*", "").strip();
                            if (lbl.equals(flipLabel)) flipLabelIdx = j;
                            continue;
                        }
                        if (flipLabelIdx < 0) {
                            // no-flip fall-through: find first ld xwa,<bankReg>
                            if (restoreNFIdx < 0) {
                                Matcher mR = pRestoreBankAny.matcher(s);
                                if (mR.matches() && mR.group(1).equalsIgnoreCase(bReg)) restoreNFIdx = j;
                            }
                        } else {
                            // flip path: find first ld xwa,<bankReg>
                            if (restoreFlipIdx < 0) {
                                Matcher mR = pRestoreBankAny.matcher(s);
                                if (mR.matches() && mR.group(1).equalsIgnoreCase(bReg)) restoreFlipIdx = j;
                                else if (restoreFlipIdx < 0 && !pLabel.matcher(t).matches()) {
                                    // first non-label in flip path must be the restore, or we skip
                                    // (don't bail — just scan on, it might appear shortly)
                                }
                            } else {
                                break;
                            }
                        }
                    }
                    if (restoreNFIdx < 0 || flipLabelIdx < 0 || restoreFlipIdx < 0) continue;

                    out.set(ni[2], null);                                          // delete ld bankReg,xwa
                    out.set(ni[3], indent + "bit 2, a");                           // and xwa,4 → bit 2,a
                    out.set(restoreNFIdx, null);                                   // delete ld xwa,bankReg (no-flip)
                    out.set(restoreFlipIdx, null);                                 // delete ld xwa,bankReg (flip)
                }
            }
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: foldIncrCounterPhiRoundtrip
    //
    // Eliminates temp-slot round-trips for loop counter increment+phi patterns.
    //
    // Sub-case A: incremented value stays in register through compare+branch
    //   ld xwa, <src>
    //   inc 1, xwa
    //   ld (xsp+M), xwa      ; temp store  ← DELETE
    //   cp xwa, K            ; compare (doesn't clobber xwa)
    //   jr Z/NZ/C/NC, label
    //   ; fall-through phi:
    //   ld xwa, (xsp+M)      ; ← DELETE (xwa already = incremented value)
    //   ld <dst>, xwa        ; phi copy to destination
    //
    // Sub-case B: compare uses old value (reload before compare)
    //   ld wa, (xsp+N)
    //   inc 1, wa
    //   ld (xsp+M), wa       ; temp store  ← DELETE
    //   ld wa, (xsp+N)       ; reload old  ← DELETE
    //   cp wa, K             ; compare old value (→ change K to K+1)
    //   jr NC/C, label
    //   ; phi:
    //   ld wa, (xsp+M)       ; ← DELETE
    //   ld (xsp+N), wa       ; write back new counter
    // =========================================================================
    static List<String> foldIncrCounterPhiRoundtrip(List<String> lines) {
        // Broad register: wa/bc/de/hl with optional x prefix + bank suffix, plus index regs
        String BREG = "x?(?:wa|bc|de|hl)[123]?|wa|bc|de|hl|x(?:ix|iy|iz)";
        Pattern pLdReg   = Pattern.compile("^(\\s+)ld\\s+(" + BREG + "),\\s*(.+?)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLdAnySrc= Pattern.compile("^\\s+ld\\s+(?:" + BREG + "),\\s*(\\S+.*)$", Pattern.CASE_INSENSITIVE);
        Pattern pInc1    = Pattern.compile("^\\s+inc\\s+1,\\s*(" + BREG + ")\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLdMem   = Pattern.compile("^\\s+ld\\s+(?:" + BREG + "),\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pStoreMem= Pattern.compile("^\\s+ld\\s+\\(xsp\\+(\\d+)\\),\\s*(" + BREG + ")\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pCp      = Pattern.compile("^(\\s+)cp\\s+(" + BREG + "),\\s*([0-9a-fA-F]+h?)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pJr      = Pattern.compile("^\\s+jrl?\\s+(?:Z|NZ|C|NC|V|NV|M|P),\\s*(\\S+)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLabel   = Pattern.compile("^[.\\w].*:\\s*(?:;.*)?$");
        Pattern pHazard  = Pattern.compile("^\\s+(?:call|ret|reti|push|pop|swi|djnz)\\b", Pattern.CASE_INSENSITIVE);
        Pattern pJrUnc   = Pattern.compile("^\\s+jrl?\\s+\\S", Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);

        outer:
        for (int i = 0; i + 4 < out.size(); i++) {
            String l0 = out.get(i);
            if (l0 == null) continue;

            // Line 0: ld <reg>, <src>
            Matcher mLd0 = pLdReg.matcher(l0);
            if (!mLd0.matches()) continue;
            String indent = mLd0.group(1);
            String reg    = mLd0.group(2).toLowerCase();

            // Next non-blank: inc 1, <reg>
            int incIdx = -1;
            for (int j = i+1; j < out.size() && j < i+4; j++) {
                String s = out.get(j); if (s == null) continue;
                String t = s.strip(); if (t.isEmpty() || t.startsWith(";")) continue;
                Matcher m = pInc1.matcher(s);
                if (!m.matches() || !m.group(1).equalsIgnoreCase(reg)) continue outer;
                incIdx = j; break;
            }
            if (incIdx < 0) continue;

            // Next: ld (xsp+M), <reg>  (temp store)
            int tempStoreIdx = -1; String tempSlot = null;
            for (int j = incIdx+1; j < out.size() && j < incIdx+4; j++) {
                String s = out.get(j); if (s == null) continue;
                String t = s.strip(); if (t.isEmpty() || t.startsWith(";")) continue;
                Matcher m = pStoreMem.matcher(s);
                if (!m.matches() || !m.group(2).equalsIgnoreCase(reg)) continue outer;
                tempStoreIdx = j; tempSlot = m.group(1); break;
            }
            if (tempStoreIdx < 0) continue;

            // Next line determines sub-case
            int nextIdx = -1; String nextLine = null;
            for (int j = tempStoreIdx+1; j < out.size() && j < tempStoreIdx+4; j++) {
                String s = out.get(j); if (s == null) continue;
                String t = s.strip(); if (t.isEmpty() || t.startsWith(";")) continue;
                nextIdx = j; nextLine = s; break;
            }
            if (nextIdx < 0) continue;

            // Sub-case B: ld <reg>, (xsp+N) reload (same slot as load at i)
            Matcher mReload = pLdMem.matcher(nextLine);
            // Sub-case A: cp <reg>, K (compare uses incremented value directly)
            Matcher mCpA = pCp.matcher(nextLine);

            if (mReload.matches()) {
                // Sub-case B: reload old value before compare
                // Line at i must be ld <16-bit-reg>, (xsp+N)
                Matcher mLd0b = pStoreMem.matcher(""); // just checking format
                // Re-check that the original load was from a stack slot
                Matcher mSrc = Pattern.compile("^\\s+ld\\s+(wa|bc|de|hl|x\\w+),\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE).matcher(l0);
                if (!mSrc.matches()) continue;
                String origSlot = mSrc.group(2);
                // Confirm reload is from same slot
                if (!mReload.group(1).equals(origSlot)) continue;
                String reloadReg = mReload.group(0).replaceAll(".*ld\\s+(\\S+),.*", "$1").toLowerCase().trim();
                // Actually, re-extract reload reg from original pattern
                Matcher mRR = Pattern.compile("^\\s+ld\\s+(" + BREG + "),\\s*\\(xsp\\+" + origSlot + "\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE).matcher(nextLine);
                if (!mRR.matches()) continue;
                String reloadedReg = mRR.group(1).toLowerCase();
                if (!reloadedReg.equalsIgnoreCase(reg)) continue;

                // Next: cp <reg>, K
                int cpIdx = -1; Matcher mCpB = null; String cpLine = null;
                for (int j = nextIdx+1; j < out.size() && j < nextIdx+4; j++) {
                    String s = out.get(j); if (s == null) continue;
                    String t = s.strip(); if (t.isEmpty() || t.startsWith(";")) continue;
                    mCpB = pCp.matcher(s);
                    if (!mCpB.matches() || !mCpB.group(2).equalsIgnoreCase(reg)) continue outer;
                    cpIdx = j; cpLine = s; break;
                }
                if (cpIdx < 0) continue;

                // Next: jr NC/C (some condition)
                int jrIdx = -1; Matcher mJrB = null;
                for (int j = cpIdx+1; j < out.size() && j < cpIdx+4; j++) {
                    String s = out.get(j); if (s == null) continue;
                    String t = s.strip(); if (t.isEmpty() || t.startsWith(";")) continue;
                    mJrB = pJr.matcher(s);
                    if (!mJrB.matches()) continue outer;
                    jrIdx = j; break;
                }
                if (jrIdx < 0) continue;

                // Phi region: scan for ld <reg>, (xsp+M) then ld (xsp+origSlot), <reg>
                int phiLoadIdx = -1, phiStoreIdx = -1;
                // Phi region scan: find ld <reg>,(xsp+M) and ld (xsp+origSlot),<reg>
                // These may not be adjacent — other phi copies can appear between them.
                // Safety: no instruction between entry and phiLoadIdx must clobber <reg>.
                Pattern pPhiLd  = Pattern.compile("^\\s+ld\\s+(" + BREG + "),\\s*\\(xsp\\+" + tempSlot + "\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
                Pattern pPhiSt  = Pattern.compile("^\\s+ld\\s+\\(xsp\\+" + origSlot + "\\),\\s*(" + BREG + ")\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
                // Clobber check: for 16-bit reg like "wa", also watch for writes to "xwa"
                // (since ld xwa,... overwrites all 32 bits including wa)
                String clobberPat = reg.startsWith("x") ? reg : "x?" + reg;
                Pattern pWritesReg = Pattern.compile("^\\s+(?:ld|add|sub|adc|sbc|and|or|xor|inc|dec|sla|srl|sra|sll|extz|exts|neg)\\s+(" + clobberPat + ")(?:[, \\t]|$)", Pattern.CASE_INSENSITIVE);
                for (int j = jrIdx+1; j < out.size() && j < jrIdx+30; j++) {
                    String s = out.get(j); if (s == null) continue;
                    String t = s.strip();
                    if (t.isEmpty() || t.startsWith(";")) continue;
                    if (pLabel.matcher(t).matches()) continue;
                    if (pHazard.matcher(s).find()) continue outer;
                    if (pJrUnc.matcher(s).find()) { break; }
                    if (phiLoadIdx < 0) {
                        Matcher m = pPhiLd.matcher(s);
                        if (m.matches() && m.group(1).equalsIgnoreCase(reg)) { phiLoadIdx = j; continue; }
                        // Check this instruction doesn't clobber <reg> before we find the phi load
                        if (pWritesReg.matcher(s).find()) continue outer; // <reg> overwritten before phi load
                    } else if (phiStoreIdx < 0) {
                        Matcher m = pPhiSt.matcher(s);
                        if (m.matches() && m.group(1).equalsIgnoreCase(reg)) { phiStoreIdx = j; break; }
                        // Check this instruction doesn't clobber <reg> between load and store
                        if (pWritesReg.matcher(s).find()) continue outer;
                    }
                }
                if (phiLoadIdx < 0 || phiStoreIdx < 0) continue;

                // Parse old comparison constant K and compute K+1
                String kStr = mCpB.group(3);
                long kVal;
                try {
                    if (kStr.endsWith("h") || kStr.endsWith("H")) kVal = Long.parseLong(kStr.substring(0, kStr.length()-1), 16);
                    else kVal = Long.parseLong(kStr);
                } catch (NumberFormatException e) { continue; }
                long newK = kVal + 1;
                String newKStr = Long.toHexString(newK) + "h";
                String newCpLine = indent + "cp " + reg + ", " + newKStr;

                // Apply: delete tempStore, delete reload, change cp constant, delete phiLoad
                out.set(tempStoreIdx, null);
                out.set(nextIdx, null);
                out.set(cpIdx, newCpLine);
                out.set(phiLoadIdx, null);

            } else if (mCpA.matches() && mCpA.group(2).equalsIgnoreCase(reg)) {
                // Sub-case A: compare uses incremented value directly
                // Next: jr <cc>, label
                int jrIdx = -1;
                for (int j = nextIdx+1; j < out.size() && j < nextIdx+4; j++) {
                    String s = out.get(j); if (s == null) continue;
                    String t = s.strip(); if (t.isEmpty() || t.startsWith(";")) continue;
                    if (!pJr.matcher(s).matches()) continue outer;
                    jrIdx = j; break;
                }
                if (jrIdx < 0) continue;

                // Phi region: scan for ld <reg>, (xsp+M) then ld <dst>, <reg>
                int phiLoadIdx = -1, phiCopyIdx = -1;
                for (int j = jrIdx+1; j < out.size() && j < jrIdx+20; j++) {
                    String s = out.get(j); if (s == null) continue;
                    String t = s.strip();
                    if (t.isEmpty() || t.startsWith(";")) continue;
                    if (pLabel.matcher(t).matches()) { if (phiLoadIdx >= 0) continue; continue; }
                    if (pHazard.matcher(s).find()) continue outer;
                    if (pJrUnc.matcher(s).find()) { if (phiLoadIdx < 0) continue outer; break; }
                    if (phiLoadIdx < 0) {
                        Matcher m = Pattern.compile("^\\s+ld\\s+(" + BREG + "),\\s*\\(xsp\\+" + tempSlot + "\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE).matcher(s);
                        if (m.matches() && m.group(1).equalsIgnoreCase(reg)) { phiLoadIdx = j; continue; }
                        continue outer;
                    } else if (phiCopyIdx < 0) {
                        // Any ld <dst>, <reg> — the phi copy to final destination
                        Matcher m = Pattern.compile("^\\s+ld\\s+(" + BREG + "),\\s*(" + BREG + ")\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE).matcher(s);
                        if (m.matches() && m.group(2).equalsIgnoreCase(reg)) { phiCopyIdx = j; break; }
                        continue outer;
                    }
                }
                if (phiLoadIdx < 0 || phiCopyIdx < 0) continue;

                // Apply: delete tempStore, delete phiLoad
                out.set(tempStoreIdx, null);
                out.set(phiLoadIdx, null);
            } else {
                continue;
            }
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: hoistBoundsCheckBeforeAccShift
    //
    // In the mode7 inner loop, each PIXEL_SHIFT emits:
    //   ld (xsp+S2), xwa          ; S2: store new aY  [XWA = new aY]
    //   ld xwa, (xsp+ACC)         ; L3: load acc (clobbers XWA!)
    //   sla 2, xwa
    //   ld (xsp+SACC), xwa        ; S3: store shifted acc
    //   ld xwa, (xsp+S2)          ; L4: REDUNDANT reload of new aY
    //   or xwa, (xsp+AX)          ; M-OR: bounds check (new aY | new aX)
    //   cp xwa, 020000h
    //   jr C, IB_LABEL
    //   ; OOB path: ld xwa,(xsp+SACC); or xwa,3; ...; jr .NEXT
    //   IB_LABEL: ; in-bounds track lookup
    //
    // The acc-shift (L3+sla2+S3) runs before the bounds check only because of
    // code ordering, but it is not needed until after the branch. Moving the
    // bounds check up (right after S2, where XWA = new aY) eliminates L4 and
    // moves the acc-shift into both paths. On the hot in-bounds path the
    // `ld xwa,(xsp+SACC)` at the start of the OOB path is also eliminated
    // (XWA already holds shifted acc from the newly inserted acc-shift).
    //
    // Savings: ~8 cycles on in-bounds path, ~16 cycles on OOB path per iteration.
    // =========================================================================
    static List<String> hoistBoundsCheckBeforeAccShift(List<String> lines) {
        Pattern pStoreXwa = Pattern.compile(
            "^(\\s+)ld\\s+\\(xsp\\+(\\d+)\\),\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLoadXwa = Pattern.compile(
            "^\\s+ld\\s+xwa,\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pSla2 = Pattern.compile(
            "^\\s+sla\\s+2,\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pOrMem = Pattern.compile(
            "^\\s+or\\s+xwa,\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pCp020000 = Pattern.compile(
            "^\\s+cp\\s+xwa,\\s*020000h\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pJrC = Pattern.compile(
            "^\\s+jrl?\\s+C,\\s*(\\S+)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pJrUncond = Pattern.compile(
            "^\\s+jrl?\\s+(\\S+)\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile(
            "^(\\S[^:]*):.*$");

        List<String> out = new ArrayList<>(lines);
        int i = 0;
        while (i < out.size()) {
            String l = out.get(i);
            if (l == null) { i++; continue; }

            // Match S2: ld (xsp+S2), xwa
            Matcher mS2 = pStoreXwa.matcher(l);
            if (!mS2.matches()) { i++; continue; }
            String indent = mS2.group(1);
            String slotS2 = mS2.group(2);

            // Need at least 8 more lines
            if (i + 7 >= out.size()) { i++; continue; }

            // L3: ld xwa, (xsp+ACC)
            String l1 = out.get(i+1);
            if (l1 == null) { i++; continue; }
            Matcher mL3 = pLoadXwa.matcher(l1);
            if (!mL3.matches()) { i++; continue; }
            String slotAcc = mL3.group(1);

            // sla 2, xwa
            String l2 = out.get(i+2);
            if (l2 == null || !pSla2.matcher(l2).matches()) { i++; continue; }

            // S3: ld (xsp+SACC), xwa
            String l3 = out.get(i+3);
            if (l3 == null) { i++; continue; }
            Matcher mS3 = pStoreXwa.matcher(l3);
            if (!mS3.matches()) { i++; continue; }
            String slotSacc = mS3.group(2);

            // L4: ld xwa, (xsp+S2) — redundant reload of S2
            String l4 = out.get(i+4);
            if (l4 == null) { i++; continue; }
            Matcher mL4 = pLoadXwa.matcher(l4);
            if (!mL4.matches() || !mL4.group(1).equals(slotS2)) { i++; continue; }

            // M-OR: or xwa, (xsp+AX)
            String l5 = out.get(i+5);
            if (l5 == null) { i++; continue; }
            Matcher mOr = pOrMem.matcher(l5);
            if (!mOr.matches()) { i++; continue; }
            String slotAx = mOr.group(1);

            // cp xwa, 020000h
            String l6 = out.get(i+6);
            if (l6 == null || !pCp020000.matcher(l6).matches()) { i++; continue; }

            // jr C, IB_LABEL
            String l7 = out.get(i+7);
            if (l7 == null) { i++; continue; }
            Matcher mJrC = pJrC.matcher(l7);
            if (!mJrC.matches()) { i++; continue; }
            String ibLabel = mJrC.group(1).trim();

            // Verify OOB[0] = ld xwa, (xsp+SACC)  (the acc reload we'll remove)
            if (i + 8 >= out.size()) { i++; continue; }
            String oob0 = out.get(i+8);
            if (oob0 == null) { i++; continue; }
            Matcher mOob0 = pLoadXwa.matcher(oob0);
            if (!mOob0.matches() || !mOob0.group(1).equals(slotSacc)) { i++; continue; }

            // Find OOB path terminator: the unconditional jr .NEXT_BLOCK
            // Scan from i+9 for a `jr .label` or `jrl .label`
            int oobJrIdx = -1;
            int ibLabelIdx = -1;
            for (int j = i + 9; j < out.size() && j < i + 20; j++) {
                String s = out.get(j);
                if (s == null) continue;
                String t = s.strip();
                // Look for the unconditional jump ending the OOB path
                Matcher mJr = pJrUncond.matcher(s);
                if (mJr.matches()) {
                    // Make sure it's not a conditional (already excluded by pJrUncond shape)
                    // pJrUncond matches `jrl? LABEL` — could also be `jr C,...` type
                    // We want the bare unconditional `jr .Lbb_update_NNN` or `jrl .Lbb_update_NNN`
                    String lineContent = t.toLowerCase(Locale.ROOT);
                    if (!lineContent.startsWith("jr c,") && !lineContent.startsWith("jr nc,")
                            && !lineContent.startsWith("jr z,") && !lineContent.startsWith("jr nz,")
                            && !lineContent.startsWith("jr ov,") && !lineContent.startsWith("jr nov,")) {
                        oobJrIdx = j;
                        break;
                    }
                }
            }
            if (oobJrIdx < 0) { i++; continue; }

            // Find IB_LABEL: in the lines after oobJrIdx
            for (int j = oobJrIdx + 1; j < out.size() && j < oobJrIdx + 5; j++) {
                String s = out.get(j);
                if (s == null) continue;
                String t = s.strip();
                if (t.isEmpty() || t.startsWith(";")) continue;
                Matcher mLbl = pLabel.matcher(t);
                if (mLbl.matches() && (t.startsWith(ibLabel) || t.startsWith("." + ibLabel)
                        || t.replace(" ", "").startsWith(ibLabel.replace(".", "") + ":"))) {
                    ibLabelIdx = j;
                    break;
                }
                // The label line might have the label with full name
                if (t.startsWith(ibLabel.replace(".", "").isEmpty() ? ibLabel : ibLabel)) {
                    ibLabelIdx = j;
                    break;
                }
            }
            // Simpler label match: look for a line that starts (trimmed) with ibLabel + ":"
            if (ibLabelIdx < 0) {
                for (int j = oobJrIdx + 1; j < out.size() && j < oobJrIdx + 5; j++) {
                    String s = out.get(j);
                    if (s == null) continue;
                    String t = s.trim();
                    if (t.startsWith(ibLabel + ":") || t.equals(ibLabel + ":")) {
                        ibLabelIdx = j;
                        break;
                    }
                }
            }
            if (ibLabelIdx < 0) { i++; continue; }

            // === Apply transformation ===
            // Build acc-shift snippet
            String accShift0 = indent + "ld xwa, (xsp+" + slotAcc + ")";
            String accShift1 = indent + "sla 2, xwa";
            String accShift2 = indent + "ld (xsp+" + slotSacc + "), xwa";

            // 1. Remove L3 (i+1), sla2 (i+2), S3 (i+3), L4 (i+4) by nulling
            out.set(i+1, null); // L3
            out.set(i+2, null); // sla 2
            out.set(i+3, null); // S3
            out.set(i+4, null); // L4

            // 2. Remove OOB[0]: the ld xwa,(xsp+SACC) at i+8
            out.set(i+8, null);

            // 3. Insert acc-shift at OOB path start (after oobJrIdx-1 nulls, before oob[1])
            //    We insert BEFORE oobJrIdx position (i.e., right after oob0 position, which is now null)
            //    The OOB path goes: oob0(null), oob1, oob2, oob3, oobJr
            //    We need to insert after oob0(null) but before oob1.
            //    oob0 is at index i+8 (now null). oob1 is at i+9.
            //    Insert 3 lines before oob1 = insert at i+9.
            out.add(i + 9, accShift2);
            out.add(i + 9, accShift1);
            out.add(i + 9, accShift0);
            // After adding 3 lines, oobJrIdx shifted by 3, ibLabelIdx shifted by 3
            oobJrIdx += 3;
            ibLabelIdx += 3;

            // 4. Insert acc-shift after the IB_LABEL line
            out.add(ibLabelIdx + 1, accShift2);
            out.add(ibLabelIdx + 1, accShift1);
            out.add(ibLabelIdx + 1, accShift0);

            // 5. Remove nulls now (compact the list)
            out.removeIf(Objects::isNull);

            // Restart from i (the S2 store remains at same position, re-check next block)
            // But S2 is now followed by M-OR,cp,jrC — skip past this match
            // Actually we restart from i since we compacted; advance by 1 to not re-match
            i++;
            continue;
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    static List<String> eliminateOverwrittenSpills(List<String> lines) {
        Pattern functionLabel = Pattern.compile("^_[\\w.$]+:$");
        Pattern frame = Pattern.compile("^add\\s+xsp,\\s*-(\\d+)$");
        Pattern stackAccess = Pattern.compile("\\(xsp\\+(\\d+)\\)");
        Pattern stackStore = Pattern.compile("^ld\\s+\\(xsp\\+(\\d+)\\),\\s*(\\w+)$");
        Pattern stackAdjustment = Pattern.compile("^(?:(?:add|sub)\\s+xsp,\\s*-?\\d+|(?:inc|dec)\\s+\\d+,\\s*xsp)$");
        List<String> code = new ArrayList<>(lines.size());
        for (String line : lines) code.add(line.split(";", 2)[0].strip().toLowerCase(Locale.ROOT));
        Set<Integer> removed = new HashSet<>();
        for (int begin = 0; begin < code.size(); begin++) {
            if (!functionLabel.matcher(code.get(begin)).matches()) continue;
            int end = begin + 1;
            while (end < code.size() && !functionLabel.matcher(code.get(end)).matches()) end++;
            int frameSize = 0, frameLine = -1;
            boolean unsafe = false;
            Map<String, Integer> labels = new HashMap<>();
            for (int index = begin; index < end; index++) {
                String instruction = code.get(index);
                if (instruction.endsWith(":")) labels.put(instruction.substring(0, instruction.length() - 1), index);
                Matcher allocation = frame.matcher(instruction);
                if (allocation.matches()) {
                    if (frameLine >= 0) unsafe = true;
                    frameSize = Integer.parseInt(allocation.group(1));
                    frameLine = index;
                }
                if (instruction.matches(".*\\b(?:xsp|sp)\\b.*")) {
                    String withoutAccess = stackAccess.matcher(instruction).replaceAll("");
                    if (instruction.startsWith("lda ")
                            || (withoutAccess.matches(".*\\b(?:xsp|sp)\\b.*")
                                && !stackAdjustment.matcher(instruction).matches())) unsafe = true;
                }
            }
            if (!unsafe && frameLine >= 0) {
                for (int index = frameLine + 1; index < end; index++) {
                    Matcher store = stackStore.matcher(code.get(index));
                    if (!store.matches()) continue;
                    int offset = Integer.parseInt(store.group(1));
                    int bytes = spillRegisterBytes(store.group(2));
                    if (bytes == 0 || offset > frameSize - bytes) continue;
                    if (spillOverwrittenOnAllPaths(code, index + 1, end, offset, bytes,
                            labels, stackAccess, stackStore)) removed.add(index);
                }
            }
            begin = end - 1;
        }
        List<String> result = new ArrayList<>(lines.size() - removed.size());
        for (int index = 0; index < lines.size(); index++)
            if (!removed.contains(index)) result.add(lines.get(index));
        return result;
    }

    private static int spillRegisterBytes(String register) {
        if (register.matches("x(?:wa|bc|de|hl|ix|iy|iz)[123]?")) return 4;
        if (register.matches("(?:wa|bc|de|hl|ix|iy|iz)")) return 2;
        if (register.matches("[wabcdehl]")) return 1;
        return 0;
    }

    private static boolean spillOverwrittenOnAllPaths(List<String> code, int first, int end,
            int offset, int bytes, Map<String, Integer> labels, Pattern stackAccess, Pattern stackStore) {
        record Visit(int index, int pending) {}
        Pattern branch = Pattern.compile("^(?:jr|jrl|jp)\\s+(?:(\\w+),\\s*)?([.\\w$]+)$");
        Pattern ordinary = Pattern.compile("^(?:ld|ldb|ldw|ldl|lda|add|adc|sub|sbc|and|or|xor|cp|inc|dec|extz|exts|sra|srl|sla|sll|rl|rr|rlc|rrc|bit|set|res|neg|cpl|mul|muls|div|divs|nop|ex)\\b.*$");
        ArrayDeque<Visit> work = new ArrayDeque<>();
        Set<Visit> visited = new HashSet<>();
        work.add(new Visit(first, (1 << bytes) - 1));
        while (!work.isEmpty()) {
            Visit visit = work.removeFirst();
            if (!visited.add(visit)) continue;
            if (visited.size() > 100000 || visit.index() >= end) return false;
            String instruction = code.get(visit.index());
            int pending = visit.pending();
            Matcher jump = branch.matcher(instruction);
            if (jump.matches()) {
                Integer target = labels.get(jump.group(2));
                if (target == null) return false;
                work.add(new Visit(target, pending));
                if (jump.group(1) != null) work.add(new Visit(visit.index() + 1, pending));
                continue;
            }
            if (!instruction.isEmpty() && !instruction.endsWith(":")) {
                if (!ordinary.matcher(instruction).matches()
                        || instruction.matches(".*\\b(?:xsp|sp)\\b.*")
                            && !stackAccess.matcher(instruction).find()) return false;
                Matcher store = stackStore.matcher(instruction);
                int width = store.matches() ? spillRegisterBytes(store.group(2)) : 0;
                if (width > 0) {
                    int written = Integer.parseInt(store.group(1));
                    for (int index = 0; index < bytes; index++)
                        if (offset + index >= written && offset + index < written + width)
                            pending &= ~(1 << index);
                    if (pending == 0) continue;
                } else {
                    Matcher access = stackAccess.matcher(instruction);
                    while (access.find()) {
                        int read = Integer.parseInt(access.group(1));
                        for (int index = 0; index < bytes; index++)
                            if ((pending & (1 << index)) != 0
                                    && offset + index >= read && offset + index < read + 4) return false;
                    }
                }
            }
            work.add(new Visit(visit.index() + 1, pending));
        }
        return true;
    }

    // =========================================================================
    // Pass: eliminateDeadPhiOutputStores
    // Within each assembly function, remove stack stores that are written but
    // never read — SSA phi-output slots the transpiler emits alongside the
    // phi-input slot (the only one actually consumed by successor blocks).
    //
    // Safety: a store is only eliminated if no post-prologue push/pop/pushw
    // instruction appears AFTER its line within the function. Any push/pop after
    // a store could shift the SP so that the store's data is subsequently read at
    // a different raw offset; eliminating such a store could corrupt the function.
    // In practice this restricts the pass to the inner-loop body of _update (where
    // all push/pop sequences precede the phi-output dead stores).
    // =========================================================================
    static List<String> eliminateDeadPhiOutputStores(List<String> lines) {
        Pattern pWrite    = Pattern.compile("^\\s+ld\\s+\\(xsp\\+(\\d+)\\)\\s*,\\s*\\w+");
        Pattern pReadAny  = Pattern.compile("\\(xsp\\+(\\d+)\\)");
        Pattern pFuncLabel= Pattern.compile("^\\w+:\\s*$");
        Pattern pPrologue = Pattern.compile("^\\s+add\\s+xsp\\s*,\\s*-\\d+");
        // Any push after a store (SP moves down) could shift subsequent reads to a larger
        // raw offset, making a live store look dead. Pop/popw moves SP up, which would
        // require reads at smaller raw offsets — safe to ignore for our dead-store patterns.
        Pattern pSpMover  = Pattern.compile("^\\s+(push|pushw)");

        // Find function boundaries
        List<int[]> funcRanges = new ArrayList<>();
        int funcStart = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (pFuncLabel.matcher(lines.get(i)).matches()) {
                if (funcStart >= 0) funcRanges.add(new int[]{funcStart, i});
                funcStart = i;
            }
        }
        if (funcStart >= 0) funcRanges.add(new int[]{funcStart, lines.size()});

        Set<Integer> toRemove = new HashSet<>();

        for (int[] range : funcRanges) {
            // Collect all writes (raw slot → line indices) and reads (raw slot set)
            Map<Integer, List<Integer>> writeLines = new HashMap<>();
            Set<Integer> readSlots = new HashSet<>();
            for (int i = range[0]; i < range[1]; i++) {
                String line = lines.get(i);
                Matcher mw = pWrite.matcher(line);
                if (mw.find()) {
                    writeLines.computeIfAbsent(Integer.parseInt(mw.group(1)), k -> new ArrayList<>()).add(i);
                    continue; // a store line is not also a read
                }
                Matcher mr = pReadAny.matcher(line);
                while (mr.find()) readSlots.add(Integer.parseInt(mr.group(1)));
            }

            // Candidate dead slots: written but never read (naive, ignoring SP movement)
            Set<Integer> deadCandidates = new HashSet<>(writeLines.keySet());
            deadCandidates.removeAll(readSlots);
            if (deadCandidates.isEmpty()) continue;

            // Safety: find the earliest prologue line; we only care about post-prologue SP movers
            int prologueLine = range[1]; // default: no prologue found
            for (int i = range[0]; i < range[1]; i++) {
                if (pPrologue.matcher(lines.get(i)).find()) { prologueLine = i; break; }
            }

            // For each candidate dead slot, check if any post-prologue SP-moving instruction
            // appears AFTER the first write of that slot. If yes, conservatively skip it.
            for (int slot : new HashSet<>(deadCandidates)) {
                List<Integer> wlines = writeLines.get(slot);
                int firstWrite = wlines.stream().mapToInt(x -> x).min().getAsInt();
                if (firstWrite <= prologueLine) continue; // pre-prologue write, always safe

                // Check for any SP mover after firstWrite within this function
                boolean hasMoverAfter = false;
                for (int i = firstWrite + 1; i < range[1]; i++) {
                    if (i <= prologueLine) continue; // only post-prologue movers matter
                    if (pSpMover.matcher(lines.get(i)).find()) { hasMoverAfter = true; break; }
                }
                if (hasMoverAfter) deadCandidates.remove(slot);
            }

            // Schedule write lines for dead slots
            for (int slot : deadCandidates) {
                toRemove.addAll(writeLines.get(slot));
            }
        }

        if (toRemove.isEmpty()) return lines;
        List<String> out = new ArrayList<>(lines.size() - toRemove.size());
        for (int i = 0; i < lines.size(); i++) {
            if (!toRemove.contains(i)) out.add(lines.get(i));
        }
        return out;
    }

    // =========================================================================
    // Pass: removeUnusedFrame
    // Remove `add xsp, -N` / `add xsp, N` frame allocations when the body
    // never accesses any (xsp+K) slot. Handles early-return or early-reti paths
    // as long as every exit point that has `add xsp, N` is also removed.
    //
    // Safety: only fires when:
    //   1. Exactly one `add xsp, -N` (the allocation) is present.
    //   2. No `(xsp+` appears anywhere between that line and the end of the function.
    //   3. Every `add xsp, N` (deallocation) that matches the allocation size
    //      is removed together with the allocation.
    //
    // Pattern from SL_SoundInit:
    //   add xsp, -4   ; frame: 4 bytes   ← no (xsp+K) in body
    //   ...
    //   add xsp, 4    ; free frame        ← removed
    //   ret
    // =========================================================================
    static List<String> removeUnusedFrame(List<String> lines) {
        Pattern pLabel    = Pattern.compile("^_[\\w.$]+:\\s*$");
        Pattern pFrameAlloc  = Pattern.compile("^\\s+add\\s+xsp,\\s*(-\\d+)\\s*;\\s*frame:.*$");
        Pattern pFrameFree   = Pattern.compile("^\\s+add\\s+xsp,\\s*(\\d+)\\s*;\\s*free frame\\s*$");
        Pattern pXspAccess   = Pattern.compile("\\bxsp\\b");

        List<String> out = new ArrayList<>(lines.size());
        int i = 0;
        while (i < lines.size()) {
            // Collect a single function body: from label to (but not including) next label.
            String labelLine = lines.get(i);
            if (!pLabel.matcher(labelLine).matches()) { out.add(labelLine); i++; continue; }

            int start = i + 1;
            int end = lines.size();
            for (int k = start; k < lines.size(); k++) {
                if (pLabel.matcher(lines.get(k)).matches()) { end = k; break; }
            }

            // Scan body for a single frame-alloc, and whether any (xsp+ access exists.
            int allocLine = -1;
            int allocSize = 0;
            boolean hasXspAccess = false;
            for (int k = start; k < end; k++) {
                String raw = lines.get(k);
                String t = raw.trim();
                Matcher ma = pFrameAlloc.matcher(raw);
                if (ma.matches()) {
                    if (allocLine != -1) { allocLine = -1; break; } // more than one alloc: bail
                    allocLine = k;
                    allocSize = -Integer.parseInt(ma.group(1)); // -N → N
                }
                if (!ma.matches() && !pFrameFree.matcher(raw).matches()
                        && pXspAccess.matcher(t.split(";", 2)[0]).find()) {
                    hasXspAccess = true;
                    break;
                }
            }

            if (allocLine == -1 || hasXspAccess || allocSize <= 0) {
                // Not applicable — copy verbatim
                for (int k = i; k < end; k++) out.add(lines.get(k));
                i = end; continue;
            }

            // Find all matching dealloc lines
            Set<Integer> dropLines = new LinkedHashSet<>();
            dropLines.add(allocLine);
            for (int k = start; k < end; k++) {
                if (k == allocLine) continue;
                Matcher mf = pFrameFree.matcher(lines.get(k));
                if (mf.matches() && Integer.parseInt(mf.group(1)) == allocSize)
                    dropLines.add(k);
            }

            out.add(labelLine);
            for (int k = start; k < end; k++) {
                if (!dropLines.contains(k)) out.add(lines.get(k));
            }
            i = end;
        }
        return out;
    }

    // =========================================================================
    // Pass: eliminateRedundantRegLoads  (unified CSE for constant register loads)
    //
    // Eliminates any `lda xreg, LABEL`, `ld xreg, LABEL`, or `ld xreg/reg, K`
    // where the register already holds the same value and hasn't been clobbered.
    //
    // Tracked registers: xwa (+ sub-regs wa/w/a), xhl (+ hl/h/l),
    //                    xbc (+ bc/b/c), xde (+ de/d/e), xix (+ix), xiy (+iy).
    //
    // Key stored per register: "lda:<label>", "ld:<label>", or "imm:<reg>:<val>"
    // so that lda vs ld vs numeric immediate don't collide.
    //
    // Clobber rules:
    //   - Any instruction whose first-operand destination is a tracked (sub-)reg
    //   - pop xreg / pop reg
    //   - call  → clobbers all primary regs (library fns like SetSprite don't save xhl)
    //   - ldir/ldirw/ldi/ldib → auto-increments (Rreg+), clobbers that reg
    //   - Any label (function or BB) resets all tracking
    // =========================================================================
    static List<String> eliminateRedundantRegLoads(List<String> lines) {
        // lda xreg, LABEL
        Pattern pLda    = Pattern.compile("^\\s*lda\\s+(x\\w+),\\s*([_.]\\S+)\\s*$");
        // ld xreg, LABEL  (symbol address; not a numeric)
        Pattern pLdLbl  = Pattern.compile("^\\s*ld\\s+(x\\w+),\\s*([_.]\\S+)\\s*$");
        // ld xreg/reg, K  (numeric immediate, hex or decimal; not memory, not register)
        Pattern pLdImm  = Pattern.compile("^\\s*ld\\s+(x?\\w+),\\s*([0-9][0-9a-fA-FhH]*)\\s*$");
        // Any label: reset all tracking
        Pattern pLabel  = Pattern.compile("^[_.]\\S+:.*");
        // First-operand destination extractor
        Pattern pDst    = Pattern.compile("^\\s*\\w+\\s+(x?\\w+)(?:\\s*,|\\s*$)");
        Pattern pSccDst = Pattern.compile("^scc\\s+[^,]+,\\s*(\\w+)$");

        // Sub-register → 32-bit parent map (conservative: any write to a sub-reg
        // invalidates the parent's known value)
        Map<String, String> subToParent = new java.util.LinkedHashMap<>();
        for (String[] group : new String[][]{
                {"xwa","wa","w","a"}, {"xhl","hl","h","l"},
                {"xbc","bc","b","c"}, {"xde","de","d","e"},
                {"xix","ix"},         {"xiy","iy"}}) {
            String parent = group[0];
            for (String sub : group) subToParent.put(sub, parent);
        }

        Map<String, String> known = new java.util.LinkedHashMap<>();
        List<String> out = new ArrayList<>(lines.size());

        for (String line : lines) {
            String t = line.trim();

            // Reset everything at any label
            if (pLabel.matcher(t).matches()) {
                known.clear(); out.add(line); continue;
            }

            // Blank / comment
            if (t.isEmpty() || t.startsWith(";")) { out.add(line); continue; }

            // --- lda xreg, LABEL ---
            Matcher m = pLda.matcher(t);
            if (m.matches()) {
                String parent = subToParent.get(m.group(1).toLowerCase());
                if (parent != null) {
                    String key = "lda:" + m.group(2);
                    if (key.equals(known.get(parent))) continue;
                    known.put(parent, key);
                }
                out.add(line); continue;
            }

            // --- ld xreg, LABEL ---
            m = pLdLbl.matcher(t);
            if (m.matches()) {
                String parent = subToParent.get(m.group(1).toLowerCase());
                if (parent != null) {
                    String key = "ld:" + m.group(2);
                    if (key.equals(known.get(parent))) continue;
                    known.put(parent, key);
                }
                out.add(line); continue;
            }

            // --- ld xreg/reg, K ---
            m = pLdImm.matcher(t);
            if (m.matches()) {
                String sub    = m.group(1).toLowerCase();
                String parent = subToParent.get(sub);
                if (parent != null) {
                    // Key encodes both the sub-register name and the value so that
                    // `ld wa, 0` and `ld xwa, 0` are treated as distinct loads
                    // (they have different widths; only an exact same-reg match is safe).
                    String key = "imm:" + sub + ":" + m.group(2);
                    if (key.equals(known.get(parent))) continue;
                    known.put(parent, key);
                }
                out.add(line); continue;
            }

            // --- call: invalidate all primary registers ---
            // Transpiler-generated functions push/pop their used regs, but library
            // functions (SetSprite, Print, SL_PlaySFX, etc.) compiled with cc900
            // clobber xhl/xbc/xde without saving — so we cannot assume any primary
            // register survives a call.  Bank-1/2 regs (xwa1…) are not tracked here.
            if (t.startsWith("call ") || t.equals("call")) {
                known.clear(); out.add(line); continue;
            }

            // --- Block-move family: ldi/ldir/ldirw/ldib/ldirb, ldd/lddr/lddrw/lddb/lddrb ---
            // All forms: BC = repeat counter (consumed to 0); pointer regs auto-±1.
            // Pointer regs appear as (Rreg+) or (Rreg-) in the operands.
            if (t.matches("(?i)(?:ldi(?:rw|r|w|rb|b)?|ldd(?:rw|r|w|rb|b)?)\\b.*")) {
                java.util.regex.Matcher mAuto = Pattern.compile("\\(([a-z]+)[+-]\\)").matcher(t);
                while (mAuto.find()) {
                    String p = subToParent.get(mAuto.group(1));
                    if (p != null) known.remove(p);
                }
                known.remove("xbc"); // BC is the repeat counter, consumed to 0
                out.add(line); continue;
            }

            // --- General instruction: invalidate destination register ---
            Matcher sccDst = pSccDst.matcher(t);
            if (sccDst.matches()) {
                String parent = subToParent.get(sccDst.group(1).toLowerCase());
                if (parent != null) known.remove(parent);
            }
            // push/pushw are reads (not writes) — skip pDst for them.
            if (!t.startsWith("push") && !t.startsWith("pushw")) {
                Matcher mDst = pDst.matcher(t);
                if (mDst.find()) {
                    String p = subToParent.get(mDst.group(1).toLowerCase());
                    if (p != null) known.remove(p);
                }
            }
            // pop xreg writes the register (pDst above handles it, but be explicit)
            if (t.startsWith("pop ")) {
                String p = subToParent.get(t.substring(4).trim().toLowerCase());
                if (p != null) known.remove(p);
            }

            out.add(line);
        }
        return out;
    }

    // =========================================================================
    // Pass: narrowIsrPushes
    // In an ISR body, replace `push xwa` / `pop xwa` with `push wa` / `pop wa`
    // when only the lower 16 bits of XWA (wa/a/w) are ever written in the body.
    // Saves 2 bytes per push/pop pair.
    // Similarly xhl→hl, xde→de, xbc→bc (xix/xiy have no narrow form).
    //
    // Detection: a function whose ISR-prologue consists of push xwa/xhl/... and
    // whose epilogue pops them before reti. Only narrows when the upper half of
    // the 32-bit register is demonstrably never written (no ld xwa,... etc.).
    // =========================================================================
    static List<String> narrowIsrPushes(List<String> lines) {
        // Pairs: wide reg → narrow reg (only these four have a 16-bit narrow form)
        Map<String, String> narrow = Map.of("xwa","wa", "xhl","hl", "xde","de", "xbc","bc");
        // Patterns that write the upper 16 bits of a 32-bit reg (force keep wide)
        // ld xwa, ... or any 32-bit op with xwa as dest
        // We use a simple conservative rule: if "xwa" appears as a destination
        // anywhere in the body (excluding push/pop) → upper half may be written.
        Pattern pIsrLabel = Pattern.compile("^_[\\w.$]+:\\s*$");

        List<String> out = new ArrayList<>(lines.size());
        int i = 0;
        while (i < lines.size()) {
            String labelLine = lines.get(i);
            if (!pIsrLabel.matcher(labelLine).matches()) { out.add(labelLine); i++; continue; }

            // Found a function label. Collect lines until next function label.
            int start = i + 1;
            int end = lines.size();
            for (int k = start; k < lines.size(); k++) {
                if (pIsrLabel.matcher(lines.get(k)).matches()) { end = k; break; }
            }

            // Check ISR prologue: leading push xwa [xhl xde xbc xix xiy] (subset)
            List<Integer> pushLines = new ArrayList<>();
            List<String>  pushRegs  = new ArrayList<>();
            int j = start;
            while (j < end) {
                String t = lines.get(j).trim();
                if (t.isEmpty() || t.startsWith(";")) { j++; continue; }
                Matcher m = Pattern.compile("^push\\s+(xwa|xhl|xde|xbc|xix|xiy)$").matcher(t);
                if (m.matches()) { pushLines.add(j); pushRegs.add(m.group(1)); j++; }
                else break;
            }
            // Also check for frame sub (`add xsp, -N`) right after pushes
            if (j < end) {
                String t = lines.get(j).trim();
                if (t.startsWith("add xsp, -")) j++;
            }
            int bodyStart = j;

            // Must have at least one ISR push and at least one reti to qualify
            boolean hasReti = false;
            for (int k = bodyStart; k < end; k++) {
                if (lines.get(k).trim().equals("reti")) { hasReti = true; break; }
            }
            if (pushRegs.isEmpty() || !hasReti) {
                for (int k = i; k < end; k++) out.add(lines.get(k));
                i = end; continue;
            }

            // For each pushed wide reg, check if body ever writes its upper 16 bits
            // Conservative: any "ld xwa,..." OR any op with "xwa" as first operand
            // (other than push/pop) is considered a wide write.
            Set<String> keepWide = new HashSet<>();
            for (String reg : pushRegs) {
                if (!narrow.containsKey(reg)) { keepWide.add(reg); continue; }
                String wideReg = reg;          // e.g. "xwa"
                boolean wideWrite = false;
                for (int k = bodyStart; k < end && !wideWrite; k++) {
                    String t = lines.get(k).trim().toLowerCase();
                    if (t.startsWith("push ") || t.startsWith("pop ")) continue;
                    // Does this instruction write the wide (32-bit) form?
                    // Pattern: `ld xwa, ...` or any ALU where xwa is the dest (first op)
                    if (t.matches(".*\\b" + wideReg + "\\s*,.*") &&
                        !t.startsWith("cp ") && !t.startsWith("or ") &&
                        !t.startsWith("and ") && !t.startsWith("xor ") &&
                        !t.startsWith("add " + wideReg.replace("x","x?")) ) {
                        // It's a write to the wide reg
                        // Exception: `ld xwa, (xsp+N)` and arithmetic targeting xwa
                        // are definitely wide writes
                        wideWrite = true;
                    }
                    // Also: explicit ld xwa/xhl/xde/xbc from anywhere
                    if (t.startsWith("ld " + wideReg + ",")) wideWrite = true;
                    // add/sub/and/or/xor/sla/srl/etc. targeting xwa
                    if (t.matches("(add|sub|and|or|xor|sla|srl|sra|mul|muls|exts|extz)\\s+" + wideReg + "\\s*,.*"))
                        wideWrite = true;
                }
                if (wideWrite) keepWide.add(reg);
            }

            // Now rewrite: replace push/pop of narrowable regs
            // Build set of line indices to rewrite (push lines already known; find pop lines)
            Map<String, Integer> pushLineMap = new HashMap<>();
            for (int k = 0; k < pushLines.size(); k++) pushLineMap.put(pushRegs.get(k), pushLines.get(k));
            Map<String, List<Integer>> popLinesMap = new HashMap<>();
            for (String reg : pushRegs) popLinesMap.put(reg, new ArrayList<>());
            for (int k = bodyStart; k < end; k++) {
                String t = lines.get(k).trim();
                for (String reg : pushRegs) {
                    if (t.equals("pop " + reg)) popLinesMap.get(reg).add(k);
                }
            }

            // Copy all lines in this function, substituting where needed
            out.add(labelLine);
            for (int k = start; k < end; k++) {
                String line = lines.get(k);
                String t    = line.trim();
                boolean replaced = false;
                for (String reg : pushRegs) {
                    if (keepWide.contains(reg)) continue;
                    String nreg = narrow.get(reg);
                    if (t.equals("push " + reg) && pushLineMap.containsKey(reg) && pushLineMap.get(reg) == k) {
                        out.add("\tpush " + nreg); replaced = true; break;
                    }
                    if (t.equals("pop " + reg) && popLinesMap.get(reg).contains(k)) {
                        out.add("\tpop " + nreg); replaced = true; break;
                    }
                }
                if (!replaced) out.add(line);
            }
            i = end;
        }
        return out;
    }

    // =========================================================================
    // Pass: foldXhlAccumulatorRoundtrip
    // Folds:  ld xhl, xwa          into:  add xwa, <mem>
    //         add xhl, <mem>
    //         ld xwa, xhl
    // XHL is loaded from XWA (clobbering its prior value), used as scratch for
    // one ALU mem-operand op, then written back into XWA — the round-trip is
    // unnecessary when the op supports XWA as destination directly.
    // Handles add/sub/adc/sbc/and/or/xor with any memory source operand.
    // =========================================================================
    static List<String> forwardPhiAccumulators(List<String> lines) {
        List<String> result = lines;
        while (true) {
            List<String> next = forwardPhiAccumulator(result);
            if (next == result) return result;
            result = next;
        }
    }

    static List<String> forwardPhiAccumulator(List<String> lines) {
        List<String> code = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String instruction = lines.get(index).split(";", 2)[0].strip();
            if (instruction.isEmpty()) continue;
            code.add(instruction);
            positions.add(index);
        }
        Pattern function = Pattern.compile("^_[\\w.$]+:$");
        Pattern load = Pattern.compile("^ld xwa, \\(xsp\\+(\\d+)\\)$");
        Pattern access = Pattern.compile("\\(xsp\\+(\\d+)\\)");
        Pattern scratch = Pattern.compile("(?<![\\w])(?:xhl|hl|h|l)(?![\\w])");
        Pattern register = Pattern.compile("x(?:wa|bc|de|hl|ix|iy|iz)[12]?");
        for (int join = 0; join + 7 < code.size(); join++) {
            if (!code.get(join).matches("^\\.[\\w.$]+:$")) continue;
            boolean safe = true;
            for (int step = 1; step <= 6; step++) {
                String instruction = code.get(join + step);
                String expected = switch ((step - 1) % 3) {
                    case 0 -> "ld xwa, ";
                    case 1 -> "add xwa, ";
                    default -> "";
                };
                if (step % 3 == 0) {
                    if (!instruction.matches("ld (?:x(?:wa|bc|de|hl|ix|iy|iz)[12]?|\\(xsp\\+\\d+\\)), xwa")) safe = false;
                } else if (!instruction.startsWith(expected)
                        || !register.matcher(instruction.substring(expected.length())).matches()) safe = false;
                if (scratch.matcher(instruction).find()) safe = false;
            }
            Matcher reload = load.matcher(code.get(join + 7));
            if (!safe || !reload.matches()) continue;
            int offset = Integer.parseInt(reload.group(1));
            int begin = join;
            while (begin >= 0 && !function.matcher(code.get(begin)).matches()) begin--;
            if (begin < 0) continue;
            int end = join + 1;
            while (end < code.size() && !function.matcher(code.get(end)).matches()) end++;
            String target = code.get(join).substring(0, code.get(join).length() - 1);
            String store = "ld (xsp+" + offset + "), xwa";
            List<Integer> stores = new ArrayList<>();
            Set<Integer> predecessors = new HashSet<>();
            Map<String, Integer> labels = new HashMap<>();
            Set<Integer> balancedStackOperations = new HashSet<>();
            List<String> division = List.of("push xde", "push xwa", "push xhl", "call _C9H_divls",
                    "inc 8, xsp", "ld xwa, xhl", "pop xde");
            for (int index = begin; index + division.size() <= end; index++) {
                if (code.subList(index, index + division.size()).equals(division)) {
                    for (int step : List.of(0, 1, 2, 4, 6)) balancedStackOperations.add(index + step);
                }
            }
            int frameSize = 0;
            int frameStart = -1;
            int frameEnd = -1;
            for (int index = begin; index < end && safe; index++) {
                String instruction = code.get(index);
                if (instruction.endsWith(":")) labels.put(instruction.substring(0, instruction.length() - 1), index);
                if (instruction.matches("add xsp, -\\d+")) {
                    if (frameStart >= 0) safe = false;
                    frameSize = Integer.parseInt(instruction.substring(10));
                    frameStart = index;
                } else if (instruction.equals("add xsp, " + frameSize) && frameStart >= 0 && frameEnd < 0) {
                    frameEnd = index;
                } else if (instruction.matches("(?:pushw?|pop)\\s+.*") && frameStart >= 0 && frameEnd < 0
                    && !balancedStackOperations.contains(index)) {
                    safe = false;
                }
                String withoutAccess = access.matcher(instruction).replaceAll("");
                if (instruction.startsWith("lda ") && access.matcher(instruction).find()
                        || withoutAccess.matches(".*\\b(?:xsp|sp)\\b.*")
                            && !(index == frameStart || index == frameEnd
                                || balancedStackOperations.contains(index))) safe = false;
                Matcher stack = access.matcher(instruction);
                while (stack.find()) {
                    int other = Integer.parseInt(stack.group(1));
                    if (other < offset + 4 && other + 4 > offset) {
                        if (instruction.equals(store)) stores.add(index);
                        else if (index != join + 7) safe = false;
                    }
                }
                if (instruction.contains(target) && index != join) {
                    if (!(instruction.equals("jr " + target) || instruction.equals("jrl " + target))
                            || index == begin || !code.get(index - 1).equals(store)) safe = false;
                    else predecessors.add(index - 1);
                }
                if (instruction.matches("(?:jp|jr|jrl|djnz)\\s+.*")
                        && !instruction.matches("(?:jr|jrl)\\s+(?:\\w+,\\s*)?\\.[\\w.$]+")) safe = false;
            }
            if (!safe || offset > frameSize - 4 || frameEnd <= join + 7 || stores.size() != 3
                    || !code.get(join - 1).equals(store)) continue;
            predecessors.add(join - 1);
            if (!predecessors.equals(new HashSet<>(stores))) continue;
            for (int index : stores) {
                if (index <= frameStart || index >= frameEnd) safe = false;
                if (index + 1 != join && !(code.get(index + 1).equals("jr " + target)
                        || code.get(index + 1).equals("jrl " + target))) safe = false;
            }
            if (!safe || !scratchDeadOnAllPaths(code, join + 8, end, labels, scratch)) continue;
            List<String> result = new ArrayList<>(lines);
            for (int index : stores) result.set(positions.get(index), "\tld xhl, xwa");
            result.set(positions.get(join + 7), "\tld xwa, xhl");
            return result;
        }
        return lines;
    }

    static List<String> forwardShiftedAccumulators(List<String> lines) {
        List<String> result = lines;
        while (true) {
            List<String> next = forwardShiftedAccumulator(result);
            if (next == result) return result;
            result = next;
        }
    }

    static List<String> forwardShiftedAccumulator(List<String> lines) {
        List<String> code = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String instruction = lines.get(index).split(";", 2)[0].strip();
            if (instruction.startsWith("jrl ")) instruction = "jr " + instruction.substring(4);
            if (instruction.isEmpty()) continue;
            code.add(instruction);
            positions.add(index);
        }
        Pattern store = Pattern.compile("ld (\\(xsp\\+(\\d+)\\)), xwa");
        Pattern function = Pattern.compile("^_[\\w.$]+:$");
        Pattern scratch = Pattern.compile("(?<![\\w])(?:xbc2|[qr]?bc2|[qr]?[bc]2)(?![\\w])");
        for (int first = 1; first + 40 < code.size(); first++) {
            Matcher spill = store.matcher(code.get(first));
            if (!spill.matches() || !code.get(first - 1).equals("sla 2, xwa")) continue;
            String slot = spill.group(1);
            String inside = code.get(first + 9);
            String plain = code.get(first + 28);
            String join = code.get(first + 31);
            if (!inside.matches("\\.[\\w.$]+:") || !plain.matches("\\.[\\w.$]+:")
                    || !join.matches("\\.[\\w.$]+:") || inside.equals(plain)
                    || inside.equals(join) || plain.equals(join)) continue;
            List<String> expected = List.of(
                "ld " + slot + ", xwa", "ld xwa, xde1", "or xwa, xde2", "cp xwa, 020000h",
                "jr C, " + inside.substring(0, inside.length() - 1), "ld xwa, " + slot, "or xwa, 3",
                "ld xhl, xwa", "jr " + join.substring(0, join.length() - 1), inside,
                "ld xwa, xde2", "srl 8, xwa", "ld xhl, xbc", "add xhl, xwa", "ld xwa, xde1",
                "sla 1, xwa", "and xwa, 03fe00h", "add xhl, xwa", "ld a, (xhl+0)", "extz wa",
                "extz xwa", "bit 2, a", "jr Z, " + plain.substring(0, plain.length() - 1),
                "sla 16, xwa", "add xwa, 0fffd0000h", "or xwa, " + slot, "ld xhl, xwa",
                "jr " + join.substring(0, join.length() - 1), plain, "or xwa, " + slot,
                "ld xhl, xwa", join, "ld xwa, xde2", "add xwa, xde", "ld xde2, xwa",
                "ld xwa, xde1", "add xwa, xix", "ld xde1, xwa", "ld xwa, xhl", "sla 2, xwa",
                "ld " + slot + ", xwa");
            if (!code.subList(first, first + expected.size()).equals(expected)) continue;
            int begin = first;
            while (begin >= 0 && !function.matcher(code.get(begin)).matches()) begin--;
            if (begin < 0) continue;
            int end = first + expected.size();
            while (end < code.size() && !function.matcher(code.get(end)).matches()) end++;
            boolean safe = true;
            Map<String, Integer> labels = new HashMap<>();
            int frameSize = 0;
            int frameStart = -1;
            int frameEnd = -1;
            Set<Integer> balancedStackOperations = new HashSet<>();
            for (int index = begin; index + 7 <= end; index++) {
                if (divisionFallbackAt(code, index)) {
                    for (int step : List.of(0, 1, 2, 4, 6)) balancedStackOperations.add(index + step);
                }
            }
            for (int index = begin; index < end && safe; index++) {
                String instruction = code.get(index);
                if (instruction.endsWith(":")) labels.put(instruction.substring(0, instruction.length() - 1), index);
                if (instruction.matches("add xsp, -\\d+")) {
                    if (frameStart >= 0) safe = false;
                    frameStart = index;
                    frameSize = Integer.parseInt(instruction.substring(10));
                } else if (instruction.equals("add xsp, " + frameSize) && frameStart >= 0 && frameEnd < 0) {
                    frameEnd = index;
                } else if (instruction.matches("(?:pushw?|pop)\\s+.*") && frameStart >= 0 && frameEnd < 0
                        && !balancedStackOperations.contains(index)) safe = false;
                if (instruction.startsWith("lda ") && instruction.contains("xsp")
                        || instruction.replaceAll("\\(xsp\\+\\d+\\)", "").matches(".*\\b(?:xsp|sp)\\b.*")
                            && !(index == frameStart || index == frameEnd
                                || balancedStackOperations.contains(index))) safe = false;
                if (instruction.matches("(?:jp|jr|jrl|djnz)\\s+.*")
                        && !instruction.matches("(?:jr|jrl)\\s+(?:\\w+,\\s*)?\\.[\\w.$]+")) safe = false;
                for (String label : List.of(inside, plain, join)) {
                    String target = label.substring(0, label.length() - 1);
                    if (instruction.contains(target) && (index < first || index >= first + 32)) safe = false;
                }
            }
                    if (!safe || frameStart >= first || frameEnd <= first + 40
                    || Integer.parseInt(spill.group(2)) > frameSize - 4
                    || !scratchDeadOnAllPaths(code, first + 32, end, labels, scratch, "xbc2")) continue;
            List<String> result = new ArrayList<>(lines);
            result.set(positions.get(first), "\tld xbc2, xwa");
            result.set(positions.get(first + 5), "\tld xwa, xbc2");
            result.set(positions.get(first + 25), "\tor xwa, xbc2");
            result.set(positions.get(first + 29), "\tor xwa, xbc2");
            return result;
        }
        return lines;
    }

    static List<String> rescheduleAccumulatorCoordinates(List<String> lines) {
        List<String> code = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String instruction = lines.get(index).split(";", 2)[0].strip();
            if (instruction.isEmpty()) continue;
            code.add(instruction);
            positions.add(index);
        }
        Pattern function = Pattern.compile("^_[\\w.$]+:$");
        Pattern register = Pattern.compile("(?:x(?:bc|de|ix|iy|iz)[12]?|x(?:wa|hl)[12])");
        Pattern destination = Pattern.compile("(?:" + register.pattern() + "|\\(xsp\\+\\d+\\))");
        Pattern scratch = Pattern.compile("(?<![\\w])(?:xhl|hl|h|l)(?![\\w])");
        for (int join = 1; join + 7 < code.size(); join++) {
            if (!code.get(join).matches("\\.[\\w.$]+:")
                    || !code.get(join - 1).equals("ld xhl, xwa")
                    || !code.get(join + 7).equals("ld xwa, xhl")) continue;
            boolean safe = true;
            for (int step = 1; step <= 6; step++) {
                String instruction = code.get(join + step);
                String prefix = step % 3 == 1 ? "ld xwa, " : "add xwa, ";
                if (step % 3 == 0) {
                    if (!instruction.endsWith(", xwa")
                            || !instruction.startsWith("ld ")
                            || !destination.matcher(instruction.substring(3, instruction.length() - 5)).matches()) safe = false;
                } else if (!instruction.startsWith(prefix)
                        || !register.matcher(instruction.substring(prefix.length())).matches()) safe = false;
            }
            if (!safe) continue;
            int begin = join;
            while (begin >= 0 && !function.matcher(code.get(begin)).matches()) begin--;
            if (begin < 0) continue;
            int end = join + 8;
            while (end < code.size() && !function.matcher(code.get(end)).matches()) end++;
            String target = code.get(join).substring(0, code.get(join).length() - 1);
            List<Integer> copies = new ArrayList<>();
            copies.add(join - 1);
            Map<String, Integer> labels = new HashMap<>();
            for (int index = begin; index < end; index++) {
                String instruction = code.get(index);
                if (instruction.endsWith(":")) labels.put(instruction.substring(0, instruction.length() - 1), index);
                if (instruction.contains(target) && index != join) {
                    if (!(instruction.equals("jr " + target) || instruction.equals("jrl " + target))
                            || index <= begin || !code.get(index - 1).equals("ld xhl, xwa")) safe = false;
                    else copies.add(index - 1);
                }
                if (instruction.matches("(?:jp|jr|jrl|djnz)\\s+.*")
                        && !instruction.matches("(?:jr|jrl)\\s+(?:\\w+,\\s*)?\\.[\\w.$]+")) safe = false;
            }
            if (!safe || copies.size() != 3 || new HashSet<>(copies).size() != 3
                    || !scratchDeadOnAllPaths(code, join + 8, end, labels, scratch)) continue;
            List<String> result = new ArrayList<>(lines);
            for (int step = 1; step <= 6; step++) {
                result.set(positions.get(join + step), "\t" + code.get(join + step).replaceAll("\\bxwa\\b", "xhl"));
            }
            Set<Integer> removed = new HashSet<>();
            for (int copy : copies) removed.add(positions.get(copy));
            removed.add(positions.get(join + 7));
            List<String> filtered = new ArrayList<>();
            for (int index = 0; index < result.size(); index++) {
                if (!removed.contains(index)) filtered.add(result.get(index));
            }
            return rescheduleAccumulatorCoordinates(filtered);
        }
        return lines;
    }

    private static boolean divisionFallbackAt(List<String> code, int index) {
        List<String> division = List.of("push xde", "push xwa", "push xhl", "call _C9H_divls",
                "inc 8, xsp", "ld xwa, xhl", "pop xde");
        return index + division.size() <= code.size()
                && code.subList(index, index + division.size()).equals(division);
    }

    private static boolean divisionPreservesBank(List<String> code) {
        Pattern function = Pattern.compile("^_[\\w.$]+:$");
        Pattern bank = Pattern.compile("(?<![\\w])(?:x?[a-z]+[123]|r[bcdehlwa]+[123]|q[bcdehlwa]+[123]|sr|rfp)(?![\\w])");
        Pattern ordinary = Pattern.compile("^(?:ld|add|sub|adc|sbc|and|or|xor|cp|sla|sra|srl|sll|rl|rr|rlc|rrc|"
                + "extz|exts|bit|inc|dec|divs?|push|pop|ret)\\b.*$");
        ArrayDeque<String> work = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        work.add("_C9H_divls");
        while (!work.isEmpty()) {
            String helper = work.removeFirst();
            if (!visited.add(helper)) continue;
            int begin = code.indexOf(helper + ":");
            if (begin < 0) return false;
            int end = begin + 1;
            while (end < code.size() && !function.matcher(code.get(end)).matches()
                    && !code.get(end).equals("end")) end++;
            Set<String> localLabels = new HashSet<>();
            for (int index = begin + 1; index < end; index++) {
                if (code.get(index).endsWith(":")) localLabels.add(code.get(index).substring(0, code.get(index).length() - 1));
            }
            for (int index = begin + 1; index < end; index++) {
                String instruction = code.get(index);
                if (bank.matcher(instruction).find()) return false;
                if (instruction.replaceAll("\\(xsp\\+\\d+\\)", "").contains("(")) return false;
                if (instruction.endsWith(":")) continue;
                if (instruction.matches("call _C9H_divl[us]")) {
                    work.add(instruction.substring(5));
                } else if (instruction.matches("(?:jr|jrl) (?:\\w+,\\s*)?\\.[\\w.$]+")) {
                    String target = instruction.substring(instruction.lastIndexOf(' ') + 1);
                    if (!localLabels.contains(target)) return false;
                } else if (!ordinary.matcher(instruction).matches()) return false;
            }
        }
        return true;
    }

    private static boolean scratchDeadOnAllPaths(List<String> code, int first, int end,
            Map<String, Integer> labels, Pattern scratch) {
        return scratchDeadOnAllPaths(code, first, end, labels, scratch, "xhl");
    }

    private static boolean scratchDeadOnAllPaths(List<String> code, int first, int end,
            Map<String, Integer> labels, Pattern scratch, String scratchRegister) {
        Pattern branch = Pattern.compile("^(?:jr|jrl) (?:(\\w+),\\s*)?(\\.[\\w.$]+)$");
        Pattern ordinary = Pattern.compile("^(?:ld|add|sub|and|or|xor|cp|sla|sra|srl|extz|exts|bit|inc|dec|divs?)\\b.*$");
        ArrayDeque<Integer> work = new ArrayDeque<>();
        Set<Integer> visited = new HashSet<>();
        work.add(first);
        while (!work.isEmpty()) {
            int index = work.removeFirst();
            if (!visited.add(index)) continue;
            if (index >= end) return false;
            String instruction = code.get(index);
            if (scratchRegister.equals("xbc2") && divisionFallbackAt(code, index)
                    && divisionPreservesBank(code)) {
                work.add(index + 7);
                continue;
            }
            if (instruction.equals("pop " + scratchRegister)) continue;
            if (instruction.matches("(?:ld|lda) " + scratchRegister + ", .*")
                    && !scratch.matcher(instruction.substring(instruction.indexOf(',') + 1)).find()) continue;
            if (scratch.matcher(instruction).find()) return false;
            if (scratchRegister.equals("xbc2") && instruction.matches("pop x(?:wa|bc|de|hl|ix|iy|iz)[12]?")) {
                work.add(index + 1);
                continue;
            }
            Matcher jump = branch.matcher(instruction);
            if (jump.matches()) {
                Integer target = labels.get(jump.group(2));
                if (target == null) return false;
                work.add(target);
                if (jump.group(1) != null) work.add(index + 1);
            } else {
                if (!instruction.endsWith(":") && !ordinary.matcher(instruction).matches()) return false;
                work.add(index + 1);
            }
        }
        return true;
    }

    static List<String> foldXhlAccumulatorRoundtrip(List<String> lines) {
        Pattern pLdXhlXwa = Pattern.compile("^(\\s+)ld\\s+xhl,\\s*xwa\\s*(?:;.*)?$");
        Pattern pAluXhl   = Pattern.compile("^\\s+(add|sub|adc|sbc|and|or|xor)\\s+xhl,\\s*(\\(.*\\))\\s*(?:;.*)?$");
        Pattern pLdXwaXhl = Pattern.compile("^\\s+ld\\s+xwa,\\s*xhl\\s*(?:;.*)?$");

        List<String> out = new ArrayList<>(lines.size());
        int i = 0;
        while (i < lines.size()) {
            String line0 = lines.get(i);
            Matcher m0 = pLdXhlXwa.matcher(line0);
            if (m0.matches() && i + 2 < lines.size()) {
                String line1 = lines.get(i + 1);
                String line2 = lines.get(i + 2);
                Matcher m1 = pAluXhl.matcher(line1);
                Matcher m2 = pLdXwaXhl.matcher(line2);
                if (m1.matches() && m2.matches() && xhlOverwrittenBeforeUse(lines, i + 3)
                    && !Pattern.compile("(?i)\\b(?:xhl|hl|h|l)\\b").matcher(m1.group(2)).find()) {
                    String indent = m0.group(1);
                    String op  = m1.group(1);
                    String mem = m1.group(2);
                    out.add(indent + op + " xwa, " + mem);
                    i += 3;
                    continue;
                }
            }
            out.add(line0);
            i++;
        }
        return out;
    }

    private static boolean xhlOverwrittenBeforeUse(List<String> lines, int start) {
        Pattern alias = Pattern.compile("(?i)\\b(?:xhl|hl|h|l)\\b");
        Pattern overwrite = Pattern.compile("(?i)^(?:ld|lda|pop)\\s+xhl(?:,\\s*(.+))?$");
        for (int index = start; index < lines.size(); index++) {
            String instruction = lines.get(index).split(";", 2)[0].strip();
            if (instruction.isEmpty()) continue;
            if (instruction.contains(":") || instruction.matches("(?i)^(?:j\\w*|call|calr|ret|reti|djnz|swi)\\b.*"))
                return false;
            Matcher written = overwrite.matcher(instruction);
            if (written.matches()) return written.group(1) == null || !alias.matcher(written.group(1)).find();
            if (alias.matcher(instruction).find()) return false;
        }
        return false;
    }

    // =========================================================================
    // Pass: eliminateRegisterRoundtrip  (Rule A)
    //
    //   ld xDeN, xwa          <- saves xwa into a bank/index reg
    //   ld xwa, xDeN          <- immediately reloads it back (no-op: xwa unchanged)
    //
    // Only fires when save and reload are consecutive (no instructions between them,
    // only blank lines or comments).  This is the safe conservative form: xwa is
    // guaranteed unchanged because no instruction executed between the two moves.
    // =========================================================================
    static List<String> eliminateRegisterRoundtrip(List<String> lines) {
        // Matches:  ld xDeN, xwa  (bank/index reg on the left, xwa on the right)
        Pattern pSaveXwa = Pattern.compile(
            "^(\\s+)ld\\s+(x(?:de|bc|hl|wa|iz|iy|ix)[0-9]?),\\s*xwa\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        // Matches:  ld xwa, xDeN  (xwa on the left, same bank/index reg on the right)
        Pattern pReloadXwa = Pattern.compile(
            "^\\s+ld\\s+xwa,\\s*(x(?:de|bc|hl|wa|iz|iy|ix)[0-9]?)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) continue;
            Matcher mSave = pSaveXwa.matcher(out.get(i));
            if (!mSave.matches()) continue;
            String reg = mSave.group(2).toLowerCase(Locale.ROOT);  // e.g. "xde1"
            // Find the very next non-blank/non-comment line
            int j = i + 1;
            while (j < out.size()) {
                if (out.get(j) == null) { j++; continue; }
                String raw = out.get(j);
                if (raw.matches("^\\s*(;.*)?$")) { j++; continue; }
                break;
            }
            if (j >= out.size()) continue;
            // The very next instruction must be the reload ld xwa, same-reg
            Matcher mReload = pReloadXwa.matcher(out.get(j));
            if (!mReload.matches()) continue;
            if (!mReload.group(1).equalsIgnoreCase(reg)) continue;
            // Safe: save immediately followed by reload — xwa is unchanged, drop reload
            out.set(j, null);
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: foldXhlLoadAddRoundtrip  (Rule C)
    //
    //   ld xhl, xwa            <- save xwa in xhl
    //   ld xwa, (xsp+N)        <- overwrite xwa with stack value
    //   OP xhl, xwa            <- xhl = xwa_orig OP (xsp+N)
    //   ld xwa, xhl            <- move result back
    //
    //  =>  OP xwa, (xsp+N)     <- in-place: xwa = xwa_orig OP (xsp+N)
    //
    // OP is any ordinary binary ALU op (add/adc/sub/sbc/and/or/xor). Both the
    // original `OP xhl,xwa` and the folded `OP xwa,(mem)` compute
    // xwa_orig OP mem with identical operand order, so this does not rely on
    // commutativity. The logical R,(mem) source forms are TLCS-900-encodable
    // and sim-decoded — see memory [[mode7-logical-mem-operand-alu]].
    //
    // Requires xhl to be overwritten before any later use (same guard as
    // foldXhlAccumulatorRoundtrip).  Does not fire if the mem operand itself
    // involves xhl.
    // =========================================================================
    static List<String> foldXhlLoadAddRoundtrip(List<String> lines) {
        Pattern pSaveXhl   = Pattern.compile("^(\\s+)ld\\s+xhl,\\s*xwa\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pLdXwaMem  = Pattern.compile("^\\s+ld\\s+xwa,\\s*(\\(xsp\\+[^)]+\\))\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pAddXhlXwa = Pattern.compile("^\\s+(add|adc|sub|sbc|and|or|xor)\\s+xhl,\\s*xwa\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pLdXwaXhl  = Pattern.compile("^\\s+ld\\s+xwa,\\s*xhl\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pXhlRef    = Pattern.compile("(?i)\\b(?:xhl|hl|h|l)\\b");

        List<String> out = new ArrayList<>(lines.size());
        int i = 0;
        while (i < lines.size()) {
            String line0 = lines.get(i);
            Matcher m0 = pSaveXhl.matcher(line0);
            if (m0.matches() && i + 3 < lines.size()) {
                String line1 = lines.get(i + 1);
                String line2 = lines.get(i + 2);
                String line3 = lines.get(i + 3);
                Matcher m1 = pLdXwaMem.matcher(line1);
                Matcher m2 = pAddXhlXwa.matcher(line2);
                Matcher m3 = pLdXwaXhl.matcher(line3);
                if (m1.matches() && m2.matches() && m3.matches()
                        && !pXhlRef.matcher(m1.group(1)).find()
                        && xhlOverwrittenBeforeUse(lines, i + 4)) {
                    String indent = m0.group(1);
                    String op  = m2.group(1).toLowerCase(Locale.ROOT);
                    String mem = m1.group(1);
                    out.add(indent + op + " xwa, " + mem);
                    i += 4;
                    continue;
                }
            }
            out.add(line0);
            i++;
        }
        return out;
    }

    // =========================================================================
    // Pass: foldSpillReloadAdd  (Rule D)
    //
    //   ld (xsp+T), xwa        <- spill xwa to temp slot T
    //   ld xwa, (xsp+M)        <- load a different stack value into xwa
    //   OP xwa, (xsp+T)        <- combine with the spilled value
    //
    //  =>  ld (xsp+T), xwa    <- preserve the value for later readers
    //      OP xwa, (xsp+M)    <- load + OP in-place
    //
    // The original computes mem_M OP xwa_orig; the fold computes
    // xwa_orig OP mem_M. These are equal ONLY for commutative ops, so OP is
    // restricted to add/and/or/xor — sub/sbc/adc are NOT commutative and must
    // not be admitted here. The logical R,(mem) source forms are
    // TLCS-900-encodable and sim-decoded — see [[mode7-logical-mem-operand-alu]].
    //
    // Only adjacent instructions are accepted. Store removal belongs to the
    // separate CFG-aware dead-store pass.
    // =========================================================================
    static List<String> foldSpillReloadAdd(List<String> lines) {
        // ld (xsp+T), xwa
        Pattern pSpill = Pattern.compile(
            "^(\\s+)ld\\s+(\\(xsp\\+(\\d+)\\)),\\s*xwa\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        // ld xwa, (xsp+M)  where M != T
        Pattern pLoad = Pattern.compile(
            "^\\s+ld\\s+xwa,\\s*(\\(xsp\\+(\\d+)\\))\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        // OP xwa, (xsp+T)  — commutative op, must have same slot
        Pattern pAddBack = Pattern.compile(
            "^\\s+(add|and|or|xor)\\s+xwa,\\s*(\\(xsp\\+(\\d+)\\))\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == null) continue;
            Matcher mSpill = pSpill.matcher(out.get(i));
            if (!mSpill.matches()) continue;
            String indent = mSpill.group(1);
            long slotN = Long.parseLong(mSpill.group(3));

            // Next non-null non-blank line must be ld xwa,(xsp+M)
            int j = i + 1;
            while (j < out.size() && (out.get(j) == null || out.get(j).isBlank())) j++;
            if (j >= out.size()) continue;
            Matcher mLoad = pLoad.matcher(out.get(j));
            if (!mLoad.matches()) continue;
            long loadN = Long.parseLong(mLoad.group(2));
            if (loadN == slotN) continue;
            String loadMem = mLoad.group(1);

            int addBackAt = j + 1;
            while (addBackAt < out.size()
                    && (out.get(addBackAt) == null || out.get(addBackAt).isBlank())) addBackAt++;
            if (addBackAt >= out.size()) continue;
            Matcher mAdd = pAddBack.matcher(out.get(addBackAt));
            if (!mAdd.matches() || Long.parseLong(mAdd.group(3)) != slotN) continue;
            String op = mAdd.group(1).toLowerCase(Locale.ROOT);
            out.set(j, indent + op + " xwa, " + loadMem);
            out.set(addBackAt, null);
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    // =========================================================================
    // Pass: foldXhlImmAddToMem  (Rule E)
    //
    //   ld xhl, IMM            <- load immediate into xhl
    //   add xhl, xwa           <- xhl = IMM + xwa  (commutative)
    //   ld (mem), xhl          <- store result
    //
    //  =>  add xwa, IMM        <- in-place accumulate
    //      ld (mem), xwa       <- store from xwa
    //
    // Requires xhl to be overwritten before any later use.
    // =========================================================================
    static List<String> foldXhlImmAddToMem(List<String> lines) {
        // ld xhl, IMM  (hex or decimal immediate, no register or memory operand)
        Pattern pLdXhlImm  = Pattern.compile(
            "^(\\s+)ld\\s+xhl,\\s*(0[0-9a-fA-F]*h|[0-9]+)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pAddXhlXwa = Pattern.compile(
            "^\\s+add\\s+xhl,\\s*xwa\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pStoreXhl  = Pattern.compile(
            "^\\s+ld\\s+(\\([^)]+\\)),\\s*xhl\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pXhlRef    = Pattern.compile("(?i)\\b(?:xhl|hl|h|l)\\b");

        List<String> out = new ArrayList<>(lines.size());
        int i = 0;
        while (i < lines.size()) {
            String line0 = lines.get(i);
            Matcher m0 = pLdXhlImm.matcher(line0);
            if (m0.matches() && i + 2 < lines.size()) {
                String line1 = lines.get(i + 1);
                String line2 = lines.get(i + 2);
                Matcher m1 = pAddXhlXwa.matcher(line1);
                Matcher m2 = pStoreXhl.matcher(line2);
                if (m1.matches() && m2.matches()) {
                    String indent = m0.group(1);
                    String imm = m0.group(2);
                    String mem = m2.group(1);
                    // mem must not involve xhl/hl
                    if (!pXhlRef.matcher(mem).find() && xhlOverwrittenBeforeUse(lines, i + 3)) {
                        out.add(indent + "add xwa, " + imm);
                        out.add(indent + "ld " + mem + ", xwa");
                        i += 3;
                        continue;
                    }
                }
            }
            out.add(line0);
            i++;
        }
        return out;
    }

    // =========================================================================
    // Pass: invertCondJumpPair  (Rule G)
    //
    //   jr CC, .Lbb_edge       <- conditional jump to edge block
    //   jr .Lbb_exit           <- unconditional fall-through to exit
    // .Lbb_edge:               <- edge label immediately follows
    //
    //  =>  jr NCC, .Lbb_exit   <- invert condition; fall through into edge block
    //
    // Eliminates one taken-branch penalty and one instruction.  The NCC map
    // covers the standard TLCS-900 condition codes.
    // =========================================================================
    static List<String> invertCondJumpPair(List<String> lines) {
        // jrl? CC, .Lbb_target  (short jr or long jrl conditional jump)
        Pattern pCondJr  = Pattern.compile(
            "^(\\s+)(jrl?)\\s+(\\w+),\\s*(\\.Lbb_\\S+)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        // jrl? .Lbb_exit  (unconditional jump)
        Pattern pUncondJr = Pattern.compile(
            "^\\s+(jrl?)\\s+(\\.Lbb_\\S+)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        // label line: .Lbb_X:
        Pattern pLabel = Pattern.compile("^(\\.Lbb_\\S+):.*$");

        // Negation table for TLCS-900 condition codes
        Map<String, String> negate = new HashMap<>();
        negate.put("c",   "nc");  negate.put("nc",  "c");
        negate.put("z",   "nz");  negate.put("nz",  "z");
        negate.put("m",   "p");   negate.put("p",   "m");
        negate.put("v",   "nv");  negate.put("nv",  "v");
        negate.put("ov",  "nov"); negate.put("nov", "ov");
        negate.put("gt",  "le");  negate.put("le",  "gt");
        negate.put("ge",  "lt");  negate.put("lt",  "ge");
        negate.put("ugt", "ule"); negate.put("ule", "ugt");
        negate.put("uge", "ult"); negate.put("ult", "uge");

        // Count all label references so we can update them
        Map<String, Integer> refCount = new HashMap<>();
        Pattern pAnyRef = Pattern.compile("(?<![\\w.])(\\.Lbb_\\w+)");
        for (String line : lines) {
            Matcher m = pAnyRef.matcher(line);
            while (m.find()) refCount.merge(m.group(1), 1, Integer::sum);
        }

        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size() - 1; i++) {
            if (out.get(i) == null) continue;
            Matcher mCond = pCondJr.matcher(out.get(i));
            if (!mCond.matches()) continue;
            String indent  = mCond.group(1);
            String jmpInsn = mCond.group(2).toLowerCase(Locale.ROOT);   // "jr" or "jrl"
            String cc      = mCond.group(3).toLowerCase(Locale.ROOT);
            String edge    = mCond.group(4);   // the label jumped-to conditionally

            String negCC = negate.get(cc);
            if (negCC == null) continue;   // unknown condition code — skip

            // Next line (skip blanks) must be unconditional jr/jrl to exit
            int j = i + 1;
            while (j < out.size() && out.get(j) != null && out.get(j).isBlank()) j++;
            if (j >= out.size() || out.get(j) == null) continue;
            Matcher mUncond = pUncondJr.matcher(out.get(j));
            if (!mUncond.matches()) continue;
            String uncondInsn = mUncond.group(1).toLowerCase(Locale.ROOT);
            String exit = mUncond.group(2);
            if (exit.equals(edge)) continue;   // same target — no benefit

            // Line after the unconditional jr must be the edge label
            int k = j + 1;
            while (k < out.size() && out.get(k) != null && out.get(k).isBlank()) k++;
            if (k >= out.size() || out.get(k) == null) continue;
            Matcher mEdge = pLabel.matcher(out.get(k).trim());
            if (!mEdge.matches() || !mEdge.group(1).equals(edge)) continue;

            // Use the same jump instruction form as the unconditional jump
            // (so jrl CC stays jrl, jr CC stays jr)
            String newInsn = uncondInsn.equals("jrl") ? "jrl" : jmpInsn;
            // Transform: replace conditional jump with negated jump to exit; drop the unconditional jump
            out.set(i, indent + newInsn + " " + negCC + ", " + exit);
            refCount.merge(edge, -1, Integer::sum);   // one fewer reference to edge
            refCount.merge(exit, 1, Integer::sum);    // one more reference to exit
            out.set(j, null);
            refCount.merge(exit, -1, Integer::sum);   // the original unconditional jr is gone
            // Note: the edge label refCount drop from the conditional jr is already accounted for above.
            // exit gets +1 from the new jr NC, exit and -1 from removing the old jr exit => net 0. ✓
        }
        out.removeIf(Objects::isNull);
        return out;
    }

}
