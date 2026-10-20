# LLVM-to-TLCS-900/H Transpiler

Build Neo Geo Pocket Color ROMs from C using Clang, a Java LLVM-IR backend,
and the AS cross-assembler.

This is a project-oriented backend, not a complete implementation of LLVM IR
or a drop-in replacement for every cc900 feature. Existing ports include
MODE7, Bomberman, SAMPLE_2, and MIX.

## 1. Build pipeline

```text
C sources
   |
   | Clang: --target=arm-none-eabi -S -emit-llvm -DCLANG
   v
LLVM IR (.ll)
   |
   | Ir2Tlcs900
   v
TLCS-900/H assembly (.asm) + ROM constants (.inc)
   |
   | AsmOptimizer
   v
Optimized assembly
   |
   | AS assembler through Wine / build.bat
   v
Assembler output (.p) + symbol map (.map)
   |
   | P2BIN
   v
NGPC ROM (.ngp)
```

Clang's ARM target is used to obtain suitable 32-bit LLVM IR. No ARM machine
code is generated or executed. The Java backend emits TLCS-900/H assembly.

The LLVM build does not use cc900 to compile C.

## 2. Requirements

The existing makefiles are intended to run under Linux/WSL with:

- GNU Make.
- Clang: the makefiles default to `clang-22`; comments also document
  compatibility with `clang-18`.
- A modern JDK providing `java` and `javac`.
- Wine, for the Windows assembler and conversion tools.
- AS and P2BIN under `as/bin` at the repository root.
- Shared assembler includes under `src/asm`:
  `stddef96.inc`, `HARDWARE.INC`, and `SYSTEM.INC`.
- The project's existing headers, assets, build.bat, and build prerequisites.

MODE7's experimental grouped targets require GNU Make 4.3 or newer.

Run builds from the game directory, not from the transpiler directory.

## 3. Quick start

For MODE7:

```sh
cd src/mode7
make -f makefile.llvm
```

Other existing ports:

```sh
cd src/bomberman
make -f makefile.llvm
```

```sh
cd src/SAMPLE_2
make -f makefile.llvm
```

```sh
cd src/mix
make -f makefile.llvm
```

Output ROM names:

| Project directory | Default ROM |
| --- | --- |
| mode7 | MODE7.ngp |
| bomberman | BOMBERMAN.ngp |
| SAMPLE_2 | SAMPLE.ngp |
| mix | MIX.ngp |

Missing or outdated backend classes are compiled automatically by the
makefiles.

### Select Clang and its optimization level

```sh
make -f makefile.llvm CC=clang-18
make -f makefile.llvm CC=clang-22 OPT=-O2
```

Current project defaults:

| Project | CC | OPT |
| --- | --- | --- |
| MODE7 | clang-22 | -O3 |
| Bomberman | clang-22 | -O3 |
| SAMPLE_2 | clang-22 | -O1 |
| MIX | clang-22 | -O3 |

The makefiles track changes to `CC` and `OPT` in `.llvm-flags`, causing the
LLVM IR to be rebuilt when those settings change.

A higher Clang optimization level does not necessarily produce faster
TLCS-900/H code. Preserve the project's default unless testing a change.

## 4. makefile.llvm variables and targets

### Variables

| Variable | Purpose |
| --- | --- |
| `NAME` | Output basename, such as MODE7 or BOMBERMAN. |
| `CC` | Clang executable. Override on the make command line. |
| `OPT` | Clang optimization options; normally a single `-O` level. |
| `LL` | LLVM IR inputs corresponding to the project's C translation units. |
| `ALLOCATED_NAME` | MODE7-only experimental output basename; defaults to MODE7_ALLOCATED. |

When adding a C translation unit, add its `.ll` output to `LL`. Preserve
subdirectory names, as the Bomberman and MIX makefiles do.

These makefiles do not expose a general `CFLAGS`, `LDFLAGS`, or
`TRANSPILER_FLAGS` interface. Additional include directories, defines, or
backend options must be added to the relevant recipe.

### Targets

| Target | Purpose |
| --- | --- |
| `all` | Default build, including the project's declared prerequisites and ROM. |
| `clean` | Remove the generated files listed by that project's clean recipe. |
| `bench` | MODE7: run the default ROM's motion benchmark with renderer verification. |
| `allocated` | MODE7: build the separate experimental register-allocation ROM. |
| `bench-allocated` | MODE7: benchmark and verify that experimental ROM. |

The clean recipes differ between projects; do not assume they remove every
generated map, include, listing, or diagnostic file.

## 5. Flags used by the build

### Clang

A representative compilation is:

```sh
clang-22 --target=arm-none-eabi -S -O3 -Wall -emit-llvm \
  -DCLANG main.c -o main.ll
```

| Flag | Meaning |
| --- | --- |
| `--target=arm-none-eabi` | Select the frontend's 32-bit target/data layout. This is not the final machine-code target. |
| `-S` | Produce textual output rather than an object file. |
| `-emit-llvm` | With `-S`, produce textual LLVM IR. |
| `-O1`, `-O2`, `-O3` | Select Clang's optimization level before translation. |
| `-Wall` | Enable common C diagnostics. Review warnings when porting legacy sources. |
| `-DCLANG` | Enable the source compatibility branches used by these ports. |
| `-I.` | Search the current project directory for headers; used by Bomberman and MIX. |
| `-Imusix` | Add MIX's music-header directory. |
| `-fno-builtin-strlen` | MIX: prevent Clang from treating the project's strlen as its standard builtin. |
| `-o FILE` | Select the output path, particularly for sources in subdirectories. |

`-DCLANG` is a project compatibility define, not a Clang target-selection flag.

### Ir2Tlcs900

```sh
java -cp ../transpiler Ir2Tlcs900 main.ll library.ll \
  -o GAME.asm --data-output GAME.inc
```

| Argument | Meaning |
| --- | --- |
| `file1.ll file2.ll ...` | LLVM modules to translate together. |
| `-o FILE` | Write assembly to FILE; otherwise assembly is printed to stdout. |
| `--data-output FILE` | Write ROM constants to a separate include referenced by the assembly. Without it, constants remain inline. |
| `--experimental-register-allocation FUNCTION` | Enable the experimental loop allocator for a named C function. Repeat for multiple functions. |
| `--shorten-jumps-from-warnings ASM LOG` | Separate maintenance mode: rewrite eligible jumps using assembler warnings, update ASM, and print the number changed. |

Important: the translator also discovers additional `.ll` files in the
directory of the first input. It does not recursively discover subdirectories.
Keep stale or unrelated `.ll` files out of that directory, and explicitly list
subdirectory inputs.

### AsmOptimizer

```sh
java -cp ../transpiler AsmOptimizer GAME.asm -v \
  --pre-opt-output GAME.preopt.asm
```

| Argument | Meaning |
| --- | --- |
| `input.asm` | Assembly to optimize. |
| `output.asm` | Optional separate output; otherwise the input is overwritten. |
| `-v` | Print optimization statistics to stderr. |
| `--dry-run` | Run analysis and print statistics without writing files. |
| `--pre-opt-output FILE` | Save the input before standalone optimizer passes; ignored in dry-run mode. |

The pre-optimization snapshot is the input to the standalone optimizer.
The translator has already performed its own optimizations.

### AS and P2BIN

The project build.bat files use commands equivalent to:

```bat
..\..\as\bin\asl -i ..\asm -g map GAME.asm
..\..\as\bin\p2bin -r $-$ GAME.p GAME.ngp
```

Bomberman and MIX additionally use AS's `-U`.

| Flag | Meaning |
| --- | --- |
| AS `-i ..\asm` | Add the shared assembler include directory. |
| AS `-g map` | Generate the symbol map used by diagnostics and simulator tests. |
| AS `-U` | Enable case-sensitive symbols. Preserve this for projects that require it. |
| P2BIN `-r $-$` | Convert the assembler output using its occupied address range. |

The make recipe assembles, reads the assembler warnings, shortens eligible
jumps, and repeats until no further changes are reported. Keep this loop when
adapting a makefile; a single manual assembler invocation is not equivalent.

## 6. Adapting original cc900 C sources

Most ordinary C gameplay and rendering code can remain shared. The main
changes concern compiler extensions, inline assembly, types, interrupts, and
library declarations.

Keep cc900-specific behavior under conditional compilation rather than
replacing it globally.

### 6.1 Compiler compatibility definitions

Existing ports put these definitions in ngpc.h:

```c
#ifdef CLANG
#define __interrupt
#define __ASM __asm
#endif
```

This lets Clang parse sources using cc900's interrupt spelling and assembly
macro. It does not make Clang understand TLCS-900/H registers or instructions.

Keep these compatibility definitions visible before declarations using them.

### 6.2 Audit integer and pointer assumptions

For the Clang target used here:

- char is 8 bits.
- short is 16 bits.
- int and long are 32 bits.
- Pointers are 32 bits.

cc900 uses a 16-bit int. Its pointer representation also depends on the
compiler's memory model and near/far qualifiers.

Prefer explicit project types such as u8, u16, u32, s16, and s32 where width
matters. Audit:

- sizeof(int), structures, and serialized data.
- Integer promotions, overflow, shifts, and signedness.
- Pointer-to-integer conversions.
- Inline-assembly argument offsets.
- Shared C/assembly data layouts.

Do not assume that Clang and cc900 give every C expression identical semantics.
The backend includes compatibility lowering, but explicit widths are still
important.

If the original code uses __far or an NGP_FAR abstraction, provide a
Clang-compatible conditional definition. The current LLVM path uses 32-bit
pointers; it does not use cc900's near-pointer representation.

### 6.3 Preserve volatile hardware access

Hardware registers, polling locations, and values modified by interrupts
must retain appropriate volatile declarations:

```c
#define JOYPAD (*(volatile u8 *)0x6f82)

extern volatile u8 VBCounter;
```

Without volatile, Clang may remove, combine, or move accesses, or turn a
polling loop into a loop that never rereads memory.

### 6.4 Convert inline-assembly syntax where necessary

Assembly strings are passed through to AS. Clang does not assemble them.

Existing ports use conditional branches for assembler-specific hexadecimal
notation:

```c
#ifdef CLANG
__asm("ld xix,0fffe00h");
#else
__asm("ld xix,0xfffe00");
#endif
```

Use a leading zero when an AS hexadecimal literal would otherwise begin with
a letter, for example `0f7h`.

Also review raw `db` byte lists, EQU definitions, and symbol names.

The generated assembly already includes stddef96.inc, HARDWARE.INC, and
SYSTEM.INC. Avoid redefining their hardware or BIOS symbols inside the
Clang path. Existing hicolor, movie-player, and flash sources demonstrate
these conditional exclusions.

### 6.5 Replace cc900 register pseudo-variables

Clang does not understand cc900 expressions such as:

```c
return __L;
return __HL;
return __XHL;
```

Prefer a normal C implementation where possible. For example, the ports
replace the hand-written Multiply32bit implementation with C multiplication
under CLANG.

For a deliberately assembly-only wrapper, the existing linkkit code moves the
result into the required return register, executes an explicit assembly `ret`,
and supplies an unreachable C return to satisfy the frontend.

That technique is for assembly-owned functions, not arbitrary mixed C/assembly
functions.

### 6.6 Respect raw-assembly and mixed-function boundaries

The backend recognizes a narrow assembly-only function pattern and suppresses
its normal frame and parameter-spill code. This supports existing routines
that read arguments directly at entry-relative `(xsp+N)` offsets.

Adding C arithmetic, loads, stores, branches, or calls can make the function
a normal translated function with a stack frame.

Therefore:

- Do not assume entry-relative stack offsets remain valid in mixed functions.
- Do not put an early assembly `ret` into a framed C function.
- Treat raw assembly as responsible for its register and stack discipline.
- Reinspect generated assembly after changing an assembly wrapper.

The supported pattern is not a general GCC-style extended-assembly interface
with arbitrary operands, outputs, and clobbers.

### 6.7 Interrupt handlers need special attention

Defining __interrupt as empty only makes the source parse.

The current backend identifies translated interrupt handlers by a
case-insensitive `interrupt` substring in the function name.

A C handler named `myVBLInterrupt` is recognized. A differently named handler
does not become an ISR merely because the original source used __interrupt.

For normal translated C handlers, use an appropriate name and verify that the
generated function saves/restores the necessary state and returns with `reti`.

Some existing handlers, such as MODE7's myHBL Clang branch, are raw assembly
with an explicit `reti`. Preserve that distinction: an explicit `reti` is not
a substitute for restoring a generated C frame.

Always inspect and test interrupt changes.

### 6.8 Declare project library functions correctly

Clang needs declarations matching the definitions and call sites.

Review legacy functions named memcpy, memset, strlen, and similar standard
library names. Existing projects sometimes use nonstandard signatures and
provide their own implementations.

MIX adds Clang-only declarations/implementations and uses
`-fno-builtin-strlen`. SAMPLE_2 supplies a hand-written memcpy.

Do not blindly add hosted system headers: they may declare incompatible
signatures or introduce assumptions unsuitable for this ROM environment.

Resolve compiler warnings rather than hiding conflicting declarations.

### 6.9 Preserve compatibility with cc900

If both toolchains must remain usable:

- Keep the existing cc900 build scripts.
- Use CLANG branches for frontend/assembler differences.
- Retain C syntax accepted by the original compiler in shared code.
- Rebuild both versions after changing shared interfaces or layouts.
- Compare behavior, not just whether both versions compile.

## 7. Assembly interface

The backend follows a CC900-style __cdecl stack interface:

- Arguments are pushed right-to-left.
- Each argument occupies at least two bytes.
- A far call leaves a four-byte return address.
- At raw function entry, the first argument starts at `(xsp+4)`.
- The caller removes arguments after the call.

Return registers:

| Result | Register |
| --- | --- |
| u8 / s8 | L |
| u16 / s16 | HL |
| u32 / s32 / pointer | XHL |

A raw wrapper taking a pointer followed by a u16 therefore finds the pointer
at `(xsp+4)` and the u16 at `(xsp+8)`.

Do not generalize these offsets to arbitrary original cc900 memory models or
to functions after a generated prologue.

BIOS wrappers must also preserve the required status/register-bank state.
See the existing linkkit wrappers for examples.

## 8. Experimental register allocation

MODE7 provides separate outputs:

```sh
make -f makefile.llvm allocated
make -f makefile.llvm bench-allocated
```

These select update and generate MODE7_ALLOCATED outputs without overwriting
the default ROM.

For another supported function, add the option to the translator invocation:

```sh
java -cp ../transpiler Ir2Tlcs900 main.ll library.ll \
  --experimental-register-allocation firstFunction \
  --experimental-register-allocation secondFunction \
  -o GAME_ALLOCATED.asm --data-output GAME_ALLOCATED.inc
```

Use C function names without the assembler's leading underscore.

A missing or unsupported selected function is an error, not a silent fallback.

The allocator currently supports a restricted closed-natural-loop subset.
Restrictions include calls inside the region, volatile/atomic accesses,
variable shifts, signed comparisons, unsupported pointer forms, and SSA
values escaping the region. ISR and raw-assembly functions are not supported.

Selected functions carry named protection fences. Legacy assembly passes
continue outside those functions, but must not rewrite their protected bodies.
Do not remove the markers or fences.

The allocator is opt-in because support is incomplete and performance is
function-dependent. Some tested functions become faster; others become slower.
Passing correctness tests does not guarantee a performance improvement.

## 9. Validation

### Compile the backend and simulator

From src/transpiler:

```sh
classes=/tmp/ngpc-transpiler-classes
mkdir -p "$classes"
javac -d "$classes" *.java sim/*.java sim/tests/*.java \
  sim/mode7/*.java sim/bomberman/*.java sim/sample2/*.java
```

This produces an independent class directory rather than relying on existing
.class files next to the sources.

### Run compiler regression tests

From the same directory:

```sh
java -cp "$classes" Ir2Tlcs900CorrectnessTest
java -cp "$classes" AsmOptimizerAddressTest
java -cp "$classes" AsmOptimizerDeadSpillTest
java -cp "$classes" AsmOptimizerColorBitTest
java -cp "$classes" AsmOptimizerPhiAccumulatorTest
```

Focused allocator tests:

```sh
java -cp "$classes" Ir2Tlcs900CorrectnessTest --allocator-only
```

Assembled correctness tests additionally require Clang, Wine, and AS/P2BIN.

### Verify a ROM

Use a ROM and map from the same completed build. For MODE7, from
src/transpiler:

```sh
java -cp "$classes" sim.mode7.Mode7GraphicsTest \
  ../mode7/MODE7.ngp ../mode7/MODE7.map

java -cp "$classes" sim.tests.IsrRegisterPreservationTest \
  ../mode7/MODE7.ngp ../mode7/MODE7.map

java -cp "$classes" sim.mode7.Mode7MotionBench \
  ../mode7/MODE7.ngp ../mode7/MODE7.map --verify
```

The simulator also includes Bomberman, SAMPLE_2, and MIX-specific checks.

Simulator cycle counts are comparative estimates, not guaranteed hardware
timings. Validate timing-sensitive graphics, interrupts, audio, and link
behavior on hardware where possible.

## 10. Troubleshooting checklist

- **Clang rejects cc900 keywords:** check -DCLANG and compatibility definitions.
- **Unknown __L/__HL/__XHL:** replace register pseudo-variable returns.
- **Duplicate assembler symbols:** check definitions already supplied by includes.
- **Incorrect inline-assembly constants:** check AS syntax in the CLANG branch.
- **Incorrect assembly argument values:** check argument widths and whether a
  generated frame changed XSP.
- **Interrupt corruption:** check handler naming, register preservation, and reti.
- **Polling loop hangs:** check volatile and the hardware/simulator behavior.
- **Unexpected extra code or duplicate modules:** remove stale .ll inputs.
- **Code-size or speed regression:** compare both Clang optimization levels and
  complete backend pipelines; do not assume -O3 or the allocator always wins.
- **ROM/map disagreement:** wait for the build to finish and use matching outputs.
