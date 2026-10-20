import java.util.*;
import java.util.regex.*;

/**
 * Experimental closed-loop backend. XWA is instruction-local scratch; XHL is
 * allocatable and borrowed (saved/restored) only for memory stores.
 */
final class LoopRegisterAllocator {
    private static final Pattern NAME = Pattern.compile("%[\\w.]+");
    private static final String[] PRIMARY = {"xde", "xbc", "xhl", "xix", "xiy", "xiz"};
    private static final String[] BANK = {"xde2", "xbc1", "xhl1", "xwa1", "xbc2", "xhl2", "xwa2", "xde1"};
    private static final List<String> SAVED = List.of(
        "xwa", "xbc", "xde", "xhl", "xix", "xiy", "xiz",
        "xbc1", "xde1", "xhl1", "xwa1", "xbc2", "xde2", "xhl2", "xwa2");

    record Incoming(String value, String predecessor) {}
    record Instruction(String name, String op, String type, List<String> operands,
                       List<Incoming> incoming, List<String> successors) {
        Set<String> uses() {
            Set<String> result = new LinkedHashSet<>();
            for (String operand : operands) if (operand.startsWith("%")) result.add(operand);
            return result;
        }
    }
    record Block(String label, List<Instruction> instructions) {}
    interface InputLoader { void load(String value, String type, int stackAdjustment); }

    private static Instruction parse(String raw) {
        String s = Ir2Tlcs900.FuncTranslator.stripMeta(raw.strip());
        String value = "(%[\\w.]+|-?\\d+|true|false|inttoptr \\(i32 -?\\d+ to ptr\\))";
        Matcher m = Pattern.compile("(%[\\w.]+) = phi (i1|i8|i16|i32|ptr) (.+)").matcher(s);
        if (m.matches()) {
            List<Incoming> incoming = new ArrayList<>();
            for (String pair : Ir2Tlcs900.FuncTranslator.extractPhiPairs(m.group(3))) {
                Matcher p = Pattern.compile("\\s*" + value + ",\\s*%([\\w.]+)\\s*").matcher(pair);
                if (!p.matches()) throw new IllegalArgumentException("unsupported phi: " + s);
                incoming.add(new Incoming(p.group(1), p.group(2)));
            }
            return new Instruction(m.group(1), "phi", m.group(2), List.of(), incoming, List.of());
        }
        m = Pattern.compile("(%[\\w.]+) = (add|sub|and|or|xor|shl|lshr|ashr) (i1|i8|i16|i32) "
            + value + ",\\s*" + value).matcher(s);
        if (m.matches()) return new Instruction(m.group(1), m.group(2), m.group(3),
            List.of(m.group(4), m.group(5)), List.of(), List.of());
        m = Pattern.compile("(%[\\w.]+) = (trunc|zext|sext) (i1|i8|i16|i32) " + value
            + " to (i1|i8|i16|i32)").matcher(s);
        if (m.matches()) return new Instruction(m.group(1), m.group(2), m.group(5),
            List.of(m.group(4), m.group(3)), List.of(), List.of());
        m = Pattern.compile("(%[\\w.]+) = getelementptr (i8|i16|i32), ptr " + value
            + ", i32 " + value).matcher(s);
        if (m.matches()) return new Instruction(m.group(1), "gep", "ptr",
            List.of(m.group(3), m.group(4), m.group(2)), List.of(), List.of());
        m = Pattern.compile("(%[\\w.]+) = load (i8|i16|i32), ptr " + value
            + "(?:, align \\d+)?").matcher(s);
        if (m.matches()) return new Instruction(m.group(1), "load", m.group(2),
            List.of(m.group(3)), List.of(), List.of());
        m = Pattern.compile("store (i8|i16|i32) " + value + ", ptr " + value
            + "(?:, align \\d+)?").matcher(s);
        if (m.matches()) return new Instruction(null, "store", m.group(1),
            List.of(m.group(2), m.group(3)), List.of(), List.of());
        m = Pattern.compile("(%[\\w.]+) = icmp (?:samesign )?(eq|ne|ult|uge|ugt|ule)"
            + " (i1|i8|i16|i32) " + value + ",\\s*" + value).matcher(s);
        if (m.matches()) return new Instruction(m.group(1), "cmp:" + m.group(2), "i1",
            List.of(m.group(4), m.group(5), m.group(3)), List.of(), List.of());
        m = Pattern.compile("br i1 " + value + ", label %([\\w.]+), label %([\\w.]+)").matcher(s);
        if (m.matches()) return new Instruction(null, "branch", null,
            List.of(m.group(1)), List.of(), List.of(m.group(2), m.group(3)));
        m = Pattern.compile("br label %([\\w.]+)").matcher(s);
        if (m.matches()) return new Instruction(null, "branch", null,
            List.of(), List.of(), List.of(m.group(1)));
        throw new IllegalArgumentException("unsupported loop instruction: " + s);
    }

    static Plan plan(List<Ir2Tlcs900.FuncTranslator.BasicBlock> source) {
        if (source.isEmpty()) throw new IllegalArgumentException("function has no blocks");
        Map<String, Ir2Tlcs900.FuncTranslator.BasicBlock> byLabel = new LinkedHashMap<>();
        Map<String, Set<String>> predecessors = new LinkedHashMap<>();
        for (var block : source) {
            byLabel.put(block.label(), block);
            predecessors.put(block.label(), new LinkedHashSet<>());
        }
        for (var block : source)
            for (String successor : Ir2Tlcs900.FuncTranslator.successorsOf(block)) {
                if (!predecessors.containsKey(successor))
                    throw new IllegalArgumentException("unknown block " + successor);
                predecessors.get(successor).add(block.label());
            }
        Map<String, Set<String>> dominators = new LinkedHashMap<>();
        String entry = source.get(0).label();
        for (String label : byLabel.keySet())
            dominators.put(label, new LinkedHashSet<>(label.equals(entry) ? Set.of(entry) : byLabel.keySet()));
        boolean changed;
        do {
            changed = false;
            for (String label : byLabel.keySet()) {
                if (label.equals(entry)) continue;
                Set<String> dom = new LinkedHashSet<>(byLabel.keySet());
                if (predecessors.get(label).isEmpty()) dom.clear();
                for (String pred : predecessors.get(label)) dom.retainAll(dominators.get(pred));
                dom.add(label);
                if (!dom.equals(dominators.get(label))) {
                    dominators.put(label, dom);
                    changed = true;
                }
            }
        } while (changed);
        List<Set<String>> candidates = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        for (var latch : source)
            for (String header : Ir2Tlcs900.FuncTranslator.successorsOf(latch)) {
                if (!dominators.get(latch.label()).contains(header)) continue;
                Set<String> loop = new LinkedHashSet<>();
                loop.add(header);
                Deque<String> pending = new ArrayDeque<>();
                if (!header.equals(latch.label())) pending.add(latch.label());
                while (!pending.isEmpty()) {
                    String label = pending.removeFirst();
                    if (loop.add(label)) pending.addAll(predecessors.get(label));
                }
                candidates.add(loop);
                headers.add(header);
            }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) order.add(i);
        order.sort(Comparator.comparingInt(i -> candidates.get(i).size()));
        List<String> rejected = new ArrayList<>();
        for (int index : order) {
            String header = headers.get(index);
            Set<String> loop = candidates.get(index);
            try {
                if (header.equals(entry)) throw new IllegalArgumentException("function-entry loop");
                Set<String> exits = new LinkedHashSet<>();
                for (String label : loop) {
                    if (!dominators.get(label).contains(header))
                        throw new IllegalArgumentException("non-dominated loop block");
                    if (!label.equals(header) && !loop.containsAll(predecessors.get(label)))
                        throw new IllegalArgumentException("extra loop entry");
                    for (String successor : Ir2Tlcs900.FuncTranslator.successorsOf(byLabel.get(label)))
                        if (!loop.contains(successor)) exits.add(successor);
                }
                if (exits.size() != 1) throw new IllegalArgumentException("requires one exit");
                String exit = exits.iterator().next();
                for (String instr : byLabel.get(exit).instrs())
                    if (instr.matches("%[^=]+=\\s*phi\\b.*"))
                        throw new IllegalArgumentException("exit phi needs an output bridge");
                List<Block> blocks = new ArrayList<>();
                Set<String> definitions = new LinkedHashSet<>();
                for (var block : source) {
                    if (!loop.contains(block.label())) continue;
                    List<Instruction> instructions = new ArrayList<>();
                    for (String instr : block.instrs()) {
                        Instruction parsed = parse(instr);
                        if (parsed.name != null) definitions.add(parsed.name);
                        instructions.add(parsed);
                    }
                    blocks.add(new Block(block.label(), instructions));
                }
                for (var block : source) {
                    if (loop.contains(block.label())) continue;
                    for (String instr : block.instrs()) {
                        String rhs = instr.contains("=") ? instr.substring(instr.indexOf('=') + 1) : instr;
                        Matcher use = NAME.matcher(rhs);
                        while (use.find())
                            if (definitions.contains(use.group()))
                                throw new IllegalArgumentException("loop result escapes: " + use.group());
                    }
                }
                return new Plan(blocks, header, exit, source);
            } catch (IllegalArgumentException unsupported) {
                rejected.add(header + ": " + unsupported.getMessage());
            }
        }
        throw new IllegalArgumentException("no supported closed loop (" + String.join("; ", rejected) + ")");
    }

    static final class Plan {
        final List<Block> blocks;
        final String header, exit;
        final Set<String> labels = new LinkedHashSet<>();
        final Map<String, String> types = new LinkedHashMap<>();
        final Set<String> inputs = new LinkedHashSet<>();
        final Map<String, Set<String>> interference = new LinkedHashMap<>();
        final Map<String, String> parent = new LinkedHashMap<>();
        final Map<String, String> homes = new LinkedHashMap<>();
        final Map<String, Integer> slots = new LinkedHashMap<>();
        final Map<String, Integer> writes = new LinkedHashMap<>();
        final Map<String, Integer> uses = new LinkedHashMap<>();
        final int scratch, copies, frameSize;
        int edgeNumber;

        Plan(List<Block> blocks, String header, String exit,
             List<Ir2Tlcs900.FuncTranslator.BasicBlock> source) {
            this.blocks = blocks;
            this.header = header;
            this.exit = exit;
            Set<String> local = new LinkedHashSet<>();
            for (Block block : blocks) {
                labels.add(block.label);
                for (Instruction instr : block.instructions)
                    if (instr.name != null) {
                        local.add(instr.name);
                        types.put(instr.name, instr.type);
                    }
            }
            Map<String, String> outsideTypes = new LinkedHashMap<>();
            for (var block : source) {
                if (labels.contains(block.label())) continue;
                for (String raw : block.instrs()) {
                    Matcher m = Pattern.compile("(%[\\w.]+) = .+").matcher(raw);
                    if (!m.matches()) continue;
                    try {
                        Instruction instr = parse(raw);
                        outsideTypes.put(instr.name, instr.type);
                    } catch (IllegalArgumentException unsupported) {
                        Matcher type = Pattern.compile("%[\\w.]+ = (?:load |phi |add |sub |sdiv |udiv |"
                            + "mul |and |or |xor |shl |lshr |ashr )(i1|i8|i16|i32|ptr)\\b.*").matcher(raw);
                        if (type.matches()) outsideTypes.put(m.group(1), type.group(1));
                        if (raw.matches("%[\\w.]+ = (?:inttoptr|getelementptr)\\b.*"))
                            outsideTypes.put(m.group(1), "ptr");
                    }
                }
            }
            for (Block block : blocks)
                for (Instruction instr : block.instructions) {
                    if (instr.op.equals("phi")) {
                        Set<String> incomingLabels = new LinkedHashSet<>();
                        for (Incoming incoming : instr.incoming) {
                            if (!incomingLabels.add(incoming.predecessor))
                                throw new IllegalArgumentException("duplicate phi predecessor");
                            if (!labels.contains(incoming.predecessor)) {
                                if (!block.label.equals(header))
                                    throw new IllegalArgumentException("non-header incoming phi");
                                inputs.add(instr.name);
                            } else if (incoming.value.startsWith("%") && !local.contains(incoming.value)) {
                                String type = outsideTypes.get(incoming.value);
                                if (!instr.type.equals(type))
                                    throw new IllegalArgumentException("unknown phi input type " + incoming.value);
                                types.put(incoming.value, type);
                                inputs.add(incoming.value);
                            }
                        }
                    }
                    for (String operand : instr.uses())
                        if (!local.contains(operand)) {
                            String type = outsideTypes.get(operand);
                            if (type == null) throw new IllegalArgumentException("unknown input type " + operand);
                            types.put(operand, type);
                            inputs.add(operand);
                        }
                    if (Set.of("shl", "lshr", "ashr").contains(instr.op)) {
                        OptionalLong amount = Ir2Tlcs900.parseImmediate(instr.operands.get(1));
                        if (amount.isEmpty() || amount.getAsLong() < 0
                                || amount.getAsLong() >= bits(instr.type))
                            throw new IllegalArgumentException("requires a constant in-range shift");
                    }
                }
            for (String value : types.keySet()) {
                interference.put(value, new LinkedHashSet<>());
                parent.put(value, value);
                writes.put(value, 0);
                uses.put(value, 0);
            }
            buildInterference();
            for (Block block : blocks)
                for (Instruction instr : block.instructions) {
                    if (instr.op.equals("phi"))
                        for (Incoming incoming : instr.incoming)
                            if (labels.contains(incoming.predecessor)) merge(instr.name, incoming.value);
                }
            for (Block block : blocks)
                for (Instruction instr : block.instructions) {
                    if (Set.of("zext", "trunc").contains(instr.op))
                        merge(instr.name, instr.operands.get(0));
                    if (instr.op.equals("gep")) {
                        merge(instr.name, instr.operands.get(0));
                        merge(instr.name, instr.operands.get(1));
                    }
                    if (Set.of("add", "sub", "and", "or", "xor", "shl", "lshr", "ashr").contains(instr.op)) {
                        merge(instr.name, instr.operands.get(0));
                        if (Set.of("add", "and", "or", "xor").contains(instr.op))
                            merge(instr.name, instr.operands.get(1));
                    }
                }
            allocate();
            int offset = 0;
            for (String value : types.keySet())
                if (inputs.contains(value) || homes.get(value) == null) {
                    slots.put(value, offset);
                    offset += 4;
                }
            scratch = offset;
            copies = offset + 4;
            int maxCopies = blocks.stream().mapToInt(b -> (int) b.instructions.stream()
                .filter(i -> i.op.equals("phi")).count()).max().orElse(0);
            frameSize = copies + maxCopies * 4;
        }

        private void connect(String a, String b) {
            if (!a.equals(b)) {
                interference.get(a).add(b);
                interference.get(b).add(a);
            }
        }
        private void clique(Set<String> values) {
            for (String a : values) for (String b : values) connect(a, b);
        }
        private void buildInterference() {
            Map<String, Set<String>> in = new LinkedHashMap<>(), out = new LinkedHashMap<>();
            Map<String, Block> byLabel = new LinkedHashMap<>();
            for (Block block : blocks) {
                in.put(block.label, new LinkedHashSet<>());
                out.put(block.label, new LinkedHashSet<>());
                byLabel.put(block.label, block);
            }
            boolean changed;
            do {
                changed = false;
                for (int bi = blocks.size() - 1; bi >= 0; bi--) {
                    Block block = blocks.get(bi);
                    Set<String> liveOut = new LinkedHashSet<>();
                    Instruction terminator = block.instructions.get(block.instructions.size() - 1);
                    for (String successor : terminator.successors) {
                        if (!labels.contains(successor)) continue;
                        Set<String> edge = new LinkedHashSet<>(in.get(successor));
                        for (Instruction phi : byLabel.get(successor).instructions) {
                            if (!phi.op.equals("phi")) continue;
                            edge.remove(phi.name);
                            for (Incoming incoming : phi.incoming)
                                if (incoming.predecessor.equals(block.label) && incoming.value.startsWith("%"))
                                    edge.add(incoming.value);
                        }
                        liveOut.addAll(edge);
                    }
                    Set<String> live = new LinkedHashSet<>(liveOut);
                    for (int ii = block.instructions.size() - 1; ii >= 0; ii--) {
                        Instruction instr = block.instructions.get(ii);
                        if (instr.name != null) live.remove(instr.name);
                        live.addAll(instr.uses());
                    }
                    if (!liveOut.equals(out.get(block.label)) || !live.equals(in.get(block.label))) {
                        out.put(block.label, liveOut);
                        in.put(block.label, live);
                        changed = true;
                    }
                }
            } while (changed);
            for (Block block : blocks) {
                Set<String> live = new LinkedHashSet<>(out.get(block.label));
                clique(live);
                for (int ii = block.instructions.size() - 1; ii >= 0; ii--) {
                    Instruction instr = block.instructions.get(ii);
                    if (instr.name != null) {
                        for (String other : live) connect(instr.name, other);
                        live.remove(instr.name);
                        if (!Set.of("phi", "trunc", "zext").contains(instr.op))
                            writes.merge(instr.name, 1, Integer::sum);
                    }
                    live.addAll(instr.uses());
                    for (String use : instr.uses()) uses.merge(use, 1, Integer::sum);
                    for (Incoming incoming : instr.incoming)
                        if (labels.contains(incoming.predecessor) && incoming.value.startsWith("%"))
                            uses.merge(incoming.value, 1, Integer::sum);
                    clique(live);
                }
            }
            clique(inputs);
        }
        private String root(String value) {
            String next = parent.get(value);
            if (next == null) return value;
            if (!next.equals(value)) parent.put(value, root(next));
            return parent.get(value);
        }
        private void merge(String a, String b) {
            if (!types.containsKey(b)) return;
            String ra = root(a), rb = root(b);
            if (ra.equals(rb)) return;
            for (String value : types.keySet())
                if (root(value).equals(ra))
                    for (String neighbor : interference.get(value))
                        if (root(neighbor).equals(rb)) return;
            parent.put(rb, ra);
        }
        private void allocate() {
            Map<String, List<String>> groups = new LinkedHashMap<>();
            for (String value : types.keySet())
                groups.computeIfAbsent(root(value), key -> new ArrayList<>()).add(value);
            List<String> order = new ArrayList<>(groups.keySet());
            order.sort(Comparator.comparingInt((String group) -> groups.get(group).stream()
                .mapToInt(v -> uses.get(v) + 4 * writes.get(v)).sum()).reversed());
            Map<String, String> colors = new LinkedHashMap<>();
            for (String group : order) {
                Set<String> forbidden = new LinkedHashSet<>();
                for (String value : groups.get(group))
                    for (String neighbor : interference.get(value)) {
                        String color = colors.get(root(neighbor));
                        if (color != null) forbidden.add(color);
                    }
                boolean written = groups.get(group).stream().anyMatch(v -> writes.get(v) != 0);
                List<String> choices = new ArrayList<>();
                choices.addAll(Arrays.asList(written ? PRIMARY : BANK));
                choices.addAll(Arrays.asList(written ? BANK : PRIMARY));
                for (String choice : choices)
                    if (!forbidden.contains(choice)) {
                        colors.put(group, choice);
                        break;
                    }
                for (String value : groups.get(group)) homes.put(value, colors.get(group));
            }
        }

        void emit(List<String> output, String prefix, InputLoader loader) {
            int start = output.size();
            for (String reg : SAVED) output.add("\tpush " + reg);
            output.add("\tadd xsp, -" + frameSize + "\t; experimental loop frame");
            for (String input : inputs) {
                loader.load(input, types.get(input), frameSize + 4 * SAVED.size());
                canonical(output, "xwa", types.get(input));
                output.add("\tld " + slot(input) + ", xwa");
            }
            for (String input : inputs)
                if (homes.get(input) != null) {
                    output.add("\tld xwa, " + slot(input));
                    output.add("\tld " + homes.get(input) + ", xwa");
                }
            output.add("\tjrl " + prefix + header);
            for (Block block : blocks) {
                output.add(prefix + block.label + ":");
                for (int ii = 0; ii < block.instructions.size(); ii++) {
                    Instruction instr = block.instructions.get(ii);
                    if (instr.op.equals("phi")) continue;
                    if (instr.op.equals("and") && ii + 2 < block.instructions.size()) {
                        Instruction cmp = block.instructions.get(ii + 1);
                        Instruction br = block.instructions.get(ii + 2);
                        OptionalLong mask = Ir2Tlcs900.parseImmediate(instr.operands.get(1));
                        if (mask.isPresent() && mask.getAsLong() > 0 && mask.getAsLong() <= 0x8000
                                && mask.getAsLong() < (1L << bits(instr.type))
                                && Long.bitCount(mask.getAsLong()) == 1 && uses.get(instr.name) == 1
                                && Set.of("cmp:eq", "cmp:ne").contains(cmp.op)
                                && cmp.operands.get(0).equals(instr.name) && cmp.operands.get(1).equals("0")
                                && uses.get(cmp.name) == 1 && br.op.equals("branch")
                                && br.operands.equals(List.of(cmp.name))) {
                            String source = homes.get(instr.operands.get(0));
                            if (Arrays.asList(PRIMARY).contains(source)) {
                                source = source.substring(1);
                            } else {
                                load(output, "xwa", instr.operands.get(0));
                                source = "wa";
                            }
                            output.add("\tbit " + Long.numberOfTrailingZeros(mask.getAsLong()) + ", " + source);
                            branch(output, prefix, block.label, br, cmp.op.equals("cmp:eq") ? "Z" : "NZ");
                            ii += 2;
                            continue;
                        }
                    }
                    if (instr.op.equals("trunc") && uses.get(instr.name) == 1
                            && ii + 1 < block.instructions.size()) {
                        Instruction next = block.instructions.get(ii + 1);
                        if (next.op.equals("store") && next.operands.get(0).equals(instr.name)) {
                            instruction(output, prefix, block.label,
                                new Instruction(null, "store", next.type,
                                    List.of(instr.operands.get(0), next.operands.get(1)), List.of(), List.of()));
                            ii++;
                            continue;
                        }
                    }
                    if (instr.op.startsWith("cmp:") && ii + 1 < block.instructions.size()) {
                        Instruction next = block.instructions.get(ii + 1);
                        if (next.op.equals("branch") && next.operands.equals(List.of(instr.name))
                                && uses.get(instr.name) == 1) {
                            compare(output, instr);
                            branch(output, prefix, block.label, next, condition(instr.op.substring(4)));
                            ii++;
                            continue;
                        }
                    }
                    instruction(output, prefix, block.label, instr);
                }
            }
            output.add(prefix + "exit:");
            output.add("\tadd xsp, " + frameSize);
            for (int i = SAVED.size() - 1; i >= 0; i--) output.add("\tpop " + SAVED.get(i));
            for (int i = output.size() - 2; i >= start; i--)
                if (output.get(i).startsWith("\tjrl ")
                        && output.get(i + 1).equals(output.get(i).substring(5) + ":"))
                    output.remove(i);
        }
        private String slot(String value) { return "(xsp+" + slots.get(value) + ")"; }
        private String operand(String value) {
            if (types.containsKey(value)) return homes.get(value) == null ? slot(value) : homes.get(value);
            if (value.equals("true")) return "1";
            if (value.equals("false")) return "0";
            Matcher pointer = Pattern.compile("inttoptr \\(i32 (-?\\d+) to ptr\\)").matcher(value);
            if (pointer.matches()) value = pointer.group(1);
            return Ir2Tlcs900.fmtImm(Long.parseLong(value) & 0xFFFFFFFFL);
        }
        private void load(List<String> out, String reg, String value) {
            String source = operand(value);
            if (!reg.equals(source)) out.add("\tld " + reg + ", " + source);
        }
        private void store(List<String> out, String name, String reg) {
            String dest = homes.get(name);
            if (dest == null) dest = slot(name);
            if (!dest.equals(reg)) out.add("\tld " + dest + ", " + reg);
        }
        private String target(String name) {
            String home = homes.get(name);
            return Arrays.asList(PRIMARY).contains(home) ? home : "xwa";
        }
        private static void canonical(List<String> out, String reg, String type) {
            int bits = bits(type);
            if (bits < 32) out.add("\tand " + reg + ", " + Ir2Tlcs900.fmtImm((1L << bits) - 1));
        }
        private void compare(List<String> out, Instruction instr) {
            String lhs = homes.get(instr.operands.get(0));
            if (!Arrays.asList(PRIMARY).contains(lhs)) {
                load(out, "xwa", instr.operands.get(0));
                lhs = "xwa";
            }
            String type = instr.operands.get(2);
            if (!types.containsKey(instr.operands.get(0))) canonical(out, lhs, type);
            String rhs = instr.operands.get(1);
            OptionalLong immediate = Ir2Tlcs900.parseImmediate(rhs);
            String source = operand(rhs);
            if (immediate.isPresent())
                source = Ir2Tlcs900.fmtImm(immediate.getAsLong() & ((1L << bits(type)) - 1));
            out.add("\tcp " + lhs + ", " + source);
        }
        private static String condition(String pred) {
            return switch (pred) {
                case "eq" -> "Z"; case "ne" -> "NZ"; case "ult" -> "C";
                case "uge" -> "NC"; case "ugt" -> "UGT"; case "ule" -> "ULE";
                default -> throw new IllegalArgumentException("unsupported predicate " + pred);
            };
        }
        private void binary(List<String> out, String name, String type, String op, String lhs, String rhs) {
            String dest = target(name);
            if (dest.equals(operand(rhs)) && !dest.equals(operand(lhs))) {
                if (op.equals("sub")) {
                    dest = "xwa";
                } else {
                    String swap = lhs;
                    lhs = rhs;
                    rhs = swap;
                }
            }
            load(out, dest, lhs);
            out.add("\t" + op + " " + dest + ", " + operand(rhs));
            canonical(out, dest, type);
            store(out, name, dest);
        }
        private void instruction(List<String> out, String prefix, String block, Instruction i) {
            List<String> a = i.operands;
            switch (i.op) {
                case "add", "sub", "and", "or", "xor" ->
                    binary(out, i.name, i.type, i.op, a.get(0), a.get(1));
                case "shl", "lshr", "ashr" -> {
                    String dest = target(i.name);
                    if (i.op.equals("ashr") && bits(i.type) < 32) dest = "xwa";
                    load(out, dest, a.get(0));
                    if (i.op.equals("ashr") && bits(i.type) < 32) signExtend(out, i.type);
                    int amount = Integer.parseInt(a.get(1));
                    String op = switch (i.op) { case "shl" -> "sla"; case "lshr" -> "srl"; default -> "sra"; };
                    while (amount > 0) {
                        int part = Math.min(16, amount);
                        out.add("\t" + op + " " + part + ", " + dest);
                        amount -= part;
                    }
                    canonical(out, dest, i.type);
                    store(out, i.name, dest);
                }
                case "trunc", "zext", "sext" -> {
                    if (i.op.equals("zext") && homes.get(i.name) != null
                            && homes.get(i.name).equals(homes.get(a.get(0)))) break;
                    String dest = i.op.equals("sext") ? "xwa" : target(i.name);
                    load(out, dest, a.get(0));
                    if (!types.containsKey(a.get(0))) canonical(out, dest, a.get(1));
                    if (i.op.equals("sext")) signExtend(out, a.get(1));
                    canonical(out, dest, i.type);
                    store(out, i.name, dest);
                }
                case "gep" -> {
                    int size = bits(a.get(2)) / 8;
                    if (size == 1) {
                        binary(out, i.name, "ptr", "add", a.get(0), a.get(1));
                        break;
                    }
                    String dest = target(i.name);
                    OptionalLong index = Ir2Tlcs900.parseImmediate(a.get(1));
                    if (index.isPresent()) {
                        load(out, dest, a.get(0));
                        out.add("\tadd " + dest + ", " + Ir2Tlcs900.fmtImm(index.getAsLong() * size & 0xFFFFFFFFL));
                    } else {
                        if (dest.equals(operand(a.get(0))) && !dest.equals(operand(a.get(1)))) {
                            load(out, "xwa", a.get(1));
                            if (size != 1) out.add("\tsla " + (size == 2 ? 1 : 2) + ", xwa");
                            out.add("\tadd " + dest + ", xwa");
                        } else {
                            load(out, dest, a.get(1));
                            if (size != 1) out.add("\tsla " + (size == 2 ? 1 : 2) + ", " + dest);
                            out.add("\tadd " + dest + ", " + operand(a.get(0)));
                        }
                    }
                    store(out, i.name, dest);
                }
                case "load" -> {
                    String address = homes.get(a.get(0));
                    if (bits(i.type) == 8 && Arrays.asList(PRIMARY).contains(address)) {
                        out.add("\tld xwa, 0");
                        out.add("\tld a, (" + address + ")");
                        store(out, i.name, "xwa");
                        break;
                    }
                    load(out, "xwa", a.get(0));
                    String dest = bits(i.type) == 8 ? "a" : bits(i.type) == 16 ? "wa" : "xwa";
                    out.add("\tld " + dest + ", (xwa)");
                    if (bits(i.type) == 8) out.add("\textz wa");
                    if (bits(i.type) < 32) out.add("\textz xwa");
                    store(out, i.name, "xwa");
                }
                case "store" -> {
                    String value = homes.get(a.get(0));
                    String address = homes.get(a.get(1));
                    if (Arrays.asList(PRIMARY).contains(value)
                            && (bits(i.type) != 8 || Set.of("xbc", "xde", "xhl").contains(value))) {
                        if (!Arrays.asList(PRIMARY).contains(address)) {
                            load(out, "xwa", a.get(1));
                            address = "xwa";
                        }
                        String source = bits(i.type) == 32 ? value : bits(i.type) == 16
                            ? value.substring(1) : value.equals("xbc") ? "c" : value.equals("xde") ? "e" : "l";
                        out.add("\tld (" + address + "), " + source);
                        break;
                    }
                    load(out, "xwa", a.get(1));
                    out.add("\tld (xsp+" + scratch + "), xhl");
                    load(out, "xhl", a.get(0));
                    String source = bits(i.type) == 8 ? "l" : bits(i.type) == 16 ? "hl" : "xhl";
                    out.add("\tld (xwa), " + source);
                    out.add("\tld xhl, (xsp+" + scratch + ")");
                }
                case "branch" -> branch(out, prefix, block, i, null);
                default -> {
                    if (!i.op.startsWith("cmp:")) throw new IllegalStateException("unlowered " + i.op);
                    compare(out, i);
                    out.add("\tscc " + condition(i.op.substring(4)) + ", a");
                    out.add("\textz wa");
                    out.add("\textz xwa");
                    store(out, i.name, "xwa");
                }
            }
        }
        private static void signExtend(List<String> out, String type) {
            if (type.equals("i1")) {
                out.add("\txor xwa, 0ffffffffh");
                out.add("\tadd xwa, 1");
            } else {
                if (type.equals("i8")) out.add("\texts wa");
                if (!type.equals("i32")) out.add("\texts xwa");
            }
        }
        private void branch(List<String> out, String prefix, String block, Instruction i, String cc) {
            if (i.successors.size() == 1) {
                edge(out, prefix, block, i.successors.get(0));
            } else {
                if (cc == null) {
                    load(out, "xwa", i.operands.get(0));
                    out.add("\tor xwa, xwa");
                    cc = "NZ";
                }
                if (copiesFor(block, i.successors.get(0)).isEmpty()) {
                    out.add("\tjrl " + cc + ", " + prefix
                        + (labels.contains(i.successors.get(0)) ? i.successors.get(0) : "exit"));
                    edge(out, prefix, block, i.successors.get(1));
                    return;
                }
                String edge = prefix + "edge_" + edgeNumber++;
                out.add("\tjrl " + cc + ", " + edge);
                edge(out, prefix, block, i.successors.get(1));
                out.add(edge + ":");
                edge(out, prefix, block, i.successors.get(0));
            }
        }
        private void edge(List<String> out, String prefix, String from, String to) {
            List<Instruction> phis = copiesFor(from, to);
            List<Instruction> remaining = new ArrayList<>(phis);
            while (!remaining.isEmpty()) {
                Instruction ready = null;
                for (Instruction phi : remaining) {
                    String dest = location(phi.name);
                    boolean needed = false;
                    for (Instruction other : remaining)
                        if (other != phi && dest.equals(location(incoming(other, from)))) needed = true;
                    if (!needed) { ready = phi; break; }
                }
                if (ready == null) break;
                String value = incoming(ready, from);
                String dest = target(ready.name);
                load(out, dest, value);
                if (!types.containsKey(value)) canonical(out, dest, ready.type);
                store(out, ready.name, dest);
                remaining.remove(ready);
            }
            List<Instruction> pending = new ArrayList<>();
            for (Instruction phi : remaining) {
                String value = incoming(phi, from);
                load(out, "xwa", value);
                if (!types.containsKey(value)) canonical(out, "xwa", phi.type);
                out.add("\tld (xsp+" + (copies + pending.size() * 4) + "), xwa");
                pending.add(phi);
            }
            for (int index = 0; index < pending.size(); index++) {
                out.add("\tld xwa, (xsp+" + (copies + index * 4) + ")");
                store(out, pending.get(index).name, "xwa");
            }
            out.add("\tjrl " + prefix + (labels.contains(to) ? to : "exit"));
        }
        private String incoming(Instruction phi, String from) {
            return phi.incoming.stream().filter(p -> p.predecessor.equals(from))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("missing phi edge from " + from)).value;
        }
        private String location(String value) {
            if (!types.containsKey(value)) return value;
            return homes.get(value) == null ? slot(value) : homes.get(value);
        }
        private List<Instruction> copiesFor(String from, String to) {
            return blocks.stream().filter(b -> b.label.equals(to))
                .flatMap(b -> b.instructions.stream()).filter(i -> i.op.equals("phi"))
                .filter(i -> !location(i.name).equals(location(incoming(i, from)))).toList();
        }
    }
    private static int bits(String type) { return type.equals("ptr") ? 32 : Integer.parseInt(type.substring(1)); }
}
