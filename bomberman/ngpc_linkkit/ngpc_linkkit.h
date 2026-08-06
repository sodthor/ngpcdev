#ifndef NGPC_LINKKIT_H
#define NGPC_LINKKIT_H

/*
 * ngpc_linkkit -- a blank two-console link layer for the NgpCraft template.
 *
 * It does exactly four things and nothing game-specific:
 *
 *   - finds the other console and agrees on who is host,
 *   - carries an opaque payload of NGPC_LINKKIT_PAYLOAD bytes each way, once
 *     per frame,
 *   - frames that payload so a lost byte costs one packet instead of the whole
 *     session,
 *   - tells you when the peer has gone quiet.
 *
 * What goes inside the payload is entirely yours. The module never looks at it.
 *
 * ---------------------------------------------------------------------------
 * HOW TO USE IT
 * ---------------------------------------------------------------------------
 *   1. Add the two .rel files to your makefile OBJS (see README).
 *   2. Pick your payload size:  -DNGPC_LINKKIT_PAYLOAD=8
 *   3. Once, when the player enters link mode:   ngpc_linkkit_init();
 *      If your menu already asked "HOST or JOIN", also call
 *      ngpc_linkkit_set_role() -- see the warning below.
 *   4. Once per frame, in this order:
 *
 *          ngpc_linkkit_stage(my_bytes);      // what I want the peer to see
 *          ngpc_linkkit_update();             // talks to the cable
 *          if (ngpc_linkkit_fresh())          // did something new arrive?
 *              ngpc_linkkit_peek(their_bytes);
 *
 *      update() never blocks and never waits for the peer.
 *   5. Watch ngpc_linkkit_state() and react to LOST.
 *
 * ---------------------------------------------------------------------------
 * THE ONE THING THAT WILL BITE YOU
 * ---------------------------------------------------------------------------
 * Two consoles that are byte-for-byte identical -- same build, started on the
 * same frame, nobody touching the buttons -- cannot break a tie. Whatever you
 * randomise from (clock, frame counter, RNG state) is identical on both, so
 * both draw the same number, forever. No algorithm fixes this; it is a real
 * property of a symmetric system.
 *
 * This module handles it in two ways, and you should pick one deliberately:
 *
 *   - Default: on a tie it retries a few times, then CONNECTS ANYWAY and sets
 *     ngpc_linkkit_role_ambiguous(). Right choice when your game is symmetric
 *     (both sides send the same kind of thing) and does not care who is host.
 *
 *   - Better, if the roles actually matter: put HOST / JOIN in your menu and
 *     call ngpc_linkkit_set_role() before init. Deterministic, no coin flip,
 *     and it is what commercial link games do.
 *
 * ---------------------------------------------------------------------------
 * BANDWIDTH -- read this before choosing NGPC_LINKKIT_PAYLOAD
 * ---------------------------------------------------------------------------
 * The cable runs at 19200 bps 8N1 = 1920 bytes/s = about 32 bytes per frame at
 * 60 Hz. Each packet costs PAYLOAD + 4 bytes of overhead. So a 26-byte payload
 * already fills the wire; anything above that is dropped, not queued.
 *
 * If your game state does not fit, do not raise the payload -- send less:
 *   - send what CHANGED instead of the whole state,
 *   - or split the state into slices and send one slice per frame,
 *   - or send at 30 Hz instead of 60.
 * A small packet that always arrives beats a big one that sometimes does.
 */

// #ifndef NGPC_LINKKIT_TYPES_HEADER
// #define NGPC_LINKKIT_TYPES_HEADER "ngpc_types.h"
// #endif
// #include NGPC_LINKKIT_TYPES_HEADER

/* ---- Tunables (override with -D on the compiler command line) ---- */

/* Bytes of game data carried each way, each frame. Keep it small: see above. */
#ifndef NGPC_LINKKIT_PAYLOAD
#define NGPC_LINKKIT_PAYLOAD 8
#endif

/* Bumped by you whenever the meaning of the payload changes. Two consoles
 * running different builds then report MISMATCH instead of trading nonsense. */
#ifndef NGPC_LINKKIT_VERSION
#define NGPC_LINKKIT_VERSION 1
#endif

/* Frames to keep looking for a peer before giving up. 300 = about 5 seconds. */
#ifndef NGPC_LINKKIT_CONNECT_FRAMES
#define NGPC_LINKKIT_CONNECT_FRAMES 300
#endif

/* Frames without a valid packet before the link is declared LOST. */
#ifndef NGPC_LINKKIT_LOST_FRAMES
#define NGPC_LINKKIT_LOST_FRAMES 180
#endif

/* ---- States ---- */
#define LINKKIT_IDLE       0  /* init() not called, or link torn down       */
#define LINKKIT_CONNECTING 1  /* announcing and listening                   */
#define LINKKIT_READY      2  /* payloads flowing                           */
#define LINKKIT_LOST       3  /* peer went quiet; update() keeps retrying   */
#define LINKKIT_MISMATCH   4  /* peer runs a different version/payload size */

/* ---- Roles ---- */
#define LINKKIT_ROLE_AUTO  0  /* decide by drawing lots (default)           */
#define LINKKIT_ROLE_HOST  1
#define LINKKIT_ROLE_GUEST 2

/* ---- API ---- */

/* Bring up the port and start looking for a peer. Safe to call again at any
 * time -- it holds no leftover state, so it doubles as "reconnect". */
void ngpc_linkkit_init(void);

/* Force the role instead of drawing lots. Call BEFORE init(). Pass
 * LINKKIT_ROLE_HOST on one console and _GUEST on the other, typically from a
 * menu. This is the only way to be certain who is who. */
void ngpc_linkkit_set_role(u8 role);

/* Once per frame. Drives the handshake, sends the staged payload, drains
 * whatever arrived. Never blocks. Never call it from an ISR. */
void ngpc_linkkit_update(void);

/* Copy NGPC_LINKKIT_PAYLOAD bytes to be sent by the next update(). Call every
 * frame; if you skip it, the previous contents are sent again. */
void ngpc_linkkit_stage(const u8 *payload);

/* 1 if a new payload arrived since the last time you called peek(). */
u8 ngpc_linkkit_fresh(void);

/* Copy the most recent payload received. Clears fresh(). Between two arrivals
 * this keeps returning the last good one -- it is never garbage. */
void ngpc_linkkit_peek(u8 *payload);

u8 ngpc_linkkit_state(void);
u8 ngpc_linkkit_role(void);            /* HOST or GUEST once READY   */
u8 ngpc_linkkit_role_ambiguous(void);  /* 1 = tie, both think HOST   */

/* ---- Counters. Look at these first when something does not work. ----
 *
 * The single most useful debugging step on a link cable is to stop guessing and
 * count. "I send N, the peer receives M, K of them parse" tells you in one shot
 * whether your problem is wiring, bandwidth or framing.
 *
 *   tx_packets  rose but rx_packets did not  -> nothing is arriving: cable,
 *                                               COMRECIVESTART, or RTS
 *   tx_skipped  climbing                     -> you are over budget, the ring
 *                                               is full; send less
 *   rx_bad_sum  climbing                     -> bytes are being lost or the two
 *                                               sides disagree on the format
 *   rx_dropped  climbing                     -> you are not calling update()
 *                                               often enough
 */
u16 ngpc_linkkit_tx_packets(void);
u16 ngpc_linkkit_rx_packets(void);
u16 ngpc_linkkit_tx_skipped(void);
u16 ngpc_linkkit_rx_bad_sum(void);
u16 ngpc_linkkit_rx_dropped(void);

#endif /* NGPC_LINKKIT_H */
