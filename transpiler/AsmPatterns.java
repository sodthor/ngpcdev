import java.util.*;
import java.util.regex.*;

/** Register-role patterns for ordinary TLCS-900 registers (never SP or RFP). */
final class AsmPatterns {
    static final String BANK_ZERO_ABI = "; register-bank-abi: current=0";
    static final String VOID_SCRATCH_ABI = "; function-abi: return=void clobbers=xwa,xhl";
    private AsmPatterns() {}
    record Instruction(String op, String left, String right) {}
    static Instruction parse(String raw) {
        String core = raw.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        int space = core.indexOf(' ');
        if (space < 0) return new Instruction(core, "", "");
        String operands = core.substring(space + 1).strip();
        int comma = operands.indexOf(',');
        return new Instruction(core.substring(0, space),
            comma < 0 ? operands : operands.substring(0, comma).strip(),
            comma < 0 ? "" : operands.substring(comma + 1).strip());
    }
    static int width(String reg) {
        if (reg.matches("x(?:wa|bc|de|hl|ix|iy|iz)")) return 32;
        if (reg.matches("(?:wa|bc|de|hl|ix|iy|iz)")) return 16;
        if (reg.matches("[wabcdehl]")) return 8;
        return 0;
    }
    static Set<String> aliases(String reg) {
        for (String group : List.of("xwa wa w a", "xbc bc b c", "xde de d e",
                "xhl hl h l", "xix ix", "xiy iy", "xiz iz")) {
            Set<String> names = new HashSet<>(List.of(group.split(" ")));
            if (names.contains(reg)) return names;
        }
        return Set.of(reg);
    }
    static boolean mentions(String operand, String reg) {
        return Pattern.compile("(?<![\\w.$])(?:" + String.join("|", aliases(reg))
            + ")(?![\\w.$])").matcher(operand).find();
    }
    static boolean disjoint(String a, String b) {
        return width(a) != 0 && width(a) == width(b) && !aliases(a).contains(b);
    }
    // A full overwrite must precede every use. Unknown instructions, absolute
    // bank aliases, status changes and control flow are conservative barriers.
    static boolean deadAfter(List<String> lines, int last, String reg) {
        for (int i = last + 1; i < lines.size(); i++) {
            Instruction n = parse(lines.get(i));
            if (n.op().isEmpty()) continue;
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.matches(".*\\b(?:sr|rfp|f|[rq](?:wa|bc|de|hl|ix|iy|iz|sp|[wabcdehl])[0-9]*|x(?:wa|bc|de|hl)[0-3])\\b.*")) return false;
            if ((n.op().equals("ld") || n.op().equals("lda") || n.op().equals("pop"))
                    && aliases(reg).contains(n.left()) && width(n.left()) >= width(reg))
                return !mentions(n.right(), reg);
            if (mentions(core, reg)) return false;
            if (!n.op().matches("ld|lda|pushw?|pop|add|sub|adc|sbc|and|or|xor|cp|sla|sra|srl|extz|exts|bit|inc|dec|scc|nop")) return false;
        }
        return false;
    }
    static String emit(String original, String core) {
        int comment = original.indexOf(';');
        String indent = original.substring(0, original.length() - original.stripLeading().length());
        return indent + core + (comment < 0 ? "" : " " + original.substring(comment));
    }
    static void removeInstruction(List<String> lines, int index) {
        int comment = lines.get(index).indexOf(';');
        if (comment < 0) lines.remove(index);
        else lines.set(index, "\t" + lines.get(index).substring(comment));
    }
    static boolean stackMemory(String operand) {
        Matcher m = Pattern.compile("\\(xsp\\+(\\d+)\\)").matcher(operand);
        if (!m.matches()) return false;
        try { return Integer.parseInt(m.group(1)) <= 32767; }
        catch (NumberFormatException e) { return false; }
    }
    /** LD T,R; OP T,(stack); LD R,T, optionally with LD R,(stack). */
    static List<String> foldAluRoundtrip(List<String> lines, boolean reload) {
        List<String> out = new ArrayList<>(lines);
        int size = reload ? 4 : 3;
        for (int i = 0; i + size <= out.size(); i++) {
            Instruction save = parse(out.get(i));
            String temp = save.left(), result = save.right();
            if (!save.op().equals("ld") || !disjoint(temp, result) || width(temp) < 16) continue;
            Instruction load = reload ? parse(out.get(i + 1)) : null;
            Instruction alu = parse(out.get(i + size - 2)), copy = parse(out.get(i + size - 1));
            String memory = reload ? load.right() : alu.right();
            if (!stackMemory(memory) || !alu.op().matches("add|adc|sub|sbc|and|or|xor")
                    || !alu.left().equals(temp) || !copy.op().equals("ld")
                    || !copy.left().equals(result) || !copy.right().equals(temp)
                    || reload && (!load.op().equals("ld") || !load.left().equals(result) || !alu.right().equals(result))
                    || !deadAfter(out, i + size - 1, temp)) continue;
            out.set(i, emit(out.get(i), alu.op() + " " + result + ", " + memory));
            for (int j = i + size - 1; j > i; j--) removeInstruction(out, j);
        }
        return out;
    }
    /** The spill remains observable; only the commutative reload/ALU is folded. */
    static List<String> foldSpillAlu(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 2 < out.size(); i++) {
            Instruction store = parse(out.get(i)), load = parse(out.get(i + 1)), alu = parse(out.get(i + 2));
            String reg = store.right();
            if (!store.op().equals("ld") || width(reg) < 16 || !stackMemory(store.left())
                    || !load.op().equals("ld") || !load.left().equals(reg) || !stackMemory(load.right())
                    || !alu.op().matches("add|and|or|xor") || !alu.left().equals(reg) || !alu.right().equals(store.left())) continue;
            int a = Integer.parseInt(store.left().substring(5, store.left().length() - 1));
            int b = Integer.parseInt(load.right().substring(5, load.right().length() - 1));
            if (Math.abs(a - b) < width(reg) / 8) continue;
            out.set(i + 1, emit(out.get(i + 1), alu.op() + " " + reg + ", " + load.right()));
            removeInstruction(out, i + 2);
        }
        return out;
    }

    /** Save R; AND R,one-bit; branch; restore R on both successors -> BIT.
     * No assumptions about a tile byte, bit 2, coordinate math or labels.
     * Keep the save, since it may have unrelated readers after the diamond.
     */
    static List<String> foldRestoredSingleBitTests(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 3 < out.size(); i++) {
            Instruction save = parse(out.get(i)), test = parse(out.get(i + 1)), branch = parse(out.get(i + 2));
            String reg = test.left(), home = save.left();
            OptionalLong mask = AsmOptimizer.parseAsmImmediate(test.right());
            if (!save.op().equals("ld") || !save.right().equals(reg) || width(reg) == 0
                    || !(stackMemory(home) || disjoint(home, reg)) || !test.op().equals("and")
                    || mask.isEmpty() || mask.getAsLong() <= 0 || mask.getAsLong() > 128
                    || Long.bitCount(mask.getAsLong()) != 1
                    || !branch.op().matches("jr|jrl|jp") || !branch.left().matches("z|nz")) continue;
            String byteReg = switch (reg) {
                case "xwa", "wa", "a" -> "a";
                case "xbc", "bc", "c" -> "c";
                case "xde", "de", "e" -> "e";
                case "xhl", "hl", "l" -> "l";
                case "w", "b", "d", "h" -> reg;
                default -> "";
            };
            if (byteReg.isEmpty()) continue; // Index registers have no byte BIT form.
            int target = -1;
            for (int j = 0; j < out.size(); j++) {
                if (out.get(j).split(";", 2)[0].strip().equalsIgnoreCase(branch.right() + ":")) {
                    if (target >= 0) { target = -1; break; }
                    target = j;
                }
            }
            if (target <= i + 3 || target + 1 >= out.size()) continue;
            // The fallthrough arm must not execute the taken-arm restore after
            // modifying R. Require an actual diamond, not two adjacent arms.
            int terminator = target - 1;
            while (terminator > i + 3 && parse(out.get(terminator)).op().isEmpty()) terminator--;
            Instruction skip = parse(out.get(terminator));
            if (!skip.op().matches("jr|jrl|jp") || !skip.right().isEmpty()
                    || skip.left().equals(branch.right())) continue;
            boolean mergeAfterTarget = false;
            for (int j = target + 2; j < out.size(); j++)
                if (out.get(j).split(";", 2)[0].strip().equalsIgnoreCase(skip.left() + ":")) mergeAfterTarget = true;
            if (!mergeAfterTarget) continue;
            Instruction fall = parse(out.get(i + 3)), taken = parse(out.get(target + 1));
            if (!fall.equals(new Instruction("ld", reg, home)) || !taken.equals(fall)) continue;
            // The taken entry must be private to this test, not a shared block.
            Pattern reference = Pattern.compile("(?<![\\w.$])" + Pattern.quote(branch.right()) + "(?![\\w.$])", Pattern.CASE_INSENSITIVE);
            int refs = 0;
            for (String raw : out) {
                Matcher m = reference.matcher(raw.split(";", 2)[0]);
                while (m.find()) refs++;
            }
            if (refs != 2 || !AsmOptimizer.flagsDeadOnBranchSuccessors(out, i + 2, branch.right())) continue;
            out.set(i + 1, emit(out.get(i + 1), "bit " + Long.numberOfTrailingZeros(mask.getAsLong()) + ", " + byteReg));
            removeInstruction(out, target + 1);
            removeInstruction(out, i + 3);
        }
        return out;
    }

    static List<String> eliminateRedundantLoads(List<String> lines) {
        Map<String, String> known = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (String raw : lines) {
            Instruction n = parse(raw);
            if (n.op().isEmpty()) { out.add(raw); continue; }
            String core = raw.split(";", 2)[0].strip();
            // A conditional jump does not modify registers on fallthrough.
            // Its destination label still clears all facts when encountered.
            if (n.op().matches("jr|jrl|jp") && n.left().matches("z|nz|eq|ne|c|nc|s|ns|v|nv|lt|le|gt|ge|ult|ule|ugt|uge")) {
                out.add(raw); continue;
            }
            if (!n.op().matches("ld|lda|add|sub|adc|sbc|and|or|xor|cp|inc|dec|sla|srl|sra|extz|exts|bit|scc|pushw?|pop|nop")
                    || core.toLowerCase(Locale.ROOT).matches(".*\\b(?:rfp|sr|f|[rq](?:wa|bc|de|hl|ix|iy|iz|sp|[wabcdehl])[0-9]*|x(?:wa|bc|de|hl)[0-3])\\b.*")
                    || core.matches(".*\\([^)]*[+-]\\).*")) {
                known.clear(); out.add(raw); continue;
            }
            String destination = n.op().matches("inc|dec|sla|srl|sra|scc") ? n.right() : n.left();
            String value = null;
            if (n.op().matches("ld|lda") && width(destination) != 0 && !n.right().contains("(")) {
                String originalSource = core.substring(core.indexOf(',') + 1).strip();
                if (AsmOptimizer.parseAsmImmediate(n.right()).isPresent() || originalSource.matches("[_.$][\\w.$]+"))
                    value = n.op() + ":" + originalSource;
                else if (n.op().equals("ld") && disjoint(destination, n.right())) value = "reg:" + n.right();
            }
            if (value != null && (value.equals(known.get(destination))
                    || value.startsWith("reg:") && ("reg:" + destination).equals(known.get(n.right())))) {
                int c = raw.indexOf(';');
                if (c >= 0) out.add("\t" + raw.substring(c));
                continue;
            }
            if (!n.op().matches("cp|bit|pushw?|nop")
                    && !(n.op().matches("or|and") && n.left().equals(n.right()) && width(n.left()) != 0)) {
                Set<String> written = aliases(destination);
                known.entrySet().removeIf(e -> written.contains(e.getKey())
                    || e.getValue().startsWith("reg:") && written.contains(e.getValue().substring(4)));
            }
            if (value != null) known.put(destination, value);
            out.add(raw);
        }
        return out;
    }

    static List<String> foldPushImmediates(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 1 < out.size(); i++) {
            Instruction load = parse(out.get(i)), push = parse(out.get(i + 1));
            OptionalLong immediate = AsmOptimizer.parseAsmImmediate(load.right());
            if (!load.op().equals("ld") || width(load.left()) != 16 || immediate.isEmpty()
                    || immediate.getAsLong() < 0 || immediate.getAsLong() > 65535
                    || !push.equals(new Instruction("push", load.left(), ""))) continue;
            // The emitted ABI explicitly treats WA as scratch at a call; this
            // exception must not be applied to arbitrary callee-save registers.
            int next = i + 2;
            while (next < out.size() && parse(out.get(next)).op().isEmpty()) next++;
            boolean abiScratch = load.left().equals("wa") && next < out.size()
                && parse(out.get(next)).op().matches("call|calr");
            if (!abiScratch && !deadAfter(out, i + 1, load.left())) continue;
            out.set(i + 1, emit(out.get(i + 1), "pushw " + load.right()));
            removeInstruction(out, i);
            i--;
        }
        return out;
    }

    static List<String> eliminateByteMasksBeforeExtend(List<String> lines) {
        List<String> out = new ArrayList<>();
        Pattern label = Pattern.compile("^[.\\w].*:.*$");
        for (int i = 0; i < lines.size(); i++) {
            Instruction mask = parse(lines.get(i));
            OptionalLong value = AsmOptimizer.parseAsmImmediate(mask.right());
            if (i + 1 < lines.size() && mask.op().equals("and") && mask.left().matches("wa|bc|de|hl")
                    && value.isPresent() && value.getAsLong() == 255
                    && parse(lines.get(i + 1)).equals(new Instruction("extz", mask.left(), ""))) {
                int last = i + 1;
                if (i + 2 < lines.size() && parse(lines.get(i + 2)).equals(new Instruction("extz", "x" + mask.left(), ""))) last++;
                if (AsmOptimizer.flagsAreDeadAfterLine(lines, last, label)) {
                    int c = lines.get(i).indexOf(';');
                    if (c >= 0) out.add("\t" + lines.get(i).substring(c));
                    continue;
                }
            }
            out.add(lines.get(i));
        }
        return out;
    }

    /** Earlier byte-normalization masks die at the next normalization if only
     * the low byte is touched meanwhile. Keep any mask whose flags are read;
     * EXTZ itself does not overwrite flags. No game or fixed register names.
     */
    static List<String> simplifyByteMaskChains(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            Instruction first = parse(out.get(i));
            OptionalLong value = AsmOptimizer.parseAsmImmediate(first.right());
            String word = first.left();
            if (!first.op().equals("and") || !word.matches("wa|bc|de|hl")
                    || value.isEmpty() || value.getAsLong() != 255) continue;
            String low = lowByte(word);
            boolean flagsOverwritten = false;
            for (int j = i + 1; j < out.size(); j++) {
                Instruction next = parse(out.get(j));
                if (next.op().isEmpty()) continue;
                OptionalLong immediate = AsmOptimizer.parseAsmImmediate(next.right());
                boolean laterMask = next.op().equals("and")
                    && (next.left().equals(word) || next.left().equals("x" + word))
                    && immediate.isPresent() && immediate.getAsLong() >= 0 && immediate.getAsLong() <= 255;
                boolean extend = next.equals(new Instruction("extz", word, ""));
                if (laterMask || extend) {
                    if (laterMask || flagsOverwritten) {
                        removeInstruction(out, i);
                        i--;
                    }
                    break;
                }
                // Logical byte operations neither read nor write the high byte,
                // and completely overwrite the earlier logical mask's flags.
                if (next.op().matches("and|or|xor") && next.left().equals(low)
                        && immediate.isPresent() && immediate.getAsLong() >= 0 && immediate.getAsLong() <= 255) {
                    flagsOverwritten = true;
                    continue;
                }
                if (next.op().equals("nop")) continue;
                if (next.op().equals("ld") && next.left().equals(low)
                        && (immediate.isPresent() || width(next.right()) == 8)
                        && !next.right().equals(word.substring(0, 1))) continue;
                // Reads of the word/high byte, addresses through its wide alias,
                // flag consumers, labels, calls and unknown operations all stop.
                break;
            }
        }
        // AND word,low8 already clears the high byte. EXTZ word is now a no-op
        // irrespective of flag liveness: keep the AND and its exact flags.
        for (int i = 0; i < out.size(); i++) {
            Instruction mask = parse(out.get(i));
            OptionalLong value = AsmOptimizer.parseAsmImmediate(mask.right());
            if (!mask.op().equals("and") || !mask.left().matches("wa|bc|de|hl")
                    || value.isEmpty() || value.getAsLong() < 0 || value.getAsLong() > 255) continue;
            int j = i + 1;
            while (j < out.size() && parse(out.get(j)).op().isEmpty()) j++;
            if (j < out.size() && parse(out.get(j)).equals(new Instruction("extz", mask.left(), ""))) removeInstruction(out, j);
        }
        return out;
    }

    private static String lowByte(String word) {
        return switch (word) { case "wa" -> "a"; case "bc" -> "c"; case "de" -> "e"; default -> "l"; };
    }

    /** ((value & before) >>> count) & after ==
     *  (value >>> count) & ((before >>> count) & after).
     * The final AND retains exactly the original result and logical flags.
     * Only adjacent instructions qualify: the intermediate value/flags cannot
     * be observed, and SRL never consumes the incoming carry. */
    static List<String> foldMasksThroughLogicalShifts(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = out.size() - 3; i >= 0; i--) {
            Instruction before = parse(out.get(i)), shift = parse(out.get(i + 1));
            Instruction after = parse(out.get(i + 2));
            int bits = width(before.left());
            OptionalLong first = AsmOptimizer.parseAsmImmediate(before.right());
            OptionalLong last = AsmOptimizer.parseAsmImmediate(after.right());
            OptionalLong count = AsmOptimizer.parseAsmImmediate(shift.left());
            if (!before.op().equals("and") || bits == 0 || !shift.op().equals("srl")
                    || !shift.right().equals(before.left()) || !after.op().equals("and")
                    || !after.left().equals(before.left()) || first.isEmpty() || last.isEmpty()
                    || count.isEmpty() || count.getAsLong() < 1 || count.getAsLong() > 16) continue;
            long mask = (1L << bits) - 1;
            long combined = ((first.getAsLong() & mask) >>> (int) count.getAsLong())
                & last.getAsLong() & mask;
            out.set(i + 2, emit(out.get(i + 2), "and " + after.left() + ", " + Ir2Tlcs900.fmtImm(combined)));
            removeInstruction(out, i);
        }
        return out;
    }

    /** (x >>> right) << left, truncated to register width, is equivalent to
     * (x & mask) << (left-right) when left > right. The mask clears both
     * discarded low bits and high bits shifted beyond the register width.
     * No input range assumption is required; flags must be overwritten.
     * Experimental only: MODE7 benchmarks showed a slowdown with the 32-bit
     * immediate mask, so this rewrite is NOT in the production pass list. */
    static List<String> experimentalFoldLogicalRightLeftShifts(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 1 < out.size(); i++) {
            Instruction right = parse(out.get(i)), left = parse(out.get(i + 1));
            int bits = width(right.right());
            OptionalLong r = AsmOptimizer.parseAsmImmediate(right.left());
            OptionalLong l = AsmOptimizer.parseAsmImmediate(left.left());
            if (!right.op().equals("srl") || !left.op().equals("sla")
                    || bits == 0 || !left.right().equals(right.right())
                    || r.isEmpty() || l.isEmpty() || r.getAsLong() < 1
                    || l.getAsLong() > 16 || l.getAsLong() >= bits
                    || l.getAsLong() <= r.getAsLong()
                    || !flagsOverwrittenImmediately(out, i + 1)) continue;
            int delta = (int) (l.getAsLong() - r.getAsLong());
            long mask = (((1L << bits) - 1) >>> delta) & (-1L << (int) r.getAsLong());
            out.set(i, emit(out.get(i), "and " + right.right() + ", " + Ir2Tlcs900.fmtImm(mask)));
            out.set(i + 1, emit(out.get(i + 1), "sla " + delta + ", " + right.right()));
        }
        return out;
    }

    private static boolean flagsOverwrittenImmediately(List<String> lines, int last) {
        for (int i = last + 1; i < lines.size(); i++) {
            Instruction n = parse(lines.get(i));
            if (n.op().isEmpty()) continue;
            // Do not traverse labels, calls or status/bank changes. An adjacent
            // ordinary ALU operation overwrites all flags changed by the pair.
            return n.op().matches("add|sub|cp|and|or|xor") && width(n.left()) != 0
                && (width(n.right()) == width(n.left())
                    || AsmOptimizer.parseAsmImmediate(n.right()).isPresent()
                    || !registerBytes(n.right()).isEmpty())
                && !n.right().matches(".*\\b(?:sr|f|rfp)\\b.*");
        }
        return false;
    }

    /** Shift a proven zero-extended byte in its low lane. Keep the high-byte
     * clear and the load unchanged, including any address dependence; discard the
     * redundant final mask only when its flags are subsequently overwritten. */
    static List<String> narrowZeroExtendedByteShifts(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = out.size() - 4; i >= 0; i--) {
            Instruction clear = parse(out.get(i)), load = parse(out.get(i + 1));
            Instruction shift = parse(out.get(i + 2)), mask = parse(out.get(i + 3));
            String word = clear.left();
            boolean zero = clear.op().equals("ld") && word.matches("wa|bc|de|hl")
                && AsmOptimizer.parseAsmImmediate(clear.right()).orElse(-1) == 0;
            if (!zero && clear.op().equals("xor") && clear.left().equals(clear.right())
                    && clear.left().matches("w|b|d|h")) {
                word = switch (clear.left()) { case "w" -> "wa"; case "b" -> "bc"; case "d" -> "de"; default -> "hl"; };
                zero = true;
            }
            OptionalLong count = AsmOptimizer.parseAsmImmediate(shift.left());
            if (!zero || !load.op().equals("ld") || !load.left().equals(lowByte(word))
                    || !shift.op().equals("srl") || !shift.right().equals(word)
                    || count.isEmpty() || count.getAsLong() < 1 || count.getAsLong() > 8
                    || !mask.op().equals("and") || !mask.left().equals(word)
                    || AsmOptimizer.parseAsmImmediate(mask.right()).orElse(-1) != 255
                    || !byteShiftMaskFlagsDead(out, i + 3)) continue;
            out.set(i + 2, emit(out.get(i + 2), "srl " + count.getAsLong() + ", " + lowByte(word)));
            removeInstruction(out, i + 3);
        }
        return out;
    }

    private static boolean byteShiftMaskFlagsDead(List<String> lines, int last) {
        if (flagsDeadAlongSuccessors(lines, last)) return true;
        // Argument marshalling does not read flags. Keep this extension local
        // to the byte-shift proof; PUSH/POP SR, bank changes and calls block it.
        for (int i = last + 1; i < lines.size(); i++) {
            Instruction n = parse(lines.get(i));
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.matches(".*\\b(?:sr|f|rfp)\\b.*")) return false;
            if (n.op().isEmpty() || n.op().matches("ld|lda|extz|nop")) continue;
            if (n.op().matches("push|pop") && !registerBytes(n.left()).isEmpty()) continue;
            if (n.op().equals("pushw") && AsmOptimizer.parseAsmImmediate(n.left()).isPresent()) continue;
            return flagsDeadAlongSuccessors(lines, i - 1);
        }
        return false;
    }

    // Generated-code ABI: ordinary GP registers use current bank 0. Explicit
    // banks remain separate, but their byte/word/wide spellings overlap.
    // This model must not be used across RFP/status changes or opaque calls.
    static Set<String> registerBytes(String name) {
        String group;
        int bank = 0, first = 0, count;
        Matcher explicit = Pattern.compile("(x|r)(wa|bc|de|hl|[wabcdehl])([0-3])").matcher(name);
        String base = name;
        if (explicit.matches()) {
            base = explicit.group(2);
            bank = Integer.parseInt(explicit.group(3));
            count = explicit.group(1).equals("x") ? 4 : base.length();
            if (explicit.group(1).equals("x") && base.length() != 2) return Set.of();
        } else {
            count = width(name) / 8;
            if (count == 0) return Set.of();
            if (base.startsWith("x")) base = base.substring(1);
        }
        group = switch (base) {
            case "a", "w", "wa" -> "wa";
            case "b", "c", "bc" -> "bc";
            case "d", "e", "de" -> "de";
            case "h", "l", "hl" -> "hl";
            default -> base;
        };
        if (base.matches("w|b|d|h")) first = 1;
        Set<String> bytes = new HashSet<>();
        for (int i = first; i < first + count; i++) bytes.add(group + bank + ":" + i);
        return bytes;
    }

    static Set<String> operandBytes(String operand) {
        Set<String> bytes = new HashSet<>();
        Matcher names = Pattern.compile("(?<![\\w.$])[a-z][a-z0-9]*(?![\\w.$])").matcher(operand);
        while (names.find()) bytes.addAll(registerBytes(names.group()));
        // Byte/word multiply and divide forms can access a wider implicit
        // dividend/result than the destination spelling. Overapproximate.
        if (operand.matches("(?:mul|muls|div|divs)\\s+.*")) {
            Set<String> wide = new HashSet<>();
            for (String lane : registerBytes(parse(operand).left())) for (int i = 0; i < 4; i++)
                wide.add(lane.substring(0, lane.indexOf(':') + 1) + i);
            bytes.addAll(wide);
        }
        return bytes;
    }

    static boolean dataflowInstruction(Instruction n, String core) {
        Matcher special = Pattern.compile("\\b[rq](?:wa|bc|de|hl|ix|iy|iz|sp|[wabcdehl])[0-9]*\\b").matcher(core);
        while (special.find()) if (registerBytes(special.group()).isEmpty()) return false;
        return !core.matches(".*\\b(?:sr|f|rfp|sp|qsp)\\b.*")
            && !core.matches(".*\\([^)]*(?:[+-]{2}|[+-]\\s*\\)).*")
            && !core.matches(".*\\(\\s*[+-].*")
            && n.op().matches("ld|lda|pop|pushw?|add|sub|adc|sbc|and|or|xor|cp|inc|dec|sla|sra|srl|extz|exts|mul|muls|div|divs|bit|scc|nop");
    }

    /** Eliminate inverse copies while the original source remains available.
     * Keep the save unless the separate CFG liveness proof declares it dead. */
    static List<String> eliminateAvailableRegisterCopies(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        Set<Integer> unsafe = new HashSet<>();
        Set<Integer> redundant = new HashSet<>();
        int begin = 0;
        boolean stable = true;
        for (int k = 0; k <= out.size(); k++) {
            String core = k == out.size() ? "" : out.get(k).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (k == out.size() || core.matches("_[\\w.$]+:")) {
                if (!stable) for (int j = begin; j < k; j++) unsafe.add(j);
                begin = k;
                stable = true;
            }
            if (core.matches(".*\\b(?:sr|rfp|f)\\b.*") || core.matches("(?:incf|decf|ldf|ex|exx)\\b.*")) stable = false;
        }
        for (int i = 0; i < out.size(); i++) {
            if (unsafe.contains(i)) continue;
            Instruction save = parse(out.get(i));
            Set<String> source = registerBytes(save.right()), temporary = registerBytes(save.left());
            if (!save.op().equals("ld") || source.isEmpty() || source.size() != temporary.size()
                    || !Collections.disjoint(source, temporary)) continue;
            for (int j = i + 1; j < out.size(); j++) {
                Instruction n = parse(out.get(j));
                if (n.op().isEmpty()) continue;
                String core = out.get(j).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                if (!dataflowInstruction(n, core)) break;
                if (n.op().equals("ld") && registerBytes(n.left()).equals(source)
                        && registerBytes(n.right()).equals(temporary)) {
                    // Keep indices stable until every function's bank-state
                    // classification has been consumed.
                    int comment = out.get(j).indexOf(';');
                    out.set(j, comment < 0 ? "" : "\t" + out.get(j).substring(comment));
                    if (comment < 0) redundant.add(j);
                    continue;
                }
                Set<String> touched = operandBytes(core);
                // For LD, only its destination is written, except auto-update
                // addresses (conservatively rejected whenever they overlap).
                if (n.op().matches("ld|lda|pop")) touched = operandBytes(n.left());
                else if (n.op().matches("cp|bit|pushw?|nop")) continue;
                if (!Collections.disjoint(touched, source) || !Collections.disjoint(touched, temporary)) break;
            }
        }
        for (int j = out.size() - 1; j >= 0; j--) if (redundant.contains(j)) out.remove(j);
        return out;
    }

    /** A removed LD must have every written byte overwritten before any read
     * on every CFG path. No ABI live-out assumption: RET/calls remain barriers. */
    static List<String> eliminateDeadRegisterCopies(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        Pattern function = Pattern.compile("_[\\w.$]+:");
        for (int end = out.size(), start = end - 1; start >= 0; start--) {
            String core = out.get(start).split(";", 2)[0].strip();
            if (!function.matcher(core).matches()) continue;
            Map<String, Integer> labels = new HashMap<>();
            boolean safe = true;
            for (int i = start + 1; i < end; i++) {
                String s = out.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                // Without a stable register bank, even a later restore may not
                // overwrite the same physical bytes as the removed store.
                if (s.matches(".*\\b(?:sr|rfp|f)\\b.*") || s.matches("(?:incf|decf|ldf|ex|exx)\\b.*")) safe = false;
                if (s.endsWith(":")) if (labels.put(s.substring(0, s.length() - 1), i) != null) safe = false;
            }
            if (safe) {
                Set<Integer> drop = new HashSet<>();
                for (int i = start + 1; i < end; i++) {
                    Instruction n = parse(out.get(i));
                    Set<String> written = registerBytes(n.left());
                    boolean pureSource = !registerBytes(n.right()).isEmpty()
                        || AsmOptimizer.parseAsmImmediate(n.right()).isPresent()
                        || n.right().matches("[_.$][\\w.$]+");
                    // Compiler stack reads are ordinary RAM, not MMIO. A
                    // read whose result dies before use may be dropped even
                    // when address materialization separates the overwrites.
                    // Never extend this to arbitrary pointer/global reads:
                    // they may have observable device/volatile side effects.
                    boolean stackSource = stackMemory(n.right());
                    if (n.op().equals("ld") && !written.isEmpty() && (pureSource || stackSource)
                            && registerCopyDead(out, i + 1, end, written, labels)) drop.add(i);
                }
                for (int i = end - 1; i > start; i--) if (drop.contains(i)) removeInstruction(out, i);
            }
            end = start;
        }
        return out;
    }

    private static boolean registerCopyDead(List<String> lines, int first, int end,
            Set<String> bytes, Map<String, Integer> labels) {
        return registerCopyDead(lines, first, end, bytes, labels, null);
    }

    private static boolean registerCopyDead(List<String> lines, int first, int end,
            Set<String> bytes, Map<String, Integer> labels, Set<String> returnProof) {
        return registerCopyDead(lines, first, end, bytes, labels, returnProof, true);
    }

    private static boolean registerCopyDead(List<String> lines, int first, int end,
            Set<String> bytes, Map<String, Integer> labels, Set<String> returnProof, boolean allowCallKills) {
        record State(int line, Set<String> pending) {}
        ArrayDeque<State> work = new ArrayDeque<>();
        Set<State> seen = new HashSet<>();
        work.add(new State(first, Set.copyOf(bytes)));
        while (!work.isEmpty()) {
            State state = work.removeFirst();
            if (!seen.add(state)) continue;
            if (state.line() >= end || seen.size() > 10000) return false;
            String core = lines.get(state.line()).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            Instruction n = parse(lines.get(state.line()));
            if (core.isEmpty() || core.endsWith(":")) {
                work.add(new State(state.line() + 1, state.pending())); continue;
            }
            if (n.op().matches("jr|jrl|jp")) {
                String target = n.right().isEmpty() ? n.left() : n.right();
                Integer destination = labels.get(target);
                if (destination == null) return false;
                work.add(new State(destination, state.pending()));
                if (!n.right().isEmpty()) work.add(new State(state.line() + 1, state.pending()));
                continue;
            }
            if (n.op().equals("call") && calleeLeavesBytesUntouched(lines, n.left(), state.pending(), new HashSet<>())) {
                work.add(new State(state.line() + 1, state.pending())); continue;
            }
            if (n.op().equals("call") && allowCallKills && calleeKillsBytesBeforeRead(lines, n.left(), state.pending())) continue;
            if (n.op().equals("ret") && n.left().isEmpty() && returnLanesDeadByDeclaredAbi(lines, state.line(), state.pending())) continue;
            if (n.op().equals("ret") && n.left().isEmpty() && returnProof != null) {
                if (!returnedBytesDeadAtCallers(lines, state.line(), state.pending(), returnProof)) return false;
                continue;
            }
            if (!dataflowInstruction(n, core)) return false;
            Set<String> reads = operandBytes(core), pending = new HashSet<>(state.pending());
            if (n.op().matches("ld|lda|pop")) {
                reads = operandBytes(n.right());
                if (registerBytes(n.left()).isEmpty()) reads.addAll(operandBytes(n.left()));
                if (!Collections.disjoint(reads, pending)) return false;
                pending.removeAll(registerBytes(n.left()));
            } else if (n.op().equals("extz") && !zeroExtensionBytes(n.left()).isEmpty()) {
                Set<String> cleared = zeroExtensionBytes(n.left());
                reads.removeAll(cleared);
                if (!Collections.disjoint(reads, pending)) return false;
                pending.removeAll(cleared);
            } else if (n.op().equals("and") && AsmOptimizer.parseAsmImmediate(n.right()).isPresent()
                    && !registerBytes(n.left()).isEmpty()) {
                Set<String> cleared = constantMaskClearedBytes(n.left(), AsmOptimizer.parseAsmImmediate(n.right()).getAsLong());
                reads.removeAll(cleared);
                if (!Collections.disjoint(reads, pending)) return false;
                pending.removeAll(cleared);
            } else if (n.op().equals("xor") && n.left().equals(n.right()) && !registerBytes(n.left()).isEmpty()) {
                // XOR R,R produces zero and fixed logical flags without
                // observing the previous register value.
                pending.removeAll(registerBytes(n.left()));
            } else if (!Collections.disjoint(reads, pending)) return false;
            if (!pending.isEmpty()) work.add(new State(state.line() + 1, Set.copyOf(pending)));
        }
        return true;
    }

    /** A known callee may also kill all pending input bytes before reading any
     * of them. Nested calls get only untouched-byte summaries, preventing
     * recursive call-kill proofs. Stack balance and bank stability are required. */
    private static boolean calleeKillsBytesBeforeRead(List<String> lines, String target, Set<String> bytes) {
        if (!target.matches("_[\\w.$]+")) return false;
        int start = -1, end = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.equals(target + ":")) start = i;
            else if (start >= 0 && core.matches("_[\\w.$]+:")) { end = i; break; }
        }
        if (start < 0) return false;
        List<Instruction> code = new ArrayList<>();
        Map<String, Integer> labels = new HashMap<>();
        for (int i = start + 1; i < end; i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            Instruction n = parse(lines.get(i));
            if (!n.op().isEmpty()) code.add(n);
            if (core.matches(".*\\b(?:sr|rfp|f)\\b.*") || core.matches("(?:incf|decf|ldf|ex|exx)\\b.*")) return false;
            if (core.endsWith(":")) if (labels.put(core.substring(0, core.length() - 1), i) != null) return false;
            if (core.contains("xsp") && !(core.matches("add xsp, -?\\d+")
                    || core.matches("ld [^,]+, \\(xsp\\+\\d+\\)") || core.matches("ld \\(xsp\\+\\d+\\), .+"))) return false;
        }
        if (code.isEmpty() || !code.get(code.size() - 1).op().equals("ret")
                || code.subList(0, code.size() - 1).stream().anyMatch(n -> n.op().equals("ret"))
                || saveRemovalStackDepths(code, 0, code.size() - 1) == null) return false;
        return registerCopyDead(lines, start + 1, end, bytes, labels, null, false);
    }

    /** Strict whole-module call summary: no reads OR writes of these lanes,
     * no bank switches, opaque calls, traps or escaping stack addresses. */
    static boolean calleeLeavesBytesUntouched(List<String> lines, String target,
            Set<String> bytes, Set<String> visiting) {
        if (!target.matches("_[\\w.$]+") || !visiting.add(target)) return false;
        int start = -1, end = lines.size();
        for (int i = 0; i < lines.size(); i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.equals(target + ":")) {
                if (start >= 0) return false;
                start = i;
            } else if (start >= 0 && core.matches("_[\\w.$]+:")) { end = i; break; }
        }
        if (start < 0) return false;
        List<Instruction> calleeCode = new ArrayList<>();
        for (int i = start + 1; i < end; i++) {
            Instruction n = parse(lines.get(i));
            if (!n.op().isEmpty()) calleeCode.add(n);
        }
        if (calleeCode.isEmpty() || !calleeCode.get(calleeCode.size() - 1).op().equals("ret")
                || calleeCode.subList(0, calleeCode.size() - 1).stream().anyMatch(n -> n.op().equals("ret"))
                || saveRemovalStackDepths(calleeCode, 0, calleeCode.size() - 1) == null) return false;
        Set<String> labels = new HashSet<>();
        for (int i = start + 1; i < end; i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.endsWith(":")) labels.add(core.substring(0, core.length() - 1));
        }
        boolean returned = false;
        for (int i = start + 1; i < end; i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            Instruction n = parse(lines.get(i));
            if (core.isEmpty() || core.endsWith(":")) continue;
            if (core.equals("ret")) { returned = true; continue; }
            if (n.op().equals("call")) {
                if (!calleeLeavesBytesUntouched(lines, n.left(), bytes, visiting)) return false;
                continue;
            }
            if (n.op().matches("jr|jrl|jp")) {
                if (!labels.contains(n.right().isEmpty() ? n.left() : n.right())) return false;
                continue;
            }
            if (!dataflowInstruction(n, core) || !Collections.disjoint(operandBytes(core), bytes)
                    || core.contains("xsp") && !(core.matches("add xsp, -?\\d+")
                        || core.matches("ld [^,]+, \\(xsp\\+\\d+\\)")
                        || core.matches("ld \\(xsp\\+\\d+\\), .+"))) return false;
        }
        visiting.remove(target);
        return returned;
    }

    /** Remove mirrored saves for physically unused registers. Rebase only
     * incoming argument references; frame-local offsets remain unchanged. */
    static List<String> removeUnusedRegisterSaves(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int end = out.size(), start = end - 1; start >= 0; start--) {
            if (!out.get(start).split(";", 2)[0].strip().matches("_[\\w.$]+:")) continue;
            List<Integer> positions = new ArrayList<>();
            List<Instruction> code = new ArrayList<>();
            for (int i = start + 1; i < end; i++) {
                // A named allocation fence or END terminates this function even
                // when there is no following global label in the module.
                if (out.get(i).startsWith(AsmOptimizer.PROTECTED_END)) break;
                Instruction n = parse(out.get(i));
                if (n.op().equals("end")) break;
                if (!n.op().isEmpty()) { positions.add(i); code.add(n); }
            }
            int count = 0, savedBytes = 0;
            while (count < code.size() && code.get(count).op().equals("push")
                    && !registerBytes(code.get(count).left()).isEmpty()) {
                savedBytes += registerBytes(code.get(count).left()).size(); count++;
            }
            int last = code.size() - 1, popStart = last - count;
            if (count == 0 || last < count || !code.get(last).op().equals("ret")) { end = start; continue; }
            boolean safe = true;
            for (int i = 0; i < count; i++)
                safe &= code.get(last - 1 - i).equals(new Instruction("pop", code.get(i).left(), ""));
            int frame = 0, body = count, bodyEnd = popStart;
            if (body < bodyEnd && code.get(body).op().equals("add") && code.get(body).left().equals("xsp")
                    && code.get(body).right().matches("-\\d+")) {
                frame = Integer.parseInt(code.get(body).right().substring(1)); body++;
                bodyEnd--;
                safe &= code.get(bodyEnd).equals(new Instruction("add", "xsp", Integer.toString(frame)));
            }
            if (!safe) { end = start; continue; }
            Set<Integer> removed = new HashSet<>();
            Map<Integer, String> narrowed = new HashMap<>();
            int removedBytes = 0;
            for (int saved = 0; saved < count; saved++) {
                Set<String> bytes = registerBytes(code.get(saved).left());
                boolean unused = savedBytesUnused(out, positions, code, body, bodyEnd, bytes);
                if (unused) {
                    removed.add(positions.get(saved)); removed.add(positions.get(last - 1 - saved));
                    removedBytes += bytes.size();
                } else if (bytes.size() == 4) {
                    String wide = code.get(saved).left();
                    String narrow = wide.matches("x(?:wa|bc|de|hl)[0-3]") ? "r" + wide.substring(1) : wide.substring(1);
                    Set<String> low = registerBytes(narrow), upper = new HashSet<>(bytes);
                    upper.removeAll(low);
                    if (low.size() == 2 && savedBytesUnused(out, positions, code, body, bodyEnd, upper)) {
                        int push = positions.get(saved), pop = positions.get(last - 1 - saved);
                        narrowed.put(push, emit(out.get(push), "push " + narrow));
                        narrowed.put(pop, emit(out.get(pop), "pop " + narrow));
                        removedBytes += 2;
                    }
                }
            }
            if (removedBytes > 0) {
                Map<Integer, Integer> depth = saveRemovalStackDepths(code, body, bodyEnd);
                Map<Integer, String> rewritten = new HashMap<>(narrowed);
                if (depth == null) safe = false;
                if (frame > 0) {
                    safe &= flagsDeadAlongSuccessors(out, positions.get(count));
                    safe &= returnFlagsDeadAtCallers(out, out.get(start).split(";", 2)[0].strip().replaceFirst(":$", ""));
                }
                if (safe) for (int i = body; i < bodyEnd; i++) {
                    Instruction n = code.get(i);
                    String raw = out.get(positions.get(i)), core = raw.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                    if (!depth.containsKey(i)) { safe = false; break; }
                    String withoutMemory = core.replaceAll("\\(xsp\\+\\d+\\)", "");
                    if (withoutMemory.contains("xsp") && !core.matches("add xsp, -?\\d+")) { safe = false; break; }
                    if (n.op().equals("lda") && core.contains("xsp")) { safe = false; break; }
                    if (core.matches("add xsp, -?\\d+") && !flagsDeadAlongSuccessors(out, positions.get(i))) { safe = false; break; }
                    Matcher offsets = Pattern.compile("\\(xsp\\+(\\d+)\\)").matcher(raw);
                    StringBuffer result = new StringBuffer();
                    while (offsets.find()) {
                        int offset = Integer.parseInt(offsets.group(1)), normalized = offset - depth.get(i);
                        if (normalized >= frame && normalized < frame + savedBytes + 4) { safe = false; break; }
                        if (normalized < frame && normalized >= 0) {
                            // A local access must not straddle the frame/saves boundary.
                            int width = Math.max(registerBytes(n.left()).size(), registerBytes(n.right()).size());
                            if (width == 0 || normalized + width > frame) { safe = false; break; }
                        }
                        int changed = normalized >= frame + savedBytes + 4 ? offset - removedBytes : offset;
                        offsets.appendReplacement(result, Matcher.quoteReplacement("(xsp+" + changed + ")"));
                    }
                    offsets.appendTail(result);
                    rewritten.put(positions.get(i), result.toString());
                }
                if (safe) {
                    for (var entry : rewritten.entrySet()) out.set(entry.getKey(), entry.getValue());
                    for (int i = end - 1; i > start; i--) if (removed.contains(i)) removeInstruction(out, i);
                }
            }
            end = start;
        }
        return out;
    }

    static List<String> cleanupRegisterValuesAndSaves(List<String> lines) {
        List<String> current = lines;
        while (true) {
            List<String> next = foldOperandRegisterShuffles(current);
            next = foldCommutativeStackOperands(next);
            next = foldCounterCopyBackedges(next);
            next = foldDeadStackRoundtrips(next);
            next = foldRegisterCopyToStackStores(next);
            next = eliminateAvailableRegisterCopies(next);
            next = eliminateRepeatedRegisterMasks(next);
            next = eliminateDeadZeroExtensions(next);
            next = foldMaskedByteComparisons(next);
            next = eliminateDeadRegisterMasks(next);
            next = eliminateDeadRegisterZeroes(next);
            next = narrowRegisterCopiesWithDeadUpperHalves(next);
            next = eliminateDeadRegisterCopies(next);
            next = removeUnusedRegisterSaves(next);
            if (next.equals(current)) return next;
            current = next;
        }
    }

    /** LD T,R; LD R,S; OP T,R; LD R,T -> OP R,S.
     * Then route a saved accumulator's word operand directly to its consumer:
     * LD B,R; LD r16,M; EXTZ R; LD T,R; LD R,B -> LD t16,M.
     * Retain all memory reads and ALU flags; remove only proven-dead lanes. */
    static List<String> foldOperandRegisterShuffles(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        // LD s16,M; EXTZ S; LD (stack),S; LD D,S ->
        // LD d16,M; EXTZ D; LD (stack),D, if S's value is dead afterward.
        for (int i = 0; i + 3 < out.size(); i++) {
            Instruction load = parse(out.get(i)), extend = parse(out.get(i + 1));
            Instruction store = parse(out.get(i + 2)), copy = parse(out.get(i + 3));
            String source = extend.left(), destination = copy.left();
            if (!extend.op().equals("extz") || !extend.right().isEmpty() || width(source) != 32
                    || !disjoint(source, destination) || !load.op().equals("ld")
                    || !load.left().equals(source.substring(1)) || !load.right().startsWith("(")
                    || !dataflowInstruction(load, out.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT))
                    || !store.equals(new Instruction("ld", store.left(), source)) || !stackMemory(store.left())
                    || !copy.equals(new Instruction("ld", destination, source))
                    || !lanesDeadAfterInFunction(out, i + 3, registerBytes(source))) continue;
            String originalLoad = out.get(i).split(";", 2)[0];
            String memory = originalLoad.substring(originalLoad.indexOf(',') + 1).strip();
            out.set(i, emit(out.get(i), "ld " + destination.substring(1) + ", " + memory));
            out.set(i + 1, emit(out.get(i + 1), "extz " + destination));
            out.set(i + 2, emit(out.get(i + 2), "ld " + store.left() + ", " + destination));
            removeInstruction(out, i + 3);
        }
        for (int i = 0; i + 3 < out.size(); i++) {
            Instruction save = parse(out.get(i)), load = parse(out.get(i + 1));
            Instruction alu = parse(out.get(i + 2)), copy = parse(out.get(i + 3));
            String result = save.right(), temp = save.left(), source = load.right();
            if (!save.op().equals("ld") || width(result) != 32 || !disjoint(result, temp)
                    || !load.equals(new Instruction("ld", result, source)) || width(source) != 32
                    || source.equals(temp) || !alu.op().matches("add|sub|adc|sbc|and|or|xor")
                    || !alu.left().equals(temp) || !alu.right().equals(result)
                    || !copy.equals(new Instruction("ld", result, temp))) continue;
            if (!lanesDeadAfterInFunction(out, i + 3, registerBytes(temp))) continue;
            out.set(i, emit(out.get(i), alu.op() + " " + result + ", " + source));
            for (int j = i + 3; j > i; j--) removeInstruction(out, j);
        }
        for (int i = 0; i + 4 < out.size(); i++) {
            Instruction save = parse(out.get(i)), load = parse(out.get(i + 1)), extend = parse(out.get(i + 2));
            Instruction copy = parse(out.get(i + 3)), reload = parse(out.get(i + 4));
            String result = save.right(), home = save.left(), temp = copy.left();
            if (!save.op().equals("ld") || width(result) != 32 || registerBytes(home).size() != 4
                    || !Collections.disjoint(registerBytes(result), registerBytes(home))
                    || !disjoint(result, temp) || !Collections.disjoint(registerBytes(temp), registerBytes(home))
                    || !load.op().equals("ld") || !load.left().equals(result.substring(1))
                    || !load.right().startsWith("(") || !dataflowInstruction(load, out.get(i + 1).split(";", 2)[0].strip().toLowerCase(Locale.ROOT))
                    || !Collections.disjoint(operandBytes(load.right()), registerBytes(home))
                    || !extend.equals(new Instruction("extz", result, ""))
                    || !copy.right().equals(result) || !copy.op().equals("ld")
                    || !reload.equals(new Instruction("ld", result, home))) continue;
            Set<String> upper = new HashSet<>(registerBytes(temp));
            upper.removeAll(registerBytes(temp.substring(1)));
            if (!lanesDeadAfterInFunction(out, i + 4, registerBytes(home))
                    || !lanesDeadAfterInFunction(out, i + 4, upper)) continue;
            String originalLoad = out.get(i + 1).split(";", 2)[0];
            String memory = originalLoad.substring(originalLoad.indexOf(',') + 1).strip();
            out.set(i, emit(out.get(i), "ld " + temp.substring(1) + ", " + memory));
            for (int j = i + 4; j > i; j--) removeInstruction(out, j);
        }
        return out;
    }

    private static boolean lanesDeadAfterInFunction(List<String> lines, int last, Set<String> bytes) {
        return lanesDeadAfterInFunction(lines, last, bytes, null);
    }

    private static boolean lanesDeadAfterInFunction(List<String> lines, int last, Set<String> bytes, Set<String> returnProof) {
        int first = last;
        while (first >= 0 && !lines.get(first).split(";", 2)[0].strip().matches("_[\\w.$]+:")) first--;
        if (first < 0) return false;
        int end = last + 1;
        while (end < lines.size() && !lines.get(end).split(";", 2)[0].strip().matches("_[\\w.$]+:")) end++;
        Map<String, Integer> labels = new HashMap<>();
        for (int i = first + 1; i < end; i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.matches(".*\\b(?:sr|rfp|f)\\b.*") || core.matches("(?:incf|decf|ldf|ex|exx)\\b.*")) return false;
            if (core.endsWith(":")) if (labels.put(core.substring(0, core.length() - 1), i) != null) return false;
        }
        return registerCopyDead(lines, last + 1, end, bytes, labels, returnProof);
    }

    /** No assumed void/word-return ABI: prove the affected return lanes dead
     * at every direct caller. Address-taking, missing callers and recursive
     * return proofs are conservative barriers. */
    private static boolean returnedBytesDeadAtCallers(List<String> lines, int ret, Set<String> bytes, Set<String> proving) {
        int start = ret;
        while (start >= 0 && !lines.get(start).split(";", 2)[0].strip().matches("_[\\w.$]+:")) start--;
        if (start < 0) return false;
        String name = lines.get(start).split(";", 2)[0].strip().replaceFirst(":$", "");
        if (!proving.add(name.toLowerCase(Locale.ROOT))) return false;
        Pattern reference = Pattern.compile("(?<![\\w.$])" + Pattern.quote(name) + "(?![\\w.$])", Pattern.CASE_INSENSITIVE);
        boolean called = false;
        for (int i = 0; i < lines.size(); i++) {
            if (i == start) continue;
            String core = lines.get(i).split(";", 2)[0].strip();
            if (!reference.matcher(core).find()) continue;
            Instruction call = parse(lines.get(i));
            if (!call.op().equals("call") || !call.left().equalsIgnoreCase(name)
                    || !lanesDeadAfterInFunction(lines, i, bytes, proving)) return false;
            called = true;
        }
        proving.remove(name.toLowerCase(Locale.ROOT));
        return called;
    }

    /** Explicit translator metadata, not a guessed void ABI. Only current-bank
     * caller-clobbered WA/HL lanes are dead; flags and preserved registers are
     * deliberately excluded. Metadata must occur at the function entry. */
    private static boolean returnLanesDeadByDeclaredAbi(List<String> lines, int ret, Set<String> bytes) {
        int start = ret;
        while (start >= 0 && !lines.get(start).split(";", 2)[0].strip().matches("_[\\w.$]+:")) start--;
        if (start < 0) return false;
        boolean declared = false;
        for (int i = start + 1; i < lines.size(); i++) {
            String raw = lines.get(i).strip();
            if (raw.equals(VOID_SCRATCH_ABI)) declared = true;
            if (!raw.split(";", 2)[0].isBlank()) break;
        }
        Set<String> clobbered = new HashSet<>(registerBytes("xwa"));
        clobbered.addAll(registerBytes("xhl"));
        return declared && clobbered.containsAll(bytes);
    }

    private static Set<String> zeroExtensionBytes(String register) {
        Set<String> bytes = registerBytes(register);
        if (bytes.size() != 2 && bytes.size() != 4) return Set.of();
        int half = bytes.size() / 2;
        Set<String> cleared = new HashSet<>();
        for (String lane : bytes) if (Integer.parseInt(lane.substring(lane.indexOf(':') + 1)) >= half) cleared.add(lane);
        return cleared;
    }

    /** AND does not observe an input byte whose entire mask byte is zero.
     * Its output value and flags therefore do not depend on those lanes. */
    private static Set<String> constantMaskClearedBytes(String register, long mask) {
        Set<String> bytes = registerBytes(register), cleared = new HashSet<>();
        int first = bytes.stream().mapToInt(lane -> Integer.parseInt(lane.substring(lane.indexOf(':') + 1))).min().orElse(0);
        for (String lane : bytes) {
            int index = Integer.parseInt(lane.substring(lane.indexOf(':') + 1)) - first;
            if (((mask >>> (8 * index)) & 0xffL) == 0) cleared.add(lane);
        }
        return cleared;
    }

    /** Identical ANDs separated only by flag-neutral transfers with no write
     * to the masked register repeat both the value and the logical flags.
     * Preserve the first AND; stop at aliases, flag writers and CFG boundaries. */
    static List<String> eliminateRepeatedRegisterMasks(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = 0; i < out.size(); i++) {
            Instruction first = parse(out.get(i));
            OptionalLong mask = AsmOptimizer.parseAsmImmediate(first.right());
            Set<String> lanes = registerBytes(first.left());
            if (!first.op().equals("and") || lanes.isEmpty() || mask.isEmpty()) continue;
            for (int j = i + 1; j < out.size(); j++) {
                Instruction n = parse(out.get(j));
                String core = out.get(j).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                if (core.isEmpty()) continue;
                if (n.op().equals("and") && n.left().equals(first.left())
                        && AsmOptimizer.parseAsmImmediate(n.right()).equals(mask)) {
                    int before = out.size();
                    removeInstruction(out, j);
                    if (out.size() < before) j--;
                    continue;
                }
                if (!n.op().matches("ld|lda|nop") || !dataflowInstruction(n, core)
                        || !Collections.disjoint(registerBytes(n.left()), lanes)) break;
            }
        }
        return out;
    }

    /** A mask need not execute if all bytes it can change are dead, and its
     * flags are overwritten before observation. Bytes masked with FF are
     * unchanged and may remain live (e.g. A in AND WA,0FFh). */
    static List<String> eliminateDeadRegisterMasks(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = out.size() - 1; i >= 0; i--) {
            Instruction n = parse(out.get(i));
            OptionalLong mask = AsmOptimizer.parseAsmImmediate(n.right());
            Set<String> changed = new HashSet<>(registerBytes(n.left()));
            if (!n.op().equals("and") || mask.isEmpty() || changed.isEmpty()) continue;
            // Complemented zero bytes identify original FF bytes: those
            // input lanes are preserved exactly by the mask.
            changed.removeAll(constantMaskClearedBytes(n.left(), ~mask.getAsLong()));
            if (flagsDeadAlongSuccessors(out, i) && (changed.isEmpty()
                    || lanesDeadAfterInFunction(out, i, changed))) removeInstruction(out, i);
        }
        return out;
    }

    private static String registerLowByte(String register) {
        if (register.matches("x?(?:wa|bc|de|hl)")) return lowByte(register.replaceFirst("^x", ""));
        if (register.matches("[rx](?:wa|bc|de|hl)[0-3]")) return "r" + register.charAt(2) + register.charAt(3);
        return "";
    }

    /** Narrow normalized low-byte compares only when sign/overflow and other
     * non-equivalent flags are dead. Logical-result CP zero needs only Z. */
    static List<String> foldMaskedByteComparisons(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = 0; i < out.size(); i++) {
            Instruction mask = parse(out.get(i));
            OptionalLong value = AsmOptimizer.parseAsmImmediate(mask.right());
            String low = registerLowByte(mask.left());
            if (!mask.op().equals("and") || low.isEmpty() || value.isEmpty()
                    || value.getAsLong() < 0 || value.getAsLong() > 255) continue;
            int j = i + 1;
            while (j < out.size() && out.get(j).split(";", 2)[0].isBlank()) j++;
            if (j >= out.size()) continue;
            Instruction compare = parse(out.get(j));
            OptionalLong immediate = AsmOptimizer.parseAsmImmediate(compare.right());
            if (!compare.op().equals("cp") || !compare.left().equals(mask.left()) || immediate.isEmpty()
                    || immediate.getAsLong() < 0 || immediate.getAsLong() > 255
                    || !onlySelectedFlagsObservedAfter(out, j, Set.of("z", "nz", "eq", "ne", "c", "nc", "ult", "uge", "ule", "ugt"))) continue;
            String original = out.get(j).split(";", 2)[0];
            out.set(j, emit(out.get(j), "cp " + low + ", " + original.substring(original.indexOf(',') + 1).strip()));
        }
        for (int i = out.size() - 1; i >= 0; i--) {
            Instruction compare = parse(out.get(i));
            OptionalLong value = AsmOptimizer.parseAsmImmediate(compare.right());
            if (!compare.op().equals("cp") || registerBytes(compare.left()).isEmpty() || value.isEmpty()
                    || value.getAsLong() != 0 || !onlySelectedFlagsObservedAfter(out, i, Set.of("z", "nz", "eq", "ne"))) continue;
            Set<String> lanes = registerBytes(compare.left());
            for (int j = i - 1; j >= 0; j--) {
                String core = out.get(j).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                Instruction producer = parse(out.get(j));
                if (core.isEmpty()) continue;
                OptionalLong producerMask = AsmOptimizer.parseAsmImmediate(producer.right());
                boolean narrowMaskedResult = producer.op().equals("and") && producerMask.isPresent()
                    // Leave a pure wide byte-normalization mask's CP in
                    // place until dead-mask cleanup can expose a preceding
                    // byte logical producer, rather than pinning that mask
                    // solely as a Z-flag producer.
                    && producerMask.getAsLong() >= 0 && producerMask.getAsLong() < 255
                    && registerLowByte(producer.left()).equals(compare.left());
                if (producer.op().matches("and|or|xor") && (producer.left().equals(compare.left()) || narrowMaskedResult)) {
                    removeInstruction(out, i); break;
                }
                if (!producer.op().matches("ld|lda|nop") || !dataflowInstruction(producer, core)
                        || !Collections.disjoint(registerBytes(producer.left()), lanes)) break;
            }
        }
        return out;
    }

    /** Follow local successors until a complete flag overwrite. Only the
     * listed conditions may observe the old flags; opaque effects/returns
     * and cycles are rejected instead of assuming flags dead at the ABI. */
    private static boolean onlySelectedFlagsObservedAfter(List<String> lines, int last, Set<String> conditions) {
        int start = last;
        while (start >= 0 && !lines.get(start).split(";", 2)[0].strip().matches("_[\\w.$]+:")) start--;
        if (start < 0) return false;
        int end = last + 1;
        while (end < lines.size() && !lines.get(end).split(";", 2)[0].strip().matches("_[\\w.$]+:")) end++;
        Map<String, Integer> labels = new HashMap<>();
        for (int i = start + 1; i < end; i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.endsWith(":")) if (labels.put(core.substring(0, core.length() - 1), i) != null) return false;
        }
        return selectedFlagsPath(lines, last + 1, end, labels, conditions, new HashSet<>(), new HashSet<>());
    }

    private static boolean selectedFlagsPath(List<String> lines, int first, int end, Map<String, Integer> labels,
            Set<String> conditions, Set<Integer> visiting, Set<Integer> proven) {
        if (first >= end || visiting.size() > 1000 || !visiting.add(first)) return false;
        if (proven.contains(first)) { visiting.remove(first); return true; }
        String core = lines.get(first).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        Instruction n = parse(lines.get(first));
        boolean safe;
        if (core.isEmpty() || core.endsWith(":")) safe = selectedFlagsPath(lines, first + 1, end, labels, conditions, visiting, proven);
        else if (n.op().matches("cp|add|sub|and|or|xor") && dataflowInstruction(n, core)) safe = true;
        else if (n.op().equals("scc") && conditions.contains(n.left()) && dataflowInstruction(n, core)
                && !registerBytes(n.right()).isEmpty())
            safe = selectedFlagsPath(lines, first + 1, end, labels, conditions, visiting, proven);
        else if (n.op().matches("jr|jrl|jp")) {
            String target = n.right().isEmpty() ? n.left() : n.right();
            safe = labels.containsKey(target) && (n.right().isEmpty() || conditions.contains(n.left()))
                && selectedFlagsPath(lines, labels.get(target), end, labels, conditions, visiting, proven)
                && (n.right().isEmpty() || selectedFlagsPath(lines, first + 1, end, labels, conditions, visiting, proven));
        } else safe = n.op().matches("ld|lda|extz|nop") && dataflowInstruction(n, core)
            && selectedFlagsPath(lines, first + 1, end, labels, conditions, visiting, proven);
        visiting.remove(first);
        if (safe) proven.add(first);
        return safe;
    }

    static List<String> eliminateDeadRegisterZeroes(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = out.size() - 1; i >= 0; i--) {
            Instruction n = parse(out.get(i));
            Set<String> lanes = registerBytes(n.left());
            if (n.op().equals("xor") && n.left().equals(n.right()) && !lanes.isEmpty()
                    && lanesDeadAfterInFunction(out, i, lanes) && flagsDeadAlongSuccessors(out, i)) removeInstruction(out, i);
        }
        return out;
    }

    private static String lowHalfRegister(String register) {
        if (register.matches("x(?:wa|bc|de|hl)[0-3]")) return "r" + register.substring(1);
        if (register.matches("x(?:wa|bc|de|hl|ix|iy|iz)")) return register.substring(1);
        return registerLowByte(register);
    }

    /** A wide register copy need only copy its low half when the destination's
     * upper lanes are dead, including proven dead lanes at every direct caller.
     * This can expose an earlier wide save as dead after a partial overwrite. */
    static List<String> narrowRegisterCopiesWithDeadUpperHalves(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = 0; i < out.size(); i++) {
            Instruction n = parse(out.get(i));
            Set<String> destination = registerBytes(n.left()), source = registerBytes(n.right());
            String lowDestination = lowHalfRegister(n.left()), lowSource = lowHalfRegister(n.right());
            if (!n.op().equals("ld") || destination.size() < 2 || destination.size() != source.size()
                    || !Collections.disjoint(destination, source) || lowDestination.isEmpty() || lowSource.isEmpty()) continue;
            Set<String> upper = new HashSet<>(destination);
            upper.removeAll(registerBytes(lowDestination));
            if (upper.size() != destination.size() / 2 || registerBytes(lowSource).size() != source.size() / 2) continue;
            if (lanesDeadAfterInFunction(out, i, upper, new HashSet<>()))
                out.set(i, emit(out.get(i), "ld " + lowDestination + ", " + lowSource));
        }
        return out;
    }

    /** Keep a spilled value in its eventual destination. Restrict the proof to
     * a straight-line window ending in a complete overwrite of the stack slot.
     * Unknown memory, overlapping accesses, SP changes and escaped frame
     * addresses are barriers; the destination must be untouched before reload. */
    static List<String> foldDeadStackRoundtrips(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = 0; i < out.size(); i++) {
            Instruction store = parse(out.get(i));
            if (!store.op().equals("ld") || !stackMemory(store.left()) || width(store.right()) == 0
                    || !frameAddressUnescaped(out, i)) continue;
            int offset = Integer.parseInt(store.left().substring(5, store.left().length() - 1));
            int size = width(store.right()) / 8, reload = -1;
            String destination = "";
            boolean overwritten = false;
            for (int j = i + 1; j < out.size(); j++) {
                Instruction n = parse(out.get(j));
                String core = out.get(j).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                if (core.isEmpty()) continue;
                if (!dataflowInstruction(n, core) || n.op().matches("pushw?|pop")
                        || n.left().equals("xsp")) break;
                boolean stop = false;
                for (String operand : List.of(n.left(), n.right())) {
                    if (!operand.contains("(")) continue;
                    if (!n.op().equals("ld") || !stackMemory(operand)) { stop = true; break; }
                    int at = Integer.parseInt(operand.substring(5, operand.length() - 1));
                    int accessSize = width(operand.equals(n.left()) ? n.right() : n.left()) / 8;
                    if (accessSize == 0) { stop = true; break; }
                    if (at >= offset + size || at + accessSize <= offset) continue;
                    if (reload < 0 && operand.equals(n.right()) && at == offset && accessSize == size
                            && disjoint(n.left(), store.right())) {
                        reload = j; destination = n.left();
                    } else if (reload >= 0 && operand.equals(n.left()) && at <= offset && at + accessSize >= offset + size) {
                        overwritten = true;
                    } else stop = true;
                }
                if (stop || overwritten) break;
            }
            if (!overwritten || reload < 0) continue;
            Set<String> lanes = registerBytes(destination);
            boolean untouched = true;
            for (int j = i + 1; j < reload; j++) {
                String core = out.get(j).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                if (!Collections.disjoint(operandBytes(core), lanes)) { untouched = false; break; }
            }
            if (!untouched) continue;
            out.set(i, emit(out.get(i), "ld " + destination + ", " + store.right()));
            removeInstruction(out, reload);
        }
        return out;
    }

    /** LD T,S; LD (stack),T -> LD (stack),S when the copied T lanes are
     * dead on every successor. The store keeps its width and address, and
     * both instructions are flag-neutral. Explicit-bank sources require the
     * ordinary-register bridge and must not be routed directly to memory. */
    static List<String> foldRegisterCopyToStackStores(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = 0; i + 1 < out.size(); i++) {
            Instruction copy = parse(out.get(i)), store = parse(out.get(i + 1));
            Set<String> temp = registerBytes(copy.left()), source = registerBytes(copy.right());
            if (!copy.op().equals("ld") || temp.isEmpty() || temp.size() != source.size()
                    // Memory stores use the ordinary-register encoding, not
                    // the extended register IDs used by banked LD R,R.
                    || width(copy.right()) == 0
                    || !Collections.disjoint(temp, source) || !store.op().equals("ld")
                    || !store.right().equals(copy.left()) || !stackMemory(store.left())
                    || !lanesDeadAfterInFunction(out, i + 1, temp)) continue;
            out.set(i, emit(out.get(i), "ld " + store.left() + ", " + copy.right()));
            removeInstruction(out, i + 1);
        }
        return out;
    }

    /** LD T,R; LD R,(stack); OP R,T -> OP R,(stack) for commutative
     * bitwise operations. Preserve the stack read's width and logical flags;
     * require all copied T lanes dead after the operation. */
    static List<String> foldCommutativeStackOperands(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = 0; i + 2 < out.size(); i++) {
            Instruction save = parse(out.get(i)), load = parse(out.get(i + 1)), op = parse(out.get(i + 2));
            Set<String> temp = registerBytes(save.left()), result = registerBytes(save.right());
            if (!save.op().equals("ld") || temp.isEmpty() || temp.size() != result.size()
                    || width(save.right()) == 0
                    || !Collections.disjoint(temp, result) || !load.op().equals("ld")
                    || !load.left().equals(save.right()) || !stackMemory(load.right())
                    || !op.op().matches("and|or|xor") || !op.left().equals(save.right()) || !op.right().equals(save.left())
                    || !lanesDeadAfterInFunction(out, i + 2, temp)) continue;
            out.set(i, emit(out.get(i), op.op() + " " + save.right() + ", " + load.right()));
            removeInstruction(out, i + 2);
            removeInstruction(out, i + 1);
        }
        return out;
    }

    private static boolean frameAddressUnescaped(List<String> lines, int position) {
        int start = position;
        while (start >= 0 && !lines.get(start).split(";", 2)[0].strip().matches("_[\\w.$]+:")) start--;
        if (start < 0) return false;
        for (int i = start + 1; i < lines.size(); i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.matches("_[\\w.$]+:")) break;
            if (!core.contains("xsp")) continue;
            Instruction n = parse(lines.get(i));
            if (n.op().equals("add") && n.left().equals("xsp") && n.right().matches("-?\\d+")) continue;
            if (n.op().matches("inc|dec") && n.right().equals("xsp") && n.left().matches("[1-8]")) continue;
            if (dataflowInstruction(n, core) && !n.op().equals("lda") && ((stackMemory(n.left()) && !n.right().contains("xsp"))
                    || (stackMemory(n.right()) && !n.left().contains("xsp")))) continue;
            return false;
        }
        return true;
    }

    /** EXTZ is flag-neutral. Only its cleared high half needs to be dead;
     * low-half compares/loads/multipliers do not observe that write. */
    static List<String> eliminateDeadZeroExtensions(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        for (int i = out.size() - 1; i >= 0; i--) {
            Instruction n = parse(out.get(i));
            Set<String> cleared = zeroExtensionBytes(n.left());
            if (n.op().equals("extz") && n.right().isEmpty() && !cleared.isEmpty()
                    && lanesDeadAfterInFunction(out, i, cleared, new HashSet<>())) removeInstruction(out, i);
        }
        return out;
    }

    /** Move a speculative counter update off a scratch register and collapse
     * the exit-test/copy-back/jump into an inverted conditional backedge.
     * The counter's old exit value AND the scratch result must be dead. */
    static List<String> foldCounterCopyBackedges(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        if (!lines.contains(BANK_ZERO_ABI)) return out;
        Map<String, String> inverse = Map.of("c", "nc", "nc", "c", "z", "nz", "nz", "z",
            "eq", "ne", "ne", "eq", "lt", "ge", "ge", "lt", "le", "gt", "gt", "le");
        for (int i = 0; i < out.size(); i++) {
            List<Integer> p = new ArrayList<>();
            for (int j = i; j < out.size() && p.size() < 7; j++) if (!parse(out.get(j)).op().isEmpty()) p.add(j);
            if (p.size() < 7) continue;
            Instruction save = parse(out.get(p.get(0))), update = parse(out.get(p.get(1))), compare = parse(out.get(p.get(2)));
            Instruction branch = parse(out.get(p.get(3))), copy = parse(out.get(p.get(4))), jump = parse(out.get(p.get(5))), exit = parse(out.get(p.get(6)));
            String temp = save.left(), counter = save.right();
            if (!save.op().equals("ld") || width(counter) < 16 || !disjoint(temp, counter)
                    || !update.op().matches("inc|dec") || !update.right().equals(temp)
                    || !update.left().matches("[1-8]") || !compare.op().equals("cp") || !compare.left().equals(temp)
                    || AsmOptimizer.parseAsmImmediate(compare.right()).isEmpty()
                    || !branch.op().matches("jr|jrl") || !inverse.containsKey(branch.left())
                    || !copy.equals(new Instruction("ld", counter, temp)) || !jump.op().matches("jr|jrl")
                    || !jump.right().isEmpty() || !jump.left().startsWith(".")
                    || !exit.op().equals(branch.right() + ":") || !exit.left().isEmpty()) continue;
            int exitLine = p.get(6);
            if (!lanesDeadAfterInFunction(out, exitLine - 1, registerBytes(counter))
                    || !lanesDeadAfterInFunction(out, exitLine - 1, registerBytes(temp))
                    || !lanesDeadAfterInFunction(out, p.get(4), registerBytes(temp))) continue;
            // Both conditional and unconditional long forms have the same
            // displacement range. Never shorten a long jump without assembly.
            String cc = inverse.get(branch.left()).toUpperCase(Locale.ROOT);
            String compareCore = out.get(p.get(2)).split(";", 2)[0];
            String immediate = compareCore.substring(compareCore.indexOf(',') + 1).strip();
            String originalJump = out.get(p.get(5)).split(";", 2)[0].strip();
            String loopLabel = originalJump.replaceFirst("^\\S+\\s+", "");
            out.set(p.get(0), emit(out.get(p.get(0)), update.op() + " " + update.left() + ", " + counter));
            out.set(p.get(1), emit(out.get(p.get(1)), "cp " + counter + ", " + immediate));
            out.set(p.get(2), emit(out.get(p.get(2)), jump.op() + " " + cc + ", " + loopLabel));
            for (int j = 5; j >= 3; j--) removeInstruction(out, p.get(j));
        }
        return out;
    }

    private static boolean savedBytesUnused(List<String> lines, List<Integer> positions,
            List<Instruction> code, int first, int end, Set<String> bytes) {
        for (int i = first; i < end; i++) {
            Instruction n = code.get(i);
            String core = lines.get(positions.get(i)).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (n.op().endsWith(":")) continue;
            if (n.op().equals("call")) {
                if (!calleeLeavesBytesUntouched(lines, n.left(), bytes, new HashSet<>())) return false;
            } else if (!n.op().matches("jr|jrl|jp")
                    && (!dataflowInstruction(n, core) || !Collections.disjoint(operandBytes(core), bytes))) return false;
        }
        return true;
    }

    private static boolean returnFlagsDeadAtCallers(List<String> lines, String function) {
        Pattern reference = Pattern.compile("(?<![\\w.$])" + Pattern.quote(function) + "(?![\\w.$])", Pattern.CASE_INSENSITIVE);
        boolean called = false;
        for (int i = 0; i < lines.size(); i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (!reference.matcher(core).find() || core.equals(function.toLowerCase(Locale.ROOT) + ":")) continue;
            Instruction n = parse(lines.get(i));
            if (!n.op().equals("call") || !n.left().equalsIgnoreCase(function) || !flagsDeadAlongSuccessors(lines, i)) return false;
            called = true;
        }
        return called;
    }

    private static Map<Integer, Integer> saveRemovalStackDepths(List<Instruction> code, int first, int end) {
        Map<String, Integer> labels = new HashMap<>();
        for (int i = first; i < end; i++) if (code.get(i).op().endsWith(":"))
            if (labels.put(code.get(i).op().replaceFirst(":$", ""), i) != null) return null;
        ArrayDeque<int[]> work = new ArrayDeque<>();
        Map<Integer, Integer> depths = new HashMap<>();
        work.add(new int[]{first, 0});
        while (!work.isEmpty()) {
            int[] state = work.removeFirst();
            int i = state[0], depth = state[1];
            if (i == end) { if (depth != 0) return null; continue; }
            if (i < first || i > end || depth < 0) return null;
            Integer previous = depths.putIfAbsent(i, depth);
            if (previous != null) { if (previous != depth) return null; continue; }
            Instruction n = code.get(i);
            if (n.op().equals("push") || n.op().equals("pop")) {
                int width = registerBytes(n.left()).size();
                if (width == 0) return null;
                depth += n.op().equals("push") ? width : -width;
            } else if (n.op().equals("pushw")) depth += 2;
            else if (n.op().equals("add") && n.left().equals("xsp")) {
                if (!n.right().matches("-?\\d+")) return null;
                depth -= Integer.parseInt(n.right());
            }
            if (n.op().matches("jr|jrl|jp")) {
                Integer target = labels.get(n.right().isEmpty() ? n.left() : n.right());
                if (target == null) return null;
                work.add(new int[]{target, depth});
                if (n.right().isEmpty()) continue;
            }
            work.add(new int[]{i + 1, depth});
        }
        return depths;
    }

    static List<String> eliminateRepeatedByteZeroes(List<String> lines) {
        List<String> out = new ArrayList<>();
        Set<String> zero = new HashSet<>();
        Pattern label = Pattern.compile("^[.\\w].*:.*$");
        for (int i = 0; i < lines.size(); i++) {
            String raw = lines.get(i), core = raw.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            Instruction n = parse(raw);
            if (core.isEmpty()) { out.add(raw); continue; }
            if (n.op().matches("jr|jrl|jp") && n.left().matches("z|nz|eq|ne|c|nc|s|ns|v|nv|lt|le|gt|ge|ult|ule|ugt|uge")) {
                out.add(raw); continue;
            }
            if (!n.op().matches("ld|lda|add|sub|adc|sbc|and|or|xor|cp|inc|dec|sla|sra|srl|extz|exts|scc|bit|pushw?|pop|nop")
                    || core.matches(".*\\b(?:sr|f|rfp|[rq](?:wa|bc|de|hl|ix|iy|iz|sp|[wabcdehl])[0-9]*|x(?:wa|bc|de|hl)[0-3])\\b.*")
                    || core.matches(".*\\([^)]*[+-]\\).*")) {
                zero.clear(); out.add(raw); continue;
            }
            boolean clearsByte = n.op().equals("xor") && n.left().equals(n.right()) && width(n.left()) == 8;
            OptionalLong immediate = AsmOptimizer.parseAsmImmediate(n.right());
            if (n.op().equals("and") && n.left().matches("wa|bc|de|hl")
                    && immediate.isPresent() && immediate.getAsLong() == 255
                    && zero.contains(n.left().substring(0, 1)) && flagsDeadAlongSuccessors(lines, i)) {
                int c = raw.indexOf(';');
                if (c >= 0) out.add("\t" + raw.substring(c));
                continue;
            }
            if (clearsByte && zero.contains(n.left()) && AsmOptimizer.flagsAreDeadAfterLine(lines, i, label)) {
                int c = raw.indexOf(';');
                if (c >= 0) out.add("\t" + raw.substring(c));
                continue;
            }
            String destination = n.op().matches("inc|dec|sla|sra|srl|scc") ? n.right() : n.left();
            // A low-byte write does not invalidate its sibling high byte.
            if (!n.op().matches("cp|bit|pushw?|nop")) {
                if (width(destination) == 8) zero.remove(destination);
                else zero.removeAll(aliases(destination));
            }
            if (clearsByte) zero.add(n.left());
            // AND word/wide,low8 proves the high byte is zero, regardless
            // of the previous value. Retain the AND and its exact flags.
            OptionalLong mask = AsmOptimizer.parseAsmImmediate(n.right());
            String pair = n.left().startsWith("x") ? n.left().substring(1) : n.left();
            if (n.op().equals("and") && pair.matches("wa|bc|de|hl")
                    && mask.isPresent() && mask.getAsLong() >= 0 && mask.getAsLong() <= 255)
                zero.add(pair.substring(0, 1));
            out.add(raw);
        }
        return out;
    }

    /** Prove a complete flag overwrite on every local successor; unknown
     * instructions, calls, status accesses and cycles are conservative barriers. */
    static boolean flagsDeadAlongSuccessors(List<String> lines, int last) {
        // Local labels may be reused by different assembler functions. Their
        // ambiguity outside this function must not poison a local proof, nor
        // may a jump accidentally resolve into another function's label.
        int begin = last;
        while (begin >= 0 && !lines.get(begin).split(";", 2)[0].strip().matches("_[\\w.$]+:")) begin--;
        begin = Math.max(0, begin);
        int end = last + 1;
        while (end < lines.size() && !lines.get(end).split(";", 2)[0].strip().matches("_[\\w.$]+:")) end++;
        Map<String, Integer> labels = new HashMap<>();
        for (int i = begin; i < end; i++) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (core.endsWith(":")) {
                String name = core.substring(0, core.length() - 1);
                if (labels.put(name, i) != null) return false;
            }
        }
        Set<Integer> seen = new HashSet<>();
        int i = last + 1;
        // Only unconditional jumps are traversed: a conditional jump reads flags.
        while (i < end && seen.add(i)) {
            String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            Instruction n = parse(lines.get(i));
            if (core.isEmpty() || core.startsWith(".") && core.endsWith(":")) { i++; continue; }
            if (core.matches(".*\\b(?:sr|f|rfp)\\b.*")) return false;
            if (n.op().matches("cp|add|sub|and|or|xor")) return true;
            if (n.op().matches("ld|lda|extz|inc|dec|sla|sra|srl|nop")) { i++; continue; }
            if (n.op().matches("jr|jrl|jp") && n.right().isEmpty() && labels.containsKey(n.left())) {
                i = labels.get(n.left()); continue;
            }
            return false;
        }
        return false;
    }

    static List<String> eliminateZeroBeforeByteExtend(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 2 < out.size(); i++) {
            Instruction zero = parse(out.get(i)), load = parse(out.get(i + 1)), extend = parse(out.get(i + 2));
            String low = switch (zero.left()) { case "wa" -> "a"; case "bc" -> "c"; case "de" -> "e"; case "hl" -> "l"; default -> ""; };
            if (low.isEmpty() || !zero.op().equals("ld") || !zero.right().equals("0")
                    || !load.op().equals("ld") || !load.left().equals(low) || mentions(load.right(), zero.left())
                    || !extend.equals(new Instruction("extz", zero.left(), ""))) continue;
            removeInstruction(out, i);
        }
        return out;
    }

    static List<String> foldReloadAlu(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 1 < out.size(); i++) {
            Instruction load = parse(out.get(i)), alu = parse(out.get(i + 1));
            String temp = load.left();
            if (!load.op().equals("ld") || width(temp) < 16 || !stackMemory(load.right())
                    || !alu.op().matches("add|adc|sub|sbc|and|or|xor") || !alu.right().equals(temp)
                    || !disjoint(temp, alu.left()) || !deadAfter(out, i + 1, temp)) continue;
            out.set(i + 1, emit(out.get(i + 1), alu.op() + " " + alu.left() + ", " + load.right()));
            removeInstruction(out, i);
            i--;
        }
        return out;
    }

    static List<String> foldImmediateAluStore(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i + 2 < out.size(); i++) {
            Instruction load = parse(out.get(i)), add = parse(out.get(i + 1)), store = parse(out.get(i + 2));
            String temp = load.left(), source = add.right();
            if (!load.op().equals("ld") || AsmOptimizer.parseAsmImmediate(load.right()).isEmpty()
                    || !add.op().equals("add") || !add.left().equals(temp) || !disjoint(temp, source)
                    || width(temp) < 16 || !store.op().equals("ld") || !store.right().equals(temp)
                    || !stackMemory(store.left()) || !deadAfter(out, i + 2, temp)
                    || !deadAfter(out, i + 2, source)) continue;
            out.set(i, emit(out.get(i), "add " + source + ", " + load.right()));
            out.set(i + 2, emit(out.get(i + 2), "ld " + store.left() + ", " + source));
            removeInstruction(out, i + 1);
        }
        return out;
    }

    /** Byte counter RMW on a direct symbol. Preserve the operation family when
     * flags are live; ADD/SUB may use shorter INC/DEC only with dead flags.
     * No register-dependent addresses, MMIO literals or intervening code. */
    static List<String> foldByteMemoryCounters(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        for (int i = out.size() - 3; i >= 0; i--) {
            Instruction load = parse(out.get(i)), operation = parse(out.get(i + 1));
            Instruction store = parse(out.get(i + 2));
            boolean arithmetic = operation.op().matches("add|sub");
            OptionalLong count = AsmOptimizer.parseAsmImmediate(arithmetic ? operation.right() : operation.left());
            if (!load.op().equals("ld") || width(load.left()) != 8
                    || !load.right().matches("\\(_[\\w.$]+\\)")
                    || !operation.op().matches("inc|dec|add|sub")
                    || !(arithmetic ? operation.left() : operation.right()).equals(load.left())
                    || count.isEmpty() || count.getAsLong() < 1 || count.getAsLong() > (arithmetic ? 255 : 8)
                    || !store.equals(new Instruction("ld", load.right(), load.left()))
                    || !deadAfter(out, i + 2, load.left())) continue;
            boolean flagsDead = counterFlagsDeadLocally(out, i + 2);
            // Byte register and memory INC/DEC have matching flag effects.
            // ADD/SUB also match their memory forms, but switching families
            // changes carry, so only do that when flags are dead.
            String op = operation.op();
            if (arithmetic && count.getAsLong() <= 8 && flagsDead)
                op = op.equals("add") ? "inc" : "dec";
            String raw = out.get(i).split(";", 2)[0];
            String address = raw.substring(raw.indexOf(',') + 1).trim(); // preserve symbol case
            String operands = op.matches("inc|dec") ? count.getAsLong() + ", " + address
                : address + ", " + Ir2Tlcs900.fmtImm(count.getAsLong());
            out.set(i, emit(out.get(i), op + "b " + operands));
            removeInstruction(out, i + 2);
            removeInstruction(out, i + 1);
        }
        return out;
    }

    private static boolean counterFlagsDeadLocally(List<String> lines, int last) {
        for (int i = last + 1; i < lines.size(); i++) {
            Instruction n = parse(lines.get(i));
            if (n.op().isEmpty()) continue;
            String core = lines.get(i).split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (core.matches(".*\\b(?:sr|f|rfp)\\b.*")) return false;
            if (n.op().matches("ld|lda|extz|nop")) continue;
            return n.op().matches("cp|add|sub|and|or|xor") && width(n.left()) != 0;
        }
        return false;
    }

    /** Whole-function private-frame analysis, not 'unread before next label'. */
    static List<String> eliminateUnreadPrivateStores(List<String> lines) {
        List<String> out = new ArrayList<>(lines);
        Set<Integer> drop = new HashSet<>();
        Pattern function = Pattern.compile("_[\\w.$]+:");
        Pattern slot = Pattern.compile("\\(xsp\\+(\\d+)\\)");
        for (int first = 0; first < lines.size(); first++) {
            if (!function.matcher(lines.get(first).split(";", 2)[0].strip()).matches()) continue;
            int end = first + 1;
            while (end < lines.size() && !function.matcher(lines.get(end).split(";", 2)[0].strip()).matches()) end++;
            int allocation = -1, bytes = 0, release = -1;
            boolean safe = true;
            Set<String> labels = new HashSet<>();
            for (int i = first + 1; i < end; i++) {
                String core = lines.get(i).split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
                if (core.endsWith(":")) labels.add(core.substring(0, core.length() - 1));
                Instruction n = parse(lines.get(i));
                if (n.op().equals("add") && n.left().equals("xsp")) {
                    OptionalLong size = AsmOptimizer.parseAsmImmediate(n.right());
                    if (size.isEmpty()) { safe = false; break; }
                    if (size.getAsLong() < 0 && allocation < 0 && -size.getAsLong() <= 32767) {
                        allocation = i; bytes = (int) -size.getAsLong();
                    } else if (size.getAsLong() == bytes && allocation >= 0 && release < 0) release = i;
                    else safe = false;
                    continue;
                }
                if (core.isEmpty() || core.endsWith(":")) continue;
                if (n.op().equals("end") && release >= 0) { end = i; break; }
                if (allocation < 0 && n.op().equals("push") && width(n.left()) > 0) continue;
                if (release >= 0 && (n.op().equals("pop") || n.op().equals("ret"))) continue;
                if (!n.op().matches("ld|lda|add|sub|and|or|xor|adc|sbc|cp|inc|dec|extz|exts|sla|sra|srl|bit|scc|jr|jrl|jp|nop")) safe = false;
                if (n.op().equals("lda") && slot.matcher(core).find()
                        || slot.matcher(core).replaceAll("").matches(".*\\b(?:xsp|sp|[rq]sp[0-9]*|sr|rfp)\\b.*")) safe = false;
                if (release >= 0 && slot.matcher(core).find()) safe = false;
            }
            if (!safe || allocation < 0 || release < 0) { first = end - 1; continue; }
            List<int[]> reads = new ArrayList<>();
            Map<Integer, int[]> stores = new HashMap<>();
            for (int i = allocation + 1; i < release; i++) {
                Instruction n = parse(lines.get(i));
                if (n.op().matches("jr|jrl|jp")) {
                    String target = n.right().isEmpty() ? n.left() : n.right();
                    if (!labels.contains(target)) safe = false;
                }
                Matcher m = slot.matcher(n.left());
                if (n.op().equals("ld") && m.matches() && width(n.right()) > 0) {
                    int offset = Integer.parseInt(m.group(1));
                    stores.put(i, new int[]{offset, width(n.right()) / 8});
                } else {
                    m = slot.matcher(lines.get(i).split(";", 2)[0]);
                    while (m.find()) reads.add(new int[]{Integer.parseInt(m.group(1)), 4});
                }
            }
            if (safe) for (var entry : stores.entrySet()) {
                int offset = entry.getValue()[0], size = entry.getValue()[1];
                if (offset < 0 || offset + size > bytes) continue;
                if (reads.stream().noneMatch(r -> offset < r[0] + r[1] && r[0] < offset + size)) drop.add(entry.getKey());
            }
            first = end - 1;
        }
        for (int i = out.size() - 1; i >= 0; i--) if (drop.contains(i)) removeInstruction(out, i);
        return out;
    }
}
