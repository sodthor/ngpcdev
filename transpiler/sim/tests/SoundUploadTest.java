package sim.tests;

import sim.NgpcTiming;

import sim.Cpu;
import sim.Memory;
import sim.NgpLoader;
import sim.Registers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SoundUploadTest {
    private final Memory memory;
    private final Registers registers = new Registers();
    private final Cpu cpu;
    private final Map<String, Integer> symbols = new HashMap<>();
    private final int[] ports = new int[256];
    private final List<String> commands = new ArrayList<>();
    private int pc;
    private int polls;
    private int heartbeat;
    private int soundWrites;
    private boolean allowSoundWrites;
    private boolean groupDriver;
    private Z80SoundDevice z80;
    private boolean inCall;
    private boolean running;
    private int frame;
    private int uploadSource;
    private int uploadDestination;
    private int uploadLength;
    private int uploads;
    private final int entry;

    private SoundUploadTest(String rom, String map) throws Exception {
        NgpLoader.NgpRom image = NgpLoader.load(rom);
        entry = image.entryPoint;
        memory = new Memory(image.data);
        cpu = new Cpu(memory, registers);
        cpu.setSwiHandler((number, regs, mem) -> true);
        for (String line : Files.readAllLines(Path.of(map))) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length >= 3 && fields[1].equals("Int")) {
                symbols.put(fields[0].toUpperCase(Locale.ROOT), Integer.parseInt(fields[2], 16));
            }
        }
        memory.setIoDevice(new Memory.IoDevice() {
            public int read(int address) {
                if (running) {
                    if (address == 0x6f82) return frame % 60 >= 30 && frame % 60 < 34 ? 0x10 : 0;
                    if (address == 0x6f85 || address == 0x6f91 || address == 0xb2) return 0;
                    if (address == 0x6f87) return 1;
                    if (address == 0x8009) return (int) ((cpu.getCycles() / 515) % 199);
                    if (address == 0x8008) return (cpu.getCycles() % 515) < 256 ? 0 : 0x80;
                }
                if (z80 != null) return z80.read(address);
                if (address == 0xbc) {
                    if (++polls >= 20) ports[address] = 0;
                    return ports[address];
                }
                if (address < 256) return ports[address];
                if (address == 0x70de && groupDriver && inCall) return (++heartbeat / 20) & 255;
                if (address == 0x6f87) return 1;
                return -1;
            }
            public void write(int address, int value) {
                if (z80 != null) z80.write(address, value);
                if (address < 256) {
                    ports[address] = value;
                    if (running && (address == 0xb8 || address == 0xb9)) {
                        System.out.printf("Sound control: %02X=%02X PC=%06X frame=%d%n", address, value, pc, frame);
                    }
                    if (address == 0xbc) {
                        polls = 0;
                        commands.add(String.format("COMM=%02X@%06X", value, pc));
                    }
                    if (address == 0xba) commands.add(String.format("NMI=%02X@%06X", value, pc));
                }
                if (address >= 0x7000 && address < 0x8000) {
                    if (!allowSoundWrites) throw new AssertionError(String.format(
                        "Unexpected sound write %06X=%02X at PC=%06X", address, value, pc));
                    if (running) {
                        require(soundRoutine(pc), String.format("Non-sound TLCS write at %06X to %04X", pc, address));
                        if (address >= uploadDestination && address < uploadDestination + uploadLength) {
                            require(value == memory.readByte(uploadSource + address - uploadDestination),
                                String.format("Bad upload byte at %04X from PC=%06X", address, pc));
                        }
                    }
                    soundWrites++;
                }
            }
        });
    }

    private int symbol(String name) {
        Integer address = symbols.get(name.toUpperCase(Locale.ROOT));
        if (address == null) throw new IllegalArgumentException("Missing symbol " + name);
        return address;
    }

    private void call(String name, int... arguments) {
        inCall = true;
        int stack = 0x6b00;
        memory.writeDword(stack, 0x123456);
        int offset = 4;
        for (int argument : arguments) {
            memory.writeWord(stack + offset, argument);
            offset += 2;
        }
        registers.setSR(0x0800);
        registers.setXSP(stack);
        registers.setPC(symbol(name));
        for (int steps = 0; steps < 2_000_000; steps++) {
            pc = registers.getPC();
            if (pc == 0x123456) {
                require(registers.getXSP() == stack + 4, "Unbalanced stack in " + name);
                inCall = false;
                return;
            }
            long before = cpu.getCycles();
            cpu.step();
            if (z80 != null) z80.tick((int) (cpu.getCycles() - before));
        }
        throw new AssertionError(String.format("%s timed out PC=%06X", name, registers.getPC()));
    }

    private void compare(int source, int destination, int length, String description) {
        for (int offset = 0; offset < length; offset++) {
            int actual = memory.readByte(destination + offset);
            int expected = memory.readByte(source + offset);
            if (actual != expected) throw new AssertionError(String.format(
                "%s offset=%04X expected=%02X actual=%02X", description, offset, expected, actual));
        }
    }

    private static byte[] headerBytes(Path path) throws Exception {
        Matcher matcher = Pattern.compile("0x([0-9a-fA-F]+)").matcher(Files.readString(path));
        List<Byte> bytes = new ArrayList<>();
        while (matcher.find()) bytes.add((byte) Integer.parseInt(matcher.group(1), 16));
        byte[] result = new byte[bytes.size()];
        for (int index = 0; index < result.length; index++) result[index] = bytes.get(index);
        return result;
    }

    private void compareHeader(int source, Path path) throws Exception {
        byte[] expected = headerBytes(path);
        for (int index = 0; index < expected.length; index++) {
            require(memory.readByte(source + index) == (expected[index] & 255), String.format(
                "%s ROM/header mismatch at %04X ROM=%02X header=%02X", path, index,
                memory.readByte(source + index), expected[index] & 255));
        }
    }

    private void group(Path directory, String driverName) throws Exception {
        groupDriver = true;
        int driver = symbol(driverName);
        byte[] expected = headerBytes(directory.resolve("driver.h"));
        allowSoundWrites = true;
        call("_SL_SoundInit");
        if (z80 == null) compare(driver, 0x7000, expected.length, "driver upload");
        for (int index = 0; index < expected.length; index++) {
            require(memory.readByte(driver + index) == (expected[index] & 255), String.format(
                "ROM driver differs from header at %04X ROM=%02X header=%02X", index,
                memory.readByte(driver + index), expected[index] & 255));
        }
        require(ports[0xb8] == 0x55 && ports[0xb9] == 0x55, "Z80 not enabled");
        require(ports[0x20] == 0x88 && ports[0x27] == 0x62, "Timer 3 not configured");
        System.out.printf("Driver PASS: %d bytes, timer TRUN=%02X TFFCR=%02X T23MOD=%02X TREG3=%02X%n",
            expected.length, ports[0x20], ports[0x25], ports[0x28], ports[0x27]);
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(item -> item.toString().endsWith(".mh")).sorted().toList()) {
                String file = path.getFileName().toString();
                String name = "_" + file.substring(0, file.length() - 3);
                if (!symbols.containsKey(name.toUpperCase(Locale.ROOT))) continue;
                int source = symbol(name);
                int length = headerBytes(path).length;
                compareHeader(source, path);
                require(expected.length + length <= 4096, "Music exceeds sound RAM: " + path);
                call("_SL_LoadGroup", source & 65535, source >>> 16, length);
                compare(source, 0x7000 + expected.length, length, file);
                if (z80 == null) compare(driver, 0x7000, expected.length, "driver retained after " + file);
                allowSoundWrites = false;
                call("_SL_PlayTune", 0);
                allowSoundWrites = true;
                if (z80 == null) require(ports[0xbc] == 3 && ports[0xba] == 1, "Wrong tune command");
                else {
                    long before = z80.getState()[3];
                    for (int tick = 0; tick < 1000; tick++) z80.tick(6144);
                    System.out.println(file + ": " + z80.report());
                    require(z80.getState()[3] > before, "No audible PSG writes for " + file);
                    require(z80.getState()[7] == 0, "Invalid Z80 access for " + file);
                }
                System.out.printf("Group PASS: %s %d bytes%n", file, length);
            }
        }
        System.out.println("Commands: " + commands);
        System.out.println("TLCS sound writes: " + soundWrites);
    }

    private void mode7() throws Exception {
        String[] headers = {"Aladdin01.h", "Aladdin03.h", "Aladdin13.h", "Aladdin16.h", "GnG07.h", "GNG13.h",
            "Sonic05.h", "SONIC12.h", "ryu_ending.h", "boss_battle.h", "burning_town.h", "mastertracker.h"};
        Path sourceDirectory = Path.of(System.getProperty("ngpc.mode7.sources", "../mode7"));
        compareHeader(symbol("_PSGLIB"), sourceDirectory.resolve("PSGlib.h"));
        allowSoundWrites = true;
        call("_SL_SoundInit");
        if (z80 == null) compare(symbol("_PSGLIB"), 0x7000, 228, "PSG driver");
        int list = symbol("_LIST");
        for (int index = 0; index < 12; index++) {
            int source = memory.readDword(list + index * 8);
            int length = memory.readWord(list + index * 8 + 4);
            compareHeader(source, sourceDirectory.resolve(headers[index]));
            call("_SL_StopBGM", 0);
            call("_SL_LoadData", source & 65535, source >>> 16, length, 0);
            compare(source, 0x70e4, Math.min(length, 4096 - 228), "PSG song " + index);
            compare(symbol("_PSGLIB") + 16, 0x7010, 228 - 16, "PSG code retained");
            call("_SL_PlayBGM", 0, 0);
            require(memory.readWord(0x7006) == 228, "Wrong PSG song pointer");
            if (z80 != null) {
                long before = z80.getState()[3];
                for (int frame = 0; frame < 120; frame++) {
                    z80.write(0xbc, 0xff);
                    z80.tick((int) NgpcTiming.CYCLES_PER_FRAME);
                    require(z80.read(0xbc) == 0, "PSG frame not acknowledged");
                }
                System.out.println(z80.report());
                require(z80.getState()[3] > before, "No audible PSG writes for song " + index);
                require(z80.getState()[7] == 0, "Invalid PSG access");
            }
            System.out.printf("PSG song PASS: %d, %d/%d bytes%n", index, Math.min(length, 3868), length);
        }
        System.out.println("Commands: " + commands);
    }

    private boolean soundRoutine(int address) {
        for (String name : new String[] {"_SL_SOUNDINIT", "_SL_LOADGROUP", "_SL_LOADDATA", "_SL_PLAYBGM"}) {
            Integer start = symbols.get(name);
            if (start == null || address < start) continue;
            int end = symbols.entrySet().stream()
                .filter(item -> item.getKey().startsWith("_") && !item.getKey().contains(".") && item.getValue() > start)
                .mapToInt(Map.Entry::getValue).min().orElse(0x400000);
            if (address < end) return true;
        }
        return false;
    }

    private void runGame(long limit) {
        running = true;
        allowSoundWrites = true;
        registers.setPC(entry);
        registers.setXSP(NgpLoader.STACK_INIT);
        registers.setSR(0x0800);
        long nextFrame = NgpcTiming.CYCLES_PER_FRAME;
        int interruptStack = -1;
        boolean interrupts = true;
        int starts = 0;
        int firstSoundFrame = -1;
        long previousPsg = 0;
        for (long steps = 0; steps < limit; steps++) {
            if (interruptStack >= 0 && registers.getXSP() > interruptStack) interruptStack = -1;
            if (cpu.getCycles() >= nextFrame) {
                nextFrame += NgpcTiming.CYCLES_PER_FRAME;
                frame++;
                int handler = memory.readDword(0x6fcc) & 0xffffff;
                if (interrupts && interruptStack < 0 && handler >= 0x200000) {
                    int stack = registers.getXSP() - 2;
                    memory.writeWord(stack, registers.getSR());
                    stack -= 4;
                    memory.writeDword(stack, registers.getPC());
                    registers.setXSP(stack);
                    registers.setPC(handler);
                    interruptStack = stack;
                }
            }
            pc = registers.getPC();
            if (pc == symbols.getOrDefault("_SL_SOUNDINIT", -1)) {
                uploadSource = symbol(symbols.containsKey("_DRIVER") ? "_DRIVER" : "_PSGLIB");
                uploadDestination = 0x7000;
                uploadLength = symbols.containsKey("_DRIVER") ? 2741 : 228;
                uploads++;
            }
            if (pc == symbols.getOrDefault("_SL_LOADGROUP", -1) || pc == symbols.getOrDefault("_SL_LOADDATA", -1)) {
                int stack = registers.getXSP();
                uploadSource = memory.readDword(stack + 4);
                uploadLength = memory.readWord(stack + 8);
                uploadDestination = symbols.containsKey("_DRIVER") ? 0x7ab5 : 0x70e4;
                require(uploadDestination + uploadLength <= 0x8000, "Runtime sound upload exceeds RAM");
                uploads++;
                System.out.printf("Runtime upload: ROM=%06X RAM=%04X length=%d frame=%d%n",
                    uploadSource, uploadDestination, uploadLength, frame);
            }
            if (pc == symbols.getOrDefault("_SL_PLAYBGM", -1) || pc == symbols.getOrDefault("_SL_PLAYTUNE", -1)) starts++;
            if (memory.readByte(pc) == 6) interrupts = memory.readByte(pc + 1) != 7;
            long before = cpu.getCycles();
            cpu.step();
            z80.tick((int) (cpu.getCycles() - before));
            if (firstSoundFrame < 0 && z80.getState()[3] > 0) firstSoundFrame = frame;
            if (steps % 1_000_000 == 0 && steps > 0) {
                System.out.printf("Runtime: %,d instructions, frame=%d PC=%06X uploads=%d starts=%d %s%n",
                    steps, frame, registers.getPC(), uploads, starts, z80.report());
                if (starts > 0 && steps > 2_000_000) {
                    require(z80.getState()[2] > previousPsg, "Runtime PSG output stopped");
                }
                previousPsg = z80.getState()[2];
            }
        }
        System.out.printf("Runtime completed: frames=%d uploads=%d starts=%d firstSoundFrame=%d TLCS-sound-writes=%d %s%n",
            frame, uploads, starts, firstSoundFrame, soundWrites, z80.report());
        require(starts > 0 && z80.getState()[3] > 0, "No runtime music playback");
        require(z80.getState()[7] == 0, "Invalid runtime Z80 access");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        SoundUploadTest test = new SoundUploadTest(args[0], args[1]);
        boolean execute = List.of(args).contains("--z80");
        if (execute) test.z80 = new Z80SoundDevice();
        int run = List.of(args).indexOf("--run");
        if (run >= 0) test.runGame(Long.parseLong(args[run + 1]));
        else if (args.length > (execute ? 3 : 2)) test.group(Path.of(args[2]), "_driver");
        else test.mode7();
        if (test.z80 != null) test.z80.close();
        System.out.println("SoundUploadTest: PASS (" + (execute ? "Z80Ex execution" : "modeled Z80 responses") + ")");
    }
}