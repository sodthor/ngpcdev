import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.Arrays;
import java.util.stream.*;

/**
 * Ir2Tlcs900 — LLVM IR → TLCS-900/H assembly translator for the Neo Geo Pocket Color.
 *
 * Output targets the "AS" cross-assembler (asl.exe) with "cpu 93C141" / maxmode syntax,
 * matching the style used in the columns/ sample project.
 *
 * Calling convention: CC900 __cdecl
 *   - Args pushed right-to-left, minimum 2 bytes each (1-byte args padded to 2)
 *   - Far return address = 4 bytes at (XSP+0) after CALL
 *   - Return u8  → L   (low byte of HL)
 *   - Return u16 → HL
 *   - Return u32 → XHL
 *   - Caller cleans stack via "ld xsp,xsp+N"
 *
 * Usage:
 *   javac Ir2Tlcs900.java
 *   java  Ir2Tlcs900 input.ll [output.asm]
 *   java  Ir2Tlcs900 main.ll library.ll -o output.asm
 */

public class Ir2Tlcs900 {

    // =========================================================================
    // NGPC constants

    // =========================================================================

    static final Map<Integer, String> MMIO = new LinkedHashMap<>();
    static {
        MMIO.put(0x006F, "rWDCR");
        MMIO.put(0x6F80, "rBV");
        MMIO.put(0x6F82, "rSL");
        MMIO.put(0x6F83, "rUSERB");
        MMIO.put(0x6F84, "rUSERB");
        MMIO.put(0x6F85, "rUSERS");
        MMIO.put(0x6F86, "rUSERA");
        MMIO.put(0x6F87, "rLANG");
        MMIO.put(0x6F91, "rOSV");
        MMIO.put(0x6FB8, "rSWI3");
        MMIO.put(0x6FCC, "rVBI");
        MMIO.put(0x8000, "rICR");
        MMIO.put(0x8002, "rWBAX");
        MMIO.put(0x8003, "rWBAY");
        MMIO.put(0x8004, "rWSIX");
        MMIO.put(0x8005, "rWSIY");
        MMIO.put(0x8009, "rRASV");
        MMIO.put(0x8010, "r2DSR");
        MMIO.put(0x8020, "rPOX");
        MMIO.put(0x8021, "rPOY");
        MMIO.put(0x8030, "rPF");
        MMIO.put(0x8032, "rSCR1X");
        MMIO.put(0x8033, "rSCR1Y");
        MMIO.put(0x8034, "rSCR2X");
        MMIO.put(0x8035, "rSCR2Y");
        MMIO.put(0x8118, "rBGCPAL");
        MMIO.put(0x8200, "rSPRPAL");
        MMIO.put(0x8280, "rSCR1PAL");
        MMIO.put(0x8300, "rSCR2PAL");
        MMIO.put(0x83E0, "rBGPAL");
        MMIO.put(0x8800, "rSPRAM");
        MMIO.put(0x8C00, "rSPRCOL");
        MMIO.put(0x9000, "rSCR1MAP");
        MMIO.put(0x9800, "rSCR2MAP");
        MMIO.put(0xA000, "rTILERAM");
        MMIO.put(0x7000, "rZ80RAM");
    }

    // =========================================================================
    // Data model

    // =========================================================================

    record GlobalVar(String name, String kind, String init, int align) {}

    record IrFunction(String name, String retType, String params, List<String> body) {}

    record TranslationOutput(String assembly, String staticData) {}

    /** A sequence of lines that loads a value from a stack slot into a register,
     *  optionally preceded by "ld wa, 0" and followed by extend instructions. */

    record LoadUnit(int first, int last, String reg, String off) {}

    // =========================================================================
    // Entry point

    // =========================================================================

    public static void main(String[] args) throws IOException {
        if (args.length == 3 && "--shorten-jumps-from-warnings".equals(args[0])) {
            System.out.println(shortenJumpsFromWarnings(Path.of(args[1]), Path.of(args[2])));
            return;
        }

        if (args.length == 0) {
            System.err.println("Usage: java Ir2Tlcs900 <file1.ll> [file2.ll ...] [-o output.asm] [--data-output data.asm]");
            System.err.println("       java Ir2Tlcs900 --shorten-jumps-from-warnings output.asm assembler.log");
            System.exit(1);
        }

        List<String> inputs = new ArrayList<>();
        String output = null;
        String dataOutput = null;
        for (int i = 0; i < args.length; i++) {
            if ("-o".equals(args[i]) && i + 1 < args.length) {
                output = args[++i];
            } else if ("--data-output".equals(args[i]) && i + 1 < args.length) {
                dataOutput = args[++i];
            } else {
                inputs.add(args[i]);
            }
        }

        if (inputs.isEmpty()) {
            System.err.println("Error: no input files");
            System.exit(1);
        }

        // Auto-include every *.ll in the same directory as the first input
        // that was not already listed explicitly.
        Path baseDir = Path.of(inputs.get(0)).toAbsolutePath().getParent();
        Set<Path> explicit = inputs.stream()
            .map(s -> Path.of(s).toAbsolutePath())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        try (var ds = Files.newDirectoryStream(baseDir, "*.ll")) {
            List<Path> extras = new ArrayList<>();
            for (Path p : ds) extras.add(p);
            extras.sort(Comparator.naturalOrder());
            for (Path p : extras) {
                if (!explicit.contains(p.toAbsolutePath())) {
                    inputs.add(p.toString());
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String f : inputs) {
            sb.append(Files.readString(Path.of(f)));
            sb.append("\n");
        }

        Translator translator = new Translator(sb.toString(), inputs.get(0).replaceAll("\\.ll$", ""));
        TranslationOutput translated = dataOutput == null
            ? new TranslationOutput(translator.translate(), null)
            : translator.translate(dataOutput.replace('\\', '/'));
        if (output != null) {
            Files.writeString(Path.of(output), translated.assembly());
        } else {
            System.out.print(translated.assembly());
        }

        if (dataOutput != null) {
            Files.writeString(Path.of(dataOutput), translated.staticData());
        }
    }

    // =========================================================================
    // Utilities

    // =========================================================================
    static int shortenJumpsFromWarnings(Path assemblyPath, Path warningPath) throws IOException {
        List<String> warnings = Files.readAllLines(warningPath);
        Set<Integer> passTwoLines = new LinkedHashSet<>();
        Pattern warning = Pattern.compile("^.*\\((\\d+)\\): warning: short jump possible\\s*$");
        boolean passTwo = false;
        for (String line : warnings) {
            if (line.strip().equals("PASS 2")) {
                passTwo = true;
                continue;
            }

            if (!passTwo) continue;
            Matcher matcher = warning.matcher(line);
            if (matcher.matches()) passTwoLines.add(Integer.parseInt(matcher.group(1)));
        }

        List<String> assembly = Files.readAllLines(assemblyPath);
        int changed = 0;
        for (int lineNumber : passTwoLines) {
            int index = lineNumber - 1;
            if (index < 0 || index >= assembly.size()) continue;
            String line = assembly.get(index);
            String shortened = line.replaceFirst("^(\\s*)jrl(\\s+)", "$1jr$2");
            if (!shortened.equals(line)) {
                assembly.set(index, shortened);
                changed++;
            }
        }

        if (changed != 0) {
            Files.writeString(assemblyPath, String.join("\n", assembly) + "\n");
            System.err.println("[short-jumps] shortened " + changed + " jump(s)");
        }

        return changed;
    }

    static int irTypeBits(String t) {
        t = t.trim();
        Matcher m = Pattern.compile("i(\\d+)").matcher(t);
        if (m.matches()) return Integer.parseInt(m.group(1));
        if (t.equals("ptr"))    return 32;
        if (t.equals("float"))  return 32;
        if (t.equals("double")) return 64;
        return 32;
    }

    static int irTypeBytes(String t) {
        return irTypeBytes(t, null);
    }

    /**
     * Result width in bits of the SSA value defined by an IR instruction, or -1 when it
     * cannot be determined confidently.
     *
     * Only the unambiguous forms are recognised; everything else returns -1 so the
     * caller keeps its existing default. Used to seed a promoted register's width, which
     * otherwise starts at 32 and is only corrected once the value's defining store is
     * emitted â€” so blocks emitted earlier read XBC instead of B for a byte value.
     */
    static int irDefBits(String instr) {
        String s = instr.strip();
        int eq = s.indexOf('=');
        if (eq < 0) return -1;
        String rhs = s.substring(eq + 1).strip();
        if (rhs.startsWith("alloca")) return -1;   // width comes from the alloca type
        Matcher m;
        if ((m = Pattern.compile("^phi\\s+(i\\d+|ptr)\\s").matcher(rhs)).find())
            return irTypeBits(m.group(1));
        if ((m = Pattern.compile("^load\\s+(i\\d+|ptr),").matcher(rhs)).find())
            return irTypeBits(m.group(1));
        if ((m = Pattern.compile("^select\\s+i1\\s+[^,]+,\\s*(i\\d+|ptr)\\s").matcher(rhs)).find())
            return irTypeBits(m.group(1));
        if ((m = Pattern.compile("^(?:zext|sext|trunc)\\b.*\\bto\\s+(i\\d+)\\s*$").matcher(rhs)).find())
            return irTypeBits(m.group(1));
        if ((m = Pattern.compile("^(?:add|sub|mul|and|or|xor|shl|lshr|ashr|udiv|sdiv|urem|srem)"
                               + "(?:\\s+(?:nuw|nsw|exact|disjoint))*\\s+(i\\d+)\\s").matcher(rhs)).find())
            return irTypeBits(m.group(1));
        return -1;
    }

    static int irTypeBytes(String t, Map<String, StructLayout> sl) {
        t = t.trim();
        // Array type `[N x ELEM]`. ELEM may itself be a bracketed array (e.g.
        // `[15 x [15 x i8]]`), so capture it greedily up to the final `]` rather
        // than with `\S+` (which stops at the first space inside a nested type and
        // made this fall through to the 4-byte scalar default â€” mis-scaling
        // multi-dimensional array GEP strides, e.g. maps[lvl] stepped by 4 not 225).
        Matcher m = Pattern.compile("\\[(\\d+)\\s+x\\s+(.+)\\]", Pattern.DOTALL).matcher(t);
        if (m.matches()) return Integer.parseInt(m.group(1)) * irTypeBytes(m.group(2), sl);
        // Struct type: look up actual size from layout if available
        Matcher ms = Pattern.compile("%struct\\.(\\w+)").matcher(t);
        if (ms.matches() && sl != null) {
            StructLayout layout = sl.get(ms.group(1));
            if (layout != null) return layout.size();
        }

        return (irTypeBits(t) + 7) / 8;
    }

    static String directiveFor(String t) {
        int b = irTypeBytes(t.split("\\s+")[0]);
        return switch (b) {
            case 1 -> "db";
            case 2 -> "dw";
            default -> "dd";
        };
    }

    static String stripAlign(String s) {
        return s.replaceAll(",?\\s*align\\s+\\d+\\s*$", "").trim();
    }

    static String stripAttrs(String s) {
        s = s.replaceAll("\\bdereferenceable(?:_or_null)?\\s*\\(\\s*\\d+\\s*\\)", "");
        s = s.replaceAll("\\balign\\s+\\d+\\b", "");
        // clang-22 replaced the bare `nocapture` attribute with `captures(none)` /
        // `captures(...)`; strip the whole parenthesised form.
        s = s.replaceAll("\\bcaptures\\s*\\([^)]*\\)", "");
        s = s.replaceAll("\\b(noundef|zeroext|signext|noalias|readonly|writeonly|nonnull|nocapture|readnone|immarg|inreg|returned)\\b", "");
        return s.replaceAll("\\s{2,}", " ").trim();
    }

    static OptionalLong parseImmediate(String v) {
        v = v.trim().replaceAll(",$", "");
        if (v.equals("true"))  return OptionalLong.of(1);
        if (v.equals("false") || v.equals("null")) return OptionalLong.of(0);
        try {
            if (v.startsWith("0x") || v.startsWith("0X"))
                return OptionalLong.of(Long.parseLong(v.substring(2), 16));
            return OptionalLong.of(Long.parseLong(v));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    static boolean isZeroInit(String init) {
        String s = init.trim();
        if (s.equals("null") || s.equals("zeroinitializer") || s.equals("0") || s.equals("ptr null")) return true;
        if (Pattern.compile("i\\d+\\s+0$").matcher(s).matches()) return true;
        Matcher m = Pattern.compile("\\[(\\d+)\\s+x\\s+(\\S+)\\]\\s+(zeroinitializer|\\[(.+)\\])", Pattern.DOTALL).matcher(s);
        if (m.matches()) {
            if ("zeroinitializer".equals(m.group(3))) return true;
            List<String> vals = parseInitList(m.group(4), m.group(2));
            return vals.stream().allMatch(x -> x.trim().equals("0"));
        }

        return false;
    }

    static List<String> parseInitList(String s, String elemType) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '[' || ch == '{') { depth++; cur.append(ch); }
            else if (ch == ']' || ch == '}') { depth--; cur.append(ch); }
            else if (ch == ',' && depth == 0) { result.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(ch);
        }

        if (!cur.toString().isBlank()) result.add(cur.toString().trim());
        List<String> values = new ArrayList<>();
        for (String item : result) {
            Matcher m = Pattern.compile("^\\S+\\s+(.*)").matcher(item.trim());
            values.add(m.matches() ? m.group(1).trim() : item.trim());
        }

        return values;
    }

    /** Decode LLVM IR string-literal escapes (\XX two-digit hex, and \\ ) into
     *  raw characters. LLVM emits every non-printable/special byte in an inline-asm
     *  string as \XX -- most importantly \09 (tab) and \0A (newline) that appear when
     *  the C source uses tabs to lay out asm. asl treats the raw tab as whitespace, but
     *  chokes on a literal backslash sequence, so this must run before the asm passes
     *  through verbatim. A lone backslash not followed by two hex digits (or another
     *  backslash) is left as-is. */
    static String unescapeLlvmString(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == '\\') { out.append('\\'); i += 1; continue; }
                if (i + 2 < s.length()
                        && isHexDigit(n) && isHexDigit(s.charAt(i + 2))) {
                    out.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                }
            }

            out.append(c);
        }

        return out.toString();
    }

    static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    static String irValLiteral(String v, String irType) {
        v = v.trim();
        if (v.startsWith("@")) return "_" + v.substring(1);
        Matcher m = Pattern.compile("inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\)").matcher(v);
        if (m.matches()) return String.valueOf(Long.parseLong(m.group(1)));
        if (v.equals("null") || v.equals("zeroinitializer")) return "0";
        OptionalLong n = parseImmediate(v);
        if (n.isPresent()) {
            long val = n.getAsLong();
            if (val < 0) {
                int bits = irTypeBits(irType);
                if (bits < 64) val = val & ((1L << bits) - 1);
                else val = val;
            }

            return String.valueOf(val);
        }

        return v;
    }

    // Format an address for AS assembler: use hex if > 9
    static String fmtAddr(int addr) {
        if (addr > 9) return String.format("0%xh", addr);
        return String.valueOf(addr);
    }

    static String fmtImm(long v) {
        if (v < 0 || v > 9) return String.format("0%xh", v & 0xFFFFFFFFL);
        return String.valueOf(v);
    }

    // =========================================================================
    // IR Parser

    // =========================================================================

    static class ParseResult {
        List<GlobalVar>   globals   = new ArrayList<>();
        List<IrFunction>  functions = new ArrayList<>();
        List<String>      declares  = new ArrayList<>();
        // struct name -> computed layout (field byte offsets + total size)
        Map<String, StructLayout> structLayouts = new LinkedHashMap<>();
    }

    /** Field byte offsets and total size for a struct, under target alignment rules. */

    record StructLayout(int[] fieldOffsets, int size) {}

    /**
     * Compute a struct's field offsets from its IR field-type list.
     *
     * Target rule (TLCS-900/H): each field is naturally aligned to its own size
     * (i8→1, i16→2, i32/ptr→4), and the struct's total size is rounded up to its
     * largest field alignment. This reproduces the compiler's default (non-packed)
     * struct layout; #pragma pack(1) structs are not modeled.
     */
    static StructLayout computeStructLayout(String fieldTypesStr) {
        List<String> fields = splitTopLevel(fieldTypesStr);
        int[] offsets = new int[fields.size()];
        int off = 0, maxAlign = 1;
        for (int i = 0; i < fields.size(); i++) {
            String ft = fields.get(i).trim();
            int sz    = irTypeBytes(ft);
            int align = Math.min(Math.max(sz, 1), 4); // 1/2/4-byte natural alignment
            if (align > maxAlign) maxAlign = align;
            if (off % align != 0) off += align - (off % align);
            offsets[i] = off;
            off += sz;
        }

        if (maxAlign > 1 && off % maxAlign != 0) off += maxAlign - (off % maxAlign);
        return new StructLayout(offsets, off);
    }

    static ParseResult parseIR(String text) {
        ParseResult pr = new ParseResult();
        String[] lines = text.split("\r?\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].strip();
            // Struct type def
            Matcher mStruct = Pattern.compile("%struct\\.(\\w+)\\s*=\\s*type\\s+\\{(.*)\\}").matcher(line);
            if (mStruct.matches()) {
                pr.structLayouts.put(mStruct.group(1), computeStructLayout(mStruct.group(2)));
                i++; continue;
            }

            // Global variable/constant. clang -O3 inserts linkage / attribute
            // keywords between "=" and the global|constant keyword, e.g.
            //   @map = dso_local local_unnamed_addr global ptr null, align 8
            //   @levels = external local_unnamed_addr constant [...]
            // Skip any run of such keywords (dso_local, local_unnamed_addr,
            // external, internal, private, unnamed_addr, thread_local, ...).
            Matcher mGlob = Pattern.compile(
                "@(\\S+)\\s*=\\s*"
                + "(?:(?:dso_local|dso_preemptable|external|internal|private|weak|"
                + "linkonce(?:_odr)?|common|appending|available_externally|extern_weak|"
                + "local_unnamed_addr|unnamed_addr|thread_local(?:\\([^)]*\\))?|"
                + "hidden|protected|default)\\s+)*"
                + "(global|constant)\\s+(.+)").matcher(line);
            if (mGlob.matches()) {
                String name = mGlob.group(1);
                String kind = mGlob.group(2);
                String rest = mGlob.group(3);
                // `external` / `extern_weak` / `available_externally` globals are
                // DECLARATIONS only â€” the definition (and its data) lives in another
                // module. Emitting them here would double-define the symbol at link
                // time and, since they carry only a type (no initializer), produce
                // garbage data. Skip them; the linker resolves the reference.
                boolean isExternal = Pattern.compile(
                    "@\\S+\\s*=\\s*(?:\\w+\\s+)*?(?:external|extern_weak|available_externally)\\b")
                    .matcher(line).find();
                if (isExternal) { i++; continue; }
                // Collect continuation lines for multi-line arrays.
                // Skip continuation for c"..." byte blobs: their raw binary content
                // can contain literal '[' and ']' bytes that fool the bracket counter.
                StringBuilder full = new StringBuilder(rest);
                if (!rest.contains("c\"")) {
                    while (countChar(full.toString(), '[') > countChar(full.toString(), ']')
                        || countChar(full.toString(), '{') > countChar(full.toString(), '}')) {
                        i++;
                        if (i >= lines.length) break;
                        full.append(' ').append(lines[i].strip());
                    }
                }

                String fullStr = full.toString().trim();
                Matcher mAlign = Pattern.compile(",?\\s*align\\s+(\\d+)\\s*$").matcher(fullStr);
                int globalAlign = mAlign.find() ? Integer.parseInt(mAlign.group(1)) : 1;
                String init = stripAlign(fullStr);
                pr.globals.add(new GlobalVar(name, kind, init, globalAlign));
                i++; continue;
            }

            // External declare
            Matcher mDecl = Pattern.compile("declare\\s+\\S+\\s+@(\\S+)\\(").matcher(line);
            if (mDecl.find()) {
                pr.declares.add(mDecl.group(1));
                i++; continue;
            }

            // Function definition. The param list is captured with a GREEDY (.*)
            // that backtracks to the last ')' before the trailing attributes/brace â€”
            // NOT "[^)]*", which would stop at the first ')' inside a parenthesised
            // param attribute such as clang-22's `captures(none)` and silently
            // truncate the parameter list.
            Matcher mFunc = Pattern.compile("define\\s+(?:dso_local\\s+)?(.+?)\\s+@(\\S+)\\((.*)\\)\\s*(?:[A-Za-z_][\\w()]*\\s+)*(?:#\\d+\\s*)*\\{?\\s*$").matcher(line);
            if (mFunc.find()) {
                String retType = mFunc.group(1);
                String fname   = mFunc.group(2);
                String params  = mFunc.group(3);
                List<String> bodyLines = new ArrayList<>();
                int braceDepth = 0;
                while (i < lines.length) {
                    String l = lines[i];
                    for (char c : l.toCharArray()) {
                        if (c == '{') braceDepth++;
                        if (c == '}') braceDepth--;
                    }

                    bodyLines.add(l);
                    i++;
                    if (braceDepth == 0) break;
                }

                pr.functions.add(new IrFunction(fname, retType, params, bodyLines));
                continue;
            }

            i++;
        }

        return pr;
    }

    static int countChar(String s, char c) {
        int n = 0;
        for (char ch : s.toCharArray()) if (ch == c) n++;
        return n;
    }

    // =========================================================================
    // Globals emitter

    // =========================================================================
    static void emitGlobals(List<GlobalVar> globals, List<String> out, String moduleName,
                            Map<String, StructLayout> structLayouts) {
        List<GlobalVar> constants   = globals.stream().filter(g -> g.kind().equals("constant")).toList();
        List<GlobalVar> variables   = globals.stream().filter(g -> g.kind().equals("global")).toList();
        List<GlobalVar> zeroVars    = variables.stream().filter(g -> isZeroInit(g.init())).toList();
        List<GlobalVar> nonZeroVars = variables.stream().filter(g -> !isZeroInit(g.init())).toList();
        // RAM variables: RAMDB/RAMDW/RAMDD/RAMBUF macros (from HARDWARE.INC)
        if (!zeroVars.isEmpty() || !nonZeroVars.isEmpty()) {
            out.add("START_OF_RAM\tEVAL\t_MAINRAM");
            for (GlobalVar g : zeroVars) {
                int sz = initSize(g.init(), structLayouts);
                if (sz > 4 || (sz != 1 && sz != 2 && sz != 4)) {
                    out.add("\tRAMBUF\t_" + g.name() + ", " + sz);
                } else {
                    out.add("\t" + ramMacro(sz) + "\t_" + g.name());
                }
            }

            for (GlobalVar g : nonZeroVars) {
                int sz = initSize(g.init(), structLayouts);
                if (sz > 4 || (sz != 1 && sz != 2 && sz != 4)) {
                    out.add("\tRAMBUF\t_" + g.name() + ", " + sz);
                } else {
                    out.add("\t" + ramMacro(sz) + "\t_" + g.name());
                }
            }

            out.add("");
        }

        // ROM constants: labeled data placed at cart ROM base
        if (!constants.isEmpty()) {
            out.add("; ---- ROM constants ----");
            out.add("\torg\t0200000h");
            for (GlobalVar g : constants) {
                out.add("_" + g.name() + ":");
                emitInitializer(g.init(), out, structLayouts);
            }

            out.add("");
        }
    }

    /** Choose the appropriate RAM macro for a scalar variable. */
    static String ramMacro(int sz) {
        if (sz == 1) return "RAMDB";
        if (sz == 2) return "RAMDW";
        return "RAMDD"; // 4 bytes
    }

    static int initSize(String init, Map<String, StructLayout> sl) {
        String s = init.trim();
        // Use greedy capture for nested array types like [15 x [15 x i8]]
        Matcher m = Pattern.compile("\\[(\\d+)\\s+x\\s+(.+)\\]", Pattern.DOTALL).matcher(s);
        if (m.find()) return Integer.parseInt(m.group(1)) * irTypeBytes(m.group(2), sl);
        m = Pattern.compile("^(%struct\\.\\w+|\\S+)\\s*").matcher(s);
        if (m.find()) return irTypeBytes(m.group(1), sl);
        return 2;
    }

    static int initSize(String init) {
        return initSize(init, null);
    }

    static void emitInitializer(String initStr, List<String> out,
                                Map<String, StructLayout> structLayouts) {
        String s = stripAlign(initStr.trim());
        // String literal: [N x i8] c"..."
        Matcher mStr = Pattern.compile("\\[(\\d+)\\s+x\\s+i8\\]\\s+c\"(.*)\"", Pattern.DOTALL).matcher(s);
        if (mStr.matches()) {
            emitByteChunks(decodeCString(mStr.group(2)), out);
            return;
        }

        // Single struct: %struct.NAME { fields }
        Matcher mStruct = Pattern.compile("%struct\\.(\\w+)\\s*(\\{.+\\})", Pattern.DOTALL).matcher(s);
        if (mStruct.matches()) {
            emitStructInstance(mStruct.group(1), mStruct.group(2), out, structLayouts);
            return;
        }

        // Anonymous struct with an explicit inline type: "{ T1, T2, ... } { V1, V2, ... }"
        // clang-22 emits aggregate globals this way (vs clang-18's named `%struct.NAME
        // { ... }`). The inline type carries explicit padding fields ([N x i8]), so
        // emitting each value field in order reproduces the exact byte layout â€” no
        // struct-layout table needed. Detect a leading balanced {..} (the type)
        // FOLLOWED by another {..} (the value); flatten the value's fields.
        if (s.startsWith("{")) {
            int typeEnd = matchBrace(s, 0);
            if (typeEnd > 0) {
                String afterType = s.substring(typeEnd + 1).trim();
                if (afterType.startsWith("{") && afterType.endsWith("}")) {
                    String values = afterType.substring(1, afterType.length() - 1).trim();
                    for (String field : splitTopLevel(values)) {
                        emitInitializer(field.trim(), out, structLayouts);
                    }

                    return;
                }
            }
        }

        // Bare struct literal: { fields }
        if (s.startsWith("{")) {
            emitStructInstance("", s, out, structLayouts);
            return;
        }

        // Packed anonymous struct: "<{ T1, T2, ... }> <{ V1, V2, ... }>"
        // No padding, no alignment â€” each field is a standalone initializer
        // (typically an array of scalars). Flatten each field in order.
        if (s.startsWith("<{")) {
            int typeEnd = findPackedStructEnd(s, 0);
            if (typeEnd > 0) {
                String afterType = s.substring(typeEnd + 1).trim();  // skip past "}>"
                if (afterType.startsWith("<{")) {
                    int valEnd = findPackedStructEnd(afterType, 0);
                    if (valEnd > 0) {
                        String values = afterType.substring(2, valEnd - 1).trim(); // strip "<{" ... "}>"
                        for (String field : splitTopLevel(values)) {
                            emitInitializer(field, out, structLayouts);
                        }

                        return;
                    }
                }
            }
        }

        // Array: [N x T] zeroinitializer or [...]
        // Handles nested arrays such as [71 x [4028 x i16]] by flattening to a
        // single sequence of scalar values of the innermost element type.
        // Also handles arrays of named or anonymous structs.
        String[] hdr = parseArrayHeader(s);
        if (hdr != null) {
            int    count    = Integer.parseInt(hdr[0]);
            String elemType = hdr[1];
            String body     = hdr[2];
            String scalarType = innermostElemType(elemType);
            if ("zeroinitializer".equals(body)) {
                emitZeroBytes(count * irTypeBytes(elemType), out);
            } else if (elemType.startsWith("%struct.") || elemType.startsWith("{")) {
                List<String> instances = splitStructInstances(body);
                String sName = elemType.startsWith("%struct.") ? elemType.substring(8) : "";
                for (String inst : instances) {
                    emitStructInstance(sName, inst, out, structLayouts);
                }

            } else {
                List<String> items = flattenArrayItems(elemType, body);
                emitDataChunks(directiveFor(scalarType), items, scalarType, out);
            }

            return;
        }

        // Scalar: 'type value' or 'ptr @sym'
        String[] parts = s.split("\\s+", 2);
        if (parts.length == 2) {
            String t = parts[0];
            String v = stripAlign(parts[1]);
            out.add("\t" + directiveFor(t) + " " + irValLiteral(v, t));
            return;
        }

        out.add("\t; unhandled initializer: " + s);
    }

    /** Decode an LLVM `c"..."` byte-string body (with \XX hex escapes) into byte
     *  values. Shared by the top-level string initializer and the nested-array
     *  ([N x i8] c"...") element path. */
    static List<Integer> decodeCString(String raw) {
        List<Integer> bytes = new ArrayList<>();
        int j = 0;
        while (j < raw.length()) {
            if (raw.charAt(j) == '\\' && j + 2 < raw.length()
                    && isHexChar(raw.charAt(j+1)) && isHexChar(raw.charAt(j+2))) {
                bytes.add(Integer.parseInt(raw.substring(j+1, j+3), 16));
                j += 3;
            } else {
                bytes.add((int) raw.charAt(j));
                j++;
            }
        }

        return bytes;
    }

    static void emitByteChunks(List<Integer> bytes, List<String> out) {        for (int i = 0; i < bytes.size(); i += 16) {
            List<Integer> chunk = bytes.subList(i, Math.min(i + 16, bytes.size()));
            out.add("\tdb " + chunk.stream().map(String::valueOf).collect(Collectors.joining(", ")));
        }
    }

    static void emitZeroBytes(int count, List<String> out) {
        List<Integer> zeros = new ArrayList<>(Collections.nCopies(count, 0));
        emitByteChunks(zeros, out);
    }

    static void emitDataChunks(String dir, List<String> items, String elemType, List<String> out) {
        for (int i = 0; i < items.size(); i += 8) {
            List<String> chunk = new ArrayList<>();
            for (int j = i; j < Math.min(i + 8, items.size()); j++) {
                chunk.add(irValLiteral(items.get(j), elemType));
            }

            out.add("\t" + dir + " " + String.join(", ", chunk));
        }
    }

    /**
     * Parse an array initializer header "[N x T] BODY" where T itself may be a
     * nested "[M x U]" and BODY is either "zeroinitializer" or "[ ... ]".
     * Returns {countStr, elemType, body} on success, or null if s is not an
     * array initializer. Bracket-aware â€” unlike a plain regex, this correctly
     * accepts an element type that contains further "[...]".
     */
    static String[] parseArrayHeader(String s) {
        s = s.trim();
        if (s.isEmpty() || s.charAt(0) != '[') return null;
        // Find matching ']' for the type prefix
        int depth = 0, end = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) { end = i; break; }
            }
        }

        if (end < 0) return null;
        String type = s.substring(1, end).trim();           // "N x T"
        Matcher mt = Pattern.compile("(\\d+)\\s+x\\s+(.+)", Pattern.DOTALL).matcher(type);
        if (!mt.matches()) return null;
        String count    = mt.group(1);
        String elemType = mt.group(2).trim();
        String rest     = s.substring(end + 1).trim();
        if (rest.equals("zeroinitializer")) {
            return new String[]{count, elemType, "zeroinitializer"};
        }

        if (rest.startsWith("[") && rest.endsWith("]")) {
            return new String[]{count, elemType, rest.substring(1, rest.length() - 1).trim()};
        }

        return null;
    }

    /** Innermost scalar element type of a (possibly nested) array type. */
    static String innermostElemType(String t) {
        t = t.trim();
        while (t.startsWith("[")) {
            String[] hdr = parseArrayHeader(t + " zeroinitializer");
            if (hdr == null) break;
            t = hdr[1];
        }

        return t;
    }

    /**
     * Flatten a (possibly nested) array initializer body into a flat list of
     * scalar-typed value strings. `elemType` is the type of each element of
     * the outer body â€” it may itself be "[M x U]", in which case each element
     * is recursively flattened.
     */
    static List<String> flattenArrayItems(String elemType, String body) {
        // Split top-level (respecting [] and {} nesting).
        List<String> rawItems = splitTopLevel(body);
        // If elemType is not an array, strip the leading type token from each
        // item (same convention as parseInitList) and return.
        if (!elemType.trim().startsWith("[")) {
            List<String> values = new ArrayList<>();
            for (String item : rawItems) {
                Matcher m = Pattern.compile("^\\S+\\s+(.*)", Pattern.DOTALL).matcher(item.trim());
                values.add(m.matches() ? m.group(1).trim() : item.trim());
            }

            return values;
        }

        // Nested array elements. Each item is itself "[M x U] [...]" or
        // "[M x U] zeroinitializer" or the string-literal form "[M x i8] c"..."" â€”
        // recurse / decode.
        List<String> out = new ArrayList<>();
        for (String item : rawItems) {
            String it = item.trim();
            // String-literal element: [N x i8] c"..." â€” decode to byte values.
            Matcher mS = Pattern.compile("\\[(\\d+)\\s+x\\s+i8\\]\\s+c\"(.*)\"", Pattern.DOTALL).matcher(it);
            if (mS.matches()) {
                for (int b : decodeCString(mS.group(2))) out.add(String.valueOf(b));
                continue;
            }

            String[] hdr = parseArrayHeader(it);
            if (hdr == null) {
                // Shouldn't happen for well-formed IR; fall back to a raw token.
                out.add(it);
                continue;
            }

            int    innerCount = Integer.parseInt(hdr[0]);
            String innerType  = hdr[1];
            String innerBody  = hdr[2];
            if ("zeroinitializer".equals(innerBody)) {
                String scalarType = innermostElemType(innerType);
                int totalScalars  = innerCount * (irTypeBytes(innerType) / Math.max(1, irTypeBytes(scalarType)));
                for (int k = 0; k < totalScalars; k++) out.add("0");
            } else {
                out.addAll(flattenArrayItems(innerType, innerBody));
            }
        }

        return out;
    }

    /** Split a comma-separated list at top level, respecting [] and {} nesting. */
    static List<String> splitTopLevel(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '[' || ch == '{') { depth++; cur.append(ch); }
            else if (ch == ']' || ch == '}') { depth--; cur.append(ch); }
            else if (ch == ',' && depth == 0) { result.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(ch);
        }

        if (!cur.toString().isBlank()) result.add(cur.toString().trim());
        return result;
    }

    /**
     * Given s[start..start+1] == "<{", return the index of the '>' that
     * closes the matching "}>", tracking [] and {} depth. Returns -1 if
     * unmatched.
     */

    /** Index of the '}' matching the '{' at position `start` (brackets/braces
     *  nested), or -1 if unbalanced / not a '{'. */
    static int matchBrace(String s, int start) {
        if (start >= s.length() || s.charAt(start) != '{') return -1;
        int depth = 0;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') depth++;
            else if (c == '}' || c == ']') { if (--depth == 0) return i; }
        }

        return -1;
    }

    static int findPackedStructEnd(String s, int start) {        if (start + 1 >= s.length() || s.charAt(start) != '<' || s.charAt(start + 1) != '{') return -1;
        int depth = 0;
        for (int i = start + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{' || c == '[') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0 && i + 1 < s.length() && s.charAt(i + 1) == '>') return i + 1;
            } else if (c == ']') depth--;
        }

        return -1;
    }

    /**
     * Split a comma-separated list of struct instances like
     * "%struct.FOO { i8 1, i8 2, ptr @BAR }, %struct.FOO { ... }"
     * into individual "{ ... }" bodies, respecting brace depth.
     */
    static List<String> splitStructInstances(String body) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(body.substring(start, i + 1).trim());
                    start = -1;
                }
            }
        }

        return result;
    }

    /**
     * Emit a single struct instance `{ field, field, ... }` for a named struct.
     * When a StructLayout is available the fields are emitted with explicit padding
     * (`ds N`) between them so the target (NGPC) memory layout is exact.
     * Unknown structs fall back to emitting each field with its declared type and
     * no padding.
     */
    static void emitStructInstance(String structName, String braceExpr, List<String> out,
                                   Map<String, StructLayout> structLayouts) {
        // Strip outer braces
        String inner = braceExpr.trim();
        if (inner.startsWith("{")) inner = inner.substring(1);
        if (inner.endsWith("}"))   inner = inner.substring(0, inner.length() - 1);
        inner = inner.trim();
        // Parse typed fields:  "i8 48, i8 48, ptr @LEVEL0_MAP"  etc.
        List<String[]> fields = parseTypedFields(inner); // each entry: [type, value]
        // Bare literal struct (structName == "") has no named %struct type, so no
        // precomputed layout is available and the field list carries whatever padding
        // the *host* compiler baked in. clang-18 emits a named %struct.LEVEL and the
        // layout comes from computeStructLayout; clang-22 instead inlines the struct as
        // a literal aggregate with explicit host padding, e.g.
        //   { i8, i8, [6 x i8], ptr }   (x86-64: ptr 8-byte aligned -> 6-byte filler)
        // That 6-byte filler is host-specific and wrong for the TLCS900 (4-byte ptr).
        // Drop the zero-initialized [N x i8] filler fields and let the transpiler's own
        // target-correct layout rule (computeStructLayout) place the real fields, so both
        // clang versions produce identical output that agrees with the named-struct GEP
        // offsets used on the code side (main.ll).
        StructLayout sl = (structLayouts != null) ? structLayouts.get(structName) : null;
        if (sl == null && (structName == null || structName.isEmpty()) && !fields.isEmpty()) {
            List<String[]> real = new ArrayList<>();
            boolean strippedPad = false;
            for (String[] f : fields) {
                if (isPaddingField(f[0], f[1])) { strippedPad = true; continue; }
                real.add(f);
            }

            if (strippedPad && !real.isEmpty()) {
                fields = real;
                String fieldTypes = fields.stream().map(f -> f[0])
                                          .collect(java.util.stream.Collectors.joining(", "));
                sl = computeStructLayout(fieldTypes);
            }
        }

        // If we have a computed layout for this struct, use it to place each field
        // at the correct byte offset with explicit padding gaps.
        if (sl != null && fields.size() == sl.fieldOffsets().length) {
            int cursor = 0;
            for (int i = 0; i < fields.size(); i++) {
                int targetOff = sl.fieldOffsets()[i];
                if (targetOff > cursor) {
                    out.add("\tds " + (targetOff - cursor) + "\t; alignment padding");
                    cursor = targetOff;
                }

                String type = fields.get(i)[0], val = fields.get(i)[1];
                emitInitializer(type + " " + val, out, structLayouts);
                cursor += irTypeBytes(type);
            }

            // Trailing padding to reach the struct's total size
            if (sl.size() > cursor) {
                out.add("\tds " + (sl.size() - cursor) + "\t; trailing padding");
            }

            return;
        }

        // Generic fallback: emit each field with its natural directive (no padding)
        for (String[] f : fields) {
            emitInitializer(f[0] + " " + f[1], out, structLayouts);
        }
    }

    /**
     * True for a compiler-inserted alignment-padding field: an [N x i8] array whose
     * value is all-zero (`zeroinitializer`, an empty aggregate, or an explicit list of
     * `i8 0`). Such fields exist only to reproduce the *host* ABI's alignment and must
     * be dropped so the target layout can be recomputed. A non-zero i8 array is a real
     * data member (e.g. an embedded char buffer) and is preserved.
     */
    static boolean isPaddingField(String type, String val) {
        String t = type.trim();
        if (!t.matches("\\[\\d+\\s*x\\s*i8\\]")) return false;
        String v = val == null ? "" : val.trim();
        if (v.equals("zeroinitializer") || v.isEmpty()) return true;
        if (v.startsWith("[") && v.endsWith("]")) {
            String body = v.substring(1, v.length() - 1).trim();
            if (body.equals("zeroinitializer")) return true;
            for (String item : splitTopLevel(body)) {
                if (!item.trim().matches("i8\\s+0")) return false;
            }

            return true;
        }


        return false;
    }

    /** Parse "type val, type val, ..." respecting nested parens/brackets. */
    static List<String[]> parseTypedFields(String s) {
        List<String[]> result = new ArrayList<>();
        List<String> tokens = splitCommaDepth(s);
        for (String tok : tokens) {
            tok = tok.trim();
            int sp = 0, depth = 0;
            while (sp < tok.length()) {
                char c = tok.charAt(sp);
                if (c == '[' || c == '{' || c == '<' || c == '(') depth++;
                else if (c == ']' || c == '}' || c == '>' || c == ')') depth--;
                else if (c == ' ' && depth == 0) break;
                sp++;
            }

            if (sp >= tok.length()) continue;
            String type = tok.substring(0, sp).trim();
            String val  = tok.substring(sp + 1).trim();
            // Strip attribute keywords
            val = val.replaceAll("^(noundef|zeroext|signext)\\s+", "").trim();
            result.add(new String[]{type, val});
        }

        return result;
    }

    static List<String> splitCommaDepth(String s) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(' || c == '[' || c == '{') { depth++; cur.append(c); }
            else if (c == ')' || c == ']' || c == '}') { depth--; cur.append(c); }
            else if (c == ',' && depth == 0) { result.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(c);
        }

        if (!cur.toString().isBlank()) result.add(cur.toString().trim());
        return result;
    }

    static boolean isHexChar(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    // =========================================================================
    // Call-argument parser

    // =========================================================================

    record Arg(String type, String val) {}
    static List<Arg> parseCallArgs(String argsStr) {
        if (argsStr == null || argsStr.isBlank()) return List.of();
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (char ch : argsStr.toCharArray()) {
            if (ch == '(' || ch == '[') { depth++; cur.append(ch); }
            else if (ch == ')' || ch == ']') { depth--; cur.append(ch); }
            else if (ch == ',' && depth == 0) { parts.add(cur.toString().trim()); cur.setLength(0); }
            else cur.append(ch);
        }

        if (!cur.toString().isBlank()) parts.add(cur.toString().trim());
        List<Arg> result = new ArrayList<>();
        for (String part : parts) {
            part = part.trim();
            Matcher m = Pattern.compile("^(\\S+)\\s+(.*)$", Pattern.DOTALL).matcher(part);
            String argType, rest;
            if (m.matches()) {
                argType = stripAttrs(m.group(1));
                // Strip any parameter attributes (noundef, nonnull, align N,
                // dereferenceable(N), ...) that clang -O3 places between the type
                // and the actual value operand.
                rest = stripAttrs(m.group(2)).trim();
            } else {
                argType = "i32";
                rest = part;
            }

            result.add(new Arg(argType, rest.trim()));
        }

        return result;
    }

    // WORKAROUND: StringBuilder.toString() is a method, not a field
    // (Fix the parseCallArgs method's compilation error)

    // =========================================================================
    // Function Translator

    // =========================================================================

    static class FuncTranslator {
        // Module-wide set of `equ` symbols already emitted. asl makes `equ` global,
        // so the same hardware-constant equ appearing in several inline-asm functions
        // (e.g. _TILERAM in both hc_load and mp_load) would be a "symbol double
        // defined" error. Emit each symbol's equ once; skip later identical ones.

        static final Map<String, String> emittedEqu = new HashMap<>();
        final IrFunction func;
        final Set<String> globalNames;
        final Map<String, StructLayout> structLayouts;
        final String bbPrefix; // per-function BB label prefix, e.g. ".Lbb_main_"
        final boolean regChain;
        // slot name → [frameOffset, sizeBytes]
        final Map<String, int[]> slots = new LinkedHashMap<>();
        int frameSize = 0;
        final Set<String> allocaSlots = new LinkedHashSet<>();
        final List<String> out = new ArrayList<>();
        int intrinLabelCtr = 0;   // unique suffix for intrinsic-lowering local labels
        // Tracks which SSA slot (name) is currently live in XWA/WA/A after the
        // last storeWAtoSlot. Cleared at every basic-block boundary (label emit)
        // and whenever a non-WA register is loaded into WA by a different source.
        // Allows loadToWA to skip a redundant reload when the value is already there.
        String waSlotName = null;   // slot whose value is currently in WA/XWA
        int    waSlotBits = 0;
        int    memLoopSeq = 0;      // unique-label counter for memfill/memcopy loops
        String xhlSlotName = null;  // slot whose value is currently in XHL
        // When a GEP whose result is xhlOnly has its base in XHL and its
        // (possibly pre-scaled) index still live in XWA — i.e. the `add xhl,xwa`
        // was deliberately NOT emitted — this holds the GEP result name.
        // The immediately-following consuming load/store then emits `(xhl+wa)` directly.
        // Cleared with xhlSlotName at the BB boundary and on any WA/XWA write in emit().
        String xhlIndexPending = null;
        // SSA names promoted to XBC / XDE / XIX / XIY / XIZ instead of a stack slot.
        // Chosen in prePass: the up-to-five most-accessed alloca slots.
        // All five are callee-saved: push/pop emitted in prologue/epilogue.
        String bcSlotName = null;   // SSA name living in XBC/BC/B (set by prePass)
        String deSlotName = null;   // SSA name living in XDE/DE/E (set by prePass)
        String ixSlotName = null;   // SSA name living in XIX/IX (set by prePass)
        String iySlotName = null;   // SSA name living in XIY/IY (set by prePass)
        String izSlotName = null;   // SSA name living in XIZ/IZ (set by prePass)
        int    bcSlotBits = 32;     // width of the value in XBC
        int    deSlotBits = 32;     // width of the value in XDE
        int    ixSlotBits = 32;     // width of the value in XIX
        int    iySlotBits = 32;     // width of the value in XIY
        int    izSlotBits = 32;     // width of the value in XIZ
        // Allocation-time widths used to choose symmetric callee saves. Unlike the
        // live slot widths above, these never change while instructions are emitted.
        int    bcSaveBits = 32;
        int    deSaveBits = 32;
        int    ixSaveBits = 32;
        int    iySaveBits = 32;
        int    izSaveBits = 32;
        boolean useXBC = false;     // true if XBC is allocated to any SSA name
        boolean useXDE = false;     // true if XDE is allocated to any SSA name
        boolean useXIX = false;     // true if XIX is allocated to any SSA name
        boolean useXIY = false;     // true if XIY is allocated to any SSA name
        boolean useXIZ = false;     // true if XIZ is allocated to any SSA name

        // ---- Bank-1 and bank-2 general registers (TLCS-900/H alternate register files).
        // The CPU has 4 banks of WA/BC/DE/HL; the transpiler runs in bank 0 (RBS never
        // changes here except the BIOS `ldf 3` idiom in hand-written library code, which
        // uses bank 3 and restores RBS). Banks 1 and 2 are otherwise unused, so their
        // 8 registers extend the promotion pool from 5 to 13. asl names them xwaN/xbcN/
        // xdeN/xhlN (32-bit), rwaN/rbcN/... (16-bit), raN/rcN/... (8-bit); encoded via the
        // "R" register-file-direct prefix (C7/D7/E7). They are physically separate register
        // files, so their contents survive calls/interrupts automatically — but this
        // emitter still push/pops them (callee-saved) for uniformity and safety.
        // Fixed pool order for deterministic output: bc1,de1,hl1,wa1, bc2,de2,hl2,wa2.
        static final String[] BANK_REG_KEYS = {"bc1","de1","hl1","wa1","bc2","de2","hl2","wa2"};
        // 32/16/8-bit assembly mnemonics keyed by BANK_REG_KEYS index.
        static final String[] BANK_REG_X = {"xbc1","xde1","xhl1","xwa1","xbc2","xde2","xhl2","xwa2"};
        static final String[] BANK_REG_R = {"rbc1","rde1","rhl1","rwa1","rbc2","rde2","rhl2","rwa2"};
        static final String[] BANK_REG_B = {"rc1","re1","rl1","ra1","rc2","re2","rl2","ra2"};
        final String[] bankSlotName = new String[BANK_REG_KEYS.length];  // SSA name in each bank reg
        final int[]    bankSlotBits = new int[BANK_REG_KEYS.length];     // live width
        final int[]    bankSaveBits = new int[BANK_REG_KEYS.length];     // allocation-time width (for save)
        final boolean[] useBank     = new boolean[BANK_REG_KEYS.length]; // allocated?
        final String[] bankLive     = new String[BANK_REG_KEYS.length];  // current runtime contents

        // Promoted values live in callee-saved registers under the CC900 convention
        // used by this emitter (callee prologues push/pop any allocated BC/DE/IX/IY/IZ),
        // so they do not need stack spill/reload around calls.
        boolean bcNeedsSpillAroundCall = false;
        boolean deNeedsSpillAroundCall = false;
        boolean ixNeedsSpillAroundCall = false;
        boolean iyNeedsSpillAroundCall = false;
        boolean izNeedsSpillAroundCall = false;
        // Current runtime contents of XBC / XDE / XIX / XIY / XIZ (tracks live value name)
        String bcLive = null;
        String deLive = null;
        String ixLive = null;
        String iyLive = null;
        String izLive = null;
        // Analysis results shared between analyzeValues(), assignRegisters(), allocSlots().
        // accessCounts: per-slot access count used for promotion ranking.
        // needsSpillSet: slots accessed in BBs that contain calls (spill-around-call candidates).
        // valueSizeBytes: byte size of each named value (alloca type size or 4 for SSA temps).
        //   Populated during analysis so assignRegisters() can determine slot width
        //   before slots are allocated.
        Map<String, Integer> accessCounts  = new LinkedHashMap<>();
        Set<String> needsSpillSet          = new LinkedHashSet<>();
        Set<String> callArgValues          = new LinkedHashSet<>();
        Map<String, Integer> valueSizeBytes = new LinkedHashMap<>();
        // Declared result width (bits) of SSA temps whose defining instruction has an
        // unambiguous type. Seeds the promoted-register width (see promotedBits).
        Map<String, Integer> valueDefBits = new LinkedHashMap<>();
        // Conservative unsigned upper bound (saturating at 0x10000 = "wide") for each
        // i32 SSA value in this function. Computed once in translate() via computeBounds.
        // Used by emitBinop's mul lowering to prove a 32-bit multiply's operands both
        // fit in 16 bits, so it can use the hardware 16x16->32 `mul` (a single
        // instruction giving the exact 32-bit product) instead of calling the
        // _C9H_mul32 software shift-add helper. Null until computed.
        Map<String, Long> valueBounds = null;
        // i32 SSA values known to be sign-extended from i8/i16. Two such operands
        // can use the native signed 16x16->32 multiply without losing high bits.
        Set<String> signed16Values = Set.of();
        // Names of i32 SSA values whose ONLY uses are as getelementptr index operands.
        // This is use information only; it must not imply that the address fits 16 bits.
        // Large objects such as Mode 7's 256 KiB track require the full i32 offset.
        // Null until computed.
        Set<String> addrOnlyMulResults = null;
        // i32 SSA values that are a widened 16-bit C quantity: built from zext i8/i16,
        // small loop-induction phis, and/or immediates, combined only with
        // add/sub/and/or/xor/mul/shl. clang widens these to i32 to satisfy this
        // pseudo-target's 32-bit `int` promotion rules, but the real TLCS-900 (and the
        // CC900 compiler this transpiler must match) has a 16-bit `int`, so the
        // ORIGINAL C semantics are 16-bit wrapping arithmetic throughout. Truncating
        // such a value to its low 16 bits before feeding the hardware `mul` therefore
        // reproduces CC900's output exactly -- unlike valueBounds (a true numeric upper
        // bound), this set says nothing about magnitude and must NEVER be used where the
        // actual numeric range matters (e.g. finishGepIndexed). Populated once in
        // translate() via computeU16TruncSafe(); null until computed.
        Set<String> u16TruncSafeValues = null;
        // Names that were alloca instructions in the original IR (before scalarization).
        // Populated in analyzeValues() so allocSlots() can correctly identify allocas
        // even when scalarizeLocalAllocas() has removed the alloca instructions.
        Set<String> originalAllocaNames    = new LinkedHashSet<>();
        // Parsed basic blocks for switch scanning
        List<BasicBlock> parsedBlocks = null;
        BasicBlock currentBlock = null;
        int currentInstrIndex = -1;
        String booleanAndLeftInL = null;

        static final List<String> ISR_REGS = List.of("xwa", "xhl", "xde", "xbc", "xix", "xiy");
        final boolean isInterrupt;
        final boolean isEntryPoint;
        // An interrupt handler with no real code (body is only ret/ret void) needs
        // no register save/restore â€” just reti. Computed lazily on first use since
        // it depends on the parsed body. Read by both translate() (prologue) and
        // translateInstr() (epilogue), so it must be a field, not a local.
        Boolean isrHasBodyCache;
        boolean isrHasBody() {
            if (isrHasBodyCache == null) {
                isrHasBodyCache = isInterrupt && parseBody().stream()
                    .flatMap(bb -> bb.instrs().stream())
                    .anyMatch(i -> !i.equals("ret void") && !i.equals("ret"));
            }

            return isrHasBodyCache;
        }

        // A function written as raw inline asm (the hand-written `memcpy` pattern)
        // manages the stack itself and reads its arguments at fixed (XSP+N) offsets
        // relative to the ENTRY stack pointer. We must NOT allocate a local frame or
        // spill params for such a function â€” doing so shifts XSP and invalidates every
        // hardcoded offset in the inline asm (and its early `ret`s would then skip a
        // frame-cleanup epilogue). When set, the prologue/epilogue and all
        // frame-relative boilerplate are suppressed and the asm is emitted verbatim.
        //
        // This is INTENTIONALLY narrow: a function qualifies only if it contains
        // inline asm AND every other instruction is pure boilerplate (alloca, a
        // param→alloca spill store, or a terminator). A function that also does real
        // work â€” loads, GEPs, arithmetic, compares, branches, calls, MMIO stores
        // (e.g. InitNGPC, which has a lone `ei`) â€” is a NORMAL function and keeps its
        // frame; its inline asm is emitted inline but the surrounding IR is compiled.
        final boolean isRawAsm;
        FuncTranslator(IrFunction func, Set<String> globalNames,
                       Map<String, StructLayout> structLayouts) {
            this.func          = func;
            this.globalNames   = globalNames;
            this.structLayouts = structLayouts;
            this.regChain      = func.body().stream()
                .noneMatch(line -> line.contains(" alloca "));
            this.bbPrefix      = ".Lbb_" + func.name() + "_";
            this.isInterrupt   = func.name().toLowerCase().contains("interrupt");
            this.isEntryPoint  = func.name().equals("main");
            this.isRawAsm      = computeIsRawAsm(func);
        }

        /** True only for the "body is entirely inline asm + param-spill boilerplate"
         *  pattern (see the isRawAsm doc comment). */
        static boolean computeIsRawAsm(IrFunction func) {
            boolean hasAsm = false;
            boolean inFunc = false;
            for (String raw : func.body()) {
                String s = raw.strip();
                if (s.isEmpty() || s.startsWith(";")) continue;
                if (s.startsWith("define ")) { inFunc = true; continue; }
                if (s.equals("{")) { inFunc = true; continue; }
                if (s.equals("}")) break;
                if (!inFunc) continue;
                // Labels / block terminators structure only.
                if (s.matches("^[A-Za-z_][\\w.]*:.*$") || s.matches("^\\d+:.*$")) continue;
                boolean isAsm    = s.contains("asm sideeffect") || s.contains("asm \"");
                boolean isAlloca = s.matches("%[^=]+=\\s*alloca\\s+.*");
                // A param spill: `store <ty> %p, ptr %slot` (dest is an SSA alloca slot).
                boolean isSpill  = s.matches("store\\s+\\S+\\s+%[^,]+,\\s*ptr\\s+%[^,\\s]+.*");
                boolean isRet    = s.equals("ret void") || s.equals("ret")
                                   || s.matches("ret\\s+\\S+\\s+\\S+");
                if (isAsm) { hasAsm = true; continue; }
                if (isAlloca || isSpill || isRet) continue;
                // Any real computation disqualifies the whole function.
                return false;
            }

            return hasAsm;
        }

        // -- slot management --
        int allocSlot(String name, int size) {
            if (slots.containsKey(name)) return slots.get(name)[0];
            size = Math.max(size, 2);
            slots.put(name, new int[]{frameSize, size});
            frameSize += size;
            if ((frameSize & 1) != 0) frameSize++; // 2-byte align
            return slots.get(name)[0];
        }

        int tmpSlot(String tag) {
            String name = "__t_" + tag;
            if (!slots.containsKey(name)) allocSlot(name, 4);
            return slots.get(name)[0];
        }

        // -- slot compaction (liveness-based stack-slot reuse) --------------------
        //
        // The allocator above assigns every SSA temporary its own permanent frame
        // slot and never reuses them, so a branch-heavy function ends up with a huge
        // frame (e.g. _doMove: 462 bytes / 135 slots for ~7 real locals). This pass
        // reassigns the frame OFFSETS of reusable slots so that slots whose live
        // ranges do not overlap share the same physical offset, then recomputes a
        // smaller frameSize.
        //
        // It runs after prePass()+preallocTmpSlots() (all up-front slots known) and
        // BEFORE any instruction is emitted. Every emit reads its offset live via
        // slots.get(name)[0] (+ a relative stackAdj), and frameSize drives the
        // prologue/epilogue and incoming-arg offsets uniformly â€” so rewriting the
        // map here is transparent to the whole emitter. Lazily-created gepptr_ tmps
        // (allocated during emission) simply append past the compacted frame.
        //
        // Two tiers:
        //   A. __t_* scratch tmps (gep_/gep16_/gepst_/gepptr_/store_) are live only
        //      within the expansion of a SINGLE IR instruction and never two-at-once,
        //      so ALL of them collapse onto one shared scratch offset.
        //   B. value slots (%N spilled by storeWAtoSlot) get real CFG liveness and
        //      are greedily colored onto shared offsets grouped by size.
        //
        // Excluded (kept at private, whole-function offsets): address-escaping allocas
        // and scratch values whose lifetimes are implicit in the emitter.
        void compactSlots() {
            if (slots.isEmpty()) return;
            List<BasicBlock> bbs = parseBody();
            // Names that must NOT be remapped: address-escaping allocas and implicit
            // emitter scratch values. Keep all phi participants private as well: phi
            // edge copies are emitted sequentially and interact with the emitter's WA
            // residency cache, so ordinary interval coloring is not sufficient to
            // preserve every loop-carried copy.
            // Non-escaping allocas (only direct load/store ptr %slot) are safe to compact.
            // Promoted names (bcSlotName etc.) no longer have stack slots at all in the
            // new multi-pass design, so there is nothing to pin here.
            Set<String> pinned = new LinkedHashSet<>();
            for (String a : allocaSlots) {
                if (allocaEscapes(a, bbs)) pinned.add(a);
            }

            pinned.addAll(phiParticipantNames());
            // The branch-condition spill slot (see maybeSpillBranchCond) must keep a
            // fixed private offset: it holds a value live across phi copies, so it must
            // not alias the shared __t_ scratch or any compacted value slot.
            if (slots.containsKey(CONDSPILL_SLOT)) pinned.add(CONDSPILL_SLOT);
            if (slots.containsKey(SELSPILL_SLOT))  pinned.add(SELSPILL_SLOT);
            // memfill/memcopy scratch: three distinct offsets held live simultaneously.
            if (slots.containsKey(MEM_DST_SLOT)) pinned.add(MEM_DST_SLOT);
            if (slots.containsKey(MEM_LEN_SLOT)) pinned.add(MEM_LEN_SLOT);
            if (slots.containsKey(MEM_AUX_SLOT)) pinned.add(MEM_AUX_SLOT);
            // Partition current slots into: scratch tmps, remappable value slots,
            // and pinned (left exactly where they are).
            List<String> scratch = new ArrayList<>();
            List<String> valueSlots = new ArrayList<>();
            for (String name : slots.keySet()) {
                if (name.startsWith("__t_")) { scratch.add(name); continue; }
                if (pinned.contains(name))   continue;
                valueSlots.add(name);
            }

            // ---- Liveness for value slots over the BB CFG --------------------
            // Flatten blocks in body order; each instruction gets a global index.
            // A slot's live interval is [firstAccess, lastAccess] extended across the
            // CFG so a value defined before a loop and used inside stays live across
            // the whole loop body. We compute per-block use/def sets at slot
            // granularity and iterate live-in/live-out to a fixpoint, then derive a
            // conservative [min,max] global-index interval per slot.
            Map<String, Integer> firstIdx = new HashMap<>();
            Map<String, Integer> lastIdx  = new HashMap<>();
            Map<String, int[]> blockRange = new HashMap<>();   // label -> [startIdx,endIdx]
            List<String> labelsInOrder = new ArrayList<>();

            {

                int gi = 0;
                for (BasicBlock bb : bbs) {
                    int start = gi;
                    labelsInOrder.add(bb.label());
                    for (String instr : bb.instrs()) {
                        for (String n : slotRefs(instr, valueSlots)) {
                            firstIdx.merge(n, gi, Math::min);
                            lastIdx.merge(n, gi, Math::max);
                        }

                        gi++;
                    }

                    blockRange.put(bb.label(), new int[]{start, gi});
                }
            }

            // CFG successors from terminators.
            Map<String, List<String>> succ = new HashMap<>();
            for (BasicBlock bb : bbs) succ.put(bb.label(), successorsOf(bb));
            // Per-block upward-exposed use and def sets (proper SSA liveness with
            // kill). def[B] = value slots defined (%n = ...) in B; use[B] = value
            // slots referenced in B BEFORE being defined in B. A single-def SSA value
            // is killed at its definition, so it is NOT live-in to blocks that precede
            // its def â€” this keeps intervals tight so disjoint values can coalesce.
            Map<String, Set<String>> useB = new HashMap<>();
            Map<String, Set<String>> defB = new HashMap<>();
            for (BasicBlock bb : bbs) {
                Set<String> u = new LinkedHashSet<>();
                Set<String> d = new LinkedHashSet<>();
                for (String instr : bb.instrs()) {
                    String s = instr.strip();
                    // Definition (LHS) of this instruction, if any.
                    String defName = null;
                    Matcher md = Pattern.compile("^%([^\\s,=]+)\\s*=").matcher(s);
                    if (md.find()) defName = md.group(1).replaceAll(",$", "");
                    // Uses = RHS references (exclude the LHS token itself).
                    String rhs = s.contains("=") ? s.substring(s.indexOf('=') + 1) : s;
                    for (String n : slotRefs(rhs, valueSlots)) {
                        if (!d.contains(n)) u.add(n);   // upward-exposed only
                    }

                    if (defName != null && valueSlots.contains(defName)) d.add(defName);
                }

                useB.put(bb.label(), u);
                defB.put(bb.label(), d);
            }

            Map<String, Set<String>> liveIn  = new HashMap<>();
            Map<String, Set<String>> liveOut = new HashMap<>();
            for (BasicBlock bb : bbs) { liveIn.put(bb.label(), new LinkedHashSet<>());
                                        liveOut.put(bb.label(), new LinkedHashSet<>()); }
            boolean changed = true;
            int guard = 0;
            while (changed && guard++ < 100000) {
                changed = false;
                for (int bi = bbs.size() - 1; bi >= 0; bi--) {
                    String lbl = bbs.get(bi).label();
                    Set<String> out = new LinkedHashSet<>();
                    for (String s : succ.getOrDefault(lbl, List.of()))
                        out.addAll(liveIn.getOrDefault(s, Set.of()));
                    // liveIn = use âˆª (liveOut âˆ’ def)
                    Set<String> in = new LinkedHashSet<>(out);
                    in.removeAll(defB.get(lbl));
                    in.addAll(useB.get(lbl));
                    if (!out.equals(liveOut.get(lbl))) { liveOut.put(lbl, out); changed = true; }
                    if (!in.equals(liveIn.get(lbl)))   { liveIn.put(lbl, in);   changed = true; }
                }
            }

            // Extend each slot's interval to cover every block where it is live-in or
            // live-out (handles back-edges / loop bodies). A value that is live-in to
            // a block is live from that block's start; live-out extends to its end.
            for (BasicBlock bb : bbs) {
                String lbl = bb.label();
                int[] r = blockRange.get(lbl);
                for (String n : liveIn.get(lbl))  firstIdx.merge(n, r[0], Math::min);
                for (String n : liveOut.get(lbl)) lastIdx.merge(n, r[1], Math::max);
            }

            // ---- Reassign offsets --------------------------------------------
            // Rebuild the frame from zero. Pinned slots keep their relative layout
            // first (stable, contiguous), then one shared scratch offset, then the
            // colored value slots.
            Map<String, int[]> newSlots = new LinkedHashMap<>();
            int newFrame = 0;
            // 1. Pinned slots: preserve, packed in their original offset order.
            List<String> pinnedOrdered = new ArrayList<>();
            for (String name : slots.keySet())
                if (pinned.contains(name) && !name.startsWith("__t_")) pinnedOrdered.add(name);
            pinnedOrdered.sort(Comparator.comparingInt(n -> slots.get(n)[0]));
            for (String name : pinnedOrdered) {
                int sz = slots.get(name)[1];
                newSlots.put(name, new int[]{newFrame, sz});
                newFrame += sz; if ((newFrame & 1) != 0) newFrame++;
            }

            // 2. Single shared scratch slot for ALL __t_* tmps (4 bytes, max width).
            if (!scratch.isEmpty()) {
                int scratchOff = newFrame;
                newFrame += 4; if ((newFrame & 1) != 0) newFrame++;
                for (String name : scratch) newSlots.put(name, new int[]{scratchOff, 4});
            }

            // 3. Color value slots: greedy linear-scan over [first,last] intervals.
            //    Prefer exact-size reuse; allow narrower values to reuse wider bins
            //    when intervals do not overlap to avoid frame fragmentation.
            valueSlots.sort(Comparator.comparingInt(n -> firstIdx.getOrDefault(n, 0)));
            // Each physical bin: {offset, size, lastEnd (exclusive high-water)}.
            List<int[]> bins = new ArrayList<>();          // [offset, size, freeAt]
            for (String name : valueSlots) {
                int sz  = slots.get(name)[1];
                int lo  = firstIdx.getOrDefault(name, 0);
                int hi  = lastIdx.getOrDefault(name, lo) + 1;   // exclusive
                int chosen = -1;
                int bestIdx = -1;
                int bestSize = Integer.MAX_VALUE;
                for (int bi = 0; bi < bins.size(); bi++) {
                    int[] bin = bins.get(bi);
                    if (bin[1] >= sz && bin[2] <= lo && bin[1] < bestSize) {
                        bestIdx = bi;
                        bestSize = bin[1];
                    }
                }

                if (bestIdx >= 0) {
                    int[] bin = bins.get(bestIdx);
                    chosen = bin[0];
                    bin[2] = hi;
                }

                if (chosen < 0) {
                    chosen = newFrame;
                    newFrame += sz; if ((newFrame & 1) != 0) newFrame++;
                    bins.add(new int[]{chosen, sz, hi});
                }

                newSlots.put(name, new int[]{chosen, sz});
            }

            // Edge copies are emitted sequentially. If one phi destination aliases a
            // different copy's source, writing it could destroy that source before it
            // is read. Keep own-source coalescing, but relocate cross-copy conflicts to
            // fresh slots so sequential emission retains parallel-copy semantics.
            Pattern pPhi = Pattern.compile("%([^\\s,=]+)\\s*=\\s*phi\\s+(\\S+)\\s+(.*)");
            Map<String, List<String[]>> copiesByPred = new LinkedHashMap<>();
            for (BasicBlock bb : bbs) {
                for (String instr : bb.instrs()) {
                    Matcher matcher = pPhi.matcher(instr.strip());
                    if (!matcher.matches()) continue;
                    String dst = matcher.group(1).replaceAll(",$", "");
                    for (String pair : extractPhiPairs(matcher.group(3))) {
                        int comma = lastTopLevelComma(pair);
                        if (comma < 0) continue;
                        String val = pair.substring(0, comma).trim().replaceAll(",$", "");
                        String pred = pair.substring(comma + 1).trim().replaceFirst("^%", "")
                            .replaceAll("[\\]\\s]+$", "").trim();
                        copiesByPred.computeIfAbsent(pred, key -> new ArrayList<>())
                            .add(new String[]{dst, val});
                    }
                }
            }

            Set<String> relocate = new LinkedHashSet<>();
            for (List<String[]> copies : copiesByPred.values()) {
                for (int i = 0; i < copies.size(); i++) {
                    String dst = copies.get(i)[0];
                    int[] dstSlot = newSlots.get(dst);
                    if (dstSlot == null) continue;
                    for (int j = 0; j < copies.size(); j++) {
                        if (i == j || !copies.get(j)[1].startsWith("%")) continue;
                        int[] srcSlot = newSlots.get(copies.get(j)[1].substring(1));
                        if (srcSlot != null && srcSlot[0] == dstSlot[0]) relocate.add(dst);
                    }
                }
            }

            for (String name : relocate) {
                int size = newSlots.get(name)[1];
                newSlots.put(name, new int[]{newFrame, size});
                newFrame += size;
                if ((newFrame & 1) != 0) newFrame++;
            }

            // Commit.
            slots.clear();
            slots.putAll(newSlots);
            frameSize = newFrame;
        }

        /**
         * True if an alloca slot's address is used in a context other than direct
         * load/store on ptr %slot. Such allocas must keep a stable dedicated slot.
         */
        static boolean allocaEscapes(String name, List<BasicBlock> bbs) {
            String q = Pattern.quote(name);
            Pattern pDef   = Pattern.compile("^%" + q + "\\s*=\\s*alloca\\b.*");
            Pattern pLoad  = Pattern.compile("^%[^=]+=\\s*load\\s+\\S+,\\s*ptr\\s+%" + q + "(?:\\s|,|$).*");
            Pattern pStore = Pattern.compile("^store\\s+\\S+\\s+[^,]+,\\s*ptr\\s+%" + q + "(?:\\s|,|$).*");
            for (BasicBlock bb : bbs) {
                for (String instr : bb.instrs()) {
                    String s = instr.strip();
                    if (!containsSsaToken(s, name)) continue;
                    if (s.startsWith("%" + name) && pDef.matcher(s).matches()) continue;
                    int eq = s.indexOf('=');
                    String rhs = eq >= 0 ? s.substring(eq + 1).strip() : s;
                    if (rhs.startsWith("load ") && pLoad.matcher(s).matches()) continue;
                    if (rhs.startsWith("store ") && pStore.matcher(s).matches()) continue;
                    return true;
                }
            }

            return false;
        }

        /** SSA slot names referenced (as %name) by this instruction that are in the
         *  candidate set. Used for liveness; matches both def (LHS) and uses. */
        static Set<String> slotRefs(String instr, Collection<String> candidates) {
            if (candidates.isEmpty()) return Set.of();
            Set<String> refs = new LinkedHashSet<>();
            Matcher m = Pattern.compile("%([^\\s,=()\\[\\]]+)").matcher(instr);
            while (m.find()) {
                String n = m.group(1).replaceAll(",$", "");
                if (candidates.contains(n)) refs.add(n);
            }

            return refs;
        }

        /** Successor block labels named by a block's terminator (br/switch). */
        static List<String> successorsOf(BasicBlock bb) {
            List<String> instrs = bb.instrs();
            if (instrs.isEmpty()) return List.of();
            List<String> succ = new ArrayList<>();
            for (String instr : instrs) {
                String s = instr.strip();
                if (!isTerminator(s)) continue;
                Matcher m = Pattern.compile("label\\s+%([\\w.]+)").matcher(s);
                while (m.find()) succ.add(m.group(1));
            }

            return succ;
        }

        // -- body parser --

        record BasicBlock(String label, List<String> instrs) {}
        // A phi incoming value that must be copied into the phi's slot at the end
        // of the named predecessor block (SSA phi -> predecessor copy lowering).

        record PhiCopy(String targetLabel, String dstName, String type, String val) {}
        // predecessor block label -> phi copies to perform before that block's
        // terminator. Built by scanPhis(), consumed in translate().
        Map<String, List<PhiCopy>> phiCopiesByPred = null;

        /** Scan all phi nodes and record, per predecessor block, the copies needed. */
        void scanPhis() {
            phiCopiesByPred = new LinkedHashMap<>();
            Pattern pPhi = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*phi\\s+(\\S+)\\s+(.*)");
            for (BasicBlock bb : parseBody()) {
                for (String instr : bb.instrs()) {
                    Matcher m = pPhi.matcher(instr.strip());
                    if (!m.matches()) continue;
                    String dst  = m.group(1).replaceAll(",$", "");
                    String type = m.group(2);
                    // Each pair is "[ <value>, %<pred> ]" where <value> may itself
                    // contain commas/parens (e.g. inttoptr (i64 N to ptr)). Extract
                    // bracket groups honoring nested parens, then split each on its
                    // LAST top-level comma to separate the value from the predecessor.
                    for (String pair : extractPhiPairs(m.group(3))) {
                        int comma = lastTopLevelComma(pair);
                        if (comma < 0) continue;
                        String val  = pair.substring(0, comma).trim().replaceAll(",$", "");
                        String pred = pair.substring(comma + 1).trim();
                        if (pred.startsWith("%")) pred = pred.substring(1);
                        pred = pred.replaceAll("[\\]\\s]+$", "").trim();
                        phiCopiesByPred
                            .computeIfAbsent(pred, k -> new ArrayList<>())
                            .add(new PhiCopy(bb.label(), dst, type, val));
                    }
                }
            }
        }

        /** Split a phi operand list into the inner text of each "[ ... ]" group,
         *  honoring nested parens/brackets. */
        static List<String> extractPhiPairs(String s) {
            List<String> out = new ArrayList<>();
            int depth = 0, start = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '[') { if (depth == 0) start = i + 1; depth++; }
                else if (c == ']') { depth--; if (depth == 0 && start >= 0) { out.add(s.substring(start, i)); start = -1; } }
                else if (c == '(') depth++;
                else if (c == ')') depth--;
            }

            return out;
        }

        /** Index of the last comma at paren/bracket depth 0 in s, or -1. */
        static int lastTopLevelComma(String s) {
            int depth = 0, idx = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '(' || c == '[') depth++;
                else if (c == ')' || c == ']') depth--;
                else if (c == ',' && depth == 0) idx = i;
            }

            return idx;
        }

        /** Emit the phi copies registered for the given predecessor block, if any.
         *  Copies are grouped by successor target and each group is lowered as an
         *  independent parallel copy (a phi edge is a simultaneous assignment). */
        void emitPhiCopies(String predLabel, Map<String, int[]> argOffsets) {
            List<PhiCopy> copies = phiCopiesByPred == null ? null
                                 : phiCopiesByPred.get(predLabel);
            if (copies == null || copies.isEmpty()) return;
            emitComment("phi copies for block " + predLabel);
            // Preserve first-seen target order so hazard-free lists emit verbatim.
            java.util.LinkedHashMap<String, List<PhiCopy>> byTarget = new java.util.LinkedHashMap<>();
            for (PhiCopy c : copies) byTarget.computeIfAbsent(c.targetLabel(), k -> new ArrayList<>()).add(c);
            for (List<PhiCopy> group : byTarget.values()) {
                emitParallelPhiCopies(group, argOffsets);
            }
        }

        void emitPhiCopy(PhiCopy copy, Map<String, int[]> argOffsets) {
            String val = copy.val().trim().replaceAll(",$", "");
            if (val.startsWith("%")) {
                String src = val.substring(1);
                int[] srcSlot = slots.get(src);
                int[] dstSlot = slots.get(copy.dstName());
                if (srcSlot != null && dstSlot != null && srcSlot[0] == dstSlot[0]) {
                    if (src.equals(waSlotName)) waSlotName = copy.dstName();
                    return;
                }
            }

            loadToWA(copy.val(), copy.type(), argOffsets);
            storeWAtoSlot(copy.dstName(), Math.min(irTypeBits(copy.type()), 32));
        }

        /** Physical destination location of a phi copy target name:
         *  "REG:<family>" if promoted, "SLOT:<off>" if it has a stack slot, else null.
         *  regOnly names have no stable phi-edge home and are treated as null. */
        String phiCopyLoc(String name) {
            if (name.equals(bcSlotName)) return "REG:bc";
            if (name.equals(deSlotName)) return "REG:de";
            if (name.equals(ixSlotName)) return "REG:ix";
            if (name.equals(iySlotName)) return "REG:iy";
            if (name.equals(izSlotName)) return "REG:iz";
            for (int i = 0; i < BANK_REG_KEYS.length; i++) {
                if (name.equals(bankSlotName[i])) return "REG:" + BANK_REG_KEYS[i];
            }
            int[] s = slots.get(name);
            if (s != null) return "SLOT:" + s[0];
            return null;
        }

        /** Physical source location of a phi copy value (the SSA name it reads from),
         *  or null for immediates / globals / args / regOnly (pure sources — never a dst). */
        String phiCopySrcLoc(String val) {
            String v = val.trim().replaceAll(",$", "");
            if (!v.startsWith("%")) return null;   // immediate / global / inttoptr
            return phiCopyLoc(v.substring(1));
        }

        /** Lower one phi EDGE's copies as a simultaneous (parallel) assignment.
         *  A phi edge assigns all its copies at once, so no copy may clobber a
         *  location another copy still needs to read. We sequentialise by:
         *   1. dropping self-copies (src loc == dst loc),
         *   2. order-stable topological emit (emit a copy only when its dst is not a
         *      still-pending source — on a hazard-free list this is exactly list order,
         *      so strict-gate output is byte-identical to the old linear emitter),
         *   3. breaking any residual cycles via a scratch stack slot.
         *  The incoming WA-residency (waSlotName) is preserved so a first copy whose
         *  source is already in WA still skips its reload exactly as the old emitter
         *  did. Residency is invalidated only inside the cycle-break path, where the
         *  scratch save/restore must not be defeated by the "already in WA" fast path. */
        void emitParallelPhiCopies(List<PhiCopy> copies, Map<String, int[]> argOffsets) {
            if (copies == null || copies.isEmpty()) return;

            // Build the working set, dropping copies whose src and dst resolve to the
            // same physical location (nothing to move). Null dst loc (regOnly/unresolved)
            // is emitted as-is via emitPhiCopy, which handles it.
            List<PhiCopy> pending = new ArrayList<>();
            for (PhiCopy c : copies) {
                String dl = phiCopyLoc(c.dstName());
                String sl = phiCopySrcLoc(c.val());
                if (dl != null && dl.equals(sl)) {
                    // pure self-move: preserve the old waSlotName rename so a later
                    // consumer that expected the value under the dst name still resolves.
                    String src = c.val().trim().replaceAll(",$", "");
                    if (src.startsWith("%") && src.substring(1).equals(waSlotName)) waSlotName = c.dstName();
                    continue;
                }
                pending.add(c);
            }
            if (pending.isEmpty()) return;

            // srcCount[loc] = number of still-pending copies that READ from loc.
            java.util.Map<String, Integer> srcCount = new java.util.HashMap<>();
            for (PhiCopy c : pending) {
                String sl = phiCopySrcLoc(c.val());
                if (sl != null) srcCount.merge(sl, 1, Integer::sum);
            }

            // Order-stable topological drain: repeatedly emit, in list order, every copy
            // whose destination is not currently a pending source. Emitting a copy stops
            // it reading its source (decrement srcCount), which may unblock others.
            // No WA invalidation here: emitPhiCopy maintains waSlotName correctly and on a
            // hazard-free list the emit order equals list order, so the fast path fires
            // identically to the old linear emitter (byte-identical output).
            java.util.List<PhiCopy> work = new ArrayList<>(pending);
            boolean progress = true;
            while (!work.isEmpty() && progress) {
                progress = false;
                java.util.Iterator<PhiCopy> it = work.iterator();
                while (it.hasNext()) {
                    PhiCopy c = it.next();
                    String dl = phiCopyLoc(c.dstName());
                    // Safe to emit if writing dl clobbers nothing still needed.
                    if (dl == null || srcCount.getOrDefault(dl, 0) == 0) {
                        emitPhiCopy(c, argOffsets);
                        String sl = phiCopySrcLoc(c.val());
                        if (sl != null) srcCount.merge(sl, -1, Integer::sum);
                        it.remove();
                        progress = true;
                    }
                }
            }

            // Any copies left form disjoint cycles (each dst is still someone's source).
            // Break each cycle: park one member's source value in a scratch slot, emit the
            // rest of the cycle normally, then satisfy the parked edge from the scratch.
            // Here the WA fast path MUST be defeated, so invalidate around every step.
            while (!work.isEmpty()) {
                PhiCopy start = work.remove(0);
                int scratchOff = tmpSlot("phi_cycle");
                waSlotName = null; waSlotBits = 0;
                // Save start's SOURCE value into the scratch slot.
                loadToWA(start.val(), start.type(), argOffsets);
                int startBits = Math.min(irTypeBits(start.type()), 32);
                if (startBits <= 8)       emit("ld (xsp+" + scratchOff + "), a");
                else if (startBits <= 16) emit("ld (xsp+" + scratchOff + "), wa");
                else                      emit("ld (xsp+" + scratchOff + "), xwa");
                waSlotName = null; waSlotBits = 0;
                String startSrc = phiCopySrcLoc(start.val());
                if (startSrc != null) srcCount.merge(startSrc, -1, Integer::sum);

                // Drain the rest of this cycle in dependency order (now that start's
                // read is satisfied from the scratch copy, the cycle is a chain).
                boolean prog = true;
                while (prog) {
                    prog = false;
                    java.util.Iterator<PhiCopy> it = work.iterator();
                    while (it.hasNext()) {
                        PhiCopy c = it.next();
                        String dl = phiCopyLoc(c.dstName());
                        if (dl == null || srcCount.getOrDefault(dl, 0) == 0) {
                            emitPhiCopy(c, argOffsets);
                            waSlotName = null; waSlotBits = 0;
                            String sl = phiCopySrcLoc(c.val());
                            if (sl != null) srcCount.merge(sl, -1, Integer::sum);
                            it.remove();
                            prog = true;
                        }
                    }
                }

                // Finally write start's destination from the scratch value.
                if (startBits <= 8)       { emit("ld wa, 0"); emit("ld a, (xsp+" + scratchOff + ")"); }
                else if (startBits <= 16)   emit("ld wa, (xsp+" + scratchOff + ")");
                else                        emit("ld xwa, (xsp+" + scratchOff + ")");
                waSlotName = null; waSlotBits = 0;
                storeWAtoSlot(start.dstName(), startBits);
                waSlotName = null; waSlotBits = 0;
            }
        }

        List<PhiCopy> phiCopiesForEdge(String predLabel, String targetLabel) {
            List<PhiCopy> copies = phiCopiesByPred == null ? null
                                 : phiCopiesByPred.get(predLabel);
            if (copies == null || copies.isEmpty()) return List.of();
            return copies.stream()
                .filter(copy -> copy.targetLabel().equals(targetLabel))
                .toList();
        }

        void emitPhiCopiesForEdge(String predLabel, String targetLabel,
                                  List<PhiCopy> copies, Map<String, int[]> argOffsets) {
            if (copies.isEmpty()) return;
            emitComment("phi copies for edge " + predLabel + " -> " + targetLabel);
            emitParallelPhiCopies(copies, argOffsets);
        }

        /** True if this stripped instruction is a block terminator (ends the block). */
        static boolean isTerminator(String s) {
            return s.startsWith("br ") || s.equals("br")
                || s.startsWith("switch ")
                || s.startsWith("ret ") || s.equals("ret") || s.equals("ret void")
                || s.equals("unreachable");
        }

        List<BasicBlock> parseBody() {
            if (parsedBlocks != null) return parsedBlocks;
            List<BasicBlock> blocks = new ArrayList<>();
            String curLabel = computeEntryLabel();
            List<String> curInstrs = new ArrayList<>();
            boolean inFunc = false;
            for (String line : func.body()) {
                String s = line.strip();
                if (s.startsWith("define ") && s.endsWith("{")) { inFunc = true; continue; }
                if (s.equals("{")) { inFunc = true; continue; }
                if (s.equals("}")) {
                    if (!curInstrs.isEmpty()) blocks.add(new BasicBlock(curLabel, curInstrs));
                    break;
                }

                if (!inFunc || s.isEmpty() || s.startsWith(";")) continue;
                // Named block label: "name:"
                Matcher mLbl = Pattern.compile("^([A-Za-z_][\\w.]*):.*$").matcher(s);
                if (mLbl.matches()) {
                    if (!curInstrs.isEmpty()) blocks.add(new BasicBlock(curLabel, curInstrs));
                    curLabel = mLbl.group(1);
                    curInstrs = new ArrayList<>();
                    continue;
                }

                // Numeric block label: "N:"
                Matcher mNum = Pattern.compile("^(\\d+):\\s*(?:;.*)?$").matcher(s);
                if (mNum.matches()) {
                    if (!curInstrs.isEmpty()) blocks.add(new BasicBlock(curLabel, curInstrs));
                    curLabel = mNum.group(1);
                    curInstrs = new ArrayList<>();
                    continue;
                }

                // strip trailing loop metadata
                s = s.replaceAll(",?\\s*!llvm\\.\\S+\\s+!\\d+\\s*$", "").strip();
                if (!s.isEmpty()) curInstrs.add(s);
            }

            parsedBlocks = blocks;
            return blocks;
        }

        // LLVM gives the entry block an implicit number (no "N:" label line). When a
        // phi lists the entry block as a predecessor (common under -O3, which merges
        // paths that feed values from entry), that number must match the label we use
        // for the entry block internally â€” otherwise emitPhiCopies(entry) finds nothing
        // and the phi destination is left uninitialized.
        //
        // The entry number is the one referenced by branches/phi-preds/`preds =`
        // comments but never DEFINED by a "N:" label line. Fall back to "0".
        String cachedEntryLabel = null;
        String computeEntryLabel() {
            if (cachedEntryLabel != null) return cachedEntryLabel;
            Set<String> defined = new LinkedHashSet<>();
            Set<String> referenced = new LinkedHashSet<>();
            boolean inFunc = false;
            for (String line : func.body()) {
                String s = line.strip();
                if (s.startsWith("define ") && s.endsWith("{")) { inFunc = true; continue; }
                if (s.equals("{")) { inFunc = true; continue; }
                if (s.equals("}")) break;
                if (!inFunc) continue;
                Matcher mNum = Pattern.compile("^(\\d+):.*$").matcher(s);
                if (mNum.matches()) { defined.add(mNum.group(1)); continue; }
                // Branch / switch targets: "label %N"
                Matcher mt = Pattern.compile("label\\s+%(\\d+)").matcher(s);
                while (mt.find()) referenced.add(mt.group(1));
                // phi predecessors: "[ val, %N ]"
                if (s.contains("= phi")) {
                    Matcher mp = Pattern.compile(",\\s*%(\\d+)\\s*\\]").matcher(s);
                    while (mp.find()) referenced.add(mp.group(1));
                }

                // "preds = %N, %M" comments
                Matcher mc = Pattern.compile("preds\\s*=\\s*(.*)$").matcher(s);
                if (mc.find()) {
                    Matcher mn = Pattern.compile("%(\\d+)").matcher(mc.group(1));
                    while (mn.find()) referenced.add(mn.group(1));
                }
            }

            referenced.removeAll(defined);
            String entry = "0";
            long best = Long.MAX_VALUE;
            for (String r : referenced) {
                try { long v = Long.parseLong(r); if (v < best) { best = v; entry = r; } }
                catch (NumberFormatException ignore) {}
            }

            cachedEntryLabel = entry;
            return entry;
        }

        // -- pre-pass: discover all frame slots --
        // SSA names that can live purely in registers â€” defined once and used
        // exactly once by the immediately following instruction in the same BB,
        // never referenced across a block boundary or by a phi.
        // These never need a stack slot: storeWAtoSlot/loadToWA skip them.
        final Set<String> regOnly = new LinkedHashSet<>();
        // GEP results whose address lands in XHL and is consumed only by the
        // immediately following load/store instruction in the same BB.
        // These never need a stack slot: emitGEP sets xhlSlotName instead of
        // spilling, and loadToXHL skips the reload when xhlSlotName matches.
        final Set<String> xhlOnly = new LinkedHashSet<>();
        int mem2rPhiSerial = 0;

        /**
         * Generic mem2reg-style scalar promotion for non-escaping allocas.
         *
         * Works across CFG (including loops/branches) for scalar allocas used only
         * by direct load/store ptr %slot. Inserts phi nodes at merge points when
         * incoming values differ, then removes alloca/load/store for promoted slots.
         */
        void scalarizeLocalAllocas() {
            if (isRawAsm) return;
            List<BasicBlock> bbs = parseBody();
            if (bbs.isEmpty()) return;
            Map<String, List<String>> succ = new LinkedHashMap<>();
            Map<String, List<String>> preds = new LinkedHashMap<>();
            for (BasicBlock bb : bbs) {
                succ.put(bb.label(), new ArrayList<>(successorsOf(bb)));
                preds.putIfAbsent(bb.label(), new ArrayList<>());
            }

            for (Map.Entry<String, List<String>> e : succ.entrySet()) {
                String from = e.getKey();
                for (String to : e.getValue()) {
                    preds.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
                }
            }

            Pattern pAlloca = Pattern.compile("^%([^\\s,=]+)\\s*=\\s*alloca\\s+(\\S+).*$");
            Pattern pLoad = Pattern.compile("^%([^\\s,=]+)\\s*=\\s*load\\s+(?:volatile\\s+)?(\\S+),\\s*ptr\\s+%([^\\s,]+).*$");
            Pattern pStore = Pattern.compile("^store\\s+(?:volatile\\s+)?(\\S+)\\s+(.+?),\\s*ptr\\s+%([^\\s,]+).*$");
            boolean progress = true;
            Set<String> attempted = new HashSet<>();
            while (progress) {
                progress = false;
                // Discover alloca slots + declared type.
                Map<String, String> allocaType = new LinkedHashMap<>();
                for (BasicBlock bb : bbs) {
                    for (String instr : bb.instrs()) {
                        Matcher ma = pAlloca.matcher(instr.strip());
                        if (ma.find()) {
                            allocaType.put(ma.group(1).replaceAll(",$", ""), normIrType(ma.group(2)));
                        }
                    }
                }

                if (allocaType.isEmpty()) break;
                // Keep only safe scalar candidates.
                Map<String, String> candidates = new LinkedHashMap<>();
                for (Map.Entry<String, String> e : allocaType.entrySet()) {
                    String slot = e.getKey();
                    if (attempted.contains(slot)) continue;
                    String declTy = e.getValue();
                    if (allocaEscapes(slot, bbs)) continue;
                    int bits = irTypeBits(declTy);
                    if (bits > 32) continue;
                    boolean ok = true;
                    for (BasicBlock bb : bbs) {
                        for (String instr : bb.instrs()) {
                            String s = instr.strip();
                            if (!containsSsaToken(s, slot)) continue;
                            Matcher ma = pAlloca.matcher(s);
                            if (ma.matches() && ma.group(1).replaceAll(",$", "").equals(slot)) continue;
                            if (s.startsWith("load volatile ") || s.startsWith("store volatile ")) {
                                ok = false; break;
                            }

                            Matcher ml = pLoad.matcher(s);
                            if (ml.matches() && ml.group(3).replaceAll(",$", "").equals(slot)) {
                                if (!normIrType(ml.group(2)).equals(declTy)) { ok = false; break; }
                                continue;
                            }

                            Matcher ms = pStore.matcher(s);
                            if (ms.matches() && ms.group(3).replaceAll(",$", "").equals(slot)) {
                                if (!normIrType(ms.group(1)).equals(declTy)) { ok = false; break; }
                                continue;
                            }

                            ok = false;
                            break;
                        }

                        if (!ok) break;
                    }

                    if (ok) candidates.put(slot, declTy);
                }

                if (candidates.isEmpty()) break;
                for (Map.Entry<String, String> ce : candidates.entrySet()) {
                    String slot = ce.getKey();
                    String slotType = ce.getValue();
                    attempted.add(slot);
                    if (!promoteAllocaAcrossCfg(slot, slotType, bbs, preds, pAlloca, pLoad, pStore)) {
                        continue;
                    }

                    progress = true;
                }

                if (progress) cleanupDeadM2rDefs(bbs);
            }
        }

        /** Drop now-unused synthetic mem2reg phi defs created as %__m2r_* names. */
        void cleanupDeadM2rDefs(List<BasicBlock> bbs) {
            Pattern pDef = Pattern.compile("^%([^\\s,=]+)\\s*=");
            Pattern pUse = Pattern.compile("%([^\\s,=()\\[\\]]+)");
            boolean changed = true;
            int guard = 0;
            while (changed && guard++ < 100000) {
                changed = false;
                Map<String, Integer> uses = new HashMap<>();
                for (BasicBlock bb : bbs) {
                    for (String instr : bb.instrs()) {
                        String rhs = instr.contains("=")
                            ? instr.substring(instr.indexOf('=') + 1)
                            : instr;
                        Matcher mu = pUse.matcher(rhs);
                        while (mu.find()) uses.merge(mu.group(1), 1, Integer::sum);
                    }
                }

                for (BasicBlock bb : bbs) {
                    List<String> next = new ArrayList<>();
                    for (String instr : bb.instrs()) {
                        String s = instr.strip();
                        Matcher md = pDef.matcher(s);
                        if (md.find()) {
                            String n = md.group(1);
                            if (n.startsWith("__m2r_") && uses.getOrDefault(n, 0) == 0) {
                                changed = true;
                                continue;
                            }
                        }

                        next.add(instr);
                    }

                    bb.instrs().clear();
                    bb.instrs().addAll(next);
                }
            }
        }

        boolean promoteAllocaAcrossCfg(
                String slot,
                String slotType,
                List<BasicBlock> bbs,
                Map<String, List<String>> preds,
                Pattern pAlloca,
                Pattern pLoad,
                Pattern pStore) {
            if (bbs.isEmpty()) return false;
            final String V_UNDEF = "#m2r_undef";
            final String V_MIXED = "#m2r_mixed";
            Map<String, String> inVal = new LinkedHashMap<>();
            Map<String, String> outVal = new LinkedHashMap<>();
            Map<String, String> phiByBlock = new LinkedHashMap<>();
            String entry = bbs.get(0).label();
            inVal.put(entry, V_UNDEF);
            boolean changed = true;
            int guard = 0;
            while (changed && guard++ < 100000) {
                changed = false;
                for (BasicBlock bb : bbs) {
                    String lbl = bb.label();
                    String merged;
                    if (lbl.equals(entry)) {
                        merged = V_UNDEF;
                    } else {
                        List<String> ps = preds.getOrDefault(lbl, List.of());
                        merged = null;
                        boolean any = false;
                        for (String p : ps) {
                            String pv = outVal.get(p);
                            if (pv == null) continue;
                            any = true;
                            merged = meetMem2rValue(merged, pv, lbl, phiByBlock, slot, V_UNDEF, V_MIXED);
                        }

                        if (!any) merged = V_UNDEF;
                    }

                    if (!Objects.equals(inVal.get(lbl), merged)) {
                        inVal.put(lbl, merged);
                        changed = true;
                    }

                    String cur = merged == null ? V_UNDEF : merged;
                    Map<String, String> alias = new LinkedHashMap<>();
                    for (String raw : bb.instrs()) {
                        String line = substituteAliases(raw, alias);
                        String s = line.strip();
                        int eq = s.indexOf('=');
                        String rhs = eq >= 0 ? s.substring(eq + 1).strip() : s;
                        if (rhs.startsWith("load ")) {
                            Matcher ml = pLoad.matcher(s);
                            if (ml.matches() && ml.group(3).replaceAll(",$", "").equals(slot)) {
                                String dstTok = "%" + ml.group(1).replaceAll(",$", "");
                                if (isMem2rUnknown(cur, V_UNDEF, V_MIXED)) {
                                    // Unknown memory on some incoming paths: keep this load
                                    // in rewrite mode and use its SSA result as the new value.
                                    cur = dstTok;
                                } else {
                                    alias.put(dstTok, resolveAliasToken(cur, alias));
                                }

                                continue;
                            }
                        }

                        if (rhs.startsWith("store ")) {
                            Matcher ms = pStore.matcher(s);
                            if (ms.matches() && ms.group(3).replaceAll(",$", "").equals(slot)) {
                                cur = resolveAliasToken(ms.group(2).trim().replaceAll(",$", ""), alias);
                                continue;
                            }
                        }
                    }

                    if (!Objects.equals(outVal.get(lbl), cur)) {
                        outVal.put(lbl, cur);
                        changed = true;
                    }
                }
            }

            // Partial mode: if any load may see unknown incoming memory, keep
            // alloca+stores and only forward known loads.
            boolean partialMode = false;
            for (BasicBlock bb : bbs) {
                String cur = inVal.getOrDefault(bb.label(), V_UNDEF);
                Map<String, String> alias = new LinkedHashMap<>();
                for (String raw : bb.instrs()) {
                    String line = substituteAliases(raw, alias);
                    String s = line.strip();
                    int eq = s.indexOf('=');
                    String rhs = eq >= 0 ? s.substring(eq + 1).strip() : s;
                    if (rhs.startsWith("load ")) {
                        Matcher ml = pLoad.matcher(s);
                        if (ml.matches() && ml.group(3).replaceAll(",$", "").equals(slot)) {
                            String dstTok = "%" + ml.group(1).replaceAll(",$", "");
                            if (isMem2rUnknown(cur, V_UNDEF, V_MIXED)) {
                                partialMode = true;
                                cur = dstTok;
                            } else {
                                alias.put(dstTok, resolveAliasToken(cur, alias));
                            }

                            continue;
                        }
                    }

                    if (rhs.startsWith("store ")) {
                        Matcher ms = pStore.matcher(s);
                        if (ms.matches() && ms.group(3).replaceAll(",$", "").equals(slot)) {
                            cur = resolveAliasToken(ms.group(2).trim().replaceAll(",$", ""), alias);
                        }
                    }
                }
            }

            boolean fullMode = !partialMode;
            // Rewrite blocks and materialize phi nodes where needed.
            boolean changedAny = false;
            for (BasicBlock bb : bbs) {
                String lbl = bb.label();
                String cur = inVal.getOrDefault(lbl, V_UNDEF);
                Map<String, String> alias = new LinkedHashMap<>();
                List<String> rewritten = new ArrayList<>();
                String phiName = phiByBlock.get(lbl);
                if (phiName != null && Objects.equals(cur, phiName)) {
                    List<String> incoming = new ArrayList<>();
                    for (String p : preds.getOrDefault(lbl, List.of())) {
                        String pv = outVal.get(p);
                        if (pv == null || isMem2rUnknown(pv, V_UNDEF, V_MIXED)) {
                            return false;
                        }

                        incoming.add("[ " + pv + ", %" + p + " ]");
                    }

                    if (incoming.isEmpty()) return false;
                    rewritten.add(phiName + " = phi " + slotType + " " + String.join(", ", incoming));
                    changedAny = true;
                }

                for (String raw : bb.instrs()) {
                    String line = substituteAliases(raw, alias);
                    String s = line.strip();
                    int eq = s.indexOf('=');
                    String rhs = eq >= 0 ? s.substring(eq + 1).strip() : s;
                    if (rhs.startsWith("alloca ")) {
                        Matcher ma = pAlloca.matcher(s);
                        if (ma.matches() && ma.group(1).replaceAll(",$", "").equals(slot)) {
                            if (fullMode) {
                                changedAny = true;
                                continue;
                            }

                            rewritten.add(line);
                            continue;
                        }
                    }

                    if (rhs.startsWith("load ")) {
                        Matcher ml = pLoad.matcher(s);
                        if (ml.matches() && ml.group(3).replaceAll(",$", "").equals(slot)) {
                            String dstTok = "%" + ml.group(1).replaceAll(",$", "");
                            if (isMem2rUnknown(cur, V_UNDEF, V_MIXED)) {
                                rewritten.add(line);
                                cur = dstTok;
                            } else {
                                alias.put(dstTok, resolveAliasToken(cur, alias));
                                changedAny = true;
                            }

                            continue;
                        }
                    }

                    if (rhs.startsWith("store ")) {
                        Matcher ms = pStore.matcher(s);
                        if (ms.matches() && ms.group(3).replaceAll(",$", "").equals(slot)) {
                            cur = resolveAliasToken(ms.group(2).trim().replaceAll(",$", ""), alias);
                            if (fullMode) {
                                changedAny = true;
                                continue;
                            }

                            rewritten.add(line);
                            continue;
                        }
                    }

                    rewritten.add(line);
                }

                bb.instrs().clear();
                bb.instrs().addAll(rewritten);
            }

            return changedAny;
        }

        static boolean isMem2rUnknown(String v, String undef, String mixed) {
            return v == null || undef.equals(v) || mixed.equals(v);
        }

        String meetMem2rValue(
                String a,
                String b,
                String blockLabel,
                Map<String, String> phiByBlock,
                String slot,
                String undef,
                String mixed) {
            if (a == null) return b;
            if (b == null) return a;
            if (a.equals(b)) return a;
            if (mixed.equals(a) || mixed.equals(b)) return mixed;
            if (undef.equals(a) && undef.equals(b)) return undef;
            if (undef.equals(a) || undef.equals(b)) return mixed;
            String phi = phiByBlock.get(blockLabel);
            if (phi == null) {
                phi = "%__m2r_" + slot + "_" + blockLabel + "_" + (mem2rPhiSerial++);
                phiByBlock.put(blockLabel, phi);
            }

            return phi;
        }

        static boolean containsSsaToken(String text, String name) {
            String token = "%" + name;
            int from = 0;
            while (true) {
                int at = text.indexOf(token, from);
                if (at < 0) return false;
                int end = at + token.length();
                boolean leftBoundary = at == 0 || !isSsaNameChar(text.charAt(at - 1));
                boolean rightBoundary = end == text.length() || !isSsaNameChar(text.charAt(end));
                if (leftBoundary && rightBoundary) return true;
                from = at + 1;
            }
        }

        static boolean isSsaNameChar(char c) {
            return c == '.' || c == '_' || c >= '0' && c <= '9'
                || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
        }

        static String normIrType(String t) {
            return stripAttrs(t).replaceAll(",$", "").trim();
        }

        static String resolveAliasToken(String tok, Map<String, String> alias) {
            String cur = tok.trim();
            Set<String> seen = new HashSet<>();
            while (alias.containsKey(cur) && seen.add(cur)) cur = alias.get(cur);
            return cur;
        }

        static String substituteAliases(String line, Map<String, String> alias) {
            if (alias.isEmpty() || !line.contains("%")) return line;
            String head = "";
            String tail = line;
            Matcher mDef = Pattern.compile("^(\\s*%[^=]+?=)(.*)$").matcher(line);
            if (mDef.matches()) {
                head = mDef.group(1);
                tail = mDef.group(2);
            }

            List<String> keys = new ArrayList<>(alias.keySet());
            keys.sort((a, b) -> Integer.compare(b.length(), a.length()));
            for (String k : keys) {
                tail = tail.replaceAll(Pattern.quote(k) + "(?![\\w.])",
                    Matcher.quoteReplacement(alias.get(k)));
            }

            return head + tail;
        }

        // -----------------------------------------------------------------------
        // Multi-pass slot allocation pipeline

        // -----------------------------------------------------------------------
        // Pass 1: analyse values â€” use counts, regOnly/xhlOnly/aResident classification,
        // access counts for promotion ranking. Does NOT allocate any slots.
        void analyzeValues() {
            // Scan for alloca instructions BEFORE scalarization, since
            // scalarizeLocalAllocas() removes successfully-promoted alloca instructions
            // from the body. We need their names and sizes for promotion decisions.
            for (String raw : func.body()) {
                String s = raw.strip();
                Matcher ma = Pattern.compile("%([^\\s,=]+)\\s*=\\s*alloca\\s+(\\S+)").matcher(s);
                if (ma.find()) {
                    String n = ma.group(1).replaceAll(",$", "");
                    String t = ma.group(2).replaceAll(",$", "");
                    int sz = Math.max(irTypeBytes(t), 2);
                    originalAllocaNames.add(n);
                    valueSizeBytes.put(n, sz);
                }
            }

            scalarizeLocalAllocas();
            // Count all uses of each SSA name across the whole function.
            Map<String, Integer> useCount = new HashMap<>();
            Map<String, Integer> defBlock = new HashMap<>();
            Map<String, Integer> defPos   = new HashMap<>();
            List<BasicBlock> bbs = parseBody();
            for (int bi = 0; bi < bbs.size(); bi++) {
                List<String> instrs = bbs.get(bi).instrs();
                for (int ii = 0; ii < instrs.size(); ii++) {
                    String instr = instrs.get(ii);
                    Matcher mDef = Pattern.compile("^\\s*%([^\\s,=]+)\\s*=").matcher(instr);
                    if (mDef.find()) { defBlock.put(mDef.group(1), bi); defPos.put(mDef.group(1), ii); }
                    String rhs = instr.contains("=") ? instr.substring(instr.indexOf('=') + 1) : instr;
                    Matcher mUse = Pattern.compile("%([^\\s,=()]+)").matcher(rhs);
                    while (mUse.find()) useCount.merge(mUse.group(1), 1, Integer::sum);
                }
            }

            Set<String> phiUsed = new HashSet<>();
            for (BasicBlock bb : bbs) {
                for (String instr : bb.instrs()) {
                    if (instr.contains("= phi")) {
                        Matcher mUse = Pattern.compile("%([^\\s,=()\\[\\]]+)").matcher(instr);
                        while (mUse.find()) phiUsed.add(mUse.group(1));
                    }
                }
            }

            Pattern pCallArgs = Pattern.compile("\\bcall\\b.*@[^\\s(]+\\((.*)\\)");
            for (BasicBlock bb : bbs) {
                for (String instr : bb.instrs()) {
                    Matcher mc = pCallArgs.matcher(instr);
                    if (!mc.find()) continue;
                    for (Arg a : parseCallArgs(mc.group(1))) {
                        String av = a.val().trim().replaceAll(",$", "");
                        if (av.startsWith("%")) callArgValues.add(av.substring(1));
                    }
                }
            }

            Set<Integer> loopBlockIndexes = naturalLoopBlockIndexes(bbs);
            int[] loopDepth = null;
            // Identify xhlOnly: GEP/ptr-load results consumed only by the immediately
            // following load/store in the same BB.
            for (int bi = 0; bi < bbs.size(); bi++) {
                List<String> instrs = bbs.get(bi).instrs();
                for (int ii = 0; ii < instrs.size(); ii++) {
                    String instr = instrs.get(ii);
                    Matcher mPtrDef = Pattern.compile(
                        "^%([^\\s,=]+)\\s*=\\s*(?:getelementptr\\b|load\\s+ptr\\s*,)").matcher(instr);
                    if (!mPtrDef.find()) continue;
                    String n = mPtrDef.group(1);
                    if (useCount.getOrDefault(n, 0) == 1
                            && !phiUsed.contains(n)
                            && bi == defBlock.getOrDefault(n, -1)
                            && ii + 1 < instrs.size()) {
                        String consumer = instrs.get(ii + 1);
                        boolean isLoadPtr  = consumer.matches(".*load\\s+\\S+,\\s*ptr\\s+%" + n + "\\b.*");
                        boolean isStorePtr = consumer.matches(".*store\\s+\\S+\\s+[^,]+,\\s*ptr\\s+%" + n + "\\b.*");
                        if (isLoadPtr || isStorePtr) {
                            xhlOnly.add(n);
                        }
                    }
                }
            }

            // Identify A-resident values (no slot needed).
            Set<String> aResident = new LinkedHashSet<>();
            for (int bi = 0; bi < bbs.size(); bi++) {
                List<String> instrs = bbs.get(bi).instrs();
                for (int ii = 0; ii < instrs.size(); ii++) {
                    Matcher md = Pattern.compile("^\\s*%([^\\s,=]+)\\s*=\\s*(.*)").matcher(instrs.get(ii));
                    if (!md.find()) continue;
                    String n = md.group(1).replaceAll(",$", "");
                    String rhs = md.group(2);
                    if (rhs.startsWith("getelementptr") || rhs.startsWith("call ")
                            || rhs.contains(" call ") || rhs.startsWith("phi")
                            || rhs.startsWith("alloca")) continue;
                    if (phiUsed.contains(n)) continue;
                    int uses = useCount.getOrDefault(n, 0);
                    if (uses < 2) continue;
                    boolean ok = true;
                    int seen = 0;
                    for (int k = ii + 1; k < instrs.size() && seen < uses; k++) {
                        String consumer = instrs.get(k);
                        boolean usesN = consumer.matches(".*%" + Pattern.quote(n) + "(?![\\w.]).*");
                        if (!usesN) { ok = false; break; }
                        if (consumer.contains("getelementptr")) { ok = false; break; }
                        seen++;
                        boolean isLast = (seen == uses);
                        if (!isLast) {
                            String ct = consumer.trim();
                            if (!ct.startsWith("store ")) { ok = false; break; }
                            // A store keeps `n` live in WA only if its destination address
                            // is materialized WITHOUT touching WA/XWA. Stores to a global
                            // (@sym) or an inttoptr immediate use `lda`/immediate addressing
                            // and are WA-safe. A store through an SSA `%`-pointer (a GEP
                            // result) may need XWA to compute its index, clobbering the
                            // value we are trying to keep resident.
                            int comma = ct.lastIndexOf(',');
                            String destOperand = comma >= 0 ? ct.substring(comma + 1).trim() : "";
                            boolean waSafeDest = destOperand.startsWith("@")
                                              || destOperand.startsWith("inttoptr");
                            if (!waSafeDest) { ok = false; break; }
                        }
                    }

                    if (ok && seen == uses) aResident.add(n);
                }
            }

            // Classify every SSA value as regOnly, xhlOnly, or needing a slot.
            // Also record the size of each value for promotion and slot allocation.
            // Alloca names are already in valueSizeBytes/originalAllocaNames from the
            // pre-scalarization scan above; skip alloca instructions here.
            // (allocSlot is NOT called here â€” sizes go into valueSizeBytes.)
            for (int bi = 0; bi < bbs.size(); bi++) {
                List<String> instrs = bbs.get(bi).instrs();
                for (int ii = 0; ii < instrs.size(); ii++) {
                    String instr = instrs.get(ii);
                    // Skip alloca instructions â€” already recorded pre-scalarization.
                    if (instr.strip().matches("%[^=]+=\\s*alloca\\s+.*")) continue;
                    Matcher mSSA = Pattern.compile("%([^\\s,=]+)\\s*=").matcher(instr);
                    if (mSSA.find() && !instr.contains("alloca")) {
                        String n = mSSA.group(1);
                        boolean singleNextUse = false;
                        if (useCount.getOrDefault(n, 0) == 1
                                && !phiUsed.contains(n)
                                && !instr.contains("getelementptr")
                                && !instr.contains("call ")
                                && !instr.contains("alloca")
                                && bi == defBlock.getOrDefault(n, -1)
                                && ii + 1 < instrs.size()) {
                            String consumer = instrs.get(ii + 1);
                            if (consumer.contains("%" + n)) {
                                boolean isGepConsumer = consumer.contains("getelementptr");
                                int lastComma = consumer.lastIndexOf(',');
                                boolean onlyAfterComma = lastComma >= 0
                                    && !consumer.substring(0, lastComma).contains("%" + n)
                                    && consumer.substring(lastComma).contains("%" + n);
                                    boolean commutativeRhsConsumer = regChain && onlyAfterComma
                                        && consumer.matches("^\\s*%[^=]+\\s*=\\s*(?:add|and|or|xor|mul)\\b.*%" + Pattern.quote(n) + "(?![\\w.]).*");
                                boolean unsafeCallArg = false;
                                int parenOpen = consumer.indexOf('(');
                                int parenClose = consumer.lastIndexOf(')');
                                if (consumer.contains("call ") && parenOpen >= 0 && parenClose > parenOpen) {
                                    String argsInner = consumer.substring(parenOpen + 1, parenClose);
                                    String firstPushedArg = argsInner.substring(argsInner.lastIndexOf(',') + 1).trim();
                                    unsafeCallArg = !firstPushedArg.matches(".*%" + Pattern.quote(n) + "(?![\\w.]).*");
                                }

                                singleNextUse = !isGepConsumer && (!onlyAfterComma || commutativeRhsConsumer) && !unsafeCallArg;
                            }
                        }

                        if (singleNextUse || aResident.contains(n)) {
                            regOnly.add(n);
                        } else if (!xhlOnly.contains(n)) {
                            int db = irDefBits(instr);
                            if (db > 0) {
                                valueDefBits.put(n, db);
                                valueSizeBytes.put(n, db <= 16 ? 2 : 4);
                            } else {
                                // Pointer and otherwise unclassified SSA results use the
                                // target's full 32-bit representation.
                                valueSizeBytes.put(n, 4);
                            }
                        }
                    }
                }
            }

            // Count accesses for promotion ranking.
            // Mirrors the original two-loop approach exactly:
            //   (a) alloca reads via load/store ptr %name
            //   (b) SSA value RHS references (non-alloca, non-regOnly, non-xhlOnly,
            //       non-GEP/call result)
            // originalAllocaNames was populated above (from pre-scalarization alloca scan).
            if (!isInterrupt) {
                Set<String> localAllocaNames = originalAllocaNames;
                Set<String> nonWAStore = new LinkedHashSet<>();
                for (BasicBlock bb : bbs)
                    for (String instr : bb.instrs()) {
                        Matcher md = Pattern.compile("^%([^\\s,=]+)\\s*=\\s*(.*)").matcher(instr.strip());
                        if (!md.find()) continue;
                        String n = md.group(1).replaceAll(",$","");
                        String rhs = md.group(2);
                        if (rhs.startsWith("getelementptr") || rhs.startsWith("call ")
                                || rhs.contains(" call "))
                            nonWAStore.add(n);
                    }

                // (a) alloca accesses: count loads; track call BBs for spill flag.
                for (int bi = 0; bi < bbs.size(); bi++) {
                    BasicBlock bb = bbs.get(bi);
                    int accessWeight = promotionWeight(loopBlockIndexes, loopDepth, bi);
                    boolean bbHasCall = bb.instrs().stream().anyMatch(s -> s.contains("call "));
                    for (String instr : bb.instrs()) {
                        Matcher mAcc = Pattern.compile(
                            "(?:load\\s+\\S+,\\s*ptr\\s+%([^\\s,]+))" +
                            "|(?:store\\s+\\S+\\s+[^,]+,\\s*ptr\\s+%([^\\s,]+))")
                            .matcher(instr);
                        while (mAcc.find()) {
                            String n = mAcc.group(1) != null ? mAcc.group(1) : mAcc.group(2);
                            n = n.replaceAll(",$","");
                            if (!localAllocaNames.contains(n)) continue;
                            if (mAcc.group(1) != null) accessCounts.merge(n, accessWeight, Integer::sum);
                            if (bbHasCall) needsSpillSet.add(n);
                        }
                    }
                }

                // (b) SSA value RHS uses (non-alloca candidates).
                for (int bi = 0; bi < bbs.size(); bi++) {
                    BasicBlock bb = bbs.get(bi);
                    int accessWeight = promotionWeight(loopBlockIndexes, loopDepth, bi);
                    boolean bbHasCall = bb.instrs().stream().anyMatch(s -> s.contains("call "));
                    for (String instr : bb.instrs()) {
                        String rhs = instr.contains("=") ? instr.substring(instr.indexOf('=') + 1) : instr;
                        Matcher mu = Pattern.compile("%([^\\s,=()\\[\\]]+)").matcher(rhs);
                        while (mu.find()) {
                            String n = mu.group(1).replaceAll(",$","");
                            if (localAllocaNames.contains(n)) continue; // counted above
                            if (!valueSizeBytes.containsKey(n)) continue;
                            if (regOnly.contains(n) || xhlOnly.contains(n)) continue;
                            if (nonWAStore.contains(n)) continue;
                            accessCounts.merge(n, accessWeight, Integer::sum);
                            if (bbHasCall) needsSpillSet.add(n);
                        }
                    }
                }
            }
        }

        static Set<Integer> naturalLoopBlockIndexes(List<BasicBlock> bbs) {
            Map<String, Integer> byLabel = new HashMap<>();
            for (int i = 0; i < bbs.size(); i++) byLabel.put(bbs.get(i).label(), i);
            Set<Integer> loopBlocks = new LinkedHashSet<>();
            for (int i = 0; i < bbs.size(); i++) {
                for (String succ : successorsOf(bbs.get(i))) {
                    Integer header = byLabel.get(succ);
                    if (header == null || header > i) continue;
                    for (int j = header; j <= i; j++) loopBlocks.add(j);
                }
            }

            return loopBlocks;
        }

        /**
         * Loop-nesting depth of each basic block, indexed by block position.
         *
         * A back-edge i->header (header <= i) defines a natural loop spanning
         * blocks [header..i]; every block in that span gets +1 depth. Nested
         * loops therefore accumulate depth. Used by the register-promotion
         * ranking to weight promotion candidates by 4^depth so that values live
         * in the hottest (deepest) loops win the five callee-saved registers.
         */
        static int[] loopDepths(List<BasicBlock> bbs) {
            Map<String, Integer> byLabel = new HashMap<>();
            for (int i = 0; i < bbs.size(); i++) byLabel.put(bbs.get(i).label(), i);
            int[] depth = new int[bbs.size()];
            for (int i = 0; i < bbs.size(); i++) {
                for (String succ : successorsOf(bbs.get(i))) {
                    Integer header = byLabel.get(succ);
                    if (header == null || header > i) continue;   // only back-edges
                    for (int j = header; j <= i; j++) depth[j]++;
                }
            }

            return depth;
        }

        /** Promotion access weight for block bi: 1, or 2 inside any loop.
         *  Depth-weighted variant (4^depth): available via loopDepths(),
         *  currently unused (measured cycle-neutral on reference ROMs). */
        static int promotionWeight(Set<Integer> loopBlockIndexes, int[] loopDepth, int bi) {
            if (loopDepth == null) return loopBlockIndexes.contains(bi) ? 2 : 1;
            int d = Math.min(loopDepth[bi], 10);   // 4^10 fits comfortably in int
            int w = 1;
            for (int k = 0; k < d; k++) w *= 4;
            return w;
        }

        // Pass 2: assign promoted registers (BC/DE/IX/IY/IZ) to the top-5 most-accessed
        // values. Must run after analyzeValues() and before allocSlots().
        void assignRegisters() {
            if (isInterrupt) return;
            // Promotion threshold: >=2 accesses. BC/DE/IX/IY/IZ are callee-saved by
            // generated functions, so values used in call-containing blocks are still
            // profitable register candidates instead of forced through stack slots.
            Map<String, Integer> candidates = new LinkedHashMap<>(accessCounts);
            candidates.entrySet().removeIf(e -> e.getValue() < 2);
            // Phi participants are normally pinned to private stack slots (edge copies
            // interact with the WA residency cache). But the hottest spill slots in
            // loop-heavy functions are loop-carried phi vars; promoting one to a
            // callee-saved register removes that traffic. Only promote phi vars that
            // are safe from the parallel-copy hazard (see phiSafeToPromote).
            Set<String> phiExcluded = new LinkedHashSet<>(phiParticipantNames());
            phiExcluded.removeIf(this::phiSafeToPromote);
            candidates.keySet().removeAll(phiExcluded);
            // Pick top-5 by weighted access count. For ties, prefer values that are
            // passed to calls; promoting them removes concrete argument spill/reload
            // traffic in call-heavy loops.
            List<Map.Entry<String, Integer>> ranked = new ArrayList<>(candidates.entrySet());
            ranked.sort((a, b) -> {
                int byCount = Integer.compare(b.getValue(), a.getValue());
                if (byCount != 0) return byCount;
                int byCallArg = Boolean.compare(callArgValues.contains(b.getKey()), callArgValues.contains(a.getKey()));
                if (byCallArg != 0) return byCallArg;
                return 0;
            });

            List<String> picks = new ArrayList<>();
            for (int i = 0; i < 5 && i < ranked.size(); i++) picks.add(ranked.get(i).getKey());
            // Fixed rank order BC, DE, IX, IY, IZ. This order is deliberately
            // not reshuffled by access-frequency. An earlier experiment routed
            // narrow values to BC/DE and pointer/32-bit values to IX/IY/IZ;
            // it regressed (_mp_play_hw in bomberman crashed due to an
            // emitter path that produced a mishandled addressing form).
            // ISR save trimming (trimIsrSaves) provides the register-allocation
            // win without changing register assignment.
            assignBC(picks.size() > 0 ? picks.get(0) : null);
            assignDE(picks.size() > 1 ? picks.get(1) : null);
            assignIX(picks.size() > 2 ? picks.get(2) : null);
            assignIY(picks.size() > 3 ? picks.get(3) : null);
            assignIZ(picks.size() > 4 ? picks.get(4) : null);

            // Ranks 6..13 spill over into the bank-1/2 register pool (see BANK_REG_KEYS),
            // in fixed order. Same callee-saved treatment as BC/DE/IX/IY/IZ.
            for (int i = 0; i < BANK_REG_KEYS.length; i++) {
                int rank = 5 + i;
                assignBank(i, rank < ranked.size() ? ranked.get(rank).getKey() : null);
            }
        }

        private void assignBank(int idx, String n) {
            if (n == null) return;
            bankSlotName[idx] = n;
            bankSlotBits[idx] = promotedBits(n);
            bankSaveBits[idx] = bankSlotBits[idx];
            useBank[idx] = true;
        }

        /** True if SSA value `n` is homed in a bank-1/bank-2 register (no stack slot). */
        private boolean isBankPromoted(String n) {
            if (n == null) return false;
            for (int i = 0; i < BANK_REG_KEYS.length; i++) {
                if (useBank[i] && n.equals(bankSlotName[i])) return true;
            }
            return false;
        }

        /** Index into BANK_REG_* for the bank register homing `n`, or -1. */
        private int bankIndexOf(String n) {
            if (n == null) return -1;
            for (int i = 0; i < BANK_REG_KEYS.length; i++) {
                if (useBank[i] && n.equals(bankSlotName[i])) return i;
            }
            return -1;
        }


        private void assignBC(String n) {
            if (n == null) return;
            bcSlotName = n; bcSlotBits = promotedBits(n); bcSaveBits = bcSlotBits; useXBC = true; bcNeedsSpillAroundCall = false;
        }

        private void assignDE(String n) {
            if (n == null) return;
            deSlotName = n; deSlotBits = promotedBits(n); deSaveBits = deSlotBits; useXDE = true; deNeedsSpillAroundCall = false;
        }

        private void assignIX(String n) {
            if (n == null) return;
            ixSlotName = n; ixSlotBits = promotedBits(n); ixSaveBits = ixSlotBits; useXIX = true; ixNeedsSpillAroundCall = false;
        }

        private void assignIY(String n) {
            if (n == null) return;
            iySlotName = n; iySlotBits = promotedBits(n); iySaveBits = iySlotBits; useXIY = true; iyNeedsSpillAroundCall = false;
        }

        private void assignIZ(String n) {
            if (n == null) return;
            izSlotName = n; izSlotBits = promotedBits(n); izSaveBits = izSlotBits; useXIZ = true; izNeedsSpillAroundCall = false;
        }

        Set<String> phiParticipantNames() {
            Set<String> names = new LinkedHashSet<>();
            Pattern phi = Pattern.compile("%([^\\s,=]+)\\s*=\\s*phi\\s+\\S+\\s+(.*)");
            for (BasicBlock bb : parseBody()) {
                for (String instr : bb.instrs()) {
                    Matcher matcher = phi.matcher(instr.strip());
                    if (!matcher.matches()) continue;
                    names.add(matcher.group(1).replaceAll(",$", ""));
                    for (String pair : extractPhiPairs(matcher.group(2))) {
                        int comma = lastTopLevelComma(pair);
                        if (comma < 0) continue;
                        String value = pair.substring(0, comma).trim().replaceAll(",$", "");
                        if (value.startsWith("%")) names.add(value.substring(1));
                    }
                }
            }

            return names;
        }

        /**
         * True if a phi participant is safe to promote to a callee-saved register.
         *
         * Phi copies on an edge are emitted sequentially (loadToWA src; storeWAtoSlot
         * dst). The parallel-copy hazard: a value that is BOTH the source of one copy
         * and the destination of another on the same edge can be clobbered before it
         * is read. We sidestep it entirely by promoting only phi RESULTS that never
         * appear as a phi SOURCE anywhere — their register is only ever written by an
         * edge copy (dst), never read-then-overwritten on an edge. Loop-carried vars
         * that feed themselves (source == result) are therefore excluded.
         */
        boolean phiSafeToPromote(String name) {
            Pattern phi = Pattern.compile("%([^\\s,=]+)\\s*=\\s*phi\\s+\\S+\\s+(.*)");
            boolean isResult = false;
            for (BasicBlock bb : parseBody()) {
                for (String instr : bb.instrs()) {
                    Matcher m = phi.matcher(instr.strip());
                    if (!m.matches()) continue;
                    if (m.group(1).replaceAll(",$", "").equals(name)) isResult = true;
                    for (String pair : extractPhiPairs(m.group(2))) {
                        int comma = lastTopLevelComma(pair);
                        if (comma < 0) continue;
                        String value = pair.substring(0, comma).trim().replaceAll(",$", "");
                        if (value.startsWith("%") && value.substring(1).equals(name))
                            return false; // appears as a phi source -> unsafe
                    }
                }
            }
            return isResult;
        }

        /**
         * Initial width of a value promoted to XBC/XDE/XIX/XIY/XIZ.
         *
         * Prefers the SSA def's declared width when it is narrower than the default, so
         * blocks emitted before the def read the register at the same width the def will
         * write it. Falls back to the recorded byte size (allocas, parameters).
         */
        int promotedBits(String name) {
            int fallback = valueSizeBytes.getOrDefault(name, 4) * 8;
            Integer db = valueDefBits.get(name);
            if (db == null || db <= 0 || db >= fallback) return fallback;
            return db <= 8 ? 8 : (db <= 16 ? 16 : 32);
        }

        // Pass 3: allocate stack slots â€” ONLY for values not assigned to a register.
        // Promoted non-escaping allocas get no stack slot at all.
        void allocSlots() {
            List<BasicBlock> bbs = parseBody();
            // Step A: handle allocas. Use originalAllocaNames (populated pre-scalarization)
            // because scalarizeLocalAllocas() may have removed the alloca instructions.
            for (String n : originalAllocaNames) {
                allocaSlots.add(n);
                boolean promoted = n.equals(bcSlotName) || n.equals(deSlotName)
                        || n.equals(ixSlotName) || n.equals(iySlotName) || n.equals(izSlotName)
                        || isBankPromoted(n);
                // Escaping allocas need an addressable stack slot even if promoted.
                boolean escaping = allocaEscapes(n, bbs);
                if (!promoted || escaping) {
                    int sz = valueSizeBytes.getOrDefault(n, 2);
                    allocSlot(n, sz);
                }
            }

            // Step B: handle SSA value temps from the scalarized body.
            for (int bi = 0; bi < bbs.size(); bi++) {
                List<String> instrs = bbs.get(bi).instrs();
                for (int ii = 0; ii < instrs.size(); ii++) {
                    String instr = instrs.get(ii);
                    // Skip alloca instructions â€” handled above via originalAllocaNames.
                    if (instr.strip().matches("%[^=]+=\\s*alloca\\s+.*")) continue;
                    Matcher mSSA = Pattern.compile("%([^\\s,=]+)\\s*=").matcher(instr);
                    if (mSSA.find()) {
                        String n = mSSA.group(1);
                        if (regOnly.contains(n) || xhlOnly.contains(n)) continue;
                        if (allocaSlots.contains(n)) continue; // already handled
                        boolean promoted = n.equals(bcSlotName) || n.equals(deSlotName)
                                || n.equals(ixSlotName) || n.equals(iySlotName) || n.equals(izSlotName)
                                || isBankPromoted(n);
                        if (!promoted && !slots.containsKey(n)) {
                            allocSlot(n, valueSizeBytes.getOrDefault(n, 4));
                        }
                    }
                }
            }
        }

        // Legacy entry point kept for reference; now replaced by the three passes above.
        // Called nowhere â€” exists only to make the diff readable.
        void prePass() {
            analyzeValues();
            assignRegisters();
            allocSlots();
        }

        /** Find the index of the first instruction at or after `start` that references %name. */
        static int findNextUse(List<String> instrs, String name, int start) {
            for (int i = start; i < instrs.size(); i++) {
                if (instrs.get(i).contains("%" + name)) return i;
            }

            return -1;
        }

        // -- param parsing --

        record Param(String type, String name) {}
        List<Param> parseParams() {
            List<Param> params = new ArrayList<>();
            if (func.params() == null || func.params().isBlank()) return params;
            List<String> parts = splitCommaRespectingParens(func.params());
            for (String part : parts) {
                part = part.trim();
                Matcher m = Pattern.compile("(.+?)\\s+%([^\\s,]+)$").matcher(part);
                if (m.matches()) {
                    params.add(new Param(stripAttrs(m.group(1)).trim(), m.group(2)));
                }
            }

            return params;
        }

        static List<String> splitCommaRespectingParens(String s) {
            List<String> result = new ArrayList<>();
            int depth = 0;
            StringBuilder cur = new StringBuilder();
            for (char ch : s.toCharArray()) {
                if (ch == '(' || ch == '[') { depth++; cur.append(ch); }
                else if (ch == ')' || ch == ']') { depth--; cur.append(ch); }
                else if (ch == ',' && depth == 0) { result.add(cur.toString().trim()); cur.setLength(0); }
                else cur.append(ch);
            }

            if (!cur.toString().isBlank()) result.add(cur.toString().trim());
            return result;
        }

        // -- main entry --
        // Pre-scan the IR body for patterns that call tmpSlot() during emission
        // (GEP instructions, pointer stores) so that frameSize is finalised
        // before param spills are emitted.  Without this, params are read from
        // wrong stack offsets when tmpSlot() later grows the frame.
        void preallocTmpSlots() {
            Pattern pPtrStore = Pattern.compile(
                "store\\s+\\S+\\s+[^,]+,\\s*ptr\\s+%([^\\s,]+).*");
            boolean hasCondBr = false, hasPhi = false, hasSelect = false;
            boolean hasMemFill = false, hasMemCopy = false;
            for (BasicBlock bb : parseBody()) {
                for (String raw : bb.instrs()) {
                    String s = raw.strip();
                    Matcher m = pPtrStore.matcher(s);
                    if (m.matches()) {
                        String vname = m.group(1).replaceAll(",$", "");
                        if (slots.containsKey(vname) && !allocaSlots.contains(vname))
                            tmpSlot("store_" + vname);
                    }

                    if (s.startsWith("br i1 ")) hasCondBr = true;
                    if (s.matches("%[^=]+=\\s*phi\\b.*")) hasPhi = true;
                    if (s.matches("%[^=]+=\\s*select\\s+i1\\b.*")) hasSelect = true;
                    if (s.startsWith("memfill ")) hasMemFill = true;
                    if (s.startsWith("memcopy ")) hasMemCopy = true;
                }
            }

            // Pre-reserve the scratch slots emitMemPseudo() needs. Without this, the
            // first memfill/memcopy grows frameSize DURING emission â€” after the
            // function's incoming-argument stack offsets were already emitted with the
            // smaller frame â€” so every arg load in a function that clears/copies memory
            // reads from the wrong XSP offset (e.g. ClearScreen's switch reads garbage
            // and falls through to default, leaving the tilemap uncleared).
            //
            // These four values are LIVE SIMULTANEOUSLY inside emitMemPseudo, so they
            // must NOT go through the shared `__t_` scratch slot (compactSlots collapses
            // all __t_ tmps onto one offset). Use dedicated pinned slots instead.
            if (hasMemFill || hasMemCopy) {
                if (!slots.containsKey(MEM_DST_SLOT)) allocSlot(MEM_DST_SLOT, 4);
                if (!slots.containsKey(MEM_LEN_SLOT)) allocSlot(MEM_LEN_SLOT, 4);
                if (!slots.containsKey(MEM_AUX_SLOT)) allocSlot(MEM_AUX_SLOT, 4);
            }

            // Reserve the branch-condition spill slot up front when the function has a
            // conditional branch that could be preceded by phi copies. Allocating it
            // lazily during emission would grow frameSize AFTER argument offsets were
            // already emitted with the smaller frame, corrupting every incoming-arg load.
            if (hasCondBr && hasPhi && !slots.containsKey(CONDSPILL_SLOT))
                allocSlot(CONDSPILL_SLOT, 4);
            if (hasSelect && !slots.containsKey(SELSPILL_SLOT))
                allocSlot(SELSPILL_SLOT, 4);
        }

        // =====================================================================
        // -O3 normalization (pass 0)
        //
        // clang -O3 emits IR forms the scalar backend below never learned to read:
        //   * `tail`/`musttail`/`notail` prefixes on call / inline-asm
        //   * poison-generating flags on integer ops: nsw nuw exact disjoint nneg
        //   * SIMD vector load/store (<N x T>) from the SLP/loop vectorizers
        //   * the @llvm.memset / @llvm.memcpy intrinsics
        // The TLCS-900/H has no SIMD and the rest of the translator is strictly
        // scalar, so this pass rewrites each of these into equivalent scalar IR
        // *before* value analysis and slot allocation see the body. Everything
        // downstream then works unchanged.
        //
        // Inline-asm lines are passed through verbatim (their string bodies may
        // legitimately contain any of these tokens).

        // =====================================================================
        void normalizeBody() {
            if (isRawAsm) return;
            List<String> in = func.body();
            List<String> out = new ArrayList<>(in.size());
            // Vector loads pending a paired store: SSA name -> per-element SSA names.
            Map<String, List<String>> vecLoadElems = new LinkedHashMap<>();
            int veCounter = 0;
            for (String raw : in) {
                String indent = raw.substring(0, raw.length() - raw.stripLeading().length());
                String s = raw.strip();
                // Structural / comment / asm lines: emit unchanged.
                boolean isAsm = s.contains("asm sideeffect") || s.contains(" asm \"")
                             || s.contains("call void asm") || s.contains("call i")  // asm with result
                                && s.contains(" asm ");
                if (s.isEmpty() || s.startsWith(";") || s.startsWith("define ")
                        || s.equals("{") || s.equals("}")
                        || s.matches("^[A-Za-z_][\\w.]*:.*$") || s.matches("^\\d+:.*$")
                        || isAsm) {
                    // Still strip a leading tail/musttail/notail on inline-asm calls so
                    // the raw-asm passthrough in translateInstr recognizes them.
                    if (isAsm) s = stripTailPrefix(s);
                    out.add(isAsm ? indent + s : raw);
                    continue;
                }

                // --- vector store ---
                // store <N x T> <val>, ptr <dest>[, align A][, !meta]
                Matcher mVStore = Pattern.compile(
                    "store\\s+(?:volatile\\s+)?<(\\d+)\\s+x\\s+([^>]+)>\\s+(.*?),\\s*ptr\\s+(.*)")
                    .matcher(s);
                if (mVStore.matches()) {
                    int n        = Integer.parseInt(mVStore.group(1));
                    String elemT = mVStore.group(2).trim();
                    String val   = mVStore.group(3).trim();
                    String dest  = stripAlign(stripMeta(mVStore.group(4).trim()));
                    int esz      = irTypeBytes(elemT);
                    List<String> elems;   // per-element scalar value strings
                    if (val.startsWith("%")) {
                        // Value is a previously-scalarized vector load.
                        String vn = val.substring(1).replaceAll(",$", "");
                        elems = vecLoadElems.get(vn);
                        if (elems == null) {            // unexpected: leave for the TODO path
                            out.add(raw); continue;
                        }

                    } else {
                        // Constant vector literal: <T c0, T c1, ...> or zeroinitializer.
                        elems = new ArrayList<>();
                        String body = val.trim();
                        if (body.equals("zeroinitializer")) {
                            for (int k = 0; k < n; k++) elems.add("0");
                        } else {
                            if (body.startsWith("<")) body = body.substring(1);
                            if (body.endsWith(">")) body = body.substring(0, body.length() - 1);
                            for (String part : splitCommaRespectingParens(body.trim())) {
                                // each part is "T const"; keep only the constant token(s)
                                elems.add(part.trim().replaceFirst("^\\S+\\s+", ""));
                            }
                        }
                    }

                    // Emit N scalar stores at dest + k*esz.
                    for (int k = 0; k < n; k++) {
                        String p = elemPtr(indent, out, dest, elemT, esz, k, "vsd", veCounter++);
                        out.add(indent + "store " + elemT + " " + elems.get(k)
                                + ", ptr " + p + ", align 1");
                    }

                    continue;
                }

                // --- vector load ---
                // %D = load <N x T>, ptr <src>[, align A][, !meta]
                Matcher mVLoad = Pattern.compile(
                    "%([^\\s,=]+)\\s*=\\s*load\\s+(?:volatile\\s+)?<(\\d+)\\s+x\\s+([^>]+)>\\s*,\\s*ptr\\s+(.*)")
                    .matcher(s);
                if (mVLoad.matches()) {
                    String dst   = mVLoad.group(1).replaceAll(",$", "");
                    int n        = Integer.parseInt(mVLoad.group(2));
                    String elemT = mVLoad.group(3).trim();
                    String src   = stripAlign(stripMeta(mVLoad.group(4).trim()));
                    int esz      = irTypeBytes(elemT);
                    List<String> elemNames = new ArrayList<>();
                    for (int k = 0; k < n; k++) {
                        String p = elemPtr(indent, out, src, elemT, esz, k, "vld", veCounter++);
                        String en = "%__ve_" + dst + "_" + k;
                        out.add(indent + en + " = load " + elemT + ", ptr " + p + ", align 1");
                        elemNames.add(en);
                    }

                    vecLoadElems.put(dst, elemNames);
                    continue;
                }

                // --- @llvm.memset / @llvm.memcpy / @llvm.memmove intrinsics ---
                Matcher mMemIntr = Pattern.compile(
                    ".*@llvm\\.(memset|memcpy|memmove)\\.[^(]*\\((.*)\\)\\s*(?:#\\d+)?$")
                    .matcher(stripMeta(s));
                if (mMemIntr.matches()) {
                    List<String> expanded = expandMemIntrinsic(
                        indent, mMemIntr.group(1), mMemIntr.group(2), veCounter++);
                    if (expanded != null) { out.addAll(expanded); continue; }
                    // Large/variable length → emit a compact pseudo-op that the asm
                    // layer lowers to an LDIR block copy / fill loop instead of
                    // unrolling thousands of scalar stores.
                    String pseudo = memPseudo(mMemIntr.group(1), mMemIntr.group(2));
                    if (pseudo != null) { out.add(indent + pseudo); continue; }
                    // Fall through to generic handling if we couldn't lower it.
                }

                // --- scalar line: strip trailing metadata, tail prefix, opt flags ---
                s = stripMeta(s);
                s = stripTailPrefix(s);
                s = stripOpFlags(s);
                out.add(indent + s);
            }

            in.clear();
            in.addAll(out);
            parsedBlocks = null;   // invalidate any cached parse
        }

        /** Remove a leading tail/musttail/notail keyword before `call`. */
        static String stripTailPrefix(String s) {
            return s.replaceFirst("^(%[^=]+=\\s*)?(?:tail\\s+|musttail\\s+|notail\\s+)(call\\b)",
                                  "$1$2");
        }

        /** Strip poison-generating / optimization flag keywords that clang -O3
         *  places between an integer opcode and its type. Safe on scalar lines
         *  only (never called on inline-asm or vector lines). */
        static String stripOpFlags(String s) {
            // Flags that appear as standalone tokens after an opcode: remove each.
            // (nsw nuw exact disjoint nneg â€” plus GEP `inbounds`.)
            s = s.replaceAll("\\b(nsw|nuw|exact|disjoint|nneg|inbounds)\\b\\s*", "");
            // Collapse any doubled whitespace left behind.
            return s.replaceAll("\\s{2,}", " ").trim();
        }

        static String stripMeta(String s) {
            // Remove one-or-more trailing metadata attachments, e.g.
            //   ", !tbaa !5"      ", !tbaa !5, !range !6"      ", !llvm.loop !31"
            return s.replaceAll("(?:,\\s*!\\S+\\s+!?\\d+)+\\s*$", "").trim();
        }

        /** Extract the actual value operand from a call-argument value string that
         *  may still carry pointer/int parameter attributes, e.g.
         *    "nonnull align 2048 dereferenceable(2048) %4"  -> "%4"
         *    "inttoptr (i64 40960 to ptr)"                  -> "inttoptr (i64 40960 to ptr)"
         *    "0"                                            -> "0" */
        static String memOperand(String v) {
            v = v.trim().replaceAll(",$", "");
            // Keep composite expressions (inttoptr/getelementptr/bitcast) intact.
            if (v.startsWith("inttoptr") || v.startsWith("getelementptr")
                    || v.startsWith("bitcast")) return v;
            // Drop leading attribute tokens (nonnull, align N, dereferenceable(N),
            // readonly, writeonly, noundef, immarg, ...). The real operand is the
            // final %reg / @sym / numeric token.
            String[] toks = v.split("\\s+");
            return toks.length == 0 ? v : toks[toks.length - 1];
        }

        /** Build a synthetic pointer name for element k, emitting the getelementptr
         *  needed to reach base + k*elemSize when k>0. Returns the ptr operand string
         *  to use in the scalar load/store (either the base itself for k==0, or a
         *  fresh %__ve_gep_* SSA temp). */
        String elemPtr(String indent, List<String> out, String base, String elemT,
                       int esz, int k, String tag, int uid) {
            if (k == 0) return base;
            String gp = "%__ve_gep_" + tag + "_" + uid + "_" + k;
            out.add(indent + gp + " = getelementptr " + elemT + ", ptr " + base
                    + ", i64 " + k);
            return gp;
        }

        /** Expand a memset/memcpy/memmove intrinsic call into scalar stores/loads
         *  when the length is a small compile-time constant; otherwise return null
         *  so the caller can leave the original line for downstream handling.
         *  Args string (already inside the parens) example:
         *    ptr ... %4, i8 0, i64 2048, i1 false */
        List<String> expandMemIntrinsic(String indent, String kind, String argsStr, int uid) {
            List<Arg> args = parseCallArgs(argsStr);
            if (args.size() < 3) return null;
            // length is the 3rd argument for memset(dst,val,len) and
            // memcpy(dst,src,len). Must be a constant to unroll.
            String lenTok = memOperand(args.get(2).val());
            OptionalLong lenOpt = parseImmediate(lenTok);
            if (lenOpt.isEmpty()) return null;
            long len = lenOpt.getAsLong();
            // Only unroll SMALL constant lengths inline. Larger fills/copies are
            // lowered to an LDIR / counted loop by memPseudo() to avoid emitting
            // thousands of scalar stores.
            if (len <= 0 || len > MEM_UNROLL_MAX) return null;
            String dst = memOperand(args.get(0).val());
            List<String> out = new ArrayList<>();
            if (kind.equals("memset")) {
                String valTok = memOperand(args.get(1).val());
                // Emit byte stores. Unrolling 4096 bytes is large but bounded and
                // matches -O3's own decision to inline the fill.
                for (long k = 0; k < len; k++) {
                    String p = (k == 0) ? dst : "%__ms_" + uid + "_" + k;
                    if (k != 0)
                        out.add(indent + p + " = getelementptr i8, ptr " + dst + ", i64 " + k);
                    out.add(indent + "store i8 " + valTok + ", ptr " + p + ", align 1");
                }

            } else {
                // memcpy / memmove: copy byte-by-byte (forward). memmove with
                // overlap would need reverse copy, but clang only emits memmove
                // here for non-overlapping ranges it proved disjoint.
                String src = memOperand(args.get(1).val());
                for (long k = 0; k < len; k++) {
                    String sp = (k == 0) ? src : "%__mcs_" + uid + "_" + k;
                    String dp = (k == 0) ? dst : "%__mcd_" + uid + "_" + k;
                    if (k != 0) {
                        out.add(indent + sp + " = getelementptr i8, ptr " + src + ", i64 " + k);
                        out.add(indent + dp + " = getelementptr i8, ptr " + dst + ", i64 " + k);
                    }

                    String tv = "%__mcv_" + uid + "_" + k;
                    out.add(indent + tv + " = load i8, ptr " + sp + ", align 1");
                    out.add(indent + "store i8 " + tv + ", ptr " + dp + ", align 1");
                }
            }

            return out;
        }

        // Above this many bytes, a constant-length (or any variable-length) mem
        // intrinsic is lowered to an LDIR/loop instead of being unrolled inline.

        static final int MEM_UNROLL_MAX = 32;

        /** Build a compact pseudo-instruction lowered by translateInstr into an
         *  LDIR block copy (memcpy/memmove) or a counted fill loop (memset):
         *    memfill <dstOperand> ; <valOperand> ; <lenOperand>
         *    memcopy <dstOperand> ; <srcOperand> ; <lenOperand>
         *  Operands are kept as raw IR value strings (%reg / @sym / inttoptr / const);
         *  the emitter resolves them with loadToXHL/loadToWA. Returns null if the
         *  intrinsic shape is unexpected. */
        String memPseudo(String kind, String argsStr) {
            List<Arg> args = parseCallArgs(argsStr);
            if (args.size() < 3) return null;
            String dst = memOperand(args.get(0).val());
            String a1  = memOperand(args.get(1).val());   // val (memset) or src (memcpy)
            String len = memOperand(args.get(2).val());
            String op  = kind.equals("memset") ? "memfill" : "memcopy";
            return op + " " + dst + " ; " + a1 + " ; " + len;
        }

        List<String> translate() {
            normalizeBody();    // pass 0: -O3 normalization (flags, tail, vectors, intrinsics)
            // Unsigned value-range bounds over the (normalized) body, so mul lowering can
            // prove a 32-bit multiply's operands both fit 16 bits and use the hardware mul.
            valueBounds = Translator.computeBounds(func.body(),
                    Pattern.compile("^\\s*%([\\w.]+)\\s*=\\s*(.*)$"),
                    Pattern.compile("%([\\w.]+)"));
                signed16Values = computeSigned16Values();
            addrOnlyMulResults = computeAddrOnlyMulResults();
            u16TruncSafeValues = computeU16TruncSafe();
            analyzeValues();    // pass 1: classify values, compute access counts
            assignRegisters();  // pass 2: assign top-5 to BC/DE/IX/IY/IZ
            allocSlots();       // pass 3: stack slots only for non-promoted values
            preallocTmpSlots(); // finalise frameSize before emitting param spills
            compactSlots();     // reuse non-overlapping stack slots (shrinks frame)
            // An interrupt handler with no real code (body is only ret/ret void) needs
            // no register save/restore â€” just reti.
            boolean isrHasBody = isrHasBody();
            List<Param> params = parseParams();
            // Build arg offset map: param name → (stack offset from XSP after frame)
            // For interrupt handlers: 6 registers (xwa/xhl/xde/xbc/xix/xiy) × 4 bytes
            // are pushed before the local frame, so args are 24 bytes further up.
            Map<String, int[]> argOffsets = new LinkedHashMap<>();
            int argSp = 4 + (isrHasBody ? ISR_REGS.size() * 4 : 0); // past far return address (+ saved regs)
            for (Param p : params) {
                if (p.name() != null && !p.name().isBlank()) {
                    argOffsets.put(p.name(), new int[]{argSp, irTypeBits(p.type())});
                }

                argSp += Math.max(irTypeBytes(p.type()), 2);
            }

            out.add("_" + func.name() + ":");
            // Raw inline-asm function: emit the asm verbatim with NO frame prologue,
            // NO param spill, and NO epilogue. The inline asm owns the stack and its
            // args live at their entry (XSP+N) positions. Only `asm sideeffect` and
            // terminators are emitted; alloca/store/load boilerplate is dropped.
            if (isRawAsm) {
                for (BasicBlock bb : parseBody()) {
                    if (!bb.label().equals("0")) {
                        out.add(bbPrefix + bb.label() + ":");
                    }

                    for (String instr : bb.instrs()) {
                        translateInstr(instr, argOffsets);
                    }
                }

                return out;
            }

            if (isrHasBody) {
                for (String reg : ISR_REGS) out.add("\tpush " + reg);
            }

            // Save only the portion of each callee-saved register this function can
            // modify. Narrow promoted values write BC/DE/IX/IY/IZ but leave the upper
            // half of XBC/XDE/XIX/XIY/XIZ untouched. Allocation-time save widths keep
            // the prologue and epilogue symmetric even if live widths later change.
            if (!isEntryPoint) {
                if (useXBC) out.add("\tpush " + savedRegister("bc", bcSaveBits));
                if (useXDE) out.add("\tpush " + savedRegister("de", deSaveBits));
                if (useXIX) out.add("\tpush " + savedRegister("ix", ixSaveBits));
                if (useXIY) out.add("\tpush " + savedRegister("iy", iySaveBits));
                if (useXIZ) out.add("\tpush " + savedRegister("iz", izSaveBits));
                // Callee-saved bank-1/bank-2 registers (fixed order 0..N).
                for (int i = 0; i < BANK_REG_KEYS.length; i++)
                    if (useBank[i]) out.add("\tpush " + bankSavedReg(i, bankSaveBits[i]));
            }

            // Emit a placeholder; back-patched below once tmpSlot() calls
            // during translation may have grown frameSize beyond prePass's value.
            int frameLine = -1;
            if (frameSize > 0) {
                frameLine = out.size();
                out.add(null); // placeholder
            }

            // argOffsets are relative to XSP at function entry (after far-ret address).
            // Adjust parameter offsets by the actual mix of word/dword saves.
            int regSaveBytes = promotedRegisterSaveBytes();
            if (regSaveBytes > 0) {
                Map<String, int[]> adjusted = new LinkedHashMap<>();
                for (Map.Entry<String, int[]> e : argOffsets.entrySet())
                    adjusted.put(e.getKey(), new int[]{e.getValue()[0] + regSaveBytes, e.getValue()[1]});
                argOffsets = adjusted;
            }

            scanPhis();
            List<BasicBlock> bbs = parseBody();
            for (int bi = 0; bi < bbs.size(); bi++) {
                BasicBlock bb = bbs.get(bi);
                // Tell emitCondBr which BB label comes next in emit order.
                nextBBLabel = (bi + 1 < bbs.size()) ? bbs.get(bi + 1).label() : null;
                if (bi != 0) {
                    waSlotName = null; xhlSlotName = null; xhlIndexPending = null; bcLive = null; deLive = null; ixLive = null; iyLive = null; izLive = null;
                    out.add(bbPrefix + bb.label() + ":");
                }

                currentBlock = bb;
                for (int ii = 0; ii < bb.instrs().size(); ii++) {
                    currentInstrIndex = ii;
                    String instr = bb.instrs().get(ii);
                    // Before this block's terminator, materialize any phi values that
                    // successor blocks expect from this predecessor (SSA phi lowering).
                    if (isTerminator(instr.strip())) {
                        // Conditional branches emit successor-specific phi copies after
                        // choosing an edge. Other terminators keep the conservative
                        // predecessor-copy lowering used by switches and direct branches.
                        if (!instr.strip().startsWith("br i1 ")) {
                            maybeSpillBranchCond(instr.strip(), bb.label());
                            emitPhiCopies(bb.label(), argOffsets);
                        }
                    }

                    translateInstr(instr, argOffsets);
                }
            }

            currentBlock = null;
            currentInstrIndex = -1;
            // Back-patch frame allocation with the final frameSize (may have grown
            // during translation due to lazy tmpSlot() allocations).
            if (frameLine >= 0) {
                out.set(frameLine, "\tadd xsp, -" + frameSize + "\t; frame: " + frameSize + " bytes");
            } else if (frameSize > 0) {
                // frameSize grew from zero after prePass â€” insert the line now.
                // Find insertion point: after the label (and after any ISR/reg-save pushes).
                int insertAt = out.indexOf("_" + func.name() + ":") + 1;
                if (isrHasBody) insertAt += ISR_REGS.size();
                if (!isEntryPoint) {
                    insertAt += (useXBC ? 1 : 0) + (useXDE ? 1 : 0) + (useXIX ? 1 : 0) + (useXIY ? 1 : 0) + (useXIZ ? 1 : 0);
                    for (int i = 0; i < BANK_REG_KEYS.length; i++) if (useBank[i]) insertAt++;
                }
                out.add(insertAt, "\tadd xsp, -" + frameSize + "\t; frame: " + frameSize + " bytes");
            }

            return out;
        }

        // -----------------------------------------------------------------------
        // Core value resolution â€” load into XWA/WA/A

        // -----------------------------------------------------------------------
        void loadToWA(String val, String irType, Map<String, int[]> argOffsets) {
            loadToWA(val, irType, argOffsets, 0);
        }

        // stackAdj: bytes already pushed since function frame was set up (e.g. during
        // emitCall's right-to-left push loop). Frame-relative slot offsets must be
        // increased by this amount because XSP has moved down.
        void loadToWA(String val, String irType, Map<String, int[]> argOffsets, int stackAdj) {
            val = val.trim().replaceAll(",$", "");
            int bits = irTypeBits(irType);
            // Immediate
            OptionalLong n = parseImmediate(val);
            if (n.isPresent()) {
                long v = n.getAsLong();
                if (v < 0) v = v & ((bits < 64) ? ((1L << bits) - 1) : 0xFFFFFFFFFFFFFFFFL);
                if (bits <= 16) emit("ld wa, " + fmtImm(v));
                else            emit("ld xwa, " + fmtImm(v));
                waSlotName = null;
                return;
            }

            // inttoptr MMIO
            Matcher mITP = Pattern.compile("inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\)").matcher(val);
            if (mITP.matches()) {
                int addr = Integer.parseInt(mITP.group(1));
                emit("ld xwa, " + fmtAddr(addr));
                waSlotName = null;
                return;
            }

            // Global address
            if (val.startsWith("@")) {
                emit("lda xwa, _" + val.substring(1));
                waSlotName = null;
                return;
            }

            // SSA register
            if (val.startsWith("%")) {
                String vname = val.substring(1).replaceAll(",$", "");
                // Register-only value: never spilled, value is in WA from last store.
                if (regOnly.contains(vname)) {
                    if (!vname.equals(waSlotName)) {
                        throw new IllegalStateException("register-only value %" + vname
                            + " is not resident in WA while translating " + func.name());
                    }

                    return;
                }

                // Register-promoted value: load from its physical register family.
                if (vname.equals(bcSlotName)) {
                    if (bcSlotBits <= 8)       { emit("ld wa, 0"); emit("ld a, b"); }
                    else if (bcSlotBits <= 16)   emit("ld wa, bc");
                    else                         emit("ld xwa, xbc");
                    waSlotName = vname; waSlotBits = bcSlotBits; return;
                }

                if (vname.equals(deSlotName)) {
                    if (deSlotBits <= 8)       { emit("ld wa, 0"); emit("ld a, e"); }
                    else if (deSlotBits <= 16)   emit("ld wa, de");
                    else                         emit("ld xwa, xde");
                    waSlotName = vname; waSlotBits = deSlotBits; return;
                }

                if (vname.equals(ixSlotName)) {
                    if (ixSlotBits <= 8)      { emit("ld wa, ix"); emit("and wa, 0ffh"); }
                    else if (ixSlotBits <= 16) emit("ld wa, ix");
                    else                        emit("ld xwa, xix");
                    waSlotName = vname; waSlotBits = ixSlotBits; return;
                }

                if (vname.equals(iySlotName)) {
                    if (iySlotBits <= 8)      { emit("ld wa, iy"); emit("and wa, 0ffh"); }
                    else if (iySlotBits <= 16) emit("ld wa, iy");
                    else                        emit("ld xwa, xiy");
                    waSlotName = vname; waSlotBits = iySlotBits; return;
                }

                if (vname.equals(izSlotName)) {
                    if (izSlotBits <= 8)      { emit("ld wa, iz"); emit("and wa, 0ffh"); }
                    else if (izSlotBits <= 16) emit("ld wa, iz");
                    else                        emit("ld xwa, xiz");
                    waSlotName = vname; waSlotBits = izSlotBits; return;
                }

                // Bank-1/2 register-promoted value: load from its bank register into WA.
                for (int i = 0; i < BANK_REG_KEYS.length; i++) {
                    if (vname.equals(bankSlotName[i])) {
                        int bb = bankSlotBits[i];
                        if (bb <= 8)       { emit("ld wa, 0"); emit("ld a, " + BANK_REG_B[i]); }
                        else if (bb <= 16)   emit("ld wa, " + BANK_REG_R[i]);
                        else                 emit("ld xwa, " + BANK_REG_X[i]);
                        waSlotName = vname; waSlotBits = bb; return;
                    }
                }


                if (slots.containsKey(vname)) {
                    // Skip reload if this slot's value is already in WA/XWA
                    if (vname.equals(waSlotName)) return;
                    int off = slots.get(vname)[0] + stackAdj;
                    int sz  = slots.get(vname)[1];
                    if (sz <= 1 || bits <= 8) {
                        emit("ld wa, 0");
                        emit("ld a, (xsp+" + off + ")");
                    } else if (sz <= 2 || bits <= 16) {
                        emit("ld wa, (xsp+" + off + ")");
                    } else {
                        emit("ld xwa, (xsp+" + off + ")");
                    }

                    waSlotName = vname;
                    waSlotBits = sz * 8;
                    return;
                }

                if (argOffsets.containsKey(vname)) {
                    int aoff  = argOffsets.get(vname)[0];
                    int abits = argOffsets.get(vname)[1];
                    int total = frameSize + aoff + stackAdj;
                    if (abits <= 8) {
                        emit("ld wa, 0");
                        emit("ld a, (xsp+" + total + ")");
                    } else if (abits <= 16) {
                        emit("ld wa, (xsp+" + total + ")");
                    } else {
                        emit("ld xwa, (xsp+" + total + ")");
                    }

                    waSlotName = null;
                    return;
                }

                emitComment("unresolved val: " + val);
                return;
            }

            // undef/poison as a value: any bit pattern is valid; use 0.
            if (val.equals("undef") || val.equals("poison")) {
                emit("ld xwa, 0");
                waSlotName = null;
                return;
            }

            // Inline constant-expression GEP used as a pointer VALUE (e.g. &data[N]).

            {

                OptionalLong ceOff = constExprGepOffset(val.trim());
                if (ceOff.isPresent()) {
                    emit("lda xwa, _" + constExprGepSymbol(val.trim()));
                    if (ceOff.getAsLong() != 0) emit("add xwa, " + ceOff.getAsLong());
                    waSlotName = null;
                    return;
                }
            }

            emitComment("unresolved val: " + val);
        }

        /** If `val` is a full-dword (>=4 byte) spill slot, return its `(xsp+N)`
         *  memory operand for a direct src-mem ALU op; else null. Requires the
         *  slot to be a genuine 32-bit spill (not a narrow value whose stored
         *  dword has stale high bytes) and NOT WA-resident (a resident value is
         *  cheaper to read from WA). Only usable for add/sub, whose src-mem
         *  encoding (E3 pfx, op2 0x80/0xA0) the sim decodes at execMemOp:724. */
        String dwordSlotMem(String val, int stackAdj) {
            val = val.trim().replaceAll(",$", "");
            if (!val.startsWith("%")) return null;
            String vname = val.substring(1).replaceAll(",$", "");
            if (vname.equals(waSlotName)) return null;
            int[] s = slots.get(vname);
            if (s == null || s[1] < 4) return null;
            return "(xsp+" + (s[0] + stackAdj) + ")";
        }

        void loadToXHL(String val, String irType, Map<String, int[]> argOffsets) {
            val = val.trim().replaceAll(",$", "");
            int bits = irTypeBits(irType);
            OptionalLong n = parseImmediate(val);
            if (n.isPresent()) {
                long v = n.getAsLong();
                if (v < 0) v = v & ((bits < 64) ? ((1L << bits) - 1) : 0xFFFFFFFFFFFFFFFFL);
                if (bits <= 16) {
                    emit("ld hl, " + fmtImm(v));
                    emit("extz xhl");
                } else {
                    emit("ld xhl, " + fmtImm(v));
                }

                xhlSlotName = null;
                return;
            }

            Matcher mITP = Pattern.compile("inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\)").matcher(val);
            if (mITP.matches()) {
                emit("ld xhl, " + fmtAddr(Integer.parseInt(mITP.group(1))));
                xhlSlotName = null;
                return;
            }

            if (val.startsWith("@")) {
                emit("lda xhl, _" + val.substring(1));
                xhlSlotName = null;
                return;
            }

            if (val.startsWith("%")) {
                String vname = val.substring(1).replaceAll(",$", "");
                // GEP result that was left in XHL without a stack spill â€” no reload needed.
                if (xhlOnly.contains(vname) && vname.equals(xhlSlotName)) {
                    return;
                }

                // If this value is already in XWA/WA, use a register-to-register copy
                // instead of going through the stack.
                if (vname.equals(waSlotName) || (regOnly.contains(vname) && waSlotName != null && waSlotName.equals(vname))) {
                    if (bits <= 16) {
                        emit("ld hl, wa");
                        if (bits <= 8) emit("and hl, 0ffh");
                        emit("extz xhl");
                    } else {
                        emit("ld xhl, xwa");
                    }

                    xhlSlotName = vname;
                    return;
                }

                // Register-promoted: load from XBC / XDE / XIX / XIY directly into XHL.
                // Honor both the LLVM operand width and the register's byte lane. Promoted
                // i8 values live in B and E (not C and D), whereas IX/IY/IZ use their low
                // byte. Copying BC/DE and masking to 8 bits would therefore select the
                // adjacent, unrelated C/D byte.
                if (vname.equals(bcSlotName)) {
                    if (bits <= 8)       { emit("ld hl, 0"); emit("ld l, b"); emit("extz xhl"); }
                    else if (bits <= 16) { emit("ld hl, bc"); emit("extz xhl"); }
                    else                  emit("ld xhl, xbc");
                    xhlSlotName = vname; return;
                }

                if (vname.equals(deSlotName)) {
                    if (bits <= 8)       { emit("ld hl, 0"); emit("ld l, e"); emit("extz xhl"); }
                    else if (bits <= 16) { emit("ld hl, de"); emit("extz xhl"); }
                    else                  emit("ld xhl, xde");
                    xhlSlotName = vname; return;
                }

                if (vname.equals(ixSlotName)) {
                    if (bits <= 16) { emit("ld hl, ix"); if (bits <= 8) emit("and hl, 0ffh"); emit("extz xhl"); }
                    else                   emit("ld xhl, xix");
                    xhlSlotName = vname; return;
                }

                if (vname.equals(iySlotName)) {
                    if (bits <= 16) { emit("ld hl, iy"); if (bits <= 8) emit("and hl, 0ffh"); emit("extz xhl"); }
                    else                   emit("ld xhl, xiy");
                    xhlSlotName = vname; return;
                }

                if (vname.equals(izSlotName)) {
                    if (bits <= 16) { emit("ld hl, iz"); if (bits <= 8) emit("and hl, 0ffh"); emit("extz xhl"); }
                    else                   emit("ld xhl, xiz");
                    xhlSlotName = vname; return;
                }

                for (int i = 0; i < BANK_REG_KEYS.length; i++) {
                    if (useBank[i] && vname.equals(bankSlotName[i])) {
                        if (bits <= 8)       { emit("ld hl, 0"); emit("ld l, " + BANK_REG_B[i]); emit("extz xhl"); }
                        else if (bits <= 16) { emit("ld hl, " + BANK_REG_R[i]); emit("extz xhl"); }
                        else                  emit("ld xhl, " + BANK_REG_X[i]);
                        xhlSlotName = vname; return;
                    }
                }

                if (slots.containsKey(vname)) {
                    int off = slots.get(vname)[0];
                    int sz  = slots.get(vname)[1];
                    if (bits <= 16 || sz <= 2) {
                        emit("ld hl, (xsp+" + off + ")");
                        if (bits <= 8) emit("and hl, 0ffh");
                        emit("extz xhl");
                    } else {
                        emit("ld xhl, (xsp+" + off + ")");
                    }

                    xhlSlotName = vname;
                    // Loading XHL invalidates WA (XHL and XWA share no registers)
                    // but does NOT invalidate waSlotName â€” they are independent.
                    return;
                }

                if (argOffsets.containsKey(vname)) {
                    int total = frameSize + argOffsets.get(vname)[0];
                    int abits = argOffsets.get(vname)[1];
                    if (bits <= 16 || abits <= 16) {
                        emit("ld hl, (xsp+" + total + ")");
                        if (bits <= 8) emit("and hl, 0ffh");
                        emit("extz xhl");
                    } else {
                        emit("ld xhl, (xsp+" + total + ")");
                    }

                    xhlSlotName = null;
                    return;
                }

                emitComment("unresolved xhl val: " + val);
                return;
            }

            // Inline constant-expression GEP used as a pointer VALUE in XHL (e.g. &data[N]).

            {

                OptionalLong ceOff = constExprGepOffset(val.trim());
                if (ceOff.isPresent()) {
                    emit("lda xhl, _" + constExprGepSymbol(val.trim()));
                    if (ceOff.getAsLong() != 0) emit("add xhl, " + ceOff.getAsLong());
                    xhlSlotName = null;
                    return;
                }
            }

            emitComment("unresolved xhl val: " + val);
        }

        /** Mirror a just-stored promoted value (still in WA/XWA) to its backing stack
         *  slot, so the spill-around-call reload after a call finds a current value.
         *  No-op unless the value is live across a call and actually has a slot. */
        void syncPromotedToSlot(String name, int bits, boolean needsSpillAroundCall) {
            if (!needsSpillAroundCall) return;
            if (!slots.containsKey(name)) return;
            int off = slots.get(name)[0];
            if (bits <= 8)       emit("ld (xsp+" + off + "), a");
            else if (bits <= 16) emit("ld (xsp+" + off + "), wa");
            else                 emit("ld (xsp+" + off + "), xwa");
        }

        void storeWAtoSlot(String name, int bits) {
            // Register-only values are never spilled; the consumer reads WA directly.
            if (regOnly.contains(name)) { waSlotName = name; waSlotBits = bits; return; }
            // Register-promoted values go to XBC or XDE instead of the stack.
            if (name.equals(bcSlotName)) {
                if (bits <= 8)       emit("ld b, a");
                else if (bits <= 16) emit("ld bc, wa");
                else                 emit("ld xbc, xwa");
                bcSlotBits = bits; bcLive = name; waSlotName = name; waSlotBits = bits;
                syncPromotedToSlot(name, bits, bcNeedsSpillAroundCall);
                return;
            }

            if (name.equals(deSlotName)) {
                if (bits <= 8)       emit("ld e, a");
                else if (bits <= 16) emit("ld de, wa");
                else                 emit("ld xde, xwa");
                deSlotBits = bits; deLive = name; waSlotName = name; waSlotBits = bits;
                syncPromotedToSlot(name, bits, deNeedsSpillAroundCall);
                return;
            }

            if (name.equals(ixSlotName)) {
                if (bits <= 8)      { emit("and wa, 0ffh"); emit("ld ix, wa"); }
                else if (bits <= 16) emit("ld ix, wa");
                else                 emit("ld xix, xwa");
                ixSlotBits = bits; ixLive = name; waSlotName = name; waSlotBits = bits;
                syncPromotedToSlot(name, bits, ixNeedsSpillAroundCall);
                return;
            }

            if (name.equals(iySlotName)) {
                if (bits <= 8)      { emit("and wa, 0ffh"); emit("ld iy, wa"); }
                else if (bits <= 16) emit("ld iy, wa");
                else                 emit("ld xiy, xwa");
                iySlotBits = bits; iyLive = name; waSlotName = name; waSlotBits = bits;
                syncPromotedToSlot(name, bits, iyNeedsSpillAroundCall);
                return;
            }

            if (name.equals(izSlotName)) {
                if (bits <= 8)      { emit("and wa, 0ffh"); emit("ld iz, wa"); }
                else if (bits <= 16) emit("ld iz, wa");
                else                 emit("ld xiz, xwa");
                izSlotBits = bits; izLive = name; waSlotName = name; waSlotBits = bits;
                syncPromotedToSlot(name, bits, izNeedsSpillAroundCall);
                return;
            }

            // Bank-1/2 register-promoted value: store WA into its bank register. The R
            // operand cannot be an arithmetic destination on this CPU, but a plain
            // `ld <bankreg>, wa` is fine — the value was already computed in WA. Bank
            // registers are a separate physical file, so no stack sync is needed.
            for (int i = 0; i < BANK_REG_KEYS.length; i++) {
                if (name.equals(bankSlotName[i])) {
                    if (bits <= 8)      { emit("and wa, 0ffh"); emit("ld " + BANK_REG_B[i] + ", a"); }
                    else if (bits <= 16) emit("ld " + BANK_REG_R[i] + ", wa");
                    else                 emit("ld " + BANK_REG_X[i] + ", xwa");
                    bankSlotBits[i] = bits; bankLive[i] = name; waSlotName = name; waSlotBits = bits;
                    return;
                }
            }

            if (!slots.containsKey(name)) return;
            int off = slots.get(name)[0];
            if (bits <= 8)       emit("ld (xsp+" + off + "), a");
            else if (bits <= 16) emit("ld (xsp+" + off + "), wa");
            else                 emit("ld (xsp+" + off + "), xwa");
            waSlotName = name;
            waSlotBits = bits;
        }

        // -----------------------------------------------------------------------
        // Instruction dispatch

        // -----------------------------------------------------------------------
        void translateInstr(String instr, Map<String, int[]> argOffsets) {
            String s = instr.strip();
            if (s.startsWith(";") || s.isEmpty()) return;
            // -- alloca (already pre-allocated) --
            if (s.matches("%[^=]+=\\s*alloca\\s+.*")) { emitComment(s); return; }
            // -- memfill / memcopy pseudo-ops (lowered from llvm.memset/memcpy) --
            if (s.startsWith("memfill ") || s.startsWith("memcopy ")) {
                emitMemPseudo(s, argOffsets);
                return;
            }

            // Raw inline-asm function: pass through only the inline asm itself and
            // plain terminators. Drop all frame-relative boilerplate (param spills,
            // slot loads/stores) â€” the asm reads its args directly from the stack.
            if (isRawAsm) {
                Matcher mRawAsm = Pattern.compile(
                    "(?:tail\\s+|musttail\\s+|notail\\s+)?call\\s+void\\s+asm\\s+sideeffect\\s+\"([^\"]*)\".*").matcher(s);
                if (mRawAsm.matches()) {
                    String insn = unescapeLlvmString(mRawAsm.group(1)).trim();
                    if (!insn.isEmpty()) emit(insn);
                    return;
                }

                if (s.equals("ret void") || s.equals("ret")) {
                    if (isInterrupt) emit("reti"); else emit("ret");
                    return;
                }

                // Non-void ret or any other IR: the inline asm already returns on its
                // own paths; drop the residual instruction (recorded as a comment).
                emitComment("raw-asm fn: dropped " + s);
                return;
            }

            // -- store --
            Matcher mStore = Pattern.compile(
                "store\\s+(volatile\\s+)?(\\S+)\\s+(.*?),\\s*ptr\\s+(.*)")
                .matcher(s);
            if (mStore.matches()) {
                emitStore(mStore.group(2), mStore.group(3).trim(),
                          stripAlign(mStore.group(4).trim()), argOffsets);
                return;
            }

            // -- load --
            Matcher mLoad = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*load\\s+(volatile\\s+)?(\\S+),\\s*ptr\\s+(.*)")
                .matcher(s);
            if (mLoad.matches()) {
                emitLoad(mLoad.group(1), mLoad.group(3), stripAlign(mLoad.group(4).trim()), argOffsets);
                return;
            }

            // -- getelementptr --
            // clang-20+ decorates the opcode with any combination of the
            // `inbounds` / `nuw` / `nusw` flag keywords (e.g. `getelementptr
            // inbounds nuw ...`). Strip every leading flag before the type so the
            // per-shape patterns in emitGEP see the bare `<type>, ptr ...` operand.
            Matcher mGep = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*getelementptr\\s+(?:(?:inbounds|nuw|nusw)\\s+)*(.*)")
                .matcher(s);
            if (mGep.matches()) {
                emitGEP(mGep.group(1).replaceAll(",$", ""), mGep.group(2), argOffsets);
                return;
            }

            // -- zext --
            Matcher mZext = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*zext\\s+(\\S+)\\s+(%?\\S+)\\s+to\\s+(\\S+)")
                .matcher(s);
            if (mZext.matches()) {
                String dst = mZext.group(1).replaceAll(",$","");
                String st  = mZext.group(2), src = mZext.group(3), dt = mZext.group(4);
                loadToWA(src, st, argOffsets);
                int sb = irTypeBits(st), db = irTypeBits(dt);
                // Zero-extend explicitly by SOURCE width. loadToWA only clears the
                // upper lanes when it emits a fresh byte/word load; when the source
                // value is already resident in WA (e.g. a prior i8 load left it in A
                // with W dirty), the upper lanes still hold garbage, so we must clear
                // them here based on the declared source type, not on residency.
                if (sb <= 8) {
                    if (db > 8)  emit("extz wa");    // i8 -> i16: A -> W = 0
                    if (db > 16) emit("extz xwa");   // i8 -> i32/i64: clear XWA[31:16]
                } else if (sb <= 16 && db > 16) {
                    emit("extz xwa");                // i16 -> i32/i64
                }

                storeWAtoSlot(dst, Math.min(db, 32));
                return;
            }

            // -- sext --
            Matcher mSext = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*sext\\s+(\\S+)\\s+(%?\\S+)\\s+to\\s+(\\S+)")
                .matcher(s);
            if (mSext.matches()) {
                String dst = mSext.group(1).replaceAll(",$","");
                String st  = mSext.group(2), src = mSext.group(3), dt = mSext.group(4);
                loadToWA(src, st, argOffsets);
                int sb = irTypeBits(st), db = irTypeBits(dt);
                // Byte source: `EXTS WA` sign-extends the low byte (A, bit 7) into the
                // high byte W â€” the i8->i16 form. (There is no byte-register `exts a`;
                // asl rejects it as an invalid operand size.)
                if (sb <= 8  && db > 8)  emit("exts wa");    // byte -> word (i8 -> i16)
                if (sb <= 16 && db > 16) emit("exts xwa");  // word -> long
                storeWAtoSlot(dst, 32);
                return;
            }

            // -- trunc --
            Matcher mTrunc = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*trunc\\s+(\\S+)\\s+(%?\\S+)\\s+to\\s+(\\S+)")
                .matcher(s);
            if (mTrunc.matches()) {
                String dst = mTrunc.group(1).replaceAll(",$","");
                loadToWA(mTrunc.group(3), mTrunc.group(2), argOffsets);
                storeWAtoSlot(dst, irTypeBits(mTrunc.group(4)));
                return;
            }

            // -- freeze --
            // `%dst = freeze <ty> %src` yields a fixed (non-poison) copy of %src.
            // For codegen it is just a copy: materialize src, store into dst's slot.
            Matcher mFreeze = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*freeze\\s+(\\S+)\\s+(%?\\S+)")
                .matcher(s);
            if (mFreeze.matches()) {
                String dst = mFreeze.group(1).replaceAll(",$","");
                String ty  = mFreeze.group(2);
                loadToWA(mFreeze.group(3).replaceAll(",$",""), ty, argOffsets);
                storeWAtoSlot(dst, irTypeBits(ty));
                return;
            }

            // -- ptrtoint --
            Matcher mPti = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*ptrtoint\\s+(\\S+)\\s+(%?\\S+)\\s+to\\s+(\\S+)")
                .matcher(s);
            if (mPti.matches()) {
                String dst = mPti.group(1).replaceAll(",$","");
                loadToWA(mPti.group(3), mPti.group(2), argOffsets);
                storeWAtoSlot(dst, irTypeBits(mPti.group(4)));
                return;
            }

            // -- inttoptr --
            Matcher mItp = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*inttoptr\\s+(\\S+)\\s+(%?\\S+)\\s+to\\s+(\\S+)")
                .matcher(s);
            if (mItp.matches()) {
                String dst = mItp.group(1).replaceAll(",$","");
                loadToWA(mItp.group(3), mItp.group(2), argOffsets);
                storeWAtoSlot(dst, 32);
                return;
            }

            // -- binary ops --
            // Allow any number of poison-generating flags (e.g. `mul nuw nsw`, `add nsw`,
            // `shl nuw nsw`) before the type â€” LLVM emits them in combination.
            Matcher mBin = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*(add|sub|and|or|xor|shl|ashr|lshr|mul|srem|urem|sdiv|udiv)" +
                "\\s+(?:(?:nsw|nuw|exact)\\s+)*(\\S+)\\s+(%?\\S+),\\s*(%?\\S+)")
                .matcher(s);
            if (mBin.matches()) {
                emitBinop(mBin.group(1).replaceAll(",$",""), mBin.group(2), mBin.group(3),
                          mBin.group(4).replaceAll(",$",""), mBin.group(5).replaceAll(",$",""),
                          argOffsets);
                return;
            }

            // -- icmp --
            // clang-20+ may prefix the predicate with the `samesign` flag
            // (e.g. `icmp samesign ugt`); it is a hint only, so skip it.
            Matcher mIcmp = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*icmp\\s+(?:samesign\\s+)?(eq|ne|slt|sgt|sle|sge|ult|ugt|ule|uge)" +
                "\\s+(\\S+)\\s+(%?\\S+),\\s*(%?\\S+)")
                .matcher(s);
            if (mIcmp.matches()) {
                emitIcmp(mIcmp.group(1).replaceAll(",$",""), mIcmp.group(2), mIcmp.group(3),
                         mIcmp.group(4).replaceAll(",$",""), mIcmp.group(5).replaceAll(",$",""),
                         argOffsets);
                return;
            }

            // -- fcmp (floating point â€” approximate with integer) --
            Matcher mFcmp = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*fcmp\\s+(\\S+)\\s+(\\S+)\\s+(%?\\S+),\\s*(%?\\S+)")
                .matcher(s);
            if (mFcmp.matches()) {
                emitComment("fcmp (float) approximated: " + s);
                String dst = mFcmp.group(1).replaceAll(",$","");
                emit("ld wa, 0");
                storeWAtoSlot(dst, 16);   // clean 16-bit boolean (see emitIcmp)
                return;
            }

            // -- unconditional branch --
            Matcher mBr = Pattern.compile("br\\s+label\\s+%([\\w.]+)").matcher(s);
            if (mBr.matches()) {
                emit("jrl " + bbPrefix + mBr.group(1));
                return;
            }

            // -- conditional branch --
            Matcher mCbr = Pattern.compile(
                "br\\s+i1\\s+%([^\\s,]+),\\s*label\\s+%([\\w.]+),\\s*label\\s+%([\\w.]+)")
                .matcher(s);
            if (mCbr.matches()) {
                emitCondBr(mCbr.group(1).replaceAll(",$",""),
                           mCbr.group(2), mCbr.group(3), argOffsets);
                return;
            }

            // -- switch --
            Matcher mSwitch = Pattern.compile(
                "switch\\s+(\\S+)\\s+%([^\\s,]+),\\s*label\\s+%([\\w.]+)\\s*\\[")
                .matcher(s);
            if (mSwitch.matches()) {
                emitSwitch(mSwitch.group(1), mSwitch.group(2).replaceAll(",$",""),
                           mSwitch.group(3), argOffsets);
                return;
            }

            // switch case and bracket lines (collected by emitSwitch)
            if (s.matches("i\\d+\\s+-?\\d+,\\s*label\\s+%[\\w.]+")) return;
            if (s.equals("]")) return;
            // -- call with return value --
            // The optional prefix tolerates multiple call/return attributes in any
            // order, including clang-22's `range(iN A, B)` return-range attribute
            // (e.g. `%x = tail call range(i32 0, 9) i32 @llvm.ctpop.i32(...)`).
            Matcher mCallRet = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*call\\s+(?:(?:zeroext|signext|noundef|tail|notail|musttail|range\\([^)]*\\))\\s+)*" +
                "(\\S+)\\s+@(\\S+)\\((.*)\\)\\s*(?:#\\d+)?\\s*$")
                .matcher(s);
            if (mCallRet.matches()) {
                emitCall(mCallRet.group(1).replaceAll(",$",""),
                         mCallRet.group(2), mCallRet.group(3), mCallRet.group(4), argOffsets);
                return;
            }

            // -- void call --
            Matcher mCallVoid = Pattern.compile(
                "call\\s+(?:(?:zeroext|signext|noundef|tail|notail|musttail|range\\([^)]*\\))\\s+)*" +
                "(\\S+)\\s+@(\\S+)\\((.*)\\)\\s*(?:#\\d+)?\\s*$")
                .matcher(s);
            if (mCallVoid.matches()) {
                emitCall(null, mCallVoid.group(1), mCallVoid.group(2), mCallVoid.group(3), argOffsets);
                return;
            }

            // -- inline asm sideeffect --
            Matcher mAsm = Pattern.compile(
                "call\\s+void\\s+asm\\s+sideeffect\\s+\"([^\"]*)\".*")
                .matcher(s);
            if (mAsm.matches()) {
                String insn = unescapeLlvmString(mAsm.group(1)).trim();
                if (!insn.isEmpty()) emit(insn);
                return;
            }

            // -- ret non-void --
            Matcher mRet = Pattern.compile("ret\\s+(\\S+)\\s+(%?\\S+)").matcher(s);
            if (mRet.matches()) {
                String irType = mRet.group(1);
                String val    = mRet.group(2).replaceAll(",$","");
                int bits = irTypeBits(irType);
                loadToWA(val, irType, argOffsets);
                // CC900: return in XHL/HL/L
                if (bits <= 8)       emit("ld l, a");
                else if (bits <= 16) emit("ld hl, wa");
                else                 emit("ld xhl, xwa");
                if (frameSize > 0) emit("add xsp, " + frameSize + "\t; free frame");
                if (isInterrupt) {
                    if (isrHasBody()) {
                        for (int ri = ISR_REGS.size() - 1; ri >= 0; ri--) emit("pop " + ISR_REGS.get(ri));
                    }

                    emit("reti");
                } else {
                    emitRegRestore();
                    emit("ret");
                }

                return;
            }

            // -- ret void --
            if (s.equals("ret void") || s.equals("ret")) {
                if (frameSize > 0) emit("add xsp, " + frameSize + "\t; free frame");
                if (isInterrupt) {
                    if (isrHasBody()) {
                        for (int ri = ISR_REGS.size() - 1; ri >= 0; ri--) emit("pop " + ISR_REGS.get(ri));
                    }

                    emit("reti");
                } else {
                    emitRegRestore();
                    emit("ret");
                }

                return;
            }

            // -- select (ternary) --
            Matcher mSel = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*select\\s+i1\\s+%([^,]+),\\s*(\\S+)\\s+(%?\\S+),\\s*(\\S+)\\s+(%?\\S+)")
                .matcher(s);
            if (mSel.matches()) {
                emitSelect(mSel.group(1).replaceAll(",$",""), mSel.group(2).replaceAll(",$",""),
                           mSel.group(3), mSel.group(4).replaceAll(",$",""),
                           mSel.group(5), mSel.group(6).replaceAll(",$",""), argOffsets);
                return;
            }

            // -- phi node --
            // Values are materialized by the predecessors (see scanPhis/emitPhiCopies),
            // so the phi itself is a no-op here. Its dst slot is allocated in prePass.
            Matcher mPhi = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*phi\\s+(\\S+)\\s+.*")
                .matcher(s);
            if (mPhi.matches()) {
                emitComment("phi (materialized by predecessors): " + s);
                return;
            }

            // -- bitcast / addrspacecast --
            Matcher mCast = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*(?:bitcast|addrspacecast)\\s+(\\S+)\\s+(%?\\S+)\\s+to\\s+(\\S+)")
                .matcher(s);
            if (mCast.matches()) {
                String dst = mCast.group(1).replaceAll(",$","");
                loadToWA(mCast.group(3), mCast.group(2), argOffsets);
                storeWAtoSlot(dst, 32);
                return;
            }

            // -- unreachable --
            if (s.equals("unreachable")) {
                emit("jp 0ffffh\t; unreachable");
                return;
            }

            emitComment("TODO: " + s);
        }

        // -----------------------------------------------------------------------
        // memfill / memcopy  (lowered from llvm.memset / llvm.memcpy / memmove)
        //
        //   memfill <dst> ; <val> ; <len>   → fill <len> bytes at <dst> with <val>
        //   memcopy <dst> ; <src> ; <len>   → copy <len> bytes <src>→<dst> (LDIR)
        //
        // Operands are raw IR value strings. XIX/XIY/XBC are used as scratch and
        // saved/restored around the operation so any register-promoted live value
        // is preserved. Length is treated as a byte count (matching LDIR's BC).

        // -----------------------------------------------------------------------
        void emitMemPseudo(String s, Map<String, int[]> argOffsets) {
            boolean fill = s.startsWith("memfill ");
            String rest = s.substring(fill ? "memfill ".length() : "memcopy ".length());
            String[] p = rest.split("\\s*;\\s*");
            if (p.length < 3) { emitComment("bad mem pseudo: " + s); return; }
            String dst = p[0].trim(), a1 = p[1].trim(), len = p[2].trim();
            // Stash operands into dedicated scratch slots first (their loads may read
            // promoted registers we are about to clobber). All addresses/counts are
            // 32-bit. These slots are preallocated + pinned (see MEM_*_SLOT) so they do
            // not grow the frame here and keep three distinct offsets.
            int dstOff = slots.containsKey(MEM_DST_SLOT) ? slots.get(MEM_DST_SLOT)[0] : tmpSlot("mem_dst");
            int lenOff = slots.containsKey(MEM_LEN_SLOT) ? slots.get(MEM_LEN_SLOT)[0] : tmpSlot("mem_len");
            int auxOff = slots.containsKey(MEM_AUX_SLOT) ? slots.get(MEM_AUX_SLOT)[0] : tmpSlot("mem_aux");
            loadToXHL(dst, "ptr", argOffsets);
            emit("ld (xsp+" + dstOff + "), xhl");
            loadToWA(len, "i32", argOffsets);
            emit("ld (xsp+" + lenOff + "), xwa");
            int srcOff = -1, valOff = -1;
            if (!fill) {
                srcOff = auxOff;
                loadToXHL(a1, "ptr", argOffsets);
                emit("ld (xsp+" + srcOff + "), xhl");
            } else {
                // Byte fill value into A (constant → immediate; else low byte).
                OptionalLong fv = parseImmediate(a1);
                if (fv.isPresent()) {
                    emit("ld a, " + fmtImm(fv.getAsLong() & 0xFF));
                } else {
                    loadToWA(a1, "i8", argOffsets);   // low byte in A
                }

                valOff = auxOff;
                emit("ld (xsp+" + valOff + "), a");
            }

            // Save scratch registers; adjust slot offsets for the pushes below.
            emit("push xix");
            emit("push xiy");
            emit("push xbc");
            int adj = 12;   // three 4-byte pushes
            if (fill) {
                // memset via overlapping LDIR: seed dst[0] with the fill byte, then
                // copy dst -> dst+1 for len-1 bytes so the byte propagates. Uses only
                // LDIR + supported addressing (no DJNZ / no plain post-increment).
                emit("ld xiy, (xsp+" + (dstOff + adj) + ")");   // src = dst
                emit("ld a, (xsp+" + (valOff + adj) + ")");
                emit("ld (xiy+0), a");                          // seed first byte
                emit("ld xix, xiy");
                emit("inc 1, xix");                             // dst = dst + 1
                emit("ld xbc, (xsp+" + (lenOff + adj) + ")");
                emit("dec 1, xbc");                            // count = len - 1
                // If len was 1, count is 0 → LDIR would wrap (copies 65536). Guard it.
                String skip = ".Lmf_" + func.name() + "_" + (memLoopSeq++);
                emit("cp xbc, 0");
                emit("jr Z, " + skip);
                emit("ldir (xix+), (xiy+)");
                emit(skip + ":");
            } else {
                emit("ld xix, (xsp+" + (dstOff + adj) + ")");   // dst
                emit("ld xiy, (xsp+" + (srcOff + adj) + ")");   // src
                emit("ld xbc, (xsp+" + (lenOff + adj) + ")");   // count
                emit("ldir (xix+), (xiy+)");
            }

            emit("pop xbc");
            emit("pop xiy");
            emit("pop xix");
            // These scratch uses invalidate any tracked register residency.
            waSlotName = null;
            xhlSlotName = null;
        }

        // -----------------------------------------------------------------------
        // store

        // -----------------------------------------------------------------------
        void emitStore(String irType, String valStr, String destStr, Map<String, int[]> argOffsets) {
            int bits = irTypeBits(irType);
            // MMIO inttoptr
            Matcher mITP = Pattern.compile("inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\)").matcher(destStr);
            if (mITP.matches()) {
                int addr = Integer.parseInt(mITP.group(1));
                // Special: storing a function pointer (ptr type)
                if (irType.equals("ptr")) {
                    String v = valStr.trim();
                    if (v.startsWith("@")) {
                        emit("lda xwa, _" + v.substring(1));
                        emit("ld (" + fmtAddr(addr) + "), xwa");
                    } else {
                        loadToWA(valStr, irType, argOffsets);
                        emit("ld (" + fmtAddr(addr) + "), xwa");
                    }

                    return;
                }

                loadToWA(valStr, irType, argOffsets);
                if (bits <= 8)       emit("ld (" + fmtAddr(addr) + "), a");
                else if (bits <= 16) emit("ld (" + fmtAddr(addr) + "), wa");
                else                 emit("ld (" + fmtAddr(addr) + "), xwa");
                return;
            }

            // Global symbol
            if (destStr.startsWith("@")) {
                String sym = "_" + destStr.substring(1);
                loadToWA(valStr, irType, argOffsets);
                if (bits <= 8)       emit("ld (" + sym + "), a");
                else if (bits <= 16) emit("ld (" + sym + "), wa");
                else                 emit("ld (" + sym + "), xwa");
                return;
            }

            // Inline constant-expression GEP destination:
            //   getelementptr ([N x iK], ptr @sym, i32 0, i32 CONST)   (and the i8/struct
            //   forms handled by constExprGepOffset). clang -O3 emits these for unrolled
            //   stores into a global array. Load the value into WA first (lda/add below do
            //   not touch WA), then compute the byte address in XHL and store through it.

            {

                OptionalLong ceOff = constExprGepOffset(destStr.trim());
                if (ceOff.isPresent()) {
                    String sym = "_" + constExprGepSymbol(destStr.trim());
                    long off = ceOff.getAsLong();
                    loadToWA(valStr, irType, argOffsets);
                    emit("lda xhl, " + sym);
                    if (off != 0) emit("add xhl, " + off);
                    if (bits <= 8)       emit("ld (xhl+0), a");
                    else if (bits <= 16) emit("ld (xhl+0), wa");
                    else                 emit("ld (xhl+0), xwa");
                    return;
                }
            }

            // SSA register (alloca slot or pointer)
            if (destStr.startsWith("%")) {
                String vname = destStr.substring(1).replaceAll(",$","");
                // GEP result left in XHL without a stack spill â€” store through it directly.
                if (xhlOnly.contains(vname)) {
                    // A store cannot use (xhl+wa) — WA is needed for the datum,
                    // which collides with the WA index. Materialize the address first by
                    // emitting the deferred `add xhl,xwa`, then load the value and store.
                    if (vname.equals(xhlIndexPending)) {
                        emit("add xhl, xwa");
                        xhlIndexPending = null;
                    }

                    loadToWA(valStr, irType, argOffsets);
                    if (bits <= 8)       emit("ld (xhl+0), a");
                    else if (bits <= 16) emit("ld (xhl+0), wa");
                    else                 emit("ld (xhl+0), xwa");
                    xhlSlotName = null;
                    return;
                }

                // Register-promoted value (alloca or SSA temp). In the multi-pass design
                // a non-escaping promoted value has NO stack slot, so this MUST be handled
                // before the slots.containsKey() guard below â€” otherwise the store is lost.
                // If the value also has a backing slot (escaping alloca), keep it in sync
                // when spill-around-call is required.

                {

                    Integer promoOff = slots.containsKey(vname) ? slots.get(vname)[0] : null;
                    if (vname.equals(bcSlotName)) {
                        loadToWA(valStr, irType, argOffsets);
                        if (bits <= 8)       emit("ld b, a");
                        else if (bits <= 16) emit("ld bc, wa");
                        else                 emit("ld xbc, xwa");
                        bcLive = vname; bcSlotBits = bits;
                        if (bcNeedsSpillAroundCall && promoOff != null) {
                            if (bits <= 8)       emit("ld (xsp+" + promoOff + "), a");
                            else if (bits <= 16) emit("ld (xsp+" + promoOff + "), wa");
                            else                 emit("ld (xsp+" + promoOff + "), xwa");
                        }

                        return;
                    }

                    if (vname.equals(deSlotName)) {
                        loadToWA(valStr, irType, argOffsets);
                        if (bits <= 8)       emit("ld e, a");
                        else if (bits <= 16) emit("ld de, wa");
                        else                 emit("ld xde, xwa");
                        deLive = vname; deSlotBits = bits;
                        if (deNeedsSpillAroundCall && promoOff != null) {
                            if (bits <= 8)       emit("ld (xsp+" + promoOff + "), a");
                            else if (bits <= 16) emit("ld (xsp+" + promoOff + "), wa");
                            else                 emit("ld (xsp+" + promoOff + "), xwa");
                        }

                        return;
                    }

                    if (vname.equals(ixSlotName)) {
                        loadToWA(valStr, irType, argOffsets);
                        if (bits <= 8)      { emit("and wa, 0ffh"); emit("ld ix, wa"); }
                        else if (bits <= 16) emit("ld ix, wa");
                        else                 emit("ld xix, xwa");
                        ixLive = vname; ixSlotBits = bits;
                        if (ixNeedsSpillAroundCall && promoOff != null) {
                            if (bits <= 8)       emit("ld (xsp+" + promoOff + "), a");
                            else if (bits <= 16) emit("ld (xsp+" + promoOff + "), wa");
                            else                 emit("ld (xsp+" + promoOff + "), xwa");
                        }

                        return;
                    }

                    if (vname.equals(iySlotName)) {
                        loadToWA(valStr, irType, argOffsets);
                        if (bits <= 8)      { emit("and wa, 0ffh"); emit("ld iy, wa"); }
                        else if (bits <= 16) emit("ld iy, wa");
                        else                 emit("ld xiy, xwa");
                        iyLive = vname; iySlotBits = bits;
                        if (iyNeedsSpillAroundCall && promoOff != null) {
                            if (bits <= 8)       emit("ld (xsp+" + promoOff + "), a");
                            else if (bits <= 16) emit("ld (xsp+" + promoOff + "), wa");
                            else                 emit("ld (xsp+" + promoOff + "), xwa");
                        }

                        return;
                    }

                    if (vname.equals(izSlotName)) {
                        loadToWA(valStr, irType, argOffsets);
                        if (bits <= 8)      { emit("and wa, 0ffh"); emit("ld iz, wa"); }
                        else if (bits <= 16) emit("ld iz, wa");
                        else                 emit("ld xiz, xwa");
                        izLive = vname; izSlotBits = bits;
                        if (izNeedsSpillAroundCall && promoOff != null) {
                            if (bits <= 8)       emit("ld (xsp+" + promoOff + "), a");
                            else if (bits <= 16) emit("ld (xsp+" + promoOff + "), wa");
                            else                 emit("ld (xsp+" + promoOff + "), xwa");
                        }

                        return;
                    }

                    int bidx = bankIndexOf(vname);
                    if (bidx >= 0) {
                        if (allocaSlots.contains(vname)) {
                            // Promoted scalar home: store the value INTO the bank register.
                            // A bank reg can never be an arith/ld dst except via WA, so
                            // route through WA (loadToWA already left the datum there).
                            loadToWA(valStr, irType, argOffsets);
                            if (bits <= 8)       emit("ld " + BANK_REG_B[bidx] + ", a");
                            else if (bits <= 16) emit("ld " + BANK_REG_R[bidx] + ", wa");
                            else                 emit("ld " + BANK_REG_X[bidx] + ", xwa");
                            bankLive[bidx] = vname; bankSlotBits[bidx] = bits;
                        } else {
                            // Genuine pointer value in the bank register: dereference.
                            // Spill the datum, move the pointer to XHL, store (xhl+0).
                            loadToWA(valStr, irType, argOffsets);
                            int tmp = tmpSlot("store_bank_" + vname);
                            if (bits <= 8)       emit("ld (xsp+" + tmp + "), a");
                            else if (bits <= 16) emit("ld (xsp+" + tmp + "), wa");
                            else                 emit("ld (xsp+" + tmp + "), xwa");
                            emit("ld xhl, " + BANK_REG_X[bidx]);
                            if (bits <= 8)       { emit("ld a, (xsp+" + tmp + ")");   emit("ld (xhl+0), a"); }
                            else if (bits <= 16) { emit("ld wa, (xsp+" + tmp + ")");  emit("ld (xhl+0), wa"); }
                            else                 { emit("ld xwa, (xsp+" + tmp + ")"); emit("ld (xhl+0), xwa"); }
                            xhlSlotName = null;
                        }

                        return;
                    }
                }

                if (slots.containsKey(vname)) {
                    int off = slots.get(vname)[0];
                    if (allocaSlots.contains(vname)) {
                        loadToWA(valStr, irType, argOffsets);
                        if (bits <= 8)       emit("ld (xsp+" + off + "), a");
                        else if (bits <= 16) emit("ld (xsp+" + off + "), wa");
                        else                 emit("ld (xsp+" + off + "), xwa");
                    } else {
                        // Pointer slot â€” dereference
                        loadToWA(valStr, irType, argOffsets);
                        int tmp = tmpSlot("store_" + vname);
                        if (bits <= 8)       emit("ld (xsp+" + tmp + "), a");
                        else if (bits <= 16) emit("ld (xsp+" + tmp + "), wa");
                        else                 emit("ld (xsp+" + tmp + "), xwa");
                        emit("ld xhl, (xsp+" + off + ")");
                        if (bits <= 8)       { emit("ld a, (xsp+" + tmp + ")");  emit("ld (xhl+0), a"); }
                        else if (bits <= 16) { emit("ld wa, (xsp+" + tmp + ")"); emit("ld (xhl+0), wa"); }
                        else                 { emit("ld xwa, (xsp+" + tmp + ")"); emit("ld (xhl+0), xwa"); }
                    }

                    return;
                }

                // Pointer that is a function parameter (lives on the arg stack, e.g.
                // `store <ty> %v, ptr %0` where %0 is a `ptr` param). Materialize the
                // value, then load the pointer from its arg slot and store through it.
                if (argOffsets.containsKey(vname)) {
                    loadToWA(valStr, irType, argOffsets);
                    int tmp = tmpSlot("store_arg_" + vname);
                    if (bits <= 8)       emit("ld (xsp+" + tmp + "), a");
                    else if (bits <= 16) emit("ld (xsp+" + tmp + "), wa");
                    else                 emit("ld (xsp+" + tmp + "), xwa");
                    emit("ld xhl, (xsp+" + (frameSize + argOffsets.get(vname)[0]) + ")");
                    if (bits <= 8)       { emit("ld a, (xsp+" + tmp + ")");   emit("ld (xhl+0), a"); }
                    else if (bits <= 16) { emit("ld wa, (xsp+" + tmp + ")");  emit("ld (xhl+0), wa"); }
                    else                 { emit("ld xwa, (xsp+" + tmp + ")"); emit("ld (xhl+0), xwa"); }
                    return;
                }

                emitComment("store to unknown slot: " + destStr);
                return;
            }

            emitComment("store unhandled dest: " + destStr);
        }

        // -----------------------------------------------------------------------
        // load

        // -----------------------------------------------------------------------
        void emitLoad(String dst, String irType, String srcStr, Map<String, int[]> argOffsets) {
            int bits = irTypeBits(irType);
            // Pointer load whose result is xhlOnly: load the address into XHL directly
            // so the consuming load/store can use (xhl+0) without a stack round-trip.
            if (xhlOnly.contains(dst) && irType.equals("ptr")) {
                loadToXHL(srcStr, irType, argOffsets);
                xhlSlotName = dst;
                return;
            }

            // MMIO inttoptr
            Matcher mITP = Pattern.compile("inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\)").matcher(srcStr);
            if (mITP.matches()) {
                int addr = Integer.parseInt(mITP.group(1));
                if (bits <= 8) {
                    emit("ld wa, 0");   // clear high byte W before byte load
                    emit("ld a, (" + fmtAddr(addr) + ")");
                } else if (bits <= 16) {
                    emit("ld wa, (" + fmtAddr(addr) + ")");
                } else {
                    emit("ld xwa, (" + fmtAddr(addr) + ")");
                }

                storeWAtoSlot(dst, bits);
                return;
            }

            // Constant-expression GEP as the load source, off a global symbol.
            // Two shapes appear depending on clang version:
            //   clang-22: getelementptr inbounds nuw (i8, ptr @levels, i32 4)
            //             â€” pre-folded to a byte offset.
            //   clang-18: getelementptr inbounds ([2 x %struct.LEVEL], ptr @levels,
            //             i32 0, i32 0, i32 2)  â€” array elem + struct field indices.
            // Both address `@sym + constByteOffset`; compute that offset, load the
            // address into XHL, then dereference.
            OptionalLong ceOff = constExprGepOffset(srcStr.trim());
            if (ceOff.isPresent()) {
                String sym = "_" + constExprGepSymbol(srcStr.trim());
                long off = ceOff.getAsLong();
                emit("lda xhl, " + sym);
                if (off != 0) emit("add xhl, " + off);
                if (bits <= 8)       { emit("ld wa, 0"); emit("ld a, (xhl+0)"); }
                else if (bits <= 16)   emit("ld wa, (xhl+0)");
                else                   emit("ld xwa, (xhl+0)");
                storeWAtoSlot(dst, Math.min(bits, 32));
                return;
            }

            // Global symbol
            if (srcStr.startsWith("@")) {
                String sym = "_" + srcStr.substring(1);
                if (bits <= 8) {
                    emit("ld wa, 0");   // clear high byte W before byte load
                    emit("ld a, (" + sym + ")");
                } else if (bits <= 16) {
                    emit("ld wa, (" + sym + ")");
                } else {
                    emit("ld xwa, (" + sym + ")");
                }

                storeWAtoSlot(dst, Math.min(bits, 32));
                return;
            }

            // SSA register
            if (srcStr.startsWith("%")) {
                String vname = srcStr.substring(1).replaceAll(",$","");
                // GEP result left in XHL without a stack spill â€” dereference directly.
                if (xhlOnly.contains(vname)) {
                    // If the GEP deferred its `add xhl,xwa` (base in XHL, index
                    // still in XWA), fuse into a single (xhl+wa) indexed load.
                    if (vname.equals(xhlIndexPending)) {
                        if (bits <= 8) {
                            // Read the byte FIRST (WA still holds the index), then zero-extend.
                            emit("ld a, (xhl+wa)");
                            emit("extz wa");
                        } else if (bits <= 16) {
                            emit("ld wa, (xhl+wa)");
                        } else {
                            emit("ld xwa, (xhl+wa)");
                        }

                        xhlIndexPending = null;
                        xhlSlotName = null;
                        storeWAtoSlot(dst, Math.min(bits, 32));
                        return;
                    }

                    if (bits <= 8)       { emit("ld wa, 0"); emit("ld a, (xhl+0)"); }
                    else if (bits <= 16) emit("ld wa, (xhl+0)");
                    else                 emit("ld xwa, (xhl+0)");
                    xhlSlotName = null;
                    storeWAtoSlot(dst, Math.min(bits, 32));
                    return;
                }

                // Register-promoted value: read from the register directly. In the
                // multi-pass design a non-escaping promoted value has NO stack slot,
                // so this MUST run before the slots.containsKey() guard below â€”
                // otherwise the load falls through to "load from unknown".
                if (vname.equals(bcSlotName)) {
                    if (bits <= 8)       { emit("ld wa, 0"); emit("ld a, b"); }
                    else if (bits <= 16)   emit("ld wa, bc");
                    else                   emit("ld xwa, xbc");
                    storeWAtoSlot(dst, bits); return;
                }

                if (vname.equals(deSlotName)) {
                    if (bits <= 8)       { emit("ld wa, 0"); emit("ld a, e"); }
                    else if (bits <= 16)   emit("ld wa, de");
                    else                   emit("ld xwa, xde");
                    storeWAtoSlot(dst, bits); return;
                }

                if (vname.equals(ixSlotName)) {
                    if (bits <= 8)       { emit("ld wa, ix"); emit("and wa, 0ffh"); }
                    else if (bits <= 16)   emit("ld wa, ix");
                    else                   emit("ld xwa, xix");
                    storeWAtoSlot(dst, bits); return;
                }

                if (vname.equals(iySlotName)) {
                    if (bits <= 8)       { emit("ld wa, iy"); emit("and wa, 0ffh"); }
                    else if (bits <= 16)   emit("ld wa, iy");
                    else                   emit("ld xwa, xiy");
                    storeWAtoSlot(dst, bits); return;
                }

                if (vname.equals(izSlotName)) {
                    if (bits <= 8)       { emit("ld wa, iz"); emit("and wa, 0ffh"); }
                    else if (bits <= 16)   emit("ld wa, iz");
                    else                   emit("ld xwa, xiz");
                    storeWAtoSlot(dst, bits); return;
                }

                {
                    int bi = bankIndexOf(vname);
                    if (bi >= 0) {
                        if (allocaSlots.contains(vname)) {
                            // Promoted scalar home: the register holds the value itself.
                            if (bits <= 8)       { emit("ld wa, 0"); emit("ld a, " + BANK_REG_B[bi]); }
                            else if (bits <= 16)   emit("ld wa, " + BANK_REG_R[bi]);
                            else                   emit("ld xwa, " + BANK_REG_X[bi]);
                            storeWAtoSlot(dst, bits); return;
                        }
                        // Genuine pointer value in the bank register: dereference via XHL.
                        emit("ld xhl, " + BANK_REG_X[bi]);
                        if (bits <= 8)       { emit("ld wa, 0"); emit("ld a, (xhl+0)"); }
                        else if (bits <= 16)   emit("ld wa, (xhl+0)");
                        else                   emit("ld xwa, (xhl+0)");
                        xhlSlotName = null;
                        storeWAtoSlot(dst, Math.min(bits, 32)); return;
                    }
                }

                if (slots.containsKey(vname)) {
                    int off = slots.get(vname)[0];
                    if (allocaSlots.contains(vname)) {
                        if (bits <= 8)  {
                            emit("ld wa, 0");   // clear high byte W before byte load
                            emit("ld a, (xsp+" + off + ")");
                        } else if (bits <= 16) {
                            emit("ld wa, (xsp+" + off + ")");
                        } else {
                            emit("ld xwa, (xsp+" + off + ")");
                        }

                        storeWAtoSlot(dst, bits);
                    } else {
                        // Pointer dereference
                        emit("ld xhl, (xsp+" + off + ")");
                        if (bits <= 8)  {
                            emit("ld wa, 0");   // clear high byte W before byte load
                            emit("ld a, (xhl+0)");
                        } else if (bits <= 16) {
                            emit("ld wa, (xhl+0)");
                        } else {
                            emit("ld xwa, (xhl+0)");
                        }

                        storeWAtoSlot(dst, Math.min(bits, 32));
                    }

                    return;
                }

                if (argOffsets.containsKey(vname)) {
                    // The source is a pointer-typed function argument; `load T, ptr %arg`
                    // means DEREFERENCE it. Load the pointer from its incoming arg slot
                    // into XHL, then read T from (xhl+0). (Previously this loaded the
                    // argument value itself and skipped the dereference, so e.g.
                    // `%c = load i8, ptr %s` returned the low byte of the pointer `%s`
                    // instead of `s[0]`.)
                    int total = frameSize + argOffsets.get(vname)[0];
                    emit("ld xhl, (xsp+" + total + ")");
                    if (bits <= 8)  {
                        emit("ld wa, 0");   // clear high byte W before byte load
                        emit("ld a, (xhl+0)");
                    } else if (bits <= 16) {
                        emit("ld wa, (xhl+0)");
                    } else {
                        emit("ld xwa, (xhl+0)");
                    }

                    storeWAtoSlot(dst, Math.min(bits, 32));
                    return;
                }

                emitComment("load from unknown: " + srcStr);
                return;
            }

            emitComment("load unhandled src: " + srcStr);
        }

        // -----------------------------------------------------------------------
        // getelementptr

        // -----------------------------------------------------------------------
        // Emit the GEP result pointer: if dst is xhlOnly, skip the stack spill and
        // record xhlSlotName so the consuming load/store can skip its reload too.
        void gepStoreXHL(String dst) {
            if (xhlOnly.contains(dst)) {
                xhlSlotName = dst;
            } else if (slots.containsKey(dst)) {
                emit("ld (xsp+" + slots.get(dst)[0] + "), xhl");
                xhlSlotName = dst;
            }
        }

        // Finalize a variable-index GEP whose base is in XHL and whose (possibly
        // pre-scaled) index is in XWA. TLCS-900's `(xhl+wa)` indexed form uses only
        // the low 16 bits of the offset. LLVM pointer indices are 32-bit here and can
        // exceed 64 KiB (for example Mode 7's 512x512 byte track), so always
        // materialize the full address unless a future range analysis proves that the
        // offset fits WA.
        void finishGepIndexed(String dst, String index, long stride) {
            final long WIDE = 0x1_0000L;
            long bound = valueBounds == null ? WIDE
                : Translator.operandBound(index, valueBounds, WIDE);
            boolean fitsWA = bound < WIDE && stride > 0 && bound * stride <= 0xFFFFL;
            if (xhlOnly.contains(dst) && fitsWA) {
                xhlSlotName = dst;
                xhlIndexPending = dst;
                return;
            }

            emit("add xhl, xwa");
            gepStoreXHL(dst);
        }

        void emitGEP(String dst, String rest, Map<String, int[]> argOffsets) {
            rest = rest.trim();
            // [N x i8] ptr @sym, i64 0, i64 %idx
            Matcher m = Pattern.compile("\\[(\\d+)\\s+x\\s+i8\\],\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+0,\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String sym = m.group(2); String idx = m.group(3).replaceAll(",$","");
                loadToWA("%" + idx, "i32", argOffsets);
                emit("lda xhl, _" + sym);
                finishGepIndexed(dst, "%" + idx, 1);
                return;
            }

            // [N x i16] ptr @sym, i64 0, i64 %idx
            m = Pattern.compile("\\[(\\d+)\\s+x\\s+i16\\],\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+0,\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String sym = m.group(2); String idx = m.group(3).replaceAll(",$","");
                loadToWA("%" + idx, "i32", argOffsets);
                emit("sla 1, xwa");   // *2
                emit("lda xhl, _" + sym);
                finishGepIndexed(dst, "%" + idx, 2);
                return;
            }

            // [N x %struct.NAME] ptr @sym, i64 0, i64 %idx [, i32 F [, i32 G]]
            // (array of structs in global ROM/RAM). The optional trailing i32
            // index selects a struct field; -O3 emits e.g.
            //   [2 x %struct.LEVEL], ptr @levels, i64 0, i64 %2, i32 2
            // to address levels[%2].map. Without honoring the field index the
            // address collapses to field 0, so `map`/`h` load from the wrong offset.
            m = Pattern.compile("\\[\\d+\\s+x\\s+%struct\\.(\\w+)\\],\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+0,\\s*i(?:32|64)\\s+%([^,\\s]+)((?:\\s*,\\s*i32\\s+\\d+)*)").matcher(rest);
            if (m.find()) {
                String sname = m.group(1), sym = m.group(2), idx = m.group(3).replaceAll(",$","");
                int fOff = structTrailingFieldOffset(sname, m.group(4));
                int esz = structSize(sname);
                loadToWA("%" + idx, "i32", argOffsets);
                emitMulImm(esz);
                emit("lda xhl, _" + sym);
                if (fOff != 0) {
                    // Materialize element base then add the field byte offset.
                    emit("add xhl, xwa");
                    emit("add xhl, " + fOff);
                    gepStoreXHL(dst);
                } else {
                    finishGepIndexed(dst, "%" + idx, esz);
                }

                return;
            }

            // i16, ptr inttoptr(addr), i64 %idx  (MMIO i16 array)
            m = Pattern.compile("i16,\\s*ptr\\s+inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                int addr = Integer.parseInt(m.group(1)); String idx = m.group(2).replaceAll(",$","");
                loadToWA("%" + idx, "i32", argOffsets);
                emit("sla 1, xwa");
                emit("ld xhl, " + fmtAddr(addr));
                finishGepIndexed(dst, "%" + idx, 2);
                return;
            }

            // i8, ptr inttoptr(addr), i64 %idx  (MMIO i8 array, variable index)
            m = Pattern.compile("i8,\\s*ptr\\s+inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                int addr = Integer.parseInt(m.group(1)); String idx = m.group(2).replaceAll(",$","");
                loadToWA("%" + idx, "i32", argOffsets);
                emit("ld xhl, " + fmtAddr(addr));
                finishGepIndexed(dst, "%" + idx, 1);
                return;
            }

            // <ty>, ptr inttoptr(addr), i64 N  (MMIO array, constant index) â€” any elem size
            m = Pattern.compile("(i8|i16|i32|ptr),\\s*ptr\\s+inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\),\\s*i(?:32|64)\\s+(-?\\d+)").matcher(rest);
            if (m.find()) {
                int esz  = irTypeBytes(m.group(1));
                int addr = Integer.parseInt(m.group(2));
                long n   = Long.parseLong(m.group(3));
                emit("ld xhl, " + fmtAddr((int)(addr + n * esz)));
                gepStoreXHL(dst);
                return;
            }

            // i8, ptr @sym, <ity> %idx  (global byte array, single index)
            // clang canonicalizes `[N x i8], ptr @sym, i32 0, i32 %idx` to this
            // single-index byte GEP off the global symbol directly. The index type
            // is i32 on a 32-bit target (arm-none-eabi datalayout) or i64 on a
            // 64-bit host layout, so accept either.
            m = Pattern.compile("i8,\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String sym = m.group(1).replaceAll(",$",""), idx = m.group(2).replaceAll(",$","");
                loadToWA("%" + idx, "i32", argOffsets);
                emit("lda xhl, _" + sym);
                finishGepIndexed(dst, "%" + idx, 1);
                return;
            }

            // i8, ptr @sym, <ity> N  (global byte array, constant index)
            m = Pattern.compile("i8,\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+(-?\\d+)").matcher(rest);
            if (m.find()) {
                String sym = m.group(1).replaceAll(",$",""); int off = Integer.parseInt(m.group(2));
                emit("lda xhl, _" + sym);
                if (off != 0) emit("add xhl, " + off);
                gepStoreXHL(dst);
                return;
            }

            // i16, ptr @sym, <ity> %idx  (global word array, single variable index)
            m = Pattern.compile("i16,\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String sym = m.group(1).replaceAll(",$",""), idx = m.group(2).replaceAll(",$","");
                loadToWA("%" + idx, "i32", argOffsets);
                emit("sla 1, xwa");   // *2
                emit("lda xhl, _" + sym);
                finishGepIndexed(dst, "%" + idx, 2);
                return;
            }

            // i16, ptr @sym, <ity> N  (global word array, constant index)
            m = Pattern.compile("i16,\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+(-?\\d+)").matcher(rest);
            if (m.find()) {
                String sym = m.group(1).replaceAll(",$",""); int n = Integer.parseInt(m.group(2));
                emit("lda xhl, _" + sym);
                if (n != 0) emit("add xhl, " + (n * 2));
                gepStoreXHL(dst);
                return;
            }

            // ptr, ptr @sym, <ity> %idx  (global array of pointers, 4-byte elems, variable index)
            m = Pattern.compile("ptr,\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String sym = m.group(1).replaceAll(",$",""), idx = m.group(2).replaceAll(",$","");
                loadToWA("%" + idx, "i32", argOffsets);
                emit("sla 2, xwa");   // *4
                emit("lda xhl, _" + sym);
                finishGepIndexed(dst, "%" + idx, 4);
                return;
            }

            // ptr, ptr @sym, <ity> N  (global array of pointers, constant index)
            m = Pattern.compile("ptr,\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+(-?\\d+)").matcher(rest);
            if (m.find()) {
                String sym = m.group(1).replaceAll(",$",""); int n = Integer.parseInt(m.group(2));
                emit("lda xhl, _" + sym);
                if (n != 0) emit("add xhl, " + (n * 4));
                gepStoreXHL(dst);
                return;
            }

            // %struct.NAME, ptr @sym, <ity> %idx  (global array of structs, single index)
            // clang canonicalizes `[N x %struct.NAME], ptr @sym, i32 0, i32 %idx`
            // to this form; the field offset (if any) arrives as a separate byte GEP.
            m = Pattern.compile("%struct\\.(\\w+),\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String sname = m.group(1), sym = m.group(2).replaceAll(",$",""), idx = m.group(3).replaceAll(",$","");
                int esz = structSize(sname);
                loadToWA("%" + idx, "i32", argOffsets);
                emitMulImm(esz);
                emit("lda xhl, _" + sym);
                finishGepIndexed(dst, "%" + idx, esz);
                return;
            }

            // %struct.NAME, ptr @sym, <ity> N  (global array of structs, constant index)
            m = Pattern.compile("%struct\\.(\\w+),\\s*ptr\\s+@(\\S+),\\s*i(?:32|64)\\s+(-?\\d+)").matcher(rest);
            if (m.find()) {
                String sname = m.group(1), sym = m.group(2).replaceAll(",$",""); int n = Integer.parseInt(m.group(3));
                int esz = structSize(sname);
                emit("lda xhl, _" + sym);
                if (n != 0) emit("add xhl, " + (n * esz));
                gepStoreXHL(dst);
                return;
            }

            // i8, ptr %base, <ity> %idx
            m = Pattern.compile("i8,\\s*ptr\\s+%([^,\\s]+),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String base = m.group(1).replaceAll(",$",""), idx = m.group(2).replaceAll(",$","");
                loadToXHL("%" + base, "ptr", argOffsets);
                // loadToWA never touches XHL, so XHL still holds the base pointer.
                loadToWA("%" + idx, "i32", argOffsets);
                finishGepIndexed(dst, "%" + idx, 1);
                return;
            }

            // i8, ptr %base, i32 N  (constant offset)
            m = Pattern.compile("i8,\\s*ptr\\s+%([^,\\s]+),\\s*i(?:32|64)\\s+(-?\\d+)").matcher(rest);
            if (m.find()) {
                String base = m.group(1).replaceAll(",$",""); int off = Integer.parseInt(m.group(2));
                loadToXHL("%" + base, "ptr", argOffsets);
                if (off != 0) emit("add xhl, " + off);
                gepStoreXHL(dst);
                return;
            }

            // i16, ptr %base, i64 %idx
            m = Pattern.compile("i16,\\s*ptr\\s+%([^,\\s]+),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String base = m.group(1).replaceAll(",$",""), idx = m.group(2).replaceAll(",$","");
                loadToXHL("%" + base, "ptr", argOffsets);
                // loadToWA never touches XHL, so XHL still holds the base pointer.
                loadToWA("%" + idx, "i32", argOffsets);
                emit("sla 1, xwa");
                finishGepIndexed(dst, "%" + idx, 2);
                return;
            }

            // i16, ptr %base, i32/i64 N  (constant index)
            m = Pattern.compile("i16,\\s*ptr\\s+%([^,\\s]+),\\s*i(?:32|64)\\s+(-?\\d+)").matcher(rest);
            if (m.find()) {
                String base = m.group(1).replaceAll(",$",""); int n = Integer.parseInt(m.group(2));
                loadToXHL("%" + base, "ptr", argOffsets);
                int byteOff = n * 2;
                if (byteOff != 0) emit("add xhl, " + byteOff);
                gepStoreXHL(dst);
                return;
            }

            // Generic struct array: %struct.NAME, ptr %base, i64 %idx
            m = Pattern.compile("%struct\\.(\\w+),\\s*ptr\\s+%([^,\\s]+),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String sname = m.group(1), base = m.group(2).replaceAll(",$",""), idx = m.group(3).replaceAll(",$","");
                int esz = structSize(sname);
                loadToXHL("%" + base, "ptr", argOffsets);
                // loadToWA never touches XHL, so XHL still holds the base pointer.
                loadToWA("%" + idx, "i32", argOffsets);
                emitMulImm(esz);
                finishGepIndexed(dst, "%" + idx, esz);
                return;
            }

            // Generic struct field: %struct.NAME, ptr %base, i32 0, i32 N
            m = Pattern.compile("%struct\\.(\\w+),\\s*ptr\\s+%([^,\\s]+),\\s*i32\\s+0,\\s*i32\\s+(\\d+)").matcher(rest);
            if (m.find()) {
                String sname = m.group(1), base = m.group(2).replaceAll(",$",""); int fIdx = Integer.parseInt(m.group(3));
                int fOff = structFieldOffset(sname, fIdx);
                loadToXHL("%" + base, "ptr", argOffsets);
                if (fOff != 0) emit("add xhl, " + fOff);
                gepStoreXHL(dst);
                return;
            }

            // ptr, ptr %base, i32/i64 %idx  (array of pointers off a local base; 4-byte elems)
            m = Pattern.compile("ptr,\\s*ptr\\s+%([^,\\s]+),\\s*i(?:32|64)\\s+%([^,\\s]+)").matcher(rest);
            if (m.find()) {
                String base = m.group(1).replaceAll(",$",""), idx = m.group(2).replaceAll(",$","");
                loadToXHL("%" + base, "ptr", argOffsets);
                // loadToWA never touches XHL, so XHL still holds the base pointer.
                loadToWA("%" + idx, "i32", argOffsets);
                emit("sla 2, xwa");   // *4 (pointer element size)
                finishGepIndexed(dst, "%" + idx, 4);
                return;
            }

            // Scalar GEP whose BASE is itself an inline constant-expression GEP:
            //   <iK>, ptr getelementptr(<inner all-const gep>), i32 <idx>
            // Resolve the inner const GEP to _sym+innerOff, then add idx*sizeof(iK).
            m = Pattern.compile(
                "(i8|i16|i32),\\s*ptr\\s+(getelementptr\\s+(?:(?:inbounds|nuw|nusw)\\s+)*\\(.*\\)),\\s*i(?:32|64)\\s+(-?\\d+|%[^,\\s]+)\\s*$")
                .matcher(rest);
            if (m.find()) {
                String elemTy = m.group(1);
                String innerGep = m.group(2).trim();
                String idx = m.group(3).replaceAll(",$","");
                OptionalLong innerOff = constExprGepOffset(innerGep);
                String sym = constExprGepSymbol(innerGep);
                if (innerOff.isPresent() && !sym.isEmpty()) {
                    int esz = irTypeBytes(elemTy);
                    if (idx.startsWith("%")) {
                        loadToWA(idx, "i32", argOffsets);
                        if (esz != 1) emitMulImm(esz);
                        emit("lda xhl, _" + sym);
                        if (innerOff.getAsLong() != 0) emit("add xhl, " + innerOff.getAsLong());
                        emit("add xhl, xwa");
                        gepStoreXHL(dst);
                    } else {
                        long total = innerOff.getAsLong() + Long.parseLong(idx) * esz;
                        emit("lda xhl, _" + sym);
                        if (total != 0) emit("add xhl, " + total);
                        gepStoreXHL(dst);
                    }

                    return;
                }
            }

            // Generic nested-array GEP:
            //   [D0 x [D1 x ... E]], ptr <base>, i32 i0, i32 i1, ..., i32 ik
            // where <base> is @global or %reg and each index is a constant or %reg.
            // Address = base + sum(idx[j] * stride[j]) with stride[j] = byte size of
            // the type reached after peeling j leading dimensions. Covers the 2-D map
            // grids ([15 x [15 x i8]]), 3-D ([12 x [15 x [15 x i8]]]), local arrays
            // ([9 x i8]/[12 x i8]), and arrays whose element is a struct (a trailing
            // field selector is absorbed as a byte offset via the innermost type).
            if (emitNestedArrayGep(dst, rest, argOffsets)) return;
            emitComment("gep unhandled: " + rest.substring(0, Math.min(rest.length(), 80)));
        }

        /**
         * Lower a (possibly multi-dimensional) array GEP. Returns true if handled.
         * Handles a leading `[N x T]` aggregate type, an @global or %reg base, and a
         * list of constant/%reg indices. Constant contributions are folded; each
         * variable index emits an index*stride multiply added into the running XHL.
         */
        boolean emitNestedArrayGep(String dst, String rest, Map<String, int[]> argOffsets) {
            String s = rest.trim();
            if (!s.startsWith("[")) return false;
            // Split off the leading aggregate type (bracket-balanced), then base+indices.
            int depth = 0, end = -1;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c == '[') depth++;
                else if (c == ']') { if (--depth == 0) { end = i; break; } }
            }

            if (end < 0) return false;
            String aggType = s.substring(0, end + 1).trim();
            String afterTy = s.substring(end + 1).trim();
            if (afterTy.startsWith(",")) afterTy = afterTy.substring(1).trim();
            if (!afterTy.startsWith("ptr")) return false;
            afterTy = afterTy.substring(3).trim();
            // base operand: @sym or %reg, then the comma-separated index list.
            Matcher mb = Pattern.compile("^(@[\\w.$]+|%[^,\\s]+)\\s*,?(.*)$", Pattern.DOTALL).matcher(afterTy);
            if (!mb.matches()) return false;
            String base = mb.group(1);
            String idxList = mb.group(2).trim();
            // Parse indices: each "iK <value>" (value = constant or %reg).
            List<String> idxVals = new ArrayList<>();
            Matcher mi = Pattern.compile("i(?:8|16|32|64)\\s+(-?\\d+|%[\\w.$]+)").matcher(idxList);
            while (mi.find()) idxVals.add(mi.group(1));
            if (idxVals.isEmpty()) return false;
            // Compute the stride (byte size) for each index level. Index 0 strides over
            // the whole aggregate `aggType`; index j (j>=1) strides over the element
            // reached after peeling j-1 dimensions of aggType.
            // strides[0] = size(aggType); strides[j>=1] = size(peel(aggType, j)).
            List<String> peeled = new ArrayList<>();  // type at each depth: peeled[0]=aggType
            String t = aggType;
            peeled.add(t);
            for (int j = 1; j < idxVals.size(); j++) {
                String[] hdr = parseArrayHeader(t + " zeroinitializer");
                if (hdr == null) { t = null; peeled.add(null); }
                else { t = hdr[1].trim(); peeled.add(t); }
            }

            // stride for index j is the byte size of the *element* type at that level:
            //   idx 0 walks whole aggregates of type aggType's element? No: for
            //   `[N x T], ptr b, i32 i0, ...` idx0 scales by size(aggType) (walking
            //   past whole [N x T] blocks), idx j>=1 scales by size(element of peeled[j-1]).
            long[] strides = new long[idxVals.size()];
            strides[0] = irTypeBytes(aggType);
            for (int j = 1; j < idxVals.size(); j++) {
                // stride = size of the element type of the (j-1)-th peeled array,
                // i.e. size(peeled[j]) when peeled[j] is the element type after one peel.
                String elemTy = peeled.get(j);
                if (elemTy == null) return false;
                strides[j] = irTypeBytes(elemTy);
            }

            // Fold constant contributions; collect the (single) variable term, if any.
            long constOff = 0;
            String varIdx = null; long varStride = 0; int varCount = 0;
            for (int j = 0; j < idxVals.size(); j++) {
                String v = idxVals.get(j);
                if (v.startsWith("%")) { varIdx = v; varStride = strides[j]; varCount++; }
                else constOff += Long.parseLong(v) * strides[j];
            }

            // Load base into XHL.
            if (base.startsWith("@")) emit("lda xhl, _" + base.substring(1));
            else                      loadToXHL(base, "ptr", argOffsets);
            if (varCount == 0) {
                if (constOff != 0) emit("add xhl, " + constOff);
                gepStoreXHL(dst);
                return true;
            }

            if (varCount == 1) {
                // XHL = base; compute index*stride in XWA and add (+ any const offset).
                loadToWA(varIdx, "i32", argOffsets);   // does not touch XHL
                if (varStride != 1) emitMulImm((int) varStride);
                if (constOff != 0) {
                    // Materialize now; the deferred (xhl+wa) fusion can't carry a const.
                    emit("add xhl, xwa");
                    emit("add xhl, " + constOff);
                    gepStoreXHL(dst);
                } else {
                    finishGepIndexed(dst, varIdx, varStride);
                }

                return true;
            }

            // Two or more variable indices: XHL holds base; add each index*stride.
            // loadToWA/emitMulImm never touch XHL, so accumulate straight into it.
            for (int j = 0; j < idxVals.size(); j++) {
                String v = idxVals.get(j);
                if (!v.startsWith("%")) continue;
                loadToWA(v, "i32", argOffsets);
                if (strides[j] != 1) emitMulImm((int) strides[j]);
                emit("add xhl, xwa");
            }

            if (constOff != 0) emit("add xhl, " + constOff);
            gepStoreXHL(dst);
            return true;
        }

        /** Total byte size of a named struct: parsed layout, else 16 as last resort. */
        int structSize(String sname) {
            StructLayout sl = structLayouts == null ? null : structLayouts.get(sname);
            if (sl != null) return sl.size();
            return 16; // last-resort fallback for an unparsed struct
        }

        /** Byte offset of field #fIdx in a named struct: parsed layout, else fIdx*2 as last resort. */
        int structFieldOffset(String sname, int fIdx) {
            StructLayout sl = structLayouts == null ? null : structLayouts.get(sname);
            if (sl != null) {
                return fIdx < sl.fieldOffsets().length ? sl.fieldOffsets()[fIdx] : sl.size();
            }

            return fIdx * 2; // last-resort fallback for an unparsed struct
        }

        /**
         * Sum the byte offset of a chain of trailing struct field selectors captured
         * as ", i32 N" tokens (e.g. ", i32 2" or ", i32 0, i32 1"). Only the first
         * level indexes struct fields of `sname`; deeper selectors (nested structs)
         * are not modeled here (the project's structs are flat), so any extra index
         * is treated as a field of the same struct. Returns 0 for an empty chain.
         */
        int structTrailingFieldOffset(String sname, String trailing) {
            if (trailing == null || trailing.isBlank()) return 0;
            Matcher fm = Pattern.compile("i32\\s+(\\d+)").matcher(trailing);
            int off = 0;
            while (fm.find()) off += structFieldOffset(sname, Integer.parseInt(fm.group(1)));
            return off;
        }

        /**
         * If `s` is a constant-expression GEP off a global (`getelementptr [flags]
         * (<ty>, ptr @sym, <const indices...>)`), return its constant byte offset
         * from the symbol; otherwise empty. Supports the two clang shapes seen:
         *   (i8, ptr @sym, i32 N)                         -> N                (clang-22)
         *   ([E x %struct.T], ptr @sym, i32 0, i32 e, i32 f...) -> e*size(T)+fieldOff  (clang-18)
         * Only literal indices are handled; a variable index makes it non-constant.
         */
        OptionalLong constExprGepOffset(String s) {
            // clang-22 byte-offset form.
            Matcher mByte = Pattern.compile(
                "getelementptr\\s+(?:(?:inbounds|nuw|nusw)\\s+)*\\(\\s*i8,\\s*ptr\\s+@\\S+,\\s*i(?:32|64)\\s+(-?\\d+)\\s*\\)$")
                .matcher(s);
            if (mByte.matches()) return OptionalLong.of(Long.parseLong(mByte.group(1)));
            // clang-18 array-of-struct field form.
            Matcher mArr = Pattern.compile(
                "getelementptr\\s+(?:(?:inbounds|nuw|nusw)\\s+)*\\(\\s*\\[\\d+\\s+x\\s+%struct\\.(\\w+)\\],\\s*ptr\\s+@\\S+,\\s*i(?:32|64)\\s+0,\\s*i(?:32|64)\\s+(\\d+)((?:\\s*,\\s*i32\\s+\\d+)*)\\s*\\)$")
                .matcher(s);
            if (mArr.matches()) {
                String sname = mArr.group(1);
                int elem = Integer.parseInt(mArr.group(2));
                int fOff = structTrailingFieldOffset(sname, mArr.group(3));
                return OptionalLong.of((long) elem * structSize(sname) + fOff);
            }

            // Array-of-scalar two-index form: [N x iK], ptr @sym, i32 0, i32 CONST
            // Folds to CONST * sizeof(iK). Covers e.g. clang -O3 unrolled stores into
            // a global byte/word array: getelementptr ([256 x i8], ptr @data, i32 0, i32 4).
            Matcher mScal = Pattern.compile(
                "getelementptr\\s+(?:(?:inbounds|nuw|nusw)\\s+)*\\(\\s*\\[\\d+\\s+x\\s+(i\\d+)\\],\\s*ptr\\s+@\\S+,\\s*i(?:32|64)\\s+0,\\s*i(?:32|64)\\s+(-?\\d+)\\s*\\)$")
                .matcher(s);
            if (mScal.matches()) {
                int elemBytes = Math.max(1, irTypeBits(mScal.group(1)) / 8);
                long idx = Long.parseLong(mScal.group(2));
                return OptionalLong.of(idx * elemBytes);
            }

            // General all-constant aggregate GEP: getelementptr (AGG, ptr @sym, i32 c0,
            // i32 c1, ...) where AGG is a (possibly nested) array and/or the indices
            // walk struct fields. Folds every constant index by its level stride.
            Matcher mAgg = Pattern.compile(
                "getelementptr\\s+(?:(?:inbounds|nuw|nusw)\\s+)*\\(\\s*(.+?),\\s*ptr\\s+@\\S+,\\s*((?:i(?:32|64)\\s+-?\\d+\\s*,?\\s*)+)\\)$")
                .matcher(s);
            if (mAgg.matches()) {
                OptionalLong off = foldConstAggGep(mAgg.group(1).trim(), mAgg.group(2));
                if (off.isPresent()) return off;
            }

            return OptionalLong.empty();
        }

        /** Fold a chain of constant indices over an aggregate type into a byte offset.
         *  `aggType` is the GEP source element type (array or %struct.NAME); `idxCsv`
         *  is the "i32 c0, i32 c1, ..." index list. Returns empty if a level can't be
         *  resolved. Index 0 strides over whole `aggType` blocks; deeper indices walk
         *  into array dimensions or struct fields. */
        OptionalLong foldConstAggGep(String aggType, String idxCsv) {
            List<Long> idx = new ArrayList<>();
            Matcher mi = Pattern.compile("i(?:32|64)\\s+(-?\\d+)").matcher(idxCsv);
            while (mi.find()) idx.add(Long.parseLong(mi.group(1)));
            if (idx.isEmpty()) return OptionalLong.empty();
            long off = idx.get(0) * irTypeBytes(aggType);   // walk whole-aggregate blocks
            String t = aggType;
            for (int j = 1; j < idx.size(); j++) {
                if (t.startsWith("[")) {
                    String[] hdr = parseArrayHeader(t + " zeroinitializer");
                    if (hdr == null) return OptionalLong.empty();
                    String elemTy = hdr[1].trim();
                    off += idx.get(j) * irTypeBytes(elemTy);
                    t = elemTy;
                } else if (t.startsWith("%struct.")) {
                    String sname = t.substring("%struct.".length());
                    off += structFieldOffset(sname, (int) (long) idx.get(j));
                    // Descend no further (project structs are flat scalars/arrays).
                    t = "i8";
                } else {
                    return OptionalLong.empty();
                }
            }

            return OptionalLong.of(off);
        }

        /** Extract the @symbol name from a constant-expression GEP (see constExprGepOffset). */
        String constExprGepSymbol(String s) {
            Matcher m = Pattern.compile("ptr\\s+@(\\S+?)[,\\s)]").matcher(s);
            return m.find() ? m.group(1) : "";
        }

        // Emit multiplication of XWA by immediate.
        // Strategy:
        //   1 → nothing
        //   power-of-2 → sla chunks (â‰¤7 per instruction)
        //   n â‰¤ 255 → native `mul bc, e`: load n into BC, A (low byte) into E, BC = BC*E
        //   else → _C9H_mullu / _C9H_mul32 helper
        void emitMulImm(int n) {
            if (n == 1) return;
            if (n > 0 && (n & (n - 1)) == 0) {
                int shift = Long.numberOfTrailingZeros(n);
                while (shift > 0) {
                    int chunk = Math.min(shift, 7);
                    emit("sla " + chunk + ", xwa");
                    shift -= chunk;
                }

            } else if (n > 0 && n <= 255) {
                // `mul bc, e`: BC (16-bit) * E (8-bit) → BC. Load n into BC and
                // the index (in A) into E. Both registers are allocatable and may hold
                // unrelated live SSA values, so preserve the full XBC and XDE registers.
                // In particular, clobbering E here corrupted a live Y array index while
                // scaling X for curmap[x][y], turning collision reads into curmap[x][x].
                emit("push xde");
                emit("ld e, a");
                emit("push xbc");
                emit("ld bc, " + n);
                emit("mul bc, e");
                emit("extz xbc");
                emit("ld xwa, xbc");
                emit("pop xbc");
                emit("pop xde");
            } else {
                emit("ld xhl, " + n);
                emit("push xwa");
                emit("push xhl");
                emit("call _C9H_mullu");
                emit("add xsp, 4");
                emit("ld xwa, xhl");
            }
        }

        // -----------------------------------------------------------------------
        // Binary operations

        // -----------------------------------------------------------------------
        // True when a 32-bit `mul lhs, rhs` can be lowered to the hardware 16x16->32
        // `mul` (which yields the EXACT 32-bit product in XWA when both operands fit
        // 16 bits) instead of the _C9H_mul32 software shift-add helper. Sound because
        // computeBounds over-approximates: a returned bound <= 0xFFFF is a proof the
        // value's high 16 bits are zero, so loading it at 32-bit width leaves the low
        // 16 in WA/HL and `mul xwa,hl` reads exactly those. Constants <= 0xFFFF qualify
        // directly. Any operand whose bound is unknown (WIDE) fails the check, so we
        // fall back to the software helper and never lose correctness.
        boolean mul32OperandsFit16(String lhs, String rhs) {
            if (valueBounds == null) return false;
            final long WIDE = 0x1_0000L;
            return Translator.operandBound(lhs, valueBounds, WIDE) <= 0xFFFFL
                && Translator.operandBound(rhs, valueBounds, WIDE) <= 0xFFFFL;
        }

        // True when both operands of a 32-bit `mul` are u16TruncSafeValues (see field
        // doc): each represents a 16-bit C quantity clang widened to i32, so truncating
        // to the low 16 bits before the hardware `mul` matches CC900 bit-for-bit, even
        // in the (unreachable for these programs) case where the untruncated i32 sum
        // would exceed 0xFFFF.
        boolean mul32OperandsAreU16TruncSafe(String lhs, String rhs) {
            return u16TruncSafeOperand(lhs) && u16TruncSafeOperand(rhs);
        }

        boolean u16TruncSafeOperand(String operand) {
            if (u16TruncSafeValues == null) return false;
            OptionalLong immediate = parseImmediate(operand);
            if (immediate.isPresent()) {
                long value = immediate.getAsLong();
                return value >= 0 && value <= 0xFFFFL;
            }

            if (!operand.startsWith("%")) return false;
            return u16TruncSafeValues.contains(operand.substring(1).replaceAll(",$", ""));
        }

        // Computes u16TruncSafeValues (see field doc) with a two-pass forward scan over
        // the function body, mirroring computeBounds: pass 1 seeds direct zext-from-<=16-
        // bit results and loop-induction phis (via Translator.seedInductionBounds); pass
        // 2 lets values derived from those (through add/sub/and/or/xor/mul/shl) settle.
        Set<String> computeU16TruncSafe() {
            Set<String> safe = new LinkedHashSet<>();
            Pattern pDef    = Pattern.compile("^\\s*%([\\w.]+)\\s*=\\s*(.*)$");
            Pattern pZext16 = Pattern.compile("^zext\\s+i\\d+\\s+\\S+\\s+to\\s+i32$");
            Pattern pOp     = Pattern.compile(
                "^(?:add|sub|and|or|xor|mul)\\s+(?:(?:nsw|nuw|exact|disjoint)\\s+)*i32\\s+(\\S+?),\\s*(\\S+)$");
            Pattern pShl    = Pattern.compile("^shl\\s+(?:(?:nsw|nuw)\\s+)*i32\\s+(\\S+?),\\s*(\\S+)$");
            // Loop counters are always tiny (bounded by the trip count), so they are
            // safe ingredients even though they are not zext-derived.
            Set<String> inductionVars = Translator.seedInductionBounds(func.body(), pDef, 0x1_0000L).keySet();
            for (int pass = 0; pass < 2; pass++) {
                for (String line : func.body()) {
                    Matcher md = pDef.matcher(line);
                    if (!md.matches()) continue;
                    String dst = md.group(1);
                    if (safe.contains(dst)) continue;
                    String rhs = md.group(2).trim().replaceAll(",?\\s*align\\s+\\d+\\s*$", "").trim();
                    if (inductionVars.contains(dst) || pZext16.matcher(rhs).matches()) {
                        safe.add(dst);
                        continue;
                    }

                    Matcher mo = pOp.matcher(rhs);
                    Matcher ms = pShl.matcher(rhs);
                    if (mo.matches()) {
                        if (isU16TruncSafeIngredient(mo.group(1), safe, inductionVars)
                            && isU16TruncSafeIngredient(mo.group(2), safe, inductionVars)) {
                            safe.add(dst);
                        }

                    } else if (ms.matches()) {
                        OptionalLong shiftAmt = parseImmediate(ms.group(2));
                        if (shiftAmt.isPresent() && shiftAmt.getAsLong() >= 0 && shiftAmt.getAsLong() < 32
                            && isU16TruncSafeIngredient(ms.group(1), safe, inductionVars)) {
                            safe.add(dst);
                        }
                    }
                }
            }

            return safe;
        }

        boolean isU16TruncSafeIngredient(String operand, Set<String> safe, Set<String> inductionVars) {
            OptionalLong imm = parseImmediate(operand);
            if (imm.isPresent()) {
                long v = imm.getAsLong();
                return v >= 0 && v <= 0xFFFFL;
            }

            if (!operand.startsWith("%")) return false;
            String name = operand.substring(1).replaceAll(",$", "");
            return safe.contains(name) || inductionVars.contains(name);
        }

        boolean operandFitsSigned16(String operand) {
            OptionalLong immediate = parseImmediate(operand);
            if (immediate.isPresent()) {
                long value = immediate.getAsLong();
                return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
            }

            if (!operand.startsWith("%")) return false;
            return signed16Values.contains(operand.substring(1).replaceAll(",$", ""));
        }

        boolean mul32OperandsFitSigned16(String lhs, String rhs) {
            return operandFitsSigned16(lhs) && operandFitsSigned16(rhs);
        }

        Set<String> computeSigned16Values() {
            Set<String> result = new LinkedHashSet<>();
            Pattern sext = Pattern.compile("^\\s*%([\\w.]+)\\s*=\\s*sext\\s+i(?:8|16)\\s+.+\\s+to\\s+i32\\s*$");
            for (String line : func.body()) {
                Matcher matcher = sext.matcher(line);
                if (matcher.matches()) result.add(matcher.group(1));
            }

            return result;
        }

        // True when a 32-bit mul may be lowered to the hardware 16x16->32 operation.
        // Address use alone is not sufficient: arrays can exceed 64 KiB, so both operands
        // must be proven to fit 16 bits to preserve the exact 32-bit byte offset. Falls
        // back to mul32OperandsAreU16TruncSafe (see field doc on u16TruncSafeValues) for
        // operands whose numeric bound cannot be proven <= 0xFFFF only because they add
        // a small loop counter to an unbounded-looking (but genuinely 16-bit) global.
        boolean mul32CanUseHardware(String dst, String lhs, String rhs) {
            return mul32OperandsFit16(lhs, rhs) || mul32OperandsAreU16TruncSafe(lhs, rhs);
        }

        // Find every i32 SSA value whose sole uses are getelementptr index operands.
        // A GEP is written `getelementptr [inbounds] <ty>, ptr <base>, i32 <idx>...`; the
        // base pointer is a `ptr` operand and the index/indices are the trailing operands.
        // A value qualifies only if EVERY textual use of it appears strictly as a GEP
        // index (never as the base ptr, never in any non-GEP instruction). Conservative:
        // any unrecognized use disqualifies it.
        Set<String> computeAddrOnlyMulResults() {
            Pattern pDef  = Pattern.compile("^\\s*%([\\w.]+)\\s*=\\s*(.*)$");
            Pattern pMul  = Pattern.compile("^mul\\s+(?:(?:nsw|nuw)\\s+)*i32\\s+\\S+.*$");
            Pattern pName = Pattern.compile("%([\\w.]+)");
            Pattern pGep  = Pattern.compile("getelementptr\\b(.*)$");
            // Candidate mul results (i32).
            Set<String> muls = new LinkedHashSet<>();
            for (String line : func.body()) {
                Matcher md = pDef.matcher(line);
                if (md.matches() && pMul.matcher(md.group(2).trim()).matches()) muls.add(md.group(1));
            }

            if (muls.isEmpty()) return Set.of();
            // For each candidate, scan all uses. Disqualify on any non-GEP-index use.
            Set<String> disq = new HashSet<>();
            for (String line : func.body()) {
                Matcher md = pDef.matcher(line);
                String rhs = md.matches() ? md.group(2).trim() : line.trim();
                Matcher mg = pGep.matcher(rhs);
                if (mg.find()) {
                    // GEP: the base pointer token is the first `ptr <tok>` after the type;
                    // index operands are the rest. Disqualify a candidate used as the base.
                    // Tokens of the form `ptr %x` → %x is a base (disqualifies); every other
                    // %name in a GEP is an index (allowed).
                    String gep = mg.group(1);
                    Matcher pb = Pattern.compile("ptr\\s+%([\\w.]+)").matcher(gep);
                    Set<String> bases = new HashSet<>();
                    while (pb.find()) bases.add(pb.group(1));
                    for (String b : bases) if (muls.contains(b)) disq.add(b);
                    // (index uses are fine â€” do nothing)
                } else {
                    // Non-GEP line: any candidate appearing here (as operand or anywhere)
                    // is disqualified.
                    Matcher mu = pName.matcher(rhs);
                    while (mu.find()) if (muls.contains(mu.group(1))) disq.add(mu.group(1));
                }
            }

            Set<String> ok = new LinkedHashSet<>(muls);
            ok.removeAll(disq);
            return ok;
        }

        void emitBinop(String dst, String op, String itype, String lhs, String rhs,
                       Map<String, int[]> argOffsets) {
            int bits = irTypeBits(itype);
            OptionalLong rhsN = parseImmediate(rhs);
            // If the RHS already has a permanent promoted register, use it directly
            // instead of shuttling LHS through HL and reloading RHS into WA. These are
            // ordinary register-register forms already used throughout the emitter.
            if (bits >= 16 && bits <= 32 && rhsN.isEmpty() && rhs.startsWith("%")
                    && (op.equals("add") || op.equals("sub") || op.equals("and")
                        || op.equals("or") || op.equals("xor"))) {
                String rhsName = rhs.substring(1).replaceAll(",$", "");
                String rhsReg = null;
                int rhsBits = 0;
                if (rhsName.equals(bcSlotName)) { rhsReg = bits == 32 ? "xbc" : "bc"; rhsBits = bcSlotBits; }
                else if (rhsName.equals(deSlotName)) { rhsReg = bits == 32 ? "xde" : "de"; rhsBits = deSlotBits; }
                else if (rhsName.equals(ixSlotName)) { rhsReg = bits == 32 ? "xix" : "ix"; rhsBits = ixSlotBits; }
                else if (rhsName.equals(iySlotName)) { rhsReg = bits == 32 ? "xiy" : "iy"; rhsBits = iySlotBits; }
                else if (rhsName.equals(izSlotName)) { rhsReg = bits == 32 ? "xiz" : "iz"; rhsBits = izSlotBits; }
                else {
                    for (int i = 0; i < BANK_REG_KEYS.length; i++) {
                        if (useBank[i] && rhsName.equals(bankSlotName[i])) {
                            rhsReg = bits == 32 ? BANK_REG_X[i] : BANK_REG_R[i];
                            rhsBits = bankSlotBits[i];
                            break;
                        }
                    }
                }
                if (rhsReg != null && rhsBits >= bits) {
                    loadToWA(lhs, itype, argOffsets);
                    emit(op + " " + (bits == 32 ? "xwa" : "wa") + ", " + rhsReg);
                    storeWAtoSlot(dst, bits);
                    return;
                }
            }

            // For a COMMUTATIVE op with a non-immediate RHS, if the
            // RHS value is already resident in WA (e.g. it was just produced by the
            // preceding instruction) but the LHS is not, swapping operands lets us copy
            // the resident RHS into HL with no reload, then load LHS into WA. Because the
            // op is commutative the result is identical, and we save one stack reload.
            // Guarded by the WA cache (waSlotName), which emit() invalidates soundly.
            boolean commutative = switch (op) { case "add","and","or","xor","mul" -> true; default -> false; };
            boolean rhsResident = regChain && rhsN.isEmpty() && commutative
                    && rhs.startsWith("%")
                    && waSlotName != null
                    && rhs.substring(1).replaceAll(",$","").equals(waSlotName)
                    // The resident value must be at least as wide as the op, so the
                    // `ld hl,wa` copy carries all the bits the op reads (no stale high byte).
                    && waSlotBits >= Math.min(bits, 32);
            if (rhsResident) {
                // RHS already in WA → HL; then load LHS into WA; then commutative op.
                if (bits <= 16) emit("ld hl, wa");
                else            emit("ld xhl, xwa");
                waSlotName = null;
                loadToWA(lhs, itype, argOffsets);
                switch (op) {
                    case "add" -> { if (bits<=16) emit("add hl, wa"); else emit("add xhl, xwa"); }
                    case "and" -> { if (bits<=16) emit("and hl, wa"); else emit("and xhl, xwa"); }
                    case "or"  -> { if (bits<=16) emit("or hl, wa");  else emit("or xhl, xwa"); }
                    case "xor" -> { if (bits<=16) emit("xor hl, wa"); else emit("xor xhl, xwa"); }
                    case "mul" -> {
                        if (bits <= 16) {
                            emit("mul xwa, hl");
                            storeWAtoSlot(dst, Math.min(bits, 32));
                            return;
                        }

                        // 32-bit mul whose operands both fit 16 bits, or whose result is
                        // only an address index (low 16 bits are all that's observable):
                        // the hardware 16x16->32 `mul xwa,hl` computes it in one
                        // instruction (LHS in WA, RHS in HL). Avoids _C9H_mul32.
                        if (mul32CanUseHardware(dst, lhs, rhs)) {
                            emit("mul xwa, hl");
                            storeWAtoSlot(dst, Math.min(bits, 32));
                            return;
                        }

                        if (mul32OperandsFitSigned16(lhs, rhs)) {
                            emit("muls xwa, hl");
                            storeWAtoSlot(dst, Math.min(bits, 32));
                            return;
                        }

                        emit("push xwa");
                        emit("push xhl");
                        emit("call _C9H_mul32");
                        emit("add xsp, 8");
                        emit("ld xwa, xhl");
                        storeWAtoSlot(dst, Math.min(bits, 32));
                        return;
                    }

                    default -> {}
                }

                if (bits <= 16) emit("ld wa, hl");
                else            emit("ld xwa, xhl");
                storeWAtoSlot(dst, Math.min(bits, 32));
                return;
            }

            loadToWA(lhs, itype, argOffsets);
            if (rhsN.isPresent()) {
                long rv = rhsN.getAsLong();
                if (rv < 0 && bits < 64) rv = rv & ((1L << bits) - 1);
                switch (op) {
                    case "add"  -> { if (bits<=8) emit("add a, " + fmtImm(rv)); else if (bits<=16) emit("add wa, " + fmtImm(rv)); else emit("add xwa, " + fmtImm(rv)); }
                    case "sub"  -> { if (bits<=8) emit("sub a, " + fmtImm(rv)); else if (bits<=16) emit("sub wa, " + fmtImm(rv)); else emit("sub xwa, " + fmtImm(rv)); }
                    case "and"  -> { if (bits<=8) emit("and a, " + fmtImm(rv)); else if (bits<=16) emit("and wa, " + fmtImm(rv)); else emit("and xwa, " + fmtImm(rv)); }
                    case "or"   -> { if (bits<=8) emit("or a, "  + fmtImm(rv)); else if (bits<=16) emit("or wa, "  + fmtImm(rv)); else emit("or xwa, "  + fmtImm(rv)); }
                    case "xor"  -> { if (bits<=8) emit("xor a, " + fmtImm(rv)); else if (bits<=16) emit("xor wa, " + fmtImm(rv)); else emit("xor xwa, " + fmtImm(rv)); }
                    case "shl"  -> emitShift("sla", (int)rv, bits<=16?"wa":"xwa");
                    case "ashr" -> emitShift("sra", (int)rv, bits<=16?"wa":"xwa");
                    case "lshr" -> emitShift("srl", (int)rv, bits<=16?"wa":"xwa");
                    case "mul"  -> emitMulImmBinop(bits, rv, lhs);
                    case "sdiv","udiv" -> emitDiv(op, dst, itype, argOffsets, true, rv);
                    case "srem","urem" -> emitRem(op, dst, itype, argOffsets, true, rv);
                    default -> emitComment("binop: " + op);
                }

            } else {
                // For add/sub/and/or/xor: save LHS via reg-to-reg copy into XHL/HL,
                // then load RHS into XWA/WA, then operate â€” no tmp stack slot needed.
                // For shift/mul/div where RHS must go into XHL and LHS must be in XWA,
                // we still need a tmp spill for the LHS.
                boolean canUseLdHL = switch (op) {
                    case "add","sub","and","or","xor" -> true;
                    default -> false;
                };

                if (canUseLdHL) {
                    // Fast path: for a 32-bit add/sub whose RHS is a full-dword
                    // spill slot, operate directly on memory. LHS is already in
                    // XWA (loaded above), so `op xwa,(xsp+N)` computes the result
                    // in place — eliminating the ld xhl,xwa / reload / ld xwa,xhl
                    // round-trip (4 instrs -> 1). add is commutative; sub is A-B
                    // with A already in XWA, so `sub xwa,(mem_B)` is correct.
                    String rhsMem = (bits == 32 && (op.equals("add") || op.equals("sub")))
                            ? dwordSlotMem(rhs, 0) : null;
                    if (rhsMem != null) {
                        emit(op + " xwa, " + rhsMem);
                        waSlotName = null;
                        storeWAtoSlot(dst, Math.min(bits, 32));
                        return;
                    }

                    // LHS is in XWA. Copy to XHL, then load RHS into XWA.
                    if (bits <= 16) emit("ld hl, wa");
                    else            emit("ld xhl, xwa");
                    // waSlotName is now stale (we're about to overwrite XWA with RHS)
                    waSlotName = null;
                    loadToWA(rhs, itype, argOffsets);
                    switch (op) {
                        case "add" -> { if (bits<=16) emit("add hl, wa");   else emit("add xhl, xwa"); }
                        case "sub" -> { if (bits<=16) emit("sub hl, wa");   else emit("sub xhl, xwa"); }
                        case "and" -> { if (bits<=16) emit("and hl, wa");   else emit("and xhl, xwa"); }
                        case "or"  -> { if (bits<=16) emit("or hl, wa");    else emit("or xhl, xwa"); }
                        case "xor" -> { if (bits<=16) emit("xor hl, wa");   else emit("xor xhl, xwa"); }
                        default -> {}
                    }

                    // Result is now in XHL/HL â€” move to XWA/WA for storeWAtoSlot.
                    if (bits <= 16) emit("ld wa, hl");
                    else            emit("ld xwa, xhl");
                } else {
                    // shift / mul / div: LHS is in XWA, load RHS into XHL.
                    // loadToXHL only emits ld xhl,... â€” it never touches XWA â€”
                    // so LHS is still in XWA after the call; no tmp spill needed.
                    loadToXHL(rhs, itype, argOffsets);
                    switch (op) {
                        case "shl"  -> {
                            if (bits <= 16) { emit("push wa"); emit("push hl"); emit("call _C9H_sll"); emit("add xsp, 4"); emit("ld wa, hl"); }
                            else            { emit("push xwa"); emit("push hl"); emit("call _C9H_sll32"); emit("add xsp, 6"); emit("ld xwa, xhl"); }
                        }

                        case "ashr" -> {
                            if (bits <= 16) { emit("push wa"); emit("push hl"); emit("call _C9H_sra"); emit("add xsp, 4"); emit("ld wa, hl"); }
                            else            { emit("push xwa"); emit("push hl"); emit("call _C9H_sra32"); emit("add xsp, 6"); emit("ld xwa, xhl"); }
                        }

                        case "lshr" -> {
                            if (bits <= 16) { emit("push wa"); emit("push hl"); emit("call _C9H_srl"); emit("add xsp, 4"); emit("ld wa, hl"); }
                            else            { emit("push xwa"); emit("push hl"); emit("call _C9H_srl32"); emit("add xsp, 6"); emit("ld xwa, xhl"); }
                        }

                        case "mul"  -> {
                            if (bits <= 16) {
                                // Native word-form multiply: XWA <- WA(16) x HL(16), a
                                // full 16x16->32 product (TLCS-900 spec p81). The low 16
                                // (WA) is exactly the 16-bit product for all inputs, so
                                // this is bit-identical to the old _C9H_mullu shift-add
                                // loop but a single instruction. LHS is in WA, RHS in HL.
                                emit("mul xwa, hl");
                            } else if (mul32CanUseHardware(dst, lhs, rhs)) {
                                // 32-bit mul, either operands proven to fit 16 bits or the
                                // result used only as an address index: the hardware `mul`
                                // gives the (low-16-correct) product in XWA. Avoids _C9H_mul32.
                                emit("mul xwa, hl");
                            } else if (mul32OperandsFitSigned16(lhs, rhs)) {
                                emit("muls xwa, hl");
                            } else {
                                emit("push xwa");   // LHS
                                emit("push xhl");   // RHS → (xsp+4)
                                emit("call _C9H_mul32");
                                emit("add xsp, 8");
                                emit("ld xwa, xhl");
                            }
                        }

                        case "sdiv","udiv" -> emitDiv(op, dst, itype, argOffsets, false, 0);
                        case "srem","urem" -> emitRem(op, dst, itype, argOffsets, false, 0);
                        default -> emitComment("binop: " + op);
                    }
                }
            }

            storeWAtoSlot(dst, Math.min(bits, 32));
        }

        void emitShift(String mnemonic, int count, String reg) {
            while (count > 0) {
                int chunk = Math.min(count, 7);
                emit(mnemonic + " " + chunk + ", " + reg);
                count -= chunk;
            }
        }

        // Immediate multiply for emitBinop: LHS already in XWA/WA. `lhs` is the LHS
        // operand name (for the 16-bit-fit check on the wide path).
        void emitMulImmBinop(int bits, long rv, String lhs) {
            if (rv == 1) { /* no-op */ return; }
            if (bits <= 16) {
                if (rv > 0 && (rv & (rv - 1)) == 0) {
                    int shift = Long.numberOfTrailingZeros(rv);
                    while (shift > 0) { int c = Math.min(shift, 7); emit("sla " + c + ", wa"); shift -= c; }
                } else if (rv > 0 && rv <= 255) {
                    // `mul bc, e`: load rv into BC, WA low byte (A) into E → BC = BC*E.
                    // Preserve XBC and XDE: both are allocatable and may hold live
                    // values (see emitMulImm) that the multiply would otherwise destroy.
                    emit("push xde");
                    emit("ld e, a");
                    emit("push xbc");
                    emit("ld bc, " + fmtImm(rv));
                    emit("mul bc, e");
                    emit("ld wa, bc");
                    emit("pop xbc");
                    emit("pop xde");
                } else {
                    // Native word-form multiply: load the constant into HL and multiply
                    // XWA <- WA(16) x HL(16). Low 16 = the 16-bit product (spec p81).
                    emit("ld hl, " + fmtImm(rv));
                    emit("mul xwa, hl");
                }

            } else {
                if (rv > 0 && (rv & (rv - 1)) == 0) {
                    int shift = Long.numberOfTrailingZeros(rv);
                    while (shift > 0) { int c = Math.min(shift, 7); emit("sla " + c + ", xwa"); shift -= c; }
                } else if (rv >= 0 && rv <= 0xFFFFL && (mul32OperandsFit16(lhs, Long.toString(rv))
                        || mul32OperandsAreU16TruncSafe(lhs, Long.toString(rv)))) {
                    // 32-bit mul by a constant that fits 16 bits, with the LHS proven (or
                    // known u16-trunc-safe; see mul32OperandsAreU16TruncSafe) to fit 16
                    // bits too: hardware 16x16->32 `mul` gives the exact product.
                    emit("ld hl, " + fmtImm(rv));
                    emit("mul xwa, hl");
                } else if (mul32OperandsFitSigned16(lhs, Long.toString(rv))) {
                    emit("muls xwa, " + fmtImm(rv));
                } else {
                    emit("push xwa");
                    emit("ld xhl, " + fmtImm(rv));
                    emit("push xhl");
                    emit("call _C9H_mul32");
                    emit("add xsp, 8");
                    emit("ld xwa, xhl");
                }
            }
        }

        void emitDiv(String op, String dst, String itype, Map<String, int[]> argOffsets,
                     boolean rhsImm, long rhsVal) {
            boolean isUnsigned = op.equals("udiv");
            int bits = irTypeBits(itype);
            // Native word-form divide for 16-bit ops (spec p85-88):
            //   DIV  RR,r : RR(32) / r(16) -> quotient in RR low 16, remainder in high 16
            //   DIVS RR,r : signed variant.
            // A 16-bit dividend extended to 32 bits guarantees the quotient fits 16 bits
            // (no overflow) and the remainder is < divisor, so this is exact. LHS is in
            // WA; extend it into XBC, load divisor into HL, and use the register-source
            // form (op2=0x51/0x59) â€” NOT the immediate form (op2=0x0A/0x0B), which the
            // simulator does not decode.
            if (bits <= 16) {
                // XBC is allocatable and may hold a live SSA value, so save it around
                // the divide (which needs XBC as the 32-bit dividend/result register).
                emit("push xbc");
                if (isUnsigned) emit("extz xwa"); else emit("exts xwa");
                emit("ld xbc, xwa");                 // dividend (sign/zero-extended) -> XBC
                if (rhsImm) emit("ld hl, " + fmtImm(rhsVal));
                // else: divisor already in HL (loadToXHL by caller)
                emit(isUnsigned ? "div xbc, hl" : "divs xbc, hl");
                emit("ld wa, bc");                   // quotient (low 16) -> WA
                emit("pop xbc");
                return;
            }

            String helper = op.equals("sdiv") ? "_C9H_divls" : "_C9H_divlu";
            // The helper returns its secondary result in XDE, which is otherwise
            // callee-saved and may hold a promoted value in the caller.
            emit("push xde");
            if (rhsImm) {
                emit("push xwa");
                emit("ld xhl, " + fmtImm(rhsVal));
                emit("push xhl");
            } else {
                emit("push xwa");
                emit("push xhl"); // rhs already in XHL
            }

            emit("call " + helper);
            emit("add xsp, 8");
            emit("ld xwa, xhl"); // quotient
            emit("pop xde");
        }

        void emitRem(String op, String dst, String itype, Map<String, int[]> argOffsets,
                     boolean rhsImm, long rhsVal) {
            boolean isUnsigned = op.equals("urem");
            int bits = irTypeBits(itype);
            // Native word-form divide for 16-bit ops: the remainder lands in the UPPER
            // 16 bits of XBC (spec p85-88). Extend the 16-bit dividend into XBC, divide
            // by HL (register-source form), then relocate the high word to WA. Sound for
            // any 16-bit dividend/divisor (see emitDiv). Signed uses DIVS; the remainder
            // takes the dividend's sign, matching C srem.
            if (bits <= 16) {
                // XBC is allocatable and may hold a live SSA value, so save it around
                // the divide (which needs XBC as the 32-bit dividend/result register).
                emit("push xbc");
                if (isUnsigned) emit("extz xwa"); else emit("exts xwa");
                emit("ld xbc, xwa");                 // dividend -> XBC
                if (rhsImm) emit("ld hl, " + fmtImm(rhsVal));
                emit(isUnsigned ? "div xbc, hl" : "divs xbc, hl");
                // remainder is XBC<31:16>; bring it down to BC (16-bit shift = 7+7+2).
                emit("srl 7, xbc");
                emit("srl 7, xbc");
                emit("srl 2, xbc");
                emit("ld wa, bc");                   // remainder -> WA
                emit("pop xbc");
                return;
            }

            String helper = op.equals("srem") ? "_C9H_divls" : "_C9H_divlu";
            // Preserve a promoted XDE value, but copy the helper's remainder out first.
            emit("push xde");
            if (rhsImm) {
                emit("push xwa");
                emit("ld xhl, " + fmtImm(rhsVal));
                emit("push xhl");
            } else {
                emit("push xwa");
                emit("push xhl");
            }

            emit("call " + helper);
            emit("add xsp, 8");
            emit("ld xwa, xde"); // remainder
            emit("pop xde");
        }

        // -----------------------------------------------------------------------
        // icmp

        // -----------------------------------------------------------------------
        void emitIcmp(String dst, String pred, String itype, String lhs, String rhs,
                      Map<String, int[]> argOffsets) {
            int bits = irTypeBits(itype);
            boolean isSigned = pred.equals("slt") || pred.equals("sgt")
                            || pred.equals("sle") || pred.equals("sge");
            // Signed byte compares must sign-extend. loadToWA zero-extends bytes,
            // which turns a negative i8 into a positive 16-bit value and breaks
            // LT/GT/LE/GE. Widen to i16 with sign extension.
            boolean signByte = isSigned && bits <= 8;
            String lhsReg = bits <= 16 ? "wa"  : "xwa";
            String rhsReg = bits <= 16 ? "hl"  : "xhl";
            OptionalLong rhsN = parseImmediate(rhs);
            if (rhsN.isPresent()) {
                // RHS is an immediate â€” load LHS into WA/XWA, compare directly.
                long rv = rhsN.getAsLong();
                if (rv < 0 && bits < 64 && !signByte) rv = rv & ((1L << bits) - 1);
                if (signByte && rv < 0) rv = rv & 0xFFFF;
                loadToWA(lhs, itype, argOffsets);
                if (signByte) emit("exts wa");
                emit("cp " + lhsReg + ", " + fmtImm(rv));
            } else {
                // RHS is a variable â€” load RHS into XHL first (doesn't touch XWA),
                // then load LHS into XWA and compare. No tmp spill needed.
                if (signByte) {
                    loadToWA(rhs, itype, argOffsets);
                    emit("exts wa");
                    emit("ld hl, wa");
                } else {
                    loadToXHL(rhs, itype, argOffsets);
                }

                loadToWA(lhs, itype, argOffsets);
                if (signByte) emit("exts wa");
                emit("cp " + lhsReg + ", " + rhsReg);
            }

            String cc = switch (pred) {
                case "eq"  -> "Z";
                case "ne"  -> "NZ";
                case "slt" -> "LT";
                case "sgt" -> "GT";
                case "sle" -> "LE";
                case "sge" -> "GE";
                case "ult" -> "C";
                case "ugt" -> "UGT";
                case "ule" -> "ULE";
                case "uge" -> "NC";
                default    -> "NZ";
            };

            emit("scc " + cc + ", a");
            if (isFirstOfImmediateBooleanAnd(dst)) {
                emit("ld l, a");
                booleanAndLeftInL = dst;
                waSlotName = null;
                return;
            }

            // A is 0 or 1; W is already 0 from the preceding `ld wa,0` / `cp` sequence.
            // Store as 8-bit boolean so emitCondBr can test with `or a,a` â€” no `ld w,0` needed.
            storeWAtoSlot(dst, 8);
        }

        boolean isFirstOfImmediateBooleanAnd(String name) {
            if (currentBlock == null || currentInstrIndex < 0
                    || currentInstrIndex + 2 >= currentBlock.instrs().size()
                    || valueUseCount(name) != 1) return false;
            String next = currentBlock.instrs().get(currentInstrIndex + 1).strip();
            Matcher nextCmp = Pattern.compile(
                "%([^\\s,=]+)\\s*=\\s*icmp\\s+(?:samesign\\s+)?"
                + "(?:eq|ne|slt|sgt|sle|sge|ult|ugt|ule|uge)\\s+\\S+\\s+%?\\S+,\\s*(%?\\S+)")
                .matcher(next);
            if (!nextCmp.matches() || parseImmediate(nextCmp.group(2)).isEmpty()) return false;
            String select = currentBlock.instrs().get(currentInstrIndex + 2).strip();
            return select.matches("%[^\\s,=]+\\s*=\\s*select\\s+i1\\s+%"
                + Pattern.quote(name) + ",\\s*i1\\s+%" + Pattern.quote(nextCmp.group(1))
                + ",\\s*i1\\s+false");
        }

        int valueUseCount(String name) {
            Pattern use = Pattern.compile("%" + Pattern.quote(name) + "(?![\\w.])");
            int count = 0;
            for (BasicBlock block : parseBody()) {
                for (String instr : block.instrs()) {
                    String rhs = instr.contains("=")
                            ? instr.substring(instr.indexOf('=') + 1) : instr;
                    Matcher matcher = use.matcher(rhs);
                    while (matcher.find()) count++;
                }
            }

            return count;
        }

        // -----------------------------------------------------------------------
        // Conditional branch

        // -----------------------------------------------------------------------
        // nextBB: label of the BB immediately following the current one in emit order,
        // or null if unknown. Used to suppress a redundant fall-through jump.
        String nextBBLabel = null;
        int phiEdgeLabelCtr = 0;
        // When a conditional branch's condition is a regOnly value (expected live in
        // WA), but phi copies must be emitted before the branch (which clobber WA),
        // the condition is spilled to this slot first. emitCondBr then reads it from
        // here instead of the stale register. Reset after each branch. See the
        // terminator handling in the block emit loop.
        // Fixed frame slot name for the branch-condition spill (see maybeSpillBranchCond).
        // Pre-allocated in preallocTmpSlots so frameSize is final before any argument
        // offset is emitted; pinned in compactSlots so it never aliases other slots.

        static final String CONDSPILL_SLOT = "__condspill";
        // Scratch slot for emitSelect: when a select operand (trueV/falseV) is a
        // regOnly value currently live in WA, evaluating the condition would clobber
        // WA before the arm consumes it. We spill the operand here first and reload
        // it from the slot inside the arm. Preallocated so frameSize is final before
        // any argument offset is emitted.

        static final String SELSPILL_SLOT  = "__selspill";
        // Dedicated scratch slots for emitMemPseudo (llvm.memset/memcpy/memmove lowered
        // to memfill/memcopy). dst, len, and aux (= fill value OR copy src) are all live
        // AT THE SAME TIME during the operation, so they cannot share the collapsed __t_
        // scratch slot. They are preallocated in preallocTmpSlots (so frameSize is final
        // before incoming-arg offsets are emitted) and pinned in compactSlots (so they
        // keep three distinct offsets instead of being merged).

        static final String MEM_DST_SLOT = "__mem_dst";

        static final String MEM_LEN_SLOT = "__mem_len";

        static final String MEM_AUX_SLOT = "__mem_aux";
        String condSpillVar = null;   // the cvar that was spilled
        int    condSpillOff = -1;     // frame offset it was spilled to
        int    condSpillBits = 8;     // width of the spilled boolean

        /**
         * If `term` is a conditional branch or a switch whose operand is a regOnly value
         * currently live in WA, and this block emits phi copies before the terminator
         * (which would clobber WA and the flags), spill the operand to a scratch slot now.
         * emitCondBr / emitSwitch then reloads it from condSpillOff instead of the stale
         * register.
         */
        void maybeSpillBranchCond(String term, String blockLabel) {
            List<PhiCopy> copies = phiCopiesByPred == null ? null : phiCopiesByPred.get(blockLabel);
            if (copies == null || copies.isEmpty()) return;   // no phi copies -> WA stays valid
            String cvar = null;
            int spillBits;
            Matcher m = Pattern.compile("br\\s+i1\\s+%([^\\s,]+),").matcher(term);
            if (m.find()) {
                cvar = m.group(1);
                spillBits = Math.min(waSlotBits, 16);
            } else {
                // A `switch iN %v, ...` reads its operand AFTER these phi copies clobber
                // WA. Spill a regOnly operand now; emitSwitch reloads it from the slot.
                Matcher ms = Pattern.compile("switch\\s+i(\\d+)\\s+%([^\\s,]+),").matcher(term);
                if (!ms.find()) return;
                cvar = ms.group(2);
                spillBits = Math.min(Integer.parseInt(ms.group(1)), 32);
            }

            if (!regOnly.contains(cvar)) return;               // has a slot; reloaded anyway
            if (!cvar.equals(waSlotName)) return;              // not the live WA value; leave it
            if (!slots.containsKey(CONDSPILL_SLOT)) allocSlot(CONDSPILL_SLOT, 4); // fallback (should be prealloc'd)
            int off = slots.get(CONDSPILL_SLOT)[0];
            int bits = spillBits;
            if (bits <= 8)       emit("ld (xsp+" + off + "), a");
            else if (bits <= 16) emit("ld (xsp+" + off + "), wa");
            else                 emit("ld (xsp+" + off + "), xwa");
            condSpillVar = cvar; condSpillOff = off; condSpillBits = bits;
        }

        void emitCondBr(String cvar, String tbb, String fbb, Map<String, int[]> argOffsets) {
            // True branch falls through when tbb is the next label in emit order.
            boolean tbbFallsThrough = tbb.equals(nextBBLabel);
            // False branch falls through when fbb is the next label.
            boolean fbbFallsThrough = fbb.equals(nextBBLabel);
            // If this condition was spilled before the block's phi copies (which
            // clobber WA), reload it from the spill slot rather than a stale register.
            if (cvar.equals(condSpillVar) && condSpillOff >= 0) {
                if (condSpillBits <= 8) { emit("ld a, (xsp+" + condSpillOff + ")"); emit("or a, a"); }
                else                    { emit("ld wa, (xsp+" + condSpillOff + ")"); emit("or wa, wa"); }
                condSpillVar = null; condSpillOff = -1;
                emitCondBrJumps(tbb, fbb, tbbFallsThrough, fbbFallsThrough);
                return;
            }

            if (cvar.equals(waSlotName)) {
                if (waSlotBits <= 8) emit("or a, a");
                else                 emit("or wa, wa");
            } else if (cvar.equals(bcSlotName)) {
                if (bcSlotBits <= 8) { emit("ld wa, 0"); emit("ld a, b"); emit("or a, a"); }
                else if (bcSlotBits <= 16) { emit("ld wa, bc"); emit("or wa, wa"); }
                else { emit("ld xwa, xbc"); emit("or wa, wa"); }
            } else if (cvar.equals(deSlotName)) {
                if (deSlotBits <= 8) { emit("ld wa, 0"); emit("ld a, e"); emit("or a, a"); }
                else if (deSlotBits <= 16) { emit("ld wa, de"); emit("or wa, wa"); }
                else { emit("ld xwa, xde"); emit("or wa, wa"); }
            } else if (isBankPromoted(cvar)) {
                int bi = bankIndexOf(cvar);
                if (bankSlotBits[bi] <= 8) { emit("ld wa, 0"); emit("ld a, " + BANK_REG_B[bi]); emit("or a, a"); }
                else if (bankSlotBits[bi] <= 16) { emit("ld wa, " + BANK_REG_R[bi]); emit("or wa, wa"); }
                else { emit("ld xwa, " + BANK_REG_X[bi]); emit("or wa, wa"); }
            } else if (slots.containsKey(cvar)) {
                int off = slots.get(cvar)[0];
                // A conditional branch operand is always i1. Slot compaction may
                // reuse its byte with a wider value, so the physical slot size
                // must not determine the reload width.
                emit("ld a, (xsp+" + off + ")");
                emit("or a, a");
            } else if (argOffsets.containsKey(cvar)) {
                int total = frameSize + argOffsets.get(cvar)[0];
                emit("ld a, (xsp+" + total + ")");
                emit("or a, a");
            } else if (regOnly.contains(cvar)) {
                // regOnly value lives in WA/XWA from the preceding instruction.
                if (waSlotBits <= 8) emit("or a, a");
                else                 emit("or wa, wa");
            } else {
                emitComment("cond_br: var " + cvar + " not in slots");
                emit("jrl " + bbPrefix + fbb);
                return;
            }

            List<PhiCopy> trueCopies = phiCopiesForEdge(currentBlock.label(), tbb);
            List<PhiCopy> falseCopies = phiCopiesForEdge(currentBlock.label(), fbb);
            if (trueCopies.isEmpty() && falseCopies.isEmpty()) {
                emitCondBrJumps(tbb, fbb, tbbFallsThrough, fbbFallsThrough);
                return;
            }

            if (trueCopies.isEmpty()) {
                emit("jrl NZ, " + bbPrefix + tbb);
                emitPhiCopiesForEdge(currentBlock.label(), fbb, falseCopies, argOffsets);
                emit("jrl " + bbPrefix + fbb);
                return;
            }

            if (falseCopies.isEmpty()) {
                emit("jrl Z, " + bbPrefix + fbb);
                emitPhiCopiesForEdge(currentBlock.label(), tbb, trueCopies, argOffsets);
                emit("jrl " + bbPrefix + tbb);
                return;
            }

            String falseEdge = bbPrefix + "phi_edge_" + (phiEdgeLabelCtr++);
            emit("jrl Z, " + falseEdge);
            emitPhiCopiesForEdge(currentBlock.label(), tbb, trueCopies, argOffsets);
            emit("jrl " + bbPrefix + tbb);
            out.add(falseEdge + ":");
            emitPhiCopiesForEdge(currentBlock.label(), fbb, falseCopies, argOffsets);
            emit("jrl " + bbPrefix + fbb);
        }

        /** Emit the Z/NZ conditional jumps for a branch whose condition already set the Z flag. */
        void emitCondBrJumps(String tbb, String fbb, boolean tbbFallsThrough, boolean fbbFallsThrough) {
            if (fbbFallsThrough) {
                // False branch falls through â€” only need the conditional jump for true.
                emit("jrl NZ, " + bbPrefix + tbb);
            } else if (tbbFallsThrough) {
                // True branch falls through â€” only need the conditional jump for false.
                emit("jrl Z, " + bbPrefix + fbb);
            } else {
                emit("jrl Z, " + bbPrefix + fbb);
                emit("jrl " + bbPrefix + tbb);
            }
        }

        // -----------------------------------------------------------------------
        // switch

        // -----------------------------------------------------------------------
        void emitSwitch(String irType, String val, String defaultBB, Map<String, int[]> argOffsets) {
            // Case lines immediately follow this exact switch terminator in its block.
            // Do not search by SSA operand name: one function may switch on the same value
            // more than once, and doing so made later switches reuse the first case table.
            List<String[]> cases = new ArrayList<>();
            if (currentBlock != null && currentInstrIndex >= 0) {
                for (int i = currentInstrIndex + 1; i < currentBlock.instrs().size(); i++) {
                    String instr = currentBlock.instrs().get(i).strip();
                    if (instr.equals("]")) break;
                    Matcher m = Pattern.compile(
                        "i\\d+\\s+(-?\\d+),\\s*label\\s+%([\\w.]+)").matcher(instr);
                    if (m.find()) cases.add(new String[]{m.group(1), m.group(2)});
                }
            }

            // If this operand was spilled before the block's phi copies (which clobber
            // WA), reload it from the spill slot rather than a stale register.
            if (val.equals(condSpillVar) && condSpillOff >= 0) {
                int sb = condSpillBits;
                if (sb <= 8)       { emit("ld wa, 0"); emit("ld a, (xsp+" + condSpillOff + ")"); }
                else if (sb <= 16) emit("ld wa, (xsp+" + condSpillOff + ")");
                else               emit("ld xwa, (xsp+" + condSpillOff + ")");
                condSpillVar = null; condSpillOff = -1;
            } else {
                loadToWA("%" + val, irType, argOffsets);
            }

            int bits = irTypeBits(irType);
            long mask = bits >= 64 ? -1L : ((1L << bits) - 1);
            for (String[] cas : cases) {
                // Mask negative case values to the operand width so the compare matches
                // the (already width-masked) value in WA/XWA (e.g. i8 -2 -> 254 / 0feh).
                long cv = Long.parseLong(cas[0]) & mask;
                if (bits <= 16) emit("cp wa, " + cv);
                else            emit("cp xwa, " + cv);
                emit("jrl Z, " + bbPrefix + cas[1]);
            }

            emit("jrl " + bbPrefix + defaultBB);
        }

        // -----------------------------------------------------------------------
        // Call

        // -----------------------------------------------------------------------
        void emitCall(String dst, String retType, String callee, String argsStr,
                      Map<String, int[]> argOffsets) {
            List<Arg> args = parseCallArgs(argsStr);

            // --- LLVM intrinsics that must not become a real `call _llvm.*` ---
            if (callee.startsWith("llvm.")) {
                // Lifetime markers are no-ops with no result: drop them.
                if (callee.startsWith("llvm.lifetime.")) return;
                // Integer min/max: llvm.{u,s}{min,max}.iN(a, b). Lower to
                // load a -> WA, load b -> HL, compare, keep the winner in WA.
                Matcher mMM = Pattern.compile("llvm\\.(u|s)(min|max)\\.i(\\d+)").matcher(callee);
                if (mMM.matches() && args.size() == 2 && dst != null) {
                    boolean unsigned = mMM.group(1).equals("u");
                    boolean isMax    = mMM.group(2).equals("max");
                    int bits = Integer.parseInt(mMM.group(3));
                    String aReg = bits <= 16 ? "wa" : "xwa";
                    String bReg = bits <= 16 ? "hl" : "xhl";
                    loadToXHL(args.get(1).val(), args.get(1).type(), argOffsets); // b -> (X)HL
                    loadToWA(args.get(0).val(), args.get(0).type(), argOffsets);  // a -> (X)WA
                    // Keep the winner: for max, replace WA with HL when b > a; for min,
                    // when b < a. Condition tested is "should WA already win" so we skip.
                    String keepCC = isMax
                        ? (unsigned ? "UGE" : "GE")   // a >= b -> a already the max
                        : (unsigned ? "ULE" : "LE");  // a <= b -> a already the min
                    String lbl = bbPrefix + "intr_" + (intrinLabelCtr++);
                    emit("cp " + aReg + ", " + bReg);
                    emit("jrl " + keepCC + ", " + lbl);
                    emit("ld " + aReg + ", " + bReg);
                    out.add(lbl + ":");
                    storeWAtoSlot(dst, Math.min(bits, 32));
                    return;
                }

                // Population count: llvm.ctpop.iN(x) -> number of set bits.
                // Lower to a shift-and-count loop over the value in XWA, result in XHL.
                Matcher mPop = Pattern.compile("llvm\\.ctpop\\.i(\\d+)").matcher(callee);
                if (mPop.matches() && args.size() == 1 && dst != null) {
                    int bits = Integer.parseInt(mPop.group(1));
                    loadToWA(args.get(0).val(), args.get(0).type(), argOffsets); // x -> XWA
                    String loop = bbPrefix + "intr_" + (intrinLabelCtr++);
                    String done = bbPrefix + "intr_" + (intrinLabelCtr++);
                    // XBC/XHL may hold promoted values live across this intrinsic.
                    emit("push xbc");
                    emit("push xhl");
                    emit("ld xhl, 0");                 // count
                    out.add(loop + ":");
                    emit("cp xwa, 0");
                    emit("jrl Z, " + done);
                    emit("ld xbc, xwa");
                    emit("and bc, 1");                 // low bit
                    emit("add xhl, xbc");
                    emit("srl 1, xwa");                // x >>= 1 (logical)
                    emit("jrl " + loop);
                    out.add(done + ":");
                    emit("ld xwa, xhl");
                    emit("pop xhl");
                    emit("pop xbc");
                    storeWAtoSlot(dst, Math.min(bits, 32));
                    return;
                }

                // Count trailing zeros: llvm.cttz.iN(x, is_zero_poison).
                // Shift right until bit0 is set, counting; if x==0 the result is `bits`.
                Matcher mCtz = Pattern.compile("llvm\\.cttz\\.i(\\d+)").matcher(callee);
                if (mCtz.matches() && args.size() >= 1 && dst != null) {
                    int bits = Integer.parseInt(mCtz.group(1));
                    loadToWA(args.get(0).val(), args.get(0).type(), argOffsets); // x -> XWA
                    String loop = bbPrefix + "intr_" + (intrinLabelCtr++);
                    String done = bbPrefix + "intr_" + (intrinLabelCtr++);
                    // XBC/XHL may hold promoted values live across this intrinsic.
                    emit("push xbc");
                    emit("push xhl");
                    emit("ld xhl, 0");                 // count
                    out.add(loop + ":");
                    emit("cp xwa, 0");
                    emit("jrl Z, " + done);            // x==0 -> count holds trailing zeros so far
                    emit("ld xbc, xwa");
                    emit("and bc, 1");
                    emit("cp bc, 0");
                    emit("jrl NZ, " + done);           // bit0 set -> done
                    emit("add xhl, 1");
                    emit("srl 1, xwa");
                    emit("jrl " + loop);
                    out.add(done + ":");
                    emit("ld xwa, xhl");
                    emit("pop xhl");
                    emit("pop xbc");
                    storeWAtoSlot(dst, Math.min(bits, 32));
                    return;
                }

                // Any other llvm.* intrinsic falls through to a normal call and will
                // surface as an undefined symbol â€” intentional, so it is not silent.
            }

            int pushBytes = 0;
            // Push args right-to-left (CC900 __cdecl)
            for (int i = args.size() - 1; i >= 0; i--) {
                Arg a = args.get(i);
                String av = a.val().trim();
                // inttoptr address as value
                Matcher mITP = Pattern.compile("inttoptr\\s*\\(i(?:32|64)\\s+(\\d+)\\s+to\\s+ptr\\)").matcher(av);
                if (mITP.matches()) {
                    emit("ld xwa, " + fmtAddr(Integer.parseInt(mITP.group(1))));
                    emit("push xwa");
                    pushBytes += 4;
                    continue;
                }

                // Function pointer @sym
                if (av.startsWith("@") && a.type().equals("ptr")) {
                    emit("lda xwa, _" + av.substring(1));
                    emit("push xwa");
                    pushBytes += 4;
                    continue;
                }

                int abits = irTypeBits(a.type());
                loadToWA(av, a.type(), argOffsets, pushBytes);
                int sz = Math.max(irTypeBytes(a.type()), 2);
                if (sz <= 2) {
                    emit("push wa");
                    pushBytes += 2;
                } else {
                    emit("push xwa");
                    pushBytes += 4;
                }
            }

            // Absolute call: `calr` is PC-relative with limited range and overflows
            // ("distance too big") in large ROMs where caller and callee are far apart.
            emit("call _" + callee);
            if (pushBytes > 0) emit("add xsp, " + pushBytes);
            // Reload promoted registers clobbered by the call.
            // The stack slot was kept in sync by emitStore (bcNeedsSpillAroundCall path),
            // so we can reload from (xsp+N) now that the arg stack is cleaned up.
            if (bcNeedsSpillAroundCall && bcSlotName != null && slots.containsKey(bcSlotName)) {
                int off = slots.get(bcSlotName)[0];
                if (bcSlotBits <= 8)       { emit("ld wa, 0"); emit("ld a, (xsp+" + off + ")"); emit("ld b, a"); }
                else if (bcSlotBits <= 16) { emit("ld wa, (xsp+" + off + ")"); emit("ld bc, wa"); }
                else                       { emit("ld xwa, (xsp+" + off + ")"); emit("ld xbc, xwa"); }
                bcLive = bcSlotName;
            }

            if (deNeedsSpillAroundCall && deSlotName != null && slots.containsKey(deSlotName)) {
                int off = slots.get(deSlotName)[0];
                if (deSlotBits <= 8)       { emit("ld wa, 0"); emit("ld a, (xsp+" + off + ")"); emit("ld e, a"); }
                else if (deSlotBits <= 16) { emit("ld wa, (xsp+" + off + ")"); emit("ld de, wa"); }
                else                       { emit("ld xwa, (xsp+" + off + ")"); emit("ld xde, xwa"); }
                deLive = deSlotName;
            }

            if (ixNeedsSpillAroundCall && ixSlotName != null && slots.containsKey(ixSlotName)) {
                int off = slots.get(ixSlotName)[0];
                if (ixSlotBits <= 8)       { emit("ld wa, 0"); emit("ld a, (xsp+" + off + ")"); emit("ld ix, wa"); }
                else if (ixSlotBits <= 16) { emit("ld wa, (xsp+" + off + ")"); emit("ld ix, wa"); }
                else                       { emit("ld xwa, (xsp+" + off + ")"); emit("ld xix, xwa"); }
                ixLive = ixSlotName;
            }

            if (iyNeedsSpillAroundCall && iySlotName != null && slots.containsKey(iySlotName)) {
                int off = slots.get(iySlotName)[0];
                if (iySlotBits <= 8)       { emit("ld wa, 0"); emit("ld a, (xsp+" + off + ")"); emit("ld iy, wa"); }
                else if (iySlotBits <= 16) { emit("ld wa, (xsp+" + off + ")"); emit("ld iy, wa"); }
                else                       { emit("ld xwa, (xsp+" + off + ")"); emit("ld xiy, xwa"); }
                iyLive = iySlotName;
            }

            if (izNeedsSpillAroundCall && izSlotName != null && slots.containsKey(izSlotName)) {
                int off = slots.get(izSlotName)[0];
                if (izSlotBits <= 8)       { emit("ld wa, 0"); emit("ld a, (xsp+" + off + ")"); emit("ld iz, wa"); }
                else if (izSlotBits <= 16) { emit("ld wa, (xsp+" + off + ")"); emit("ld iz, wa"); }
                else                       { emit("ld xwa, (xsp+" + off + ")"); emit("ld xiz, xwa"); }
                izLive = izSlotName;
            }

            if (dst != null) {
                String rname = dst.replaceAll("^%","").replaceAll(",$","");
                int retBits = irTypeBits(retType.split("\\s+")[0]);
                if (slots.containsKey(rname)) {
                    int off = slots.get(rname)[0];
                    if (retBits <= 8)       emit("ld (xsp+" + off + "), l");
                    else if (retBits <= 16) emit("ld (xsp+" + off + "), hl");
                    else                   emit("ld (xsp+" + off + "), xhl");
                }
            }
        }

        // -----------------------------------------------------------------------
        // select (ternary: cond ? a : b)

        // -----------------------------------------------------------------------
        void emitSelect(String dst, String cond, String typeT, String trueV,
                        String typeF, String falseV, Map<String, int[]> argOffsets) {
            // Clang represents short-circuit boolean AND as
            // `select i1 %cond, i1 %value, i1 false`. LLVM i1 operands are
            // canonical 0/1 values, so a byte AND is equivalent and avoids two
            // labels plus stack materialization in unrolled conditions.
            if (typeT.equals("i1") && typeF.equals("i1") && falseV.trim().equals("false")) {
                String trueName = trueV.trim().replaceAll("^%|,$", "");
                boolean conditionInL = cond.equals(booleanAndLeftInL);
                if (!conditionInL && trueV.trim().startsWith("%") && trueName.equals(waSlotName)) {
                    emit("ld l, a");
                } else if (!conditionInL) {
                    loadToXHL(trueV, typeT, argOffsets);
                }

                if (conditionInL && trueV.trim().startsWith("%") && trueName.equals(waSlotName)) {
                    booleanAndLeftInL = null;
                } else if (slots.containsKey(cond)) {
                    emit("ld a, (xsp+" + slots.get(cond)[0] + ")");
                } else if (argOffsets.containsKey(cond)) {
                    emit("ld a, (xsp+" + (frameSize + argOffsets.get(cond)[0]) + ")");
                } else {
                    loadToWA("%" + cond, "i1", argOffsets);
                }

                emit("and a, l");
                if (isSoleImmediateBranchUse(dst)) {
                    waSlotName = dst;
                    waSlotBits = 8;
                } else {
                    storeWAtoSlot(dst, 8);
                }

                return;
            }

            // if cond != 0, result = trueV else result = falseV
            String skipLabel  = ".Lsel_f_" + dst;
            String doneLabel  = ".Lsel_d_" + dst;
            // Hazard: an operand that is a regOnly value currently live in WA has no
            // stack home. Testing the condition below clobbers A/WA, destroying that
            // operand before its arm can consume it (loadToWA would then no-op on a
            // stale register â€” the historical select miscompile). Spill any such
            // operand to the select scratch slot NOW and rewrite the operand to read
            // from that slot inside its arm.
            String tV = trueV.trim().replaceAll(",$", "");
            String fV = falseV.trim().replaceAll(",$", "");
            boolean tAtRisk = tV.startsWith("%") && tV.substring(1).equals(waSlotName)
                              && regOnly.contains(tV.substring(1));
            boolean fAtRisk = fV.startsWith("%") && fV.substring(1).equals(waSlotName)
                              && regOnly.contains(fV.substring(1));
            String selSpillReg = null;   // "wa" or "xwa" size actually spilled
            if ((tAtRisk || fAtRisk) && slots.containsKey(SELSPILL_SLOT)) {
                int soff = slots.get(SELSPILL_SLOT)[0];
                int sbits = tAtRisk ? irTypeBits(typeT) : irTypeBits(typeF);
                if (sbits <= 16) { emit("ld (xsp+" + soff + "), wa");  selSpillReg = "wa"; }
                else             { emit("ld (xsp+" + soff + "), xwa"); selSpillReg = "xwa"; }
                // Only ONE of the two operands can be the live-WA value at a time
                // (there is a single WA), so at most one arm is rewritten.
            }

            if (slots.containsKey(cond)) {
                int off = slots.get(cond)[0];
                emit("ld a, (xsp+" + off + ")");
                emit("or a, a");
            } else if (argOffsets.containsKey(cond)) {
                int total = frameSize + argOffsets.get(cond)[0];
                emit("ld a, (xsp+" + total + ")");
                emit("or a, a");
            } else if (cond.equals(bcSlotName)) {
                if (bcSlotBits <= 8) { emit("ld wa, 0"); emit("ld a, b"); emit("or a, a"); }
                else if (bcSlotBits <= 16) { emit("ld wa, bc"); emit("or wa, wa"); }
                else { emit("ld xwa, xbc"); emit("or wa, wa"); }
            } else if (cond.equals(deSlotName)) {
                if (deSlotBits <= 8) { emit("ld wa, 0"); emit("ld a, e"); emit("or a, a"); }
                else if (deSlotBits <= 16) { emit("ld wa, de"); emit("or wa, wa"); }
                else { emit("ld xwa, xde"); emit("or wa, wa"); }
            } else if (cond.equals(ixSlotName)) {
                emit("ld wa, ix"); if (ixSlotBits <= 8) emit("and wa, 0ffh"); emit("or wa, wa");
            } else if (cond.equals(iySlotName)) {
                emit("ld wa, iy"); if (iySlotBits <= 8) emit("and wa, 0ffh"); emit("or wa, wa");
            } else if (cond.equals(izSlotName)) {
                emit("ld wa, iz"); if (izSlotBits <= 8) emit("and wa, 0ffh"); emit("or wa, wa");
            } else if (isBankPromoted(cond)) {
                int bi = bankIndexOf(cond);
                if (bankSlotBits[bi] <= 8) { emit("ld wa, 0"); emit("ld a, " + BANK_REG_B[bi]); emit("or a, a"); }
                else if (bankSlotBits[bi] <= 16) { emit("ld wa, " + BANK_REG_R[bi]); emit("or wa, wa"); }
                else { emit("ld xwa, " + BANK_REG_X[bi]); emit("or wa, wa"); }
            } else if (regOnly.contains(cond) && cond.equals(waSlotName)) {
                // Still live in WA from the icmp that produced it.
                if (waSlotBits <= 8) emit("or a, a");
                else                 emit("or wa, wa");
            } else {
                // `ld a, 0` does not set the flags, so without this the `jr Z` below
                // branched on the leftover flags of the condition's own `cp`.
                emit("ld a, 0");
                emit("or a, a");
            }

            emit("jr Z, " + skipLabel);
            // True arm
            if (tAtRisk && selSpillReg != null) {
                int soff = slots.get(SELSPILL_SLOT)[0];
                emit("ld " + selSpillReg + ", (xsp+" + soff + ")");
                waSlotName = null;
            } else {
                loadToWA(trueV, typeT, argOffsets);
            }

            storeWAtoSlot(dst, irTypeBits(typeT));
            emit("jrl " + doneLabel);
            out.add(skipLabel + ":");
            // False arm
            if (fAtRisk && selSpillReg != null) {
                int soff = slots.get(SELSPILL_SLOT)[0];
                emit("ld " + selSpillReg + ", (xsp+" + soff + ")");
                waSlotName = null;
            } else {
                loadToWA(falseV, typeF, argOffsets);
            }

            storeWAtoSlot(dst, irTypeBits(typeF));
            out.add(doneLabel + ":");
        }

            boolean isSoleImmediateBranchUse(String name) {
                if (currentBlock == null || currentInstrIndex < 0
                    || currentInstrIndex + 1 >= currentBlock.instrs().size()) return false;
                String quoted = Pattern.quote(name);
                String next = currentBlock.instrs().get(currentInstrIndex + 1).strip();
                if (!next.matches("br\\s+i1\\s+%" + quoted
                    + ",\\s*label\\s+%[\\w.]+,\\s*label\\s+%[\\w.]+")) return false;
                Pattern use = Pattern.compile("%" + quoted + "(?![\\w.])");
                int count = 0;
                for (BasicBlock block : parseBody()) {
                for (String instr : block.instrs()) {
                    String rhs = instr.contains("=")
                        ? instr.substring(instr.indexOf('=') + 1) : instr;
                    Matcher matcher = use.matcher(rhs);
                    while (matcher.find()) count++;
                }
                }

                return count == 1;
            }

        // -----------------------------------------------------------------------
        // Emit helpers

        // -----------------------------------------------------------------------
        static String savedRegister(String wordRegister, int bits) {
            return bits <= 16 ? wordRegister : "x" + wordRegister;
        }

        static int savedRegisterBytes(int bits) {
            return bits <= 16 ? 2 : 4;
        }

        /** Bank register push/pop mnemonic by save width: 16-bit → R form, 32-bit → X form. */
        String bankSavedReg(int idx, int bits) {
            return bits <= 16 ? BANK_REG_R[idx] : BANK_REG_X[idx];
        }

        int promotedRegisterSaveBytes() {
            if (isEntryPoint) return 0;
            int n = (useXBC ? savedRegisterBytes(bcSaveBits) : 0)
                + (useXDE ? savedRegisterBytes(deSaveBits) : 0)
                + (useXIX ? savedRegisterBytes(ixSaveBits) : 0)
                + (useXIY ? savedRegisterBytes(iySaveBits) : 0)
                + (useXIZ ? savedRegisterBytes(izSaveBits) : 0);
            for (int i = 0; i < BANK_REG_KEYS.length; i++)
                if (useBank[i]) n += savedRegisterBytes(bankSaveBits[i]);
            return n;
        }

        void emitRegRestore() {
            if (isEntryPoint) return;
            // Pop in reverse push order, using the same allocation-time widths.
            for (int i = BANK_REG_KEYS.length - 1; i >= 0; i--)
                if (useBank[i]) out.add("\tpop " + bankSavedReg(i, bankSaveBits[i]));
            if (useXIZ) out.add("\tpop " + savedRegister("iz", izSaveBits));
            if (useXIY) out.add("\tpop " + savedRegister("iy", iySaveBits));
            if (useXIX) out.add("\tpop " + savedRegister("ix", ixSaveBits));
            if (useXDE) out.add("\tpop " + savedRegister("de", deSaveBits));
            if (useXBC) out.add("\tpop " + savedRegister("bc", bcSaveBits));
        }

        void emit(String s) {
            // Normalize negative displacements: asl wants `(xhl-1)`, not `(xhl+-1)`
            // (clang-22 produces negative constant GEP indices more often).
            if (s.indexOf("+-") >= 0) s = s.replace("+-", "-");
            String t0 = s.trim();
            // asl requires the symbol of an `equ` (or `:` label) to sit in column 0;
            // an indented `SYM equ VALUE` is parsed as an instruction ("unknown
            // instruction"). Inline asm passed through verbatim keeps its leading
            // whitespace, so strip it for these directive forms.
            if (t0.matches("(?i)[A-Za-z_.$][\\w.$]*\\s+equ\\s+.*")) {
                Matcher me = Pattern.compile("(?i)([A-Za-z_.$][\\w.$]*)\\s+equ\\s+(.*)").matcher(t0);
                if (me.matches()) {
                    String sym = me.group(1);
                    String val = me.group(2).trim();
                    String prev = emittedEqu.get(sym);
                    if (prev != null) {
                        // Already defined module-wide; asl would reject a redefinition.
                        // Skip identical ones silently; flag a genuine value conflict.
                        if (!prev.equals(val))
                            out.add("\t; equ conflict for " + sym + " (" + prev + " vs " + val + ")");
                        return;
                    }

                    emittedEqu.put(sym, val);
                }

                out.add(t0);
                return;
            }

            out.add("\t" + s);
            String t = s.trim();
            // Stack stores set waSlotName via storeWAtoSlot â€” don't re-invalidate.
            if (t.startsWith("ld (xsp+")) return;
            // Any other memory store invalidates nothing in registers.
            if (t.startsWith("ld (")) return;
            // Invalidate xhlSlotName when XHL/HL is written.
            if (xhlSlotName != null && (t.contains("xhl") || t.contains(", hl")
                    || t.startsWith("ld hl,") || t.startsWith("add hl")
                    || t.startsWith("sub hl") || t.startsWith("and hl")
                    || t.startsWith("or hl")  || t.startsWith("xor hl")
                    || t.startsWith("pop ")
                    || t.startsWith("calr") || t.startsWith("call"))) {
                xhlSlotName = null;
            }

            // A pending index-address needs BOTH the base in XHL and the
            // index in XWA to survive to the consumer. Any write to XHL/HL OR XWA/WA/A
            // invalidates it. (The consumer reads and clears it before emitting, so its
            // own `(xhl+wa)` instruction never reaches this guard with it still set.)
            if (xhlIndexPending != null && (
                    t.contains("xhl") || t.contains(", hl")
                    || t.startsWith("ld hl,") || t.startsWith("add hl")
                    || t.startsWith("sub hl") || t.startsWith("and hl")
                    || t.startsWith("or hl")  || t.startsWith("xor hl")
                    || t.contains("xwa") || t.contains(", wa")
                    || t.startsWith("ld wa,") || t.contains(", a")
                    || t.startsWith("ld a,") || t.startsWith("add ")
                    || t.startsWith("sub ") || t.startsWith("and ")
                    || t.startsWith("or ")  || t.startsWith("xor ")
                    || t.startsWith("sla ") || t.startsWith("srl ")
                    || t.startsWith("sra ") || t.startsWith("extz")
                    || t.startsWith("exts") || t.startsWith("scc ")
                    || t.startsWith("pop ") || t.startsWith("calr")
                    || t.startsWith("call"))) {
                xhlIndexPending = null;
            }

            // Invalidate waSlotName when XWA/WA/A is written. Be operand-aware:
            // shift/extend/ALU ops that target a NON-WA register (e.g. `extz xhl`,
            // `and hl, 0ffh`, `sla 1, xhl`) do NOT clobber the value held in WA and
            // must not spuriously invalidate it. calr/call clobber caller-saved regs.
            if (waSlotName != null && asmWritesWA(t)) {
                waSlotName = null;
            }

            // Invalidate bcLive/deLive when XBC or XDE are clobbered.
            // calr/call clobbers all caller-saved regs (including XBC, XDE as used here).
            boolean isCall = t.startsWith("calr") || t.startsWith("call");
            if (bcLive != null && (isCall
                    || t.contains("xbc") || t.startsWith("ld bc,")
                    || t.startsWith("ld b,") || t.startsWith("mul ")
                    || t.startsWith("div "))) {
                bcLive = null;
            }

            if (deLive != null && (isCall
                    || t.contains("xde") || t.startsWith("ld de,")
                    || t.startsWith("ld e,") || t.startsWith("ld d,"))) {
                deLive = null;
            }
        }

        void emitComment(String s)  { out.add("\t; " + s); }

        /**
         * True if asm instruction `t` (already trimmed+lowercased) writes the WA/A/XWA
         * register lane, thereby destroying a value cached there (waSlotName). Operand-
         * aware: `extz xhl`, `sla 1, xhl`, `and hl, 0ffh` target other registers and do
         * NOT clobber WA. calr/call clobber caller-saved registers, so they do.
         */
        static boolean asmWritesWA(String t) {
            if (t.startsWith("calr") || t.startsWith("call")) return true;
            // Direct WA-family reads-as-destination or explicit mentions.
            if (t.contains("xwa") || t.contains(", wa") || t.contains(", a")) return true;
            if (t.startsWith("ld wa,") || t.startsWith("ld a,")) return true;
            if (t.startsWith("scc ")) {
                // scc cc, R  — only clobbers WA when R is a/wa/xwa.
                String r = t.replaceFirst("^scc\\s+[a-z]+\\s*,\\s*", "").trim();
                return r.equals("a") || r.equals("wa") || r.equals("xwa");
            }

            if (t.startsWith("pop ")) {
                String r = t.substring(4).trim();
                return r.equals("wa") || r.equals("xwa");
            }

            // Unary shift/extend forms: `extz xwa`, `exts wa`, `sla N, xwa`, ...
            // Extract the last operand (destination register) and test it.
            if (t.startsWith("extz") || t.startsWith("exts")
                    || t.startsWith("sla ") || t.startsWith("srl ") || t.startsWith("sra ")
                    || t.startsWith("add ") || t.startsWith("sub ")
                    || t.startsWith("and ") || t.startsWith("or ") || t.startsWith("xor ")
                    || t.startsWith("inc ") || t.startsWith("dec ") || t.startsWith("neg ")
                    || t.startsWith("mul ") || t.startsWith("div ")
                    || t.startsWith("adc ") || t.startsWith("sbc ")) {
                int comma = t.lastIndexOf(',');
                String dst = (comma >= 0 ? t.substring(comma + 1) : t.replaceFirst("^\\S+", "")).trim();
                return dst.equals("a") || dst.equals("wa") || dst.equals("xwa")
                    || dst.equals("w");
            }

            return false;
        }
    }

    // =========================================================================
    // Translator (top-level)

    // =========================================================================

    static class Translator {
        final String  irText;
        final String  moduleName;
        Translator(String irText, String moduleName) {
            this.irText     = irText;
            this.moduleName = moduleName;
        }

        String translate() {
            return translate(null).assembly();
        }

        TranslationOutput translate(String staticDataInclude) {
            ParseResult pr = parseIR(irText);
            runDCE(pr);
            List<String> out = new ArrayList<>();
            out.add("");
            out.add("\tcpu\t93C141");
            out.add("\tmaxmode\ton");
            out.add("\tinclude\t\"stddef96.inc\"");
            out.add("\tinclude\t\"HARDWARE.INC\"");
            out.add("\tinclude\t\"SYSTEM.INC\"");
            out.add("");
            // Globals
            Set<String> globalNames = pr.globals.stream().map(GlobalVar::name).collect(Collectors.toSet());
            emitGlobals(pr.globals, out, moduleName, pr.structLayouts);
            // Code
            out.add("; ---- code ----");
            out.add("");
            for (IrFunction func : pr.functions) {
                foldSextI64Idiom(func);
                elideRedundantGlobalLoads(func);
                boolean hasOriginalAllocas = func.body().stream()
                    .anyMatch(line -> line.contains(" alloca "));
                if (!hasOriginalAllocas) narrowFunction(func);
                FuncTranslator ft = new FuncTranslator(func, globalNames, pr.structLayouts);
                List<String> fnOut = eliminateSpills(ft.translate());
                fnOut = trimIsrSaves(fnOut);
                out.addAll(fnOut);
                out.add("");
            }

            out.add("");
            emitC9HRuntime(out);
            out.add("\tend");
            List<String> finalOut = peepholeEliminate(out);
            finalOut = simplifyBoolBranchPatterns(finalOut);
            finalOut = simplifyZeroCompareBranches(finalOut);
            finalOut = optimizeByteZeroExtendPairs(finalOut);
            finalOut = removeRedundantByteClearBeforeMask(finalOut);
            finalOut = removeRedundantMaskBeforeMask(finalOut);
            finalOut = removeRedundantOrAfterAndForZeroBranch(finalOut);
            finalOut = foldXhlDisplacement(finalOut);
            finalOut = foldIncDec(finalOut);
            finalOut = removeUnreferencedLabels(finalOut);
            finalOut = stripUnusedRuntimeHelpers(finalOut);
            finalOut = removeUnreferencedLabels(finalOut);
            if (staticDataInclude == null) {
                return new TranslationOutput(String.join("\n", finalOut) + "\n", null);
            }

            int dataStart = finalOut.indexOf("; ---- ROM constants ----");
            int codeStart = finalOut.indexOf("; ---- code ----");
            if (dataStart < 0 || codeStart <= dataStart) {
                return new TranslationOutput(String.join("\n", finalOut) + "\n", "");
            }

            List<String> assembly = new ArrayList<>(finalOut.subList(0, dataStart));
            assembly.add("\tinclude\t\"" + staticDataInclude + "\"");
            assembly.add("");
            assembly.addAll(finalOut.subList(codeStart, finalOut.size()));
            String staticData = String.join("\n", finalOut.subList(dataStart, codeStart)) + "\n";
            return new TranslationOutput(String.join("\n", assembly) + "\n", staticData);
        }

        /**
         * Whole-program dead-code elimination.
         *
         * Roots:
         *   - `main` (if present) â€” the sole entry point in CC900 ABI.
         *   - Any function or global whose address is taken in a live global
         *     initializer (transitively). Function pointers stashed in ROM
         *     tables and struct fields (e.g. `sod_img.draw` callbacks) are
         *     reachable this way, not via `call`.
         *
         * Edges: every "@name" token that appears in a live function's body
         * or a live global's initializer is a reference to another function
         * or global. We take the transitive closure over these edges and
         * drop everything not reached.
         *
         * External-linkage note: because inputs are all *.ll files
         * concatenated (whole-program view), there is no "someone else might
         * call it" case â€” if nothing in the linked set references a symbol
         * and it isn't `main` or address-taken from a live root, it's dead.
         */
        static void runDCE(ParseResult pr) {
            Map<String, IrFunction> funcByName = new LinkedHashMap<>();
            for (IrFunction f : pr.functions) funcByName.put(f.name(), f);
            Map<String, GlobalVar> globByName = new LinkedHashMap<>();
            for (GlobalVar g : pr.globals) globByName.put(g.name(), g);
            // Mandatory cart-header globals â€” always kept regardless of reachability.
            Set<String> mandatoryGlobals = Set.of(
                "Licensed", "ptr", "CartID", "System", "CartTitle", "Reserved");
            Set<String> liveFuncs   = new LinkedHashSet<>();
            Set<String> liveGlobals = new LinkedHashSet<>();
            Deque<String> worklist  = new ArrayDeque<>();
            // Seed mandatory globals so they are never pruned.
            for (String mg : mandatoryGlobals) {
                if (globByName.containsKey(mg)) {
                    if (liveGlobals.add(mg)) worklist.push(mg);
                }
            }

            // Seed roots: main() is always live if present.
            if (funcByName.containsKey("main")) {
                liveFuncs.add("main");
                worklist.push("main");
            }

            // Include a leading '.' so private globals like @.str.18 are matched;
            // the old [A-Za-z_] start missed every @.str reference, letting DCE drop
            // string constants that live functions actually use.
            Pattern refPat = Pattern.compile("@([A-Za-z_.][A-Za-z0-9_.]*)");
            while (!worklist.isEmpty()) {
                String name = worklist.pop();
                String body;
                if (funcByName.containsKey(name)) {
                    body = String.join("\n", funcByName.get(name).body());
                } else if (globByName.containsKey(name)) {
                    body = globByName.get(name).init();
                } else {
                    // External symbol (declare-only, or C9H runtime) â€” no body to scan.
                    continue;
                }

                Matcher m = refPat.matcher(body);
                while (m.find()) {
                    String ref = m.group(1);
                    if (ref.equals(name)) continue;                 // self-ref
                    if (funcByName.containsKey(ref)) {
                        if (liveFuncs.add(ref)) worklist.push(ref);
                    } else if (globByName.containsKey(ref)) {
                        if (liveGlobals.add(ref)) worklist.push(ref);
                    }

                    // else: external declare or _C9H_* helper â€” nothing to do.
                }
            }

            // Report and filter.
            List<String> deadFuncs = pr.functions.stream()
                .map(IrFunction::name)
                .filter(n -> !liveFuncs.contains(n))
                .toList();
            List<String> deadGlobals = pr.globals.stream()
                .map(GlobalVar::name)
                .filter(n -> !liveGlobals.contains(n))
                .toList();
            if (!funcByName.containsKey("main")) {
                System.err.println("[DCE] no `main` found across inputs â€” leaving all symbols in place");
                return;
            }

            pr.functions.removeIf(f -> !liveFuncs.contains(f.name()));
            pr.globals.removeIf(g -> !liveGlobals.contains(g.name()));
            System.err.println("[DCE] kept " + liveFuncs.size() + " function(s), "
                + liveGlobals.size() + " global(s); "
                + "dropped " + deadFuncs.size() + " function(s), "
                + deadGlobals.size() + " global(s)");
            if (!deadFuncs.isEmpty()) {
                System.err.println("[DCE] dead functions: " + String.join(", ", deadFuncs));
            }

            if (!deadGlobals.isEmpty()) {
                System.err.println("[DCE] dead globals: "   + String.join(", ", deadGlobals));
            }
        }

        /**
         * i32 -> i16 narrowing pre-pass.
         *
         * clang at -O0 integer-promotes u16 C arithmetic to i32:
         *   %a = zext i16 %x to i32 ; %b = add i32 %a, %c ; ... ; %t = trunc i32 %r to i16
         * The translator emits full 32-bit code (xwa/xhl pairs, extz, 4-byte slots) for
         * every i32 op. On the 16-bit TLCS-900 that is wasteful when the value is
         * semantically u16. Codegen width is driven entirely by the type token
         * (irTypeBits), so retyping a provably-narrowable computation cone from i32 to
         * i16 in the raw IR text makes emitBinop/etc. emit compact 16-bit code with no
         * other change.
         *
         * A "narrowable cone" is a set of i32 SSA values such that every value:
         *   (a) is produced by a narrowable op â€” add/sub/mul/and/or/xor, or shl by a
         *       CONSTANT amount â€” or is a `zext iK->i32` leaf (K<=16); AND
         *   (b) has every use either as an operand of another narrowable cone member, or
         *       as the source of a `trunc i32->i16` (the ONLY permitted cone exit).
         *
         * Soundness: add/sub/mul/and/or/xor/shl produce identical LOW 16 bits in 16-bit
         * vs 32-bit two's complement. Because the only cone exit is trunc->i16, no
         * consumer ever reads bits >= 16, so the high half is dead. Every operation that
         * DOES read bits >= 16 â€” lshr/ashr, variable-amount shl, div/rem, icmp, i64
         * widening (sext/zext to i64), store of the i32, call arg, ret, ptrtoint, use as
         * a GEP index â€” is a hard poison barrier and keeps its whole cone i32. "Narrow
         * the whole cone or none": computed by fixpoint. DO NOT admit an i32 store/ret/
         * icmp as a cone exit â€” that would make shl/mul unsound.
         *
         * Poison discrimination needs no special-casing: a poison consumer line (GEP,
         * store, call, icmp, shift-RHS, i64 cast, ...) never matches the PRODUCER or
         * TRUNC regex, so the value's use on it falls through to POISON automatically.
         */
        // Loop-invariant global-load hoisting.

        // -------------------------------------------------------------------------
        // Interprocedural mod-check: for each function, which @globals may be written
        // (transitively), and whether it (transitively) reaches an external/unknown
        // callee (which could write anything). Computed once for the whole module.

        static final class ModInfo {
            final Map<String, Set<String>> modGlobals = new HashMap<>();   // fn -> globals it/callees write
            final Map<String, Boolean>     reachesExternal = new HashMap<>();
            // A call to `callee` inside a loop is safe to hoist `@g` past iff the callee
            // reaches no external code and does not (transitively) write @g.
            boolean callSafeFor(String callee, String g) {
                if (reachesExternal.getOrDefault(callee, true)) return false;
                return !modGlobals.getOrDefault(callee, Set.of()).contains(g);
            }
        }

        static final Pattern P_STORE_GLOBAL =
            Pattern.compile("\\bstore\\b.*,\\s*ptr\\s+@([A-Za-z0-9_.$]+)\\b");

        static final Pattern P_CALL_TARGET =
            Pattern.compile("\\bcall\\b[^@]*@([A-Za-z0-9_.$]+)\\s*\\(");
        static ModInfo computeModInfo(List<IrFunction> functions) {
            ModInfo mi = new ModInfo();
            Set<String> defined = functions.stream().map(IrFunction::name).collect(Collectors.toSet());
            // Direct writes + direct callees + external flag.
            Map<String, Set<String>> directWrites = new HashMap<>();
            Map<String, Set<String>> callees      = new HashMap<>();
            Map<String, Boolean>     directExternal = new HashMap<>();
            for (IrFunction f : functions) {
                Set<String> w = new HashSet<>();
                Set<String> c = new HashSet<>();
                boolean ext = false;
                for (String raw : f.body()) {
                    Matcher ms = P_STORE_GLOBAL.matcher(raw);
                    while (ms.find()) w.add(ms.group(1));
                    Matcher mc = P_CALL_TARGET.matcher(raw);
                    while (mc.find()) {
                        String callee = mc.group(1);
                        c.add(callee);
                        if (!defined.contains(callee)) ext = true;   // unknown/external
                    }
                }

                directWrites.put(f.name(), w);
                callees.put(f.name(), c);
                directExternal.put(f.name(), ext);
            }

            // Transitive closure over the call graph (bounded fixpoint).
            for (IrFunction f : functions) {
                mi.modGlobals.put(f.name(), new HashSet<>(directWrites.get(f.name())));
                mi.reachesExternal.put(f.name(), directExternal.get(f.name()));
            }

            boolean changed = true;
            while (changed) {
                changed = false;
                for (IrFunction f : functions) {
                    String fn = f.name();
                    Set<String> mg = mi.modGlobals.get(fn);
                    boolean rx = mi.reachesExternal.get(fn);
                    for (String callee : callees.get(fn)) {
                        if (!defined.contains(callee)) continue; // external already flagged
                        if (mg.addAll(mi.modGlobals.getOrDefault(callee, Set.of()))) changed = true;
                        if (!rx && mi.reachesExternal.getOrDefault(callee, true)) { rx = true; changed = true; }
                    }

                    mi.reachesExternal.put(fn, rx);
                }
            }

            return mi;
        }

        // Hoist loop-invariant `load T, ptr @global` out of natural loops into the
        // Hoist loop-invariant `load T, ptr @global` out of natural loops into the
        // loop preheader. Sound and conservative. Runs on raw IR
        static void hoistLoopInvariantGlobals(IrFunction f, ModInfo mods) {
            List<String> body = f.body();
            if (body.isEmpty() || mods == null) return;

            // --- split into IR-order blocks (label -> [startIdx, endIdxExclusive]) ---
            // A label line looks like "N:" or "name:"; the entry block has no label.
            List<String> labels = new ArrayList<>();       // block label ("" for entry)
            List<Integer> starts = new ArrayList<>();       // first instr index (after label line)
            List<Integer> labelLineIdx = new ArrayList<>(); // index of the label line itself (-1 for entry)
            labels.add(""); starts.add(0); labelLineIdx.add(-1);
            Pattern pLabel = Pattern.compile("^([A-Za-z0-9_.$]+):\\s*(;.*)?$");
            for (int i = 0; i < body.size(); i++) {
                Matcher ml = pLabel.matcher(body.get(i).strip());
                if (ml.matches()) {
                    labels.add(ml.group(1));
                    labelLineIdx.add(i);
                    starts.add(i + 1);
                }
            }

            int nb = labels.size();
            if (nb < 2) return;   // no labelled blocks → no loop
            // block end = next block's label line (or body end)
            int[] blkEnd = new int[nb];
            for (int b = 0; b < nb; b++) {
                blkEnd[b] = (b + 1 < nb) ? labelLineIdx.get(b + 1) : body.size();
            }

            Map<String, Integer> labelToBlk = new HashMap<>();
            for (int b = 0; b < nb; b++) labelToBlk.put(labels.get(b), b);
            Pattern pBr    = Pattern.compile("\\bbr\\s+label\\s+%([A-Za-z0-9_.$]+)");
            Pattern pBrCc  = Pattern.compile("\\bbr\\s+i1\\s+\\S+,\\s*label\\s+%([A-Za-z0-9_.$]+),\\s*label\\s+%([A-Za-z0-9_.$]+)");
            Pattern pLoadG = Pattern.compile("^%([A-Za-z0-9_.$]+)\\s*=\\s*load\\s+([^,\\s]+),\\s*ptr\\s+@([A-Za-z0-9_.$]+),");
            Pattern pStoreNonGlobal = Pattern.compile("^store\\b.*,\\s*ptr\\s+%");
            Pattern pStoreGlobal    = Pattern.compile("^store\\b.*,\\s*ptr\\s+@([A-Za-z0-9_.$]+)");
            Pattern pCall  = Pattern.compile("\\bcall\\b");
            Pattern pCallTgt = P_CALL_TARGET;

            // --- find one natural loop: a back-edge br to an earlier block ---
            // header block index hb, latch block lb with hb <= (blk of br target) and
            // the branch target block index < the branching block index.
            int headerBlk = -1, latchBlk = -1;
            outer:
            for (int b = nb - 1; b >= 1; b--) {
                for (int i = starts.get(b); i < blkEnd[b]; i++) {
                    String s = body.get(i).strip();
                    Matcher mb = pBr.matcher(s);
                    if (mb.find()) {
                        Integer tgt = labelToBlk.get(mb.group(1));
                        if (tgt != null && tgt < b) { headerBlk = tgt; latchBlk = b; break outer; }
                    }
                }
            }

            if (headerBlk < 0) return;   // no loop found
            // Loop body = contiguous blocks [headerBlk .. latchBlk]. Require contiguity
            // (all these blocks form the loop; no external entries other than via header).
            // Instruction index range of the loop:
            int loopLo = starts.get(headerBlk);
            int loopHi = blkEnd[latchBlk];
            // Preheader = the UNIQUE block (outside the loop) whose terminator branches to
            // the header. If not unique, skip.
            int preheaderBlk = -1;
            for (int b = 0; b < nb; b++) {
                if (b >= headerBlk && b <= latchBlk) continue;   // inside loop
                boolean branchesToHeader = false;
                for (int i = starts.get(b); i < blkEnd[b]; i++) {
                    String s = body.get(i).strip();
                    Matcher mb = pBr.matcher(s);
                    while (mb.find()) if (labels.get(headerBlk).equals(mb.group(1))) branchesToHeader = true;
                    Matcher mc = pBrCc.matcher(s);
                    if (mc.find()) {
                        if (labels.get(headerBlk).equals(mc.group(1))
                            || labels.get(headerBlk).equals(mc.group(2))) branchesToHeader = true;
                    }
                }

                if (branchesToHeader) {
                    if (preheaderBlk >= 0) return;   // not unique → skip
                    preheaderBlk = b;
                }
            }

            if (preheaderBlk < 0) return;

            // --- gather loop facts ---
            // Local allocas: stores through these %ptrs are stack writes that provably
            // cannot alias a @global, so they don't block hoisting. Only a store through
            // an UNKNOWN pointer (not a local alloca) forces the conservative bail.
            Set<String> localAllocas = new HashSet<>();
            Pattern pAllocaDef = Pattern.compile("^%([A-Za-z0-9_.$]+)\\s*=\\s*alloca\\b");
            for (String raw : body) {
                Matcher ma = pAllocaDef.matcher(raw.strip());
                if (ma.find()) localAllocas.add(ma.group(1));
            }

            Pattern pStorePtrName = Pattern.compile(",\\s*ptr\\s+%([A-Za-z0-9_.$]+)");
            Set<String> storedGlobals = new HashSet<>();   // @globals stored in the loop
            boolean unknownPtrStore = false;               // store through a non-alloca %ptr
            List<String> loopCallTargets = new ArrayList<>();
            boolean loopHasUnknownCall = false;
            for (int i = loopLo; i < loopHi; i++) {
                String s = body.get(i).strip();
                Matcher mg = pStoreGlobal.matcher(s);
                if (mg.find()) storedGlobals.add(mg.group(1));
                if (pStoreNonGlobal.matcher(s).find()) {
                    // store ..., ptr %p â€” safe only if %p is a known local alloca.
                    Matcher mp = pStorePtrName.matcher(s);
                    if (mp.find()) {
                        if (!localAllocas.contains(mp.group(1))) unknownPtrStore = true;
                    } else {
                        unknownPtrStore = true;
                    }
                }

                if (pCall.matcher(s).find()) {
                    Matcher mc = pCallTgt.matcher(s);
                    if (mc.find()) loopCallTargets.add(mc.group(1));
                    else loopHasUnknownCall = true;   // indirect call → unknown
                }
            }

            if (unknownPtrStore) return;   // conservative anti-alias: unknown pointer may hit a global

            // --- find hoistable invariant global loads in the loop body ---
            // Candidate: `%k = load T, ptr @g` where @g not stored in loop, not volatile,
            // and every in-loop call is safe to hoist @g past.
            // Track the max numeric SSA id to mint fresh temp names.
            long maxId = 0;
            Pattern pNumId = Pattern.compile("%(\\d+)\\b");
            for (String raw : body) {
                Matcher mn = pNumId.matcher(raw);
                while (mn.find()) maxId = Math.max(maxId, Long.parseLong(mn.group(1)));
            }

            record Hoist(int lineIdx, String oldName, String type, String global, String newName) {}
            List<Hoist> hoists = new ArrayList<>();
            long nextId = maxId + 1;
            for (int i = loopLo; i < loopHi; i++) {
                String s = body.get(i).strip();
                if (s.contains("volatile")) continue;
                Matcher ml = pLoadG.matcher(s);
                if (!ml.find()) continue;
                String oldName = "%" + ml.group(1), type = ml.group(2), g = ml.group(3);
                if (storedGlobals.contains(g)) continue;      // written in loop → variant
                if (loopHasUnknownCall) continue;             // indirect call → unsafe
                boolean callsSafe = true;
                for (String callee : loopCallTargets) {
                    if (!mods.callSafeFor(callee, g)) { callsSafe = false; break; }
                }

                if (!callsSafe) continue;
                hoists.add(new Hoist(i, oldName, type, g, "%__licm" + (nextId++)));
            }

            if (hoists.isEmpty()) return;

            // --- rewrite: delete in-loop loads, substitute uses, insert preheader loads ---
            // Build substitution map old->new, and the set of lines to delete.
            Map<String, String> subst = new HashMap<>();
            Set<Integer> deleteLines = new HashSet<>();
            for (Hoist h : hoists) { subst.put(h.oldName(), h.newName()); deleteLines.add(h.lineIdx()); }
            List<String> newBody = new ArrayList<>(body.size() + hoists.size());
            int preheaderTerminatorIdx = -1;
            // find the terminator line of the preheader (its last br/ret in the block range)
            for (int i = starts.get(preheaderBlk); i < blkEnd[preheaderBlk]; i++) {
                String s = body.get(i).strip();
                if (s.startsWith("br ") || s.startsWith("ret") || s.startsWith("switch")) {
                    preheaderTerminatorIdx = i; break;
                }
            }

            for (int i = 0; i < body.size(); i++) {
                if (i == preheaderTerminatorIdx) {
                    // Insert hoisted loads just before the preheader's terminator.
                    for (Hoist h : hoists) {
                        newBody.add("  " + h.newName() + " = load " + h.type()
                                    + ", ptr @" + h.global() + ", align 2");
                    }
                }

                if (deleteLines.contains(i)) continue;   // drop the in-loop load
                String line = body.get(i);
                // Substitute uses of hoisted names (word-boundary on the % token).
                if (!subst.isEmpty()) {
                    for (Map.Entry<String, String> e : subst.entrySet()) {
                        // %old not followed by an identifier char → replace with %new
                        line = line.replaceAll(Pattern.quote(e.getKey()) + "(?![A-Za-z0-9_.$])",
                                               Matcher.quoteReplacement(e.getValue()));
                    }
                }

                newBody.add(line);
            }

            body.clear();
            body.addAll(newBody);
            System.err.println("[licm] " + f.name() + ": hoisted " + hoists.size()
                + " invariant global load(s): "
                + hoists.stream().map(Hoist::global).distinct().collect(Collectors.joining(", ")));
        }

        /**
         * Correctness pre-pass: fold LLVM's 32→64 sign-extension idiom.
         *
         * clang expresses `sext i32 -> i64` (typically to feed a signed GEP index) as a
         * pair of full-width i64 shifts:
         *     %b = shl  i64 %a, 32
         *     %c = ashr i64 %b, 32          ; ("ashr exact" when a nsw guarantees it)
         * %c is %a with its low 32 bits sign-extended through bits 32..63.
         *
         * The translator models every value in a 32-bit register (XWA) â€” bits >= 32 are
         * never represented. It lowers each shift literally via emitShift, so `shl i64,32`
         * becomes `sla 32, xwa` on a 32-BIT register: the count exceeds the register width
         * and the value is annihilated (then `sra 32, xwa` on the result yields 0/-1).
         * The GEP index collapses to 0/garbage â€” the collision/array read hits the wrong
         * tile. (This is exactly the LEFT/UP wall miscompile: the negative index arm
         * `map_x + (spr>>3) - 1 + ...` is the only place a signed i64 GEP index appears.)
         *
         * Because we only ever consume the low 32 bits (24-bit addresses, 32-bit value
         * model), sign-extension into bits 32..63 is dead: %c is semantically identical to
         * %a for every downstream use. So we drop both shifts and alias %b and %c to %a.
         * This is unconditional (a correctness fix, not an optimization) and general: any
         * `shl i64 X,32 ; ashr i64 X,32` sext idiom is folded, in any function.
         */
        static void foldSextI64Idiom(IrFunction f) {
            List<String> body = f.body();
            Pattern pShl  = Pattern.compile(
                "^\\s*%([\\w.]+)\\s*=\\s*shl\\s+(?:(?:nsw|nuw)\\s+)*i64\\s+(%[\\w.]+),\\s*32\\s*$");
            Pattern pAshr = Pattern.compile(
                "^\\s*%([\\w.]+)\\s*=\\s*ashr\\s+(?:exact\\s+)?i64\\s+(%[\\w.]+),\\s*32\\s*$");
            Map<String,String> alias = new HashMap<>();   // %shl / %ashr result -> %source
            for (int i = 0; i < body.size() - 1; i++) {
                Matcher ms = pShl.matcher(body.get(i));
                if (!ms.matches()) continue;
                String shlDst = "%" + ms.group(1), src = ms.group(2);
                Matcher ma = pAshr.matcher(body.get(i + 1));
                if (!ma.matches() || !ma.group(2).equals(shlDst)) continue;
                String ashrDst = "%" + ma.group(1);
                // Resolve source through any alias already recorded.
                String root = alias.getOrDefault(src, src);
                alias.put(shlDst, root);
                alias.put(ashrDst, root);
                body.set(i,     "  ; sext-i64 idiom folded: " + shlDst + " = shl "  + src + ", 32");
                body.set(i + 1, "  ; sext-i64 idiom folded: " + ashrDst + " = ashr " + shlDst + ", 32");
            }

            if (alias.isEmpty()) return;
            // Rewrite every remaining use of an aliased value to its root source.
            for (int i = 0; i < body.size(); i++) {
                String line = body.get(i);
                if (line.contains("; sext-i64 idiom folded")) continue;
                for (Map.Entry<String,String> e : alias.entrySet()) {
                    // Whole-token replacement: %old not followed by an identifier char.
                    line = line.replaceAll(Pattern.quote(e.getKey()) + "(?![\\w.$])",
                                           Matcher.quoteReplacement(e.getValue()));
                }

                body.set(i, line);
            }

            System.err.println("[sext-i64] " + f.name() + ": folded "
                + (alias.size() / 2) + " sign-extend idiom(s)");
        }

        /**
         * Redundant global-load elimination (per basic block).
         *
         * clang `-O3` reloads a linker global (`%x = load T, ptr @g`) after every store it
         * cannot prove non-aliasing â€” notably stores to a fixed hardware address expressed
         * as `store ..., ptr inttoptr (i32 N to ptr)` (e.g. the 0x9000 scroll-plane VRAM
         * write in the unrolled fill body). On this target a linker `@global` and a fixed
         * MMIO/VRAM constant address never alias, so those reloads are pure overhead â€” in
         * initScreen alone, @map_x/@map_y/@map/@map_w are reloaded ~42 times.
         *
         * This value-numbers global loads within each basic block: the first `load @g`
         * caches its SSA result; a later `load @g` (same type) with no intervening
         * invalidation is rewritten to reuse the cached value and deleted. Invalidation is
         * deliberately conservative:
         *   - a `store ..., ptr @g` invalidates @g;
         *   - a `store` through ANY pointer that is NOT a fixed `inttoptr(...)` constant
         *     invalidates ALL cached globals (an unknown %ptr might alias a global â€” a GEP
         *     into a *different* named global would be safe, but we don't prove that here);
         *   - any `call` invalidates ALL cached globals;
         *   - a label (block boundary) clears the cache entirely (keeps it a sound,
         *     dominator-free per-block pass â€” the unrolled fill is one straight-line block).
         * volatile loads/stores are never cached or reused.
         */
        static void elideRedundantGlobalLoads(IrFunction f) {
            List<String> body = f.body();
            Pattern pLoadG  = Pattern.compile(
                "^\\s*%([\\w.]+)\\s*=\\s*load\\s+(?:volatile\\s+)?([^,\\s]+),\\s*ptr\\s+@([\\w.$]+)\\s*(?:,.*)?$");
            Pattern pStoreG = Pattern.compile("^\\s*store\\b.*,\\s*ptr\\s+@([\\w.$]+)\\s*(?:,.*)?$");
            // store through a destination pointer token: capture the pointer operand.
            Pattern pStoreDst = Pattern.compile("^\\s*store\\b.*,\\s*ptr\\s+(\\S+?)\\s*(?:,\\s*align\\b.*)?$");
            // A GEP definition: %p = getelementptr ... â€” capture dst and whether it is
            // rooted at a fixed inttoptr(...) constant or another fixed pointer %q.
            Pattern pGepDef = Pattern.compile("^\\s*%([\\w.]+)\\s*=\\s*getelementptr\\b(.*)$");
            Pattern pGepFromPtrName = Pattern.compile("\\bptr\\s+%([\\w.]+)\\b");
            Pattern pLabel  = Pattern.compile("^\\s*[\\w.$]+:\\s*(;.*)?$");
            Pattern pCall   = Pattern.compile("\\bcall\\b");
            Map<String,String> avail = new HashMap<>();   // "@g|type" -> SSA value (e.g. %11)
            Map<String,String> alias = new HashMap<>();    // redundant %load -> cached %value
            Set<Integer> deleteLines = new HashSet<>();
            // SSA pointers provably rooted at a fixed integer address (inttoptr constant):
            // a store through one cannot alias a linker @global on this target. Built as we
            // scan (GEP defs precede their store uses within a block); cleared per block.
            Set<String> fixedPtrs = new HashSet<>();
            for (int i = 0; i < body.size(); i++) {
                String s = body.get(i);
                String t = s.strip();
                if (pLabel.matcher(t).matches()) { avail.clear(); fixedPtrs.clear(); continue; }
                // Track fixed-address-derived pointers (GEP off inttoptr(...) or off another
                // fixed pointer).
                Matcher mgep = pGepDef.matcher(s);
                if (mgep.matches()) {
                    String dst = mgep.group(1), rest = mgep.group(2);
                    boolean fixed = rest.contains("inttoptr");
                    if (!fixed) {
                        Matcher mp = pGepFromPtrName.matcher(rest);
                        if (mp.find() && fixedPtrs.contains(mp.group(1))) fixed = true;
                    }

                    if (fixed) fixedPtrs.add(dst);
                    // (a GEP defines a pointer; it does not store â€” no invalidation)
                    continue;
                }

                Matcher ml = pLoadG.matcher(s);
                if (ml.matches() && !t.contains("volatile")) {
                    String dst = "%" + ml.group(1), type = ml.group(2), g = ml.group(3);
                    String key = "@" + g + "|" + type;
                    String cached = avail.get(key);
                    if (cached != null) {
                        alias.put(dst, cached);      // reuse the earlier load's value
                        deleteLines.add(i);
                    } else {
                        avail.put(key, dst);         // first load of @g in this block
                    }

                    continue;
                }

                // Invalidations.
                Matcher msg = pStoreG.matcher(s);
                if (msg.matches()) {
                    // store to @g: drop every cached entry for that global (any type).
                    String g = msg.group(1);
                    avail.keySet().removeIf(k -> k.startsWith("@" + g + "|"));
                    continue;
                }

                Matcher mst = pStoreDst.matcher(s);
                if (mst.matches()) {
                    String dstPtr = mst.group(1);
                    // Non-aliasing to globals iff the destination is a fixed integer address
                    // (literal inttoptr) or a pointer derived from one. Otherwise an unknown
                    // %ptr may hit a global → clobber all cached loads.
                    boolean nonAliasing =
                        dstPtr.startsWith("inttoptr")
                        || (dstPtr.startsWith("%") && fixedPtrs.contains(dstPtr.substring(1)));
                    if (!nonAliasing) avail.clear();
                    continue;
                }

                if (pCall.matcher(s).find()) { avail.clear(); continue; }
            }

            if (alias.isEmpty()) return;
            // Rewrite uses of every redundant load to its cached value, then drop the loads.
            List<String> out = new ArrayList<>(body.size());
            for (int i = 0; i < body.size(); i++) {
                if (deleteLines.contains(i)) continue;
                String line = body.get(i);
                for (Map.Entry<String,String> e : alias.entrySet()) {
                    line = line.replaceAll(Pattern.quote(e.getKey()) + "(?![\\w.$])",
                                           Matcher.quoteReplacement(e.getValue()));
                }

                out.add(line);
            }

            body.clear();
            body.addAll(out);
            System.err.println("[gvn-load] " + f.name() + ": elided "
                + alias.size() + " redundant global load(s)");
        }

        static void narrowFunction(IrFunction f) {
            List<String> body = f.body();
            final int n = body.size();
            Pattern pDef    = Pattern.compile("^\\s*%([\\w.]+)\\s*=\\s*(.*)$");
            Pattern pProd   = Pattern.compile(
                "^(add|sub|mul|and|or|xor|shl)\\s+((?:(?:nsw|nuw|exact)\\s+)*)i32\\s+(\\S+?),\\s*(\\S+)$");
            Pattern pZext16 = Pattern.compile("^zext\\s+(i\\d+)\\s+(\\S+?)\\s+to\\s+i32$");
            Pattern pTrunc  = Pattern.compile("^trunc\\s+i32\\s+(\\S+?)\\s+to\\s+i16$");
            Pattern pDivRem = Pattern.compile(
                "^(udiv|sdiv|urem|srem)\\s+((?:(?:nsw|nuw|exact)\\s+)*)i32\\s+(\\S+?),\\s*(\\S+)$");
            Pattern pName   = Pattern.compile("%([\\w.]+)");
            // Conservative unsigned upper bound for each i32 SSA value (saturating past
            // 0xFFFF to WIDE). Used to prove a div/rem dividend fits so the op can narrow
            // to the native 16-bit DIV/DIVS (32/16 word form). Sound because every bound
            // is an over-approximation: if we say <=0x7FFF it truly is.
            final long WIDE = 0x1_0000L;   // "does not fit 16 bits"
            Map<String,Long> bound = computeBounds(body, pDef, pName);
            // Phase 1: classify every SSA def.
            Map<String,Integer> defLine = new HashMap<>();
            Map<String,String>  defKind = new HashMap<>();   // PRODUCER | ZEXT16 | OTHER
            Map<String,String>  rhsOf   = new HashMap<>();
            for (int i = 0; i < n; i++) {
                Matcher md = pDef.matcher(body.get(i));
                if (!md.matches()) continue;
                String dst = md.group(1);
                String rhs = md.group(2).trim().replaceAll(",?\\s*align\\s+\\d+\\s*$", "").trim();
                defLine.put(dst, i);
                rhsOf.put(dst, rhs);
                Matcher mp = pProd.matcher(rhs);
                if (mp.matches()) {
                    // shl narrowable only with a constant shift amount (variable-count
                    // 16- vs 32-bit shifts differ for count >= 16).
                    boolean ok = !mp.group(1).equals("shl") || parseImmediate(mp.group(4)).isPresent();
                    defKind.put(dst, ok ? "PRODUCER" : "OTHER");
                    continue;
                }

                // Divide/remainder: normally a poison barrier (right-shift-like: it reads
                // bits >= 16). BUT it can narrow to the native 16-bit DIV/DIVS when the
                // dividend provably fits the width the result is interpreted in:
                //   unsigned (udiv/urem): dividend and divisor <= 0xFFFF
                //   signed   (sdiv/srem): dividend and divisor <= 0x7FFF   (else i16 would
                //     read them as negative and disagree with the i32 op)
                Matcher mdr = pDivRem.matcher(rhs);
                if (mdr.matches()) {
                    boolean signed = mdr.group(1).startsWith("s");
                    long lim = signed ? 0x7FFFL : 0xFFFFL;
                    long bl = operandBound(mdr.group(3), bound, WIDE);
                    long br = operandBound(mdr.group(4), bound, WIDE);
                    defKind.put(dst, (bl <= lim && br <= lim && br >= 1) ? "PRODUCER" : "OTHER");
                    continue;
                }

                Matcher mz = pZext16.matcher(rhs);
                if (mz.matches() && irTypeBits(mz.group(1)) <= 16) { defKind.put(dst, "ZEXT16"); continue; }
                defKind.put(dst, "OTHER");
            }

            // Phase 2: build use edges. kind: 0=NARROW_OP operand, 1=TRUNC_SINK, 2=POISON.
            Map<String,List<int[]>> uses = new HashMap<>();
            Map<Integer,String>     lineDefName = new HashMap<>();
            for (int i = 0; i < n; i++) {
                Matcher md = pDef.matcher(body.get(i));
                String dstHere = md.matches() ? md.group(1) : null;
                String rhs = md.matches() ? md.group(2).trim() : body.get(i).trim();
                lineDefName.put(i, dstHere);
                Matcher mt = pTrunc.matcher(rhs);
                Matcher mp = pProd.matcher(rhs);
                boolean prodConsumer = mp.matches()
                    && (!mp.group(1).equals("shl") || parseImmediate(mp.group(4)).isPresent());
                boolean truncConsumer = mt.matches();
                Set<String> ops = new LinkedHashSet<>();
                Matcher mu = pName.matcher(rhs);
                while (mu.find()) ops.add(mu.group(1));
                for (String v : ops) {
                    if (!defLine.containsKey(v)) continue;   // param / global / not our def
                    int kind;
                    if (truncConsumer && mt.group(1).equals("%" + v)) kind = 1;
                    else if (prodConsumer
                             && (mp.group(3).equals("%" + v) || mp.group(4).equals("%" + v))) kind = 0;
                    else kind = 2;
                    uses.computeIfAbsent(v, k -> new ArrayList<>()).add(new int[]{i, kind});
                }
            }

            // Phase 3: fixpoint. Start optimistic (all producers + zext16 leaves), then
            // drop any value with a poison use or a narrow-op use whose consumer is not
            // (or no longer) narrowable. Monotone removal => terminates.
            Set<String> N = new LinkedHashSet<>();
            for (var e : defKind.entrySet())
                if (e.getValue().equals("PRODUCER") || e.getValue().equals("ZEXT16")) N.add(e.getKey());
            boolean changed = true;
            while (changed) {
                changed = false;
                List<String> drop = new ArrayList<>();
                for (String v : N) {
                    for (int[] u : uses.getOrDefault(v, List.of())) {
                        if (u[1] == 2) { drop.add(v); break; }
                        if (u[1] == 0) {
                            String c = lineDefName.get(u[0]);
                            if (c == null || !N.contains(c)) { drop.add(v); break; }
                        }
                    }
                }

                if (!drop.isEmpty()) { N.removeAll(drop); changed = true; }
            }

            // Phase 4: rewrite the narrowable set.
            //
            // Producers are retyped i32 -> i16 in place. zext16 leaves and trunc sinks
            // become identities once their cone is i16 (a zext of an i16 value that is
            // now used as i16, and a trunc-to-i16 of a value that is now i16), so instead
            // of emitting `add i16 %x, 0` no-op copies we copy-propagate: alias the dead
            // def to its source and substitute references. This removes the def line
            // entirely (SSA dominance guarantees the source is in scope at every use).
            int narrowed = 0;
            Map<String,String> alias = new LinkedHashMap<>();  // deadDef -> source value token (e.g. "%8")
            // 4a: producers -> retype operand i32 -> i16; zext16 leaves -> alias to source.
            for (String v : N) {
                int i = defLine.get(v);
                String line = body.get(i);
                if (defKind.get(v).equals("PRODUCER")) {
                    // Anchored after "= op flags" so only the operand type token is hit,
                    // never a cast's "to i32". Includes div/rem when a bounds check
                    // classified them narrowable (dividend/divisor provably fit).
                    body.set(i, line.replaceFirst(
                        "(=\\s*(?:add|sub|mul|and|or|xor|shl|udiv|sdiv|urem|srem)\\s+(?:(?:nsw|nuw|exact)\\s+)*)i32(\\s)",
                        "$1i16$2"));
                } else { // ZEXT16: the zext is now identity â€” alias %v to its source.
                    Matcher mz = pZext16.matcher(rhsOf.get(v)); mz.matches();
                    alias.put("%" + v, mz.group(2));           // e.g. %9 -> %8
                    String indent = line.substring(0, line.indexOf('%'));
                    body.set(i, indent + "; narrowed: %" + v + " = zext " + mz.group(2));
                }

                narrowed++;
            }

            // 4b: trunc sinks whose source is narrowed -> alias to source (now i16).
            for (int i = 0; i < n; i++) {
                Matcher md = pDef.matcher(body.get(i));
                if (!md.matches()) continue;
                Matcher mt = pTrunc.matcher(md.group(2).trim());
                if (!mt.matches() || !mt.group(1).startsWith("%")) continue;
                String src = mt.group(1).substring(1);
                if (!N.contains(src)) continue;
                alias.put("%" + md.group(1), mt.group(1));     // %t -> %v
                String line = body.get(i);
                String indent = line.substring(0, line.indexOf('%'));
                body.set(i, indent + "; narrowed: %" + md.group(1) + " = trunc " + mt.group(1));
            }

            // 4c: resolve alias chains (a trunc of a value that is itself an alias target,
            // or a zext whose source is later aliased), then substitute every reference.
            if (!alias.isEmpty()) {
                for (String k : new ArrayList<>(alias.keySet())) {
                    String t = alias.get(k);
                    Set<String> seen = new HashSet<>();
                    while (alias.containsKey(t) && seen.add(t)) t = alias.get(t);
                    alias.put(k, t);
                }

                // Word-boundary-safe replacement of each dead SSA name with its source.
                // Sort longest-first so %12 is not partially matched inside %120.
                List<String> deads = new ArrayList<>(alias.keySet());
                deads.sort((a, b) -> b.length() - a.length());
                for (int i = 0; i < n; i++) {
                    String line = body.get(i);
                    if (line.contains("; narrowed:") || !line.contains("%")) continue;
                    boolean hit = false;
                    for (String dead : deads) if (line.contains(dead)) { hit = true; break; }
                    if (!hit) continue;
                    for (String dead : deads) {
                        // \Q..\E literal; negative lookahead so %12 doesn't match %123.
                        line = line.replaceAll(java.util.regex.Pattern.quote(dead) + "(?![\\w.])",
                                               java.util.regex.Matcher.quoteReplacement(alias.get(dead)));
                    }

                    body.set(i, line);
                }
            }

            if (narrowed > 0) {
                System.err.println("[narrow] " + f.name() + ": narrowed " + narrowed + " i32 value(s) to i16");
            }
        }

        /**
         * Conservative unsigned upper bound for every i32 SSA value in the body,
         * saturating at WIDE (0x10000) meaning "may exceed 0xFFFF / unknown". Used only
         * to prove a div/rem dividend&divisor fit so the op can safely narrow to the
         * native 16-bit DIV/DIVS. Every bound over-approximates, so a reported "<=0x7FFF"
         * is always true. Single linear pass in SSA order (defs precede uses at -O0).
         */
        static Map<String,Long> computeBounds(List<String> body, Pattern pDef, Pattern pName) {
            final long WIDE = 0x1_0000L;
            Map<String,Long> b = new HashMap<>();
            Pattern pZ = Pattern.compile("^zext\\s+(i\\d+)\\s+\\S+\\s+to\\s+i32$");
            Pattern pOp = Pattern.compile(
                "^(add|sub|and|or|xor|mul|shl)\\s+(?:(?:nsw|nuw|exact)\\s+)*i32\\s+(\\S+?),\\s*(\\S+)$");
            // Seed bounds for counted-loop induction variables. LLVM emits the loop
            // counter as a phi (`%i = phi i32 [ C0, %pre ], [ %next, %latch ]`) which the
            // forward scan below treats as WIDE â€” yet it provably stays below the trip
            // count. When the counter is compared against a constant limit N (icmp on the
            // phi or its increment) and stepped by a non-negative constant, every value of
            // the phi that reaches a use is < N, so bounding the phi (and its increment) by
            // max(C0, N-1) is sound. This is what lets index math like (map_y+i)*map_w â€”
            // where i is the loop counter â€” be proved to fit 16 bits and use the hardware
            // 16x16 multiply. Requires the whole body (all blocks) â€” true here since we
            // pass func.body().
            Map<String,Long> phiSeed = seedInductionBounds(body, pDef, WIDE);
            // Two forward passes: the first computes straight-line bounds; the second lets
            // induction-derived values (add/and/etc. off a seeded phi) settle. Monotone and
            // bounded (values only ever move up toward WIDE), so two passes suffice for the
            // one level of phi indirection these index expressions use.
            for (int pass = 0; pass < 2; pass++) {
            for (String line : body) {
                Matcher md = pDef.matcher(line);
                if (!md.matches()) continue;
                String dst = md.group(1);
                String rhs = md.group(2).trim().replaceAll(",?\\s*align\\s+\\d+\\s*$", "").trim();
                long v;
                Matcher mz = pZ.matcher(rhs);
                Matcher mo = pOp.matcher(rhs);
                if (phiSeed.containsKey(dst)) {
                    v = phiSeed.get(dst);      // induction variable: use the seeded bound
                } else if (mz.matches()) {
                    int k = irTypeBits(mz.group(1));
                    v = k >= 16 ? 0xFFFFL : ((1L << k) - 1);
                } else if (mo.matches()) {
                    String op = mo.group(1);
                    long l = boundOf(mo.group(2), b, WIDE);
                    long r = boundOf(mo.group(3), b, WIDE);
                    v = switch (op) {
                        case "add" -> sat(l + r, WIDE);
                        case "sub" -> l;                          // <= dividend (result <= minuend for unsigned)
                        case "and" -> Math.min(l, r);             // AND cannot exceed either operand
                        case "or", "xor" -> sat(l + r, WIDE);     // loose but sound-ish upper bound
                        default -> WIDE;                          // mul/shl: assume wide
                    };

                } else {
                    v = WIDE;   // load / call / phi / trunc / sext / etc. â€” unknown
                }

                b.put(dst, Math.min(v, WIDE));
            }
            }

            return b;
        }

        // Recognize counted-loop induction variables and return a sound unsigned upper
        // bound for each. A phi `%i = phi i32 [ C0, ... ], [ %next, ... ]` where
        // `%next = add [nuw nsw] i32 %i, C1` (C1 >= 0) and %i or %next is compared against
        // a constant limit N (`icmp eq/ne/ult/ule/slt/sle`) is bounded by max(C0, N-1) â€”
        // clamped to <= 0xFFFF only when that proof holds, else omitted (caller sees WIDE).
        static Map<String,Long> seedInductionBounds(List<String> body, Pattern pDef, long WIDE) {
            Map<String,Long> seed = new HashMap<>();
            Pattern pPhi = Pattern.compile(
                "^phi\\s+i32\\s+\\[\\s*(\\S+?),.*?\\],\\s*\\[\\s*(\\S+?),.*?\\]\\s*$");
            Pattern pInc = Pattern.compile(
                "^add\\s+(?:(?:nuw|nsw)\\s+)*i32\\s+(\\S+?),\\s*(\\S+)$");
            Pattern pCmp = Pattern.compile(
                "^icmp\\s+(eq|ne|ult|ule|slt|sle)\\s+i32\\s+(\\S+?),\\s*(\\S+)$");
            // Collect def rhs per name.
            Map<String,String> rhsOf = new HashMap<>();
            for (String line : body) {
                Matcher md = pDef.matcher(line);
                if (md.matches()) rhsOf.put(md.group(1),
                        md.group(2).trim().replaceAll(",?\\s*align\\s+\\d+\\s*$", "").trim());
            }

            for (Map.Entry<String,String> e : rhsOf.entrySet()) {
                String phiName = e.getKey();
                Matcher mphi = pPhi.matcher(e.getValue());
                if (!mphi.matches()) continue;
                String initTok = mphi.group(1), stepTok = mphi.group(2);
                OptionalLong initImm = parseImmediate(initTok);
                if (initImm.isEmpty() || initImm.getAsLong() < 0) continue;
                if (!stepTok.startsWith("%")) continue;
                // The back-edge value must be `add %phi, C1` (C1 >= 0), i.e. a monotone
                // non-decreasing counter derived from this same phi.
                String stepRhs = rhsOf.get(stepTok.substring(1));
                if (stepRhs == null) continue;
                Matcher minc = pInc.matcher(stepRhs);
                if (!minc.matches()) continue;
                String incBase = minc.group(1), incAmt = minc.group(2);
                OptionalLong incImm = parseImmediate(incAmt);
                if (!("%" + phiName).equals(incBase) || incImm.isEmpty() || incImm.getAsLong() < 0) continue;
                // Find a constant limit N from an icmp on the phi or its increment.
                long limit = -1;
                for (String rhs2 : rhsOf.values()) {
                    Matcher mc = pCmp.matcher(rhs2);
                    if (!mc.matches()) continue;
                    String a = mc.group(2), c = mc.group(3);
                    boolean onIv = a.equals("%" + phiName) || a.equals(stepTok);
                    if (!onIv) continue;
                    OptionalLong lim = parseImmediate(c);
                    if (lim.isPresent() && lim.getAsLong() >= 0) { limit = lim.getAsLong(); break; }
                }

                if (limit < 0) continue;
                // Sound upper bound: the counter starts at C0 and every value reaching a
                // use is < N (the compare exits at N), so max is max(C0, N-1). Only seed
                // when it proves the value fits 16 bits.
                long bnd = Math.max(initImm.getAsLong(), limit - 1);
                if (bnd <= 0xFFFFL) {
                    seed.put(phiName, bnd);
                    seed.put(stepTok.substring(1), Math.min(bnd + incImm.getAsLong(), WIDE));
                }
            }

            return seed;
        }

        /** Bound of a producer operand token: constant -> its value; %name -> its bound; else WIDE. */
        static long boundOf(String tok, Map<String,Long> b, long WIDE) {
            OptionalLong imm = parseImmediate(tok);
            if (imm.isPresent()) { long v = imm.getAsLong(); return (v < 0 || v >= WIDE) ? WIDE : v; }
            if (tok.startsWith("%")) return b.getOrDefault(tok.substring(1), WIDE);
            return WIDE;
        }

        /** Public helper mirroring boundOf for the div/rem operand check in narrowFunction. */
        static long operandBound(String tok, Map<String,Long> b, long WIDE) {
            return boundOf(tok, b, WIDE);
        }

        static long sat(long v, long WIDE) { return (v < 0 || v >= WIDE) ? WIDE : v; }
        static void emitC9HRuntime(List<String> out) {
            out.add("");
            // _C9H_mullu : unsigned 16×16 → low-16  (far __cdecl)
            //   (XSP+0..3)=far-ret, (XSP+4..5)=RHS, (XSP+6..7)=LHS
            //   Returns: HL = product low 16.  Clobbers WA, BC, DE.
            //   Algorithm: shift-and-add, 16 iterations.
            //   Counter goes in HL (saved/restored around XDE accumulator use).
            //   We use XDE as accumulator and keep the loop counter in L so it
            //   is not clobbered by "sla 1, wa" (which shifts the A register).
            out.add("_C9H_mullu:");
            out.add("\tpush xbc");
            out.add("\tpush xde");
            out.add("\tld de, 0");
            out.add("\tld wa, (xsp+14)");  // multiplicand â€” offset +8 due to push xbc + push xde
            out.add("\tld bc, (xsp+12)");  // multiplier   â€” offset +8 due to push xbc + push xde
            out.add("\tld hl, 16");        // loop counter in HL (not WA!)
            out.add("._mul16_loop:");
            out.add("\tsrl 1, bc");
            out.add("\tjr NC, ._mul16_skip");
            out.add("\tadd de, wa");
            out.add("._mul16_skip:");
            out.add("\tsla 1, wa");
            out.add("\tsub hl, 1");        // decrement counter (HL, not A)
            out.add("\tjr NZ, ._mul16_loop");
            out.add("\tld hl, de");
            out.add("\tpop xde");
            out.add("\tpop xbc");
            out.add("\tret");
            out.add("");
            // _C9H_divlu : unsigned 32÷32 → quot:XHL, rem:XDE  (far __cdecl)
            //   (XSP+4..7)=divisor, (XSP+8..11)=dividend
            //   Algorithm: shift-and-subtract (non-restoring), 32 iterations.
            //   Registers: XWA=running dividend, XBC=divisor, XDE=remainder, XHL=quotient
            out.add("_C9H_divlu:");
            out.add("\tpush xbc");
            out.add("\tld xwa, (xsp+12)");  // dividend  â€” offset +4 due to push xbc
            out.add("\tld xbc, (xsp+8)");   // divisor   â€” offset +4 due to push xbc
            out.add("\tld xde, 0");
            out.add("\tld xhl, 0");
            out.add("\tld l, 32");           // loop counter in L (low byte of XHL, safe: XHL=0 until end)
            out.add("._div32u_loop:");
            out.add("\tsla 1, xwa");           // CF = old MSB of dividend
            out.add("\tadc xde, xde");         // remainder = (remainder<<1)|CF
            out.add("\tcp xde, xbc");
            out.add("\tjr C, ._div32u_nosub");
            out.add("\tsub xde, xbc");
            out.add("\tor xwa, 1");            // set quotient bit in LSB of XWA
            out.add("._div32u_nosub:");
            out.add("\tsub l, 1");             // decrement counter (L not aliased to XWA/XDE/XBC)
            out.add("\tjr NZ, ._div32u_loop");
            out.add("\tld xhl, xwa");          // quotient → XHL
            out.add("\tpop xbc");
            out.add("\tret");
            //   Sign strategy: negate the operands if needed, delegate to _C9H_divlu,
            //   then derive both result signs from the original stack arguments. Do not
            //   keep the quotient sign in B: loading the divisor into XBC overwrites B.
            //   The remainder takes the dividend's sign; the quotient is negative when
            //   the original operand signs differ.
            out.add("_C9H_divls:");
            out.add("\tpush xbc");
            // Fast path for the common i32/i16 case. DIVS performs signed 32/16
            // division and packs remainder:quotient into XWA. Fall back to the
            // full software divider when the divisor or quotient does not fit i16.
            out.add("\tld xbc, (xsp+8)");
            out.add("\tld wa, bc");
            out.add("\texts xwa");
            out.add("\tcp xwa, xbc");
            out.add("\tjr NZ, ._div32s_full");
            out.add("\tld xwa, (xsp+12)");
            out.add("\tdivs xwa, bc");
            out.add("\tjr OV, ._div32s_full");
            out.add("\tld xde, xwa");
            out.add("\tsra 8, xde");
            out.add("\tsra 8, xde");
            out.add("\tld hl, wa");
            out.add("\texts xhl");
            out.add("\tpop xbc");
            out.add("\tret");
            out.add("._div32s_full:");
            out.add("\tld xwa, (xsp+12)");        // dividend â€” offset +4 due to push xbc
            out.add("\tbit 7, (xsp+15)");          // sign bit of dividend high byte
            out.add("\tjr Z, ._divs_div_ok");
            out.add("\txor xwa, 0FFFFFFFFh");       // negate xwa: ~x + 1
            out.add("\tadd xwa, 1");
            out.add("._divs_div_ok:");
            out.add("\tld xbc, (xsp+8)");          // divisor â€” offset +4 due to push xbc
            out.add("\tbit 7, (xsp+11)");          // sign bit of divisor high byte
            out.add("\tjr Z, ._divs_dvs_ok");
            out.add("\txor xbc, 0FFFFFFFFh");       // negate xbc: ~x + 1
            out.add("\tadd xbc, 1");
            out.add("._divs_dvs_ok:");
            out.add("\tpush xwa");                 // dividend → (xsp+8)
            out.add("\tpush xbc");                 // divisor  → (xsp+4)
            out.add("\tcall _C9H_divlu");
            out.add("\tadd xsp, 8");
            out.add("\tbit 7, (xsp+15)");          // dividend negative?
            out.add("\tjr Z, ._divs_rem_ok");
            out.add("\txor xde, 0FFFFFFFFh");       // negate xde: ~x + 1
            out.add("\tadd xde, 1");
            out.add("._divs_rem_ok:");
            out.add("\tbit 7, (xsp+15)");          // compare original operand signs
            out.add("\tjr Z, ._divs_div_positive");
            out.add("\tbit 7, (xsp+11)");
            out.add("\tjr NZ, ._divs_quot_ok");     // negative / negative
            out.add("\tjr ._divs_quot_negative");   // negative / positive
            out.add("._divs_div_positive:");
            out.add("\tbit 7, (xsp+11)");
            out.add("\tjr Z, ._divs_quot_ok");
            out.add("._divs_quot_negative:");       // positive / negative
            out.add("\txor xhl, 0FFFFFFFFh");       // negate xhl: ~x + 1
            out.add("\tadd xhl, 1");
            out.add("._divs_quot_ok:");
            out.add("\tpop xbc");
            out.add("\tret");
            out.add("");
            // _C9H_sll / _C9H_sra / _C9H_srl â€” shift HL by A bits, return HL
            //   (XSP+4..5)=shift count, (XSP+6..7)=value.  Returns HL.
            for (String[] helper : new String[][]{
                    {"_C9H_sll", "sll"},
                    {"_C9H_sra", "sra"},
                    {"_C9H_srl", "srl"}}) {
                String hname = helper[0], mnem = helper[1];
                out.add(hname + ":");
                out.add("\tld hl, (xsp+6)");
                out.add("\tld a, (xsp+4)");
                out.add("\tor a, a");
                out.add("\tjr Z, ._" + mnem + "_done");
                out.add("._" + mnem + "_loop:");
                out.add("\t" + mnem + " 1, hl");
                out.add("\tsub a, 1");
                out.add("\tjr NZ, ._" + mnem + "_loop");
                out.add("._" + mnem + "_done:");
                out.add("\tret");
                out.add("");
            }

            // _C9H_mul32 : 32×32 → low-32  (far __cdecl)
            //   (XSP+4..7)=RHS, (XSP+8..11)=LHS.  Returns XHL = product low 32 bits.
            //   Signed and unsigned agree on the low 32 bits, so one helper serves both.
            //   Algorithm: shift-and-add, 32 iterations. XDE=accumulator,
            //   XWA=LHS (shifted left each step), XBC=RHS (shifted right each step).
            //   Loop counter in XHL â€” not in A, because "sla 1, xwa" shifts through A.
            //   XBC is callee-saved per CC900 â€” push/pop around its use.
            out.add("_C9H_mul32:");
            out.add("\tpush xbc");
            out.add("\tpush xde");
            out.add("\tld xde, 0");
            out.add("\tld xwa, (xsp+16)");   // LHS â€” offset +8 due to push xbc + push xde
            out.add("\tld xbc, (xsp+12)");   // RHS â€” offset +8 due to push xbc + push xde
            out.add("\tld xhl, 32");         // loop counter (not A â€” sla clobbers A)
            out.add("._mul32_loop:");
            out.add("\tsrl 1, xbc");         // CF = current LSB of multiplier
            out.add("\tjr NC, ._mul32_skip");
            out.add("\tadd xde, xwa");       // accumulate shifted multiplicand
            out.add("._mul32_skip:");
            out.add("\tsla 1, xwa");         // multiplicand <<= 1
            out.add("\tsub xhl, 1");         // decrement counter
            out.add("\tjr NZ, ._mul32_loop");
            out.add("\tld xhl, xde");        // product → XHL
            out.add("\tpop xde");
            out.add("\tpop xbc");
            out.add("\tret");
            out.add("");
            // _C9H_sll32 / _C9H_sra32 / _C9H_srl32 â€” shift XHL by count, return XHL.
            //   (XSP+4..5)=shift count (16-bit), (XSP+6..9)=value (32-bit). Returns XHL.
            for (String[] helper : new String[][]{
                    {"_C9H_sll32", "sla"},   // logical left == arithmetic left
                    {"_C9H_sra32", "sra"},
                    {"_C9H_srl32", "srl"}}) {
                String hname = helper[0], mnem = helper[1];
                out.add(hname + ":");
                out.add("\tld xhl, (xsp+6)");
                out.add("\tld a, (xsp+4)");
                out.add("\tor a, a");
                out.add("\tjr Z, ._" + hname + "_done");
                out.add("._" + hname + "_loop:");
                out.add("\t" + mnem + " 1, xhl");
                out.add("\tsub a, 1");
                out.add("\tjr NZ, ._" + hname + "_loop");
                out.add("._" + hname + "_done:");
                out.add("\tret");
                out.add("");
            }
        }
    }

    // =========================================================================
    // Peephole: redundant stack-spill elimination
    //
    // The SSA-to-stack translator emits a spill for every SSA value, which
    // produces chains like:
    //
    //   ld wa, 0 / ld a, (xsp+N)   <- load from alloca slot N
    //   ld (xsp+M), a               <- spill to SSA slot M
    //   ld wa, 0 / ld a, (xsp+M)   <- immediately reload from M
    //   ld (xsp+P), a               <- spill to another slot P
    //   ld wa, 0 / ld a, (xsp+P)   <- immediately reload from P
    //   ... actual use ...
    //
    // Two rules, applied to a fixed point:
    //
    //   Rule A â€” store/load same slot same register:
    //     ld (xsp+N), R  followed immediately by  ld R, (xsp+N)
    //     → drop the load (value is already in R)
    //
    //   Rule B â€” load/store/reload triple:
    //     ld R, (xsp+N)  /  ld (xsp+M), R  /  ld R, (xsp+M)
    //     → drop lines 2 and 3 (value stays in R)
    //
    // "Immediately" means: no intervening non-comment, non-blank lines that
    // touch R or branch.  Labels are treated as barriers (a reload after a
    // label might be reached from elsewhere with a different register state).

    // =========================================================================

    // =========================================================================
    // Peephole: redundant stack-spill elimination
    //
    // Every IR value gets its own stack slot, producing chains like:
    //
    //   ld wa, 0 / ld a, (xsp+N)   <- load slot N (byte)
    //   extz xwa                    <- optional transform (zext/sext)
    //   ld (xsp+M), xwa             <- spill result to slot M
    //   ld xwa, (xsp+M)             <- immediate reload
    //   ... actual use of xwa ...
    //
    // A "load unit" is: optional "ld wa, 0", a stack load, and optional
    // single-register transforms (extz/exts).  The unit's effective register
    // is the register as it stands after all transforms.
    //
    // Two rules applied to a fixed point:
    //   Rule A: ld (xsp+N), R  then  ld R, (xsp+N)  → drop the reload
    //   Rule B: load-unit(→R, from N) / ld (xsp+M), R / load-unit(→R, from M)
    //           → drop the store and the second load-unit
    //
    // Labels are barriers.  Comments and blank lines are transparent.

    // =========================================================================

    /** Index of next non-dropped, non-blank, non-comment line >= start, or -1. */
    static int nextSig(List<String> lines, boolean[] drop, int start) {
        for (int i = start; i < lines.size(); i++) {
            if (!drop[i] && !lines.get(i).matches("^\\s*(;.*)?$")) return i;
        }

        return -1;
    }

    /**
     * ISR save trimming.
     *
     * An interrupt handler emitted by this translator opens with an unconditional
     * `push xwa/xhl/xde/xbc/xix/xiy` prologue and closes each return path with the
     * mirrored `pop` sequence before `reti`. That saves all six pairs even when the
     * body only clobbers, say, WA and HL — 4 wasted push/pop pairs on every entry
     * of a per-frame handler.
     *
     * This pass removes the push (and its matching pops before every reti in the
     * same function) for any of the six registers that the body never writes.
     * Correctness: a register that is never written cannot be corrupted, so the
     * interrupted code sees it unchanged whether or not we saved it. Dropping a
     * push and ALL its matching pops keeps XSP balanced on every path.
     *
     * Guard: only functions whose prologue is EXACTLY the six-push ISR sequence
     * right after the label are touched, and only registers proven unclobbered are
     * dropped. `add xsp,N` frame lines and argument offsets are unaffected because
     * they are computed relative to XSP AFTER the (retained) saves — and ISRs in
     * practice take no parameters, so no arg offset depends on the dropped saves.
     */
    static List<String> trimIsrSaves(List<String> in) {
        final List<String> isrRegs = List.of("xwa", "xhl", "xde", "xbc", "xix", "xiy");
        // Locate a function label line (column-0 "_name:") whose next six non-blank
        // lines are exactly the ISR pushes in order.
        int labelIdx = -1;
        for (int i = 0; i < in.size(); i++) {
            String t = in.get(i);
            if (!t.matches("^_[\\w.$]+:\\s*$")) continue;
            // Collect the next 6 significant lines.
            List<Integer> pushIdx = new ArrayList<>();
            int j = i + 1;
            for (; j < in.size() && pushIdx.size() < 6; j++) {
                String s = in.get(j).trim();
                if (s.isEmpty() || s.startsWith(";")) continue;
                if (s.equals("push " + isrRegs.get(pushIdx.size()))) pushIdx.add(j);
                else break;
            }

            if (pushIdx.size() != 6) continue;   // not the ISR prologue shape
            // Function body spans until the next column-0 label or end of list.
            int end = in.size();
            for (int k = j; k < in.size(); k++) {
                if (in.get(k).matches("^_[\\w.$]+:\\s*$")) { end = k; break; }
            }

            // Determine which of the six regs the body clobbers. Belt-and-braces:
            // a reg counts as clobbered if clobbersReg says so OR its family name
            // is even mentioned anywhere in the body (guards against any opcode
            // clobbersReg does not model — we never drop a save we're unsure about).
            Map<String, Set<String>> fam = Map.of(
                "xwa", Set.of("xwa","wa","a","w"), "xhl", Set.of("xhl","hl","l","h"),
                "xde", Set.of("xde","de","e","d"), "xbc", Set.of("xbc","bc","b","c"),
                "xix", Set.of("xix","ix"), "xiy", Set.of("xiy","iy"));
            // A call anywhere in the ISR body forces us to keep ALL saves: the ISR
            // interrupts arbitrary code and the callee (and its transitive callees)
            // may use any register — including the callee-saved xbc/xde/xix/xiy the
            // ISR itself never names. `clobbersReg` deliberately reports a call as
            // clobbering only caller-saved regs, which is the wrong assumption for an
            // ISR prologue that must protect the *interrupted* context. (This is the
            // bomberman _mp_play_hw case: the VBL handler calls the music player,
            // which uses XIX/XIY.) So bail out of trimming entirely for any ISR that
            // contains a call.
            boolean hasCall = false;
            for (int k = j; k < end; k++) {
                String s = in.get(k).trim();
                if (s.startsWith("call ") || s.startsWith("calr ")) { hasCall = true; break; }
            }

            if (hasCall) { labelIdx = i; continue; }
            Set<String> clobbered = new LinkedHashSet<>();
            for (String reg : isrRegs) {
                boolean touched = false;
                for (int k = j; k < end && !touched; k++) {
                    String raw = in.get(k).trim();
                    // The handler's own save/restore of an ISR reg is not a clobber of
                    // the interrupted code's value — skip push/pop of any isrReg so the
                    // mirrored epilogue pops don't spuriously mark a reg as used.
                    if (raw.matches("(push|pop)\\s+(xwa|xhl|xde|xbc|xix|xiy)")) continue;
                    String body = raw.toLowerCase(Locale.ROOT);
                    if (clobbersReg(in.get(k), reg)) { touched = true; break; }
                    for (String r : fam.get(reg)) {
                        // Word-boundary match so "a" does not match inside "add"/"and".
                        if (body.matches(".*(?<![a-z0-9])" + r + "(?![a-z0-9]).*")) { touched = true; break; }
                    }
                }

                if (touched) clobbered.add(reg);
            }

            if (clobbered.size() == 6) { labelIdx = i; continue; } // nothing to trim; move on
            // Drop unclobbered pushes and their mirrored pops (before every reti).
            Set<Integer> dropLines = new LinkedHashSet<>();
            for (String reg : isrRegs) {
                if (clobbered.contains(reg)) continue;
                dropLines.add(pushIdx.get(isrRegs.indexOf(reg)));
                for (int k = j; k < end; k++) {
                    if (in.get(k).trim().equals("pop " + reg)) dropLines.add(k);
                }
            }

            if (!dropLines.isEmpty()) {
                List<String> out = new ArrayList<>(in.size());
                for (int k = 0; k < in.size(); k++) if (!dropLines.contains(k)) out.add(in.get(k));
                return trimIsrSaves(out);   // re-scan for further ISR functions
            }

            labelIdx = i;
        }

        return in;
    }

    static List<String> eliminateSpills(List<String> in) {
        Pattern pStore    = Pattern.compile("^\\s+ld\\s+\\(xsp\\+(\\d+)\\),\\s*(\\S+)\\s*(?:;.*)?$");
        Pattern pLoad     = Pattern.compile("^\\s+ld\\s+(\\S+),\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$");
        // Src-mem ALU read: `add/sub/adc/sbc REG, (xsp+N)` reads slot N (group 1).
        Pattern pAluMemRead = Pattern.compile("^\\s+(?:add|sub|adc|sbc)\\s+\\S+,\\s*\\(xsp\\+(\\d+)\\)\\s*(?:;.*)?$");
        Pattern pWaClear  = Pattern.compile("^\\s+ld\\s+wa,\\s*0\\s*(?:;.*)?$");
        Pattern pTransform = Pattern.compile("^\\s+(extz|exts)\\s+(xwa|xhl|a)\\s*(?:;.*)?$");
        Pattern pLabel    = Pattern.compile("^[.\\w].*:.*$");
        // Pure register-to-register move: "ld DST, SRC" where both operands are
        // register names (bare identifiers â€” no memory operand '(', no immediate).
        Pattern pRegMove  = Pattern.compile("^\\s+ld\\s+([a-z]\\w*),\\s*([a-z]\\w*)\\s*(?:;.*)?$");
        // A load unit: a sequence of lines that loads a value from a stack slot
        // into a register (possibly transforming it), with no side-effects on
        // other slots or branches.
        List<String> cur = new ArrayList<>(in);
        boolean changed = true;
        while (changed) {
            changed = false;
            boolean[] drop = new boolean[cur.size()];
            // Rule D: dead-store elimination.
            // Build a set of frame offsets that are actually read (adjusting for
            // SP changes from push instructions before each load).
            // A store ld(xsp+N) is live if a load reads the same physical slot
            // before the store is overwritten. Because push/pop shift XSP, the
            // reload's literal offset M relates to the store's offset N by the
            // net SP delta between them: M = N + (bytes pushed since the store).
            // We therefore scan forward from each store, tracking the SP delta,
            // and keep the store if any matching load is found.
            //
            // NB: only argument-marshalling pushes emitted after this store count.
            // The prologue callee-save pushes (push xbc/xde/xix/xiy) and the frame
            // `add xsp,-N` happen before the body and are already baked into every
            // slot offset, so they must never enter this delta â€” we only start
            // counting at the store itself, so they are naturally excluded.

            {

                Pattern pPush = Pattern.compile("\\s*push\\s+(\\S+)\\s*(?:;.*)?");
                Pattern pPop  = Pattern.compile("\\s*pop\\s+(\\S+)\\s*(?:;.*)?");
                Pattern pAddSp = Pattern.compile("\\s*add\\s+xsp,\\s*(-?\\d+)\\s*(?:;.*)?");
                for (int i = 0; i < cur.size(); i++) {
                    if (drop[i]) continue;
                    Matcher mSt = pStore.matcher(cur.get(i));
                    if (!mSt.matches()) continue;
                    int stOff = Integer.parseInt(mSt.group(1));
                    int spDelta = 0;       // bytes XSP has moved DOWN since the store
                    boolean live = false;
                    for (int j = i + 1; j < cur.size(); j++) {
                        if (drop[j]) continue;
                        String lj = cur.get(j);
                        // A label starts a new basic block: the slot may be read on a
                        // successor path we can't follow linearly. Treat as live
                        // (conservative â€” keep the store) rather than risk dropping a
                        // store whose only reader is in another block.
                        if (pLabel.matcher(lj).matches()) { live = true; break; }
                        Matcher mPush = pPush.matcher(lj);
                        if (mPush.matches()) {
                            spDelta += mPush.group(1).toLowerCase().startsWith("x") ? 4 : 2;
                            continue;
                        }

                        Matcher mPop = pPop.matcher(lj);
                        if (mPop.matches()) {
                            spDelta -= mPop.group(1).toLowerCase().startsWith("x") ? 4 : 2;
                            continue;
                        }

                        Matcher mAdd = pAddSp.matcher(lj);
                        if (mAdd.matches()) { spDelta -= Integer.parseInt(mAdd.group(1)); continue; }
                        Matcher mLd = pLoad.matcher(lj);
                        if (mLd.matches() && Integer.parseInt(mLd.group(2)) == stOff + spDelta) {
                            live = true; break;
                        }

                        // A src-mem ALU op reads the slot as its source operand.
                        Matcher mAlu = pAluMemRead.matcher(lj);
                        if (mAlu.matches() && Integer.parseInt(mAlu.group(1)) == stOff + spDelta) {
                            live = true; break;
                        }

                        // A later store to the same physical slot kills this store's value.
                        Matcher mSt2 = pStore.matcher(lj);
                        if (mSt2.matches() && Integer.parseInt(mSt2.group(1)) == stOff + spDelta) break;
                        // A branch may reach a reader earlier in textual order (a loop
                        // backedge) or on another successor. Linear forward scanning
                        // cannot prove the store dead across that CFG edge. Keep it.
                        String tj = lj.trim().toLowerCase(Locale.ROOT);
                        if (tj.startsWith("jr ") || tj.startsWith("jrl ")
                                || tj.startsWith("jp ") || tj.startsWith("ret")
                                || tj.startsWith("reti")) {
                            live = true; break;
                        }
                    }

                    if (!live) { drop[i] = true; changed = true; }
                }
            }

            if (changed) {
                List<String> next = new ArrayList<>(cur.size());
                for (int i = 0; i < cur.size(); i++) if (!drop[i]) next.add(cur.get(i));
                cur = next;
                // Restart the full loop after dead-store removal; reload patterns
                // may now be eligible for elimination.
                drop = new boolean[cur.size()];
            }

            // Rule F: stack-slot copy-forward.
            //   ld R, (xsp+S)
            //   ld (xsp+D), R
            //   ...
            //   ld R, (xsp+D)
            // -> rewrite last load to ld R, (xsp+S) until S/D are redefined/barrier.
            // This is generic and especially helps O0-style temporary slot churn.
            for (int i = 0; i < cur.size(); i++) {
                if (drop[i]) continue;
                Matcher mLd = pLoad.matcher(cur.get(i));
                if (!mLd.matches()) continue;
                String reg = mLd.group(1);
                String srcOff = mLd.group(2);
                int j = nextSig(cur, drop, i + 1);
                if (j < 0) continue;
                Matcher mSt = pStore.matcher(cur.get(j));
                if (!mSt.matches()) continue;
                String dstOff = mSt.group(1);
                String stReg  = mSt.group(2);
                if (!stReg.equals(reg) || dstOff.equals(srcOff)) continue;
                int k = j;
                while (true) {
                    k = nextSig(cur, drop, k + 1);
                    if (k < 0) break;
                    String lk = cur.get(k);
                    if (isSlotForwardBarrier(lk, pLabel)) break;
                    Matcher mSt2 = pStore.matcher(lk);
                    if (mSt2.matches()) {
                        String off = mSt2.group(1);
                        if (off.equals(srcOff) || off.equals(dstOff)) break;
                        continue;
                    }

                    Matcher mLd2 = pLoad.matcher(lk);
                    if (mLd2.matches() && mLd2.group(1).equals(reg) && mLd2.group(2).equals(dstOff)) {
                        cur.set(k, "\tld " + reg + ", (xsp+" + srcOff + ")");
                        changed = true;
                    }
                }
            }

            for (int i = 0; i < cur.size(); i++) {
                if (drop[i]) continue;
                String line = cur.get(i);
                // Try to parse a load unit starting at i.
                LoadUnit lu = parseLoadUnit(cur, drop, i, pWaClear, pLoad, pTransform, pLabel);
                if (lu == null) {
                    // Rule E: register↔register round-trip.
                    //   ld R2, R1   (pure reg-to-reg move)
                    //   ld R1, R2   (immediate inverse move)
                    // The second move reads back a value R1 already holds, so it is
                    // dead. This appears when an SSA value is promoted into an index
                    // register (ld xix, xwa) and then reloaded on the very next
                    // instruction (ld xwa, xix). Because the reload is the *next*
                    // significant line, nothing runs between the two, R1 is unchanged,
                    // and dropping the reload is always safe. A label at j is a barrier
                    // (the reload may be reached from elsewhere with a different state).
                    Matcher mRR = pRegMove.matcher(line);
                    if (mRR.matches()) {
                        String dst = mRR.group(1), src = mRR.group(2);
                        int j = nextSig(cur, drop, i + 1);
                        if (j >= 0 && !pLabel.matcher(cur.get(j)).matches()) {
                            Matcher mRR2 = pRegMove.matcher(cur.get(j));
                            if (mRR2.matches()
                                    && mRR2.group(1).equals(src)   // ld R1, ...
                                    && mRR2.group(2).equals(dst)) { // ..., R2
                                drop[j] = true; changed = true;
                            }
                        }

                        continue;
                    }

                    // Rule A: ld (xsp+N), R  /  ld R, (xsp+N)  → drop the reload
                    Matcher mSt = pStore.matcher(line);
                    if (mSt.matches()) {
                        String off = mSt.group(1), reg = mSt.group(2);
                        int j = nextSig(cur, drop, i + 1);
                        if (j >= 0 && !pLabel.matcher(cur.get(j)).matches()) {
                            Matcher mLd = pLoad.matcher(cur.get(j));
                            if (mLd.matches() && mLd.group(1).equals(reg) && mLd.group(2).equals(off)) {
                                drop[j] = true; changed = true;
                            }
                        }

                        // Rule C: cross-register forwarding.
                        // ld (xsp+N), R1  /  [up to 3 instructions that don't clobber R1]
                        // /  ld R2, (xsp+N)  →  drop the store+reload, emit ld R2, R1
                        // (in-place: replace the reload with ld R2, R1, drop the store).
                        // Safe only when the slot has exactly one write (this store) and
                        // one read (the load), confirmed by counting occurrences.
                        if (j >= 0 && !pLabel.matcher(cur.get(j)).matches()) {
                            // Count total reads and writes of this slot in the whole list.
                            int reads = 0, writes = 0;
                            for (String s : cur) {
                                Matcher r = pLoad.matcher(s);
                                if (r.matches() && r.group(2).equals(off)) reads++;
                                Matcher w = pStore.matcher(s);
                                if (w.matches() && w.group(1).equals(off)) writes++;
                            }

                            if (reads == 1 && writes == 1) {
                                // Scan forward up to 3 significant instructions for the reload.
                                int window = 0;
                                int k = i;
                                boolean r1Safe = true;
                                String xferInstr = null;
                                int reloadIdx = -1;
                                while (window <= 3) {
                                    k = nextSig(cur, drop, k + 1);
                                    if (k < 0 || pLabel.matcher(cur.get(k)).matches()) break;
                                    Matcher mLd2 = pLoad.matcher(cur.get(k));
                                    if (mLd2.matches() && mLd2.group(2).equals(off)) {
                                        // Found the reload into R2.
                                        String r2 = mLd2.group(1);
                                        if (!r2.equals(reg) && xferInstr == null) {
                                            xferInstr = regToRegMove(reg, r2);
                                            if (xferInstr != null) reloadIdx = k;
                                        }

                                        break;
                                    }

                                    if (clobbersReg(cur.get(k), reg)) { r1Safe = false; break; }
                                    window++;
                                }

                                if (r1Safe && reloadIdx >= 0) {
                                    // Replace reload with reg-to-reg move, drop the store.
                                    cur.set(reloadIdx, "\t" + xferInstr);
                                    drop[i] = true; changed = true;
                                }
                            }
                        }
                    }

                    continue;
                }

                // Rule B: load-unit(→R from N) / ld (xsp+M), R / load-unit(→R from M)
                //         → drop store + second load-unit
                int j = nextSig(cur, drop, lu.last() + 1);
                if (j < 0 || pLabel.matcher(cur.get(j)).matches()) continue;
                Matcher mSt = pStore.matcher(cur.get(j));
                if (!mSt.matches() || !mSt.group(2).equals(lu.reg())) continue;
                String off2 = mSt.group(1);
                int k = nextSig(cur, drop, j + 1);
                if (k < 0 || pLabel.matcher(cur.get(k)).matches()) continue;
                LoadUnit lu2 = parseLoadUnit(cur, drop, k, pWaClear, pLoad, pTransform, pLabel);
                if (lu2 != null && lu2.reg().equals(lu.reg()) && lu2.off().equals(off2)) {
                    drop[j] = true;
                    for (int x = k; x <= lu2.last(); x++) drop[x] = true;
                    changed = true;
                }
            }

            // Rule H: dead accumulator load.
            //   ld ACC, <src1>          (ACC in {a, wa, xwa})
            //   ld ACC, <src2>          (next significant line fully overwrites ACC,
            //                            and does not read ACC in its source)
            // The first load's value is never observed â€” drop it. This cleans up the
            // orphaned load left behind when Rule F/D forward a stack-slot copy and
            // remove the intervening store (e.g. `ld wa,(xsp+24)` feeding a now-dead
            // `ld (xsp+0),wa`). A label at j is a barrier.
            for (int i = 0; i < cur.size(); i++) {
                if (drop[i]) continue;
                Matcher mLd1 = pLoad.matcher(cur.get(i));
                boolean isAccLoad = false;
                String acc = null;
                if (mLd1.matches()) {
                    acc = mLd1.group(1);
                    isAccLoad = acc.equals("a") || acc.equals("wa") || acc.equals("xwa");
                }

                // Also handle immediate/global/address loads into the accumulator.
                if (!isAccLoad) {
                    Matcher mImm = Pattern.compile(
                        "^\\s+ld[a]?\\s+(a|wa|xwa),\\s*(?!\\(xsp\\+)([^,]+?)\\s*(?:;.*)?$")
                        .matcher(cur.get(i));
                    if (mImm.matches()) { isAccLoad = true; acc = mImm.group(1); }
                }

                if (!isAccLoad) continue;
                int j = nextSig(cur, drop, i + 1);
                if (j < 0 || pLabel.matcher(cur.get(j)).matches()) continue;
                String next = cur.get(j);
                // The next instruction must write the SAME accumulator width-family as
                // its destination, and must not read the accumulator in its source.
                Matcher mLd2 = pLoad.matcher(next);
                String dst2 = null, src2 = null;
                if (mLd2.matches()) { dst2 = mLd2.group(1); src2 = "(xsp+" + mLd2.group(2) + ")"; }
                else {
                    Matcher mAny = Pattern.compile(
                        "^\\s+ld[a]?\\s+(a|wa|xwa),\\s*(.+?)\\s*(?:;.*)?$").matcher(next);
                    if (mAny.matches()) { dst2 = mAny.group(1); src2 = mAny.group(2); }
                }

                if (dst2 == null) continue;
                // Both destinations must be the exact same accumulator name so that the
                // second load fully overwrites the first (a→a, wa→wa, xwa→xwa; also a
                // wider write like xwa overwrites a/wa, and wa overwrites a).
                boolean fullyOverwrites =
                    dst2.equals(acc)
                    || (dst2.equals("xwa") && (acc.equals("wa") || acc.equals("a")))
                    || (dst2.equals("wa")  && acc.equals("a"));
                if (!fullyOverwrites) continue;
                // The overwriting load must not read the accumulator as a source.
                if (registersOverlap2(src2, acc)) continue;
                drop[i] = true; changed = true;
            }

            if (changed) {
                List<String> next = new ArrayList<>(cur.size());
                for (int i = 0; i < cur.size(); i++) if (!drop[i]) next.add(cur.get(i));
                cur = next;
            }
        }

        return cur;
    }

    /** Conservative barrier for stack-slot forward propagation. */
    static boolean isSlotForwardBarrier(String line, Pattern pLabel) {
        String t = line.trim();
        if (t.isEmpty() || t.startsWith(";")) return false;
        if (pLabel.matcher(t).matches()) return true;
        if (t.startsWith("jr ") || t.startsWith("jrl ") || t.startsWith("jp ")) return true;
        if (t.startsWith("ret") || t.startsWith("reti")) return true;
        if (t.startsWith("calr ") || t.startsWith("call ")) return true;
        if (t.startsWith("push ") || t.startsWith("pop ")) return true;
        if (t.startsWith("add xsp,")) return true;
        // Any non-stack memory store could alias through pointer-based stack access.
        if (t.startsWith("ld (") && !t.startsWith("ld (xsp+")) return true;
        return false;
    }

    /** Return a reg-to-reg move instruction for R1→R2, or null if not directly expressible. */

    private static String regToRegMove(String r1, String r2) {
        // Cross-family moves supported by TLCS-900/H:
        // xwa family ↔ xhl family
        if (r1.equals("xwa") && r2.equals("xhl")) return "ld xhl, xwa";
        if (r1.equals("wa")  && r2.equals("hl"))  return "ld hl, wa";
        if (r1.equals("xhl") && r2.equals("xwa")) return "ld xwa, xhl";
        if (r1.equals("hl")  && r2.equals("wa"))  return "ld wa, hl";
        // xwa family ↔ xbc/bc/b
        if (r1.equals("xwa") && r2.equals("xbc")) return "ld xbc, xwa";
        if (r1.equals("wa")  && r2.equals("bc"))  return "ld bc, wa";
        if (r1.equals("a")   && r2.equals("b"))   return "ld b, a";
        if (r1.equals("xbc") && r2.equals("xwa")) return "ld xwa, xbc";
        if (r1.equals("bc")  && r2.equals("wa"))  return "ld wa, bc";
        if (r1.equals("b")   && r2.equals("a"))   return "ld a, b";
        // xwa family ↔ xde/de/e
        if (r1.equals("xwa") && r2.equals("xde")) return "ld xde, xwa";
        if (r1.equals("wa")  && r2.equals("de"))  return "ld de, wa";
        if (r1.equals("a")   && r2.equals("e"))   return "ld e, a";
        if (r1.equals("xde") && r2.equals("xwa")) return "ld xwa, xde";
        if (r1.equals("de")  && r2.equals("wa"))  return "ld wa, de";
        if (r1.equals("e")   && r2.equals("a"))   return "ld a, e";
        // xwa family ↔ xix / xiy / xiz  (only full 32-bit moves exist)
        if (r1.equals("xwa") && r2.equals("xix")) return "ld xix, xwa";
        if (r1.equals("xwa") && r2.equals("xiy")) return "ld xiy, xwa";
        if (r1.equals("xwa") && r2.equals("xiz")) return "ld xiz, xwa";
        if (r1.equals("xix") && r2.equals("xwa")) return "ld xwa, xix";
        if (r1.equals("xiy") && r2.equals("xwa")) return "ld xwa, xiy";
        if (r1.equals("xiz") && r2.equals("xwa")) return "ld xwa, xiz";
        // xwa family ↔ bank-1/bank-2 registers (R register-file-direct).
        // Only WA is a legal partner: a bank reg can be LD src/dst against WA and can
        // be the 2nd operand of an arith whose dst is WA, but never an arith dst itself.
        // 32-bit dword forms use the X mnemonic; 16-bit the R (rwa1…) mnemonic;
        // 8-bit the low-byte mnemonic (ra1…) paired with A.
        for (int i = 0; i < FuncTranslator.BANK_REG_KEYS.length; i++) {
            String x = FuncTranslator.BANK_REG_X[i];
            String r = FuncTranslator.BANK_REG_R[i];
            String b = FuncTranslator.BANK_REG_B[i];
            if (r1.equals("xwa") && r2.equals(x)) return "ld " + x + ", xwa";
            if (r1.equals(x) && r2.equals("xwa")) return "ld xwa, " + x;
            if (r1.equals("wa")  && r2.equals(r)) return "ld " + r + ", wa";
            if (r1.equals(r) && r2.equals("wa"))  return "ld wa, " + r;
            if (r1.equals("a")   && r2.equals(b)) return "ld " + b + ", a";
            if (r1.equals(b) && r2.equals("a"))   return "ld a, " + b;
        }
        return null;
    }

    /** Return true if the assembly instruction line clobbers (writes) register R. */

    private static boolean clobbersReg(String line, String reg) {
        String t = line.trim();
        // Labels and comments never clobber.
        if (t.isEmpty() || t.startsWith(";") || t.endsWith(":")) return false;
        // Calls clobber caller-saved registers (xwa, xhl) but NOT callee-saved (xbc/xde/xix/xiy).
        if (t.startsWith("calr ") || t.startsWith("call ")) {
            return reg.equals("xwa") || reg.equals("wa") || reg.equals("a")
                || reg.equals("xhl") || reg.equals("hl") || reg.equals("l");
        }

        // push/pop: pop writes a register.
        if (t.startsWith("pop ")) {
            String popped = t.substring(4).trim();
            return registersOverlap(popped, reg);
        }

        // ld dest, src â€” dest is the first operand (before the comma, after "ld ").
        if (t.startsWith("ld ")) {
            String rest = t.substring(3).trim();
            int comma = rest.indexOf(',');
            if (comma > 0) {
                String dest = rest.substring(0, comma).trim();
                // Memory destination ld (xsp+N), R does NOT clobber reg-file registers.
                if (dest.startsWith("(")) return false;
                return registersOverlap(dest, reg);
            }
        }

        // Arithmetic that writes its first operand in place.
        for (String pfx : new String[]{"add ","sub ","and ","or ","xor ","sla ","sra ","srl ",
                                        "inc ","dec ","neg ","extz ","exts ","mul ","div "}) {
            if (t.startsWith(pfx)) {
                // For shift/add/etc: "op count, reg" or "op reg" â€” reg is last token before comma or end.
                String[] parts = t.split("[,\\s]+");
                String dest = parts[parts.length - 1];
                return registersOverlap(dest, reg);
            }
        }

        // scc writes a (byte).
        if (t.startsWith("scc ")) return reg.equals("a");
        return false;
    }

    /** True if an instruction source-operand string references any register that
     *  overlaps the accumulator family of `acc` (a/wa/xwa/w). Used to check whether
     *  an overwriting load actually reads the accumulator it overwrites (e.g.
     *  `ld xwa, (xwa+4)` or `ld wa, bc` â€” the latter does NOT overlap wa). Memory
     *  offsets and immediates never overlap; only bare register tokens do. */

    private static boolean registersOverlap2(String src, String acc) {
        if (src == null) return false;
        // Extract candidate register tokens (letter-led identifiers).
        Matcher m = Pattern.compile("[a-z][a-z0-9]*").matcher(src.toLowerCase());
        while (m.find()) {
            String tok = m.group();
            if (registersOverlap(tok, acc)) return true;
        }

        return false;
    }

    /** True if two register names refer to overlapping hardware registers. */

    private static boolean registersOverlap(String a, String b) {
        a = a.trim(); b = b.trim();
        if (a.equals(b)) return true;
        Set<String> xwaFamily = Set.of("xwa", "wa", "a", "w");
        Set<String> xhlFamily = Set.of("xhl", "hl", "l", "h");
        Set<String> xbcFamily = Set.of("xbc", "bc", "b", "c");
        Set<String> xdeFamily = Set.of("xde", "de", "e", "d");
        Set<String> xixFamily = Set.of("xix", "ix");
        Set<String> xiyFamily = Set.of("xiy", "iy");
        Set<String> xizFamily = Set.of("xiz", "iz");
        // Bank-1 / bank-2 register families (R register-file-direct operands).
        // Each pair aliases its 32/16/hi-byte/lo-byte views onto one physical register.
        Set<String> wa1Family = Set.of("xwa1", "rwa1", "rw1", "ra1");
        Set<String> bc1Family = Set.of("xbc1", "rbc1", "rb1", "rc1");
        Set<String> de1Family = Set.of("xde1", "rde1", "rd1", "re1");
        Set<String> hl1Family = Set.of("xhl1", "rhl1", "rh1", "rl1");
        Set<String> wa2Family = Set.of("xwa2", "rwa2", "rw2", "ra2");
        Set<String> bc2Family = Set.of("xbc2", "rbc2", "rb2", "rc2");
        Set<String> de2Family = Set.of("xde2", "rde2", "rd2", "re2");
        Set<String> hl2Family = Set.of("xhl2", "rhl2", "rh2", "rl2");
        return (xwaFamily.contains(a) && xwaFamily.contains(b))
            || (xhlFamily.contains(a) && xhlFamily.contains(b))
            || (xbcFamily.contains(a) && xbcFamily.contains(b))
            || (xdeFamily.contains(a) && xdeFamily.contains(b))
            || (xixFamily.contains(a) && xixFamily.contains(b))
            || (xiyFamily.contains(a) && xiyFamily.contains(b))
            || (xizFamily.contains(a) && xizFamily.contains(b))
            || (wa1Family.contains(a) && wa1Family.contains(b))
            || (bc1Family.contains(a) && bc1Family.contains(b))
            || (de1Family.contains(a) && de1Family.contains(b))
            || (hl1Family.contains(a) && hl1Family.contains(b))
            || (wa2Family.contains(a) && wa2Family.contains(b))
            || (bc2Family.contains(a) && bc2Family.contains(b))
            || (de2Family.contains(a) && de2Family.contains(b))
            || (hl2Family.contains(a) && hl2Family.contains(b));
    }

    /**
     * Try to parse a "load unit" starting at index i.
     * A load unit is:
     *   [ld wa, 0]  ld R, (xsp+N)  [extz/exts ...]
     * The effective register after all transforms is returned in LoadUnit.reg().
     * LoadUnit.off() is the stack offset of the actual load instruction.
     * Returns null if line i does not start a load unit.
     */

    private static LoadUnit parseLoadUnit(List<String> cur, boolean[] drop, int i,
            Pattern pWaClear, Pattern pLoad, Pattern pTransform, Pattern pLabel) {
        int first = i;
        String reg, off;
        // Optional "ld wa, 0" prefix for byte loads
        boolean hadWaClear = false;
        if (pWaClear.matcher(cur.get(i)).matches()) {
            hadWaClear = true;
            i = nextSig(cur, drop, i + 1);
            if (i < 0 || pLabel.matcher(cur.get(i)).matches()) return null;
        }

        Matcher mLd = pLoad.matcher(cur.get(i));
        if (!mLd.matches()) return null;
        if (hadWaClear && !mLd.group(1).equals("a")) return null; // wa,0 must pair with a load
        reg = mLd.group(1);
        off = mLd.group(2);
        int last = i;
        // Absorb trailing single-register transforms (extz xwa, exts a, etc.)
        // Only absorb if they operate on a register in the same family as reg.
        while (true) {
            int j = nextSig(cur, drop, last + 1);
            if (j < 0 || pLabel.matcher(cur.get(j)).matches()) break;
            Matcher mTr = pTransform.matcher(cur.get(j));
            if (!mTr.matches()) break;
            String trReg = mTr.group(2);
            // Accept transforms that widen the current register:
            // a → xwa (extz/exts a then exts/extz xwa), or xwa directly
            if (!trReg.equals("xwa") && !trReg.equals("a") && !trReg.equals("xhl")) break;
            reg = trReg;  // effective register after transform
            last = j;
        }

        return new LoadUnit(first, last, reg, off);
    }

    //   1. Collect every label that is still referenced by a branch other than
    //      the fall-through jump we intend to delete.
    //   2. Walk the list; when we see the pattern, remove the jrl and, if the
    //      target label is no longer referenced by anyone, remove it too.

    // =========================================================================

    // =========================================================================
    // Fold a constant pointer adjust into the following (XHL+0) access.
    //
    //   add xhl, N            (N a nonzero constant in signed-16 range)
    //   ... (lines that do NOT touch XHL) ...
    //   ld REG, (xhl+0)   ->  ld REG, (xhl+N)     (and drop the `add`)
    //   ld (xhl+0), REG   ->  ld (xhl+N), REG
    //
    // This is exactly the shape emitGEP produces for a struct/array field at a
    // constant byte offset (`add xhl, fOff` then a plain `(xhl+0)` load/store).
    // TLCS-900/H has (XHL+d8)/(XHL+d16) displacement addressing, so the separate
    // `add` is redundant. Between the add and the consumer the backend may emit
    // the datum load for a store (loadToWA â€” touches WA, never XHL) and the
    // byte-load high-clear `ld wa,0`; those are safe to skip over.
    //
    // Conservative bail-outs (keep the `add`, fold nothing) if before the first
    // (xhl+0) access we hit: a label, any branch/call/ret, or ANY other use or
    // redefinition of XHL (push/pop xhl, ld xhl,.., lda xhl,.., add/sub xhl,..,
    // ld ..,xhl, an (xhl+<nonzero>) access, etc.). The GEP results this targets
    // are single-consumer (`xhlOnly`), so one fold per `add` is the common case.
    //
    // Architecture-generic; not tied to any project/function names. Verified
    // decodable by the bundled simulator (Cpu.ea cases (XHL+d8)/(XHL+d16)).

    // =========================================================================
    static List<String> foldXhlDisplacement(List<String> lines) {
        Pattern pAddXhl = Pattern.compile("^\\s+add\\s+xhl,\\s*(-?(?:\\d+|0x[0-9a-fA-F]+|[0-9a-fA-F]+h))\\s*(?:;.*)?$");
        Pattern pLabel  = Pattern.compile("^[.\\w].*:.*$");
        Pattern pXhl0   = Pattern.compile("^(\\s+ld\\s+)(.*)\\(xhl\\+0\\)(.*)$");
        // Any mention of xhl that is NOT the (xhl+0) we want to fold means the
        // base register is live for another purpose â€” do not fold.
        Pattern pTouchesXhl = Pattern.compile("(?<![\\w.])x?hl(?![\\w.])", Pattern.CASE_INSENSITIVE);
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            Matcher mAdd = pAddXhl.matcher(out.get(i));
            if (!mAdd.matches()) continue;
            long disp = parseSignedImm(mAdd.group(1));
            // Must be nonzero and encodable as a signed 8-bit displacement. asl emits
            // the compact (XHL+d8) form (prefix nibble B) only for -128..127; larger
            // offsets use a multi-byte index-prefix encoding the simulator does not
            // decode, so restrict the fold to the d8 range to stay verifiable.
            if (disp == 0 || disp < -128 || disp > 127) continue;
            int foldAt = -1;
            boolean bail = false;
            for (int j = i + 1; j < out.size(); j++) {
                String core = out.get(j).split(";", 2)[0];
                if (core.trim().isEmpty()) continue;
                if (pLabel.matcher(core.trim()).matches()) { bail = true; break; }
                String lc = core.trim().toLowerCase(Locale.ROOT);
                // Control flow ends the linear window.
                if (lc.startsWith("jr") || lc.startsWith("jp") || lc.startsWith("call")
                        || lc.startsWith("calr") || lc.startsWith("ret") || lc.startsWith("djnz")
                        || lc.startsWith("reti") || lc.startsWith("halt") || lc.startsWith("swi")) {
                    bail = true; break;
                }

                Matcher mUse = pXhl0.matcher(core);
                if (mUse.matches()) { foldAt = j; break; }
                // The line does not contain (xhl+0) but mentions XHL/HL in any
                // other form → the base is used/redefined; do not fold.
                if (pTouchesXhl.matcher(core).find()) { bail = true; break; }
            }

            if (bail || foldAt < 0) continue;
            // Rewrite the consumer's (xhl+0) → (xhl+N) and drop the add.
            // Format a negative displacement as (xhl-N), not (xhl+-N) â€” asl rejects
            // the "+-" form.
            String dispStr = disp < 0 ? ("-" + (-disp)) : ("+" + disp);
            String consumer = out.get(foldAt);
            String repl = consumer.replaceFirst(
                "\\(xhl\\+0\\)", Matcher.quoteReplacement("(xhl" + dispStr + ")"));
            out.set(foldAt, repl);
            out.set(i, null);   // mark the add for removal
        }

        out.removeIf(Objects::isNull);
        return out;
    }

    // Parse a signed immediate that may be decimal, 0x-hex, or trailing-h hex.
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

    /** Invert a TLCS-900 condition code used by scc/jrl. Returns null if unknown. */
    static String invertCond(String cc) {
        return switch (cc) {
            case "Z"   -> "NZ";
            case "NZ"  -> "Z";
            case "LT"  -> "GE";
            case "GE"  -> "LT";
            case "GT"  -> "LE";
            case "LE"  -> "GT";
            case "C"   -> "NC";
            case "NC"  -> "C";
            case "UGT" -> "ULE";
            case "ULE" -> "UGT";
            case "ULT" -> "UGE";
            case "UGE" -> "ULT";
            default      -> null;
        };
    }

    // =========================================================================
    // Generic zero-compare branch canonicalization
    //
    // cp REG, 0 ; jrl Z/NZ, L  ->  or REG, REG ; jrl Z/NZ, L
    //
    // Safe for Z/NZ branches because both forms test zero/non-zero, and branch
    // consumes only Z. This is generic and target-idiomatic for TLCS-900.

    // =========================================================================
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
            if (!(rhs.equals("0") || rhs.equals("0h") || rhs.equals("00h") || rhs.equals("0000h") || rhs.equals("00000000h"))) continue;
            int j = i + 1;
            while (j < out.size() && out.get(j).matches("^\\s*(;.*)?$")) j++;
            if (j >= out.size()) continue;
            if (pLabel.matcher(out.get(j).trim()).matches()) continue;
            Matcher mJ = pBr.matcher(out.get(j));
            if (!mJ.matches()) continue;
            out.set(i, "\tor " + lhs + ", " + lhs);
        }

        return out;
    }

    // =========================================================================
    // Generic bool/branch simplification
    //
    // 1) cp X,0 ; scc CC,a ; or a,a ; jrl Z/NZ,L  -> cp X,0 ; jrl CC/L inverse
    // 2) ld wa,0 ; ld a,(mem) ; cp wa,0 ; jrl Z/NZ,L -> ld a,(mem) ; or a,a ; jrl Z/NZ,L
    //
    // These patterns are architecture-generic for this backend and not tied to
    // any specific project/function names.

    // =========================================================================
    static List<String> simplifyBoolBranchPatterns(List<String> lines) {
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        Pattern pSccA  = Pattern.compile("^\\s+scc\\s+([A-Z]+),\\s*a\\s*(?:;.*)?$");
        Pattern pOrAA  = Pattern.compile("^\\s+or\\s+a,\\s*a\\s*(?:;.*)?$");
        Pattern pJrl   = Pattern.compile("^\\s+jrl\\s+(Z|NZ),\\s*(\\S+)\\s*(?:;.*)?$");
        Pattern pLdWa0 = Pattern.compile("^\\s+ld\\s+wa,\\s*0\\s*(?:;.*)?$");
        Pattern pLdASt = Pattern.compile("^\\s+ld\\s+a,\\s*\\([^)]*\\)\\s*(?:;.*)?$");
        Pattern pCpWa0 = Pattern.compile("^\\s+cp\\s+wa,\\s*(?:0|0+h)\\s*(?:;.*)?$");
        List<String> cur = new ArrayList<>(lines);
        boolean changed = true;
        while (changed) {
            changed = false;
            boolean[] drop = new boolean[cur.size()];
            for (int i = 0; i < cur.size(); i++) {
                if (drop[i]) continue;
                if (pLabel.matcher(cur.get(i).trim()).matches()) continue;
                // Rule 1: scc CC,a ; or a,a ; jrl Z/NZ,L  ->  jrl CC or inverse, L
                Matcher mS = pSccA.matcher(cur.get(i));
                if (mS.matches()) {
                    int i1 = nextSig(cur, drop, i + 1);
                    if (i1 >= 0 && !pLabel.matcher(cur.get(i1).trim()).matches()
                            && pOrAA.matcher(cur.get(i1)).matches()) {
                        int i2 = nextSig(cur, drop, i1 + 1);
                        if (i2 >= 0 && !pLabel.matcher(cur.get(i2).trim()).matches()) {
                            Matcher mJ = pJrl.matcher(cur.get(i2));
                            if (mJ.matches()) {
                                String sccCc = mS.group(1);
                                String brCc  = mJ.group(1);
                                String tgt   = mJ.group(2);
                                String outCc = brCc.equals("NZ") ? sccCc : invertCond(sccCc);
                                if (outCc != null) {
                                    cur.set(i2, "\tjrl " + outCc + ", " + tgt);
                                    drop[i] = true;
                                    drop[i1] = true;
                                    changed = true;
                                    continue;
                                }
                            }
                        }
                    }
                }

                // Rule 2: ld wa,0 ; ld a,(mem) ; cp wa,0 ; jrl Z/NZ -> ld a,(mem); or a,a ; jrl Z/NZ
                Matcher mLd0 = pLdWa0.matcher(cur.get(i));
                if (!mLd0.matches()) continue;
                int j1 = nextSig(cur, drop, i + 1);
                if (j1 < 0 || pLabel.matcher(cur.get(j1).trim()).matches()) continue;
                Matcher mLdA = pLdASt.matcher(cur.get(j1));
                if (!mLdA.matches()) continue;
                int j2 = nextSig(cur, drop, j1 + 1);
                if (j2 < 0 || pLabel.matcher(cur.get(j2).trim()).matches()) continue;
                Matcher mCpWa = pCpWa0.matcher(cur.get(j2));
                if (!mCpWa.matches()) continue;
                int j3 = nextSig(cur, drop, j2 + 1);
                if (j3 < 0 || pLabel.matcher(cur.get(j3).trim()).matches()) continue;
                Matcher mJ = pJrl.matcher(cur.get(j3));
                if (!mJ.matches()) continue;
                cur.set(i, cur.get(j1));
                cur.set(j1, "\tor a, a");
                drop[j2] = true;
                changed = true;
            }

            if (changed) {
                List<String> next = new ArrayList<>(cur.size());
                for (int i = 0; i < cur.size(); i++) if (!drop[i]) next.add(cur.get(i));
                cur = next;
            }
        }

        return cur;
    }

    // =========================================================================
    // Micro-peephole: drop redundant byte clear before low-byte mask
    //
    // Pattern:
    //   ld wa, 0    (or xor w, w / xor wa, wa)
    //   ld a, SRC
    //   and wa|xwa, IMM
    //
    // If IMM keeps only low 8 bits (IMM & ~0xFF == 0), the AND itself guarantees
    // upper bits are zero, so the initial clear is redundant and can be removed.
    // This is always flag-safe because ld does not affect flags and the retained
    // and remains the first flag-producing instruction in the sequence.

    // =========================================================================
    static List<String> removeRedundantByteClearBeforeMask(List<String> lines) {
        Pattern pClr = Pattern.compile(
            "^\\s+(?:ld\\s+wa,\\s*(?:0|0h|00h|0000h|00000000h)|xor\\s+w,\\s*w|xor\\s+wa,\\s*wa)\\s*(?:;.*)?$",
            Pattern.CASE_INSENSITIVE);
        Pattern pLdA   = Pattern.compile("^\\s+ld\\s+a,\\s*([^;]+?)\\s*(?:;.*)?$",
                                         Pattern.CASE_INSENSITIVE);
        Pattern pAnd   = Pattern.compile("^\\s+and\\s+(wa|xwa),\\s*([^\\s;]+)\\s*(?:;.*)?$",
                                         Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        List<String> out = new ArrayList<>(lines);
        boolean[] drop = new boolean[out.size()];
        for (int i = 0; i < out.size(); i++) {
            if (!pClr.matcher(out.get(i)).matches()) continue;
            int j = i + 1;
            while (j < out.size() && out.get(j).matches("^\\s*(;.*)?$")) j++;
            if (j >= out.size() || pLabel.matcher(out.get(j).trim()).matches()) continue;
            if (!pLdA.matcher(out.get(j)).matches()) continue;
            int k = j + 1;
            while (k < out.size() && out.get(k).matches("^\\s*(;.*)?$")) k++;
            if (k >= out.size() || pLabel.matcher(out.get(k).trim()).matches()) continue;
            Matcher mAnd = pAnd.matcher(out.get(k));
            if (!mAnd.matches()) continue;
            OptionalLong imm = parseAsmImmediate(mAnd.group(2));
            if (imm.isEmpty()) continue;
            long mask = imm.getAsLong() & 0xFFFFFFFFL;
            if ((mask & ~0xFFL) != 0) continue;
            drop[i] = true;
        }

        List<String> shrunk = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) if (!drop[i]) shrunk.add(out.get(i));
        return shrunk;
    }

    // =========================================================================
    // Micro-peephole: fold stack load + move into direct destination load
    //
    // Patterns:
    //   ld wa,  (xsp+N) ; ld hl|bc|de, wa   -> ld hl|bc|de,   (xsp+N)
    //   ld xwa, (xsp+N) ; ld xhl|xbc|xde,xwa -> ld xhl|xbc|xde,(xsp+N)
    //
    // This removes a temporary register hop and keeps semantics identical.
    // Intentionally excludes XI* destinations due prior runtime regressions.

    // =========================================================================
    static List<String> foldStackLoadMovePairs(List<String> lines) {
        Pattern pLdWaStack = Pattern.compile("^\\s+ld\\s+wa,\\s*(\\(xsp\\+[^)]+\\))\\s*(?:;.*)?$",
                                             Pattern.CASE_INSENSITIVE);
        Pattern pLdXwaStack = Pattern.compile("^\\s+ld\\s+xwa,\\s*(\\(xsp\\+[^)]+\\))\\s*(?:;.*)?$",
                                              Pattern.CASE_INSENSITIVE);
        Pattern pLdFromWa = Pattern.compile("^\\s+ld\\s+(hl|bc|de),\\s*wa\\s*(?:;.*)?$",
                                            Pattern.CASE_INSENSITIVE);
        Pattern pLdFromXwa = Pattern.compile("^\\s+ld\\s+(xhl|xbc|xde),\\s*xwa\\s*(?:;.*)?$",
                                             Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        List<String> out = new ArrayList<>(lines);
        boolean[] drop = new boolean[out.size()];
        for (int i = 0; i < out.size(); i++) {
            if (drop[i]) continue;
            Matcher mWa = pLdWaStack.matcher(out.get(i));
            Matcher mXwa = pLdXwaStack.matcher(out.get(i));
            if (!mWa.matches() && !mXwa.matches()) continue;
            int j = i + 1;
            while (j < out.size() && out.get(j).matches("^\\s*(;.*)?$")) j++;
            if (j >= out.size() || pLabel.matcher(out.get(j).trim()).matches()) continue;
            if (mWa.matches()) {
                Matcher mDst = pLdFromWa.matcher(out.get(j));
                if (mDst.matches()) {
                    out.set(i, "\tld " + mDst.group(1).toLowerCase(Locale.ROOT) + ", " + mWa.group(1));
                    drop[j] = true;
                    continue;
                }
            }

            if (mXwa.matches()) {
                Matcher mDst = pLdFromXwa.matcher(out.get(j));
                if (mDst.matches()) {
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
    // Micro-peephole: drop redundant low-byte mask before stricter low-byte mask
    //
    // Pattern:
    //   and wa, 0ffh
    //   ... (flag-neutral only)
    //   and wa|xwa, IMM(low8)
    //
    // The first mask is redundant because the second mask already confines the
    // value to low 8 bits. This also preserves flag behavior at the consumer
    // branch because the second AND remains the nearest flag producer.

    // =========================================================================
    static List<String> removeRedundantMaskBeforeMask(List<String> lines) {
        Pattern pAndFF = Pattern.compile("^\\s+and\\s+wa,\\s*(?:0ffh|255|0x0*ff)\\s*(?:;.*)?$",
                                         Pattern.CASE_INSENSITIVE);
        Pattern pAnd   = Pattern.compile("^\\s+and\\s+(wa|xwa),\\s*([^\\s;]+)\\s*(?:;.*)?$",
                                         Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        List<String> out = new ArrayList<>(lines);
        boolean[] drop = new boolean[out.size()];
        for (int i = 0; i < out.size(); i++) {
            if (!pAndFF.matcher(out.get(i)).matches()) continue;
            int j = i + 1;
            while (j < out.size() && out.get(j).matches("^\\s*(;.*)?$")) j++;
            if (j >= out.size()) continue;
            boolean blocked = false;
            while (j < out.size()) {
                String raw = out.get(j);
                if (raw.matches("^\\s*(;.*)?$")) { j++; continue; }
                String trimmed = raw.trim();
                if (pLabel.matcher(trimmed).matches()) { blocked = true; break; }
                Matcher mAnd = pAnd.matcher(raw);
                if (mAnd.matches()) {
                    OptionalLong imm = parseAsmImmediate(mAnd.group(2));
                    if (imm.isPresent()) {
                        long mask = imm.getAsLong() & 0xFFFFFFFFL;
                        if ((mask & ~0xFFL) == 0) {
                            drop[i] = true;
                        }
                    }

                    break;
                }

                String t = trimmed.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (asmConsumesFlags(t) || asmClobbersFlags(t) || !asmNeutralForFlags(t)) {
                    blocked = true;
                    break;
                }

                j++;
            }

            if (blocked) continue;
        }

        List<String> shrunk = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) if (!drop[i]) shrunk.add(out.get(i));
        return shrunk;
    }

    // =========================================================================
    // Micro-peephole: drop redundant OR self-test after AND before Z/NZ branch
    //
    // Pattern:
    //   and REG, IMM
    //   or  REG, REG
    //   jrl Z|NZ|EQ|NE, label
    //
    // `and` already sets Z/NZ from REG, so the OR self-test is redundant.
    // Restricting to zero/not-zero conditions keeps this conservative.

    // =========================================================================
    static List<String> removeRedundantOrAfterAndForZeroBranch(List<String> lines) {
        Pattern pAnd = Pattern.compile("^\\s+and\\s+(a|wa|xwa),\\s*([^\\s;]+)\\s*(?:;.*)?$",
                                       Pattern.CASE_INSENSITIVE);
        Pattern pOrSelf = Pattern.compile("^\\s+or\\s+(a|wa|xwa),\\s*(a|wa|xwa)\\s*(?:;.*)?$",
                                          Pattern.CASE_INSENSITIVE);
        Pattern pJ = Pattern.compile("^\\s+(?:jr|jrl|jp)\\s+([a-z]+)\\s*,\\s*([^;]+?)\\s*(?:;.*)?$",
                                     Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        List<String> out = new ArrayList<>(lines);
        boolean[] xwaUpper16ZeroBefore = computeXwaUpper16ZeroBefore(out);
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
            // Also allow: and wa, low8 ; or xwa, xwa ; j{z|nz}
            // only when xwa[31:16] is definitely zero at the AND site.
            boolean sameRegCase = lhs.equals(andReg);
            boolean waToXwaCase = andReg.equals("wa") && lhs.equals("xwa") && xwaUpper16ZeroBefore[i];
            if (!(sameRegCase || waToXwaCase)) continue;
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

    /**
     * For each line index i, returns whether xwa[31:16] is definitely zero
     * just before executing line i, using conservative linear-block tracking.
     * Labels reset knowledge.
     */
    static boolean[] computeXwaUpper16ZeroBefore(List<String> lines) {
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        Pattern pLdXwaImm = Pattern.compile("^ld\\s+xwa,\\s*([^\\s;]+)\\s*$",
                                            Pattern.CASE_INSENSITIVE);
        Pattern pAndXwaImm = Pattern.compile("^and\\s+xwa,\\s*([^\\s;]+)\\s*$",
                                             Pattern.CASE_INSENSITIVE);
        Pattern pWriteXwaDirect = Pattern.compile(
            "^(?:ld|add|sub|and|or|xor|adc|sbc|exts|mul|div|inc|dec|neg)\\s+xwa(?:\\s|,).*$",
            Pattern.CASE_INSENSITIVE);
        Pattern pShiftWriteXwa = Pattern.compile("^(?:sla|sra|srl)\\s+[^,]+,\\s*xwa\\s*$",
                                                 Pattern.CASE_INSENSITIVE);
        boolean[] before = new boolean[lines.size()];
        boolean upperZero = false;
        for (int i = 0; i < lines.size(); i++) {
            before[i] = upperZero;
            String raw = lines.get(i);
            String t = raw.split(";", 2)[0].trim();
            if (t.isEmpty()) continue;
            if (pLabel.matcher(t).matches()) { upperZero = false; continue; }
            String lc = t.toLowerCase(Locale.ROOT);
            if (lc.equals("extz xwa") || lc.equals("xor xwa, xwa")) {
                upperZero = true;
                continue;
            }

            Matcher mLd = pLdXwaImm.matcher(t);
            if (mLd.matches()) {
                OptionalLong imm = parseAsmImmediate(mLd.group(1));
                upperZero = imm.isPresent() && ((imm.getAsLong() & 0xFFFFFFFFL) <= 0xFFFFL);
                continue;
            }

            Matcher mAnd = pAndXwaImm.matcher(t);
            if (mAnd.matches()) {
                OptionalLong imm = parseAsmImmediate(mAnd.group(1));
                if (!imm.isPresent()) {
                    upperZero = false;
                } else {
                    long mask = imm.getAsLong() & 0xFFFFFFFFL;
                    upperZero = (mask & ~0xFFFFL) == 0;
                }

                continue;
            }

            if (pWriteXwaDirect.matcher(t).matches() || pShiftWriteXwa.matcher(t).matches()) {
                upperZero = false;
            }
        }

        return before;
    }

    /** Parse asm immediates like 7, 0x7, 07h, 0ffh, 0FFFFh. */
    static OptionalLong parseAsmImmediate(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.endsWith("h")) {
            String hex = t.substring(0, t.length() - 1);
            if (hex.isEmpty()) return OptionalLong.empty();
            try { return OptionalLong.of(Long.parseUnsignedLong(hex, 16)); }
            catch (NumberFormatException e) { return OptionalLong.empty(); }
        }

        if (t.startsWith("0x")) {
            try { return OptionalLong.of(Long.parseUnsignedLong(t.substring(2), 16)); }
            catch (NumberFormatException e) { return OptionalLong.empty(); }
        }

        try { return OptionalLong.of(Long.parseLong(t)); }
        catch (NumberFormatException e) { return OptionalLong.empty(); }
    }

    // =========================================================================
    // Micro-peephole: zero-extend byte load with flag-safety
    //
    // Pattern:
    //   ld wa, 0
    //   ld a, SRC
    //
    // Rewrites to:
    //   xor w, w
    //   ld a, SRC
    //
    // but only when flags from the replacement cannot be observed: the next
    // flag-relevant instruction in the same linear block must clobber flags
    // before any instruction consumes them. Labels are treated as barriers.

    // =========================================================================
    static List<String> optimizeByteZeroExtendPairs(List<String> lines) {
        Pattern pLdWa0 = Pattern.compile("^\\s+ld\\s+wa,\\s*(?:0|0h|00h|0000h|00000000h)\\s*(?:;.*)?$",
                                         Pattern.CASE_INSENSITIVE);
        Pattern pLdA   = Pattern.compile("^\\s+ld\\s+a,\\s*([^;]+?)\\s*(?:;.*)?$",
                                         Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            if (!pLdWa0.matcher(out.get(i)).matches()) continue;
            int j = i + 1;
            while (j < out.size() && out.get(j).matches("^\\s*(;.*)?$")) j++;
            if (j >= out.size()) continue;
            if (pLabel.matcher(out.get(j).trim()).matches()) continue;
            if (!pLdA.matcher(out.get(j)).matches()) continue;
            if (!flagsAreDeadAfter(out, j, pLabel)) continue;
            out.set(i, "\txor w, w");
        }

        return out;
    }

    /**
     * Returns true if the first flag-relevant instruction after startIdx in the
     * same linear block clobbers flags before any consumer appears.
     */
    static boolean flagsAreDeadAfter(List<String> lines, int startIdx, Pattern pLabel) {
        for (int k = startIdx + 1; k < lines.size(); k++) {
            String raw = lines.get(k);
            if (raw.matches("^\\s*(;.*)?$")) continue;
            String t = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            if (t.isEmpty()) continue;
            if (pLabel.matcher(t).matches()) return false;
            if (asmConsumesFlags(t)) return false;
            if (asmClobbersFlags(t)) return true;
            if (asmNeutralForFlags(t)) continue;
            // Unknown instruction kind: keep original conservative form.
            return false;
        }

        return false;
    }

    /** True if instruction reads condition flags. */
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
            Set<String> cond = Set.of("Z", "NZ", "LT", "GE", "GT", "LE", "C", "NC", "UGT", "ULE", "ULT", "UGE", "EQ", "NE");
            return cond.contains(cc);
        }

        return false;
    }

    /** True if instruction definitely overwrites flags. */
    static boolean asmClobbersFlags(String t) {
        return t.startsWith("cp ")
            || t.startsWith("add ") || t.startsWith("sub ")
            || t.startsWith("and ") || t.startsWith("or ") || t.startsWith("xor ")
            || t.startsWith("sla ") || t.startsWith("sra ") || t.startsWith("srl ")
            || t.startsWith("inc ") || t.startsWith("dec ") || t.startsWith("neg ")
            || t.startsWith("extz ") || t.startsWith("exts ")
            || t.startsWith("mul ") || t.startsWith("div ")
            || t.startsWith("bit ");
    }

    /** True if instruction is known to neither consume nor clobber flags. */
    static boolean asmNeutralForFlags(String t) {
        if (t.startsWith("ld ") || t.startsWith("lda ")) return true;
        if (t.startsWith("push ") || t.startsWith("pop ")) return true;
        if (t.startsWith("add xsp,")) return true;
        if (t.equals("di") || t.equals("ei")) return true;
        return false;
    }

    // =========================================================================
    // Fold add/sub of a small constant (1..8) into INC/DEC #n,r.
    //
    //   add {a|wa|xwa}, N   ->  inc N, {a|wa|xwa}     (N in 1..8)
    //   sub {a|wa|xwa}, N   ->  dec N, {a|wa|xwa}
    //
    // INC/DEC #3 is 1 byte shorter than ADD/SUB #imm and, per the TLCS-900 spec,
    // does NOT affect the carry flag (and changes NO flags at all for word/long
    // operands). Because the flag effect differs from ADD/SUB, this is only sound
    // when the flags produced by the original ADD/SUB are dead â€” i.e. the next
    // flag-touching instruction clobbers them before any consumer. We reuse the
    // same flag-liveness analysis that gates the ld-wa-0 -> xor-w-w rewrite.
    //
    // Architecture-generic; the bundled simulator decodes INC/DEC #3 (reg-op2
    // 0x60..0x6F) for a/wa/xwa. Verified via asl: `inc 1,a`=C9 61, `inc 1,wa`=D8 61.

    // =========================================================================
    static List<String> foldIncDec(List<String> lines) {
        Pattern pAddSub = Pattern.compile(
            "^\\s+(add|sub)\\s+(a|wa|xwa),\\s*([1-8])\\s*(?:;.*)?$", Pattern.CASE_INSENSITIVE);
        Pattern pLabel = Pattern.compile("^[.\\w].*:.*$");
        List<String> out = new ArrayList<>(lines);
        for (int i = 0; i < out.size(); i++) {
            Matcher m = pAddSub.matcher(out.get(i));
            if (!m.matches()) continue;
            if (!flagsAreDeadAfter(out, i, pLabel)) continue;
            String op  = m.group(1).toLowerCase(Locale.ROOT);
            String reg = m.group(2).toLowerCase(Locale.ROOT);
            String n   = m.group(3);
            out.set(i, "\t" + (op.equals("add") ? "inc" : "dec") + " " + n + ", " + reg);
        }

        return out;
    }

    // =========================================================================
    // Runtime helper DCE
    //
    // Keep only _C9H_* helpers that are reachable from non-helper code, plus
    // their transitive helper-to-helper dependencies.

    // =========================================================================
    static List<String> stripUnusedRuntimeHelpers(List<String> lines) {
        int hdr = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith("; ---- inline runtime helpers")) { hdr = i; break; }
        }

        if (hdr < 0) return lines;
        int end = -1;
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (lines.get(i).trim().equals("end")) { end = i; break; }
        }

        if (end < 0 || end <= hdr) return lines;
        Pattern pHelperLabel = Pattern.compile("^(_C9H_\\w+):\\s*$");
        Pattern pHelperRef   = Pattern.compile("(?<![\\w.])(_C9H_\\w+)");
        List<String> helperOrder = new ArrayList<>();
        Map<String, int[]> helperRange = new LinkedHashMap<>(); // name -> [start, endExclusive)
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (int i = hdr + 1; i < end; i++) {
            Matcher m = pHelperLabel.matcher(lines.get(i).trim());
            if (m.matches()) {
                starts.add(i);
                names.add(m.group(1));
            }
        }

        if (starts.isEmpty()) return lines;
        for (int i = 0; i < starts.size(); i++) {
            int s = starts.get(i);
            int e = (i + 1 < starts.size()) ? starts.get(i + 1) : end;
            String n = names.get(i);
            helperOrder.add(n);
            helperRange.put(n, new int[]{s, e});
        }

        // Build helper->helper dependency graph from helper bodies.
        Map<String, Set<String>> deps = new LinkedHashMap<>();
        for (String h : helperOrder) {
            int[] r = helperRange.get(h);
            Set<String> ds = new LinkedHashSet<>();
            for (int i = r[0]; i < r[1]; i++) {
                Matcher m = pHelperRef.matcher(lines.get(i));
                while (m.find()) {
                    String ref = m.group(1);
                    if (!ref.equals(h) && helperRange.containsKey(ref)) ds.add(ref);
                }
            }

            deps.put(h, ds);
        }

        // Roots: helper references that appear outside helper ranges.
        Set<String> roots = new LinkedHashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            if (i >= hdr && i < end) {
                boolean inHelperBody = false;
                for (int[] r : helperRange.values()) {
                    if (i >= r[0] && i < r[1]) { inHelperBody = true; break; }
                }

                if (inHelperBody) continue;
            }

            Matcher m = pHelperRef.matcher(lines.get(i));
            while (m.find()) {
                String ref = m.group(1);
                if (helperRange.containsKey(ref)) roots.add(ref);
            }
        }

        Set<String> keep = new LinkedHashSet<>();
        Deque<String> q = new ArrayDeque<>(roots);
        while (!q.isEmpty()) {
            String h = q.removeFirst();
            if (!keep.add(h)) continue;
            for (String d : deps.getOrDefault(h, Set.of())) {
                if (!keep.contains(d)) q.addLast(d);
            }
        }

        // If nothing is referenced, drop the whole runtime helper section.
        if (keep.isEmpty()) {
            List<String> out = new ArrayList<>(lines.size());
            for (int i = 0; i < lines.size(); i++) {
                if (i >= hdr && i < end) continue;
                out.add(lines.get(i));
            }

            return out;
        }

        boolean[] drop = new boolean[lines.size()];
        for (String h : helperOrder) {
            if (keep.contains(h)) continue;
            int[] r = helperRange.get(h);
            for (int i = r[0]; i < r[1]; i++) drop[i] = true;
        }

        List<String> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            if (drop[i]) continue;
            out.add(lines.get(i));
        }

        return out;
    }

    static List<String> peepholeEliminate(List<String> lines) {

        // --- Pass 1: mark which labels are referenced after we remove

        //     fall-through jumps -------------------------------------------
        // We need to know, for each candidate (jrl L / L:) pair, whether L is
        // referenced elsewhere.  Build a full reference count first.
        Map<String, Integer> refCount = new HashMap<>();
        Pattern pJrl   = Pattern.compile("^\\s+jrl\\s+(\\.Lbb_\\S+)\\s*$");
        Pattern pLabel = Pattern.compile("^(\\.Lbb_\\S+):.*$");
        // count every jrl / jr / jp reference  (no \b â€” dot is non-word, boundary never fires)
        Pattern pAnyRef = Pattern.compile("(?<![\\w.])(\\.Lbb_\\w+)");
        for (String line : lines) {
            Matcher m = pAnyRef.matcher(line);
            while (m.find()) {
                refCount.merge(m.group(1), 1, Integer::sum);
            }
        }

        // --- Pass 2: identify fall-through pairs and remove -----------------
        // Build a boolean mask: true = keep this line.
        int n = lines.size();
        boolean[] keep = new boolean[n];
        Arrays.fill(keep, true);
        for (int i = 0; i < n - 1; i++) {
            if (!keep[i]) continue;
            Matcher mJ = pJrl.matcher(lines.get(i));
            if (!mJ.matches()) continue;
            String target = mJ.group(1);
            // Find the next non-empty line
            int next = i + 1;
            while (next < n && lines.get(next).isBlank()) next++;
            if (next >= n) continue;
            Matcher mL = pLabel.matcher(lines.get(next));
            if (!mL.matches()) continue;
            if (!mL.group(1).equals(target)) continue;
            // It's a fall-through jump â€” remove it.
            keep[i] = false;
            refCount.merge(target, -1, Integer::sum);
            // If the label is now unreferenced, remove it too (cosmetic).
            if (refCount.getOrDefault(target, 0) <= 0) {
                keep[next] = false;
            }
        }

        List<String> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (keep[i]) result.add(lines.get(i));
        }

        return mergeConsecutiveLabels(result);
    }

    // Collapse runs of consecutive label lines into a single label.
    // All removed labels are rewritten to the survivor throughout the file.
    static List<String> mergeConsecutiveLabels(List<String> lines) {
        Pattern pLabel = Pattern.compile("^(\\.Lbb_\\w+):$");
        // Pass 1: build alias map â€” survivor is the last label in each consecutive run.
        Map<String, String> alias = new LinkedHashMap<>();
        int n = lines.size();
        for (int i = 0; i < n - 1; i++) {
            String cur = lines.get(i).trim();
            String nxt = lines.get(i + 1).trim();
            Matcher mC = pLabel.matcher(cur);
            Matcher mN = pLabel.matcher(nxt);
            if (mC.matches() && mN.matches()) {
                // cur is an alias for nxt (or nxt's eventual survivor)
                alias.put(mC.group(1), mN.group(1));
            }
        }

        if (alias.isEmpty()) return lines;
        // Resolve chains: A→B, B→C  =>  A→C, B→C
        for (String k : new ArrayList<>(alias.keySet())) {
            String v = alias.get(k);
            while (alias.containsKey(v)) v = alias.get(v);
            alias.put(k, v);
        }

        // Pass 2: drop aliased label lines; rewrite all references.
        Pattern pAnyRef = Pattern.compile("(\\.Lbb_\\w+)");
        List<String> out2 = new ArrayList<>(n);
        for (String line : lines) {
            String t = line.trim();
            Matcher mL = pLabel.matcher(t);
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
            out2.add(changed ? sb.toString() : line);
        }

        return out2;
    }
}

