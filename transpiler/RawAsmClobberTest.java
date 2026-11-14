import java.util.*;
import java.nio.file.*;
import sim.*;

/** Conservative raw-call summaries and ISR-level preservation. */
public final class RawAsmClobberTest {
    public static void main(String[] args) throws Exception {
        biosTrustChecks();
        var effects = Ir2Tlcs900.FuncTranslator.summarizeRawClobbers(List.of(
            "ld xhl, (xsp+4)", "ld xde, 04000h", "ld bc, 3", "ldirw (xde+),(xhl+)", "ret"));
        if (!effects.equals(Set.of("xhl", "xde", "xbc"))) throw new AssertionError("block copy effects " + effects);
        for (String barrier : List.of("call _opaque", "call xix", "jp xix", "jp _external", "ld xwa3, 1", "swi 1", "ldf 1", "exx", "unknown", "db 0"))
            if (Ir2Tlcs900.FuncTranslator.summarizeRawClobbers(List.of(barrier)) != null)
                throw new AssertionError("unsafe summary " + barrier);
        var bank = Ir2Tlcs900.FuncTranslator.summarizeRawClobbers(List.of("ld rwa2, 1", "ld ix, 2", "ret"));
        if (!bank.equals(Set.of("xwa2", "xix"))) throw new AssertionError("bank aliases " + bank);
        for (boolean interrupt : new boolean[]{false, true}) {
            var ft = new Ir2Tlcs900.FuncTranslator(new Ir2Tlcs900.IrFunction(
                interrupt ? "testInterrupt" : "test", "void", "", new ArrayList<>()), Set.of(), Map.of());
            ft.rawAsmCallees = Set.of("copy");
            ft.rawAsmClobbers = Map.of("copy", effects);
            ft.emitCall(null, "void", "copy", "i16 %p", Map.of("p", new int[]{4, 16}));
            if (interrupt) {
                if (ft.out.contains("\tpush xde") || !ft.out.contains("\tld wa, (xsp+4)"))
                    throw new AssertionError("ISR saves duplicated " + ft.out);
            } else if (!ft.out.containsAll(List.of("\tpush xbc", "\tpush xde", "\tpop xde", "\tpop xbc", "\tld wa, (xsp+12)"))
                    || ft.out.contains("\tpush xwa2")) throw new AssertionError("narrow saves/rebase " + ft.out);
        }
        String module = """
            define void @testInterrupt() {
              call void @raw()
              call void @raw()
              ret void
            }
            define void @raw() {
              call void asm sideeffect "ld xwa2, 1", ""()
              call void asm sideeffect "ld iz, 2", ""()
              ret void
            }
            """;
        String output = new Ir2Tlcs900.Translator(module, "raw_summary").translate();
        String handler = output.substring(output.indexOf("_testInterrupt:"), output.indexOf("_raw:"));
        if (handler.lines().filter(line -> line.trim().equals("push xwa2")).count() != 1
                || handler.lines().filter(line -> line.trim().equals("pop xwa2")).count() != 1
                || handler.lines().filter(line -> line.trim().equals("push xiz")).count() != 1
                || !handler.contains("reti")) throw new AssertionError("ISR union hoisting " + handler);
        if (args.length > 0 && args[0].equals("--assemble")) {
            Path dir = Files.createTempDirectory("raw-clobber-isr-");
            String assembly = output.replaceAll("(?m)^\\s*include\\s+.*$", "")
                .replace("; ---- code ----", "\torg 0200000h\n; ---- code ----");
            Files.writeString(dir.resolve("probe.asm"), assembly);
            for (String tool : List.of("asl.exe", "p2bin.exe")) {
                List<String> command = new ArrayList<>(List.of("wine", Path.of("../../as/bin", tool).toAbsolutePath().normalize().toString()));
                command.addAll(tool.equals("asl.exe") ? List.of("probe.asm") : List.of("-r", "$-$", "probe.p", "probe.bin"));
                Path log = dir.resolve(tool + ".log");
                Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
                if (process.waitFor() != 0) throw new AssertionError(Files.readString(log));
            }
            Memory memory = new Memory(Files.readAllBytes(dir.resolve("probe.bin")));
            Registers registers = new Registers();
            registers.setSR(0x0800);
            for (int bankIndex = 0; bankIndex < 4; bankIndex++) for (int pair = 0; pair < 4; pair++)
                registers.setBankGP32(bankIndex, pair, 0x11223344 + bankIndex * 16 + pair);
            registers.setXIZ(0xabcdef12);
            registers.setXSP(0x6b00);
            memory.writeDword(0x6b00, 0x20fff0);
            memory.writeWord(0x6b04, 0x0800);
            registers.setPC(0x200000);
            Cpu cpu = new Cpu(memory, registers);
            int steps = 0;
            while (registers.getPC() != 0x20fff0 && steps++ < 1000) cpu.step();
            if (registers.getPC() != 0x20fff0 || registers.getXSP() != 0x6b06 || registers.getXIZ() != 0xabcdef12 || registers.getSR() != 0x0800)
                throw new AssertionError("assembled ISR union preservation");
            for (int bankIndex = 0; bankIndex < 4; bankIndex++) for (int pair = 0; pair < 4; pair++)
                if (registers.getBankGP32(bankIndex, pair) != 0x11223344 + bankIndex * 16 + pair)
                    throw new AssertionError("assembled ISR bank preservation");
            System.out.println("Raw ISR union execution: PASS (artifacts " + dir + ")");
        }
        System.out.println("RawAsmClobberTest: PASS");
    }

    private static void biosTrustChecks() {
        String module = """
            define void @callerInterrupt() {
              call void @SysShutdown()
              ret void
            }
            define void @SysShutdown() {
              call void asm sideeffect "ld rw3,0", ""()
              call void asm sideeffect "ldf 3", ""()
              call void asm sideeffect "ld xix,0fffe00h", ""()
              call void asm sideeffect "ld xix,(xix+w)", ""()
              call void asm sideeffect "call xix", ""()
              ret void
            }
            """;
        String previous = System.getProperty("ngpc.trustBiosWrappers");
        try {
            for (boolean trust : new boolean[]{false, true}) {
                System.setProperty("ngpc.trustBiosWrappers", String.valueOf(trust));
                String output = new Ir2Tlcs900.Translator(module, "bios_contract").translate();
                String caller = output.substring(output.indexOf("_callerInterrupt:"), output.indexOf("_SysShutdown:"));
                if (caller.contains("\tpush xwa2") == trust || !caller.contains("\tcall _SysShutdown"))
                    throw new AssertionError("BIOS opt-in save contract " + trust + " " + caller);
                String unrelated = new Ir2Tlcs900.Translator(module.replace("SysShutdown", "unrelated"), "not_bios").translate();
                String otherCaller = unrelated.substring(unrelated.indexOf("_callerInterrupt:"), unrelated.indexOf("_unrelated:"));
                if (!otherCaller.contains("\tpush xwa2")) throw new AssertionError("trust leaked to unrelated raw call");
            }
            if (Ir2Tlcs900.FuncTranslator.trustedBiosWrapper("SysShutdown", List.of("call xix", "ret")))
                throw new AssertionError("name alone must not identify BIOS dispatch");
        } finally {
            if (previous == null) System.clearProperty("ngpc.trustBiosWrappers");
            else System.setProperty("ngpc.trustBiosWrappers", previous);
        }
    }
}