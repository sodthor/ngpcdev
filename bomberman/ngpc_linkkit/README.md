# ngpc_linkkit — blank link cable module

A **generic, empty** two-console link layer: it carries N opaque bytes each way,
once per frame. What goes inside is yours — the module never looks at it.

It is the neutral counterpart of `ngpc_link/`, which is written for Windcup
(INPUT/SETUP packets, receive queue sized for one simulation step). The two can
live side by side: symbols and macros use different prefixes (`lkcom_*` /
`LINKKIT_*` versus `ngpc_com_*` / `NGPC_LINK_*`).

## Files

| | |
|---|---|
| `ngpc_linkkit_com.c/.h` | BIOS COM calls (vectors 0x10–0x1A) |
| `ngpc_linkkit.c/.h` | handshake, framing, bandwidth budget, link loss |
| `example_linkkit_main.c` | minimal loop, not part of the build |

## Wiring it in

In the `Makefile`, next to the others:

```
	ngpc_linkkit/ngpc_linkkit.rel \
	ngpc_linkkit/ngpc_linkkit_com.rel \
```

and the build rule (the `-I.` is already there for `ngpc_link/`; the headers
include each other as `"ngpc_linkkit/..."` because cc900 resolves relative
includes from the primary source file, not from the header doing the including):

```
ngpc_linkkit/%.rel: ngpc_linkkit/%.c
	$(CC) -c -O3 -I. $< -o $@
```

Payload size: `-DNGPC_LINKKIT_PAYLOAD=8`, or a `#define` in `ngpc_types.h`
before the include.

## Using it

```c
ngpc_linkkit_set_role(LINKKIT_ROLE_HOST);   /* optional, see below */
ngpc_linkkit_init();

/* once per frame, in this order */
ngpc_linkkit_stage(my_bytes);
ngpc_linkkit_update();
if (ngpc_linkkit_fresh())
    ngpc_linkkit_peek(their_bytes);
```

`update()` never blocks. A frame with no packet is **normal**: keep the previous
state on screen, do not freeze and do not wait for the peer.

## What to know before building on top of it

**Bandwidth.** 19200 bps 8N1 = 1920 bytes/s = about **32 bytes per frame** at
60 Hz, and the BIOS rings hold 64 bytes. Each packet costs `PAYLOAD + 4`, so a
26-byte payload already fills the wire. If your state does not fit, do not grow
the packet: send what **changed**, or split the state into slices and send one
per frame, or drop to 30 Hz. A small packet that always arrives beats a big one
that sometimes does.

**Never a truncated packet.** The module checks the free space before writing
anything into the ring, and skips the frame otherwise (counter `tx_skipped`).
Writing half a packet because the ring filled up mid-way leaves the peer's parser
staring at an incomplete message — that is how a link desynchronises for good.

**The format is self-describing.** Marker `0xA5`, type, sequence, body, checksum.
The marker is what lets a receiver find its feet again after a lost byte; the
checksum stops a false marker inside the data being taken for a real packet; the
type byte stops a handshake message being parsed as game data. A protocol written
as a sequence of phases — send this, then wait for that — breaks the moment one
console is a phase ahead, because the bytes carry no clue about which phase they
belong to. Self-describing messages have no phases to get out of step.

**The host/guest tie-break has a hard limit.** Two identical consoles, started on
the same frame, nobody touching the buttons, draw the same token — whatever you
mix in, the state is the same on both sides. No algorithm fixes that. The module
retries a few times and then **connects anyway**, raising
`ngpc_linkkit_role_ambiguous()`: refusing the link over a coin flip the game does
not use would be absurd. If the roles genuinely matter in your game, put
HOST / JOIN in your menu and call `ngpc_linkkit_set_role()` before `init()`. That
is deterministic, and it is what commercial link games do.

**`NGPC_LINKKIT_VERSION`.** Bump it whenever the meaning of the payload changes.
Two carts from different builds then report `LINKKIT_MISMATCH` instead of trading
nonsense.

**The five counters.** `tx_packets`, `rx_packets`, `tx_skipped`, `rx_bad_sum`,
`rx_dropped`. On a cable, the first thing to do when "it does not work" is to
stop guessing and count. TX climbing while RX stays at zero: nothing is arriving
— cable, `recv_start`, or RTS. `skipped` climbing: you are over budget.
`bad_sum` climbing: bytes are being lost, or the two sides disagree on the
format. `dropped` climbing: you are not calling `update()` often enough.

## cc900 ABI constraints the BIOS layer respects

Spelled out at the top of `ngpc_linkkit_com.h`; in short:

1. **No wrapper returns a value.** A cc900 function whose body is nothing but
   inline asm has no C return: what the caller gets is whatever was left in the
   return register. Every result goes through a pointer, and buffer levels are
   read from the BIOS counters at `0x6D00` / `0x6D01` — more reliable than any
   status word.
2. **Bank-3 registers named explicitly** (`rb3`, `xhl3`, `ra3`). Bare names mean
   the current bank, and whether the C runtime happens to run in bank 3 depends
   on the project's startup code. Naming them explicitly is correct either way.
   ⚠️ A `memory ← bank-3 register` store is **not encodable**: it has to transit
   through a register of the current bank (`ldb a, rb3` then `ldb (xde), a`).
3. **`push sr` / `pop sr` around the call.** It puts the register bank *and* the
   interrupt level back the way they were — which matters because `COMOFFRTS`
   starts with `ei 6`, masking VBlank: without the restore,
   `rts_off(); wait_vblank();` never returns, and a real console powers itself
   off through the watchdog in about a second. Corollary: **never call these from
   an ISR**.
4. **Arguments on the stack**: `(xsp+4)`, then `(xsp+6)` — except a **pointer
   takes 4 bytes**, so `(ptr, u8)` puts the `u8` at `(xsp+8)`. And they must be
   read **before** the pushes, which move `xsp`.

## Validation status

Compiles cleanly (`cc900 -O3 -I.`) in this project.

**Exercised across two emulated consoles, in a different project.** The module
was dropped into an unrelated cc900 homebrew (Fruity Vines: flat layout, its own
`library.c`, no `ngpc_types.h`) and driven for 600 frames on two linked consoles:

```
cable : A->B 7135 bytes, B->A 7135 bytes
[P1] state=READY role=HOST ambiguous=1 | tx=480 rx=469 skip=0 bad=0
[P2] state=READY role=HOST ambiguous=1 | tx=480 rx=469 skip=0 bad=0
     payload received = [64,65,66,67,68,69,70,71,72,73,74]   (byte-exact)
```

That run matters for one specific reason: **that project's C runtime does NOT run
in register bank 3** (measured: `ldb l,w` returns 0x48 while bank 3 holds 0x5A).
So the explicit bank naming in the BIOS layer is not theoretical caution — it is
what makes the module portable, and it is now proven on both kinds of project.

`ambiguous=1` with both sides HOST is the expected outcome there: two emulated
consoles are perfectly symmetric, so the tie-break cannot resolve. See the
tie-break section above; use `ngpc_linkkit_set_role()` if the roles matter.

⛔ **Never tested on two real consoles with a real cable.** The emulator relays
bytes without simulating bit-level transmission time, so the bandwidth budget in
this file is calculated, not measured.

To exercise it here, `validate_link_2p.py` in the project root already drives two
consoles over the cable: point it at a ROM where `ngpc_linkkit` is wired in place
of `ngpc_link`.

## Dropping it into another project

Three things, no more:

1. A `ngpc_types.h` next to your sources — three lines, it just includes your
   SDK header so the module gets `u8` / `u16`, and it is the natural place for
   your `#define NGPC_LINKKIT_PAYLOAD`. (Or pass
   `-DNGPC_LINKKIT_TYPES_HEADER="ngpc.h"` instead.)
2. `-I.` on the compile line, because the headers include each other as
   `"ngpc_linkkit/..."`.
3. The two `.rel` files in your OBJS.

Nothing else: the module calls no SDK function at all, only the BIOS and a few
fixed RAM addresses.
